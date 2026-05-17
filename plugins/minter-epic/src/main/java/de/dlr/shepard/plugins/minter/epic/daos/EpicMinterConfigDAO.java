package de.dlr.shepard.plugins.minter.epic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * KIP1c — DAO for the singleton {@link EpicMinterConfig} node.
 *
 * <p>Mirrors the UH1a / KIP1d pattern. {@code @ApplicationScoped}
 * because the config is read on every mint and the OGM session is
 * short-lived per call.
 *
 * <p>Singleton invariant (exactly one {@code :EpicMinterConfig}
 * node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code EpicMinterConfigService.seedIfNeeded()} that
 *       creates the node when none exists.</li>
 *   <li>The {@code V45} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class EpicMinterConfigDAO extends GenericDAO<EpicMinterConfig> {

  @Override
  public Class<EpicMinterConfig> getEntityType() {
    return EpicMinterConfig.class;
  }

  /**
   * Load the single {@link EpicMinterConfig} node, or
   * {@code null} if none has been seeded yet. Callers should treat
   * {@code null} as "feature unconfigured" — typically by seeding
   * the install default.
   */
  public EpicMinterConfig findSingleton() {
    Collection<EpicMinterConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
