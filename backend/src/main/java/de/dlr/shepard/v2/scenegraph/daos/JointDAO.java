package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 — DAO for {@link Joint}.
 *
 * <p>Overrides {@code createOrUpdate} to acquire a fresh OGM session per
 * call — mirrors the {@code JupyterConfigDAO} / {@code SqlTimeseriesConfigDAO}
 * CHOKE-03 fix (DT1-DAO-FRESH-SESSION). Adds a convenience finder
 * ({@link #findByParentFrameAppId}) for SCENEGRAPH-REST-1's scene-tree
 * assembly.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session per call —
   * mirrors {@code JupyterConfigDAO} (DT1-DAO-FRESH-SESSION fix). The cached
   * {@code this.session} inherited from {@code GenericDAO} can be {@code null}
   * when the bean is constructed before the {@code SessionFactory} finishes
   * booting.
   *
   * <p>Null-guards the entity and mints an {@code appId} (UUID v7) via
   * {@link AppIdGenerator#next()} when absent before persisting.
   */
  @Override
  public Joint createOrUpdate(Joint entity) {
    if (entity == null) return null;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :Joint");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }

  /**
   * Find all joints whose {@code parentFrameAppId} equals the given
   * value.
   */
  public Collection<Joint> findByParentFrameAppId(String parentFrameAppId) {
    Filter filter = new Filter(
      "parentFrameAppId",
      parentFrameAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentFrameAppId
    );
    return findMatching(filter);
  }
}
