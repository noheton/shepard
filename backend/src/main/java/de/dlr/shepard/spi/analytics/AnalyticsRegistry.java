package de.dlr.shepard.spi.analytics;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AT1 — CDI registry for {@link TimeseriesAnalytics} detectors.
 *
 * <p>Walks every {@link TimeseriesAnalytics} CDI bean on the classpath at
 * startup and indexes them by {@link TimeseriesAnalytics#id()}. The
 * in-tree dispatcher ({@code AnomalyDetectionService}) looks up by id via
 * {@link #get(String)} and routes by {@link ExecutionMode}.
 *
 * <p>Mirrors the established {@link
 * de.dlr.shepard.publish.minter.MinterRegistry} idiom: an
 * {@code @ApplicationScoped} bean, a {@code StartupEvent} observer that
 * resolves once, duplicate-id detection that keeps the first registration
 * and logs a WARN.
 *
 * <h2>Routing</h2>
 *
 * <p>Today only the {@link ExecutionMode#IN_PROCESS} branch is live;
 * the {@link ExecutionMode#VIA_ORCHESTRATOR} branch is a stub that logs
 * "future home of orchestrator adapters via shepard-plugin-mlops" and
 * throws {@link UnsupportedOperationException} until that plugin
 * arrives.
 */
@ApplicationScoped
public class AnalyticsRegistry {

  /** Detector id used when the caller omits {@code detectorId}. */
  public static final String DEFAULT_DETECTOR_ID = "mad-v1";

  @Inject
  Instance<TimeseriesAnalytics> detectors;

  private volatile Map<String, TimeseriesAnalytics> byId = Map.of();

  /** Constructor for CDI. */
  public AnalyticsRegistry() {}

  /** Visible for testing. */
  AnalyticsRegistry(Instance<TimeseriesAnalytics> detectors) {
    this.detectors = detectors;
    resolve();
  }

  /**
   * Quarkus startup hook — indexes the discovered beans once.
   */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /** Idempotent resolve. */
  void resolve() {
    Map<String, TimeseriesAnalytics> map = new LinkedHashMap<>();
    List<TimeseriesAnalytics> discovered = new ArrayList<>();
    if (detectors != null) {
      for (TimeseriesAnalytics d : detectors) {
        discovered.add(d);
        if (d == null) continue;
        String id = d.id();
        if (id == null || id.isBlank()) {
          Log.warnf(
            "AnalyticsRegistry: skipping %s — id() returned null/blank",
            d.getClass().getName()
          );
          continue;
        }
        TimeseriesAnalytics prior = map.putIfAbsent(id, d);
        if (prior != null) {
          Log.warnf(
            "AnalyticsRegistry: duplicate detector id '%s' — keeping %s, ignoring %s",
            id,
            prior.getClass().getName(),
            d.getClass().getName()
          );
        }
      }
    }
    this.byId = Map.copyOf(map);

    String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
    Log.infof(
      "AnalyticsRegistry: discovered %d detector(s): [%s]",
      byId.size(),
      available
    );
  }

  /**
   * @param id detector id (e.g. {@code "mad-v1"}); null or blank
   *           resolves to {@link #DEFAULT_DETECTOR_ID}
   * @return the matching detector, or {@code Optional.empty()} when no
   *         detector with that id is registered
   */
  public Optional<TimeseriesAnalytics> get(String id) {
    String key = (id == null || id.isBlank()) ? DEFAULT_DETECTOR_ID : id;
    return Optional.ofNullable(byId.get(key));
  }

  /**
   * @return immutable view of the registered detectors, keyed by
   *         {@link TimeseriesAnalytics#id()}. Surface for admin REST /
   *         diagnostics; the {@link #get(String)} lookup is the
   *         dispatcher's path.
   */
  public Map<String, TimeseriesAnalytics> all() {
    return byId;
  }

  /**
   * Route a detection call based on the detector's {@link
   * ExecutionMode}. For {@link ExecutionMode#IN_PROCESS} this delegates
   * to {@link TimeseriesAnalytics#detect(TimeseriesAnalyticsInput)} and
   * returns the synchronous result. For {@link
   * ExecutionMode#VIA_ORCHESTRATOR} this throws
   * {@link UnsupportedOperationException} with a "future home"
   * message — the {@code shepard-plugin-mlops} adapter wires the
   * async path when it lands.
   *
   * @param detector the resolved detector (caller has already validated
   *                 the id)
   * @param input    detector input (validated by detector)
   * @return the synchronous detection result
   * @throws UnsupportedOperationException when the detector reports
   *         {@link ExecutionMode#VIA_ORCHESTRATOR}
   */
  public AnomalyDetectionResult dispatch(TimeseriesAnalytics detector, TimeseriesAnalyticsInput input) {
    ExecutionMode mode = detector.executionMode();
    if (mode == ExecutionMode.IN_PROCESS) {
      return detector.detect(input);
    }
    Log.warnf(
      "AnalyticsRegistry: detector '%s' is VIA_ORCHESTRATOR — future home of orchestrator adapters via shepard-plugin-mlops",
      detector.id()
    );
    throw new UnsupportedOperationException(
      "Detector '" + detector.id() + "' runs VIA_ORCHESTRATOR; the async dispatch path " +
      "is not yet wired (future home of shepard-plugin-mlops)"
    );
  }
}
