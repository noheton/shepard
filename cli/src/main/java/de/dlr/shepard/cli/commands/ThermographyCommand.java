package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — container for
 * {@code shepard-admin thermography <verb>} sub-commands.
 *
 * <ul>
 *   <li>{@code status} — read-only view of the current
 *       {@code :ThermographyConfig} singleton via
 *       {@code GET /v2/admin/thermography/config}.</li>
 *   <li>{@code set-threshold} — update the quality-score denominator
 *       in degrees Celsius at runtime without a restart.</li>
 *   <li>{@code set-grid} — update the plate-heatmap grid dimensions
 *       at runtime without a restart.</li>
 * </ul>
 *
 * <p>Precedence of effective values: per-request override (AnalyzeRequestIO)
 * &gt; runtime singleton (this config) &gt; deploy-time default
 * ({@code shepard.v2.thermography.*} in application.properties).
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} MFFD-NDT-ADMIN-CONFIG-1 row
 * and {@code docs/admin/runbooks/thermography-config.md} for the operator runbook.
 */
@Command(
  name = "thermography",
  mixinStandardHelpOptions = true,
  description = "Manage the instance-wide thermography analysis configuration.",
  subcommands = {
    ThermographyStatusCommand.class,
    ThermographySetThresholdCommand.class,
    ThermographySetGridCommand.class,
  }
)
public final class ThermographyCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin thermography` gets the usage
    // banner from Picocli's default no-subcommand behaviour.
  }
}
