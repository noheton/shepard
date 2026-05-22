package de.dlr.shepard.plugins.v1compat;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * V1COMPAT.0 Phase 1 — plugin manifest for the v1 compat marker
 * plugin. Discovered by {@code de.dlr.shepard.plugin.PluginRegistry}
 * at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>The plugin's CDI beans — {@code LegacyV1ConfigService},
 * {@code LegacyV1StatsService}, {@code LegacyV1ConfigAdminRest},
 * {@code LegacyV1StatsAdminRest}, {@code LegacyV1GateFilter},
 * {@code LegacyV1DeprecationFilter} — are discovered by Quarkus's
 * build-time CDI scanner via the backend's classpath (an
 * {@code application.properties} {@code quarkus.index-dependency}
 * entry indexes this JAR at build time, mirroring the UH1a / FS1b
 * shape).
 *
 * <p>Default state: enabled. Operators flip the runtime knob via
 * {@code PATCH /v2/admin/legacy/v1/config} per CLAUDE.md
 * "Always: surface operator knobs in the admin config".
 *
 * @see de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config
 * @see aidocs/platform/103a-v1-compat-marker-plugin.md
 */
public final class V1CompatPluginManifest implements PluginManifest {

  private static final String ID = "v1-compat";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "v1 Compat Surface";

  private static final String DESCRIPTION =
    "Phase 1 marker plugin for shepard's upstream-frozen v1 surface (/shepard/api/...). " +
    "Ships the :LegacyV1Config singleton + /v2/admin/legacy/v1/{config,stats} REST + " +
    "request filters that emit RFC 8594 deprecation headers and gate /shepard/api/* with " +
    "410 Gone (RFC 7807 problem-detail) when the runtime enabled flag is flipped off. " +
    "Default state: enabled — every byte of every v1 response stays identical to upstream " +
    "shepard 5.2.0. See aidocs/platform/103a-v1-compat-marker-plugin.md.";

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
      "V1COMPAT.0: v1-compat plugin v%s active via PluginManifest SPI (id=%s, compat=%s). " +
      "Phase 1 marker — :LegacyV1Config singleton seeded by V63 Cypher migration; " +
      "runtime knob at /v2/admin/legacy/v1/config; stats at /v2/admin/legacy/v1/stats.",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("V1COMPAT.0: v1-compat plugin onUnregister invoked");
  }
}
