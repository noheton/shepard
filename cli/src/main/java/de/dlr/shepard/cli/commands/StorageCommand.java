package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * FS1e1 — container for {@code shepard-admin storage <verb>}
 * sub-commands. Ships {@code status} (read-only): shows all
 * discovered storage adapters with their enabled/active state
 * via the {@code GET /v2/admin/storage} admin endpoint.
 *
 * <p>Switching the active storage adapter is a re-bootstrap
 * decision per the {@code CLAUDE.md} "cluster identity / topology"
 * exception — no mutation verbs here. File migration lives under
 * {@code shepard-admin files migrate}.
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
