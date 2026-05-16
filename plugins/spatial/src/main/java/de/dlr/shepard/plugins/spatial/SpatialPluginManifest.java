package de.dlr.shepard.plugins.spatial;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * SPI1a — PostGIS spatial-data payload-kind plugin manifest,
 * discovered by {@code de.dlr.shepard.plugin.PluginRegistry} at
 * startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>Phase 1 shape (ADR-0023 + ADR-0024): the plugin's CDI beans —
 * {@code SpatialDataContainerService}, {@code SpatialDataPointService},
 * {@code SpatialDataReferenceService}, {@code SpatialDataPointRest},
 * {@code SpatialDataReferenceRest}, {@code SpatialDataPointRepository}
 * and the rest — are discovered by Quarkus's build-time CDI scanner
 * via the backend's own classpath. This manifest exists so the
 * {@code PluginRegistry} tracks SPI1a in {@code GET /v2/admin/plugins}
 * and so the {@code shepard.plugins.spatial.enabled} runtime toggle is
 * surfaced.
 *
 * <p>Neo4j-OGM entity-package registration ({@code SpatialDataContainer},
 * {@code SpatialDataReference}) is handled separately by
 * {@link SpatialPayloadKind} via the {@code PayloadKind} ServiceLoader
 * SPI — that path fires inside {@code NeoConnector.connect()}, before
 * CDI is up.
 */
public final class SpatialPluginManifest implements PluginManifest {

  private static final String ID = "spatial";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "Spatial Data (PostGIS)";

  private static final String DESCRIPTION =
    "PostGIS-backed spatial-data payload kind. Provides SpatialDataContainer + " +
    "SpatialDataPoint JPA persistence and SpatialDataReference Neo4j context nodes. " +
    "Requires a PostGIS-enabled PostgreSQL datasource configured via " +
    "quarkus.datasource.\"spatial\".* in application.properties.";

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
      "SPI1a: spatial plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("SPI1a: spatial plugin onUnregister invoked");
  }
}
