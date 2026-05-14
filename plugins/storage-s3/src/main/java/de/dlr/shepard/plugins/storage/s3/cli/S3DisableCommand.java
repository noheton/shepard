package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3Config;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin storage s3 disable} — FS1b.
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the S3 storage adapter (sets enabled=false)."
)
public final class S3DisableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    S3Config cfg = buildClient()
      .patchJson(S3AdminPaths.CONFIG, Map.of("enabled", false), new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("S3 storage disabled — enabled=" + cfg.isEnabled());
    return 0;
  }
}
