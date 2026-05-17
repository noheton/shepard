package de.dlr.shepard.v2.timeseries.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * AI1b — response body for
 * {@code POST /v2/timeseries-references/{refAppId}/detect-anomalies}.
 */
@Schema(description = "Result of a rolling-median MAD anomaly detection run.")
public record AnomalyDetectResultIO(
  @Schema(description = "List of detected contiguous anomaly intervals. Empty when the series is clean.")
  List<AnomalyIntervalIO> anomalies,

  @Schema(description = "Effective rolling window size used (after odd-forcing and series-length clamping).")
  int windowSize,

  @Schema(description = "Anomaly Z-score threshold used (k). A point is anomalous when |z| > threshold.")
  double threshold,

  @Schema(description = "Total number of data points in the series that were evaluated.")
  int totalPoints,

  @Schema(description = "Number of TimeseriesAnnotation nodes created (0 when createAnnotations=false).")
  int annotationsCreated
) {}
