package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;

public class PayloadWriteTask {

  private static int taskIdCounter = 0;

  public static final PayloadWriteTask poisonPill = new PayloadWriteTask(null, null, null, null, true);

  InfluxTimeseriesPayload payload;
  InfluxTimeseriesDataType dataType;
  InfluxTimeseries influxTimeseries;
  TimeseriesContainer container;
  int taskId;
  boolean isLastTask;

  public PayloadWriteTask(
    InfluxTimeseriesPayload payload,
    InfluxTimeseriesDataType dataType,
    InfluxTimeseries influxTimeseries,
    TimeseriesContainer container,
    boolean isLastTask
  ) {
    this.payload = payload;
    this.dataType = dataType;
    this.influxTimeseries = influxTimeseries;
    this.container = container;
    this.isLastTask = isLastTask;
    taskId = taskIdCounter++;
  }
}
