package de.dlr.shepard.plugins.video.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.output.TableFormatter;
import de.dlr.shepard.plugins.video.cli.io.VideoConfigCli;
import picocli.CommandLine.Command;

/**
 * VID1c — {@code shepard-admin video status}.
 *
 * <p>Fetches and prints the runtime {@code :VideoConfig} singleton.
 * Mirrors the shape returned by {@code GET /v2/admin/video/config}.
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Print the current video plugin runtime config."
)
public final class VideoStatusCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    VideoConfigCli cfg = buildClient().getJson(VideoAdminPaths.CONFIG, new TypeReference<VideoConfigCli>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise VideoConfig to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("ffprobeEnabled", Boolean.toString(cfg.isFfprobeEnabled()));
    table.addRow("maxFileSizeMb", cfg.getMaxFileSizeMb() == null ? "(unlimited)" : cfg.getMaxFileSizeMb().toString() + " MiB");
    out().print(table.render());
    return 0;
  }
}
