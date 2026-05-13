package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collection;

/**
 * N1c2 — DAO for the single-instance {@link SemanticConfig} node.
 *
 * <p>The singleton invariant is service-level — {@code OntologyConfigService}
 * is the only caller, and it routes every read through
 * {@link #findFirst()}. Multiple rows on disk would be a bug; in
 * that case the service uses the first one and logs a WARN, but
 * does not raise (defence-in-depth).
 */
@RequestScoped
public class SemanticConfigDAO extends GenericDAO<SemanticConfig> {

  /**
   * Find the singleton {@link SemanticConfig}. Returns {@code null}
   * when no row exists yet (first-start case — the service then
   * creates one seeded from the deploy-time defaults).
   */
  public SemanticConfig findFirst() {
    Collection<SemanticConfig> all = session.loadAll(SemanticConfig.class, DEPTH_ENTITY);
    if (all == null || all.isEmpty()) return null;
    return all.iterator().next();
  }

  @Override
  public Class<SemanticConfig> getEntityType() {
    return SemanticConfig.class;
  }
}
