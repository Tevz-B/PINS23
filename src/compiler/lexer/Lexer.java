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
                }
                break;

                case NUM_CONST: 
                switch (categorize(c)) {
                    case COMMENT:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.column += 1;
                        currPosition.start.column = currPosition.end.column;
                        state = State.COMMENT;
                        break;
                    case LETTER:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.column += 1;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WORD;
                        break;
                    case NEWLINE:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        currPosition.start.column = startPos;
                        currPosition.end.column = startPos;
                        state = State.WHITESPACE;
                        break;
                    case TAB:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.column += 4;
                        currPosition.start.column = currPosition.end.column;
                        state = State.WHITESPACE;
                        break;
                    case SPACE:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        state = State.WHITESPACE;
                        break;
                    
                    case NUMBER:
                        currPosition.end.column += 1;
                        lexeme += c;
                        break;
                    
                    case STRSYM:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        state = State.STR_CONST;
                        break;
                    case SYMBOL:
                        symbols.add(new Symbol(currPosition, C_INTEGER, lexeme));
                        lexeme = "" + c;
                        currPosition.end.line += 1;
                        currPosition.start.line = currPosition.end.line;
                        state = State.SYMBOL;
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

                case WORD:
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
                        symbols.add(new Symbol(currPosition, KW_ID_AT_true_false, lexeme));
                        currPosition.end.column += 1;
                        currPosition.start.column = currPosition.end.column;
                        lexeme = "" + c; 
                        state = State.SYMBOL;

                        break;
                }
                break;

            }
        }
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

    private Symbol createSymbol(Position pos, String lexeme) {
        TokenType type = keywordMapping.get(lexeme);
        if (type == null) { // determine type
            
        }
        return new Symbol(pos, type, lexeme);
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

/*
 * 
 * switch(categorize(c)) {
                case LETTER:
                    switch(prevCategory) {
                        case NO_CATEGORY:
                        case LETTER:
                        case STRSYM:
                            currPosition.end.column += 1;
                            break;
                        default:
                            createSymbol(currPosition, lexeme);
                            break;
                        
                    }
                    break;
                case NUMBER: 
                    break;
                case SYMBOL: 
                    break;
                case SPACE:
                    if (prevCategory == Category.)
                    currPosition.start.column += 1;
                    currPosition.end.column += 1;
                    break;
                case TAB: 
                    currPosition.start.column += 4;
                    currPosition.end.column += 4;
                    break;
                case NEWLINE:
                    currPosition.start.line += 1;
                    currPosition.end.line += 1;
                    break;
                case STRSYM: 
                    break;
                case COMMENT: 
                    break;
                case OTHER:
                    break;
                case NO_CATEGORY: 
                    break;
            }
        }
 * 
 */
