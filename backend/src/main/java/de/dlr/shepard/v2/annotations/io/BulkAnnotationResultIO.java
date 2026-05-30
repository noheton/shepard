package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — response body for {@code POST /v2/annotations/bulk}.
 *
 * <p>Top-level summary counts plus a per-row result list so callers can
 * identify exactly which rows failed without re-submitting the whole batch.
 */
@Data
@NoArgsConstructor
@Schema(
  name = "BulkAnnotationResult",
  description = "SEMANTIC-ANNOTATE-BULK-REST-1 — bulk annotation creation result."
)
public class BulkAnnotationResultIO {

  @Schema(description = "Total number of annotation payloads received in the request.")
  private int requested;

  @Schema(description = "Number of annotations successfully created.")
  private int succeeded;

  @Schema(description = "Number of annotations that failed (validation or permission error).")
  private int failed;

  @Schema(description = "Per-row result — one entry per input row, in input order.")
  private List<RowResult> results;

  // ─── per-row shape ────────────────────────────────────────────────────────

  @Data
  @NoArgsConstructor
  @Schema(name = "BulkAnnotationRowResult", description = "Result for a single row in a bulk annotation request.")
  public static class RowResult {

    @Schema(description = "Zero-based index of the row in the input list.")
    private int index;

    @Schema(description = "True if the annotation was created successfully.")
    private boolean ok;

    @Schema(nullable = true, description = "UUID v7 of the created annotation. Present only when ok=true.")
    private String appId;

    @Schema(nullable = true, description = "Human-readable error message. Present only when ok=false.")
    private String error;

    public static RowResult success(int index, String appId) {
      RowResult r = new RowResult();
      r.index = index;
      r.ok = true;
      r.appId = appId;
      return r;
    }

    public static RowResult failure(int index, String error) {
      RowResult r = new RowResult();
      r.index = index;
      r.ok = false;
      r.error = error;
      return r;
    }
  }
}
