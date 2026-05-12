package de.dlr.shepard.v2.provenance.io;

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

  @Schema(required = true, description = "Acting Agent's username.")
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

  public static ActivityIO from(Activity a) {
    return new ActivityIO(
      a.getAppId(),
      a.getActionKind(),
      a.getTargetKind(),
      a.getTargetAppId(),
      a.getAgentUsername(),
      a.getSummary(),
      a.getStartedAtMillis(),
      a.getEndedAtMillis(),
      a.getMethod(),
      a.getPath(),
      a.getStatus(),
      a.getOriginInstance()
    );
  }
}
