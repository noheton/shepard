package de.dlr.shepard.context.references.timeseriesreference.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectRequestIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyDetectResultIO;
import de.dlr.shepard.v2.timeseries.io.AnomalyIntervalIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * AI1b — unit tests for {@link AnomalyDetectionService}.
 *
 * <p>Algorithm tests use pure in-process calls (no CDI container needed).
 * Service-integration tests use Mockito to stub {@link TimeseriesService}
 * and {@link TimeseriesAnnotationDAO}.
 */
class AnomalyDetectionServiceTest {

  // ── fixtures ─────────────────────────────────────────────────────────────

  private static final String REF_APP_ID = "ref-001";
  private static final long CONTAINER_ID = 42L;
  private static final Timeseries SERIES = new Timeseries("m", "d", "l", "s", "f");

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Mock
  TimeseriesService timeseriesService;

  @Mock
  TimeseriesAnnotationDAO annotationDAO;

  AnomalyDetectionService svc;
  TimeseriesReference ref;
  TimeseriesContainer container;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    svc = new AnomalyDetectionService();
    svc.timeseriesReferenceDAO = timeseriesReferenceDAO;
    svc.timeseriesService = timeseriesService;
    svc.annotationDAO = annotationDAO;

    container = new TimeseriesContainer();
    container.setId(CONTAINER_ID);

    ref = new TimeseriesReference();
    ref.setAppId(REF_APP_ID);
    ref.setStart(0L);
    ref.setEnd(Long.MAX_VALUE);
    ref.setTimeseriesContainer(container);
    var node = new ReferencedTimeseriesNodeEntity("m", "d", "l", "s", "f");
    ref.addTimeseries(node);
  }

  // ── algorithm unit tests ──────────────────────────────────────────────────

  @Test
  void median_oddLength() {
    // [1,2,3] → median = 2
    assertThat(AnomalyDetectionService.median(new double[]{1, 2, 3})).isEqualTo(2.0);
  }

  @Test
  void median_evenLength() {
    // [1,2,3,4] → median = 2.5
    assertThat(AnomalyDetectionService.median(new double[]{1, 2, 3, 4})).isEqualTo(2.5);
  }

  @Test
  void median_singleElement() {
    assertThat(AnomalyDetectionService.median(new double[]{7.0})).isEqualTo(7.0);
  }

  @Test
  void effectiveWindow_forcesOdd() {
    assertThat(AnomalyDetectionService.effectiveWindow(4, 100)).isEqualTo(5);
    assertThat(AnomalyDetectionService.effectiveWindow(5, 100)).isEqualTo(5);
  }

  @Test
  void effectiveWindow_clampsToSeriesLength() {
    // window=51, series=10 → clamp to 10, then force odd → 9
    assertThat(AnomalyDetectionService.effectiveWindow(51, 10)).isEqualTo(9);
  }

  @Test
  void effectiveWindow_clampedToOddWhenSeriesLengthIsOdd() {
    // window=100, series=7 (odd) → clamp to 7
    assertThat(AnomalyDetectionService.effectiveWindow(100, 7)).isEqualTo(7);
  }

  @Test
  void effectiveWindow_minimumOneForSinglePoint() {
    // window=51, series=1 → clamp to 1
    assertThat(AnomalyDetectionService.effectiveWindow(51, 1)).isEqualTo(1);
  }

  @Test
  void rollingMadDetect_cleanConstantSeriesHasNoAnomalies() {
    // 100 identical values → MAD = 0, floored to 1e-3; Z = 0 → no anomalies
    double[] v = new double[100];
    Arrays.fill(v, 5.0);
    boolean[] flags = AnomalyDetectionService.rollingMadDetect(v, 51, 6.0);
    for (boolean f : flags) assertThat(f).isFalse();
  }

  @Test
  void rollingMadDetect_spikeIsDetected() {
    // 100 values of 1.0 with a large spike at index 50
    double[] v = new double[100];
    Arrays.fill(v, 1.0);
    v[50] = 1000.0;
    boolean[] flags = AnomalyDetectionService.rollingMadDetect(v, 51, 6.0);
    assertThat(flags[50]).isTrue();
    // Neighbours well within normal range should not be flagged
    assertThat(flags[0]).isFalse();
    assertThat(flags[99]).isFalse();
  }

  @Test
  void rollingMadDetect_twoDistinctSpikes_separatedByCleanGap() {
    double[] v = new double[200];
    Arrays.fill(v, 1.0);
    v[30] = 1000.0;
    v[170] = -1000.0;
    boolean[] flags = AnomalyDetectionService.rollingMadDetect(v, 51, 6.0);
    assertThat(flags[30]).isTrue();
    assertThat(flags[170]).isTrue();
    // Middle of the clean region should be fine
    assertThat(flags[100]).isFalse();
  }

  @Test
  void collectIntervals_singlePointAnomaly() {
    List<Long> ts = List.of(1_000L, 2_000L, 3_000L);
    double[] v = {1.0, 100.0, 1.0};
    double[] z = {0.0, 10.0, 0.0};
    boolean[] flags = {false, true, false};
    List<AnomalyIntervalIO> intervals = AnomalyDetectionService.collectIntervals(ts, v, z, flags);
    assertThat(intervals).hasSize(1);
    AnomalyIntervalIO a = intervals.get(0);
    assertThat(a.startNs()).isEqualTo(2_000L);
    assertThat(a.endNs()).isEqualTo(2_000L);
    assertThat(a.peakValue()).isEqualTo(100.0);
    assertThat(a.maxZScore()).isEqualTo(10.0);
  }

  @Test
  void collectIntervals_contiguousRun() {
    List<Long> ts = List.of(100L, 200L, 300L, 400L, 500L);
    double[] v = {1.0, 50.0, 100.0, 50.0, 1.0};
    double[] z = {0.0, 8.0, 15.0, 8.0, 0.0};
    boolean[] flags = {false, true, true, true, false};
    List<AnomalyIntervalIO> intervals = AnomalyDetectionService.collectIntervals(ts, v, z, flags);
    assertThat(intervals).hasSize(1);
    AnomalyIntervalIO a = intervals.get(0);
    assertThat(a.startNs()).isEqualTo(200L);
    assertThat(a.endNs()).isEqualTo(400L);
    assertThat(a.maxZScore()).isEqualTo(15.0);
    assertThat(a.peakValue()).isEqualTo(100.0);
  }

  @Test
  void collectIntervals_twoSeparateRuns() {
    List<Long> ts = List.of(100L, 200L, 300L, 400L, 500L);
    double[] v = {50.0, 1.0, 50.0, 1.0, 50.0};
    double[] z = {8.0, 0.0, 9.0, 0.0, 7.0};
    boolean[] flags = {true, false, true, false, true};
    List<AnomalyIntervalIO> intervals = AnomalyDetectionService.collectIntervals(ts, v, z, flags);
    assertThat(intervals).hasSize(3);
  }

  @Test
  void collectIntervals_noAnomalies() {
    List<Long> ts = List.of(100L, 200L, 300L);
    double[] v = {1.0, 1.0, 1.0};
    double[] z = {0.0, 0.0, 0.0};
    boolean[] flags = {false, false, false};
    assertThat(AnomalyDetectionService.collectIntervals(ts, v, z, flags)).isEmpty();
  }

  @Test
  void computeZScores_zeroDivisionProtection() {
    // Constant series → MAD = 0 → floored to 1e-3; Z = 0 (v == med)
    double[] v = new double[20];
    Arrays.fill(v, 5.0);
    double[] z = AnomalyDetectionService.computeZScores(v, 7);
    for (double zi : z) {
      assertThat(zi).isZero();
      assertThat(Double.isNaN(zi)).isFalse();
      assertThat(Double.isInfinite(zi)).isFalse();
    }
  }

  // ── service integration tests (Mockito) ──────────────────────────────────

  @Test
  void detect_emptySeriesReturnsEmptyResult() {
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(Collections.emptyList());

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    AnomalyDetectResultIO result = svc.detect(ref, SERIES, req);

    assertThat(result.anomalies()).isEmpty();
    assertThat(result.totalPoints()).isZero();
    assertThat(result.annotationsCreated()).isZero();
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void detect_singlePointNoAnomaly() {
    var dp = new TimeseriesDataPoint(1_000_000L, 1.0);
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(List.of(dp));

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, new AnomalyDetectRequestIO());

    assertThat(result.anomalies()).isEmpty();
    assertThat(result.totalPoints()).isEqualTo(1);
    // Window clamps to 1 (series length=1, forced odd=1)
    assertThat(result.windowSize()).isEqualTo(1);
  }

  @Test
  void detect_allNullValuesReturnsEmpty() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 20; i++) pts.add(new TimeseriesDataPoint(i * 1_000L, null));
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, new AnomalyDetectRequestIO());

    assertThat(result.totalPoints()).isZero();
    assertThat(result.anomalies()).isEmpty();
  }

  @Test
  void detect_allCleanSeries_noAnomalies() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) pts.add(new TimeseriesDataPoint(i * 1_000_000L, 1.0));
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, new AnomalyDetectRequestIO());

    assertThat(result.anomalies()).isEmpty();
    assertThat(result.totalPoints()).isEqualTo(100);
    assertThat(result.annotationsCreated()).isZero();
  }

  @Test
  void detect_spikeIsDetected() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      double val = (i == 50) ? 10_000.0 : 1.0;
      pts.add(new TimeseriesDataPoint(i * 1_000_000L, val));
    }
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(11);
    req.setK(4.0);
    AnomalyDetectResultIO result = svc.detect(ref, SERIES, req);

    assertThat(result.anomalies()).isNotEmpty();
    // The spike at index 50 should be inside at least one interval
    long spikeTs = 50 * 1_000_000L;
    boolean found = result.anomalies().stream()
      .anyMatch(a -> a.startNs() <= spikeTs && a.endNs() >= spikeTs);
    assertThat(found).isTrue();
  }

  @Test
  void detect_windowLargerThanSeries_clamped() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 5; i++) pts.add(new TimeseriesDataPoint(i * 1_000L, 1.0));
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(51); // Much larger than series
    AnomalyDetectResultIO result = svc.detect(ref, SERIES, req);

    // Window must be clamped to ≤ 5, forced odd = 5
    assertThat(result.windowSize()).isLessThanOrEqualTo(5);
    assertThat(result.windowSize() % 2).isEqualTo(1); // odd
  }

  @Test
  void detect_createAnnotations_persistsOneAnnotationPerInterval() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      double val = (i == 50) ? 10_000.0 : 1.0;
      pts.add(new TimeseriesDataPoint(i * 1_000_000L, val));
    }
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(11);
    req.setK(4.0);
    req.setCreateAnnotations(true);

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, req);

    int expectedAnnotations = result.anomalies().size();
    assertThat(result.annotationsCreated()).isEqualTo(expectedAnnotations);
    verify(annotationDAO, times(expectedAnnotations)).createOrUpdate(any(TimeseriesAnnotation.class));
    verify(annotationDAO, times(expectedAnnotations)).linkToReference(eq(REF_APP_ID), any());
  }

  @Test
  void detect_createAnnotations_aiGeneratedAndLabelSet() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      double val = (i == 50) ? 10_000.0 : 1.0;
      pts.add(new TimeseriesDataPoint(i * 1_000_000L, val));
    }
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(11);
    req.setK(4.0);
    req.setCreateAnnotations(true);

    svc.detect(ref, SERIES, req);

    ArgumentCaptor<TimeseriesAnnotation> captor = ArgumentCaptor.forClass(TimeseriesAnnotation.class);
    verify(annotationDAO, times(1)).createOrUpdate(captor.capture());
    TimeseriesAnnotation ann = captor.getValue();
    assertThat(ann.isAiGenerated()).isTrue();
    assertThat(ann.getLabel()).isEqualTo("anomaly");
    assertThat(ann.getConfidence()).isNotNull();
    assertThat(ann.getConfidence()).isBetween(0.0, 1.0);
  }

  @Test
  void detect_createAnnotationsFalse_noPersistence() {
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      double val = (i == 50) ? 10_000.0 : 1.0;
      pts.add(new TimeseriesDataPoint(i * 1_000_000L, val));
    }
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(11);
    req.setK(4.0);
    req.setCreateAnnotations(false);

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, req);

    assertThat(result.annotationsCreated()).isZero();
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void detect_invalidWindowThrows() {
    when(timeseriesService.getDataPointsByTimeseries(any(), any(), any()))
      .thenReturn(Collections.emptyList());

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(2); // too small

    assertThatThrownBy(() -> svc.detect(ref, SERIES, req))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("window must be ≥ 3");
  }

  @Test
  void detect_invalidKThrows() {
    when(timeseriesService.getDataPointsByTimeseries(any(), any(), any()))
      .thenReturn(Collections.emptyList());

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setK(-1.0);

    assertThatThrownBy(() -> svc.detect(ref, SERIES, req))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("k must be > 0");
  }

  @Test
  void detect_nonNumericValuesAreSkipped() {
    // Series with mix of numeric and non-numeric — non-numeric silently skipped
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    pts.add(new TimeseriesDataPoint(1_000L, "text"));  // skipped
    pts.add(new TimeseriesDataPoint(2_000L, 1.0));
    pts.add(new TimeseriesDataPoint(3_000L, null));     // skipped
    pts.add(new TimeseriesDataPoint(4_000L, 1.0));
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectResultIO result = svc.detect(ref, SERIES, new AnomalyDetectRequestIO());

    assertThat(result.totalPoints()).isEqualTo(2); // only the numeric ones
  }

  @Test
  void detect_confidenceMonotonicallyScaled() {
    // Spike at 10_000 relative to baseline 1.0 with window=11, k=4.0
    // The maxZScore should be >> k, and confidence = min(1.0, maxZ / (2k))
    // For a huge spike: maxZ >> 2k → confidence == 1.0
    List<TimeseriesDataPoint> pts = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      double val = (i == 50) ? 10_000.0 : 1.0;
      pts.add(new TimeseriesDataPoint(i * 1_000_000L, val));
    }
    when(timeseriesService.getDataPointsByTimeseries(eq(CONTAINER_ID), eq(SERIES), any()))
      .thenReturn(pts);

    AnomalyDetectRequestIO req = new AnomalyDetectRequestIO();
    req.setWindow(11);
    req.setK(4.0);
    req.setCreateAnnotations(true);

    svc.detect(ref, SERIES, req);

    ArgumentCaptor<TimeseriesAnnotation> captor = ArgumentCaptor.forClass(TimeseriesAnnotation.class);
    verify(annotationDAO).createOrUpdate(captor.capture());
    assertThat(captor.getValue().getConfidence()).isEqualTo(1.0);
  }
}
