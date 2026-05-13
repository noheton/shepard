package de.dlr.shepard.plugins.minter.datacite.cli;

import picocli.CommandLine.Command;

/**
 * KIP1d — container for {@code shepard-admin minters datacite <verb>}
 * subcommands.
 *
 * <p>Twelve verbs cover the full operator runtime story: status,
 * enable/disable, set-* (api-url, prefix, repository-id, publisher,
 * landing-page-base, state), credential management
 * (set-password, clear-password), and the diagnostic
 * test-connection.
 */
@Command(
  name = "datacite",
  mixinStandardHelpOptions = true,
  description = "Configure the DataCite Fabrica minter plugin (KIP1d).",
  subcommands = {
    DataciteStatusCommand.class,
    DataciteEnableCommand.class,
    DataciteDisableCommand.class,
    DataciteSetApiUrlCommand.class,
    DataciteSetPrefixCommand.class,
    DataciteSetRepositoryIdCommand.class,
    DataciteSetPublisherCommand.class,
    DataciteSetLandingPageBaseCommand.class,
    DataciteSetStateCommand.class,
    DataciteSetPasswordCommand.class,
    DataciteClearPasswordCommand.class,
    DataciteTestConnectionCommand.class,
  }
)
public final class DataciteCommand implements Runnable {

  @Override
  public void run() {
    // No-op — Picocli prints usage when the user types just
    // `shepard-admin minters datacite`.
  }
}
