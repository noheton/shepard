package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestScoped
public class TimeseriesRepository implements PanacheRepositoryBase<Timeseries, Integer> {

  @PersistenceContext
  EntityManager entityManager;

  public Optional<Timeseries> findTimeseries(long containerId, TimeseriesTuple timeseries) {
    List<Timeseries> timeseriesList =
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
      var errorMessage =
        "Multiple Timeseries exist with the same properties for container id: %d. Timeseries Ids: %s".formatted(
            containerId,
            timeseriesList.stream().map(ts -> ts.getId() + "").collect(Collectors.joining(", "))
          );
      throw new RuntimeException(errorMessage);
    }
    return Optional.of(timeseriesList.getFirst());
  }

  public void upsert(long containerId, Timeseries entity) {
    var rowCount = entityManager
      .createQuery(
        "insert into Timeseries (containerId, measurement, field, symbolicName, device, location, valueType)" +
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

  @Timed(value = "shepard.timeseries.delete")
  public void deleteByContainerId(long containerId) {
    var rowCount = entityManager
      .createQuery("delete from Timeseries where containerId = :containerId")
      .setParameter("containerId", containerId)
      .executeUpdate();

    if (rowCount == 0) Log.warn("deleteByContainerId did not delete any timeseries record.");
    if (rowCount > 0) Log.infof("deleteByContainerId has deleted %s timeseries records.", rowCount);
  }
}
