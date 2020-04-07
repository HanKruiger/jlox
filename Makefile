JFLAGS = -g
JC = javac
JRE = java

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	Hello.java

default: build

build: $(CLASSES:.java=.class)

clean:
	$(RM) *.class

run: build
	${JRE} Hello