package de.dlr.shepard.v2.annotations.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — response body for {@code POST /v2/annotations/bulk}.
 *
 * <p>Aggregates the per-row outcome ({@link #results}) with summary counters
 * ({@link #requested}, {@link #succeeded}, {@link #failed}). The batch is best-effort —
 * failed rows are recorded in {@link BulkAnnotationItemResultIO#getError()} but do not
 * abort the rest of the batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BulkAnnotationResult", description = "Response from POST /v2/annotations/bulk.")
public class BulkAnnotationResultIO {

  @Schema(required = true, description = "Number of annotation rows in the request.")
  private int requested;

  @Schema(required = true, description = "Number of rows that succeeded.")
  private int succeeded;

  @Schema(required = true, description = "Number of rows that failed.")
  private int failed;

  @Schema(required = true, description = "Per-row results, in the same order as the request items.")
  private List<BulkAnnotationItemResultIO> results;
}
