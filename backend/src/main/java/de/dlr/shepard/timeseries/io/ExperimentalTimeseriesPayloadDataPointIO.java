package de.dlr.shepard.timeseries.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Representation of a timeseries payload data point data object, containing an unix-timestamp in
 * nanoseconds and the actual point value.
 */
@Data
@AllArgsConstructor
public class ExperimentalTimeseriesPayloadDataPointIO {

  @JsonProperty("timestamp")
  @Schema(description = "Time in nanoseconds since epoch")
  private long timestamp;

  @Schema(description = "A string, a number or a boolean")
  private Object value;
}
