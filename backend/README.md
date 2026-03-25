# shepard backend

This is the source code folder for the shepard backend written in Java and based on [Quarkus](https://quarkus.io/).

### Backend Development Environment

#### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- Node.js v20.17.0 (LTS) and NPM: <https://nodejs.org/en/>
- One of the following IDEs:
  - VSCode: <https://code.visualstudio.com/download>
  - IntelliJ: <https://www.jetbrains.com/idea/download/>

> **WARNING** We mainly use the Ultimate Edition of IntelliJ.
> Although there is also a free Community Edition, there may be differences and limitations.

#### Installation

1. Install OpenJDK
2. Install Node.js
3. Install VSCode or IntelliJ
4. Clone Git repository
5. Run `npm install` in the top level
6. Add project to IDE:
   - IntelliJ:
     1. In the "Project Structure" settings go to "Modules" (or press F4).
     2. Click "+" and "Import Module".
     3. Select the "backend" folder.
     4. Select "Import Module from External Model".
     5. Select "Maven" and create.
     6. Click "ok" to save the new module.
     7. Wait for indexing of files.
     8. Afterwards you can use the existing run configuration on the top right to run quarkus.
   - VSCode:
     1. Install the recommended extensions in `.vscode/extensions.json` (VSCode should already suggest it)
     2. The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

#### Configuration

The Backend configuration is environment dependant and specific properties need to be setup.
This setup is done using environment variables to override or append existing application properties in `application.properties` file according to [the Quarkus documentation](https://quarkus.io/guides/config-reference#env-file).
The variables preconfigured in `.env.example` also contain variables for local databases and frontend as described below.

1. Copy `.env.example` to `.env`
2. Enter valid OIDC parameters

#### Local databases

1. install Docker and Docker Compose (alternatively Podman and Podman Compose)
2. change to the `infrastructure-local` directory
3. run `docker-compose --profile dev up` (or `podman-compose --profile dev up`)
4. local instances of the databases will be launched and the storage will be persistent

| Service              | URL                      | Comment                           |
| -------------------- | ------------------------ | --------------------------------- |
| Keycloak frontend    | <http://localhost:8082/> | _user: admin, pw: admin_          |
| neo4j database       | <http://localhost:7687>  | _user: neo4j, pw: shepardshepard_ |
| neo4j frontend       | <http://localhost:7474>  |                                   |
| mongodb Database     | <http://localhost:27017> | _user: mongo, pw: shepard_        |
| mongodb Frontend     | <http://localhost:8084/> |                                   |
| timescaledb Database | <http://localhost:5432>  | _user: username, pw: password_    |
| postgis Database     | <http://localhost:5433>  | _user: username, pw: password_    |

The credentials can be overridden with environment variables.
Check the docker-compose file to find overridable variables.

#### First run

- start the project:
  - IntelliJ: use the existing run configuration on the top right to run quarkus.
  - VSCode: The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance
    > **Warning:** Using the VSCode run configuration "Debug Quarkus" above an opened file can edit the run configuration and cause issues.
    > Stick to the "Run and Debug" tab on the left side to avoid this.
  - Command line: Run `npm run start:dev` from the root directory
- visit your local frontend at <http://localhost:3000/user#api-keys> and generate an api key
- the Swagger UI for development and testing can then be found at <http://localhost:8080/shepard/doc/swagger-ui>

> **Hint:** If you don't have or want a local frontend and identity provider, you can easily generate an api key by running the integration tests
>
> 1. go to <http://localhost:7474/> and log in to your local neo4j database
> 2. obtain your api key with the following query:
>    `MATCH (a:ApiKey)-[:belongs_to]->(u:User {username: "test_it"}) RETURN a`
> 3. switch to the table view and copy the attribute `jws`, this is your api key

#### Running Tests

In order for all the tests to run we need empty databases prior to each run.
If you intend to run the tests we recommend using the compose file `infrastructure-local/docker-compose-backend-tests.yml` for the databases instead of the "standard" `docker-compose.yml`.
In this file there are no volumes defined so after shutting down the compose provider all data should be deleted.

Running Unit Tests:

Either start quarkus with `./mvnw quarkus:dev` and start the interactive test runner or run the following command:

```sh
./mvnw test
```

Running Integration Tests:

```sh
env SHEPARD_SPATIAL_DATA_ENABLED=true ./mvnw verify -P integration
```

Note that for the integration tests to work completely we need to set the environment variable `SHEPARD_SPATIAL_DATA_ENABLED` to `true`!

#### Known Issues

- VSCode
  - [Test runner for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-test) has issues when the project is not built, build it first using `./mvnw package -DskipUTs`
  - Test runner added by the [Oracle Java extension](https://marketplace.visualstudio.com/items?itemName=Oracle.oracle-java) works without building first, but adds a duplicate extension since we prefer the Java-Package by Red Hat that is recommended by Microsoft.
    For that reason we don't use it.
