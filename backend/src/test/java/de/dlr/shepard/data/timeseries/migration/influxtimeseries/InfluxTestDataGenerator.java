package de.dlr.shepard.data.timeseries.migration.influxtimeseries;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InfluxTestDataGenerator {

  private InfluxTimeseries timeseries = new InfluxTimeseries(
    "measurement",
    "device",
    "location",
    "symbolicName",
    "field"
  );
  private List<InfluxPoint> points = new ArrayList<>();

  public InfluxTestDataGenerator setTimeseries(String measurement) {
    this.timeseries.setMeasurement(measurement);
    return this;
  }

  public InfluxTestDataGenerator addInfluxPoint(Object value) {
    this.addInfluxPoint(Instant.now().toEpochMilli() * 1_000_000, value);
    return this;
  }

  public InfluxTestDataGenerator addInfluxPoint(long timestamp, Object value) {
    this.points.add(new InfluxPoint(timestamp, value));
    return this;
  }

  public InfluxTimeseriesPayload buildPayload() {
    return new InfluxTimeseriesPayload(this.timeseries, this.points);
  }
}
