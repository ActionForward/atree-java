package com.actionforward.atree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An atomic condition over a single named attribute: {@code ⟨attr, op, values⟩} (paper Sec. 3.1).
 * Attribute names are chosen freely by the library user; at matching time they are resolved
 * against the attributes of an {@link Event}, either statically bound or computed by a
 * {@link java.util.function.Supplier}.
 *
 * <p>Numeric values are compared numerically regardless of their concrete {@link Number} type;
 * other values must be of the same class and {@link Comparable} to satisfy an ordering operator.
 * The complement operators ({@link Op#NE}, {@link Op#GE}, …) accept exactly the values their
 * counterpart rejects, so pushing a negation into a predicate preserves semantics.
 */
public final class Predicate {

    private final String attr;
    private final Op op;
    private final List<Object> values;
    private final String literal;
    private final long id;

    private Predicate(String attr, Op op, List<Object> values) {
        this.attr = Objects.requireNonNull(attr, "attr");
        this.op = Objects.requireNonNull(op, "op");
        for (Object v : values) {
            Objects.requireNonNull(v, "predicate value");
        }
        switch (op) {
            case LT, LE, GT, GE, EQ, NE -> require(values.size() == 1, op + " requires exactly one value");
            case BETWEEN, NOT_BETWEEN -> require(values.size() == 2, op + " requires exactly two values");
            case IN, NOT_IN -> require(!values.isEmpty(), op + " requires at least one value");
        }
        this.values = List.copyOf(values);
        this.literal = buildLiteral();
        // Force the ID odd so that OR combination (multiplication, see NormExpr) keeps entropy.
        this.id = fnv64(literal) | 1L;
    }

    public static Predicate of(String attr, Op op, Object... values) {
        return new Predicate(attr, op, List.of(values));
    }

    public String attr() {
        return attr;
    }

    public Op op() {
        return op;
    }

    public List<Object> values() {
        return values;
    }

    /** Unique ID of this predicate, derived from its canonical literal (paper Sec. 4.2.1). */
    long id() {
        return id;
    }

    /** The predicate accepting exactly the values this one rejects (zero suppression filter). */
    public Predicate negate() {
        return new Predicate(attr, op.negate(), values);
    }

    /** Evaluates this predicate against a bound attribute value (never null). */
    public boolean evaluate(Object value) {
        return switch (op) {
            case EQ -> eq(value, values.get(0));
            case NE -> !eq(value, values.get(0));
            case LT -> lessThan(value, values.get(0));
            case GE -> !lessThan(value, values.get(0));
            case LE -> lessOrEqual(value, values.get(0));
            case GT -> !lessOrEqual(value, values.get(0));
            case IN -> contains(value);
            case NOT_IN -> !contains(value);
            case BETWEEN -> between(value);
            case NOT_BETWEEN -> !between(value);
        };
    }

    private boolean lessThan(Object a, Object b) {
        Integer c = cmp(a, b);
        return c != null && c < 0;
    }

    private boolean lessOrEqual(Object a, Object b) {
        Integer c = cmp(a, b);
        return c != null && c <= 0;
    }

    private boolean contains(Object value) {
        for (Object candidate : values) {
            if (eq(value, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean between(Object value) {
        Integer lo = cmp(value, values.get(0));
        Integer hi = cmp(value, values.get(1));
        return lo != null && lo >= 0 && hi != null && hi <= 0;
    }

    private static boolean eq(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        }
        return a.equals(b);
    }

    /** Returns null when the two values are not comparable (different non-numeric types). */
    @SuppressWarnings("unchecked")
    private static Integer cmp(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable<?>) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return null;
    }

    private String buildLiteral() {
        List<String> canon = new ArrayList<>(values.size());
        for (Object v : values) {
            canon.add(canonical(v));
        }
        if (op == Op.IN || op == Op.NOT_IN) {
            canon.sort(null); // membership is order-insensitive
        }
        return attr + "|" + op.name() + "|" + String.join(",", canon);
    }

    private static String canonical(Object v) {
        if (v instanceof Number n) {
            return "n:" + n.doubleValue();
        }
        return v.getClass().getName() + ":" + v;
    }

    private static long fnv64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Predicate p && literal.equals(p.literal);
    }

    @Override
    public int hashCode() {
        return literal.hashCode();
    }

    @Override
    public String toString() {
        String vals = values.size() == 1 ? String.valueOf(values.get(0)) : values.toString();
        return attr + " " + op.symbol() + " " + vals;
    }
}
