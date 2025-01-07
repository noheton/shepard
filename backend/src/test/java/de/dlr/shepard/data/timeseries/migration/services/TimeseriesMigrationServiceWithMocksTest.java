package de.dlr.shepard.data.timeseries.migration.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.migration.model.MigrationState;
import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskEntity;
import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskState;
import de.dlr.shepard.data.timeseries.migration.repositories.MigrationTaskRepository;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.Mockito;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(MigrationModeTestProfile.class)
@Transactional
@ActivateRequestContext
public class TimeseriesMigrationServiceWithMocksTest extends BaseTestCase {

  private TimeseriesContainer container;

  @Inject
  TimeseriesMigrationService migrationService;

  @InjectMock
  TimeseriesContainerDAO timeseriesContainerDao;

  @InjectMock
  MigrationTaskRepository migrationTaskRepository;

  @Mock
  PanacheQuery<MigrationTaskEntity> query;

  @BeforeEach
  public void setup() {
    this.container = new TimeseriesContainer(1);
    this.container.setDatabase("InfluxDbName");

    Mockito.when(timeseriesContainerDao.findAll()).thenReturn(Arrays.asList(container));
    Mockito.when(migrationTaskRepository.findAll()).thenReturn(query);
    Mockito.when(migrationTaskRepository.find("state <> 'Finished'")).thenReturn(query);
    Mockito.when(migrationTaskRepository.find("state = 'Finished'")).thenReturn(query);
  }

  @Test
  public void getMigrationState_containersWithoutMigrationTask_returnsNeeded() {
    // arrange
    var entities = new MigrationTaskEntity[] {};

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.Needed, actual);
  }

  @Test
  public void getMigrationState_migrationTaskInRunningState_returnsHasErrors() {
    // arrange
    MigrationTaskEntity entity = new MigrationTaskEntity(container.getId());
    entity.setState(MigrationTaskState.Running);
    var entities = new MigrationTaskEntity[] { entity };

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));
    Mockito.when(query.count()).thenReturn(1l);
    Mockito.when(query.list()).thenReturn(Arrays.asList(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.HasErrors, actual);
  }

  @Test
  public void getMigrationState_migrationTaskInPlannedState_returnsHasErrors() {
    // arrange
    MigrationTaskEntity entity = new MigrationTaskEntity(container.getId());
    entity.setState(MigrationTaskState.Planned);
    var entities = new MigrationTaskEntity[] { entity };

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));
    Mockito.when(query.count()).thenReturn(1l);
    Mockito.when(query.list()).thenReturn(Arrays.asList(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.HasErrors, actual);
  }

  @Test
  public void getMigrationState_migrationTaskFinishedWithErrors_returnsHasErrors() {
    // arrange
    MigrationTaskEntity entity = new MigrationTaskEntity(container.getId());
    entity.setErrors(Arrays.asList("error message"));
    entity.setState(MigrationTaskState.Finished);
    var entities = new MigrationTaskEntity[] { entity };

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));
    Mockito.when(query.count()).thenReturn(1l);
    Mockito.when(query.list()).thenReturn(Arrays.asList(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.HasErrors, actual);
  }

  @Test
  public void getMigrationState_onlyContainersWithEmptyDatabaseName_returnsNotNeeded() {
    // arrange
    this.container.setDatabase(null);
    var entities = new MigrationTaskEntity[] {};

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));
    Mockito.when(query.count()).thenReturn(0l);
    Mockito.when(query.list()).thenReturn(Arrays.asList(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.NotNeeded, actual);
  }

  @Test
  public void getMigrationState_allMigrationTaskFinishedWithoutErrors_returnsNotNeeded() {
    // arrange
    MigrationTaskEntity entity = new MigrationTaskEntity(container.getId());
    entity.setState(MigrationTaskState.Finished);
    var entities = new MigrationTaskEntity[] { entity };

    Mockito.when(query.stream()).thenReturn(Arrays.stream(entities));
    Mockito.when(query.count()).thenReturn(0l);
    Mockito.when(query.list()).thenReturn(Arrays.asList(entities));

    // act
    var actual = migrationService.getMigrationState();

    // assert
    assertEquals(MigrationState.NotNeeded, actual);
  }
}
