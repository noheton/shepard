package de.dlr.shepard.plugins.jupyter.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.plugins.jupyter.cli.JupyterConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * J1e — {@code shepard-admin jupyter enable}. Flips the master switch
 * for the "Open in JupyterHub" affordance via
 * {@code PATCH /v2/admin/plugins/jupyter/config} with {@code {"enabled": true}}.
 *
 * <p>This call alone is not enough to make the affordance visible —
 * the user must also have set a hub URL (via
 * {@link JupyterSetHubUrlCommand}). The command prints a hint when
 * {@code hubUrl} is still null after enabling.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the 'Open in JupyterHub' link-out affordance."
)
public final class JupyterEnableCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/plugins/jupyter/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("enabled", true);

    JupyterConfig config;
    try {
      config = client.patchJson(CONFIG_PATH, patch, new TypeReference<JupyterConfig>() {});
    } catch (AdminCliException e) {
      throw e;
    } catch (Exception ex) {
      throw new AdminCliException("Could not update Jupyter config at " + CONFIG_PATH + ": " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(config));
      } catch (Exception e) {
        throw new AdminCliException("Could not serialise Jupyter config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("JUPYTER — affordance enabled");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow("hubUrl", config.getHubUrl() != null ? config.getHubUrl() : "(not set)");
    out().print(table.render());

    if (config.getHubUrl() == null || config.getHubUrl().isBlank()) {
      out().println();
      out().println("note: affordance is still hidden until 'shepard-admin jupyter set-hub-url <url>' is run.");
    }

    return 0;
  }
}
