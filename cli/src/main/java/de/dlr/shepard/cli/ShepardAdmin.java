package de.dlr.shepard.cli;

import de.dlr.shepard.cli.commands.FeaturesCommand;
import de.dlr.shepard.cli.commands.HealthCommand;
import de.dlr.shepard.cli.commands.MigrationsCommand;
import de.dlr.shepard.cli.commands.SemanticCommand;
import de.dlr.shepard.cli.commands.UnhideCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level entry point for the {@code shepard-admin} CLI.
 *
 * <p>L1 Phase 1 surfaces read-only operator info: feature toggles,
 * health, and migration state. N1c grafts the first mutation
 * subcommand on — {@code semantic refresh-ontologies} re-imports the
 * bundled ontologies from their pinned canonical URLs. UH1a adds
 * the {@code unhide} subcommand group — Helmholtz Unhide publish-
 * plugin runtime config + harvest-key minting. See
 * {@code aidocs/22-admin-cli-draft.md} for the full design and
 * {@code aidocs/67 §6} for the UH1a subcommand-group shape.
 */
@Command(
  name = "shepard-admin",
  mixinStandardHelpOptions = true,
  versionProvider = VersionProvider.class,
  description = "Administration CLI for a running shepard instance.",
  subcommands = {
    FeaturesCommand.class,
    HealthCommand.class,
    MigrationsCommand.class,
    SemanticCommand.class,
    UnhideCommand.class,
  }
)
public final class ShepardAdmin implements Runnable {

  /** Invoked when no subcommand is given — prints the usage banner. */
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exit = new CommandLine(new ShepardAdmin()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exit);
  }
}
