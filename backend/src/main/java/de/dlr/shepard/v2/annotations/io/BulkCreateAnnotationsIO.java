package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — request body for
 * {@code POST /v2/semantic-annotations/bulk}.
 *
 * <p>Up to 100 entries per call (same cap as the {@code semantic_annotate_bulk}
 * MCP tool). Per-row error isolation: entries that fail individually are
 * reported in the {@code errors} array of the response; successful entries are
 * committed independently.
 */
@Schema(
  name = "BulkCreateAnnotationsV2",
  description = "SEMANTIC-ANNOTATE-BULK-REST-1 — request body for POST /v2/semantic-annotations/bulk. Max 100 entries."
)
public record BulkCreateAnnotationsIO(
  @Schema(required = true, description = "List of annotation entries to create. Max 100 per call.")
  List<BulkAnnotationEntryIO> entries
) {}
