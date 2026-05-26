package de.dlr.shepard.plugins.video.cli;

import picocli.CommandLine.Command;

/**
 * VID1c — container for {@code shepard-admin video <verb>} sub-commands.
 *
 * <p>Mirrors the UH1a / AAS1c CLI design: a no-op parent command
 * whose sub-commands handle each verb. Registered with
 * {@code shepard-admin} via {@code VideoAdminCliCommandProvider}
 * (an {@link de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * declared in {@code META-INF/services/}).
 */
@Command(
  name = "video",
  mixinStandardHelpOptions = true,
  description = "Manage the video plugin runtime config.",
  subcommands = {
    VideoStatusCommand.class,
    VideoSetFfprobeEnabledCommand.class,
    VideoSetMaxFileSizeCommand.class,
  }
)
public final class VideoCommand implements Runnable {

  @Override
  public void run() {
    // No-op: a user typing `shepard-admin video` gets the usage banner.
  }
}
