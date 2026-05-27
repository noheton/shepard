package de.dlr.shepard.data.spatialdata.model.geometryFilter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  {
    @JsonSubTypes.Type(value = KNearestNeighbor.class, name = "K_NEAREST_NEIGHBOR"),
    @JsonSubTypes.Type(value = BoundingSphere.class, name = "BOUNDING_SPHERE"),
    @JsonSubTypes.Type(value = OrientedBoundingBox.class, name = "ORIENTED_BOUNDING_BOX"),
    @JsonSubTypes.Type(value = AxisAlignedBoundingBox.class, name = "AXIS_ALIGNED_BOUNDING_BOX"),
  }
)
@Schema(description = "abstract geometry filter")
@Data
@NoArgsConstructor
public abstract class AbstractGeometryFilter {

  protected GeometryFilterType type;

  @JsonIgnore
  public abstract boolean isValid();

  public AbstractGeometryFilter(GeometryFilterType type) {
    this.type = type;
  }
}
