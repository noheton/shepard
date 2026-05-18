package de.dlr.shepard.v2.timeseriescontainer.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * TS_CHART_VIEW1 — per-container persisted chart-overview selection.
 *
 * <p>One node per TimeseriesContainer, lazily created on first PATCH.
 * Holds the curated subset of channels the container's "Channel
 * Overview" chart renders by default. Mirrors the
 * {@code :InstanceRorConfig} / {@code :SqlTimeseriesConfig} / etc.
 * pattern (CLAUDE.md "Always: surface operator knobs in the admin
 * config") but scoped per-container, not per-instance.
 *
 * <p><b>Auth gate.</b> Mutation requires Write permission on the
 * referenced container; read inherits Read permission. The selection
 * is <em>shared</em> across all users — writers shape what other
 * users see. Per-session "Show all channels" overrides live in the
 * browser, not here.
 *
 * <p>Each entry in {@link #selectedChannels} is the 5-tuple
 * channel-key {@code measurement|device|location|symbolicName|field}
 * (pipe-separated, the same shape the frontend uses for v-treeview
 * keys and the timeseries-reference chart). The list is empty by
 * default, which the frontend interprets as "no curated view —
 * fall back to showing all channels".
 *
 * <p><b>Constraint.</b> {@code V47__Add_appId_constraint_TimeseriesContainerChartView.cypher}
 * adds {@code REQUIRE n.appId IS UNIQUE} on
 * {@code :TimeseriesContainerChartView}.
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TimeseriesContainerChartView implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * AppId of the {@link de.dlr.shepard.data.timeseries.model.TimeseriesContainer}
   * this view belongs to. Used to look the view up by container —
   * one view per container. Unique in practice (no Cypher uniqueness
   * constraint yet; the service layer enforces single-instance on save).
   */
  @Property("containerAppId")
  private String containerAppId;

  /**
   * Curated channel selection. Each entry is the 5-tuple
   * {@code measurement|device|location|symbolicName|field} (pipe
   * separator, all five segments present, empty segments rendered
   * as the empty string).
   *
   * <p>OGM stores list-of-string properties natively; no separator
   * escaping concerns since the pipe character is not valid in any
   * of the five segments (per shepard's timeseries ingest validation).
   *
   * <p>Empty list means "no curated view configured" — frontend
   * shows all channels (today's behaviour).
   */
  @Property("selectedChannels")
  private List<String> selectedChannels = new ArrayList<>();

  /** ISO-8601 millis-epoch of the last PATCH. Audit / display only. */
  @Property("updatedAt")
  private Long updatedAt;

  /**
   * Username of the writer that performed the last PATCH. Audit /
   * display only.
   */
  @Property("updatedBy")
  private String updatedBy;

  /** For testing only. */
  public TimeseriesContainerChartView(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TimeseriesContainerChartView other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
