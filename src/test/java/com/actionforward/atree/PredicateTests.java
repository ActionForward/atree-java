package com.actionforward.atree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredicateTests {

    @Test
    void relationalOperators() {
        assertTrue(Predicate.of("age", Op.LT, 18).evaluate(17));
        assertFalse(Predicate.of("age", Op.LT, 18).evaluate(18));
        assertTrue(Predicate.of("age", Op.LE, 18).evaluate(18));
        assertTrue(Predicate.of("age", Op.GT, 18).evaluate(19));
        assertTrue(Predicate.of("age", Op.GE, 18).evaluate(18));
        assertTrue(Predicate.of("age", Op.EQ, 18).evaluate(18));
        assertTrue(Predicate.of("age", Op.NE, 18).evaluate(19));
    }

    @Test
    void numbersCompareAcrossTypes() {
        assertTrue(Predicate.of("age", Op.EQ, 18).evaluate(18.0));
        assertTrue(Predicate.of("age", Op.LT, 18.5).evaluate(18L));
    }

    @Test
    void stringsCompareLexicographically() {
        assertTrue(Predicate.of("name", Op.LT, "bob").evaluate("alice"));
        assertTrue(Predicate.of("name", Op.EQ, "alice").evaluate("alice"));
    }

    @Test
    void setMembership() {
        Predicate in = Predicate.of("city", Op.IN, "paris", "lyon");
        assertTrue(in.evaluate("paris"));
        assertFalse(in.evaluate("nice"));
        assertTrue(Predicate.of("city", Op.NOT_IN, "paris").evaluate("nice"));
    }

    @Test
    void between() {
        Predicate p = Predicate.of("age", Op.BETWEEN, 16, 18);
        assertTrue(p.evaluate(16));
        assertTrue(p.evaluate(17));
        assertTrue(p.evaluate(18));
        assertFalse(p.evaluate(19));
        assertTrue(Predicate.of("age", Op.NOT_BETWEEN, 16, 18).evaluate(19));
    }

    @Test
    void negationIsExactComplement() {
        Object[] samples = {15, 16, 17, 18, 19, "16"};
        for (Op op : new Op[]{Op.LT, Op.LE, Op.GT, Op.GE, Op.EQ, Op.NE}) {
            Predicate p = Predicate.of("age", op, 17);
            for (Object v : samples) {
                assertEquals(!p.evaluate(v), p.negate().evaluate(v), op + " on " + v);
            }
        }
        Predicate between = Predicate.of("age", Op.BETWEEN, 16, 18);
        for (Object v : samples) {
            assertEquals(!between.evaluate(v), between.negate().evaluate(v), "between on " + v);
        }
    }

    @Test
    void equivalentPredicatesShareTheirId() {
        assertEquals(Predicate.of("age", Op.EQ, 18).id(), Predicate.of("age", Op.EQ, 18.0).id());
        assertEquals(Predicate.of("city", Op.IN, "a", "b").id(), Predicate.of("city", Op.IN, "b", "a").id());
        assertNotEquals(Predicate.of("age", Op.EQ, 18).id(), Predicate.of("age", Op.NE, 18).id());
    }

    @Test
    void arityIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> Predicate.of("age", Op.LT, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> Predicate.of("age", Op.BETWEEN, 1));
        assertThrows(IllegalArgumentException.class, () -> Predicate.of("age", Op.IN));
    }
}
