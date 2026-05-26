package de.dlr.shepard.plugins.video.cli;

import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

/**
 * VID1c — registers the {@code video} Picocli subcommand group with
 * the {@code shepard-admin} root command.
 *
 * <p>Discovered at CLI startup by {@code CliPluginBootstrap}'s
 * {@link java.util.ServiceLoader} scan via the
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file shipped in the same JAR.
 *
 * <p>The provider returns {@link VideoCommand}, which is a no-op
 * parent container with three nested verb commands ({@code status},
 * {@code set-ffprobe-enabled}, {@code set-max-file-size}).
 */
public final class VideoAdminCliCommandProvider implements AdminCliCommandProvider {

  @Override
  public Class<?> commandClass() {
    return VideoCommand.class;
  }
}
