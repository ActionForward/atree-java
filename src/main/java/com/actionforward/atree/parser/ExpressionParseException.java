package com.actionforward.atree.parser;

import java.util.List;

/** Thrown when an expression cannot be parsed; carries every syntax error found. */
public class ExpressionParseException extends IllegalArgumentException {

    private final List<String> errors;

    ExpressionParseException(String input, List<String> errors) {
        super("cannot parse \"" + input + "\": " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
