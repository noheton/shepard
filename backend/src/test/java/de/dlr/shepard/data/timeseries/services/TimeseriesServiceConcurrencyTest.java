package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TimeseriesServiceConcurrencyTest {

  @Inject
  ManagedExecutor managedExecutor;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesService timeseriesService;

  private final String containerName = "ThreadingTestContainer";
  private final String userName = "Testuser";

  @Disabled("TimeseriesSerivce is not thread safe and therefore we get a deadlock in db.")
  @Test
  public void saveDataPoints_addPointsWithMultipleThreadsSimultaneously_noException() throws InterruptedException {
    // arrange
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(123.456);
    dataPoints.add(point);

    // act
    int numberOfThreads = 20;
    CountDownLatch latch = new CountDownLatch(numberOfThreads);

    for (int i = 0; i < numberOfThreads; i++) {
      managedExecutor.execute(() -> {
        QuarkusTransaction.requiringNew()
          .run(() -> {
            try {
              var timeseriesEntity = timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
              assertNotNull(timeseriesEntity);
            } catch (Exception ex) {
              Log.error(ex.toString());
            }
          });
        latch.countDown();
      });
    }
    latch.await();

    // assert
    TimeseriesDataPointsQueryParams params = new TimeseriesDataPointsQueryParams(
      1L,
      Instant.now().toEpochMilli() * 1_000_000L,
      1_000_000_000L,
      null,
      AggregateFunction.COUNT
    );
    var points = timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, params);
    assertTrue(points.size() > 0);
  }
}
