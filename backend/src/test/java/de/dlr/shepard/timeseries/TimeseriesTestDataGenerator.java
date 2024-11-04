package de.dlr.shepard.timeseries;

import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import java.time.Instant;

public final class TimeseriesTestDataGenerator {

  public static ExperimentalTimeseries generateTimeseries(String measurement) {
    return new ExperimentalTimeseries(measurement, "field", "device", "location", "symbolicName");
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointDouble(long timestamp, Double value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(timestamp, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointDouble(Double value) {
    return generateDataPointDouble(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointInteger(long timestamp, Integer value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(timestamp, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointInteger(Integer value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointString(String value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointString(long timestamp, String value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(timestamp, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointBoolean(Boolean value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli() * 1_000_000, value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointBoolean(long timestamp, Boolean value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(timestamp, value);
  }
}
