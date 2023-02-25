/**
 * @Author: basaj
 * @Description: Leksikalni analizator.
 */

package compiler.lexer;

import static common.RequireNonNull.requireNonNull;
import static compiler.lexer.TokenType.*;
import compiler.lexer.Position.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Report;

public class Lexer {
    /**
     * Izvorna koda.
     */
    private final String source;

    /**
     * Preslikava iz ključnih besed v vrste simbolov.
     */
    private final static Map<String, TokenType> keywordMapping;

    static {
        keywordMapping = new HashMap<>();
        for (var token : TokenType.values()) {
            var str = token.toString();
            if (str.startsWith("KW_")) {
                keywordMapping.put(str.substring("KW_".length()).toLowerCase(), token);
            }
            if (str.startsWith("AT_")) {
                keywordMapping.put(str.substring("AT_".length()).toLowerCase(), token);
            }
        }
    }

    /**
     * Ustvari nov analizator.
     * 
     * @param source Izvorna koda programa.
     */
    public Lexer(String source) {
        requireNonNull(source);
        this.source = source;
    }

    /**
     * Izvedi leksikalno analizo.
     * 
     * @return seznam leksikalnih simbolov.
     */
    // '''' je en narekovaj v narekovajih "'"
    // 'bla' je string const
    // Pravilo: niz more biti zakljucen: ''' je napaka
    // Pravilo: vedno dodamo EOF (zadnji simbol)
    // Pravilo: najdaljse ujemanje - pozresno
    // Pravilo: tabulator je premik za 4
    public List<Symbol> scan() {
        var symbols = new ArrayList<Symbol>();
    
        // todo: implementacija leksikalne analize
        final int startPos = 1;
        var currPosition = new Position(startPos,startPos,startPos,startPos);
        //var startPosition = new Position(startPos,startPos,startPos,startPos);
        

        String lexeme = "";
        //Category prevCategory = Category.NO_CATEGORY;
        State state = State.START;

        for (int pos = 0; pos < source.length(); ++pos) {
            char c = source.charAt(pos);
            switch (state) {
                case START:
                case WHITESPACE:
                switch (categorize(c)) {
                    case LETTER:
                        currPosition.end.column += 1;
                        lexeme += c;
                        state = State.WORD;
                        break;
                    case NUMBER: 
                        currPosition.end.column += 1;
                        lexeme += c;
                        state = State.NUM_CONST;
                        break;
                    case SYMBOL: 
                        currPosition.end.column += 1;
                        lexeme += c;
                        state = State.SYMBOL;
                        break;
                    case SPACE:
                        currPosition.end.column += 1;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WHITESPACE;
                        break;
                    case TAB: 
                        currPosition.end.column += 4;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WHITESPACE;
                        break;
                    case NEWLINE:
                        currPosition.start.line += 1;
                        currPosition.end.line += 1;
                        currPosition.start.column = startPos;
                        currPosition.end.column = startPos;
                        state = State.WHITESPACE;
                        break;
                    case STRSYM: 
                        currPosition.end.column += 1;
                        lexeme += c;
                        state = State.STR_CONST;
                        break;
                    case COMMENT:
                        currPosition.end.column += 1;
                        lexeme += c;
                        state = State.COMMENT;
                        break;
                    
                    case OTHER:
                    case NO_CATEGORY:
                        Report.error(currPosition, "Bad symbol " + c);
                        break;
                    default:
                        Report.error(currPosition, "Unhandled symbol:" + c);
                        break;
                }
                break;

                case NUM_CONST: 
                switch (categorize(c)) {
                    case COMMENT:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER ));
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;
                    case LETTER:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "" + c;
                        state = State.WORD;
                        break;
                    case NEWLINE:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "";
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        currPosition.start.column = startPos;
                        currPosition.end.column = startPos;
                        state = State.WHITESPACE;
                        break;
                    case TAB:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "";
                        currPosition.end.column += 3;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WHITESPACE;
                        break;
                    case SPACE:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case NUMBER:
                        currPosition.end.column += 1;
                        lexeme += c;
                        break;
                    case STRSYM:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "" + c;
                        state = State.STR_CONST;
                        break;
                    case SYMBOL:
                        symbols.add(createSymbol(currPosition, lexeme, C_INTEGER));
                        lexeme = "" + c;
                        state = State.SYMBOL;
                        break;

                    case OTHER:
                    case NO_CATEGORY:
                        Report.error(currPosition, "Bad symbol: " + c);
                        break;
                    default:
                        Report.error(currPosition, "Unhandled symbol: " + c);
                        break;
                }
                break;

                case WORD: // ab_asd?
                switch (categorize(c)) {
                    case LETTER:
                        currPosition.end.column += 1;
                        lexeme += c;
                        break;
                    case NUMBER:
                        currPosition.end.column += 1;
                        lexeme += c;
                        break;
                    case SYMBOL: 
                        symbols.add(createSymbol(currPosition, lexeme));
                        lexeme = "" + c; 
                        state = State.SYMBOL;
                        break;
                    case SPACE:
                        symbols.add(createSymbol(currPosition, lexeme));
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case TAB:
                        symbols.add(createSymbol(currPosition, lexeme));
                        currPosition.end.column += 3;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case NEWLINE:
                        symbols.add(createSymbol(currPosition, lexeme));
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        currPosition.start.column = startPos;
                        currPosition.end.column = startPos;
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case STRSYM:
                        symbols.add(createSymbol(currPosition, lexeme));
                        lexeme = "" + c;
                        state = State.STR_CONST;
                        break;
                    case COMMENT:
                        symbols.add(createSymbol(currPosition, lexeme));
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;

                    case OTHER: // no others like ? in ID and KW ,...
                    case NO_CATEGORY:
                        Report.error(currPosition, "Bad symbol: " + c);
                        break;
                    default:
                        Report.error(currPosition, "Unhandled symbol:" + c);
                        break;
                }
                break;

                case SYMBOL: // +   - =  == =< . , ...
                    switch(categorize(c)) {
                        case SYMBOL:
                            if (c == '=' && lexeme.length() == 1 && "!=<>".contains(lexeme)) {
                                lexeme += c;
                                currPosition.end.column += 1;
                            }
                            else { // if symbol is length 2 or smth else, start a new one
                                symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                                lexeme = "" + c;
                            }
                            break;
                        case SPACE:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            lexeme = "";
                            state = State.WHITESPACE;
                            break;
                        case TAB:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            currPosition.end.column += 3;
                            currPosition.start.column = currPosition.end.column;
                            lexeme = "";
                            state = State.WHITESPACE;
                            break;
                        case NEWLINE:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            currPosition.end.line += 1;
                            currPosition.start.line = currPosition.end.line;
                            currPosition.end.column = startPos;
                            currPosition.start.column = startPos;
                            lexeme = "";
                            state = State.WHITESPACE;
                            break;
                        case NUMBER:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            lexeme = "" + c;
                            state = State.NUM_CONST;
                            break;
                        case LETTER:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            lexeme = "" + c;
                            state = State.WORD;
                            break;
                        case STRSYM:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            lexeme = "" + c;
                            state = State.STR_CONST;
                            break;
                        case COMMENT:
                            symbols.add(createSymbol(currPosition, lexeme, resolveLexeme(lexeme, state)));
                            lexeme = "" + c;
                            state = State.COMMENT;
                            break;

                        case OTHER:
                        case NO_CATEGORY:
                            Report.error(currPosition, "Bad symbol: " + c);
                            break;
                        default:
                            Report.error(currPosition, "Unhandled symbol:" + c);
                            break;
                    }
                break;

                default: // TODO remove
                    break;

            }
        }
        symbols.add(new Symbol(currPosition.copy(), EOF, ""));
        return symbols;
    }

    private enum State {
        START,
        NUM_CONST, // int=0123
        WORD, // ID, KW, AT, true, false
        SYMBOL,
        COMMENT, // # ... \n
        STR_CONST, // 'asdasd''asd?_!'
        WHITESPACE,
    }

    private enum Category {
        LETTER, // _ is a letter
        NUMBER, // 0 - 9
        SYMBOL, // + - * / % & | ! == != < > <= >= ( ) [ ] { } : ; . , =
        SPACE, // 32
        TAB, // 9
        NEWLINE, // 10 13
        STRSYM, // '
        COMMENT, // #
        OTHER, // other in stringconst
        NO_CATEGORY // code is not in correct range
    }

    private TokenType resolveLexeme(String lexeme, State state) {
        TokenType type = EOF;
        if (lexeme.equals("+")) type = OP_ADD;
        else if (lexeme.equals("-")) type = OP_SUB;
        else if (lexeme.equals("*")) type = OP_MUL;
        else if (lexeme.equals("/")) type = OP_DIV;
        else if (lexeme.equals("%")) type = OP_MOD;

        else if (lexeme.equals("&")) type = OP_AND;
        else if (lexeme.equals("|")) type = OP_OR;
        else if (lexeme.equals("!")) type = OP_NOT;

        else if (lexeme.equals("==")) type = OP_EQ;
        else if (lexeme.equals("!=")) type = OP_NEQ;
        else if (lexeme.equals("<")) type = OP_LT;
        else if (lexeme.equals(">")) type = OP_GT;
        else if (lexeme.equals("<=")) type = OP_LEQ;
        else if (lexeme.equals(">=")) type = OP_GEQ;

        else if (lexeme.equals("(")) type = OP_LPARENT;
        else if (lexeme.equals(")")) type = OP_RPARENT;
        else if (lexeme.equals("[")) type = OP_LBRACKET;
        else if (lexeme.equals("]")) type = OP_RBRACKET;
        else if (lexeme.equals("{")) type = OP_LBRACE;
        else if (lexeme.equals("}")) type = OP_RBRACE;

        else if (lexeme.equals(":")) type = OP_COLON;
        else if (lexeme.equals(";")) type = OP_SEMICOLON;
        else if (lexeme.equals(".")) type = OP_DOT;
        else if (lexeme.equals(",")) type = OP_COMMA;
        else if (lexeme.equals("=")) type = OP_ASSIGN;
        else Report.error("Lexeme " + lexeme + "can't be a symbol type");
        return type;
    } 

    private Symbol createSymbol(Position pos, String lexeme) {
        TokenType type = keywordMapping.get(lexeme);
        if (type == null) { // determine type
            if (lexeme.equals("true") || lexeme.equals("false")) {
                type = C_LOGICAL;
            }
            
            else {
                type = IDENTIFIER;
                //Report.error(pos, "Can't resolve tokentype from lexeme: " + lexeme);
            }
        }
        return createSymbol(pos, lexeme, type);
    }

    private Symbol createSymbol(Position pos, String lexeme, TokenType type) {
        Symbol newSymbol = new Symbol(new Position(
                                        pos.start.line, 
                                        pos.start.column, 
                                        pos.start.line, 
                                        pos.start.column+lexeme.length()-1), 
                                            type, lexeme);
        pos.end.column = pos.start.column+lexeme.length(); // because above ^^^^ +1x
        pos.start.column = pos.end.column;
        return newSymbol;
    }

    private Category categorize(char c) {
        if (c == 9) return Category.TAB;
        else if (c == 10 || c == 13) return Category.NEWLINE;
        else if (c == 32) return Category.SPACE;
        else if (c == 35) return Category.COMMENT;
        else if (c == 39) return Category.STRSYM;
        else if (c >= 48 && c <= 57) return Category.NUMBER;
        else if ("+-*/%&|!<>=()[]{}:;.,".indexOf(c) >= 0) return Category.SYMBOL;
        else if (c >= 65 && c <= 90 || c >= 97 && c <= 122 || c == 95) return Category.LETTER;
        else if (c >= 32 && c <= 126) return Category.OTHER;
        return Category.NO_CATEGORY;
    }
}
