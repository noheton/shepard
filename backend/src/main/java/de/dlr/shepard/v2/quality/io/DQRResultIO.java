package de.dlr.shepard.v2.quality.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * TPL10 — a single evaluation result for one DQR × DataObject pair.
 *
 * <p>Returned as a list by
 * {@code POST /v2/collections/{appId}/dqr/evaluate}.
 *
 * <p>A result where {@code passed == true} means the DQR was satisfied for
 * that DataObject. A result where {@code passed == false} means the rule was
 * violated; {@code message} carries a human-readable explanation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DQRResultIO(
  /** AppId of the evaluated DQR. */
  String dqrAppId,

  /** AppId of the DataObject being evaluated. May be {@code null} for
   *  collection-level DQRs (e.g. FILE_COUNT_MIN) that are not per-DataObject. */
  String dataObjectAppId,

  /** {@code true} when the DQR passed for this DataObject; {@code false} when violated. */
  boolean passed,

  /** Human-readable explanation; non-null only when {@code passed == false}. */
  String message
) {
  /** Convenience constructor for a passing result. */
  public static DQRResultIO pass(String dqrAppId, String dataObjectAppId) {
    return new DQRResultIO(dqrAppId, dataObjectAppId, true, null);
  }

  /** Convenience constructor for a failing result. */
  public static DQRResultIO fail(String dqrAppId, String dataObjectAppId, String message) {
    return new DQRResultIO(dqrAppId, dataObjectAppId, false, message);
  }
}
