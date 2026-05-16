package de.dlr.shepard.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * DX1 self-test — plain JUnit 5 (no {@code @QuarkusTest}) test that:
 * <ol>
 *   <li>Starts all three infrastructure containers via {@link ShepardTestStack#start()}.
 *   <li>Asserts that the returned config map contains the correct keys with
 *       non-blank values.
 *   <li>Stops the containers via {@link ShepardTestStack#stop()}.
 * </ol>
 *
 * <p>This is intentionally lightweight — it proves the plumbing (container
 * startup + config wiring) without firing up the full Quarkus application.
 * A full {@code @QuarkusTest} integration test annotated with
 * {@code @QuarkusTestResource(ShepardTestStack.class)} is the follow-up
 * (deferred so Flyway migration compatibility against a plain Postgres image
 * can be validated separately from the DX1 skeleton).
 *
 * <p>If Docker is not available (e.g. in a sandbox environment that lacks a
 * Docker daemon) the test is skipped so it doesn't fail the build.
 */
class ShepardTestStackTest {

  private static ShepardTestStack stack;
  private static Map<String, String> config;
  private static boolean dockerAvailable;

  @BeforeAll
  static void startStack() {
    dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    if (!dockerAvailable) {
      return;
    }
    stack = new ShepardTestStack();
    config = stack.start();
  }

  @AfterAll
  static void stopStack() {
    if (stack != null) {
      stack.stop();
    }
  }

  @Test
  void neo4jHostIsWired() {
    if (!dockerAvailable) return;
    assertThat(config)
        .containsKey("neo4j.host")
        .extractingByKey("neo4j.host")
        .asString()
        .isNotBlank()
        .contains(":");
  }

  @Test
  void neo4jCredentialsAreWired() {
    if (!dockerAvailable) return;
    assertThat(config).containsEntry("neo4j.username", "neo4j");
    assertThat(config).containsEntry("neo4j.password", "shepardshepard");
  }

  @Test
  void mongoConnectionStringIsWired() {
    if (!dockerAvailable) return;
    assertThat(config)
        .containsKey("quarkus.mongodb.connection-string")
        .extractingByKey("quarkus.mongodb.connection-string")
        .asString()
        .startsWith("mongodb://");
  }

  @Test
  void postgresJdbcUrlIsWired() {
    if (!dockerAvailable) return;
    assertThat(config)
        .containsKey("quarkus.datasource.jdbc.url")
        .extractingByKey("quarkus.datasource.jdbc.url")
        .asString()
        .startsWith("jdbc:postgresql://");
  }

  @Test
  void postgresCredentialsAreWired() {
    if (!dockerAvailable) return;
    assertThat(config)
        .containsEntry("quarkus.datasource.username", "shepard")
        .containsEntry("quarkus.datasource.password", "shepard_secret");
  }

  @Test
  void spatialDataSourceIsDisabled() {
    if (!dockerAvailable) return;
    assertThat(config)
        .containsEntry("shepard.infrastructure.spatial.enabled", "false")
        .containsEntry("quarkus.flyway.spatial.active", "false")
        .containsEntry("quarkus.hibernate-orm.spatial.active", "false");
  }

  @Test
  void allContainersAreRunning() {
    if (!dockerAvailable) return;
    assertThat(ShepardTestStack.NEO4J.isRunning()).isTrue();
    assertThat(ShepardTestStack.MONGO.isRunning()).isTrue();
    assertThat(ShepardTestStack.POSTGRES.isRunning()).isTrue();
  }
}
