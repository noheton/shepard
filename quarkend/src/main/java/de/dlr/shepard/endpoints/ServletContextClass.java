package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.InfluxDBConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4j.MigrationsRunner;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PKIHelper;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.ServletContextEvent;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ServletContextClass {

  private static IConnector neo4j = NeoConnector.getInstance();
  private static IConnector mongodb = MongoDBConnector.getInstance();
  private static IConnector influxdb = InfluxDBConnector.getInstance();

  @Startup
  void init() {
    log.info("Starting shepard backend");
    // TODO: fix initialization of databases
    /*
    var pkiHelper = new PKIHelper();
    var migrationRunner = new MigrationsRunner();
    pkiHelper.init();

    log.info("Waiting for databases");
    migrationRunner.waitForConnection();

    log.info("Run database migrations");
    migrationRunner.apply();

    log.info("Initialize databases");
    neo4j.connect();
    mongodb.connect();
    influxdb.connect();
*/
  }

  @Shutdown
  void shutdown() {
    // TODO: fix initialization of databases
    // neo4j.disconnect();
    // mongodb.disconnect();
    // influxdb.disconnect();
  }
}
