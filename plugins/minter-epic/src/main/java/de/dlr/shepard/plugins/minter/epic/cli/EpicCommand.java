package de.dlr.shepard.plugins.minter.epic.cli;

import picocli.CommandLine.Command;

/**
 * KIP1c — container for {@code shepard-admin minters epic <verb>}
 * subcommands.
 *
 * <p>Eight verbs cover the full operator runtime story: status,
 * enable/disable, set-api-url, set-prefix, credential management
 * (set-credential, clear-credential), and the diagnostic
 * test-connection.
 */
@Command(
  name = "epic",
  mixinStandardHelpOptions = true,
  description = "Configure the ePIC handle service minter plugin (KIP1c).",
  subcommands = {
    EpicStatusCommand.class,
    EpicEnableCommand.class,
    EpicDisableCommand.class,
    EpicSetApiUrlCommand.class,
    EpicSetPrefixCommand.class,
    EpicSetCredentialCommand.class,
    EpicClearCredentialCommand.class,
    EpicTestConnectionCommand.class,
  }
)
public final class EpicCommand implements Runnable {

  @Override
  public void run() {
    // No-op — Picocli prints usage when the user types just
    // `shepard-admin minters epic`.
  }
}
