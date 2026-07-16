grammar BoolExpr;

// Word operators are not lexed as keywords: the lexer only emits IDENT tokens, and
// KeywordTokenSource (driven by a user-defined ExpressionVocabulary) re-types the IDENTs
// matching vocabulary words into the imaginary tokens below before parsing. This is what
// lets library users rename and/or/not/xor/xnor/in/between without touching the grammar.
tokens { AND, OR, NOT, XOR, XNOR, IN, BETWEEN }

parse
    : expression EOF
    ;

expression
    : orExpression
    ;

orExpression
    : xorExpression (OR xorExpression)*
    ;

xorExpression
    : andExpression (op+=(XOR | XNOR) andExpression)*
    ;

andExpression
    : notExpression (AND notExpression)*
    ;

notExpression
    : NOT notExpression
    | atom
    ;

atom
    : LPAREN expression RPAREN
    | predicate
    ;

predicate
    : IDENT comparison=(LT | LE | GT | GE | EQ | NEQ) value        # comparisonPredicate
    | IDENT NOT? IN LPAREN value (COMMA value)* RPAREN             # inPredicate
    | IDENT NOT? BETWEEN value AND value                           # betweenPredicate
    ;

value
    : NUMBER
    | STRING
    ;

LPAREN: '(';
RPAREN: ')';
COMMA: ',';
LE: '<=';
GE: '>=';
NEQ: '!=' | '<>';
LT: '<';
GT: '>';
EQ: '=';
NUMBER: '-'? [0-9]+ ('.' [0-9]+)?;
STRING: '\'' ~[']* '\'' | '"' ~["]* '"';
IDENT: [a-zA-Z_] [a-zA-Z0-9_.]*;
WS: [ \t\r\n]+ -> skip;
