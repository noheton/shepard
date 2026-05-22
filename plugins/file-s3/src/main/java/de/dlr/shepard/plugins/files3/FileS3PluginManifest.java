package de.dlr.shepard.plugins.files3;

import de.dlr.shepard.plugin.HealthcheckSpec;
import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import de.dlr.shepard.plugin.PortSpec;
import de.dlr.shepard.plugin.SidecarSpec;
import de.dlr.shepard.plugin.VolumeSpec;
import io.quarkus.logging.Log;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

  /**
   * PM1f — declares Garage as the default S3 backend sidecar this
   * plugin needs to function. The operator-side bootstrap reads this
   * declaration (via {@link de.dlr.shepard.plugin.SidecarsAssembler})
   * and renders a compose snippet the operator pastes into their
   * {@code docker-compose.override.yml}.
   *
   * <p>Garage is the canonical pick per ADR-0024 (MinIO archived
   * their community edition); the operator is free to point
   * {@code shepard.files.s3.endpoint} at any other S3-compatible
   * backend (AWS S3, Cloudflare R2, etc.) and skip the Garage
   * sidecar entirely — in that case they don't activate the
   * snippet at all.
   *
   * <p>The shape mirrors {@code aidocs/integrations/93 §9}.
   */
  @Override
  public List<SidecarSpec> sidecars() {
    return List.of(
      new SidecarSpec(
        "garage",
        "dxflrs/garage:v1.0.1",
        List.of(
          new PortSpec(3900, "s3-api"),
          new PortSpec(3902, "web-admin")
        ),
        List.of(new VolumeSpec("garage_data", "/var/lib/garage")),
        Map.of(
          "GARAGE_RPC_SECRET",
          "{{generate:hex:64}}",
          "GARAGE_S3_API_BIND_ADDR",
          "0.0.0.0:3900",
          "GARAGE_WEB_BIND_ADDR",
          "0.0.0.0:3902"
        ),
        new HealthcheckSpec(
          "curl -fsS http://localhost:3900/health",
          Duration.ofSeconds(30),
          Duration.ofSeconds(10),
          3
        ),
        List.of(
          "/garage layout assign ${NODE_ID} -z dc1 -c 1G",
          "/garage layout apply --version 1",
          "/garage bucket create shepard-files",
          "/garage key new --name shepard-backend",
          "/garage bucket allow --read --write shepard-files --key shepard-backend"
        ),
        Map.of(
          "SHEPARD_FILES_S3_ENDPOINT",
          "http://{{sidecar.host}}:3900",
          "SHEPARD_FILES_S3_REGION",
          "garage-region",
          "SHEPARD_FILES_S3_PATH_STYLE",
          "true",
          "SHEPARD_FILES_S3_BUCKET",
          "shepard-files",
          "SHEPARD_FILES_S3_ACCESS_KEY_ID",
          "{{from:postInit.3.access_key_id}}",
          "SHEPARD_FILES_S3_SECRET_ACCESS_KEY",
          "{{from:postInit.3.secret_access_key}}"
        )
      )
    );
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
