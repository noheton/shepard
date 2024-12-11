package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class TimeseriesDataPoint {

  @JsonProperty("timestamp")
  @Schema(description = "Time in nanoseconds since unix epoch.")
  private long timestamp;

  @Schema(description = "A string, an int, a double or a boolean")
  private Object value;

  public TimeseriesDataPoint(long timestamp, Object value) {
    this.timestamp = timestamp;
    this.value = value;
  }
}
