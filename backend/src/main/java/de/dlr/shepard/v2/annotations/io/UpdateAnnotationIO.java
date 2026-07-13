package de.dlr.shepard.v2.annotations.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-004 — request body for {@code PUT /v2/annotations/{appId}}.
 *
 * <p>RFC 7396 merge-patch semantics: null fields are left unchanged. Only
 * the object value, temporal validity, and confidence are patchable. The
 * subject and predicate of an annotation are immutable once created.
 */
@Data
@NoArgsConstructor
@Schema(name = "UpdateAnnotationV2", description = "SEMA-V6-004 — merge-patch body for updating a semantic annotation.")
public class UpdateAnnotationIO {

  // ─── object ────────────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "New literal text value (leave null to leave unchanged). " +
      "If provided, objectIri is cleared.")
  private String objectLiteral;

  @Schema(nullable = true, description = "New IRI-typed object value (leave null to leave unchanged). " +
      "If provided, objectLiteral is cleared.")
  private String objectIri;

  @Schema(nullable = true, description = "QA-1 numeric value update.")
  private Double numericValue;

  @Schema(nullable = true, description = "QA-1 unit IRI update.")
  private String unitIri;

  // ─── temporal validity ─────────────────────────────────────────────────────

  @Schema(nullable = true, description = "ISO 8601 UTC timestamp closing the prior validity window (e.g. '2024-06-01T12:00:00Z'). " +
      "Setting this to a past time soft-deletes the annotation.")
  private String validUntil;

  @Schema(nullable = true, description = "ISO 8601 UTC timestamp start of validity.")
  private String validFrom;

  // ─── confidence ────────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "Updated confidence score [0.0, 1.0].")
  private Double confidence;

  // ─── source (update mode tracking) ────────────────────────────────────────

  @Schema(nullable = true, description = "Updated provenance mode for this write.")
  private String sourceMode;
}
