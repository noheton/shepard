package de.dlr.shepard;

import de.dlr.shepard.configuration.feature.toggles.MigrationModeToggle;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@QuarkusMain
public class ShepardMain implements QuarkusApplication {

  @Inject
  MigrationInitService migrationInitService;

  @Override
  @ActivateRequestContext
  public int run(String... args) throws Exception {
    if (MigrationModeToggle.isActive() && migrationInitService.isInfluxDataMigratedAlready()) {
      Log.warn("Influx Data has already been migrated. Please restart shepard without migration mode. Exiting...");
      return 0;
    }
    if (MigrationModeToggle.isActive()) {
      int migrationExitCode = migrationInitService.runMigrations();
      Quarkus.waitForExit();
      return migrationExitCode;
    }
    if (!migrationInitService.isInfluxDataMigratedAlready()) {
      Log.error(
        "Can't start the instance without first migrating Influx Data. Enable migration mode to start the migration."
      );
      return 1;
    }

    migrationInitService.warnIfInfluxIsStillConfigured();
    Quarkus.waitForExit();
    return 0;
  }
}
