package de.dlr.shepard.plugins.hdf5;

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
 * PL1c — HDF5/HSDS payload-kind plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>The plugin's CDI beans — {@code HdfContainerService},
 * {@code HdfContainerKindHandler}, {@code HdfAdminRest},
 * {@code HdfPermissionBridge}, {@code HsdsClient} — are discovered
 * by Quarkus's build-time CDI scanner via the backend's classpath.
 * This manifest exists so the {@code PluginRegistry} tracks PL1c in
 * {@code GET /v2/admin/plugins} and so the
 * {@code shepard.plugins.hdf5.enabled} runtime toggle is surfaced.
 *
 * <p>Neo4j-OGM entity-package registration ({@code HdfContainer}) is
 * handled separately by {@link HdfPayloadKind} via the
 * {@code PayloadKind} ServiceLoader SPI — that path fires inside
 * {@code NeoConnector.connect()}, before CDI is up.
 */
public final class HdfPluginManifest implements PluginManifest {

  private static final String ID = "hdf5";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "HDF5/HSDS Data";

  private static final String DESCRIPTION =
    "HDF5/HSDS-backed payload kind. Provides HdfContainer Neo4j nodes, " +
    "container CRUD + raw-file download on the unified /v2/containers surface " +
    "(kind=hdf), admin endpoints (/v2/admin/hdf/*), the HSDS HTTP client, " +
    "and the HdfPermissionBridge permission-sync hook. " +
    "Requires a running HSDS sidecar configured via shepard.hdf.hsds.* properties.";

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
   * PM1f — declares the HSDS (Highly Scalable Data Service) sidecar this
   * plugin needs to serve HDF5 payloads. HSDS provides an HDF-REST API
   * over either a POSIX local-storage backend (quick-start default) or an
   * S3-compatible backend (production; see {@code HSDS_AWS_S3_GATEWAY} env
   * var for pointing at Garage).
   *
   * <p>This is additive-only for now — the {@link de.dlr.shepard.plugin.SidecarsAssembler}
   * that reads this declaration is not yet deployed. The declaration exists
   * so the tooling has the information ready (PM1f partial; compose removal
   * deferred to the SidecarsAssembler milestone per
   * PLUGIN-HDF5-AUDIT-2026-05-24-001).
   *
   * <p>The {@code backendEnvBinding} keys map directly to the
   * {@code shepard.hdf.hsds.*} properties consumed by {@code HsdsClient}.
   * The {@code SHEPARD_HDF_ENABLED} binding activates the plugin toggle;
   * without it the HSDS sidecar starts but Shepard never calls it.
   *
   * <p>Default credentials ({@code admin}/{@code admin}) are intentional
   * quick-start placeholders — the operator MUST override
   * {@code HSDS_USERNAME} and {@code HSDS_PASSWORD} (and the matching
   * {@code SHEPARD_HDF_HSDS_USERNAME}/{@code SHEPARD_HDF_HSDS_PASSWORD}
   * backend bindings) before exposing to any network.
   */
  @Override
  public List<SidecarSpec> sidecars() {
    return List.of(
      new SidecarSpec(
        "shepard-hsds",
        "hdfgroup/hsds:v0.9.5",
        List.of(new PortSpec(5101, "hdf-rest-api")),
        List.of(new VolumeSpec("hsds_storage", "/data")),
        Map.of(
          "BUCKET_NAME",
          "shepard",
          "HSDS_USERNAME",
          "{{operator:HSDS_USERNAME:-admin}}",
          "HSDS_PASSWORD",
          "{{operator:HSDS_PASSWORD:-admin}}",
          "LOG_LEVEL",
          "INFO"
        ),
        new HealthcheckSpec(
          "curl --fail --silent http://localhost:5101/about || exit 1",
          Duration.ofSeconds(15),
          Duration.ofSeconds(5),
          6
        ),
        List.of(),
        Map.of(
          "SHEPARD_HDF_HSDS_ENDPOINT",
          "http://{{sidecar.host}}:5101",
          "SHEPARD_HDF_HSDS_USERNAME",
          "{{operator:HSDS_USERNAME:-admin}}",
          "SHEPARD_HDF_HSDS_PASSWORD",
          "{{operator:HSDS_PASSWORD:-admin}}",
          "SHEPARD_HDF_ENABLED",
          "true"
        ),
        "512m"
      )
    );
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof(
      "PL1c: hdf5 plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("PL1c: hdf5 plugin onUnregister invoked");
  }
}
