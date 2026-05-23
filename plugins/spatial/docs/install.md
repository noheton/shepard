---
title: spatial — Install
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# spatial — install

`shepard-plugin-spatial` adds a PostGIS-backed spatial-data payload
kind: `:SpatialDataContainer` for point clouds + observations,
`:SpatialDataReference` for per-DataObject anchors. The plugin
requires a dedicated PostGIS-enabled PostgreSQL sidecar — installs
are non-trivial because the database has to come up before the
backend boots.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-spatial-${revision}.jar`.
- A reachable PostgreSQL instance with the `postgis` extension
  installed. The `spatial` compose profile in `infrastructure/`
  provisions one for development.
- Network reachability between the backend container and the
  PostGIS host.

---

## PostGIS sidecar

The `infrastructure/` directory ships a `spatial` compose profile
that pins `postgis/postgis:16-3.4`:

```bash
cd infrastructure
docker compose --profile spatial up -d postgis
```

To verify:

```bash
docker compose exec postgis psql -U shepard -d spatial \
  -c "SELECT PostGIS_Full_Version();"
```

You should see `POSTGIS=...` with the postgis version line.

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.spatial.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |
| `quarkus.datasource."spatial".db-kind` | — | Must be `postgresql`. |
| `quarkus.datasource."spatial".jdbc.url` | — | JDBC URL to the PostGIS instance, e.g. `jdbc:postgresql://postgis:5432/spatial`. |
| `quarkus.datasource."spatial".username` | — | Database user with read+write on the `spatial` database. |
| `quarkus.datasource."spatial".password` | — | Password (or leave empty and use trust auth on the network). |
| `quarkus.hibernate-orm."spatial".database.generation` | `none` | Schema management strategy. Leave as `none` after first start. Set to `drop-and-create` on a clean install to bootstrap the tables. |

The **named** datasource is critical — `"spatial"` is the
Quarkus datasource qualifier, distinct from shepard's main
`quarkus.datasource.*` keys. Without the named datasource the
plugin fails at startup with a `DataSource not found` error.

Minimal `application.properties`:

```properties
shepard.plugins.spatial.enabled=true

quarkus.datasource."spatial".db-kind=postgresql
quarkus.datasource."spatial".jdbc.url=jdbc:postgresql://postgis:5432/spatial
quarkus.datasource."spatial".username=shepard
quarkus.datasource."spatial".password=changeme
quarkus.hibernate-orm."spatial".database.generation=none
```

---

## First-start schema bootstrap

On a fresh PostGIS database (no tables yet), flip the generation
key to `drop-and-create` for the **first** start only:

```properties
quarkus.hibernate-orm."spatial".database.generation=drop-and-create
```

Start the backend. Hibernate creates `spatial_data_container` and
`spatial_data_point` tables. After the first successful startup,
flip the key back to `none` and restart — leaving `drop-and-create`
on at runtime will wipe data on every backend boot.

Alternative: pre-create the schema manually via `psql`:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
-- Hibernate's expected schema is generated from the entity classes;
-- run with drop-and-create the first time and then capture the DDL.
```

---

## Healthcheck

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

# 1. Plugin registry.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "spatial")'

# 2. List endpoint (200 with empty array if the datasource is alive).
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/shepard/api/spatialDataContainers"
```

A 200 with `[]` (or actual containers) on the second call
confirms the named datasource is connected. A `503` indicates the
PostGIS host is unreachable; a `500` typically means the schema
wasn't bootstrapped.

---

## Disabling the plugin

```properties
shepard.plugins.spatial.enabled=false
```

When disabled, all `/shepard/api/spatialDataContainers/*` and
`/shepard/api/collections/{id}/dataObjects/{id}/spatialDataReferences/*`
endpoints return 404. The PostGIS host stays alive (other plugins
or external services may still need it); the backend simply
stops connecting to it.

---

## Known pitfalls

- **PostGIS missing the extension**. A vanilla `postgres` image
  doesn't ship PostGIS. Use `postgis/postgis:*` or run `CREATE
  EXTENSION postgis;` on first use.
- **Wrong datasource qualifier**. The plugin requires the
  named `"spatial"` datasource. Quarkus's main `quarkus.datasource.*`
  keys (without the `"spatial"` qualifier) don't reach this
  plugin and the startup log shows a `DataSource not found`
  error.
- **`drop-and-create` left on in production**. Wipes the
  `spatial_data_container` + `spatial_data_point` tables on
  every backend restart. After first install, flip back to
  `none`.
- **CRS mismatch**. The plugin assumes EPSG:4326 (WGS-84, lat/lon
  degrees). All ingested coordinates are interpreted in this
  CRS; mixing units (UTM, ECEF, local) silently produces wrong
  spatial queries.
- **Index sizing**. PostGIS's default GIST index is fine up to
  ~10⁶ points; beyond that consider partial indexes or the
  PostGIS `clusterindex` tool.

---

## See also

- [`reference.md`](reference.md) — payload kinds, endpoints, IO shapes.
- [`quickstart.md`](quickstart.md) — upload a point + query bounding box.
- [PostGIS docs](https://postgis.net/documentation/).
- [GeoJSON spec (RFC 7946)](https://datatracker.ietf.org/doc/html/rfc7946).
