package de.dlr.shepard.v2.collection.services;

import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineAggregate;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineRow;
import de.dlr.shepard.v2.collection.io.CollectionTimelineBinIO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineIO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineLaneIO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * COLL-TIMELINE-1 — pure-Java post-processing from
 * {@link CollectionTimelineDAO.TimelineAggregate} into the wire
 * {@link CollectionTimelineIO} envelope.
 *
 * <p>Why a separate class: the DAO returns a flat aggregate (one row per
 * lane + day). The wire shape is a nested swimlane envelope with adaptive
 * bin coarsening. Splitting the DB hit from the shape-build keeps both
 * pieces unit-testable in isolation — the resource test wires a fake DAO,
 * and this builder is tested directly with synthetic rows (no Cypher).
 *
 * <p>Bin-size auto-coarsening: when the campaign span divided by the
 * requested {@code binSizeDays} exceeds {@link #MAX_BINS_PER_LANE},
 * coarsen upward through the ladder {@code 1 → 7 → 30 → 90 → 365} until
 * we fit. This caps the response payload at roughly
 * {@code lanes × 730 × bin-bytes} regardless of campaign duration.
 */
public final class CollectionTimelineBuilder {

  /** Soft cap on bins per lane. 730 ≈ two years of daily bins. */
  public static final int MAX_BINS_PER_LANE = 730;

  /** Auto-coarsen ladder. */
  public static final int[] BIN_LADDER = { 1, 7, 30, 90, 365 };

  private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final long DAY_MILLIS = 86_400_000L;

  private CollectionTimelineBuilder() {}

  /**
   * Build the wire envelope.
   *
   * @param aggregate          DAO output (rows already grouped by lane + day)
   * @param requestedBinDays   client-requested bin size; clamped + coarsened
   *                           internally so the response always fits the cap
   * @return populated {@link CollectionTimelineIO}; never null
   */
  public static CollectionTimelineIO build(TimelineAggregate aggregate, int requestedBinDays) {
    if (aggregate == null) {
      return new CollectionTimelineIO(1, null, null, 0L, List.of());
    }
    int effective = effectiveBinSizeDays(aggregate, requestedBinDays);

    List<TimelineRow> rows = aggregate.rows();
    long total = aggregate.totalDataObjects();
    String rangeStart = toIsoUtc(aggregate.minCreatedAtEpochMillis());
    String rangeEnd = toIsoUtc(aggregate.maxCreatedAtEpochMillis());

    if (rows.isEmpty()) {
      return new CollectionTimelineIO(effective, rangeStart, rangeEnd, total, List.of());
    }

    // Group rows by lane preserving first-seen ordering for stable lane order.
    Map<String, List<TimelineRow>> byLane = new LinkedHashMap<>();
    for (TimelineRow r : rows) {
      byLane.computeIfAbsent(r.laneKey(), k -> new ArrayList<>()).add(r);
    }

    // Lane order: earliest first-bin first. Stable secondary sort by key
    // so lanes that share a start day (rare but possible) order
    // deterministically.
    List<Map.Entry<String, List<TimelineRow>>> ordered = new ArrayList<>(byLane.entrySet());
    ordered.sort((a, b) -> {
      long aMin = minDay(a.getValue());
      long bMin = minDay(b.getValue());
      if (aMin != bMin) return Long.compare(aMin, bMin);
      return a.getKey().compareTo(b.getKey());
    });

    List<CollectionTimelineLaneIO> lanes = new ArrayList<>(ordered.size());
    for (var entry : ordered) {
      lanes.add(buildLane(entry.getKey(), entry.getValue(), effective));
    }

    return new CollectionTimelineIO(effective, rangeStart, rangeEnd, total, lanes);
  }

  /**
   * Compute the effective bin size by snapping the requested size up the
   * ladder until {@code span / binSize ≤ MAX_BINS_PER_LANE}. When span is
   * unknown or zero, the requested size passes through unchanged (clamped
   * to a ladder value).
   */
  static int effectiveBinSizeDays(TimelineAggregate aggregate, int requestedBinDays) {
    int requested = clampToLadder(requestedBinDays);
    Long min = aggregate.minCreatedAtEpochMillis();
    Long max = aggregate.maxCreatedAtEpochMillis();
    if (min == null || max == null || max <= min) return requested;
    long spanDays = Math.max(1L, (max - min) / DAY_MILLIS + 1);
    for (int candidate : BIN_LADDER) {
      if (candidate < requested) continue;
      long bins = (spanDays + candidate - 1) / candidate;
      if (bins <= MAX_BINS_PER_LANE) return candidate;
    }
    // Past the largest ladder rung — return the largest; the cap is a
    // soft target, never a hard truncation.
    return BIN_LADDER[BIN_LADDER.length - 1];
  }

  /**
   * Map an arbitrary user-supplied bin size to the nearest valid rung at or
   * above it. Unknown sizes (negative, zero) become 1. Sizes above 365 become 365.
   */
  static int clampToLadder(int requested) {
    if (requested <= 0) return 1;
    for (int rung : BIN_LADDER) {
      if (requested <= rung) return rung;
    }
    return BIN_LADDER[BIN_LADDER.length - 1];
  }

  private static long minDay(List<TimelineRow> laneRows) {
    long min = Long.MAX_VALUE;
    for (TimelineRow r : laneRows) {
      if (r.dayEpochMillis() > 0 && r.dayEpochMillis() < min) min = r.dayEpochMillis();
    }
    return min == Long.MAX_VALUE ? 0L : min;
  }

  private static CollectionTimelineLaneIO buildLane(String laneKey, List<TimelineRow> laneRows, int binSizeDays) {
    String label = humanLabel(laneKey);
    String key = stableSlug(laneKey);

    // Coalesce by bin-window. When binSizeDays > 1, multiple day rows fall
    // into the same window — use TreeMap so the bins emit ordered.
    TreeMap<Long, long[]> byWindow = new TreeMap<>();
    for (TimelineRow r : laneRows) {
      long windowStart = windowStartFor(r.dayEpochMillis(), binSizeDays);
      long[] acc = byWindow.computeIfAbsent(windowStart, k -> new long[3]);
      acc[0] += r.count();
      acc[1] += r.ncrCount();
      acc[2] += r.rejectCount();
    }

    List<CollectionTimelineBinIO> bins = new ArrayList<>(byWindow.size());
    for (var e : byWindow.entrySet()) {
      bins.add(new CollectionTimelineBinIO(
        toIsoDay(e.getKey()),
        e.getValue()[0],
        e.getValue()[1],
        e.getValue()[2]
      ));
    }
    return new CollectionTimelineLaneIO(key, label, bins);
  }

  /**
   * Snap an epoch-millis day onto a bin-window start by floor-dividing the
   * day-index. For {@code binSizeDays=7} this clusters every 7 calendar days
   * regardless of weekday; consistent with the chronograph use case.
   */
  static long windowStartFor(long dayEpochMillis, int binSizeDays) {
    if (binSizeDays <= 1) return dayEpochMillis;
    long dayIndex = dayEpochMillis / DAY_MILLIS;
    long windowIndex = Math.floorDiv(dayIndex, binSizeDays);
    return windowIndex * binSizeDays * DAY_MILLIS;
  }

  /**
   * Stable URL-safe slug for a lane key. Lower-cases, replaces every
   * non-alphanumeric run with a single hyphen, trims edges. The
   * {@code __unclassified__} sentinel becomes {@code "unclassified"}.
   */
  static String stableSlug(String laneKey) {
    if (laneKey == null || laneKey.isBlank()) return "unclassified";
    if (CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY.equals(laneKey)) return "unclassified";
    String lowered = laneKey.toLowerCase().trim();
    String slug = lowered.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return slug.isEmpty() ? "unclassified" : slug;
  }

  /**
   * Human-readable label. Echoes the raw value name for ordinary lanes;
   * "Unclassified" for the sentinel. Multi-word valueNames (already
   * capitalised in V100 seeds, e.g. "AFP Layup") pass through unchanged.
   */
  static String humanLabel(String laneKey) {
    if (laneKey == null || laneKey.isBlank()) return "Unclassified";
    if (CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY.equals(laneKey)) return "Unclassified";
    return laneKey;
  }

  static String toIsoUtc(Long epochMillis) {
    if (epochMillis == null) return null;
    return Instant.ofEpochMilli(epochMillis).toString();
  }

  static String toIsoDay(long epochMillis) {
    LocalDate d = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDate();
    return d.format(ISO_DAY);
  }
}
