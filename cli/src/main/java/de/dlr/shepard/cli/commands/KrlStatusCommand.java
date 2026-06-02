package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.KrlInterpreterConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * KRL-CONFIG-1 — {@code shepard-admin krl status}. Read-only visibility
 * into the KRL interpreter configuration.
 *
 * <p>Exit code: 0 when {@code enabled} is true; 1 when the feature is
 * disabled (the interpret endpoint returns 503 when disabled).
 */
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show the current KRL interpreter configuration.")
public final class KrlStatusCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/krl/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    var response = client.get(CONFIG_PATH);
    String body = response.body();

    KrlInterpreterConfig config;
    try {
      config = ShepardHttpClient.mapper().readValue(body, KrlInterpreterConfig.class);
    } catch (Exception ex) {
      throw new AdminCliException("Could not parse KRL config response: " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      out().println(body);
      return config.isEnabled() ? 0 : 1;
    }

    out().println("KRL — KRL interpreter configuration");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow(
        "sidecarUrl", config.getSidecarUrl() != null ? config.getSidecarUrl() : "(deploy-time default)");
    table.addRow("timeoutSeconds", Integer.toString(config.getTimeoutSeconds()));
    table.addRow("maxBodySizeMb", Integer.toString(config.getMaxBodySizeMb()));
    out().print(table.render());

    if (!config.isEnabled()) {
      out().println();
      out().println("note: use 'shepard-admin krl enable' to activate the KRL interpreter.");
    }

    return config.isEnabled() ? 0 : 1;
  }
}
