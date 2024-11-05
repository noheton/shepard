package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.model.FillOption;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepository
  implements PanacheRepositoryBase<ExperimentalTimeseriesDataPointEntity, Long> {

  @Inject
  EntityManager entityManager;

  public List<ExperimentalTimeseriesDataPoint> getDataPoints(
    int timeseriesId,
    long startNanoseconds,
    long endNanoseconds,
    Long timeIntervalNanoseconds,
    ExperimentalDataPointValueTypes valueType,
    AggregateFunctions function,
    FillOption fillOption
  ) throws InvalidRequestException {
    assertNotIntegral(function);
    assertCorrectValueTypesForAggregation(function, valueType);
    assertCorrectValueTypesForFillOption(fillOption, valueType);
    assertTimeIntervalForFillOption(timeIntervalNanoseconds, fillOption);
    assertAggregationSetForFillOrGrouping(function, timeIntervalNanoseconds, fillOption);

    var query = buildQueryObject(
      timeseriesId,
      startNanoseconds,
      endNanoseconds,
      timeIntervalNanoseconds,
      valueType,
      function,
      fillOption
    );

    @SuppressWarnings("unchecked")
    List<ExperimentalTimeseriesDataPoint> dataPoints = query.getResultList();
    return dataPoints;
  }

  public Query buildQueryObject(
    int timeseriesId,
    long startNanoseconds,
    long endNanoseconds,
    Long timeIntervalNanoseconds,
    ExperimentalDataPointValueTypes valueType,
    AggregateFunctions function,
    FillOption fillOption
  ) {
    String columnName =
      switch (valueType) {
        case Double -> "double_value";
        case Integer -> "int_value";
        case String -> "string_value";
        case Boolean -> "boolean_value";
      };

    if (fillOption == null) {
      fillOption = FillOption.NONE;
    }

    String queryString = "";
    if (function != null) {
      if (timeIntervalNanoseconds == null) {
        timeIntervalNanoseconds = endNanoseconds - startNanoseconds;
      }

      queryString = "SELECT ";

      queryString +=
      switch (fillOption) {
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
        case INTEGRAL -> {} //TODO: has to be implemented in #284
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

    queryString +=
    """
    FROM timeseries_payload
    WHERE timeseries_id = :timeseriesId
      AND time >= :startTimeNano
      AND time <= :endTimeNano
    """;

    if (function != null) {
      queryString += " GROUP BY timestamp";
    }

    var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);

    if (timeIntervalNanoseconds != null) {
      query.setParameter("timeInNanoseconds", timeIntervalNanoseconds);
    }
    query.setParameter("timeseriesId", timeseriesId);
    query.setParameter("startTimeNano", startNanoseconds);
    query.setParameter("endTimeNano", endNanoseconds);

    return query;
  }

  /**
   * Throw when trying to access unsupported aggregation function.
   */
  private void assertNotIntegral(AggregateFunctions function) {
    if (function == AggregateFunctions.INTEGRAL) {
      throw new InvalidRequestException("Aggregation function 'integral' is currently not implemented.");
    }
  }

  /**
   * Throw when trying to use aggregation functions with boolean or string value types.
   */
  private void assertCorrectValueTypesForAggregation(
    AggregateFunctions function,
    ExperimentalDataPointValueTypes valueType
  ) {
    if (
      (valueType == ExperimentalDataPointValueTypes.Boolean || valueType == ExperimentalDataPointValueTypes.String) &&
      (function != null)
    ) {
      throw new InvalidRequestException(
        "Cannot execute aggregation functions on data points of type boolean or string."
      );
    }
  }

  /**
   * Throw when trying to use gap filling with unsupported value types boolean or string.
   */
  private void assertCorrectValueTypesForFillOption(FillOption fillOption, ExperimentalDataPointValueTypes valueType) {
    if (
      (valueType == ExperimentalDataPointValueTypes.Boolean || valueType == ExperimentalDataPointValueTypes.String) &&
      (fillOption != null)
    ) {
      throw new InvalidRequestException("Cannot use gap filling options on data points of type boolean or string.");
    }
  }

  /**
   * Throw when trying to use fill option without specifying the timeinterval/ groupBy value
   */
  private void assertTimeIntervalForFillOption(Long timeIntervalNanoseconds, FillOption fillOption) {
    if (timeIntervalNanoseconds == null && fillOption != null) {
      throw new InvalidRequestException("Cannot use gap filling option when no grouping interval is specified.");
    }
  }

  /**
   * Throw when trying to use fill option or grouping when no aggregation function is set.
   */
  private void assertAggregationSetForFillOrGrouping(
    AggregateFunctions function,
    Long timeIntervalNanoseconds,
    FillOption fillOption
  ) {
    if (function == null && (fillOption != null || timeIntervalNanoseconds != null)) {
      throw new InvalidRequestException(
        "Cannot use gap filling option or grouping of data when no aggregation function is specified."
      );
    }
  }
}
