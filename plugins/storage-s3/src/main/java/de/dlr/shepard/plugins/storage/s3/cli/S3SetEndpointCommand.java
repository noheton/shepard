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
 * {@code shepard-admin storage s3 set-endpoint <url>} — FS1b.
 *
 * <p>Sets the S3 endpoint override (e.g. {@code https://garage.example.org}).
 * Pass an empty string to clear the override and use AWS's default endpoint.
 */
@Command(
  name = "set-endpoint",
  mixinStandardHelpOptions = true,
  description = "Set the S3-compatible endpoint URL (e.g. https://garage.example.org). " +
  "Leave empty to use AWS default endpoint resolution."
)
public final class S3SetEndpointCommand extends AbstractCommand {

  @Parameters(index = "0", description = "Endpoint URL (e.g. https://s3.example.com:9000). Pass '' to clear.")
  String endpointUrl;

  @Override
  protected Integer run() {
    Map<String, Object> body = Map.of("endpointUrl", endpointUrl == null ? "" : endpointUrl);
    S3Config cfg = buildClient()
      .patchJson(S3AdminPaths.CONFIG, body, new TypeReference<S3Config>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise S3 config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("S3 endpoint set to: " + (cfg.getEndpointUrl() == null || cfg.getEndpointUrl().isBlank()
      ? "(AWS default)" : cfg.getEndpointUrl()));
    return 0;
  }
}
