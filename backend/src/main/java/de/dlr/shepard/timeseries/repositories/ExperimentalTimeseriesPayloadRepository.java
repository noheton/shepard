package de.dlr.shepard.timeseries.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ApplicationScoped
public class ExperimentalTimeseriesPayloadRepository {

  @Inject
  EntityManager entityManager;

  public Object findByTimeseriesId(Long timeseriesId) {
    Query query = entityManager.createNativeQuery("SELECT * FROM your_table WHERE timeseriesId = :timeseriesId");
    query.setParameter("timeseriesId", timeseriesId);
    return query.getSingleResult();
  }
}
