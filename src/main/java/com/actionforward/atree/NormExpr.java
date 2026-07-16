package com.actionforward.atree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An expression normalized by the zero suppression filter (paper Sec. 5.2.1): {@code not} is
 * pushed onto the predicates via De Morgan's laws, {@code xor}/{@code xnor} are expanded, so
 * only {@code and}/{@code or} over (possibly complemented) predicates remain. Nested children
 * with the same operator are flattened and duplicate children removed.
 *
 * <p>Each node carries the order-insensitive ID of paper Sec. 4.2.1: {@code and} combines child
 * IDs with (wrapping) addition and {@code or} with multiplication. Both operations being
 * associative and commutative, the ID is invariant under child reordering and under regrouping
 * of same-operator children — the property that lets reorganization and self-adjustment restructure
 * the index without changing identities.
 */
final class NormExpr {

    final Expr.Kind kind; // PREDICATE, AND or OR
    final Predicate predicate; // only for PREDICATE
    final long id;
    /** Mutated by {@code ATree.reorganize}; null for leaves and for references to existing nodes. */
    List<NormExpr> children;

    private NormExpr(Expr.Kind kind, Predicate predicate, List<NormExpr> children, long id) {
        this.kind = kind;
        this.predicate = predicate;
        this.children = children;
        this.id = id;
    }

    static NormExpr normalize(Expr expr) {
        return norm(expr, false);
    }

    /** A stand-in for an existing index node, produced by expression reorganization. */
    static NormExpr ref(Node node) {
        return new NormExpr(node.kind, node.predicate, null, node.id);
    }

    private static NormExpr norm(Expr expr, boolean negated) {
        return switch (expr.kind()) {
            case PREDICATE -> leaf(negated ? expr.predicate().negate() : expr.predicate());
            case NOT -> norm(expr.children().get(0), !negated);
            case AND -> inner(negated ? Expr.Kind.OR : Expr.Kind.AND, normAll(expr, negated));
            case OR -> inner(negated ? Expr.Kind.AND : Expr.Kind.OR, normAll(expr, negated));
            case XOR, XNOR -> {
                Expr a = expr.children().get(0);
                Expr b = expr.children().get(1);
                // xor  == (a ∧ ¬b) ∨ (¬a ∧ b);  xnor == (a ∧ b) ∨ (¬a ∧ ¬b)
                boolean same = (expr.kind() == Expr.Kind.XNOR) != negated;
                NormExpr left = inner(Expr.Kind.AND, List.of(norm(a, false), norm(b, !same)));
                NormExpr right = inner(Expr.Kind.AND, List.of(norm(a, true), norm(b, same)));
                yield inner(Expr.Kind.OR, List.of(left, right));
            }
        };
    }

    private static List<NormExpr> normAll(Expr expr, boolean negated) {
        List<NormExpr> result = new ArrayList<>(expr.children().size());
        for (Expr child : expr.children()) {
            result.add(norm(child, negated));
        }
        return result;
    }

    private static NormExpr leaf(Predicate predicate) {
        return new NormExpr(Expr.Kind.PREDICATE, predicate, null, predicate.id());
    }

    private static NormExpr inner(Expr.Kind kind, List<NormExpr> children) {
        // Flatten same-operator children (associativity) and drop duplicates (idempotence).
        Map<Long, NormExpr> unique = new LinkedHashMap<>();
        for (NormExpr child : children) {
            if (child.kind == kind && child.children != null) {
                for (NormExpr grandChild : child.children) {
                    unique.putIfAbsent(grandChild.id, grandChild);
                }
            } else {
                unique.putIfAbsent(child.id, child);
            }
        }
        if (unique.size() == 1) {
            return unique.values().iterator().next();
        }
        List<NormExpr> flat = new ArrayList<>(unique.values());
        return new NormExpr(kind, null, flat, combineIds(kind, flat));
    }

    private static long combineIds(Expr.Kind kind, List<NormExpr> children) {
        long acc = kind == Expr.Kind.AND ? 0L : 1L;
        for (NormExpr child : children) {
            acc = kind == Expr.Kind.AND ? acc + child.id : acc * child.id;
        }
        return acc;
    }
}
