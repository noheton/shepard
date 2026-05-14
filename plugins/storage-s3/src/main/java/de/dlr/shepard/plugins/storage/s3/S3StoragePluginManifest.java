package de.dlr.shepard.plugins.storage.s3;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * FS1b — S3-compatible storage plugin manifest. Discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * companion
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file in this JAR.
 *
 * <p>Phase 1 shape (ADR-0023 + ADR-0024): the plugin's CDI beans —
 * {@code S3FileStorage}, {@code S3StorageConfigService},
 * {@code S3StorageAdminRest}, {@code S3StorageConfigDAO} — are
 * discovered by Quarkus's build-time CDI scanner via the backend's
 * own classpath. This manifest exists so {@code PluginRegistry}
 * tracks FS1b in {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.storage-s3.enabled} runtime toggle is
 * surfaced.
 */
public final class S3StoragePluginManifest implements PluginManifest {

  /** Plugin id — matches {@code shepard.plugins.storage-s3.enabled}. */
  private static final String ID = "storage-s3";

  /** Plugin version. Hand-pinned to ${revision} from the pom. */
  private static final String VERSION = "1.0.0-SNAPSHOT";

  /**
   * Semver range of the shepard core this plugin is known compatible
   * with. FS1b depends on the FS1a FileStorage SPI shipped from 5.2.0+.
   */
  private static final String SHEPARD_COMPATIBILITY = ">=5.2.0,<6";

  /** Display name surfaced in admin REST + CLI. */
  private static final String TITLE = "S3-compatible File Storage";

  /** Operator-facing summary. */
  private static final String DESCRIPTION =
    "Stores file payloads in any S3-compatible object store (AWS S3, Garage, MinIO). " +
    "Activated by `shepard.storage.provider=s3`. Configure endpoint, bucket, and " +
    "credentials via `shepard-admin storage s3` or `POST /v2/admin/storage/s3/credential`, " +
    "then `PATCH /v2/admin/storage/s3/config` to enable.";

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
    return Optional.empty();
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
    return List.of();
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "FS1b: storage-s3 plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("FS1b: storage-s3 plugin onUnregister invoked");
  }
}
