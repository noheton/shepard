package de.dlr.shepard.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.configuration.feature.toggles.ExperimentalTimeseriesFeatureToggle;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.timeseries.model.enums.FillOption;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@QuarkusTest
@EnabledIf(ExperimentalTimeseriesFeatureToggle.IS_ENABLED_METHOD_ID)
public class ExperimentalTimeseriesServiceTest {

  @Inject
  ExperimentalTimeseriesService timeseriesService;

  @Inject
  ExperimentalTimeseriesContainerService timeseriesContainerService;

  private final String containerName = "AnotherContainer";
  private final String userName = "Testuser";
  private final long startDate = InstantHelper.fromGermanDate("01.01.2024").toNano();
  private final long endDate = InstantHelper.now().addHours(1).toNano();
  private final double doubleEpsilon = 1E-9;

  @Test
  @Transactional
  public void saveDataPoints_addDoubleValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(123.456);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    assertNotNull(created);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    ExperimentalTimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Double, "DataPoint value must be a double");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addBooleanValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointBoolean(true);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    assertNotNull(created);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    ExperimentalTimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Boolean, "DataPoint value must be a boolean");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addStringValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointString("Hello World");
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    assertNotNull(created);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    ExperimentalTimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof String, "DataPoint value must be a string");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_addIntegerValue_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("measurement");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(42);
    dataPoints.add(point);

    var created = this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    assertNotNull(created);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      startDate,
      endDate,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    assertNotNull(actual);
    assertEquals(1, actual.size());
    ExperimentalTimeseriesDataPoint actualPoint = actual.get(0);
    assertTrue(actualPoint.getValue() instanceof Integer, "DataPoint value must be an integer");
    assertEquals(point.getTimestamp(), actualPoint.getTimestamp(), "DataPoint timestamp must be taken over");
  }

  @Test
  @Transactional
  public void saveDataPoints_toExistingTimeseries_success() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);

    List<ExperimentalTimeseriesDataPoint> morePoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.2))
    );

    this.timeseriesService.saveDataPoints(container, timeseries, morePoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
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
    var timeseries = new ExperimentalTimeseries("", "", "", "", "");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointInteger(5);
    dataPoints.add(point);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  @Test
  @Transactional
  public void saveDataPoints_addDataPointToExistingTimeseriesWithDifferentType_throwsException() throws Exception {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");

    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
    var point = TimeseriesTestDataGenerator.generateDataPointDouble(22.3);
    dataPoints.add(point);
    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);

    List<ExperimentalTimeseriesDataPoint> otherDataPoints = new ArrayList<>();
    var pointWithDifferentType = TimeseriesTestDataGenerator.generateDataPointInteger(20);
    otherDataPoints.add(pointWithDifferentType);

    InvalidBodyException thrown = assertThrowsExactly(InvalidBodyException.class, () -> {
      this.timeseriesService.saveDataPoints(container, timeseries, otherDataPoints);
    });

    assertEquals(Status.BAD_REQUEST.getStatusCode(), thrown.getResponse().getStatus());
  }

  /*************************
   * getTimeseriesAvailable
   *************************/
  @Test
  @Transactional
  public void getTimeseriesAvailable_timeseriesExists_returnsTimeseries() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(TimeseriesTestDataGenerator.generateDataPointDouble(22.1))
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);

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

  /**************
   * getTimeseriesDataIO
   ****************/

  @Test
  @Transactional
  public void getDataPointsByTimeseries_forGivenDuration_returnsAll() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("humidity");
    var start = InstantHelper.now().addDays(-4).toNano();
    var end = InstantHelper.now().addDays(-2).toNano();

    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(start, 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start + 1000, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(start + 100000, 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end - 10000, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(end, 88)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);

    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      start,
      end,
      null,
      null,
      null
    );

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
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-5).toNano(), 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-3).toNano(), 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(InstantHelper.now().addDays(-1).toNano(), 88)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      start,
      end,
      null,
      null,
      null
    );
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
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1000, 70),
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1100, 80),
        TimeseriesTestDataGenerator.generateDataPointInteger(start - 1200, 65),
        TimeseriesTestDataGenerator.generateDataPointInteger(end + 10000, 72),
        TimeseriesTestDataGenerator.generateDataPointInteger(end + 1000, 88)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      start,
      end,
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(0, actual.size());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(1).toNano(),
      null,
      null,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMean_returnMean_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
      null,
      null,
      AggregateFunction.MEAN
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMin_returnMin_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
      null,
      null,
      AggregateFunction.MIN
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getLast_returnLast_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
      null,
      null,
      AggregateFunction.LAST
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getFirst_returnFirst_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
      null,
      null,
      AggregateFunction.FIRST
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getSpread_returnSpread_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(3).toNano(),
      null,
      null,
      AggregateFunction.SPREAD
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(0.2, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMode_returnMode_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(1).toNano(),
      null,
      null,
      AggregateFunction.MODE
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMedian_returnMedian_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(8).toNano(),
      null,
      null,
      AggregateFunction.MEDIAN
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(13.0, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getCount_returnCount_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.COUNT
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals((long) 7, actual.get(0).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getSum_returnSum_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 2.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 3.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 5.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 7.0)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.SUM
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(17.0, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getStddev_returnStddev_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.STDDEV
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(5.2372293656638, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getSum_returnSum_noFill_noGroupBy_integer() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 3),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 5),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 7)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.SUM
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals((long) 17, actual.get(0).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getStddev_returnStddev_noFill_noGroupBy_integer() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.STDDEV
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(5.2372293656638, ((BigDecimal) actual.get(0).getValue()).doubleValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsMax_noFill_groupBy_integer() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 21),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 22),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofMinutes(2).toNanos(),
      null,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(23, actual.get(0).getValue());
  }

  /*
   * Test aggregate functions with options of fill and groupBy
   */

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsMax_noFill_groupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.3)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.now().toNano(),
      Duration.ofMinutes(2).toNanos(),
      null,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(22.3, (Double) actual.get(0).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_prevFill_groupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.PREVIOUS,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.5, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(5).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_noFunc_noFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(2).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_noFunc_noFill_noGroupBy_boolean() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(true)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.now().toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
    assertEquals(true, actual.get(0).getValue());
    assertEquals(false, actual.get(1).getValue());
    assertEquals(true, actual.get(2).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_noFunc_noFill_noGroupBy_string() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString("Hello"),
        TimeseriesTestDataGenerator.generateDataPointString("World"),
        TimeseriesTestDataGenerator.generateDataPointString("!")
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.now().toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
    assertEquals("Hello", actual.get(0).getValue());
    assertEquals("World", actual.get(1).getValue());
    assertEquals("!", actual.get(2).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_noFunc_noFill_noGroupBy_integer() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 1),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 3)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.now().toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
    assertEquals(1, actual.get(0).getValue());
    assertEquals(2, actual.get(1).getValue());
    assertEquals(3, actual.get(2).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_nullFill_groupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.NULL,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(1).getValue());
    assertEquals(22.3, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(3).getValue());
    assertEquals(22.2, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(5).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_linearFill_groupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.LINEAR,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

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
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsException_noFill_groupBy_boolean() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(true)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofMinutes(2).toNanos(),
      null,
      AggregateFunction.MAX
    );
    // Booleans must not use aggregation
    assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    });
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsException_noFill_groupBy_string() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString("Hello"),
        TimeseriesTestDataGenerator.generateDataPointString("World"),
        TimeseriesTestDataGenerator.generateDataPointString("!")
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofMinutes(2).toNanos(),
      null,
      AggregateFunction.MAX
    );
    // Strings must not use aggregation
    assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    });
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_returnsException_noFunc_prevFill_groupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(6).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(9).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
      Duration.ofSeconds(3).toNanos(),
      FillOption.PREVIOUS,
      null
    );
    // Filling/ Grouping without specifying aggregation function is not allowed
    assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    });
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsException_linearFill_noGroupBy() {
    var container = timeseriesContainerService.createContainer(containerName, userName);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container, timeseries, dataPoints);
    ExperimentalTimeseriesDataPointsQueryParams queryParams = new ExperimentalTimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(5).toNano(),
      null,
      FillOption.LINEAR,
      AggregateFunction.MAX
    );
    // Setting fill option, without specifying groupBy/ interval is not allowed
    assertThrowsExactly(InvalidRequestException.class, () -> {
      this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);
    });
  }
}
