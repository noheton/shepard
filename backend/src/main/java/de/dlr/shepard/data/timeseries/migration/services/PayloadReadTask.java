package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;

class PayloadReadTask {

  public static final PayloadReadTask poisonPill = new PayloadReadTask(0, 0, 0, null, null, null, null, true);

  long startTimestamp;
  long endTimestamp;
  InfluxTimeseries influxTimeseries;
  TimeseriesContainer container;
  String databaseName;
  InfluxTimeseriesDataType influxTimeseriesDataType;
  int runningNumber; // running number starting with 1 for each container
  boolean isLastTask = false;

  public PayloadReadTask(
    int runningNumber,
    long startTimestamp,
    long endTimestamp,
    InfluxTimeseries influxTimeseries,
    TimeseriesContainer container,
    String databaseName,
    InfluxTimeseriesDataType influxTimeseriesDataType,
    boolean isLastTask
  ) {
    this.runningNumber = runningNumber;
    this.startTimestamp = startTimestamp;
    this.endTimestamp = endTimestamp;
    this.influxTimeseries = influxTimeseries;
    this.container = container;
    this.databaseName = databaseName;
    this.influxTimeseriesDataType = influxTimeseriesDataType;
    this.isLastTask = isLastTask;
  }
}
