package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
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
    long start,
    long end,
    ExperimentalDataPointValueTypes valueType,
    long timeInNanoseconds,
    AggregateFunctions function
  ) {
    // var queryString = buildQuery(timeseriesId, timeInNanoseconds, function);
    // var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);

    var query = buildQueryObject(timeseriesId, timeInNanoseconds, function);

    @SuppressWarnings("unchecked")
    List<ExperimentalTimeseriesDataPoint> dataPoints = query.getResultList();
    return dataPoints;
  }

  public Query buildQueryObject(int timeseriesId, long timeInNanoseconds, AggregateFunctions function) {
    var columnName = "double_value";
    var queryString = String.format(
      "SELECT time_bucket(:timeInNanoseconds, time) as timestamp, %s(%s) as value from timeseries_payload WHERE timeseries_id = :timeseriesId GROUP BY timestamp",
      function.name(),
      columnName
    );
    var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesDataPoint.class);
    query.setParameter("timeInNanoseconds", timeInNanoseconds);
    query.setParameter("timeseriesId", timeseriesId);

    return query;
  }

  public static String buildQuery(int timeseriesId, long timeInNanoseconds, AggregateFunctions function) {
    String query = "";

    if (null != function) switch (function) {
      case MAX, MIN, COUNT, SUM, STDDEV -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, %s(double_value) as value from timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        function.name(),
        timeseriesId
      );
      case LAST -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, last(double_value, time) as value from timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      case FIRST -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, first(double_value, time) as value from timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      case MEAN -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, AVG(double_value) as value from timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      case MEDIAN -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, percentile_cont(0.5) WITHIN GROUP (ORDER BY double_value) as value FROM timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      case MODE -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp, mode() WITHIN GROUP (ORDER BY double_value) as value FROM timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      case SPREAD -> query = String.format(
        "SELECT time_bucket(%d, time) as timestamp,  MAX(double_value) - MIN(double_value) as value FROM timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
        timeInNanoseconds,
        timeseriesId
      );
      default -> {}
    }
    else {
      query = String.format(
        "SELECT time as timestamp, double_value as value from timeseries_payload WHERE timeseries_id = %d",
        timeseriesId
      );
    }

    Log.debugf("SQL Query: %s", query);
    return query;
  }
}
