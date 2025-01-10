package de.dlr.shepard.data.timeseries.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
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
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

@RequestScoped
public class TimeseriesService {

  private TimeseriesRepository timeseriesRepository;
  private TimeseriesDataPointRepository timeseriesDataPointRepository;

  TimeseriesService() {}

  @Inject
  public TimeseriesService(
    TimeseriesRepository timeseriesRepository,
    TimeseriesDataPointRepository timeseriesDataPointRepository
  ) {
    this.timeseriesRepository = timeseriesRepository;
    this.timeseriesDataPointRepository = timeseriesDataPointRepository;
  }

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param containerId of the given timeseries container
   * @return a list of timeseries entities
   */
  public List<TimeseriesEntity> getTimeseriesAvailable(long containerId) {
    return timeseriesRepository.list("containerId", containerId);
  }

  @Transactional
  public void deleteTimeseriesByContainerId(long containerId) {
    this.timeseriesRepository.deleteByContainerId(containerId);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/ time slicing, filling and aggregating.
   *
   * We add <code>@ActivateRequestContext</code> in order to call this method in a parallel stream.
   *
   * @return List of TimeseriesDataPoint
   */
  @ActivateRequestContext
  public List<TimeseriesDataPoint> getDataPointsByTimeseries(
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

  public List<TimeseriesWithDataPoints> getManyTimeseriesWithDataPoints(
    Long containerId,
    List<Timeseries> timeseriesList,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    ConcurrentLinkedQueue<TimeseriesWithDataPoints> timeseriesWithDataPointsQueue = new ConcurrentLinkedQueue<
      TimeseriesWithDataPoints
    >();
    timeseriesList
      .parallelStream()
      .forEach(timeseries -> {
        timeseriesWithDataPointsQueue.add(
          new TimeseriesWithDataPoints(timeseries, getDataPointsByTimeseries(containerId, timeseries, queryParams))
        );
      });
    return new ArrayList<TimeseriesWithDataPoints>(timeseriesWithDataPointsQueue);
  }

  /**
   * Saves data points in the database.
   * If the corresponding timeseries did not exist before, it will be persisted in the database.
   *
   * @param timeseriesContainerId    Identifies the TimeseriesContainer
   * @param timeseries               The timeseries identifiers
   * @param dataPoints               Data points to be added to the timeseries
   * @return created timeseries
   */
  public TimeseriesEntity saveDataPoints(
    long timeseriesContainerId,
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints
  ) {
    DataPointValueType incomingValueType = ObjectTypeEvaluator.determineType(dataPoints.get(0).getValue()).orElseThrow(
      () -> new InvalidBodyException()
    );

    return saveDataPoints(timeseriesContainerId, timeseries, dataPoints, incomingValueType);
  }

  /**
   * Saves data points in the database.
   * If the corresponding timeseries did not exist before, it will be persisted in the database.
   *
   * @param timeseriesContainerId    Identifies the TimeseriesContainer
   * @param timeseries               The timeseries identifiers
   * @param dataPoints               Data points to be added to the timeseries
   * @param dataType                 The data type that values in this timeseries will have
   * @return created timeseries
   */
  @Transactional
  public TimeseriesEntity saveDataPoints(
    long timeseriesContainerId,
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints,
    DataPointValueType dataType
  ) {
    TimeseriesEntity timeseriesEntity = getOrCreateTimeseries(timeseriesContainerId, timeseries, dataType);

    assertDataPointsMatchTimeseriesValueType(timeseriesEntity, dataPoints);

    timeseriesDataPointRepository.insertManyDataPoints(dataPoints, timeseriesEntity);

    return timeseriesEntity;
  }

  private TimeseriesEntity getOrCreateTimeseries(
    long containerId,
    Timeseries timeseries,
    DataPointValueType incomingValueType
  ) {
    // try to find timeseries in db
    Optional<TimeseriesEntity> matchingTimeseries = timeseriesRepository.findTimeseries(containerId, timeseries);

    if (matchingTimeseries.isPresent()) return matchingTimeseries.get();

    TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);

    // create new timeseries because it does not exist
    TimeseriesEntity timeseriesEntity = new TimeseriesEntity(containerId, timeseries, incomingValueType);
    this.timeseriesRepository.upsert(containerId, timeseriesEntity);
    var found = this.timeseriesRepository.findTimeseries(containerId, timeseries);
    return found.get();
  }

  private static void assertDataPointsMatchTimeseriesValueType(
    TimeseriesEntity timeseriesEntity,
    List<TimeseriesDataPoint> dataPoints
  ) {
    for (TimeseriesDataPoint dataPoint : dataPoints) {
      DataPointValueType expectedType = ObjectTypeEvaluator.determineType(dataPoint.getValue()).orElseThrow(() ->
        new InvalidBodyException()
      );
      assertValueTypeMatchesTimeseries(timeseriesEntity, expectedType);
    }
  }

  private static void assertValueTypeMatchesTimeseries(
    TimeseriesEntity timeseries,
    DataPointValueType incomingValueType
  ) {
    if (timeseries.getValueType() != incomingValueType) throw new InvalidBodyException(
      "Timeseries already exists for data type %s but new data points are of type %s",
      timeseries.getValueType(),
      incomingValueType
    );
  }
}
