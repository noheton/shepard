package de.dlr.shepard.v2.krl.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IK-solver convergence statistics — the EN 9100 audit needs these to
 * attest the offline trajectory's reproducibility (per design doc §3.3).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KrlIkStatsIO {
  private Double meanCycleMs;
  private Double p99CycleMs;
  private Double maxResidualMeters;
  private Double maxResidualRadians;
  private Integer failedPoses;
  private Integer totalPoses;
  private String solverName;
  private String solverVersion;
}
