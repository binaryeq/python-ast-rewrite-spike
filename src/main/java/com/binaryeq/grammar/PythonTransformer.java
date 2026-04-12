package com.binaryeq.grammar;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.xpath.XPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Parses a Python source file and applies a sequence of
 * {@link Transformation} steps to it.
 *
 * <p>Each step re-parses the source produced by the previous step, so later
 * steps can match nodes introduced or modified by earlier ones. Within a
 * single step, matched nodes are replaced right-to-left so earlier character
 * positions stay valid after each substitution.
 *
 * <p>Using an empty replacement string (the default for
 * {@link Transformation#delete}) removes matched character ranges entirely.
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

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String applyStep(String source, Transformation t) {
        ParseResult parsed = parse(source);
        Collection<ParseTree> matches = XPath.findAll(parsed.tree, t.xpathExpr(), parsed.parser);
        LOG.info("  {} match(es)", matches.size());

        List<ParseTree> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingInt(PythonTransformer::startIndex).reversed());

        StringBuilder sb = new StringBuilder(source);
        for (ParseTree match : sorted) {
            int start = startIndex(match);
            int stop  = stopIndex(match);
            if (start >= 0 && stop >= start && stop < sb.length()) {
                sb.replace(start, stop + 1, t.replacement());
            }
        }
        return sb.toString();
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

    private static int startIndex(ParseTree node) {
        if (node instanceof ParserRuleContext ctx) {
            return ctx.start != null ? ctx.start.getStartIndex() : -1;
        }
        if (node instanceof TerminalNode tn) {
            return tn.getSymbol().getStartIndex();
        }
        return -1;
    }

    private static int stopIndex(ParseTree node) {
        if (node instanceof ParserRuleContext ctx) {
            if (ctx.stop != null) return ctx.stop.getStopIndex();
            return ctx.start != null ? ctx.start.getStopIndex() : -1;
        }
        if (node instanceof TerminalNode tn) {
            return tn.getSymbol().getStopIndex();
        }
        return -1;
    }

    private record ParseResult(ParseTree tree, Python3Parser parser) {}
}
