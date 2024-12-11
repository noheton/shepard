package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.timeseries.utilities.TimeseriesValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

  public void deleteTimeseriesByContainerId(long containerId) {
    timeseriesRepository.delete("containerId", containerId);
  }

  /**
   * Retrieve a list of DataPoints for a time-interval with options to grouping/ time slicing, filling and aggregating.
   *
   * @return List of TimeseriesDataPoint
   */
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
    TimeseriesContainer timeseriesContainer,
    Timeseries timeseries,
    List<TimeseriesDataPoint> dataPoints
  ) {
    DataPointValueType incomingValueType = ObjectTypeEvaluator.determineType(dataPoints.get(0).getValue()).orElseThrow(
      () -> new InvalidBodyException()
    );

    TimeseriesEntity timeseriesEntity = getOrCreateTimeseries(
      timeseriesContainer.getId(),
      timeseries,
      incomingValueType
    );

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
    this.timeseriesRepository.persist(timeseriesEntity);
    return timeseriesEntity;
  }

  private static void assertDataPointsMatchTimeseriesValueType(
    TimeseriesEntity timeseriesEntity,
    List<TimeseriesDataPoint> dataPoints
  ) {
    for (var dataPoint : dataPoints) {
      var expectedType = ObjectTypeEvaluator.determineType(dataPoint.getValue()).orElseThrow(() ->
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
