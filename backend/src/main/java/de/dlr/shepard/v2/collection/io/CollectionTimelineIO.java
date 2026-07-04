package de.dlr.shepard.v2.collection.io;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * COLL-TIMELINE-1 — top-level envelope returned by
 * {@code GET /v2/collections/{appId}/timeline}.
 *
 * <p>Models the campaign as a chronograph of swimlanes (one row per
 * process-type), each carrying day-binned counts of DataObjects with NCR /
 * REJECTED status counts overlaid. Adaptive bin size: 1 day / 7 days / 30 days
 * — server auto-coarsens so each lane fits in &le; 730 bins.
 *
 * <p>Design: GAP-8 of {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md}.
 */
@Schema(description = "Collection timeline envelope (swimlane chronograph).")
public class CollectionTimelineIO {

  @Schema(
    description =
      "Bin size in days actually used. Either honours the client's `?binSizeDays=` " +
      "request or auto-coarsens upward (1 → 7 → 30 → 90 → 365) so each lane fits " +
      "in &le; 730 bins. Echoed so the client renders the correct x-axis tick stride.",
    example = "1")
  private int binSizeDays;

  @Schema(
    description =
      "Earliest DataObject anchor timestamp considered in this response, " +
      "ISO-8601 UTC. Null when the Collection has no DataObjects.",
    example = "2023-03-20T00:00:00Z")
  private String rangeStart;

  @Schema(
    description =
      "Latest DataObject anchor timestamp considered in this response, ISO-8601 UTC. " +
      "Null when the Collection has no DataObjects.",
    example = "2025-11-12T23:59:59Z")
  private String rangeEnd;

  @Schema(
    description = "Total number of DataObjects observed across all lanes.",
    example = "8251")
  private long totalDataObjects;

  @Schema(
    description =
      "Lanes ordered by their first non-empty bin (earliest campaign-start first). " +
      "Empty list when the Collection has no DataObjects.")
  private List<CollectionTimelineLaneIO> lanes = new ArrayList<>();

  public CollectionTimelineIO() {}

  public CollectionTimelineIO(
      int binSizeDays,
      String rangeStart,
      String rangeEnd,
      long totalDataObjects,
      List<CollectionTimelineLaneIO> lanes) {
    this.binSizeDays = binSizeDays;
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
    this.totalDataObjects = totalDataObjects;
    this.lanes = lanes != null ? lanes : new ArrayList<>();
  }

  public int getBinSizeDays() { return binSizeDays; }
  public void setBinSizeDays(int binSizeDays) { this.binSizeDays = binSizeDays; }

  public String getRangeStart() { return rangeStart; }
  public void setRangeStart(String rangeStart) { this.rangeStart = rangeStart; }

  public String getRangeEnd() { return rangeEnd; }
  public void setRangeEnd(String rangeEnd) { this.rangeEnd = rangeEnd; }

  public long getTotalDataObjects() { return totalDataObjects; }
  public void setTotalDataObjects(long totalDataObjects) { this.totalDataObjects = totalDataObjects; }

  public List<CollectionTimelineLaneIO> getLanes() { return lanes; }
  public void setLanes(List<CollectionTimelineLaneIO> lanes) { this.lanes = lanes; }
}
