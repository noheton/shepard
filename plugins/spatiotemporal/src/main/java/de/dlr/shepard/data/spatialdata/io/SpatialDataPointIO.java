package de.dlr.shepard.data.spatialdata.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Schema(name = "SpatialDataPoint")
public class SpatialDataPointIO {

  @Schema(
    description = "Time in nanoseconds since unix epoch. If no value is provided, the current server time is used."
  )
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
    Map<String, Object> metadata,
    @NotEmpty Map<String, Object> measurements
  ) {
    this.timestamp = timestamp == null ? Instant.now().toEpochMilli() * 1_000_000 : timestamp;
    this.x = x;
    this.y = y;
    this.z = z;
    this.metadata = metadata;
    this.measurements = measurements;
  }
}
