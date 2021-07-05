package de.dlr.shepard.influxDB;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.dlr.shepard.neo4Core.entities.HasId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeseries implements HasId {

	private String measurement;

	private String device;

	private String location;

	private String symbolicName;

	private String field;

	@Override
	@JsonIgnore
	public String getUniqueId() {
		return String.join("-", measurement, device, location, symbolicName, field);
	}

}
