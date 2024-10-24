package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.utilities.LocalDateTimeHelper;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ExperimentalTimeseriesContainerServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";

  @Test
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    var created = timeseriesService.createContainer(containerName, userName);

    Assertions.assertEquals(created.getName(), containerName);
    Assertions.assertTrue(created.getId() > 0);
  }

  @Test
  public void addPayload_addDoubleValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(123.456);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Log.infof("Timeseries: containerId: %d, timeseriesId: %d", container.getId(), created.getId());
    Assertions.assertNotNull(created);
    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
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
    Assert.assertTrue("TimeseriesId must be unequal to 0.", actualPoint.getTimeseriesId() > 0);
  }

  @Test
  public void addPayload_addBooleanValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointBoolean(true);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Assertions.assertNotNull(created);

    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("BooleanValue must be taken over.", point.getValue(), actualPoint.getBooleanValue());
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    Assert.assertEquals("DoubleValue must be null.", null, actualPoint.getDoubleValue());
    Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
  }

  @Test
  public void addPayload_addStringValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("status");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointString("ready");
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Assertions.assertNotNull(created);

    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("BooleanValue must be null.", null, actualPoint.getBooleanValue());
    Assert.assertEquals("StringValue must be taken over.", point.getValue(), actualPoint.getStringValue());
    Assert.assertEquals("DoubleValue must be null.", null, actualPoint.getDoubleValue());
    Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
  }

  @Test
  public void addPayload_addIntegerValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("numberOfDays");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    Assertions.assertNotNull(created);

    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("BooleanValue must be null.", null, actualPoint.getBooleanValue());
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    Assert.assertEquals("DoubleValue must be null.", null, actualPoint.getDoubleValue());
    Assert.assertEquals("IntValue must be taken over.", point.getValue(), actualPoint.getIntValue());
  }

  @Test
  public void addPayload_toExistingTimeseries_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    List<ExperimentalTimeseriesPayloadDataPointIO> morePoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.2))
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, morePoints);

    var actual =
      this.timeseriesService.getDataPoints(container.getId(), timeseries, 0L, 1_000_000_000_000_000_000L, 0L);
    Assert.assertEquals(2, actual.size());
  }

  @Test
  public void addPayload_containerDoesNotExist_throwsException() throws Exception {
    int nonExistingContainerId = -1;
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("test");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(nonExistingContainerId, timeseries, dataPoints);
    });

    Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  @Test
  public void addPayload_requiredFieldsMissing_throwsException() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = new ExperimentalTimeseries("", "", "", "", "");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    });

    Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }
}
