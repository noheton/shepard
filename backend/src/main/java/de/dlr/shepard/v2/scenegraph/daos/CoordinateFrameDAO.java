package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — DAO for {@link CoordinateFrame}.
 *
 * <p>Overrides {@link #createOrUpdate} and {@link #findByParentAppId} to
 * refetch a live OGM session per call. {@code GenericDAO}'s
 * {@code findMatching} uses the cached {@code this.session} field, which
 * can be null due to the CHOKE-03 startup race (see {@link DigitalTwinSceneDAO}
 * for the full rationale). Since {@code findByParentAppId} delegates to
 * {@code findMatching}, it must also fetch a live session directly.
 */
@ApplicationScoped
public class CoordinateFrameDAO extends GenericDAO<CoordinateFrame> {

  @Override
  public Class<CoordinateFrame> getEntityType() {
    return CoordinateFrame.class;
  }

  /**
   * Persist (create or update) a {@link CoordinateFrame}, refetching a
   * live OGM session on every call to survive the CHOKE-03 startup race.
   *
   * @param entity the frame to persist; must not be {@code null}
   * @return the same entity instance (appId minted if it was null on entry)
   * @throws IllegalStateException if the Neo4j session is unavailable
   */
  @Override
  public CoordinateFrame createOrUpdate(CoordinateFrame entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :CoordinateFrame");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }

  /**
   * Find all frames whose {@code parentFrameAppId} equals the given
   * value. Returns an empty collection when no matches exist.
   *
   * <p>Uses a live session from {@code NeoConnector} rather than the
   * inherited cached {@code this.session} (see CHOKE-03 note on
   * {@link DigitalTwinSceneDAO}).
   *
   * @param parentAppId the parent frame's appId; {@code null} returns
   *                    root frames (those with no parent).
   */
  public Collection<CoordinateFrame> findByParentAppId(String parentAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return java.util.Collections.emptyList();
    }
    Filter filter = new Filter(
      "parentFrameAppId",
      parentAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentAppId
    );
    return live.loadAll(CoordinateFrame.class, filter, DEPTH_ENTITY);
  }
}
