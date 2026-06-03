package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — DAO for {@link Joint}.
 *
 * <p>Overrides {@code createOrUpdate} and {@code findAll} to use a live
 * OGM session fetched per call (CHOKE-03 fix — mirrors
 * {@code DigitalTwinSceneDAO} and the canonical {@code JupyterConfigDAO}
 * pattern). Also overrides {@link #findByParentFrameAppId} to avoid the
 * inherited {@code findMatching} path which reads from the cached
 * {@code session} field.
 *
 * <p>See {@link DigitalTwinSceneDAO} Javadoc for the full CHOKE-03
 * rationale.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
  }

  /**
   * Persist {@code entity} using a fresh OGM session fetched per call.
   *
   * <p>Mints an appId when absent (the L2a write seam), then saves at
   * depth 1. Throws {@link IllegalStateException} if the Neo4j session
   * factory has not yet booted.
   */
  @Override
  public Joint createOrUpdate(Joint entity) {
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
   * Load all {@link Joint} nodes using a live session.
   *
   * <p>Returns an empty collection when the session factory is not
   * yet available (fail-soft on reads).
   */
  @Override
  public Collection<Joint> findAll() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return Collections.emptyList();
    }
    return live.loadAll(Joint.class, DEPTH_ENTITY);
  }

  /**
   * Find all joints whose {@code parentFrameAppId} equals the given
   * value. Returns an empty collection when the session factory is not
   * yet available.
   */
  public Collection<Joint> findByParentFrameAppId(String parentFrameAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return Collections.emptyList();
    }
    Filter filter = new Filter(
      "parentFrameAppId",
      parentFrameAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentFrameAppId
    );
    return live.loadAll(Joint.class, filter, DEPTH_ENTITY);
  }
}
