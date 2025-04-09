package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.concurrent.Callable;
import org.influxdb.InfluxDBException;

@RequestScoped
class PayloadReader implements Callable<Object> {

  @Inject
  TimeseriesMigrationService migrationService;

  @Inject
  InfluxTimeseriesService influxTimeseriesService;

  @Override
  public Object call() {
    boolean overallLastTask = false;
    try {
      while (true) {
        var queue = migrationService.getPayloadReadQueue();
        PayloadReadTask payloadReadTask;
        synchronized (queue) { // Taking the element and looking up if there is no further element must be done atomically
          payloadReadTask = queue.poll();
          overallLastTask = queue.isEmpty();
          if (payloadReadTask.isLastTask) {
            Log.infof("ReaderThread was poisoned, remaining queue: %s, last: %s", queue.size(), overallLastTask);
            break;
          }
        }

        Log.infof(
          "started PayloadReadTask: %s, from %s to %s",
          payloadReadTask.runningNumber,
          payloadReadTask.startTimestamp,
          payloadReadTask.endTimestamp
        );
        InfluxTimeseriesPayload payload =
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
          payloadReadTask.runningNumber,
          payloadReadTask.startTimestamp,
          payloadReadTask.endTimestamp,
          payload,
          payloadReadTask.influxTimeseriesDataType,
          payloadReadTask.influxTimeseries,
          payloadReadTask.container,
          false
        );
        migrationService.getPayloadWriteQueue().put(payloadTask);
      }
    } catch (InterruptedException e) {
      // Cancel the task
      Thread.currentThread().interrupt();
    } catch (InfluxDBException e) {
      throw e;
    } finally {
      // To ensure adding them once!
      if (overallLastTask) migrationService.addWriterPoisonPills();
      migrationService.addCompressionPoisonPills();
    }
    return "PayloadReader Done!";
  }
}
