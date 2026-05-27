package de.dlr.shepard.plugins.spatiotemporal;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * SPATIAL-V6-001 — Spatiotemporal payload-kind plugin manifest,
 * discovered by {@code de.dlr.shepard.plugin.PluginRegistry} at
 * startup via the
 * {@code META-INF/services/de.dlr.shepard.plugin.PluginManifest}
 * file shipped alongside this class.
 *
 * <p>Consolidates the PostGIS spatial-data payload kind with the v6
 * green-field {@code shepard_spatial} schema (profile hypertable,
 * PostGIS co-located on TimescaleDB). The Quarkus datasource name
 * stays {@code "spatial"} for backward compatibility with existing
 * {@code application.properties} deployments.
 *
 * <p>Neo4j-OGM entity-package registration ({@code SpatialDataContainer},
 * {@code SpatialDataReference}) is handled separately by
 * {@link SpatiotemporalPayloadKind} via the {@code PayloadKind}
 * ServiceLoader SPI — that path fires inside
 * {@code NeoConnector.connect()}, before CDI is up.
 */
public final class SpatiotemporalPluginManifest implements PluginManifest {

  private static final String ID = "spatiotemporal";

  private static final String VERSION = "2.0.0-SNAPSHOT";

  private static final String SHEPARD_COMPATIBILITY = ">=6.0.0-SNAPSHOT,<7";

  private static final String TITLE = "Spatiotemporal Data (PostGIS + TimescaleDB)";

  private static final String DESCRIPTION =
    "PostGIS + TimescaleDB-backed spatiotemporal payload kind. Provides SpatialDataContainer + " +
    "SpatialDataPoint JPA persistence and SpatialDataReference Neo4j context nodes. " +
    "Includes the v6 green-field shepard_spatial schema (profile hypertable). " +
    "PostGIS is co-located on the TimescaleDB instance. " +
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
      "SPATIAL-V6-001: spatiotemporal plugin v%s active via PluginManifest SPI (id=%s, compat=%s)",
      VERSION,
      ID,
      SHEPARD_COMPATIBILITY
    );
  }

  @Override
  public void onUnregister(PluginContext ctx) {
    Log.debugf("SPATIAL-V6-001: spatiotemporal plugin onUnregister invoked");
  }
}
