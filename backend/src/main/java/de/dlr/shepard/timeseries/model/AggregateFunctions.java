package de.dlr.shepard.timeseries.model;

public enum AggregateFunctions {
  MEAN,
  MEDIAN,
  COUNT,
  SUM,
  MIN,
  MAX,
  LAST,
  FIRST,
  //  INTEGRAL, TODO: postbone implementation for now until since this function requires the timescaledb toolkit
  MODE,
  SPREAD,
  STDDEV,
}
