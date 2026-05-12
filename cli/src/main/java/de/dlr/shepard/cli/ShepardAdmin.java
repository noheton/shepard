package de.dlr.shepard.cli;

import de.dlr.shepard.cli.commands.FeaturesCommand;
import de.dlr.shepard.cli.commands.HealthCommand;
import de.dlr.shepard.cli.commands.MigrationsCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level entry point for the {@code shepard-admin} CLI.
 *
 * <p>L1 Phase 1 surfaces read-only operator info: feature toggles,
 * health, and migration state. See {@code aidocs/22-admin-cli-draft.md}
 * for the full design and Phase 2+ scope.
 */
@Command(
  name = "shepard-admin",
  mixinStandardHelpOptions = true,
  versionProvider = VersionProvider.class,
  description = "Read-only administration CLI for a running shepard instance (L1 Phase 1).",
  subcommands = {
    FeaturesCommand.class,
    HealthCommand.class,
    MigrationsCommand.class,
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
