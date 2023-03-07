package compiler.syntax;

import java.util.Stack;

import common.Report;
import compiler.lexer.Position;
import compiler.lexer.Symbol;
import compiler.lexer.TokenType;

public class SyntaxDel {

    Stack<Symbol> stack;

    void parseDefinitions() {
        dump("definitions -> definition definitions2");
        parseDefinition();
	    parseDefinitions2();
    }

    void parseDefinitions2() {
        switch(cToken()) {
        case OP_SEMICOLON: 
            dump("definitions2 -> ; definitions2");
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
        if( check( TokenType.KW_VAR ) ) {
            dump("definition -> variable_definition");
            skip();
            parseVariableDefinition();
        }
        else if( check( TokenType.KW_FUN ) ) {
            dump("definition -> function_definition");
            skip();
            parseFunctionDefinition();
        }
        else if( check( TokenType.KW_TYP ) ) {
            dump("definition -> type_definition");
            skip();
            parseTypeDefinition();
        } else {
            err("Expected 'fun', 'typ' or 'var' keyword as definition");
        }
    }

    void parseVariableDefinition() {
        if (check(TokenType.IDENTIFIER)) {
            checkErr(1, TokenType.OP_COLON);
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
                break;
            case AT_LOGICAL:
                break;
            case AT_INTEGER:
                break;
            case AT_STRING:
                break;
            case KW_ARR:
                checkErr(1, TokenType.OP_LBRACKET);
                checkErr(2, TokenType.C_INTEGER);
                checkErr(3, TokenType.OP_RBRACKET);
                dump("type -> arr [ int_constant ] type");
                skip(4);
                parseType();
                break;
            default:
                err("Expected: identifier, logical, integer, string or arr token as type");
        }
    }

    void parseFunctionDefinition() {
        
    }

    void parseTypeDefinition() {
        
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

    void dump( String string ) {
        System.out.println( string );
        return;
    }

    void err(String errorString) {
        Report.error(cPos(), "Syntax error on token " + cLex() + " : " + errorString);
    }

    void checkErr( int pos, TokenType expectedType ) {
        Symbol symbol = stack.get(pos);
        TokenType actualType = symbol.tokenType;
        if (expectedType != actualType) {
            Report.error(symbol.position, "Syntax error: Expected token " + 
                expectedType.toString() + " but got " + symbol.lexeme + " (" + actualType.toString() + ")");
        }
    }
 





}


