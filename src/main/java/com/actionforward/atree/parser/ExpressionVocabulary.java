package com.actionforward.atree.parser;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The word operators recognized by {@link ExpressionParser}. Every logical and textual operator
 * word can be redefined (with any number of synonyms) by the library user, e.g. to localize the
 * expression language:
 *
 * <pre>{@code
 * ExpressionVocabulary french = ExpressionVocabulary.builder()
 *         .word(Word.AND, "et")
 *         .word(Word.OR, "ou")
 *         .word(Word.NOT, "non", "pas")
 *         .word(Word.IN, "dans")
 *         .word(Word.BETWEEN, "entre")
 *         .build();
 * Expr e = new ExpressionParser(french).parse("age entre 16 et 18 et ville dans ('paris')");
 * }</pre>
 *
 * The builder starts from the default English vocabulary ({@code and, or, not, xor, xnor, in,
 * between}); calling {@link Builder#word} replaces all synonyms of that word. Vocabulary words
 * are reserved: an attribute cannot carry the same name. Matching is case-insensitive unless
 * {@link Builder#caseInsensitive(boolean)} is disabled.
 */
public final class ExpressionVocabulary {

    /** The redefinable operator words of the expression language. */
    public enum Word { AND, OR, NOT, XOR, XNOR, IN, BETWEEN }

    /** Words must be lexable as identifiers, otherwise they could never match. */
    private static final Pattern VALID_WORD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");

    private final Map<String, Word> byText;
    private final boolean caseInsensitive;

    private ExpressionVocabulary(Map<String, Word> byText, boolean caseInsensitive) {
        this.byText = Map.copyOf(byText);
        this.caseInsensitive = caseInsensitive;
    }

    /** The default English vocabulary: {@code and, or, not, xor, xnor, in, between}. */
    public static ExpressionVocabulary defaults() {
        return builder().build();
    }

    /** A builder pre-seeded with the default English vocabulary. */
    public static Builder builder() {
        return new Builder();
    }

    /** The operator word matching this text, or null if it is a plain identifier. */
    Word lookup(String text) {
        return byText.get(caseInsensitive ? text.toLowerCase(Locale.ROOT) : text);
    }

    public static final class Builder {

        private final EnumMap<Word, List<String>> synonyms = new EnumMap<>(Word.class);
        private boolean caseInsensitive = true;

        private Builder() {
            for (Word word : Word.values()) {
                synonyms.put(word, List.of(word.name().toLowerCase(Locale.ROOT)));
            }
        }

        /** Replaces the synonyms of a word. */
        public Builder word(Word word, String... texts) {
            Objects.requireNonNull(word, "word");
            if (texts.length == 0) {
                throw new IllegalArgumentException(word + " requires at least one synonym");
            }
            for (String text : texts) {
                Objects.requireNonNull(text, "synonym");
                if (!VALID_WORD.matcher(text).matches()) {
                    throw new IllegalArgumentException(
                            "not a valid operator word (must be an identifier): " + text);
                }
            }
            synonyms.put(word, List.of(texts));
            return this;
        }

        public Builder caseInsensitive(boolean caseInsensitive) {
            this.caseInsensitive = caseInsensitive;
            return this;
        }

        public ExpressionVocabulary build() {
            Map<String, Word> byText = new LinkedHashMap<>();
            synonyms.forEach((word, texts) -> {
                for (String text : texts) {
                    String key = caseInsensitive ? text.toLowerCase(Locale.ROOT) : text;
                    Word previous = byText.put(key, word);
                    if (previous != null && previous != word) {
                        throw new IllegalArgumentException(
                                "'" + text + "' is mapped to both " + previous + " and " + word);
                    }
                }
            });
            return new ExpressionVocabulary(byText, caseInsensitive);
        }
    }
}
