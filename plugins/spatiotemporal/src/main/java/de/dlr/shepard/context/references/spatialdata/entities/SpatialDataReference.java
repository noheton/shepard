package de.dlr.shepard.context.references.spatialdata.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.spatialdata.endpoints.SpatialDataParamParser;
import de.dlr.shepard.data.spatialdata.io.SpatialDataQueryParams;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.Collections;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
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
