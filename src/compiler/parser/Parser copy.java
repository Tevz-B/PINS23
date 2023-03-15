/**
 * @Author: turk
 * @Description: Sintaksni analizator.
 */

package compiler.parser;

import static compiler.lexer.TokenType.*;
import static common.RequireNonNull.requireNonNull;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import common.Report;
import compiler.lexer.Position;
import compiler.lexer.Symbol;
import compiler.lexer.TokenType;

public class Parser2 {
    /**
     * Seznam leksikalnih simbolov.
     */
    private final List<Symbol> symbols;

    /**
     * Sklad leksikalnih simbolov.
     */
    private Stack<Symbol> stack;

    /**
     * Ciljni tok, kamor izpisujemo produkcije. Če produkcij ne želimo izpisovati,
     * vrednost opcijske spremenljivke nastavimo na Optional.empty().
     */
    private final Optional<PrintStream> productionsOutputStream;

    public Parser2(List<Symbol> symbols, Optional<PrintStream> productionsOutputStream) {
        requireNonNull(symbols, productionsOutputStream);
        this.symbols = symbols;
        this.productionsOutputStream = productionsOutputStream;

        this.stack = new Stack<Symbol>();
        Collections.reverse(symbols);
        stack.addAll(symbols);
    }

    /**
     * Izvedi sintaksno analizo.
     */
    public void parse() {
        parseSource();
    }
    /*
     * TODO oznaci produkcije s stevilkami
     * NPR: enum in ga sprintaj z metodo toString()
     */

    private void parseSource() {
        parseDefinitions();
    }


    private void parseDefinitions() {
        dump("definitions -> definition definitions2");
        parseDefinition();
	    parseDefinitions2();
    }

    private void parseDefinitions2() {
        switch(cToken()) {
        case OP_SEMICOLON: 
            dump("definitions2 -> ; definition definitions2");
            skip();
            parseDefinition();
            parseDefinitions2();
            break;
        case EOF:
            // end of definitions
            dump("definitions2 -> e");
            dump("$ -> $"); // ?
            skip();
            // EOF: end of file $
            break;
        default:
            err("Expected ';' or EOF token");
        }
    }

    private void parseDefinition() {
        if( check( KW_VAR ) ) {
            dump("definition -> variable_definition");
            skip();
            parseVariableDefinition();
        }
        else if( check( KW_FUN ) ) {
            dump("definition -> function_definition");
            skip();
            parseFunctionDefinition();
        }
        else if( check( KW_TYP ) ) {
            dump("definition -> type_definition");
            skip();
            parseTypeDefinition();
        } else {
            err("Expected 'fun', 'typ' or 'var' keyword as definition");
        }
    }

    private void parseVariableDefinition() {
        if (check(IDENTIFIER)) {
            checkErr(1, OP_COLON);
            dump("variable_definition -> var identifier : type");
            skip(2);
            parseType();
        } else {
            err("Expected identifier as variable definition");
        }
    }

    private Type parseType() {
        switch(cToken()) {
            case IDENTIFIER:
                dump("type -> identifier");
                skip();
                // end of Type
                break;
            case AT_LOGICAL:
                dump("type -> logical");
                skip();
                // end of Type
                break;
            case AT_INTEGER:
                dump("type -> integer");
                skip();
                // end of Type
                break;
            case AT_STRING:
                dump("type -> string");
                skip();
                // end of Type
                break;
            case KW_ARR:
                checkErr(1, OP_LBRACKET);
                checkErr(2, C_INTEGER);
                checkErr(3, OP_RBRACKET);
                dump("type -> arr [ int_constant ] type");
                skip(4);
                parseType();
                break;
            default:
                err("Expected: identifier, logical, integer, string or arr token as type");
        }
    }

    private void parseFunctionDefinition() {
        checkErr(0, IDENTIFIER);
        checkErr(1, OP_LPARENT);
        dump("function_definition -> fun identifier ( parameters ) : type = expression");
        skip(2);
        parseParameters();
        checkErr(0, OP_RPARENT);
        checkErr(1, OP_COLON);
        skip(2);
        var type = parseType();
        checkErr(0, OP_ASSIGN);
        skip();
        parseExpression();
    }

    private void parseTypeDefinition() {
        checkErr(0, IDENTIFIER);
        checkErr(1, OP_COLON);
        dump("type_definition -> typ identifier : type");
        skip(2);
        parseType();
    }

    private void parseParameters() {
        dump("parameters -> parameter parameters2");
        parseParameter();
        parseParameters2();
    }

    private void parseParameter() {
        checkErr(0, IDENTIFIER);
        checkErr(1, OP_COLON);
        dump("parameter -> identifier : type");
        skip(2);
        parseType();
    }

    private void parseParameters2() {
        if (check(OP_COMMA)) {
            dump("parameters2 -> , parameter parameters2");
            skip();
            parseParameter();
            parseParameters2();
        } 
        else {
            dump("parameters2 -> e");
            // end of parameters
        }
    }

    private Expression parseExpression() {
        dump("expression -> logical_ior_expression expression2");
        parseLogicalIorExpression();
        parseExpression2();
    }

    private void parseExpression2() {
        if (check(OP_LBRACE)) {
            checkErr(1, KW_WHERE);
            dump("expression2 -> { where definitions }");
            skip(2);
            parseDefinitions();
        } 
        else {
            dump("expression2 -> e");
            // end of expressions
        }
    }

    private Expression parseLogicalIorExpression() {
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        var e1  = parseLogicalAndExpression();
        var e2 = parseLogicalIorExpression2(e1);
        return e2;
    }

    private void parseLogicalIorExpression2() {
        if (check(OP_OR)) {
            dump("logical_ior_expression2 -> | logical_and_expression logical_ior_expression2");
            skip();
            var right = parseLogicalAndExpression();
            var bin = new Binary(...);
            parseLogicalIorExpression2(bin);
        }
        else {
            dump("logical_ior_expression2 -> e");
        }
    }

    private void parseLogicalAndExpression() {
        dump("logical_and_expression -> compare_expression logical_and_expression2");
        parseCompareExpression();
        parseLogicalAndExpression2();
    }

    private void parseLogicalAndExpression2() {
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

    private void parseCompareExpression() {
        dump("compare_expression -> additive_expression compare_expression2");
        parseAdditiveExpression();
        parseCompareExpression2();
    }

    private void parseCompareExpression2() {
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

    private void parseAdditiveExpression() {
        dump("additive_expression -> multiplicative_expression additive_expression2");
        parseMultiplicativeExpression();
        parseAdditiveExpression2();
    }

    private void parseAdditiveExpression2() {
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

    private void parseMultiplicativeExpression() {
        dump("multiplicative_expression -> prefix_expression multiplicative_expression2");
        parsePrefixExpression();
        parseMultiplicativeExpression2();
    }

    private void parseMultiplicativeExpression2() {
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

    private void parsePrefixExpression() {
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

    private void parsePostfixExpression() {
        dump("postfix_expression -> atom_expression postfix_expression2");
        parseAtomExpression();
        parsePostfixExpression2();
    }

    private void parsePostfixExpression2() {
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

    private void parseAtomExpression() {
        switch (cToken()) {
            case C_LOGICAL:
                dump("atom_expression -> log_constant");
                skip();
                break;
            case C_INTEGER:
                dump("atom_expression -> int_constant");
                skip();
                break;
            case C_STRING:
                dump("atom_expression -> str_constant");
                skip();
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
    }
/* 
    private AST parseAtomExpressionDEL() {
        switch (cToken()) {
            case C_LOGICAL:
                dump("atom_expression -> log_constant");
                skip();
                return ASTlog( ... );
                break;
            case C_INTEGER:
                dump("atom_expression -> int_constant");
                skip();
                return ASTint( ... );

                break;
            case C_STRING:
                dump("atom_expression -> str_constant");
                skip();
                return ASTstr( ... );

                break;
            case IDENTIFIER:
                dump("atom_expression -> identifier atom_expression2");
                skip();
                parseAtomExpression2(smth);
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
    }
    */

    private void parseAtomExpression2() {
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

    private void parseAtomExpression3() {
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

    private void parseAtomExpression4() {
        if (check(KW_ELSE)) {
            dump("atom_expression4 -> else expression");
            skip();
            parseExpression();
        }
        else {
            dump("atom_expression4 -> e");
        }
    }

    private void parseExpressions() {
        dump("expressions -> expression expressions2");
        var exp = parseExpression();
        parseExpressions2();
    }

    private void parseExpressions2() {
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

    /**
     * Izpiše produkcijo na izhodni tok.
     */
    private void dump(String production) {
        // remove empty productions
        //if (production.endsWith("-> e")) return;
        if (productionsOutputStream.isPresent()) {
            productionsOutputStream.get().println(production);
        }
    }
}

indent+= 2;

left.accept(this);
right.accept(this);

indent -= 2;

correct:

inNewScope(()-> {
    left.accept();
    right.accept();
})

inNewScope(VoidOperator op) {
    indent += 2;
    op.apply();
    indent -= 2;
}
