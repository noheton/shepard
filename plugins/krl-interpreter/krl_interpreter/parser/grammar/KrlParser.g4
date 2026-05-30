// KrlParser.g4 — KUKA Robot Language (KRL) tier-1 parser.
//
// Scope mirrors KrlLexer.g4. See `aidocs/integrations/117 §4` for the
// KRL subset covered tier-1 and the IR shape this parser feeds.
//
// Statements with semicolon-comments are terminated by NEWLINE (the
// lexer skips comment text + emits NEWLINE from comment mode).
// Empty lines are folded by allowing NEWLINE* between statements.

parser grammar KrlParser;
options { tokenVocab=KrlLexer; }

// -------------------------------------------------------------------
// Top-level: either a `.src` (DEF/DEFFCT module) or a `.dat` (DEFDAT).
// We also accept a bare statement stream so callers can parse
// snippets directly in tests.
// -------------------------------------------------------------------

program     : NEWLINE* (srcModule | datModule | statementBlock) EOF ;

srcModule   : GLOBAL? (DEF | DEFFCT) NAME LPAREN paramList? RPAREN NEWLINE+
              statementBlock
              (END | ENDFCT) NEWLINE* ;

datModule   : DEFDAT NAME PUBLIC? NEWLINE+
              datDeclBlock
              ENDDAT NEWLINE* ;

paramList   : paramDecl (COMMA paramDecl)* ;
paramDecl   : NAME COLON (IN | OUT) ;

datDeclBlock: (varDecl NEWLINE+)* ;

// -------------------------------------------------------------------
// Statements.
// -------------------------------------------------------------------

statementBlock
            : (statement NEWLINE+)* ;

statement   : motionStmt
            | waitStmt
            | ifStmt
            | forStmt
            | whileStmt
            | loopStmt
            | exitStmt
            | frameSwitchStmt
            | varDecl
            | assignStmt
            | unsupportedStmt
            ;

// -------------------------------------------------------------------
// Motion primitives.
// -------------------------------------------------------------------

motionStmt  : (PTP | PTP_REL | LIN | LIN_REL) poseExpr motionOpts? # ptpLinMotion
            | (CIRC | CIRC_REL) poseExpr COMMA poseExpr motionOpts? # circMotion
            ;

motionOpts  : NAME+ ;  // `C_DIS`, `C_PTP`, named tags — captured as tokens.

poseExpr    : frameLiteral
            | NAME
            ;

// -------------------------------------------------------------------
// WAIT.
// -------------------------------------------------------------------

waitStmt    : WAIT SEC expr     # waitSec
            | WAIT FOR expr     # waitFor
            ;

// -------------------------------------------------------------------
// Flow control.
// -------------------------------------------------------------------

ifStmt      : IF expr THEN NEWLINE+
              statementBlock
              (ELSE NEWLINE+ statementBlock)?
              ENDIF ;

forStmt     : FOR NAME EQ expr TO expr (STEP expr)? NEWLINE+
              statementBlock
              ENDFOR ;

whileStmt   : WHILE expr NEWLINE+
              statementBlock
              ENDWHILE ;

loopStmt    : LOOP NEWLINE+
              statementBlock
              ENDLOOP ;

exitStmt    : EXIT ;

// -------------------------------------------------------------------
// $BASE / $TOOL switching.
// -------------------------------------------------------------------

frameSwitchStmt
            : (BASE | TOOL) EQ (frameLiteral | NAME) ;

// -------------------------------------------------------------------
// Variable declaration + assignment.
// -------------------------------------------------------------------

varDecl     : DECL? typeName NAME (EQ expr)? ;

typeName    : INT | REAL | BOOL | CHAR | FRAME | POS | E6POS | E6AXIS | AXIS | NAME ;

assignStmt  : (NAME | DOLLAR_NAME) EQ expr ;

// -------------------------------------------------------------------
// Unsupported tier-1 — tokenise to keep the parse moving; the walker
// emits an UnsupportedConstruct warning.
// -------------------------------------------------------------------

unsupportedStmt
            : BCO                                                   # bcoStmt
            | SPS NAME?                                             # spsStmt
            | INTERRUPT (~NEWLINE)*                                 # interruptStmt
            | ON NAME (~NEWLINE)*                                   # onErrorStmt
            | ANIN (~NEWLINE)*                                      # aninStmt
            | ANOUT (~NEWLINE)*                                     # anoutStmt
            | CONTINUE                                              # continueStmt
            | HALT                                                  # haltStmt
            ;

// -------------------------------------------------------------------
// Frame literal: { X 100, Y 0, Z 200, A 0, B 0, C 0 } and friends.
// Sparse literals (subset of fields) are tier-1; missing fields → 0.
// -------------------------------------------------------------------

frameLiteral
            : LBRACE frameField (COMMA frameField)* RBRACE ;

frameField  : NAME expr ;

// -------------------------------------------------------------------
// Expressions — minimal arithmetic + comparison + boolean. Precedence
// follows the textbook ladder: OR < AND < NOT < CMP < ADD < MUL < unary.
// -------------------------------------------------------------------

expr        : orExpr ;
orExpr      : andExpr (OR andExpr)* ;
andExpr     : notExpr (AND notExpr)* ;
notExpr     : NOT notExpr
            | cmpExpr
            ;
cmpExpr     : addExpr ((EQEQ | NEQ | LT | LE | GT | GE) addExpr)? ;
addExpr     : mulExpr ((PLUS | MINUS) mulExpr)* ;
mulExpr     : unaryExpr ((STAR | SLASH) unaryExpr)* ;
unaryExpr   : (PLUS | MINUS) unaryExpr
            | atom
            ;
atom        : INT_LIT
            | REAL_LIT
            | STRING_LIT
            | TRUE
            | FALSE
            | frameLiteral
            | (NAME | DOLLAR_NAME) (LBRACK expr RBRACK)?
            | LPAREN expr RPAREN
            ;
