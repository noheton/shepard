package de.dlr.shepard.v2.provenance.io;

import de.dlr.shepard.auth.users.services.DisplayNameResolver;
import de.dlr.shepard.provenance.entities.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for one row in the {@code GET /v2/provenance/activities}
 * response. PROV-O-aligned but flat — the casual-user dashboard
 * doesn't render nested Agent/Entity blocks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Activity")
public class ActivityIO {

  @Schema(required = true, description = "Application-level identifier (UUID v7).")
  private String appId;

  @Schema(required = true, description = "CREATE | READ | UPDATE | DELETE | EXECUTE")
  private String actionKind;

  @Schema(required = false, nullable = true, description = "OGM label of the target entity, when known.")
  private String targetKind;

  @Schema(required = false, nullable = true, description = "appId of the target entity, when known.")
  private String targetAppId;

  @Schema(required = true, description = "Acting Agent's display name (UUID-shaped Keycloak subjects are redacted to their first 8 chars).")
  private String agentUsername;

  @Schema(required = true, description = "Short human-readable summary, ≤ 256 chars.")
  private String summary;

  @Schema(required = true, description = "Millis since epoch when the activity began.")
  private Long startedAtMillis;

  @Schema(required = true, description = "Millis since epoch when the activity ended.")
  private Long endedAtMillis;

  @Schema(required = false, nullable = true, description = "HTTP method that drove the activity, when captured via REST.")
  private String method;

  @Schema(required = false, nullable = true, description = "Request path that drove the activity, when captured via REST.")
  private String path;

  @Schema(required = false, nullable = true, description = "HTTP status code of the resulting response, when captured via REST.")
  private Integer status;

  @Schema(
    required = false,
    nullable = true,
    description = "Origin-instance identifier — 'local' by default; Edge-instance UUID on synced rows."
  )
  private String originInstance;

  /**
   * PROV1j (activity-layer) — EU AI Act Art. 50 per-artefact visibility.
   * {@code "human"} when the caller sent no {@code X-AI-Agent} header;
   * {@code "ai"} when the header was present and non-blank.
   * {@code null} for activities captured before PROV1j shipped (treat as
   * {@code "human"} at the consumer side).
   */
  @Schema(
    required = false,
    nullable = true,
    description = "PROV1j — agent mode: 'human' or 'ai'. Null for pre-PROV1j rows (treat as 'human')."
  )
  private String sourceMode;

  /**
   * PROV1j (activity-layer) — the value of the {@code X-AI-Agent} request
   * header, when present. {@code null} when {@link #sourceMode} is
   * {@code "human"} (header was absent or blank).
   */
  @Schema(
    required = false,
    nullable = true,
    description = "PROV1j — AI agent identifier from X-AI-Agent header (e.g. 'claude-sonnet-4-6'). Null for human callers."
  )
  private String agentId;

  public static ActivityIO from(Activity a) {
    return new ActivityIO(
      a.getAppId(),
      a.getActionKind(),
      a.getTargetKind(),
      a.getTargetAppId(),
      DisplayNameResolver.redactUsername(a.getAgentUsername()),
      a.getSummary(),
      a.getStartedAtMillis(),
      a.getEndedAtMillis(),
      a.getMethod(),
      a.getPath(),
      a.getStatus(),
      a.getOriginInstance(),
      a.getSourceMode(),
      a.getAgentId()
    );
  }

  /**
   * Return a copy with the request-shape fields (method / path / status
   * / endedAt / originInstance) cleared. Used by the
   * {@code ?profile=metadata} path to drop everything that isn't the
   * core PROV-O Activity surface.
   */
  public ActivityIO metadataOnly() {
    return new ActivityIO(appId, actionKind, targetKind, targetAppId, agentUsername, summary, startedAtMillis, null, null, null, null, null, sourceMode, agentId);
  }

  /**
   * Return a copy with only metadata + the {@code targetAppId} relation
   * (the only "relation" an Activity row carries — to its target
   * entity). Drops the request-shape fields the way
   * {@link #metadataOnly()} does.
   */
  public ActivityIO relationsOnly() {
    return new ActivityIO(appId, actionKind, targetKind, targetAppId, agentUsername, summary, startedAtMillis, null, null, null, null, null, sourceMode, agentId);
  }
}
