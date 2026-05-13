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
 * {@code shepard-admin minters datacite set-state <state>} — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.defaultState}. One of
 * {@code draft} (default — DOI not findable, can be deleted),
 * {@code registered} (committed, resolvable, not findable), or
 * {@code findable} (fully discoverable in DataCite Commons).
 * Server-side validation rejects anything else.
 */
@Command(
  name = "set-state",
  mixinStandardHelpOptions = true,
  description = "Set the default DOI state on mint: draft | registered | findable."
)
public final class DataciteSetStateCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    paramLabel = "<state>",
    description = "One of draft | registered | findable."
  )
  String defaultState;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("defaultState", (defaultState == null || defaultState.isBlank()) ? null : defaultState);

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

    out().println("defaultState = " + (cfg.getDefaultState() == null ? "(unset)" : cfg.getDefaultState()));
    return 0;
  }
}
