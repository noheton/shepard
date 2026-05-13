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
 * {@code shepard-admin plugins enable <id>} — PM1b.
 *
 * <p>Flips the named plugin's runtime override to {@code enabled=true}
 * via {@code PATCH /v2/admin/plugins/<id>} with body
 * {@code {"enabled": true}}.
 *
 * <p>The flip is in-memory only — surviving across restart requires
 * editing {@code shepard.plugins.<id>.enabled} in
 * {@code application.properties} (PM1a's drop-in model). The CLI
 * surface intentionally mirrors the REST shape so the same admin
 * can operate from either pathway.
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
        " (the runtime override survives until restart; persist via shepard.plugins." +
        updated.getId() + ".enabled in application.properties)"
    );
    return 0;
  }
}
