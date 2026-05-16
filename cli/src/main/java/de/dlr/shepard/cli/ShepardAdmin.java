package de.dlr.shepard.cli;

import de.dlr.shepard.cli.commands.FeaturesCommand;
import de.dlr.shepard.cli.commands.FilesCommand;
import de.dlr.shepard.cli.commands.HealthCommand;
import de.dlr.shepard.cli.commands.MigrationsCommand;
import de.dlr.shepard.cli.commands.PluginsCommand;
import de.dlr.shepard.cli.commands.SemanticCommand;
import de.dlr.shepard.cli.commands.StorageCommand;
import de.dlr.shepard.cli.plugin.CliPluginBootstrap;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level entry point for the {@code shepard-admin} CLI.
 *
 * <p>L1 Phase 1 surfaces read-only operator info: feature toggles,
 * health, and migration state. N1c grafts the first mutation
 * subcommand on — {@code semantic refresh-ontologies} re-imports the
 * bundled ontologies from their pinned canonical URLs. PM1b adds the
 * {@code plugins} subcommand group — list / enable / disable
 * registered shepard plugins via the PM1a {@code PluginRegistry}'s
 * admin REST surface. PM1d wires the
 * {@link CliPluginBootstrap} — at startup the CLI walks the same
 * plugin directory the backend uses and ServiceLoader-discovers any
 * {@code de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * implementations a plugin JAR ships, registering each one's
 * {@code @Command} class as a top-level subcommand. {@code unhide}
 * is the first plugin under the new shape — moved from in-tree to
 * {@code plugins/unhide/} alongside the backend bits.
 *
 * <p>See {@code aidocs/22-admin-cli-draft.md} for the L1 design,
 * {@code aidocs/16}-PM1d row for the SPI extensibility shape, and
 * {@code docs/reference/plugins.md} §"CLI extensibility" for the
 * third-party-plugin contribution recipe.
 */
@Command(
  name = "shepard-admin",
  mixinStandardHelpOptions = true,
  versionProvider = VersionProvider.class,
  description = "Administration CLI for a running shepard instance.",
  subcommands = {
    FeaturesCommand.class,
    FilesCommand.class,
    HealthCommand.class,
    MigrationsCommand.class,
    PluginsCommand.class,
    SemanticCommand.class,
    StorageCommand.class,
  }
)
public final class ShepardAdmin implements Runnable {

  /** Invoked when no subcommand is given — prints the usage banner. */
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  /**
   * Build a {@link CommandLine} for {@code shepard-admin} with all
   * core subcommands wired and the PM1d
   * {@link CliPluginBootstrap} discovery applied. Public so tests
   * can assert on the post-discovery subcommand graph without
   * forking a JVM, and so production {@link #main(String[])} and
   * any future programmatic embedding share the same wiring.
   */
  public static CommandLine commandLine() {
    CommandLine cmd = new CommandLine(new ShepardAdmin()).setCaseInsensitiveEnumValuesAllowed(true);
    new CliPluginBootstrap().discoverInto(cmd);
    return cmd;
  }

  public static void main(String[] args) {
    int exit = commandLine().execute(args);
    System.exit(exit);
  }
}
