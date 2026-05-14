package de.dlr.shepard.plugins.references.dbpediadatabus.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.references.dbpediadatabus.entities.DbpediaDatabusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * REF1c — DAO for the singleton {@link DbpediaDatabusConfig} node.
 * Same shape as {@code UnhideConfigDAO} (UH1a). The singleton
 * invariant is held by:
 *
 * <ol>
 *   <li>The startup hook in {@code DbpediaDatabusConfigService}.</li>
 *   <li>The V36 migration's {@code REQUIRE n.appId IS UNIQUE}.</li>
 * </ol>
 */
@ApplicationScoped
public class DbpediaDatabusConfigDAO extends GenericDAO<DbpediaDatabusConfig> {

  @Override
  public Class<DbpediaDatabusConfig> getEntityType() {
    return DbpediaDatabusConfig.class;
  }

  public DbpediaDatabusConfig findSingleton() {
    Collection<DbpediaDatabusConfig> all = findAll();
    if (all.isEmpty()) return null;
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
