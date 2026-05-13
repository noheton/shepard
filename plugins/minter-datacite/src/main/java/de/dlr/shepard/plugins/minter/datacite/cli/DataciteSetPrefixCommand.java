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
 * {@code shepard-admin minters datacite set-prefix <prefix>} — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.handlePrefix}. DataCite
 * Fabrica test installs use {@code 10.5072}; production members get
 * their own prefix. Pass an empty string to clear (mint will then
 * fail with a clear error).
 */
@Command(
  name = "set-prefix",
  mixinStandardHelpOptions = true,
  description = "Set the DataCite-allocated DOI prefix (e.g. 10.5072 for Fabrica test)."
)
public final class DataciteSetPrefixCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<prefix>",
    description = "DataCite-allocated DOI prefix. Omit or pass an empty string to clear."
  )
  String handlePrefix;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("handlePrefix", (handlePrefix == null || handlePrefix.isBlank()) ? null : handlePrefix);

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

    out().println("handlePrefix = " + (cfg.getHandlePrefix() == null ? "(unset)" : cfg.getHandlePrefix()));
    return 0;
  }
}
