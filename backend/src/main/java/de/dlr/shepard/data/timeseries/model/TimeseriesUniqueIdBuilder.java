package de.dlr.shepard.data.timeseries.model;

public final class TimeseriesUniqueIdBuilder {

  public static String buildUniqueId(
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
