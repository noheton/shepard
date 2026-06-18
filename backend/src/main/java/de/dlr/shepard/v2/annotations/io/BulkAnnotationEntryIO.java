package de.dlr.shepard.v2.annotations.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — one entry in a
 * {@link BulkCreateAnnotationsIO} request body.
 *
 * <p>Field semantics mirror the single-create fields of
 * {@link CreateAnnotationIO} except that {@code subjectKind} is fixed to
 * {@code "DataObject"} for the v0 bulk surface (the most common caller shape —
 * UI mass-annotation sweeps and CLI import pipelines annotating DataObject
 * sets). Callers needing {@code subjectKind != "DataObject"} should use the
 * single-create endpoint.
 */
@Schema(
  name = "BulkAnnotationEntryV2",
  description = "SEMANTIC-ANNOTATE-BULK-REST-1 — one annotation to create in a bulk call."
)
public record BulkAnnotationEntryIO(

  @Schema(required = true, description = "appId of the DataObject to annotate.")
  String dataObjectAppId,

  @Schema(required = true, description = "appId of the Vocabulary that defines the predicate (may be null for unvocabularied predicates).")
  String vocabularyId,

  @Schema(required = true, description = "IRI of the predicate (from a registered vocabulary).")
  String predicate,

  @Schema(required = true, description = "Literal text value for the annotation.")
  String value
) {}
