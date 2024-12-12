package de.dlr.shepard;

import de.dlr.shepard.configuration.feature.toggles.MigrationModeToggle;
import de.dlr.shepard.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.neo4j.MigrationsRunner;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PKIHelper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DatabaseInitAndShutdown {

  @Inject
  InfluxDBConnector influxdb;

  private static IConnector neo4j = NeoConnector.getInstance();

  @Startup
  void init() {
    Log.info("Starting shepard backend");

    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    Log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    Log.info("Run database migrations");
    migrationRunner.apply();

    Log.info("Initialize databases");
    neo4j.connect();
    Log.info("Connection established to neo4j database.");
    if (MigrationModeToggle.isActive()) {
      influxdb.connect();
    }
    Log.info(("Connection established to influx database."));
  }

  @Shutdown
  void shutdown() {
    neo4j.disconnect();
    influxdb.disconnect();
  }
}
