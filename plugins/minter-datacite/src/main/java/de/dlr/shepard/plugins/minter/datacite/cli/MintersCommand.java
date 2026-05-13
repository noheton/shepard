package de.dlr.shepard.plugins.minter.datacite.cli;

import picocli.CommandLine.Command;

/**
 * KIP1d — top-level container for {@code shepard-admin minters
 * <plugin> …} subcommand groups.
 *
 * <p>One level deeper than {@code unhide} / {@code features} /
 * {@code semantic} because shepard supports multiple minter plugins
 * (mock in-core, KIP1d DataCite, KIP1c ePIC when shipped) and each
 * needs its own configuration verbs. The nesting matches
 * {@code shepard-admin semantic ontologies …} (two levels deep).
 *
 * <p>Wired into {@code shepard-admin} via
 * {@link MintersAdminCliCommandProvider} (PM1d CLI extensibility
 * SPI).
 */
@Command(
  name = "minters",
  mixinStandardHelpOptions = true,
  description = "Manage PID minter plugins (DataCite, ePIC, …) — configure, enable, rotate credentials.",
  subcommands = { DataciteCommand.class }
)
public final class MintersCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin minters` gets Picocli's
    // usage banner for the available minter subgroups.
  }
}
