package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TimeseriesServiceTest {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";
  private final long startDate = InstantHelper.fromGermanDate("01.01.2024").toNano();
  private final long endDate = InstantHelper.now().addHours(1).toNano();

  @Test
  @Transactional
  public void saveDataPoints_addDoubleValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(123.456);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    assertNotNull(created);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    TimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Double, "DataPoint value must be a double");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addBooleanValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointBoolean(true);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    assertNotNull(created);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    TimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Boolean, "DataPoint value must be a boolean");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addStringValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointString("Hello World");
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    assertNotNull(created);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    TimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof String, "DataPoint value must be a string");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addIntegerValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(42);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    assertNotNull(created);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    TimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Integer, "DataPoint value must be an integer");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_toExistingTimeseries_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);

    List<TimeseriesDataPoint> morePoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.2))
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, morePoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertEquals(2, actual.size());
  }

  @Test
  @Transactional
  public void saveDataPoints_requiredFieldsMissing_throwsException() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = new Timeseries("", "", "", "", "");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  @Test
  @Transactional
  public void saveDataPoints_addDataPointToExistingTimeseriesWithDifferentType_throwsException() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");

    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(22.3);
    dataPoints.add(point);
    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);

    List<TimeseriesDataPoint> otherDataPoints = new ArrayList<>();
    var pointWithDifferentType = TimeseriesTestDataGenerator.generateDataPointInteger(20);
    otherDataPoints.add(pointWithDifferentType);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.saveDataPoints(container.getId(), timeseries, otherDataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  @Test
  @Transactional
  public void getTimeseriesAvailable_timeseriesExists_returnsTimeseries() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);

    var actual = this.timeseriesService.getTimeseriesAvailable(container.getId());
    assertEquals(1, actual.size());
    assertEquals("temperature", actual.get(0).getMeasurement());
  }

  @Test
  @Transactional
  public void getTimeseriesAvailable_containerDoesNotExist_returnsEmptyList() {
    int nonExistingContainerId = -1;
    var actual = this.timeseriesService.getTimeseriesAvailable(nonExistingContainerId);
    assertEquals(0, actual.size());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_forGivenDuration_returnsAll() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("humidity");
    var start = InstantHelper.now().addDays(-4).toNano();
    var end = InstantHelper.now().addDays(-2).toNano();

    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(start, 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start + 1000, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(start + 100000, 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end - 10000, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(end, 88)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);

    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(start, end, null, null, null);

    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(dataPoints.size(), actual.size());
    assertTrue(actual.containsAll(dataPoints));
    assertTrue(dataPoints.containsAll(actual));
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_forGivenDuration_returnsThreeOutOfFive() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("humidity");
    var start = InstantHelper.now().addDays(-4).toNano();
    var end = InstantHelper.now().addDays(-2).toNano();
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-5).toNano(), 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-3).toNano(), 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-1).toNano(), 88)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(start, end, null, null, null);
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_forGivenDuration_returnNone() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("humidity");
    var start = InstantHelper.now().addDays(-4).toNano();
    var end = InstantHelper.now().addDays(-2).toNano();
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1000, 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1100, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1200, 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end + 10000, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(end + 1000, 88)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(start, end, null, null, null);
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(0, actual.size());
  }

  /**
   * The intended behavior of the timescaleDb is to silently overwrite non-unique timestamp values with the most recent record.
   * Meaning that when a record has a timestamp that is already present in the DB, the new record should overwrite the old one.
   */
  @Test
  @Transactional
  public void saveDataPoint_non_unique_returnOverwritten() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("uniqueness-test-1");

    // setup batch of distinct timeseries data points
    var timeseriesDataPoint1 = new TimeseriesDataPoint(1708067683056880001L, "value 1");
    var timeseriesDataPoint2 = new TimeseriesDataPoint(1708067683056880002L, "value 2");
    var timeseriesDataPoint3 = new TimeseriesDataPoint(1708067683056880003L, "value 3");

    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(timeseriesDataPoint1, timeseriesDataPoint2, timeseriesDataPoint3)
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);

    // create new batch of data points, this checks that overwriting existing timeseries datapoints work
    var timeseriesDataPoint3New = new TimeseriesDataPoint(1708067683056880003L, "value 3 UPDATED");
    var timeseriesDataPoint4 = new TimeseriesDataPoint(1708067683056880004L, "value 4");
    var timeseriesDataPoint5 = new TimeseriesDataPoint(1708067683056880005L, "value 5");

    List<TimeseriesDataPoint> dataPointsContainingNonUnique = new ArrayList<>(
      List.of(timeseriesDataPoint3New, timeseriesDataPoint4, timeseriesDataPoint5)
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPointsContainingNonUnique);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      1,
      1708067683056880099L,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(5, actual.size());
    assertEquals(actual.get(0).getValue(), "value 1");
    assertEquals(actual.get(1).getValue(), "value 2");
    assertEquals(actual.get(2).getValue(), "value 3 UPDATED");
    assertEquals(actual.get(3).getValue(), "value 4");
    assertEquals(actual.get(4).getValue(), "value 5");
  }

  @Test
  @Transactional
  public void saveDataPoint_non_unique_batch_returnException() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("uniqueness-test-2");

    // setup batch of non-unique timestamp values - we expect an exception to be thrown
    var timeseriesDataPoint1 = new TimeseriesDataPoint(1708067683056880001L, "value 1");
    var timeseriesDataPoint2 = new TimeseriesDataPoint(1708067683056880001L, "value 2");

    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(List.of(timeseriesDataPoint1, timeseriesDataPoint2));

    assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    });
  }
}
