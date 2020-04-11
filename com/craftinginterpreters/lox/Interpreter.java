package com.craftinginterpreters.lox;

import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Ternary;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Visitor;

import static com.craftinginterpreters.lox.TokenType.*;

public class Interpreter implements Visitor<Object> {
    public void interpret(Expr expr) {
        try {
            Object value = evaluate(expr);
            System.out.println(stringify(value));
        } catch (RuntimeError e) {
            Lox.runtimeError(e);
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
            if (! (operand instanceof Double)) {
                throw new RuntimeError(operator, "Operands must be numbers.");
            }
        }
    }

    @Override
    public Object visitLiteralExpr(Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Unary expr) {
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
    public Object visitBinaryExpr(Binary expr) {
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
    public Object visitTernaryExpr(Ternary expr) {
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
}
