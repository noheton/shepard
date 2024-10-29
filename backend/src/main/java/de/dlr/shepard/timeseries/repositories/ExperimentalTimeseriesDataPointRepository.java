package de.dlr.shepard.timeseries.repositories;

import java.util.List;

import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepository
  implements PanacheRepositoryBase<ExperimentalTimeseriesDataPointEntity, Long> {

  @Inject
  EntityManager entityManager;

  public List<ExperimentalTimeseriesPayloadDataPointIO> getDataPoints(
    int timeseriesId,
    long start,
    long end,
    long timeInNanoseconds,
    AggregateFunctions function
  ) {
    var queryString = buildQuery(timeseriesId, timeInNanoseconds, function);
    var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesPayloadDataPointIO.class);

    @SuppressWarnings("unchecked")
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = query.getResultList();
    return dataPoints;
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

    Log.debugf("SQL Query: %s", query);
    return query;
  }
}
