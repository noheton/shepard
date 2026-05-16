package de.dlr.shepard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.commands.FeaturesCommand;
import de.dlr.shepard.cli.commands.FeaturesListCommand;
import de.dlr.shepard.cli.commands.FilesCommand;
import de.dlr.shepard.cli.commands.FilesMigrateCommand;
import de.dlr.shepard.cli.commands.FilesMigrateStatusCommand;
import de.dlr.shepard.cli.commands.HealthCommand;
import de.dlr.shepard.cli.commands.MigrationsCommand;
import de.dlr.shepard.cli.commands.MigrationsStatusCommand;
import de.dlr.shepard.cli.commands.SemanticCommand;
import de.dlr.shepard.cli.commands.SemanticRefreshOntologiesCommand;
import de.dlr.shepard.cli.commands.StorageCommand;
import de.dlr.shepard.cli.commands.StorageStatusCommand;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Smoke tests for the Picocli wiring on the top-level
 * {@code ShepardAdmin} entry point: usage banner, version provider,
 * and that all four top-level sub-commands are discoverable.
 */
final class ShepardAdminTest {

  @Test
  void allTopLevelSubcommandsAreRegistered() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    assertThat(cmd.getSubcommands().keySet())
      .contains("features", "files", "health", "migrations", "semantic", "storage");
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

  @Test
  void semanticSubcommandHasRefreshOntologiesUnderIt() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    CommandLine semantic = cmd.getSubcommands().get("semantic");
    assertThat(semantic).isNotNull();
    assertThat((Object) semantic.getCommand()).isInstanceOf(SemanticCommand.class);
    assertThat(semantic.getSubcommands().keySet()).contains("refresh-ontologies");
    assertThat((Object) semantic.getSubcommands().get("refresh-ontologies").getCommand())
      .isInstanceOf(SemanticRefreshOntologiesCommand.class);
  }

  @Test
  void semanticAloneIsAccessible() {
    int exit = new CommandLine(new ShepardAdmin()).execute("semantic");
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void storageSubcommandHasStatusUnderIt() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    CommandLine storage = cmd.getSubcommands().get("storage");
    assertThat(storage).isNotNull();
    assertThat((Object) storage.getCommand()).isInstanceOf(StorageCommand.class);
    assertThat(storage.getSubcommands().keySet()).contains("status");
    assertThat((Object) storage.getSubcommands().get("status").getCommand())
      .isInstanceOf(StorageStatusCommand.class);
  }

  @Test
  void storageAloneIsAccessible() {
    int exit = new CommandLine(new ShepardAdmin()).execute("storage");
    assertThat(exit).isEqualTo(0);
  }

  @Test
  void filesSubcommandHasMigrateAndMigrateStatusUnderIt() {
    CommandLine cmd = new CommandLine(new ShepardAdmin());

    CommandLine files = cmd.getSubcommands().get("files");
    assertThat(files).isNotNull();
    assertThat((Object) files.getCommand()).isInstanceOf(FilesCommand.class);
    assertThat(files.getSubcommands().keySet()).contains("migrate", "migrate-status");
    assertThat((Object) files.getSubcommands().get("migrate").getCommand())
      .isInstanceOf(FilesMigrateCommand.class);
    assertThat((Object) files.getSubcommands().get("migrate-status").getCommand())
      .isInstanceOf(FilesMigrateStatusCommand.class);
  }

  @Test
  void filesAloneIsAccessible() {
    int exit = new CommandLine(new ShepardAdmin()).execute("files");
    assertThat(exit).isEqualTo(0);
  }
}
