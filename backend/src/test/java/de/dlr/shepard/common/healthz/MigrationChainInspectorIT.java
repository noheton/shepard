package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.simons.neo4j.migrations.core.MigrationsConfig;
import ac.simons.neo4j.migrations.core.MigrationsConfig.VersionSortOrder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end test for {@link MigrationChainInspector} against a real
 * Neo4j testcontainer + the real
 * {@link ac.simons.neo4j.migrations.core.Migrations} library.
 *
 * <p>Two scenarios:
 * <ol>
 *   <li><b>Healthy chain</b> — apply migrations from a temp dir,
 *       inspect against the same dir → readiness UP / outcome VALID.</li>
 *   <li><b>Deliberately mismatched chain</b> — apply migrations from
 *       a smaller dir, then inspect against an expanded dir
 *       containing an extra {@code V99__never_applied.cypher} →
 *       readiness DOWN with the new version listed as pending.</li>
 * </ol>
 *
 * <p>We deliberately bypass the production {@code MigrationsRunner}
 * driver path and construct {@link
 * ac.simons.neo4j.migrations.core.Migrations} directly so the test
 * can swap location directories between the two scenarios. The
 * inspector itself uses the same library entry points the production
 * path does — the readiness signal and the {@code apply()} gate
 * share their definition of "chain matches".
 */
@Tag("integration")
@Testcontainers
public class MigrationChainInspectorIT {

  @Container
  static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>(DockerImageName.parse("neo4j:5"))
    .withAdminPassword("shepardshepard");

  private static Driver driver;

  @BeforeAll
  static void openDriver() {
    NEO4J.start();
    driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "shepardshepard"));
  }

  @AfterAll
  static void closeDriver() {
    if (driver != null) {
      driver.close();
    }
  }

  @Test
  public void inspect_returnsHealthyAfterFullApply() throws IOException {
    Path migrationsDir = Files.createTempDirectory("mig-healthy-");
    writeMigration(migrationsDir, "V1__create_node.cypher", "CREATE (n:HealthCheckSentinel { id: 'V1' });");
    writeMigration(migrationsDir, "V2__more.cypher", "CREATE (n:HealthCheckSentinel { id: 'V2' });");

    ac.simons.neo4j.migrations.core.Migrations m = buildMigrations(migrationsDir);
    m.apply();

    MigrationChainInspector inspector = new MigrationChainInspector();
    MigrationChainStatus s = inspector.inspect(m);

    assertTrue(s.healthy(), () -> "expected healthy after apply, got: " + s);
    assertEquals("VALID", s.outcome());
    assertTrue(s.pendingVersions().isEmpty());
  }

  @Test
  public void inspect_returnsDownWithPendingWhenExtraMigrationAdded() throws IOException {
    // 1. Apply a 2-migration chain
    Path dir = Files.createTempDirectory("mig-mismatch-");
    writeMigration(dir, "V10__create_node.cypher", "CREATE (n:HealthCheckSentinel { id: 'V10' });");
    writeMigration(dir, "V11__more.cypher", "CREATE (n:HealthCheckSentinel { id: 'V11' });");

    ac.simons.neo4j.migrations.core.Migrations applied = buildMigrations(dir);
    applied.apply();

    // 2. Drop a 3rd migration into the same dir WITHOUT applying it.
    //    Building a new Migrations instance now picks it up as PENDING.
    writeMigration(dir, "V99__never_applied.cypher", "CREATE (n:HealthCheckSentinel { id: 'V99' });");
    ac.simons.neo4j.migrations.core.Migrations inspected = buildMigrations(dir);

    MigrationChainInspector inspector = new MigrationChainInspector();
    MigrationChainStatus s = inspector.inspect(inspected);

    assertFalse(s.healthy(), () -> "expected unhealthy after adding pending migration, got: " + s);
    assertTrue(
      s.pendingVersions().contains("99"),
      () -> "expected pending version 99, got: " + s.pendingVersions()
    );
    assertTrue(
      s.errorMessage().contains("99"),
      () -> "expected error message to name pending V99, got: " + s.errorMessage()
    );
    assertTrue(
      s.errorMessage().contains("runbooks/migration-chain-integrity.md"),
      () -> "expected error message to point at the runbook, got: " + s.errorMessage()
    );
  }

  // ------------------------------------------------------------------ helpers

  private static ac.simons.neo4j.migrations.core.Migrations buildMigrations(Path dir) {
    MigrationsConfig config = MigrationsConfig.builder()
      .withTransactionMode(MigrationsConfig.TransactionMode.PER_STATEMENT)
      .withLocationsToScan("file://" + dir.toAbsolutePath())
      .withVersionSortOrder(VersionSortOrder.SEMANTIC)
      .build();
    return new ac.simons.neo4j.migrations.core.Migrations(config, driver);
  }

  private static void writeMigration(Path dir, String name, String content) throws IOException {
    Path file = dir.resolve(name);
    Files.writeString(file, content + "\n");
    // Some filesystems default to restrictive perms; ensure the
    // library can read the file no matter who ran the test.
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX filesystem (Windows) — skip.
    }
  }
}
