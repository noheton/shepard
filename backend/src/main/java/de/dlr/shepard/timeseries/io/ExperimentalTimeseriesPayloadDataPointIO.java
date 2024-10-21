package de.dlr.shepard.timeseries.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Representation of a timeseries payload data point data object, containing an unix-timestamp in
 * nanoseconds and the actual point value.
 */
@Data
public class ExperimentalTimeseriesPayloadDataPointIO {

  @JsonProperty("timestamp")
  @Schema(description = "Time in nanoseconds since epoch")
  private long timestamp;

  // https://github.com/OpenAPITools/openapi-generator/issues/12556
  // https://github.com/swagger-api/swagger-core/issues/3834
  // https://github.com/swagger-api/swagger-core/issues/4014
  // https://github.com/swagger-api/swagger-core/issues/4457
  @Schema(description = "A string, a number or a boolean") // , oneOf = { String.class, Number.class, Boolean.class })
  private Object value;
}
