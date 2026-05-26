package de.dlr.shepard.v2.annotations.io;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-004 — v6 JSON wire shape for a {@link SemanticAnnotation}.
 *
 * <p>All v6 fields are included; legacy fields ({@code propertyName},
 * {@code propertyIRI}, {@code valueName}, {@code valueIRI}) are preserved for
 * backward compatibility with callers that already consume the v1 surface.
 *
 * <p>The JSON shape follows §3.2 of
 * {@code aidocs/semantics/100-consistent-semantic-annotation-design.md} but uses
 * flat fields rather than nested objects for serialisation simplicity.
 */
@Data
@NoArgsConstructor
@Schema(name = "AnnotationV2", description = "SEMA-V6-004 — full v6 semantic annotation response shape.")
public class AnnotationIO {

  // ─── identity ─────────────────────────────────────────────────────────────

  @Schema(readOnly = true, required = true, description = "UUID v7 of this annotation.")
  private String appId;

  // ─── subject (polymorphic) ─────────────────────────────────────────────────

  @Schema(nullable = true, description = "Kind of the annotated entity " +
      "(e.g. 'DataObject', 'Collection', 'FileReference'). Null on legacy rows.")
  private String subjectKind;

  @Schema(nullable = true, description = "appId of the annotated entity. Null on legacy rows.")
  private String subjectAppId;

  // ─── predicate ─────────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "IRI of the predicate (controlled-vocabulary).")
  private String predicateIri;

  @Schema(nullable = true, description = "Human-readable label snapshot of the predicate.")
  private String predicateLabel;

  @Schema(nullable = true, description = "appId of the Vocabulary that defines this predicate. Null means free-form.")
  private String vocabularyId;

  // ─── object ────────────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "Literal text value of the annotation. " +
      "Exactly one of objectLiteral / objectIri is non-null.")
  private String objectLiteral;

  @Schema(nullable = true, description = "IRI-typed object value. " +
      "Exactly one of objectLiteral / objectIri is non-null.")
  private String objectIri;

  @Schema(nullable = true, description = "QA-1 numeric value (for quantitative annotations).")
  private Double numericValue;

  @Schema(nullable = true, description = "QA-1 unit IRI (for quantitative annotations).")
  private String unitIri;

  // ─── provenance / source ───────────────────────────────────────────────────

  @Schema(nullable = true, description = "f(ai)²r provenance mode: 'human', 'ai', or 'collaborative'. Null on legacy rows.")
  private String sourceMode;

  @Schema(nullable = true, description = "appId of the Activity that captured this annotation write.")
  private String sourceActivityAppId;

  // ─── temporal validity ─────────────────────────────────────────────────────

  @Schema(nullable = true, description = "Epoch-millis at which this annotation becomes valid. Null = valid from creation.")
  private Long validFromMillis;

  @Schema(nullable = true, description = "Epoch-millis at which this annotation expires. Null = no expiry (currently asserted).")
  private Long validUntilMillis;

  // ─── confidence ────────────────────────────────────────────────────────────

  @Schema(nullable = true, description = "Confidence score [0.0, 1.0]. Null = not specified (human writes default 1.0).")
  private Double confidence;

  // ─── legacy fields (backward compat) ──────────────────────────────────────

  @Schema(nullable = true, description = "Legacy: human-readable predicate label snapshot (mapped from propertyName).")
  private String propertyName;

  @Schema(nullable = true, description = "Legacy: predicate IRI (mapped from propertyIRI; same as predicateIri).")
  private String propertyIri;

  @Schema(nullable = true, description = "Legacy: object label snapshot (mapped from valueName).")
  private String valueName;

  @Schema(nullable = true, description = "Legacy: object IRI (mapped from valueIRI; same as objectIri).")
  private String valueIri;

  // ─── constructor from entity ───────────────────────────────────────────────

  /**
   * Construct from a {@link SemanticAnnotation} entity, mapping all v6 +
   * legacy fields. Legacy callers see the original {@code property*} / {@code value*}
   * names; v6 callers see the new flattened names.
   */
  public AnnotationIO(SemanticAnnotation a) {
    this.appId = a.getAppId();

    // v6 subject
    this.subjectKind = a.getSubjectKind();
    this.subjectAppId = a.getSubjectAppId();

    // predicate
    this.predicateIri = a.getPropertyIRI();
    this.predicateLabel = a.getPropertyName();
    this.vocabularyId = a.getVocabularyId();

    // object — objectLiteral from valueName if no valueIRI, else objectIri
    this.objectIri = a.getValueIRI();
    this.objectLiteral = a.getValueIRI() == null ? a.getValueName() : null;
    this.numericValue = a.getNumericValue();
    this.unitIri = a.getUnitIRI();

    // provenance
    this.sourceMode = a.getSourceMode();
    this.sourceActivityAppId = a.getSourceActivityAppId();

    // temporal
    this.validFromMillis = a.getValidFromMillis();
    this.validUntilMillis = a.getValidUntilMillis();

    // confidence
    this.confidence = a.getConfidence();

    // legacy aliases
    this.propertyName = a.getPropertyName();
    this.propertyIri = a.getPropertyIRI();
    this.valueName = a.getValueName();
    this.valueIri = a.getValueIRI();
  }
}
