package de.dlr.shepard.data.spatialdata.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "SpatialDataPoint")
public class SpatialDataPointIO {

  @Schema(description = "Time in nanoseconds since unix epoch.")
  private Long timestamp;

  @NotNull
  @Schema(description = "X Coordinate", required = true)
  private Double x;

  @NotNull
  @Schema(description = "Y Coordinate", required = true)
  private Double y;

  @NotNull
  @Schema(description = "Z Coordinate", required = true)
  private Double z;

  @NotEmpty
  @Schema(description = "measurement", required = true)
  private Map<String, Object> measurements;

  @Schema(description = "Data point meta data", required = true)
  private Map<String, Object> metadata;

  public SpatialDataPointIO(
    Long timestamp,
    @NotNull Double x,
    @NotNull Double y,
    @NotNull Double z,
    @NotEmpty Map<String, Object> measurements,
    Map<String, Object> metadata
  ) {
    this.timestamp = timestamp;
    this.x = x;
    this.y = y;
    this.z = z;
    this.measurements = measurements;
    this.metadata = metadata;
  }
}
