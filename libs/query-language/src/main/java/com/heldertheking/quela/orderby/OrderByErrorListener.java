package com.heldertheking.quela.orderby;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import java.util.BitSet;

/// Error listener for the order by parser that throws an [InternalOrderByParseException] if an error occurs because
/// the order by string is syntactically incorrect.
class OrderByErrorListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        throw new InternalOrderByParseException("Error parsing order by clause at %d: %s".formatted(charPositionInLine, msg));
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
        throw new RuntimeException("Unexpected ambiguity found in query. This is likely an issue with the language definition starting from position" + startIndex + " to " + stopIndex);
    }
}
