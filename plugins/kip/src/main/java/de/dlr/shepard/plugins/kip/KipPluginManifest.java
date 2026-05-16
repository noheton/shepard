package de.dlr.shepard.plugins.kip;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;

/**
 * KIP1g — Helmholtz Kernel Information Profile (KIP) plugin
 * manifest, discovered by {@code de.dlr.shepard.plugin.PluginRegistry}
 * at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * shipped alongside this class.
 *
 * <p>Shape (same as UH1a + PM1a phase 3): the plugin's REST
 * resource — {@code KipResolverRest} — is discovered by Quarkus's
 * build-time CDI scanner via the backend's own classpath. This
 * manifest exists so the {@code PluginRegistry} tracks KIP1g in
 * {@code GET /v2/admin/plugins}: operators see "kip
 * v1.0.0-SNAPSHOT enabled" alongside UH1a + any later plugin, and
 * the per-plugin {@code shepard.plugins.kip.enabled} toggle is
 * surfaced.
 *
 * <p>The {@code onRegister} hook is intentionally tiny: a single
 * INFO log line so a grep through startup output confirms the
 * discovery path fired. The actual wiring — the resolver bean —
 * happens through CDI lifecycle hooks the existing KIP1a code
 * already declares ({@code @RequestScoped} on the resource).
 * Nothing new to wire here.
 *
 * <p>Per the plugin-first heuristic #2 in CLAUDE.md
 * ("New external integrations → plugin shape"), the
 * HMC-flavoured KIP record + resolver belongs in a plugin —
 * the Helmholtz HMC spec has its own release cadence and could
 * be replaced by an alternative findability protocol without
 * touching the in-core {@code Minter} SPI or {@code :Publication}
 * entity.
 *
 * <p>PM1c (parallel slice) enriches the {@code PluginManifest}
 * SPI with default methods for title / description / etc.;
 * KIP1g intentionally leaves those at their interface defaults
 * to avoid fighting PM1c's roll-out.
 */
public final class KipPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.kip.enabled}. */
  private static final String ID = "kip";

  /**
   * Plugin version. Hand-pinned to {@code ${revision}} from
   * {@code plugins/kip/pom.xml}; updated on each shepard-plugin-kip
   * release. PM1b/PM1c will read this from a build-generated
   * resource so the version doesn't drift from the pom.
   */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. KIP1g depends on the KIP1a in-core surfaces shipped from
   * 5.2.0+ (the {@code PublicationDAO}, the {@code :Publication}
   * entity, the {@code PublishableKindRegistry}).
   */
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

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
  public void onRegister(PluginContext ctx) {
    // The KIP1g resolver bean self-registers through Quarkus's
    // build-time CDI scan (@RequestScoped on KipResolverRest).
    // Nothing to wire imperatively here in Phase 1.
    Log.infof(
      "KIP1g: kip plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    // No-op — Quarkus's shutdown sequence drives the CDI beans'
    // own lifecycle. The resolver is @RequestScoped, so there is
    // no long-lived state to release.
    Log.debugf("KIP1g: kip plugin onUnregister invoked");
  }
}
