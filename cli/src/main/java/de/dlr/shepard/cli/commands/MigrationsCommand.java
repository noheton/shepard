package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin migrations <verb>} sub-commands.
 * Phase 1 ships {@code status} (read-only); later phases will add
 * {@code resume} / {@code abort} per
 * {@code aidocs/22-admin-cli-draft.md §4.4}.
 */
@Command(
  name = "migrations",
  mixinStandardHelpOptions = true,
  description = "Inspect data-migration progress.",
  subcommands = { MigrationsStatusCommand.class }
)
public final class MigrationsCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin migrations` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
