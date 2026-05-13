package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * Container for {@code shepard-admin semantic <verb>} sub-commands.
 * Phase 1 (post-L1) ships {@code refresh-ontologies} (N1c); N1c2
 * adds the {@code ontologies} sub-group ({@code list}, {@code enable},
 * {@code disable}, {@code upload}, {@code remove}) per
 * {@code aidocs/65 §2.5}.
 */
@Command(
  name = "semantic",
  mixinStandardHelpOptions = true,
  description = "Manage the internal (n10s) semantic repository — refresh bundled ontologies, etc.",
  subcommands = { SemanticRefreshOntologiesCommand.class, SemanticOntologiesCommand.class }
)
public final class SemanticCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin semantic` gets the
    // usage banner from Picocli's default no-subcommand behaviour.
  }
}
