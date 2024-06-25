package de.dlr.shepard.influxDB;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representation of an influx data object, containing an unix-timestamp in
 * nanoseconds and the actual influx value.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InfluxPoint {

	@JsonProperty("timestamp")
	@Schema(description = "Time in nanoseconds since epoch")
	private long timeInNanoseconds;

	// https://github.com/OpenAPITools/openapi-generator/issues/12556
	// https://github.com/swagger-api/swagger-core/issues/3834
	// https://github.com/swagger-api/swagger-core/issues/4014
	// https://github.com/swagger-api/swagger-core/issues/4457
	@Schema(description = "A string, a number or a boolean") // , oneOf = { String.class, Number.class, Boolean.class })
	private Object value;
}
