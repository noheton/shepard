package de.dlr.shepard.plugins.video.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * VID1c — DAO for the singleton {@link VideoConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on every upload and the OGM session
 * is short-lived per call. The singleton invariant (exactly one
 * {@code :VideoConfig} node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code VideoConfigService.seedIfNeeded()} creating the node
 *       when none exists.</li>
 *   <li>The {@code V89} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class VideoConfigDAO extends GenericDAO<VideoConfig> {

  @Override
  public Class<VideoConfig> getEntityType() {
    return VideoConfig.class;
  }

  /**
   * Load the single {@link VideoConfig} node, or {@code null} if
   * none has been seeded yet. Callers should treat {@code null} as
   * "feature unconfigured" and either seed the default or fail-fast
   * depending on the caller's needs.
   */
  public VideoConfig findSingleton() {
    Collection<VideoConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // The V89 uniqueness constraint + service-layer seed guarantees
    // at most one row; an installed multiplicity is a bug elsewhere
    // — return the first deterministically (smallest Neo4j id).
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
