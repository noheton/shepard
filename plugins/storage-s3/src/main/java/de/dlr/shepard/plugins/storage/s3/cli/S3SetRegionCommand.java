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
 * {@code shepard-admin storage s3 set-region <region>} — FS1b.
 *
 * <p>Sets the AWS region (or the dummy value most non-AWS endpoints accept,
 * typically {@code us-east-1}).
 */
@Command(
  name = "set-region",
  mixinStandardHelpOptions = true,
  description = "Set the AWS region (e.g. eu-central-1). " +
  "Non-AWS endpoints typically accept 'us-east-1' as a dummy value."
)
public final class S3SetRegionCommand extends AbstractCommand {

  @Parameters(index = "0", description = "AWS region string (e.g. us-east-1, eu-central-1).")
  String region;

  @Override
  protected Integer run() {
    S3Config cfg = buildClient()
      .patchJson(S3AdminPaths.CONFIG, Map.of("region", region), new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("S3 region set to: " + cfg.getRegion());
    return 0;
  }
}
