package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * REF1c — registers the {@code dbpedia-databus} Picocli subcommand
 * group under {@code shepard-admin references} with the
 * {@code shepard-admin} root command.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via the
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file shipped in the same JAR.
 */
public final class DbpediaDatabusAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return DbpediaDatabusCommand.class;
  }
}
