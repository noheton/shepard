package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.services.TimeseriesSemanticDualWriteService;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestScoped
public class TimeseriesRepository implements PanacheRepositoryBase<TimeseriesEntity, Integer> {

  @PersistenceContext
  EntityManager entityManager;

  @Inject
  TimeseriesSemanticDualWriteService semanticDualWriteService;

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
    // Step 1: insert the primary row, get the auto-generated id and shepard_id back via RETURNING
    List<Object[]> rows = entityManager
      .createNativeQuery(
        "INSERT INTO timeseries (container_id, value_type) VALUES (:c, :v) RETURNING id, shepard_id"
      )
      .setParameter("c", containerId)
      .setParameter("v", entity.getValueType().name())
      .getResultList();

    if (rows.isEmpty()) {
      Log.warn("upsert: INSERT INTO timeseries returned no id — unexpected");
      return;
    }
    Object[] row = rows.get(0);
    int tsId = ((Number) row[0]).intValue();
    // shepard_id is a UUID in Postgres — comes back as a java.util.UUID or String depending on driver
    String shepardId = row[1] != null ? row[1].toString() : null;

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
      // TS-SEMANTIC-01: dual-write channel metadata to Neo4j SemanticAnnotation nodes.
      // Best-effort — failure is caught inside the service and logged at WARN; Postgres
      // write is never rolled back as a consequence.
      semanticDualWriteService.dualWriteChannelMetadata(
        containerId,
        tsId,
        shepardId,
        entity.getMeasurement(),
        entity.getField(),
        entity.getDevice(),
        entity.getLocation(),
        entity.getSymbolicName()
      );
    }
  }

  /**
   * Delete all {@code timeseries_data_points} rows for a container via a single
   * native SQL statement, BEFORE JPA removes the parent {@code timeseries} rows.
   *
   * <p><b>Why native SQL before JPQL?</b> The {@code timeseries_data_points} table is a
   * TimescaleDB hypertable with compression enabled (policy: compress after 7 days,
   * configured in V1.8.0). When JPA issues the JPQL
   * {@code DELETE FROM TimeseriesEntity WHERE containerId = ?} below, Postgres fires
   * the {@code ON DELETE CASCADE} on {@code timeseries_data_points.timeseries_id}.
   * For compressed chunks the cascade forces chunk decompression before row-level
   * deletion — an operation that consumes memory proportional to the compressed row
   * count and fails with HTTP 500 on containers with more than ~100 K data points
   * (TSDB-DDL-2 in aidocs/16).
   *
   * <p><b>This call</b> issues one native DELETE scoped to the container's timeseries IDs.
   * TimescaleDB processes it chunk-by-chunk without decompressing all rows at once.
   * After this returns, the ON DELETE CASCADE triggered by the subsequent JPQL DELETE
   * finds zero {@code timeseries_data_points} rows and completes instantly.
   *
   * <p>The DELETE is idempotent: if the container has no data points the query deletes
   * 0 rows and returns normally.
   *
   * @param containerId the numeric container id (FK on the {@code timeseries} table)
   */
  @Timed(value = "shepard.timeseries-data-point.delete-by-container")
  public void deleteDataPointsByContainerId(long containerId) {
    // TSDB-DDL-2: chunk-aware native DELETE to pre-empt JPA cascade decompression.
    // Scoped to all timeseries_id values belonging to the container so TimescaleDB
    // can handle compressed chunks without decompression overhead.
    int dpRows = entityManager
      .createNativeQuery(
        "DELETE FROM timeseries_data_points " +
        "WHERE timeseries_id IN (SELECT id FROM timeseries WHERE container_id = :containerId)"
      )
      .setParameter("containerId", containerId)
      .executeUpdate();

    Log.infof(
      "TSDB-DDL-2 deleteDataPointsByContainerId: deleted %d data-point rows for container %d",
      dpRows, containerId
    );
  }

  @Timed(value = "shepard.timeseries.delete")
  public void deleteByContainerId(long containerId) {
    // TSDB-DDL-2: pre-delete data points via native SQL BEFORE this JPQL fires the
    // ON DELETE CASCADE. Without this step, compressed TimescaleDB chunks must be
    // decompressed before row-level deletion — an OOM-class failure on large containers.
    deleteDataPointsByContainerId(containerId);

    // Bulk JPQL DELETE on primary table; ON DELETE CASCADE on channel_metadata.timeseries_id
    // propagates the deletion to channel_metadata rows automatically at the DB level.
    // The timeseries_data_points cascade is a no-op here because deleteDataPointsByContainerId
    // above already removed all rows.
    var rowCount = entityManager
      .createQuery("delete from TimeseriesEntity where containerId = :containerId")
      .setParameter("containerId", containerId)
      .executeUpdate();

    if (rowCount == 0) Log.warn("deleteByContainerId did not delete any timeseries record.");
    if (rowCount > 0) Log.infof("deleteByContainerId has deleted %s timeseries records.", rowCount);
  }
}
