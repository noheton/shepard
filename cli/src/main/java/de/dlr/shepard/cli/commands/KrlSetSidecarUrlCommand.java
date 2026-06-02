package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.KrlInterpreterConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * KRL-CONFIG-1 — {@code shepard-admin krl set-sidecar-url <url>}. Configures
 * the KRL interpreter sidecar base URL via
 * {@code PATCH /v2/admin/krl/config} with
 * {@code {"sidecarUrl": "<url>"}}.
 *
 * <p>Pass an empty string ({@code ""}) to clear the URL — this sends an
 * explicit JSON {@code null} in the PATCH body, reverting to the
 * deploy-time default ({@code shepard.krl.sidecar.url}).
 *
 * <p>The URL is not validated client-side — the server returns an error
 * if the URL is not a valid absolute http(s) URL.
 */
@Command(
    name = "set-sidecar-url",
    mixinStandardHelpOptions = true,
    description = "Set the KRL interpreter sidecar base URL.")
public final class KrlSetSidecarUrlCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/krl/config";

  @Parameters(
      index = "0",
      paramLabel = "<url>",
      description =
          "KRL interpreter sidecar base URL, e.g. http://krl-interpreter-sidecar:8000."
              + " Pass empty string to clear (revert to deploy-time default).")
  String sidecarUrl;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    // Empty string → explicit JSON null = clear the field on the server.
    patch.put("sidecarUrl", (sidecarUrl == null || sidecarUrl.isEmpty()) ? null : sidecarUrl);

    KrlInterpreterConfig config;
    try {
      config = client.patchJson(CONFIG_PATH, patch, new TypeReference<KrlInterpreterConfig>() {});
    } catch (AdminCliException e) {
      throw e;
    } catch (Exception ex) {
      throw new AdminCliException(
          "Could not update KRL config at " + CONFIG_PATH + ": " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(config));
      } catch (Exception e) {
        throw new AdminCliException(
            "Could not serialise KRL config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("KRL — sidecarUrl updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("enabled", Boolean.toString(config.isEnabled()));
    table.addRow(
        "sidecarUrl",
        config.getSidecarUrl() != null ? config.getSidecarUrl() : "(deploy-time default)");
    table.addRow("timeoutSeconds", Integer.toString(config.getTimeoutSeconds()));
    table.addRow("maxBodySizeMb", Integer.toString(config.getMaxBodySizeMb()));
    out().print(table.render());

    return 0;
  }
}
