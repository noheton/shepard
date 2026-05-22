package de.dlr.shepard.data.timeseries.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LttbTest {

  @Test
  void returnsInputVerbatimWhenAlreadyUnderTarget() {
    List<TimeseriesDataPoint> input = List.of(
      new TimeseriesDataPoint(1L, 1.0),
      new TimeseriesDataPoint(2L, 2.0),
      new TimeseriesDataPoint(3L, 3.0)
    );
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 10);
    // Identity — no copy, same reference.
    assertSame(input, out);
  }

  @Test
  void preservesEndpoints() {
    List<TimeseriesDataPoint> input = new ArrayList<>();
    for (int i = 0; i < 100; i++) input.add(new TimeseriesDataPoint(i, (double) i));
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 10);
    assertEquals(10, out.size());
    assertEquals(0L, out.get(0).getTimestamp());
    assertEquals(99L, out.get(9).getTimestamp());
  }

  @Test
  void preservesPeakOverFlatBackground() {
    // 1000 zeros with a spike at index 500 — LTTB should keep the spike.
    List<TimeseriesDataPoint> input = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      double v = (i == 500) ? 100.0 : 0.0;
      input.add(new TimeseriesDataPoint(i, v));
    }
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 50);
    assertEquals(50, out.size());
    boolean peakSurvives = out.stream().anyMatch(p -> ((Number) p.getValue()).doubleValue() == 100.0);
    assertTrue(peakSurvives, "LTTB lost the spike — the algorithm should preserve isolated peaks");
  }

  @Test
  void degenerateTargetUnder3KeepsEndpoints() {
    List<TimeseriesDataPoint> input = List.of(
      new TimeseriesDataPoint(1L, 1.0),
      new TimeseriesDataPoint(2L, 2.0),
      new TimeseriesDataPoint(3L, 3.0),
      new TimeseriesDataPoint(4L, 4.0)
    );
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 2);
    assertEquals(2, out.size());
    assertEquals(1L, out.get(0).getTimestamp());
    assertEquals(4L, out.get(1).getTimestamp());
  }

  @Test
  void coercesBooleansForArea() {
    // Mixed bool series — algorithm shouldn't crash.
    List<TimeseriesDataPoint> input = new ArrayList<>();
    for (int i = 0; i < 100; i++) input.add(new TimeseriesDataPoint(i, i % 5 == 0));
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 10);
    assertEquals(10, out.size());
  }

  @Test
  void coercesStringsForArea() {
    List<TimeseriesDataPoint> input = new ArrayList<>();
    for (int i = 0; i < 100; i++) input.add(new TimeseriesDataPoint(i, "tag-" + i));
    List<TimeseriesDataPoint> out = Lttb.downsample(input, 10);
    assertEquals(10, out.size());
  }
}
