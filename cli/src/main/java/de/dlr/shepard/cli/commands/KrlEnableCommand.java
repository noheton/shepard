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

/**
 * KRL-CONFIG-1 — {@code shepard-admin krl enable}. Flips the master switch
 * for the KRL interpreter sidecar via
 * {@code PATCH /v2/admin/krl/config} with {@code {"enabled": true}}.
 *
 * <p>The sidecar must still be running and reachable at the configured
 * {@code sidecarUrl} for interpret calls to succeed.
 */
@Command(
    name = "enable",
    mixinStandardHelpOptions = true,
    description = "Enable the KRL interpreter sidecar feature.")
public final class KrlEnableCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/krl/config";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("enabled", true);

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

    out().println("KRL — interpreter enabled");
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
