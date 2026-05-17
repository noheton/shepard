package de.dlr.shepard.plugins.minter.epic.cli;

import picocli.CommandLine.Command;

/**
 * KIP1c — top-level container for {@code shepard-admin minters
 * <plugin> …} subcommand groups.
 *
 * <p>When both the DataCite and ePIC minter plugins are on the
 * classpath, each ships its own {@code MintersCommand} class (in
 * its own package). The in-CLI bootstrap deduplicates by class name
 * + merges subcommands, so both {@code minters datacite} and
 * {@code minters epic} appear under a single {@code minters} node.
 * A WARN is logged once on the duplicate top-level name; the
 * subcommands all register correctly.
 *
 * <p>Wired into {@code shepard-admin} via
 * {@link MintersAdminCliCommandProvider} (PM1d CLI extensibility SPI).
 */
@Command(
  name = "minters",
  mixinStandardHelpOptions = true,
  description = "Manage PID minter plugins (DataCite, ePIC, …) — configure, enable, rotate credentials.",
  subcommands = { EpicCommand.class }
)
public final class MintersCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin minters` gets Picocli's
    // usage banner for the available minter subgroups.
  }
}
