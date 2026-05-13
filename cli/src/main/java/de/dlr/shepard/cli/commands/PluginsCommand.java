package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin plugins <verb>} sub-commands.
 *
 * <p>PM1b — admin CLI parity for the PM1a {@code PluginRegistry}.
 * Mirrors the design in {@code aidocs/16}-PM1b row + the
 * "admin-configurable at runtime" rule from CLAUDE.md. The verbs
 * map 1:1 to the {@code /v2/admin/plugins} REST surface.
 *
 * <p>Same shape as {@link UnhideCommand} and {@link SemanticCommand} —
 * a no-op runnable parent + nested verb commands wired through
 * {@code subcommands}.
 */
@Command(
  name = "plugins",
  mixinStandardHelpOptions = true,
  description = "Inspect and flip the runtime enabled toggle for the PM1a plugin registry.",
  subcommands = {
    PluginsListCommand.class,
    PluginsEnableCommand.class,
    PluginsDisableCommand.class,
  }
)
public final class PluginsCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin plugins` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
