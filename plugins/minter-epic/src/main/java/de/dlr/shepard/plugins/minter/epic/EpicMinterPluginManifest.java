package de.dlr.shepard.plugins.minter.epic;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * KIP1c — ePIC handle service minter plugin manifest. Discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * companion
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file in this JAR.
 *
 * <p>Phase 1 shape: the plugin's CDI beans —
 * {@code EpicMinter}, {@code EpicMinterConfigService},
 * {@code EpicAdminRest}, {@code EpicHttpClient} — are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * own classpath. This manifest exists so {@code PluginRegistry}
 * tracks KIP1c in {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.minter-epic.enabled} runtime toggle is
 * surfaced.
 */
public final class EpicMinterPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.minter-epic.enabled}. */
  private static final String ID = "minter-epic";

  /** Plugin version. Hand-pinned to ${revision} from the pom. */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. KIP1c depends on the KIP1a Minter SPI shipped from 5.2.0+.
   */
  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  /** Display name surfaced in admin REST + CLI. */
  private static final String TITLE = "ePIC Handle Service Minter";

  /** Operator-facing summary. */
  private static final String DESCRIPTION =
    "Mints persistent handles via the ePIC Handle Service (B2HANDLE-compatible REST API). " +
    "Supports Helmholtz Data Federation deployments and any B2HANDLE-compatible endpoint. " +
    "Configure apiBaseUrl, handlePrefix, and credential via shepard-admin minters epic, " +
    "then test-connection before enabling.";

  /** Homepage of the ePIC consortium. */
  private static final URI HOMEPAGE = URI.create("https://www.pidconsortium.net/");

  /** Fork source-code repository. */
  private static final URI REPOSITORY = URI.create("https://github.com/noheton/shepard");

  /** SPDX licence id. */
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

  @Override
  public List<de.dlr.shepard.plugin.PluginDependency> dependencies() {
    // No plugin-to-plugin deps; the in-core Minter SPI is not a
    // PluginDependency target — those name sibling plugins by id.
    return List.of();
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "KIP1c: minter-epic plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("KIP1c: minter-epic plugin onUnregister invoked");
  }
}
