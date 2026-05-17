package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin minters epic set-api-url <url>} — KIP1c.
 *
 * <p>Updates {@code :EpicMinterConfig.apiBaseUrl}. Typical value:
 * {@code https://handle.argo.grnet.gr/api}. Pass an empty string to
 * clear.
 */
@Command(
  name = "set-api-url",
  mixinStandardHelpOptions = true,
  description = "Set the ePIC Handle Service REST API base URL."
)
public final class EpicSetApiUrlCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<url>",
    description = "ePIC API base URL — e.g. https://handle.argo.grnet.gr/api. " +
    "Omit or pass an empty string to clear."
  )
  String apiBaseUrl;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("apiBaseUrl", (apiBaseUrl == null || apiBaseUrl.isBlank()) ? null : apiBaseUrl);

    EpicConfig cfg = buildClient()
      .patchJson(EpicAdminPaths.CONFIG, body, new TypeReference<EpicConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("apiBaseUrl = " + (cfg.getApiBaseUrl() == null ? "(unset)" : cfg.getApiBaseUrl()));
    return 0;
  }
}
