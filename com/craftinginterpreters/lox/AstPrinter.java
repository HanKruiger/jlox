package com.craftinginterpreters.lox;

public class AstPrinter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return parenthesize(expr.leftOperator.lexeme +
            expr.rightOperator.lexeme, expr.left, expr.middle, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) {
            return "nil";
        } else {
            return expr.value.toString();
        }
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return expr.name.lexeme;
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return parenthesize("= " + expr.name.lexeme, expr.value);
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        return parenthesize(expr.callee.accept(this), expr.arguments.toArray(new Expr[0]));
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return expr.object.toString() + "." + expr.name.lexeme;
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return parenthesize(".= ", new Expr.Get(expr.object, expr.name), expr.value);
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return expr.keyword.lexeme;
    }

    private String parenthesize(String lexeme, Expr... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(lexeme);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(print(expr));
        }
        builder.append(")");
        return builder.toString();
    }
}