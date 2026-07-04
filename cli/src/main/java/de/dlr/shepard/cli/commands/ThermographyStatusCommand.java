package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.ThermographyConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — {@code shepard-admin thermography status}.
 * Read-only visibility into the thermography analysis configuration.
 *
 * <p>Prints the effective (resolved) values for {@code thresholdC},
 * {@code gridWidth}, and {@code gridHeight} as returned by
 * {@code GET /v2/admin/thermography/config}. These are always resolved
 * against deploy-time defaults — null never appears on the wire.
 *
 * <p>Exit code: always 0 (status command; not a conditional gate).
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the current thermography analysis configuration."
)
public final class ThermographyStatusCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/thermography/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    var response = client.get(CONFIG_PATH);
    String body = response.body();

    ThermographyConfig config;
    try {
      config = ShepardHttpClient.mapper().readValue(body, ThermographyConfig.class);
    } catch (Exception ex) {
      throw new AdminCliException(
        "Could not parse thermography config response: " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      out().println(body);
      return 0;
    }

    out().println("THERMOGRAPHY — analysis configuration");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("appId",       config.getAppId() != null ? config.getAppId() : "(none)");
    table.addRow("thresholdC",  String.format("%.2f °C", config.getThresholdC()));
    table.addRow("gridWidth",   Integer.toString(config.getGridWidth()));
    table.addRow("gridHeight",  Integer.toString(config.getGridHeight()));
    out().print(table.render());

    return 0;
  }
}
