# shepard backend

> This project is currently under heavy development. Use at your own risk.

## Getting started as an administrator

Information about the deployment can be found [here](https://gitlab.com/dlr-shepard/deployment)

## Getting started as a contributor

### Documentation

- [Guide: Writing Testable Code](http://misko.hevery.com/attachments/Guide-Writing%20Testable%20Code.pdf)
- [Jersey Documentation](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest)
- [Tomcat Documentation](https://tomcat.apache.org/tomcat-9.0-doc/)
- [Our Developer Documentation](https://dlr-shepard.gitlab.io/backend/)

### Downloads

- JDK 11: <https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot>
- Eclipse IDE for Enterprise Java Developers: <https://www.eclipse.org/downloads/packages/>
- Tomcat 9: <https://tomcat.apache.org/download-9.cgi>
- Project Lombok: <https://projectlombok.org/download>
- Postman (optional): <https://www.getpostman.com/downloads/>

### Installation

1. install JDK
2. unpack Eclipse
3. unpack Tomcat
4. install Project Lombok (run JAR file and follow the installer)
5. clone Git repository
6. import project into Eclipse (File -> Open Projects from File System...)
7. link Tomcat with Eclipse (create a new server in tab `Servers` and select Tomcat)
8. if needed, modify the file `src/main/resources/db.properties` and configure databases
9. start the project: right click on the project -> Run on Server

### Local databases (optional)

1. install Docker and Docker Compose
2. change to the root directory
3. run `docker-compose up`.
4. local instances of the databases will be launched without persistent storage

### Code Review Checklist

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
- Classes and public methods are uniformly documented using javadoc.
- Don't add comments to obvious things.
- If you need any of the features of objects, use them otherwise use primitives.
- Try to offload as much work as possible to the databases.
- Parallelize things only when needed (e.g. if more than 1000 objects will occur in a stream).

## Getting started as a user

- The respective OpenAPI definition can be found at `/shepard/doc/openapi.json`.
- This specification can be used to create arbitrary clients with the [OpenAPI Generator](https://openapi-generator.tech/)
- Clients for Python, Java and Typescript are created and deployed automatically via Gitlab CI/CD.
- Further clients can be created manually with the [openapi generator](https://openapi-generator.tech/).
