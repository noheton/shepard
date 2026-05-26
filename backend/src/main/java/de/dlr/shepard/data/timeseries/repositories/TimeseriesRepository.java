package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
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
      var errorMessage =
        "Multiple Timeseries exist with the same properties for container id: %d. Timeseries Ids: %s".formatted(
            containerId,
            timeseriesList.stream().map(ts -> ts.getId() + "").collect(Collectors.joining(", "))
          );
      throw new RuntimeException(errorMessage);
    }
    return Optional.of(timeseriesList.getFirst());
  }

  /**
   * Atomically upsert a channel row across both the {@code timeseries} and
   * {@code channel_metadata} tables.
   *
   * <p>Two-step native SQL:
   * <ol>
   *   <li>INSERT INTO timeseries (container_id, value_type) — no 5-tuple constraint here;
   *       always produces a new row with a DB-generated shepard_id.</li>
   *   <li>INSERT INTO channel_metadata ON CONFLICT DO NOTHING — if another thread
   *       created the same channel concurrently, rowCount == 0.</li>
   *   <li>On conflict (rowCount == 0): DELETE the orphan timeseries row just inserted.</li>
   * </ol>
   *
   * <p>The caller always calls {@link #findTimeseries} after this method to obtain the
   * canonical managed entity (with shepard_id and channel_metadata fully populated).
   *
   * <p>Do NOT use {@code entityManager.persist(entity)} for channel creation —
   * {@code channel_metadata.container_id} is not a JPA-managed column and persist()
   * would leave it NULL, violating the NOT NULL constraint.
   */
  @SuppressWarnings("unchecked")
  public void upsert(long containerId, TimeseriesEntity entity) {
    // Step 1: insert the primary row, get the auto-generated id back via RETURNING
    List<Number> ids = entityManager
      .createNativeQuery(
        "INSERT INTO timeseries (container_id, value_type) VALUES (:c, :v) RETURNING id"
      )
      .setParameter("c", containerId)
      .setParameter("v", entity.getValueType().name())
      .getResultList();

    if (ids.isEmpty()) {
      Log.warn("upsert: INSERT INTO timeseries returned no id — unexpected");
      return;
    }
    int tsId = ids.get(0).intValue();

    // Step 2: insert channel_metadata; skip silently on 5-tuple conflict (race)
    int cmRows = entityManager
      .createNativeQuery(
        "INSERT INTO channel_metadata" +
        " (timeseries_id, container_id, measurement, field, device, location, symbolic_name)" +
        " VALUES (:tsId, :c, :m, :f, :dev, :loc, :sym)" +
        " ON CONFLICT (container_id, measurement, field, symbolic_name, device, location) DO NOTHING"
      )
      .setParameter("tsId", tsId)
      .setParameter("c", containerId)
      .setParameter("m", entity.getMeasurement())
      .setParameter("f", entity.getField())
      .setParameter("dev", entity.getDevice())
      .setParameter("loc", entity.getLocation())
      .setParameter("sym", entity.getSymbolicName())
      .executeUpdate();

    if (cmRows == 0) {
      // Race: another thread created this channel first; clean up the orphan timeseries row.
      entityManager
        .createNativeQuery("DELETE FROM timeseries WHERE id = :id")
        .setParameter("id", tsId)
        .executeUpdate();
      Log.debugf(
        "upsert: channel_metadata UNIQUE conflict — cleaned up orphan timeseries id=%d " +
        "(container=%d measurement=%s field=%s)",
        tsId, containerId, entity.getMeasurement(), entity.getField()
      );
    } else {
      Log.infof(
        "upsert: created channel — container=%d measurement=%s field=%s symbolicName=%s",
        containerId, entity.getMeasurement(), entity.getField(), entity.getSymbolicName()
      );
    }
  }

  @Timed(value = "shepard.timeseries.delete")
  public void deleteByContainerId(long containerId) {
    // Bulk JPQL DELETE on primary table; ON DELETE CASCADE on channel_metadata.timeseries_id
    // propagates the deletion to channel_metadata rows automatically at the DB level.
    var rowCount = entityManager
      .createQuery("delete from TimeseriesEntity where containerId = :containerId")
      .setParameter("containerId", containerId)
      .executeUpdate();

    if (rowCount == 0) Log.warn("deleteByContainerId did not delete any timeseries record.");
    if (rowCount > 0) Log.infof("deleteByContainerId has deleted %s timeseries records.", rowCount);
  }
}
