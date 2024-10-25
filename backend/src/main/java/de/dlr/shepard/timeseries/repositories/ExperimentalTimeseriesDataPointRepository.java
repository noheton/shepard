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
    long timeInMicroseconds,
    AggregateFunctions function
  ) {
    var queryString =
      "SELECT time_bucket('? microseconds', time), ?(double_value) from timeseries_payload WHERE timeseries_id = ? GROUP BY time_bucket('? microseconds', time)";
    var query = entityManager.createNativeQuery(queryString, ExperimentalTimeseriesPayloadDataPointIO.class);
    query.setParameter(1, timeInMicroseconds);
    query.setParameter(2, function.toString());
    query.setParameter(3, timeseriesId);
    query.setParameter(4, timeInMicroseconds);

    @SuppressWarnings("unchecked")
    List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = query.getResultList();
    return dataPoints;
  }

  public static String buildQuery(int timeseriesId, long timeInMicroseconds, AggregateFunctions function) {
    var query = String.format(
      "SELECT time_bucket('%d microseconds', time), %s(double_value) from timeseries_payload WHERE timeseries_id = %d GROUP BY time_bucket('%d microseconds', time)",
      timeInMicroseconds,
      function.toString(),
      timeseriesId,
      timeInMicroseconds
    );
    Log.debugf("SQL Query: %s", query);
    return query;
  }
}
