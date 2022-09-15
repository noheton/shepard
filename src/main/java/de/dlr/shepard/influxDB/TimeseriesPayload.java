package de.dlr.shepard.influxDB;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeseriesPayload {

	@NotNull
	private Timeseries timeseries;

	@NotEmpty
	private List<InfluxPoint> points = new ArrayList<>();
}
