package de.dlr.shepard.neo4j;

import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.MigrationsConfig;
import ac.simons.neo4j.migrations.core.MigrationsException;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

@Slf4j
public class MigrationsRunner {

  private Migrations migrations;
  private Driver driver;

  public MigrationsRunner() {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = "neo4j://" + ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    driver = GraphDatabase.driver(host, AuthTokens.basic(username, password));

    // This is a workaround to make all migrations available in a dockerized quarkus jar.
    // See https://gitlab.com/dlr-shepard/shepard/-/issues/146 for more information
    var localPath = this.getClass().getClassLoader().getResource("neo4j/migrations").toString();
    var dockerPath = Paths.get("/deployments/neo4j/migrations").toAbsolutePath().toString();
    var isDocker = localPath.startsWith("jar");
    var locationsToScan = isDocker ? dockerPath : localPath;

    var config = MigrationsConfig.builder()
      .withTransactionMode(MigrationsConfig.TransactionMode.PER_STATEMENT)
      .withPackagesToScan("de.dlr.shepard.neo4j.migrations")
      .withLocationsToScan("file://" + locationsToScan)
      .build();

    migrations = new Migrations(config, driver);
  }

  public void waitForConnection() {
    while (true) {
      try {
        driver.verifyConnectivity();
        break;
      } catch (Exception e) {
        log.warn("Cannot connect to neo4j database. Retrying...");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        log.error("Cannot sleep while waiting for neo4j Connection");
        Thread.currentThread().interrupt();
      }
    }
  }

  public void apply() {
    try {
      migrations.apply();
    } catch (ServiceUnavailableException e) {
      log.error("Migrations cannot be executed because the neo4j database is not available");
    } catch (MigrationsException e) {
      log.error("An error occurred during the execution of the migrations: ", e);
    }
  }
}
