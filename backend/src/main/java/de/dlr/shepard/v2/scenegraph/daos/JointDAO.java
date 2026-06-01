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
 * <p>Overrides {@link #createOrUpdate} and {@link #findByParentFrameAppId}
 * to refetch a live OGM session per call (CHOKE-03 / JupyterConfig pattern).
 * See {@link DigitalTwinSceneDAO} for the full rationale.
 *
 * <p>Adds a convenience finder ({@link #findByParentFrameAppId}) for
 * SCENEGRAPH-REST-1's scene-tree assembly, exercised by the scaffold tests.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
  }

  /**
   * Find all joints whose {@code parentFrameAppId} equals the given
   * value. Uses a fresh OGM session per call (CHOKE-03). Returns an
   * empty collection when no matches exist or when the session factory
   * is not yet available (fail-soft per the registries rule).
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

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findByParentFrameAppId} does — the cached
   * {@code this.session} inherited from {@code GenericDAO} can be null
   * when the bean was constructed before the {@code SessionFactory}
   * finished booting.
   *
   * <p>Mints an appId via {@link AppIdGenerator#next()} if absent, then
   * saves at depth 1.
   *
   * @throws IllegalStateException if the Neo4j session factory is not
   *         yet available — a primary write cannot be deferred.
   */
  @Override
  public Joint createOrUpdate(Joint entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
        "Neo4j session unavailable — cannot persist :Joint"
      );
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
