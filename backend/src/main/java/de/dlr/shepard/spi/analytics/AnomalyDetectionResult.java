package de.dlr.shepard.spi.analytics;

import java.util.List;

/**
 * AT1 — result of a synchronous (in-process) anomaly detection run.
 *
 * <p>Field semantics mirror the in-tree {@code AnomalyDetectResultIO}:
 *
 * <ul>
 *   <li>{@code intervals} — contiguous anomalous runs, in time order;
 *       empty when no points crossed the threshold.</li>
 *   <li>{@code effectiveWindow} — the window size actually used (the
 *       caller's request value forced odd and clamped to series length).
 *       Surfaced so a client can see "I asked for 51 but got 49 because
 *       the series only had 49 points".</li>
 *   <li>{@code k} — the threshold actually applied (echoed verbatim from
 *       the input parameters).</li>
 *   <li>{@code totalPoints} — total numeric points scored. Less than the
 *       raw point count when non-numeric / null points were skipped.</li>
 * </ul>
 *
 * @param intervals       anomaly runs in time order; never {@code null}
 * @param effectiveWindow window actually applied
 * @param k               threshold actually applied
 * @param totalPoints     total numeric points scored
 */
public record AnomalyDetectionResult(
  List<AnomalyInterval> intervals,
  int effectiveWindow,
  double k,
  int totalPoints
) {
  public AnomalyDetectionResult {
    if (intervals == null) throw new IllegalArgumentException("intervals must not be null");
    intervals = List.copyOf(intervals);
  }
}
