package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3Config;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin storage s3 enable} — FS1b.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the S3 storage adapter (sets enabled=true)."
)
public final class S3EnableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    S3Config cfg = buildClient()
      .patchJson(S3AdminPaths.CONFIG, Map.of("enabled", true), new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("S3 storage enabled — bucket=" + nv(cfg.getBucket()) +
      ", secretKeySet=" + cfg.isSecretKeySet());
    if (!cfg.isSecretKeySet()) {
      err().println("warning: secretKeySet=false — put/get will fail until you run " +
        "`shepard-admin storage s3 set-credentials`");
    }
    if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
      err().println("warning: bucket unset — set via `shepard-admin storage s3 set-bucket <name>`");
    }
    return 0;
  }

  private static String nv(String s) {
    return (s == null || s.isBlank()) ? "(unset)" : s;
  }
}
