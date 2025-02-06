package de.dlr.shepard.data.spatialdata.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
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
@Schema(
  description = "abstract geometry filter",
  discriminatorProperty = "type",
  discriminatorMapping = {
    @DiscriminatorMapping(schema = KNearestNeighbor.class, value = "K_NEAREST_NEIGHBOR"),
    @DiscriminatorMapping(schema = BoundingSphere.class, value = "BOUNDING_SPHERE"),
    @DiscriminatorMapping(schema = OrientedBoundingBox.class, value = "ORIENTED_BOUNDING_BOX"),
    @DiscriminatorMapping(schema = AxisAlignedBoundingBox.class, value = "AXIS_ALIGNED_BOUNDING_BOX"),
  }
)
@Data
@NoArgsConstructor
public abstract class AbstractGeometryFilter {

  private GeometryFilterType type;

  public AbstractGeometryFilter(GeometryFilterType type) {
    this.type = type;
  }
}
