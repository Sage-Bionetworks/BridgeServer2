# BridgeServer2
Bridge Server 2.0 running on Spring Boot.

## Set-up
In your home directory, add a file `BridgeServer2.conf` and add any other local overrides.

If you are using a local MySQL instance, it should not have any `sql_mode` flags set (none are set in our Aurora DB instances in any environment, and some of our SQL depends on this behavior...more recent MySQL installations set a number of these mode flags by default). Verify that no `sql_mode` values are set by adding a `.my.cnf` (in your home directory) or `my.cnf` file (in other directories) based on where your MySQL installation is looking for this file. [Here’s a good explanation of how to determine the right location](https://www.sitepoint.com/quick-tip-how-to-permanently-change-sql-mode-in-mysql/) and there may be installed or pre-existing config files that will overwrite your changes if you don’t supercede them in the precedence order MySQL is using to find the file. On Macs for example, `/etc/my.cnf` is the first place a `dmg`-installed version of MySQL will look for this file, but it is different if you install MySQL using `brew`.

The contents should be:

    [mysqld]
    bind-address = 127.0.0.1
    sql-mode =

Then restart your MySQL server.

## Build
To run a full build (including compile, unit tests, findbugs, and jacoco test coverage), run:<br>
`mvn verify`

(A full build takes about 30 seconds on my laptop, from a clean workspace.)

To just run findbugs, run:<br>
`mvn compile findbugs:check`

To run findbugs and get a friendly GUI to read about the bugs, run:<br>
`mvn compile findbugs:findbugs findbugs:gui`

To run jacoco coverage reports and checks, run:<br>
`mvn test jacoco:report jacoco:check`

Jacoco report will be in target/site/jacoco/index.html

## Execution
To run this locally, run<br>
`mvn spring-boot:run`

To run this locally without executing boostrapping framework (which improves start-up time):<br>
`mvn spring-boot:run -Dspring.profiles.active=noinit`

To debug remotely, run:<br>
`mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"`

Useful Spring Boot / Maven development resouces:

- http://stackoverflow.com/questions/27323104/spring-boot-and-maven-exec-plugin-issue<br>
- http://techblog.molindo.at/2007/11/maven-unable-to-find-resources-in-test-cases.html
