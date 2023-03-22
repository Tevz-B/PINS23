/**
 * @Author: basaj
 * @Description: Sintaksni analizator.
 */

package compiler.parser;

import static compiler.lexer.TokenType.*;
import static common.RequireNonNull.requireNonNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import common.Report;
import compiler.lexer.Position;
import compiler.lexer.Position.Location;
import compiler.lexer.Symbol;
import compiler.lexer.TokenType;
import compiler.parser.ast.*;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.expr.Binary.Operator;
import compiler.parser.ast.type.*;

// CHECK for BUGS: definitions inside { where definitions }

public class Parser {
    /**
     * Sklad leksikalnih simbolov.
     */
    private Stack<Symbol> stack;

    /**
     * Prejsnji simbol.
     */
    private Symbol prevSymbol;

    /**
     * Ciljni tok, kamor izpisujemo produkcije. Če produkcij ne želimo izpisovati,
     * vrednost opcijske spremenljivke nastavimo na Optional.empty().
     */
    private final Optional<PrintStream> productionsOutputStream;

    public Parser(List<Symbol> symbols, Optional<PrintStream> productionsOutputStream) {
        requireNonNull(symbols, productionsOutputStream);
        // this.symbols = symbols;
        this.productionsOutputStream = productionsOutputStream;
        this.stack = new Stack<Symbol>();
        Collections.reverse(symbols);
        stack.addAll(symbols);
    }

    public Ast parse() {
        return parseSource();
    }

    private Ast parseSource() {
        if(check(EOF)) {
            err("No definition in source code!");
        }
        dump("source -> definitions");
        Ast ast = parseDefinitions();

        if (check(EOF)) {
            // end of definitions
            dump("$ -> $");
            skip();
            // EOF: end of file $
        }
        else {
            err("Expected EOF at end of definitions.");
        }
        return ast;
    }

    private Defs parseDefinitions() {
        dump("definitions -> definition definitions2");
        Position startPos = cPos();
        Def t = parseDefinition();
	    Defs t2 = parseDefinitions2();

        List<Def> defs = new ArrayList<>(); defs.add(t);
        Position endPos = t.position;
        if (t2 != null) {
            defs.addAll(t2.definitions);
            endPos = t2.position;
        }
        return new Defs(newPos(startPos, endPos), defs);
    }

    private Defs parseDefinitions2() {
        Defs res = null;
        switch(cToken()) {
        case OP_SEMICOLON: 
            dump("definitions2 -> ; definition definitions2");
            skip();
            Position startPos = cPos();
            Def t = parseDefinition();
            Defs t2 = parseDefinitions2();

            List<Def> defs = new ArrayList<>(); defs.add(t);
            Position endPos = t.position;
            if (t2 != null) {
                defs.addAll(t2.definitions);
                endPos = t2.position;
            }
            res = new Defs(newPos(startPos, endPos), defs);
            break;
        default:
            dump("definitions2 -> e");
            break;
        }
        return res;
    }

    private Def parseDefinition() { // TODO ?
        Position startPos = cPos();
        if( check( KW_VAR ) ) {
            dump("definition -> variable_definition");
            skip();
            VarDef t = parseVariableDefinition();
            return new VarDef(newPos(startPos, t.position), t.name, t.type);
        }
        else if( check( KW_FUN ) ) {
            dump("definition -> function_definition");
            skip();
            FunDef t = parseFunctionDefinition();
            return new FunDef(newPos(startPos, t.position), t.name, t.parameters, t.type, t.body);
        }
        else if( check( KW_TYP ) ) {
            dump("definition -> type_definition");
            skip();
            TypeDef t = parseTypeDefinition();
            // correct position to include TYP keyword
            return new TypeDef( newPos(startPos, t.position), t.name, t.type );
        } 
        else {
            if( check( EOF ) ) {
                err("Delete extra ';' at the end of file.");
            }
            err("Expected 'fun', 'typ' or 'var' keyword as definition.");
            return null;
        }
    }

    private VarDef parseVariableDefinition() {
        VarDef res = null;
        if (check(IDENTIFIER)) {
            Position startPos = cPos();
            String name = cLex();
            checkErr(1, OP_COLON);
            dump("variable_definition -> var identifier : type");
            skip(2);
            Type type = parseType();
            res = new VarDef(newPos(startPos, type.position), name, type);
        } else {
            err("Expected identifier as variable definition");
        }
        return res;
    }

    private Type parseType() {
        Type res = null;
        switch(cToken()) {
            case IDENTIFIER:
                dump("type -> identifier");
                res = new TypeName(cPos(), cLex());
                skip();
                break;
            case AT_LOGICAL:
                dump("type -> logical");
                res = Atom.LOG(cPos());
                skip();
                break;
            case AT_INTEGER:
                dump("type -> integer");
                res = Atom.INT(cPos());
                skip();
                break;
            case AT_STRING:
                dump("type -> string");
                res = Atom.STR(cPos());
                skip();
                break;
            case KW_ARR:
                Position startPos = cPos();
                checkErr(1, OP_LBRACKET);
                skip(2);
                checkErr(0, C_INTEGER);
                int size = Integer.parseInt(cLex());
                checkErr(1, OP_RBRACKET);
                dump("type -> arr [ int_constant ] type");
                skip(2);
                Type type = parseType();
                res = new Array(newPos(startPos, type.position), size, type);
                break;
            default:
                err("Expected: identifier, logical, integer, string or arr token as type");
        }
        return res;
    }

    private FunDef parseFunctionDefinition() { // TODO ?
        Position startPos = cPos();
        checkErr(0, IDENTIFIER);
        String name = cLex();
        checkErr(1, OP_LPARENT);
        dump("function_definition -> fun identifier ( parameters ) : type = expression");
        skip(2);
        List<Parameter> parameters = parseParameters();
        checkErr(0, OP_RPARENT);
        checkErr(1, OP_COLON);
        skip(2);
        Type type = parseType();
        checkErr(0, OP_ASSIGN);
        skip();
        Expr body = parseExpression();
        return new FunDef(newPos(startPos, body.position), name, parameters, type, body);
    }

    private TypeDef parseTypeDefinition() {
        checkErr(0, IDENTIFIER);
        String name = cLex();
        Position startPos = cPos();
        skip();

        checkErr(0, OP_COLON);
        dump("type_definition -> typ identifier : type");
        skip();

        Type type = parseType();
        Position endPos = type.position;
        return new TypeDef( newPos(startPos, endPos), name, type );
    }

    private List<Parameter> parseParameters() { // TODO ?
        dump("parameters -> parameter parameters2");
        Parameter t = parseParameter();
        List<Parameter> t2 = parseParameters2();

        List<Parameter> parameters = new ArrayList<>(); parameters.add(t);
        if (t2 != null) {
            parameters.addAll(t2);
        }
        return parameters; 
    }

    private Parameter parseParameter() { // TODO ?
        Position startPos = cPos();
        checkErr(0, IDENTIFIER);
        String name = cLex();
        checkErr(1, OP_COLON);
        dump("parameter -> identifier : type");
        skip(2);
        Type type = parseType();
        return new Parameter(newPos(startPos, type.position), name, type);
    }

    private List<Parameter> parseParameters2() { // TODO ?
        if (check(OP_COMMA)) {
            dump("parameters2 -> , parameter parameters2");
            skip();

            Parameter t = parseParameter();
            List<Parameter> t2 = parseParameters2();
            List<Parameter> parameters = new ArrayList<>(); parameters.add(t);
            if (t2 != null) {
                parameters.addAll(t2);
            }
            return parameters;
        }
        dump("parameters2 -> e");
        return null;
    }

    private Expr parseExpression() { // TODO
        dump("expression -> logical_ior_expression expression2");
        Expr t = parseLogicalIorExpression();
        return parseExpression2(t);
    }

    private Expr parseExpression2(Expr expression) { // TODO
        if (check(OP_LBRACE)) {
            checkErr(1, KW_WHERE);
            dump("expression2 -> { where definitions }");
            skip(2);
            Defs t = parseDefinitions();
            checkErr(0, OP_RBRACE);
            Position endPos = cPos();
            skip();
            return new Where(newPos(expression.position, endPos), expression, t); 
            // IMPORTANT: Position includes braces!
        } 
        else {
            dump("expression2 -> e");
            return expression;
        }
    }

    private Expr parseLogicalIorExpression() { // TODO ?
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        Expr left = parseLogicalAndExpression();
        return parseLogicalIorExpression2(left);
    }

    private Expr parseLogicalIorExpression2(Expr left_sub) { // TODO ?
        if (check(OP_OR)) {
            dump("logical_ior_expression2 -> | logical_and_expression logical_ior_expression2");
            skip();
            Expr right_sub = parseLogicalAndExpression();
            Expr left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.OR, right_sub);
            return parseLogicalIorExpression2(left_par);
        }
        else {
            dump("logical_ior_expression2 -> e");
            return left_sub;
        }
    }

    private Expr parseLogicalAndExpression() { // TODO ?
        dump("logical_and_expression -> compare_expression logical_and_expression2");
        Expr left = parseCompareExpression();
        return parseLogicalAndExpression2(left);
    }

    private Expr parseLogicalAndExpression2(Expr left_sub) { // TODO ?
        if (check(OP_AND)) {
            dump("logical_and_expression2 -> & compare_expression logical_and_expression2");
            skip();
            Expr right_sub = parseCompareExpression();
            Expr left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.AND, right_sub);
            return parseLogicalAndExpression2(left_par);
        }
        else {
            dump("logical_and_expression2 -> e");
            return left_sub;
        }
    }

    private Expr parseCompareExpression() { // TODO ?
        dump("compare_expression -> additive_expression compare_expression2");
        Expr left = parseAdditiveExpression();
        return parseCompareExpression2(left);
    }

    private Expr parseCompareExpression2(Expr left) { // TODO ?
        Expr right;
        switch (cToken()) {
            case OP_EQ:
                dump("compare_expression2 ->  == additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.EQ, right);
            case OP_NEQ:
                dump("compare_expression2 ->  != additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.NEQ, right);
            case OP_LT:
                dump("compare_expression2 ->  < additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.LT, right);
            case OP_GT:
                dump("compare_expression2 ->  > additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.GT, right);
            case OP_LEQ:
                dump("compare_expression2 ->  <= additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.LEQ, right);
            case OP_GEQ:
                dump("compare_expression2 ->  >= additive_expression");
                skip();
                right = parseAdditiveExpression();
                return new Binary(newPos(left.position, right.position), left, Operator.GEQ, right);
            default:
                dump("compare_expression2 -> e");
                return left;
        }
    }

    private Expr parseAdditiveExpression() { // TODO ?
        dump("additive_expression -> multiplicative_expression additive_expression2");
        Expr left = parseMultiplicativeExpression();
        return parseAdditiveExpression2(left);
    }

    private Expr parseAdditiveExpression2(Expr left_sub) { // TODO ?
        if (check(OP_ADD)) {
            dump("additive_expression2 -> + multiplicative_expression additive_expression2");
            skip();
            Expr right_sub = parseMultiplicativeExpression();
            Expr left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.ADD, right_sub);
            return parseAdditiveExpression2(left_par);
        }
        else if (check(OP_SUB)) {
            dump("additive_expression2 -> - multiplicative_expression additive_expression2");
            skip();
            Expr right_sub = parseMultiplicativeExpression();
            Expr left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.SUB, right_sub);
            return parseAdditiveExpression2(left_par);
        }
        else {
            dump("additive_expression2 -> e");
            return left_sub;
        }
    }

    private Expr parseMultiplicativeExpression() { // TODO ?
        dump("multiplicative_expression -> prefix_expression multiplicative_expression2");
        Expr left = parsePrefixExpression();
        return parseMultiplicativeExpression2(left);
    }

    private Expr parseMultiplicativeExpression2(Expr left_sub) { // TODO ?
        Expr right_sub;
        Expr left_par;
        switch (cToken()) {
            case OP_MUL:
                dump("multiplicative_expression2 -> * prefix_expression multiplicative_expression2");
                skip();
                right_sub = parsePrefixExpression();
                left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.MUL, right_sub);
                return parseMultiplicativeExpression2(left_par);

            case OP_DIV:
                dump("multiplicative_expression2 -> / prefix_expression multiplicative_expression2");
                skip();
                right_sub = parsePrefixExpression();
                left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.DIV, right_sub);
                return parseMultiplicativeExpression2(left_par);

            case OP_MOD:
                dump("multiplicative_expression2 -> % prefix_expression multiplicative_expression2");
                skip();
                right_sub = parsePrefixExpression();
                left_par = new Binary(newPos(left_sub.position, right_sub.position), left_sub, Operator.MOD, right_sub);
                return parseMultiplicativeExpression2(left_par);

            default:
                dump("multiplicative_expression2 -> e");
                return left_sub;
        }
    }

    private Expr parsePrefixExpression() { // TODO ? 
        // Check for arrays (if propper nesting level: prednost pred ostalimi operandi)
        // TODO check if !!!!op, ++++works ??
        Position startPos = cPos();
        Expr t;
        switch (cToken()) {
            case OP_ADD:
                dump("prefix_expression -> + prefix_expression");
                skip();
                t = parsePrefixExpression();
                return new Unary(newPos(startPos, t.position), t, Unary.Operator.ADD);
            case OP_SUB:
                dump("prefix_expression -> - prefix_expression");
                skip();
                t = parsePrefixExpression();
                return new Unary(newPos(startPos, t.position), t, Unary.Operator.SUB);
            case OP_NOT:
                dump("prefix_expression -> ! prefix_expression");
                skip();
                t = parsePrefixExpression();
                return new Unary(newPos(startPos, t.position), t, Unary.Operator.NOT);
            default:
                dump("prefix_expression -> postfix_expression");
                return parsePostfixExpression();
        }
    }

    private Expr parsePostfixExpression() { // TODO ?
        dump("postfix_expression -> atom_expression postfix_expression2");
        Expr left = parseAtomExpression();
        return parsePostfixExpression2(left);
    }

    private Expr parsePostfixExpression2(Expr left_sub) { // TODO TEST [expr][expr][expr] .. najrpej se gnezdi prvi, potem drugi ...        if (check(OP_LBRACKET)) {
        if (check(OP_LBRACKET)) {    
            dump("postfix_expression2 -> [ expression ] postfix_expression2");
            skip();
            Expr right_sub = parseExpression();
            checkErr(0, OP_RBRACKET);
            Position endPos = cPos();
            skip();
            Expr left_par = new Binary(newPos(left_sub.position, endPos), left_sub, Operator.ARR, right_sub);
            return parsePostfixExpression2(left_par);
        }
        else {
            dump("postfix_expression2 -> e");
            return left_sub;
        }
    }

    private Expr parseAtomExpression() { // TODO ?
        Position startPos = cPos();
        String value = cLex();
        Atom.Type type;
        Expr t;
        
        switch (cToken()) {
            case C_LOGICAL:
                dump("atom_expression -> log_constant");
                skip();
                type = Atom.Type.LOG;
                return new Literal(startPos, value, type);
            case C_INTEGER:
                dump("atom_expression -> int_constant");
                skip();
                type = Atom.Type.INT;
                return new Literal(startPos, value, type);
            case C_STRING:
                dump("atom_expression -> str_constant");
                skip();
                type = Atom.Type.STR;
                return new Literal(startPos, value, type);
            case IDENTIFIER: // call or name: expr -> foo(a,b,c) || abc
                dump("atom_expression -> identifier atom_expression2");
                skip();
                Block t2 = parseAtomExpression2();
                if (t2 == null) // id
                    return new Name(startPos, value);
                else // call
                    return new Call(newPos(startPos, t2.position), t2.expressions, value);
            case OP_LBRACE:
                dump("atom_expression -> { atom_expression3 }");
                skip();
                t = parseAtomExpression3();
                checkErr(0, OP_RBRACE);
                skip();
                return t;
            case OP_LPARENT:
                dump("atom_expression -> ( expressions )");
                skip();
                Block b = parseExpressions();
                checkErr(0, OP_RPARENT);
                Position endPos = cPos();
                skip();
                return new Block(newPos(startPos, endPos), b.expressions);
            default:
                err("Expected constant, identifier, { or (");
                return null;
        }
    }


    private Block parseAtomExpression2() { // TODO ?
        if (check(OP_LPARENT)) {
            dump("atom_expression2 -> ( expressions )");
            skip();
            Block t = parseExpressions();
            checkErr(0, OP_RPARENT);
            skip();
            return t;
        }
        else {
            dump("atom_expression2 -> e");
            return null;
        }
    }

     // IMPORTANT: pozicija okrog { ... }
    //                           ^      ^
    private Expr parseAtomExpression3() { // TODO ?
        Expr first;
        Expr last;
        Position startPos = prevPos();
        switch (cToken()) {
            case KW_WHILE:
                dump("atom_expression3 -> while expression : expression");
                skip();
                first = parseExpression();
                checkErr(0, OP_COLON);
                skip();
                last = parseExpression();
                return new While(newPos(startPos, cPos()), first, last); 
                                            // cPos(): oklepaji {} zraven pozicije!
            case KW_IF:
                dump("atom_expression3 -> if expression then expression atom_expression4");
                skip();
                first = parseExpression();
                checkErr(0, KW_THEN);
                skip();
                last = parseExpression();
                IfThenElse ifthen = new IfThenElse(newPos(startPos, cPos()), first, last); 
                                                            // cPos(): oklepaji {} zraven pozicije!
                return parseAtomExpression4(ifthen);
            case KW_FOR:
                dump("atom_expression3 -> for identifier = expression , expression , expression : expression");
                skip();
                checkErr(0, IDENTIFIER);
                Name ctr = new Name(cPos(), cLex());
                checkErr(1, OP_ASSIGN);
                skip(2);
                first = parseExpression();
                checkErr(0, OP_COMMA);
                skip();
                Expr mid = parseExpression();
                checkErr(0, OP_COMMA);
                skip();
                Expr mid2 = parseExpression();
                checkErr(0, OP_COLON);
                skip();
                last = parseExpression();
                return new For(newPos(startPos, cPos()), ctr, first, mid, mid2, last); // TODO test
                                            // cPos(): oklepaji {} zraven pozicije!
            default:
                dump("atom_expression3 -> expression = expression");
                first = parseExpression();
                checkErr(0, OP_ASSIGN);
                skip();
                last = parseExpression();
                return new Binary(newPos(startPos, last.position), first, Operator.ASSIGN, last);
        }
    }

    private Expr parseAtomExpression4(IfThenElse ifthen) { // TODO ?
        if (check(KW_ELSE)) {
            dump("atom_expression4 -> else expression");
            skip();
            Expr els = parseExpression();
            return new IfThenElse(newPos(ifthen.position, cPos()), ifthen.condition, ifthen.thenExpression, els); 
                                                // cPos(): oklepaji {} zraven pozicije!
        }
        else {
            dump("atom_expression4 -> e");
            return ifthen;
        }
    }

    private Block parseExpressions() { // TODO ? 
        dump("expressions -> expression expressions2");
        Expr t = parseExpression();
        Block t2 = parseExpressions2();
        List<Expr> exps = new ArrayList<>(); exps.add(t);
        Position pos = t.position;
        if (t2 != null) {
            exps.addAll(t2.expressions);
            pos = newPos(pos, t2.position);
        }
        return new Block(newPos(t.position, pos), exps);
    }

    private Block parseExpressions2() { // TODO ?
        if (check(OP_COMMA)) {
            dump("expressions2 -> , expression expressions2");
            skip();
            Expr t = parseExpression();
            Block t2 = parseExpressions2();
            List<Expr> exps = new ArrayList<>(); exps.add(t);
            Position pos = t.position;
            if (t2 != null) {
                exps.addAll(t2.expressions);
                pos = newPos(pos, t2.position);
            }
            return new Block(newPos(t.position, pos), exps);
        }
        else {
            dump("expressions2 -> e");
            return null;
        }
    }

    void skip(int num) {
        for (int i = 0; i < num; i++) {
            prevSymbol = stack.pop();
        }
    }

    void skip() {
        prevSymbol = stack.pop();
    }

    Symbol cSym() {
        return stack.peek();
    }

    TokenType cToken() {
        return stack.peek().tokenType;
    }

    String cLex() {
        return stack.peek().lexeme;
    }

    Position cPos() {
        return stack.peek().position;
    }

    Position prevPos() {
        return prevSymbol.position;
    }

    boolean check( TokenType type ) {
        return stack.peek().tokenType == type;
    }

    void err(String errorString) {
        Report.error(cPos(), "Syntax error on token " + cLex() + " : " + errorString);
    }

    void checkErr( int pos, TokenType expectedType ) {
        Symbol symbol = stack.get(stack.size() - pos - 1);
        TokenType actualType = symbol.tokenType;
        if (expectedType != actualType) {
            Report.error(symbol.position, "Syntax error: Expected token " + 
                expectedType.toString() + " but got " + symbol.lexeme + " (" + actualType.toString() + ")");
        }
    }

    private void dump(String production) {
        // remove empty productions
        //if (production.endsWith("-> e")) return;
        if (productionsOutputStream.isPresent()) {
            productionsOutputStream.get().println(production);
        }
    }

    Position newPos(Location start, Location end) {
        return new Position( start.line, start.column, end.line, end.column );
    }

    Position newPos(Location start, int endLine, int endCol) {
        return new Position( start.line, start.column, endLine, endCol );
    }

    Position newPos(int startLine, int startCol, Location end) {
        return new Position( startLine, startCol, end.line, end.column );
    }

    Position newPos(Position start, Position end) {
        return new Position( start.start.line, start.start.column, end.end.line, end.end.column );
    }
}
