package de.dlr.shepard.v2.collection.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * COLL-TIMELINE-1 — single day-bin row inside a swimlane.
 *
 * <p>A bin counts the DataObjects that fall into a {@code [day, day+binSizeDays)}
 * window for a specific process-type lane. The day key is the ISO date of the
 * bin's first day in UTC (e.g. {@code "2023-04-15"}); when {@code binSizeDays}
 * is &gt; 1, that day is the start of the window.
 */
@Schema(description = "Single time-bin row inside a CollectionTimelineLane.")
public class CollectionTimelineBinIO {

  @Schema(description = "First day of the bin window (UTC ISO date, e.g. \"2023-04-15\").")
  private String day;

  @Schema(description = "Total DataObjects whose anchor timestamp falls in this bin.")
  private long count;

  @Schema(
    description =
      "DataObjects whose status is NCR-flavoured (NCR_OPEN, CONCESSION_PENDING). " +
      "Counted from the same set as count — ncrCount <= count.")
  private long ncrCount;

  @Schema(
    description =
      "DataObjects whose status is REJECTED. Counted from the same set as count — " +
      "rejectCount <= count.")
  private long rejectCount;

  public CollectionTimelineBinIO() {}

  public CollectionTimelineBinIO(String day, long count, long ncrCount, long rejectCount) {
    this.day = day;
    this.count = count;
    this.ncrCount = ncrCount;
    this.rejectCount = rejectCount;
  }

  public String getDay() { return day; }
  public void setDay(String day) { this.day = day; }

  public long getCount() { return count; }
  public void setCount(long count) { this.count = count; }

  public long getNcrCount() { return ncrCount; }
  public void setNcrCount(long ncrCount) { this.ncrCount = ncrCount; }

  public long getRejectCount() { return rejectCount; }
  public void setRejectCount(long rejectCount) { this.rejectCount = rejectCount; }
}
