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
import picocli.CommandLine.Option;

/**
 * FTOGGLE-CLI-PARITY-1 — {@code shepard-admin sql-timeseries set-max-duration}.
 * Updates the per-query time window at runtime via
 * {@code PATCH /v2/admin/sql-timeseries/config} with
 * {@code {"maxDuration": "<iso8601>"}}.
 *
 * <p>Pass {@code --reset} to revert the field to the deploy-time default
 * ({@code shepard.timeseries.sql.max-duration}).
 */
@Command(
  name = "set-max-duration",
  mixinStandardHelpOptions = true,
  description = "Set the per-query time window for SQL timeseries queries (ISO-8601 duration)."
)
public final class SqlTimeseriesSetMaxDurationCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/config/sql-timeseries";

  @Option(
    names = { "--value" },
    description = "ISO-8601 duration string (e.g. PT60S, PT2M30S, PT1H)."
  )
  String value;

  @Option(
    names = { "--reset" },
    description = "Revert maxDuration to the deploy-time default " +
    "(shepard.timeseries.sql.max-duration). Sends explicit JSON null.",
    defaultValue = "false"
  )
  boolean reset;

  @Override
  protected Integer run() {
    if (value == null && !reset) {
      err().println("error: provide --value <ISO-8601 duration> or --reset.");
      return 1;
    }
    if (value != null && reset) {
      err().println("error: --value and --reset are mutually exclusive.");
      return 1;
    }

    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("maxDuration", reset ? null : value);

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

    out().println("SQL-TIMESERIES — maxDuration updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("maxRows",     Long.toString(config.getMaxRows()));
    table.addRow("maxDuration", config.getMaxDuration() != null ? config.getMaxDuration() : "(default)");
    out().print(table.render());

    return 0;
  }
}
