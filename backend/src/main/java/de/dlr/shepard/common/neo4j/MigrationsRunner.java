package de.dlr.shepard.common.neo4j;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import ac.simons.neo4j.migrations.core.MigrationsConfig.VersionSortOrder;
import ac.simons.neo4j.migrations.core.MigrationsException;
import io.quarkus.logging.Log;
import jakarta.annotation.Nullable;
import java.nio.file.Path;
import java.time.Duration;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

public class MigrationsRunner {

  static final String CONNECTION_WAIT_TIMEOUT_PROPERTY = "shepard.migrations.connection-wait-timeout";
  static final Duration DEFAULT_CONNECTION_WAIT_TIMEOUT = Duration.ofSeconds(60);
  static final Duration INITIAL_BACKOFF = Duration.ofMillis(250);
  // Cap the per-attempt sleep so logs surface a "still trying" message at least every 5s.
  static final Duration MAX_BACKOFF = Duration.ofSeconds(5);

  private final Migrations migrations;
  private final Driver driver;
  private final Duration connectionWaitTimeout;

  public MigrationsRunner() {
    this(null);
  }

  public MigrationsRunner(@Nullable String targetVersion) {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = "neo4j://" + ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    driver = GraphDatabase.driver(host, AuthTokens.basic(username, password));
    connectionWaitTimeout = ConfigProvider.getConfig()
      .getOptionalValue(CONNECTION_WAIT_TIMEOUT_PROPERTY, Duration.class)
      .orElse(DEFAULT_CONNECTION_WAIT_TIMEOUT);

    // This is a workaround to make all migrations available in a dockerized quarkus jar.
    // See https://gitlab.com/dlr-shepard/shepard/-/issues/146 for more information
    var localPath = this.getClass().getClassLoader().getResource("neo4j/migrations").toString();
    var dockerPath = Path.of("/deployments/neo4j/migrations").toAbsolutePath().toString();
    var isDocker = localPath.startsWith("jar");
    var locationsToScan = isDocker ? dockerPath : localPath;

    var config = MigrationsConfig.builder()
      .withTransactionMode(MigrationsConfig.TransactionMode.PER_STATEMENT)
      .withPackagesToScan("de.dlr.shepard.common.neo4j.migrations")
      .withLocationsToScan("file://" + locationsToScan)
      .withVersionSortOrder(VersionSortOrder.SEMANTIC)
      .withTarget(targetVersion)
      .build();

    migrations = new Migrations(config, driver);
  }

  public void waitForConnection() {
    awaitConnectivity(driver::verifyConnectivity, connectionWaitTimeout, Thread::sleep, System::nanoTime);
  }

  static void awaitConnectivity(Runnable connectivityCheck, Duration timeout, Sleeper sleeper, NanoClock clock) {
    long deadlineNanos = clock.nanoTime() + timeout.toNanos();
    Duration backoff = INITIAL_BACKOFF;
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        connectivityCheck.run();
        return;
      } catch (Exception e) {
        long remainingNanos = deadlineNanos - clock.nanoTime();
        if (remainingNanos <= 0) {
          throw new ConnectionWaitTimeoutException(
            "Timed out after " + timeout + " waiting for neo4j connectivity (attempts=" + attempt + ")",
            e
          );
        }
        long sleepMillis = Math.min(backoff.toMillis(), Math.max(1, remainingNanos / 1_000_000));
        Log.warnf(
          "Cannot connect to neo4j database (attempt %d, %s: %s). Retrying in %d ms...",
          attempt,
          e.getClass().getSimpleName(),
          e.getMessage(),
          sleepMillis
        );
        try {
          sleeper.sleep(sleepMillis);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new ConnectionWaitTimeoutException("Interrupted while waiting for neo4j connectivity", ie);
        }
        backoff = nextBackoff(backoff);
      }
    }
  }

  private static Duration nextBackoff(Duration current) {
    Duration doubled = current.multipliedBy(2);
    return doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
  }

  public void apply() {
    runMigrations(migrations::apply);
  }

  static void runMigrations(Runnable migrationsApply) {
    try {
      migrationsApply.run();
    } catch (ServiceUnavailableException e) {
      Log.error("Migrations cannot be executed because the neo4j database is not available", e);
      throw new RuntimeException("Aborting startup: neo4j became unavailable during migrations", e);
    } catch (MigrationsException e) {
      Log.error("An error occurred during the execution of the migrations: ", e);
      throw new RuntimeException("Aborting startup: neo4j migration failed", e);
    }
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  @FunctionalInterface
  interface NanoClock {
    long nanoTime();
  }
}
