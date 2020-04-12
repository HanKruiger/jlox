JFLAGS = -g -d ./target
JC = javac
JRE = java
JREFLAGS = -classpath ./target

TOOL_MAIN_CLASSES = \
	com/craftinginterpreters/tool/GenerateAst.java

GENERATED_CLASSES = \
	com/craftinginterpreters/lox/Expr.java \
	com/craftinginterpreters/lox/Stmt.java

default: build

build: ast
	$(JC) $(JFLAGS) com/craftinginterpreters/lox/Lox.java

ast: toolbuild
	${JRE} ${JREFLAGS} com.craftinginterpreters.tool.GenerateAst ./com/craftinginterpreters/lox

toolbuild:
	$(JC) $(JFLAGS) $(TOOL_MAIN_CLASSES)

clean:
	$(RM) -rf ./target/* $(GENERATED_CLASSES)

run: build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox

run_test_file: build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox test.lox
