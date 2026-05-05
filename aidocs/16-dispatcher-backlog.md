# 16 — Dispatcher Backlog

Working backlog for items extracted from `input/input_raw.md`. Each row
points to the originating line range so context is recoverable.

**Convention:** items are dispatched in rounds; each round runs as one
or more parallel sub-agents on the
`claude/implement-input-raw-changes-2WiOF` branch (or worktree of it).
Status legend:

- `queued` — vetted, waiting for dispatch slot
- `in-progress` — agent is working (note round + agent ID/branch)
- `done` — landed in this branch (commit hash)
- `blocked` — needs user input (note reason)
- `parked` — out of scope for this branch (e.g. dataship work, infra)

## Scope filter

`input_raw.md` mixes work for at least three projects:

1. `dlr-shepard/shepard` (this repo, Java/Quarkus backend + Angular frontend +
   client libs) — **in scope**.
2. `dlr-shepard/shepard-dataship` (separate Python client; M1–M9 milestones
   on lines 975–1214 reference `src/ui/*.py`, `src/worker.py`, etc.) —
   **out of scope here**, parked.
3. Program-level work (HMC, federation, project website, AAS) — **parked**,
   not implementable from this repo.

## Backlog (this repo only)

### Architectural / performance (lines 1387–7000)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| A1 | Async DB init: bounded timeout + exponential backoff in `MigrationsRunner.waitForConnection` | 1395–1440, 1599–1644 | S | done | Round 1, commit `a74d278`. Also fixed second infinite-loop in `NeoConnector`. Config: `shepard.migrations.connection-wait-timeout` (default `PT60S`). |
| A1b | Health checks: distinguish startup readiness vs runtime; per-DB status | 1420–1428 | S–M | done | Round 2, commit `8f40156` |
| A1c | Async DB init: graceful degradation when optional DBs (PostGIS) unavailable | 1408–1414, 1625–1627 | M | queued | Annotation-driven endpoint gating |
| A1f | Automated DB recovery scheduler on top of `DbHealthState` | 1427 | M | queued | Follow-up from A1b — infra now in place; a `@Scheduled` recovery loop is straightforward |
| A1d | Audit MongoDB / Flyway / Quarkus JDBC startup wait/retry semantics | — | S–M | queued | Follow-up from A1: only Neo4j had explicit infinite waits; confirm the Quarkus extensions fail fast within the same timeout philosophy |
| A1e | `MigrationsRunner.apply()` swallows `ServiceUnavailableException` / `MigrationsException` — surface failed migrations as a startup error rather than continuing | — | S | done | Round 2, commit `0f2f512` |
| A2 | Decompose monolithic `TimeseriesRest` / `FileRest` / `CollectionRest` | 1443–1489 | L | queued | JAX-RS sub-resources, breaking only for code, not API |
| A3 | Runtime feature toggles via CDI `@Produces` + `@ConditionalOnFeature` | 1492–1543 | M | done | Round 2, commit `ddeeb31`. Mechanism in place; `versioning` migrated (the only `@IfBuildProperty` use found). Other toggles (`SpatialDataFeatureToggle`, `MigrationModeToggle`) were already runtime via `ConfigProvider`. |
| A3b | `/admin/features` endpoint to view/modify runtime toggles | 1519–1523 | S | queued | After A3 |
| A3c | Namespace split: catalog/migrate `shepard.spatial-data.*` etc. as `shepard.infrastructure.*`; document toggle naming convention | 1514–1518 | S–M | queued | Follow-up from A3 |
| A4 | Permission cache: TTL/LRU (Caffeine), user+entity keying | 1581–1592, 1679–1684 | S–M | done | Round 1, commit `53996a3`. **Correction:** the cache was already Caffeine-backed (via `quarkus-cache`) and keyed by user × entity × access type via `CompositeCacheKey`. The input_raw.md "basic Map" critique was stale. Landed change is per-cache TTL/max-size config (`shepard.permissions.cache.ttl` default `PT5M`, `.max-size` default `10000`) overriding the global 3h / 8192 defaults, plus a behaviour-pinning test (hit/miss/TTL/invalidation/LRU). |
| A4b | TimescaleDB + PostGIS instance consolidation | 1564–1580 | M | parked | Infra/ops decision; defer |
| A4c | Permission cache warming on `StartupEvent` for top-N entities | 1684 | S | queued | Follow-up from A4 |
| A4d | Enable Micrometer metrics on `permissions-service-cache` | — | S | queued | Follow-up from A4; needs `quarkus.cache.caffeine.*.metrics-enabled=true` once Micrometer registry route is wired |
| P1 | Parallelize DB connection checks (CompletableFuture / virtual threads) | 1599–1644 | M | queued | Bundles with A1 |
| P2 | Batch permission checks: `checkPermissionsBatch(List<Long>)` | 1672–1678 | S–M | queued | One Cypher query per request, not N |
| P2b | TimescaleDB continuous aggregates / materialized views | 1690–1698 | M | queued | Already partly tracked in `12-timescaledb-performance-analysis.md` |
| P3 | Migration progress monitoring endpoint | 1720–1737 | S | done | Round 2, commit `7cc74b8`. Side-finding: the existing legacy `migration_tasks` table (V1.1.0) is unreferenced from Java; left untouched as lower-risk than consolidating. |
| P3b | Wire the external `timescale-migration-preparation` image to write `migration_progress` rows (or call `MigrationRunner.migrateContainer` over JDBC) | — | M | queued | Follow-up from P3 — image source lives outside this repo |
| P3c | Tighten authorisation on `/temp/migrations/*` (currently in `PermissionsService.java:202-205` always-allowed carve-out) | — | S | queued | Follow-up from P3 |
| P4 | API versioning prefix (`/shepard/api/v1`) | 1760–1764 | S | queued | **Breaking** — needs strategy decision |
| P4b | OpenAPI client tree-shaking / code splitting | 1765–1774 | S | queued | Frontend / clients |

### Loose-ideas section (lines 1–672, 700–847)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| L1 | Admin CLI: cleanup of data marked for deletion, import/export of collections as RO-Crate | 705–708 | M | queued | New scripts/ tool |
| L2 | Neo4J: stop using deprecated `id()` function, migration to custom ID scheme | 90, 715–717 | M | queued | Touches a lot of Cypher |
| L3 | Templates system: YAML-defined templates for collections / data objects / refs (admin role) | 98–137 | L | queued | Backend storage + frontend forms; SPW compat goal |
| L4 | Search-as-you-type with tree/graph view of ontology | 96, 869 | M | queued | Frontend; intersects with `13-search-improvements.md` and `14-semantic-improvements.md` |
| L5 | Semi-permanent API keys with expiry | 694 | S | done | Round 2, commit `30c687a`. API keys are hybrid Neo4j-row + JJWT-encoded; landed `validUntil` field, JWT `exp` claim, distinguishable 401 on expiry. |
| L6 | Output control: pagination on more endpoints | 689–691 | S | queued | Aligns with `13-search-improvements.md` cursor pagination |
| L7 | (Semantically) annotate everything: extend semantic annotations to file/structured/spatial payloads | 692, lines 0+ in `14-semantic-improvements.md` | L | queued | Already designed in §14 |
| L8 | Review permissions model | 693 | M | queued | Needs design first |

### Streaming / publication

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| S1 | Streaming OpenAPI compatible read path → client generation | 4 | M–L | queued | Already covered in `12-timescaledb-performance-analysis.md` §11 |
| S2 | Databus publication service (in dataship, but back-reference URI lands here) | 1199–1214, 721 | S | parked | Dataship-side; this repo only needs to accept a URI reference |

### Parked (out of repo or superseded)

| ID | Item | input_raw refs | Why parked |
|---|---|---|---|
| X1 | M1–M9 dataship milestones (IDP, async+S3, granular selection, approval, RO-Crate, joint pubs, back-ref) | 975–1214 | Different repo (`shepard-dataship`) |
| X2 | HDF5/HSDS support, AAS integration, table store | 697–712 | Multi-repo / external integration; needs scoping |
| X3 | Federation, HMC 2, project website | 8176–8400 | Program-level |
| X4 | Node-RED file collector flow (incl. JWT tokens) | 100–660 | Reference material; security follow-up below |

## Open user decisions (blocking some items)

1. **Leaked JWTs** at `aidocs/input/input_raw.md:222` and `:360` (issuers
   `bt-au-cube2.intra.dlr.de`, `bt-au-cube3.intra.dlr.de`, sub `kreb_fl`).
   Already on `origin`. Need: rotate tokens, decide whether to redact in
   the file, and whether to rewrite history.
2. **API versioning (P4)** is breaking — confirm strategy
   (`/shepard/api/v1` prefix, dual-serve, deprecation window) before
   dispatching.
3. **Templates system (L3)** is large and design-heavy — needs a written
   design before implementation.
4. **Neo4J ID migration (L2)** is large and touches a lot of code — needs
   a written design before implementation.

## Round log

### Round 1 — 2026-05-05

Dispatched two small, well-isolated agents in parallel worktrees.
Their output lands as separate commits on this branch.

| ID | Agent description | Status | Commit | Notes |
|---|---|---|---|---|
| A1 | Bounded-timeout DB connection wait with exponential backoff | done | `a74d278` | Replaces infinite loops in `MigrationsRunner.waitForConnection` and `NeoConnector`. Adds `ConnectionWaitTimeoutException`, 5-test `MigrationsRunnerTest` (deterministic via injected clock+sleeper). Spawned follow-ups A1d, A1e. |
| A4 | Caffeine-backed permission cache with TTL/LRU | done | `53996a3` | Cache was already Caffeine via `quarkus-cache`; landed per-cache TTL/max-size config + 5-test behavioural contract. Spawned follow-ups A4c (warming), A4d (metrics). |

**Round 1 outcome:** both items landed cleanly on the dispatcher branch.
A1 + A4 land 357 lines added across 7 files (5 prod, 2 test) with 9 new
unit tests, all passing. Two stale assumptions in `input_raw.md` were
corrected on the way (A1: the analysis flagged Mongo/Timescale/PostGIS
infinite waits, but only Neo4j had them in this codebase; A4: the
permission cache was already Caffeine-backed, not a basic Map).

### Round 2 — 2026-05-05

Dispatched 5 agents in parallel worktrees. P3, A1b, and A1e all sit in
the migrations / startup / health area, so cherry-pick conflicts are
possible — explicit non-overlap clauses included in each prompt:

- A1e owns `MigrationsRunner.java` (just the catch blocks).
- A1b owns health-check classes; explicitly told **not** to touch
  `MigrationsRunner` / `NeoConnector` / Neo4j common.
- P3 owns the InfluxDB→TimescaleDB migration tool (separate module);
  expected to be disjoint from A1b/A1e.

L5 (API keys) and A3 (feature toggles) are independent of all of the above.

**Round 2 outcome:** all 5 items landed on the dispatcher branch.
~3,200 lines added across ~60 files, **102 new unit tests** all passing
on the merged branch (8 `MigrationsRunner` + 21 health-check + 9 API-key
+ 2 versioning toggle + 17 migration progress + 5 from earlier round +
existing regressions). Two cherry-pick conflicts resolved manually:
- `MigrationsRunnerTest.java` (A1 vs A1e — combined test sets).
- `application.properties` (A1 vs A3 — kept both new keys).
Three stale spots in `input_raw.md` were corrected during dispatch:
- A1: only Neo4j had infinite waits, not Mongo/Timescale/PostGIS.
- A4: cache was already Caffeine, not a basic Map.
- P3: orchestrator image source lives outside this repo; only the
  COPY path was instrumentable here.

| ID | Agent description | Status | Commit | Notes |
|---|---|---|---|---|
| P3 | InfluxDB→TimescaleDB migration progress monitoring + persisted resume | done | `7cc74b8` | Found split: orchestrator is the external `timescale-migration-preparation` image (source not in this repo); the COPY path lives in `TimeseriesDataPointRepository`. Landed: Flyway `V1.9.0__add_migration_progress_table.sql`, `MigrationProgress` entity + repo + service, `MigrationRunner`, `MigrationProgressIO`, `GET /temp/migrations/state` and `GET /temp/migrations/{containerId}` endpoints. New COPY method `insertManyDataPointsWithCopyCommandBatched` with batch index + reporter callbacks (existing method untouched). 17 new tests passing. |
| A1b | Split health checks into Startup / Readiness / Liveness with per-DB detail | done | `8f40156` | New `common/healthz/` infra: `DbHealthState`, `DbPinger`, `ReadinessConfig`, `AbstractDb*Check`, `JvmLivenessCheck`. Per-DB pingers + startup checks for Neo4j/Mongo/Timescale/PostGIS. PostGIS short-circuits to UP when toggle off via existing `SpatialDataFeatureToggle.isActive()`. `shepard.health.readiness.max-staleness=PT30S` configurable. `HealthzIT` updated. 21 new unit tests passing. |
| A1e | `MigrationsRunner.apply()` fail-fast on swallowed exceptions | done | `0f2f512` | Worktree was forked from before A1; merge conflict in `MigrationsRunnerTest.java` resolved by combining both test sets. Adds 3 tests to A1's 5 → 8 tests total on `MigrationsRunner`, all passing. |
| L5 | Optional `validUntil` on API keys; auth rejects expired | done | `30c687a` | Hybrid system (Neo4j `ApiKey` row + JJWT-encoded). Filter rejects expired keys with 401 + `WWW-Authenticate: ApiKey error="expired"`. Existing keys without `validUntil` keep working. Schema impact additive nullable. 9 new tests across 3 test classes, all passing. |
| A3 | Runtime feature toggle mechanism + migrate the `versioning` toggle | done | `ddeeb31` | One `@IfBuildProperty` use found (`versioning`) and migrated. Adds `@ConditionalOnFeature` qualifier + `FeatureBeanProducer`; renames property to `shepard.features.versioning.enabled`. Conflict with A1's added property in `application.properties` resolved trivially. 2 new tests (enabled/disabled profiles), passing. |
