package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesRepository implements PanacheRepositoryBase<ExperimentalTimeseriesEntity, Integer> {

  public List<ExperimentalTimeseriesEntity> findByTimeseries(long containerId, ExperimentalTimeseries timeseries) {
    return this.find(
        "containerId = ?1 and measurement = ?2 and field = ?3 and symbolicName = ?4 and device = ?5 and location = ?6",
        containerId,
        timeseries.getMeasurement(),
        timeseries.getField(),
        timeseries.getSymbolicName(),
        timeseries.getDevice(),
        timeseries.getLocation()
      ).list();
  }
}
