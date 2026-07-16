package com.actionforward.atree;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventSupplierTests {

    @Test
    void supplierValuesParticipateInMatching() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("adult", Expr.of(Predicate.of("age", Op.GE, 18)));
        Event event = Event.builder().supply("age", () -> 21).build();
        assertEquals(Set.of("adult"), tree.match(event));
    }

    @Test
    void supplierIsInvokedAtMostOncePerEvent() {
        ATree<String> tree = new ATree<>();
        // Two predicates on the same attribute: the supplier must still run only once.
        tree.subscribe("teen", Expr.of(Predicate.of("age", Op.BETWEEN, 13, 19)));
        tree.subscribe("adult", Expr.of(Predicate.of("age", Op.GE, 18)));
        AtomicInteger calls = new AtomicInteger();
        Event event = Event.builder().supply("age", () -> {
            calls.incrementAndGet();
            return 18;
        }).build();
        assertEquals(Set.of("teen", "adult"), tree.match(event));
        assertEquals(1, calls.get());
    }

    @Test
    void supplierIsNotInvokedWhenNoPredicateReferencesItsAttribute() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("adult", Expr.of(Predicate.of("age", Op.GE, 18)));
        AtomicInteger calls = new AtomicInteger();
        Event event = Event.builder()
                .with("age", 30)
                .supply("expensive.metric", () -> {
                    calls.incrementAndGet();
                    return 42;
                })
                .build();
        assertEquals(Set.of("adult"), tree.match(event));
        assertEquals(0, calls.get());
    }

    @Test
    void nullSupplierResultMeansAbsentAttribute() {
        ATree<String> tree = new ATree<>();
        tree.subscribe("known", Expr.of(Predicate.of("age", Op.NE, -1)));
        Event event = Event.builder().supply("age", () -> null).build();
        assertEquals(Set.of(), tree.match(event));
    }

    @Test
    void staticValuesAndSuppliersMix() {
        ATree<String> tree = new ATree<>();
        ExprBuilderShortcut b = new ExprBuilderShortcut();
        tree.subscribe("combo", Expr.and(b.eq("city", "paris"), b.ge("age", 18)));
        Event event = Event.builder()
                .with("city", "paris")
                .supply("age", () -> 25)
                .build();
        assertEquals(Set.of("combo"), tree.match(event));
    }

    @Test
    void anAttributeCannotBeBoundTwice() {
        assertThrows(IllegalArgumentException.class,
                () -> Event.builder().with("age", 1).supply("age", () -> 2));
        assertThrows(IllegalArgumentException.class,
                () -> Event.builder().with("age", 1).with("age", 2));
    }

    private static final class ExprBuilderShortcut {
        Expr eq(String attr, Object value) {
            return Expr.of(Predicate.of(attr, Op.EQ, value));
        }

        Expr ge(String attr, Object value) {
            return Expr.of(Predicate.of(attr, Op.GE, value));
        }
    }
}
