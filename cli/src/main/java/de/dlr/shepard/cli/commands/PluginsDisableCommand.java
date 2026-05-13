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
 * {@code shepard-admin plugins disable <id>} — PM1b.
 *
 * <p>Flips the named plugin's runtime override to {@code enabled=false}
 * via {@code PATCH /v2/admin/plugins/<id>} with body
 * {@code {"enabled": false}}.
 *
 * <p>Same caveat as {@link PluginsEnableCommand}: the flip is
 * in-memory only — the deploy-time
 * {@code shepard.plugins.<id>.enabled} key in
 * {@code application.properties} is restored on next start.
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Set the named plugin's runtime override to enabled=false."
)
public final class PluginsDisableCommand extends AbstractCommand {

  @Parameters(index = "0", description = "Plugin id (matches shepard.plugins.<id>.enabled).")
  String pluginId;

  @Override
  protected Integer run() {
    PluginInfo updated = buildClient()
      .patchJson(PluginsAdminPaths.forId(pluginId), Map.of("enabled", false), new TypeReference<PluginInfo>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(updated));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise plugin info to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "Plugin '" + updated.getId() + "' disabled — state=" + updated.getState() +
        " (the runtime override survives until restart)"
    );
    return 0;
  }
}
