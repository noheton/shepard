package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.concurrent.Callable;
import org.influxdb.InfluxDBException;

@RequestScoped
class PayloadReader implements Callable<Object> {

  @Inject
  PayloadReader(TimeseriesMigrationService migrationService, InfluxTimeseriesService influxTimeseriesService) {
    this.migrationService = migrationService;
    this.influxTimeseriesService = influxTimeseriesService;
  }

  private TimeseriesMigrationService migrationService;

  private InfluxTimeseriesService influxTimeseriesService;

  @Override
  public Object call() {
    try {
      while (true) {
        PayloadReadTask payloadReadTask = migrationService.getPayloadReadQueue().poll();
        if (payloadReadTask.isLastTask) break;
        Log.infof(
          "started PayloadReadTask: %s, from %s to %s",
          payloadReadTask.taskId,
          payloadReadTask.startTimestamp,
          payloadReadTask.endTimestamp
        );
        var payload =
          this.influxTimeseriesService.getTimeseriesPayload(
              payloadReadTask.startTimestamp,
              payloadReadTask.endTimestamp,
              payloadReadTask.databaseName,
              payloadReadTask.influxTimeseries,
              null,
              null,
              null
            );

        PayloadWriteTask payloadTask = new PayloadWriteTask(
          payload,
          payloadReadTask.influxTimeseriesDataType,
          payloadReadTask.influxTimeseries,
          payloadReadTask.container,
          false
        );
        migrationService.getPayloadWriteQueue().put(payloadTask);
      }
      // Notify
      PayloadWriteTask payloadWriteTaskPoisonPill = new PayloadWriteTask(null, null, null, null, true);
      for (int i = 0; i < migrationService.numberOfSaverThreads; i++) {
        migrationService.getPayloadWriteQueue().put(payloadWriteTaskPoisonPill);
      }
    } catch (InterruptedException | InfluxDBException e) {
      // Cancel the task
      Log.errorf("Error while loading payloads from influx.", e.getMessage());
      Thread.currentThread().interrupt();
    }
    return "PayloadReader Done!";
  }
}
