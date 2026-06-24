package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * DT1-PHASE-0 — DAO for {@link CoordinateFrame}.
 *
 * <p>Same shape as {@link DigitalTwinSceneDAO} — see that Javadoc for
 * the deferred CHOKE-03 fresh-session note. Adds one convenience
 * finder ({@link #findByParentAppId}) used by SCENEGRAPH-REST-1's
 * tree traversal endpoint — exposed here at scaffold time so the
 * tests in this PR exercise it.
 */
@ApplicationScoped
public class CoordinateFrameDAO extends GenericDAO<CoordinateFrame> {

  @Override
  public Class<CoordinateFrame> getEntityType() {
    return CoordinateFrame.class;
  }

  /**
   * Find all frames whose {@code parentFrameAppId} equals the given
   * value. Returns an empty collection when no matches exist.
   *
   * @param parentAppId the parent frame's appId; {@code null} returns
   *                    root frames (those with no parent).
   */
  public Collection<CoordinateFrame> findByParentAppId(String parentAppId) {
    Filter filter = new Filter(
      "parentFrameAppId",
      parentAppId == null ? ComparisonOperator.IS_NULL : ComparisonOperator.EQUALS,
      parentAppId
    );
    return findMatching(filter);
  }
}
