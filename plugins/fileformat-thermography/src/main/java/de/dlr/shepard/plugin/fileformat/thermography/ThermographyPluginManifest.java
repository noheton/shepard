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
 * by moving ThermographyV2Rest + ThermographyAnalysisService +
 * OtvisFrameRenderService + IO classes into this plugin, dissolving the
 * bespoke in-core de.dlr.shepard.v2.thermography.* surface.
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
        + "lock-in frames and raw calibrated temperature grids. "
        + "POST /v2/thermography/analyze runs quality scoring on a TIFF image bundle; "
        + "GET /v2/thermography/otvis/{appId}/frames renders PNG frames for the viewer. "
        + "Extracted from backend core per V2CONV-A6 (MFFD-NDT-QUALITY-1).";
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
    Log.info("V2CONV-A6: plugin 'fileformat-thermography' registered — "
        + "POST /v2/thermography/analyze + GET /v2/thermography/otvis/* active");
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.info("plugin 'fileformat-thermography' unregistered");
  }
}
