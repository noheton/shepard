package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.utilities.LocalDateTimeHelper;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentalTimeseriesContainerServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";

  @Test
  @Order(1)
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    var created = timeseriesService.createContainer(containerName, userName);

    Assertions.assertEquals(created.getName(), containerName);
    Assertions.assertTrue(created.getId() > 0);
  }

  @Test
  @Order(2)
  public void addPayload_addDoubleValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries(container.getId(), "measurement");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(123.456);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Log.infof("Timeseries: containerId: %d, timeseriesId: %d", container.getId(), created.getId());
    Assertions.assertNotNull(created);

    var actual = this.timeseriesService.getDataPoints(timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("DoubleValue must be taken over.", point.getValue(), actualPoint.getDoubleValue());
    Assert.assertTrue("Id must be set.", actualPoint.getId() > 0);
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    Assert.assertEquals("BooleanValue must be null.", null, actualPoint.getBooleanValue());
    Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
    Assert.assertEquals(
      "Timestamp must be taken over.",
      LocalDateTimeHelper.fromMilliseconds(point.getTimestamp()),
      actualPoint.getTime()
    );
    // Assert.assertTrue("TimeseriesId must be unequal to 0.", actualPoint.getTimeseriesId() > 0); // Todo: id is 0
  }

  @Test
  @Order(3)
  public void addPayload_addBooleanValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries(container.getId(), "measurement");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointBoolean(true);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Assertions.assertNotNull(created);

    var actual = this.timeseriesService.getDataPoints(timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("BooleanValue must be taken over.", point.getValue(), actualPoint.getBooleanValue());
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    Assert.assertEquals("DoubleValue must be null.", null, actualPoint.getDoubleValue());
    Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
  }
}
