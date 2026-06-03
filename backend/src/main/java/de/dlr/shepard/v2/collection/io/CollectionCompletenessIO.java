package de.dlr.shepard.v2.collection.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * RDM-005a(d) — response body for
 * {@code GET /v2/collections/{appId}/completeness}.
 *
 * <p>Mirrors the {@code MetadataCompletenessResult} TypeScript shape from
 * {@code frontend/utils/metadataCompleteness.ts}: same 9 checks, same
 * 100-point ceiling, same three score bands.
 *
 * <p>Score bands (matching the client-side Vuetify colour tokens):
 * <ul>
 *   <li>{@code "error"} — score < 50 (red; collection is not publication-ready)</li>
 *   <li>{@code "warning"} — 50 ≤ score < 80 (amber; missing key FAIR fields)</li>
 *   <li>{@code "success"} — score ≥ 80 (green; DMP-grade)</li>
 * </ul>
 *
 * <p>The {@code maxScore} field is always 100 today; it is included so
 * callers can compute percentage coverage without hard-coding the ceiling.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CollectionCompleteness")
public class CollectionCompletenessIO {

  @Schema(
    description = "Metadata completeness score (0–100). " +
    "Sum of points from all passing checks.",
    example = "40",
    minimum = "0",
    maximum = "100"
  )
  private int score;

  @Schema(
    description = "Maximum achievable score — today always 100 (sum of all check points).",
    example = "100"
  )
  private int maxScore;

  @Schema(
    description = "Score band: 'error' (score < 50), 'warning' (50 ≤ score < 80), " +
    "'success' (score ≥ 80). Mirrors the Vuetify colour token.",
    example = "error",
    enumeration = {"error", "warning", "success"}
  )
  private String band;

  @Schema(
    description = "Per-check breakdown in the same order as the client-side widget. " +
    "Always 9 entries; the sum of `points` values equals `maxScore`."
  )
  private List<CompletenessCheckIO> checks;
}
