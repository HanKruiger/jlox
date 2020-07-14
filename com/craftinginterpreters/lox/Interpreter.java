package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    static final Environment globals = new Environment();
    private Environment environment = globals;

    // Stores how many hops we must take (to different environments) when
    // resolving a variable.
    private final Map<Expr, Integer> locals = new HashMap<>();

    private boolean breaking = false;
    private boolean continuing = false;
    private boolean returning = false;

    private Object returnValue;

    public Interpreter() {
        globals.define("clock", new LoxCallable(){
        
            @Override
            public Object call(Interpreter interpreter, List<Object> arguments, Token paren) {
                return (double) System.currentTimeMillis() / 1000.0;
            }
        
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError e) {
            Lox.runtimeError(e);
        }
    }

    private void execute(Stmt statement) {
        if (!breaking && !continuing && !returning) {
            statement.accept(this);
        } else {
            // Left this in to be sure...
            throw new RuntimeError(null,
                "I was breaking/continuing/returning and tried to execute.");
        }
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        this.environment = environment;
        try {
            for (Stmt stmt : statements) {
                if (breaking || continuing || returning) {
                    break;
                }
                execute(stmt);
            }
        } finally {
            this.environment = previous;
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return "nil";
        } else {
            // I decided not to remove ".0" from 'integer' doubles because it
            // hides the fact that everything is a double.
            return value.toString();
        }
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        return true;
    }

    private boolean isEqual(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null) {
            return false;
        }
        return left.equals(right);
    }

    private void checkNumberOperands(Token operator, Object... operands) {
        for (Object operand : operands) {
            if (!(operand instanceof Double)) {
                throw new RuntimeError(operator, "Operands must be numbers.");
            }
        }
    }

    public Object consumeReturnValue(Token paren, Object thisValue) {
        if (!returning && thisValue == null) {
            // No return keyword was interpreted, so the function body
            // terminated without a return value. Return `nil` in that case.
            resetReturningState();
            return null;
        }

        Object returnThisValue;
        if (thisValue != null) {
            returnThisValue = thisValue;
        } else {
            returnThisValue = returnValue;
        }

        // Reset state after consumption.
        resetReturningState();

        return returnThisValue;
    }

    private void resetReturningState() {
        returning = false;
        returnValue = null;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object value = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(value);
            case MINUS:
                checkNumberOperands(expr.operator, value);
                return -((double) value);
            default:
                // Should be unreachable.
                return null;
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                } else if (left instanceof String) {
                    return (String) left + stringify(right);
                } else if (right instanceof String) {
                    return stringify(left) + right;
                }
                throw new RuntimeError(expr.operator,
                    "Operands must both be numbers or some of them have to be" +
                    " a String");
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double) right == 0.0) {
                    throw new RuntimeError(expr.operator,
                        "Cannot divide by zero.");
                }
                return (double) left / (double) right;

            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;

            case EQUAL_EQUAL:
                return isEqual(left, right);
            case BANG_EQUAL:
                return !isEqual(left, right);
            default:
                // Should be unreachable.
                return null;
        }
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        if (expr.leftOperator.type == QSTN &&
                expr.rightOperator.type == COLON) {
            if (isTruthy(evaluate(expr.left))) {
                return evaluate(expr.middle);
            } else {
                return evaluate(expr.right);
            }
        } else {
            // Should be unreachable.
            return null;
        }
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object leftValue = evaluate(expr.left);

        if (expr.operator.type == OR) {
            if (isTruthy(leftValue)) {
                return leftValue;
            }
        } else if (expr.operator.type == AND) {
            if (!isTruthy(leftValue)) {
                return leftValue;
            }
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> args = new ArrayList<>();
        for (Expr arg : expr.arguments) {
            args.add(evaluate(arg));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Expression is not callable.");
        }

        LoxCallable function = (LoxCallable) callee;

        if (args.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() +
                " arguments, but got " + args.size() + ".");
        }

        return function.call(this, args, expr.paren);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
            if (breaking) {
                break;
            } else if (continuing) {
                continuing = false;
                continue;
            } else if (returning) {
                break;
            }
        }
        breaking = false;
        continuing = false;
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        breaking = true;
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        continuing = true;
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }
        returning = true;
        returnValue = value;

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        environment.define(stmt.name.lexeme,
            stmt.initializer != null ? evaluate(stmt.initializer) : null);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block blockStmt) {
        executeBlock(blockStmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment);
            methods.put(method.name.lexeme, function);
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme, methods);
        environment.assign(stmt.name, klass);

        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function funcStmt) {
        LoxFunction function = new LoxFunction(funcStmt, environment);
        environment.define(funcStmt.name.lexeme, function);
        return null;
    }
}
