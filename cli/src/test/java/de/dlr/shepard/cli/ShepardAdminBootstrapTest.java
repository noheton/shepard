package de.dlr.shepard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.plugin.CliPluginBootstrap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * PM1d — verifies the {@link ShepardAdmin#commandLine()} entry
 * point wires the {@link CliPluginBootstrap} so the
 * {@code AdminCliCommandProvider} ServiceLoader path runs at
 * startup.
 *
 * <p>This test deliberately does not assert that the {@code unhide}
 * subcommand is present: the {@code shepard-plugin-unhide} JAR is
 * NOT on the CLI module's own test classpath (its tests live in
 * the plugin module). What we assert here is that the bootstrap
 * is wired without crashing, and that the core hardcoded
 * subcommands stay intact alongside whatever the bootstrap
 * discovered (zero, in this module's test environment).
 *
 * <p>{@code UnhideCommandsTest} in {@code plugins/unhide/} covers
 * the post-discovery assertion that {@code unhide} actually appears
 * — that test runs in the plugin's test classpath where the
 * provider IS visible.
 */
final class ShepardAdminBootstrapTest {

  @Test
  void commandLine_wiresAllCoreSubcommands_withoutCrashing() {
    CommandLine cmd = ShepardAdmin.commandLine();

    // The hardcoded core subcommands always present, regardless of
    // what the bootstrap discovered.
    assertThat(cmd.getSubcommands().keySet())
      .contains("features", "health", "migrations", "plugins", "semantic");
  }

  @Test
  void commandLine_invokesBootstrap_idempotentOnRepeatedCalls() {
    CommandLine first = ShepardAdmin.commandLine();
    int firstCount = first.getSubcommands().size();

    CommandLine second = ShepardAdmin.commandLine();
    int secondCount = second.getSubcommands().size();

    // Each invocation builds a fresh CommandLine + walks discovery
    // independently — the count should match across calls.
    assertThat(secondCount).isEqualTo(firstCount);
  }

  @Test
  void commandLine_helpFlagSucceeds() {
    int exit = ShepardAdmin.commandLine().execute("--help");
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void commandLine_versionFlagSucceeds() {
    int exit = ShepardAdmin.commandLine().execute("--version");
    assertThat(exit).isEqualTo(0);
  }
}
