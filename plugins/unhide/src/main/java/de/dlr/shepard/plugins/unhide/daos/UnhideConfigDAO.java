package de.dlr.shepard.plugins.unhide.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.unhide.entities.UnhideConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * UH1a — DAO for the singleton {@link UnhideConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on every feed request and the OGM
 * session is short-lived per call. The singleton invariant (exactly
 * one {@code :UnhideConfig} node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code UnhideConfigService.seedIfNeeded()} creating the
 *       node when none exists.</li>
 *   <li>The {@code V27} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class UnhideConfigDAO extends GenericDAO<UnhideConfig> {

  @Override
  public Class<UnhideConfig> getEntityType() {
    return UnhideConfig.class;
  }

  /**
   * Load the single {@link UnhideConfig} node, or {@code null} if
   * none has been seeded yet. Callers should treat {@code null} as
   * "feature unconfigured" and either seed the default or fail-fast
   * depending on the caller's needs.
   */
  public UnhideConfig findSingleton() {
    Collection<UnhideConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // The V27 uniqueness constraint + service-layer seed guarantees
    // at most one row; an installed multiplicity is a bug elsewhere
    // — return the first deterministically (smallest Neo4j id).
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
