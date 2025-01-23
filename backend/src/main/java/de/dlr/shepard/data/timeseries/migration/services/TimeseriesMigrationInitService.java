package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.common.configuration.feature.toggles.MigrationModeToggle;
import de.dlr.shepard.data.timeseries.migration.model.MigrationState;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TimeseriesMigrationInitService {

  @ConfigProperty(name = "influx.host")
  Optional<String> influxHost;

  @ConfigProperty(name = "influx.username")
  Optional<String> influxUsername;

  @ConfigProperty(name = "influx.password")
  Optional<String> influxPassword;

  @Inject
  TimeseriesMigrationService timeseriesMigrationService;

  /**
   * @return true if shepard should terminate
   */
  public boolean orchestrateMigrations() {
    if (MigrationModeToggle.isActive()) {
      return runMigrationsIfNecessary();
    }

    return !assertShepardCanRunNormally();
  }

  /**
   * @return true if shepard should terminate
   */
  private boolean runMigrationsIfNecessary() {
    MigrationState migrationState = timeseriesMigrationService.getMigrationState();

    if (migrationState == MigrationState.NotNeeded) {
      logThatMigrationModeCanBeDisabled();
      return false;
    }
    runMigrations();
    return false;
  }

  /**
   * @return false if shepard should terminate
   */
  private boolean assertShepardCanRunNormally() {
    MigrationState migrationState = timeseriesMigrationService.getMigrationState();
    if (migrationState == MigrationState.HasErrors) {
      logThatMigrationStateHasErrors();
      return false;
    }
    if (migrationState == MigrationState.Needed) {
      logThatMigrationsNeedToBeRun();
      return false;
    }

    if (isInfluxConfigured()) logThatInfluxIsStillConfigured();

    return true;
  }

  private void runMigrations() {
    Log.info("Running migration from InfluxDB to TimescaleDB.");
    timeseriesMigrationService.runMigrations();

    var totalTasks = timeseriesMigrationService.getMigrationTasks(false);
    var tasksWithErrors = timeseriesMigrationService.getMigrationTasks(true);
    int tasksWithErrorsCount = tasksWithErrors.size();

    if (tasksWithErrorsCount == 0) {
      Log.info("Migrations successfully completed.");
    } else {
      Log.errorf(
        "%d errors occurred during migration of %d containers. " +
        "You can retrieve the current migration status at <backend-url>/shepard/api/temp/migrations/state",
        tasksWithErrorsCount,
        totalTasks.size()
      );
    }
  }

  private boolean isInfluxConfigured() {
    return influxHost.isPresent() || influxUsername.isPresent() || influxPassword.isPresent();
  }

  private void logThatMigrationModeCanBeDisabled() {
    Log.warn("No migration necessary. Please restart shepard without migration mode. Exiting...");
  }

  private void logThatMigrationStateHasErrors() {
    Log.error(
      "Migrations ran with errors. " +
      "You can retrieve the current migration status in migration mode at \"<backend-url>/shepard/api/temp/migrations/state\". " +
      "Please try again after cleaning your TimescaleDB instance."
    );
  }

  private void logThatMigrationsNeedToBeRun() {
    Log.error(
      "Can't start the instance without first migrating InfluxDB Data. Enable migration mode to start the migration."
    );
  }

  private void logThatInfluxIsStillConfigured() {
    Log.warn(
      "InfluxDB connection is configured but shepard does not support InfluxDB anymore. Feel free to remove the InfluxDB configuration properties."
    );
  }
}
