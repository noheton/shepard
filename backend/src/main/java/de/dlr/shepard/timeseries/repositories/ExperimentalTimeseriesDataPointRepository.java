package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointsQueryParams;
import de.dlr.shepard.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.timeseries.model.enums.ExperimentalDataPointValueType;
import de.dlr.shepard.timeseries.model.enums.FillOption;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepository
  implements PanacheRepositoryBase<ExperimentalTimeseriesDataPointEntity, Long> {

  @Inject
  EntityManager entityManager;

  public List<ExperimentalTimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    ExperimentalDataPointValueType valueType,
    ExperimentalTimeseriesDataPointsQueryParams queryParams
  ) {
    assertNotIntegral(queryParams.getFunction());
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);
    assertCorrectValueTypesForFillOption(queryParams.getFillOption(), valueType);
    assertTimeIntervalForFillOption(queryParams.getTimeSliceNanoseconds(), queryParams.getFillOption());
    assertAggregationSetForFillOrGrouping(
      queryParams.getFunction(),
      queryParams.getTimeSliceNanoseconds(),
      queryParams.getFillOption()
    );

    var query = buildQueryObject(timeseriesId, valueType, queryParams);

    @SuppressWarnings("unchecked")
    List<ExperimentalTimeseriesDataPoint> dataPoints = query.getResultList();
    return dataPoints;
  }

  private Query buildQueryObject(
    int timeseriesId,
    ExperimentalDataPointValueType valueType,
    ExperimentalTimeseriesDataPointsQueryParams queryParams
  ) {
    String columnName =
      switch (valueType) {
        case Double -> "double_value";
        case Integer -> "int_value";
        case String -> "string_value";
        case Boolean -> "boolean_value";
      };

    FillOption fillOption = queryParams.getFillOption().orElse(FillOption.NONE);
    var timeSliceNanoseconds = queryParams.getTimeSliceNanoseconds().orElse(null);

    String queryString = "";
    if (queryParams.getFunction().isPresent()) {
      AggregateFunction function = queryParams.getFunction().get();
      if (timeSliceNanoseconds == null) {
        timeSliceNanoseconds = queryParams.getEndTime() - queryParams.getStartTime();
      }

      queryString = "SELECT ";

      queryString += switch (fillOption) {
        case NONE -> "time_bucket(:timeInNanoseconds, time) as timestamp, ";
        case NULL, LINEAR, PREVIOUS -> "time_bucket_gapfill(:timeInNanoseconds, time) as timestamp, ";
      };

      String aggregationString = "";
      switch (function) {
        case MAX, MIN, COUNT, SUM, STDDEV -> aggregationString = String.format("%s(%s)", function.name(), columnName);
        case MEAN -> aggregationString = String.format("AVG(%s)", columnName);
        case LAST, FIRST -> aggregationString = String.format("%s(%s, time)", function.name(), columnName);
        case SPREAD -> aggregationString = String.format("MAX(%s) - MIN(%s)", columnName, columnName);
        case MEDIAN -> aggregationString = String.format("percentile_cont(0.5) WITHIN GROUP (ORDER BY %s)", columnName);
        case MODE -> aggregationString = String.format("mode() WITHIN GROUP (ORDER BY %s)", columnName);
        case INTEGRAL -> {}
      }

      // handle filling - by default bucket_gapfill uses NULL filloption
      if (fillOption == FillOption.LINEAR) {
        aggregationString = String.format("interpolate(%s) as value ", aggregationString);
      } else if (fillOption == FillOption.PREVIOUS) {
        aggregationString = String.format("locf(%s) as value ", aggregationString);
      } else {
        aggregationString += " as value ";
      }

      queryString += aggregationString;
    } else {
      queryString = String.format("SELECT time, %s ", columnName);
    }

    queryString += """
    FROM timeseries_data_points
    WHERE timeseries_id = :timeseriesId
      AND time >= :startTimeNano
      AND time <= :endTimeNano
    """;

    if (queryParams.getFunction().isPresent()) {
      queryString += " GROUP BY timestamp";
    }

    Query query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);

    if (timeSliceNanoseconds != null) {
      query.setParameter("timeInNanoseconds", timeSliceNanoseconds);
    }
    query.setParameter("timeseriesId", timeseriesId);
    query.setParameter("startTimeNano", queryParams.getStartTime());
    query.setParameter("endTimeNano", queryParams.getEndTime());

    return query;
  }

  /**
   * Throw when trying to access unsupported aggregation function.
   */
  private void assertNotIntegral(Optional<AggregateFunction> function) {
    if (function.isPresent() && function.get() == AggregateFunction.INTEGRAL) {
      throw new InvalidRequestException("Aggregation function 'integral' is currently not implemented.");
    }
  }

  /**
   * Throw when trying to use aggregation functions with boolean or string value types.
   */
  private void assertCorrectValueTypesForAggregation(
    Optional<AggregateFunction> function,
    ExperimentalDataPointValueType valueType
  ) {
    if (
      (valueType == ExperimentalDataPointValueType.Boolean || valueType == ExperimentalDataPointValueType.String) &&
      (function.isPresent())
    ) {
      throw new InvalidRequestException(
        "Cannot execute aggregation functions on data points of type boolean or string."
      );
    }
  }

  /**
   * Throw when trying to use gap filling with unsupported value types boolean or string.
   */
  private void assertCorrectValueTypesForFillOption(
    Optional<FillOption> fillOption,
    ExperimentalDataPointValueType valueType
  ) {
    if (
      (valueType == ExperimentalDataPointValueType.Boolean || valueType == ExperimentalDataPointValueType.String) &&
      (fillOption.isPresent())
    ) {
      throw new InvalidRequestException("Cannot use gap filling options on data points of type boolean or string.");
    }
  }

  /**
   * Throw when trying to use fill option without specifying the timeSlice value
   */
  private void assertTimeIntervalForFillOption(Optional<Long> timeSliceNanoseconds, Optional<FillOption> fillOption) {
    if (timeSliceNanoseconds.isEmpty() && fillOption.isPresent()) {
      throw new InvalidRequestException("Cannot use gap filling option when no grouping interval is specified.");
    }
  }

  /**
   * Throw when trying to use fill option or grouping when no aggregation function is set.
   */
  private void assertAggregationSetForFillOrGrouping(
    Optional<AggregateFunction> function,
    Optional<Long> timeSliceNanoseconds,
    Optional<FillOption> fillOption
  ) {
    if (function.isEmpty() && (fillOption.isPresent() || timeSliceNanoseconds.isPresent())) {
      throw new InvalidRequestException(
        "Cannot use gap filling option or grouping of data when no aggregation function is specified."
      );
    }
  }
}
