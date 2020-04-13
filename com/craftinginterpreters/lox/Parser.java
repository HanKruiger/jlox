package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.ArrayList;
import java.util.List;

class Parser {
    private static class ParseError extends RuntimeException {
        private static final long serialVersionUID = 2322035634061302440L;
    }

    private final List<Token> tokens;
    
    // 'Pointer' to current token (we 'scan' tokens now, not characters).
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // program → declaration* EOF ;
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
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

    // declaration → varDecl | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            } else {
                return statement();
            }
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    // statement → printStmt | exprStmt | block ;
    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        } else if (match(LEFT_BRACE)) {
            return block();
        } else {
            return expressionStatement();
        }
    }
    
    // block → "{" declaration* "}" ;
    private Stmt block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return new Stmt.Block(statements);
    }

    // printStmt → "print" expressionStatement ;
    private Stmt printStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after print value.");
        return new Stmt.Print(expr);
    }

    // expressionStatement → expression ;
    // ('cast' to a statement)
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // expression → assignment
    private Expr expression() {
        return assignment();
    }

    // assignment → IDENTIFIER "=" assignment | ternary
    private Expr assignment() {
        Expr expr = ternary();

        if (match(EQUAL)) {
            // It must be an assignment.
            Token equals = previous();
            Expr value =  assignment(); // right-recursion

            // Assert the previous expression was a valid l-value, otherwise,
            // error out. (But don't throw since we're not in a confused state;
            // we can keep on parsing.)
            if (expr instanceof Expr.Variable) {
                // Convert the r-value expression into an l-value
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            } else {
                error(equals, "Invalid assignment target.");
            }
        }

        return expr;
    }

    // ternary -> equality ( "?" expression ":" expression )*
    private Expr ternary() {
        Expr expr = equality();

        if (match(QSTN)) {
            Token leftOperator = previous();
            Expr middle = expression();
            Token rightOperator = consume(COLON,
                "Expect ':' in ternary operator.");
            Expr right = expression();
            expr = new Expr.Ternary(expr, leftOperator, middle, rightOperator,
                right);
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
    
    // primary → NUMBER | STRING | "false" | "true" | "nil" | IDENTIFIER |
    //     "(" expression ")"
    private Expr primary() {
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        } else if (match(FALSE)) {
            return new Expr.Literal(false);
        } else if (match(TRUE)) {
            return new Expr.Literal(true);
        } else if (match(NIL)) {
            return new Expr.Literal(null);
        } else if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        } else if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression in grouping.");
            return new Expr.Grouping(expr);
        } else {
            throw error(peek(), "Expect expression.");
        }
    }
}
