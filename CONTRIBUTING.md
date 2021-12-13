# Contributing to shepard

Thank you for taking the time to contribute!
If you want to fix a bug, feel free to create a merge request right away.
If you want to add a new feature or make significant changes to the codebase, we should discuss it either [here](https://gitlab.com/dlr-shepard/backend/-/issues) (changes affecting only the backend) or [here](https://gitlab.com/dlr-shepard/architecture/-/issues) (changes affecting the entire infrastructure).

## Reporting Bugs

If you notice an error or inconsistent behavior, please create an issue.
Make sure that you use the right issue template.
Describe your problem as detailed as possible so that developers can reproduce your situation.

If you have a feature request that only affects the backend, you can create an issue as well.
Please use the `Feature` template this time.
If your feature request involves major conceptual changes, please submit an issue to the [architecture repository](https://gitlab.com/dlr-shepard/architecture).

## Code Review Checklist

- The code on the main branch is always functional.
- There is no commented out code for the main branch.
- No console output (`System.out.println()`) is used on the main branch.
- Changes in merge requests are complete, self-contained, and implement only a single functionality.
- Changes that do not belong to an existing merge request are implemented in a separate merge request.
- Added functionality is tested by unit tests.
- Regular use cases are tested with integration tests.
- The existing package structure is not violated by the new code.
- Do not introduce dependency cycles.
- All code is formatted according to the eclipse buildin code style.
- No warnings occur during compilation.
- The OpenAPI documentation is consistent with the actual behavior of the software.
- Classes and public methods are uniformly documented using javadoc comments.
- Don't add comments to obvious things.
- If you need any of the features of objects, use them otherwise use primitives.
- Try to offload as much work as possible to the databases.
- Parallelize things only when needed (e.g. if more than 1000 objects will occur in a stream).
- Use Jakarta Bean Validation to validate user input.

## Documentation

- [Guide: Writing Testable Code](http://misko.hevery.com/attachments/Guide-Writing%20Testable%20Code.pdf)
- [Jersey Documentation](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest3x/user-guide.html)
- [Tomcat Documentation](https://tomcat.apache.org/tomcat-10.0-doc/index.html)

## Setting up a develping environment

### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- Eclipse IDE for Enterprise Java and Web Developers: <https://www.eclipse.org/downloads/packages/>
- Apache Tomcat 10: <https://tomcat.apache.org/download-10.cgi>
- Project Lombok: <https://projectlombok.org/download>

### Installation

1. install OpenJDK
2. install Eclipse IDE for Enterprise Java and Web Developers
3. unpack Apache Tomcat
4. install Project Lombok (run JAR file and follow the installer)
5. clone Git repository
6. import project into Eclipse (File -> Open Projects from File System...)
7. link Tomcat with Eclipse (create a new server in tab `Servers` and select Tomcat)

### Local databases

1. install Docker and Docker Compose (alternatively Podman and Podman Compose)
2. change to the project root directory
3. run `docker-compose up` (or `podman-compose up`)
4. local instances of the databases will be launched without persistent storage
5. quick tip: run the integration tests to fill your database with some content

### First run

> If you don't have a local frontend and identity provider, you can easily generate an api key by running the integration tests

1. start the project: right click on the project -> Run on Server
2. run the integration tests: right click on `src/test/java/en/dlr/shepard/integrationtests` -> Run as JUnit Test
3. go to <http://localhost:7474/> and log in to your local neo4j database
4. obtain your api key with the following query: `MATCH (a:ApiKey)-[:belongs_to]->(u:User {username: "test_it"}) RETURN a`
5. switch to the table view and copy the attribute `jws`, this is your api key
