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
 * KRL-CONFIG-1 — {@code shepard-admin krl set-timeout <seconds>}. Configures
 * the per-call request timeout for the KRL interpreter sidecar via
 * {@code PATCH /v2/admin/krl/config} with
 * {@code {"timeoutSeconds": <seconds>}}.
 *
 * <p>Pass {@code 0} to clear the runtime override and revert to the
 * deploy-time default ({@code shepard.krl.sidecar.timeout-seconds}, default 120).
 *
 * <p>The timeout controls how long the backend waits for the sidecar to
 * respond before returning HTTP 504 to the caller. Long-running IK
 * solves (dense KRL scripts, many trajectory points) may require a
 * higher timeout than the default 120s.
 */
@Command(
    name = "set-timeout",
    mixinStandardHelpOptions = true,
    description =
        "Set the per-call KRL interpreter request timeout in seconds (0 = revert to deploy-time default).")
public final class KrlSetTimeoutCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/krl/config";

  @Parameters(
      index = "0",
      paramLabel = "<seconds>",
      description =
          "Timeout in seconds. Positive value overrides the deploy-time default."
              + " Pass 0 to revert to the deploy-time default"
              + " (shepard.krl.sidecar.timeout-seconds, default 120).")
  int timeoutSeconds;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("timeoutSeconds", timeoutSeconds);

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

    out().println("KRL — timeoutSeconds updated");
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
