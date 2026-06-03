package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — response body for
 * {@code POST /v2/semantic-annotations/bulk}.
 *
 * <p>{@code created} + {@code failed} = total entries submitted. The
 * {@code errors} list carries one entry per failed row with its zero-based
 * index, {@code dataObjectAppId}, predicate, and a human-readable message.
 */
@Schema(
  name = "BulkCreateAnnotationsResultV2",
  description = "SEMANTIC-ANNOTATE-BULK-REST-1 — response body for POST /v2/semantic-annotations/bulk."
)
public record BulkCreateAnnotationsResultIO(
  @Schema(description = "Number of annotations successfully created.")
  int created,

  @Schema(description = "Number of entries that failed to create.")
  int failed,

  @Schema(description = "Per-entry error details. Empty when failed=0.")
  List<BulkAnnotationErrorIO> errors
) {}
