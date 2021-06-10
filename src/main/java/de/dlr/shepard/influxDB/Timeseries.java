package de.dlr.shepard.influxDB;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Timeseries {

	private String measurement;

	private String device;

	private String location;

	private String symbolicName;

	private String field;

}
