package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    // We reuse the interpreter because it will have state later.
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        System.out.println();
        System.out.println("Welcome to Lox!");
        for (;;) {
            System.out.print("> ");
            run(reader.readLine());

            // Reset error state in REPL. (Don't kill session after mistake.)
            hadError = false;
        }
    }

    private static void run(String source) {
        if (source == null) {
            System.out.println("\nReached end of stream. Bye!");
            System.exit(0);
        }

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        
        List<Stmt> statements = parser.parse();
        if (hadError) {
            return;
        }

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        if (hadError) {
            return;
        }

        interpreter.interpret(statements);
    }

    static void error(int line, String msg) {
        report(line, "", msg);
    }

    static void error(Token token, String msg) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", msg);
        } else {
            report(token.line, " at '" + token.lexeme + "'", msg);
        }
    }

    private static void report(int line, String where, String msg) {
        System.err.println("[line " + line + "] Error" + where + ": " + msg);
        hadError = true;
    }

    public static void runtimeError(RuntimeError e) {
        System.err.println(e.getMessage() +
            "\n[line " + e.token.line + "]");
        hadRuntimeError = true;
    }
}
