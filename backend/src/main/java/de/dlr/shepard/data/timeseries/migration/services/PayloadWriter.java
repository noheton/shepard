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

@RequestScoped
class PayloadWriter implements Callable<Object> {

  private static int compressionBatchSize = 10;
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
      while (true) {
        PayloadWriteTask task = queue.take();
        if (task.isLastTask) break;
        Log.infof(
          "started PayloadWriteTask: %s of %s for container %s with %s points.",
          task.taskId,
          initialQueueSize,
          task.container.getId(),
          task.payload.getPoints().size()
        );
        saveDataPoints(task.container, task.influxTimeseries, task.dataType, task.payload);

        if (task.taskId % compressionBatchSize == 0) runCompression();
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

  private void runCompression() {
    try {
      migrationService.compressAllDataPoints();
    } catch (Exception e) {
      Log.warnf("Compression had an error but we still continue with data migration. %s", e.toString());
    }
  }
}
