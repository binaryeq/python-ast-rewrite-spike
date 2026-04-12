package com.binaryeq.grammar;

/**
 * A single parse-tree transformation step: an ANTLR4 XPath expression and the
 * replacement text to insert in place of each matched character range.
 *
 * <p>Use the static factory methods for concise construction:
 * <pre>
 *   Transformation.delete("//shebang")
 *   Transformation.replace("//import_stmt", "import sys")
 * </pre>
 */
public record Transformation(String xpathExpr, String replacement) {

    /** Deletes all nodes matching {@code xpathExpr}. */
    public static Transformation delete(String xpathExpr) {
        return new Transformation(xpathExpr, "");
    }

    /** Replaces all nodes matching {@code xpathExpr} with {@code replacement}. */
    public static Transformation replace(String xpathExpr, String replacement) {
        return new Transformation(xpathExpr, replacement);
    }
}
