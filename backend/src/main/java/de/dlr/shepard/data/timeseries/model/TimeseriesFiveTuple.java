package de.dlr.shepard.data.timeseries.model;

public record TimeseriesFiveTuple(
  String measurement,
  String device,
  String location,
  String symbolicName,
  String field
) {
  public TimeseriesFiveTuple(Timeseries ts) {
    this(ts.getMeasurement(), ts.getDevice(), ts.getLocation(), ts.getSymbolicName(), ts.getField());
  }
}
