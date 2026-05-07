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

### Cross-cutting auth and security (added / promoted 2026-05-05)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| A0 | Admin role mechanism: configurable `shepard.admin.role`, populate `JWTPrincipal.roles` from realm-access claims (in `JWTFilter`), mirror for API-key path, so `@RolesAllowed("admin")` actually works | — | S–M | **needs decision** | Unblocker for A3b and P3c. Three options offered to user (full role mechanism / dev-profile-only / scope-shift to `/temp/admin/...`). |
| C3 | Remove the full-access fallback in `PermissionsService.getRoles` when the permissions node is missing — `PermissionsService.java:258-262` returns `new Roles(false, true, true, true)` (full-access backdoor) | `aidocs/07-security-issues.md` C3, `aidocs/19-architecture-feedback.md` §2, `aidocs/24-permission-system-review.md` §3 | S | **escalated** | Concrete fix: invert the default to deny; add explicit allow only for endpoints that legitimately operate without a permissions node. Per `aidocs/24`, depends on **A0** because the inverse default needs a way to authorize legitimate admin paths. |
| C5 | Replace string-concatenated Cypher query construction in `Neo4jQueryBuilder.java:198-244` and `PermissionsDAO.java:14` with parameterised queries | `aidocs/07-security-issues.md` C5, `aidocs/19-architecture-feedback.md` §2, `aidocs/25-neo4j-id-migration-design.md` §5 | M | **escalated — now also gates L2c** | Was tracked in `07`. Cross-cuts every search endpoint and gates `aidocs/13-search-improvements.md`'s unified search proposal. **Now also gates L2c (Phase 3 of Neo4j ID migration):** when entity ids become UUID strings, any string-concatenated `id()` Cypher becomes injectable. Fix C5 *before* `/search/v2` **and** before L2c. |
| H4 | Surface RFC 7807-shape error responses (existing high-finding from `07`) | `aidocs/07-security-issues.md` H4, `aidocs/19-architecture-feedback.md` §3 | M | **needs decision** | Bundles with API-versioning P4 — the response-shape change should land at the same time as the `/v1/` prefix to avoid two breaking changes in close succession. |

### Architectural / performance (lines 1387–7000)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| A1 | Async DB init: bounded timeout + exponential backoff in `MigrationsRunner.waitForConnection` | 1395–1440, 1599–1644 | S | done | Round 1, commit `a74d278`. Also fixed second infinite-loop in `NeoConnector`. Config: `shepard.migrations.connection-wait-timeout` (default `PT60S`). |
| A1b | Health checks: distinguish startup readiness vs runtime; per-DB status | 1420–1428 | S–M | done | Round 2, commit `8f40156` |
| A1c | Async DB init: graceful degradation when optional DBs (PostGIS) unavailable | 1408–1414, 1625–1627 | M | done | Round 5, commit `5805363` (salvaged from rate-limited worktree). New `@RequiresDatabase(DatabaseKind...)` annotation + `RequiresDatabaseFilter` JAX-RS provider that consults `DbHealthRegistry` (wraps post-A1b/A1f `DbPinger` ecosystem). Spatial endpoints return **503** with RFC-7807-shaped body + `Retry-After` header when PostGIS is DOWN; **404** when the spatial-data toggle is OFF (matches the readiness short-circuit pattern from A1b). Annotated `SpatialDataPointRest` only in this round; Neo4j/Mongo/Timescale stay implicitly required. 11/11 tests pass. |
| A1f | Automated DB recovery scheduler on top of `DbHealthState` | 1427 | M | done | Round 3, commit `2f80600`. `@Scheduled(every="{shepard.health.recovery.interval}")` default `PT15S`. Adds `quarkus-scheduler` dep. 4 tests passing. |
| A1d | Audit MongoDB / Flyway / Quarkus JDBC startup wait/retry semantics | — | S–M | queued | Follow-up from A1: only Neo4j had explicit infinite waits; confirm the Quarkus extensions fail fast within the same timeout philosophy |
| A1e | `MigrationsRunner.apply()` swallows `ServiceUnavailableException` / `MigrationsException` — surface failed migrations as a startup error rather than continuing | — | S | done | Round 2, commit `0f2f512` |
| A2 | Decompose monolithic `TimeseriesRest` / `FileRest` / `CollectionRest` | 1443–1489 | L | queued | JAX-RS sub-resources, breaking only for code, not API |
| A3 | Runtime feature toggles via CDI `@Produces` + `@ConditionalOnFeature` | 1492–1543 | M | done | Round 2, commit `ddeeb31`. Mechanism in place; `versioning` migrated (the only `@IfBuildProperty` use found). Other toggles (`SpatialDataFeatureToggle`, `MigrationModeToggle`) were already runtime via `ConfigProvider`. |
| A3b | `/admin/features` endpoint to view/modify runtime toggles | 1519–1523 | S | queued | After A3 |
| A3c | Namespace split: catalog/migrate `shepard.spatial-data.*` as `shepard.infrastructure.spatial.*`; document toggle naming convention | 1514–1518 | S–M | done | Round 4-extra, commit `156ad5a`. Aliasing via a `@Produces` `SpatialDataConfig` bean: both names resolve, new takes precedence, old logs a one-shot deprecation warning. Conflict-on-different-values: new wins, WARN logged (no exception — not breaking). Removal deadline `v6.0`. Migration note in `aidocs/A3c-namespace-migration.md`. 4 new tests passing. Quarkus interpolations `quarkus.flyway.spatial.active=${shepard.spatial-data.enabled}` and `quarkus.hibernate-orm.spatial.active=${shepard.spatial-data.enabled}` keep resolving through the alias chain. |
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
| L1 | Admin CLI: cleanup of data marked for deletion, import/export of collections as RO-Crate | 705–708 | M–L | queued | Now backed by `aidocs/22-admin-cli-draft.md`. Phase 1 (read-only — features list / health / migrations status) is unblocked once **A0** lands. Phase 2 (cleanup deleted-entities) needs explicit `--dry-run` semantics + TTL design. Recommended framework: Java + Picocli. |
| L2 | Neo4J: stop using deprecated `id()` function, **migrate to application-generated IDs** (NOT `elementId()`) | 90, 715–717 | L (~7–9 eng-weeks across ≥2 minor releases) | **design done** — see `aidocs/25` | Design landed `8a9c14a`. ID scheme **UUID v7** (time-ordered, Java native, composes with cursor pagination per `aidocs/12 §11.A.2` / `aidocs/13 §2.6`). Sub-IDs **L2a–L2e** below. |
| L2a | Phase 1: additive `appId` property + unique constraint, mixin on entity write side | — | S–M | queued | Read path still uses `id()` |
| L2b | Phase 2: Cypher backfill `appId = randomUUID()`, idempotent | — | S | queued | Use the existing `migration_progress` pattern (post-P3) for observability |
| L2c | Phase 3: switch every Cypher to `WHERE e.appId = $appId` | — | L | **gated on C5** | C5 must land first: `PermissionsDAO.java:14` (`"WHERE ID(e) = %d ".formatted(entityId)`) is forgeable once the parameter becomes a string. Also requires updating `PermissionsService.isAllowed` numeric-only segment dispatch at `PermissionsService.java:226` (today returns `false` on non-numeric → blanket-403 on UUID paths). |
| L2d | Phase 4: `/v2/` exposes `appId` natively | — | L | **gated on P4 + H4** | API versioning window (P4) + RFC 7807 error shape (H4) bundle here |
| L2e | Phase 5: drop legacy `/v1/` long-id paths, flip `permissions-service-cache` key from `(long, AccessType, String)` to `(String, AccessType, String)`, drop TimescaleDB legacy column | — | M | queued | Deprecation-window exit. Cache flush on cutover; intersects with A4c warming |
| L3 | Templates system: YAML-defined templates for collections / data objects / refs (admin role) | 98–137 | L | queued | Backend storage + frontend forms; SPW compat goal. **Depends on L2** — template instances need to reference their template by a stable application-generated ID, not by a Neo4j internal id that gets reused on delete-and-reimport. |
| L4 | Search-as-you-type with tree/graph view of ontology | 96, 869 | M | queued | Frontend; intersects with `13-search-improvements.md` and `14-semantic-improvements.md` |
| L5 | Semi-permanent API keys with expiry | 694 | S | done | Round 2, commit `30c687a`. API keys are hybrid Neo4j-row + JJWT-encoded; landed `validUntil` field, JWT `exp` claim, distinguishable 401 on expiry. |
| L6 | Output control: pagination on more endpoints | 689–691 | S | queued | Aligns with `13-search-improvements.md` cursor pagination |
| L7 | (Semantically) annotate everything: extend semantic annotations to file/structured/spatial payloads | 692, lines 0+ in `14-semantic-improvements.md` | L | queued | Already designed in §14 |
| L8 | Review permissions model — umbrella | 693 | M | **design done** | `aidocs/24-permission-system-review.md` is the deliverable. Concrete unpacking is **F1 + F2 + F3** plus the C3 / A0 / F4 / F5 fixes. F6 (OPA seam) and F7 (row-level security) deferred. |

### Streaming / publication

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| S1 | Streaming OpenAPI compatible read path → client generation | 4 | M–L | queued | Already covered in `12-timescaledb-performance-analysis.md` §11 |
| S2 | Databus publication service (in dataship, but back-reference URI lands here) | 1199–1214, 721 | S | parked | Dataship-side; this repo only needs to accept a URI reference |

### Newly surfaced (second-pass analysis of `input_raw.md` lines 1840–8409)

These come from a re-read of regions the first scan missed. `R7` deduplicates with existing `L1`. `R2` (RO-Crate) is **already implemented** in this repo (`ExportConstants.ROCRATE_METADATA`); only the per-payload selection enhancement remains. Most others are blocked on user/maintainer decisions.

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| R1 | Databus integration for referencing foreign systems | 8380 | M | **needs decision** | Wait for Databus API spec stabilisation; sister repo `shepard-dataship` already has a prototype |
| R2 | Per-payload selective RO-Crate export (refinement of existing export) | 8381 | S–M | queued | Base is implemented; this is the column/file selector layer (compare M4 for dataship) |
| R3 | Provenance capture in exports (OpenLineage / W3C PROV-O / both) | 8386 | M | **needs decision** | Ontology choice; aligns with `aidocs/14-semantic-improvements.md` |
| R4 | Evaluate NovaCrate library for RO-Crate metadata editing | 8389 | S | queued | Pre-decision assessment task |
| R5 | Frontend UI / component test suite | 8393–8395 | L | **needs decision** | Framework choice (Cypress / Playwright); no UI tests exist today |
| R6 | Comprehensive REST API examples in docs (Postman / OpenAPI examples / live) | 8396 | S–M | **needs decision** | Format choice |
| R8 | DLR Corporate-Design theming pass on frontend | 8400 | M | **needs decision** | Source `aidocs/input/*.htm` reference files **removed 2026-05-06** ("design agent now bootstrapped") — the design context lives in the produced aidocs/ docs (see `aidocs/20-epic-roadmap.md` E12 UX & ecosystem; `aidocs/19-architecture-feedback.md`). Implementation still needs design assets + brand guidelines from a fresh source if/when the team picks this up. |
| R9 | Git repo references as payload type (with pinned commits) | 8402 | L | **needs decision** | Versioning + payload-schema design |

### API critique series (from `aidocs/23`, 2026-05-05)

The MVP "minimum-viable clunkiness fix" is **P5 + P6 + P7 + P10 + P12 + P16 + P18** (≈ 2 sprints).

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| P5 | Replace path-segment switching in `PermissionsService.isAllowed` with annotation-based authz on each handler | M | queued | **Same proposal as F1** in `aidocs/24`. Use F1 as the canonical ID; P5 is its API-critique counterpart. |
| P6 | Fold `X-API-KEY` into `Authorization: Bearer`, deprecate the second header | S | queued | Breaking; bundles with P4 versioning. |
| P7 | Ship the unified `POST /search/v2` from `aidocs/13 §2`; deprecate the five legacy search routes | L | queued | Maps to epic E2 in `aidocs/20`. **Gated on C5** (Cypher injection). |
| P8 | Polymorphic `/annotations` endpoint replacing the four per-kind annotation rests | M | queued | Depends on `aidocs/14` model unification. |
| P9 | Single `/entities/{kind}/{id}/permissions` route replacing per-container permissions endpoints | M | queued | Overlaps **A2**. |
| P10 | `POST /sql/timeseries` curated SQL-over-HTTP for bulk reads — **answers the "timeseries to Excel" prompt** | M | **design done** | New paradigm slice; design landed in `aidocs/29-p10-implementation-design.md`. JSON DSL request body (vs raw SQL or templated lang). Cypher-first permission flow via `filterAllowedForUser` (post-P2). Three-format content negotiation (`text/csv` default, `application/json`, `application/x-ndjson`; Arrow deferred to P11). 1M-row + PT60S caps; mandatory time range. **Gated on C5.** Spawns **P10a** (S, JSON-only behind feature flag), **P10b** (M, CSV+NDJSON streaming, caps), **P10c** (S, default-on, wrapper retarget). |
| P10a | Phase 1: `SqlQuerySpec` + `SqlQueryCompiler` + JSON-only endpoint behind `shepard.features.sql-timeseries.enabled=false` | — | S | queued (gated on C5) | Includes injection regression suite |
| P10b | Phase 2: `text/csv` + `application/x-ndjson` content negotiation; streaming output with row + duration caps; `Trailer: x-shepard-truncated` | — | M | queued | After P10a |
| P10c | Phase 3: feature flag default-on; convenience-wrapper retarget (`aidocs/27` Phase 4); deprecation notice on legacy paginate-and-concat | — | S | queued | After P10b + P16 Phase 1 |
| P11 | Apache Arrow Flight / DuckDB read endpoint for analytical workloads | L (ARCH) | queued — deferred | Heavier analytics path; consider after P10 ships. |
| P12 | S3-presigned URLs for File and StructuredData payloads | M | queued | Dovetails with `input_raw.md:703`. |
| P13 | SSE change-feed (`GET /collections/{id}/events`) | M | queued | Replaces polling; bidirectional via WebSockets only if needed. |
| P14 | NDJSON streaming ingest for high-throughput timeseries imports | S | done | Round 4-extra, commit `24d4585`. `application/x-ndjson` `@Consumes` variant on `POST /timeseriesContainers/{id}/payload`. New `TimeseriesNdjsonService` reads request as `BufferedReader`, validates each `{timestamp, value}` line independently (timeseries identity supplied as query params), batches 5000 lines per `insertManyDataPointsWithCopyCommand` call. Streams response (per-line errors + `{"summary": {"accepted": A, "rejected": R, "durationMs": D}}` final line). Caps: `shepard.timeseries.ingest.ndjson.batch-size` (default 5000), `…max-duration` (default PT5M). Auth check happens **before** stream consumption. 5 `@QuarkusComponentTest` cases pass: mixed valid/invalid, multi-chunk streaming proof, exact-batch-count assertion (12000 lines → 3 batches), pre-stream auth failure. **Follow-up:** once P3's `insertManyDataPointsWithCopyCommandBatched` is preferred, swap the service-layer batching for the repository-layer one. |
| P15 | Migrate spec to OpenAPI 3.1 once `smallrye-open-api 4.x` is stable | M | **blocked on tooling** | Wait for upstream. |
| P16 | `shepard-py` and `shepard-ts` convenience layers — 30-LoC hand-written wrapper to fix the 14-line client boilerplate | S per language | **design done** | Top-of-list user-friendliness win. Design landed in `aidocs/27-convenience-clients-design.md` as commit `a2689f7`. Verdicts: "no new dependencies" is **true-with-extras** (`pandas` and `openpyxl` ship as PEP 508 extras `shepard[pandas]` / `shepard[excel]`); "~150 LoC" is **achievable** (core ~150, tests ~150, docs ~30). Phase 1 ships `Client` + domain proxies + pagination iterator + `ShepardError` hierarchy; phases 2–4 add helpers, TS counterpart, and P10 retarget. Single biggest risk: `to_pandas` OOM on multi-million-row timeseries — mitigated with `chunksize` now and a streaming `to_arrow()` companion once **P10** ships. |
| P17 | Pin `openapi-generator-cli` version across languages, add Microsoft Kiota PoC | S | queued | Dovetails with **P4b**. |
| P17b | CI lint: every IO class has `@Schema(name=…)` | XS | done | Round 4-extra, commit `963d0b0`. Bash script + GitLab CI job; baseline allowlist for incremental cleanup. Predicate: classes under any `*/io/` package (depth-1, excluding `*Mapper`) or files named `*IO.java`. **59 candidates · 32 already annotated · 27 in baseline.** Lint passes on merged branch. **Note:** baseline file currently lives at `backend/src/main/resources/schema-name-baseline.txt` so it ships in the production JAR — `BASELINE_FILE` at the top of the script can be retargeted to `.lint/` or `scripts/` as a one-line follow-up. |
| P18 | RFC 7807 error envelope (`application/problem+json`) | S | queued | **Same proposal as H4**; use H4 as the canonical ID. |
| P19 | Cursor pagination on the unified search; offset elsewhere stays for now | M (in P7) | queued | Dovetails with **L6**. |
| P20 | Reactive (Mutiny) migration for the timeseries read path as the first slice | M | queued | Dovetails with **A2**. |
| P21 | Introduce PATCH for partial-update endpoints (currently every update is PUT despite partial DTOs — `DataObjectRest:162`, `TimeseriesRest:472`, etc.) | M | **3 of N follow-ons done** | Surfaced by `aidocs/26-crud-consistency.md` §3 finding #1: **0 PATCH endpoints across 153**. Strategy choice: ship PATCH additively in `/v1/` and deprecate the partial-PUT semantics in `/v2/` (cleanest of three). Done so far: pilot **`Collection`** (`10531bd`), **`DataObject`** (`fd7ea76`), **`LabJournalEntry`** (`f0b3bd3` + canonicalisation). All under `context/`, all use `Constants.APPLICATION_MERGE_PATCH_JSON` (post-P21x), all six IT cases each. LabJournalEntry's PATCH also runs the existing `HtmlSanitizer.isSafeHtml` on the merged result so PATCH cannot bypass HTML safety. **Remaining `context/` candidates:** none with a metadata PUT (everything else hits P21-File-prereq or P21-References-prereq). |
| P21x | Move `APPLICATION_MERGE_PATCH_JSON` constant from per-Rest declarations to `Constants.java` so further P21 follow-ons reference one source of truth | — | XS | done | Round 5-extra, in-line edit. `Constants.APPLICATION_MERGE_PATCH_JSON` is now the single source of truth; `CollectionRest` and `DataObjectRest` updated to reference it. 58 sanity-check tests pass on merged branch. |
| P21-References-prereq | **Architectural finding:** the `*ReferenceRest` family (`BasicReference`, `CollectionReference`, `DataObjectReference`, `FileReference`, `SpatialDataReference`, `StructuredDataReference`, `TimeseriesReference`, `URIReference`) has **no `@PUT` for entity metadata** — same shape as P21-File-prereq. References are create-only-then-delete in the current model. | — | M | **needs decision** | Same call as P21-File-prereq: should references be metadata-editable post-creation? If yes, ship `PUT /<reference>/{id}` first; if no, P21 follow-ons for the references family are formally out of scope and `aidocs/26` finding #1 is further partially closed. |
| P21-File-prereq | **Architectural finding (P21-File agent stop-and-report):** the entire `data/` container family (`FileRest`, `StructuredDataRest`, `SpatialDataPointRest`, `TimeseriesRest`) has **no `@PUT` for entity metadata** — only `@PUT /{id}/permissions`. None of the `*ContainerService` classes expose an `updateContainer` method. PATCH on those resources is blocked on a prior decision: should data containers be metadata-editable post-creation, or stay create-only-then-delete? | — | M | **needs decision** | If yes, ship `PUT /<container>/{id}` first (for each of File / StructuredData / SpatialData / TimeseriesContainer), then the P21 PATCH follow-ons land trivially. If no, the PATCH follow-ons for the data family are formally out of scope and `aidocs/26` finding #1 is partially closed — the data containers are *deliberately* immutable. |
| P22 | SSE proxy-compatibility integration test — confirm `GET /collections/{id}/events` (P13) survives the existing reverse-proxy stack (Caddy / Keycloak) | S | queued | Tripwire from `aidocs/28-paradigms-and-clients-synthesis.md`. Must run before P13 default-on. |
| P23 | Presign-vs-cache TTL invariant validator — bound presigned-URL TTL by `shepard.permissions.cache.ttl` so a permission revoked while a URL is live cannot keep granting access | S | queued | Tripwire from `aidocs/28-paradigms-and-clients-synthesis.md`. Bundles with P12 (S3-presigned blob payloads). |

### Permission-system evolutions (from `aidocs/24`, 2026-05-05)

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| F1 | Declarative `@Authz(action, resource)` annotation seam replacing the path-segment switch in `PermissionsService.isAllowed` | M | queued | **Same proposal as P5.** Designed so a future `PolicyDecisionPoint` (OPA / Cedar) is a drop-in (~½-day add). Unblocks the path-segment-switch problem that L2c also needs to solve. |
| F2 | Group / sharing model: `Group` Neo4j node with `(User)-[:member_of]->(Group)-[:has_permissions]->(Permissions)-[:has_permissions]->(Entity)` | L | queued | Major gap for a research-data platform. Includes manager-groups + grant inheritance. |
| F3 | Permission audit log (Postgres table) for grants/revokes | S–M | queued | Postgres for query simplicity; not Mongo or Neo4j. Required before "who deleted my data" is answerable. |
| F4 | Versioned cache invalidation key: `(entityId, AccessType, userSub, permissionsVersion)` | S | queued | Mitigates cache blindness to context flips (project membership, group claim flips, role rotation). |
| F5 | Explicit fail-closed invariant when Neo4j is degraded | S | queued | Ties to A1b readiness signal: today probably 500s; recommend explicit DENY when Neo4j is DOWN, with an integration test. |
| F6 | Design F1's seam so a future OPA / Cedar `PolicyDecisionPoint` is a drop-in | XS (within F1) | queued | **Verdict from `aidocs/24`:** No to OPA/Cedar adoption now (overkill for a single Quarkus service with small graph-shaped policy); yes to making the seam future-proof at half-day cost. |
| F7 | Row-level security in TimescaleDB / PostGIS — denormalise ACLs into the data DBs | L | parked — conditions for unparking listed in `aidocs/24` | **Verdict:** No for v1. Backend is the only enforcement point; replicating ACLs into Postgres adds a second source of truth with no current threat-model justification. Conditions for a flip: more than one client bypasses the backend, or read fanout becomes the dominant cost. |

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
5. **Admin role mechanism (A0)** — A3b discovered there is no admin auth
   in shepard today. JWT roles are always `new String[0]`, so
   `@RolesAllowed("admin")` denies everyone. Three paths offered:
   (a) full admin-role mechanism populating roles from OIDC claims
   (~50 lines + config), (b) dev-profile-only exposure of admin
   endpoints, (c) scope-shift admin endpoints to `/temp/admin/*` with
   the same auth as the existing `/temp/migrations/*` carve-out and
   harden later. Affects A3b, P3c.
6. **R-series items (R1–R9)** from the second-pass input analysis are
   mostly blocked on design / framework / asset decisions — see
   "Newly surfaced" table.

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

### Round 3 — 2026-05-05

Dispatched 7 agents in parallel worktrees. Most are small/additive
follow-ups from Rounds 1–2; P2 is medium; L6 is research-only.
Non-overlap clauses included in each prompt:

- A1f owns new files in `common/healthz/` only; cannot modify A1b's surface.
- A4c owns a new `PermissionsCacheWarmer` startup observer; cannot add
  public methods to `PermissionsService` (P2's territory) or modify
  cache config (A4d's territory).
- A4d owns the `metrics-enabled` config key and a smoke test only;
  cannot touch `PermissionsService` or other cache config.
- A3b owns a new `FeaturesAdminRest` resource; cannot modify A3 surface.
- A1d owns `application.properties` config additions + a new
  `aidocs/17-startup-wait-audit.md`; **no source changes**.
- P2 owns the new `filterAllowedForUser` method on `PermissionsService`
  + rewiring **one** call site; cannot warm the cache or change config.
- L6 is research-only, producing `aidocs/18-pagination-inventory.md`
  and an index update; **no source changes**.

| ID | Agent description | Status | Commit | Notes |
|---|---|---|---|---|
| A1f | DB recovery scheduler on top of `DbHealthState` | done | `2f80600` | `@Scheduled` `PT15S`; adds `quarkus-scheduler` dep; 4 tests passing |
| A4c | Opt-in permission cache warming on `StartupEvent` | done | `a7167ff` | `shepard.permissions.cache.warm.enabled=false` default. `DefaultMostUsedEntityProvider` runs Cypher for top-N most-recently-updated entities. 3 tests passing. Conflict with A4's properties block resolved (kept both). |
| A4d | Enable Micrometer metrics on `permissions-service-cache` | done (partial) | `cf1e374` | Salvaged from rate-limited agent worktree. Property change landed; smoke test marked `@Disabled` because `@QuarkusTest` boots the full stack and hangs on `MigrationsRunner.awaitConnectivity` without live DBs. Spawned **A4e**. |
| A4e | Convert `PermissionsServiceCacheMetricsTest` to a `HealthzIT`-style integration test that runs against the docker-compose stack in CI | — | S | done | Round 4-extra, commit `1a5ac81` (+ disabled test removed in the same delta). Lands as `de.dlr.shepard.integrationtests.PermissionsServiceCacheMetricsIT` to match the project's actual Surefire-exclude convention (`pom.xml:333` excludes `**/integrationtests/*.java` by package, not by `*IT.java` filename). Failsafe picks it up; Surefire skips it. The `cache.gets > 0` assertion runs against the live stack via Prometheus metrics endpoint, matching `HealthzIT`'s pattern. |
| A3b | Read-only `GET /admin/features` endpoint | **blocked** | — | Agent stopped per scope guard: **no admin auth model exists**. JWT roles always `new String[0]`; `@RolesAllowed("admin")` would deny everyone. New unblocker item **A0** added. |
| A1d | Audit Mongo/Flyway/JDBC startup wait/retry; align with 60s ceiling | done | `e1c3635` | Adds `quarkus.flyway.connect-retries=10` + `connect-retries-interval=PT5S` (default was 120s, exceeded ceiling). Mongo/JDBC defaults already fail fast — no redundant config added. New `aidocs/17-startup-wait-audit.md`. |
| P2 | `PermissionsService.filterAllowedForUser` (single Cypher for N ids) + rewire one call site | done | `22f78b3` | **Stale assumption corrected:** the `parallelStream` cited at `input_raw.md:1668-1678` is actually a *data-fetching* stream, not an N+1 permission check. No N+1 permission-check site exists in the repo. Primitive landed (8 new tests; `PermissionsService.filterAllowedForUser` + `PermissionsDAO.findByEntityNeo4jIds`); call-site rewire is a noop until a downstream caller emerges. |
| P2c | `filterAllowedUsers(entityId, AccessType, Collection<String> usernames)` for `SubscriptionFilter.filter` | — | S | done | Round 4-extra, commit `a90f983`. Symmetric to P2's `filterAllowedForUser`. Rewires `SubscriptionFilter.filter` from N-call loop to one batched permission check; reuses `permissionsDAO.findByEntityNeo4jId` (no new DAO method needed). 5 new tests + `SubscriptionFilterTest` updated; 28/28 pass on merged branch. External behaviour preserved — asserted by `testFilterBatchPermissionCheck_preservesPerSubscriptionDecisions`. |
| L6 | Pagination inventory + sized rollout plan (research) | done | `c896fd9` | New `aidocs/18-pagination-inventory.md`. 38 list endpoints inventoried, 11 paginated today (29%). Recommends extending the existing `?page&size` convention rather than the cursor proposal in §2.6 of `13-search-improvements.md` for the existing 27 unpaginated list endpoints — coexistence with cursor for `POST /search/v2`. |

**Rate-limited agents (2026-05-05, ~10:48 UTC):** Three background
agents hit the global usage cap (resets 15:20 UTC) before pushing.
Outcomes:

- **A4d (cache metrics):** worktree had partial work (property + a
  smoke test). Salvaged manually as `cf1e374`; test marked `@Disabled`
  pending integration-test conversion (A4e).
- **`aidocs/23-api-critique.md`:** worktree empty; nothing salvageable.
  Need to redispatch after limit resets.
- **`aidocs/24-permission-system-review.md`:** worktree empty; nothing
  salvageable. Need to redispatch after limit resets.

### Round 4 — 2026-05-05 (analysis layer)

While Round 3 implementation agents ran, four parallel **analysis-only**
agents produced strategic / design docs requested by the maintainer:

| Doc | Purpose | Status | Commit |
|---|---|---|---|
| `aidocs/19-architecture-feedback.md` | Critical review of post-Round-3 architecture | done | `879ffc0` |
| `aidocs/20-epic-roadmap.md` | 14-epic catalogue with Mermaid dependency graph + 2-track 6-month plan | done | `fc4ae3b` |
| `aidocs/21-user-interest-gauge.md` | Demand signals for HDF5/HSDS, tabular, KG interfaces + survey plan | done | `362712e` |
| `aidocs/22-admin-cli-draft.md` | Candidate functions for a future `shepard-admin` CLI | done | `452eded` |

**Key findings from these docs that should drive Round 4 priorities:**

- **`aidocs/19` flags two security fragilities at the top:**
  - **C3 fallback** at `PermissionsService.java:262+` — when the
    permissions node is missing, the fallback grants Reader+Writer+Manager.
    This is a full-access backdoor catalogued in
    `aidocs/07-security-issues.md` C3 — **not new, but not yet fixed**.
  - **C5 Cypher injection** at `Neo4jQueryBuilder.java:198-244` —
    string-concatenated query construction; cross-cuts every search
    endpoint and gates `aidocs/13-search-improvements.md`.
  - Plus **A0** (admin role) — already in our backlog.
- **`aidocs/20` recommends two parallel tracks:**
  - **Foundations track:** E1 (foundations: A0 + observability + P4 + L6 + S1) → E5 (streaming + ID alignment) → E11 (permissions + admin CLI) → E2 (unified search v2).
  - **User-value track:** E12 (UX & ecosystem) → E6 (annotation generalisation) → E9 (spatial graduate/deprecate) → E4 (triplestore + SPARQL).
  - Three "could start tomorrow" epics: **E1, E5, E12**.
  - Three "blocked on user decision" epics: **E14** (Neo4j ID migration owner), **E9** (spatial graduate vs deprecate), **E4** (default ontology + reasoning profile + n10s deployment).
- **`aidocs/21` ranks the three candidate development directions:**
  - **KG interfaces:** strongest evidence (4 open GitLab issues, partial `SparqlConnector` already, two independent user groups). Ready to act once #274 unblocks.
  - **HDF5/HSDS:** low-medium; one named asker, no GitLab issue, TimescaleDB satisfies the temporal subset. **Maintainer-confirmed hard constraint (2026-05-05):** any HDF5 surface must be compatible with the existing Python ecosystem (`h5py` / `PyTables` / `pandas.read_hdf`) — `h5pyd` over HSDS is the canonical access path; "download original file" is the fallback. Recorded in epic E7 (`aidocs/20-epic-roadmap.md:309-329`) and §3.6 of `aidocs/21-user-interest-gauge.md`.
  - **Tabular/relational:** thin; the ask hides two distinct products (interface vs storage). Defer until separated.

| ID | Agent description | Status | Commit | Notes |
|---|---|---|---|---|
| P3 | InfluxDB→TimescaleDB migration progress monitoring + persisted resume | done | `7cc74b8` | Found split: orchestrator is the external `timescale-migration-preparation` image (source not in this repo); the COPY path lives in `TimeseriesDataPointRepository`. Landed: Flyway `V1.9.0__add_migration_progress_table.sql`, `MigrationProgress` entity + repo + service, `MigrationRunner`, `MigrationProgressIO`, `GET /temp/migrations/state` and `GET /temp/migrations/{containerId}` endpoints. New COPY method `insertManyDataPointsWithCopyCommandBatched` with batch index + reporter callbacks (existing method untouched). 17 new tests passing. |
| A1b | Split health checks into Startup / Readiness / Liveness with per-DB detail | done | `8f40156` | New `common/healthz/` infra: `DbHealthState`, `DbPinger`, `ReadinessConfig`, `AbstractDb*Check`, `JvmLivenessCheck`. Per-DB pingers + startup checks for Neo4j/Mongo/Timescale/PostGIS. PostGIS short-circuits to UP when toggle off via existing `SpatialDataFeatureToggle.isActive()`. `shepard.health.readiness.max-staleness=PT30S` configurable. `HealthzIT` updated. 21 new unit tests passing. |
| A1e | `MigrationsRunner.apply()` fail-fast on swallowed exceptions | done | `0f2f512` | Worktree was forked from before A1; merge conflict in `MigrationsRunnerTest.java` resolved by combining both test sets. Adds 3 tests to A1's 5 → 8 tests total on `MigrationsRunner`, all passing. |
| L5 | Optional `validUntil` on API keys; auth rejects expired | done | `30c687a` | Hybrid system (Neo4j `ApiKey` row + JJWT-encoded). Filter rejects expired keys with 401 + `WWW-Authenticate: ApiKey error="expired"`. Existing keys without `validUntil` keep working. Schema impact additive nullable. 9 new tests across 3 test classes, all passing. |
| A3 | Runtime feature toggle mechanism + migrate the `versioning` toggle | done | `ddeeb31` | One `@IfBuildProperty` use found (`versioning`) and migrated. Adds `@ConditionalOnFeature` qualifier + `FeatureBeanProducer`; renames property to `shepard.features.versioning.enabled`. Conflict with A1's added property in `application.properties` resolved trivially. 2 new tests (enabled/disabled profiles), passing. |
