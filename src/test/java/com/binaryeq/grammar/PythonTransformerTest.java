package com.binaryeq.grammar;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonTransformerTest {

    private final PythonTransformer transformer = new PythonTransformer();

    // -------------------------------------------------------------------------
    // Single-step delete
    // -------------------------------------------------------------------------

    /**
     * The shebang line (#!/usr/bin/env python3) is removed by the XPath
     * expression //shebang. The newline that follows is a sibling of the
     * shebang node in file_input; because the output is compacted, that
     * newline is suppressed and the file begins directly with the next
     * statement.
     */
    @Test
    void removeShebang() throws Exception {
        String result = transform("shebang_input.py", "//shebang");
        assertEquals(readOracle("shebang_expected.txt"), result);
    }

    /**
     * When the XPath expression matches nothing the output is identical to the
     * input source.
     */
    @Test
    void noMatchLeavesFileUnchanged() throws Exception {
        String result = transform("no_match_input.py", "//shebang");
        assertEquals(readOracle("no_match_expected.txt"), result);
    }

    /**
     * An import_stmt node (e.g. "import os") is removed. The NEWLINE that
     * closes the enclosing simple_stmts production is outside the import_stmt
     * span; because the output is compacted, that newline is suppressed and
     * the file begins directly with the next statement.
     */
    @Test
    void removeImportStatement() throws Exception {
        String result = transform("import_input.py", "//import_stmt");
        assertEquals(readOracle("import_expected.txt"), result);
    }

    /**
     * An entire if/else statement is removed by the //if_stmt XPath
     * expression. The surrounding assignments (x = 1 and y = 2) are
     * preserved on adjacent lines.
     */
    @Test
    void removeIfStatement() throws Exception {
        String result = transform("if_stmt_input.py", "//if_stmt");
        assertEquals(readOracle("if_stmt_expected.txt"), result);
    }

    // -------------------------------------------------------------------------
    // Single-step replace
    // -------------------------------------------------------------------------

    /**
     * The shebang token is replaced with a different interpreter path. Because
     * the SHEBANG token does not include the line-terminating newline, the
     * replacement is inserted cleanly and the rest of the file follows on the
     * next line as before.
     */
    @Test
    void replaceShebang() throws Exception {
        String result = transform("shebang_input.py", "//shebang", "#!/usr/bin/env python2");
        assertEquals(readOracle("replace_shebang_expected.txt"), result);
    }

    /**
     * An import_stmt is swapped for a different one. Like the shebang, the
     * import_stmt character range does not include the trailing newline
     * (which belongs to the enclosing simple_stmts production), so no extra
     * blank line is produced at the replacement site.
     */
    @Test
    void replaceImportStatement() throws Exception {
        String result = transform("import_input.py", "//import_stmt", "import sys");
        assertEquals(readOracle("replace_import_expected.txt"), result);
    }

    // -------------------------------------------------------------------------
    // Multi-step transformations
    // -------------------------------------------------------------------------

    /**
     * Two independent delete steps are applied sequentially to a file that
     * contains both a shebang and an import statement. Each step re-parses the
     * result of the previous one, so character positions are always correct.
     */
    @Test
    void multiStepDeleteAll() throws Exception {
        List<Transformation> steps = List.of(
                Transformation.delete("//shebang"),
                Transformation.delete("//import_stmt")
        );
        String result = transformer.transform(resourcePath("multi_step_input.py"), steps);
        assertEquals(readOracle("multi_step_delete_expected.txt"), result);
    }

    /**
     * Two independent replace steps are applied sequentially: the shebang is
     * updated to python2 and the import is changed to "import sys".
     */
    @Test
    void multiStepReplaceAll() throws Exception {
        List<Transformation> steps = List.of(
                Transformation.replace("//shebang", "#!/usr/bin/env python2"),
                Transformation.replace("//import_stmt", "import sys")
        );
        String result = transformer.transform(resourcePath("multi_step_input.py"), steps);
        assertEquals(readOracle("multi_step_replace_expected.txt"), result);
    }

    /**
     * Demonstrates that transformations are truly sequential: step 1 replaces
     * the shebang with "import os", producing a new import_stmt node that did
     * not exist in the original source. Step 2 then deletes //import_stmt,
     * which matches the node introduced by step 1. A single-pass approach
     * would leave "import os" in the output.
     */
    @Test
    void multiStepOrderDependent() throws Exception {
        List<Transformation> steps = List.of(
                Transformation.replace("//shebang", "import os"),
                Transformation.delete("//import_stmt")
        );
        String result = transformer.transform(resourcePath("order_matters_input.py"), steps);
        assertEquals(readOracle("order_matters_expected.txt"), result);
    }

    /**
     * All # comments (standalone lines, inline trailing comments, consecutive
     * comment blocks) are removed by the //comment XPath expression. Because
     * the output is compacted, lines that held only a comment produce no blank
     * line, and inline comments are stripped without leaving trailing
     * whitespace. All blank lines in the original source are also removed.
     */
    @Test
    void removeAllComments() throws Exception {
        String result = transform("comments_input.py", "//comment");
        assertEquals(readOracle("comments_expected.txt"), result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String transform(String inputResource, String xpath) throws Exception {
        return transformer.transform(resourcePath(inputResource), xpath);
    }

    private String transform(String inputResource, String xpath, String replacement)
            throws Exception {
        return transformer.transform(resourcePath(inputResource), xpath, replacement);
    }

    private Path resourcePath(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(name).toURI());
    }

    private String readOracle(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
