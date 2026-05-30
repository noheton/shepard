package de.dlr.shepard.v2.krl.io;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KRL-INTERPRETER-05 — request shape for {@code POST /v2/krl/interpret}.
 *
 * <p>Mirrors the sidecar protocol body documented in
 * {@code aidocs/integrations/117-krl-interpreter.md §6} but with shepard
 * {@code appId}s on the file fields — the backend resolves them to
 * payload bytes before calling the sidecar.
 *
 * <p>Every field is optional except {@code srcFileAppId},
 * {@code urdfFileAppId}, and {@code targetDataObjectAppId}
 * (validated at the resource layer). {@code timeseriesContainerAppId}
 * is optional — when absent the backend auto-mints a default container
 * named {@code "KRL Trajectories"} under {@code targetDataObjectAppId}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KrlInterpretRequestIO {

  /** appId of the {@code FileReference} holding the KRL {@code .src} program. Required. */
  private String srcFileAppId;

  /** appId of the {@code FileReference} holding the URDF XML. Required. */
  private String urdfFileAppId;

  /**
   * Optional appId of a {@code :DigitalTwinScene} — provides default
   * base / tool frame when not overridden. {@code null} when the caller
   * supplies frames explicitly.
   */
  private String sceneAppId;

  /** Optional companion {@code .dat} FileReference appIds. */
  private List<String> datFileAppIds;

  /** Optional base-frame override {x,y,z,rx,ry,rz}. */
  private Map<String, Double> baseFrame;

  /** Optional tool-frame override {x,y,z,rx,ry,rz}. */
  private Map<String, Double> toolFrame;

  /** Optional seed joint angles for IK convergence. */
  private List<Double> seedPose;

  /** Trajectory sample step in seconds. Defaults to 0.01 (100 Hz). */
  private Double timeStep;

  /**
   * Required — the DataObject under which the resulting
   * {@code TimeseriesReference} is attached.
   */
  private String targetDataObjectAppId;

  /**
   * Optional — when absent ({@code null}), the backend auto-mints a
   * {@code :TimeseriesContainer} named {@code "KRL Trajectories"} under
   * {@code targetDataObjectAppId} and uses it. Subsequent interpret runs
   * against the same DataObject reuse the same container (idempotent
   * lookup-or-create by name per DataObject).
   *
   * <p>When present, the caller-supplied container is used as-is
   * (original tier-1 behaviour — explicit container takes precedence).
   */
  private String timeseriesContainerAppId;

  /** Optional pass-through options for the sidecar (ikTolerance, maxIterations, …). */
  private Map<String, Object> options;
}
