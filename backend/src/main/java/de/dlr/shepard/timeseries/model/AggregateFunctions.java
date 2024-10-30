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
  INTEGRAL, //TODO: this function needs to be properly implemented
  MODE,
  SPREAD,
  STDDEV,
}
