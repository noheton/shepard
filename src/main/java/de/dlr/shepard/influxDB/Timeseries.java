package de.dlr.shepard.influxDB;

import de.dlr.shepard.util.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeseries implements HasId {

	@NotBlank
	private String measurement;

	@NotBlank
	@Schema(nullable = true)
	private String device;

	@NotBlank
	@Schema(nullable = true)
	private String location;

	@NotBlank
	@Schema(nullable = true)
	private String symbolicName;

	@NotBlank
	@Schema(nullable = true)
	private String field;

	@Override
	public String getUniqueId() {
		return String.join("-", measurement, device, location, symbolicName, field);
	}

}
