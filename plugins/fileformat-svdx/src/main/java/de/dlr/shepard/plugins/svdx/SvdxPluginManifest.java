package de.dlr.shepard.plugins.svdx;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * V2CONV-A6 — PluginManifest SPI entry for shepard-plugin-fileformat-svdx.
 *
 * Upgraded from Tier-0 (standalone pure-Java parser) to Tier-1 (backend dep)
 * by moving SvdxIngestRest + SvdxCsvIngestionService + IOs into this plugin,
 * dissolving the bespoke core /v2/svdx/ surface.
 */
public class SvdxPluginManifest implements PluginManifest {

  @Override
  public String id() {
    return "fileformat-svdx";
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
    return "SVDX / TwinCAT Scope (Beckhoff) ingest plugin";
  }

  @Override
  public String description() {
    return "Parses Beckhoff TwinCAT Scope project files (.svdx): decodes the binary envelope, "
        + "extracts the XML manifest, emits urn:shepard:svdx:* semantic annotations. "
        + "POST /v2/svdx/ingest ingests the paired TwinCAT Scope Export Tool .csv sibling "
        + "into TimescaleDB. Extracted from backend core per V2CONV-A6.";
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
    Log.info("V2CONV-A6: plugin 'fileformat-svdx' registered — "
        + "POST /v2/svdx/ingest active for TwinCAT Scope CSV ingest");
  }
}
