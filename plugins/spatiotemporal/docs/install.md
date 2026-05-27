---
title: spatiotemporal — Install
stage: deployed
last-stage-change: 2026-05-27
audience: plugin-author
---

# spatiotemporal — install

`shepard-plugin-spatiotemporal` (SPATIAL-V6-001) adds a PostGIS + TimescaleDB-backed
spatiotemporal payload kind: `:SpatialDataContainer` for point clouds + observations,
`:SpatialDataReference` for per-DataObject anchors, and the v6 green-field
`shepard_spatial` profile hypertable (aidocs/data/90 §3).

**Key change from the v5 `shepard-plugin-spatial`**: PostGIS is now co-located on
the existing TimescaleDB instance via a custom Docker image
(`infrastructure/timescaledb-postgis/Dockerfile`). The separate `postgis` container
and `spatial` compose profile have been retired.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven profile (the default).
  The plugin JAR is already in `/deployments/plugins/shepard-plugin-spatiotemporal-${revision}.jar`.
- The custom `timescaledb-postgis` Docker image (built automatically by `docker compose build`
  or manually via `docker build infrastructure/timescaledb-postgis/`).
- Network reachability between the backend container and `timescaledb`.

---

## Database setup

The `timescaledb` service in `infrastructure/docker-compose.yml` is now built from
`infrastructure/timescaledb-postgis/Dockerfile` which installs the `postgis` Alpine
package on top of `timescale/timescaledb:2.24.0-pg16`.

```bash
cd infrastructure
docker compose build timescaledb   # installs postgis on the timescaledb image
docker compose up -d timescaledb
```

The Flyway migration `V2.0.0__green_field_schema.sql` ships with the plugin and
runs automatically on backend startup. It:
1. Runs `CREATE EXTENSION IF NOT EXISTS postgis;` (idempotent).
2. Creates the `shepard_spatial` schema.
3. Creates the `profile_container` metadata table.
4. Creates the `profile` hypertable with TimescaleDB time-partitioning.
5. Creates BRIN + GIST + GIN indexes per aidocs/data/90 §3.3.
6. Applies the 7-day compression policy per §3.4.

No manual DDL is required.

---

## V2 Migration — upgrading from shepard-plugin-spatial v5

Operators running the old separate `postgis` container must:

1. Stop the backend: `docker compose stop backend`
2. Dump the old `spatial_data_points` table from the `postgis` container:
   ```bash
   docker compose exec postgis pg_dump -U shepard -t spatial_data_points spatial > spatial_v1.sql
   ```
3. Rebuild and start the new `timescaledb` image:
   ```bash
   docker compose build timescaledb && docker compose up -d timescaledb
   ```
4. Restore the V1 dump into TimescaleDB:
   ```bash
   docker compose exec -T timescaledb psql -U shepard -d ${POSTGRES_DB} < spatial_v1.sql
   ```
5. Start the backend — V2.0.0 migration runs automatically.
6. Remove the old `postgis` container and volume after verifying data integrity:
   ```bash
   docker compose rm -f postgis
   ```

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.spatiotemporal.enabled` | `false` | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`. |
| `quarkus.datasource."spatial".db-kind` | — | Must be `postgresql`. |
| `quarkus.datasource."spatial".jdbc.url` | — | JDBC URL to the TimescaleDB instance, e.g. `jdbc:postgresql://timescaledb:5432/postgres`. |
| `quarkus.datasource."spatial".username` | — | Database user with read+write. |
| `quarkus.datasource."spatial".password` | — | Password. |
| `quarkus.flyway."spatial".locations` | `db/spatial/migration` | Flyway migration location. |

Note: the Quarkus datasource qualifier stays `"spatial"` (not `"spatiotemporal"`) for backward
compatibility with any `application.properties` keys that already configure this datasource.

Minimal `application.properties` addition:

```properties
shepard.plugins.spatiotemporal.enabled=true

quarkus.datasource."spatial".db-kind=postgresql
quarkus.datasource."spatial".jdbc.url=jdbc:postgresql://timescaledb:5432/postgres
quarkus.datasource."spatial".username=shepard
quarkus.datasource."spatial".password=changeme
```

---

## Healthcheck

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

# 1. Plugin registry.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "spatiotemporal")'

# 2. List endpoint (200 with empty array if the datasource is alive).
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/shepard/api/spatialDataContainers"
```

A 200 on the second call confirms the named datasource is connected. A 503 indicates
TimescaleDB is unreachable; a 500 typically means the schema migration failed.

---

## Disabling the plugin

```properties
shepard.plugins.spatiotemporal.enabled=false
```

Or at runtime:
```bash
curl -X PATCH -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins/spatiotemporal/enabled" \
  -H "Content-Type: application/json" -d '"false"'
```

---

## Known pitfalls

- **PostGIS extension not installed**. If the database was started with the plain
  `timescale/timescaledb` image (not the custom `timescaledb-postgis` build),
  `CREATE EXTENSION postgis` will fail. Rebuild with `docker compose build timescaledb`.
- **Wrong datasource qualifier**. The plugin requires the named `"spatial"` datasource.
  The main `quarkus.datasource.*` keys don't reach this plugin.
- **CRS mismatch**. The plugin assumes coordinates in the frame declared on
  `profile_container.coord_frame_app_id`. The frame CRS is defined in the CST1 tree
  (aidocs/85); mixing frames without a transform produces wrong spatial queries.
- **Compression policy timing**. TimescaleDB compression policies are async.
  The `add_compression_policy(INTERVAL '7 days')` call in V2.0.0 means rows
  older than 7 days are compressed on the next maintenance job run, not immediately.

---

## See also

- [`reference.md`](reference.md) — payload kinds, endpoints, IO shapes, v6 schema.
- [`quickstart.md`](quickstart.md) — upload a profile sweep + query bounding box.
- [PostGIS docs](https://postgis.net/documentation/).
- [TimescaleDB compression docs](https://docs.timescale.com/use-timescale/latest/compression/).
- [aidocs/data/90](../../aidocs/data/90-spatial-as-temporal-sweep.md) — v6 SSOT design.
