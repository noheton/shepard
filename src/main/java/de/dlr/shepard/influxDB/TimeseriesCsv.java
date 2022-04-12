package de.dlr.shepard.influxDB;

import com.opencsv.bean.CsvBindByName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeseriesCsv {

	@CsvBindByName
	private long timestamp;

	@CsvBindByName
	private String measurement;

	@CsvBindByName
	private String device;

	@CsvBindByName
	private String location;

	@CsvBindByName
	private String symbolicName;

	@CsvBindByName
	private String field;

	@CsvBindByName
	private Object value;
}
