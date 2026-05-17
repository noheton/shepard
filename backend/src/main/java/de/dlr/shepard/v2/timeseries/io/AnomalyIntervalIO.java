package de.dlr.shepard.v2.timeseries.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AI1b — one contiguous run of anomalous data points detected by the
 * rolling-median MAD algorithm.
 *
 * <p>{@code startNs} and {@code endNs} are both set and refer to the
 * first and last anomalous sample's timestamps (nanoseconds since Unix
 * epoch). For a single-point anomaly they are equal.
 */
@Schema(description = "A contiguous interval of anomalous data points detected by the rolling-median MAD algorithm.")
public record AnomalyIntervalIO(
  @Schema(description = "Timestamp of the first anomalous point in the run, in nanoseconds since Unix epoch.")
  long startNs,

  @Schema(description = "Timestamp of the last anomalous point in the run, in nanoseconds since Unix epoch. Equal to startNs for single-point anomalies.")
  long endNs,

  @Schema(description = "The raw value at the sample with the highest absolute Z-score within this interval.")
  double peakValue,

  @Schema(description = "The highest absolute Z-score (|z|) across all samples in this interval.")
  double maxZScore
) {}
