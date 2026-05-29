package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
   * TS-IDc — container-scoped shepardId lookup. Resolves the channel by
   * {@code shepardId} and then guards that the row belongs to the named
   * container. This is the canonical single-key path on the
   * {@code /v2/timeseries-containers/{containerId}/...} surface: the
   * container scopes authorisation (Read permission is enforced by
   * {@code TimeseriesContainerService.getContainer(containerId)} before
   * this call) while {@code shepardId} pinpoints the channel.
   *
   * <p>The shepardId column carries a UNIQUE B-tree index
   * ({@code idx_timeseries_shepard_id}, per V1.11.0) so the underlying
   * lookup is an index-only scan; planning time still dominates execution
   * (per {@code TS-AUDIT-2026-05-24-009}, ~4.4 ms plan vs. ~0.17 ms exec
   * on the MFFD synthetic container — Hibernate first-call planning
   * caches subsequent calls). The post-filter on {@code containerId} is
   * a cheap in-memory predicate against the single returned row, not a
   * second SQL pass.
   *
   * <p>Returns {@link Optional#empty()} when the shepardId is unknown OR
   * when the matching row belongs to a different container — both
   * surface to callers as 404, indistinguishable on purpose: an
   * unauthorised peek across containers would leak the existence of a
   * channel.
   *
   * @param containerId the owning container's numeric id
   * @param shepardId   the channel's stable identity
   * @return the channel row, or {@link Optional#empty()} if no row matches
   *         or the row is in a different container
   */
  public Optional<TimeseriesEntity> findByContainerAndShepardId(long containerId, UUID shepardId) {
    return findByShepardId(shepardId).filter(row -> row.getContainerId() == containerId);
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
   * Look up channels for a container using a <em>partial</em> 5-tuple.
   *
   * <p>Only the predicates for non-null arguments are added to the WHERE clause.
   * {@code containerId} is always required. All null fields are treated as
   * "don't filter on this dimension" — i.e. they match any row.
   *
   * <p>The underlying {@code channel_metadata} UNIQUE index
   * {@code (container_id, measurement, field, symbolic_name, device, location)}
   * has {@code container_id} as its leading column, so all partial-tuple
   * combinations benefit from an index prefix scan. This makes the lookup
   * O(matching-channels) rather than O(all-channels-in-container).
   *
   * <p>Returns the full list of matching rows so that the caller can implement
   * its own cardinality check (0 → 404, 1 → ok, >1 → 400 ambiguous).
   *
   * <p>This is the PERF10 fix: the live-window endpoint previously called
   * {@code timeseriesRepository.list("containerId", id)} (a full scan) and
   * then filtered in Java. This method pushes all supplied filters into SQL.
   *
   * @param containerId  the owning timeseries container's numeric id (required)
   * @param measurement  filter value, or {@code null} to skip this dimension
   * @param device       filter value, or {@code null} to skip this dimension
   * @param location     filter value, or {@code null} to skip this dimension
   * @param symbolicName filter value, or {@code null} to skip this dimension
   * @param field        filter value, or {@code null} to skip this dimension
   * @return all channel rows that satisfy the supplied predicates
   */
  public List<TimeseriesEntity> findByContainerAndPartialTuple(
    long containerId,
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {
    StringBuilder query = new StringBuilder("containerId = :cid");
    Map<String, Object> params = new HashMap<>();
    params.put("cid", containerId);

    if (measurement != null) {
      query.append(" and measurement = :m");
      params.put("m", measurement);
    }
    if (device != null) {
      query.append(" and device = :dev");
      params.put("dev", device);
    }
    if (location != null) {
      query.append(" and location = :loc");
      params.put("loc", location);
    }
    if (symbolicName != null) {
      query.append(" and symbolicName = :sym");
      params.put("sym", symbolicName);
    }
    if (field != null) {
      query.append(" and field = :f");
      params.put("f", field);
    }

    return this.find(query.toString(), params).list();
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
