JFLAGS = -g
JC = javac
JRE = java

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	com/craftinginterpreters/lox/Lox.java \
	com/craftinginterpreters/lox/Scanner.java \
	com/craftinginterpreters/lox/Token.java

default: build

build: $(CLASSES:.java=.class)

clean:
	$(RM) *.class

run: build
	${JRE} com.craftinginterpreters.lox.Lox

run_test_file: build
	${JRE} com.craftinginterpreters.lox.Lox test.lox
