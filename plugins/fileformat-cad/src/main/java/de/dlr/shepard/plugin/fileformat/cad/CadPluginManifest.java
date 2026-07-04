package de.dlr.shepard.plugin.fileformat.cad;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * PluginManifest SPI entry for {@code shepard-plugin-fileformat-cad}.
 *
 * <p>Surfaces {@code fileformat-cad} in {@code GET /v2/admin/plugins}.
 * Supported formats: STEP ISO 10303-21, Dassault 3DXML, JT (ISO 14306), OBJ.
 */
public class CadPluginManifest implements PluginManifest {

  @Override
  public String id() {
    return "fileformat-cad";
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
    return "CAD file format metadata parser (STEP / 3DXML / JT / OBJ)";
  }

  @Override
  public String description() {
    return "Phase-1 metadata parser for CAD exchange formats common in aerospace "
        + "composite manufacturing. Extracts STEP ISO 10303-21 HEADER + partial "
        + "AP242 DATA section (PRODUCT, MATERIAL, ply/fibre-angle hints), "
        + "Dassault 3DXML product metadata, JT magic detection, and OBJ mesh "
        + "statistics. Emits urn:shepard:cad:* and urn:shepard:mffd:cad:* "
        + "SemanticAnnotations on file upload. Rendering (glTF preview) deferred "
        + "to CAD-RENDER-1.";
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
    Log.info("fileformat-cad plugin registered — STEP/3DXML/JT/OBJ metadata extraction active");
  }
}
