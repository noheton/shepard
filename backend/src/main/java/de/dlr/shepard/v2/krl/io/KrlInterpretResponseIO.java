package de.dlr.shepard.v2.krl.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KRL-INTERPRETER-05 — response shape for {@code POST /v2/krl/interpret}.
 *
 * <p>Returns the {@code appId} of the produced
 * {@code TimeseriesReference} (the trajectory) and the {@code appId} of
 * the {@code :KrlInterpretActivity} row that recorded the call — both
 * are addressable handles for the URDF viewer's deep-link and the
 * IME / AQE audit drill-down respectively.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KrlInterpretResponseIO {

  /** appId of the persisted trajectory {@code TimeseriesReference}. */
  private String trajectoryAppId;

  /** appId of the persisted {@code :KrlInterpretActivity} row. */
  private String activityAppId;

  /** Sidecar warnings (may be empty). */
  private List<KrlWarningIO> warnings;

  /** Unsupported KRL constructs the interpreter skipped (may be empty). */
  private List<KrlUnsupportedConstructIO> unsupportedConstructs;

  /** Convergence stats from the IK back-solver. */
  private KrlIkStatsIO ikSolverStats;

  /** Sidecar version string — captured for the EN 9100 audit trail. */
  private String interpreterVersion;
}
