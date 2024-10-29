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
  // INTEGRAL, TODO: align implementation of this
  MODE,
  SPREAD,
  STDDEV,
}
