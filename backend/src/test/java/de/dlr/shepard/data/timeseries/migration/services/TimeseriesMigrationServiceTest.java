package de.dlr.shepard.data.timeseries.migration.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.RandomGenerator;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTestDataGenerator;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskState;
import de.dlr.shepard.data.timeseries.migration.repositories.MigrationTaskRepository;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.services.InstantHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(MigrationModeTestProfile.class)
@ActivateRequestContext
public class TimeseriesMigrationServiceTest {

  private TimeseriesContainer container;

  @Inject
  TimeseriesMigrationService migrationService;

  @Inject
  InfluxDBConnector influxConnector;

  @Inject
  TimeseriesContainerDAO timeseriesContainerDao;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Inject
  MigrationTaskRepository migrationTaskRepository;

  @BeforeEach
  public void setup() {
    migrationTaskRepository.deleteAll();
    this.container = createTimeseriesContainerAndInfluxDbForTesting();
  }

  @Test
  public void runMigrations_createMigrationTask_propertiesAreSet() {
    // arrange
    var payload = new InfluxTestDataGenerator().addInfluxPoint("value").buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();
    var migrationStates = migrationService.getMigrationTasks(false);

    // assert
    var relevantMigrationStates = migrationStates
      .stream()
      .filter(m -> m.getContainerId() == container.getId())
      .toList();
    assertEquals(1, relevantMigrationStates.size(), "More than one migration states do exist.");
    var migrationState = relevantMigrationStates.get(0);
    assertEquals(container.getId(), migrationState.getContainerId(), "wrong container id");
    assertIterableEquals(new ArrayList<String>(), migrationState.getErrors(), "there are errors in the list");
    assertTrue(migrationState.getId() > 0, "id is not set properly");
    assertEquals(MigrationTaskState.Finished, migrationState.getState(), "wrong migration state");
    assertTrue(new Date().getTime() - migrationState.getCreatedAt().getTime() < 30_000, "createdAt not set properly");
    assertTrue(new Date().getTime() - migrationState.getStartedAt().getTime() < 30_000, "startedAt not set properly");
    assertTrue(new Date().getTime() - migrationState.getFinishedAt().getTime() < 30_000, "finishedAt not set properly");
  }

  @Test
  public void runMigrations_migrateValues_timeseriesIsCreated() {
    // arrange
    var payload = new InfluxTestDataGenerator().addInfluxPoint(100, "string").buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var timeseries = timeseriesRepository.findTimeseries(container.getId(), mapToTimeseries(payload.getTimeseries()));
    assertTrue(timeseries.isPresent(), "timeseries not found in timescaledb");
    assertEquals(container.getId(), timeseries.get().getContainerId());
    assertEquals(payload.getTimeseries().getDevice(), timeseries.get().getDevice());
    assertEquals(payload.getTimeseries().getField(), timeseries.get().getField());
    assertTrue(timeseries.get().getId() > 0);
    assertEquals(payload.getTimeseries().getLocation(), timeseries.get().getLocation());
    assertEquals(payload.getTimeseries().getMeasurement(), timeseries.get().getMeasurement());
    assertEquals(payload.getTimeseries().getSymbolicName(), timeseries.get().getSymbolicName());
    assertEquals(DataPointValueType.String, timeseries.get().getValueType());
  }

  @Test
  public void runMigrations_migrateDoubleValues_success() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint(100, 1.1)
      .addInfluxPoint(200, 2.2)
      .addInfluxPoint(300, 3.3)
      .addInfluxPoint(500, 3.4)
      .addInfluxPoint(600, 3.5)
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var timeseries = timeseriesRepository.findTimeseries(container.getId(), mapToTimeseries(payload.getTimeseries()));
    assertTrue(timeseries.isPresent(), "timeseries not found in timescaledb");
    assertEquals(DataPointValueType.Double, timeseries.get().getValueType());

    var queryParams = new TimeseriesDataPointsQueryParams(0, InstantHelper.now().toNano(), null, null, null);
    var dataPoints = timeseriesDataPointRepository.queryDataPoints(
      timeseries.get().getId(),
      DataPointValueType.Double,
      queryParams
    );

    assertEquals(payload.getPoints().size(), dataPoints.size());
    for (int i = 0; i < payload.getPoints().size(); i++) {
      assertEquals(payload.getPoints().get(i).getTimeInNanoseconds(), dataPoints.get(i).getTimestamp());
      assertEquals(payload.getPoints().get(i).getValue(), dataPoints.get(i).getValue());
    }
  }

  @Test
  public void runMigrations_migrateStringValues_success() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint(100, "Hello from influx.")
      .addInfluxPoint(200, "!§$%&/()=\\_-*ÖÄÜ")
      .addInfluxPoint(300, "<html>")
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var timeseries = timeseriesRepository.findTimeseries(container.getId(), mapToTimeseries(payload.getTimeseries()));
    assertTrue(timeseries.isPresent(), "timeseries not found in timescaledb");
    assertEquals(DataPointValueType.String, timeseries.get().getValueType());

    var queryParams = new TimeseriesDataPointsQueryParams(0, InstantHelper.now().toNano(), null, null, null);
    var dataPoints = timeseriesDataPointRepository.queryDataPoints(
      timeseries.get().getId(),
      DataPointValueType.String,
      queryParams
    );

    assertEquals(payload.getPoints().size(), dataPoints.size());
    for (int i = 0; i < payload.getPoints().size(); i++) {
      assertEquals(payload.getPoints().get(i).getTimeInNanoseconds(), dataPoints.get(i).getTimestamp());
      assertEquals(payload.getPoints().get(i).getValue(), dataPoints.get(i).getValue());
    }
  }

  @Test
  public void runMigrations_migrateBooleanValues() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint(100, true)
      .addInfluxPoint(200, false)
      .addInfluxPoint(300, true)
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var timeseries = timeseriesRepository.findTimeseries(container.getId(), mapToTimeseries(payload.getTimeseries()));
    assertTrue(timeseries.isPresent(), "timeseries not found in timescaledb");
    assertEquals(DataPointValueType.Boolean, timeseries.get().getValueType());

    var queryParams = new TimeseriesDataPointsQueryParams(0, InstantHelper.now().toNano(), null, null, null);
    var dataPoints = timeseriesDataPointRepository.queryDataPoints(
      timeseries.get().getId(),
      DataPointValueType.Boolean,
      queryParams
    );

    assertEquals(payload.getPoints().size(), dataPoints.size());
    for (int i = 0; i < payload.getPoints().size(); i++) {
      assertEquals(payload.getPoints().get(i).getTimeInNanoseconds(), dataPoints.get(i).getTimestamp());
      assertEquals(payload.getPoints().get(i).getValue(), dataPoints.get(i).getValue());
    }
  }

  @Test
  public void runMigrations_migrateIntegerValues() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint(1000, 1)
      .addInfluxPoint(2200, 2)
      .addInfluxPoint(3300, 3)
      .addInfluxPoint(3400, 4)
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var timeseries = timeseriesRepository.findTimeseries(container.getId(), mapToTimeseries(payload.getTimeseries()));
    assertTrue(timeseries.isPresent(), "timeseries not found in timescaledb");
    // That is not a mistake but depends on the current implementation in InfluxUtil.java.
    // Integer values are stored as double values in the influxDB, because if you
    // want to insert double values but start with an integer, you get an error
    // when inserting the first double value.
    assertEquals(DataPointValueType.Double, timeseries.get().getValueType());

    var queryParams = new TimeseriesDataPointsQueryParams(0, InstantHelper.now().toNano(), null, null, null);
    var dataPoints = timeseriesDataPointRepository.queryDataPoints(
      timeseries.get().getId(),
      DataPointValueType.Double,
      queryParams
    );

    assertEquals(payload.getPoints().size(), dataPoints.size());
    for (int i = 0; i < payload.getPoints().size(); i++) {
      assertEquals(payload.getPoints().get(i).getTimeInNanoseconds(), dataPoints.get(i).getTimestamp());
      // Attention: we have to convert to original Integer values into Double because
      // of the existing implementation where Integers are store as Double in influxDB.
      var valueAsDouble = Double.valueOf(payload.getPoints().get(i).getValue().toString());
      assertEquals(valueAsDouble, dataPoints.get(i).getValue());
    }
  }

  @Test
  public void runMigrations_migrateDatabaseWithoutAnyData_shouldNotFail() {
    // arrange
    influxConnector.deleteDatabase(this.container.getDatabase());

    var payload = new InfluxTestDataGenerator().buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);

    // act
    migrationService.runMigrations();

    // assert
    var migrationStates = migrationService.getMigrationTasks(false);
    var relevantMigrationStates = migrationStates
      .stream()
      .filter(m -> m.getContainerId() == container.getId())
      .toList();
    assertEquals(1, relevantMigrationStates.size());
    assertEquals(0, relevantMigrationStates.get(0).getErrors().size());
    assertEquals(container.getId(), relevantMigrationStates.get(0).getContainerId());
    assertEquals(MigrationTaskState.Finished, relevantMigrationStates.get(0).getState());
  }

  private TimeseriesContainer createTimeseriesContainerAndInfluxDbForTesting() {
    var databaseName = "Database-" + RandomGenerator.generateString(10);
    var containerName = "Container-" + RandomGenerator.generateString(10);
    TimeseriesContainer entity = new TimeseriesContainer();
    entity.setDatabase(databaseName);
    entity.setName(containerName);
    var savedEntity = timeseriesContainerDao.createOrUpdate(entity);
    influxConnector.createDatabase(databaseName);
    return savedEntity;
  }

  private static Timeseries mapToTimeseries(InfluxTimeseries timeseries) {
    return new Timeseries(
      timeseries.getMeasurement(),
      timeseries.getDevice(),
      timeseries.getLocation(),
      timeseries.getSymbolicName(),
      timeseries.getField()
    );
  }
}
