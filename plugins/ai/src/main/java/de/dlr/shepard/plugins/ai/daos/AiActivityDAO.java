package de.dlr.shepard.plugins.ai.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.ai.entities.AiActivity;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * AI1 — DAO for {@link AiActivity} provenance nodes.
 *
 * <p>{@code @ApplicationScoped}. Activity nodes are append-only —
 * writes happen through {@link #save(AiActivity)} via the inherited
 * {@code GenericDAO.createOrUpdate()}; no reads or deletes are needed
 * from this DAO (provenance queries use Cypher directly).
 */
@ApplicationScoped
public class AiActivityDAO extends GenericDAO<AiActivity> {

  @Override
  public Class<AiActivity> getEntityType() {
    return AiActivity.class;
  }

  /**
   * Persist a new {@link AiActivity} provenance node. The
   * {@code GenericDAO.createOrUpdate()} call mints the {@code appId}
   * automatically (via {@code AppIdGenerator.next()}) when the entity
   * has none yet.
   *
   * @param activity the activity to persist
   * @return the saved entity with a populated {@code appId}
   */
  public AiActivity save(AiActivity activity) {
    return createOrUpdate(activity);
  }
}
