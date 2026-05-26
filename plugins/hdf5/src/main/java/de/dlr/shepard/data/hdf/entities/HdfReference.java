package de.dlr.shepard.data.hdf.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

/**
 * A5c — per-DataObject anchor pointing to a specific HDF5 dataset path
 * within an {@link HdfContainer}.
 *
 * <p>Extends {@link BasicReference} so the OGM graph edge
 * {@code (DataObject)-[:HAS_REFERENCE]->(HdfReference)} is wired
 * automatically via the inherited {@code @Relationship(direction=INCOMING)}.
 * No extra Cypher migration is needed for the DataObject side — the
 * {@code HAS_REFERENCE} relationship type is already indexed and
 * traversed by the upstream list query ({@code DataObject.references}).
 *
 * <p>An additional outgoing edge {@code (HdfReference)-[:IS_HDF_REFERENCE_OF]->(HdfContainer)}
 * pins which container this anchor belongs to.
 *
 * <p>Immutable after creation: the dataset path and container can only
 * be set at POST time. The v2 PATCH surface intentionally does not
 * expose {@code datasetPath} or {@code hdfContainerAppId}.
 */
@NodeEntity
@Data
@NoArgsConstructor
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class HdfReference extends BasicReference implements HasAppId {

  /**
   * Application-level identifier (UUID v7). Server-minted at creation;
   * immutable afterwards.
   */
  @Property
  @ToString.Include
  private String appId;

  /**
   * HDF5 dataset path within the container — e.g.
   * {@code "/sensor_data/channel_A"} or {@code "/runs/run004/pressure"}.
   * Must be non-null and non-blank. The server stores the value verbatim;
   * no validation of the HSDS domain is performed at reference-creation
   * time (deferred to A5e data-path).
   */
  @Property
  @ToString.Include
  private String datasetPath;

  /**
   * Optional free-form description surfaced in the UI. May be null.
   */
  @Property
  private String description;

  /**
   * Outgoing edge to the container this reference belongs to.
   * The relationship type {@code IS_HDF_REFERENCE_OF} is unique to
   * HdfReference; the {@code HAS_REFERENCE} back-link is handled by
   * the inherited {@link BasicReference} field.
   */
  @Relationship(type = "IS_HDF_REFERENCE_OF")
  private HdfContainer hdfContainer;

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public HdfReference(long id) {
    super(id);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (appId == null ? 0 : appId.hashCode());
    result = prime * result + (datasetPath == null ? 0 : datasetPath.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof HdfReference)) return false;
    HdfReference other = (HdfReference) obj;
    return java.util.Objects.equals(appId, other.appId) &&
           java.util.Objects.equals(datasetPath, other.datasetPath);
  }
}
