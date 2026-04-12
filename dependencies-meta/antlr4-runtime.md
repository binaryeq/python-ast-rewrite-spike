# ANTLR4 Runtime

## Decision

ANTLR4 is the industry-standard parser generator and runtime for Java. It is maintained by Terence Parr (the original author) with a large contributor base and is widely used in production compilers and tooling. The runtime provides the `CharStream`, `Lexer`, `Parser`, `ParseTree`, and `XPath` APIs used in this project.

## Metadata

| Field | Value |
|-------|-------|
| GAV | `org.antlr:antlr4-runtime:4.13.2` |
| purl | `pkg:maven/org.antlr/antlr4-runtime@4.13.2` |
| Maven Central | https://central.sonatype.com/artifact/org.antlr/antlr4-runtime/4.13.2 |
| License | BSD 3-Clause |
| Source | https://github.com/antlr/antlr4 |

## Python3 grammar

The `Python3Lexer.g4` and `Python3Parser.g4` grammars are taken from the ANTLR grammars-v4 community repository and adapted minimally (shebang rule added). The Java base classes `Python3LexerBase` and `Python3ParserBase` are likewise sourced from the same repository.

| Field | Value |
|-------|-------|
| Source | https://github.com/antlr/grammars-v4/tree/master/python/python3 |
| Author | Bart Kiers |
| License | MIT |

## Build-time tool

The Maven plugin `org.antlr:antlr4-maven-plugin:4.13.2` is also required to generate lexer and parser Java sources from `.g4` grammar files during `generate-sources`.

| Field | Value |
|-------|-------|
| GAV | `org.antlr:antlr4-maven-plugin:4.13.2` |
| purl | `pkg:maven/org.antlr/antlr4-maven-plugin@4.13.2` |
| License | BSD 3-Clause |
