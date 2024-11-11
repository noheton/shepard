package de.dlr.shepard.timeseries;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import java.time.Instant;

public final class TimeseriesTestDataGenerator {

  public static ExperimentalTimeseries generateTimeseries(String measurement) {
    return new ExperimentalTimeseries(measurement, "device", "location", "symbolicName", "field");
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointDouble(long timestamp, Double value) {
    return new ExperimentalTimeseriesDataPoint(timestamp, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointDouble(Double value) {
    return generateDataPointDouble(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointInteger(long timestamp, Integer value) {
    return new ExperimentalTimeseriesDataPoint(timestamp, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointInteger(Integer value) {
    return new ExperimentalTimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointString(String value) {
    return new ExperimentalTimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointString(long timestamp, String value) {
    return new ExperimentalTimeseriesDataPoint(timestamp, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointBoolean(Boolean value) {
    return new ExperimentalTimeseriesDataPoint(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesDataPoint generateDataPointBoolean(long timestamp, Boolean value) {
    return new ExperimentalTimeseriesDataPoint(timestamp, value);
  }
}
