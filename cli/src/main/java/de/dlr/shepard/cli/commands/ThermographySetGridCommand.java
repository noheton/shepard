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
 * MFFD-NDT-ADMIN-CONFIG-1 — {@code shepard-admin thermography set-grid}.
 * Updates the plate-heatmap grid dimensions at runtime via
 * {@code PATCH /v2/admin/thermography/config} with
 * {@code {"gridWidth": <w>, "gridHeight": <h>}}.
 *
 * <p>The grid dimensions control how the plate-surface temperature field
 * is accumulated into a 2D heatmap during analysis. Default is 64×64.
 * Larger grids give finer spatial resolution but produce proportionally
 * larger cached plate-heatmap annotations.
 *
 * <p>Pass {@code --reset} to revert both dimensions to their deploy-time
 * defaults ({@code shepard.v2.thermography.grid-width} and
 * {@code shepard.v2.thermography.grid-height}).
 */
@Command(
  name = "set-grid",
  mixinStandardHelpOptions = true,
  description = "Set the plate-heatmap grid dimensions (columns × rows)."
)
public final class ThermographySetGridCommand extends AbstractCommand {

  static final String CONFIG_PATH = "/v2/admin/thermography/config";

  @Option(
    names = { "--width" },
    description = "Number of grid columns (e.g. 64)."
  )
  Integer width;

  @Option(
    names = { "--height" },
    description = "Number of grid rows (e.g. 64)."
  )
  Integer height;

  @Option(
    names = { "--reset" },
    description = "Revert gridWidth and gridHeight to their deploy-time defaults " +
    "(shepard.v2.thermography.grid-*). Sends explicit JSON null for both fields.",
    defaultValue = "false"
  )
  boolean reset;

  @Override
  protected Integer run() {
    if (!reset && width == null && height == null) {
      err().println("error: provide --width and/or --height, or --reset.");
      return 1;
    }
    if (reset && (width != null || height != null)) {
      err().println("error: --reset is mutually exclusive with --width / --height.");
      return 1;
    }

    ShepardHttpClient client = buildClient();

    Map<String, Object> patch = new LinkedHashMap<>();
    if (reset) {
      // Explicit JSON null = revert to deploy-time default on server.
      patch.put("gridWidth", null);
      patch.put("gridHeight", null);
    } else {
      if (width != null) {
        patch.put("gridWidth", width);
      }
      if (height != null) {
        patch.put("gridHeight", height);
      }
    }

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

    out().println("THERMOGRAPHY — grid dimensions updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("thresholdC", String.format("%.2f °C", config.getThresholdC()));
    table.addRow("gridWidth",  Integer.toString(config.getGridWidth()));
    table.addRow("gridHeight", Integer.toString(config.getGridHeight()));
    out().print(table.render());

    return 0;
  }
}
