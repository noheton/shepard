package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — request body for {@code POST /v2/annotations/bulk}.
 *
 * <p>Wraps up to {@link #MAX_SIZE} {@link CreateAnnotationIO} specs in one request,
 * letting non-MCP callers (UI mass-annotation, CLI sweeps, importer pipelines) share
 * the same bulk-annotate semantics as the MCP {@code semantic_annotate_bulk} tool.
 */
@Data
@NoArgsConstructor
@Schema(
  name = "BulkCreateAnnotationV2",
  description =
    "SEMANTIC-ANNOTATE-BULK-REST-1 — request body for POST /v2/annotations/bulk. " +
    "Wraps up to 100 CreateAnnotationV2 specs in a single request."
)
public class BulkCreateAnnotationIO {

  /** Hard cap on specs per call — same as MCP {@code semantic_annotate_bulk}. */
  public static final int MAX_SIZE = 100;

  @Schema(
    required = true,
    description =
      "Annotation specs to create in one round-trip. Up to " + MAX_SIZE + " entries. " +
      "Each spec has the same shape as the POST /v2/annotations request body " +
      "(CreateAnnotationV2): required subjectAppId, subjectKind, predicateIri; " +
      "exactly one of objectLiteral or objectIri; optional numericValue, unitIri, " +
      "vocabularyId, sourceMode, confidence, validFromMillis, validUntilMillis."
  )
  private List<CreateAnnotationIO> annotations;
}
