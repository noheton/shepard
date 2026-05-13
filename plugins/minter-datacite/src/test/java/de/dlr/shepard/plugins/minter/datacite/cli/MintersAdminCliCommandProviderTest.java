package de.dlr.shepard.plugins.minter.datacite.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * KIP1d — SPI discovery + Picocli structure smoke tests for the
 * {@code minters} CLI subgroup.
 */
class MintersAdminCliCommandProviderTest {

  @Test
  void provider_isDiscoverableViaServiceLoader() {
    ServiceLoader<AdminCliCommandProvider> loader = ServiceLoader.load(AdminCliCommandProvider.class);

    boolean foundMinters = false;
    for (AdminCliCommandProvider p : loader) {
      if (p instanceof MintersAdminCliCommandProvider) {
        foundMinters = true;
        assertThat(p.commandClass()).isEqualTo(MintersCommand.class);
      }
    }
    assertThat(foundMinters).as("MintersAdminCliCommandProvider must be ServiceLoader-discoverable").isTrue();
  }

  @Test
  void mintersCommand_hasDataciteSubcommand() {
    CommandLine cmd = new CommandLine(new MintersCommand());

    assertThat(cmd.getSubcommands()).containsKey("datacite");
  }

  @Test
  void dataciteCommand_listsAllExpectedVerbs() {
    CommandLine cmd = new CommandLine(new DataciteCommand());

    List<String> expected = List.of(
      "status",
      "enable",
      "disable",
      "set-api-url",
      "set-prefix",
      "set-repository-id",
      "set-publisher",
      "set-landing-page-base",
      "set-state",
      "set-password",
      "clear-password",
      "test-connection"
    );

    assertThat(cmd.getSubcommands().keySet()).containsAll(expected);
  }

  @Test
  void mintersCommand_carriesUsageBanner() {
    CommandLine cmd = new CommandLine(new MintersCommand());
    String usage = cmd.getUsageMessage();
    assertThat(usage).contains("minters");
    assertThat(usage).contains("datacite");
  }

  @Test
  void mintersCommand_runDoesNotThrow() {
    // No-op container — typing just `shepard-admin minters` prints
    // usage; the .run() method should be a clean no-op.
    new MintersCommand().run();
    new DataciteCommand().run();
  }
}
