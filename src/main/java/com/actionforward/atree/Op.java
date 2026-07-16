package com.actionforward.atree;

/**
 * Relational operators supported by {@link Predicate}s (paper Sec. 3.1): the standard
 * relational operators, set membership, and SQL's BETWEEN, plus their complements so that
 * the zero suppression filter can push negations into predicates (paper Sec. 5.2.1).
 */
public enum Op {
    LT("<"), LE("<="), GT(">"), GE(">="), EQ("="), NE("!="),
    IN("in"), NOT_IN("not in"), BETWEEN("between"), NOT_BETWEEN("not between");

    private final String symbol;

    Op(String symbol) {
        this.symbol = symbol;
    }

    String symbol() {
        return symbol;
    }

    /** The operator accepting exactly the complement set of values. */
    public Op negate() {
        return switch (this) {
            case LT -> GE;
            case GE -> LT;
            case LE -> GT;
            case GT -> LE;
            case EQ -> NE;
            case NE -> EQ;
            case IN -> NOT_IN;
            case NOT_IN -> IN;
            case BETWEEN -> NOT_BETWEEN;
            case NOT_BETWEEN -> BETWEEN;
        };
    }
}
