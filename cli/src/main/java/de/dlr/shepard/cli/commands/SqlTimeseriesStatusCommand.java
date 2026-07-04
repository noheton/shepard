package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.SqlTimeseriesConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * FTOGGLE-CLI-PARITY-1 — {@code shepard-admin sql-timeseries status}.
 * Read-only view of the SQL timeseries runtime configuration.
 *
 * <p>Prints the effective (resolved) values for {@code enabled},
 * {@code maxRows}, and {@code maxDuration} as returned by
 * {@code GET /v2/admin/sql-timeseries/config}.
 *
 * <p>Exit code: always 0 (status command; not a conditional gate).
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the current SQL timeseries runtime configuration."
)
public final class SqlTimeseriesStatusCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/config/sql-timeseries";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    var response = client.get(CONFIG_PATH);
    String body = response.body();

    SqlTimeseriesConfig config;
    try {
      config = ShepardHttpClient.mapper().readValue(body, SqlTimeseriesConfig.class);
    } catch (Exception ex) {
      throw new AdminCliException(
        "Could not parse SQL timeseries config response: " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      out().println(body);
      return 0;
    }

    out().println("SQL-TIMESERIES — runtime configuration");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled",     config.getEnabled() != null ? Boolean.toString(config.getEnabled()) : "(default)");
    table.addRow("maxRows",     Long.toString(config.getMaxRows()));
    table.addRow("maxDuration", config.getMaxDuration() != null ? config.getMaxDuration() : "(default)");
    out().print(table.render());

    return 0;
  }
}
