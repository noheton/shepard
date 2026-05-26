package de.dlr.shepard.plugins.video.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.video.cli.io.VideoConfigCli;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * VID1c — {@code shepard-admin video set-max-file-size <MiB>} (or {@code --clear}).
 *
 * <p>Sets (or clears) {@code :VideoConfig.maxFileSizeMb} via RFC 7396
 * merge-patch. Uploads exceeding the cap are rejected with HTTP 413.
 * {@code --clear} removes the cap (equivalent to patching
 * {@code "maxFileSizeMb": null}).
 */
@Command(
  name = "set-max-file-size",
  mixinStandardHelpOptions = true,
  description = "Set the video upload size cap in MiB, or clear it (unlimited)."
)
public final class VideoSetMaxFileSizeCommand extends AbstractCommand {

  @Parameters(index = "0", description = "Size cap in MiB (positive integer).", defaultValue = "-1", arity = "0..1")
  private long sizeMib;

  @Option(names = { "--clear" }, description = "Remove the upload-size cap (allow unlimited uploads).")
  private boolean clear;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    if (clear) {
      body.put("maxFileSizeMb", null);
    } else {
      if (sizeMib <= 0) {
        out().println("Provide a positive MiB value, or use --clear to remove the cap.");
        return 2;
      }
      body.put("maxFileSizeMb", sizeMib);
    }

    VideoConfigCli cfg = buildClient()
      .patchJson(VideoAdminPaths.CONFIG, body, new TypeReference<VideoConfigCli>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise VideoConfig to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    String cap = cfg.getMaxFileSizeMb() == null ? "(unlimited)" : cfg.getMaxFileSizeMb() + " MiB";
    out().println("maxFileSizeMb=" + cap + ", ffprobeEnabled=" + cfg.isFfprobeEnabled());
    return 0;
  }
}
