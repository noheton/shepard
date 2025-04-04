package de.dlr.shepard.data.timeseries.migration.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import de.dlr.shepard.RandomGenerator;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(MigrationModeTestProfile.class)
@ActivateRequestContext
public class TimeseriesMigrationTestDataIngestionServiceTest {

  @Inject
  TimeseriesMigrationTestDataIngestionService dataIngestionService;

  @Inject
  InfluxTimeseriesService influxTimeseriesService;

  @Inject
  InfluxDBConnector influxConnector;

  @Inject
  UserService userService;

  @Inject
  TimeseriesContainerDAO timeseriesContainerDAO;

  private final String userName = "user_name";

  private String databaseName;

  @BeforeAll
  public void setupUser() {
    User user = new User(userName);
    userService.createOrUpdateUser(user);
  }

  @BeforeEach
  public void setupDatabase() {
    databaseName = "Database-" + RandomGenerator.generateString(10);
  }

  @Test
  public void ingestData_ingestBooleanValues_success() {
    // arrange

    // act
    TimeseriesContainer container = dataIngestionService.ingestTestData(
      databaseName,
      5000,
      userName,
      DataPointValueType.Boolean
    );

    // assert

    try {
      InfluxTimeseries timeseries = influxConnector.getTimeseriesAvailable(databaseName).get(0);
      var influxTimeseriesDataType = influxConnector.getTimeseriesDataType(
        databaseName,
        timeseries.getMeasurement(),
        timeseries.getField()
      );
      assertEquals(influxTimeseriesDataType, InfluxTimeseriesDataType.BOOLEAN);
      var payload = influxTimeseriesService.getTimeseriesPayload(
        0,
        Instant.now().getEpochSecond() * 1_000_000_000,
        databaseName,
        timeseries,
        null,
        null,
        null
      );
      assertEquals(payload.getPoints().size(), 5000);
    } catch (Exception e) {
      fail("Exception should not have been thrown: " + e.getMessage());
    }

    // cleanup
    container.setDeleted(true);
    timeseriesContainerDAO.createOrUpdate(container);
    influxTimeseriesService.deleteDatabase(container.getDatabase());
  }

  @Test
  public void ingestData_ingestStringValues_success() {
    // arrange

    // act
    TimeseriesContainer container = dataIngestionService.ingestTestData(
      databaseName,
      5000,
      userName,
      DataPointValueType.String
    );

    // assert
    try {
      InfluxTimeseries timeseries = influxConnector.getTimeseriesAvailable(databaseName).get(0);
      var influxTimeseriesDataType = influxConnector.getTimeseriesDataType(
        databaseName,
        timeseries.getMeasurement(),
        timeseries.getField()
      );
      assertEquals(influxTimeseriesDataType, InfluxTimeseriesDataType.STRING);
      var payload = influxTimeseriesService.getTimeseriesPayload(
        0,
        Instant.now().getEpochSecond() * 1_000_000_000,
        databaseName,
        timeseries,
        null,
        null,
        null
      );
      assertEquals(payload.getPoints().size(), 5000);
    } catch (Exception e) {
      fail("Exception should not have been thrown: " + e.getMessage());
    }

    // cleanup
    container.setDeleted(true);
    timeseriesContainerDAO.createOrUpdate(container);
    influxTimeseriesService.deleteDatabase(container.getDatabase());
  }

  @Test
  public void ingestData_ingestDoubleValues_success() {
    // arrange

    // act
    TimeseriesContainer container = dataIngestionService.ingestTestData(
      databaseName,
      5000,
      userName,
      DataPointValueType.Double
    );

    // assert
    try {
      InfluxTimeseries timeseries = influxConnector.getTimeseriesAvailable(databaseName).get(0);
      var influxTimeseriesDataType = influxConnector.getTimeseriesDataType(
        databaseName,
        timeseries.getMeasurement(),
        timeseries.getField()
      );
      assertEquals(influxTimeseriesDataType, InfluxTimeseriesDataType.FLOAT);
      var payload = influxTimeseriesService.getTimeseriesPayload(
        0,
        Instant.now().getEpochSecond() * 1_000_000_000,
        databaseName,
        timeseries,
        null,
        null,
        null
      );
      assertEquals(payload.getPoints().size(), 5000);
    } catch (Exception e) {
      fail("Exception should not have been thrown: " + e.getMessage());
    }

    // cleanup
    container.setDeleted(true);
    timeseriesContainerDAO.createOrUpdate(container);
    influxTimeseriesService.deleteDatabase(container.getDatabase());
  }
}
