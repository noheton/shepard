---
stage: fragment
last-stage-change: 2026-05-23
---

# A3c: infrastructure-vs-feature toggle namespace split

## What changed

Spatial-data infrastructure toggle moved namespace:

| Before                          | After                                        |
| ------------------------------- | -------------------------------------------- |
| `shepard.spatial-data.enabled`  | `shepard.infrastructure.spatial.enabled`     |

Feature toggles (A3) live under `shepard.features.*`; infrastructure toggles
now live under `shepard.infrastructure.*`. Other infrastructure keys owned by
upstream drivers (`neo4j.host`, `quarkus.mongodb.connection-string`, etc.) are
left untouched.

## Backward compatibility

Both names resolve. The new name is canonical; the old name is wired in
`application.properties` as `${shepard.infrastructure.spatial.enabled}` so
that `quarkus.flyway.spatial.active=${shepard.spatial-data.enabled}` and
`quarkus.hibernate-orm.spatial.active=${shepard.spatial-data.enabled}`
interpolations keep resolving exactly as before.

A startup CDI bean (`SpatialDataConfig`) reads both keys directly via
`Config#getConfigValue(...)` and:

- prefers the new key when both are externally overridden;
- emits a one-shot deprecation warning when only the legacy key is overridden;
- emits a warning (new wins) when both are overridden with different values.

## Deprecation deadline

The legacy key `shepard.spatial-data.enabled` (env: `SHEPARD_SPATIAL_DATA_ENABLED`)
is scheduled for removal in **v6.0**. Migrate at your convenience before then.

## Migration steps for deployments

1. Replace `shepard.spatial-data.enabled = ...` with
   `shepard.infrastructure.spatial.enabled = ...` in your override
   `application.properties`.
2. Replace `SHEPARD_SPATIAL_DATA_ENABLED=...` with
   `SHEPARD_INFRASTRUCTURE_SPATIAL_ENABLED=...` in your environment.
3. Restart the backend; the deprecation warning should disappear from the
   startup log.

If you run with both keys set during the transition, ensure they match — the
runtime will pick the new value and warn about the divergence.
