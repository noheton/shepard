package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.PluginInfo;
import de.dlr.shepard.cli.io.PluginList;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin plugins list} — PM1b.
 *
 * <p>Calls {@code GET /v2/admin/plugins} and renders the registry's
 * insertion-ordered snapshot. Human-mode columns: ID, VERSION,
 * STATE, ENABLED, SOURCE. The source column collapses null (build
 * classpath) to {@code -} so the table stays scannable.
 *
 * <p>Exit codes: 0 on success (read-only command); propagates
 * {@link AdminCliException} as exit 1 on HTTP / IO errors; 2 on
 * unexpected runtime exceptions (shared {@code AbstractCommand}
 * shape).
 */
@Command(
  name = "list",
  mixinStandardHelpOptions = true,
  description = "List every discovered plugin (id, version, state, enabled, source)."
)
public final class PluginsListCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    PluginList list = buildClient().getJson(PluginsAdminPaths.LIST, new TypeReference<PluginList>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(list));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise plugin list to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("ID", "VERSION", "STATE", "ENABLED", "SOURCE");
    for (PluginInfo p : list.getPlugins()) {
      table.addRow(
        safe(p.getId()),
        safe(p.getVersion()),
        safe(p.getState()),
        p.isEnabled() ? "true" : "false",
        p.getSourcePath() == null ? "(build-classpath)" : p.getSourcePath()
      );
    }
    out().print(table.render());
    return 0;
  }

  private static String safe(String s) {
    return s == null ? "-" : s;
  }
}
