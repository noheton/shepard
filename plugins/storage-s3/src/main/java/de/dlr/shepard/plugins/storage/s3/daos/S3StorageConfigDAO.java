package de.dlr.shepard.plugins.storage.s3.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * FS1b — DAO for the singleton {@link S3StorageConfig} node.
 *
 * <p>Mirrors KIP1d's {@code DataciteMinterConfigDAO} (and through it
 * UH1a / A3b / N1c2). {@code @ApplicationScoped} because the config
 * is read on every put/get and the OGM session is short-lived per
 * call.
 *
 * <p>Singleton invariant (exactly one {@code :S3StorageConfig}
 * node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code S3StorageConfigService.seedIfNeeded()} that creates
 *       the node when none exists.</li>
 *   <li>The {@code V36} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 */
@ApplicationScoped
public class S3StorageConfigDAO extends GenericDAO<S3StorageConfig> {

  @Override
  public Class<S3StorageConfig> getEntityType() {
    return S3StorageConfig.class;
  }

  /**
   * Load the single {@link S3StorageConfig} node, or {@code null}
   * if none has been seeded yet. Callers should treat {@code null}
   * as "feature unconfigured" — typically by seeding the install
   * default.
   */
  public S3StorageConfig findSingleton() {
    Collection<S3StorageConfig> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
