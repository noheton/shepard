package de.dlr.shepard.plugin.fileformat.thermography;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * V2CONV-A6 — PluginManifest SPI entry for
 * shepard-plugin-fileformat-thermography.
 *
 * Upgraded from Tier-0 (standalone pure-Java parser) to Tier-1 (backend dep)
 * by moving ThermographyAnalysisService + OtvisFrameRenderService + IO classes
 * into this plugin, dissolving the bespoke in-core de.dlr.shepard.v2.thermography.*
 * surface.
 *
 * <p>V2CONV-A7-THERMO further dissolved the plugin's own bespoke
 * {@code /v2/thermography/*} REST: viewing now flows through the generic
 * {@code POST /v2/shapes/render} via two {@link de.dlr.shepard.spi.view.ViewRecipeRenderer}
 * registrations ({@code OtvisFrameRenderer} + {@code ThermographyHeatmapRenderer}).
 */
public class ThermographyPluginManifest implements PluginManifest {

  @Override
  public String id() {
    return "fileformat-thermography";
  }

  @Override
  public String version() {
    return "${revision}";
  }

  @Override
  public String shepardCompatibility() {
    return ">=6.0.0-SNAPSHOT,<7";
  }

  @Override
  public String title() {
    return "Thermography / OTvis frame viewer plugin";
  }

  @Override
  public String description() {
    return "Parses Edevis OTvis thermography archives (.OTvis): decodes amplitude/phase "
        + "lock-in frames and raw calibrated temperature grids. Viewing flows through the "
        + "generic POST /v2/shapes/render endpoint: an OtvisFrameShape render returns the "
        + "frame catalogue (params.mode=index) or a colour-mapped frame PNG "
        + "(params.frame/channel, Accept: image/png); a ThermographyHeatmapShape render "
        + "returns the composite plate-heatmap grid (Accept: application/json). "
        + "Quality scoring runs on TIFF image bundles at upload (FileFormatPlugin.parse). "
        + "Bespoke /v2/thermography/* REST dissolved per V2CONV-A7-THERMO (MFFD-NDT-QUALITY-1).";
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(URI.create("https://github.com/noheton/shepard"));
  }

  @Override
  public String licence() {
    return "Apache-2.0";
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.info("V2CONV-A7-THERMO: plugin 'fileformat-thermography' registered — "
        + "OTvis frame + plate-heatmap renderers serve POST /v2/shapes/render "
        + "(OtvisFrameShape / ThermographyHeatmapShape); analysis runs on upload");
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.info("plugin 'fileformat-thermography' unregistered");
  }
}
