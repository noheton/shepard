package de.dlr.shepard.timeseries.migration.services;

import static de.dlr.shepard.util.InfluxDataMapper.mapToTimeseries;
import static de.dlr.shepard.util.InfluxDataMapper.mapToTimeseriesDataPoints;
import static de.dlr.shepard.util.InfluxDataMapper.mapToValueType;

import de.dlr.shepard.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.influxtimeseries.InfluxSingleValuedUnaryFunction;
import de.dlr.shepard.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesPayload;
import de.dlr.shepard.influxtimeseries.InfluxTimeseriesService;
import de.dlr.shepard.timeseries.migration.model.MigrationState;
import de.dlr.shepard.timeseries.migration.model.MigrationTaskEntity;
import de.dlr.shepard.timeseries.migration.model.MigrationTaskState;
import de.dlr.shepard.timeseries.migration.repositories.MigrationTaskRepository;
import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.timeseries.services.TimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@RequestScoped
public class TimeseriesMigrationService {

  private TimeseriesContainerService timeseriesContainerService;
  private MigrationTaskRepository migrationTaskRepository;
  private InfluxDBConnector influxConnector;
  private InfluxTimeseriesService influxTimeseriesService;
  private TimeseriesService timeseriesService;

  @ConfigProperty(name = "shepard.migration-mode.timeseries-slice-duration", defaultValue = "60000000000")
  long sliceDuration;

  @Inject
  TimeseriesMigrationService(
    TimeseriesContainerService timeseriesContainerService,
    MigrationTaskRepository migrationTaskRepository,
    InfluxDBConnector influxConnector,
    TimeseriesService timeseriesService,
    InfluxTimeseriesService influxTimeseriesService
  ) {
    this.timeseriesContainerService = timeseriesContainerService;
    this.migrationTaskRepository = migrationTaskRepository;
    this.influxConnector = influxConnector;
    this.timeseriesService = timeseriesService;
    this.influxTimeseriesService = influxTimeseriesService;
  }

  public List<MigrationTaskEntity> getMigrationTasks(boolean onlyShowErrors) {
    var tasks = migrationTaskRepository.findAll().list();
    if (onlyShowErrors) tasks = tasks.stream().filter(t -> t.getErrors().size() > 0).toList();
    return tasks;
  }

  public MigrationState getMigrationState() {
    var containerIds = getExistingContainerIds();
    var containerIdsToMigrate = getContainerIdsThatDoNotHaveMigrationTaskYet(containerIds);
    if (containerIdsToMigrate.size() > 0) {
      Log.infof(
        "Migration is necessary because there are %s containers that do not have a MigrationTask yet.",
        containerIdsToMigrate.size()
      );
      return MigrationState.Needed;
    }

    var notFinishedState = migrationTaskRepository.find("state <> 'Finished'").count();
    if (notFinishedState > 0) {
      Log.infof(
        "Migration is necessary because there are %s MigrationTasks that do not have state 'Finished'.",
        notFinishedState
      );
      return MigrationState.HasErrors;
    }

    var finishedState = migrationTaskRepository.find("state = 'Finished'").list();
    var finishedWithErrors = finishedState.stream().filter(t -> t.getErrors().size() > 0).toList();
    if (finishedWithErrors.size() > 0) {
      Log.infof(
        "Migration necessary because there are %s MigrationTasks that have an error.",
        finishedWithErrors.size()
      );
      return MigrationState.HasErrors;
    }

    return MigrationState.NotNeeded;
  }

  public void runMigrations() {
    createMigrationTaskForEachContainer();
    var tasks = getPlannedMigrationTasks();
    Log.info("Starting migrations...");
    Log.info("To check the current migration state call the REST endpoint [/temp/migrations/state].");
    for (var task : tasks) {
      try {
        migrateTask(task);
      } catch (Exception ex) {
        Log.errorf("Exception occurred during migration of container %s: %s", task.getContainerId(), ex.getMessage());
        persistError(task, ex.getMessage());
      }
    }
  }

  protected void migrateTask(MigrationTaskEntity task) throws Exception {
    setStateToRunning(task);

    var container = timeseriesContainerService.getContainer(task.getContainerId());

    var databaseName = container.getDatabase();
    if (doesDatabaseExist(databaseName) == false) {
      setStateToFinished(task);
      return;
    }

    var timeseriesAvailable = influxConnector.getTimeseriesAvailable(databaseName);

    for (InfluxTimeseries influxTimeseries : timeseriesAvailable) {
      var influxTimeseriesDataType = influxConnector.getTimeseriesDataType(
        databaseName,
        influxTimeseries.getMeasurement(),
        influxTimeseries.getField()
      );
      migratePayloads(container, databaseName, influxTimeseries, influxTimeseriesDataType);
    }

    setStateToFinished(task);
  }

  private List<MigrationTaskEntity> getPlannedMigrationTasks() {
    return migrationTaskRepository.find("where state = 'Planned'").list();
  }

  /**
   * Get all containers in neo4j that are not deleted.
   * Create a MigrationTask for each container that does not already have one.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void createMigrationTaskForEachContainer() {
    var containerIds = getExistingContainerIds();
    var containerIdsToMigrate = getContainerIdsThatDoNotHaveMigrationTaskYet(containerIds);
    var tasks = createMigrationTasksForContainers(containerIdsToMigrate);
    storeTasksInDatabase(tasks);
  }

  /**
   * Get ids of all containers from neo4j that are not deleted and do not have a database name.
   * Hint: Containers that have a database name are Timeseries containers stored in influxdb.
   * If the database prop is empty, it is a Timeseries container stored in TimescaleDB.
   * @return
   */
  private List<Long> getExistingContainerIds() {
    var existingContainerIds = timeseriesContainerService
      .getContainers()
      .stream()
      .filter(c -> StringUtils.isNotEmpty(c.getDatabase()))
      .map(c -> c.getId())
      .toList();

    Log.infof(
      "We found %s containers in neo4j that are not deleted and have a database prop that is not empty.",
      existingContainerIds.size()
    );
    return existingContainerIds;
  }

  private List<MigrationTaskEntity> createMigrationTasksForContainers(List<Long> containerIds) {
    return containerIds.stream().map(id -> new MigrationTaskEntity(id)).toList();
  }

  private void storeTasksInDatabase(List<MigrationTaskEntity> tasks) {
    migrationTaskRepository.persist(tasks);
    Log.infof("We created %s migration tasks.", tasks.size());
  }

  private List<Long> getContainerIdsThatDoNotHaveMigrationTaskYet(List<Long> allContainerIds) {
    var alreadyHandledContainerIds = migrationTaskRepository.findAll().stream().map(t -> t.getContainerId()).toList();
    var containerIdsLeft = allContainerIds.stream().filter(t -> !alreadyHandledContainerIds.contains(t)).toList();

    Log.infof(
      "We found %s containers that are already handled and %s containers that are not handled yet.",
      alreadyHandledContainerIds.size(),
      containerIdsLeft.size()
    );
    return containerIdsLeft;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void setStateToRunning(MigrationTaskEntity task) {
    task.setStartedAt(new Date());
    task.setState(MigrationTaskState.Running);
    this.migrationTaskRepository.getEntityManager().merge(task);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void setStateToFinished(MigrationTaskEntity task) {
    task.setFinishedAt(new Date());
    task.setState(MigrationTaskState.Finished);
    this.migrationTaskRepository.getEntityManager().merge(task);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void persistError(MigrationTaskEntity task, String errorMessage) {
    task.addError(errorMessage);
    task.setState(MigrationTaskState.Finished);
    task.setFinishedAt(new Date());
    this.migrationTaskRepository.getEntityManager().merge(task);
  }

  /**
   * Throws an error if the database with the given name does not exist.
   */
  private boolean doesDatabaseExist(String databaseName) {
    if (influxConnector.databaseExist(databaseName) == false) {
      Log.warnf(
        "InfluxDB with name %s does not exist. Migration not possible. We do not treat that as an error.",
        databaseName
      );
      return false;
    }
    return true;
  }

  /**
   * Identifies the first record of the timeseries and returns that timestamp.
   */
  private long getFirstTimestampOfPayload(String database, InfluxTimeseries influxTimeseries) {
    return getTimestampOfPayload(database, influxTimeseries, InfluxSingleValuedUnaryFunction.FIRST);
  }

  /**
   * Identifies the last record of the timeseries and returns that timestamp.
   */
  private long getLastTimestampOfPayload(String database, InfluxTimeseries influxTimeseries) {
    return getTimestampOfPayload(database, influxTimeseries, InfluxSingleValuedUnaryFunction.LAST);
  }

  private long getTimestampOfPayload(
    String database,
    InfluxTimeseries influxTimeseries,
    InfluxSingleValuedUnaryFunction function
  ) {
    var payload =
      this.influxTimeseriesService.getTimeseriesPayload(
          0,
          Instant.now().getEpochSecond() * 1_000_000_000,
          database,
          influxTimeseries,
          function,
          null,
          null
        );
    return (payload.getPoints().size() > 0) ? payload.getPoints().get(0).getTimeInNanoseconds() : 0;
  }

  /**
   * Copy all payloads from a InfluxTimeseries to the TimeseriesContainer.
   * This method will try to do the copy in batches based on time based slices. The slice duration is defined in env. [shepard.migration-mode.timeseries-slice-duration]
   */
  private void migratePayloads(
    TimeseriesContainer container,
    String databaseName,
    InfluxTimeseries influxTimeseries,
    InfluxTimeseriesDataType influxTimeseriesDataType
  ) {
    var firstTimestamp = getFirstTimestampOfPayload(databaseName, influxTimeseries);
    var lastTimestamp = getLastTimestampOfPayload(databaseName, influxTimeseries);

    long currentStartTimestamp = firstTimestamp - 1;

    while (currentStartTimestamp < lastTimestamp) {
      long currentEndTimestamp = Math.min(currentStartTimestamp + sliceDuration, lastTimestamp);

      var payload =
        this.influxTimeseriesService.getTimeseriesPayload(
            currentStartTimestamp,
            currentEndTimestamp,
            databaseName,
            influxTimeseries,
            null,
            null,
            null
          );

      saveDataPoints(container, influxTimeseries, influxTimeseriesDataType, payload);
      currentStartTimestamp = currentEndTimestamp;
    }
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void saveDataPoints(
    TimeseriesContainer container,
    InfluxTimeseries influxTimeseries,
    InfluxTimeseriesDataType influxTimeseriesDataType,
    InfluxTimeseriesPayload payload
  ) {
    timeseriesService.saveDataPoints(
      container.getId(),
      mapToTimeseries(influxTimeseries),
      mapToTimeseriesDataPoints(payload.getPoints()),
      mapToValueType(influxTimeseriesDataType)
    );
  }
}
