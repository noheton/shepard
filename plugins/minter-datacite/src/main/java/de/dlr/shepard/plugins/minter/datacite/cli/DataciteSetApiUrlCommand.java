package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin minters datacite set-api-url <url>} — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.apiBaseUrl}. Typical values:
 * {@code https://api.test.datacite.org} (Fabrica test, the default)
 * and {@code https://api.datacite.org} (production). Pass an empty
 * string to reset to the test default.
 */
@Command(
  name = "set-api-url",
  mixinStandardHelpOptions = true,
  description = "Set the DataCite REST API base URL (test or production)."
)
public final class DataciteSetApiUrlCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<url>",
    description = "DataCite API base URL — typically https://api.test.datacite.org or https://api.datacite.org. " +
    "Omit or pass an empty string to fall back to the test default."
  )
  String apiBaseUrl;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("apiBaseUrl", (apiBaseUrl == null || apiBaseUrl.isBlank()) ? null : apiBaseUrl);

    DataciteConfig cfg = buildClient()
      .patchJson(DataciteAdminPaths.CONFIG, body, new TypeReference<DataciteConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("apiBaseUrl = " + (cfg.getApiBaseUrl() == null ? "(unset)" : cfg.getApiBaseUrl()));
    return 0;
  }
}
