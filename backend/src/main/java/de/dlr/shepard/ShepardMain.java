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
import jakarta.enterprise.context.control.ActivateRequestContext;

@QuarkusMain
public class ShepardMain implements QuarkusApplication {

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
