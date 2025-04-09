package de.dlr.shepard.data.timeseries.migration.services;

import static de.dlr.shepard.common.util.InfluxDataMapper.mapToTimeseries;
import static de.dlr.shepard.common.util.InfluxDataMapper.mapToTimeseriesDataPoints;
import static de.dlr.shepard.common.util.InfluxDataMapper.mapToValueType;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RequestScoped
class PayloadWriter implements Callable<Object> {

  @Inject
  TimeseriesMigrationService migrationService;

  @Inject
  TimeseriesService timeseriesService;

  @Override
  public Object call() {
    try {
      var initialQueueSize = migrationService.getPayloadReadQueueSize();
      BlockingQueue<PayloadWriteTask> queue = migrationService.getPayloadWriteQueue();
      ReentrantReadWriteLock lock = migrationService.getReadWriteLock();
      while (true) {
        PayloadWriteTask task = queue.take();
        if (task.isLastTask) break;
        try {
          lock.readLock().lock();
          Log.infof(
            "started PayloadWriteTask: %s of %s for container %s with %s points.",
            task.runningNumber,
            initialQueueSize,
            task.container.getId(),
            task.payload.getPoints().size()
          );

          saveDataPoints(task.container, task.influxTimeseries, task.dataType, task.payload);

          migrationService.getInsertionCount().getAndAdd(task.payload.getPoints().size());

          int oldval = migrationService
            .getInsertionCount()
            .getAndUpdate(x -> x > migrationService.getNumberOfPointsBeforeCompression() ? 0 : x);

          Log.debugf(
            "Current write counter: %s, max number: %s",
            oldval,
            migrationService.getNumberOfPointsBeforeCompression()
          );

          // Add compression if the task occurs in between two days
          if (oldval > migrationService.getNumberOfPointsBeforeCompression()) {
            Log.infof(
              "Adding compression task for after inserting data for container %s, number of points written: %s",
              task.container.getId(),
              oldval
            );
            migrationService.addCompressionTask();
          }
        } finally {
          lock.readLock().unlock();
        }
      }
    } catch (InterruptedException e) {
      Log.error(e);
      Thread.currentThread().interrupt();
    }
    return "PayloadWriter Done!";
  }

  protected void saveDataPoints(
    TimeseriesContainer container,
    InfluxTimeseries influxTimeseries,
    InfluxTimeseriesDataType influxTimeseriesDataType,
    InfluxTimeseriesPayload payload
  ) {
    timeseriesService.saveDataPointsNoChecks(
      container.getId(),
      mapToTimeseries(influxTimeseries),
      mapToTimeseriesDataPoints(payload.getPoints()),
      mapToValueType(influxTimeseriesDataType)
    );
  }
}
