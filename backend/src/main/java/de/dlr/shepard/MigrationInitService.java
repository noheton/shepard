package de.dlr.shepard;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MigrationInitService {

  @ConfigProperty(name = "influx.host")
  Optional<String> influxHost;

  @ConfigProperty(name = "influx.username")
  Optional<String> influxUsername;

  @ConfigProperty(name = "influx.password")
  Optional<String> influxPassword;

  public boolean isInfluxDataMigratedAlready() {
    // TODO: Properly detect
    return true;
  }

  public void warnIfInfluxIsStillConfigured() {
    if (influxHost.isPresent() || influxUsername.isPresent() || influxPassword.isPresent()) {
      Log.warn(
        "Influx connection is configured but shepard does not support Influx anymore. Feel free to remove the influx configuration properties."
      );
    }
  }

  public int runMigrations() {
    Log.info("Running migration from InfluxDB to TimescaleDB.");

    // migrationService.doYourThing()
    return 0;
  }
}
