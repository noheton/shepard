package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-BATCH-01 — outcome for one item in a {@code POST /v2/data-objects/batch}
 * response (HTTP 207 Multi-Status).
 *
 * <p>Fields {@code appId}, {@code errorCode}, and {@code errorMessage} are
 * mutually exclusive between the {@code "created"} and {@code "error"} states:
 * <ul>
 *   <li>On success: {@code status="created"}, {@code appId} non-null,
 *       {@code errorCode} and {@code errorMessage} are null (omitted from JSON).</li>
 *   <li>On failure: {@code status="error"}, {@code errorCode} non-null,
 *       {@code errorMessage} non-null, {@code appId} is null (omitted from JSON).</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
  name = "DataObjectBatchResultItem",
  description =
    "Per-item outcome within a batch create response (HTTP 207 Multi-Status). " +
    "On success: status='created', appId populated. " +
    "On failure: status='error', errorCode + errorMessage populated."
)
public class DataObjectBatchResultItemIO {

  @Schema(
    required = true,
    description = "Zero-based position of this item in the input array."
  )
  private int index;

  @Schema(
    required = true,
    description = "Outcome: \"created\" on success, \"error\" on per-item failure."
  )
  private String status;

  @Schema(
    nullable = true,
    description =
      "The appId (UUID v7) minted for the created DataObject. " +
      "Null (omitted) when status is \"error\"."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String appId;

  @Schema(
    nullable = true,
    description =
      "Machine-readable error code. Null (omitted) when status is \"created\". " +
      "Known values: COLLECTION_NOT_FOUND, FORBIDDEN, INVALID_INPUT, " +
      "PARENT_NOT_FOUND, INTERNAL_ERROR."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String errorCode;

  @Schema(
    nullable = true,
    description =
      "Human-readable error description. Null (omitted) when status is \"created\"."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String errorMessage;

  // ── static factories ────────────────────────────────────────────────────

  /**
   * Builds a {@code "created"} result for the item at {@code index} with the
   * minted {@code appId}.
   */
  public static DataObjectBatchResultItemIO success(int index, String appId) {
    return new DataObjectBatchResultItemIO(index, "created", appId, null, null);
  }

  /**
   * Builds an {@code "error"} result for the item at {@code index}.
   *
   * @param errorCode    machine-readable code (e.g. {@code COLLECTION_NOT_FOUND})
   * @param errorMessage human-readable detail
   */
  public static DataObjectBatchResultItemIO error(int index, String errorCode, String errorMessage) {
    return new DataObjectBatchResultItemIO(index, "error", null, errorCode, errorMessage);
  }
}
