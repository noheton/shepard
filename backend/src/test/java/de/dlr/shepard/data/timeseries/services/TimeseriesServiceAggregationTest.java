package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TimeseriesServiceAggregationTest {

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  private final String containerName = "AnotherContainer";
  private final double doubleEpsilon = 1E-9;

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_noFill_noGroupBy() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.1),
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

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 2.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 3.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 5.0),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 7.0)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 3),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 5),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 7)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      null,
      null,
      AggregateFunction.SUM
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(BigDecimal.valueOf(17), actual.get(0).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getStddev_returnStddev_noFill_noGroupBy_integer() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
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

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 21),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 22),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 23)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(2).toNano(),
      Duration.ofMinutes(2).toNanos(),
      null,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(1, actual.size());
    assertEquals(23L, actual.get(0).getValue());
  }

  /*
   * Test aggregate functions with options of fill and groupBy
   */

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsMax_noFill_groupBy() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(10).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.PREVIOUS,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(6, actual.size());
    assertEquals(22.1, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.1, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.5, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(22.5, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(5).getValue(), doubleEpsilon);
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_noFunc_noFill_noGroupBy() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), true)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "Hello"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "World"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "!")
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.toNano(), 1),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 2),
        TimeseriesTestDataGenerator.generateDataPointInteger(instantHelper.addSeconds(1).toNano(), 3)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.now().toNano(),
      null,
      null,
      null
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(3, actual.size());
    assertEquals(1L, actual.get(0).getValue());
    assertEquals(2L, actual.get(1).getValue());
    assertEquals(3L, actual.get(2).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_nullFill_groupBy() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(4).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(10).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.NULL,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(2).getValue());
    assertEquals(22.2, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(4).getValue());
    assertEquals(null, actual.get(5).getValue());
  }

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnMax_linearFill_groupBy() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.5),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(5).toNano(), 22.15)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      InstantHelper.fromGermanDate("01.01.2024").addSeconds(10).toNano(),
      Duration.ofSeconds(2).toNanos(),
      FillOption.LINEAR,
      AggregateFunction.MAX
    );
    var actual = this.timeseriesService.getDataPointsByTimeseries(container.getId(), timeseries, queryParams);

    assertEquals(6, actual.size());
    assertEquals(22.5, (Double) actual.get(0).getValue(), doubleEpsilon);
    assertEquals(22.3, (Double) actual.get(1).getValue(), doubleEpsilon);
    assertEquals(22.25, (Double) actual.get(2).getValue(), doubleEpsilon);
    assertEquals(22.2, (Double) actual.get(3).getValue(), doubleEpsilon);
    assertEquals(22.15, (Double) actual.get(4).getValue(), doubleEpsilon);
    assertEquals(null, actual.get(5).getValue());
  }

  /*
   * Aggregate Function Exceptions
   */

  @Test
  @Transactional
  public void getDataPointsByTimeseries_getMax_returnsException_noFill_groupBy_boolean() {
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), true)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(3).toNano(),
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "Hello"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "World"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "!")
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
      InstantHelper.fromGermanDate("01.01.2024").toNano(),
      instantHelper.addSeconds(3).toNano(),
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(6).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(9).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
    User user = new User("Testuser");
    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    containerIO.setName(containerName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    var container = timeseriesContainerService.createContainer(containerIO);
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(2).toNano(), 22.2)
      )
    );

    this.timeseriesService.saveDataPoints(container.getId(), timeseries, dataPoints);
    TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
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
