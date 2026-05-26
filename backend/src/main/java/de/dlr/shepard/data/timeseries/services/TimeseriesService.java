package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;

@RequestScoped
public class TimeseriesService {

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  /**
   * Flag to determine whether integer values received should be automatically converted to double if the
   * timeseries they are supposed to be inserted is of type double.
   * This flag is not injected using @ConfigProperty because that would make testing much more complicated.
   * These properties are set upon startup and cannot be changed within a single test.
   */
  Boolean autoConvertIntToDouble = ConfigProvider.getConfig()
    .getOptionalValue("shepard.autoconvert-int", Boolean.class)
    .orElse(false);

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * Returns an empty list if the timeseries container is not accessible (cannot
   * be found or wrong permissions).
   *
   * @param containerId of the given timeseries container
   * @return a list of timeseries entities
   */
  public List<TimeseriesEntity> getTimeseriesAvailable(long containerId) {
    timeseriesContainerService.getContainer(containerId);

    return timeseriesRepository.list("containerId", containerId);
  }

  /**
   * Returns a timeseries entity by id
   *
   * @param containerId timeseries container id
   * @param id
   * @return TimeseriesEntity
   * @throws InvalidPathException if container with containerId or the timeseries
   *                              are not accessible
   * @throws InvalidAuthException if user has no read permissions on the
   *                              timeseries container
   */
  public TimeseriesEntity getTimeseriesById(long containerId, int id) {
    timeseriesContainerService.getContainer(containerId);

    var timeseries = timeseriesRepository.findById(id);
    if (timeseries == null) {
      String errorMsg =
        "ID ERROR - Timeseries with id %s in container %s is null or deleted".formatted(id, containerId);
      Log.error(errorMsg);
      throw new InvalidPathException(errorMsg);
    }
    return timeseries;
  }

  /**
   * Returns a timeseries entity
   *
   * @param containerId timeseries container id
   * @param timeseries
   * @return TimeseriesEntity
   * @throws NotFoundException if the timeseries is not found
   * @throws InvalidPathException if container with containerId is not accessible
   * @throws InvalidAuthException if user has no read permissions on the timeseries container
   */
  public TimeseriesEntity getTimeseries(long containerId, Timeseries timeseries) {
    timeseriesContainerService.getContainer(containerId);

    var timeseriesEntity = timeseriesRepository.findTimeseries(containerId, timeseries);
    if (timeseriesEntity.isEmpty()) {
      String errorMsg =
        "Timeseries (%s, %s, %s, %s, %s) in container %s is null or deleted".formatted(
            timeseries.getMeasurement(),
            timeseries.getDevice(),
            timeseries.getLocation(),
            timeseries.getSymbolicName(),
            timeseries.getField(),
            containerId
          );
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    return timeseriesEntity.get();
  }

  /**
   * Deletes timeseries container by id
   *
   * @param containerId
   * @throws InvalidPathException if container could not be found
   * @throws InvalidAuthException if user has no edit permissions on container
   */
  @Transactional
  public void deleteTimeseriesByContainerId(long containerId) {
    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToDeleteContainer(containerId);
    this.timeseriesRepository.deleteByContainerId(containerId);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   *
   * @return List of TimeseriesDataPoint
   * @throws InvalidPathException if container is null or deleted
   * @throws InvalidAuthException if user has no read permissions on container
   */
  public List<TimeseriesDataPoint> getDataPointsByTimeseries(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    return getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   *
   * This function does not check if the container specified by containerId is
   * accessible.
   * We add <code>@ActivateRequestContext</code> in order to call this method in a
   * parallel stream.
   * The container check relies on an active request context.
   * However, the 'ActivateRequestContext' annotation does not allow for a
   * container check.
   *
   * @param containerId
   * @param timeseries
   * @param queryParams
   * @return List<TimeseriesDataPoint>
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseriesActivatedRequestContext(
    long containerId,
    Timeseries timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<TimeseriesEntity> timeseriesEntity = this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    DataPointValueType valueType = timeseriesEntity.get().getValueType();

    return this.timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, queryParams);
  }

  /**
   * Fetch channel data with LTTB-optimised query routing (TS-OPT1).
   *
   * <p>For large windows (bucket ≥ 1 ms after dividing window by target×5),
   * issues a SQL {@code time_bucket()} pre-aggregation query that returns only
   * {@code target*5} rows, then applies LTTB in Java on that reduced set.
   * For small windows or non-numeric types, falls back to raw point fetch + LTTB.
   *
   * <p>Result: DB→Java transfer is reduced by up to rawRowCount/numBuckets
   * (e.g. 360× for a 1-hour 100 Hz channel with target=200).
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsLttbOptimised(
    long containerId,
    Timeseries timeseries,
    long startNs,
    long endNs,
    int lttbTarget
  ) {
    Optional<TimeseriesEntity> entityOpt = timeseriesRepository.findTimeseries(containerId, timeseries);
    if (entityOpt.isEmpty()) return Collections.emptyList();

    TimeseriesEntity entity = entityOpt.get();
    int tsId = entity.getId();
    DataPointValueType valueType = entity.getValueType();

    long bucketNs = TimeseriesDataPointRepository.choosePreaggBucketNs(endNs - startNs, lttbTarget);
    List<TimeseriesDataPoint> points;

    if (bucketNs > 0 && (valueType == DataPointValueType.Double || valueType == DataPointValueType.Integer)) {
      Log.debugf("TS-OPT1 preagg: tsId=%d window=%dms bucketMs=%d target=%d",
        tsId, (endNs - startNs) / 1_000_000, bucketNs / 1_000_000, lttbTarget);
      points = timeseriesDataPointRepository.queryPreAggregated(tsId, valueType, startNs, endNs, bucketNs);
    } else {
      var queryParams = new TimeseriesDataPointsQueryParams(startNs, endNs, null, null, null);
      points = timeseriesDataPointRepository.queryDataPoints(tsId, valueType, queryParams);
    }

    return de.dlr.shepard.data.timeseries.util.Lttb.downsample(points, lttbTarget);
  }

  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);
    return timeseriesList.stream()
      .map(timeseries -> new TimeseriesWithDataPoints(
        timeseries,
        getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
      ))
      .collect(Collectors.toList());
  }

  /**
   * Fetch data points for a list of already-resolved channel entities.
   * Skips the per-channel {@code findTimeseries} lookup — callers that
   * already hold {@link TimeseriesEntity} objects (e.g. after a single
   * bulk ID-resolution query) should prefer this over
   * {@link #getManyTimeseriesWithDataPoints} to avoid a redundant round-trip.
   */
  public List<TimeseriesWithDataPoints> getManyDataPointsByEntities(
    long containerId,
    List<TimeseriesEntity> entities,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);
    return entities.stream()
      .map(entity -> new TimeseriesWithDataPoints(
        new Timeseries(entity),
        timeseriesDataPointRepository.queryDataPoints(entity.getId(), entity.getValueType(), queryParams)
      ))
      .collect(Collectors.toList());
  }

  /**
   * Saves data points in the database.
   * If the corresponding timeseries did not exist before, it will be persisted in
   * the database.
   *
   * @param timeseriesContainerId Identifies the TimeseriesContainer
   * @param timeseries            The timeseries identifiers
   * @param dataPoints            Data points to be added to the timeseries
   * @return created timeseries
   */
  public TimeseriesEntity saveDataPoints(
    long timeseriesContainerId,
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints
  ) {
    timeseriesContainerService.getContainer(timeseriesContainerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(timeseriesContainerId);

    // Fix B: scan first few points instead of blindly trusting the first.
    // A glitch first-point (null, NaN, "NaN" string) would otherwise
    // lock the channel to the wrong type for all future ingest.
    DataPointValueType incomingValueType = inferValueType(dataPoints);

    return saveDataPoints(timeseriesContainerId, timeseries, dataPoints, incomingValueType);
  }

  /**
   * Saves data points in the database.
   * If the corresponding timeseries did not exist before, it will be persisted in
   * the database.
   *
   * @param timeseriesContainerId Identifies the TimeseriesContainer
   * @param timeseries            The timeseries identifiers
   * @param dataPoints            Data points to be added to the timeseries
   * @param dataType              The data type that values in this timeseries
   *                              will have
   * @return created timeseries
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  @TransactionConfiguration(timeout = 6000)
  public TimeseriesEntity saveDataPoints(
    long timeseriesContainerId,
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints,
    DataPointValueType dataType
  ) {
    timeseriesContainerService.getContainer(timeseriesContainerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(timeseriesContainerId);

    TimeseriesEntity timeseriesEntity = getOrCreateTimeseries(timeseriesContainerId, timeseries, dataType);

    assertDataPointsMatchTimeseriesValueType(timeseriesEntity, dataPoints);

    timeseriesDataPointRepository.insertManyDataPoints(dataPoints, timeseriesEntity);

    return timeseriesEntity;
  }

  @Deprecated
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  @TransactionConfiguration(timeout = 6000)
  public TimeseriesEntity repeatSaveDataPointsWithBatchInsert(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity
  ) {
    timeseriesDataPointRepository.insertManyDataPoints(entities, timeseriesEntity);
    return timeseriesEntity;
  }

  private TimeseriesEntity getOrCreateTimeseries(
    long containerId,
    Timeseries timeseries,
    DataPointValueType incomingValueType
  ) {
    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);

    // try to find timeseries in db
    Optional<TimeseriesEntity> matchingTimeseries = timeseriesRepository.findTimeseries(containerId, timeseries);

    if (matchingTimeseries.isPresent()) return matchingTimeseries.get();

    TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);

    // create new timeseries because it does not exist
    TimeseriesEntity timeseriesEntity = new TimeseriesEntity(containerId, timeseries, incomingValueType);
    QuarkusTransaction.requiringNew()
      .run(() -> {
        this.timeseriesRepository.upsert(containerId, timeseriesEntity);
      });

    var found = this.timeseriesRepository.findTimeseries(containerId, timeseries);
    return found.get();
  }

  /** Fix B: look at up to 5 leading points to infer type, skipping null / non-finite values. */
  private DataPointValueType inferValueType(List<TimeseriesDataPoint> dataPoints) {
    int scanned = 0;
    for (TimeseriesDataPoint dp : dataPoints) {
      Optional<DataPointValueType> t = ObjectTypeEvaluator.determineType(dp.getValue());
      if (t.isPresent()) return t.get();
      if (++scanned >= 5) break;
    }
    throw new InvalidBodyException("Could not determine value type from the first data points (all null or non-finite)");
  }

  private void assertDataPointsMatchTimeseriesValueType(
    TimeseriesEntity timeseriesEntity,
    List<TimeseriesDataPoint> dataPoints
  ) {
    for (TimeseriesDataPoint dataPoint : dataPoints) {
      Optional<DataPointValueType> typeOpt = ObjectTypeEvaluator.determineType(dataPoint.getValue());
      // Fix D: null / NaN / Infinity values produce an empty Optional — skip them
      // rather than rejecting the whole batch. They will not be inserted.
      if (typeOpt.isEmpty()) continue;
      assertValueTypeMatchesTimeseries(timeseriesEntity, typeOpt.get());
    }
  }

  private void assertValueTypeMatchesTimeseries(TimeseriesEntity timeseries, DataPointValueType incomingValueType) {
    // If auto-conversion is enabled, allow transformation from Integer to Double
    if (
      autoConvertIntToDouble &&
      incomingValueType == DataPointValueType.Integer &&
      timeseries.getValueType() == DataPointValueType.Double
    ) return;

    if (timeseries.getValueType() != incomingValueType) throw new InvalidBodyException(
      "Timeseries already exists for data type %s but new data points are of type %s",
      timeseries.getValueType(),
      incomingValueType
    );
  }
}
