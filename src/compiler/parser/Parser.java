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

    void parseDefinitions() {
        dump("definitions -> definition definitions2");
        parseDefinition();
	    parseDefinitions2();
    }

    void parseDefinitions2() {
        switch(cToken()) {
        case OP_SEMICOLON: 
            dump("definitions2 -> ; definition definitions2");
            parseDefinition();
            parseDefinitions2();
            break;
        case EOF:
            // end of definitions
            dump("definitions2 -> $");
            skip();
            // EOF: end of file $
            break;
        default:
            err("Expected ';' or EOF token");
        }
    }

    void parseDefinition() {
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

    void parseVariableDefinition() {
        if (check(IDENTIFIER)) {
            checkErr(1, OP_COLON);
            dump("variable_definition -> var identifier : type");
            skip(2);
            parseType();
        } else {
            err("Expected identifier as variable definition");
        }
    }

    void parseType() {
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

    void parseFunctionDefinition() {
        // TODO
    }

    void parseTypeDefinition() {
        // TODO
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
