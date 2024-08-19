package de.dlr.shepard;

import de.dlr.shepard.influxDB.InfluxDBConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4j.MigrationsRunner;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PKIHelper;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class QuarkusApplication {

  @Inject
  InfluxDBConnector influxdb;

  @Inject
  MongoDBConnector mongodb;

  private static IConnector neo4j = NeoConnector.getInstance();

  @Startup
  void init() {
    log.info("Starting shepard backend");

    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    log.info("Run database migrations");
    migrationRunner.apply();

    log.info("Initialize databases");
    neo4j.connect();
    log.info("Connection established to neo4j database.");
    mongodb.connect();
    log.info("Connection established to mongodb database.");
    influxdb.connect();
    log.info(("Connection established to influx database."));
  }

  @Shutdown
  void shutdown() {
    neo4j.disconnect();
    mongodb.disconnect();
    influxdb.disconnect();
  }
}
