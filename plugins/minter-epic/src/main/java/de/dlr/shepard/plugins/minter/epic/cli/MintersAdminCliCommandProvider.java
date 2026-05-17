package de.dlr.shepard.plugins.minter.epic.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * PM1d — registers the {@code minters} Picocli subcommand group with
 * the {@code shepard-admin} root.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}.
 *
 * <p>The provider returns {@link MintersCommand}, a no-op parent
 * container with a single nested {@link EpicCommand} subgroup that
 * itself holds the verb subcommands.
 *
 * <p>When the DataCite minter plugin is also loaded, both plugins'
 * {@code MintersAdminCliCommandProvider} instances return a class
 * named {@code MintersCommand} (in different packages). The in-CLI
 * bootstrap deduplicates by class simple-name and merges subcommands,
 * so both {@code minters datacite} and {@code minters epic} appear
 * under a single {@code minters} node.
 */
public final class MintersAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return MintersCommand.class;
  }
}
