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
 * DT1-DAO-FRESH-SESSION — DAO for {@link Joint}.
 *
 * <p>Overrides {@link #createOrUpdate} and {@link #findByParentFrameAppId}
 * to refetch a live OGM session per call. {@code GenericDAO}'s
 * {@code findMatching} uses the cached {@code this.session} field, which
 * can be null due to the CHOKE-03 startup race (see {@link DigitalTwinSceneDAO}
 * for the full rationale). Since {@code findByParentFrameAppId} delegates
 * to {@code findMatching}, it must also fetch a live session directly.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
  }

  /**
   * Persist (create or update) a {@link Joint}, refetching a live OGM
   * session on every call to survive the CHOKE-03 startup race.
   *
   * @param entity the joint to persist; must not be {@code null}
   * @return the same entity instance (appId minted if it was null on entry)
   * @throws IllegalStateException if the Neo4j session is unavailable
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
   * Find all joints whose {@code parentFrameAppId} equals the given value.
   *
   * <p>Uses a live session from {@code NeoConnector} rather than the
   * inherited cached {@code this.session} (see CHOKE-03 note on
   * {@link DigitalTwinSceneDAO}).
   *
   * @param parentFrameAppId the parent frame's appId; {@code null} returns
   *                         joints with no declared parent frame.
   */
  public Collection<Joint> findByParentFrameAppId(String parentFrameAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return java.util.Collections.emptyList();
    }
    Filter filter = new Filter(
      "parentFrameAppId",
      parentFrameAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentFrameAppId
    );
    return live.loadAll(Joint.class, filter, DEPTH_ENTITY);
  }
}
