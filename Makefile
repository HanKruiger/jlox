JFLAGS = -g -d ./target
JC = javac
JRE = java
JREFLAGS = -classpath ./target

TOOLCLASSES = \
	com/craftinginterpreters/tool/GenerateAst.java

GENERATEDCLASSES = \
	com/craftinginterpreters/lox/Expr.java

CLASSES = \
	com/craftinginterpreters/lox/Lox.java \
	com/craftinginterpreters/lox/Scanner.java \
	com/craftinginterpreters/lox/Token.java \
	com/craftinginterpreters/lox/AstPrinter.java

default: build

build: expr
	$(JC) $(JFLAGS) $(CLASSES) $(GENERATEDCLASSES)

toolbuild:
	$(JC) $(JFLAGS) $(TOOLCLASSES)

clean:
	$(RM) -rf ./target/* $(GENERATEDCLASSES)

expr: toolbuild
	${JRE} ${JREFLAGS} com.craftinginterpreters.tool.GenerateAst ./com/craftinginterpreters/lox

run: expr build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox

run_ast_printer: expr build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.AstPrinter

run_test_file: build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox test.lox
