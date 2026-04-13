package com.binaryeq.grammar;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.xpath.XPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a Python source file and applies a sequence of
 * {@link Transformation} steps to it.
 *
 * <p>Each step re-parses the source produced by the previous step, so later
 * steps can match nodes introduced or modified by earlier ones. Output is
 * produced by a depth-first traversal of the parse tree, emitting each
 * token's own text. Structural tokens ({@code NEWLINE}, {@code INDENT},
 * {@code DEDENT}) drive indentation and line breaks; within-line spacing
 * between non-deleted adjacent tokens is recovered from the original source
 * string. When a matched node is encountered its subtree is skipped and the
 * replacement text is emitted instead. Empty lines that result from deleted
 * nodes are suppressed, producing compact, normalised output.
 *
 * <p>Using an empty replacement string (the default for
 * {@link Transformation#delete}) removes matched nodes entirely without
 * leaving blank lines in their place.
 *
 * <p>Calling {@link #normalize} with no transformation performs a
 * parse-tree round-trip that compacts the source by removing any blank lines.
 */
public class PythonTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(PythonTransformer.class);

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Applies {@code transformations} sequentially to the contents of
     * {@code inputPath} and returns the final source text.
     */
    public String transform(Path inputPath, List<Transformation> transformations)
            throws IOException {
        String source = readSource(inputPath);
        int step = 1;
        for (Transformation t : transformations) {
            LOG.info("Step {}/{}: XPath={} replacement={}", step, transformations.size(),
                    t.xpathExpr(), t.replacement().isEmpty() ? "<delete>" : t.replacement());
            source = applyStep(source, t);
            step++;
        }
        return source;
    }

    // ------------------------------------------------------------------
    // Convenience single-step overloads (delegate to the list variant)
    // ------------------------------------------------------------------

    /** Removes every node matching {@code xpathExpr}. */
    public String transform(Path inputPath, String xpathExpr) throws IOException {
        return transform(inputPath, List.of(Transformation.delete(xpathExpr)));
    }

    /**
     * Replaces every node matching {@code xpathExpr} with {@code replacement}.
     * Pass {@code ""} to delete.
     */
    public String transform(Path inputPath, String xpathExpr, String replacement)
            throws IOException {
        return transform(inputPath, List.of(Transformation.replace(xpathExpr, replacement)));
    }

    /**
     * Parses the source at {@code inputPath} and returns it as a
     * normalised string produced by a parse-tree traversal, without
     * applying any structural transformations.
     */
    public String normalize(Path inputPath) throws IOException {
        String source = readSource(inputPath);
        ParseResult parsed = parse(source);
        return buildOutput(parsed.tree, Set.of(), "", source);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String applyStep(String source, Transformation t) {
        ParseResult parsed = parse(source);
        Collection<ParseTree> matches = XPath.findAll(parsed.tree, t.xpathExpr(), parsed.parser);
        LOG.info("  {} match(es)", matches.size());
        return buildOutput(parsed.tree, new HashSet<>(matches), t.replacement(), source);
    }

    /**
     * Walks {@code root} depth-first, emitting each token's own text.
     * {@code NEWLINE} tokens drive line breaks; consecutive newlines are
     * collapsed so that deleted nodes leave no blank lines. {@code INDENT}
     * and {@code DEDENT} tokens maintain an indentation stack so each
     * content line is prefixed with the correct number of spaces. Within a
     * single logical line, the original source substring between adjacent
     * non-deleted tokens is used to recover any spaces that the lexer's
     * {@code SKIP_} rule discarded. Matched nodes are replaced by
     * {@code replacement} (or omitted when it is empty).
     */
    private String buildOutput(ParseTree root, Set<ParseTree> matched,
                               String replacement, String source) {
        StringBuilder sb = new StringBuilder();
        EmitState state = new EmitState(source);
        emitNode(root, matched, replacement, sb, state);
        return sb.toString();
    }

    private void emitNode(ParseTree node, Set<ParseTree> matched, String replacement,
                          StringBuilder sb, EmitState state) {
        if (matched.contains(node)) {
            if (!replacement.isEmpty()) {
                if (state.atLineStart) {
                    sb.append(state.currentIndent);
                    state.atLineStart = false;
                }
                sb.append(replacement);
            }
            state.afterDeletion = true;
            return;
        }
        if (node instanceof TerminalNode tn) {
            Token tok = tn.getSymbol();
            int type = tok.getType();
            if (type == Token.EOF) return;

            if (type == Python3Lexer.NEWLINE) {
                if (!state.atLineStart) {
                    sb.append('\n');
                    state.atLineStart = true;
                    state.prevContent = null;
                }
                state.afterDeletion = false;
                return;
            }
            if (type == Python3Lexer.INDENT) {
                state.indentStack.push(tok.getText());
                state.currentIndent = tok.getText();
                return;
            }
            if (type == Python3Lexer.DEDENT) {
                if (!state.indentStack.isEmpty()) state.indentStack.pop();
                state.currentIndent = state.indentStack.isEmpty() ? "" : state.indentStack.peek();
                return;
            }

            // Regular content token — emit with appropriate preceding spacing.
            if (state.atLineStart) {
                sb.append(state.currentIndent);
                state.atLineStart = false;
            } else if (state.prevContent != null && !state.afterDeletion) {
                // Same line, nothing deleted: recover original whitespace from source.
                sb.append(state.source, state.prevContent.getStopIndex() + 1, tok.getStartIndex());
            } else if (state.prevContent != null) {
                // After a deletion on the same line: use normalised spacing.
                if (needsSpaceBefore(state.prevContent, tok)) sb.append(' ');
            }
            sb.append(tok.getText());
            state.prevContent = tok;
            state.afterDeletion = false;
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                emitNode(node.getChild(i), matched, replacement, sb, state);
            }
        }
    }

    /** Returns {@code true} when a space should be inserted between two adjacent tokens. */
    private static boolean needsSpaceBefore(Token prev, Token next) {
        String n = next.getText();
        if (")".equals(n) || "]".equals(n) || "}".equals(n) ||
            ",".equals(n) || ";".equals(n) || ".".equals(n) || ":".equals(n)) return false;
        String p = prev.getText();
        if ("(".equals(p) || "[".equals(p) || "{".equals(p) ||
            ".".equals(p) || "@".equals(p)) return false;
        return true;
    }

    /** Mutable state threaded through the depth-first tree walk. */
    private static final class EmitState {
        final String source;
        Token prevContent = null;   // last non-structural token emitted
        boolean afterDeletion = false;
        boolean atLineStart = true;
        String currentIndent = "";
        final Deque<String> indentStack = new ArrayDeque<>();

        EmitState(String source) { this.source = source; }
    }

    private String readSource(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private ParseResult parse(String source) {
        CharStream input = CharStreams.fromString(source);
        Python3Lexer lexer = new Python3Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Python3Parser parser = new Python3Parser(tokens);
        ParseTree tree = parser.file_input();
        return new ParseResult(tree, parser);
    }

    private record ParseResult(ParseTree tree, Python3Parser parser) {}
}
