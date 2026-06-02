package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * KRL-CONFIG-1 — container for {@code shepard-admin krl <verb>} sub-commands.
 *
 * <ul>
 *   <li>{@code status} — read-only view of the current
 *       {@code :KrlInterpreterConfigSingleton} via
 *       {@code GET /v2/admin/krl/config}.</li>
 *   <li>{@code enable} / {@code disable} — flip the master switch
 *       gating the KRL interpreter sidecar calls.</li>
 *   <li>{@code set-sidecar-url} — point operators at the KRL interpreter
 *       sidecar instance the backend targets.</li>
 *   <li>{@code set-timeout} — adjust the per-call request timeout.</li>
 * </ul>
 *
 * <p>The KRL interpreter is active when {@code enabled === true} AND
 * the sidecar is reachable at {@code sidecarUrl}; either knob being
 * clear suppresses the feature (502 on interpret calls).
 *
 * <p>See {@code aidocs/16-dispatcher-backlog.md} KRL-CONFIG-1 row for
 * the backlog entry.
 */
@Command(
    name = "krl",
    mixinStandardHelpOptions = true,
    description = "Manage the instance-wide KRL interpreter configuration.",
    subcommands = {
      KrlStatusCommand.class,
      KrlEnableCommand.class,
      KrlDisableCommand.class,
      KrlSetSidecarUrlCommand.class,
      KrlSetTimeoutCommand.class,
    })
public final class KrlCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin krl` gets the usage
    // banner from Picocli's default no-subcommand behaviour.
  }
}
