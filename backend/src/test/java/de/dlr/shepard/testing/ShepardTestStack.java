package de.dlr.shepard.testing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * DX1 — unified Quarkus test resource that starts all required shepard
 * infrastructure containers (Neo4j, MongoDB, TimescaleDB/Postgres) in
 * parallel and wires their connection URLs into the Quarkus test
 * application via {@link #start()}.
 *
 * <p>Usage — annotate any {@code @QuarkusTest} class:
 *
 * <pre>{@code
 * @QuarkusTest
 * @QuarkusTestResource(ShepardTestStack.class)
 * class MyIntegrationTest { ... }
 * }</pre>
 *
 * <p>The resource is opt-in: existing tests that rely on the real
 * {@code infrastructure-local} Docker Compose stack are unaffected.
 *
 * <p>Container notes:
 * <ul>
 *   <li><b>Neo4j</b> — uses the raw {@code neo4j-ogm} bolt connection
 *       via {@code neo4j.host} / {@code neo4j.username} / {@code neo4j.password}
 *       (not the Quarkus Neo4j extension). The admin password is set to
 *       {@code "shepardshepard"} (matches the {@code %dev} profile default).
 *   <li><b>MongoDB</b> — wired via {@code quarkus.mongodb.connection-string}.
 *   <li><b>Postgres / TimescaleDB</b> — wired via
 *       {@code quarkus.datasource.jdbc.url} / username / password.
 *       The standard {@code postgres:15} image is used (TimescaleDB extension
 *       is not required for unit-level integration tests; switch to
 *       {@code timescale/timescaledb:latest-pg15} when Flyway migrations that
 *       exercise TimescaleDB hypertables are needed). The spatial datasource is
 *       disabled ({@code shepard.infrastructure.spatial.enabled=false}) so a
 *       second container is not required in DX1 phase 1.
 *   <li><b>InfluxDB</b> — not included. The timeseries data store in this fork
 *       is TimescaleDB (Postgres). The single Influx comment in the codebase is
 *       a legacy doc reference; there are no {@code shepard.timeseries.influx.*}
 *       config keys. Add an Influx container here if/when that integration lands.
 * </ul>
 *
 * <p>Static container fields follow the Testcontainers singleton pattern:
 * Ryuk cleans up on JVM exit; {@link #stop()} provides an explicit courtesy
 * stop for test frameworks that call it.
 *
 * @see <a href="aidocs/16-dispatcher-backlog.md">aidocs/16 DX1</a>
 */
public class ShepardTestStack implements QuarkusTestResourceLifecycleManager {

  // --- Container images (pinned for reproducibility) ---

  /**
   * Neo4j 5.x — OGM bolt connection. Admin password set to match the
   * {@code %dev} profile ({@code "shepardshepard"}).
   *
   * <p>Note: {@code NEO4J_PLUGINS=["n10s"]} is NOT added here — the neosemantics
   * plugin is only needed for semantic tests. Add a subclass or init-arg when
   * a test needs it.
   */
  static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>(DockerImageName.parse("neo4j:5"))
      .withAdminPassword("shepardshepard");

  /** MongoDB 7 — wired via {@code quarkus.mongodb.connection-string}. */
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

  /**
   * Postgres 15 — wired as the default (timeseries) datasource.
   *
   * <p>Uses the vanilla {@code postgres:15} image. To exercise TimescaleDB
   * hypertable DDL during Flyway, replace the image with
   * {@code timescale/timescaledb:latest-pg15}. The plain Postgres image
   * suffices for most integration tests and pulls faster in CI.
   */
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:15"))
      .withDatabaseName("shepard")
      .withUsername("shepard")
      .withPassword("shepard_secret");

  // ---------------------------------------------------------------------------

  @Override
  public Map<String, String> start() {
    // Start all containers in parallel — same virtual-thread pattern as
    // DbConnectivityWarmer to minimise wall-clock startup time.
    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
      var f1 = CompletableFuture.runAsync(NEO4J::start, exec);
      var f2 = CompletableFuture.runAsync(MONGO::start, exec);
      var f3 = CompletableFuture.runAsync(POSTGRES::start, exec);
      CompletableFuture.allOf(f1, f2, f3).join();
    }

    Map<String, String> config = new HashMap<>();

    // --- Neo4j ---
    // NeoConnector reads: neo4j.host (bolt host:port), neo4j.username, neo4j.password.
    // Neo4jContainer.getBoltUrl() returns "bolt://localhost:<port>" — strip the scheme.
    String boltUrl = NEO4J.getBoltUrl(); // e.g. bolt://localhost:49153
    String boltHostPort = boltUrl.replaceFirst("^bolt://", "");
    config.put("neo4j.host", boltHostPort);
    config.put("neo4j.username", "neo4j");
    config.put("neo4j.password", "shepardshepard");

    // --- MongoDB ---
    config.put("quarkus.mongodb.connection-string", MONGO.getConnectionString());

    // --- PostgreSQL (default datasource — timeseries) ---
    config.put("quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl());
    config.put("quarkus.datasource.username", POSTGRES.getUsername());
    config.put("quarkus.datasource.password", POSTGRES.getPassword());

    // --- Disable spatial datasource ---
    // %test inherits %dev, which enables spatial with a second Postgres on port 5433.
    // Disable it for DX1 phase-1 — add a second PostgreSQLContainer when spatial
    // integration tests land.
    config.put("shepard.infrastructure.spatial.enabled", "false");
    config.put("shepard.spatial-data.enabled", "false");
    config.put("quarkus.flyway.spatial.active", "false");
    config.put("quarkus.hibernate-orm.spatial.active", "false");

    return config;
  }

  @Override
  public void stop() {
    // Ryuk handles cleanup on JVM exit; explicit stop is a courtesy for
    // frameworks that call stop() between test classes.
    try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
      var f1 = CompletableFuture.runAsync(() -> { if (NEO4J.isRunning()) NEO4J.stop(); }, exec);
      var f2 = CompletableFuture.runAsync(() -> { if (MONGO.isRunning()) MONGO.stop(); }, exec);
      var f3 = CompletableFuture.runAsync(() -> { if (POSTGRES.isRunning()) POSTGRES.stop(); }, exec);
      CompletableFuture.allOf(f1, f2, f3).join();
    }
  }
}
