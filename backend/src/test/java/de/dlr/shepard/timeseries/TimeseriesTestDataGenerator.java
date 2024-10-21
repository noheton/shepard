package de.dlr.shepard.timeseries;

import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import java.time.Instant;

public final class TimeseriesTestDataGenerator {

  public static ExperimentalTimeseries generateTimeseries(long containerId, String measurement) {
    return new ExperimentalTimeseries(containerId, "device", "field", "location", measurement, "symbolicName");
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointDouble(Double value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli(), value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointInteger(Integer value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli(), value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointString(String value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli(), value);
  }

  public static ExperimentalTimeseriesPayloadDataPointIO generateDataPointBoolean(Boolean value) {
    return new ExperimentalTimeseriesPayloadDataPointIO(Instant.now().toEpochMilli(), value);
  }
}
