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
 * {@code shepard-admin minters datacite set-landing-page-base <url>}
 * — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.landingPageBase} — the
 * base URL prepended to {@code /<kind>/<appId>} when building the
 * DOI's {@code url} attribute. E.g.
 * {@code https://shepard.example.dlr.de/v2}. Pass an empty string to
 * clear (mint will then fail with a clear error).
 */
@Command(
  name = "set-landing-page-base",
  mixinStandardHelpOptions = true,
  description = "Set the landing-page base URL (the DOI's resolution target prefix)."
)
public final class DataciteSetLandingPageBaseCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<url>",
    description = "Landing-page base URL — e.g. https://shepard.example.dlr.de/v2. Omit or pass an empty string to clear."
  )
  String landingPageBase;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("landingPageBase", (landingPageBase == null || landingPageBase.isBlank()) ? null : landingPageBase);

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

    out().println("landingPageBase = " + (cfg.getLandingPageBase() == null ? "(unset)" : cfg.getLandingPageBase()));
    return 0;
  }
}
