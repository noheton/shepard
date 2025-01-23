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
import jakarta.transaction.Transactional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

@RequestScoped
class PayloadWriter implements Callable<Object> {

  @Inject
  PayloadWriter(TimeseriesMigrationService migrationService, TimeseriesService timeseriesService) {
    this.migrationService = migrationService;
    this.timeseriesService = timeseriesService;
  }

  private TimeseriesMigrationService migrationService;

  private TimeseriesService timeseriesService;

  @Override
  public Object call() {
    try {
      BlockingQueue<PayloadWriteTask> queue = migrationService.getPayloadWriteQueue();
      while (true) {
        PayloadWriteTask task = queue.take();
        if (task.isLastTask) break;
        Log.debugf(
          "started PayloadWriteTask: %s for container %s, of %s points",
          task.taskId,
          task.container.getId(),
          task.payload.getPoints().size() + ""
        );
        saveDataPoints(task.container, task.influxTimeseries, task.dataType, task.payload);
      }
    } catch (InterruptedException e) {
      Log.error(e);
      Thread.currentThread().interrupt();
    }
    return "PayloadWriter Done!";
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
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
