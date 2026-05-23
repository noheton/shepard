---
layout: default
title: Configuration
description: Environment variables, application.properties, runtime :*Config singletons, CORS.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/config/
---

# Configuration

shepard has three layers of configuration, each with a different lifecycle:

1. **Compose-level environment** (`infrastructure/.env`) — read at container
   start; changing it requires `docker compose up -d` to recreate containers.
2. **`application.properties`** — Quarkus configuration baked into the
   backend image; changing it requires a rebuild. Most properties have
   environment-variable equivalents (Quarkus auto-mapping).
3. **Runtime `:*Config` Neo4j singletons** (A3b pattern) — flipped via
   `PATCH /v2/admin/<feature>/config` without a restart. Per
   `CLAUDE.md §"Always: surface operator knobs in the admin config"`, this
   is the **default posture** for new feature toggles.

## shepard-specific properties

Verified in `backend/src/main/resources/application.properties`:

| Property | Default | Effect |
|---|---|---|
| `shepard.version` | `${project.version}` | Version reported on `/versionz` |
| `shepard.versioning.enabled` | `false` | Enables the entity versioning code path |
| `shepard.spatial-data.enabled` | `false` | Enables PostGIS spatial features |
| `shepard.autoconvert-int` | `false` | Numeric autoconversion in structured data |

## Quarkus paths

From `application.properties`:

- `quarkus.http.root-path=/shepard/api`
- `quarkus.http.non-application-root-path=/shepard/doc`
- `quarkus.smallrye-health.root-path=/shepard/api/healthz`
- `quarkus.smallrye-openapi.path=openapi.json`
- `quarkus.swagger-ui.always-include=true`

The combined OpenAPI document is served at `/shepard/doc/openapi.json`.
This fork additionally exposes two filtered views, useful when an
operator wants to generate a client pinned to a single API shelf:

- `/shepard/doc/openapi/v1.json` — only the upstream-compatible
  `/shepard/api/...` paths.
- `/shepard/doc/openapi/v2.json` — only the fork's `/v2/...`
  development surface.

Both honour `?format=yaml`; both are unauthenticated, matching the
posture of the combined document. The combined `/shepard/doc/openapi.json`
keeps working unchanged. (P4c.)

## CORS

CORS is permissive (`quarkus.http.cors.origins=*`) and accepts headers
`Origin, Accept, X-Requested-With, Content-Type, Authorization, X-API-KEY`
plus the standard preflight set. **Tighten this in front of internet-exposed
deployments** by overriding `QUARKUS_HTTP_CORS_ORIGINS` to a comma-separated
list of allowed origins.

## Database connection environment

Consumed by the backend container (see `infrastructure/docker-compose.yml`):

- `OIDC_PUBLIC`, `OIDC_AUTHORITY`, `OIDC_ROLE`
- `NEO4J_HOST`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`
- `QUARKUS_MONGODB_CONNECTION_STRING`
- `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`,
  `QUARKUS_DATASOURCE_PASSWORD`
- `QUARKUS_DATASOURCE_SPATIAL_*` (only used when spatial is enabled)
- `SHEPARD_MIGRATION_MODE_ENABLED`
- `SHEPARD_SPATIAL_DATA_ENABLED`
- `SHEPARD_AUTOCONVERT_INT`

The compose file still wires legacy `INFLUX_*` variables — these only matter
for the `timescale-migration-preparation` profile.

## Runtime `:*Config` admin singletons

Per the A3b pattern (extended by N1c2, UH1a, and future plugins), each
runtime-tunable feature ships a `:*Config` Neo4j singleton plus a
`GET/PATCH /v2/admin/<feature>/config` REST surface plus
`shepard-admin <feature>` CLI parity.

Currently shipped:

| Feature | Singleton | REST root | CLI |
|---|---|---|---|
| Feature toggles (A3b) | `:FeatureToggleRegistry` | `/v2/admin/features` | `shepard-admin features list` |
| Semantic ontologies (N1c2) | `:SemanticConfig` | `/v2/admin/semantic/ontologies` | `shepard-admin semantic` |
| Helmholtz Unhide publish (UH1a) | `:UnhideConfig` | `/v2/admin/unhide/config` | `shepard-admin unhide` |
| v1 deprecation (V1COMPAT) | `:LegacyV1Config` | `/v2/admin/v1-compat/config` | n/a (frontend banner) |

**Precedence:** the runtime `:*Config` value wins. The deploy-time
`application.properties` key is the install default that seeds the
singleton on first start. The deploy-time key stays valid so an operator
can ship a baked-in default in their IaC, but it does not override a
runtime flip.

**Audit:** mutations on these admin endpoints land in `:Activity` via
`ProvenanceCaptureFilter` (PROV1a, automatic — admin endpoints capture
by default), so the audit trail can be filtered for "who changed
`<feature>` settings when".

## Deploy-time-only knobs

A small set of properties stay deploy-time-only by necessity — flipping
them at runtime would require a re-bootstrap:

- Cluster identity / topology (`shepard.instance.id`, DB URLs,
  OIDC issuer URL).
- Pre-startup ordering invariants (`shepard.migrations.*`,
  `shepard.health.recovery.interval`).
- Buffer sizes / page sizes where there is no operator need to
  tune at runtime (e.g. `shepard.unhide.feed.page-size`).

Everything else should be admin-configurable at runtime per the
`CLAUDE.md` rule.
