package de.dlr.shepard.plugins.hdf5;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * PL1c — HDF5/HSDS payload-kind plugin manifest, discovered by
 * {@code de.dlr.shepard.plugin.PluginRegistry} at startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>The plugin's CDI beans — {@code HdfContainerService},
 * {@code HdfContainerRest}, {@code HdfAdminRest},
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
    "REST endpoints (/v2/hdf-containers/*, /v2/admin/hdf/*), the HSDS HTTP client, " +
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
