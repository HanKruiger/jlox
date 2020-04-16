package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Parser {
    private static class ParseError extends RuntimeException {
        private static final long serialVersionUID = 2322035634061302440L;
    }

    private final List<Token> tokens;
    
    // 'Pointer' to current token (we 'scan' tokens now, not characters).
    private int current = 0;

    // Counts how deeply nested inside loop constructs we are.
    private int loopNesting;

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

    // declaration → funDecl | varDecl | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            } else if (match(FUN)) {
                return function("function");
            } else {
                return statement();
            }
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    // function → IDENTIFIER "(" parameters? ")" block ;
    // parameters → IDENTIFIER ( "," IDENTIFIER )* ;
    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }
                
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
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

    // statement → printStmt | ifStmt | forStmt | whileStmt | breakStmt
    //     | continueStmt | returnStmt | block | exprStmt;
    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        } else if (match(IF)) {
            return ifStatement();
        } else if (match(FOR)) {
            return forStatement();
        } else if (match(WHILE)) {
            return whileStatement();
        } else if (match(BREAK)) {
            return breakStatement();
        } else if (match(CONTINUE)) {
            return continueStatement();
        } else if (match(RETURN)) {
            return returnStatement();
        } else if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        } else {
            return expressionStatement();
        }
    }
    
    // block → "{" declaration* "}" ;
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // printStmt → "print" expressionStatement ;
    private Stmt printStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after print value.");
        return new Stmt.Print(expr);
    }

    // ifStmt → "if" "(" expression ")" statement ( "else" statement )? ;
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition in 'if' statement.");
        
        Stmt thenBranch = statement();

        // Dangling else? We capture the else directly so it pairs with the
        // closest if.
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }
    
    // forStmt → "for" "(" ( varDecl | exprStmt | ";" )
    //     expression? ";"
    //     expression? ")" statement ;
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration(); // includes the ';'
        } else {
            initializer = expressionStatement(); // includes the ';'
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after for condition.");
        
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for increment.");

        Stmt body = statement();

        // Desugaring: Build the for construct using while constructs. This way
        // we don't have to touch the interpreter for this language feature.

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(
                body,
                new Stmt.Expression(increment)
            ));
        }

        if (condition == null) {
            // Default to infinite loop because why not!
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(
                initializer,
                body
            ));
        }

        return body;
    }

    // whileStmt → expression ;
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'");
        Expr condition = expression();
        consume(RIGHT_PAREN,
            "Expect ')' after condition in 'while' statement.");
        loopNesting++;
        Stmt body = statement();
        loopNesting--;

        return new Stmt.While(condition, body);
    }

    private Stmt breakStatement() {
        Token breakToken = previous();
        if (loopNesting <= 0) {
            throw error(previous(), "'break' disallowed outside of loop.");
        }
        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break(breakToken);
    }

    // continueStmt → "continue" ;
    private Stmt continueStatement() {
        Token continueToken = previous();
        if (loopNesting <= 0) {
            throw error(previous(), "'continue' disallowed outside of loop.");
        }
        consume(SEMICOLON, "Expect ';' after 'continue'.");
        return new Stmt.Continue(continueToken);
    }

    // returnStmt → "return" expression? ";" ;
    private Stmt returnStatement() {
        Token keyword = previous();

        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return statement.");

        return new Stmt.Return(keyword, value);
    }

    // expressionStatement → expression ;
    // ('cast' to a statement)
    private Stmt expressionStatement() {
        Expr expr = expression();
        if (isAtEnd()) {
            // Parse an expression at the end of the input as a print
            // statement. This adds support for entering and evaluating
            // expressions to the REPL.
            return new Stmt.Print(expr);
        } else {
            consume(SEMICOLON, "Expect ';' after expression.");
            return new Stmt.Expression(expr);
        }
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

    // ternary -> logic_or ( "?" expression ":" expression )*
    private Expr ternary() {
        Expr expr = or();

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

    // logic_or → logic_and ( "or" logic_and )* ;
    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }
    // logic_and → equality ( "and" equality )* ;
    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
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
    
    // unary → ( "!" | "-" ) unary | call
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        } else {
            return call();
        }
    }
    
    // call → primary ( "(" arguments? ")" )* ;
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Cannot have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after argument list.");
        return new Expr.Call(callee, paren, arguments);
    }
    // arguments → expression ( "," expression )* ;
    
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
