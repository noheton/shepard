package de.dlr.shepard.plugins.jupyter.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * J1e-PLUGIN-REFACTOR — registers the {@code jupyter} Picocli
 * subcommand group with the {@code shepard-admin} root command.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via the
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file shipped in the same JAR. Same pattern as
 * {@code UnhideAdminCliCommandProvider} (PM1d).
 *
 * <p>{@link JupyterCommand} is itself a no-op parent container with
 * four nested verb commands ({@code status}, {@code enable},
 * {@code disable}, {@code set-hub-url}). The end-user UX is byte-
 * identical to the pre-PLUGIN-REFACTOR behaviour — only the source
 * code's home moved from the in-tree {@code cli/} module to
 * {@code plugins/jupyter/}.
 */
public final class JupyterAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return JupyterCommand.class;
  }
}
