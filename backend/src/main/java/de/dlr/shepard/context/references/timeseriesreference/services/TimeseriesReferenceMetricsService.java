package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.io.MetricsIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestScoped
public class TimeseriesReferenceMetricsService {

  private TimeseriesRepository timeseriesRepository;
  private TimeseriesDataPointRepository timeseriesDataPointRepository;
  private TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Inject
  public TimeseriesReferenceMetricsService(
    TimeseriesDataPointRepository timeseriesDataPointRepository,
    TimeseriesRepository timeseriesRepository,
    TimeseriesReferenceDAO timeseriesReferenceDAO
  ) {
    this.timeseriesDataPointRepository = timeseriesDataPointRepository;
    this.timeseriesRepository = timeseriesRepository;
    this.timeseriesReferenceDAO = timeseriesReferenceDAO;
  }

  public List<MetricsIO> getTimeseriesReferenceMetrics(long timeseriesReferenceId, Timeseries timeseries) {
    return getTimeseriesReferenceMetrics(
      timeseriesReferenceId,
      timeseries,
      List.of(
        AggregateFunction.COUNT,
        AggregateFunction.MAX,
        AggregateFunction.MIN,
        AggregateFunction.STDDEV,
        AggregateFunction.MEAN,
        AggregateFunction.MEDIAN,
        AggregateFunction.FIRST,
        AggregateFunction.LAST
      )
    );
  }

  public List<MetricsIO> getTimeseriesReferenceMetrics(
    long timeseriesReferenceId,
    Timeseries timeseries,
    List<AggregateFunction> metrics
  ) {
    TimeseriesReference timeseriesReference = timeseriesReferenceDAO.findByShepardId(timeseriesReferenceId);

    Optional<TimeseriesEntity> timeseriesEntity =
      this.timeseriesRepository.findTimeseries(timeseriesReference.getTimeseriesContainer().getId(), timeseries);

    if (timeseriesEntity.isEmpty()) return Collections.emptyList();
    int timeseriesId = timeseriesEntity.get().getId();
    return metrics
      .stream()
      .map(metric -> {
        DataPointValueType valueType = timeseriesEntity.get().getValueType();
        TimeseriesDataPointsQueryParams queryParams = new TimeseriesDataPointsQueryParams(
          timeseriesReference.getStart(),
          timeseriesReference.getEnd(),
          null,
          null,
          metric
        );
        // String and boolean can get metrics only on COUNT, LAST and FIRST
        if (
          (valueType == DataPointValueType.String || valueType == DataPointValueType.Boolean) &&
          (metric != AggregateFunction.COUNT && metric != AggregateFunction.FIRST && metric != AggregateFunction.LAST)
        ) return new MetricsIO(metric, "N/A");
        var dataPoints =
          this.timeseriesDataPointRepository.queryAggregationFunction(timeseriesId, valueType, queryParams);
        return new MetricsIO(metric, dataPoints.get(0).getValue());
      })
      .collect(Collectors.toList());
  }
}
