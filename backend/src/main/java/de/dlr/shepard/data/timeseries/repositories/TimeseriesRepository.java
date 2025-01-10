package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestScoped
public class TimeseriesRepository implements PanacheRepositoryBase<TimeseriesEntity, Integer> {

  @PersistenceContext
  EntityManager entityManager;

  public Optional<TimeseriesEntity> findTimeseries(long containerId, Timeseries timeseries) {
    List<TimeseriesEntity> timeseriesList =
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
      var errorMessage = String.format(
        "Multiple Timeseries exist with the same properties for container id: %d. Timeseries Ids: %s",
        containerId,
        timeseriesList.stream().map(ts -> ts.getId() + "").collect(Collectors.joining(", "))
      );
      throw new RuntimeException(errorMessage);
    }
    return Optional.of(timeseriesList.get(0));
  }

  public void upsert(long containerId, TimeseriesEntity entity) {
    var rowCount = entityManager
      .createQuery(
        "insert into TimeseriesEntity (containerId, measurement, field, symbolicName, device, location, valueType)" +
        " values (:containerId, :measurement, :field, :symbolicName, :device, :location, :valueType)" +
        " on conflict(containerId, measurement, field, symbolicName, device, location) do nothing"
      )
      .setParameter("containerId", entity.getContainerId())
      .setParameter("measurement", entity.getMeasurement())
      .setParameter("field", entity.getField())
      .setParameter("symbolicName", entity.getSymbolicName())
      .setParameter("device", entity.getDevice())
      .setParameter("location", entity.getLocation())
      .setParameter("valueType", entity.getValueType())
      .executeUpdate();

    if (rowCount == 0) Log.warn("Upsert did not insert a timeseries record.");
    if (rowCount == 1) Log.info("Upsert has created a new timeseries record.");
    if (rowCount > 1) throw new RuntimeException("Upsert has changed multiple rows.");
  }
}
