package de.dlr.shepard.spi.analytics;

import java.util.Map;

/**
 * AT1 — input bundle handed to a {@link TimeseriesAnalytics} detector.
 *
 * <p>Decouples the detector contract from the in-tree
 * {@code TimeseriesReference} / {@code TimeseriesDataPoint} types so a
 * plugin compiles only against {@code shepard-spi-analytics} classes —
 * the same isolation principle the {@code PayloadKind} SPI uses.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code timestamps} and {@code values} are parallel arrays; the
 *       {@code i}-th data point is at {@code timestamps[i]} with value
 *       {@code values[i]}. Caller has already filtered non-numeric
 *       points.</li>
 *   <li>{@code parameters} is a free-form key-value map (detector-specific
 *       knobs — e.g. {@code window=51, k=6.0}). Detectors validate /
 *       default their own keys.</li>
 *   <li>{@code referenceAppId} is the opaque appId of the source
 *       container/reference, surfaced for logging / provenance but never
 *       used as a re-resolution key by the detector itself.</li>
 * </ul>
 *
 * @param timestamps     epoch-nanos per data point (parallel to {@code values})
 * @param values         numeric measurement value per data point
 * @param parameters     detector-specific configuration (e.g. window, k);
 *                       never {@code null} (use an empty map)
 * @param referenceAppId opaque source reference appId (for logging /
 *                       provenance); may be {@code null}
 */
public record TimeseriesAnalyticsInput(
  long[] timestamps,
  double[] values,
  Map<String, Object> parameters,
  String referenceAppId
) {
  public TimeseriesAnalyticsInput {
    if (timestamps == null) throw new IllegalArgumentException("timestamps must not be null");
    if (values == null) throw new IllegalArgumentException("values must not be null");
    if (timestamps.length != values.length) {
      throw new IllegalArgumentException(
        "timestamps and values must be parallel; got " + timestamps.length + " vs " + values.length
      );
    }
    if (parameters == null) parameters = Map.of();
  }
}
