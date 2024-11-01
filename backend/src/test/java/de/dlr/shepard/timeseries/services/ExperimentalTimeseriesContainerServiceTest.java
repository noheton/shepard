package de.dlr.shepard.timeseries.services;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.FillOption;
import de.dlr.shepard.timeseries.utilities.InstantHelper;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
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
  private final long startDate = InstantHelper.fromGermanDate("01.01.2024").toNano();
  private final long endDate = InstantHelper.now().addHours(1).toNano();
  private final double doubleEpsilon = 1E-9;

  @Test
  public void createContainer_containerDoesNotExist_containerIsCreated() {
    var created = timeseriesService.createContainer(containerName, userName);

    Assertions.assertEquals(containerName, created.getName());
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

  /*
   * Test Aggregate Functions without Special Grouping/ Filling
   */

  @Test
  public void getDataPoints_getMax_returnMax_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(1).toNano(),
          null,
          AggregateFunctions.MAX,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMean_returnMean_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
          null,
          AggregateFunctions.MEAN,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMin_returnMin_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
          null,
          AggregateFunctions.MIN,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getLast_returnLast_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
          null,
          AggregateFunctions.LAST,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getFirst_returnFirst_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
          null,
          AggregateFunctions.FIRST,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getSpread_returnSpread_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
          null,
          AggregateFunctions.SPREAD,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(0.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMode_returnMode_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.4),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(1).toNano(),
          null,
          AggregateFunctions.MODE,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMedian_returnMedian_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 2.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 3.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 11.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 13.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 26.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 34.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 47.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(8).toNano(),
          null,
          AggregateFunctions.MEDIAN,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(13.0, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getCount_returnCount_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 2.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 3.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 11.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 13.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 26.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 34.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 47.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          null,
          AggregateFunctions.COUNT,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals((long) 7, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getSum_returnSum_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 2.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 3.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 5.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 7.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          null,
          AggregateFunctions.SUM,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(17.0, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getStddev_returnStddev_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 10.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 12.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 23.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 23.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 16.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 23.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 21.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 16.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          null,
          AggregateFunctions.STDDEV,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(5.2372293656638, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getSum_returnSum_noFill_noGroupBy_integer() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 3),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 5),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 7)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          null,
          AggregateFunctions.SUM,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals((long) 17, actual.get(0).getValue());
  }

  @Test
  public void getDataPoints_getStddev_returnStddev_noFill_noGroupBy_integer() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 10),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 12),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 16),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 21),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 16)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          null,
          AggregateFunctions.STDDEV,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(5.2372293656638, ((BigDecimal) actual.get(0).getValue()).doubleValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMax_returnsMax_noFill_groupBy_integer() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 21),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 22),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MAX,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(23, actual.get(0).getValue());
  }

  /*
   * Test aggregate functions with options of fill and groupBy
   */

  @Test
  public void getDataPoints_getMax_returnsMax_noFill_groupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.3)
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
          AggregateFunctions.MAX,
          null
        );

    Assert.assertEquals(1, actual.size());
    Assert.assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_getMax_returnMax_prevFill_groupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofSeconds(2).toNanos(),
          AggregateFunctions.MAX,
          FillOption.PREVIOUS
        );

    Assert.assertEquals(6, actual.size());
    Assert.assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    Assert.assertEquals(22.5, (Double) actual.get(1).getValue(), doubleEpsilon);
    Assert.assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    Assert.assertEquals(22.3, (Double) actual.get(3).getValue(), doubleEpsilon);
    Assert.assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    Assert.assertEquals(22.2, (Double) actual.get(5).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_noFunc_noFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
          null,
          null,
          null
        );

    Assert.assertEquals(3, actual.size());
    Assert.assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
    Assert.assertEquals(22.3, (Double) actual.get(1).getValue(), doubleEpsilon);
    Assert.assertEquals(22.2, (Double) actual.get(2).getValue(), doubleEpsilon);
  }

  @Test
  public void getDataPoints_noFunc_noFill_noGroupBy_boolean() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(true)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          null,
          null,
          null
        );

    Assert.assertEquals(3, actual.size());
    Assert.assertEquals(true, actual.get(0).getValue());
    Assert.assertEquals(false, actual.get(1).getValue());
    Assert.assertEquals(true, actual.get(2).getValue());
  }

  @Test
  public void getDataPoints_noFunc_noFill_noGroupBy_string() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString("Hello"),
        TimeseriesTestDataGenerator.generateDataPointString("World"),
        TimeseriesTestDataGenerator.generateDataPointString("!")
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          null,
          null,
          null
        );

    Assert.assertEquals(3, actual.size());
    Assert.assertEquals("Hello", actual.get(0).getValue());
    Assert.assertEquals("World", actual.get(1).getValue());
    Assert.assertEquals("!", actual.get(2).getValue());
  }

  @Test
  public void getDataPoints_noFunc_noFill_noGroupBy_integer() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 1),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 3)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          null,
          null,
          null
        );

    Assert.assertEquals(3, actual.size());
    Assert.assertEquals(1, actual.get(0).getValue());
    Assert.assertEquals(2, actual.get(1).getValue());
    Assert.assertEquals(3, actual.get(2).getValue());
  }

  @Test
  public void getDataPoints_getMax_returnMax_nullFill_groupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofSeconds(2).toNanos(),
          AggregateFunctions.MAX,
          FillOption.NULL
        );

    Assert.assertEquals(6, actual.size());
    Assert.assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    Assert.assertEquals(null, actual.get(1).getValue());
    Assert.assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    Assert.assertEquals(null, actual.get(3).getValue());
    Assert.assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    Assert.assertEquals(null, actual.get(5).getValue());
  }

  @Test
  public void getDataPoints_getMax_returnMax_linearFill_groupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofSeconds(2).toNanos(),
          AggregateFunctions.MAX,
          FillOption.LINEAR
        );

    Assert.assertEquals(6, actual.size());
    Assert.assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    Assert.assertEquals(22.4, (Double) actual.get(1).getValue(), doubleEpsilon);
    Assert.assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    Assert.assertEquals(22.25, (Double) actual.get(3).getValue(), doubleEpsilon);
    Assert.assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    Assert.assertEquals(null, actual.get(5).getValue());
  }

  /*
   * Aggregate Function Exceptions
   */
  @Test
  public void getDataPoints_getMax_returnsException_noFill_groupBy_boolean() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(true)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    // Booleans must not use aggregation
    Assertions.assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MAX,
          null
        );
    });
  }

  @Test
  public void getDataPoints_getMax_returnsException_noFill_groupBy_string() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString("Hello"),
        TimeseriesTestDataGenerator.generateDataPointString("World"),
        TimeseriesTestDataGenerator.generateDataPointString("!")
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    // Strings must not use aggregation
    Assertions.assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.addSeconds(2).toNano(),
          Duration.ofMinutes(2).toNanos(),
          AggregateFunctions.MAX,
          null
        );
    });
  }

  @Test
  public void getDataPoints_returnsException_noFunc_prevFill_groupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(6).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(9).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    // Filling/ Grouping without specifying aggregation function is not allowed
    Assertions.assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
          Duration.ofSeconds(3).toNanos(),
          null,
          FillOption.PREVIOUS
        );
    });
  }

  @Test
  public void getDataPoints_getMax_returnsException_linearFill_noGroupBy() {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.2)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    // Setting fill option, without specifying groupBy/ interval is not allowed
    Assertions.assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsAggregated(
          container.getId(),
          timeseries,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
          null,
          AggregateFunctions.MAX,
          FillOption.LINEAR
        );
    });
  }

  /**********************
   * exportTimeseriesData
   ***********************/

  @Test
  public void exportTimeseriesData_oneTimeseriesWithDoubleValues_success() throws IOException, URISyntaxException {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("water_level");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-1).toNano(), 120.57),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-2).toNano(), 127.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-3).toNano(), 129.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(InstantHelper.now().addSeconds(-4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.exportTimeseriesData(
          container.getId(),
          timeseries,
          null,
          Duration.ofMinutes(2).toNanos(),
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          InstantHelper.now().toNano(),
          null
        );

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(getClass().getClassLoader().getResource("timeseries_export.csv").toURI());
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString(), expectedCsvContent); //TODO check again after finished implementation of getDataPointsAggregated()
  }

  @Test
  public void exportTimeseriesData_oneTimeseriesWithStringValues_success() throws IOException, URISyntaxException {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("status");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointString("running"))
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.exportTimeseriesData(
          container.getId(),
          timeseries,
          null,
          Duration.ofMinutes(2).toNanos(),
          InstantHelper.now().addHours(-1).toNano(),
          InstantHelper.now().addHours(1).toNano(),
          null
        );

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    assertTrue(actualCsvContent.toString().endsWith("\"running\""));
  }
}
