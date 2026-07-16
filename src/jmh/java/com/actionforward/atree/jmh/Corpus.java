package com.actionforward.atree.jmh;

import com.actionforward.atree.Event;
import com.actionforward.atree.Expr;
import com.actionforward.atree.Op;
import com.actionforward.atree.Predicate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seeded random generators for benchmark inputs, mirroring the shape of
 * {@code ATreeTests.randomExpr}/{@code randomPredicate}/{@code randomEvent} but drawing from a
 * wider attribute pool: benchmarks need enough distinct expressions to fill an index of
 * realistic size while still colliding often enough to exercise node sharing (paper Sec. 4.2.1)
 * and expression reorganization (Alg. 2).
 */
final class Corpus {

    private static final String[] ATTRS;

    static {
        ATTRS = new String[16];
        for (int i = 0; i < ATTRS.length; i++) {
            ATTRS[i] = "a" + i;
        }
    }

    private Corpus() {
    }

    /** {@code count} expressions keyed 0..count-1, built from a fixed seed so runs are comparable. */
    static Map<Integer, Expr> subscriptions(int count, long seed) {
        Random random = new Random(seed);
        Map<Integer, Expr> expressions = new LinkedHashMap<>(count * 2);
        for (int key = 0; key < count; key++) {
            expressions.put(key, randomExpr(random, 3));
        }
        return expressions;
    }

    /** {@code count} events, each attribute present with probability 0.7, from a fixed seed. */
    static List<Event> events(int count, long seed) {
        Random random = new Random(seed);
        List<Event> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(randomEvent(random));
        }
        return events;
    }

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
            if (random.nextInt(10) < 7) {
                builder.with(attr, random.nextInt(5));
            }
        }
        return builder.build();
    }
}
