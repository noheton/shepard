package de.dlr.shepard.v2.collection.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineAggregate;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineRow;
import de.dlr.shepard.v2.collection.io.CollectionTimelineBinIO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineIO;
import de.dlr.shepard.v2.collection.io.CollectionTimelineLaneIO;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * COLL-TIMELINE-1 — unit tests for the pure-Java envelope builder.
 *
 * <p>Synthetic-row tests; no Neo4j contact. Exercises the post-processing
 * the resource hands off to {@link CollectionTimelineBuilder}: lane
 * ordering, bin coalescence under coarsening, status math, empty cases,
 * and the auto-coarsen ladder.
 */
class CollectionTimelineBuilderTest {

  private static long day(String iso) {
    return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  @Test
  void emptyAggregateProducesEmptyEnvelope() {
    var agg = new TimelineAggregate(List.of(), 0L, null, null);
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    assertEquals(1, io.getBinSizeDays());
    assertEquals(0L, io.getTotalDataObjects());
    assertNull(io.getRangeStart());
    assertNull(io.getRangeEnd());
    assertTrue(io.getLanes().isEmpty());
  }

  @Test
  void mixedProcessRowsGroupIntoLanesOrderedByFirstBin() {
    var rows = List.of(
      new TimelineRow("Ultrasonic Welding", day("2023-04-10"), 5, 0, 0),
      new TimelineRow("AFP Layup",          day("2023-04-01"), 10, 0, 0),
      new TimelineRow("AFP Layup",          day("2023-04-02"), 4, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 19L, day("2023-04-01"), day("2023-04-10"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);

    assertEquals(2, io.getLanes().size());
    // AFP started 2023-04-01 → first lane; ultrasonic started 2023-04-10.
    assertEquals("afp-layup", io.getLanes().get(0).getKey());
    assertEquals("AFP Layup", io.getLanes().get(0).getLabel());
    assertEquals(2, io.getLanes().get(0).getBins().size());
    assertEquals(10, io.getLanes().get(0).getBins().get(0).getCount());
    assertEquals(4,  io.getLanes().get(0).getBins().get(1).getCount());
    assertEquals("ultrasonic-welding", io.getLanes().get(1).getKey());
    assertEquals(19L, io.getTotalDataObjects());
  }

  @Test
  void unclassifiedSentinelBecomesUnclassifiedLane() {
    var rows = List.of(
      new TimelineRow(CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY, day("2023-04-01"), 3, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 3L, day("2023-04-01"), day("2023-04-01"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    assertEquals(1, io.getLanes().size());
    assertEquals("unclassified", io.getLanes().get(0).getKey());
    assertEquals("Unclassified", io.getLanes().get(0).getLabel());
  }

  @Test
  void weeklyBinningCoalescesDaysIntoSevenDayWindows() {
    // Three rows in the same week (Mon-Tue-Wed of 2023-04-03..05).
    // Anchor day used by windowStartFor is the floor-divided day-index,
    // which for that week aligns onto an epoch-multiple-of-7 day.
    var rows = List.of(
      new TimelineRow("AFP Layup", day("2023-04-03"), 2, 0, 0),
      new TimelineRow("AFP Layup", day("2023-04-04"), 3, 1, 0),
      new TimelineRow("AFP Layup", day("2023-04-05"), 1, 0, 1)
    );
    var agg = new TimelineAggregate(rows, 6L, day("2023-04-03"), day("2023-04-05"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 7);

    assertEquals(7, io.getBinSizeDays());
    assertEquals(1, io.getLanes().size());
    var bins = io.getLanes().get(0).getBins();
    assertEquals(1, bins.size(), "All three days fall into one 7-day window");
    var bin = bins.get(0);
    assertEquals(6, bin.getCount());
    assertEquals(1, bin.getNcrCount());
    assertEquals(1, bin.getRejectCount());
  }

  @Test
  void statusCountsCarryThroughUnchanged() {
    var rows = List.of(
      new TimelineRow("NDT Inspection", day("2023-05-01"), 7, 2, 1)
    );
    var agg = new TimelineAggregate(rows, 7L, day("2023-05-01"), day("2023-05-01"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    var bin = io.getLanes().get(0).getBins().get(0);
    assertEquals(7, bin.getCount());
    assertEquals(2, bin.getNcrCount());
    assertEquals(1, bin.getRejectCount());
  }

  @Test
  void autoCoarsensWhenSpanExceedsCap() {
    // 2.6 years ≈ 950 days. Daily bins on a single lane would produce
    // 950 bins → exceeds MAX_BINS_PER_LANE (730). Builder coarsens to 7.
    var rows = List.of(
      new TimelineRow("AFP Layup", day("2023-03-20"), 1, 0, 0),
      new TimelineRow("AFP Layup", day("2025-11-12"), 1, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 2L, day("2023-03-20"), day("2025-11-12"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    // 950 / 7 ≈ 136 bins — well under cap, so the ladder lands at 7.
    assertEquals(7, io.getBinSizeDays());
  }

  @Test
  void respectsRequestedBinSizeWhenSpanIsShort() {
    var rows = List.of(
      new TimelineRow("AFP Layup", day("2024-01-01"), 1, 0, 0),
      new TimelineRow("AFP Layup", day("2024-01-31"), 1, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 2L, day("2024-01-01"), day("2024-01-31"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    // 31 days / 1 = 31 bins — well under the cap; honour the request.
    assertEquals(1, io.getBinSizeDays());
  }

  @Test
  void clampToLadderSnapsArbitraryValuesUpward() {
    assertEquals(1, CollectionTimelineBuilder.clampToLadder(0));
    assertEquals(1, CollectionTimelineBuilder.clampToLadder(-5));
    assertEquals(1, CollectionTimelineBuilder.clampToLadder(1));
    assertEquals(7, CollectionTimelineBuilder.clampToLadder(3));
    assertEquals(7, CollectionTimelineBuilder.clampToLadder(7));
    assertEquals(30, CollectionTimelineBuilder.clampToLadder(8));
    assertEquals(90, CollectionTimelineBuilder.clampToLadder(60));
    assertEquals(365, CollectionTimelineBuilder.clampToLadder(400));
  }

  @Test
  void slugAndLabelHandleSpecialValueNames() {
    assertEquals("afp-layup", CollectionTimelineBuilder.stableSlug("AFP Layup"));
    assertEquals("ndt-inspection", CollectionTimelineBuilder.stableSlug("NDT Inspection"));
    assertEquals("unclassified", CollectionTimelineBuilder.stableSlug(CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY));
    assertEquals("unclassified", CollectionTimelineBuilder.stableSlug(""));
    assertEquals("unclassified", CollectionTimelineBuilder.stableSlug(null));
    assertEquals("special-chars-stripped", CollectionTimelineBuilder.stableSlug("Special / Chars ?? stripped"));
    assertEquals("Unclassified", CollectionTimelineBuilder.humanLabel(null));
    assertEquals("Unclassified", CollectionTimelineBuilder.humanLabel(CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY));
    assertEquals("AFP Layup", CollectionTimelineBuilder.humanLabel("AFP Layup"));
  }

  @Test
  void performanceSmokeWith1000RowsCompletesQuickly() {
    // 1000 synthetic rows across 5 lanes, 200 days each → exercises the
    // grouping loop without DB I/O. Must complete in well under the 2 s
    // budget; pin at 500 ms to catch unintended quadratic regressions.
    String[] lanes = { "AFP Layup", "Ultrasonic Welding", "Resistance Welding", "NDT Inspection", "Frame Welding" };
    List<TimelineRow> rows = new ArrayList<>(1000);
    long base = day("2024-01-01");
    long min = base;
    long max = base;
    for (int i = 0; i < 1000; i++) {
      long d = base + (i % 200) * 86_400_000L;
      if (d > max) max = d;
      String lane = lanes[i % lanes.length];
      int ncr = (i % 50 == 0) ? 1 : 0;
      int rej = (i % 100 == 0) ? 1 : 0;
      rows.add(new TimelineRow(lane, d, 1, ncr, rej));
    }
    var agg = new TimelineAggregate(rows, 1000L, min, max);

    long started = System.nanoTime();
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

    assertNotNull(io);
    assertEquals(5, io.getLanes().size());
    assertEquals(1000L, io.getTotalDataObjects());
    assertTrue(elapsedMs < 500L, "Builder took " + elapsedMs + "ms for 1000 rows × 5 lanes; perf regression");
  }

  @Test
  void rangeStartAndEndRenderAsIsoInstants() {
    var rows = List.of(new TimelineRow("AFP Layup", day("2024-06-15"), 1, 0, 0));
    var agg = new TimelineAggregate(rows, 1L, day("2024-06-15"), day("2024-06-20"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    assertNotNull(io.getRangeStart());
    assertNotNull(io.getRangeEnd());
    assertTrue(io.getRangeStart().startsWith("2024-06-15T00:00:00"));
    assertTrue(io.getRangeEnd().startsWith("2024-06-20T00:00:00"));
  }

  @Test
  void binDayLabelsAreIsoDates() {
    var rows = List.of(new TimelineRow("AFP Layup", day("2024-07-04"), 2, 0, 0));
    var agg = new TimelineAggregate(rows, 2L, day("2024-07-04"), day("2024-07-04"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    CollectionTimelineBinIO bin = io.getLanes().get(0).getBins().get(0);
    assertEquals("2024-07-04", bin.getDay());
  }

  @Test
  void multipleLanesPreserveDeterministicOrderWhenStartDayTies() {
    long sameDay = day("2024-01-01");
    var rows = List.of(
      new TimelineRow("Zeta Welding", sameDay, 1, 0, 0),
      new TimelineRow("Alpha Layup", sameDay, 1, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 2L, sameDay, sameDay);
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    assertEquals(2, io.getLanes().size());
    // Same first-bin → secondary sort by raw key. "Alpha Layup" < "Zeta Welding".
    assertEquals("alpha-layup", io.getLanes().get(0).getKey());
    assertEquals("zeta-welding", io.getLanes().get(1).getKey());
  }

  @Test
  void laneBinsSortAscendingWithinLane() {
    var rows = List.of(
      new TimelineRow("AFP Layup", day("2024-06-10"), 1, 0, 0),
      new TimelineRow("AFP Layup", day("2024-06-05"), 1, 0, 0),
      new TimelineRow("AFP Layup", day("2024-06-15"), 1, 0, 0)
    );
    var agg = new TimelineAggregate(rows, 3L, day("2024-06-05"), day("2024-06-15"));
    CollectionTimelineIO io = CollectionTimelineBuilder.build(agg, 1);
    var bins = io.getLanes().get(0).getBins();
    assertEquals(3, bins.size());
    assertEquals("2024-06-05", bins.get(0).getDay());
    assertEquals("2024-06-10", bins.get(1).getDay());
    assertEquals("2024-06-15", bins.get(2).getDay());
  }
}
