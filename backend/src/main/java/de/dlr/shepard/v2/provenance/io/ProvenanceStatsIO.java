package de.dlr.shepard.v2.provenance.io;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/provenance/stats}, per
 * {@code aidocs/55 §6}. Rolls totals + a sparkline buckets list +
 * action-kind histogram into one payload so the casual-user
 * dashboard renders in a single fetch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ProvenanceStats")
public class ProvenanceStatsIO {

  @Schema(required = true, description = "Scope of the query: 'instance', 'collection', 'user'.")
  private String scope;

  @Schema(required = false, nullable = true, description = "Entity / user id the scope narrows to. Null for scope=instance.")
  private String id;

  @Schema(required = true, description = "Inclusive lower bound of the window (millis since epoch).")
  private long sinceMillis;

  @Schema(required = true, description = "Inclusive upper bound of the window (millis since epoch).")
  private long untilMillis;

  @Schema(required = true, description = "Width of one sparkline bucket in millis (daily = 86_400_000; weekly = 604_800_000).")
  private long bucketMillis;

  @Schema(required = true, description = "Total activity count over the window.")
  private long totalCount;

  @Schema(required = true, description = "Distinct contributor count over the window (instance-wide or scope-narrowed).")
  private long distinctAgents;

  @Schema(required = true, description = "Per-action-kind totals: { CREATE: N, READ: M, UPDATE: O, DELETE: P, EXECUTE: Q }.")
  private Map<String, Long> totalsByActionKind;

  @Schema(
    required = true,
    description = "Sparkline buckets: each entry is [bucketStartMillis, count]. Empty buckets within the window are NOT filled."
  )
  private List<long[]> buckets;

  @Schema(
    required = true,
    description = "Cumulative-integral buckets: each entry is [bucketStartMillis, runningTotal]. Running sum of activity counts up to " +
    "and including the bucket — useful for 'total captured so far' tiles in the dashboard. Same bucket alignment as `buckets`."
  )
  private List<long[]> cumulative;

  @Schema(
    required = false,
    nullable = true,
    description = "Entity-count census for the scope at query time (NOT window-filtered). Keys: dataObjects, fileReferences, " +
    "timeseriesReferences, structuredDataReferences, spatialDataReferences, labJournalEntries. Drives the casual-user " +
    "dashboard's 'what's in here' tiles. Null when scope=user (the census is not per-user)."
  )
  private Map<String, Long> contentCensus;
}
