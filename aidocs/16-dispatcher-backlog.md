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

1. `dlr-shepard/shepard` (this repo, Java/Quarkus backend + Nuxt 3 / Vue 3 /
   Vuetify 3 frontend + client libs) — **in scope**. (Earlier rounds said
   "Angular"; corrected by the `aidocs/33` agent which verified against
   `frontend/package.json:24,45-46`.)
2. `dlr-shepard/shepard-dataship` (separate Python client; M1–M9 milestones
   on lines 975–1214 reference `src/ui/*.py`, `src/worker.py`, etc.) —
   **out of scope here**, parked.
3. Program-level work (HMC, federation, project website, AAS) — **parked**,
   not implementable from this repo.

## Backlog (this repo only)

### Cross-cutting auth and security (added / promoted 2026-05-05)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| A0 | Admin role mechanism: `instance-admin` role tier, populate `JWTPrincipal.roles` from configurable IdP claim path AND/OR Neo4j `:HAS_ROLE` edge (dual-source), wire `SecurityContext.isUserInRole` → `@RolesAllowed("instance-admin")` works. Bundles **C3** + **F8**. | — | M–L | **done** | Backend slice landed (cherry-pick `2901834`). New `Role` entity, `:HAS_ROLE` rel, V13 appId constraint + V14 orphan-permissions backfill (idempotent, refuses if `shepard.permissions.default-owner` unset and orphans exist), bootstrap-token mechanism (`/opt/shepard/.bootstrap-token` mode 0600), `POST /v2/admin/bootstrap`, `GET/POST/DELETE /v2/admin/instance-admins`, `GET /v2/admin/permission-audit`, API-key `roles` field with allowlist + caller-must-have validation. CLI commands deferred to L1 Phase 1 (per `aidocs/51 §11` step 9). 37 new tests; 1268 → 1305 total. |
| C3 | Remove the full-access fallback in `PermissionsService.getRoles` when the permissions node is missing | `aidocs/07-security-issues.md` C3, `aidocs/19-architecture-feedback.md` §2, `aidocs/24-permission-system-review.md` §3 | S | **done** | Bundled with **A0** (`2901834`). `getRoles` now returns `Roles(false,false,false,false)` fail-closed for orphan entities. V14 backfill (`shepard.permissions.default-owner` configured) attaches default Permissions; pre-migration `OrphanPermissionsBackfillContext` aborts startup if orphans + config-unset (post-A1e fail-fast). `GET /v2/admin/permission-audit` surfaces remaining orphans. |
| C5 | Replace string-concatenated Cypher query construction in `Neo4jQueryBuilder.java`, `PermissionsDAO.java`, `DataObjectDAO.java` with parameterised queries | `aidocs/07-security-issues.md` C5, `aidocs/19-architecture-feedback.md` §2, `aidocs/25-neo4j-id-migration-design.md` §5 | M | done | Cherry-pick `e90bfd8`. New `Neo4jQuery(cypher, params)` record; `ParamBinder` threaded through every recursive `Neo4jQueryBuilder` helper. Property-name allowlist next to `OP_PROPERTY`/`OP_VALUE`. **Subsumes M9.** 10-test injection regression suite. **L2c precondition cleared.** |
| C5b | Apply C5's parameter-binding pattern to the second-wave `id()=` / `ID()=` sites — `GenericDAO`, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, `*ReferenceDAO` family, `SemanticAnnotationDAO` | — | M | done | Cherry-pick `c707e56`. 12 production files + 12 test files; final exit-grep clean of user-reachable injection sites. **L2c fully unblocked.** |
| H4 | Surface RFC 7807-shape error responses (existing high-finding from `07`) | `aidocs/07-security-issues.md` H4, `aidocs/19-architecture-feedback.md` §3 | M | done | Cherry-pick `e526183`. New `application/problem+json` shape via `ProblemJson` record; 10-entry `ShepardErrorCode` catalogue; content negotiation preserves legacy `ApiError` for explicit `Accept: application/json`. **Subsumes M7.** **L2d fully unblocked.** |

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
| A3b | `/admin/features` endpoint to view/modify runtime toggles | 1519–1523 | S | **done** | `FeatureToggleRegistry` (`@ApplicationScoped`, registers `spatial-data` + `versioning` on `StartupEvent`, runtime-mutable override on top of config value); `GET /v2/admin/features` + `PATCH /v2/admin/features/{name}`, `@RolesAllowed("instance-admin")`-gated at class level; 4 unit tests. Override is in-process only — does not survive restart. Commit `b9d87cc`. |
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
| P3c | Tighten authorisation on `/temp/migrations/*` (was: always-allowed for any authenticated user; intended for ops) | — | S | **done** | `@RolesAllowed("instance-admin")` at the class level on `MigrationProgressRest`. PermissionsService.isAllowed "temp/migrations" carve-out kept (used by SubscriptionFilter to skip notifying subscribers about migration state changes); the JAX-RS `@RolesAllowed` gate runs separately and is what blocks non-admin callers. 4 new tests (annotation-presence regression + 200/404 happy paths). **AWARE in `aidocs/34`**: scripts that polled migration status as a regular user now get 403. |
| P4 | API versioning routing scaffolding — `/shepard/api/...` (frozen upstream surface) + `/v2/...` (this fork's development surface) live side-by-side under a single Quarkus `Application` | 1760–1764 | S | done | `quarkus.http.root-path` flipped from `/shepard/api` to `/`. Every existing JAX-RS resource (29 classes) carries an explicit `@Path(Constants.SHEPARD_API + "/" + …)` prefix; new `Constants.SHEPARD_API = "shepard/api"` is the single source of truth. New `de.dlr.shepard.v2` package marker reserves the namespace for L2d's incoming `/v2/` resources. `RequestPathHelper.applicationSegments` / `.applicationPath` strip the prefix back off for filters that interpret the path semantically (`PermissionsService.isAllowed`, `SubscriptionFilter.extractEntityId`, `MigrationModeFilter`, `PublicEndpointRegistry`) so first-segment dispatch stays byte-identical. ArchUnit `V2NamespaceTest` fences the convention three ways: classes in `de.dlr.shepard.v2..` must use `/v2/`; classes outside must NOT use `/v2/`; classes outside MUST use `/shepard/api/`. New `@Sunset` annotation (RFC 8594) + `SunsetFilter` skeleton ship for L2e's deprecation window — no method/class uses it yet. User-facing URLs are unchanged. Full suite `Tests run: 1271, Failures: 0, Errors: 1, Skipped: 129` — single error is the pre-existing `oidc.public` baseline (same shape as L2c/C5b).  |
| P4b | OpenAPI client tree-shaking / code splitting | 1765–1774 | S | queued | Frontend / clients |
| P4c | OpenAPI emission — split into per-shelf documents (`/shepard/doc/openapi/{v1,v2}.json`) via `OASFilter` | — | S | queued | Follow-up from P4. Today: single `/shepard/doc/openapi.json` lists both `/shepard/api/...` and `/v2/...` paths; `ApiPathFilter` already strips the `/shepard/api` prefix from the v1 paths. Quarkus 3.27 `smallrye-openapi` only supports a single doc out-of-the-box; a per-shelf split needs a custom `OASFilter` that reads the path and routes it to the right document, plus `quarkus.smallrye-openapi.path` configured per profile. Land before generated clients diverge across shelves (intersects with **P17**). |

### Loose-ideas section (lines 1–672, 700–847)

| ID | Item | input_raw refs | Size | Status | Notes |
|---|---|---|---|---|---|
| L1 | Admin CLI: cleanup of data marked for deletion, import/export of collections as RO-Crate | 705–708 | M–L | queued | Now backed by `aidocs/22-admin-cli-draft.md`. Phase 1 (read-only — features list / health / migrations status) is unblocked once **A0** lands. Phase 2 (cleanup deleted-entities) needs explicit `--dry-run` semantics + TTL design. Recommended framework: Java + Picocli. |
| L2 | Neo4J: stop using deprecated `id()` function, **migrate to application-generated IDs** (NOT `elementId()`) | 90, 715–717 | L (~7–9 eng-weeks across ≥2 minor releases) | **design done** — see `aidocs/25` | Design landed `8a9c14a`. ID scheme **UUID v7** (time-ordered, Java native, composes with cursor pagination per `aidocs/12 §11.A.2` / `aidocs/13 §2.6`). Sub-IDs **L2a–L2e** below. |
| L2a | Phase 1: additive `appId` property + unique constraint, mixin on entity write side | — | S–M | done | Cherry-pick `fec7979`. `HasAppId` mixin on 28 `@NodeEntity` classes (via `AbstractEntity`/`AbstractMongoObject` + 8 standalones); `AppIdGenerator` (UUID v7 via `com.github.f4b6a3:uuid-creator`); single seam in `GenericDAO.createOrUpdate` mints on save when `appId == null`; Neo4j migration `V11__Add_appId_unique_constraints.cypher` adds 28 per-label `REQUIRE n.appId IS UNIQUE` constraints (Neo4j 5 ignores nulls until L2b backfill). Read path still uses `id()`. |
| L2b | Phase 2: Cypher backfill `appId = randomUUID()`, idempotent | — | S | done | Cherry-pick `796bc11` (was `541884f` on worktree). `V12__Backfill_appId.cypher` — 28 per-label `MATCH … WHERE n.appId IS NULL CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;` statements. Operator-run rollback at `V12_R__Rollback_Backfill_appId.cypher`. Test added at `TestNeo4jMigrations.testV12`. Per-chunk `MigrationProgress` Neo4j-side schema deferred. |
| L2c | Phase 3: switch every Cypher to `WHERE e.appId = $appId` | — | L | done | Cherry-pick `f3ca003`. New `EntityIdResolver` `@RequestScoped` bean translates Long ↔ `appId` at the DAO boundary; 14 DAO files swapped (`PermissionsDAO`, `DataObjectDAO`, `GenericDAO`, the `*ReferenceDAO` family, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, `SemanticAnnotationDAO`); public DAO signatures stay `long` for caller-compat; cache key stays `long` (per design §3.3); `Neo4jQueryBuilder`'s search-JSON predicates and `PermissionsService.isAllowed` segment dispatch deliberately untouched (those are L2d). 9-test `EntityIdResolverTest`. Coverage 69.05% line / 66.25% branch. |
| L2d | Phase 4: `/v2/` exposes `appId` natively | — | L | **gated on H4** (P4 cleared) | P4 routing scaffolding shipped — `/v2/` resources land in `de.dlr.shepard.v2.<feature>.resources.*` with `@Path("/v2/...")`; ArchUnit `V2NamespaceTest` fences the convention. RFC 7807 error shape (H4) is the remaining gate before L2d can ship. |
| L2e | Phase 5: drop legacy `/v1/` long-id paths, flip `permissions-service-cache` key from `(long, AccessType, String)` to `(String, AccessType, String)`, drop TimescaleDB legacy column | — | M | queued | Deprecation-window exit. Cache flush on cutover; intersects with A4c warming |
| L3 | ~~Templates system: YAML-defined templates~~ — **superseded by T1** (`aidocs/39`). | 98–137 | — | superseded | Reconciled with #630; see T1 row below. YAML lives on as T1f import/export. |
| L4 | Search-as-you-type with tree/graph view of ontology | 96, 869 | M | queued | Frontend; intersects with `13-search-improvements.md` and `14-semantic-improvements.md` |
| L5 | Semi-permanent API keys with expiry | 694 | S | done | Round 2, commit `30c687a`. API keys are hybrid Neo4j-row + JJWT-encoded; landed `validUntil` field, JWT `exp` claim, distinguishable 401 on expiry. |
| L6 | Output control: pagination on more endpoints | 689–691 | S | queued | Aligns with `13-search-improvements.md` cursor pagination |
| L7 | (Semantically) annotate everything: extend semantic annotations to file/structured/spatial payloads | 692, lines 0+ in `14-semantic-improvements.md` | L | queued | Already designed in §14 |
| L8 | Review permissions model — umbrella | 693 | M | **design done** | `aidocs/24-permission-system-review.md` is the deliverable. Concrete unpacking is **F1 + F2 + F3** plus the C3 / A0 / F4 / F5 fixes. F6 (OPA seam) and F7 (row-level security) deferred. |
| A5 | HDF5/HSDS support — umbrella (epic E7) | 697–712 | L | **design done** | `aidocs/35-hdf5-hsds-implementation-design.md` picks **HSDS sidecar + shared-Keycloak token relay** out of three architectures. `h5pyd` parity is the deliverable. Sub-IDs A5a–A5e below. **Gated on L2c** (so HDF endpoints launch directly at `/v2/<appId>`). |
| A5a | Phase 1: HSDS sidecar + `HdfContainer` create/read/delete + Neo4j model + `V13__Add_appId_constraint_HdfContainer_HdfReference.cypher` migration. HTTP Basic auth (admin-managed) for day 1. | — | M | queued (gated on L2c) | New `data/hdf` module mirroring existing payload-type pattern. New `hdf` profile in `infrastructure/docker-compose.yml`, off by default. Storage default POSIX, S3/MinIO opt-in via `SHEPARD_HDF_STORAGE`. |
| A5b | Phase 2: Permission bridge — shepard permission changes flow to HSDS ACLs via `PermissionsService` post-commit hook. shepard is source of truth; direct HSDS ACL mutation rejected with RFC 7807. Includes a "rebuild ACLs from scratch" admin endpoint for drift recovery. | — | M | queued | Builds on A5a |
| A5c | Phase 3: `HdfReference` (per-DataObject anchor at a specific dataset path) + annotation hookup via E6 (`AnnotatableHdfDataset`). | — | S | queued | Builds on A5a + E6 |
| A5d | Phase 4: Download-original-file fallback (`GET /hdf-containers/{appId}/file`) returning byte-identical HDF5 via HSDS bulk-export. Range requests pass through. | — | S | queued | Builds on A5a. Unblocks the offline-`h5py.File(local)` path. |
| A5e | Phase 5: Auth bridge — `/api-keys/{id}/hsds-token` mints short-lived JWT signed by shared Keycloak realm; HSDS validates via JWKS. 3-line `clients/python` helper that returns an `h5pyd.File`. | — | S–M | queued | Unblocks the `h5pyd` ergonomics. Depends on shared-Keycloak realm config (operator-side). |
| U1 | User profile + account settings — umbrella | issues #29, #694, #628 | M–L | **design done** | `aidocs/36-user-profile-and-settings-design.md` resolves the configuration-vs-profile UI question (split, not merge: `/me` personal, `/admin` shared) and defines the `SettingDescriptor` extension pattern. Sub-IDs U1a–U1f below. |
| U1a | Phase 1: `User.orcid` field + `OrcidValidator` (mod 11-2 checksum) + `PATCH /v2/users/me` (merge-patch, orcid only). **Closes #29.** | — | S | **done** | 20 new tests (`OrcidValidatorTest` 10 + `MeRestTest` 10 covering 401/400/checksum/null-clears/empty-clears/RFC-7396-merge-semantics/unknown-key-ignore). Endpoint landed at `/v2/users/me` (not the originally-proposed `/users/me`) per the `/v2/` API-version policy. `UserPublicIO` projection deferred — `UserIO` carries `orcid` additively today; tighter projections land alongside U1c. RO-Crate exporter picks up `orcid` automatically once present (`aidocs/31`). |
| U1b | Phase 2: `User.displayName` override + `effectiveDisplayName` derivation (`displayName ?? "${firstName} ${lastName}".trim() ?? redactUsername(username)`) + audit-trail render switch ("Created by …" uses display name). **Closes #694**, mitigates #628. | — | S | **field + derivation done; audit-trail render switch queued** | New `DisplayNameResolver.effectiveDisplayName(User)` static helper + new `User.displayName` field (nullable, auto-persists). `UserIO` carries both `displayName` (raw) and `effectiveDisplayName` (computed) so clients can distinguish "user picked this" from "system derived this". `PATCH /v2/users/me` extended (U1a's MeRest) to accept `displayName`. Cryptic-Keycloak-username path: trailing-segment extraction (after `:` or `/`) + truncate-to-8-plus-ellipsis for UUID-shaped tails. 19 new tests (`DisplayNameResolverTest` 13 + 6 added to `MeRestTest`). **Audit-trail render switch** (the "Created by …" call sites) is queued as a follow-up (U1b2). |
| U1c | Phase 3: Frontend split. New `/me` route absorbs `#profile` + `#apikeys` + `#subscriptions` panes; rename `/configuration` → `/admin` for the residual admin surface (User Groups, Semantic Repos). Land `SettingDescriptor` skeleton + Lanterna-equivalent typed-map storage. | — | M | queued | Frontend; depends on U1a + U1b. |
| U1d | Phase 4: Preferences pane in `/me`: `theme`, `language`, `timeZone`, `dateFormat`, `defaultPageSize`, `defaultLandingPage`. Backend stores via `Map<String,String>` on `User` + `GET/PATCH /users/me/preferences`. | — | M | queued | First substantial use of `SettingDescriptor`. |
| U1e | Phase 5: Avatar — shepard-uploaded path (`PUT/DELETE /users/me/avatar`), Mongo `userAvatars` collection, public render at `/users/{appId}/avatar`. Gravatar + IdP-`picture` precedence wired by U1c so this slice is just the upload path. | — | S | queued | Image processing intentionally out of scope (no ImageMagick). |
| U2-coupled | Secret-class settings (encrypted-at-rest, AES-GCM with `~/.shepard/keys/secrets.key`) + `git.pat` + `git.host`. Lands as part of the Git-integration umbrella (`aidocs/38`, design pending). | — | S | gated on aidocs/38 | Per `aidocs/36 §3.3`. |
| U1f | (deferred) Verified-ORCID via OAuth flow, `verifiedAt` timestamp, UI badge. | — | M | parked | Optional once #29 is closed by U1a. |
| U3-coupled | `editor.preferredJupyter` setting — added with the lab-journal-Jupyter feature (`aidocs/37`). | — | XS | gated on J1c | Trivial setting; lands when the feature lands. |
| J1 | Lab journal v2 + Jupyter integration — umbrella | issues #507, #368 | M | **design done** | `aidocs/37-lab-journal-and-jupyter-design.md`. Markdown body, inline `.ipynb` static render, "Open in Jupyter" deep link, edit history. New endpoints under `/v2/` per CLAUDE.md API-version policy. Sub-IDs J1a–J1f below. |
| J1a | Markdown body interpretation (CommonMark + GFM, sanitised) + `GET /v2/lab-journal/{appId}/render`. | — | S | queued | No data migration; CommonMark passes plain text through unchanged. |
| J1b | Inline `.ipynb` static render via client-side nbviewer-js. New `GET /v2/lab-journal/{appId}/notebooks` endpoint to list `.ipynb` FileReferences. | — | S | queued (gated on J1a) | Zero new backend deps; render in the frontend bundle. |
| J1c | "Open in Jupyter" deep link consuming `editor.preferredJupyter` from `aidocs/36 §3.2`. | — | XS | queued (gated on aidocs/36 U1d) | Closes the "I want to re-run this" loop. |
| J1d | Edit history via append-only `LabJournalEntryRevision` sibling node. | — | M | queued | Resolves "I corrected my entry but lost the original phrasing." |
| J1e | (deferred) Server-side `nbconvert` for very large notebooks. | — | M | parked | When notebook size becomes a real perf gap. |
| J1f | (deferred) Live kernel via JupyterHub bridge. | — | XL | parked | Out of scope; mentioned only for completeness. |
| G1 | Git integration — umbrella | user request | M | **design done** | `aidocs/38-git-integration-design.md`. New `GitReference` payload-kind. Three modes: loose link / tracked / pinned. Per-host adapters (GitLab/GitHub/Gitea). All endpoints under `/v2/`. Sub-IDs G1a–G1f below. |
| G1a | `GitReference` (mode a, loose link) + Neo4j model + `V19__Add_appId_constraint_GitReference.cypher` + `/v2/data-objects/{appId}/git-references` CRUD. UI renders as clickable link. | — | S | **done** | 20 new tests (`GitReferenceTest` 3 + `GitReferenceRestTest` 17 covering 401/403/404/cross-DataObject-leak-guard/missing-DataObject-defensive). Mode-(a) fields shipped: `repoUrl` (required), `ref`, `path`. Mode-(b)/(c) fields (`sha`, `resolvedSha`, `resolvedAtMillis`) scaffolded on the entity so G1b/c don't reshape the node label; surfaced as read-only-null on the wire. Migration number bumped from the design's `V14` to `V19` (V14 was already taken by C3 orphan-permissions backfill). |
| G1b | Mode (b) tracked-artifact + `GitLabRestClient` adapter. Reads user's `git.pat` (`aidocs/36 §3.2`); inline preview for markdown / source files; `PT5M` cache per `(user, repoUrl, ref, path)`. | — | M | gated on `aidocs/36 U2-coupled` | First per-host adapter is the reference shape for G1d. |
| G1c | Mode (c) pinned snapshot + RO-Crate `SoftwareSourceCode` integration. Resolves `ref` to SHA at export time. | — | M | gated on G1b + `aidocs/31` | Unlocks reproducible exports. |
| G1d | GitHub + Gitea per-host adapters. | — | S | gated on G1b | Reuses the GitLab adapter shape. |
| G1e | (deferred) Webhook from git host → shepard for "the analysis code changed" notifications. | — | M | parked | Subscriptions integration. |
| G1f | (deferred) shepard pushes artifact metadata back to git as a sidecar `.shepard.yaml` file. Gitops shepard. | — | L | parked | Explicit non-goal for v1. |
| T1 | Templates — umbrella (replaces L3) | issue #630 | L | **design done** | `aidocs/39-templates-design.md` reconciles L3 (YAML-defined) with #630 (Templates Collection of DataObject blueprints) — recommends #630 storage shape primary + YAML as optional admin interchange. Bakes in user constraint that **Collection owners decide which templates from the global repository are allowed in their Collection** via `Collection.allowedTemplateAppIds`. Gated on L2c so instances reference templates by stable `appId`. Sub-IDs T1a–T1h below. **Supersedes the L3 row**. |
| T1a (legacy `aidocs/39` shape) | ~~`__templates` Collection auto-created at start; reserved-name Collection.~~ | — | — | **superseded** | Replaced by the real `:ShepardTemplate` Neo4j entity per `aidocs/54` (see T1 revised below; T1a-aidocs/54 lands in `feat/t1a-shepard-template-entity`). |
| T1b | `AttributeSpec` model (required / type / enum / default / description) stored as JSON blob on template DataObject. Admin POST/PATCH/DELETE for templates. | — | M | gated on T1a | JSON blob, not per-attribute Neo4j nodes — small attr counts, fast read. |
| T1c | `FileSlot` model (required / allowedMimeTypes / description) — defines what file kinds a template requires/permits on instantiation. | — | S | gated on T1b | Same JSON-blob storage as AttributeSpec. |
| T1d | Per-Collection allow-list — `Collection.allowedTemplateAppIds` field + `GET / PUT /v2/collections/{appId}/allowed-templates` (owner role; empty = unrestricted; admin can override). | — | S | gated on T1a | Fulfils the user's "Collection owners decide allowed templates" constraint. Ships in parallel with T1b/c. |
| T1e | Instantiation flow — `POST /v2/collections/{appId}/data-objects/from-template/{templateAppId}` with required-field validation; FileSlot upload validation; `[:CREATED_FROM_TEMPLATE]` graph edge + sneaker attribute on the instance. UI picker. | — | M | gated on T1b + T1c + T1d | The user-facing payoff slice. |
| T1f | YAML import/export admin tools (`POST /v2/templates/import`, `GET /v2/templates/export`) — round-trips templates as portable artifacts (the "L3 portability story"). | — | M | gated on T1b | Best-effort; explicit warnings on non-round-trippable fields. |
| T1g | (deferred) "Update instance to latest template version" action. | — | M | parked | Manual opt-in for individual instances. |
| T1h | (deferred) Collection-level templates (templates that produce a whole Collection structure, not just a DataObject). | — | L | parked | Out of scope for v1; L3-original ambition. |
| PR1 | Process design + runtime in shepard — umbrella (replaces SPW runtime over time) | user request, `aidocs/40 §2` | L | **design done** | `aidocs/40 §2` proposes a `ProcessDefinition` extension to T-series templates plus a browser-hosted runtime. Coordinates with shepard-process-wizard maintainers; SPW becomes design-side assistant. Sub-IDs PR1a-PR1e below. |
| PR1a | `ProcessDefinition` model + JSON `processSpec` blob + CRUD `/v2/processes` (read-only). | — | M | gated on T1b | Processes are typed sequences of templates. |
| PR1b | `ProcessRun` runtime: start a run, advance steps, persist progress. UI stepper. | — | L | gated on PR1a + T1e | The user-facing payoff slice. |
| PR1c | SPW XML importer (`POST /v2/processes/import`). | — | M | gated on PR1a | Best-effort; explicit warnings on unrepresentable constructs. |
| PR1d | Conditional / parallel flow control. | — | M | gated on PR1b | Closes the gap with SPW's existing flow capabilities. |
| PR1e | (deferred) Headless / API-driven run mode (cron, CI). | — | M | parked | Useful but not v1. |
| V2 | Snapshots — umbrella (point-in-time freeze on top of today's Version marker) | user request, `aidocs/41` | M-L | **design done** | `aidocs/41-snapshots-design.md` picks logical snapshots backed by entity revisions (option C). Storage cost O(entities-in-scope). Sub-IDs V2a-V2f below. |
| V2a | `revision: long` field on `VersionableEntity` + write-side increment + `V17__Add_revision_to_versionable.cypher` (idempotent backfill `revision = COALESCE(revision, 1)`). | — | S | queued | Independent value: enables optimistic locking. |
| V2b | `Snapshot` + `SnapshotEntry` model + `POST /v2/collections/{appId}/snapshots`. Without read-side rewriting yet. | — | M | gated on V2a + L2c | Snapshot creation lands; reads stay on the live tree until V2c. |
| V2c | Snapshot-pinned read path (`GET /v2/collections/{appId}?snapshot={snapshotAppId}`). Resolver layer + permission cache key extension. | — | M | gated on V2b | The user-visible "frozen view" payoff. |
| V2d | RO-Crate export against a snapshot — reproducible by construction. | — | S | gated on V2c + `aidocs/31` | One-line addition to `ExportSelection` body. |
| V2e | Snapshot diff tool (`GET /v2/snapshots/{a}/diff/{b}`) — entities added / removed / changed-revision. | — | M | gated on V2b | Useful for "what happened between v1 and v2." |
| V2f | (deferred) "Branch from snapshot" — fork off a snapshot into a writable child Collection. | — | L | parked | Significant scope; only on real demand. |
| AI1 | AI opportunities — umbrella (traditional ML + LLM integration) | user request, `aidocs/43` | XL | **survey done** | `aidocs/43-ai-opportunities.md`. Five hard constraints (data residency / cost / auditability / human-in-the-loop / reproducibility). Local-model default; hosted-model bridge opt-in only. Sub-IDs AI1a-AI1p below. **Each LLM-flavoured slice is suggestion-only — no autonomous mutation in v1.** |
| AI1a | **AI plumbing slice.** Per-user `ai.apiKey` / `ai.baseUrl` / `ai.model` settings (`aidocs/36 §3.2`); admin `shepard.ai.fallback.*` config; OpenAI-compatible `LlmClient` against `/v1/chat/completions` + `/v1/embeddings`; resolution rule `user-key → admin-fallback → hidden`; per-Collection sensitivity toggle. **Zero models bundled, zero inference container.** AI controls hidden in UI when neither user-key nor fallback is configured. | — | M | gated on aidocs/36 U2-coupled (secret-class settings infra) | Gate for AI1d-AI1l. |
| AI1b | Anomaly detection: `POST /v2/timeseries/{appId}/detect-anomalies` with rolling-median + isolation-forest. Pure-Python, no LLM call. **Independent of AI1a** — ships even on installs without an LLM endpoint. Optional `dlr:anomaly` annotation on hits. | — | M | queued | Algorithm already exists in `examples/seed-showcase/notebooks/anomaly-analysis.ipynb`; this is an extraction. |
| AI1c | Channel-quality scoring: background job emits `qualityScore` attribute on every TimeseriesReference. Pure heuristics, no LLM. **Independent of AI1a.** Search-by-quality unblocked. | — | S | **shipped** | `TimeseriesQualityScorer` (completeness + coverage + stability heuristic, public `score(List<TimeseriesDataPoint>)` returning `Optional<Double>` in `[0, 1]`) + `TimeseriesQualityScoringJob` (`@Scheduled`, default off via `shepard.timeseries.quality-scoring.enabled=false`; interval `PT6H`; batch-size `100`, clamped `[1, 10000]`; 24 h per-ref rescoring window). Score + `lastScoredAt` surface read-only on `TimeseriesReferenceIO`. Searchable as a primitive Neo4j property. No migration needed (additive nullable). 41 unit tests; scorer 98% line / 92% branch, job 97% line / 87% branch. |
| AI1d | Embedding-based similarity + `GET /v2/data-objects/{appId}/similar`. Calls `/v1/embeddings` against the configured endpoint. | — | M | gated on AI1a | Improves search before chat lands. |
| AI1e | **Snap dashboards (§5.8) — the killer feature.** Chat sidebar with closed tool-use catalogue (`search_data_objects` / `read_attributes` / `fetch_timeseries` / `fetch_structured` / `list_lab_journal` / `render_chart` / `render_table`); inline Vega-Lite v5 rendering; `DashboardReference` save shape; iteration loop with model picker. **No code execution sandbox** — Vega-Lite specs only. | — | L | gated on AI1a + L2c | The user-facing payoff slice. Requires stable `appId` for tool-use entity addressing. |
| AI1f | Natural-language search (§5.1): `POST /v2/search/natural` returns the LLM-generated structured query *and* results. User-in-the-loop; query is editable. | — | M | gated on AI1a + `aidocs/13` unified search | |
| AI1g | Lab journal authoring assist: ghost-text completion in the editor, accept-edit-reject UX. `aiGenerated: true` attribution. | — | M | gated on AI1a + `aidocs/37` J1a | |
| AI1h | Semantic annotation suggestion: `POST /v2/semantic-annotations/suggest`. Suggestion-only; ontology-filter required. | — | M | gated on AI1a + `aidocs/14` | |
| AI1i | Auto-summarisation: per-DataObject `summary` attribute (debounced rebuild on last-modified change). | — | S | gated on AI1a | |
| AI1j | RO-Crate description generation: `?aiAssist=true` on export; operator review before commit. | — | S | gated on AI1a + `aidocs/31` | |
| AI1k | Conversational lineage: chat interface walking the lineage graph. Retrieval + render only — no claims. | — | M | gated on AI1a + `aidocs/30` | |
| AI1l | Notebook scaffolding: "Open in Jupyter with starter notebook" button. Generated `.ipynb` attached as FileReference. | — | S | gated on AI1a + `aidocs/37` J1c | |
| AI1m | (deferred) Forecasting (§3.3) — only if real demand surfaces. | — | M | parked | |
| AI1n | (deferred) Outlier detection in attribute vectors (§3.4). | — | S | parked | |
| AI1o | (deferred) Search-rank learn-to-rank (§3.6) — gated on having scale. | — | L | parked | |
| AI1p | Per-provider OpenAPI nuances (Azure deployment URL paths, Anthropic `messages` adapter, etc.). Ships incrementally as users hit them. | — | S each | gated on AI1a | Renamed from "deferred hosted-model bridge"; now a continuous compatibility track. |
| FS1 | File storage backend pluggability (GridFS → S3 evaluation) — umbrella | issue #27, user request | M-L | **design done** | `aidocs/45-gridfs-to-s3-evaluation.md` + `aidocs/47 §3.2`. **Per user direction (2026-05-12): both GridFS and S3 ship as first-class plugins, indefinitely supported, picked per-install — GridFS is _not_ a deprecation path.** Presigned-URL `/v2/` endpoints unblock the W1 wins (frontend uploads, RO-Crate delivery, long-running results) without forcing migration. Closes #27. Sub-IDs FS1a-FS1h below. |
| FS1a | `shepard-plugin-file-gridfs` first-class plugin (pure refactor extracting `FileService` into `FileStorage` SPI; behaviour-equivalent). Tests pin existing behaviour. | — | M | queued | Behaviour-preserving; ships independently. Plugin shape per `aidocs/47 §2`. |
| FS1b | `shepard-plugin-file-s3` plugin using AWS SDK v2 + endpoint-override config for MinIO. New `shepard.payload.file.backend = gridfs\|s3` config key + plugin-registry selector. Backend-proxied path works against both plugins. | — | M | gated on FS1a | |
| FS1c | Presigned-URL `/v2/` endpoints (`POST /v2/files/{containerAppId}/upload-url`, `GET /v2/files/{appId}/download-url`). Returns 404 when backend doesn't support presigned URLs. | — | S | gated on FS1b | |
| FS1d | MinIO sidecar in `infrastructure/docker-compose.yml` under `files-s3` profile (off by default; mirrors `spatial`/`hdf` patterns). One-line operator switch. | — | S | gated on FS1b + `aidocs/22 §4.6a` profile-bound toggles | |
| FS1e | `shepard-admin files migrate` CLI command (big-bang + background-sweep modes), progress via P3 pattern. | — | M | gated on FS1a + FS1b + `aidocs/22` | Two-phase migration runway from `aidocs/45 §6`. |
| FS1f | Frontend update — large-file uploads use `/v2/upload-url` presigned path when available, fall back to backend-proxied. | — | M | gated on FS1c + frontend changes | Closes the P12 chunk of `aidocs/33`. |
| FS1g | RO-Crate export delivery (`aidocs/31 §O3`) returns presigned URLs when backend = s3. | — | S | gated on FS1c + `aidocs/31` | Closes the O3 open question. |
| FS1h | (deferred) Per-Collection storage choice — only on real demand. | — | L | parked | Architecture C from `aidocs/45 §3.3`. |
| PV1 | Payload versioning — umbrella | user request, `aidocs/46` | M-L | **design done** | `aidocs/46-payload-versioning-design.md`. Extends V2 (`aidocs/41`) entity-revision snapshots down to payload bytes. SHA-256 dedup; per-Collection retention policy; FS1 S3 backend is the cheapest impl. Sub-IDs PV1a-PV1h below. |
| PV1a | `PayloadVersion` Neo4j sub-node + `payloadVersion` counter on `FileReference`. `V19__Add_appId_constraint_PayloadVersion.cypher` + `V20__Backfill_initial_payload_version.cypher`. Read path `?payloadVersion=N`; write path with SHA-256 dedup. **`FileReference` only** in this slice. | — | M | queued (FS1a recommended first for easier impl) | |
| PV1b | Same shape applied to `StructuredDataReference`. | — | M | gated on PV1a | |
| PV1c | Same shape applied to `SpatialDataReference` (PostGIS `version_id` column on row groups). | — | M | gated on PV1a | |
| PV1d | `TimeseriesReference` re-ingest flow + version-aware reads. Separate from append-only writes which keep flowing into latest. | — | M-L | gated on PV1a + careful design review | The murkiest semantics; intentionally last. |
| PV1e | V2 snapshot extension — `SnapshotEntry.payloadVersion` field; pinned reads resolve dual-axis. | — | S | gated on PV1a + V2b | Unlocks byte-identical reproducibility. |
| PV1f | RO-Crate export pins `payloadVersion` automatically when `?snapshot=` is set; cites SHA-256 in the manifest. | — | S | gated on PV1a + V2d + `aidocs/31` | The headline reproducibility payoff. |
| PV1g | Per-Collection retention policy (`Collection.payloadRetentionPolicy`) + `shepard-admin payloads gc` CLI. | — | M | gated on PV1a + `aidocs/22` | |
| PV1h | (deferred) Per-version permissions — today's perms inherit from reference. | — | L | parked | |
| PL1 | Storage-backend plugin SPI — umbrella | user request, `aidocs/47` | L | **design done** | `aidocs/47 §2`. New `PayloadKind` + `PayloadStorage` SPI; existing kinds migrate gradually; new kinds (HDF5, Git, future) ship as plugins from day 1. Sub-IDs PL1a-PL1g below. |
| PL1a | `PayloadKind` + `PayloadStorage` SPI interfaces in `backend/.../spi/payload/` + `PayloadKindRegistry`. **No** existing kind refactored yet. | — | M | queued | Pure introduction; behaviour-preserving. |
| PL1b | Pilot migration: `shepard-plugin-spatial-postgis`. Behaviour-identical with today's spatial feature toggle. | — | M | gated on PL1a | Smallest existing surface; A3c feature toggle already in place. |
| PL1c | A5a (HDF5/HSDS, `aidocs/35`) ships as a plugin from day 1. | — | M | gated on PL1a + DX3 + `aidocs/35` | First net-new plugin. |
| PL1d | G1a (Git, `aidocs/38`) ships as a plugin from day 1. | — | M | gated on PL1a + DX3 + `aidocs/38` | Second net-new plugin. |
| PL1e | (deferred) Refactor `file` payload kind to the SPI; lands the FS1 GridFS/S3 plugin split. | — | L | parked (post FS1) | |
| PL1f | (deferred) Refactor `structured` payload kind to the SPI. | — | M | parked | |
| PL1g | (deferred) Refactor `timeseries` payload kind to the SPI. | — | L | parked | More entanglement with `AnnotatableTimeseries` + hypertable specifics. |
| DX1 | Unified `ShepardTestStack` test-resource (Postgres + Mongo + Neo4j + Influx + mock OIDC via testcontainers). Unblocks `*QuarkusTest` reliability without `infrastructure-local`. | — | M | queued | Resolves the long-standing baseline failure trail. |
| DX2 | `ShepardTestFixtures` shared helpers (typed builders for Collection / DataObject / fired-Run shapes). | — | S | **✓ shipped** | Shrinks Mockito boilerplate in `*ServiceTest`. Fluent builders for `Collection` / `DataObject` / `User` / `Permissions` / `BasicReference` (one canonical reference type — pattern set for the rest) at `backend/src/test/java/de/dlr/shepard/testing/fixtures/ShepardTestFixtures.java`; defaults give a fresh `appId`, unique `id` / `shepardId`, non-null `name`, and a deterministic `createdAt`. Pilot adopters: `CollectionServiceTest`, `DataObjectServiceTest`, `BasicReferenceServiceTest` (~90 LOC of repetitive `new Collection() {{ setX(); setY(); ... }}` removed across the three). Self-test at `ShepardTestFixturesTest`. |
| DX3 | `mvn shepard:scaffold-payload-kind` archetype. | — | M | gated on PL1a | Codegen for new payload-kind plugins. |
| DX4 | `make dev` single-command bootstrap (init wizard + compose up + smoke-test). | — | S | gated on DX1 + `aidocs/22` | Casual-user path: clone-and-run in one line. |
| DX5 | Quarkus Neo4j Dev Service; OpenAPI hot-reload across `clients/*`. | — | M | queued | |
| DX6 | RFC 7807 errors everywhere (== existing H4). | — | M | queued | Existing item. |
| DX7 | `GET /v2/admin/features` + `shepard-admin features list` showing every toggle's source. | — | S | queued | |
| DX8 | BI integrations — Grafana data source plugin for shepard timeseries; Superset SQLAlchemy URI recipe (post P10); Tableau / PowerBI recipes by request. | — | M | gated on `aidocs/29` P10a (the SQL win — C5 cleared) + DX5 | The power-user dashboarding answer that complements `aidocs/43 §5.8` snap dashboards. |
| N1 | Internal semantic repository via neosemantics — umbrella | user request, `aidocs/48` | M-L | **design done** | `aidocs/48`. n10s plugin in Neo4j → new `SemanticRepositoryType.INTERNAL`. Pre-seeded common ontologies bundled. Closes the casual-user "I shouldn't need a triple store" friction. Sub-IDs N1a-N1g below. |
| N1a | n10s plugin in Neo4j compose service; `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector`; n10s bootstrap startup hook (post-A1e); `WHERE NOT n:Resource` audit on shepard's Cypher writes. | — | M | queued | A1e fail-fast already shipped. |
| N1b | Pre-seeded common ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL) bundled at `backend/src/main/resources/ontologies/`. SHA-256 pinning. Idempotent re-import. | — | M | gated on N1a | ~13 MB total. |
| N1c | `shepard-admin semantic refresh-ontologies` CLI per `aidocs/22 §4.x`. | — | S | gated on N1a + `aidocs/22` | |
| N1d | LUMEN seed integration — `lumen-phases.ttl` + `lumen-severity.ttl` + `import_ontologies.py`; replace placeholder IRIs in seed.py with real ontology IRIs; new SPARQL cell in anomaly-analysis.ipynb. | — | S | gated on N1b | The story-telling slice. |
| N1e | Frontend annotation picker shows pre-seeded ontology terms by default; couples to `aidocs/14` term-search facet. | — | M | gated on N1a + `aidocs/14` | Casual-user UX close. |
| N1f | (optional) `/v2/semantic/{repoAppId}/sparql` proxy endpoint that wraps n10s SPARQL with shepard auth. | — | M | queued | Operator-config alternative if not done. |
| N1g | (deferred) Reasoner integration — pure Cypher inference rules for SKOS broader-narrower; no RDFS/OWL reasoner. | — | L | parked | |
| D1 | In-app user docs — umbrella | user request, `aidocs/49` | M-L | **design done** | `aidocs/49`. Frontend `/help` route serves `docs/*.md` rendered; Playwright pipeline captures against a CI-booted compose stack and commits PNGs to `docs/assets/screenshots/`. Sub-IDs D1a-D1g below. |
| D1a | Frontend `/help` route + `HelpFrame.vue` + build-time copy of `docs/_site/` to `frontend/public/help/`. Top-nav "Help" link. | — | M | queued | Self-contained; works offline; same origin. |
| D1b | Playwright spec + `.github/workflows/screenshot-pipeline.yml` + `marker-routes.ts`. First capture against a CI-booted compose stack; replaces placeholder markers in `docs/showcase.md`. | — | M | gated on D1a | The pipeline that's been waiting on a CI-booted stack. |
| D1c | Task-shaped help pages — `docs/help/upload-data.md`, `share-collection.md`, `export-rocrate.md`, `process-step.md`. | — | S each | gated on D1a | Casual-user-task-shaped, complements feature-shaped `docs/user-guide.md`. |
| D1d | Version stamping — `docs/_site/version.json` + frontend version display + footer "Help for shepard X.Y" + build-time check vs `pom.xml` `<revision>`. | — | S | gated on D1a | Closes version-drift gap. |
| D1e | Per-page "Was this helpful?" telemetry — anonymous, opt-out, summary stats only. | — | M | gated on D1a + privacy review | |
| D1f | (deferred) Multilingual docs — `docs/de/*.md`, picker driven by `aidocs/36 §3.2 language` setting. | — | L | parked | |
| D1g | (deferred) Inline contextual help — every page in shepard has a "?" icon that pops the relevant help section. | — | M | parked | Phases into the casual-user UX after D1c content lands. |
| EXP1 | Experiment orchestration — umbrella | user request, `aidocs/50` | XL | **design done** | `aidocs/50`. New `shepard-experiment-coordinator` service drives PLC/SPS/KUKA experiments end-to-end; auto-materialises shepard graph; three timing strategies (preseed / JIT default / post-process); checkpoint via V2 snapshots; restart-whole + restart-at-step. EXP1a-EXP1n sub-IDs below. |
| EXP1a | Coordinator service skeleton — Quarkus app, file-based recipes, drives `/v2/process-runs/...` (PR1) + `/v2/<kind>-references/...`. JIT mode only; manual triggers only. | — | L | gated on PR1b | Gate for everything else; alone delivers no value — EXP1b+EXP1c unlock the manufacturing-environment integration. |
| EXP1b | OPC/UA trigger subscription — real PLC events drive trigger evaluator. Reuses `Eclipse Milo` via sTC's source-layer internals. | — | M | gated on EXP1a | |
| EXP1c | sTC sink integration — telemetry from sTC routes to coordinator-managed TimeseriesReference OIDs of the active step. | — | M | gated on EXP1a + sTC i1 (NDJSON ingest, shipped via P14) | |
| EXP1d | Pre-seed mode — recipe declares full graph upfront. | — | M | gated on EXP1a | |
| EXP1e | Post-process mode — staging-bucket walk + ingest endpoint. | — | M | gated on EXP1a + FS1 (S3 staging) | |
| EXP1f | Checkpoint + V2 snapshot integration. | — | M | gated on EXP1a + V2b | |
| EXP1g | Restart-whole + restart-at-step. | — | M | gated on EXP1f | |
| EXP1h | KUKA OPC/UA trigger integration. | — | S | gated on EXP1b | |
| EXP1i | KUKA RSI telemetry routing via sTC's RSI source. | — | S | gated on EXP1c + sTC RSI source | |
| EXP1j | Operator UI — web, embedded in shepard's frontend `/experiments` route. Live state, telemetry sparklines, checkpoint history, restart controls. | — | L | gated on EXP1a + frontend | |
| EXP1k | Recipe storage in shepard's `__templates` Collection with `templateKind = "EXPERIMENT_RECIPE"`. Operator picks recipes from the existing template-picker UI. | — | M | gated on EXP1a + T1b | |
| EXP1l | Modbus / REST source integration via sTC i9. | — | S | gated on sTC i9 | |
| EXP1m | (deferred) PLC writeback — coordinator writes setpoints to PLC. Audit-logged; recipe-authorisation-gated. | — | M | parked until safety review | |
| EXP1n | (deferred) Multi-coordinator coordination across synchronised stations. | — | L | parked | |

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
| R2 | Per-payload selective RO-Crate export (refinement of existing export) | 8381 | S–M | **Phase 1 done** | Round 6, commit `be0eb26` (cherry-picked). Existing exporter lives in `backend/src/main/java/de/dlr/shepard/context/export/` (`ExportService` is the walker entry point, `ExportBuilder` wraps `edu.kit.datamanager.ro_crate.RoCrate.RoCrateBuilder`). Phase 1 adds an optional `ExportSelection` body on a **new** sibling `POST /collections/{collectionId}/export` (the existing GET is preserved unchanged for byte-identical legacy behaviour). Selection has two sections: `payloads` (`include` allow-list of kinds + `excludeIds` deny-list) and `metadata` booleans (permissions / annotations / labJournal / versions / subscriptions). The selection actually applied is recorded as a JSON block on the root data entity in `ro-crate-metadata.json` so consumers can tell what was excluded. 18 tests pass (5 new selection-service + 2 new manifest-injection + 11 pre-existing). **Important:** the exporter today does **not** yet emit `permissions / versions / subscriptions / annotations` documents — the booleans are wired and recorded but bundling those metadata kinds is deferred to **R2d**. |
| R2b | File- and column-level selection on payloads (per-FileReference oid include list; per-TimeseriesReference column / time-window picks) | — | M | done | Round 6, commit `60a3ea1`. Adds `payloads.perPayload: Map<entityId, PerPayloadSelection>` and `payloads.strictPerPayload: boolean`. `PerPayloadSelection` has optional `fileOids`, `columns`, `timeRange{start,end}` — kind-applicability is silent (wrong-kind entries ignored). Existing per-OID file-fetch service reused; existing `fieldFilterSet` parameter on `TimeseriesReferenceService.exportReferencedTimeseriesByShepardId` covers per-column projection; a new 14-arg overload accepts `startNanosOverride` / `endNanosOverride` for time-window without mutating the reference. Stale OIDs / unknown columns silently skipped + recorded in a new `selection.warnings[]` array on the manifest's root selection block; `strictPerPayload=true` flips that to 400. 11 new tests pass (43/43 export-package). P17b lint stays green. |
| R2c | Per-payload metadata-field redaction (e.g. include the annotation but redact its label) | — | M | done | Round 6, commit `f993e8b`. Adds `metadata.redactFields: Set<RedactableField>` to `ExportSelection`. **Closed enum** of 6 fields, each verified against the actual IO type before shipping (one stop-and-report adjustment): `PERMISSION_USERNAME` (replaces `owner` + `reader[]` + `writer[]` + `manager[]`), `PERMISSION_GROUP_IDS` (empties `readerGroupIds` / `writerGroupIds` — renamed from proposed `…_GROUP_NAME` because `PermissionsIO` carries group ids only), `ANNOTATION_LABEL` (replaces `propertyName` — proposed `label` doesn't exist; IRI left intact as stable id), `ANNOTATION_VALUE` (replaces `valueName` — proposed `value` doesn't exist), `VERSION_AUTHOR` (replaces `createdBy` — no `author` field), `LAB_JOURNAL_CONTENT` (replaces `journalContent`). Mutation is direct setter on the Lombok `@Data` IO types before serialisation. Manifest selection block records the **set** but not the replacement values (no leak vector). 11 new tests pass; 54/54 export-package total. |
| R2d | Emit permissions / versions / subscriptions / semantic-annotations documents from the exporter (the metadata booleans from R2 Phase 1 are wired but the exporter currently doesn't bundle these doc kinds) | — | M | **mostly done** | Round 6, commit `d5fa03c`. **3 of 4 kinds wired:** `permissions` (via `PermissionsService.getPermissionsOfEntityOptional`), `versions` (via `VersionService.getAllVersions`; collection-only — only Collections are versioned), `annotations` (via `BasicEntity.getAnnotations()`). Defaults remain `false` so today's behaviour is unchanged. 14 new tests pass on merged branch (10 service-level in `ExportMetadataBundleTest`, 4 builder-level in `ExportBuilderSelectionTest`). **`subscriptions` deferred** — see **R2d2** below. |
| R2d2 | Emit per-entity subscriptions in the RO-Crate export — needs a URL-pattern → entity-URL matcher because subscriptions in shepard are URL-pattern-based, not per-entity-attached | — | M | done | Round 6, commit `967fbf5`. New `SubscriptionMatcher` extracted from `SubscriptionFilter` (pure refactor; `SubscriptionFilterTest` 7/7 pass); new `EntityUrlSynthesiser` produces canonical paths for Collection / DataObject / BasicReference / LabJournalEntry using the same `Constants.*` tokens the JAX-RS resources use. Walker hook calls `subscriptionService.getMatchingSubscriptionsForUrl(url, method)` and writes `<id>-subscriptions.json` per entity. **Documented trade-off:** subscription patterns are full-URL regexes (scheme + host); export only knows the path, so authors of host-anchored patterns may need a `.*` prefix to also match exports. 7 new tests pass; 43+ on merged branch. |
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
| F8 | Configurable OIDC roles-claim path (`shepard.oidc.roles-claim-path`, default `realm_access.roles`); `JWTFilter` walks the dot-path so non-Keycloak IdPs (Pocket ID, Authentik, Azure AD) can carry roles in their native claim shape | XS-S | **done** | Bundled with **A0** (`2901834`). `shepard.instance-admin.role` (default empty = no IdP-side admin) names the role-string within the claim that maps to `instance-admin`. CONFIG-status row in `aidocs/34`. |

### Parked (out of repo or superseded)

| ID | Item | input_raw refs | Why parked |
|---|---|---|---|
| X1 | M1–M9 dataship milestones (IDP, async+S3, granular selection, approval, RO-Crate, joint pubs, back-ref) | 975–1214 | Different repo (`shepard-dataship`) |
| X2 | HDF5/HSDS support, AAS integration, table store | 697–712 | Multi-repo / external integration; needs scoping. **HDF5/HSDS slice now has an implementation design** — see `aidocs/35-hdf5-hsds-implementation-design.md`; **A5** series queued (in-scope, not parked); AAS / table store still parked. |
| X3 | Federation, HMC 2, project website | 8176–8400 | Program-level |
| X4 | Node-RED file collector flow (incl. JWT tokens) | 100–660 | Reference material; security follow-up below |

## Open user decisions (blocking some items)

1. **Leaked JWTs** at `aidocs/input/input_raw.md:222` and `:360` (issuers
   `bt-au-cube2.intra.dlr.de`, `bt-au-cube3.intra.dlr.de`, sub `kreb_fl`).
   Already on `origin`. Need: rotate tokens, decide whether to redact in
   the file, and whether to rewrite history.
2. ~~**API versioning (P4)** is breaking — confirm strategy
   (`/shepard/api/v1` prefix, dual-serve, deprecation window) before
   dispatching.~~ — **resolved**: shipped non-breaking. Upstream
   `/shepard/api/...` stays byte-frozen via explicit class-level
   `@Path("/shepard/api/...")` prefix; new fork-additive endpoints land
   under `/v2/` (per `CLAUDE.md` API-version policy). User-facing URLs
   unchanged.
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

## New designs landed in the 2026-05-12 batch (post-A0)

Seven design docs landed (`aidocs/52`..`aidocs/58`), each with its own
phasing. Umbrella rows below; per-doc sub-ID detail lives in the cited
aidoc. **`T1` is hereby redirected to the new `aidocs/54` shape**
(`:ShepardTemplate` first-class entity), superseding the older
`aidocs/39` hack; the old T1 sub-IDs T1a–T1h are renumbered per
`aidocs/54 §9`. **`D1` in `aidocs/58 §5` is renamed `CP1`** to avoid
colliding with the existing in-app-docs D1 series. **`AI1` in
`aidocs/58 §8` is renamed `GR1`** (GraphRAG) to avoid colliding with
the existing `aidocs/43` AI1 umbrella.

| ID | Slice | Source | Size | Status | Notes |
|---|---|---|---|---|---|
| AAS1 | AAS backend integration — umbrella | user request, `aidocs/52` | XL | **design done** | `aidocs/52`. Adapter shim at `/v2/aas/...` (recommended v1); Submodel-only payload-kind plugin fall-back; full Type-2 server parked. AAS1a tiny first slice = `GET /v2/aas/shells` listing readable Collections as minimal Shells (gated on L2d). Conformance targets IDTA Nameplate + TechnicalData + TimeSeriesData. AAS1a–AAS1k + AAS1-{reg,well-known,fed,mdns,edc} for external discovery (`aidocs/52 §4a`). |
| AAS1-reg | Outbound registration at an external IDTA AAS Registry (BaSyx / FA³ST recommended) — sync-on-start + per-write outbox + admin `POST /v2/admin/aas/registrations/sync` | user request 2026-05-12, `aidocs/52 §4a.1` | M | **design done** | Config `shepard.aas.registry.{url,api-key,sync-on-start}`. `:AasRegistration` outbox entity tracks per-target state. Failures are observability-only — never block shepard writes. |
| AAS1-well-known | `GET /v2/aas/.well-known/aas-server` self-description (zero-discovery option) — emits `enabled` / `aasApiProfile` / `endpoints` / `supportedSubmodelTemplates` / `shellCount` / `registryRegistrations`. Unauthenticated (capability flags + counts only, never per-Shell identifiers); listed in `PublicEndpointRegistry`. `supportedSubmodelTemplates` pulled from `ShepardTemplate` rows with `templateKind=AAS_SUBMODEL_TEMPLATE` (non-retired); `shellCount=0` and `registryRegistrations=[]` until AAS1a / AAS1-reg land. | user request 2026-05-12, `aidocs/52 §4a.5` | S | **done** | 12 new tests (`AasServerSelfDescriptionServiceTest` 8 + `AasWellKnownRestTest` 1 + 3 `PublicEndpointRegistryTest` extensions covering the `.well-known` segment + prefix-collision). New config keys: `shepard.aas.enabled` (default `false`) + `shepard.aas.api-profile` (default `Submodel-Repository-Read-3.1`). |
| AAS1-fed | Parent-repository federation (`shepard.aas.parent-repository.url`) — shepard registers as a leaf | user request 2026-05-12, `aidocs/52 §4a.2` | M | **design done** | Reuses AAS1-reg's outbox / retry plumbing. Queued. |
| AAS1-mdns | mDNS / DNS-SD opportunistic LAN discovery via JmDNS — folded into `aidocs/60` EDGE1g for shepard-Edge | user request 2026-05-12, `aidocs/52 §4a.4` | S | **design done** | Opt-in (`shepard.aas.mdns.enabled=false` default); ships with shepard Edge. |
| AAS1-edc | (parked) Eclipse Dataspace Connector publish — Catena-X / IDS / Mobility Data Space | user request 2026-05-12, `aidocs/52 §4a.3` | M | **design hook only** | Waits for the first dataspace deployment to drive concrete config. |
| FB1 | FileReference → **FileBundle** rename + `FileGroup` sub-node grouping | user request, `aidocs/53` | M-L | **design done; FR1a shipped** | `aidocs/53`. Internal rename + `/v2/`-surface only; upstream wire shape stays frozen. `FileGroup` between `FileBundle` and `ShepardFile` carries grouping metadata (camera cyclic-capture use case). FR1a (this PR) shipped: V21 + V22 migrations, rename to `FileBundleReference`, default-group auto-create on every new bundle, full `/v2/bundles/{appId}/{groups,groups/{groupAppId},groups/{groupAppId}/files}` REST surface, 81 new tests, JaCoCo + SpotBugs green. FR1b (singleton `FileReference` reintroduction + `/v2/files/...` shelf + V16b opt-in carve-out per `aidocs/53 §1.8`) queued; FR1c (snapshot/payload-version) and FR1d (RO-Crate updates) follow. |
| FB1a-fileSize | `fileSize Long` field on `:ShepardFile` (Mongo + Neo4j round-trip via OGM), captured at upload time from `GridFSBucket.find(...).getLength()`. Pre-FB1a rows stay `null` until re-uploaded — no V19 backfill (file blobs live in Mongo/GridFS, not Neo4j; re-reading every blob to backfill is heavy and offers little payoff for a casual-install fork). Unblocks PROV1-content-stats-2 (byte totals on the dashboard) and FB1b/c (size-aware grouping policies). | user request "improve metric calculation", `aidocs/53` | S | **done** | 4 new tests (`ShepardFileTest` round-trip + `FileServiceTest` create-with-size + legacy-null read-back + missing-GridFS-defensive). OpenAPI exposes `fileSize` as `readOnly` `nullable` Long. |
| VID1 | Video payload kind — dedicated PayloadStorage plugin | user request, `aidocs/53` | XL | **design done** | `aidocs/53`. Segments + HLS manifest on object store; live ingest via sibling `shepard-video-collector` (sVC) or MediaMTX sidecar; navigation by video-time + wall-clock; ffmpeg / frame-extract gated behind a feature flag, deferred to VID1d. VID1a–VID1f. |
| T1 (revised) | Templates as first-class admin entity | user request, `aidocs/54` (supersedes `aidocs/39`) | L | **T1a shipped; T1b-g queued** | `aidocs/54`. `:ShepardTemplate` Neo4j entity (admin-only subgraph) replacing the `__templates` hack. Body = JSON DSL (inert at rest); versioning = copy-on-write; `:USES_TEMPLATE` + `:ALLOWS_TEMPLATE` edges; `/v2/templates/...` REST surface, all admin endpoints gated `@RolesAllowed("instance-admin")`. Recommended order: **CLI first, admin page second**. T1a–T1g. |
| T1a-aidocs/54 | `ShepardTemplate` entity (HasAppId; name / templateKind / version / body / description / tags / createdBy / createdAt / updatedAt / retired) + `ShepardTemplateDAO` (`findByAppId` / `findLatestByName` / `list` filtering retired by default / `nextVersionOf` copy-on-write helper) + `V18__Add_appId_constraint_ShepardTemplate.cypher` | user request, `aidocs/54 §9` | M | **done** | 8 new tests (`ShepardTemplateTest`). REST surface lands in T1b. |
| T1b | `GET /v2/templates` (list; auth'd; `includeRetired` admin-only) + `GET /v2/templates/{appId}` (read; auth'd) + `POST /v2/templates` (mint v1; admin) + `PATCH /v2/templates/{appId}` (copy-on-write: retires prior, mints v+1; admin) + `DELETE /v2/templates/{appId}` (soft-delete via `retired=true`; admin) | user request, `aidocs/54 §5` | S-M | **done** | 13 new tests (`ShepardTemplateRestTest`). createdBy / createdAt / updatedAt stamped server-side. |
| T1c-aidocs/54 | `:USES_TEMPLATE` (provenance — "this Collection was created from this template") + `:ALLOWS_TEMPLATE` (owner-curated allowed-set) edges. New `ShepardTemplateDAO.listAllowedForCollection` / `listUsedByCollection` / `setAllowedForCollection` / `recordUsage`. REST: `GET /v2/collections/{appId}/templates/{allowed,used}` (Read on Collection) + `PUT /v2/collections/{appId}/templates/allowed` (Manage). | user request, `aidocs/54 §3` | M | **done** | 9 new tests (`CollectionTemplatesRestTest`). `:USES_TEMPLATE` edge writing wired in DAO; called by T1c-instantiate below. |
| T1c-instantiate | `POST /v2/collections/{appId}/templates/from/{templateAppId}` — records the `:USES_TEMPLATE` edge (idempotent) and returns the template body for client-side JSON DSL interpretation. Requires Write on Collection; template must be non-retired. | user request, `aidocs/54 §5` | S | **done** | 6 new tests; new `ShepardTemplateDAO.recordUsageReportingCreation` reports whether the edge was newly created vs already-existed. Server-side body interpretation deferred to T1c-apply (queued). |
| T1d-aidocs/54 | `TemplateBodyValidator` — JSON DSL schema check on POST `/v2/templates` and PATCH `/v2/templates/{appId}`. Body must parse as JSON, top-level must be object, expected keys per `templateKind` (`collection` / `dataobjects` / `experiment` / `submodel` / ...). Open for plugin-supplied extensions (unknown keys tolerated v1). Maintainer locked **JSON DSL** for v1 (2026-05-12). | user request, `aidocs/54 §7` (decision locked) | S-M | **done** | 13 new tests (`TemplateBodyValidatorTest` 12 + 3 new in `ShepardTemplateRestTest`). Invalid body → 400 `{ "errors": [...] }`. Unknown templateKinds remain permissive (any well-formed JSON object passes). |
| PROV1 | PROV-O provenance + activity dashboard | user request, `aidocs/55` | L | **PROV1a shipped; PROV1b-g design done** | `aidocs/55`. `:Activity` Neo4j entity (`HasAppId`); JAX-RS request filter on mutating endpoints; service-layer hook for non-REST flows; reads opt-in. Casual-user sparkline dashboard (per-Collection + instance-admin scope). PROV-N JSON export. 2-year retention default. PROV1a–PROV1g. |
| PROV1a | `:Activity` Neo4j entity + `ActivityDAO` + `ProvenanceService` + `ProvenanceCaptureFilter` (JAX-RS request+response filter) + `GET /v2/provenance/{activities,count}` + `V15__Add_appId_constraint_Activity.cypher` | user request, `aidocs/55 §9` | M | **done** | 20 new tests (1234 → 1254 total); coverage gate met. Captures POST/PUT/PATCH/DELETE on 2xx by default; reads opt-in via `shepard.provenance.capture-reads=false`. Casual users see only their own rows; instance-admins see all. New config: `shepard.provenance.{enabled,capture-reads}`, `shepard.instance.id`. |
| PROV1b | Per-entity provenance trail — `GET /v2/provenance/entity/{appId}` + heuristic target-extraction in the capture filter (`TargetEntityResolver` finds the last UUID segment of the request path and maps the preceding plural to a Neo4j label kind) | user request, `aidocs/55 §6` | M | **done** | 11 new tests (TargetEntityResolverTest 10 + filter test row). PROV-O `:USED`/`:GENERATED` edges deferred to PROV1b2 — `targetAppId` on the `:Activity` is enough for the v1 query. Honours V2S1a `?profile=`. |
| PROV1c | On-demand stats aggregation + `GET /v2/provenance/stats?scope=instance\|collection\|user&id=&since=&until=` (totals + sparkline buckets + action-kind histogram + cumulative integral in one payload; auto daily-/weekly-bucket switch at 90-day windows; **single Cypher round-trip** via `ActivityDAO.aggregateStats`) | user request, `aidocs/55 §6` + user follow-up "improve metric calc perf + add cumulative integral" | M | **done** | 12 new tests. Single-pass aggregation (one Cypher → buckets + per-kind totals + distinct-agent count in Java); cumulative integral (running sum) emitted alongside buckets. CodeQL-flagged underflow risk in user-supplied since/until fixed via clamp. Pre-aggregated `:ActivityRollup` deferred to PROV1c2. |
| PROV1c2 | Pre-aggregated `:ActivityRollup` daily/weekly buckets (only if PROV1c per-request aggregation gets slow on big installs) | — | M | parked | gated on observed need; PROV1c shipped at on-demand speed. |
| PROV1c-acl | Per-Collection Read-permission gate on `GET /v2/provenance/stats?scope=collection` — non-admins must hold Read on the target Collection (admins bypass; missing Collection → 404) | `aidocs/55 §6` | S | **done** | Resolves Collection appId → OGM id via `CollectionPropertiesDAO.findCollectionIdByAppId` then `PermissionsService.isAccessTypeAllowedForUser`. Same path CP1b takes. |
| PROV1-content-stats | `contentCensus` field on `/v2/provenance/stats` — at-query-time entity counts (`dataObjects` / `fileReferences` / `timeseriesReferences` / `structuredDataReferences` / `spatialDataReferences` / `labJournalEntries`) per scope. Drives the casual-user dashboard's "what's in here" tiles next to the activity sparkline. Null for `scope=user`. | user request 2026-05-12, `aidocs/55 §5` | S-M | **done** | New `ContentCensusDAO` (single Cypher per scope; aggregates entity counts in one round-trip). 3 new tests. Byte-size totals deferred to PROV1-content-stats-2 (gated on FB1a's stored `fileSize` field). |
| PROV1-content-stats-2 | `byteTotals` field on `/v2/provenance/stats` — at-query-time byte sums by storage kind. v1 ships `fileBytes` (sum of `ShepardFile.fileSize` reachable from the scope). Pre-FB1a rows with `fileSize=null` contribute 0 to the sum — the total is a **lower bound** until those rows are re-uploaded; surfaced as a caveat on the wire schema. Null for `scope=user`. Open-shape: future keys (`timeseriesBytes`, `structuredDataBytes`) get appended without changing schema. | follows from PROV1-content-stats + FB1a-fileSize | S | **done** | New `ContentCensusDAO.byteTotalsForCollection` / `byteTotalsInstanceWide` (single Cypher per scope; `coalesce(sum(coalesce(f.fileSize, 0)), 0)` for null-tolerance). 3 new `ProvenanceStatsServiceTest` rows (collection / instance / user-null). |
| PROV1d | Frontend per-Collection sparkline dashboard (Vue + Chart.js or vanilla SVG) | user request, `aidocs/55 §5` | M-L | queued | gated on PROV1c. |
| PROV1e | Instance-admin all-instance dashboard | user request, `aidocs/55 §5` | M | queued | gated on PROV1d + `aidocs/51 §9.5` admin-page strip. |
| PROV1f | Nightly retention TTL job (`shepard.provenance.retention-days=730`; `@Scheduled` cron `0 42 3 * * ?`; negative value = keep-forever) | user request, `aidocs/55 §7` | S | **done** | `ProvenanceRetentionJob` calls `ActivityDAO.deleteOlderThan`. 6 new tests. New config key `shepard.provenance.retention-days=730`. |
| PROV1g | PROV-N JSON export (`Accept: application/prov+json`; W3C PROV-JSON subset with `activity` / `agent` / `entity` / `wasAssociatedWith` / `used` / `wasGeneratedBy` blocks) | user request, `aidocs/55 §6` | S | **done** | `ProvJsonRenderer` + JAX-RS content-negotiation on `/v2/provenance/{activities,entity/{appId}}`. 11 new tests. |
| MNT1 | shepard mount as a network drive — umbrella (read-only WebDAV via Apache Milton) | user request 2026-05-12, `aidocs/61` | XL | **design done** | `aidocs/61`. WebDAV at `/v2/webdav/...`; FileBundle/FileGroup → directory tree; synthetic per-entity files (`_README.md` / `_metadata.json` / `lab-journal.md` / `provenance.json`); CSV-default for timeseries. One-shot mount-credentials UX via `POST /v2/me/mount-credentials`. Per-Collection `webdavVisible` opt-out via `:CollectionProperties`. Default-off feature toggle. MNT1a–MNT1i. |
| V2S1 | v2 API simplification + output profiles + MCP-friendly OpenAPI | user request, `aidocs/56` | M-L | **V2S1a shipped; V2S1b-f queued** | `aidocs/56`. (a) Flat `/v2/dataobjects/{appId}` single-entity paths; bulk listings stay nested. (b) `?profile=metadata\|relations\|all` via Jackson views. (c) `x-mcp-tool-name` + `x-mcp-side-effects` OpenAPI extensions per operation. ArchUnit fence: `admin` side-effects ↔ `@RolesAllowed("instance-admin")`. V2S1a–V2S1f. |
| V2S1a | `OutputProfile` enum + `OutputProfileResolver` request-scoped bean + `OutputProfileFilter` JAX-RS filter (reads `?profile=metadata\|relations\|all` on `/v2/...` only; unknown → RFC 7807 400 with valid names listed). Demonstrated on `GET /v2/provenance/activities`. | user request, `aidocs/56 §3` | S | **done** | 17 new tests. `/shepard/api/...` ignores the parameter (frozen). |
| V2S1b | Apply the profile shape to every other `/v2/` IO record — refactor across PROV1b / CP1 / FB1 / VID1 surfaces as they land. ArchUnit fence: every `de.dlr.shepard.v2..` resource that returns a body must respect `OutputProfileResolver`. | user request, `aidocs/56 §3` | M | queued | gated on V2S1a (shipped). |
| CG1 | OpenAPI client-generator pick — Kiota primary, OpenAPI Generator secondary | user request, `aidocs/57` | M | **design done** | `aidocs/57`. Kiota for `/v2/`; OpenAPI Generator for the byte-frozen `/shepard/api/...`; Hey API as a TS-only tactical secondary for the Nuxt frontend. Commercial alternatives (Speakeasy / Stainless / Fern-commercial / Sideko) tour'd in §5. CG1a–CG1d. **Open question for maintainer: Kiota vs OpenAPI Generator on `/v2/` itself.** |
| UI1 | Lefthand-tree drag-and-drop (move default, copy on modifier) | user request, `aidocs/58 §2` | M | **design done** | `aidocs/58`. Backend `PATCH /v2/dataobjects/{appId}` accepts `parentAppId`; server-side cycle prevention. UI1a–UI1c. |
| UI2 | Navigable Collection graph view (cytoscape.js) | user request, `aidocs/58 §3` | M-L | **design done** | `aidocs/58`. Per-user layout persistence; entity-kind filtering. UI2a–UI2c. |
| UI3 | `@`-mention autocomplete for internal entity citations | user request, `aidocs/58 §4` | M | **design done** | `aidocs/58`. TipTap mention extension + `GET /v2/search?q=...&kinds=...` search endpoint; opaque `[entity:<appId>]` syntax; orphan-handling for deleted entities. UI3a–UI3c. |
| CP1 (was D1 in `aidocs/58`) | `:CollectionProperties` properties-node — folds template-info + default-FC-strategy + cross-cutting Collection config | user request, `aidocs/58 §5` | M | **CP1a shipped; CP1b-c queued** | `aidocs/58`. Idempotent migration `V##__Add_collection_properties.cypher`. CP1a–CP1c. |
| CP1a | `CollectionProperties` Neo4j entity + `CollectionPropertiesDAO` (`findByCollectionAppId` / `ensureFor` / `setWebdavVisible`) + V16 idempotent backfill + V16_R rollback + V17 appId-uniqueness constraint. `:HAS_PROPERTIES` constant added. | user request, `aidocs/58 §5` | S | **done** | 6 new tests. v1 fields: `webdavVisible` (default true, used by `aidocs/61` MNT1) + `defaultOntologyUri` (placeholder) + `uiDefaultsJson` (frontend-only). No REST surface yet; CP1b lands `GET/PATCH /v2/collections/{appId}/properties`. |
| CP1b | `GET /v2/collections/{appId}/properties` (read; requires Read on parent Collection) + `PATCH /v2/collections/{appId}/properties` (write; requires Manage) — surfaces `:CollectionProperties` via `/v2/` shelf | user request, `aidocs/58 §5` | S-M | **done** | 9 new tests (CollectionPropertiesIOTest 2 + CollectionPropertiesRestTest 7). Permission-check delegates to existing `PermissionsService.isAccessTypeAllowedForUser`. |
| CP1c | Wire `webdavVisible` into the MNT1 WebDAV mount path; Collection becomes invisible in the mount when set to false | user request, `aidocs/61 §13` | S | queued | gated on CP1a (shipped) + MNT1a (queued). |
| ONT1 | Add RO (Relation Ontology) to the pre-seeded bundle | user request, `aidocs/58 §6` | S | **design done** | `aidocs/58`. Adds `obo-relations.owl` to the `aidocs/48 §3.2` pre-seed table; SHA-256-pinned download in pom; LUMEN seed cites RO terms. ONT1a–ONT1c. |
| REF1 | DBpedia Databus rich-reference plugin (preview / description / title, 24h cached) | user request, `aidocs/58 §7` | M | **design done** | `aidocs/58`. Plugin = `shepard-plugin-ref-dbpedia-databus` per `aidocs/47 §2`. REF1a–REF1c. Off-by-default until v1. |
| GR1 (was AI1 in `aidocs/58`) | GraphRAG on shepard via native Neo4j 5.13+ vector index | user request, `aidocs/58 §8` | L | **design done** | `aidocs/58`. Embeddings per DataObject / Collection / lab-journal entry / Reference; similarity endpoint `GET /v2/search/similar?to=<appId>`; GraphRAG fusion (text-similar ∪ graph-neighbours) for `aidocs/43` LLM chat. BYOK + base-URL + model selection per `aidocs/43`. GR1a–GR1c. |
| BIZ1 | "odix / zeigen" InfPro use-case — placeholder | user request, `aidocs/58 §9` | ? | **design pending decision** | `aidocs/58`. Two candidate interpretations (public-display mode vs published-snapshot URL). **Needs maintainer clarification before dispatch.** |

| PERF1 | Out-of-the-box performance-metrics Grafana dashboard (`monitoring` compose profile, auto-provisioned datasource + dashboard) | user request | S | **done (this batch)** | `infrastructure/grafana/{provisioning,dashboards}/`. Brings `docker compose --profile monitoring up -d` to a working Grafana at `localhost:3001` with HTTP / JVM / DB / permission-cache panels. Documented in `docs/admin.md §Performance metrics`. |
| U1c2 | Role-in-current-context chip in page header (Owner / Editor / Reader + Instance Admin chip when applicable) | user request 2026-05-12, `aidocs/51 §9.4` | S | **U1c2-backend shipped; Vue chip queued** | `aidocs/51`. Backend: new `GET /v2/me/role-in/{collectionAppId}` returns `{ read, write, manage, isInstanceAdmin }`. Source-of-truth = existing `PermissionsService.getUserRolesOnEntity` + `SecurityContext.isUserInRole("instance-admin")`. 401/403/404/200 semantics mirror `CollectionPropertiesRest` for existence-protection. 9 new tests. Switched away from the `profile=relations`-on-other-endpoints design because the dedicated `/me/role-in/` endpoint is smaller per-page (single response) and cleaner than overloading every entity-GET with role data. Vue chip slice (U1c2-frontend) queued. |
| A3b1 | Admin-page metrics strip — server-side `/v2/admin/metrics-summary` (backend portion) | user request 2026-05-12, `aidocs/51 §9.5` | M | **A3b1 backend shipped; Vue cards queued** | `aidocs/51`. Backend reads in-process Micrometer registry + Java Runtime/ManagementFactory — no Prometheus HTTP dep, works regardless of `monitoring` compose-profile state. Payload covers JVM heap/uptime, HTTP req total + mean, permissions-cache hit ratio. Gated `@RolesAllowed("instance-admin")`. Embed-via-iframe is A3b2, deferred. 5 new tests. |
| PERF2a | k6 multi-stage stress script (5 stages, ramps to 50 VUs, exercises upload + ingest + search + permissions) | user request 2026-05-12, `aidocs/59 §3.2` | M | **design done** | `scripts/perf/k6-stress.js` (to write). Surfaces what the smoke misses (file-handle exhaustion, GC pause storms, Neo4j page-cache misses). Feeds the recommender (PERF2b). |
| PERF2b | Recommender — `scripts/perf/recommend.py` reads `last-run.json` + Prometheus instant queries → 3-5 ranked suggestions | user request 2026-05-12, `aidocs/59 §4` | M | **design done** | Rule catalogue (initial): JVM heap, GC, permissions-cache, Mongo latency, Hibernate batch, thread-pool, search. Each suggestion carries safe-to-apply boolean for the TUI step. |
| PERF2c | k6 soak (5 VUs × 2 h) + Prometheus remote-write integration so k6 runs show up as annotations in Grafana | user request 2026-05-12, `aidocs/59 §3.3` | S | **design done** | Off-by-default. Catches memory leaks / connection-leak / pool drift. |
| PERF2d | **Optional install-TUI auto-tune step** — run smoke, apply safe suggestions, re-run, verify delta | user request 2026-05-12, `aidocs/59 §5` | M-L | **design done** | Hooks into `aidocs/22` L1 Phase 1 admin-CLI. Opt-in. Backups `.env.orig` before applying. Safe-to-apply = pure env-var changes, reversible. |
| PERF3 | Weekly CI perf-smoke + auto-file issue on threshold miss | user request 2026-05-12, `aidocs/59 §6` | S | **design done** | `.github/workflows/perf-smoke.yml`. CI-booted compose stack (same shape as the `aidocs/49` screenshot pipeline). Off the critical path. |
| EDGE1 | shepard Edge — field-deployable offline-first instance — umbrella | user request 2026-05-12, `aidocs/60` | XL | **design done** | `aidocs/60`. Single-user-by-default; bootstrap-token + local instance-admin (no IdP). Sync = push-from-Edge over HTTP/2 to `POST /v2/edge/sync` (idempotent on `(originInstance, activityAppId)`); RO-Crate bundle export the always-available USB fallback. Conflicts surface as `:CONFLICTED_WITH` edges; central remains canonical. EDGE1a–EDGE1k. |
| EDGE1a | `infrastructure/edge/docker-compose.yml` + lightweight image variants + `shepard-edge up` one-command boot | user request, `aidocs/60 §8` | M | **design done** | First slice. Profiles `edge-micro` / `edge-workstation` / `edge-truck`. |
| EDGE1c | `POST /v2/edge/sync` central endpoint — streams graph-delta + payload bytes | user request, `aidocs/60 §5` | M-L | **design done** | Push-from-Edge model; idempotent on `(originInstance, activityAppId)`. |
| EDGE1d | Conflict-detection on import — `:CONFLICTED_WITH` edge on appId-collision | user request, `aidocs/60 §5` | M | **design done** | Central holds canonical; Edge variant becomes a sibling for operator triage. |
| EDGE1i | Air-gap install path — `docker save` images + import on Edge | user request, `aidocs/60 §9` | S | **design done** | For fully-disconnected Edge sites. |
