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
        System.out.println("Lex analiza todo remove");
        
        int line = 1; 
        int startLine = 1;
        int column = 1;
        int startColumn = 1;
        String lexeme = "";
        for (int sourcePos = 0; sourcePos < source.length(); sourcePos++) {
            char c = source.charAt(sourcePos);
            if (c > 32 && c < 127) {
                if (c == 32) { // space
                    // zamakni za 1
                    
                    if (!lexeme.isEmpty()) {
                        // insert symbol
                        TokenType type = keywordMapping.get(lexeme);
                        if (type == null) {
                            // todo
                            type = IDENTIFIER;
                        }
                        symbols.add(new Symbol(new Location(startLine, startColumn), new Location(line, column), type, lexeme)); // maybe copy string
                        lexeme = "";
                    }
                    ++column;
                    // reset startLine and column count
                    startLine = line;
                    startColumn = column;
                    lexeme = "";
                }
                else if (c == 9) { // tab
                    // zamakni za 4

                    if (!lexeme.isEmpty()) {
                        // insert symbol
                        TokenType type = keywordMapping.get(lexeme);
                        if (type == null) {
                            // todo
                            type = IDENTIFIER;
                        }
                        symbols.add(new Symbol(new Location(startLine, startColumn), new Location(line, column), type, lexeme)); // maybe copy string
                        lexeme = "";

                    }
                    column += 4;

                    startLine = line;
                    startColumn = column;
                }
                else if (c == 10 || c == 13) { // newline
                    if (!lexeme.isEmpty()) {
                        // insert symbol
                        TokenType type = keywordMapping.get(lexeme);
                        if (type == null) {
                            // todo
                            type = IDENTIFIER;
                        }
                        symbols.add(new Symbol(new Location(startLine, startColumn), new Location(line, column), type, lexeme)); // maybe copy string
                        lexeme = "";

                    }
                    column = 1;
                    ++line;

                    startLine = line;
                    startColumn = column;
           
                }
                else if (c > 64 && c < 91 || c > 96 && c < 123 )  { // crka
                    ++column;
                    lexeme = lexeme + c;
                }
            }
        }

        return symbols;
    }
}
