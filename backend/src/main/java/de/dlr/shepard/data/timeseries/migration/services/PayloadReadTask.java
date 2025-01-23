package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;

class PayloadReadTask {

  private static int taskIdCounter = 0;

  public static final PayloadReadTask poisonPill = new PayloadReadTask(
    taskIdCounter,
    taskIdCounter,
    null,
    null,
    null,
    null,
    true
  );

  long startTimestamp;
  long endTimestamp;
  InfluxTimeseries influxTimeseries;
  TimeseriesContainer container;
  String databaseName;
  InfluxTimeseriesDataType influxTimeseriesDataType;
  int taskId;
  boolean isLastTask = false;

  public PayloadReadTask(
    long startTimestamp,
    long endTimestamp,
    InfluxTimeseries influxTimeseries,
    TimeseriesContainer container,
    String databaseName,
    InfluxTimeseriesDataType influxTimeseriesDataType,
    boolean isLastTask
  ) {
    this.startTimestamp = startTimestamp;
    this.endTimestamp = endTimestamp;
    this.influxTimeseries = influxTimeseries;
    this.container = container;
    this.databaseName = databaseName;
    this.influxTimeseriesDataType = influxTimeseriesDataType;
    this.isLastTask = isLastTask;
    taskId = taskIdCounter++;
  }
}
