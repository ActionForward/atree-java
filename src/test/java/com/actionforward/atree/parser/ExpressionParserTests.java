package com.actionforward.atree.parser;

import com.actionforward.atree.ATree;
import com.actionforward.atree.Event;
import com.actionforward.atree.Expr;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionParserTests {

    private final ExpressionParser parser = new ExpressionParser();

    private static Set<String> match(Expr expr, Event event) {
        ATree<String> tree = new ATree<>();
        tree.subscribe("e", expr);
        return tree.match(event);
    }

    private static void assertMatches(Expr expr, Event event) {
        assertEquals(Set.of("e"), match(expr, event), expr + " should match " + event);
    }

    private static void assertNoMatch(Expr expr, Event event) {
        assertEquals(Set.of(), match(expr, event), expr + " should not match " + event);
    }

    @Test
    void parsesComparisonsAndLogic() {
        Expr expr = parser.parse("(age between 16 and 18) and city in ('paris', 'lyon') or vip = 'yes'");
        assertMatches(expr, Event.of("age", 17, "city", "paris", "vip", "no"));
        assertMatches(expr, Event.of("age", 40, "city", "nice", "vip", "yes"));
        assertNoMatch(expr, Event.of("age", 40, "city", "paris", "vip", "no"));
    }

    @Test
    void parsesEveryRelationalOperator() {
        assertMatches(parser.parse("x < 5"), Event.of("x", 4));
        assertMatches(parser.parse("x <= 5"), Event.of("x", 5));
        assertMatches(parser.parse("x > 5"), Event.of("x", 6));
        assertMatches(parser.parse("x >= 5"), Event.of("x", 5));
        assertMatches(parser.parse("x = 5"), Event.of("x", 5));
        assertMatches(parser.parse("x != 5"), Event.of("x", 4));
        assertMatches(parser.parse("x <> 5"), Event.of("x", 4));
        assertMatches(parser.parse("x = -1.5"), Event.of("x", -1.5));
        assertMatches(parser.parse("x not in (1, 2)"), Event.of("x", 3));
        assertMatches(parser.parse("x not between 1 and 3"), Event.of("x", 4));
    }

    @Test
    void andBindsTighterThanOr() {
        Expr expr = parser.parse("a = 1 or b = 1 and c = 1");
        assertMatches(expr, Event.of("a", 1, "b", 0, "c", 0));
        assertMatches(expr, Event.of("a", 0, "b", 1, "c", 1));
        assertNoMatch(expr, Event.of("a", 0, "b", 1, "c", 0));
    }

    @Test
    void notBindsTighterThanAnd() {
        Expr expr = parser.parse("not a = 1 and b = 1");
        assertMatches(expr, Event.of("a", 0, "b", 1));
        assertNoMatch(expr, Event.of("a", 1, "b", 1));
    }

    @Test
    void parsesXorAndXnor() {
        Expr xor = parser.parse("a = 1 xor b = 1");
        assertMatches(xor, Event.of("a", 1, "b", 0));
        assertNoMatch(xor, Event.of("a", 1, "b", 1));
        Expr xnor = parser.parse("a = 1 xnor b = 1");
        assertMatches(xnor, Event.of("a", 1, "b", 1));
        assertMatches(xnor, Event.of("a", 0, "b", 0));
        assertNoMatch(xnor, Event.of("a", 1, "b", 0));
    }

    @Test
    void defaultVocabularyIsCaseInsensitive() {
        Expr expr = parser.parse("age BETWEEN 16 AND 18 And city = 'paris'");
        assertMatches(expr, Event.of("age", 17, "city", "paris"));
    }

    @Test
    void vocabularyWordsCanBeRedefined() {
        ExpressionVocabulary french = ExpressionVocabulary.builder()
                .word(ExpressionVocabulary.Word.AND, "et")
                .word(ExpressionVocabulary.Word.OR, "ou")
                .word(ExpressionVocabulary.Word.NOT, "non", "pas")
                .word(ExpressionVocabulary.Word.IN, "dans")
                .word(ExpressionVocabulary.Word.BETWEEN, "entre")
                .build();
        ExpressionParser frenchParser = new ExpressionParser(french);
        Expr expr = frenchParser.parse(
                "(age entre 16 et 18) et ville dans ('paris', 'lyon') ou pas vip = 'oui'");
        assertMatches(expr, Event.of("age", 17, "ville", "paris", "vip", "oui"));
        assertMatches(expr, Event.of("age", 40, "ville", "nice", "vip", "non"));
        assertNoMatch(expr, Event.of("age", 40, "ville", "nice", "vip", "oui"));
        // The default words are no longer operators: they parse as plain attribute names.
        assertMatches(frenchParser.parse("between = 1"), Event.of("between", 1));
    }

    @Test
    void quotedStringsAndDottedIdentifiers() {
        Expr expr = parser.parse("user.name = \"alice\"");
        assertMatches(expr, Event.of("user.name", "alice"));
        assertNoMatch(expr, Event.of("user.name", "bob"));
    }

    @Test
    void integersAndDecimalsCompareNumerically() {
        assertMatches(parser.parse("price <= 9.5"), Event.of("price", 9));
        assertMatches(parser.parse("price = 9"), Event.of("price", 9.0));
    }

    @Test
    void syntaxErrorsAreReported() {
        assertThrows(ExpressionParseException.class, () -> parser.parse("a >"));
        assertThrows(ExpressionParseException.class, () -> parser.parse("a frobnicates 1"));
        assertThrows(ExpressionParseException.class, () -> parser.parse("(a = 1"));
        assertThrows(ExpressionParseException.class, () -> parser.parse(""));
    }

    @Test
    void conflictingVocabularyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExpressionVocabulary.builder()
                .word(ExpressionVocabulary.Word.AND, "both")
                .word(ExpressionVocabulary.Word.OR, "both")
                .build());
        assertThrows(IllegalArgumentException.class, () -> ExpressionVocabulary.builder()
                .word(ExpressionVocabulary.Word.AND, "&&")
                .build());
    }
}
