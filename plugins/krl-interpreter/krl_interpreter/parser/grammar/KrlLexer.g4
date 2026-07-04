// KrlLexer.g4 — KUKA Robot Language (KRL) tier-1 lexer.
//
// Scope (KRL-INTERPRETER-02): motion primitives PTP/LIN/CIRC/WAIT,
// flow IF/FOR/WHILE/LOOP, variable declarations + assignment, frame
// literals, $BASE / $TOOL switching, comments.
//
// Constructs deliberately tokenised but NOT given parser rules
// (caught as UnsupportedConstruct in the walker): BCO, SPS, INTERRUPT,
// ANIN/ANOUT, ON ERROR. See `walker.py` for the warning shape.
//
// Grammar shape inspired by the public-domain KRL ABNF grammar
// (Roiki11/KRLparser `krl_bnf.ebnf`, fetched 2026-05-30). That file
// itself derives from the public KUKA System Software 8.x KRL
// Reference Manual. No code reused; this `.g4` is original work.
// See `grammar/SOURCES.md`.

lexer grammar KrlLexer;

// -------------------------------------------------------------------
// Keywords. KRL is case-insensitive: handled by per-letter fragments.
// -------------------------------------------------------------------

DEF       : D E F ;
DEFFCT    : D E F F C T ;
DEFDAT    : D E F D A T ;
END       : E N D ;
ENDFCT    : E N D F C T ;
ENDDAT    : E N D D A T ;
GLOBAL    : G L O B A L ;
PUBLIC    : P U B L I C ;

DECL      : D E C L ;
INT       : I N T ;
REAL      : R E A L ;
BOOL      : B O O L ;
CHAR      : C H A R ;
FRAME     : F R A M E ;
POS       : P O S ;
E6POS     : E '6' P O S ;
E6AXIS    : E '6' A X I S ;
AXIS      : A X I S ;

PTP       : P T P ;
PTP_REL   : P T P '_' R E L ;
LIN       : L I N ;
LIN_REL   : L I N '_' R E L ;
CIRC      : C I R C ;
CIRC_REL  : C I R C '_' R E L ;
WAIT      : W A I T ;
SEC       : S E C ;
FOR       : F O R ;
TO        : T O ;
STEP      : S T E P ;
ENDFOR    : E N D F O R ;
WHILE     : W H I L E ;
ENDWHILE  : E N D W H I L E ;
LOOP      : L O O P ;
ENDLOOP   : E N D L O O P ;
EXIT      : E X I T ;
IF        : I F ;
THEN      : T H E N ;
ELSE      : E L S E ;
ENDIF     : E N D I F ;
TRUE      : T R U E ;
FALSE     : F A L S E ;
IN        : I N ;
OUT       : O U T ;

// Tokens for unsupported constructs — kept so the lexer doesn't
// blow up; the walker collects them as UnsupportedConstruct.
BCO       : B C O ;
SPS       : S P S ;
INTERRUPT : I N T E R R U P T ;
ON        : O N ;
ANIN      : A N I N ;
ANOUT     : A N O U T ;
CONTINUE  : C O N T I N U E ;
HALT      : H A L T ;

// System / global variables. $BASE and $TOOL are the two we resolve;
// other $-prefixed names fall through as DOLLAR_NAME.
BASE      : '$' B A S E ;
TOOL      : '$' T O O L ;
DOLLAR_NAME : '$' LETTER (LETTER | DIGIT | '_')* ;

// -------------------------------------------------------------------
// Operators + punctuation.
// -------------------------------------------------------------------

LBRACE    : '{' ;
RBRACE    : '}' ;
LPAREN    : '(' ;
RPAREN    : ')' ;
LBRACK    : '[' ;
RBRACK    : ']' ;
COMMA     : ',' ;
COLON     : ':' ;
SEMI      : ';' -> skip, pushMode(LINE_COMMENT) ;

EQ        : '=' ;
EQEQ      : '==' ;
NEQ       : '<>' ;
LT        : '<' ;
LE        : '<=' ;
GT        : '>' ;
GE        : '>=' ;
PLUS      : '+' ;
MINUS     : '-' ;
STAR      : '*' ;
SLASH     : '/' ;
AND       : A N D ;
OR        : O R ;
NOT       : N O T ;

// -------------------------------------------------------------------
// Literals.
// -------------------------------------------------------------------

REAL_LIT  : DIGIT+ '.' DIGIT* (E_EXP)?
          | '.' DIGIT+ (E_EXP)?
          | DIGIT+ E_EXP
          ;
INT_LIT   : DIGIT+ ;
STRING_LIT: '"' (~["\r\n])* '"' ;

fragment E_EXP : ('e' | 'E') ('+' | '-')? DIGIT+ ;

// Identifier. Allow trailing $-name characters per KRL convention.
NAME      : LETTER (LETTER | DIGIT | '_')* ;

// -------------------------------------------------------------------
// File-attribute lines (`&ACCESS …`) — skip them as a header marker.
// -------------------------------------------------------------------

FILE_ATTR : '&' ~[\r\n]* -> skip ;

// -------------------------------------------------------------------
// Layout.
// -------------------------------------------------------------------

NEWLINE   : ('\r'? '\n')+ ;
WS        : [ \t]+ -> skip ;

// -------------------------------------------------------------------
// Case-insensitive letter fragments.
// -------------------------------------------------------------------

fragment LETTER : [a-zA-Z] ;
fragment DIGIT  : [0-9] ;

fragment A : [aA] ; fragment B : [bB] ; fragment C : [cC] ;
fragment D : [dD] ; fragment E : [eE] ; fragment F : [fF] ;
fragment G : [gG] ; fragment H : [hH] ; fragment I : [iI] ;
fragment J : [jJ] ; fragment K : [kK] ; fragment L : [lL] ;
fragment M : [mM] ; fragment N : [nN] ; fragment O : [oO] ;
fragment P : [pP] ; fragment Q : [qQ] ; fragment R : [rR] ;
fragment S : [sS] ; fragment T : [tT] ; fragment U : [uU] ;
fragment V : [vV] ; fragment W : [wW] ; fragment X : [xX] ;
fragment Y : [yY] ; fragment Z : [zZ] ;

// -------------------------------------------------------------------
// Comment mode — `;` to end-of-line. Skipped entirely.
// -------------------------------------------------------------------

mode LINE_COMMENT;
COMMENT_TEXT : ~[\r\n]+ -> skip ;
COMMENT_END  : ('\r'? '\n') -> type(NEWLINE), popMode ;
