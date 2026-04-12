# python-grammar-spike

Parses Python source files using ANTLR4 (Python3 grammar from grammars-v4), removes all AST nodes matching an ANTLR4 XPath expression or replacing them with some custom text, and writes the result to an output file.

## Requirements

- Java 17
- Maven 3.6+

## Build

```
mvn package
```

## Run tests

```
mvn test
```

---


### XPath expression examples

| Expression | What is removed or replaced      |
|------------|----------------------------------|
| `//shebang` | The `#!/...` interpreter directive on line 1 |
| `//funcdef` | All function definitions         |
| `//classdef` | All class definitions           |
| `//import_stmt` | All import statements        |
| `//comment` | All comments                     |

