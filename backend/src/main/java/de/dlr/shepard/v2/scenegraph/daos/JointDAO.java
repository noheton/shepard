package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * DT1-PHASE-0 — DAO for {@link Joint}.
 *
 * <p>Same shape as {@link DigitalTwinSceneDAO}. Adds a convenience
 * finder ({@link #findByParentFrameAppId}) for SCENEGRAPH-REST-1's
 * scene-tree assembly, exercised by the scaffold tests.
 */
@ApplicationScoped
public class JointDAO extends GenericDAO<Joint> {

  @Override
  public Class<Joint> getEntityType() {
    return Joint.class;
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
