package de.dlr.shepard.plugins.aas.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.aas.entities.AasConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * AAS1l — DAO for the singleton {@link AasConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup and on every admin PATCH.
 * The singleton invariant (exactly one {@code :AasConfig} node) is
 * held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code AasConfigService.seedIfNeeded()} creating the node
 *       when none exists.</li>
 *   <li>The {@code V88} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class AasConfigDAO extends GenericDAO<AasConfig> {

  @Override
  public Class<AasConfig> getEntityType() {
    return AasConfig.class;
  }

  /**
   * Load the single {@link AasConfig} node, or {@code null} if
   * none has been seeded yet. Callers should treat {@code null} as
   * "feature unconfigured" and either seed the default or fail-fast
   * depending on the caller's needs.
   */
  public AasConfig findSingleton() {
    Collection<AasConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // The V88 uniqueness constraint + service-layer seed guarantees
    // at most one row; an installed multiplicity is a bug elsewhere
    // — return the first deterministically (smallest Neo4j id).
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
