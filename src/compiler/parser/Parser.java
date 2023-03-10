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

public class Parser {
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

    public Parser(List<Symbol> symbols, Optional<PrintStream> productionsOutputStream) {
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

    private void parseSource() {
        parseDefinitions();
    }

    /*
     *  definitions -> definition definitions2 .
	        definitions2 ->  .
	        definitions2 -> ";" definition definitions2 .
     */

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

    private void parseType() {
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
        parseType();
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

    private void parseExpression() {
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

    private void parseLogicalIorExpression() {
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        parseLogicalAndExpression();
        parseLogicalIorExpression2();
    }

    private void parseLogicalIorExpression2() {
        if (check(OP_OR)) {
            dump("logical_ior_expression2 -> | logical_and_expression logical_ior_expression2");
            skip();
            parseLogicalAndExpression();
            parseLogicalIorExpression2();
        }
        else {
            dump("logical_ior_expression2 ->  e");
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
                dump("compare_expression2 ->  e");
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
            dump("additive_expression2 ->  e");
        }
    }

    private void parseMultiplicativeExpression() {

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
        if (productionsOutputStream.isPresent()) {
            productionsOutputStream.get().println(production);
        }
    }
}
