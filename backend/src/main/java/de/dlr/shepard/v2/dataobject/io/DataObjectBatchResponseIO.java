package de.dlr.shepard.v2.dataobject.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-BATCH-01 — top-level response body for {@code POST /v2/data-objects/batch}
 * (HTTP 207 Multi-Status).
 *
 * <p>Always returned with HTTP 207, even when {@code failed == results.size()}
 * (all items failed). A genuine 500 is only returned for server-level errors
 * (DB unreachable, NPE) that prevent any processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
  name = "DataObjectBatchResponse",
  description =
    "HTTP 207 Multi-Status response from POST /v2/data-objects/batch. " +
    "\"created\" + \"failed\" must equal results.size(). " +
    "Each result item carries the per-item outcome (appId or errorCode)."
)
public class DataObjectBatchResponseIO {

  @Schema(
    required = true,
    description = "Number of items that were successfully created."
  )
  private int created;

  @Schema(
    required = true,
    description = "Number of items that failed."
  )
  private int failed;

  @Schema(
    required = true,
    description =
      "Per-item outcomes in the same order as the input array (0-indexed). " +
      "Count equals the input array length."
  )
  private List<DataObjectBatchResultItemIO> results;
}
