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
import picocli.CommandLine.Parameters;

/**
 * J1e — {@code shepard-admin jupyter set-hub-url <url>}. Configures
 * the JupyterHub base URL the "Open in JupyterHub" affordance targets
 * via {@code PATCH /v2/admin/jupyter/config} with
 * {@code {"hubUrl": "<url>"}}.
 *
 * <p>Pass an empty string ({@code ""}) to clear the URL — this sends an
 * explicit JSON {@code null} in the PATCH body, reverting to the
 * deploy-time default ({@code shepard.jupyter.hub-url}).
 *
 * <p>The URL is server-validated as an absolute http(s) URL with a
 * non-empty host; an invalid URL returns HTTP 400 surfaced here as
 * an {@link AdminCliException}.
 */
@Command(
  name = "set-hub-url",
  mixinStandardHelpOptions = true,
  description = "Set the JupyterHub base URL the link-out affordance targets."
)
public final class JupyterSetHubUrlCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/jupyter/config";

  @Parameters(
    index = "0",
    paramLabel = "<url>",
    description = "JupyterHub base URL, e.g. https://hub.example.org. Pass empty string to clear."
  )
  String hubUrl;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    // Empty string → explicit JSON null = clear the field on the server.
    patch.put("hubUrl", (hubUrl == null || hubUrl.isEmpty()) ? null : hubUrl);

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

    out().println("JUPYTER — hubUrl updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow("hubUrl", config.getHubUrl() != null ? config.getHubUrl() : "(not set)");
    out().print(table.render());

    return 0;
  }
}
