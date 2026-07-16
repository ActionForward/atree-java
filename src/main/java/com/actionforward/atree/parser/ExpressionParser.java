package com.actionforward.atree.parser;

import com.actionforward.atree.Expr;
import com.actionforward.atree.Op;
import com.actionforward.atree.Predicate;
import com.actionforward.atree.grammar.BoolExprLexer;
import com.actionforward.atree.grammar.BoolExprParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses textual Boolean expressions into {@link Expr} trees using an ANTLR grammar whose word
 * operators are supplied by a user-definable {@link ExpressionVocabulary}.
 *
 * <p>With the default vocabulary the language looks like:
 * <pre>{@code
 * (age between 16 and 18) and city in ('paris', 'lyon') or not vip = 'yes'
 * }</pre>
 *
 * Attribute names are free-form identifiers (letters, digits, {@code _} and {@code .}), values
 * are numbers or quoted strings, and the relational operators are {@code < <= > >= = != <>}
 * plus the (renamable) words {@code in} and {@code between}, both combinable with {@code not}.
 * Operator precedence, tightest first: {@code not}, {@code and}, {@code xor}/{@code xnor},
 * {@code or}.
 */
public final class ExpressionParser {

    private final ExpressionVocabulary vocabulary;

    /** A parser with the default English vocabulary. */
    public ExpressionParser() {
        this(ExpressionVocabulary.defaults());
    }

    public ExpressionParser(ExpressionVocabulary vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    /**
     * @throws ExpressionParseException if the input is not a valid expression
     */
    public Expr parse(String input) {
        Objects.requireNonNull(input, "input");
        ErrorCollector errors = new ErrorCollector();

        BoolExprLexer lexer = new BoolExprLexer(CharStreams.fromString(input));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);

        CommonTokenStream tokens = new CommonTokenStream(new KeywordTokenSource(lexer, vocabulary));
        BoolExprParser parser = new BoolExprParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        BoolExprParser.ParseContext tree = parser.parse();
        if (!errors.messages.isEmpty()) {
            throw new ExpressionParseException(input, errors.messages);
        }
        return toExpr(tree.expression());
    }

    private Expr toExpr(BoolExprParser.ExpressionContext ctx) {
        return orExpr(ctx.orExpression());
    }

    private Expr orExpr(BoolExprParser.OrExpressionContext ctx) {
        List<Expr> operands = new ArrayList<>();
        for (BoolExprParser.XorExpressionContext child : ctx.xorExpression()) {
            operands.add(xorExpr(child));
        }
        return operands.size() == 1 ? operands.get(0) : Expr.or(operands);
    }

    private Expr xorExpr(BoolExprParser.XorExpressionContext ctx) {
        List<BoolExprParser.AndExpressionContext> operands = ctx.andExpression();
        Expr result = andExpr(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            Expr right = andExpr(operands.get(i));
            result = ctx.op.get(i - 1).getType() == BoolExprParser.XOR
                    ? Expr.xor(result, right)
                    : Expr.xnor(result, right);
        }
        return result;
    }

    private Expr andExpr(BoolExprParser.AndExpressionContext ctx) {
        List<Expr> operands = new ArrayList<>();
        for (BoolExprParser.NotExpressionContext child : ctx.notExpression()) {
            operands.add(notExpr(child));
        }
        return operands.size() == 1 ? operands.get(0) : Expr.and(operands);
    }

    private Expr notExpr(BoolExprParser.NotExpressionContext ctx) {
        if (ctx.NOT() != null) {
            return Expr.not(notExpr(ctx.notExpression()));
        }
        return atom(ctx.atom());
    }

    private Expr atom(BoolExprParser.AtomContext ctx) {
        if (ctx.expression() != null) {
            return toExpr(ctx.expression());
        }
        return predicate(ctx.predicate());
    }

    private Expr predicate(BoolExprParser.PredicateContext ctx) {
        if (ctx instanceof BoolExprParser.ComparisonPredicateContext c) {
            Op op = switch (c.comparison.getType()) {
                case BoolExprParser.LT -> Op.LT;
                case BoolExprParser.LE -> Op.LE;
                case BoolExprParser.GT -> Op.GT;
                case BoolExprParser.GE -> Op.GE;
                case BoolExprParser.EQ -> Op.EQ;
                default -> Op.NE;
            };
            return Expr.of(Predicate.of(c.IDENT().getText(), op, value(c.value())));
        }
        if (ctx instanceof BoolExprParser.InPredicateContext c) {
            Op op = c.NOT() != null ? Op.NOT_IN : Op.IN;
            Object[] values = c.value().stream().map(ExpressionParser::value).toArray();
            return Expr.of(Predicate.of(c.IDENT().getText(), op, values));
        }
        BoolExprParser.BetweenPredicateContext c = (BoolExprParser.BetweenPredicateContext) ctx;
        Op op = c.NOT() != null ? Op.NOT_BETWEEN : Op.BETWEEN;
        return Expr.of(Predicate.of(c.IDENT().getText(), op, value(c.value(0)), value(c.value(1))));
    }

    private static Object value(BoolExprParser.ValueContext ctx) {
        if (ctx.NUMBER() != null) {
            String text = ctx.NUMBER().getText();
            return text.contains(".") ? (Object) Double.parseDouble(text) : (Object) Long.parseLong(text);
        }
        String quoted = ctx.STRING().getText();
        return quoted.substring(1, quoted.length() - 1);
    }

    /**
     * Re-types IDENT tokens matching vocabulary words into the grammar's imaginary operator
     * tokens ({@code AND}, {@code OR}, …) before they reach the parser.
     */
    private static final class KeywordTokenSource implements TokenSource {

        private final BoolExprLexer delegate;
        private final ExpressionVocabulary vocabulary;

        KeywordTokenSource(BoolExprLexer delegate, ExpressionVocabulary vocabulary) {
            this.delegate = delegate;
            this.vocabulary = vocabulary;
        }

        @Override
        public Token nextToken() {
            Token token = delegate.nextToken();
            if (token.getType() == BoolExprLexer.IDENT) {
                ExpressionVocabulary.Word word = vocabulary.lookup(token.getText());
                if (word != null) {
                    CommonToken retyped = new CommonToken(token);
                    retyped.setType(tokenType(word));
                    return retyped;
                }
            }
            return token;
        }

        private static int tokenType(ExpressionVocabulary.Word word) {
            return switch (word) {
                case AND -> BoolExprParser.AND;
                case OR -> BoolExprParser.OR;
                case NOT -> BoolExprParser.NOT;
                case XOR -> BoolExprParser.XOR;
                case XNOR -> BoolExprParser.XNOR;
                case IN -> BoolExprParser.IN;
                case BETWEEN -> BoolExprParser.BETWEEN;
            };
        }

        @Override
        public int getLine() {
            return delegate.getLine();
        }

        @Override
        public int getCharPositionInLine() {
            return delegate.getCharPositionInLine();
        }

        @Override
        public CharStream getInputStream() {
            return delegate.getInputStream();
        }

        @Override
        public String getSourceName() {
            return delegate.getSourceName();
        }

        @Override
        public void setTokenFactory(TokenFactory<?> factory) {
            delegate.setTokenFactory(factory);
        }

        @Override
        public TokenFactory<?> getTokenFactory() {
            return delegate.getTokenFactory();
        }
    }

    private static final class ErrorCollector extends BaseErrorListener {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                int charPositionInLine, String msg, RecognitionException e) {
            messages.add("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }
}
