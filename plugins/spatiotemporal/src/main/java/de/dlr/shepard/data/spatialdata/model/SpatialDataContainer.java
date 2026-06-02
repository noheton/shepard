package de.dlr.shepard.data.spatialdata.model;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpatialDataContainer extends BasicContainer {

  /**
   * MFFD-SPATIAL-FRAME-HANDSHAKE — optional FK-by-convention pointing at a
   * {@code :CoordinateFrame.appId} (CST1, see {@code aidocs/data/85}).
   *
   * <p>Mirrors the {@code shepard_spatial.profile_container.coord_frame_app_id}
   * column on the PostGIS side (Flyway migration {@code V2.0.0__green_field_schema.sql}
   * in this plugin). Spatial queries against this container are only meaningful
   * when the query geometry is expressed in the same frame.
   *
   * <p>Additive nullable property — no Neo4j migration required for storage.
   * Documented by {@code V106__NOOP_SpatialDataContainer_frameAppId_additive.cypher}.
   * Pre-feature containers simply lack the property and the OGM reads the
   * absence as {@code null}.
   *
   * <p>A real {@code (:SpatialDataContainer)-[:ANCHORED_IN]->(:CoordinateFrame)}
   * edge ships with SPATIAL-V6-006; until then this is the FK-by-convention.
   */
  @Property("frameAppId")
  private String frameAppId;

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public SpatialDataContainer(long id) {
    super(id);
  }
}
