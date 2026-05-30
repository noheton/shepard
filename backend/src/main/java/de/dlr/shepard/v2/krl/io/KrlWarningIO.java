package de.dlr.shepard.v2.krl.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One warning emitted by the KRL interpreter sidecar — per the
 * design doc §3.3 + §6 wire shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KrlWarningIO {
  /** 1-based source-line number. {@code null} when the warning is global. */
  private Integer line;

  /** Human-readable explanation. */
  private String message;

  /** One of {@code INFO}, {@code WARN}, {@code ERROR}. */
  private String severity;
}
