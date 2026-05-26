package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves between the upcoming single-field channel identity ({@code
 * shepardId}, a Postgres UUID column on the {@code timeseries} table)
 * and the legacy 5-tuple ({@code measurement}, {@code device},
 * {@code location}, {@code symbolicName}, {@code field}) — plus the
 * containing {@code containerId}.
 *
 * <p>This is the JDBC seam for PR-2..10 of the TS-ID wire-rename series.
 * The wire-facing {@code /v2/} resources can accept either form; both
 * map down to a single Postgres row via this resolver. The Neo4j side
 * is uninvolved — channels live entirely in Postgres/Timescale.
 *
 * <p>Substrate decision recorded in
 * {@code aidocs/platform/87-timeseries-appid-migration.md} CHANGELOG
 * 2026-05-22.
 */
@ApplicationScoped
public class TsChannelResolver implements PanacheRepositoryBase<TimeseriesEntity, Integer> {

  /**
   * Look up a channel by its shepardId. The {@code shepardId} column is
   * UNIQUE per the V1.11.0 migration so this is at most one row.
   *
   * @param shepardId the channel's stable identity (UUID v4, minted by
   *                  the Postgres column default on insert)
   * @return the channel row, or {@link Optional#empty()} if no row matches
   */
  public Optional<TimeseriesEntity> findByShepardId(UUID shepardId) {
    if (shepardId == null) return Optional.empty();
    return this.find("shepardId = ?1", shepardId).firstResultOptional();
  }

  /**
   * Look up a channel by its containing container and the 5-tuple. This
   * is the legacy lookup path — equivalent to
   * {@link TimeseriesRepository#findTimeseries(long, Timeseries)} but
   * exposed on the resolver so callers don't need to switch repositories
   * mid-resolution. Throws if multiple rows somehow match (a database
   * invariant violation).
   *
   * @param containerId the owning timeseries container's numeric id
   * @param ts          the 5-tuple as a {@link Timeseries} pojo
   * @return the channel row, or {@link Optional#empty()} if no row matches
   */
  public Optional<TimeseriesEntity> findByContainerAndTuple(long containerId, Timeseries ts) {
    if (ts == null) return Optional.empty();
    return this.find(
        "containerId = ?1 and measurement = ?2 and field = ?3 and symbolicName = ?4 and device = ?5 and location = ?6",
        containerId,
        ts.getMeasurement(),
        ts.getField(),
        ts.getSymbolicName(),
        ts.getDevice(),
        ts.getLocation()
      )
      .firstResultOptional();
  }

  /**
   * Resolve a list of shepardIds to their channel rows in a single query.
   * Unknown ids are silently absent from the returned list; ordering is
   * not guaranteed to match the input list.
   *
   * @param ids list of shepardId UUIDs to look up
   * @return all matching channel entities (may be smaller than ids.size())
   */
  public List<TimeseriesEntity> bulkFindByShepardIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return this.find("shepardId IN ?1", ids).list();
  }

  /**
   * List channels for a container with pagination. {@code containerId} lives on
   * the primary {@code timeseries} table so the JPQL predicate works without a
   * secondary-table join.
   *
   * @param containerId the owning timeseries container's numeric id
   * @param page        zero-based page index
   * @param size        page size (caller must clamp to a safe max before passing)
   * @return one page of channel rows
   */
  public List<TimeseriesEntity> listPaged(long containerId, int page, int size) {
    return this.find("containerId = ?1", containerId).page(page, size).list();
  }

  /**
   * Convenience: resolve a shepardId to its 5-tuple representation. The
   * caller usually wants the 5-tuple to plug into the existing data-point
   * query layer (which is still 5-tuple addressed) without needing the
   * raw row.
   *
   * @param shepardId the channel's stable identity
   * @return the 5-tuple wrapper, or {@link Optional#empty()} if no row matches
   */
  public Optional<Timeseries> resolveTuple(UUID shepardId) {
    return findByShepardId(shepardId).map(this::toTuple);
  }

  /**
   * Convenience: resolve a 5-tuple to its shepardId. The container id is
   * required because a 5-tuple is unique only within a container (the
   * Postgres index includes container_id in the uniqueness constraint).
   *
   * @param containerId the owning timeseries container's numeric id
   * @param ts          the 5-tuple as a {@link Timeseries} pojo
   * @return the shepardId, or {@link Optional#empty()} if no row matches
   */
  public Optional<UUID> resolveShepardId(long containerId, Timeseries ts) {
    return findByContainerAndTuple(containerId, ts).map(TimeseriesEntity::getShepardId);
  }

  /**
   * Project a row's 5-tuple fields back into a {@link Timeseries} pojo.
   * The value type is not part of the 5-tuple — callers needing it
   * should consult the row directly. Uses the row-copy constructor since
   * {@link Timeseries} is immutable (final 5-tuple fields).
   */
  private Timeseries toTuple(TimeseriesEntity row) {
    return new Timeseries(row);
  }
}
