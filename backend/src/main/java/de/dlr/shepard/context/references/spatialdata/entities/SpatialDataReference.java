package de.dlr.shepard.context.references.spatialdata.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  private String geometryFilter;
  private String measurementsFilter;
  private long startTime;
  private long endTime;
  private Map<String, Object> metadata;
  private Integer limit;
  private Integer offset;
  private Integer skip;

  @Relationship(type = Constants.HAS_PAYLOAD)
  private List<SpatialDataPointIO> spatialDataPointsList = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Constants.IS_IN_CONTAINER)
  private SpatialDataContainer spatialDataContainer;
}
