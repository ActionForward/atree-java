package com.actionforward.atree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A-Tree: a dynamic, multi-rooted index over arbitrary Boolean expressions, after
 * <em>Ji &amp; Jacobsen, "A-Tree: A Dynamic Data Structure for Efficiently Indexing Arbitrary
 * Boolean Expressions", SIGMOD 2021</em>. Expressions sharing predicates or subexpressions
 * share the corresponding nodes; {@link #match(Event)} traverses the index bottom-to-top and
 * returns the keys of every subscribed expression the event satisfies.
 *
 * <p>Implemented per the paper, with the two matching optimizations always enabled:
 * <ul>
 *   <li><b>Node uniqueness</b> (Sec. 4.2.1) — an expression-to-node table keyed by an
 *       order-insensitive ID ({@code and} → addition, {@code or} → multiplication of child IDs)
 *       guarantees one node per distinct predicate/subexpression.</li>
 *   <li><b>Expression reorganization</b> (Alg. 2) — an incoming expression is regrouped by a
 *       greedy set cover so it reuses as many existing subexpression nodes as possible.</li>
 *   <li><b>Index self-adjustment</b> (Alg. 3) — existing nodes whose children are a superset of
 *       a newly created node's children are rewired to consume it as a child, keeping the index
 *       shape independent of insertion order.</li>
 *   <li><b>Zero suppression filter</b> (Sec. 5.2.1) — applied at insertion: negations are pushed
 *       into predicate operators and {@code xor}/{@code xnor} expanded, so matching only ever
 *       propagates {@code true} results.</li>
 *   <li><b>Propagation on demand</b> (Sec. 5.2.2) — an AND node is only triggered through one
 *       designated access child (here: its first child; the paper picks randomly); the results of
 *       its other children are recorded in a per-match visited set and read lazily.</li>
 * </ul>
 *
 * <p>Deviations from the paper: {@code useCount} counts parent links as well as subscriptions,
 * so deletion (Alg. 5) never needs the child-splicing fallback; IDs use wrapping 64-bit
 * arithmetic, so distinct expressions could theoretically collide (inherent to the paper's
 * arithmetic ID scheme as well).
 *
 * <p>Thread-safe. {@link #match(Event)} takes a shared read lock and keeps all of its per-event
 * traversal state (queued/visited nodes) local to the call, so any number of matches may run
 * concurrently without interfering with each other. {@link #subscribe} and {@link #unsubscribe}
 * mutate the index under an exclusive write lock and never overlap a match or one another. A
 * single {@link Event} instance is still not safe to pass to concurrent {@code match} calls,
 * per its own documented contract (supplier memoization is unsynchronized).
 *
 * @param <S> the type of the subscription keys returned by {@link #match(Event)}
 */
public final class ATree<S> {

    /** Expression-to-node hash table, "Hen" in the paper. */
    private final Map<Long, Node> nodes = new HashMap<>();
    /** Leaf nodes indexed by attribute name, for the predicate matching phase. */
    private final Map<String, Set<Node>> leavesByAttr = new HashMap<>();
    private final Map<S, Node> subscriptions = new LinkedHashMap<>();
    /** Live node count per level, so {@link #maxLevel} can shrink back down as nodes are released. */
    private final TreeMap<Integer, Integer> nodesPerLevel = new TreeMap<>();
    /** Guards all index state: shared for {@link #match}, exclusive for subscribe/unsubscribe. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private int maxLevel;

    /**
     * Indexes an expression under the given key. The same expression (up to operand order and
     * grouping) may be subscribed under several keys; it is stored once.
     *
     * @throws IllegalArgumentException if the key is already subscribed
     */
    public void subscribe(S key, Expr expression) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expression, "expression");
        lock.writeLock().lock();
        try {
            if (subscriptions.containsKey(key)) {
                throw new IllegalArgumentException("already subscribed: " + key);
            }
            Node root = insert(NormExpr.normalize(expression));
            root.subscribers.add(key);
            subscriptions.put(key, root);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a subscription; nodes no longer referenced by any expression are discarded
     * (paper Alg. 5). Returns false if the key was not subscribed.
     */
    public boolean unsubscribe(S key) {
        lock.writeLock().lock();
        try {
            Node root = subscriptions.remove(key);
            if (root == null) {
                return false;
            }
            root.subscribers.remove(key);
            release(root);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the keys of all subscribed expressions satisfied by the event (paper Alg. 6).
     * A predicate whose attribute is absent from the event evaluates to {@code undefined},
     * which the zero suppression filter treats as unsatisfied.
     */
    @SuppressWarnings("unchecked")
    public Set<S> match(Event event) {
        lock.readLock().lock();
        try {
            List<ArrayDeque<Node>> queues = new ArrayList<>(maxLevel + 1);
            for (int i = 0; i <= maxLevel; i++) {
                queues.add(new ArrayDeque<>());
            }
            // Per-match traversal state, local to this call so concurrent matches never share it.
            Set<Node> queued = new HashSet<>();
            Set<Node> trueNodes = new HashSet<>();

            // Predicate matching phase: suppliers are only consulted for attributes the index knows.
            for (String attr : event.attributes()) {
                Set<Node> leaves = leavesByAttr.get(attr);
                if (leaves == null) {
                    continue;
                }
                Object value = event.value(attr);
                if (value == null) {
                    continue;
                }
                for (Node leaf : leaves) {
                    if (leaf.predicate.evaluate(value)) {
                        queued.add(leaf);
                        queues.get(1).add(leaf);
                    }
                }
            }

            // Expression matching phase: level-ordered bottom-to-top traversal.
            Set<S> matches = new LinkedHashSet<>();
            for (int level = 1; level < queues.size(); level++) {
                ArrayDeque<Node> queue = queues.get(level);
                while (!queue.isEmpty()) {
                    Node node = queue.poll();
                    if (node.kind == Expr.Kind.AND && !allChildrenTrue(node, trueNodes)) {
                        continue; // triggered by its access child, but some sibling result is missing
                    }
                    // Leaves are only queued when satisfied; an OR is queued by a true child.
                    trueNodes.add(node);
                    for (Object subscriber : node.subscribers) {
                        matches.add((S) subscriber);
                    }
                    for (Node parent : node.parents) {
                        if (!queued.contains(parent)
                                && (parent.kind == Expr.Kind.OR || parent.accessChild == node)) {
                            queued.add(parent);
                            queues.get(parent.level).add(parent);
                        }
                    }
                }
            }
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static boolean allChildrenTrue(Node node, Set<Node> trueNodes) {
        for (Node child : node.children) {
            if (!trueNodes.contains(child)) {
                return false;
            }
        }
        return true;
    }

    /** Number of live index nodes (shared predicates/subexpressions count once). */
    public int nodeCount() {
        lock.readLock().lock();
        try {
            return nodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Number of active subscriptions. */
    public int size() {
        lock.readLock().lock();
        try {
            return subscriptions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Recursive insertion (paper Alg. 4): reuse the existing node when the (sub)expression is
     * already indexed, otherwise reorganize it against the current index, insert its children,
     * create the node and self-adjust the index around it.
     */
    private Node insert(NormExpr expr) {
        Node existing = nodes.get(expr.id);
        if (existing != null) {
            existing.useCount++;
            return existing;
        }
        Node node;
        if (expr.kind == Expr.Kind.PREDICATE) {
            if (expr.predicate == null) {
                throw new IllegalStateException("dangling reference to node " + expr.id);
            }
            node = new Node(expr.id, expr.predicate);
            leavesByAttr.computeIfAbsent(expr.predicate.attr(), k -> new LinkedHashSet<>()).add(node);
        } else {
            reorganize(expr);
            node = new Node(expr.id, expr.kind);
            List<Node> children = new ArrayList<>(expr.children.size());
            for (NormExpr childExpr : expr.children) {
                children.add(insert(childExpr));
            }
            // Link only after every child is inserted: a half-built node must not become
            // visible (through its children's parent lists) to the reorganization and
            // self-adjustment passes triggered while inserting its remaining children.
            for (Node child : children) {
                if (node.childIds.add(child.id)) {
                    node.children.add(child);
                    child.parents.add(node);
                } else {
                    child.useCount--; // duplicate child collapsed, drop its extra reference
                }
            }
            if (expr.kind == Expr.Kind.AND) {
                node.accessChild = node.children.get(0);
            }
            node.level = 1 + maxChildLevel(node);
        }
        node.useCount = 1;
        trackLevel(node);
        nodes.put(node.id, node);
        if (node.kind != Expr.Kind.PREDICATE) {
            selfAdjust(node);
        }
        return node;
    }

    /**
     * Expression reorganization (paper Alg. 2): greedy set cover of the expression's children by
     * existing same-operator nodes, so that shared subexpressions are reused. Covered children are
     * replaced by references to the covering nodes; the expression ID is unchanged by regrouping.
     */
    private void reorganize(NormExpr expr) {
        Map<Long, NormExpr> remaining = new LinkedHashMap<>();
        for (NormExpr child : expr.children) {
            remaining.put(child.id, child);
        }
        // Candidate covers: existing nodes with the same operator whose children all appear here.
        Map<Long, Node> candidates = new LinkedHashMap<>();
        for (NormExpr child : expr.children) {
            Node childNode = nodes.get(child.id);
            if (childNode == null) {
                continue;
            }
            for (Node parent : childNode.parents) {
                if (parent.kind == expr.kind
                        && nodes.get(parent.id) == parent // only live, fully built nodes
                        && remaining.keySet().containsAll(parent.childIds)) {
                    candidates.putIfAbsent(parent.id, parent);
                }
            }
        }
        List<NormExpr> regrouped = new ArrayList<>();
        while (!candidates.isEmpty()) {
            Node best = null;
            for (Node candidate : candidates.values()) {
                if (remaining.keySet().containsAll(candidate.childIds)
                        && (best == null || candidate.childIds.size() > best.childIds.size())) {
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            remaining.keySet().removeAll(best.childIds);
            candidates.remove(best.id);
            regrouped.add(NormExpr.ref(best));
        }
        if (!regrouped.isEmpty()) {
            regrouped.addAll(remaining.values());
            expr.children = regrouped;
        }
    }

    /**
     * Index self-adjustment (paper Alg. 3): every existing same-operator node whose children are
     * a proper superset of the new node's children is rewired to consume the new node as a child,
     * so the final index shape does not depend on expression arrival order.
     */
    private void selfAdjust(Node newNode) {
        Set<Node> candidates = new LinkedHashSet<>();
        for (Node child : newNode.children) {
            for (Node parent : child.parents) {
                if (parent != newNode
                        && parent.kind == newNode.kind
                        && parent.childIds.size() > newNode.childIds.size()
                        && parent.childIds.containsAll(newNode.childIds)) {
                    candidates.add(parent);
                }
            }
        }
        for (Node parent : candidates) {
            boolean accessChildMoved = false;
            for (Node child : newNode.children) {
                if (parent.childIds.remove(child.id)) {
                    parent.children.remove(child);
                    child.parents.remove(parent);
                    child.useCount--; // stays >= 1: newNode still links to it
                    accessChildMoved |= parent.accessChild == child;
                }
            }
            parent.children.add(newNode);
            parent.childIds.add(newNode.id);
            newNode.parents.add(parent);
            newNode.useCount++;
            if (accessChildMoved) {
                parent.accessChild = newNode;
            }
            updateLevel(parent);
        }
    }

    private void updateLevel(Node node) {
        int level = 1 + maxChildLevel(node);
        if (level != node.level) {
            untrackLevel(node);
            node.level = level;
            trackLevel(node);
            for (Node parent : node.parents) {
                updateLevel(parent);
            }
        }
    }

    private static int maxChildLevel(Node node) {
        int max = 0;
        for (Node child : node.children) {
            max = Math.max(max, child.level);
        }
        return max;
    }

    /** Records a node at its current level and grows {@link #maxLevel} if needed. */
    private void trackLevel(Node node) {
        nodesPerLevel.merge(node.level, 1, Integer::sum);
        maxLevel = Math.max(maxLevel, node.level);
    }

    /** Forgets a node at its current level, shrinking {@link #maxLevel} once its level empties out. */
    private void untrackLevel(Node node) {
        nodesPerLevel.merge(node.level, -1, (a, b) -> a + b == 0 ? null : a + b);
        if (node.level == maxLevel && !nodesPerLevel.containsKey(maxLevel)) {
            maxLevel = nodesPerLevel.isEmpty() ? 0 : nodesPerLevel.lastKey();
        }
    }

    /**
     * Drops one reference to the node (paper Alg. 5); once unreferenced, the node is removed
     * from the index and its child references released recursively.
     */
    private void release(Node node) {
        node.useCount--;
        if (node.useCount > 0) {
            return;
        }
        nodes.remove(node.id);
        untrackLevel(node);
        if (node.kind == Expr.Kind.PREDICATE) {
            Set<Node> leaves = leavesByAttr.get(node.predicate.attr());
            leaves.remove(node);
            if (leaves.isEmpty()) {
                leavesByAttr.remove(node.predicate.attr());
            }
        }
        for (Node child : node.children) {
            child.parents.remove(node);
            release(child);
        }
        node.children.clear();
        node.childIds.clear();
    }
}
