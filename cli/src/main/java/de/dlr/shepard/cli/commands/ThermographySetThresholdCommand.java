package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.ThermographyConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — {@code shepard-admin thermography set-threshold}.
 * Updates the quality-score threshold at runtime via
 * {@code PATCH /v2/admin/thermography/config} with
 * {@code {"thresholdC": <value>}}.
 *
 * <p>The threshold is the denominator used to compute the NDT quality score:
 * hot-spot deltas above this threshold push the score toward 0; below it
 * toward 1. Typical MFFD AFP thermography threshold is 80 °C.
 *
 * <p>Pass {@code --reset} to revert the field to the deploy-time default
 * ({@code shepard.v2.thermography.threshold-c}).
 */
@Command(
  name = "set-threshold",
  mixinStandardHelpOptions = true,
  description = "Set the quality-score threshold in degrees Celsius."
)
public final class ThermographySetThresholdCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/thermography/config";

  @Option(
    names = { "--value" },
    required = false,
    description = "Threshold in degrees Celsius (e.g. 80.0). " +
    "Omit together with --reset to do nothing."
  )
  Double value;

  @Option(
    names = { "--reset" },
    description = "Revert thresholdC to the deploy-time default " +
    "(shepard.v2.thermography.threshold-c). Sends explicit JSON null.",
    defaultValue = "false"
  )
  boolean reset;

  @Override
  protected Integer run() {
    if (value == null && !reset) {
      err().println("error: provide --value <double> or --reset.");
      return 1;
    }
    if (value != null && reset) {
      err().println("error: --value and --reset are mutually exclusive.");
      return 1;
    }

    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    // reset → explicit JSON null = revert to deploy-time default on server.
    patch.put("thresholdC", reset ? null : value);

    ThermographyConfig config;
    try {
      config = client.patchJson(CONFIG_PATH, patch, new TypeReference<ThermographyConfig>() {});
    } catch (AdminCliException e) {
      throw e;
    } catch (Exception ex) {
      throw new AdminCliException(
        "Could not update thermography config at " + CONFIG_PATH + ": " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(config));
      } catch (Exception e) {
        throw new AdminCliException(
          "Could not serialise thermography config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("THERMOGRAPHY — thresholdC updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("thresholdC", String.format("%.2f °C", config.getThresholdC()));
    table.addRow("gridWidth",  Integer.toString(config.getGridWidth()));
    table.addRow("gridHeight", Integer.toString(config.getGridHeight()));
    out().print(table.render());

    return 0;
  }
}
