package de.dlr.shepard.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import de.dlr.shepard.timeseries.model.FillOption;
import de.dlr.shepard.timeseries.utilities.CsvConverter;
import de.dlr.shepard.timeseries.utilities.InstantHelper;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    assertEquals(containerName, created.getName());
    assertTrue(created.getId() > 0);
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
    assertNotNull(created);
    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    assertEquals(point.getValue(), actualPoint.getDoubleValue(), "DoubleValue must be taken over.");
    assertTrue(actualPoint.getId() > 0, "Id must be set.");
    assertEquals(null, actualPoint.getStringValue(), "StringValue must be null.");
    assertEquals(null, actualPoint.getBooleanValue(), "BooleanValue must be null.");
    assertEquals(null, actualPoint.getIntValue(), "IntValue must be null.");
    assertEquals(point.getTimestamp(), actualPoint.getTime(), "Timestamp must be taken over.");
    assertTrue(actualPoint.getTimeseriesId() > 0, "TimeseriesId must be unequal to 0.");
  }

  @Test
  public void addPayload_addBooleanValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointBoolean(true);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    assertNotNull(created);

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    assertEquals(point.getValue(), actualPoint.getBooleanValue(), "BooleanValue must be taken over.");
    assertEquals(null, actualPoint.getStringValue(), "StringValue must be null.");
    assertEquals(null, actualPoint.getDoubleValue(), "DoubleValue must be null.");
    assertEquals(null, actualPoint.getIntValue(), "IntValue must be null.");
  }

  @Test
  public void addPayload_addStringValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("status");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointString("ready");
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    assertNotNull(created);

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    assertEquals(null, actualPoint.getBooleanValue(), "BooleanValue must be null.");
    assertEquals(point.getValue(), actualPoint.getStringValue(), "StringValue must be taken over.");
    assertEquals(null, actualPoint.getDoubleValue(), "DoubleValue must be null.");
    assertEquals(null, actualPoint.getIntValue(), "IntValue must be null.");
  }

  @Test
  public void addPayload_addIntegerValue_success() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("numberOfDays");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    var created = this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    assertNotNull(created);

    var actual = this.timeseriesService.getDataPoints(container.getId(), timeseries, startDate, endDate);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    var actualPoint = actual.get(0);
    assertEquals(null, actualPoint.getBooleanValue(), "BooleanValue must be null.");
    assertEquals(null, actualPoint.getStringValue(), "StringValue must be null.");
    assertEquals(null, actualPoint.getDoubleValue(), "DoubleValue must be null.");
    assertEquals(point.getValue(), actualPoint.getIntValue(), "IntValue must be taken over.");
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
    assertEquals(2, actual.size());
  }

  @Test
  public void addPayload_containerDoesNotExist_throwsException() throws Exception {
    int nonExistingContainerId = -1;
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("test");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(nonExistingContainerId, timeseries, dataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  @Test
  public void addPayload_requiredFieldsMissing_throwsException() throws Exception {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = new ExperimentalTimeseries("", "", "", "", "");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
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

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.addPayload(container.getId(), timeseries, otherDataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
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
    assertEquals(1, actual.size());
    assertEquals("temperature", actual.get(0).getMeasurement());
  }

  @Test
  public void getTimeseriesAvailable_containerDoesNotExist_returnsEmptyList() {
    int nonExistingContainerId = -1;
    var actual = this.timeseriesService.getTimeseriesAvailable(nonExistingContainerId);
    assertEquals(0, actual.size());
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

    assertEquals(3, actual.size());
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

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(0.2, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(13.0, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals((long) 7, actual.get(0).getValue());
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

    assertEquals(1, actual.size());
    assertEquals(17.0, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(5.2372293656638, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals((long) 17, actual.get(0).getValue());
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

    assertEquals(1, actual.size());
    assertEquals(5.2372293656638, ((BigDecimal) actual.get(0).getValue()).doubleValue(), doubleEpsilon);
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

    assertEquals(1, actual.size());
    assertEquals(23, actual.get(0).getValue());
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

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
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

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.5, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(5).getValue(), doubleEpsilon);
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

    assertEquals(3, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(2).getValue(), doubleEpsilon);
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

    assertEquals(3, actual.size());
    assertEquals(true, actual.get(0).getValue());
    assertEquals(false, actual.get(1).getValue());
    assertEquals(true, actual.get(2).getValue());
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

    assertEquals(3, actual.size());
    assertEquals("Hello", actual.get(0).getValue());
    assertEquals("World", actual.get(1).getValue());
    assertEquals("!", actual.get(2).getValue());
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

    assertEquals(3, actual.size());
    assertEquals(1, actual.get(0).getValue());
    assertEquals(2, actual.get(1).getValue());
    assertEquals(3, actual.get(2).getValue());
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

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(1).getValue());
    assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(3).getValue());
    assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(5).getValue());
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

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.4, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(22.25, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(5).getValue());
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
    assertThrowsExactly(InvalidRequestException.class, () -> {
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
    assertThrowsExactly(InvalidRequestException.class, () -> {
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
    assertThrowsExactly(InvalidRequestException.class, () -> {
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
    assertThrowsExactly(InvalidRequestException.class, () -> {
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
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 90.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 120.57),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 127.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(3).toNano(), 129.25),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 134.0)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.exportTimeseriesData(
          container.getId(),
          timeseries,
          null,
          null,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.toNano(),
          null
        );

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_double.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  @Test
  public void exportTimeseriesData_oneTimeseriesWithStringValues_success() throws IOException, URISyntaxException {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("status");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "running"))
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.exportTimeseriesData(
          container.getId(),
          timeseries,
          null,
          null,
          instantHelper.toNano(),
          instantHelper.addSeconds(2).toNano(),
          null
        );

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_string.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  @Test
  public void exportTimeseriesData_oneTimeseriesWithBooleanValues_success() throws IOException, URISyntaxException {
    var container = timeseriesService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("motion");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(2).toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(3).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(4).toNano(), true)
      )
    );

    this.timeseriesService.addPayload(container.getId(), timeseries, dataPoints);

    var actual =
      this.timeseriesService.exportTimeseriesData(
          container.getId(),
          timeseries,
          null,
          null,
          InstantHelper.fromGermanDate("01.01.2024").toNano(),
          instantHelper.toNano(),
          null
        );

    StringBuilder actualCsvContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actual))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualCsvContent.append(line).append("\n");
      }
    }

    var expectedCsvFile = new File(
      getClass().getClassLoader().getResource("timeseries_export_experimental_boolean.csv").toURI()
    );
    var expectedCsvContent = Files.readString(expectedCsvFile.toPath());

    assertEquals(actualCsvContent.toString().trim(), expectedCsvContent.trim());
  }

  /**********************
   * importTimeseriesData
   ***********************/

  @Test
  public void importTimeseriesData_oneTimeseriesWithBooleanValues_success() throws IOException, URISyntaxException {
    var container = timeseriesService.createContainer(containerName, userName);
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");

    File importCSVFile = new File(
      getClass().getClassLoader().getResource("timeseries_import_experimental.csv").toURI()
    );

    String csvFileContent = Files.readString(importCSVFile.toPath());
    Log.info(csvFileContent);

    try (InputStream fileInputStream = new FileInputStream(importCSVFile)) {
      timeseriesService.importTimeseries(container.getId(), fileInputStream);
    }

    List<ExperimentalTimeseriesEntity> availTimeseriesList = timeseriesService.getTimeseriesAvailable(
      container.getId()
    );

    List<ExperimentalTimeseries> expTimeseries = new ArrayList<ExperimentalTimeseries>();

    for (var currTimeseries : availTimeseriesList) {
      expTimeseries.add(
        new ExperimentalTimeseries(
          currTimeseries.getMeasurement(),
          currTimeseries.getField(),
          currTimeseries.getDevice(),
          currTimeseries.getLocation(),
          currTimeseries.getSymbolicName()
        )
      );
    }

    var actualTimeseriesDataMap = timeseriesService.getTimeseriesDataList(
      container.getId(),
      expTimeseries,
      null,
      null,
      null,
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.toNano(),
      null,
      null,
      null
    );

    CsvConverter csvConverter = new CsvConverter();
    var actualTimeSeriesStream = csvConverter.convertToCsv(actualTimeseriesDataMap);

    StringBuilder actualTimeSeriesContent = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(actualTimeSeriesStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        actualTimeSeriesContent.append(line).append("\n");
      }
    }

    assertEquals(actualTimeSeriesContent.toString().trim(), csvFileContent.trim());
  }
}
