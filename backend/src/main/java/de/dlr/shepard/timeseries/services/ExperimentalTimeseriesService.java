package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import de.dlr.shepard.timeseries.model.enums.ExperimentalDataPointValueType;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesDataPointRepository;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesRepository;
import de.dlr.shepard.timeseries.utilities.ObjectTypeEvaluator;
import de.dlr.shepard.timeseries.utilities.TimeseriesValidator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequestScoped
public class ExperimentalTimeseriesService {

  private ExperimentalTimeseriesRepository timeseriesRepository;
  private ExperimentalTimeseriesDataPointRepository timeseriesDataPointRepository;

  ExperimentalTimeseriesService() {}

  @Inject
  public ExperimentalTimeseriesService(
    ExperimentalTimeseriesRepository timeseriesRepository,
    ExperimentalTimeseriesDataPointRepository timeseriesDataPointRepository
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
  public List<ExperimentalTimeseriesEntity> getTimeseriesAvailable(long containerId) {
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
  public List<ExperimentalTimeseriesDataPoint> getDataPointsByTimeseries(
    long containerId,
    ExperimentalTimeseries timeseries,
    ExperimentalTimeseriesDataPointsQueryParams queryParams
  ) {
    Optional<ExperimentalTimeseriesEntity> timeseriesEntity =
      this.timeseriesRepository.findTimeseries(containerId, timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();

    int timeseriesId = timeseriesEntity.get().getId();
    ExperimentalDataPointValueType valueType = timeseriesEntity.get().getValueType();

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
  public ExperimentalTimeseriesEntity saveDataPoints(
    TimeseriesContainer timeseriesContainer,
    ExperimentalTimeseries timeseries,
    List<ExperimentalTimeseriesDataPoint> dataPoints
  ) {
    ExperimentalDataPointValueType incomingValueType = ObjectTypeEvaluator.determineType(
      dataPoints.get(0).getValue()
    ).orElseThrow(() -> new InvalidBodyException());

    ExperimentalTimeseriesEntity timeseriesEntity = getOrCreateTimeseries(
      timeseriesContainer.getId(),
      timeseries,
      incomingValueType
    );

    assertDataPointsMatchTimeseriesValueType(timeseriesEntity, dataPoints);

    addDataPointsToTimeseries(timeseriesEntity, dataPoints);

    return timeseriesEntity;
  }

  private ExperimentalTimeseriesEntity getOrCreateTimeseries(
    long containerId,
    ExperimentalTimeseries timeseries,
    ExperimentalDataPointValueType incomingValueType
  ) {
    // try to find timeseries in db
    Optional<ExperimentalTimeseriesEntity> matchingTimeseries = timeseriesRepository.findTimeseries(
      containerId,
      timeseries
    );

    if (matchingTimeseries.isPresent()) return matchingTimeseries.get();

    TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);

    // create new timeseries because it does not exist
    ExperimentalTimeseriesEntity timeseriesEntity = new ExperimentalTimeseriesEntity(
      containerId,
      timeseries,
      incomingValueType
    );
    this.timeseriesRepository.persist(timeseriesEntity);
    return timeseriesEntity;
  }

  private void addDataPointsToTimeseries(
    ExperimentalTimeseriesEntity timeseriesEntity,
    List<ExperimentalTimeseriesDataPoint> dataPoints
  ) {
    List<ExperimentalTimeseriesDataPointEntity> timeseriesDataPointEntities = dataPoints
      .stream()
      .map(dataPoint -> dataPoint.toEntity(timeseriesEntity))
      .toList();

    timeseriesDataPointRepository.persist(timeseriesDataPointEntities);
  }

  private static void assertDataPointsMatchTimeseriesValueType(
    ExperimentalTimeseriesEntity timeseriesEntity,
    List<ExperimentalTimeseriesDataPoint> dataPoints
  ) {
    for (var dataPoint : dataPoints) {
      var expectedType = ObjectTypeEvaluator.determineType(dataPoint.getValue()).orElseThrow(() ->
        new InvalidBodyException()
      );
      assertValueTypeMatchesTimeseries(timeseriesEntity, expectedType);
    }
  }

  private static void assertValueTypeMatchesTimeseries(
    ExperimentalTimeseriesEntity timeseries,
    ExperimentalDataPointValueType incomingValueType
  ) {
    if (timeseries.getValueType() != incomingValueType) throw new InvalidBodyException(
      "Timeseries already exists for data type %s but new data points are of type %s",
      timeseries.getValueType(),
      incomingValueType
    );
  }
}
