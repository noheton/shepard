package de.dlr.shepard.context.references.spatialdata.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.data.spatialdata.endpoints.SpatialDataParamParser;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "SpatialDataReference")
public class SpatialDataReferenceIO extends BasicReferenceIO {

  @NotNull
  @Schema(required = true)
  private long spatialDataContainerId;

  @NotNull
  @Schema(
    required = true,
    example = """
    {
      "type": "K_NEAREST_NEIGHBOR",
      "k": 5,
      "x": 10,
      "y": 20,
      "z": 30
      }"""
  )
  private AbstractGeometryFilter geometryFilter;

  @Schema(
    example = """
    [{ "key": "temperature,val", "operator": "EQUALS", "value": 20 }]
    """
  )
  private List<FilterCondition> measurementFilters;

  @Schema
  private long startTime;

  @Schema
  private long endTime;

  @Schema
  private Map<String, Object> metadata;

  @Schema
  private Integer limit;

  @Schema
  private Integer offset;

  @Schema
  private Integer skip;

  public SpatialDataReferenceIO(SpatialDataReference ref) {
    super(ref);
    this.geometryFilter = SpatialDataParamParser.parseGeometryFilter(ref.getGeometryFilter());
    this.measurementFilters = SpatialDataParamParser.parseMeasurementsFilter(ref.getMeasurementsFilter()).orElse(
      Collections.emptyList()
    );
    this.startTime = ref.getStartTime();
    this.endTime = ref.getEndTime();
    this.metadata = SpatialDataParamParser.parseMetadata(ref.getMetadata()).orElse(Collections.emptyMap());
    this.limit = ref.getLimit();
    this.offset = ref.getOffset();
    this.skip = ref.getSkip();
    this.spatialDataContainerId = ref.getSpatialDataContainer() != null ? ref.getSpatialDataContainer().getId() : -1;
  }
}
