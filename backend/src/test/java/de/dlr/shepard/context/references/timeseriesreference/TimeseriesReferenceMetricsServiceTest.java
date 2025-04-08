package de.dlr.shepard.context.references.timeseriesreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.CollectionService;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.timeseriesreference.io.MetricsIO;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceMetricsService;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.services.InstantHelper;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TimeseriesReferenceMetricsServiceTest {

  @Inject
  TimeseriesReferenceMetricsService metricsService;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  CollectionService collectionService;

  @Inject
  DataObjectService dataObjectService;

  @Inject
  TimeseriesService timeseriesService;

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  private long containerId, collectionId, dataobjectId, timeseriesReferenceId, stringTimeseriesReferenceId, booleanTimeseriesReferenceId;
  private Timeseries timeseries, stringTimeseries, booleanTimeseries;

  @BeforeEach
  public void setup() {
    String containerName = "containerName";
    String collectionName = "collectionName";
    String dataObjectName = "DataObjectName";
    String userName = "TestUser";
    String timeseriesReferenceName = "referenceName";

    User user = new User(userName);

    TimeseriesContainerIO containerIO = new TimeseriesContainerIO();
    CollectionIO collectionIO = new CollectionIO();
    DataObjectIO dataObjectIO = new DataObjectIO();
    TimeseriesReferenceIO timeseriesReferenceIO = new TimeseriesReferenceIO();
    timeseriesReferenceIO.setName(timeseriesReferenceName);
    timeseriesReferenceIO.setStart(0);
    timeseriesReferenceIO.setEnd(Long.MAX_VALUE);
    collectionIO.setName(collectionName);
    dataObjectIO.setName(dataObjectName);
    containerIO.setName(containerName);
    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());

    collectionId = collectionService.createCollection(collectionIO).getId();
    dataobjectId = dataObjectService.createDataObject(collectionId, dataObjectIO).getId();

    var timeseriesContainer = timeseriesContainerService.createContainer(containerIO);
    containerId = timeseriesContainer.getId();
    timeseries = new Timeseries("measurement", "device", "location", "symbolicName", "field");
    stringTimeseries = new Timeseries("stringMeasurement", "device", "location", "symbolicName", "field");
    booleanTimeseries = new Timeseries("booleanMeasurement", "device", "location", "symbolicName", "field");

    InstantHelper instantHelper = InstantHelper.fromGermanDate("01.01.2024");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.toNano(), 22.1),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.3),
        TimeseriesTestDataGenerator.generateDataPointDouble(instantHelper.addSeconds(1).toNano(), 22.2)
      )
    );

    timeseriesReferenceIO.setTimeseriesContainerId(containerId);
    timeseriesReferenceIO.setTimeseries(List.of(timeseries));
    timeseriesService.saveDataPoints(containerId, timeseries, dataPoints).getId();
    timeseriesReferenceId = timeseriesReferenceService
      .createReference(collectionId, dataobjectId, timeseriesReferenceIO)
      .getId();

    dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.toNano(), "Point1"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "Point2"),
        TimeseriesTestDataGenerator.generateDataPointString(instantHelper.addSeconds(1).toNano(), "Point3")
      )
    );

    timeseriesReferenceIO.setTimeseries(List.of(stringTimeseries));
    timeseriesService.saveDataPoints(containerId, stringTimeseries, dataPoints).getId();
    stringTimeseriesReferenceId = timeseriesReferenceService
      .createReference(collectionId, dataobjectId, timeseriesReferenceIO)
      .getId();

    dataPoints = new ArrayList<>(
      List.of(
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.toNano(), true),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), false),
        TimeseriesTestDataGenerator.generateDataPointBoolean(instantHelper.addSeconds(1).toNano(), true)
      )
    );

    timeseriesReferenceIO.setTimeseries(List.of(booleanTimeseries));
    timeseriesService.saveDataPoints(containerId, booleanTimeseries, dataPoints).getId();
    booleanTimeseriesReferenceId = timeseriesReferenceService
      .createReference(collectionId, dataobjectId, timeseriesReferenceIO)
      .getId();
  }

  @Test
  public void getReferenceMetrics_doubles_returnsAllMetrics() {
    List<AggregateFunction> metrics = List.of(
      AggregateFunction.FIRST,
      AggregateFunction.LAST,
      AggregateFunction.COUNT,
      AggregateFunction.MAX,
      AggregateFunction.MIN,
      AggregateFunction.STDDEV,
      AggregateFunction.MEDIAN
    );

    var resultList = metricsService.getTimeseriesReferenceMetrics(timeseriesReferenceId, timeseries, metrics);
    assertEquals(resultList.size(), metrics.size());
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.FIRST, 22.1));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.LAST, 22.2));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MIN, 22.1));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MAX, 22.3));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.COUNT, Long.valueOf(3)));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.STDDEV, 0.09999999999999787));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MEDIAN, 22.2));
  }

  @Test
  public void getReferenceMetrics_strings_returnsNeededMetrics() {
    List<AggregateFunction> metrics = List.of(
      AggregateFunction.FIRST,
      AggregateFunction.LAST,
      AggregateFunction.COUNT,
      AggregateFunction.MAX,
      AggregateFunction.MIN,
      AggregateFunction.STDDEV,
      AggregateFunction.MEDIAN
    );

    var resultList = metricsService.getTimeseriesReferenceMetrics(
      stringTimeseriesReferenceId,
      stringTimeseries,
      metrics
    );
    assertEquals(resultList.size(), metrics.size());
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.FIRST, "Point1"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.LAST, "Point3"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MIN, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MAX, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.COUNT, Long.valueOf(3)));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.STDDEV, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MEDIAN, "N/A"));
  }

  @Test
  public void getReferenceMetrics_booleans_returnsNeededMetrics() {
    List<AggregateFunction> metrics = List.of(
      AggregateFunction.FIRST,
      AggregateFunction.LAST,
      AggregateFunction.COUNT,
      AggregateFunction.MAX,
      AggregateFunction.MIN,
      AggregateFunction.STDDEV,
      AggregateFunction.MEDIAN
    );

    var resultList = metricsService.getTimeseriesReferenceMetrics(
      booleanTimeseriesReferenceId,
      booleanTimeseries,
      metrics
    );
    assertEquals(resultList.size(), metrics.size());
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.FIRST, true));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.LAST, true));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MIN, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MAX, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.COUNT, Long.valueOf(3)));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.STDDEV, "N/A"));
    assertThat(resultList).contains(new MetricsIO(AggregateFunction.MEDIAN, "N/A"));
  }

  @AfterEach
  public void tearDown() {
    timeseriesService.deleteTimeseriesByContainerId(containerId);
    timeseriesReferenceService.deleteReference(collectionId, dataobjectId, timeseriesReferenceId);
    timeseriesReferenceService.deleteReference(collectionId, dataobjectId, stringTimeseriesReferenceId);
    timeseriesReferenceService.deleteReference(collectionId, dataobjectId, booleanTimeseriesReferenceId);
    dataObjectService.deleteDataObject(collectionId, dataobjectId);
    collectionService.deleteCollection(collectionId);
  }
}
