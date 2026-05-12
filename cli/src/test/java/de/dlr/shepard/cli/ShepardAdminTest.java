package de.dlr.shepard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.commands.FeaturesCommand;
import de.dlr.shepard.cli.commands.FeaturesListCommand;
import de.dlr.shepard.cli.commands.HealthCommand;
import de.dlr.shepard.cli.commands.MigrationsCommand;
import de.dlr.shepard.cli.commands.MigrationsStatusCommand;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Smoke tests for the Picocli wiring on the top-level
 * {@code ShepardAdmin} entry point: usage banner, version provider,
 * and that all three sub-commands are discoverable.
 */
final class ShepardAdminTest {

  @Test
  void allThreeTopLevelSubcommandsAreRegistered() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    assertThat(cmd.getSubcommands().keySet())
      .contains("features", "health", "migrations");
  }

  @Test
  void featuresSubcommandHasListUnderIt() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    CommandLine features = cmd.getSubcommands().get("features");
    assertThat(features).isNotNull();
    assertThat((Object) features.getCommand()).isInstanceOf(FeaturesCommand.class);
    assertThat(features.getSubcommands().keySet()).contains("list");
    assertThat((Object) features.getSubcommands().get("list").getCommand())
      .isInstanceOf(FeaturesListCommand.class);
  }

  @Test
  void healthSubcommandTypeIsHealthCommand() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());
    assertThat((Object) cmd.getSubcommands().get("health").getCommand())
      .isInstanceOf(HealthCommand.class);
  }

  @Test
  void migrationsSubcommandHasStatusUnderIt() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    CommandLine migrations = cmd.getSubcommands().get("migrations");
    assertThat(migrations).isNotNull();
    assertThat((Object) migrations.getCommand()).isInstanceOf(MigrationsCommand.class);
    assertThat(migrations.getSubcommands().keySet()).contains("status");
    assertThat((Object) migrations.getSubcommands().get("status").getCommand())
      .isInstanceOf(MigrationsStatusCommand.class);
  }

  @Test
  void helpFlagSucceeds() {
    int exit = new CommandLine(new ShepardAdmin()).execute("--help");
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void versionFlagSucceeds() {
    StringWriter out = new StringWriter();
    CommandLine cmd = new CommandLine(new ShepardAdmin());
    cmd.setOut(new PrintWriter(out, true));

    int exit = cmd.execute("--version");

    assertThat(exit).isEqualTo(0);
    assertThat(out.toString()).contains("shepard-admin");
  }

  @Test
  void versionProviderReturnsAtLeastOneLine() throws Exception {
    String[] versions = new VersionProvider().getVersion();
    assertThat(versions).isNotEmpty();
    assertThat(versions[0]).startsWith("shepard-admin");
  }

  @Test
  void noArgsExitsCleanly() {
    StringWriter out = new StringWriter();
    CommandLine cmd = new CommandLine(new ShepardAdmin());
    cmd.setOut(new PrintWriter(out, true));
    // ShepardAdmin.run() routes to CommandLine.usage(this, System.out)
    // — we don't assert on the captured writer (that's a quirk of
    // the production code's choice to print straight to System.out),
    // just on the exit code.
    int exit = cmd.execute();
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void featuresAloneIsAccessible() {
    int exit = new CommandLine(new ShepardAdmin()).execute("features");
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void migrationsAloneIsAccessible() {
    int exit = new CommandLine(new ShepardAdmin()).execute("migrations");
    assertThat(exit).isEqualTo(0);
  }
}
