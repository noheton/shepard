package de.dlr.shepard.influxDB;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeseriesPayload {

	private Timeseries timeseries;

	private List<InfluxPoint> points = new ArrayList<InfluxPoint>();
}
