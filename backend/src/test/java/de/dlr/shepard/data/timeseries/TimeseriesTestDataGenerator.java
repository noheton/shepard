package de.dlr.shepard.data.timeseries;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import java.time.Instant;
import java.util.List;

public final class TimeseriesTestDataGenerator {

  public static TimeseriesFiveTuple generateTimeseries(String measurement) {
    return new TimeseriesFiveTuple(measurement, "device", "location", "symbolicName", "field");
  }

  public static TimeseriesWithDataPoints generateTimeseriesWithDataPoints(
    String measurement,
    List<TimeseriesDataPoint> timeseriesDataPoints
  ) {
    return new TimeseriesWithDataPoints(generateTimeseries(measurement), timeseriesDataPoints);
  }

  public static TimeseriesDataPoint generateDataPointDouble(long timestamp, Double value) {
    return new TimeseriesDataPoint(timestamp, value);
  }

  public static TimeseriesDataPoint generateDataPointDouble(Double value) {
    return generateDataPointDouble(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointInteger(long timestamp, Integer value) {
    return generateDataPointInteger(timestamp, Long.valueOf(value));
  }

  public static TimeseriesDataPoint generateDataPointInteger(Integer value) {
    return generateDataPointInteger(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static TimeseriesDataPoint generateDataPointInteger(long timestamp, Long value) {
    return new TimeseriesDataPoint(timestamp, value);
  }

  public static TimeseriesDataPoint generateDataPointInteger(Long value) {
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
