package de.dlr.shepard.data.timeseries.migration.services;

import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxDBConnector;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxSingleValuedUnaryFunction;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesService;
import de.dlr.shepard.data.timeseries.migration.model.MigrationState;
import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskEntity;
import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskState;
import de.dlr.shepard.data.timeseries.migration.repositories.MigrationTaskRepository;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

@RequestScoped
public class TimeseriesMigrationService {

  private TimeseriesContainerService timeseriesContainerService;
  private MigrationTaskRepository migrationTaskRepository;
  private InfluxDBConnector influxConnector;
  private InfluxTimeseriesService influxTimeseriesService;
  private TimeseriesDataPointRepository timeseriesDataPointRepository;

  private PayloadReader payloadReader;
  private PayloadWriter payloadWriter;
  private CompressionRunner compressionRunner;

  @Inject
  ManagedExecutor executor;

  int numberOfReaderThreads;

  int numberOfWriterThreads;

  @Getter
  private int numberOfPointsBeforeCompression;

  private final BlockingQueue<PayloadWriteTask> payloadWriteQueue;
  private final BlockingQueue<CompressionTask> compressionTasksQueue;
  private final Queue<PayloadReadTask> payloadReadQueue;
  private final ReentrantReadWriteLock readWriteLock;

  @Getter
  private final AtomicInteger insertionCount = new AtomicInteger();

  public Queue<PayloadReadTask> getPayloadReadQueue() {
    return payloadReadQueue;
  }

  public int getPayloadReadQueueSize() {
    synchronized (payloadReadQueue) {
      return payloadReadQueue.size();
    }
  }

  public BlockingQueue<CompressionTask> getCompressionTasksQueue() {
    return compressionTasksQueue;
  }

  public ReentrantReadWriteLock getReadWriteLock() {
    return readWriteLock;
  }

  public BlockingQueue<PayloadWriteTask> getPayloadWriteQueue() {
    return payloadWriteQueue;
  }

  @ConfigProperty(name = "shepard.migration-mode.timeseries-slice-duration", defaultValue = "600000000000")
  long sliceDuration;

  @Inject
  TimeseriesMigrationService(
    TimeseriesContainerService timeseriesContainerService,
    MigrationTaskRepository migrationTaskRepository,
    InfluxDBConnector influxConnector,
    InfluxTimeseriesService influxTimeseriesService,
    TimeseriesDataPointRepository timeseriesDataPointRepository,
    PayloadReader payloadReader,
    PayloadWriter payloadWriter,
    CompressionRunner compressionRunner,
    @ConfigProperty(
      name = "shepard.migration-mode.number-of-writer-threads",
      defaultValue = "6"
    ) int numberOfWriterThreads,
    @ConfigProperty(
      name = "shepard.migration-mode.number-of-reader-threads",
      defaultValue = "4"
    ) int numberOfReaderThreads,
    @ConfigProperty(
      name = "shepard.migration-mode.number-of-points-before-compression",
      defaultValue = "20000000"
    ) int numberOfPointsBeforeCompression
  ) {
    this.timeseriesContainerService = timeseriesContainerService;
    this.migrationTaskRepository = migrationTaskRepository;
    this.influxConnector = influxConnector;
    this.influxTimeseriesService = influxTimeseriesService;
    this.timeseriesDataPointRepository = timeseriesDataPointRepository;
    this.payloadReader = payloadReader;
    this.payloadWriter = payloadWriter;
    this.compressionRunner = compressionRunner;
    this.numberOfWriterThreads = numberOfWriterThreads;
    this.numberOfReaderThreads = numberOfReaderThreads;
    this.numberOfPointsBeforeCompression = numberOfPointsBeforeCompression;

    payloadWriteQueue = new LinkedBlockingQueue<>(numberOfWriterThreads + 1);
    compressionTasksQueue = new LinkedBlockingQueue<>();
    payloadReadQueue = new LinkedList<>();
    readWriteLock = new ReentrantReadWriteLock();
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

    Log.info("Starting migrations...");
    Log.info("To check the current migration state call the REST endpoint [/temp/migrations/state].");

    while (true) {
      var tasks = getUnfinishedMigrationTasks();
      if (tasks.isEmpty()) {
        Log.info("No migration tasks left. Migration finished.");
        break;
      }
      int cnt = 0;
      var size = tasks.size();

      for (var task : tasks) {
        cnt++;
        try {
          Log.infof("Starting migration task %s of %s (so far)", cnt, size);
          migrateTask(task);
          compressAllDataPoints();
        } catch (Exception ex) {
          Log.errorf("Exception occurred during migration of container %s: %s", task.getContainerId(), ex.getMessage());
          persistError(task, ex.getMessage());
        }
      }

      try {
        // Wait some time before checking for new migration tasks to give the system
        // some time to recover
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        Log.errorf("Thread interrupted: %s", e.getMessage());
      }
    }
  }

  @Transactional(value = TxType.REQUIRES_NEW)
  @TransactionConfiguration(
    timeoutFromConfigProperty = "shepard.migration-mode.compression.transaction-timeout",
    timeout = 6000
  )
  public void compressAllDataPoints() {
    Log.info("Starting compression of timeseries data point table...");
    timeseriesDataPointRepository.compressAllChunks();
    Log.info("Finished compression of timeseries data point table.");
    insertionCount.set(0);
  }

  protected void migrateTask(MigrationTaskEntity task) throws Exception {
    setStateToRunning(task);
    Log.infof("Start with migration of container %s now.", task.getContainerId());

    var container = timeseriesContainerService.getContainerNoChecks(task.getContainerId());

    var databaseName = task.getDatabaseName();
    if (doesDatabaseExist(databaseName) == false) {
      setStateToFinishedAndRemoveErrors(task);
      return;
    }

    InfluxTimeseries influxTimeseries = getTimeseries(task);
    if (influxTimeseries != null) {
      Log.infof(
        "Starting migration of timeseries %s for container %s",
        influxTimeseries.getUniqueId(),
        task.getContainerId()
      );
      var influxTimeseriesDataType = influxConnector.getTimeseriesDataType(
        databaseName,
        influxTimeseries.getMeasurement(),
        influxTimeseries.getField()
      );

      migratePayloads(container, databaseName, influxTimeseries, influxTimeseriesDataType);
    }

    setStateToFinishedAndRemoveErrors(task);
    Log.infof("Finished migration of container %s", task.getContainerId());
  }

  private InfluxTimeseries getTimeseries(MigrationTaskEntity migrationTaskEntity) {
    String timeseriesJson = migrationTaskEntity.getTimeseries();
    return JsonConverter.convertToObject(timeseriesJson, InfluxTimeseries.class);
  }

  private List<MigrationTaskEntity> getUnfinishedMigrationTasks() {
    return migrationTaskRepository.find("state <> 'Finished' OR errors <> ''").list();
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
   * Get ids of all containers from neo4j that are not deleted and do not have a
   * database name.
   * Hint: Containers that have a database name are Timeseries containers stored
   * in influxdb.
   * If the database prop is empty, it is a Timeseries container stored in
   * TimescaleDB.
   *
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
    return containerIds
      .stream()
      .flatMap(containerId -> {
        var container = timeseriesContainerService.getContainerNoChecks(containerId);

        List<MigrationTaskEntity> migrationTasks = new ArrayList<MigrationTaskEntity>();
        var databaseName = container.getDatabase();
        if (doesDatabaseExist(databaseName) == false) {
          Log.warnf("influxdb %s does not exist.", databaseName);
          var task = new MigrationTaskEntity(containerId);
          migrationTasks.add(task);
          return migrationTasks.stream();
        }

        var timeseriesAvailable = influxConnector.getTimeseriesAvailable(databaseName);
        if (timeseriesAvailable.size() == 0) {
          Log.warnf("No timeseries available for container %s", containerId);
          var task = new MigrationTaskEntity(containerId);
          task.setDatabaseName(databaseName);
          migrationTasks.add(task);
        }

        for (int i = 0; i < timeseriesAvailable.size(); i++) {
          MigrationTaskEntity migrationTaskEntity = new MigrationTaskEntity(containerId);
          InfluxTimeseries influxTimeseries = timeseriesAvailable.get(i);
          migrationTaskEntity.setTimeseries(JsonConverter.convertToString(influxTimeseries));
          migrationTaskEntity.setDatabaseName(databaseName);
          migrationTasks.add(migrationTaskEntity);
        }
        return migrationTasks.stream();
      })
      .toList();
  }

  private void storeTasksInDatabase(List<MigrationTaskEntity> tasks) {
    migrationTaskRepository.persist(tasks);
    Log.infof("We created %s migration tasks.", tasks.size());
  }

  private List<Long> getContainerIdsThatDoNotHaveMigrationTaskYet(List<Long> allContainerIds) {
    var alreadyHandledContainerIds = migrationTaskRepository
      .findAll()
      .stream()
      .map(t -> t.getContainerId())
      .distinct()
      .toList();
    var containerIdsLeft = allContainerIds.stream().filter(t -> !alreadyHandledContainerIds.contains(t)).toList();

    Log.infof(
      "We found %s containers that are already handled and %s containers that are not handled yet.",
      alreadyHandledContainerIds.size(),
      containerIdsLeft.size()
    );

    Log.info(String.join(", ", containerIdsLeft.stream().map(String::valueOf).toList()));
    return containerIdsLeft;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void setStateToRunning(MigrationTaskEntity task) {
    task.setStartedAt(new Date());
    task.setState(MigrationTaskState.Running);
    this.migrationTaskRepository.getEntityManager().merge(task);
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  protected void setStateToFinishedAndRemoveErrors(MigrationTaskEntity task) {
    task.setFinishedAt(new Date());
    task.setState(MigrationTaskState.Finished);
    task.setErrors(new ArrayList<>());
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
   * This method will try to do the copy in batches based on time based slices.
   * The slice duration is defined in env.
   * [shepard.migration-mode.timeseries-slice-duration]
   */
  private void migratePayloads(
    TimeseriesContainer container,
    String databaseName,
    InfluxTimeseries influxTimeseries,
    InfluxTimeseriesDataType influxTimeseriesDataType
  ) throws Exception {
    var firstTimestamp = getFirstTimestampOfPayload(databaseName, influxTimeseries);
    var lastTimestamp = getLastTimestampOfPayload(databaseName, influxTimeseries);
    Log.infof(
      "Doing migration from timestamp %s to %s of container %s",
      firstTimestamp,
      lastTimestamp,
      container.getId()
    );

    long currentStartTimestamp = firstTimestamp - 1;

    payloadReadQueue.clear();
    int runningNumber = 1;
    while (currentStartTimestamp < lastTimestamp) {
      long currentEndTimestamp = Math.min(currentStartTimestamp + sliceDuration, lastTimestamp);

      payloadReadQueue.add(
        new PayloadReadTask(
          runningNumber++,
          currentStartTimestamp,
          currentEndTimestamp,
          influxTimeseries,
          container,
          databaseName,
          influxTimeseriesDataType,
          false
        )
      );
      currentStartTimestamp = currentEndTimestamp;
    }

    for (int i = 0; i < numberOfReaderThreads; i++) {
      payloadReadQueue.add(PayloadReadTask.poisonPill);
    }
    Log.infof("Finished preparing read queue of %s read tasks.", payloadReadQueue.size());
    // Ensure tasks queue is empty
    payloadWriteQueue.clear();
    compressionTasksQueue.clear();

    List<Callable<Object>> tasks = new ArrayList<>();

    Log.debug("Creating writers...");
    for (int i = 0; i < numberOfWriterThreads; i++) {
      tasks.add(payloadWriter);
    }

    Log.debug("Creating readers...");
    for (int i = 0; i < numberOfReaderThreads; i++) {
      tasks.add(payloadReader);
    }

    tasks.add(compressionRunner);

    Log.infof(
      "Starting migration with %s reader threads and %s writer threads, compressing each %s points ...",
      numberOfReaderThreads,
      numberOfWriterThreads,
      numberOfPointsBeforeCompression
    );
    CompletionService<Object> completionService = new ExecutorCompletionService<>(executor);
    var plannedFutures = tasks.stream().map(completionService::submit).collect(Collectors.toCollection(ArrayList::new));
    try {
      for (int i = 0; i < tasks.size(); i++) {
        Future<Object> future = completionService.take();
        plannedFutures.remove(future);
        future.get();
      }
    } catch (InterruptedException | ExecutionException e) {
      // Cancel remaining futures
      for (var future : plannedFutures) {
        future.cancel(true);
      }

      Log.info("Waiting for executor task to terminate ...");
      if (!executor.awaitTermination(300, TimeUnit.SECONDS)) {
        Log.error("The started executor threads did not terminate within 300s. This can cause undefined behaviour.");
      }

      Log.errorf("Error while executing tasks in parallel.", e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      payloadWriteQueue.clear();
    }
  }

  void addWriterPoisonPills() {
    try {
      for (int i = 0; i < numberOfWriterThreads; i++) {
        getPayloadWriteQueue().put(PayloadWriteTask.poisonPill);
      }
    } catch (InterruptedException e) {
      Log.errorf("Payload write queue interrupted.", e.getMessage());
      Thread.currentThread().interrupt();
    }
  }

  void addCompressionPoisonPills() {
    try {
      // clear compression queue before sending the last task
      compressionTasksQueue.clear();
      compressionTasksQueue.put(new CompressionTask(true));
    } catch (InterruptedException e) {
      Log.errorf("Compression task queue interrupted.", e.getMessage());
      Thread.currentThread().interrupt();
    }
  }

  public void addCompressionTask() {
    try {
      // Do not add a task if the queue is not empty
      if (!compressionTasksQueue.isEmpty()) {
        Log.info("Did not add a duplicate compression task");
        return;
      }
      compressionTasksQueue.put(new CompressionTask(false));
    } catch (InterruptedException e) {
      Log.errorf("Compression task queue interrupted.", e.getMessage());
      Thread.currentThread().interrupt();
    }
  }
}
