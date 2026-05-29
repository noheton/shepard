package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.JupyterConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * J1e — {@code shepard-admin jupyter status}. Read-only visibility
 * into the JupyterHub link-out configuration.
 *
 * <p>Exit code: 0 when {@code enabled} AND {@code hubUrl} is set (the
 * affordance will be visible to users); 1 when either knob is clear
 * (the affordance is suppressed).
 */
@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = "Show the current JupyterHub link-out configuration."
)
public final class JupyterStatusCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/jupyter/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    var response = client.get(CONFIG_PATH);
    String body = response.body();

    JupyterConfig config;
    try {
      config = ShepardHttpClient.mapper().readValue(body, JupyterConfig.class);
    } catch (Exception ex) {
      throw new AdminCliException("Could not parse Jupyter config response: " + ex.getMessage(), ex);
    }

    boolean affordanceVisible = config.isEnabled() && config.getHubUrl() != null && !config.getHubUrl().isBlank();

    if (wantsJson()) {
      out().println(body);
      return affordanceVisible ? 0 : 1;
    }

    out().println("JUPYTER — JupyterHub link-out configuration");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow("hubUrl", config.getHubUrl() != null ? config.getHubUrl() : "(not set)");
    table.addRow("affordanceVisible", Boolean.toString(affordanceVisible));
    out().print(table.render());

    if (!affordanceVisible) {
      out().println();
      if (!config.isEnabled()) {
        out().println("note: use 'shepard-admin jupyter enable' to turn the affordance on.");
      }
      if (config.getHubUrl() == null || config.getHubUrl().isBlank()) {
        out().println("note: use 'shepard-admin jupyter set-hub-url <url>' to configure the target hub.");
      }
    }

    return affordanceVisible ? 0 : 1;
  }
}
