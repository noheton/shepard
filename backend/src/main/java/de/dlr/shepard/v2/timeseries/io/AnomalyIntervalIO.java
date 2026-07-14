package de.dlr.shepard.v2.timeseries.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AI1b — one contiguous run of anomalous data points detected by the
 * rolling-median MAD algorithm.
 *
 * <p>{@code start} and {@code end} are ISO 8601 UTC strings with nanosecond
 * precision (e.g. {@code "2024-06-01T12:00:00.000000001Z"}) referring to the
 * first and last anomalous sample's timestamps. For a single-point anomaly
 * they are equal.
 */
@Schema(description = "A contiguous interval of anomalous data points detected by the rolling-median MAD algorithm.")
public record AnomalyIntervalIO(
  @Schema(description = "Timestamp of the first anomalous point in the run, as ISO 8601 UTC with nanosecond precision.")
  String start,

  @Schema(description = "Timestamp of the last anomalous point in the run, as ISO 8601 UTC with nanosecond precision. Equal to start for single-point anomalies.")
  String end,

  @Schema(description = "The raw value at the sample with the highest absolute Z-score within this interval.")
  double peakValue,

  @Schema(description = "The highest absolute Z-score (|z|) across all samples in this interval.")
  double maxZScore
) {}
