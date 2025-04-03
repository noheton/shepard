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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RequestScoped
class PayloadWriter implements Callable<Object> {

  private TimeseriesMigrationService migrationService;
  private TimeseriesService timeseriesService;

  @Inject
  PayloadWriter(TimeseriesMigrationService migrationService, TimeseriesService timeseriesService) {
    this.migrationService = migrationService;
    this.timeseriesService = timeseriesService;
  }

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
          long taskStartTimeMilliseconds = task.startTimestamp / 1_000_000;
          long taskEndTimeMilliseconds = (task.endTimestamp + 1) / 1_000_000;

          LocalDate startDate = Instant.ofEpochMilli(taskStartTimeMilliseconds).atZone(ZoneOffset.UTC).toLocalDate();
          LocalDate endDate = Instant.ofEpochMilli(taskEndTimeMilliseconds).atZone(ZoneOffset.UTC).toLocalDate();

          // Add compression if the task occurs in between two days
          if (!startDate.equals(endDate)) {
            Log.infof(
              "Adding compression task for after inserting data for container %s, timestamps: %s to %s",
              task.container.getId(),
              task.startTimestamp,
              task.endTimestamp
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
    timeseriesService.saveDataPoints(
      container.getId(),
      mapToTimeseries(influxTimeseries),
      mapToTimeseriesDataPoints(payload.getPoints()),
      mapToValueType(influxTimeseriesDataType)
    );
  }
}
