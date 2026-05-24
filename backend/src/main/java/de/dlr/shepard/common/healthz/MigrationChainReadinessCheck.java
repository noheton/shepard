package de.dlr.shepard.common.healthz;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import ac.simons.neo4j.migrations.core.MigrationsConfig.VersionSortOrder;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * SmallRye readiness check that asserts the Neo4j migration chain on
 * the live database matches the classpath. Closes
 * {@code OPS-MIGRATION-HEALTHCHECK} (2026-05-24): startup applies
 * migrations exactly once and aborts on error, but a deploy where
 * {@code MigrationsRunner.apply()} never ran (e.g. an operator
 * recovered a corrupted Neo4j by restoring an older snapshot, or a
 * past deploy somehow skipped a version) had no signal — the existing
 * {@link NeoHealthCheck} ping returns UP as long as the driver can
 * round-trip a single query.
 *
 * <p>Implementation:
 * <ul>
 *   <li>Owns its own long-lived {@link Driver} — we cannot reuse the
 *       OGM session factory because the migrations library wants a
 *       raw bolt {@link Driver}, and we cannot reuse
 *       {@link de.dlr.shepard.common.neo4j.MigrationsRunner}'s driver
 *       because it is local to {@code ShepardMain.init()} and closed
 *       after {@code apply()}.</li>
 *   <li>Configuration mirrors {@code MigrationsRunner} so the
 *       readiness probe sees the same set of migrations the startup
 *       gate did. If those drift apart, the operator gets a false
 *       positive — review both classes together when touching either.</li>
 *   <li>Caches the last result for {@code shepard.health.readiness.max-staleness}
 *       (default 30 s) so k8s readiness probes don't hammer Neo4j.</li>
 * </ul>
 */
@Readiness
@ApplicationScoped
public class MigrationChainReadinessCheck implements HealthCheck {

  static final String CHECK_NAME = "neo4j-migration-chain-readiness";

  @Inject
  ReadinessConfig readinessConfig;

  private MigrationChainInspector inspector = new MigrationChainInspector();

  private Driver driver;
  private Migrations migrations;
  private volatile MigrationChainStatus cached;

  @PostConstruct
  void init() {
    try {
      String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
      String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
      String host = "neo4j://" + ConfigProvider.getConfig().getValue("neo4j.host", String.class);
      this.driver = GraphDatabase.driver(host, AuthTokens.basic(username, password));

      // Mirror MigrationsRunner's classpath / docker location detection.
      String locationsToScan = resolveLocations();
      MigrationsConfig config = MigrationsConfig.builder()
        .withTransactionMode(MigrationsConfig.TransactionMode.PER_STATEMENT)
        .withPackagesToScan("de.dlr.shepard.common.neo4j.migrations")
        .withLocationsToScan("file://" + locationsToScan)
        .withVersionSortOrder(VersionSortOrder.SEMANTIC)
        .build();
      this.migrations = new Migrations(config, driver);
    } catch (Exception e) {
      // Never block startup on a readiness-check init failure — the
      // call() path will report CHECK_FAILED on every probe instead.
      Log.errorf(
        e,
        "MigrationChainReadinessCheck: failed to initialise — readiness will report DOWN until init recovers"
      );
    }
  }

  @PreDestroy
  void close() {
    if (driver != null) {
      try {
        driver.close();
      } catch (Exception ignored) {
        // shutdown — nothing to do
      }
    }
  }

  @Override
  public HealthCheckResponse call() {
    MigrationChainStatus status = currentStatus();
    HealthCheckResponseBuilder b = HealthCheckResponse.named(CHECK_NAME)
      .withData("outcome", status.outcome())
      .withData("checkedAtEpochMs", status.checkedAtEpochMs())
      .withData("maxStalenessMs", readinessConfig.maxStalenessMs());
    if (status.healthy()) {
      return b.up().build();
    }
    if (!status.pendingVersions().isEmpty()) {
      b.withData("pendingVersions", String.join(",", status.pendingVersions()));
    }
    if (!status.warnings().isEmpty()) {
      // join with " | " so prettyPrint-style multi-line warnings stay readable in a single JSON field
      b.withData("warnings", String.join(" | ", status.warnings()));
    }
    if (status.errorMessage() != null) {
      b.withData("errorMessage", status.errorMessage());
    }
    return b.down().build();
  }

  /**
   * Package-private for tests: returns the cached status if it's
   * still fresh, otherwise re-runs the inspector.
   */
  synchronized MigrationChainStatus currentStatus() {
    long now = System.currentTimeMillis();
    long maxStaleness = readinessConfig.maxStalenessMs();
    MigrationChainStatus snapshot = cached;
    if (snapshot != null && (now - snapshot.checkedAtEpochMs()) <= maxStaleness) {
      return snapshot;
    }
    MigrationChainStatus fresh = inspector.inspect(migrations);
    cached = fresh;
    return fresh;
  }

  /**
   * Mirrors the docker-vs-local resolution in
   * {@code MigrationsRunner}: production runs from a fat jar at
   * {@code /deployments} and reads cypher files from a sibling
   * {@code neo4j/migrations} directory unpacked at build time;
   * tests / dev run from the classpath. We intentionally use the
   * same algorithm rather than inject a path — keeping the two
   * classes lockstep is the whole point.
   */
  private static String resolveLocations() {
    String localPath = MigrationChainReadinessCheck.class.getClassLoader()
      .getResource("neo4j/migrations")
      .toString();
    String dockerPath = Path.of("/deployments/neo4j/migrations").toAbsolutePath().toString();
    boolean isDocker = localPath.startsWith("jar");
    return isDocker ? dockerPath : localPath;
  }

  /**
   * Test seam — inject a Migrations instance, ReadinessConfig, and
   * inspector + bypass init(). The inspector is required (no 2-arg
   * convenience overload) because the default extractor talks to the
   * library's sealed {@code ac.simons.neo4j.migrations.core.MigrationChain};
   * tests must supply an extractor that returns a controlled
   * {@link MigrationChainInspector.ChainSnapshot}.
   */
  static MigrationChainReadinessCheck forTest(
    Migrations migrations,
    ReadinessConfig config,
    MigrationChainInspector inspector
  ) {
    MigrationChainReadinessCheck c = new MigrationChainReadinessCheck();
    c.migrations = migrations;
    c.readinessConfig = config;
    c.inspector = inspector;
    return c;
  }

  // Test seam: peek at cached state without running.
  MigrationChainStatus cachedForTest() {
    return cached;
  }
}
