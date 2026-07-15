package de.dlr.shepard.v2.provenance.io;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

  /** One time-bucket entry in a sparkline series. */
  @Schema(name = "ProvenanceStatsBucket")
  public record BucketIO(
    @Schema(required = true, description = "ISO 8601 UTC start of this bucket (e.g. '2026-07-01T00:00:00Z').") String t,
    @Schema(required = true, description = "Activity count within this bucket.") long count
  ) {}

  @Schema(required = true, description = "Scope of the query: 'instance', 'collection', 'user'.")
  private String scope;

  @Schema(required = false, nullable = true, description = "Entity / user id the scope narrows to. Null for scope=instance.")
  private String id;

  @Schema(required = true, description = "Inclusive lower bound of the window (ISO 8601 UTC, e.g. '2026-01-01T00:00:00Z').")
  private String since;

  @Schema(required = true, description = "Inclusive upper bound of the window (ISO 8601 UTC, e.g. '2026-12-31T23:59:59Z').")
  private String until;

  @Schema(required = true, example = "PT86400S", description = "Width of one sparkline bucket as an ISO 8601 duration (daily = 'PT86400S'; weekly = 'PT604800S').")
  private String bucketDuration;

  @Schema(required = true, description = "Total activity count over the window.")
  private long totalCount;

  @Schema(required = true, description = "Distinct contributor count over the window (instance-wide or scope-narrowed).")
  private long distinctAgents;

  @Schema(required = true, description = "Per-action-kind totals: { CREATE: N, READ: M, UPDATE: O, DELETE: P, EXECUTE: Q }.")
  private Map<String, Long> totalsByActionKind;

  @Schema(
    required = true,
    description = "Sparkline buckets ordered by bucket-start time. Empty buckets within the window are NOT filled."
  )
  private List<BucketIO> buckets;

  @Schema(
    required = true,
    description = "Cumulative-integral buckets: running sum of activity counts up to and including each bucket — " +
    "useful for 'total captured so far' tiles in the dashboard. Same bucket alignment as `buckets`."
  )
  private List<BucketIO> cumulative;

  @Schema(
    required = false,
    nullable = true,
    description = "Entity-count census for the scope at query time (NOT window-filtered). Keys: dataObjects, fileReferences, " +
    "timeseriesReferences, structuredDataReferences, spatialDataReferences, labJournalEntries. Drives the casual-user " +
    "dashboard's 'what's in here' tiles. Null when scope=user (the census is not per-user)."
  )
  private Map<String, Long> contentCensus;

  @Schema(
    required = false,
    nullable = true,
    description = "Byte-size totals for the scope at query time (NOT window-filtered). Keys: `fileBytes` (sum of " +
    "`ShepardFile.fileSize` reachable from the scope). Pre-FB1a rows uploaded before `fileSize` capture landed " +
    "have `fileSize=null` and contribute 0 to the sum — the total is therefore a **lower bound** until those " +
    "rows are re-uploaded. Null when scope=user (the byte totals are not per-user). Open-shape: future kinds " +
    "(`timeseriesBytes`, `structuredDataBytes`) get added as separate keys without changing the schema."
  )
  private Map<String, Long> byteTotals;

  /** Converts an epoch-ms timestamp to an ISO 8601 UTC string. */
  public static String toIso(long epochMs) {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs));
  }
}
