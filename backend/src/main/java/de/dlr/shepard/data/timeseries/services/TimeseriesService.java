package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.timeseries.daos.TimeseriesDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.data.timeseries.utilities.TimeseriesValidator;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.microprofile.config.ConfigProvider;

@RequestScoped
public class TimeseriesService {

  @Inject
  TimeseriesDAO timeseriesDAO;

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
   * <p />
   * Returns an empty list if the timeseries container is not accessible (cannot
   * be found or wrong permissions).
   *
   * @param containerId of the given timeseries container
   * @return a list of timeseries entities
   */
  public List<Timeseries> getTimeseriesAvailable(long containerId) {
    timeseriesContainerService.getContainer(containerId);
    return timeseriesDAO.getAllTimeseriesInContainer(containerId);
  }

  /**
   * Returns a timeseries entity by its timeseries id.
   *
   * @param id timeseries id
   * @return timeseries
   * @throws NoSuchElementException if the timeseries does not exist
   * @throws InvalidAuthException if user has no read permissions on the timeseries container
   * @throws InvalidPathException if container with containerId or the timeseries are not accessible
   */
  public Timeseries getTimeseriesById(Long id)
    throws NoSuchElementException, InvalidAuthException, InvalidPathException {
    var timeseries = timeseriesDAO.findByTimeseriesId(id).orElseThrow();
    timeseriesContainerService.getContainer(timeseries.getContainer().getId());
    return timeseries;
  }

  /**
   * Deletes timeseries container by id
   *
   * @param containerId timeseries container id
   * @throws InvalidPathException if container could not be found
   * @throws InvalidAuthException if user has no edit permissions on container
   */
  @Transactional
  public void deleteTimeseriesByContainerId(long containerId) {
    timeseriesContainerService.getContainer(containerId);
    timeseriesContainerService.assertIsAllowedToDeleteContainer(containerId);
    timeseriesDAO.deleteAllTimeseriesInContainer(containerId);
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
    TimeseriesFiveTuple timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    return getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/
   * time slicing, filling and aggregating.
   * <p />
   * This function does not check if the container specified by containerId is
   * accessible.
   * We add <code>@ActivateRequestContext</code> in order to call this method in a
   * parallel stream.
   * The container check relies on an active request context.
   * However, the 'ActivateRequestContext' annotation does not allow for a
   * container check.
   *
   * @param containerId timeseries container id
   * @param timeseries 5-tuple identifying a timeseries
   * @param queryParams additional query parameters
   * @return List<TimeseriesDataPoint>
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseriesActivatedRequestContext(
    long containerId,
    TimeseriesFiveTuple timeseries,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<TimeseriesEntity> timeseriesEntity = this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    DataPointValueType valueType = timeseriesEntity.get().getValueType();

    return this.timeseriesDataPointRepository.queryDataPoints(timeseriesId, valueType, queryParams);
  }

  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<TimeseriesFiveTuple> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    timeseriesContainerService.getContainer(containerId);

    ConcurrentLinkedQueue<TimeseriesWithDataPoints> timeseriesWithDataPointsQueue = new ConcurrentLinkedQueue<>();
    timeseriesList
      .parallelStream()
      .forEach(timeseries ->
        timeseriesWithDataPointsQueue.add(
          new TimeseriesWithDataPoints(
            timeseries,
            getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
          )
        )
      );
    return new ArrayList<>(timeseriesWithDataPointsQueue);
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
  public Timeseries saveDataPoints(
    long timeseriesContainerId,
    TimeseriesFiveTuple timeseries,
    List<TimeseriesDataPoint> dataPoints
  ) {
    timeseriesContainerService.getContainer(timeseriesContainerId);
    timeseriesContainerService.assertIsAllowedToEditContainer(timeseriesContainerId);

    DataPointValueType incomingValueType = ObjectTypeEvaluator.determineType(
      dataPoints.getFirst().getValue()
    ).orElseThrow(InvalidBodyException::new);

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
  public Timeseries saveDataPoints(
    long timeseriesContainerId,
    TimeseriesFiveTuple timeseries,
    List<TimeseriesDataPoint> dataPoints,
    DataPointValueType dataType
  ) {
    var ts = getTimeseries(timeseriesContainerId, timeseries).orElseGet(() ->
      createTimeseries(timeseriesContainerId, timeseries, dataType)
    );
    assertDataPointsMatchTimeseriesValueType(ts.getValueType(), dataPoints);
    timeseriesDataPointRepository.insertManyDataPoints(dataPoints, ts.getTimeseriesId(), ts.getValueType());
    return ts;
  }

  @Deprecated
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  @TransactionConfiguration(timeout = 6000)
  public Timeseries repeatSaveDataPointsWithBatchInsert(
    List<TimeseriesDataPoint> entities,
    Timeseries timeseriesEntity
  ) {
    timeseriesDataPointRepository.insertManyDataPoints(
      entities,
      timeseriesEntity.getTimeseriesId(),
      timeseriesEntity.getValueType()
    );
    return timeseriesEntity;
  }

  public Optional<Timeseries> getTimeseries(long containerId, TimeseriesFiveTuple timeseries) {
    return timeseriesDAO.findTimeseries(containerId, timeseries);
  }

  private synchronized Timeseries createTimeseries(
    long containerId,
    TimeseriesFiveTuple timeseries,
    DataPointValueType incomingValueType
  ) {
    timeseriesContainerService.assertIsAllowedToEditContainer(containerId);
    TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    var container = timeseriesContainerService.getContainer(containerId);
    var tsToCreate = new Timeseries(
      timeseries.getMeasurement(),
      timeseries.getDevice(),
      timeseries.getLocation(),
      timeseries.getSymbolicName(),
      timeseries.getField(),
      incomingValueType,
      timeseriesDAO.getCurrentMaximumTimeseriesId() + 1,
      container
    );
    return timeseriesDAO.createOrUpdate(tsToCreate);
  }

  private void assertDataPointsMatchTimeseriesValueType(
    DataPointValueType valueType,
    List<TimeseriesDataPoint> dataPoints
  ) {
    for (TimeseriesDataPoint dataPoint : dataPoints) {
      DataPointValueType expectedType = ObjectTypeEvaluator.determineType(dataPoint.getValue()).orElseThrow(
        InvalidBodyException::new
      );
      assertValueTypeMatchesTimeseries(valueType, expectedType);
    }
  }

  private void assertValueTypeMatchesTimeseries(DataPointValueType tsValueType, DataPointValueType incomingValueType) {
    // If auto-conversion is enabled, allow transformation from Integer to Double
    if (
      autoConvertIntToDouble &&
      incomingValueType == DataPointValueType.Integer &&
      tsValueType == DataPointValueType.Double
    ) return;

    if (tsValueType != incomingValueType) throw new InvalidBodyException(
      "Timeseries already exists for data type %s but new data points are of type %s",
      tsValueType,
      incomingValueType
    );
  }
}
