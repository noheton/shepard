package de.dlr.shepard.context.references.timeseriesreference.services;

import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.timeseriesreference.io.MetricsIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequestScoped
public class TimeseriesReferenceMetricsService {

  @Inject
  TimeseriesService timeseriesService;

  @Inject
  TimeseriesDataPointRepository timeseriesDataPointRepository;

  @Inject
  TimeseriesReferenceService timeseriesReferenceService;

  @Inject
  DataObjectService dataObjectService;

  public List<MetricsIO> getTimeseriesReferenceMetrics(
    long collectionId,
    long dataObjectId,
    long timeseriesReferenceId,
    UUID versionUID,
    TimeseriesFiveTuple timeseries
  ) {
    return getTimeseriesReferenceMetrics(
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      versionUID,
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
    long collectionId,
    long dataObjectId,
    long timeseriesReferenceId,
    UUID versionUID,
    TimeseriesFiveTuple timeseries,
    List<AggregateFunction> metrics
  ) {
    dataObjectService.getDataObject(collectionId, dataObjectId, versionUID);

    TimeseriesReference timeseriesReference = timeseriesReferenceService.getReference(
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      versionUID
    );

    if (
      timeseriesReference.getTimeseriesContainer() == null || timeseriesReference.getTimeseriesContainer().isDeleted()
    ) {
      String errorMsg =
        "Referenced TimeseriesContainer is not set or deleted in TimeseriesReference with id %s".formatted(
            timeseriesReferenceId
          );
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    var timeseriesEntity = timeseriesService
      .getTimeseries(timeseriesReference.getTimeseriesContainer().getId(), timeseries)
      .orElseThrow(() -> {
        String errorMsg =
          "Timeseries (%s, %s, %s, %s, %s) in the referenced TimeseriesContainer under TimeseriesReference with id %s".formatted(
              timeseries.measurement(),
              timeseries.device(),
              timeseries.location(),
              timeseries.symbolicName(),
              timeseries.field(),
              timeseriesReferenceId
            );
        Log.error(errorMsg);
        return new NotFoundException(errorMsg);
      });

    return metrics
      .stream()
      .map(metric -> {
        DataPointValueType valueType = timeseriesEntity.getValueType();
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
          this.timeseriesDataPointRepository.queryAggregationFunction(
              timeseriesEntity.getTimeseriesId(),
              valueType,
              queryParams
            );
        return new MetricsIO(metric, dataPoints.getFirst().getValue());
      })
      .collect(Collectors.toList());
  }
}
