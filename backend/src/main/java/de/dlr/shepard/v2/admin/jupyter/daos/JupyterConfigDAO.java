package de.dlr.shepard.v2.admin.jupyter.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * J1e — DAO for the singleton {@link JupyterConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup seed + admin REST + on every
 * data-references render to gate the JupyterHub button — all low-volume
 * and the OGM session is short-lived per call. The singleton invariant
 * (exactly one {@code :JupyterConfig} node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code JupyterConfigService.seedIfNeeded()} creating the node
 *       when none exists.</li>
 *   <li>The {@code V94} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 *
 * <p>Mirrors the {@code SqlTimeseriesConfigDAO} / {@code InstanceRorConfigDAO}
 * shape — per the ROR1 pattern that all admin-config singletons follow.
 */
@ApplicationScoped
public class JupyterConfigDAO extends GenericDAO<JupyterConfig> {

  @Override
  public Class<JupyterConfig> getEntityType() {
    return JupyterConfig.class;
  }

  /**
   * Load the single {@link JupyterConfig} node, or {@code null} if
   * none has been seeded yet. Callers should treat {@code null} as
   * "config not yet initialised" and either seed the default or fail-
   * fast depending on their needs.
   */
  public JupyterConfig findSingleton() {
    Collection<JupyterConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // V94 uniqueness constraint + service-layer seed guarantees at most
    // one row; multiple rows are a bug elsewhere — return the smallest
    // Neo4j id deterministically.
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
