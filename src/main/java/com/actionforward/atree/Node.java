package com.actionforward.atree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A node of the A-Tree index (paper Sec. 4.1). A leaf node (l-node) corresponds to a predicate
 * and an inner node (i-node) to a subexpression; any node holding subscribers additionally acts
 * as a root node (r-node). Nodes are shared: the same predicate or subexpression is represented
 * by exactly one node, referenced by any number of parents.
 */
final class Node {

    final long id;
    /** PREDICATE, AND or OR — negations are compiled away by the zero suppression filter. */
    final Expr.Kind kind;
    final Predicate predicate; // only for leaves

    final List<Node> children = new ArrayList<>();
    final Set<Long> childIds = new LinkedHashSet<>();
    final List<Node> parents = new ArrayList<>();
    /** Subscription keys for which this node is the expression root. */
    final Set<Object> subscribers = new LinkedHashSet<>();

    /**
     * Propagation on demand (paper Sec. 5.2.2): for an AND node, the only child whose {@code true}
     * result is propagated upward to it; the results of the other children are read lazily.
     */
    Node accessChild;

    /** Distance to the farthest leaf in this node's subtrees; leaves are at level 1. */
    int level;

    /** Number of direct references: parent links plus subscriptions (paper Sec. 4.2.3/4.2.4). */
    int useCount;

    Node(long id, Predicate predicate) {
        this.id = id;
        this.kind = Expr.Kind.PREDICATE;
        this.predicate = predicate;
        this.level = 1;
    }

    Node(long id, Expr.Kind kind) {
        this.id = id;
        this.kind = kind;
        this.predicate = null;
    }
}
