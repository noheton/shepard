package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.SqlTimeseriesConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — {@code shepard-admin sql-timeseries disable}.
 * Suppresses the SQL timeseries query surface at runtime via
 * {@code PATCH /v2/admin/sql-timeseries/config} with {@code {"enabled": false}}.
 *
 * <p>Requires FTOGGLE-SQL-ENABLE-1 to be active on the target server
 * (the {@code enabled} field is added to the server-side config by that PR).
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the SQL timeseries query surface at runtime (sets enabled=false)."
)
public final class SqlTimeseriesDisableCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/config/sql-timeseries";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("enabled", false);

    SqlTimeseriesConfig config;
    try {
      config = client.patchJson(CONFIG_PATH, patch, new TypeReference<SqlTimeseriesConfig>() {});
    } catch (AdminCliException e) {
      throw e;
    } catch (Exception ex) {
      throw new AdminCliException(
        "Could not update SQL timeseries config at " + CONFIG_PATH + ": " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(config));
      } catch (Exception e) {
        throw new AdminCliException(
          "Could not serialise SQL timeseries config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("SQL-TIMESERIES — disabled");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled",     config.getEnabled() != null ? Boolean.toString(config.getEnabled()) : "(default)");
    table.addRow("maxRows",     Long.toString(config.getMaxRows()));
    table.addRow("maxDuration", config.getMaxDuration() != null ? config.getMaxDuration() : "(default)");
    out().print(table.render());

    return 0;
  }
}
