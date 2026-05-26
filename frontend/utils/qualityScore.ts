/**
 * AI1c — helper utilities for displaying the per-reference quality score
 * computed by {@code TimeseriesQualityScoringJob}.
 *
 * Score bands:
 *   ≥ 0.8  → green  ("success")
 *   ≥ 0.5  → amber  ("warning")
 *   < 0.5  → red    ("error")
 *   null   → not yet scored; display a dash
 */

export type QualityBand = "success" | "warning" | "error";

/**
 * Map a numeric quality score to a Vuetify semantic colour token.
 * Returns null when the score has not been computed yet.
 */
export function qualityScoreColor(score: number | null | undefined): QualityBand | null {
  if (score == null) return null;
  if (score >= 0.8) return "success";
  if (score >= 0.5) return "warning";
  return "error";
}

/**
 * Format a quality score for display in a chip.
 * Returns null when the score has not been computed yet (show "—" instead).
 */
export function qualityScoreLabel(score: number | null | undefined): string | null {
  if (score == null) return null;
  return score.toFixed(2);
}
