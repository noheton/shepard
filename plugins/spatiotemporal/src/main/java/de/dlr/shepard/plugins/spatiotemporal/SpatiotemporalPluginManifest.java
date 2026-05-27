package de.dlr.shepard.plugins.spatiotemporal;

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

  /**
   * PM1f — declares the PostGIS-enabled TimescaleDB sidecar this plugin
   * needs. Since SPATIAL-V6-001 (Decision D2) PostGIS is co-located on
   * the TimescaleDB instance via a custom image
   * ({@code infrastructure/timescaledb-postgis/Dockerfile}); there is no
   * separate {@code postgis} container. The sidecar id is {@code "postgis"}
   * to match the legacy service name operators recognise.
   *
   * <p>This is additive-only for now — the {@link de.dlr.shepard.plugin.SidecarsAssembler}
   * that reads this declaration to render compose snippets is not yet
   * deployed. The declaration exists so the tooling has the information
   * ready (PM1f partial; compose removal deferred to the SidecarsAssembler
   * milestone per PLUGIN-SPATIAL-AUDIT-2026-05-24-001).
   *
   * <p>The image tag {@code timescale/timescaledb-postgis:2.24.0-pg16}
   * is a logical identifier — the actual deployment uses a locally-built
   * image from {@code infrastructure/timescaledb-postgis/Dockerfile}
   * (extends {@code timescale/timescaledb:2.24.0-pg16} + {@code apk add postgis}).
   * The {@code backendEnvBinding} keys match what the backend's
   * {@code application.properties} reads via Quarkus config:
   * {@code quarkus.datasource."spatial".*}.
   */
  @Override
  public List<SidecarSpec> sidecars() {
    return List.of(
      new SidecarSpec(
        "postgis",
        "timescale/timescaledb-postgis:2.24.0-pg16",
        List.of(new PortSpec(5432, "postgresql")),
        List.of(new VolumeSpec("timescaledb_data", "/var/lib/postgres/data")),
        Map.of(
          "POSTGRES_DB",
          "{{operator:POSTGRES_DB}}",
          "POSTGRES_USER",
          "{{operator:POSTGRES_USER}}",
          "POSTGRES_PASSWORD",
          "{{generate:hex:32}}",
          "POSTGRES_SHEPARD_USER",
          "{{operator:POSTGRES_SHEPARD_USER}}",
          "POSTGRES_SHEPARD_USER_PW",
          "{{generate:hex:32}}",
          "PGDATA",
          "/var/lib/postgres/data"
        ),
        new HealthcheckSpec(
          "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}",
          Duration.ofSeconds(15),
          Duration.ofSeconds(5),
          5
        ),
        List.of(
          "psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c 'CREATE EXTENSION IF NOT EXISTS postgis;'",
          "psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c 'CREATE EXTENSION IF NOT EXISTS timescaledb;'"
        ),
        Map.of(
          "QUARKUS_DATASOURCE_SPATIAL_JDBC_URL",
          "jdbc:postgresql://{{sidecar.host}}:5432/{{operator:POSTGRES_DB}}",
          "QUARKUS_DATASOURCE_SPATIAL_USERNAME",
          "{{operator:POSTGRES_SHEPARD_USER}}",
          "QUARKUS_DATASOURCE_SPATIAL_PASSWORD",
          "{{from:env.POSTGRES_SHEPARD_USER_PW}}",
          "SHEPARD_SPATIAL_DATA_ENABLED",
          "true"
        ),
        "512m"
      )
    );
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
