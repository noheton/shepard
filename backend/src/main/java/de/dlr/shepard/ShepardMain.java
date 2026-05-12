package de.dlr.shepard;

import de.dlr.shepard.auth.bootstrap.BootstrapTokenInitializer;
import de.dlr.shepard.auth.permission.services.OrphanPermissionsBackfillContext;
import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.IConnector;
import de.dlr.shepard.common.util.PKIHelper;
import de.dlr.shepard.context.semantic.N10sBootstrapHook;
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

    // A0 / C3 — refuse startup if orphan entities exist and
    // `shepard.permissions.default-owner` is unset. Seeds the V14
    // migration's context node when properly configured.
    Log.info("Checking permission-backfill prerequisites");
    new OrphanPermissionsBackfillContext().prepare();

    Log.info("Run database migrations");
    migrationRunner.apply();

    Log.info("Initialize databases");
    neo4j.connect();
    Log.info("Connection established to neo4j database.");

    // A0 — ensure the canonical instance-admin Role node exists post-V13.
    var session = NeoConnector.getInstance().getNeo4jSession();
    if (session != null) {
      session.query(
        "MERGE (r:Role {name: $name}) " +
        "ON CREATE SET r.displayName = $displayName, r.appId = randomUUID() " +
        "ON MATCH SET r.displayName = coalesce(r.displayName, $displayName)",
        java.util.Map.of(
          "name",
          Constants.INSTANCE_ADMIN_ROLE,
          "displayName",
          Constants.INSTANCE_ADMIN_DISPLAY_NAME
        )
      );
    }

    // A0 — bootstrap-token initializer. Generates a one-shot token if
    // zero instance-admins exist; logs the path for the operator.
    new BootstrapTokenInitializer().runIfNeeded();

    // N1a — initialise the neosemantics graph config for
    // SemanticRepositoryType.INTERNAL repositories. Runs post-A1e
    // (after MigrationsRunner.apply) so the n10s_unique_uri
    // constraint creation doesn't race with V11's appId constraints.
    // Fail-soft: a missing n10s plugin is logged + skipped, not
    // fatal — see N10sBootstrapHook javadoc.
    new N10sBootstrapHook().run();
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
