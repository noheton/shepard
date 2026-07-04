package de.dlr.shepard.plugins.hdf5.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * FTOGGLE-CLI-PARITY-1 — registers the {@code hdf} Picocli subcommand
 * group with the {@code shepard-admin} root command.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via the
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file shipped in this JAR.
 *
 * <p>The provider returns {@link HdfCommand}, a no-op parent container
 * with three nested verb commands ({@code status}, {@code enable},
 * {@code disable}).
 */
public final class HdfAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return HdfCommand.class;
  }
}
