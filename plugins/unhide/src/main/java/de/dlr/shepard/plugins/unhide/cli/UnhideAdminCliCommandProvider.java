package de.dlr.shepard.plugins.unhide.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * PM1d — registers the {@code unhide} Picocli subcommand group with
 * the {@code shepard-admin} root command.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via the
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file shipped in the same JAR.
 *
 * <p>The provider returns {@link UnhideCommand}, which is itself a
 * no-op parent container with seven nested verb commands
 * ({@code status}, {@code enable}, {@code disable},
 * {@code set-feed-public}, {@code set-contact-email},
 * {@code rotate-harvest-key}, {@code revoke-harvest-key}). The
 * end-user UX is byte-identical to the pre-PM1d behaviour — only
 * the source code's home moved from the in-tree {@code cli/}
 * module to {@code plugins/unhide/}.
 */
public final class UnhideAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return UnhideCommand.class;
  }
}
