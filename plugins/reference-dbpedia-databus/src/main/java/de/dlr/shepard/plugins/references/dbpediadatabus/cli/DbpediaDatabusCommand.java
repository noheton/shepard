package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import picocli.CommandLine.Command;

/**
 * REF1c — container for {@code shepard-admin references dbpedia-databus <verb>}
 * sub-commands.
 *
 * <p>Contributed to {@code shepard-admin} via
 * {@link DbpediaDatabusAdminCliCommandProvider} (an
 * {@link de.dlr.shepard.cli.plugin.AdminCliCommandProvider} declared
 * in {@code META-INF/services/}).
 */
@Command(
  name = "dbpedia-databus",
  mixinStandardHelpOptions = true,
  description = "Manage the DBpedia Databus reference plugin — toggle, configure, test connection.",
  subcommands = {
    DbpediaDatabusStatusCommand.class,
    DbpediaDatabusEnableCommand.class,
    DbpediaDatabusDisableCommand.class,
    DbpediaDatabusSetBaseUrlCommand.class,
    DbpediaDatabusTestConnectionCommand.class,
  }
)
public final class DbpediaDatabusCommand implements Runnable {

  @Override
  public void run() {
    // No-op: user typing `shepard-admin references dbpedia-databus` gets the usage banner.
  }
}
