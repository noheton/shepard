package de.dlr.shepard.data.timeseries.migration.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.RandomGenerator;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTestDataGenerator;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * This test is a separate class next to TimeseriesMigrationServiceTest.java since it requires the timeseriesService to be mocked
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(MigrationModeTestProfile.class)
@ActivateRequestContext
public class TimeseriesMigrationServiceBatchingTest {

  @ConfigProperty(name = "shepard.migration-mode.timeseries-slice-duration", defaultValue = "60000000000")
  long sliceDuration;

  @Inject
  TimeseriesMigrationService migrationService;

  @Inject
  InfluxDBConnector influxConnector;

  @InjectSpy
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesContainerDAO timeseriesContainerDao;

  @InjectMock
  UserService userService;

  @InjectMock
  AuthenticationContext authenticationContext;

  private TimeseriesContainer container;

  private final User user = new User("Testuser");

  @BeforeEach
  public void setup() {
    var databaseName = "Database-" + RandomGenerator.generateString(10);
    var containerName = "Container-" + RandomGenerator.generateString(10);
    TimeseriesContainer entity = new TimeseriesContainer();
    entity.setDatabase(databaseName);
    entity.setName(containerName);
    container = timeseriesContainerDao.createOrUpdate(entity);
    influxConnector.createDatabase(databaseName);

    when(userService.getCurrentUser()).thenReturn(user);
    when(authenticationContext.getCurrentUserName()).thenReturn(user.getUsername());
  }

  @Test
  public void startMigrations_migrateDatabaseWithDataOfTwoBatches_shouldDoThreeBatches() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint((sliceDuration) + 1000, 1)
      .addInfluxPoint((sliceDuration * 2) + 2200, 2)
      .addInfluxPoint((sliceDuration * 2) + 3300, 3)
      .addInfluxPoint((sliceDuration * 3) + 3400, 4)
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);
    // act
    migrationService.runMigrations();
    // assert
    // The above data should be migrated on three batches, two of one DataPoint, and one of two DataPoints
    verify(timeseriesService, times(2)).saveDataPoints(
      anyLong(),
      any(),
      argThat(list -> list != null && list.size() == 1),
      any()
    );
    verify(timeseriesService, times(1)).saveDataPoints(
      anyLong(),
      any(),
      argThat(list -> list != null && list.size() == 2),
      any()
    );
  }

  @Test
  public void startMigrations_migrateDatabaseWithDataOfSingleBatch_shouldDoSingleBatch() {
    // arrange
    var payload = new InfluxTestDataGenerator()
      .addInfluxPoint((sliceDuration) + 1000, 1)
      .addInfluxPoint((sliceDuration) + 2200, 2)
      .addInfluxPoint((sliceDuration) + 3300, 3)
      .addInfluxPoint((sliceDuration) + 3400, 4)
      .buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);
    // act
    migrationService.runMigrations();
    // assert
    // The above data are inserted in one time slice, they should go all in single batch
    verify(timeseriesService, times(1)).saveDataPoints(
      anyLong(),
      any(),
      argThat(list -> list != null && list.size() == 4),
      any()
    );
  }

  @Test
  public void startMigrations_migrateDatabaseWithoutAnyData_ShouldNotSaveData() {
    // arrange
    var payload = new InfluxTestDataGenerator().buildPayload();
    influxConnector.saveTimeseriesPayload(container.getDatabase(), payload);
    // act
    migrationService.runMigrations();
    // assert
    verify(timeseriesService, times(0)).saveDataPoints(anyLong(), any(), any(), any());
  }
}
