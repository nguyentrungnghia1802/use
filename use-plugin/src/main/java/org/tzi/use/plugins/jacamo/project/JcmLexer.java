package org.tzi.use.plugins.jacamo.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small lexical scanner for discovery only; it does not parse dimension syntax. */
public final class JcmLexer {
    public record Token(String text, int line, int column) {
        public SourceSpan span(Path path) {
            return new SourceSpan(path, line, column, line, column + Math.max(0, text.length() - 1));
        }
    }

    public static List<Token> scan(String input) {
        List<Token> tokens = new ArrayList<>();
        int line = 1, column = 1;
        for (int i = 0; i < input.length();) {
            char ch = input.charAt(i);
            if (ch == '\r') { i++; continue; }
            if (ch == '\n') { i++; line++; column = 1; continue; }
            if (Character.isWhitespace(ch)) { i++; column++; continue; }
            if (ch == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                while (i < input.length() && input.charAt(i) != '\n') { i++; column++; }
                continue;
            }
            if (ch == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
                i += 2; column += 2;
                while (i < input.length()) {
                    if (input.charAt(i) == '*' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                        i += 2; column += 2; break;
                    }
                    if (input.charAt(i) == '\n') { line++; column = 1; i++; }
                    else { i++; column++; }
                }
                continue;
            }
            int start = i, startLine = line, startColumn = column;
            if (ch == '"' || ch == '\'') {
                char quote = ch;
                i++; column++;
                while (i < input.length()) {
                    char current = input.charAt(i++); column++;
                    if (current == '\\' && i < input.length()) { i++; column++; }
                    else if (current == quote) break;
                    else if (current == '\n') { line++; column = 1; }
                }
                String text = input.substring(start + 1, Math.max(start + 1, i - 1));
                tokens.add(new Token(text, startLine, startColumn));
            } else if (isSymbol(ch)) {
                tokens.add(new Token(String.valueOf(ch), startLine, startColumn));
                i++; column++;
            } else {
                while (i < input.length() && !Character.isWhitespace(input.charAt(i))
                        && !isSymbol(input.charAt(i)) && input.charAt(i) != '"'
                        && input.charAt(i) != '\'') {
                    if (input.charAt(i) == '/' && i + 1 < input.length()
                            && (input.charAt(i + 1) == '/' || input.charAt(i + 1) == '*')) break;
                    i++; column++;
                }
                if (i == start) { i++; column++; continue; }
                tokens.add(new Token(input.substring(start, i), startLine, startColumn));
            }
        }
        return List.copyOf(tokens);
    }

    private static boolean isSymbol(char ch) {
        return ch == '{' || ch == '}' || ch == ':' || ch == ',' || ch == '(' || ch == ')' || ch == ';';
    }
}
