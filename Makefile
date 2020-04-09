JFLAGS = -g -d ./target
JC = javac
JRE = java
JREFLAGS = -classpath ./target

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

TOOLCLASSES = \
	com/craftinginterpreters/tool/GenerateAst.java

GENERATEDCLASSES = \
	com/craftinginterpreters/lox/Expr.java

CLASSES = \
	com/craftinginterpreters/lox/Lox.java \
	com/craftinginterpreters/lox/Scanner.java \
	com/craftinginterpreters/lox/Token.java

default: build

build: expr $(CLASSES:.java=.class) $(GENERATEDCLASSES:.java=.class)

toolbuild: $(TOOLCLASSES:.java=.class)

clean:
	$(RM) -rf ./target/* $(GENERATEDCLASSES)

expr: toolbuild
	${JRE} ${JREFLAGS} com.craftinginterpreters.tool.GenerateAst ./com/craftinginterpreters/lox

run: expr build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox

run_test_file: build
	${JRE} ${JREFLAGS} com.craftinginterpreters.lox.Lox test.lox
