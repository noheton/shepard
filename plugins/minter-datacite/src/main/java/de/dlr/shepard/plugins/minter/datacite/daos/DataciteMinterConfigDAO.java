package de.dlr.shepard.plugins.minter.datacite.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * KIP1d — DAO for the singleton {@link DataciteMinterConfig} node.
 *
 * <p>Mirrors the UH1a {@code UnhideConfigDAO} shape (and through it
 * A3b / N1c2). {@code @ApplicationScoped} because the config is
 * read on every mint and the OGM session is short-lived per call.
 *
 * <p>Singleton invariant (exactly one {@code :DataciteMinterConfig}
 * node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code DataciteMinterConfigService.seedIfNeeded()} that
 *       creates the node when none exists.</li>
 *   <li>The {@code V33} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class DataciteMinterConfigDAO extends GenericDAO<DataciteMinterConfig> {

  @Override
  public Class<DataciteMinterConfig> getEntityType() {
    return DataciteMinterConfig.class;
  }

  /**
   * Load the single {@link DataciteMinterConfig} node, or
   * {@code null} if none has been seeded yet. Callers should treat
   * {@code null} as "feature unconfigured" — typically by seeding
   * the install default.
   */
  public DataciteMinterConfig findSingleton() {
    Collection<DataciteMinterConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
