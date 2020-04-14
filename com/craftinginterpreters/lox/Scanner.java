package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*; 

public class Scanner {
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
        keywords.put("break", BREAK);
        keywords.put("continue", CONTINUE);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // The first character in the current lexeme being scanned
    private int start = 0;

    // The character we're currently considering
    private int current = 0;

    // What source line `current` is on so we can produce tokens that know their
    // location.
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '?': addToken(QSTN); break;
            case ':': addToken(COLON); break;

            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;

            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    // Ignore (`advance()`) everything until that.
                    // But don't consume newlines, we'll only peek those because
                    // the line counter has to be incremented.
                    while (peek() != '\n' && !isAtEnd()) advance();
                    // We don't add a token for the comment.
                } else if (match('*')) {
                    cStyleComment();
                } else {
                    addToken(SLASH);
                }
                break;

            // Whitespace
            case ' ':
            case '\r':
            case '\t':
                break;
            
            // Newlines should increment the line counter.
            case '\n':
                line++;
                break;
            
            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        // See if the identifier is a reserved keyword. Otherwise it is an
        // identifier.
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            type = IDENTIFIER;
        }

        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
        }

        // Add the number as a token.
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim surrounding "s and add the string as token.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private void cStyleComment() {
        int nesting = 1;
        while (nesting > 0) {
            if (isAtEnd()) {
                Lox.error(line, "Unterminated C-style comment.");
                return;
            }
            char c = advance();

            if (c == '\n') {
                line++;
                continue;
            }

            // A 'closing bracket' decrements the nesting counter
            if (c == '*' && peek() == '/') {
                nesting--;
                advance();
            }

            // A 'opening bracket' increments the nesting counter
            if (c == '/' && peek() == '*') {
                nesting++;
                advance();
            }
        }
    }

    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        } else if (source.charAt(current) != expected) {
            return false;
        } else {
            current++;
            return true;
        }
    }

    // Sort of like `advance()`, but don't consume the character just yet.
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // Consume the next character in the source and return it.
    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    // Create a new token for the current lexeme
    private void addToken(TokenType type) {
        addToken(type, null);
    }
    
    // Create a new token for the current lexeme
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
