package de.dlr.shepard.v2.timeseries.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * AT1 PR-4 — wire-stability proof for the additive {@code detectorId}
 * field on {@link AnomalyDetectRequestIO}.
 *
 * <p>Framed as "fork-v2 wire-shape proof" not "v5 wire-shape proof":
 * the AI1b endpoint
 * {@code POST /v2/timeseries-references/{refAppId}/detect-anomalies}
 * is fork-added, not upstream v5.2.0. The byte-stability bar is the
 * same (no breaking change to existing fork callers); only the
 * framing is honest.
 *
 * <h2>What the test asserts</h2>
 *
 * <p>A request body that <em>omits</em> {@code detectorId} (the legacy
 * pre-AT1 shape) deserialises to the same effective state as one that
 * sets {@code detectorId = "mad-v1"} explicitly. Specifically:
 *
 * <ul>
 *   <li>{@link AnomalyDetectRequestIO#effectiveDetectorId()} returns
 *       {@code "mad-v1"} in both cases.</li>
 *   <li>{@link AnomalyDetectRequestIO#effectiveWindow()} and
 *       {@link AnomalyDetectRequestIO#effectiveK()} return identical
 *       defaults (51, 6.0).</li>
 *   <li>Round-trip serialise → deserialise of a legacy body produces
 *       the same effective values as the parsed source.</li>
 * </ul>
 *
 * <p>If this test ever fails the AT1 extraction has broken the
 * "additive-only" wire-stability invariant the design promises.
 */
class AnomalyDetectRequestIOWireStabilityTest {

  private static final ObjectMapper M = new ObjectMapper();

  /** Pre-AT1 legacy request body — no detectorId, defaults everywhere. */
  private static final String LEGACY_DEFAULTS = "{}";

  /** Pre-AT1 legacy request body with explicit window + k. */
  private static final String LEGACY_EXPLICIT = """
    {"window":21,"k":4.0,"createAnnotations":true}
    """;

  @Test
  void legacy_body_without_detector_id_defaults_to_mad_v1() throws Exception {
    AnomalyDetectRequestIO parsed = M.readValue(LEGACY_DEFAULTS, AnomalyDetectRequestIO.class);
    assertThat(parsed.effectiveDetectorId()).isEqualTo("mad-v1");
    assertThat(parsed.effectiveWindow()).isEqualTo(51);
    assertThat(parsed.effectiveK()).isEqualTo(6.0);
    assertThat(parsed.isCreateAnnotations()).isFalse();
  }

  @Test
  void legacy_body_with_explicit_params_unchanged() throws Exception {
    AnomalyDetectRequestIO parsed = M.readValue(LEGACY_EXPLICIT, AnomalyDetectRequestIO.class);
    assertThat(parsed.effectiveDetectorId()).isEqualTo("mad-v1");
    assertThat(parsed.effectiveWindow()).isEqualTo(21);
    assertThat(parsed.effectiveK()).isEqualTo(4.0);
    assertThat(parsed.isCreateAnnotations()).isTrue();
  }

  @Test
  void blank_detector_id_falls_back_to_mad_v1() throws Exception {
    AnomalyDetectRequestIO parsed = M.readValue("{\"detectorId\":\"\"}", AnomalyDetectRequestIO.class);
    assertThat(parsed.effectiveDetectorId()).isEqualTo("mad-v1");
  }

  @Test
  void explicit_mad_v1_matches_omitted_detector_id() throws Exception {
    AnomalyDetectRequestIO omitted = M.readValue("{}", AnomalyDetectRequestIO.class);
    AnomalyDetectRequestIO explicit = M.readValue("{\"detectorId\":\"mad-v1\"}", AnomalyDetectRequestIO.class);
    // Same effective detector id.
    assertThat(omitted.effectiveDetectorId()).isEqualTo(explicit.effectiveDetectorId());
    // Same effective window + k defaults.
    assertThat(omitted.effectiveWindow()).isEqualTo(explicit.effectiveWindow());
    assertThat(omitted.effectiveK()).isEqualTo(explicit.effectiveK());
  }

  @Test
  void custom_detector_id_round_trips() throws Exception {
    AnomalyDetectRequestIO parsed = M.readValue("{\"detectorId\":\"stl-residual-v1\"}", AnomalyDetectRequestIO.class);
    assertThat(parsed.effectiveDetectorId()).isEqualTo("stl-residual-v1");
    String reserialised = M.writeValueAsString(parsed);
    assertThat(reserialised).contains("\"detectorId\":\"stl-residual-v1\"");
  }
}
