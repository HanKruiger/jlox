[![CI](https://github.com/HanKruiger/jlox/workflows/CI/badge.svg)](https://github.com/HanKruiger/jlox/actions)

# jlox
Lox interpreter implemented in Java (based on the book: https://craftinginterpreters.com)


## Additional features that are not in the Lox language (were left as challenges in the book)

- Nestable C-style comments (`\* Foo *\`, `\* Foo \* Bar *\ *\`)
- Conditional (ternary) operator
- `+` operator works so that if one of the operands is a string, the other is converted to a string and concatenated. (`"Forty " + 2` evaluates to `Forty 2.0`.)
- Throw a runtime error when trying to divide by zero.
- The REPL allows expressions as input (and not just statements), which are evaluated and shown in the interpreter.
- `break` and `continue` keywords for loops
