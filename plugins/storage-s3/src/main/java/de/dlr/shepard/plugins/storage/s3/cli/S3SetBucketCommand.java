package de.dlr.shepard.plugins.storage.s3.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.storage.s3.cli.io.S3Config;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin storage s3 set-bucket <name>} — FS1b.
 */
@Command(
  name = "set-bucket",
  mixinStandardHelpOptions = true,
  description = "Set the default S3 bucket name."
)
public final class S3SetBucketCommand extends AbstractCommand {

  @Parameters(index = "0", description = "Bucket name.")
  String bucket;

  @Override
  protected Integer run() {
    S3Config cfg = buildClient()
      .patchJson(S3AdminPaths.CONFIG, Map.of("bucket", bucket), new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("S3 bucket set to: " + cfg.getBucket());
    return 0;
  }
}
