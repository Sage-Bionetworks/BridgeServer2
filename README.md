# BridgeServer2
Bridge Server 2.0 running on Spring Boot.

Set-up:
In your home directory, add a file BridgeServer2.conf and add
* any other local overrides

To run a full build (including compile, unit tests, findbugs, and jacoco test coverage), run:
mvn verify

(A full build takes about 30 seconds on my laptop, from a clean workspace.)

To just run findbugs, run:
mvn compile findbugs:check

To run findbugs and get a friendly GUI to read about the bugs, run:
mvn compile findbugs:findbugs findbugs:gui

To run jacoco coverage reports and checks, run:
mvn test jacoco:report jacoco:check

Jacoco report will be in target/site/jacoco/index.html

To run this locally, run
mvn spring-boot:run

To debug remotely, run:
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

Useful Spring Boot / Maven development resouces:
http://stackoverflow.com/questions/27323104/spring-boot-and-maven-exec-plugin-issue
http://techblog.molindo.at/2007/11/maven-unable-to-find-resources-in-test-cases.html
