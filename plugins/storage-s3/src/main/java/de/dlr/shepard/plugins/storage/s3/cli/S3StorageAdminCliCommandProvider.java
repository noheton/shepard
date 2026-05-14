package de.dlr.shepard.plugins.storage.s3.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * FS1b — registers the {@code storage s3} Picocli subcommand group
 * with the {@code shepard-admin} root.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}.
 *
 * <p>The provider returns {@link StorageCommand}, a no-op parent
 * container with a single nested {@link S3StorageCommand} subgroup
 * that holds the verb subcommands. The nesting mirrors
 * {@code shepard-admin minters datacite …} (two levels deep) so
 * future storage plugins can ship their own
 * {@code storage <plugin> …} subgroup.
 */
public final class S3StorageAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return StorageCommand.class;
  }
}
