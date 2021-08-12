package de.dlr.shepard.influxDB;

import de.dlr.shepard.neo4Core.entities.HasId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeseries implements HasId {

	private String measurement;

	@Schema(nullable = true)
	private String device;

	@Schema(nullable = true)
	private String location;

	@Schema(nullable = true)
	private String symbolicName;

	private String field;

	@Override
	public String getUniqueId() {
		return String.join("-", measurement, device, location, symbolicName, field);
	}

}
