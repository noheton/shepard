package de.dlr.shepard.plugins.minter.datacite.cli;

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
 * container with a single nested {@link DataciteCommand} subgroup
 * that itself holds the verb subcommands. The nesting matches
 * {@code shepard-admin semantic ontologies …} — two levels deep so
 * future minter plugins (ePIC, Crossref, …) can ship their own
 * {@code minters <plugin> …} subgroup without colliding with KIP1d.
 *
 * <p>When a second minter plugin ships, both providers will return
 * a {@code MintersCommand} class — the in-CLI bootstrap deduplicates
 * by class name + merges their subcommands. (PM1d's bootstrap
 * surfaces a WARN on duplicate top-level names; the duplicate
 * handling here is plugin-local — both plugin JARs ship the same
 * top-level {@code minters} class so the WARN logs once but the
 * subcommands all register.)
 */
public final class MintersAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return MintersCommand.class;
  }
}
