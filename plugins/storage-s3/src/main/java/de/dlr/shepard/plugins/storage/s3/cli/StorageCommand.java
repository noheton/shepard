package de.dlr.shepard.plugins.storage.s3.cli;

import picocli.CommandLine.Command;

/**
 * FS1b — top-level container for
 * {@code shepard-admin storage <plugin> …} subcommand groups.
 *
 * <p>One level deeper than {@code unhide} / {@code features} because
 * shepard supports multiple storage plugins (GridFS in-core, FS1b S3,
 * future Garage-direct / SeaweedFS adapters) and each needs its own
 * configuration verbs. The nesting matches
 * {@code shepard-admin minters datacite …}.
 *
 * <p>Wired into {@code shepard-admin} via
 * {@link S3StorageAdminCliCommandProvider} (PM1d CLI extensibility SPI).
 */
@Command(
  name = "storage",
  mixinStandardHelpOptions = true,
  description = "Manage storage backend plugins (GridFS, S3, …) — configure, enable, rotate credentials.",
  subcommands = { S3StorageCommand.class }
)
public final class StorageCommand implements Runnable {

  @Override
  public void run() {
    // No-op: user gets Picocli's usage banner for available subgroups.
  }
}
