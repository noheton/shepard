package de.dlr.shepard.auth.permission.services;

import io.quarkus.logging.Log;
import java.time.Duration;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

/**
 * Pre-migration hook that prepares the graph for V14's orphan-permission
 * backfill (aidocs/51 §8 / aidocs/07 C3).
 *
 * <p>Runs from {@code ShepardMain.init} between
 * {@code MigrationsRunner.waitForConnection()} and
 * {@code MigrationsRunner.apply()}. Three responsibilities:
 *
 * <ol>
 *   <li>Read {@code shepard.permissions.default-owner} from config.
 *   <li>Detect whether the graph has any orphan {@code BasicEntity}
 *       nodes (i.e. nodes lacking a {@code :has_permissions} edge).
 *   <li>If orphans exist AND the config is unset → <b>abort startup</b>
 *       with a clear, actionable error. Operators must set the config
 *       before the next start.
 *   <li>If the config is set but the named user doesn't exist →
 *       abort, same shape.
 *   <li>Otherwise seed the singleton
 *       {@code (:_ShepardMigrationContext {defaultOwner: $u})} node
 *       so V14 picks up the configured owner.
 * </ol>
 *
 * <p>The orphan-permissions detection runs even when the config is
 * unset because we want the "no orphans, no config — happy path"
 * deployment to start fine. Aborting unconditionally on missing
 * config (the dispatcher's documented fallback) would lock-out
 * greenfield installs that have nothing to backfill.
 *
 * <p>This class uses a fresh Neo4j {@code Driver} (same pattern as
 * {@link de.dlr.shepard.common.neo4j.MigrationsRunner}) because it
 * runs before {@code NeoConnector.connect()} and the OGM session
 * factory therefore isn't ready.
 */
public class OrphanPermissionsBackfillContext {

  static final String DEFAULT_OWNER_PROPERTY = "shepard.permissions.default-owner";

  private final Driver driver;
  private final boolean ownDriver;
  private final String defaultOwnerOverride;
  private final boolean overrideSet;

  public OrphanPermissionsBackfillContext() {
    String username = ConfigProvider.getConfig().getValue("neo4j.username", String.class);
    String password = ConfigProvider.getConfig().getValue("neo4j.password", String.class);
    String host = "neo4j://" + ConfigProvider.getConfig().getValue("neo4j.host", String.class);
    this.driver = GraphDatabase.driver(host, AuthTokens.basic(username, password));
    this.ownDriver = true;
    this.defaultOwnerOverride = null;
    this.overrideSet = false;
  }

  /** Test seam — accepts a pre-built driver. */
  public OrphanPermissionsBackfillContext(Driver driver) {
    this.driver = driver;
    this.ownDriver = false;
    this.defaultOwnerOverride = null;
    this.overrideSet = false;
  }

  /**
   * Test seam — accepts a pre-built driver and an explicit
   * default-owner value (or {@code null} to indicate "unset"). Used
   * by the unit tests so they don't have to fight SmallRye config
   * caching.
   */
  public OrphanPermissionsBackfillContext(Driver driver, String defaultOwner) {
    this.driver = driver;
    this.ownDriver = false;
    this.defaultOwnerOverride = defaultOwner == null || defaultOwner.isBlank() ? null : defaultOwner.trim();
    this.overrideSet = true;
  }

  /**
   * Run the pre-migration check + seed. Throws
   * {@link OrphanPermissionsConfigException} on the misconfigured
   * paths so {@code ShepardMain} aborts startup (post-A1e fail-fast).
   */
  public void prepare() {
    String defaultOwner;
    if (overrideSet) {
      defaultOwner = defaultOwnerOverride;
    } else {
      defaultOwner = ConfigProvider.getConfig()
        .getOptionalValue(DEFAULT_OWNER_PROPERTY, String.class)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .orElse(null);
    }

    try (Session session = driver.session()) {
      long orphanCount = countOrphans(session);

      if (defaultOwner == null) {
        if (orphanCount > 0) {
          throw new OrphanPermissionsConfigException(
            "Found " + orphanCount + " BasicEntity nodes without a :has_permissions edge, but " +
            DEFAULT_OWNER_PROPERTY + " is unset. Set this config to an existing username and restart " +
            "so V14__Backfill_orphan_permissions can attach default Permissions. " +
            "Operator runbook: aidocs/34 row C3."
          );
        }
        // No orphans + no config = happy path. Wipe any stale context
        // node from a previous half-applied run so V14 cleanly no-ops.
        session.run("MATCH (ctx:_ShepardMigrationContext) DETACH DELETE ctx").consume();
        Log.debug("No orphan permissions found; skipping backfill seed.");
        return;
      }

      // Config is set — verify the owner actually exists. We're inside
      // the migrations workflow so the OGM isn't running yet; use a
      // direct Cypher MATCH.
      boolean ownerExists = userExists(session, defaultOwner);
      if (!ownerExists) {
        throw new OrphanPermissionsConfigException(
          DEFAULT_OWNER_PROPERTY + " is set to '" + defaultOwner + "' but no User with that " +
          "username exists in Neo4j. Either pre-create the user (e.g. via the OIDC sync after " +
          "their first login) or set a different default-owner. Aborting startup."
        );
      }

      seedContext(session, defaultOwner);
      Log.infof(
        "Permission-backfill context seeded: defaultOwner='%s', orphans=%d",
        defaultOwner,
        orphanCount
      );
    } finally {
      if (ownDriver) {
        driver.close();
      }
    }
  }

  private static long countOrphans(Session session) {
    var result = session.run(
      "MATCH (e:BasicEntity) WHERE NOT (e)-[:has_permissions]->(:Permissions) RETURN count(e) AS c"
    );
    return result.single().get("c").asLong();
  }

  private static boolean userExists(Session session, String username) {
    var result = session.run(
      "MATCH (u:User {username: $u}) RETURN count(u) > 0 AS exists",
      java.util.Map.of("u", username)
    );
    return result.single().get("exists").asBoolean();
  }

  private static void seedContext(Session session, String owner) {
    session.run(
      "MERGE (ctx:_ShepardMigrationContext) SET ctx.defaultOwner = $u",
      java.util.Map.of("u", owner)
    ).consume();
  }

  /**
   * Thrown when the orphan-permission backfill cannot proceed because
   * of misconfiguration. Caught by {@code ShepardMain} which logs and
   * exits non-zero.
   */
  public static class OrphanPermissionsConfigException extends RuntimeException {

    public OrphanPermissionsConfigException(String message) {
      super(message);
    }
  }
}
