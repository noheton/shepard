package de.dlr.shepard.data.timeseries.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity for the {@code timeseries} table + its {@code channel_metadata} side table.
 *
 * <p><b>Schema layout (TS-CORE-SCHEMA-01):</b>
 * <ul>
 *   <li>{@code timeseries}: id, container_id, value_type, shepard_id — the hot row</li>
 *   <li>{@code channel_metadata}: timeseries_id, container_id, measurement, field,
 *       device, location, symbolic_name — the identity side table</li>
 * </ul>
 *
 * <p>The {@code @SecondaryTable} join is:
 * {@code channel_metadata.timeseries_id = timeseries.id}.
 * Hibernate automatically JOINs the secondary table for all reads; callers see a flat
 * object with all fields populated as before.  The {@code channel_metadata.container_id}
 * column is a constraint-helper not managed by JPA — it is populated explicitly in the
 * native-SQL {@code upsert()} in {@link de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository}.
 *
 * <p><b>Important:</b> do NOT call {@code entityManager.persist()} directly on this entity —
 * use {@link de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository#upsert} which
 * handles both tables atomically via native SQL.
 *
 * <p><b>Future (TS-SEMANTIC-01):</b> {@code channel_metadata} is a stepping stone.
 * The 5-tuple will eventually migrate to {@code SemanticAnnotation / AnnotatableTimeseries}
 * Neo4j nodes and this side table will be dropped.
 */
@Entity
@Table(name = "timeseries")
@SecondaryTable(
  name = "channel_metadata",
  pkJoinColumns = @PrimaryKeyJoinColumn(name = "timeseries_id")
)
public class TimeseriesEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "container_id", nullable = false)
  private long containerId;

  // ── 5-tuple fields — stored in channel_metadata, not in timeseries ─────────

  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String measurement;

  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String field;

  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String device;

  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String location;

  @Column(table = "channel_metadata", name = "symbolic_name", columnDefinition = "TEXT", nullable = false)
  private String symbolicName;

  // ── Primary-table columns ────────────────────────────────────────────────────

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", columnDefinition = "TEXT", nullable = false)
  private DataPointValueType valueType;

  /**
   * Single-field channel identity introduced by the TS-ID migration
   * (PR-1 — supersedes the {@code :Timeseries} Neo4j node design in
   * aidocs/platform/87 §3 TS-IDa, which never existed). Set by the
   * Postgres column default ({@code gen_random_uuid()}) on insert when
   * the application doesn't supply one. Resolution from {@code shepardId}
   * to the 5-tuple is the job of
   * {@code de.dlr.shepard.data.timeseries.repositories.TsChannelResolver}.
   *
   * <p>Wire-surface naming: the {@code /v2/} API exposes this as
   * {@code shepardId} (additive — {@code appId} is unrelated; this is a
   * Postgres row, not a Neo4j node). The {@code /shepard/api/} v5 surface
   * does NOT expose this field — wire fidelity is non-negotiable.
   */
  @Column(name = "shepard_id", columnDefinition = "UUID", nullable = false, unique = true, updatable = false)
  private UUID shepardId;

  public TimeseriesEntity() {}

  public TimeseriesEntity(
    long containerId,
    String measurement,
    String field,
    String device,
    String location,
    String symbolicName,
    DataPointValueType valueType
  ) {
    this.containerId = containerId;
    this.measurement = measurement;
    this.field = field;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.valueType = valueType;
  }

  public TimeseriesEntity(long containerId, Timeseries timeseries, DataPointValueType valueType) {
    this(
      containerId,
      timeseries.getMeasurement(),
      timeseries.getField(),
      timeseries.getDevice(),
      timeseries.getLocation(),
      timeseries.getSymbolicName(),
      valueType
    );
  }

  public int getId() {
    return id;
  }

  public String getMeasurement() {
    return measurement;
  }

  public long getContainerId() {
    return containerId;
  }

  public String getDevice() {
    return device;
  }

  public String getLocation() {
    return location;
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  public String getField() {
    return field;
  }

  public DataPointValueType getValueType() {
    return valueType;
  }

  /**
   * Single-field channel identity. May be null on freshly-constructed
   * in-memory instances before the row is persisted (the Postgres
   * default fills it in on insert). Persisted rows always have a value.
   */
  public UUID getShepardId() {
    return shepardId;
  }

  public void setShepardId(UUID shepardId) {
    this.shepardId = shepardId;
  }

  @JsonIgnore
  public String getUniqueId() {
    return String.join("-", measurement, device, location, symbolicName, field, valueType.toString());
  }
}
