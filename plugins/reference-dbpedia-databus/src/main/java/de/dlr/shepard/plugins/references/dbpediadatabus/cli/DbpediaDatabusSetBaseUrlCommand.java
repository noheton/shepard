package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.references.dbpediadatabus.cli.io.DbpediaDatabusConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin references dbpedia-databus set-base-url <url>} — REF1c.
 *
 * <p>Updates {@code :DbpediaDatabusConfig.defaultEndpoint}. The host is
 * also added to {@code allowedHosts} if not already present (the admin
 * must do this separately via PATCH if needed).
 */
@Command(
  name = "set-base-url",
  mixinStandardHelpOptions = true,
  description = "Set the default Databus base URL (e.g. https://databus.dbpedia.org)."
)
public final class DbpediaDatabusSetBaseUrlCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    paramLabel = "<url>",
    description = "Base URL of the Databus instance (e.g. https://databus.dbpedia.org)."
  )
  String url;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("defaultEndpoint", (url == null || url.isBlank()) ? null : url.trim());

    DbpediaDatabusConfig cfg = buildClient()
      .patchJson(DbpediaDatabusAdminPaths.CONFIG, body, new TypeReference<DbpediaDatabusConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("defaultEndpoint = " + cfg.getDefaultEndpoint());
    return 0;
  }
}
