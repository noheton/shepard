package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesPayloadDataPoint;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesPayloadDataPointRepository {

  @Inject
  EntityManager entityManager;

  public Object findByTimeseriesId(Long timeseriesId) {
    Query query = entityManager.createNativeQuery("SELECT * FROM your_table WHERE timeseriesId = :timeseriesId");
    query.setParameter("timeseriesId", timeseriesId);
    return query.getSingleResult();
  }

  public void persist(List<ExperimentalTimeseriesPayloadDataPoint> timeseriesPayloadDataPoints, int timeseriesId) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'persist'");
  }
}
