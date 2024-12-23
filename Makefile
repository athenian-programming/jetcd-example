default: versioncheck

clean:
	./gradlew clean

stop:
	./gradlew --stop

build: clean
	./gradlew build -xtest

tests:
	./gradlew check jacocoTestReport

lint:
	./gradlew lintKotlinMain
	./gradlew lintKotlinTest

refresh:
	./gradlew --refresh-dependencies

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=8.12 --distribution-type=bin
