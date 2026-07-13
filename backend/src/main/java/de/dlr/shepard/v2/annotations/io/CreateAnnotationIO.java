package de.dlr.shepard.v2.annotations.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-004 — request body for {@code POST /v2/annotations}.
 *
 * <p>Exactly one of {@link #objectLiteral} / {@link #objectIri} must be non-null.
 * {@link #subjectAppId} and {@link #subjectKind} identify the polymorphic
 * subject entity. {@link #predicateIri} is the controlled-vocabulary predicate.
 */
@Data
@NoArgsConstructor
@Schema(name = "CreateAnnotationV2", description = "SEMA-V6-004 — request body for creating a semantic annotation.")
public class CreateAnnotationIO {

  // ─── subject (required) ───────────────────────────────────────────────────

  @Schema(required = true, description = "appId of the entity to annotate (DataObject, Collection, etc.).")
  private String subjectAppId;

  @Schema(required = true, description = "Kind of the entity to annotate (e.g. 'DataObject', 'Collection').")
  private String subjectKind;

  // ─── predicate (required) ─────────────────────────────────────────────────

  @Schema(required = true, description = "IRI of the predicate (from a registered vocabulary).")
  private String predicateIri;

  @Schema(nullable = true, description = "Human-readable label for the predicate (snapshot; resolved if null).")
  private String predicateLabel;

  // ─── object (one required) ────────────────────────────────────────────────

  @Schema(nullable = true, description = "Literal text value. Exactly one of objectLiteral / objectIri must be non-null.")
  private String objectLiteral;

  @Schema(nullable = true, description = "IRI-typed object value. Exactly one of objectLiteral / objectIri must be non-null.")
  private String objectIri;

  @Schema(nullable = true, description = "QA-1 numeric value (for quantitative annotations).")
  private Double numericValue;

  @Schema(nullable = true, description = "QA-1 unit IRI (for quantitative annotations).")
  private String unitIri;

  // ─── vocabulary ───────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "appId of the Vocabulary supplying this predicate.")
  private String vocabularyId;

  // ─── provenance ───────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "f(ai)²r source mode: 'human', 'ai', or 'collaborative'. Defaults to 'human'.")
  private String sourceMode;

  @Schema(nullable = true, description = "appId of the Activity that triggered this annotation (for AI/collaborative modes).")
  private String sourceActivityAppId;

  // ─── temporal validity (optional) ─────────────────────────────────────────

  @Schema(nullable = true, description = "ISO 8601 UTC timestamp start of temporal validity (e.g. '2024-06-01T12:00:00Z'). Null = valid from creation.")
  private String validFrom;

  @Schema(nullable = true, description = "ISO 8601 UTC timestamp end of temporal validity. Null = currently asserted.")
  private String validUntil;

  // ─── confidence ───────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "Confidence score [0.0, 1.0]. Defaults to 1.0 for human writes.")
  private Double confidence;
}
