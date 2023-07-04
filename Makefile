.PHONY: exe dev clean

SOURCES := $(shell find src -name '*.java')

dev: target/cli-1.0-SNAPSHOT-jar-with-dependencies.jar

exe: target/xwiki-cli

# Requires GraalVM with native-image
target/xwiki-cli: ${SOURCES}
	mvn -Pnative package -DskipTests=true

target/cli-1.0-SNAPSHOT-jar-with-dependencies.jar: ${SOURCES}
	mvn package -DskipTests=true

clean:
	mvn clean
