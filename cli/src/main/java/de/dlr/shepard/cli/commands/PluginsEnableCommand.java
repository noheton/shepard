package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.PluginInfo;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin plugins enable <id>} — PM1b (CLI shape) +
 * PM1e (persistent override).
 *
 * <p>Flips the named plugin's runtime override to {@code enabled=true}
 * via {@code PATCH /v2/admin/plugins/<id>} with body
 * {@code {"enabled": true}}.
 *
 * <p>PM1e — the flip is persisted to the
 * {@code :PluginRuntimeOverride} table on the backend, so it
 * survives across restart without editing
 * {@code application.properties}. (Flipping back to the deploy-time
 * default deletes the row, keeping the table sparse.)
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Set the named plugin's runtime override to enabled=true."
)
public final class PluginsEnableCommand extends AbstractCommand {

  @Parameters(index = "0", description = "Plugin id (matches shepard.plugins.<id>.enabled).")
  String pluginId;

  @Override
  protected Integer run() {
    PluginInfo updated = buildClient()
      .patchJson(PluginsAdminPaths.forId(pluginId), Map.of("enabled", true), new TypeReference<PluginInfo>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(updated));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise plugin info to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "Plugin '" + updated.getId() + "' enabled — state=" + updated.getState() +
        " (override persisted; survives restart)"
    );
    return 0;
  }
}
