package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;

    public LoxFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments, Token paren) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < arguments.size(); i++) {
            environment.define(declaration.params.get(i).lexeme,
                arguments.get(i));
        }

        interpreter.executeBlock(declaration.body, environment);
        return interpreter.consumeReturnValue(paren);
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
