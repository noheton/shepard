package de.dlr.shepard.plugins.video.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.video.cli.io.VideoConfigCli;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * VID1c — {@code shepard-admin video set-ffprobe-enabled <true|false>}.
 *
 * <p>Flips {@code :VideoConfig.ffprobeEnabled} via RFC 7396 merge-patch.
 * When set to {@code false}, {@code VideoProbeService.probe()} is skipped
 * on every upload — useful if {@code ffprobe} is not installed or when
 * probe overhead is undesirable.
 */
@Command(
  name = "set-ffprobe-enabled",
  mixinStandardHelpOptions = true,
  description = "Enable or disable ffprobe metadata extraction on upload (true/false)."
)
public final class VideoSetFfprobeEnabledCommand extends AbstractCommand {

  @Parameters(index = "0", description = "true to enable ffprobe; false to disable.")
  private boolean enabled;

  @Override
  protected Integer run() {
    VideoConfigCli cfg = buildClient()
      .patchJson(VideoAdminPaths.CONFIG, Map.of("ffprobeEnabled", enabled), new TypeReference<VideoConfigCli>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise VideoConfig to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("ffprobeEnabled=" + cfg.isFfprobeEnabled() + ", maxFileSizeMb=" + (cfg.getMaxFileSizeMb() == null ? "(unlimited)" : cfg.getMaxFileSizeMb() + " MiB"));
    return 0;
  }
}
