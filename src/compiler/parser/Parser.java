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
import compiler.parser.ast.type.*;

// CHECK for BUGS: definitions inside { where definitions }

public class Parser {
    /**
     * Seznam leksikalnih simbolov.
     */
    // private final List<Symbol> symbols;

    /**
     * Sklad leksikalnih simbolov.
     */
    private Stack<Symbol> stack;

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
        dump("source -> definitions");
        Ast ast = parseDefinitions();
        if( ast == null) {
            err("No definition in source code!");
        }
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

    private Def parseDefinition() { // TODO
        Def res = null;
        Position startPos = cPos();
        if( check( KW_VAR ) ) {
            dump("definition -> variable_definition");
            skip();
            VarDef t = parseVariableDefinition();
            res = new VarDef(newPos(startPos, t.position), t.name, t.type);
        }
        else if( check( KW_FUN ) ) {
            dump("definition -> function_definition");
            skip();
            FunDef t = parseFunctionDefinition();
            res = new FunDef(newPos(startPos, t.position), t.name, t.parameters, t.type, t.body);
        }
        else if( check( KW_TYP ) ) {
            dump("definition -> type_definition");
            skip();
            TypeDef t = parseTypeDefinition();
            // correct position to include TYP keyword
            res = new TypeDef( newPos(startPos, t.position), t.name, t.type );
        } 
        else {
            if( check( EOF ) ) {
                err("Delete extra ';' at the end of file.");
            }
            err("Expected 'fun', 'typ' or 'var' keyword as definition.");
        }
        return res;
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

    private FunDef parseFunctionDefinition() { // TODO
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
        return new FunDef(newPos(startPos, cPos()), name, parameters, type, body); // OK POSition?
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
        Position startPos = cPos();
        dump("expression -> logical_ior_expression expression2");
        Expr t = parseLogicalIorExpression();
        Expr t2 = parseExpression2(t);
        return null; // TODO THIS
    }

    private Expr parseExpression2(Expr expression) { // TODO
        if (check(OP_LBRACE)) {
            checkErr(1, KW_WHERE);
            dump("expression2 -> { where definitions }");
            skip(2);
            Defs t = parseDefinitions();
            checkErr(0, OP_RBRACE);
            skip();
            return new Where(newPos(expression.position, cPos()), expression, t); 
            // IMPORTANT: Position includes braces!
        } 
        else {
            dump("expression2 -> e");
            return null;
        }
    }

    private Binary parseLogicalIorExpression() { // TODO
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        Binary left = parseLogicalAndExpression();
        Binary right = parseLogicalIorExpression2(left);
    }

    private Binary parseLogicalIorExpression2(Binary left) { // TODO
        if (check(OP_OR)) {
            dump("logical_ior_expression2 -> | logical_and_expression logical_ior_expression2");
            skip();
            Binary left = parseLogicalAndExpression();
            Binary right = parseLogicalIorExpression2(left);
        }
        else {
            dump("logical_ior_expression2 -> e");
        }
    }

    private void parseLogicalAndExpression() { // TODO
        dump("logical_and_expression -> compare_expression logical_and_expression2");
        parseCompareExpression();
        parseLogicalAndExpression2();
    }

    private Binary parseLogicalAndExpression2(Binary left) { // TODO
        if (check(OP_AND)) {
            dump("logical_and_expression2 -> & compare_expression logical_and_expression2");
            skip();
            parseCompareExpression();
            parseLogicalAndExpression2();
        }
        else {
            dump("logical_and_expression2 -> e");

        }
    }

    private void parseCompareExpression() { // TODO
        dump("compare_expression -> additive_expression compare_expression2");
        parseAdditiveExpression();
        parseCompareExpression2();
    }

    private void parseCompareExpression2() { // TODO
        switch (cToken()) {
            case OP_EQ:
                dump("compare_expression2 ->  == additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            case OP_NEQ:
                dump("compare_expression2 ->  != additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            case OP_LT:
                dump("compare_expression2 ->  < additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            case OP_GT:
                dump("compare_expression2 ->  > additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            case OP_LEQ:
                dump("compare_expression2 ->  <= additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            case OP_GEQ:
                dump("compare_expression2 ->  >= additive_expression");
                skip();
                parseAdditiveExpression();
                break;
            default:
                dump("compare_expression2 -> e");
                break;
        }
    }

    private void parseAdditiveExpression() { // TODO
        dump("additive_expression -> multiplicative_expression additive_expression2");
        parseMultiplicativeExpression();
        parseAdditiveExpression2();
    }

    private void parseAdditiveExpression2() { // TODO
        if (check(OP_ADD)) {
            dump("additive_expression2 -> + multiplicative_expression");
            skip();
            parseMultiplicativeExpression();
        }
        else if (check(OP_SUB)) {
            dump("additive_expression2 -> - multiplicative_expression");
            skip();
            parseMultiplicativeExpression();
        }
        else {
            dump("additive_expression2 -> e");
        }
    }

    private void parseMultiplicativeExpression() { // TODO
        dump("multiplicative_expression -> prefix_expression multiplicative_expression2");
        parsePrefixExpression();
        parseMultiplicativeExpression2();
    }

    private void parseMultiplicativeExpression2() { // TODO
        switch (cToken()) {
            case OP_MUL:
                dump("multiplicative_expression2 -> * prefix_expression");
                skip();
                parsePrefixExpression();
                break;
            
            case OP_DIV:
                dump("multiplicative_expression2 -> / prefix_expression");
                skip();
                parsePrefixExpression();
                break;

            case OP_MOD:
                dump("multiplicative_expression2 -> % prefix_expression");
                skip();
                parsePrefixExpression();
                break;
            default:
                dump("multiplicative_expression2 -> e");
                break;
        }
    }

    private void parsePrefixExpression() { // TODO
        switch (cToken()) {
            case OP_ADD:
                dump("prefix_expression -> + prefix_expression");
                skip();
                parsePrefixExpression();
            case OP_SUB:
                dump("prefix_expression -> - prefix_expression");
                skip();
                parsePrefixExpression();
            case OP_NOT:
                dump("prefix_expression -> ! prefix_expression");
                skip();
                parsePrefixExpression();
            default:
                dump("prefix_expression -> postfix_expression");
                parsePostfixExpression();
                break;
        }
    }

    private void parsePostfixExpression() { // TODO
        dump("postfix_expression -> atom_expression postfix_expression2");
        parseAtomExpression();
        parsePostfixExpression2();
    }

    private void parsePostfixExpression2() { // TODO
        if (check(OP_LBRACKET)) {
            dump("postfix_expression2 -> [ expression ] postfix_expression2");
            skip();
            parseExpression();
            checkErr(0, OP_RBRACKET);
            skip();
            parsePostfixExpression2();
        }
        else {
            dump("postfix_expression2 -> e");
        }
    }

    private Expr parseAtomExpression() { // TODO
        Expr res = null;

        Position startPos = cPos();
        String value = cLex();
        Atom.Type type;
        
        switch (cToken()) {
            case C_LOGICAL:
                dump("atom_expression -> log_constant");
                skip();
                type = Atom.Type.LOG;
                res = new Literal(startPos, value, type);
                break;
            case C_INTEGER:
                dump("atom_expression -> int_constant");
                skip();
                type = Atom.Type.INT;
                res = new Literal(startPos, value, type);
                break;
            case C_STRING:
                dump("atom_expression -> str_constant");
                skip();
                type = Atom.Type.STR;
                res = new Literal(startPos, value, type);
                break;
            case IDENTIFIER:
                dump("atom_expression -> identifier atom_expression2");
                skip();
                parseAtomExpression2();
                break;
            case OP_LBRACE:
                dump("atom_expression -> { atom_expression3 }");
                skip();
                parseAtomExpression3();
                checkErr(0, OP_RBRACE);
                skip();
                break;
            case OP_LPARENT:
                dump("atom_expression -> ( expressions )");
                skip();
                parseExpressions();
                checkErr(0, OP_RPARENT);
                skip();
                break;
            default:
                err("Expected constant, identifier, { or (");
        }
        return res;
    }


    private void parseAtomExpression2() { // TODO
        if (check(OP_LPARENT)) {
            dump("atom_expression2 -> ( expressions )");
            skip();
            parseExpressions();
            checkErr(0, OP_RPARENT);
            skip();
        }
        else {
            dump("atom_expression2 -> e");
        }
    }

    private void parseAtomExpression3() { // TODO
        switch (cToken()) {
            case KW_WHILE:
                dump("atom_expression3 -> while expression : expression");
                skip();
                parseExpression();
                checkErr(0, OP_COLON);
                skip();
                parseExpression();
                break;
            case KW_IF:
                dump("atom_expression3 -> if expression then expression atom_expression4");
                skip();
                parseExpression();
                checkErr(0, KW_THEN);
                skip();
                parseExpression();
                parseAtomExpression4();
                break;
            case KW_FOR:
                dump("atom_expression3 -> for identifier = expression , expression , expression : expression");
                checkErr(1, IDENTIFIER);
                checkErr(2, OP_ASSIGN);
                skip(3);
                parseExpression();
                checkErr(0, OP_COMMA);
                skip();
                parseExpression();
                checkErr(0, OP_COMMA);
                skip();
                parseExpression();
                checkErr(0, OP_COLON);
                skip();
                parseExpression();
                break;
            default:
                dump("atom_expression3 -> expression = expression");
                parseExpression();
                checkErr(0, OP_ASSIGN);
                skip();
                parseExpression();
                break;
        }
    }

    private void parseAtomExpression4() { // TODO
        if (check(KW_ELSE)) {
            dump("atom_expression4 -> else expression");
            skip();
            parseExpression();
        }
        else {
            dump("atom_expression4 -> e");
        }
    }

    private void parseExpressions() { // TODO
        dump("expressions -> expression expressions2");
        parseExpression();
        parseExpressions2();
    }

    private void parseExpressions2() { // TODO
        if (check(OP_COMMA)) {
            dump("expressions2 -> , expression expressions2");
            skip();
            parseExpression();
            parseExpressions2();
        }
        else {
            dump("expressions2 -> e");
        }
    }

    void skip(int num) {
        for (int i = 0; i < num; i++) {
            stack.pop();
        }
    }

    void skip() {
        stack.pop();
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
