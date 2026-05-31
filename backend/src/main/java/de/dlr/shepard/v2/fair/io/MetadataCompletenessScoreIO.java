package de.dlr.shepard.v2.fair.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FAIR4 — response body for
 * {@code GET /v2/collections/{appId}/metadata-completeness}.
 *
 * <p>Carries the composite 0–100 score, the per-check breakdown, and the
 * collectionAppId so callers that aggregate across multiple collections can
 * correlate results without re-parsing the request path.
 *
 * <p>Score bands (mirror the client-side thresholds in
 * {@code frontend/utils/metadataCompleteness.ts}):
 * <ul>
 *   <li>{@code score < 50} — not publication-ready</li>
 *   <li>{@code 50 ≤ score < 80} — missing key FAIR fields</li>
 *   <li>{@code score ≥ 80} — DMP-grade</li>
 * </ul>
 *
 * <p>Cross-references: {@code aidocs/16} FAIR4 row; {@code aidocs/34}
 * additive endpoint entry; {@code aidocs/44} feature matrix.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MetadataCompletenessScore")
public class MetadataCompletenessScoreIO {

  @Schema(
    description = "appId of the Collection this score was computed for.",
    readOnly = true,
    example = "018f9c5a-7e26-7000-a000-000000000099"
  )
  private String collectionAppId;

  @Schema(
    description = "Computed completeness score in the range 0–100 (inclusive). " +
      "Bands: < 50 = not publication-ready; 50–79 = missing key FAIR fields; ≥ 80 = DMP-grade.",
    readOnly = true,
    minimum = "0",
    maximum = "100",
    example = "75"
  )
  private int score;

  @Schema(
    description = "Maximum achievable score (100). Included for future-proofing — if new checks " +
      "are added the total may change; clients should compute percentage as score/maxScore.",
    readOnly = true,
    example = "100"
  )
  private int maxScore;

  @Schema(
    description = "Score expressed as a percentage (0.0–100.0, two decimal places). " +
      "Equals 100.0 * score / maxScore.",
    readOnly = true,
    example = "75.0"
  )
  private double percentage;

  @Schema(
    description = "Per-check breakdown in the same order as the client-side widget renders them. " +
      "Each entry carries checkId, label, passed, weight, and an optional hint.",
    readOnly = true
  )
  private List<CheckResultIO> checks;
}
