package com.actionforward.atree;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable AST of an arbitrary Boolean expression (ABE, paper Sec. 3.1): a Boolean function
 * over {@link Predicate}s combined with {@code and}, {@code or}, {@code not}, {@code xor} and
 * {@code xnor}. Build expressions with the static factories, or parse them from text with
 * {@link com.actionforward.atree.parser.ExpressionParser}.
 */
public final class Expr {

    public enum Kind { PREDICATE, AND, OR, NOT, XOR, XNOR }

    private final Kind kind;
    private final Predicate predicate;
    private final List<Expr> children;

    private Expr(Kind kind, Predicate predicate, List<Expr> children) {
        this.kind = kind;
        this.predicate = predicate;
        this.children = children;
    }

    public static Expr of(Predicate predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new Expr(Kind.PREDICATE, predicate, List.of());
    }

    public static Expr and(Expr... children) {
        return and(List.of(children));
    }

    public static Expr and(List<Expr> children) {
        return nary(Kind.AND, children);
    }

    public static Expr or(Expr... children) {
        return or(List.of(children));
    }

    public static Expr or(List<Expr> children) {
        return nary(Kind.OR, children);
    }

    public static Expr not(Expr child) {
        Objects.requireNonNull(child, "child");
        return new Expr(Kind.NOT, null, List.of(child));
    }

    public static Expr xor(Expr a, Expr b) {
        return new Expr(Kind.XOR, null, List.of(a, b));
    }

    public static Expr xnor(Expr a, Expr b) {
        return new Expr(Kind.XNOR, null, List.of(a, b));
    }

    private static Expr nary(Kind kind, List<Expr> children) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException(kind + " requires at least one child");
        }
        return new Expr(kind, null, List.copyOf(children));
    }

    public Kind kind() {
        return kind;
    }

    /** The predicate of a {@link Kind#PREDICATE} node, null otherwise. */
    public Predicate predicate() {
        return predicate;
    }

    public List<Expr> children() {
        return children;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case PREDICATE -> predicate.toString();
            case NOT -> "not(" + children.get(0) + ")";
            default -> {
                StringJoiner joiner = new StringJoiner(", ", kind.name().toLowerCase(Locale.ROOT) + "(", ")");
                for (Expr child : children) {
                    joiner.add(child.toString());
                }
                yield joiner.toString();
            }
        };
    }
}
