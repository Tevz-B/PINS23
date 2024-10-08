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
    /*
     * Stevilcenje: 
     *     inclusiveNumbering:
     *         true:  [8:17-8:18] IDENTIFIER:i
     *         false: [8:17] IDENTIFIER:i
     *     startPos:
     *         1:     [1:1] IDENTIFIER:i
     *         0:     [0:0] IDENTIFIER:i
     */
    private static final boolean inclusiveNumbering = true; 
    private static final int startPos = 1;
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
    public List<Symbol> scan() {
        var symbols = new ArrayList<Symbol>();
        
        int column = startPos; // vrstice in stolpce stejemo od 1
        int line = startPos;        
        String lexeme = "";
        State state = State.WHITESPACE; // zacetno stanje
        char c = 0;
        boolean skipChar = false; // preskoci naslednji character (npr. pri '' se vnaprej pogleda in potem preskoci)
        int lexemeOffset = 0;  // za zamik pozicije pri string_const

        for (int pos = 0; pos <= source.length(); ++pos) {
            if (skipChar) {
                skipChar = false;
                continue;
            }
            c = pos == source.length() ? '\n' : source.charAt(pos); // add newline in place of EOF

            if (c == 13 ) { // skip CR character and check if together with LF
                if (pos + 1 < source.length() && source.charAt(pos+1) == 10)
                    continue;
                else 
                    Report.error(new Position(line, column, line, column), "LEXER: CR character not followed by LF");
            }

            switch (state) {
                case WHITESPACE:
                switch (categorize(c)) {
                    case LETTER:
                        column++;
                        lexeme = "" + c;
                        state = State.WORD;
                        break;
                    case NUMBER: 
                        column++;
                        lexeme = "" + c;
                        state = State.NUM_CONST;
                        break;
                    case SYMBOL: 
                        column++;
                        lexeme = "" + c;
                        state = State.SYMBOL;
                        break;
                    case SPACE:
                        column++;
                        state = State.WHITESPACE;
                        break;
                    case TAB: 
                        column = ((column-1) / 4 + 1) * 4 + 1;
                        state = State.WHITESPACE;
                        break;
                    case NEWLINE:
                        line++;
                        column = startPos;
                        state = State.WHITESPACE;
                        break;
                    case STRSYM: 
                        column++;
                        //lexeme += c;
                        lexeme = ""; lexemeOffset++;
                        state = State.STR_CONST;
                        break;
                    case COMMENT:
                        column++;
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;
                    
                    case OTHER:
                    case NO_CATEGORY:
                        Report.error(new Position(line, column, line, column), "LEXER: Bad symbol " + c);
                        break;
                    default:
                        Report.error(new Position(line, column, line, column), "LEXER: Unhandled symbol:" + c);
                        break;
                }
                break;

                case NUM_CONST: 
                switch (categorize(c)) {
                    case COMMENT:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER ));
                        column++;
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;
                    case LETTER:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        column++;
                        lexeme = "" + c;
                        state = State.WORD;
                        break;
                    case NEWLINE:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        lexeme = "";
                        line++; column = startPos;
                        state = State.WHITESPACE;
                        break;
                    case TAB:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        lexeme = "";
                        column = ((column-1) / 4 + 1) * 4 + 1;
                        state = State.WHITESPACE;
                        break;
                    case SPACE:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        column++;
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case NUMBER:
                        column++;
                        lexeme += c;
                        break;
                    case STRSYM:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        column++;
                        lexeme = ""; lexemeOffset++;
                        state = State.STR_CONST;
                        break;
                    case SYMBOL:
                        symbols.add(createSymbol(line, column, lexeme, C_INTEGER));
                        column++;
                        lexeme = "" + c;
                        state = State.SYMBOL;
                        break;

                    case OTHER:
                    case NO_CATEGORY:
                        Report.error(new Position(line, column, line, column), "LEXER: Bad symbol: " + c);
                        break;
                    default:
                        Report.error(new Position(line, column, line, column), "LEXER: Unhandled symbol: " + c);
                        break;
                }
                break;

                case WORD:
                switch (categorize(c)) {
                    case LETTER:
                        column++;
                        lexeme += c;
                        break;
                    case NUMBER:
                        column++;
                        lexeme += c;
                        break;
                    case SYMBOL: 
                        symbols.add(createSymbol(line, column, lexeme));
                        column++;
                        lexeme = "" + c; 
                        state = State.SYMBOL;
                        break;
                    case SPACE:
                        symbols.add(createSymbol(line, column, lexeme));
                        column++;
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case TAB:
                        symbols.add(createSymbol(line, column, lexeme));
                        column = ((column-1) / 4 + 1) * 4 + 1;
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case NEWLINE:
                        symbols.add(createSymbol(line, column, lexeme));
                        line++; column = startPos;
                        state = State.WHITESPACE;
                        lexeme = "";
                        break;
                    case STRSYM:
                        symbols.add(createSymbol(line, column, lexeme));
                        column++;
                        lexeme = ""; lexemeOffset++;
                        state = State.STR_CONST;
                        break;
                    case COMMENT:
                        symbols.add(createSymbol(line, column, lexeme));
                        column++;
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;

                    case OTHER: // no others like ? in ID and KW ,...
                    case NO_CATEGORY:
                        Report.error(new Position(line, column, line, column), "LEXER: Bad symbol: " + c);
                        break;
                    default:
                        Report.error(new Position(line, column, line, column), "LEXER: Unhandled symbol:" + c);
                        break;
                }
                break;

                case SYMBOL: // +   - =  == =< . , ...
                switch(categorize(c)) {
                    case SYMBOL:
                        if (c == '=' && lexeme.length() == 1 && "!=<>".contains(lexeme)) {
                            lexeme += c;
                            column++;
                        }
                        else { // if symbol is length 2 or smth else, start a new one
                            symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                            column++;
                            lexeme = "" + c;
                        }
                        break;
                    case SPACE:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column++;
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case TAB:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column = ((column-1) / 4 + 1) * 4 + 1;
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case NEWLINE:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        line++; column = startPos;
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case NUMBER:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column++;
                        lexeme = "" + c;
                        state = State.NUM_CONST;
                        break;
                    case LETTER:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column++;
                        lexeme = "" + c;
                        state = State.WORD;
                        break;
                    case STRSYM:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column++;
                        lexeme = ""; lexemeOffset++;
                        state = State.STR_CONST;
                        break;
                    case COMMENT:
                        symbols.add(createSymbol(line, column, lexeme, resolveLexeme(lexeme, state)));
                        column++;
                        lexeme = "" + c;
                        state = State.COMMENT;
                        break;

                    case OTHER:
                    case NO_CATEGORY:
                        Report.error(new Position(line, column, line, column), "LEXER: Bad symbol: " + c);
                        break;
                    default:
                        Report.error(new Position(line, column, line, column), "LEXER: Unhandled symbol:" + c);
                        break;
                }
                break;

                case COMMENT:
                switch (categorize(c)) {
                    case NEWLINE:
                        line++; column = startPos;
                        lexeme = "";
                        state = State.WHITESPACE;
                        break;
                    case TAB:
                        column = ((column-1) / 4 + 1) * 4 + 1;
                        break;
                    default:
                        column++;
                        break;
                }
                break;
                case STR_CONST:
                switch (categorize(c)) {
                    case STRSYM:
                        lexemeOffset++;
                        if (pos+1 < source.length()) { 
                            if (source.charAt(pos+1) == 39) { // escaped ', continue
                                column += 2; 
                                skipChar = true;    
                                lexeme += c;
                            } else { // end str const
                                column++;
                                symbols.add(createSymbol(line, column, lexeme, C_STRING, lexemeOffset)); // lexeme len + escaped
                                lexemeOffset = 0;
                                lexeme = "";
                                state = State.WHITESPACE;
                            }
                        } else { // end str const
                            column++;
                            symbols.add(createSymbol(line, column, lexeme, C_STRING, lexemeOffset)); // lexeme len + escaped
                            lexemeOffset = 0;
                            lexeme = "";
                            state = State.WHITESPACE;
                        }
                        break;
                    case LETTER:
                    case NUMBER:
                    case OTHER:
                    case SPACE:
                    case SYMBOL:
                    case COMMENT:
                        column++;
                        lexeme += c;
                        break;
                    case NEWLINE:
                        Report.error(new Position(line, column, line, column), "LEXER: String const not closed.");
                        break;
                    case TAB:
                    Report.error(new Position(line, column, line, column), "LEXER: Tab not allowed inside string const.");
                        break;
                    case NO_CATEGORY:
                        Report.error(new Position(line, column, line, column),  "LEXER: Bad symbol: " + c);
                        break;
                }
                break;
            }
        }
        symbols.add(new Symbol(new Position(line-1, column, line, column), EOF, "$"));
        return symbols;
    }

    private enum State {
        NUM_CONST, // 0123
        WORD, // ID, KW, AT, true, false
        SYMBOL, // +-*/
        COMMENT, // # ... \n
        STR_CONST, // 'asdasd''asd?_!'
        WHITESPACE
    }

    private enum Category {
        LETTER, // _ is a letter
        NUMBER, // 0 - 9
        SYMBOL, // + - * / % & | ! == != < > <= >= ( ) [ ] { } : ; . , =
        SPACE, // 32
        TAB, // 9
        NEWLINE, // \n, handle \r\n separatly
        STRSYM, // '
        COMMENT, // #
        OTHER, // other in stringconst
        NO_CATEGORY // char code is not in correct range
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

    private Symbol createSymbol(int line, int column, String lexeme) {
        TokenType type = keywordMapping.get(lexeme);
        if (type == null) { // determine type
            if (lexeme.equals("true") || lexeme.equals("false")) {
                type = C_LOGICAL;
            }
            else {
                type = IDENTIFIER;
            }
        }
        return createSymbol(line, column, lexeme, type);
    }

    private Symbol createSymbol(int line, int column, String lexeme, TokenType type) {
        Location startLocation = new Location(line, column - lexeme.length());
        Location endLocation = new Location(line, column - (inclusiveNumbering ? 0 : 1));
        Position position = new Position(startLocation, endLocation);
        return new Symbol(position, type, lexeme);
    }

    private Symbol createSymbol(int line, int column, String lexeme, TokenType type, int endOffset) {
        Location startLocation = new Location(line, column - lexeme.length() - endOffset);
        Location endLocation = new Location(line, column - (inclusiveNumbering ? 0 : 1));
        Position position = new Position(startLocation, endLocation);
        return new Symbol(position, type, lexeme);
    }

    private Category categorize(char c) {
        if (c == 9) return Category.TAB;
        else if (c == 10 ) return Category.NEWLINE;
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
