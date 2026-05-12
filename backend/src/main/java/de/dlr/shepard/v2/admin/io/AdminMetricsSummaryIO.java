package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/admin/metrics-summary}.
 *
 * <p>Per {@code aidocs/51 §9.5} this is the link-only v1: a small set
 * of "instance health" stats the admin pane's strip cards render
 * directly, without the operator's browser talking to Prometheus.
 *
 * <p>Source of truth is the in-process Micrometer registry — the same
 * one that exports to Prometheus on {@code /shepard/doc/metrics/prometheus} —
 * so the endpoint works regardless of whether the monitoring compose
 * profile is up.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminMetricsSummary")
public class AdminMetricsSummaryIO {

  @Schema(required = true, description = "Heap bytes currently used by the JVM.")
  private long jvmHeapUsedBytes;

  @Schema(required = true, description = "Heap bytes maximum the JVM can grow to.")
  private long jvmHeapMaxBytes;

  @Schema(required = true, description = "JVM uptime in milliseconds.")
  private long uptimeMillis;

  @Schema(required = true, description = "Total HTTP requests observed by the embedded server since startup.")
  private long httpRequestsTotal;

  @Schema(required = false, nullable = true, description = "HTTP request total-time avg, milliseconds, since startup. Null when no requests yet.")
  private Double httpMeanRequestMillis;

  @Schema(required = true, description = "Permissions cache cumulative hits since startup.")
  private long permissionsCacheHits;

  @Schema(required = true, description = "Permissions cache cumulative misses since startup.")
  private long permissionsCacheMisses;

  @Schema(required = false, nullable = true, description = "Permissions cache hit ratio (hits / (hits+misses)), 0.0..1.0. Null when cache untouched.")
  private Double permissionsCacheHitRatio;
}
