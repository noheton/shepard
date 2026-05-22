package de.dlr.shepard.spi.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AT1 — unit tests for {@link AnalyticsRegistry}.
 *
 * <p>Verifies discovery, by-id lookup, default detector fallback,
 * duplicate-id behaviour, and execution-mode routing (the
 * {@code VIA_ORCHESTRATOR} branch throws "future home" until
 * {@code shepard-plugin-mlops} arrives).
 */
class AnalyticsRegistryTest {

  // ── fixtures ─────────────────────────────────────────────────────────────

  /** Minimal in-process detector that returns its id in the result-via-totalPoints field. */
  static class FakeInProcess implements TimeseriesAnalytics {
    private final String id;

    FakeInProcess(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public AnomalyDetectionResult detect(TimeseriesAnalyticsInput input) {
      return new AnomalyDetectionResult(List.of(), 0, 6.0, input.values().length);
    }
  }

  /** Orchestrator-tier stub. */
  static class FakeRemote implements RemoteTimeseriesAnalytics {
    @Override
    public String id() {
      return "remote-v1";
    }

    @Override
    public JobHandle submitJob(TimeseriesAnalyticsInput input) {
      return JobHandle.submitted("job-1");
    }

    @Override
    public JobHandle pollJobStatus(String jobId) {
      return JobHandle.submitted(jobId);
    }

    @Override
    public void cancelJob(String jobId) {
      // no-op
    }
  }

  /** Hand-rolled Instance shim. */
  static class FakeInstance<T> implements Instance<T> {
    final List<T> items;

    FakeInstance(List<T> items) {
      this.items = items;
    }

    @Override
    public Iterator<T> iterator() {
      return items.iterator();
    }

    @Override
    public T get() {
      return items.get(0);
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
      return (Instance<U>) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
      return (Instance<U>) this;
    }

    @Override
    public boolean isUnsatisfied() {
      return items.isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
      return false;
    }

    @Override
    public boolean isResolvable() {
      return items.size() == 1;
    }

    @Override
    public void destroy(T instance) {
      // no-op
    }

    @Override
    public Handle<T> getHandle() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
      return java.util.Collections.emptyList();
    }
  }

  // ── tests ────────────────────────────────────────────────────────────────

  @Test
  void resolve_discovers_and_indexes_by_id() {
    var a = new FakeInProcess("mad-v1");
    var b = new FakeInProcess("zscore-v1");
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(a, b)));

    assertThat(registry.all()).hasSize(2);
    assertThat(registry.get("mad-v1")).containsSame(a);
    assertThat(registry.get("zscore-v1")).containsSame(b);
  }

  @Test
  void get_with_null_or_blank_falls_back_to_default_detector() {
    var mad = new FakeInProcess("mad-v1");
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(mad)));

    assertThat(registry.get(null)).containsSame(mad);
    assertThat(registry.get("")).containsSame(mad);
    assertThat(registry.get("   ")).containsSame(mad);
  }

  @Test
  void get_with_unknown_id_returns_empty() {
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(new FakeInProcess("mad-v1"))));
    assertThat(registry.get("nonexistent-v1")).isEmpty();
  }

  @Test
  void duplicate_ids_keep_first_and_continue() {
    var first = new FakeInProcess("dup");
    var second = new FakeInProcess("dup");
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(first, second)));

    assertThat(registry.all()).hasSize(1);
    assertThat(registry.get("dup")).containsSame(first);
  }

  @Test
  void blank_id_detector_is_skipped() {
    var bad = new FakeInProcess("");
    var good = new FakeInProcess("mad-v1");
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(bad, good)));

    assertThat(registry.all()).containsOnlyKeys("mad-v1");
  }

  @Test
  void dispatch_runs_in_process_detector_synchronously() {
    var mad = new FakeInProcess("mad-v1");
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(mad)));
    var input = new TimeseriesAnalyticsInput(new long[] { 0L, 1L }, new double[] { 1.0, 2.0 }, java.util.Map.of(), "ref-1");

    AnomalyDetectionResult result = registry.dispatch(mad, input);

    assertThat(result.totalPoints()).isEqualTo(2);
    assertThat(result.intervals()).isEmpty();
  }

  @Test
  void dispatch_via_orchestrator_throws_until_mlops_lands() {
    var remote = new FakeRemote();
    var registry = new AnalyticsRegistry(new FakeInstance<>(List.of(remote)));
    var input = new TimeseriesAnalyticsInput(new long[] { 0L }, new double[] { 1.0 }, java.util.Map.of(), "ref-1");

    assertThatThrownBy(() -> registry.dispatch(remote, input))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessageContaining("VIA_ORCHESTRATOR")
      .hasMessageContaining("shepard-plugin-mlops");
  }

  @Test
  void execution_mode_defaults_to_in_process() {
    assertThat(new FakeInProcess("mad-v1").executionMode()).isEqualTo(ExecutionMode.IN_PROCESS);
  }

  @Test
  void remote_detector_reports_via_orchestrator_mode() {
    assertThat(new FakeRemote().executionMode()).isEqualTo(ExecutionMode.VIA_ORCHESTRATOR);
  }

  @Test
  void remote_detector_synchronous_detect_throws() {
    var input = new TimeseriesAnalyticsInput(new long[] { 0L }, new double[] { 1.0 }, java.util.Map.of(), "ref-1");
    assertThatThrownBy(() -> new FakeRemote().detect(input))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessageContaining("submitJob");
  }

  @Test
  void analytics_input_rejects_mismatched_array_lengths() {
    assertThatThrownBy(() ->
        new TimeseriesAnalyticsInput(new long[] { 0L }, new double[] { 1.0, 2.0 }, java.util.Map.of(), "r")
      )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("parallel");
  }

  @Test
  void analytics_input_normalizes_null_parameters_to_empty_map() {
    var in = new TimeseriesAnalyticsInput(new long[] {}, new double[] {}, null, null);
    assertThat(in.parameters()).isEmpty();
  }

  @Test
  void job_handle_submitted_factory_sets_status_to_submitted() {
    JobHandle h = JobHandle.submitted("job-42");
    assertThat(h.jobId()).isEqualTo("job-42");
    assertThat(h.status()).isEqualTo(JobStatus.SUBMITTED);
    assertThat(h.updatedAt()).isEmpty();
    assertThat(h.resultLocation()).isEmpty();
  }
}
