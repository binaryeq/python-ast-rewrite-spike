package com.binaryeq.grammar;

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 by Bart Kiers
 *
 * Source: https://github.com/antlr/grammars-v4/tree/master/python/python3/Java
 *
 * Provides semantic predicates used in pattern-matching rules to prevent
 * ambiguous parses of signed numbers and dotted names.
 */

import org.antlr.v4.runtime.*;

public abstract class Python3ParserBase extends Parser {

    protected Python3ParserBase(TokenStream input) {
        super(input);
    }

    public boolean CannotBePlusMinus() {
        return true;
    }

    public boolean CannotBeDotLpEq() {
        return true;
    }
}
