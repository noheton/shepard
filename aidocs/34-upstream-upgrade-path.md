# Upstream upgrade path — `dlr-shepard/shepard 5.2.0` → `noheton/shepard main`

**Snapshot date.** 2026-05-08.
**Upstream baseline.** `gitlab.com/dlr-shepard/shepard` v5.2.0 (the
container tags pinned in `infrastructure/docker-compose.yml`:
`registry.gitlab.com/dlr-shepard/shepard/backend:5.2.0`,
`.../frontend:5.2.0`).

This is the **live ledger** of every merged change on `main` that an
admin upgrading from upstream 5.2.0 needs to know about. New rows
land in the same PR as the change they describe — see `CLAUDE.md` at
the repo root for the standing rule.

The intended audience is an **operator** who ran `docker compose up`
on upstream 5.2.0, has data in their Neo4j / Mongo / Timescale, and
wants to upgrade to a build of this repo's `main` without losing it.

## TL;DR

As of the snapshot date, **upgrading is essentially `pull-and-restart`**.
No data-mutating migration has shipped yet; every change is either
additive (new endpoints, new optional config) or migration-safe
(idempotent Neo4j constraint adds with null-tolerance). The biggest
operator-visible change is a single config-key namespace deprecation
(A3c) which is alias-compatible until v6.0.

## API-version policy (the big rule)

The standing rule in `CLAUDE.md` (repo root) is reproduced here for
admin convenience:

- **`/shepard/api/...` is frozen.** Byte-compatible with upstream
  `dlr-shepard/shepard 5.2.0`. Existing clients (Python / TS / Java
  generated against upstream OpenAPI) keep working unchanged.
- **`/v2/...` is this fork's development surface.** Every new
  endpoint we add lands here. Opt-in for clients that want our
  additions; ignored by clients that don't.
- L2d formalises the split (`aidocs/25 §4 Phase 4`); L2e eventually
  drops the upstream long-id paths after a deprecation window.

Each row below indicates whether the endpoint touches `/shepard/api/`
(compat surface, additive only) or `/v2/` (this fork). Rows that
touch neither (config keys, internal behaviour) leave the column
blank.

The L2 chain (`L2b` … `L2e`) will introduce data-mutating migrations
and an API deprecation window — see "What's coming" at the bottom.

## Operator quick-start

```bash
# Stop upstream stack.
cd /opt/shepard && docker compose down

# Update images. Build locally, or pull this repo's CI-built tags
# once they're published.
git pull origin main
docker compose build      # if building locally
# or: docker compose pull  # if using published tags

# Optional one-time prep: rotate any default credentials per
# aidocs/07 H8 (this repo's main flags these as already-public).
$EDITOR .env

# Bring up. V11 (Neo4j unique constraints) applies on first start.
docker compose up -d
docker compose logs -f backend  # confirm 'neo4j migrations' lines log V11 applied
```

The frontend container does not need a separate migration step;
the backend is the only service with schema state.

## Change ledger

Status legend:

- **ZERO** — additive only; nothing for an admin to do.
- **CONFIG** — admin should change config (with backward-compatible
  alias / fallback in place).
- **AWARE** — admin should know about a new behaviour even though
  it's not breaking.
- **BREAKING** — a real breaking change with required admin action;
  spelled out in the row.

Migration column: filename of the Neo4j / SQL / app-side migration,
or `—` if no migration ships.

| ID | Change | Status | Config / endpoint impact | Migration | Operator action |
|---|---|---|---|---|---|
| **A1**  | DB connection wait: bounded timeout + exponential backoff | ZERO | New optional config `shepard.migrations.connection-wait-timeout` (default `PT60S`) | — | None. Default is safe. Consider raising the timeout if your Neo4j boots slowly. |
| **A1b** | Per-DB health check separation (startup vs runtime) | ZERO | `/healthz` body now reports per-DB `state` and `kind` | — | None. Useful for monitoring — point your liveness probe at `/healthz` (was already there) and your readiness probe at the same endpoint. |
| **A1c** | Graceful degradation when optional DBs (PostGIS) are down | ZERO | Spatial endpoints return 503 with RFC 7807 body + `Retry-After` when PostGIS is DOWN, 404 when feature toggle is OFF | — | None. Replaces a previously-broken behaviour where the whole API hung. |
| **A1e** | `MigrationsRunner` fails fast on migration errors | **AWARE** | A failed Neo4j-Migrations run now **aborts startup** (was: log + continue) | — | If you were dodging a known-broken migration by relying on the old swallow, that no longer works. Fix the migration or pin to upstream 5.2.0 until you can. |
| **A1f** | Automated DB recovery scheduler | ZERO | New `quarkus-scheduler` dependency. Config `shepard.health.recovery.interval` default `PT15S` | — | None. |
| **A3**  | Runtime feature toggles via `@ConditionalOnFeature` | ZERO | Mechanism only; `versioning` toggle migrated to it | — | None. |
| **A3c** | Config namespace `shepard.spatial-data.*` → `shepard.infrastructure.spatial.*` | **CONFIG** | Both keys resolve. Old keys log a one-shot deprecation warning. Removal in **v6.0**. | — | Rename `shepard.spatial-data.*` keys in your `.env` / `application.properties` at your leisure. See `aidocs/A3c-namespace-migration.md` for the full mapping. |
| **A4**  | Permission cache TTL / max-size made tunable | ZERO | New optional `shepard.permissions.cache.ttl` (default `PT5M`), `.max-size` (default `10000`) | — | None. Defaults match upstream's effective behaviour. |
| **L5**  | Semi-permanent API keys with expiry | **AWARE** | `ApiKeyIO` now carries `validUntil`. JWTs minted on this branch carry an `exp` claim. | — | **Existing API keys keep working** — they were minted without `exp`, so the validator treats them as non-expiring. Newly-minted keys honour the new field. Distinguishable 401 on expiry. |
| **P3**  | Migration progress monitoring endpoint | ZERO | New `GET /migrations/progress` (read-only) | — | None. Wire up to your monitoring stack if useful. |
| **P14** | NDJSON streaming ingest for timeseries | ZERO | `POST /timeseriesContainers/{id}/payload` accepts `application/x-ndjson` in addition to existing JSON | — | None. Optional. New caps `shepard.timeseries.ingest.ndjson.batch-size` (default 5000), `…max-duration` (default `PT5M`). |
| **R2**  | Body-form selective RO-Crate export (Phase 1) | ZERO | New sibling `POST /collections/{id}/export` with `ExportSelection` body. Existing GET unchanged. | — | None. Existing exports byte-identical. |
| **R2b/c/d/d2** | Selective-export refinements (per-payload picks, redaction, metadata bundling, subscription emission) | ZERO | Additive fields on the body-form endpoint. Defaults preserve current behaviour. | — | None. |
| **L2a** | Additive `appId` (UUID v7) on every Neo4j entity write | **AWARE** | New nullable `appId: STRING` property on 28 node labels. New per-label unique constraint (Neo4j ignores nulls until backfill). | `V11__Add_appId_unique_constraints.cypher` (idempotent, additive) | None at upgrade time. **Be aware**: subsequent writes mint `appId`; existing rows keep `appId = null` until L2b backfills them. The L2 chain is documented in `aidocs/25`. |
| **L2b** | Backfill `appId` on pre-L2a rows | ZERO | No endpoint or config impact | `V12__Backfill_appId.cypher` (chunked 10k rows per batch, idempotent via `WHERE n.appId IS NULL`) + operator-run rollback at `V12_R__Rollback_Backfill_appId.cypher` | None — runs automatically on next startup. After completion, every node in the 28 labels has a non-null `appId`. The V11 unique constraint (which previously tolerated nulls) becomes meaningfully unique. Backfill is bounded by graph size; chunked at 10k rows so it doesn't lock the entire graph. On a `> 1M` node graph, expect minutes-to-low-tens-of-minutes startup delay the first time. Subsequent starts are no-ops (`WHERE n.appId IS NULL` returns zero rows). |
| **C5** | Cypher injection fix — parameter-binding + property-name allowlist in `Neo4jQueryBuilder`, `PermissionsDAO`, `DataObjectDAO` | ZERO | No endpoint or config impact (internal Cypher construction only) | — | None. Search and DAO query shapes change internally — the wire contract (request body, response shape) is unchanged. Closes the injection vector against user-controlled property names + IRI values; subsumes M9. |
| **C5b** | Second-wave parameter-binding fix — `GenericDAO.getSearchForReachableReferences*`, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, the `*ReferenceDAO` family, `SemanticAnnotationDAO` | ZERO | No endpoint or config impact | — | None. Same shape as C5; closes the remaining `id()=` concat sites. After this lands, the L2c read-path swap from `long` to `String appId` can ship without re-opening injection sites. |
| **CI quality gates** | JaCoCo coverage gate (60% line / 60% branch); SpotBugs + findsecbugs on `verify`; OWASP Dependency-Check (weekly, fail at CVSS≥7); gitleaks secret scan | ZERO | New `backend/pom.xml` properties `jacoco.haltOnFailure` + `spotbugs.failOnError` (default `false` locally; CI flips to `true`) | — | None for an admin running shepard. Affects developers contributing to this fork: `mvn verify` now invokes JaCoCo `check` and SpotBugs `check` (informational locally; gating in CI). New `dependency-check-suppressions.xml` (empty stub, template-documented) lives at `backend/`. |
| **M4 / M5 / M2** | JWT-filter Bearer-prefix mangling fix; redaction of token values in warn-level logs; `0600` perms on the generated PKI private key | ZERO | No config / endpoint impact | — | None. Behaviour change observable only in (a) tokens that contain the literal substring `"Bearer "` mid-payload now parse correctly, (b) `Log.warn` of `"Invalid/missing authorization header"` now reports `present`/`absent` instead of full header values, (c) `~/.shepard/keys/private.key` is created with `rw-------` rather than the umask default. Pre-existing key files keep their old perms — re-run `chmod 600 ~/.shepard/keys/private.key` once on upgrade if your audit story requires it. |

## Out-of-band changes (not yet visible to admins)

Several entries are **design done** in `aidocs/` but not yet on `main`.
They appear here only to flag the upgrade-path implications for when
they land:

| ID | What it'll do | Operator impact when it lands |
|---|---|---|
| **L2b** | Backfill `appId` on all pre-L2a rows via `V12__Backfill_appId.cypher` (chunked, idempotent) | Auto-migrates on next startup. Logs progress. Aborts startup on collision (post-A1e fail-fast). Rollback: `V12_R`. |
| **C5** | Cypher injection fix (parameter binding + property-name whitelist) | Internal only. No admin action. |
| **L2c** | Read path switches to `WHERE e.appId = $appId` | **AWARE** — gated on C5. Existing API surface unchanged for `/v1/`; sets up `/v2/` (next row). |
| **L2d** | `/v2/` API exposes `appId` natively | **AWARE** — additive new versioned API surface. `/v1/` paths keep `long` for ≥ 2 minor releases. |
| **L2e** | Drops `/v1/` long-id paths; flips permission-cache key shape; drops legacy TimescaleDB `containerId: long` column | **BREAKING** — clients on `/v1/` numeric IDs must migrate to `/v2/` `appId` strings. Generated clients (Python / TS / Java) will be regenerated as part of the deprecation window; pin to the previous client version if you need time. The TimescaleDB column drop will ship with a one-shot cross-DB backfill job; the operator runbook will land in this doc when the change does. |

## Image-build notes

The published `registry.gitlab.com/dlr-shepard/shepard/backend:5.2.0`
and `.../frontend:5.2.0` images on GitLab are the **upstream** tags;
this repo does not currently publish images. Two paths to deploy
from this repo:

- **Build locally.** `docker compose build` from `infrastructure/`
  builds both backend and frontend against your local maven cache.
  Reproducible per `Dockerfile`.
- **CI-built tags.** Not yet published. When they are, this row will
  cite the registry path. Until then, building locally is the
  supported path.

For the **ARM64** caveat (Oracle Cloud Free Tier, Apple Silicon),
see `docs/deploy-oracle-free.md` §5a — the upstream images are
amd64-only; either rebuild via `docker buildx --platform linux/arm64`
or run with QEMU emulation.

## Migration tests — deferred

Per the standing rule in `CLAUDE.md`, migration tests are tracked
but not blocking. The matrix below records what regression tests
should land alongside each migration:

| Migration | Required test | Status |
|---|---|---|
| `V11__Add_appId_unique_constraints.cypher` | Apply against a pre-L2a dump; assert all 28 constraints created; assert pre-existing nulls don't conflict | **deferred** — covered indirectly by `AppIdGeneratorTest` + DAO seam tests. Add explicit constraint-application IT after L2b ships. |
| `V12__Backfill_appId.cypher` (forthcoming, L2b) | Apply against a pre-L2a dump with rows of every label; assert post-backfill `MATCH (n) WHERE n.appId IS NULL RETURN count(n) = 0` | will ship with L2b |

## Reporting an upgrade-path gap

If you find a change on `main` that breaks an upstream upgrade and
**isn't documented here**, that's the bug. File an issue on
`noheton/shepard` with the breaking commit hash and the upstream
behaviour you expected; the fix is either to add a row above or to
revert the change. The standing rule in `CLAUDE.md` makes the
tracker update part of the change — a missing row is a process gap,
not a documentation backlog item.
