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
    // var queryString = buildQuery(timeseriesId, timeInNanoseconds, function);
    // var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);

    if (function == AggregateFunctions.INTEGRAL) {
      throw new InvalidRequestException("Aggregation function 'integral' is currently not implemented.");
    }

    if (
      (valueType == ExperimentalDataPointValueTypes.Boolean || valueType == ExperimentalDataPointValueTypes.String) &&
      (function != null)
    ) {
      throw new InvalidRequestException(
        "Cannot execute aggregation functions on data points of type boolean or string."
      );
    }

    if (
      (valueType == ExperimentalDataPointValueTypes.Boolean || valueType == ExperimentalDataPointValueTypes.String) &&
      (fillOption != null)
    ) {
      throw new InvalidRequestException("Cannot use gap filling options on data points of type boolean or string.");
    }

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
    // TODO: query location, device, symbolic_name, ...
    // TODO: re-integrate the other more complex functions (spread, FIRST, LAST, MEAN, MEDIAN, MODE)
    // TODO: handle case for NONE fill function but no aggregate function

    // states that influence the query:
    // fillOption null/ not null -> depending on timeInterval we have to manually sort out NULL values
    // fillOption "NONE" -> use time_bucket (skips NULL values)
    // fillOption is "NULL" -> use time_bucket_gapfill
    // fillOption one of "PREVIOUS, LINEAR" -> use time_bucket_gapfill + loc()/interpolate()
    // timeInterval null -> dont use buckets
    // timeInterval not null -> use buckets
    // function either null -> create simple query
    // function one of (MAX, MIN, COUNT, SUM, STDDEV) -> build query where we can replace function names in query building
    // function one of (spread, FIRST, LAST, MEAN, MEDIAN, MODE) -> individually build queries for each aggregate function

    // ----------------
    // SELECT
    //  time_bucket|time_bucket_gapfill|nothing
    //  LOCF/INTERPOLATE(|FUNCTION(COLUMNAME)|COLUMNAME|)
    // FROM timeseries_payload
    // WHERE timeseries_id = :timeseriesId
    //  AND time >=
    //  AND time <=
    // (GROUP BY timestamp)

    String queryString = "SELECT ";

    if (timeIntervalNanoseconds != null && fillOption != null) {
      queryString +=
      switch (fillOption) {
        case NONE -> "time_bucket(:timeInNanoseconds, time) as timestamp,";
        case NULL, LINEAR, PREVIOUS -> "time_bucket_gapfill(:timeInNanoseconds, time) as timestamp,";
      };
    }

    String columnName =
      switch (valueType) {
        case Double -> "double_value";
        case Integer -> "int_value";
        case String -> "string_value";
        case Boolean -> "boolean_value";
      };

    String aggregationString = "";
    if (null != function) switch (function) {
      case MAX, MIN, COUNT, SUM, STDDEV -> aggregationString = String.format("%s(%s)", function.name(), columnName);
      default -> {}
    }
    else {
      aggregationString = columnName;
    }

    // build filling - by default bucket_gapfill uses NULL filloption
    if (fillOption == FillOption.LINEAR) {
      aggregationString = String.format("interpolate(%s) as value", aggregationString);
    } else if (fillOption == FillOption.PREVIOUS) {
      aggregationString = String.format("locf(%s) as value", aggregationString);
    }

    queryString += " " + aggregationString;
    queryString += " FROM timeseries_payload";
    queryString += " WHERE timeseries_id = :timeseriesId";
    queryString += " AND time >= :startTimeNano AND time <= :endTimeNano";

    if (timeIntervalNanoseconds != null) {
      queryString += " GROUP BY timestamp";
    }

    /*if (null != function) switch (function) {
      case MAX, MIN, COUNT, SUM, STDDEV -> queryString = String.format("%s(%s) as value", function.name(), columnName);
      case LAST -> queryString = String.format(
        """
        SELECT
          time_bucket(:timeInNanoseconds, time) as timestamp,
          last(%s, time) as value
        From timeseries_payload
        WHERE timeseries_id = :timeseriesId
          AND time >= :startTimeNano
          AND time <= :endTimeNano
        GROUP BY timestamp
        """,
        columnName
      );
      case FIRST -> queryString = String.format(
        "SELECT time_bucket(:timeInNanoseconds, time) as timestamp, first(%s, time) as value from timeseries_payload WHERE timeseries_id = :timeseriesId AND time >= :startTimeNano AND time <= :endTimeNano GROUP BY timestamp",
        columnName
      );
      case MEAN -> queryString = String.format(
        "SELECT time_bucket(:timeInNanoseconds, time) as timestamp, AVG(%s) as value from timeseries_payload WHERE timeseries_id = :timeseriesId AND time >= :startTimeNano AND time <= :endTimeNano GROUP BY timestamp",
        columnName
      );
      case MEDIAN -> queryString = String.format(
        "SELECT time_bucket(:timeInNanoseconds, time) as timestamp, percentile_cont(0.5) WITHIN GROUP (ORDER BY %s) as value FROM timeseries_payload WHERE timeseries_id = :timeseriesId AND time >= :startTimeNano AND time <= :endTimeNano GROUP BY timestamp",
        columnName
      );
      case MODE -> queryString = String.format(
        "SELECT time_bucket(:timeInNanoseconds, time) as timestamp, mode() WITHIN GROUP (ORDER BY %s) as value FROM timeseries_payload WHERE timeseries_id = :timeseriesId AND time >= :startTimeNano AND time <= :endTimeNano GROUP BY timestamp",
        columnName
      );
      case SPREAD -> queryString = String.format(
        "SELECT time_bucket(:timeInNanoseconds, time) as timestamp,  MAX(%s) - MIN(%s) as value FROM timeseries_payload WHERE timeseries_id = :timeseriesId AND time >= :startTimeNano AND time <= :endTimeNano GROUP BY timestamp",
        columnName
      );
    }
    else {
      queryString = String.format(
        """
        SELECT
          time,
          %s
        FROM timeseries_payload
        WHERE timeseries_id = :timeseriesId
          AND time >= :startTimeNano
          AND time <= :endTimeNano
        """,
        columnName
      );
    }*/

    var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);

    if (fillOption != null) {
      query.setParameter("timeInNanoseconds", timeIntervalNanoseconds);
    }
    query.setParameter("timeseriesId", timeseriesId);
    query.setParameter("startTimeNano", startNanoseconds);
    query.setParameter("endTimeNano", endNanoseconds);
    return query;
  }
}
