package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.List;

class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    
    // 'Pointer' to current token (we 'scan' tokens now, not characters).
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    // Check if the current token is one of the given ones, and if so, consume
    // it and returns true. Otherwise, return false and don't consume anything.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        } else {
            return peek().type == type;
        }
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String msg) {
        if (check(type)) {
            return advance();
        } else {
            throw error(peek(), msg);
        }
    }

    private ParseError error(Token token, String msg) {
        Lox.error(token, msg);
        // Return the error, not throw it. Let the caller decide if it needs to
        // be thrown.
        return new ParseError();
    }

    // Advance until we're at the next (probably) statement so we can keep on
    // parsing after a syntax error has occurred.
    private void synchronize() {
        // Advance over the illegal token.
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            } else {
                switch (peek().type) {
                    case CLASS:
                    case FUN:
                    case VAR:
                    case FOR:
                    case IF:
                    case WHILE:
                    case PRINT:
                    case RETURN:
                        return;
                    default:
                        advance();
                }
            }

        }
    }

    // expression → ternary
    private Expr expression() {
        return ternary();
    }

    // ternary -> equality ( "?" expression ":" expression )*
    private Expr ternary() {
        Expr expr = equality();

        if (match(QSTN)) {
            Token leftOperator = previous();
            Expr middle = expression();
            Token rightOperator = consume(COLON, "Expect ':' in ternary operator.");
            Expr right = expression();
            expr = new Expr.Ternary(expr, leftOperator, middle, rightOperator, right);
        }

        return expr;
    }
    
    // equality → comparison ( ( "!=" | "==" ) comparison )*
    private Expr equality() {
        Expr expr = comparison();
        
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }
    
    // comparison → addition ( ( ">" | ">=" | "<" | "<=" ) addition )*
    private Expr comparison() {
        Expr expr = addition();
        
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }
    
    // addition → multiplication ( ( "-" | "+" ) multiplication )*
    private Expr addition() {
        Expr expr = multiplication();
        
        while (match(PLUS, MINUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }
    
    // multiplication → unary ( ( "/" | "*" ) unary )*
    private Expr multiplication() {
        Expr expr = unary();
        
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }
    
    // unary → ( "!" | "-" ) unary | primary
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        } else {
            return primary();
        }
    }
    
    // primary → NUMBER | STRING | "false" | "true" | "nil" | "(" expression ")"
    private Expr primary() {
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        } else if (match(FALSE)) {
            return new Expr.Literal(false);
        } else if (match(TRUE)) {
            return new Expr.Literal(true);
        } else if (match(NIL)) {
            return new Expr.Literal(null);
        } else if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression in grouping.");
            return new Expr.Grouping(expr);
        } else {
            throw error(peek(), "Expect expression.");
        }
    }
}
