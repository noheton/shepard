package de.dlr.shepard.v2.annotations.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — per-entry error detail in a
 * {@link BulkCreateAnnotationsResultIO} response.
 */
@Schema(
  name = "BulkAnnotationErrorV2",
  description = "SEMANTIC-ANNOTATE-BULK-REST-1 — per-entry error detail in a bulk-create response."
)
public record BulkAnnotationErrorIO(
  @Schema(description = "Zero-based index of the entry in the request array that failed.")
  int index,

  @Schema(description = "dataObjectAppId of the entry that failed.")
  String dataObjectAppId,

  @Schema(description = "Predicate IRI of the entry that failed.")
  String predicate,

  @Schema(description = "Human-readable error message.")
  String message
) {}
