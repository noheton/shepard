package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin unhide <verb>} sub-commands.
 *
 * <p>UH1a — admin CLI parity for the Helmholtz Unhide publish
 * plugin. Mirrors the design in {@code aidocs/67 §6} and the
 * runtime-mutable shape required by the CLAUDE.md
 * "admin-configurable" rule (the same shape as A3b features +
 * N1c2 semantic config — every operator knob reachable from REST
 * has a matching CLI verb).
 */
@Command(
  name = "unhide",
  mixinStandardHelpOptions = true,
  description = "Manage the Helmholtz Unhide publish plugin — toggle, configure, rotate harvest keys.",
  subcommands = {
    UnhideStatusCommand.class,
    UnhideEnableCommand.class,
    UnhideDisableCommand.class,
    UnhideSetFeedPublicCommand.class,
    UnhideSetContactEmailCommand.class,
    UnhideRotateHarvestKeyCommand.class,
    UnhideRevokeHarvestKeyCommand.class,
  }
)
public final class UnhideCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin unhide` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
