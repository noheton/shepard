# quarkend

> **Experimental project:** We are in the process of migrating the backend under `backend/` to use quarkus in this directory.

This is the new backend based on Quarkus.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## CONTRIBUTING to Quarkend (Setup)

### Backend

#### Downloads

- OpenJDK 17 (LTS): <https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot>
- Node.js v21.2.0 or later and NPM: <https://nodejs.org/en/>
- One of the following IDEs:
  - VSCode: <https://code.visualstudio.com/download>
  - IntelliJ: <https://www.jetbrains.com/idea/download/>

> **WARNING** We mainly use the Ultimate Edition of IntelliJ.
> Although there is also a free Community Edition, there may be differences and limitations.

---

#### Installation

1. Install OpenJDK
2. Install Node.js v.21.2.0 or later
3. Install VSCode or IntelliJ
4. Clone Git repository
5. Run `npm install` in the top level
6. Add project to IDE:
   - IntelliJ:
     1. In the "Project Structure" settings go to "Modules" (or press F4).
     2. Click "+" and "Import Module".
     3. Select the "quarkend" folder.
     4. Select "Import Module from External Model".
     5. Select "Maven" and create.
     6. Click "ok" to save the new module.
     7. Wait for indexing of files.
     8. Afterwards you can use the existing run configuration on the top right to run quarkus.
   - VSCode:
     1. Install the recommended extensions in `.vscode/extensions.json` (VSCode should already suggest it)
     2. The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

#### Configuration

The Quarkend configuration is environment dependant and specific properties need to be setup.
This setup is done using environment variables to override or append existing application properties in `application.properties` file according to [the Quarkus documentation](https://quarkus.io/guides/config-reference#env-file).

1. Copy `env.example` to `.env`
2. Adjust the configurations according to your setup

> **NOTE:** For local environment, the database configurations can be left as is since the existing service configurations are already matching the docker compose setup in `docker-compose.yml`.

#### Local databases & frontend

1. install Docker and Docker Compose (alternatively Podman and Podman Compose)
2. change to the quarkend root directory (quarkend folder)
3. run `docker-compose up` (or `podman-compose up`)
4. local instances of the databases will be launched without persistent storage
5. quick tip: run the integration tests to fill your databases with some content

| Service           | URL                      | Comment                     |
| ----------------- | ------------------------ | --------------------------- |
| shepard Frontend  | <http://localhost:8081/> | _Requires Keycloak_         |
| neo4j Database    | <http://localhost:7687>  | _user: neo4j, pw: shepard_  |
| neo4j Frontend    | <http://localhost:7474>  |                             |
| mongodb Database  | <http://localhost:27017> | _user: mongo, pw: shepard_  |
| mongodb Frontend  | <http://localhost:8084/> |                             |
| influxdb Database | <http://localhost:8086>  | _user: influx, pw: shepard_ |
| influxdb Frontend | <http://localhost:8888>  |                             |

#### First run

> If you don't have a local frontend and identity provider, you can easily generate an api key by running the integration tests

1. start the project:

   - IntelliJ: use the existing run configuration on the top right to run quarkus.
   - VSCode: The "Debug Quarkus" configuration under "Run and Debug" in the left-side bar should start a working Quarkus instance

2. run the integration tests: `./mvnw verify -DskipUTs`
3. go to <http://localhost:7474/> and log in to your local neo4j database
4. obtain your api key with the following query: `MATCH (a:ApiKey)-[:belongs_to]->(u:User {username: "test_it"}) RETURN a`
5. switch to the table view and copy the attribute `jws`, this is your api key

> **Warning:** Using the VSCode run configuration "Debug Quarkus" above an opened file can edit the run configuration and cause issues.
> Stick to the "Run and Debug" tab on the left side to avoid this.

The Swagger UI can then be found at http://localhost:8080/shepard/doc/swagger-ui

#### Running Tests

Running Unit Tests:

Either start quarkus with `./mvnw quarkus:dev` and start the interactive test runner or run the following command:

```
./mvnw test
```

Running Integration Tests

```
./mvnw verify -DskipUTs
```

##### Known VSCode Issues

- [Test runner for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-test) has issues when the project is not built, build it first using `./mvnw package -DskipUTs`
- Test runner added by the [Oracle Java extension](https://marketplace.visualstudio.com/items?itemName=Oracle.oracle-java) works without building first, but adds a duplicate extension since we prefer the Java-Package by Red Hat that is recommended by Microsoft.
  For that reason we don't use it.
