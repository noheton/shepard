package de.dlr.shepard;

import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.IConnector;
import de.dlr.shepard.common.util.PKIHelper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;

@QuarkusMain
public class ShepardMain implements QuarkusApplication {

  private static final IConnector neo4j = NeoConnector.getInstance();

  @Inject
  Flyway flyway;

  @Startup
  void init() {
    Log.info("Starting shepard backend");

    migrateTimescale("1.7.0");

    migrateNeo4j("V12");

    Log.info("Run leftover timescale migrations...");
    flyway.migrate();
    Log.info("Finished leftover timescale migrations...");

    Log.info("Run leftover Neo4j migrations...");
    var neoRunner = new MigrationsRunner();
    neoRunner.waitForConnection();
    neoRunner.apply();
    Log.info("Finished leftover Neo4j migrations...");

    var pkiHelper = new PKIHelper();
    pkiHelper.init();

    Log.info("Initialize neo4j databases...");
    neo4j.connect();
    Log.info("Connection established to neo4j database!");
  }

  private void migrateTimescale(String version) {
    Log.info("Run PostGres migrations until V" + version + "...");
    if (version == null) flyway.migrate();
    else Flyway.configure().configuration(flyway.getConfiguration()).target(version).load().migrate();
    Log.info("Finished PostGres migrations V" + version + "!");
  }

  private void migrateNeo4j(String version) {
    var migrationRunner = new MigrationsRunner(version);
    Log.info("Waiting for neo4j database...");
    migrationRunner.waitForConnection();
    Log.info("Run neo4j database migrations until " + version + "...");
    migrationRunner.apply();
    Log.info("Finished neo4j database migrations!");
  }

  @Override
  @ActivateRequestContext
  public int run(String... args) throws Exception {
    Quarkus.waitForExit();
    return 0;
  }

  @Shutdown
  void shutdown() {
    neo4j.disconnect();
  }
}
