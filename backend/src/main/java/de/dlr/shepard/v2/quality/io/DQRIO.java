package de.dlr.shepard.v2.quality.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.quality.entities.DataQualityRequirement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL10 — wire representation of a persisted Data Quality Requirement.
 *
 * <p>Returned by GET and POST endpoints under
 * {@code /v2/collections/{appId}/dqr}.
 */
@Schema(description = "Persisted Data Quality Requirement returned by GET/POST endpoints under /v2/collections/{appId}/dqr.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DQRIO(
  String dqrAppId,
  String name,
  String description,
  String ruleType,
  String ruleParam,
  String severity,
  boolean enabled
) {
  /**
   * Map a persisted {@link DataQualityRequirement} to its wire form.
   *
   * @param d the entity to map (must not be null)
   * @return a populated {@code DQRIO}
   */
  public static DQRIO from(DataQualityRequirement d) {
    return new DQRIO(
      d.getAppId(),
      d.getName(),
      d.getDescription(),
      d.getRuleType(),
      d.getRuleParam(),
      d.getSeverity(),
      d.isEnabled()
    );
  }
}
