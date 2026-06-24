package de.dlr.shepard.v2.krl.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One unsupported-construct entry — structured so an IME (or an MCP agent
 * via {@code krl_list_unsupported}) can enumerate which KRL features the
 * offline interpreter could not honour on a given run.
 *
 * <p>Distinct from {@link KrlWarningIO}: warnings are free-text; this
 * surface is queryable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KrlUnsupportedConstructIO {
  /** KRL construct name (e.g. {@code "INTERRUPT"}, {@code "SPS"}, {@code "ANIN"}). */
  private String construct;

  /** 1-based source-line number where the construct appeared. */
  private Integer line;

  /** Human-readable reason / mitigation hint. */
  private String reason;
}
