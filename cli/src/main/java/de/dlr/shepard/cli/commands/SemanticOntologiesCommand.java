package de.dlr.shepard.cli.commands;

import picocli.CommandLine.Command;

/**
 * N1c2 — container for {@code shepard-admin semantic ontologies
 * <verb>} sub-commands. Surfaces the four runtime-management
 * verbs from {@code aidocs/65 §2.5}: {@code list}, {@code enable},
 * {@code disable}, {@code upload}, {@code remove}.
 *
 * <p>Hosts the per-bundle operations sibling to
 * {@link SemanticRefreshOntologiesCommand} (N1c), which lives one
 * level up under {@code semantic refresh-ontologies}.
 */
@Command(
  name = "ontologies",
  mixinStandardHelpOptions = true,
  description = "Manage pre-seeded + operator-uploaded ontology bundles.",
  subcommands = {
    SemanticOntologiesListCommand.class,
    SemanticOntologiesEnableCommand.class,
    SemanticOntologiesDisableCommand.class,
    SemanticOntologiesUploadCommand.class,
    SemanticOntologiesRemoveCommand.class,
  }
)
public final class SemanticOntologiesCommand implements Runnable {

  @Override
  public void run() {
    // No-op: typing `shepard-admin semantic ontologies` falls back to
    // Picocli's default no-subcommand usage banner.
  }
}
