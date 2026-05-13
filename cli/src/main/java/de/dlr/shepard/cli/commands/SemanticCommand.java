package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin semantic <verb>} sub-commands.
 * Phase 1 (post-L1) ships {@code refresh-ontologies} (N1c) — the
 * operator-triggered refresh of the bundled ontologies against their
 * pinned canonical URLs. Later slices may add {@code list-bundles},
 * {@code verify}, etc., per {@code aidocs/22 §4.x} and {@code aidocs/48}.
 */
@Command(
  name = "semantic",
  mixinStandardHelpOptions = true,
  description = "Manage the internal (n10s) semantic repository — refresh bundled ontologies, etc.",
  subcommands = { SemanticRefreshOntologiesCommand.class }
)
public final class SemanticCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin semantic` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
