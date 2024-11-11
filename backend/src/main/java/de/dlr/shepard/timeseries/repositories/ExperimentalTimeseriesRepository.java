package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExperimentalTimeseriesRepository implements PanacheRepositoryBase<ExperimentalTimeseriesEntity, Integer> {

  public Optional<ExperimentalTimeseriesEntity> findTimeseries(long containerId, ExperimentalTimeseries timeseries) {
    List<ExperimentalTimeseriesEntity> timeseriesList =
      this.find(
          "containerId = ?1 and measurement = ?2 and field = ?3 and symbolicName = ?4 and device = ?5 and location = ?6",
          containerId,
          timeseries.getMeasurement(),
          timeseries.getField(),
          timeseries.getSymbolicName(),
          timeseries.getDevice(),
          timeseries.getLocation()
        ).list();
    if (timeseriesList.isEmpty()) return Optional.empty();
    if (timeseriesList.size() > 1) {
      Log.warnf(
        "Multiple Timeseries exist with the same properties for container id: %d. Timeseries Ids: %s",
        containerId,
        timeseriesList.stream().map(ts -> ts.getId() + "").collect(Collectors.joining(", "))
      );
    }
    return Optional.of(timeseriesList.get(0));
  }
}
