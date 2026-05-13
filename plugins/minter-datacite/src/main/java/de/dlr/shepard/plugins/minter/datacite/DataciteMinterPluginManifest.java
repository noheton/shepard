package de.dlr.shepard.plugins.minter.datacite;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * KIP1d — DataCite Fabrica minter plugin manifest. Discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * companion
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file in this JAR.
 *
 * <p>Phase 1 shape (ADR-0023 + ADR-0024): the plugin's CDI beans —
 * {@code DataciteMinter}, {@code DataciteMinterConfigService},
 * {@code DataciteAdminRest}, {@code DataciteHttpClient} — are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * own classpath. This manifest exists so {@code PluginRegistry}
 * tracks KIP1d in {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.minter-datacite.enabled} runtime toggle is
 * surfaced.
 *
 * <p>The {@code onRegister} hook logs that the plugin is active.
 * Future plugin work may wire the {@code PreviousPublicationResolver}
 * to the in-core {@code PublicationDAO} via {@code ctx} when the
 * versioning chain lands — for KIP1d Phase 1 the resolver stays
 * unset (which is harmless when {@code versionNumber<=1}).
 */
public final class DataciteMinterPluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.minter-datacite.enabled}. */
  private static final String ID = "minter-datacite";

  /** Plugin version. Hand-pinned to ${revision} from the pom. */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. KIP1d depends on the KIP1a Minter SPI shipped from 5.2.0+.
   */
  private static final String SHEPARD_COMPATIBILITY = ">=5.2.0,<6";

  /** Display name surfaced in admin REST + CLI. */
  private static final String TITLE = "DataCite DOI Minter";

  /** Operator-facing summary. */
  private static final String DESCRIPTION =
    "Mints DataCite DOIs against DataCite Fabrica (test) or production. Supports " +
    "versioned DOIs via isNewVersionOf / hasVersion relations. Each shepard install " +
    "typically has one DataCite Member contract — set apiBaseUrl, handlePrefix, " +
    "repositoryId, password via shepard-admin minters datacite, then test-connection " +
    "before enabling.";

  /** Homepage of the upstream consumer (DataCite). */
  private static final URI HOMEPAGE = URI.create("https://datacite.org/");

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
      "KIP1d: minter-datacite plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("KIP1d: minter-datacite plugin onUnregister invoked");
  }
}
