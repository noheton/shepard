package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * FS1a — container for {@code shepard-admin storage <verb>}
 * sub-commands. Ships {@code status} (read-only): shows the active
 * file-payload storage adapter + the MongoDB-side connection
 * status (the proxy for GridFS health).
 *
 * <p>No mutation verbs yet: switching the active storage adapter is
 * a re-bootstrap decision per the {@code CLAUDE.md} "cluster
 * identity / topology" exception. FS1d adds a future
 * {@code GET /v2/admin/storage} listing endpoint and the matching
 * {@code shepard-admin storage list} verb; the migration sweep
 * ({@code shepard-admin files migrate}) lands in FS1e.
 *
 * <p>See {@code aidocs/22-admin-cli-draft.md} for the L1 design
 * baseline (shared flags, output formats) and {@code aidocs/45 §3.2}
 * for the underlying SPI shape.
 */
@Command(
  name = "storage",
  mixinStandardHelpOptions = true,
  description = "Inspect file-payload storage adapter status.",
  subcommands = { StorageStatusCommand.class }
)
public final class StorageCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin storage` gets the usage
    // banner from Picocli's default no-subcommand behaviour.
  }
}
