package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * ROR1 — top-level container for {@code shepard-admin instance <group>}
 * sub-commands. Currently ships one sub-group:
 *
 * <ul>
 *   <li>{@code ror} — instance-level Research Organization Registry
 *       configuration ({@link InstanceRorCommand}).</li>
 * </ul>
 *
 * <p>The {@code instance} group is the natural home for any future
 * per-instance admin knobs that do not belong to a more specific
 * domain group (e.g. {@code semantic}, {@code storage}).
 *
 * <p>See {@code aidocs/22-admin-cli-draft.md} for the L1 design
 * baseline and {@code aidocs/16-dispatcher-backlog.md} ROR1 row.
 */
@Command(
  name = "instance",
  mixinStandardHelpOptions = true,
  description = "Manage instance-level configuration (e.g. ROR identifier).",
  subcommands = { InstanceRorCommand.class }
)
public final class InstanceCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin instance` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
