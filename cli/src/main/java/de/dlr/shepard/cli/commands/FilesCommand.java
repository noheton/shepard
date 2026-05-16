package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * FS1e1 — container for {@code shepard-admin files <verb>} sub-commands.
 *
 * <p>Ships {@code migrate} (trigger a big-bang storage migration) and
 * {@code migrate-status} (poll the in-progress migration).
 *
 * <p>See {@code aidocs/45 §6} for the FS1e design and
 * {@code docs/reference/file-storage.md} for the operator runbook.
 */
@Command(
  name = "files",
  mixinStandardHelpOptions = true,
  description = "Manage file-payload storage migrations.",
  subcommands = {
    FilesMigrateCommand.class,
    FilesMigrateStatusCommand.class
  }
)
public final class FilesCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin files` gets the usage banner.
  }
}
