package com.actionforward.atree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ATreeTests {

    // Pi <=> "ai = 1", the predicates of the paper's running example.
    private static Expr p(int i) {
        return Expr.of(Predicate.of("a" + i, Op.EQ, 1));
    }

    /** An event binding a1..a8; the attributes listed in {@code trueIdx} get 1, the others 0. */
    private static Event allAttrs(int... trueIdx) {
        Event.Builder builder = Event.builder();
        for (int i = 1; i <= 8; i++) {
            int value = 0;
            for (int t : trueIdx) {
                if (t == i) {
                    value = 1;
                }
            }
            builder.with("a" + i, value);
        }
        return builder.build();
    }

    private static ATree<String> paperTree() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        tree.subscribe("S2", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        tree.subscribe("S3", Expr.or(p(1), p(2), p(3), p(4)));
        tree.subscribe("S4", Expr.and(Expr.or(p(1), p(2), p(3)), p(4)));
        tree.subscribe("S5", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8)))); // duplicate of S2
        tree.subscribe("S6", Expr.not(Expr.or(p(7), p(8))));
        return tree;
    }

    @Test
    void paperFigure6Example() {
        ATree<String> tree = paperTree();
        // The paper's walk-through event: P1 satisfied, P7/P8 unsatisfied (so ¬P7, ¬P8 satisfied).
        assertEquals(Set.of("S3", "S6"), tree.match(allAttrs(1)));
        // a7 = a8 = 0, so S6 = not(P7 or P8) also matches here.
        assertEquals(Set.of("S1", "S3", "S4", "S6"), tree.match(allAttrs(1, 4, 5)));
        assertEquals(Set.of("S2", "S5"), tree.match(allAttrs(5, 7)));
        assertEquals(Set.of("S6"), tree.match(allAttrs()));
    }

    @Test
    void absentAttributesAreUndefinedNotFalse() {
        ATree<String> tree = paperTree();
        // S6 = not(P7 or P8): with a7/a8 absent, ¬P7 is undefined, so S6 must NOT match.
        assertEquals(Set.of(), tree.match(Event.of()));
        assertEquals(Set.of("S3"), tree.match(Event.of("a4", 1)));
    }

    @Test
    void sharedSubexpressionsAreStoredOnce() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        assertEquals(9, tree.nodeCount()); // 6 leaves + or(1,2,3) + or(5,6) + root

        // S2 shares or(P5, P6): only P7, P8, or(7,8) and the root are new.
        tree.subscribe("S2", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        assertEquals(13, tree.nodeCount());

        // S5 is exactly S2: no new node at all.
        tree.subscribe("S5", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        assertEquals(13, tree.nodeCount());
    }

    @Test
    void operandOrderDoesNotDuplicateExpressions() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("E1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        int nodes = tree.nodeCount();
        // Same expression, different operand order (the paper's Expr1/Expr2 pair).
        tree.subscribe("E2", Expr.and(p(4), Expr.or(p(3), p(2), p(1)), Expr.or(p(6), p(5))));
        assertEquals(nodes, tree.nodeCount());
        assertEquals(Set.of("E1", "E2"), tree.match(allAttrs(1, 4, 5)));
    }

    @Test
    void reorganizationReusesExistingSubexpressions() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        // or(P1..P4) is regrouped as or(or(P1,P2,P3), P4): only the root is new (paper Sec. 4.2.2).
        tree.subscribe("S3", Expr.or(p(1), p(2), p(3), p(4)));
        assertEquals(10, tree.nodeCount());
        assertEquals(Set.of("S3"), tree.match(allAttrs(4)));
        assertEquals(Set.of("S3"), tree.match(allAttrs(2)));
    }

    @Test
    void selfAdjustmentRewiresExistingExpressions() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        // S4's node covers a subset of S1's children: S1 is rewired to consume it (paper Sec. 4.2.3).
        tree.subscribe("S4", Expr.and(Expr.or(p(1), p(2), p(3)), p(4)));
        assertEquals(10, tree.nodeCount());
        assertEquals(Set.of("S1", "S4"), tree.match(allAttrs(1, 4, 5)));
        assertEquals(Set.of("S4"), tree.match(allAttrs(1, 4)));
        assertEquals(Set.of(), tree.match(allAttrs(4)));
    }

    @Test
    void deletionKeepsSharedNodesAlive() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        tree.subscribe("S2", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        assertTrue(tree.unsubscribe("S1"));
        // S2's structure survives, including the shared or(P5, P6).
        assertEquals(7, tree.nodeCount()); // P5..P8, or(5,6), or(7,8), root
        assertEquals(Set.of("S2"), tree.match(allAttrs(5, 7)));
        assertTrue(tree.unsubscribe("S2"));
        assertEquals(0, tree.nodeCount());
        assertEquals(Set.of(), tree.match(allAttrs(5, 7)));
        assertFalse(tree.unsubscribe("S2"));
    }

    @Test
    void deletionAfterSelfAdjustment() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S1", Expr.and(Expr.or(p(1), p(2), p(3)), p(4), Expr.or(p(5), p(6))));
        tree.subscribe("S4", Expr.and(Expr.or(p(1), p(2), p(3)), p(4)));
        // S4's node is still referenced as a child of S1's root after unsubscribing S4.
        assertTrue(tree.unsubscribe("S4"));
        assertEquals(10, tree.nodeCount());
        assertEquals(Set.of("S1"), tree.match(allAttrs(1, 4, 5)));
        assertTrue(tree.unsubscribe("S1"));
        assertEquals(0, tree.nodeCount());
    }

    @Test
    void duplicateExpressionsUnderDifferentKeys() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("S2", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        tree.subscribe("S5", Expr.and(Expr.or(p(5), p(6)), Expr.or(p(7), p(8))));
        assertTrue(tree.unsubscribe("S5"));
        assertEquals(Set.of("S2"), tree.match(allAttrs(5, 7)));
    }

    @Test
    void xorMatchesExactlyOneOperand() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("X", Expr.xor(p(1), p(2)));
        assertEquals(Set.of("X"), tree.match(allAttrs(1)));
        assertEquals(Set.of("X"), tree.match(allAttrs(2)));
        assertEquals(Set.of(), tree.match(allAttrs(1, 2)));
        assertEquals(Set.of(), tree.match(allAttrs()));
    }

    @Test
    void xnorMatchesBothOrNeither() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("X", Expr.xnor(p(1), p(2)));
        assertEquals(Set.of("X"), tree.match(allAttrs(1, 2)));
        assertEquals(Set.of("X"), tree.match(allAttrs()));
        assertEquals(Set.of(), tree.match(allAttrs(1)));
        // With one attribute undefined, xnor is undefined: no match.
        assertEquals(Set.of(), tree.match(Event.of("a1", 0)));
    }

    @Test
    void singlePredicateExpression() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("P", p(3));
        assertEquals(1, tree.nodeCount());
        assertEquals(Set.of("P"), tree.match(allAttrs(3)));
        assertEquals(Set.of(), tree.match(allAttrs(1)));
    }

    @Test
    void randomizedExpressionsMatchBruteForce() {
        Random random = new Random(20260716L);
        ATree<Integer> tree = new ATree<>();
        List<Expr> exprs = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Expr expr = randomExpr(random, 3);
            exprs.add(expr);
            tree.subscribe(i, expr);
        }
        Set<Integer> alive = new HashSet<>();
        for (int i = 0; i < exprs.size(); i++) {
            alive.add(i);
        }
        verifyAgainstBruteForce(tree, exprs, alive, random, 60);

        // Unsubscribe every other expression, then verify the survivors still match correctly.
        for (int i = 0; i < exprs.size(); i += 2) {
            assertTrue(tree.unsubscribe(i));
            alive.remove(i);
        }
        verifyAgainstBruteForce(tree, exprs, alive, random, 40);

        for (int i = 1; i < exprs.size(); i += 2) {
            assertTrue(tree.unsubscribe(i));
        }
        assertEquals(0, tree.nodeCount());
    }

    @Test
    void concurrentMatchesAreConsistent() throws Exception {
        ATree<Integer> tree = new ATree<>();
        Random setupRandom = new Random(20260716L);
        List<Expr> exprs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Expr expr = randomExpr(setupRandom, 3);
            exprs.add(expr);
            tree.subscribe(i, expr);
        }

        // Every reader thread matches its own events against the same, unchanging tree; since
        // match() no longer stamps shared Node fields, concurrent matches must not corrupt or
        // observe each other's traversal state.
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                long seed = 1000L + t;
                futures.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    for (int e = 0; e < 200; e++) {
                        Event event = randomEvent(random);
                        Set<Integer> expected = new HashSet<>();
                        for (int i = 0; i < exprs.size(); i++) {
                            if (Boolean.TRUE.equals(eval3(exprs.get(i), event))) {
                                expected.add(i);
                            }
                        }
                        assertEquals(expected, tree.match(event), "event " + event);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void concurrentSubscribeUnsubscribeAndMatchDoNotCorruptIndex() throws Exception {
        ATree<String> tree = new ATree<>();
        int writerThreads = 4;
        int expressionsPerThread = 25;

        ExecutorService pool = Executors.newFixedThreadPool(writerThreads + 2);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int t = 0; t < writerThreads; t++) {
                int threadIndex = t;
                writers.add(pool.submit(() -> {
                    Random random = new Random(4200L + threadIndex);
                    for (int i = 0; i < expressionsPerThread; i++) {
                        tree.subscribe(threadIndex + "-" + i, randomExpr(random, 3));
                    }
                    for (int i = 0; i < expressionsPerThread; i += 2) {
                        assertTrue(tree.unsubscribe(threadIndex + "-" + i));
                    }
                }));
            }

            // Reader threads hammer match() throughout, purely to surface any concurrency bugs
            // (exceptions, corrupted traversal); the index is changing underneath them so their
            // results aren't checked against a reference here.
            AtomicBoolean stop = new AtomicBoolean(false);
            List<Future<?>> readers = new ArrayList<>();
            for (int r = 0; r < 2; r++) {
                int readerIndex = r;
                readers.add(pool.submit(() -> {
                    Random random = new Random(9000L + readerIndex);
                    while (!stop.get()) {
                        tree.match(randomEvent(random));
                    }
                }));
            }

            for (Future<?> writer : writers) {
                writer.get(30, TimeUnit.SECONDS);
            }
            stop.set(true);
            for (Future<?> reader : readers) {
                reader.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        // Half of each thread's expressions remain subscribed; no update was lost or duplicated.
        assertEquals(writerThreads * (expressionsPerThread / 2), tree.size());
    }

    private static void verifyAgainstBruteForce(ATree<Integer> tree, List<Expr> exprs,
                                                Set<Integer> alive, Random random, int events) {
        for (int e = 0; e < events; e++) {
            Event event = randomEvent(random);
            Set<Integer> expected = new HashSet<>();
            for (int i : alive) {
                if (Boolean.TRUE.equals(eval3(exprs.get(i), event))) {
                    expected.add(i);
                }
            }
            assertEquals(expected, tree.match(event), "event " + event);
        }
    }

    /** Three-valued reference evaluation (paper Sec. 5.1, Table 2); null means undefined. */
    private static Boolean eval3(Expr expr, Event event) {
        switch (expr.kind()) {
            case PREDICATE -> {
                Object value = event.value(expr.predicate().attr());
                return value == null ? null : expr.predicate().evaluate(value);
            }
            case NOT -> {
                Boolean child = eval3(expr.children().get(0), event);
                return child == null ? null : !child;
            }
            case AND -> {
                boolean undefined = false;
                for (Expr child : expr.children()) {
                    Boolean v = eval3(child, event);
                    if (Boolean.FALSE.equals(v)) {
                        return false;
                    }
                    undefined |= v == null;
                }
                return undefined ? null : true;
            }
            case OR -> {
                boolean undefined = false;
                for (Expr child : expr.children()) {
                    Boolean v = eval3(child, event);
                    if (Boolean.TRUE.equals(v)) {
                        return true;
                    }
                    undefined |= v == null;
                }
                return undefined ? null : false;
            }
            default -> {
                Boolean a = eval3(expr.children().get(0), event);
                Boolean b = eval3(expr.children().get(1), event);
                if (a == null || b == null) {
                    return null;
                }
                return expr.kind() == Expr.Kind.XOR ? a ^ b : a == b;
            }
        }
    }

    private static final String[] ATTRS = {"a", "b", "c", "d"};

    private static Expr randomExpr(Random random, int depth) {
        if (depth == 0 || random.nextInt(4) == 0) {
            return Expr.of(randomPredicate(random));
        }
        return switch (random.nextInt(6)) {
            case 0, 1 -> Expr.and(randomChildren(random, depth));
            case 2, 3 -> Expr.or(randomChildren(random, depth));
            case 4 -> Expr.not(randomExpr(random, depth - 1));
            default -> random.nextBoolean()
                    ? Expr.xor(randomExpr(random, depth - 1), randomExpr(random, depth - 1))
                    : Expr.xnor(randomExpr(random, depth - 1), randomExpr(random, depth - 1));
        };
    }

    private static List<Expr> randomChildren(Random random, int depth) {
        int count = 2 + random.nextInt(2);
        List<Expr> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            children.add(randomExpr(random, depth - 1));
        }
        return children;
    }

    private static Predicate randomPredicate(Random random) {
        String attr = ATTRS[random.nextInt(ATTRS.length)];
        Op op = Op.values()[random.nextInt(Op.values().length)];
        return switch (op) {
            case BETWEEN, NOT_BETWEEN -> {
                int lo = random.nextInt(5);
                yield Predicate.of(attr, op, lo, lo + random.nextInt(3));
            }
            case IN, NOT_IN -> {
                int count = 1 + random.nextInt(3);
                Object[] values = new Object[count];
                for (int i = 0; i < count; i++) {
                    values[i] = random.nextInt(5);
                }
                yield Predicate.of(attr, op, values);
            }
            default -> Predicate.of(attr, op, random.nextInt(5));
        };
    }

    private static Event randomEvent(Random random) {
        Event.Builder builder = Event.builder();
        for (String attr : ATTRS) {
            if (random.nextInt(10) < 7) { // each attribute present with probability 0.7
                builder.with(attr, random.nextInt(5));
            }
        }
        return builder.build();
    }
}
