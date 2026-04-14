# python-grammar-spike

Parses Python source files using ANTLR4 (Python3 grammar from grammars-v4), removes all AST nodes matching an ANTLR4 XPath expression or replacing them with some custom text, and writes the result to an output file.

## Build and Test

Requirements:

- Java 17+
- Maven 3.6+

## Build

- build: `mvn package`
- test: `mvn test`

## Usage

### API

```java
String PythonTransformer.transform(Path inputPath, String xpathExpr, String replacement)
```

Read a python file from `inputPath`, parses it into an AST, locates the nodes described by the `xpathExpr` query, 
and creates python sources by replacing the text corresponding to those nodes by `replacement` text.
If `replacement` is empty, then the text will just be removed.

There is also an API function to apply multiple transformations (multiple query+replacement pairs).

### Examples

- `transform(input, "//shebang", "")` -- removes the shebang line defining the python interpreter. 
- `transform(input, "//comment", "")`-- removes all comments


### Limitations

`antlr` does not support full xpath syntax. 
In particular, it does not support queries with node conditions, such as `//comment[contains(@text, 'TODO')]`.

### Related Work

- [pmd](https://pmd.github.io/) also uses xpath to query ASTs (Java, JS) and define antipatterns corresponding to bugs. The advantage of using antlr is that it has a grammar library supporting most mainstream languages
- [treesitter](https://tree-sitter.github.io/tree-sitter/using-parsers/queries/index.html) has a [LISP-like query language](https://tree-sitter.github.io/tree-sitter/using-parsers/queries/index.html)
- [semgrep](https://semgrep.dev/docs/writing-rules/overview) has a custom rule syntax to query syntax trees
- 