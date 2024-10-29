package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class ExperimentalTimeseriesDataPoint {

  @JsonProperty("timestamp")
  @Schema(description = "Time in nanoseconds since unix epoch.")
  private long timestamp;

  @Schema(description = "Value that can be one of the supported types defined in ExperimentalDataPointValueTypes.")
  private Object value;

  public ExperimentalTimeseriesDataPoint(long timestamp, Object value) {
    this.timestamp = timestamp;
    this.value = value;
  }
}
