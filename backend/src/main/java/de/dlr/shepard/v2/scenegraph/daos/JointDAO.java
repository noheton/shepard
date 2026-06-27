package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — DAO for {@link Joint}.
 *
 * <p>Same fresh-session contract as {@link DigitalTwinSceneDAO} — see that
 * Javadoc for the CHOKE-03 / JupyterConfig rationale. Adds a convenience
 * finder ({@link #findByParentFrameAppId}) for SCENEGRAPH-REST-1's scene-tree
 * assembly; also refetches the session per call.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session per call
   * (CHOKE-03 / JupyterConfig pattern). Mints an {@code appId} when absent.
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
    live.save(entity, DEPTH_ENTITY);
    return entity;
  }

  /**
   * Find all joints whose {@code parentFrameAppId} equals the given value.
   *
   * <p>Refetches the OGM session per call (CHOKE-03 / JupyterConfig pattern).
   */
  public Collection<Joint> findByParentFrameAppId(String parentFrameAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return List.of();
    Filter filter = new Filter(
      "parentFrameAppId",
      parentFrameAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentFrameAppId
    );
    return live.loadAll(Joint.class, filter, DEPTH_ENTITY);
  }
}
