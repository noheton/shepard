package de.dlr.shepard.plugins.analyticsts;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * AT1 — shepard-plugin-analytics-ts manifest.
 *
 * <p>Discovered by {@code de.dlr.shepard.plugin.PluginRegistry} at
 * startup via the {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class. Surfaced in
 * {@code GET /v2/admin/plugins} so operators see "analytics-ts vX
 * enabled" and can flip the per-plugin
 * {@code shepard.plugins.analytics-ts.enabled} toggle.
 *
 * <p>Phase-1 shape (matching UH1a / SPI1a / PL1c): the plugin's
 * {@code MADDetector} {@code @ApplicationScoped} bean is discovered
 * by Quarkus's build-time CDI scanner via the
 * {@code quarkus.index-dependency.shepard-plugin-analytics-ts.*}
 * declaration in {@code backend/src/main/resources/application.properties}.
 * Nothing imperative to wire in {@code onRegister} — the
 * {@code AnalyticsRegistry} {@code @Observes StartupEvent} hook picks
 * up the detector when CDI fires.
 */
public final class AnalyticsTsPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.analytics-ts.enabled}. */
  private static final String ID = "analytics-ts";

  /** Plugin version. */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range. AT1 depends on the
   * {@code de.dlr.shepard.spi.analytics} package shipped in the
   * backend from this fork's 6.0.0-SNAPSHOT line.
   */
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "Timeseries Analytics (anomaly + quality)";

  private static final String DESCRIPTION =
    "Rolling-median MAD anomaly detector for timeseries (mad-v1). " +
    "Extracted from the in-tree AI1b implementation; behavioural-equivalence " +
    "with the original math is enforced by recorded-fixture tests. " +
    "Future home of additional detectors (STL residual, isolation forest) " +
    "and the VIA_ORCHESTRATOR-tier seam for shepard-plugin-mlops.";

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
    // The MADDetector self-registers via Quarkus CDI; the
    // AnalyticsRegistry picks it up on @Observes StartupEvent.
    Log.infof(
      "AT1: analytics-ts plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("AT1: analytics-ts plugin onUnregister invoked");
  }
}
