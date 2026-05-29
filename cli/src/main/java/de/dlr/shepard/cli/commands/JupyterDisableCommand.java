package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.JupyterConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * J1e — {@code shepard-admin jupyter disable}. Suppresses the "Open in
 * JupyterHub" affordance instance-wide via
 * {@code PATCH /v2/admin/jupyter/config} with {@code {"enabled": false}}.
 *
 * <p>The configured {@code hubUrl} is left untouched so it can be
 * re-enabled later without re-entering the URL.
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Disable the 'Open in JupyterHub' link-out affordance (hubUrl untouched)."
)
public final class JupyterDisableCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/jupyter/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("enabled", false);

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

    out().println("JUPYTER — affordance disabled");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow("hubUrl", config.getHubUrl() != null ? config.getHubUrl() : "(not set)");
    out().print(table.render());

    return 0;
  }
}
