package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import de.dlr.shepard.timeseries.model.AggregateFunctions;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NamedNativeQuery;
import java.util.List;

@ApplicationScoped
@NamedNativeQuery(
  name = "getDataPoints",
  query = "SELECT time_bucket(':timeInterval microseconds', time), :aggregateFunction(double_value) from timeseries_payload WHERE timeseries_id = :timeseriesId GROUP BY time_bucket(':timeInterval microseconds', time)",
  resultClass = ExperimentalTimeseriesPayloadDataPointIO.class
)
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
    var query = String.format(
      "SELECT time_bucket(%d, time) as timestamp, %s(double_value) as value from timeseries_payload WHERE timeseries_id = %d GROUP BY timestamp",
      timeInNanoseconds,
      function.toString(),
      timeseriesId
    );
    Log.debugf("SQL Query: %s", query);
    return query;
  }
}
