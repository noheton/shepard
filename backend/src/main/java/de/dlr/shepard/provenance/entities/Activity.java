package de.dlr.shepard.provenance.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * One row in shepard's provenance log — captures a single user-driven
 * mutation (or an opt-in read) against a shepard entity. Modelled on
 * W3C PROV-O {@code prov:Activity}: an {@code Agent} acted (via this
 * Activity) on an {@code Entity}.
 *
 * <p>Designed in {@code aidocs/55-provenance-and-activity-overhaul.md}.
 * Persisted as {@code (:Activity)} Neo4j nodes; the
 * {@code (:User)-[:WAS_ASSOCIATED_WITH]->(:Activity)} edge ties the
 * activity to the acting Agent; {@code :USED} / {@code :GENERATED}
 * edges (not yet wired in this slice) tie to the target Entity.
 *
 * <p>v1 captures POST / PUT / PATCH / DELETE on 2xx responses; read
 * capture is opt-in via {@code shepard.provenance.capture-reads}.
 */
@NodeEntity
@Data
@NoArgsConstructor
public class Activity implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam.
   */
  @Property("appId")
  private String appId;

  /**
   * One of {@code CREATE} / {@code READ} / {@code UPDATE} /
   * {@code DELETE} / {@code EXECUTE}. CRUD-shaped; {@code EXECUTE}
   * covers non-CRUD verbs (sTC ingest tick, export run, future
   * coordinator step).
   */
  @Property("actionKind")
  private String actionKind;

  /**
   * Target-entity kind label — e.g. {@code Collection}, {@code DataObject},
   * {@code FileBundle}, {@code TimeseriesReference}. Free-form; matches
   * the OGM label of the target where one exists. {@code null} for
   * activities that don't target a specific entity (e.g. login).
   */
  @Property("targetKind")
  private String targetKind;

  /**
   * Stable identifier of the target entity — its {@code appId} when
   * available, otherwise the OGM-id-as-string for legacy entities not
   * yet migrated to {@code appId} (the L2 chain in {@code aidocs/25}).
   * {@code null} when the activity has no single target (a bulk-search
   * read, for example).
   */
  @Property("targetAppId")
  private String targetAppId;

  /**
   * Username of the acting Agent. Always set — every captured activity
   * runs in an authenticated request context (the JWT filter ensures
   * this).
   */
  @Property("agentUsername")
  private String agentUsername;

  /**
   * Short human-readable summary — e.g. {@code "POST /v2/dataobjects"}
   * or {@code "Created DataObject 'TR-004'"}. Capped at 256 chars on
   * the wire; longer values truncated server-side.
   */
  @Property("summary")
  private String summary;

  /**
   * Millis since epoch when the activity started server-side. Stamped
   * by the capture filter at request-start.
   */
  @Property("startedAtMillis")
  private Long startedAtMillis;

  /**
   * Millis since epoch when the activity ended server-side. May equal
   * {@code startedAtMillis} for fire-and-forget activities (deletes).
   */
  @Property("endedAtMillis")
  private Long endedAtMillis;

  /**
   * HTTP method that drove the activity — captured for debuggability;
   * not part of the PROV-O semantics.
   */
  @Property("method")
  private String method;

  /**
   * Request path that drove the activity — captured for debuggability;
   * not part of the PROV-O semantics. Truncated at 1024 chars.
   */
  @Property("path")
  private String path;

  /**
   * Resulting HTTP status. Activities are only persisted for 2xx
   * responses (the filter short-circuits on 3xx/4xx/5xx) so this
   * field is typically in {@code [200, 299]}.
   */
  @Property("status")
  private Integer status;

  /**
   * Origin-instance identifier — set to the configured
   * {@code shepard.instance.id} (defaults to {@code "local"}). Edge
   * deployments stamp this with the Edge-instance UUID so post-sync
   * (per {@code aidocs/60}) the central knows which Edge the activity
   * came from.
   */
  @Property("originInstance")
  private String originInstance;

  public Activity(
    String actionKind,
    String targetKind,
    String targetAppId,
    String agentUsername,
    String summary,
    Long startedAtMillis
  ) {
    this.actionKind = actionKind;
    this.targetKind = targetKind;
    this.targetAppId = targetAppId;
    this.agentUsername = agentUsername;
    this.summary = summary;
    this.startedAtMillis = startedAtMillis;
  }

  /** For testing purposes only. */
  public Activity(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Activity other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
