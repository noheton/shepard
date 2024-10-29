package de.dlr.shepard.timeseries.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.utilities.InstantHelper;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;

@QuarkusTest
public class ExperimentalTimeseriesContainerServiceTest {

  @Inject
  ExperimentalTimeseriesContainerService timeseriesService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";
  private final long startDate = InstantHelper.fromGermanDate("01.01.2024").toNano();
  private final long endDate = InstantHelper.now().addHours(1).toNano();
  private final double doubleEpsilon = 1E-9;

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
    Log.warn(startDate);
    Log.warn(endDate);
    Assertions.assertNotNull(created);
    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
    Assert.assertNotNull(actual);
    Assert.assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    Assert.assertEquals("DoubleValue must be taken over.", point.getValue(), actualPoint.getDoubleValue());
    Assert.assertTrue("Id must be set.", actualPoint.getId() > 0);
    Assert.assertEquals("StringValue must be null.", null, actualPoint.getStringValue());
    Assert.assertEquals("BooleanValue must be null.", null, actualPoint.getBooleanValue());
    Assert.assertEquals("IntValue must be null.", null, actualPoint.getIntValue());
    Assert.assertEquals("Timestamp must be taken over.", point.getTimestamp(), actualPoint.getTime());
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

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
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

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
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

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
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

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
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

  @Test
  public void addPayload_addDataPointToExistingTimeseriesWithDifferentType_throwsException() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");

    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(22.3);
    dataPoints.add(point);
    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    List<ExperimentalTimeseriesPayloadDataPointIO> otherDataPoints = new ArrayList<>();
    var pointWithDifferentType = TimeseriesTestDataGenerator.generateDataPointInteger(20);
    otherDataPoints.add(pointWithDifferentType);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(container.getId(), timeseries, otherDataPoints);
    });

    Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  /*************************
   * getTimeseriesAvailable
   *************************/
  @Test
  public void getTimeseriesAvailable_timeseriesExists_returnsTimeseries() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual = this.timeseriesService.getTimeseriesAvailable(container.getId());
    Assert.assertEquals(1, actual.size());
    Assert.assertEquals("temperature", actual.get(0).getMeasurement());
  }

  @Test
  public void getTimeseriesAvailable_containerDoesNotExist_returnsEmptyList() {
    int nonExistingContainerId = -1;
    var actual = this.timeseriesService.getTimeseriesAvailable(nonExistingContainerId);
    Assert.assertEquals(0, actual.size());
  }

  /**************
   * getDataPoints
   ****************/
  @Test
  public void getDataPoints_forGivenDuration_returnsThreeOutOfFive() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("humidity");
    var start = InstantHelper.now().addDays(-4).toNano();
    var end = InstantHelper.now().addDays(-2).toNano();
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-5).toNano(), 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-3).toNano(), 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-1).toNano(), 88)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, start, end);

    Assert.assertEquals(3, actual.size());
  }

  /************************
   * getDataPointsAggregated
   **************************/
  @Test
  public void getDataPoints_getMax_returnsMax() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 22.3)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MAX
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.3, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getMin_returnsMin() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 22.3)
      )
    );

    Log.info("DataPoint: " + dataPoints.get(2).getTimestamp() + " " + dataPoints.get(2).getValue());

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MIN
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.1, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getMean_returnsMean() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MEAN
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(113.0, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMedian_returnsMedian() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MEDIAN
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(120, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getCount_returnsCount() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.COUNT
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals((long) 5, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getSum_returnsSum() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.SUM
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(565.5, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getLast_returnsLast() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.LAST
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(90.0, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getFirst_returnsFirst() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.FIRST
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(134.0, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getMode_returnsMode() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-5).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-6).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-7).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MODE
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(121.25, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getSpread_returnsSpread() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.SPREAD
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(44, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getStddev_returnsStddev() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 100.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 120.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 121.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.STDDEV
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(17.745245842197, (Double) actual.get(0).getValue(), doubleEpsilon);
  }
}
