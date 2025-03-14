package de.dlr.shepard.data.timeseries.migration.services;

public class CompressionTask {

  boolean isLastTask;

  public CompressionTask(boolean isLastTask) {
    this.isLastTask = isLastTask;
  }
}
