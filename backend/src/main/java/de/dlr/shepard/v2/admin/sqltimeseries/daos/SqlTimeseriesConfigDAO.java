package de.dlr.shepard.v2.admin.sqltimeseries.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * P10c — DAO for the singleton {@link SqlTimeseriesConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup seed + admin REST — both
 * are low-volume and the OGM session is short-lived per call. The
 * singleton invariant (exactly one {@code :SqlTimeseriesConfig} node)
 * is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code SqlTimeseriesConfigService.seedIfNeeded()} creating the
 *       node when none exists.</li>
 *   <li>The {@code V43} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 *
 * <p>Mirrors the {@code InstanceRorConfigDAO} shape exactly — per the ROR1
 * pattern that all admin-config singletons follow.
 */
@ApplicationScoped
public class SqlTimeseriesConfigDAO extends GenericDAO<SqlTimeseriesConfig> {

  @Override
  public Class<SqlTimeseriesConfig> getEntityType() {
    return SqlTimeseriesConfig.class;
  }

  /**
   * Load the single {@link SqlTimeseriesConfig} node, or {@code null} if
   * none has been seeded yet. Callers should treat {@code null} as
   * "config not yet initialised" and either seed the default or fail-
   * fast depending on their needs.
   */
  public SqlTimeseriesConfig findSingleton() {
    Collection<SqlTimeseriesConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // V43 uniqueness constraint + service-layer seed guarantees at most
    // one row; multiple rows are a bug elsewhere — return the smallest
    // Neo4j id deterministically.
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
