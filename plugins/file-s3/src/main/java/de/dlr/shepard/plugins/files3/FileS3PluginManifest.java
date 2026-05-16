package de.dlr.shepard.plugins.files3;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * FS1b — S3 file storage plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>The plugin's CDI bean — {@code S3FileStorage} — is discovered
 * by Quarkus's build-time CDI scanner via the backend's classpath.
 * This manifest exists so the {@code PluginRegistry} tracks FS1b
 * in {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.file-s3.enabled} runtime toggle is
 * surfaced.
 *
 * <p>Note: the {@code FileStorageRegistry} uses
 * {@code shepard.storage.provider=s3} (not the plugin toggle) as
 * the active-adapter selection knob. Both must be set appropriately
 * — the plugin toggle gates the lifecycle hook; the storage key
 * gates the actual upload/download path.
 */
public final class FileS3PluginManifest implements PluginManifest {

  private static final String ID = "file-s3";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "S3 File Storage";

  private static final String DESCRIPTION =
    "S3-compatible object storage adapter for shepard's file payload kind (FS1b). " +
    "Uses AWS SDK v2; any S3-compatible endpoint works — Garage (quick-start default), " +
    "AWS S3, Cloudflare R2, Backblaze B2, Wasabi, MinIO, SeaweedFS, Ceph RGW. " +
    "Set shepard.storage.provider=s3 + configure shepard.files.s3.* to activate.";

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
      "FS1b: file-s3 plugin v%s active via PluginManifest SPI (id=%s, compat=%s). " +
      "Set shepard.storage.provider=s3 to activate the S3 storage path.",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("FS1b: file-s3 plugin onUnregister invoked");
  }
}
