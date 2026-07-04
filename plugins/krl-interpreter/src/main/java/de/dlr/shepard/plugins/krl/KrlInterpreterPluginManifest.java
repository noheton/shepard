package de.dlr.shepard.plugins.krl;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * V2CONV-A6 — PluginManifest for the KRL interpreter plugin.
 *
 * <p>Surfaces {@code "krl-interpreter"} in {@code GET /v2/admin/plugins}
 * with the {@code shepard.plugins.krl-interpreter.enabled} runtime toggle.
 *
 * <p>The plugin's {@link de.dlr.shepard.v2.transform.krl.KrlTrajectoryTransformExecutor}
 * is a ServiceLoader SPI entry — it is discovered automatically via
 * {@code META-INF/services/de.dlr.shepard.spi.transform.TransformExecutor}.
 * The CDI beans ({@link de.dlr.shepard.v2.transform.krl.config.KrlInterpreterConfig},
 * {@link de.dlr.shepard.v2.transform.krl.services.KrlSidecarClient},
 * {@link de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryService}) are indexed
 * via {@code quarkus.index-dependency.shepard-plugin-krl-interpreter.*} in
 * {@code application.properties}.
 */
public final class KrlInterpreterPluginManifest implements PluginManifest {

  private static final String ID = "krl-interpreter";
  private static final String VERSION = "1.0.0-SNAPSHOT";
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";
  private static final String TITLE = "KRL Interpreter (MAPPING_RECIPE transform executor)";
  private static final String DESCRIPTION =
    "Extracts the KRL trajectory interpret subsystem from backend core into a " +
    "drop-in plugin. A MAPPING_RECIPE template targeting the KrlTrajectoryShape IRI " +
    "resolves a KRL .src/.krl FileReference + a URDF FileReference, invokes the " +
    "KRL interpreter sidecar (FastAPI/Python, opt-in via krl-interpreter compose profile), " +
    "persists the derived joint-trajectory as a TimeseriesReference, and returns " +
    "a REFERENCE materialize result. See aidocs/platform/191 §3 (V2CONV-A6).";
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");
  private static final String LICENCE = "Apache-2.0";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public String shepardCompatibility() {
    return SHEPARD_COMPATIBILITY;
  }

  @Override
  public String title() {
    return TITLE;
  }

  @Override
  public String description() {
    return DESCRIPTION;
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "V2CONV-A6: krl-interpreter plugin v%s active via PluginManifest SPI " +
      "(id=%s, compat=%s); KrlTrajectoryTransformExecutor registered via ServiceLoader",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("V2CONV-A6: krl-interpreter plugin onUnregister invoked");
  }
}
