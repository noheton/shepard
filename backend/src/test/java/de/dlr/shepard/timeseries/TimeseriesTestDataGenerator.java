package de.dlr.shepard.timeseries;

import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import java.time.Instant;

public final class TimeseriesTestDataGenerator {

  public static Timeseries generateTimeseries(String measurement) {
    return new Timeseries(measurement, "device", "location", "symbolicName", "field");
  }

  public static TimeseriesDataPoint generateDataPointDouble(long timestamp, Double value) {
    return new TimeseriesDataPoint(timestamp, value);
  }

  public static TimeseriesDataPoint generateDataPointDouble(Double value) {
    return generateDataPointDouble(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointInteger(long timestamp, Integer value) {
    return new TimeseriesDataPoint(timestamp, value);
  }

  public static TimeseriesDataPoint generateDataPointInteger(Integer value) {
    return new TimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointString(String value) {
    return new TimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointString(long timestamp, String value) {
    return new TimeseriesDataPoint(timestamp, value);
  }

  public static TimeseriesDataPoint generateDataPointBoolean(Boolean value) {
    return new TimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointBoolean(long timestamp, Boolean value) {
    return new TimeseriesDataPoint(timestamp, value);
  }
}
