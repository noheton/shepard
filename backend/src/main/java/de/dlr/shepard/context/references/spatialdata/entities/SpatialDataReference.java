package de.dlr.shepard.context.references.spatialdata.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.spatialdata.endpoints.SpatialDataParamParser;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.Collections;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class SpatialDataReference extends BasicReference {

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public SpatialDataReference(long id) {
    super(id);
  }

  private String geometryFilter;
  private String measurementsFilter;
  private Long startTime;
  private Long endTime;
  private String metadata;
  private Integer limit;
  private Integer skip;

  @ToString.Exclude
  @Relationship(type = Constants.IS_IN_CONTAINER)
  private SpatialDataContainer spatialDataContainer;

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result =
      prime * result + Objects.hash(geometryFilter, measurementsFilter, startTime, endTime, metadata, limit, skip);
    result = prime * result + HasId.hashcodeHelper(spatialDataContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof SpatialDataReference)) return false;
    SpatialDataReference other = (SpatialDataReference) obj;
    return (
      Objects.equals(geometryFilter, other.geometryFilter) &&
      Objects.equals(measurementsFilter, other.measurementsFilter) &&
      startTime == other.startTime &&
      endTime == other.endTime &&
      Objects.equals(metadata, other.metadata) &&
      Objects.equals(limit, other.limit) &&
      Objects.equals(skip, other.skip) &&
      HasId.equalsHelper(spatialDataContainer, other.spatialDataContainer)
    );
  }

  public SpatialDataQueryParams toSpatialDataQueryParams() {
    return new SpatialDataQueryParams(
      SpatialDataParamParser.parseGeometryFilter(geometryFilter).orElse(null),
      SpatialDataParamParser.parseMetadata(metadata).orElse(Collections.emptyMap()),
      SpatialDataParamParser.parseMeasurementsFilter(measurementsFilter).orElse(Collections.emptyList()),
      startTime,
      endTime,
      limit,
      skip
    );
  }
}
