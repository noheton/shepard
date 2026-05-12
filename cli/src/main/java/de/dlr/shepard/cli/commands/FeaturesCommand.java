package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin features <verb>} sub-commands.
 * Phase 1 ships {@code list}; later phases will add
 * {@code get / set / enable / disable} per
 * {@code aidocs/22-admin-cli-draft.md §4.6}.
 */
@Command(
  name = "features",
  mixinStandardHelpOptions = true,
  description = "Inspect feature toggles.",
  subcommands = { FeaturesListCommand.class }
)
public final class FeaturesCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin features` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
