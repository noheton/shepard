package de.dlr.shepard.plugins.minter.local;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * KIP1h — Local PID Minter plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * shipped alongside this class.
 *
 * <p>Phase 1 shape (ADR-0023 + ADR-0024, same as UH1a + KIP1g): the
 * plugin's CDI bean — {@link LocalMinter} — is discovered by
 * Quarkus's build-time CDI scanner via the backend's own classpath
 * (the backend's `with-plugins` profile declares this JAR as a
 * Maven `<dependency>`). This manifest exists so the
 * {@code PluginRegistry} tracks KIP1h in {@code GET /v2/admin/plugins}:
 * operators see "minter-local v1.0.0-SNAPSHOT enabled" alongside
 * UH1a + KIP1g + any later plugin, and the per-plugin
 * {@code shepard.plugins.minter-local.enabled} toggle is surfaced.
 *
 * <p>Plugin-first rationale: pre-KIP1h the in-core {@code MockMinter}
 * lived under {@code de.dlr.shepard.publish.minter} — a violation of
 * the user's "MockMinter is a plugin" architectural call and of
 * CLAUDE.md plugin-first heuristic #3 ("SPIs in core, adapters in
 * plugins"). KIP1h renames it to {@code LocalMinter} (the legitimate
 * default for local PIDs — "mock" misled operators) and ships it as
 * this drop-in module. The in-core {@code Minter} SPI seam (every
 * minter compiles against) stays in {@code backend/} —
 * adapters live in plugins.
 */
public final class LocalMinterPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.minter-local.enabled}. */
  private static final String ID = "minter-local";

  /**
   * Plugin version. Hand-pinned to {@code ${revision}} from
   * {@code plugins/minter-local/pom.xml}; updated on each release.
   * PM1b/PM1c will read this from a build-generated resource so the
   * version doesn't drift from the pom.
   */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. KIP1h depends on the KIP1a in-core surfaces shipped from
   * 5.2.0+ (the {@code Minter} SPI, {@code MintRequest} record
   * carrying {@code versionNumber}).
   */
  private static final String SHEPARD_COMPATIBILITY = ">=5.2.0,<6";

  /** PM1c — display name surfaced in admin REST + CLI. */
  private static final String TITLE = "Local PID Minter";

  /** PM1c — operator-facing summary. */
  private static final String DESCRIPTION =
    "Mints local-instance PIDs of the form shepard:<instance.id>:<kind>:<appId>:v<n>. " +
    "Default minter for fresh shepard installs — no external service needed.";

  /** PM1c — homepage. */
  private static final URI HOMEPAGE = URI.create("https://github.com/noheton/shepard");

  /** PM1c — fork source-code repository. */
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");

  /**
   * PM1c — SPDX licence id. The plugin currently lives in-tree under
   * the fork's Apache-2.0 licence (see {@code LICENSE}).
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

  // dependencies() — left as the default (empty list). The local
  // minter depends only on the in-core Minter SPI shipped by KIP1a;
  // no sibling plugin is required.

  @Override
  public void onRegister(PluginContext ctx) {
    // The LocalMinter bean self-registers through Quarkus's
    // build-time CDI scan (@ApplicationScoped on the class).
    // MinterRegistry resolves it by id at startup when
    // shepard.publish.minter=local is configured. Nothing
    // imperative to wire here in Phase 1.
    Log.infof(
      "KIP1h: minter-local plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    // No-op — Quarkus's shutdown sequence drives the CDI bean's
    // own lifecycle. LocalMinter is @ApplicationScoped + stateless
    // beyond its injected config.
    Log.debugf("KIP1h: minter-local plugin onUnregister invoked");
  }
}
