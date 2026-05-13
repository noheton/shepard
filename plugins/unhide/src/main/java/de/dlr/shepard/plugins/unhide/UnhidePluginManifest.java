package de.dlr.shepard.plugins.unhide;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * UH1a — Helmholtz Unhide publish plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * shipped alongside this class.
 *
 * <p>Phase 1 shape (ADR-0023 + ADR-0024): the plugin's CDI beans —
 * {@code UnhideConfigService}, {@code UnhideFeedService},
 * {@code UnhideAdminRest}, {@code UnhideFeedRest} and the rest — are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * own classpath. This manifest exists so the {@code PluginRegistry}
 * also tracks UH1a in {@code GET /v2/admin/plugins}: operators see
 * "unhide v1.0.0-SNAPSHOT enabled" alongside any later plugin, and
 * the per-plugin {@code shepard.plugins.unhide.enabled} toggle is
 * surfaced.
 *
 * <p>The {@code onRegister} hook is intentionally tiny: a single
 * INFO log line ("UH1a active via PluginManifest SPI") so a grep
 * through startup output confirms the discovery path fired. The
 * actual wiring — {@code :UnhideConfig} seed, REST resources, feed
 * service — happens through CDI lifecycle hooks the existing UH1a
 * code already declares ({@code @Observes StartupEvent},
 * {@code @ApplicationScoped}). Nothing new to wire here.
 *
 * <p>When PM1b lands proper child-classloader CDI integration, this
 * manifest's {@code onRegister} grows to bind the UH1a beans through
 * {@code ctx.beanManager()} explicitly — but for Phase 1, the
 * build-classpath CDI scan is the active path.
 */
public final class UnhidePluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.unhide.enabled}. */
  private static final String ID = "unhide";

  /**
   * Plugin version. Hand-pinned to {@code ${revision}} from
   * {@code plugins/unhide/pom.xml}; updated on each shepard-plugin-unhide
   * release. PM1b will read this from a build-generated resource so
   * the version doesn't drift from the pom.
   */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. UH1a depends on core types shipped from 5.2.0+ (the V12
   * appId migration, the {@code HasAppId} marker, {@code GenericDAO}).
   */
  private static final String SHEPARD_COMPATIBILITY = ">=5.2.0,<6";

  /** PM1c — display name surfaced in admin REST + CLI. */
  private static final String TITLE = "Helmholtz Unhide Publish";

  /** PM1c — operator-facing summary. */
  private static final String DESCRIPTION =
    "Exposes the install's catalogue as a schema.org + metadata4ing JSON-LD feed for the " +
    "Helmholtz Knowledge Graph (HKG / Unhide) harvester.";

  /** PM1c — homepage of the upstream consumer (the Unhide service). */
  private static final URI HOMEPAGE = URI.create("https://unhide.helmholtz-metadaten.de/");

  /** PM1c — fork source-code repository. */
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");

  /**
   * PM1c — SPDX licence id. The plugin currently lives in-tree under
   * the fork's Apache-2.0 licence (see {@code LICENSE}); when UH1
   * graduates to a standalone {@code shepard-plugin-unhide}
   * repository it inherits this licence unless explicitly relicensed.
   */
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
  public Optional<URI> homepageUrl() {
    return Optional.of(HOMEPAGE);
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(REPOSITORY);
  }

  @Override
  public String licence() {
    return LICENCE;
  }

  // dependencies() — left as the default (empty list). UH1a depends
  // on the in-tree backend SPIs (PROV1, KIP1, GenericDAO) — not on
  // any other plugin.

  @Override
  public void onRegister(PluginContext ctx) {
    // The UH1a beans already self-register through their own
    // @Observes StartupEvent hooks (UnhideConfigService) and through
    // Quarkus's build-time CDI scan (REST resources). Nothing to
    // wire imperatively here in Phase 1.
    Log.infof(
      "UH1a: unhide plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    // No-op — Quarkus's shutdown sequence drives the CDI beans'
    // own @Observes ShutdownEvent hooks.
    Log.debugf("UH1a: unhide plugin onUnregister invoked");
  }
}
