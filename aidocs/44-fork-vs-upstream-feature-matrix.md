# Fork vs Upstream — Feature Matrix

**Status.** **Live.** Updated whenever a feature ships, a design doc
lands, or upstream cuts a new release.
**Snapshot date.** 2026-05-08.
**Upstream baseline.** `gitlab.com/dlr-shepard/shepard 5.2.0`.

This is the **progress tracker** comparing what's available in this
fork (`noheton/shepard main`) against the upstream 5.2.0 surface,
broken down by feature area. **Different from `aidocs/34`** — that
doc is admin-facing ("what an upgrader needs to know about each
landed change"); this doc is **contributor / PI-facing** ("how does
this fork compare across the whole feature surface, including
designed-not-yet-shipped work").

## Status legend

| Symbol | Meaning |
|---|---|
| **✓** | Shipped on this fork's `main` |
| **📐** | Designed (design doc landed; implementation queued) |
| **🚧** | Implementation in flight (agent dispatched / PR open) |
| **=** | Parity with upstream (same shape on both sides) |
| **↑** | This fork extends upstream (we ship more) |
| **—** | Not implemented anywhere |
| **⚠** | Diverges deliberately — see notes |

## Standing rule

Per `CLAUDE.md`, this matrix updates in the same PR as any feature
landing or design-doc landing — keep it consistent with `aidocs/16`
backlog and `aidocs/00-index.md`. A row that's stale is the bug.

---

## 1. DB connectivity / health / migrations

| Capability | Upstream 5.2.0 | This fork | Status | Refs |
|---|---|---|---|---|
| Bounded `MigrationsRunner.waitForConnection` w/ exponential backoff | infinite-wait loop | configurable `shepard.migrations.connection-wait-timeout` (default `PT60S`) | **✓ ↑** | A1 / `aidocs/16` row A1 / `aidocs/17` |
| Per-DB health-check separation (startup vs runtime) | combined / coarse | per-DB `state` + `kind` in `/healthz` | **✓ ↑** | A1b |
| Graceful degradation when optional DBs (PostGIS) unavailable | endpoints hang | RFC-7807 503 + `Retry-After` when `@RequiresDatabase` not satisfied; 404 when toggle OFF | **✓ ↑** | A1c |
| `MigrationsRunner.apply()` fail-fast on `MigrationsException` | swallow + log | propagates as `RuntimeException` aborting startup | **✓ ↑** | A1e (commit `0f2f512`) |
| Automated DB recovery scheduler | none | `@Scheduled(every = "${shepard.health.recovery.interval}")` default `PT15S`; new `quarkus-scheduler` dep | **✓ ↑** | A1f |
| Parallel DB connectivity checks (startup + recovery) | sequential per-DB | `DbConnectivityWarmer` (startup) + `DbRecoveryScheduler` (recovery tick) both use Java 21 virtual threads; total wait = `max(latencies)` not `sum(latencies)` | **✓ ↑** | P1 |
| Flyway startup retry ceiling (Mongo/JDBC audit) | unbounded default (Flyway `connect-retries-interval=120s`) | `quarkus.flyway.connect-retries=10` + `connect-retries-interval=PT5S`; ≈50s ceiling aligned with Neo4j gate | **✓ ↑** | A1d (commit `e1c3635`) / `aidocs/17` |
| Migration progress monitoring endpoint | none | `GET /migrations/progress` (P3) | **✓ ↑** | P3 (commit `7cc74b8`) |

## 2. Configuration / feature toggles

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Build-time vs runtime feature toggle mechanism | `@IfBuildProperty` only | `@ConditionalOnFeature` + runtime-toggleable | **✓ ↑** | A3 |
| Runtime feature-toggle admin API (`GET /v2/admin/features`, `PATCH /v2/admin/features/{name}`) | none | `FeatureToggleRegistry` + `AdminFeaturesRest`; `@RolesAllowed("instance-admin")`; in-process override, not persisted across restart | **✓ ↑** | A3b |
| Toggle `source` field (`"default"` / `"config"` / `"runtime"`) on each toggle entry returned by `GET /v2/admin/features` and `PATCH /v2/admin/features/{name}` | none | `FeatureToggleEntry.getSource()` — returns `"runtime"` when overridden by PATCH, `"config"` when an `application.properties` key is present, `"default"` otherwise. `FeatureToggleIO` carries the field; no new endpoints, no schema change. 3 new `AdminFeaturesRestTest` cases. | **✓ ↑** | DX7 |
| Spatial-data namespace alias (`shepard.spatial-data.*` → `shepard.infrastructure.spatial.*`) | only old names | both names resolve; old logs deprecation warning; removal v6.0 | **✓ ↑** | A3c / `aidocs/A3c-namespace-migration.md` |
| Permission cache TTL/max-size config | hard-coded global defaults | `shepard.permissions.cache.ttl` (`PT5M`) + `.max-size` (`10000`) | **✓ ↑** | A4 |

## 3. Auth / API keys / security

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Semi-permanent API keys with expiry (`validUntil` + JWT `exp`) | none | shipped, distinguishable 401 on expiry | **✓ ↑** | L5 (commit `30c687a`) |
| `Bearer ` prefix mangle on JWTs containing the literal substring | mangles | safe `startsWith → substring(7)` | **✓ ↑** | M4 |
| Auth-header echo to warn-level logs (token-leak) | full echo | `present`/`absent` only | **✓ ↑** | M5 |
| `~/.shepard/keys/private.key` perms | umask default | `0600` via `Files.setPosixFilePermissions` (best-effort, POSIX only) | **✓ ↑** | M2 |
| Cypher injection on user-controlled property names + IRI types | injectable | parameterised + property-name allowlist; subsumes M9 | **✓ ↑** | C5 (cherry-pick `e90bfd8`) / `aidocs/07` C5 |
| Cypher injection — second wave (`*ReferenceDAO` family + `GenericDAO` + `VersionDAO` + `SemanticAnnotationDAO`) | injectable | parameterised + named params; **L2c precondition fully cleared** | **✓ ↑** | C5b (cherry-pick `c707e56`) / `aidocs/16` C5b |
| `PublicEndpointRegistry` path-match | `startsWith`, no normalisation (path-traversal vector) | exact-match against `Path.normalize()`-normalised path; 9 regression tests; post-P4 strips the `/shepard/api/` prefix via `RequestPathHelper` first | **✓ ↑** | H5 / `aidocs/07` H5 + P4 |
| `/v2/` JAX-RS routing scaffolding | n/a | shipped — `quarkus.http.root-path=/`; resources carry explicit `@Path("/shepard/api/...")`; `de.dlr.shepard.v2.*` package reserved; ArchUnit `V2NamespaceTest` fences (3 rules); `@Sunset` annotation skeleton ready for L2e | **✓ ↑** | P4 (cherry-pick `e0c5e32`) |
| OpenAPI spec splitting (`/shepard/doc/openapi/{v1,v2}.json`) | n/a | shipped — `V1OpenApiFilter` / `V2OpenApiFilter` (`OASFilter` implementations) classify paths through a shared `OpenApiShelfMembership` utility; `OpenApiPerShelfRest` (`@PermitAll`, `@Path("/shepard/doc/openapi")`) clones the live `OpenApiDocument` via `MergeUtil` then runs the shelf-appropriate filter; `?format=yaml` honoured; both paths added to `PublicEndpointRegistry`; combined `/shepard/doc/openapi.json` unchanged. 17 unit tests covering shelf membership, post-strip path handling, YAML output, singleton non-mutation, and the unset-doc 500 path. | **✓ ↑** | P4c (this branch) / `aidocs/16` P4c |
| RFC 7807 (`application/problem+json`) error responses + sanitised exception logging — **subsumes M7** | leaks `exception.getClass().getSimpleName()` + raw `getMessage()` for every 5xx; full stack at `error` for every 4xx | shipped — `ProblemJson` record with RFC 7807 §3.1 fields + flat `extensions` map; 10-entry `ShepardErrorCode` catalogue; known shepard exceptions surface their (controlled) message as `detail`; unknown-5xx returns generic with `traceId`; legacy `ApiError` preserved for `Accept: application/json` (upstream-client compat); full stack at `debug`, error line carries only `traceId + class + method + path` | **✓ ↑** | H4 (cherry-pick `e526183`) / `aidocs/07` H4 |
| CORS allowlist instead of `origins=*` | wildcard | TBD | 📐 (queued) | `aidocs/07` C2 |
| Default-credential placeholders that fail at startup if not changed | accept shipped defaults | TBD | 📐 (queued) | `aidocs/07` H8 |
| OIDC `realm_access.roles` claim path configurable (multi-IdP) | hard-coded Keycloak shape | TBD | 📐 (queued, F8) | `aidocs/22 §4.11a.4` |
| Permission system: declarative `@Authz` annotation | path-segment switch | TBD | 📐 (queued, F1) | `aidocs/24` F1 / P5 |
| **Instance-Admin role (`instance-admin`)** — single role tier for v1; gates `/v2/admin/*` endpoints + future `shepard-admin` CLI | none — `JWTPrincipal.roles` was always `new String[0]` so `@RolesAllowed("admin")` denied everyone | shipped — new `Role` Neo4j entity + `:HAS_ROLE` relationship; `V13` appId constraint; `JWTSecurityContext.isUserInRole` consults principal; `@RolesAllowed("instance-admin")` works | **✓ ↑** | A0 (this slice) / `aidocs/51` |
| **Dual-source role check** — IdP claim AND/OR Neo4j `:HAS_ROLE` edge, deduped on the principal | n/a (no role mechanism) | shipped — `JWTFilter.resolveDualSourceRoles`; principal carries one combined `roles` list | **✓ ↑** | A0 / `aidocs/51 §3.3` |
| **Bootstrap-token mechanism** — `/opt/shepard/.bootstrap-token` (mode 0600); `POST /v2/admin/bootstrap` consumes; replay-protected via `:BootstrapState` Neo4j flag node + token hash | none | shipped — `BootstrapTokenInitializer` runs after migrations; idempotent; configurable path via `shepard.bootstrap.token-path` | **✓ ↑** | A0 / `aidocs/51 §5` |
| **API-key roles claim** — `POST /apikeys` body grows `roles: [...]` field; minted JWT carries `roles` claim; cross-checked vs Neo4j-stored `roles` Set on read; allowlist + caller-must-have-each-role validation | none | shipped — `shepard.apikey.role-allowlist` (default `["instance-admin"]`); `InvalidAuthException` on escalation attempt; `InvalidRequestException` on out-of-allowlist | **✓ ↑** | A0 §4.2 / `aidocs/51 §4.2` |
| **C3: `getRoles` fail-closed** — orphan entities (no `:has_permissions` edge) now return `Roles(false,false,false,false)` instead of full read+write+manage to every authenticated user | full-access fallback (CRITICAL backdoor — `aidocs/07` C3) | shipped — paired with `OrphanPermissionsBackfillContext` pre-migration hook + V14 backfill | **✓ ↑** | C3 (bundled with A0) / `aidocs/07` C3 / `aidocs/51 §8` |
| **`GET /v2/admin/permission-audit`** — surfaces entities lacking `:has_permissions` edge (operational triage for the post-C3 fail-closed default) | none | shipped — `@RolesAllowed("instance-admin")`-gated; returns up to 1000 rows | **✓ ↑** | A0 / `aidocs/51 §10` |
| JWT `iat`-keyed permission cache (F4) — `isAccessTypeAllowedForUser` 4-arg `(entityId, accessType, username, jwtIat)`; prevents stale cache hits across JWT rotations (role-change, group-change, key rotation) | 3-arg cache key (no iat dimension; rotated JWTs can hit stale cache entries until TTL expires) | shipped — `JWTPrincipal` gains `iat` field; `JWTFilter` extracts `body.getIssuedAt()`; services with `AuthenticationContext` use `currentIat()`; REST resources and warmer pass `0L`. `CompositeCacheKey` is 4-tuple. 4 new `PermissionsServiceIatCacheKeyTest` cases. | **✓ ↑** | F4 |
| Fail-closed Neo4j guard (F5) — `PermissionsService.isAllowed()` returns `false` immediately when `DbHealthRegistry.isCurrentlyDown(NEO4J)` is `true`, without calling the DAO | degraded Neo4j → 500 or exception-propagated; no explicit deny | shipped — `dbHealthRegistry.isCurrentlyDown(DatabaseKind.NEO4J)` guard at top of `isAllowed()`; null-safe for test environments. 3 new `PermissionsServiceNeo4jGuardTest` cases. Requires A1b `DbHealthRegistry`. | **✓ ↑** | F5 |
| Group-based sharing model (`Group` node) | none | TBD | 📐 (queued, F2) | `aidocs/24` F2 |
| Permission audit log (Postgres) | none | TBD | 📐 (queued, F3) | `aidocs/24` F3 |

## 4. Identifiers (the L2 chain)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Additive `appId` (UUID v7) on every Neo4j node-write | none | shipped via `HasAppId` mixin on 28 labels; minted by `GenericDAO` seam; `V11` per-label unique constraints | **✓ ↑** | L2a (commit `fec7979`) |
| Backfill `appId` for pre-L2a rows (`V12`) | n/a | shipped — chunked 10k rows per batch, idempotent, operator-run rollback file | **✓ ↑** | L2b (cherry-pick `796bc11`) |
| Read path uses `WHERE e.appId = $appId` | uses `id()` | shipped — `EntityIdResolver` request-scoped translates Long ↔ appId at the DAO boundary; 14 DAO files swapped (`PermissionsDAO`, `DataObjectDAO`, `GenericDAO`, the `*ReferenceDAO` family, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, `SemanticAnnotationDAO`); public DAO signatures stay `long` for caller-compat; cache key stays `long` (per design §3.3); `Neo4jQueryBuilder`'s search-JSON predicates and `PermissionsService.isAllowed` segment dispatch deliberately untouched (those are L2d's job) | **✓ ↑** | L2c (cherry-pick `f3ca003`) / `aidocs/25 §4 Phase 3` |
| `/v2/` API exposes `appId` natively | n/a | TBD; gated on P4 + H4 | 📐 (queued) | L2d / `aidocs/25` |
| Drop `/v1/` long-id paths; flip cache key shape; drop TimescaleDB legacy column | n/a | TBD | 📐 (queued) | L2e / `aidocs/25` |

## 5. API surface — additive endpoints

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| NDJSON streaming ingest for timeseries (`application/x-ndjson` on `POST /timeseriesContainers/{id}/payload`) | JSON-only | shipped | **✓ ↑** | P14 (commit `24d4585`) |
| Body-form selective RO-Crate export (`POST /collections/{id}/export` with `ExportSelection`) | GET-only | shipped — additive sibling, GET preserved | **✓ ↑** | R2 (commit `be0eb26`) |
| Per-payload selection (file OIDs / channel columns / time windows) | none | shipped | **✓ ↑** | R2b (commit `60a3ea1`) |
| Per-payload metadata-field redaction (closed enum of 6 fields) | none | shipped | **✓ ↑** | R2c (commit `f993e8b`) |
| Export emits permissions / versions / annotations / subscriptions documents | none | shipped (3 of 4 kinds via R2d, +subscriptions via R2d2) | **✓ ↑** | R2d / R2d2 |
| `application/merge-patch+json` PATCH semantics (P21x) | mixed shapes | shipped consistent across new endpoints | **✓ ↑** | P21x |
| `POST /sql/timeseries` curated SQL-over-HTTP for bulk reads | none | TBD; gated on C5 | 📐 (queued, P10a-c) | `aidocs/29` |

## 6. Search

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Unified `POST /search/v2` replacing legacy 5 routes | 5 legacy routes | TBD; gated on C5 | 📐 (queued, P7) | `aidocs/13` |
| Cursor pagination across paginated endpoints | mixed/missing | TBD | 📐 (queued, L6) | `aidocs/13` / `aidocs/18` |
| Search-as-you-type with tree/graph view | basic search page | TBD | 📐 (queued, L4) | `aidocs/13` / `aidocs/14` |
| Saved searches / search history | none | TBD | 📐 (queued) | `aidocs/13` |

## 7. Semantic annotations / knowledge graph

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Annotate file / structured / spatial payloads (today: only timeseries + DataObjects) | timeseries + DataObjects only | TBD | 📐 (queued, L7) | `aidocs/14` |
| Nested annotation search | basic | TBD | 📐 (queued, #658) | `aidocs/14` |
| Term-search facet (search ontology terms) | none | TBD | 📐 | `aidocs/14 §6` |
| Better feedback on missing language labels | basic | TBD | 📐 (queued, #682) | `aidocs/14` |
| Refactor Neo4j representation of semantic annotations | older shape | TBD | 📐 (queued, #659) | `aidocs/14` |

## 7a. Internal semantic repository (neosemantics)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Pre-seeded common ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL / OBO Relation Ontology / NFDI4Ing metadata4ing) inside the existing Neo4j | none — operator must wire an external triple store | `OntologySeedService` ships ten SHA-256-pinned Turtle bundles under `backend/src/main/resources/ontologies/`; manifest at `ontologies-manifest.json`. Bundles are minimum-viable stubs carrying each ontology's canonical IRI prefix; full canonical content lands when N1c's refresh CLI replaces them in bulk. Toggles: `shepard.semantic.internal.preseed-ontologies.{enabled,skip-bundles}` (default on), plus N1c2's runtime `:SemanticConfig` singleton (admin REST/CLI). N1c2 also adds the `required: boolean` manifest field; `prov-o` + `obo-relations` are required (admin disable refused 409). ADR-0019 records default-on rationale. RO bundle added by ONT1a as the ninth; metadata4ing added by ONT1b as the tenth (engineering-research extension of PROV-O). | **✓ ↑** (N1b + ONT1a + ONT1b + N1c2) | `aidocs/48 §4` + `aidocs/58 §6` + `aidocs/65` |
| `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector` (Cypher / SPARQL via `n10s`) | none | shipped — compose installs `n10s`; `N10sBootstrapHook` runs post-A1e; fail-soft when plugin absent. **Testcontainer-level integration test deferred** (unit coverage via mocked OGM session is in place). | **✓ ↑** (N1a, this commit) | `aidocs/48 §3` |
| `shepard-admin semantic refresh-ontologies` CLI | none | New `POST /v2/admin/semantic/refresh-ontologies` endpoint (instance-admin gated) + matching `shepard-admin semantic refresh-ontologies [--bundles=…] [--force]` CLI subcommand. Walks the bundled-ontology manifest, fetches each bundle's pinned canonical URL, recomputes SHA-256, and re-imports via `n10s.rdf.import.inline` when the hash differs. Best-effort per bundle (partial failures land in `errors[]` with 200 OK overall). Reuses the `shepard.semantic.internal.*` config namespace — no new keys. Coverage: backend 87%/74% line/branch on `OntologyRefreshService`, 100%/92% on `SemanticAdminRest`; CLI command 92% line / 92% branch. | **✓ ↑** (N1c, this commit) | `aidocs/48 §4` + `aidocs/22 §7.1` |
| Admin-configurable ontology preseed — runtime enable/disable + custom-bundle upload | none | Five new endpoints under `/v2/admin/semantic/ontologies` (instance-admin gated): `GET` merged-list, `POST {id}/enable`, `POST {id}/disable` (409 for required), `POST` multipart upload (10 MB cap, SHA-256, parse-shape heuristic), `DELETE {id}` (409 for built-ins). Backed by `:SemanticConfig` singleton (V27) + `:UserOntologyBundle` catalogue (V28) + `OntologyConfigService`. First-start seeds the singleton from deploy-time defaults; runtime row wins forever after (per CLAUDE.md "Always: surface operator knobs"). Five new CLI subcommands `shepard-admin semantic ontologies {list,enable,disable,upload,remove}`. New deploy-time-only key `shepard.semantic.internal.user-bundles-dir` (default `/var/lib/shepard/ontologies/`). Coverage: `OntologyConfigService` 90.8% line / 78.4% branch; `SemanticAdminRest` 97.9% line / 85.7% branch; `RuntimeConfig` 100/100. | **✓ ↑** (N1c2, this commit) | `aidocs/65` + `aidocs/22 §7.1` |
| LUMEN seed integration — placeholder IRIs replaced with real PROV-O / QUDT / SKOS terms; SPARQL demo cell in notebook | seed uses placeholder IRIs only | TBD | 📐 (queued, N1d) | `aidocs/48 §5` |
| Annotation picker auto-completes from pre-seeded ontologies | none | TBD | 📐 (queued, N1e) | `aidocs/48` + `aidocs/14` |

## 8. Provenance / lineage

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| OpenLineage events across the pipeline | none | TBD | 📐 (queued) | `aidocs/30` |
| `direction=upstream/downstream/both` lineage walk endpoint | none | TBD | 📐 | `aidocs/30 §4` |
| `sh.lineage.upstream(app_id, depth=N)` Python helper | none | TBD | 📐 | `aidocs/30 §5` |

## 9. Identifiers via `/v2/` payload kinds (designed, not yet shipped)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| HDF5 / HSDS as a payload kind (`HdfContainer` / `HdfReference`); `h5pyd` parity | none | HSDS sidecar + shared-Keycloak token relay (`aidocs/35`). A5a (Phase 1) ships: `HdfContainer` create / read / delete + `hdf` compose profile + V25 + HTTP Basic auth. A5b (Phase 2) ships: permission bridge (shepard ACL → HSDS ACL via `PermissionsChangedEvent` + `HdfPermissionBridge`) + `POST /v2/admin/hdf/rebuild-acls` drift-recovery admin endpoint + ADR-0020 source-of-truth. A5c–e still queued. | **✓ ↑ (A5a + A5b)** / 📐 (A5c – A5e queued) | `aidocs/35` + `aidocs/63` |
| Git integration (`GitReference`); mode-a loose link + mode-b tracked artifact with inline preview (`GET …/preview`, PT5M cache); host adapters for GitLab + GitHub + Gitea; mode-c pinned snapshot still queued (G1c); CRUD via `/v2/data-objects/{appId}/git-references`; UI renders loose-link as clickable link | none | Backend ✓ (G1a / G1b / G1d); UI 🚧 | **✓ ↑ (backend, G1a #1063 + G1b #1086 + G1d this PR)** / **🚧 UI** | `aidocs/38` + `aidocs/63` ADR-0021 |
| Per-user git credentials (host + username + AES-GCM encrypted PAT); `/v2/me/git-credentials` CRUD | none | ✓ backend (G1-cred) / ✓ UI (PR #1071) | 🚧 (G1-cred #1069) | — |
| Templates feature (Templates Collection of DataObject blueprints; per-Collection allow-list) | none | TBD; replaces / supersedes upstream-aspirational L3 | 📐 (queued, T1) | `aidocs/39` |
| Process design + runtime in shepard core (`ProcessDefinition` + browser-hosted stepper) | SPW desktop only | TBD | 📐 (queued, PR1) | `aidocs/40 §2` |
| Snapshots (point-in-time, immutable, reproducible reads) | `Version` is a marker only | V2a shipped: `revision: long` counter on every `VersionableEntity` (backfilled by V36; surfaced read-only on all entity IOs); V2b–V2f queued | **✓ V2a** / 📐 V2b–V2f | `aidocs/41` |

## 10. User profile + settings

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| ORCID iD on user profile (#29) | none | `PATCH /v2/users/me` (orcid); ISO 7064 mod 11-2 checked; UI edit dialog in ProfilePane | **✓ ↑** | U1a (#1062) + U1-profile-ui |
| `displayName` override + `effectiveDisplayName` derivation + audit-trail render switch (#694) | username only | `displayName` field + `DisplayNameResolver` fallback chain (U1b #1064); render switch across all IO classes + RO-Crate export (U1b2) | **✓ ↑** | `aidocs/36` |
| `/me` route (split from Configuration) | mixed Configuration page | TBD | 📐 (queued, U1c) | `aidocs/36 §5` |
| Preferences (`theme`, `language`, `timeZone`, `dateFormat`, `defaultPageSize`, `defaultLandingPage`) via `SettingDescriptor` enum + typed map | none | TBD | 📐 (queued, U1d) | `aidocs/36 §3.2 / §7` |
| Avatar (shepard-uploaded → IdP `picture` → Gravatar tier) | none | TBD | 📐 (queued, U1e) | `aidocs/36 §3.1` |
| Secret-class settings (encrypted-at-rest with `~/.shepard/keys/secrets.key`) | none | TBD | 📐 (queued, U2-coupled) | `aidocs/36 §3.3` |

## 11. Lab journal

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Markdown body interpretation (CommonMark + GFM) | plain text | `GET /v2/lab-journal/{appId}/render` — CommonMark 0.24.0 + GFM tables/strikethrough/task-lists; `sanitizeUrls=true`; `contentFormat: "MARKDOWN"` on IO | ✓ shipped (J1a) | `aidocs/37` |
| Inline `.ipynb` static render | none | TBD | 📐 (queued, J1b) | `aidocs/37` |
| "Open in Jupyter" deep link via `editor.preferredJupyter` | none | TBD | 📐 (queued, J1c) | `aidocs/37` |
| Edit history (append-only revisions) | write-once | TBD | 📐 (queued, J1d) | `aidocs/37` |
| Display perf for large lab-journal lists (#507) | flat scroll | TBD; gated on L6 pagination | 📐 (queued) | `aidocs/37` / #507 |

## 12. AI features

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| OpenAI-compatible BYOK + admin-fallback infrastructure (per-user `ai.apiKey` / `ai.baseUrl` / `ai.model`) | none | TBD; **shepard ships zero models** | 📐 (queued, AI1a) | `aidocs/43 §4` |
| Anomaly detection on timeseries (rolling-median + isolation-forest) | none | TBD; pure-Python, LLM-independent | 📐 (queued, AI1b) | `aidocs/43 §3.1` |
| **Timeseries interval/point annotations** (TA1a) — `TimeseriesAnnotation` Neo4j entity with `startNs`, `endNs`, `label`, `description`, `aiGenerated`, `confidence`; linked from `TimeseriesReference` via `has_timeseries_annotation`; `/v2/timeseries-references/{refAppId}/annotations` CRUD; V35 migration; 15 unit tests | none | **TA1a shipped** — full CRUD REST surface, auth piggybacks DataObject permissions, `aiGenerated` flag ready for TA1c anomaly output | **✓ ↑** | `aidocs/16 TA1a` |
| Channel-quality scoring (background job: `TimeseriesReference.qualityScore` ∈ `[0.0, 1.0]` — completeness + coverage + stability heuristic; opt-in via `shepard.timeseries.quality-scoring.enabled`) | none | pure-heuristic scorer, no LLM | ✓ (AI1c) | `aidocs/43 §3.2`, `aidocs/16` AI1c |
| Embedding-based similarity (`/data-objects/{appId}/similar`) | none | TBD; needs `/v1/embeddings` endpoint | 📐 (queued, AI1d) | `aidocs/43 §3.5` |
| **Snap dashboards** — Claude-chat-style chat sidebar with closed tool-use catalogue + Vega-Lite v5 inline rendering | none | TBD; **headline killer feature** | 📐 (queued, AI1e) | `aidocs/43 §5.8` |
| Natural-language search | none | TBD | 📐 (queued, AI1f) | `aidocs/43 §5.1` |
| Lab journal authoring assist | none | TBD | 📐 (queued, AI1g) | `aidocs/43 §5.2` |
| Semantic-annotation suggestion | none | TBD | 📐 (queued, AI1h) | `aidocs/43 §5.4` |
| Auto-summarisation of run outcomes | none | TBD | 📐 (queued, AI1i) | `aidocs/43 §5.3` |
| RO-Crate description generation | none | TBD | 📐 (queued, AI1j) | `aidocs/43 §5.5` |
| Conversational lineage (chat over the lineage graph) | none | TBD | 📐 (queued, AI1k) | `aidocs/43 §5.6` |
| Notebook scaffolding | none | TBD | 📐 (queued, AI1l) | `aidocs/43 §5.7` |

## 13. Admin tooling

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| `/v2/admin/instance-admins` (list / grant / revoke) — REST surface for admin-role mutation | none | shipped — `@RolesAllowed("instance-admin")`-gated; returns audit trail (`grantedBy`, `grantedAt`); CLI counterparts deferred to L1 Phase 1 | **✓ ↑** | A0 / `aidocs/51 §10` |
| `POST /v2/admin/bootstrap` — first instance-admin via the one-shot bootstrap token | none | shipped — unauthenticated (token-gated); replay-protected | **✓ ↑** | A0 / `aidocs/51 §5.2` |
| `GET /v2/admin/permission-audit` — list orphan-permissions entities | none | shipped (post-C3 fail-closed) | **✓ ↑** | A0 / `aidocs/51 §10` |
| Admin CLI (`shepard-admin`) — read-only commands (Phase 1: `features list`, `health`, `migrations status`) | none | **shipped** — top-level `cli/` Maven module, Java 21 + Picocli 4.7, shaded uber-jar, 54 tests / 89 % instruction / 81 % branch coverage. Auth via `X-API-KEY` + the instance-admin role per A0. Phase 2+ still designed. | **✓ ↑** (Phase 1) | `aidocs/22 §7.1` |
| Admin CLI cleanup of soft-deleted entities (TTL) | none | designed | 📐 (queued, L1 phase 2) | `aidocs/22 §4.1` |
| Admin CLI RO-Crate import / export | none | designed | 📐 (queued, L1 phase 3) | `aidocs/22 §4.7` |
| Admin CLI feature-toggle inspection / flipping (incl. profile-bound) | none | designed (read-only ✓ shipped under L1 Phase 1) | 📐 (write path queued) | `aidocs/22 §4.6 / §4.6a` |
| `shepard-admin init` TUI wizard for first-run `.env` (Lanterna) | none | designed; deferred from L1 Phase 1 ship | 📐 (queued) | `aidocs/22 §4.11` |
| Universal TUI mode for every command (auto-fill from server state) | none | designed | 📐 (queued) | `aidocs/22 §4.x` |
| Env-driven auth discovery (`SHEPARD_ADMIN_URL` / `SHEPARD_ADMIN_API_KEY`) for the CLI | none | **shipped** — flags > env > `~/.shepard/admin.toml` > defaults precedence ladder; `AdminConfigLoaderTest` covers each layer | **✓ ↑** | `aidocs/22 §3.4` |
| Init wizard's OIDC sub-flow (Keycloak / Pocket ID / external w/ auto-discovery) | none | designed; depends on F8 (configurable claim path) for non-Keycloak | 📐 (queued) | `aidocs/22 §4.11a` |
## 13a. File storage backend
| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Files stored in MongoDB GridFS (1 MiB chunks, one bucket per FileContainer) | **=** | **=** (today) | **=** | `FileService.java` |
| **`FileStorage` SPI seam in core** — `de.dlr.shepard.storage.{FileStorage, FileStorageRegistry, StorageLocator, StoragePutRequest, StorageGetResponse, StorageException}` family. In-core `GridFsFileStorage` default adapter (wraps `FileService`); `shepard.storage.provider=gridfs` deploy-time key (cluster-identity exception). Optional-posture registry (no fail-fast on missing/disabled adapter — 503 `storage.provider.not-installed` envelope). `:ShepardFile.providerId` Neo4j property + V34 backfill. `shepard-admin storage status` CLI verb. | none | shipped (FS1a) | **✓ ↑** | `aidocs/45 §3.2`, `de.dlr.shepard.storage.*` |
| **`shepard-plugin-file-s3`** — S3-compatible file storage adapter (FS1b). `S3FileStorage implements FileStorage` (AWS SDK v2); any S3-compatible endpoint works (Garage, AWS S3, Cloudflare R2, Backblaze B2, Wasabi, MinIO, SeaweedFS, Ceph RGW). Locator: `<containerOid>/<uuid>` (bucket not stored — read from config at runtime). Config: `shepard.files.s3.{endpoint,region,access-key-id,secret-access-key,bucket,path-style-access}` (deploy-time only per cluster-identity exception). `FileS3PluginManifest` in `GET /v2/admin/plugins`. `shepard.storage.provider=s3` activates; `shepard.plugins.file-s3.enabled=false` default (opt-in). | none | **FS1b + FS1d shipped** (2026-05-16) | **✓ ↑** (FS1b + FS1d, this commit) | `aidocs/45 §3.2 + §3a + §9` + `aidocs/34 FS1b` |
| **Garage sidecar in `infrastructure/docker-compose.yml`** (FS1d) — `files-s3` profile; `dxflrs/garage:v1.0.1` image; port 3900; volume `./garage-data`; quick-start operator runbook in compose comment. `docker compose --profile files-s3 up -d` enables it. | none | **FS1b + FS1d shipped** (2026-05-16) | **✓ ↑** (FS1b + FS1d, this commit) | `aidocs/45 §9 FS1d` |
| **Presigned-URL endpoints for S3 direct upload + download** (FS1c) — `POST /v2/file-containers/{containerAppId}/upload-url` → presigned PUT URL + assigned oid (15 min TTL). `POST .../upload-url/commit` → registers `ShepardFile` after upload. `GET /v2/file-containers/{containerAppId}/files/{oid}/download-url` → presigned GET URL (5 min TTL). Backend no longer a bytes proxy for S3. `FileStorage` SPI grows `presignedUploadUrl()` / `presignedDownloadUrl()` default methods (return `Optional.empty()` for non-S3 adapters). `FileContainerDAO.findByAppId()` added. `FileContainerService.commitUpload()` creates `ShepardFile` in Neo4j from presigned-upload metadata. New IO types: `PresignedUploadRequestIO`, `PresignedUploadUrlIO`, `UploadCommitIO`, `PresignedDownloadUrlIO` under `de.dlr.shepard.v2.filecontainer.io`. | none | **FS1c shipped** (2026-05-16) | **✓ ↑** (FS1c, this commit) | `aidocs/45 §4 + §9 FS1c` + `aidocs/34 FS1c` |
| **Big-bang file-storage migration** (FS1e1) — `POST /v2/admin/files/migrate` triggers an async sweep: for each `:ShepardFile` with `providerId = source`, streams bytes from the source adapter to the target adapter (OID preserved via `StoragePutRequest.assignedObjectKey` — only `providerId` changes in Neo4j), then deletes from source. `GET /v2/admin/files/migrate/status` polls in-memory state (IDLE / RUNNING / DONE / FAILED). `shepard-admin files migrate <source> <target>` + `shepard-admin files migrate-status` CLI verbs. `GET /v2/admin/storage` returns all discovered adapters with enabled/active state; replaces the FS1a readiness-proxy placeholder in `StorageStatusCommand`. `StoragePutRequest` grows nullable `assignedObjectKey` field; `S3FileStorage.put()` uses it when set. `FileMigrationService` (background single-thread executor; `AtomicReference<FileMigrationState>`). Per-file error swallowing (job continues; failed count tracked). Re-running migration after partial failure is safe (already-migrated files have `providerId = target` and are excluded by Cypher query). | none | **FS1e1 shipped** (2026-05-16) | **✓ ↑** (FS1e1) | `aidocs/45 §6` + `aidocs/34 FS1e1` |
| **Frontend presigned upload** (FS1f) — `FileContainerAccessor.uploadFile()` tries presigned PUT path when container has `appId` set; falls back to legacy `POST /shepard/api/fileContainers/{id}/payload` on 503 (GridFS active). Three-step flow: obtain presigned URL → PUT bytes direct to S3 → commit. `FileContainer` TypeScript model (`backend-client`) grows `appId?: string \| null` (was already returned by the backend; now declared in generated type). `backend-client` rebuilt. | proxied through backend | **FS1f shipped** (2026-05-16) | **✓ ↑** (FS1f) | `aidocs/45 §9 FS1f` + `aidocs/34 FS1f` |
| **RO-Crate export delivery via presigned URL (FS1g — sync variant of aidocs/31 §O3)** — `POST /v2/collections/{appId}/export-url`; builds RO-Crate ZIP in-memory and returns a presigned S3 GET URL (30 min TTL) instead of proxying bytes through the JVM. Sync variant ships first; async (O2-gated full offload) deferred. `FileStorage` SPI grows `presignedExportUrl(String key, byte[] zipBytes, String fileName, Duration ttl) → Optional<URI>` default method; `S3FileStorage` implements it (uploads to `exports/<uuid>.zip` in-bucket, returns presigned GET URL). Non-S3 adapters (`Optional.empty()`) get 503. New IO type: `ExportUrlIO {downloadUrl, fileName, expiresAt}`. New REST resource: `CollectionExportUrlRest` under `de.dlr.shepard.v2.collection.resources`. Operator action: configure lifecycle rule on `exports/` prefix (recommended 24 h TTL so stale ZIPs auto-clean). | proxied through backend | **FS1g shipped** (2026-05-16) | **✓ ↑** (FS1g) | `aidocs/45` / `aidocs/31 §O3` / `aidocs/34 FS1g` |
## 13b. CI / quality gates
| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| JaCoCo coverage report on `mvn verify` | configured but reads the wrong (Quarkus-side) exec file → reports ~0.5% | reads the real `target/jacoco.exec` → reports ~68% line / 66% branch | **✓ ↑** | `backend/pom.xml` jacoco-maven-plugin (post-fix) |
| JaCoCo `check` gate at 60% line / 60% branch (`haltOnFailure` in CI) | none | shipped; `-Djacoco.haltOnFailure=true` in `backend-ci.yml` | **✓ ↑** | `backend/pom.xml` + `.github/workflows/backend-ci.yml` |
| PR coverage comment + per-changed-file 70% gate | none | shipped via `madrapps/jacoco-report` | **✓ ↑** | `.github/workflows/backend-ci.yml` |
| SpotBugs + findsecbugs running on `mvn verify` (was `<reporting>` only — `aidocs/07` M12) | `<reporting>` only — never invoked | shipped at `Effort=Max`, `Threshold=High`, `failOnError` in CI | **✓ ↑** | `backend/pom.xml` + `.github/workflows/backend-ci.yml` |
| OWASP Dependency-Check (weekly + on pom changes; `failBuildOnCVSS=7`) | none | shipped via `.github/workflows/security.yml`; suppressions in `backend/dependency-check-suppressions.xml` with CVE+reason format | **✓ ↑** | `.github/workflows/security.yml` |
| Secret scanning (gitleaks weekly + on push) | none | shipped via `.github/workflows/security.yml` | **✓ ↑** | `.github/workflows/security.yml` |
| GitHub Pages site CI | none | shipped (separate workflow) | **✓ ↑** | `.github/workflows/pages.yml` |
| Container images published to GHCR (`ghcr.io/noheton/shepard-{backend,frontend}:{latest,sha-<7>,vX.Y.Z}`) | upstream publishes to gitlab.com | shipped via `.github/workflows/ci.yml` `build-images` job (gated on unit-tests + spotbugs) | **✓ ↑** | `.github/workflows/ci.yml` |
| **Demo auto-seeder** (`seeder` one-shot Docker Compose service in `docker-compose.override.yml`; Keycloak ROPC → bootstrap → API-key → `seed.py --regenerate`; `SHEPARD_BOOTSTRAP_TOKEN_PATH` shared via named volume; idempotent; `restart: "no"`) | none | shipped — CI2 commit | **✓ ↑** | `infrastructure/docker-compose.override.yml` + `examples/seed-showcase/entrypoint.sh` |
| **Playwright e2e test suite** (`@playwright/test` 1.49; targeting production `https://shepard.nuclide.systems`; auth helper, collections CRUD, nav coverage, smoke API health) | none | shipped — CI2 commit | **✓ ↑** | `e2e/` |
| **CodeQL** SAST (Java + JS/TS, `security-extended` query set) | none | shipped via `.github/workflows/codeql.yml`; weekly + per-PR; SARIF → Code Scanning | **✓ ↑** | `.github/workflows/codeql.yml` |
| **Trivy** container scan on every published GHCR image (CRITICAL+HIGH, ignore-unfixed) | none | shipped in `build-images.yml`; SARIF → Code Scanning per-image | **✓ ↑** | `.github/workflows/build-images.yml` |
| **SBOM** (CycloneDX) per published image via `anchore/sbom-action`; uploaded as artifact + attached to GitHub releases | none | shipped in `build-images.yml` | **✓ ↑** | `.github/workflows/build-images.yml` |
| **Dependency-review** (PR-time licence + new-CVE check) banning GPL/AGPL/SSPL families with `.github/dependency-review-config.yml` allowlist | none | shipped in `security.yml` | **✓ ↑** | `.github/workflows/security.yml` |
| **`PluginManifest` SPI + drop-in JAR discovery** (`de.dlr.shepard.plugin.{PluginManifest,PluginRegistry,PluginContext,PluginEntry,PluginState}`; `backend/plugins/*.jar` walked at startup via `ServiceLoader` post-`MigrationsRunner.apply()`; per-plugin `shepard.plugins.<id>.enabled` runtime toggle; fail-soft per plugin (a throwing `onRegister` marks the plugin `FAILED` and continues); path-traversal hardening on the plugins dir; UH1a flipped to a real `shepard-plugin-unhide-${revision}.jar` as the first plugin under the new shape) | none | **PM1a shipped** — 15 new unit tests; backend's `build-helper-maven-plugin` source-stitching of `plugins/unhide/src/{main,test}` removed; the plugin module produces a real JAR with `provided` scope on backend (so the JAR doesn't bundle backend.jar); backend declares the plugin via a default-active `with-plugins` Maven profile (`-DnoPlugins` breaks the bootstrap circular reference); Quarkus's build-time CDI scanner indexes the plugin via `quarkus.index-dependency.shepard-plugin-unhide.*`; PluginRegistry's classpath-then-JAR duplicate check silently shadows the redundant `/deployments/plugins/` copy. PM1b2 (child-classloader CDI integration + semver enforcement + JAR signature verifier) + PM1c (persist runtime overrides) queued. | **✓ ↑** (PM1a, this commit) | `aidocs/47 §2.5` + `aidocs/63 ADR-0023` + `aidocs/16 PM1a` |
| **Plugin registry admin REST + CLI parity** (`GET /v2/admin/plugins` lists every discovered plugin including DISABLED + FAILED rows with id / version / shepardCompatibility / state / enabled / sourcePath / registeredAt / failureMessage; `PATCH /v2/admin/plugins/{id}` RFC 7396 merge-patch on the `enabled` toggle with RFC 7807 `plugin.not-found` 404 + `plugin.config.read-only-field` 400 envelopes; `shepard-admin plugins {list,enable,disable}` CLI verbs with shared `--output={human,json}` / `--url` / `--api-key`; `PluginEntry.registeredAt` timestamp added so the registry surfaces discovery order chronologically; PROV1a auto-captures each PATCH as an `:Activity` row with `targetKind=PluginEntry`) | none | **PM1b shipped** — 12 backend admin-REST unit tests + 10 CLI end-to-end tests; new resource under `de.dlr.shepard.v2.admin.plugins.PluginsAdminRest` with IO records under `…plugins.io.{PluginEntryIO,PluginListIO,PluginPatchIO}`; CLI commands under `de.dlr.shepard.cli.commands.{PluginsCommand,PluginsListCommand,PluginsEnableCommand,PluginsDisableCommand}` plus IO mirrors `…cli.io.{PluginInfo,PluginList}`; runtime override stays in-memory only — PM1d persists. Pre-existing CLI compile-break (duplicate `delete(String)` in `ShepardHttpClient` left over from PM1a's PR merge) fixed in the same patch. | **✓ ↑** (PM1b, this commit) | `aidocs/63 ADR-0023` forward-work checklist + `aidocs/16 PM1b` |
| **`PluginManifest` SPI enrichment + plugin-dependency resolution** (six new default methods on `PluginManifest`: `title()` / `description()` / `homepageUrl()` / `repositoryUrl()` / `licence()` / `dependencies()`; new `PluginDependency` record + in-tree `VersionRange` parser supporting exact / `[lo,hi)` / `[lo,)` / `(,hi]` / wildcard syntax with semver-aware numeric compare; `PluginRegistry.validateAndOrderDependencies()` does Kahn's topological sort between discovery and lifecycle; failures fail-soft per plugin (`plugin.dependency.missing` / `plugin.dependency.version-mismatch` / `plugin.dependency.cycle`); registry now guarantees a plugin's `onRegister` runs after every declared dependency's `onRegister`; `GET /v2/admin/plugins` wire shape grows six fields all backwards-compatible additive; `shepard-admin plugins list` table grows three columns (TITLE 30c / LICENCE 12c / REPOSITORY 40c, truncate with `…`); `UnhidePluginManifest` filled in with title / description / homepage / repository / `Apache-2.0` licence; dependencies left empty for UH1a) | none | **PM1c shipped** — 8 `PluginManifestTest` (default-method semantics) + 12 `VersionRangeTest` (parsing + range arithmetic + semver-aware compare) + 12 `PluginRegistryDependencyTest` (linear chain registers in topo order C → B → A; two-node cycle A→B→A fails both; three-node cycle fails all three; missing dep fails dependent only; unsatisfied / satisfied / unparseable version constraint; pre-existing-failed entry isn't a dep target) + 2 new `PluginsAdminRestTest` (rich + bare manifest IO assertions) + 3 new + 3 helper-unit CLI `PluginsCommandsTest` (new columns rendered, truncation marker, bare manifest dashes, truncate helper unit tests). New core types: `de.dlr.shepard.plugin.{PluginDependency,VersionRange}`. New IO record: `de.dlr.shepard.v2.admin.plugins.io.PluginDependencyIO`. **No new dependencies pulled** — the version-range parser is hand-rolled in-tree to avoid `org.apache.maven:maven-artifact` (same posture as N1c2's ontology-bundle slug parser). | **✓ ↑** (PM1c, this commit) | `aidocs/63 ADR-0023` forward-work checklist + `aidocs/16 PM1c` |
| **CLI extensibility SPI + Unhide CLI migrates to `plugins/unhide/`** (`de.dlr.shepard.cli.plugin.AdminCliCommandProvider` SPI: plugin JARs ship their own `shepard-admin` Picocli subcommands via `META-INF/services/`; `CliPluginBootstrap` ServiceLoader-scans the same plugin directory the backend uses — `SHEPARD_PLUGINS_DIR` env or `shepard.plugins.dir` sys-prop, defaults to `/deployments/plugins` then `cli/plugins`; `ShepardAdmin.commandLine()` is the new public entry that runs the bootstrap; the seven `Unhide*Command` files + `UnhideAdminPaths` + `UnhideCommand` move from `cli/src/main/java/de/dlr/shepard/cli/commands/` to `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/cli/`; `UnhideConfig` + `HarvestKeyMinted` IO records move alongside; `UnhideCommandsTest` follows; CLI's `with-plugins` Maven profile copies `de.dlr.shepard.plugins:*` deps into `target/plugins/` at packaging time mirroring backend's profile shape; CLI's pom grows a `maven-jar-plugin` `test-jar` execution so plugin modules can pull the shared `StubBackend`/`CliRunner` test fixtures via `<type>test-jar</type>`; end-user UX byte-identical — `shepard-admin unhide …` works exactly as before; same verbs, same flags, same JSON shapes, same exit codes) | none | **PM1d shipped** — 5 new `AdminCliCommandProviderTest` cases on the JAR-walk path (synthetic plugin JAR built inline via the Java compiler API + fail-fast smoke through the bootstrap, bare-JAR-no-service-file skip, non-existent-dir warning, no-default-dir silent no-op, duplicate-subcommand-name warn-and-skip), 4 new `ShepardAdminBootstrapTest` cases on the `ShepardAdmin.commandLine()` wiring (core subcommands intact, idempotent across calls, --help, --version). 12 relocated `UnhideCommandsTest` cases run unmodified in their new location. Backend untouched — `cli/`-side + `plugins/`-side refactor only. | **✓ ↑** (PM1d, this commit) | `aidocs/63 ADR-0023` forward-work checklist + `aidocs/16 PM1d` |
| **Plugin runtime hardening: JAR signature verifier + semver enforcement + DEGRADED state** (new `de.dlr.shepard.plugin.JarSignatureVerifier` `@ApplicationScoped` bean — `KeyStore.getInstance("JKS"/"PKCS12")` for trust-anchor loading + `JarFile(verify=true)` for signature parsing; four `shepard.plugins.signing.*` deploy-time keys (`required=false` default, `truststore.path`, `truststore.password`, `trust-anchors` for future PEM support); when `required=true` an UNSIGNED or UNTRUSTED JAR's entry lands FAILED with `failureMessage=plugin.signature.unsigned` / `plugin.signature.untrusted`. `PluginRegistry.enforceCompatibility()` runs between discovery + dependency-resolution so compat failures pre-empt the dependent's `plugin.dependency.missing`; `shepard.plugins.compatibility.strict=true` default + operator-override valve — incompatible plugins land FAILED with `plugin.compatibility.failed: requires shepard <range>, running <version>` or `plugin.compatibility.unparseable: <details>`. New `PluginState.DEGRADED` lifecycle state + `PluginEntry.markDegraded(String)` hook (forward placeholder — PM1b3 detection of runtime-only JARs without build-time CDI indexing per `aidocs/69`). `VersionRange.parse` extended with operator-comma syntax (`>=5.2.0,<6`) every existing plugin manifest already uses — pre-PM1b2 the parser only accepted Maven-bracket syntax (`[5.2.0,6)`) which no plugin actually wrote, so the compat field's actual enforcement is unblocked by this slice. PM1b3 (true runtime CDI integration via Vert.x router) queued as the remaining slice with the full design rationale in `aidocs/69`.) | none | **PM1b2 shipped** — 9 `JarSignatureVerifierTest` (unsigned → UNSIGNED / self-signed-no-truststore → UNTRUSTED / signed-by-anchor → TRUSTED / tampered → JarSignatureException via JDK `JarFile` digest mismatch / empty-truststore → UNTRUSTED / default-not-required / invalid-truststore-path fail-soft WARN / `isSigningRequired()` / multi-anchor-any-match — all keys minted via `keytool` ProcessBuilder + signed via JDK's `jdk.security.jarsigner.JarSigner` API; no internal-class access so no `--add-exports` flags needed) + 11 `PluginRegistryCompatibilityTest` (compatible / too-old / too-new / empty constraint / null constraint / unparseable → FAILED / strict=false incompatible-registers / strict=false unparseable-registers / compat-precedes-dependency / multi-plugin only-incompatible-fails / already-failed-not-reprocessed) + 8 new `VersionRangeTest` operator-comma cases (>=5.2.0,<6 / single-clause ge / single-clause lt / =-operator / empty-clause IAE / malformed-clause IAE / semver-numeric compare 1.10>1.9 / bare-clause-as-exact) + 4 `PluginRegistryDegradedStateTest` (markDegraded state transition / list visibility / enum name() stability for wire shape / markEnabled clears DEGRADED failureMessage). New backend types: `de.dlr.shepard.plugin.{JarSignatureVerifier, JarSignatureException}`. New design doc: `aidocs/69-runtime-plugin-cdi.md` parking the deferred PM1b3 child-classloader CDI integration with the Option A (Quarkus extension) vs Option B (Vert.x router) trade-off + the verified-against-Quarkus-3.27.x finding that Arc doesn't expose a runtime bean-addition API. `PluginEntryIO.state` OpenAPI descriptor extended to mention `DEGRADED`. SpotBugs clean; backend coverage gate met. | **✓ ↑** (PM1b2, this commit) | `aidocs/63 ADR-0023` forward-work checklist (JarSignatureVerifier + semver-range enforcement ticked; child-classloader CDI integration deferred to PM1b3) + `aidocs/16 PM1b2` + new `aidocs/69` design doc |
| **Plugin runtime override persistence** (`:PluginRuntimeOverride` Neo4j label — one row per plugin id, V32 uniqueness constraint mirroring V11/V22/V25/V27/V30; `PluginRuntimeOverride` `@NodeEntity` with `HasAppId` + `pluginId` + `enabled` + `updatedAt` + `updatedBy`; `PluginRuntimeOverrideDAO` for findByPluginId / findAllOverrides / save / deleteByPluginId; `PluginRegistry.seedOverridesFromDao()` runs at startup before lifecycle invocation; `PluginRegistry.setEnabled(id, enabled, actorSub)` writes through synchronously within the PATCH request; sparse-table semantics — reset-to-default DELETEs the row rather than upserts; `PluginsAdminRest.patch` takes `@Context SecurityContext` and propagates the caller's `sub` to the registry → `updatedBy` field; CLI success messages updated from "runtime override survives until restart" to "override persisted; survives restart"; the deploy-time `shepard.plugins.<id>.enabled` keys remain valid as the install default — runtime override row wins forever after, matching the A3b / N1c2 / UH1a "admin-configurable" idiom with the persistent-override variant) | none | **PM1e shipped** — 13 new `PluginRuntimeOverrideDAOTest` (Mockito-on-Session: entityType / findByPluginId hit+miss+blank / findAllOverrides empty+null+populated / save mints appId / save rejects null+blank / deleteByPluginId hit+miss+blank) + 11 new `PluginRegistryPersistenceTest` (startup-seed populates / empty-table no-op / idempotent / fail-soft on DAO exception / disable persists / reset-to-default deletes / unknown plugin throws / null actor → anonymous / write-failure preserves in-memory cache / simulated restart preserves disable / 2-arg setEnabled delegates) + 4 new `PluginsAdminRestTest` (caller-sub from SecurityContext / null principal / blank-name principal / 2-arg overload) + 3 new `PluginsCommandsTest` (CLI success message contract for enable + disable + two-PATCH simulated restart). New backend types: `de.dlr.shepard.plugin.{PluginRuntimeOverride, PluginRuntimeOverrideDAO}`. New migration: `V32__Add_pluginId_constraint_PluginRuntimeOverride.cypher` (V11-shape uniqueness constraint, idempotent + fail-fast). Fail-soft on DAO outage at startup (registry falls back to deploy-time defaults — same as pre-PM1e) and on PATCH write (logs WARN; in-memory cache is current; the override doesn't survive restart but operator can re-PATCH after DB recovers). PM1 series complete; PM1b2 (child-classloader CDI + JAR signature verifier + semver-compat enforcement) is the remaining queued slice. | **✓ ↑** (PM1e, this commit) | `aidocs/63 ADR-0023` forward-work checklist + `aidocs/16 PM1e` |
| **Version 6.0.0 + opt-in plugin default** — `backend/pom.xml` `<revision>` bumped from `1.0.0-SNAPSHOT` → `6.0.0-SNAPSHOT`; all four plugin `pom.xml` revisions + CLI `pom.xml` revision bumped to `6.0.0-SNAPSHOT`; all four `SHEPARD_COMPATIBILITY` declarations updated from `">=5.2.0,<6"` → `">=6.0.0-SNAPSHOT,<7"` (SNAPSHOT-inclusive lower bound so dev builds satisfy PM1b2's semver-range gate); `frontend/package.json` version bumped to `6.0.0`; `PluginRegistry.isEnabled()` + `readDeployTimeDefault()` default flipped from `Boolean.TRUE` → `Boolean.FALSE` (opt-in posture — "all classpath plugins are optional"); `application.properties` adds explicit `shepard.plugins.{unhide,kip,minter-local}.enabled=true` for the three bundled plugins that should be on by default (minter-datacite stays `false` until credentials are configured); 11 `PluginRegistryTest` + `PluginRegistryPersistenceTest` cases updated to reflect the new default semantics; new `snapshotInclusiveLowerBound_acceptsSnapshotAndRelease` `VersionRangeTest` case validates `>=6.0.0-SNAPSHOT,<7` acceptance. | none | **V6 shipped** — breaking for third-party plugin manifests declaring `">=5.2.0,<6"` (PM1b2 semver-range gate rejects them under `6.0.0-SNAPSHOT`; fix: change to `">=6.0.0-SNAPSHOT,<7"`; escape valve: `shepard.plugins.compatibility.strict=false`). The `/shepard/api/...` wire surface remains byte-compatible with upstream 5.2.0 per the API-version policy. | **✓ ↑** (V6, this commit) | `aidocs/34` V6 row |
| **`PayloadKind` SPI** (`de.dlr.shepard.spi.payload.PayloadKind` — `name()` + `entityPackages()`; ServiceLoader-discovered plain POJO; `NeoConnector.connect()` iterates impls to register OGM entity packages; `META-INF/services/de.dlr.shepard.spi.payload.PayloadKind` in each plugin JAR) | none | **SPI1a / PL1a shipped** (2026-05-16) | **✓ ↑** (SPI1a, this commit) | `aidocs/47 §2` + `aidocs/34 SPI1a` |
| **`shepard-plugin-spatial`** — pilot extraction of PostGIS spatial payload kind to drop-in plugin JAR. `SpatialPayloadKind implements PayloadKind` contributes OGM entity packages; `SpatialPluginManifest implements PluginManifest`. 29 production Java files + SQL migration + 10 unit tests moved to `plugins/spatial/`; 2 integration tests stay in backend. `shepard.plugins.spatial.enabled=true` by default (mirrors pre-extraction always-on posture). | n/a | **SPI1a / PL1b shipped** (2026-05-16) | **✓ ↑** (SPI1a, this commit) | `aidocs/47 §3` + `aidocs/34 SPI1a` |
| **`shepard-plugin-hdf5`** — extraction of HDF5/HSDS payload kind to drop-in plugin JAR. `HdfPayloadKind implements PayloadKind` registers `de.dlr.shepard.data.hdf.entities` OGM package (fixing latent OGM-gap bug); `HdfPluginManifest implements PluginManifest`. 9 production Java files + 10 unit tests moved to `plugins/hdf5/`; `HdfFeatureToggle` stays in backend. `shepard.plugins.hdf5.enabled=false` default (matches pre-extraction posture). | n/a | **PL1c shipped** (2026-05-16) | **✓ ↑** (PL1c, this commit) | `aidocs/47 §3` + `aidocs/34 PL1c` |
| **`shepard-plugin-git`** — extraction of G1 (Git reference) payload kind to drop-in plugin JAR. `GitPayloadKind implements PayloadKind` registers `de.dlr.shepard.context.references.git.entities` OGM package (fixing latent OGM-gap bug); `GitPluginManifest implements PluginManifest`. 20 production Java files + 9 unit tests moved to `plugins/git/`; `GitCredential` entity/DAO/service + migrations V19/V20/V26 stay in backend (auth perimeter). `shepard.plugins.git.enabled=true` default (mirrors pre-extraction posture). | n/a | **PL1d shipped** (2026-05-16) | **✓ ↑** (PL1d, this commit) | `aidocs/47 §3` + `aidocs/34 PL1d` |
| Codegen archetype `mvn shepard:scaffold-payload-kind` | none | TBD | 📐 (queued, DX3) | `aidocs/47 §2.5` |
| `make dev` single-command bootstrap (init wizard + compose up + smoke) | none | TBD | 📐 (queued, DX4) | `aidocs/47 §4.4` |
| Unified `ShepardTestStack` testcontainer resource | none | TBD | 📐 (queued, DX1) | `aidocs/47 §4.1` |
| `ShepardTestFixtures` shared typed builders (Collection / DataObject / User / Permissions / BasicReference) | none | shipped — `backend/src/test/java/de/dlr/shepard/testing/fixtures/ShepardTestFixtures.java`; pilot adopters `CollectionServiceTest` / `DataObjectServiceTest` / `BasicReferenceServiceTest` | **✓ ↑** (DX2) | `aidocs/16` DX2 |
| BI integrations — Grafana data-source plugin + Superset SQLAlchemy recipe | none | TBD; "SQL win" via P10 (C5 cleared) | 📐 (queued, DX8) | `aidocs/47 §4.8` + `aidocs/29` |
| Selective export ✓ (see §5 above) | GET-only | **✓** (R2 series shipped) | **✓ ↑** | §5 |
| Streaming RO-Crate export for large Collections | possible OOM | TBD | 📐 (queued) | `aidocs/31` |
| Long-running export pattern (job-id polling) | synchronous only | TBD | 📐 (queued) | `aidocs/32` |
| Reproducible-by-snapshot exports | n/a (no snapshots) | TBD; lands at V2d | 📐 (queued, V2d) | `aidocs/41 §5` |
| Endpoint group | Internal change | User-visible? |
| `/shepard/api/.../search` | Cypher now parameter-bound (C5); identifiers whitelisted | No — request body unchanged |
| `/shepard/api/.../healthz` | Per-DB up/down state in body (A1b) | Additive fields only |
| `/shepard/api/timeseriesContainers/{id}/payload` | Now also accepts `application/x-ndjson` (P14) | Additive content-type |
| `/shepard/api/collections/{id}/export` (GET) | Unchanged | No |
| Endpoint | Status | Origin |
| `POST /v2/collections/{appId}/export` (body-form ExportSelection) | ✓ shipped | R2 / `aidocs/16` |
| `POST /v2/admin/bootstrap` (unauthenticated; consumes `/opt/shepard/.bootstrap-token`) | ✓ shipped | A0 / `aidocs/51 §5.2` |
| `GET /v2/admin/instance-admins` (list, source column: Neo4j) | ✓ shipped | A0 / `aidocs/51 §10` |
| `POST /v2/admin/instance-admins` (grant role) | ✓ shipped | A0 / `aidocs/51 §10` |
| `DELETE /v2/admin/instance-admins/{username}` (revoke role) | ✓ shipped | A0 / `aidocs/51 §10` |
| `GET /v2/admin/permission-audit` (list orphan-permissions entities) | ✓ shipped | A0 / `aidocs/51 §8` |
| `GET /v2/admin/features` (list runtime feature toggles) | ✓ shipped | A3b |
| `PATCH /v2/admin/features/{name}` (set toggle enabled/disabled) | ✓ shipped | A3b |
| (None of the L2c/L2d/L2e `/v2/...` URL forms have shipped yet — those land at L2d.) | | |

### 14a.3 New `/v2/...` endpoints — designed (queued)

| Endpoint(s) | Slice | Refs |
|---|---|---|
| `GET /v2/lab-journal/{appId}/render`, `/notebooks` | J1a/J1b | `aidocs/37` |
| `POST /v2/data-objects/{id}/git-references`, `GET /v2/git-references/{appId}{,/content}`, `PATCH/DELETE` | G1a/b/c/d | `aidocs/38` |
| `GET /v2/templates`, `GET /v2/templates/{appId}`, `POST/PATCH/DELETE`, `GET/PUT /v2/collections/{appId}/allowed-templates`, `POST /v2/collections/{appId}/data-objects/from-template/{templateAppId}` | T1a-T1e | `aidocs/39` |
| `POST /v2/processes/{appId}/runs`, `POST /v2/process-runs/{appId}/steps/{stepId}/complete`, `GET /v2/process-runs/{appId}` | PR1a/b | `aidocs/40 §2` |
| `POST /v2/collections/{appId}/snapshots`, `GET /v2/snapshots/{appId}{,/manifest}`, `GET /v2/collections/{appId}?snapshot=`, `POST /v2/collections/{appId}/export?snapshot=`, `GET /v2/snapshots/{a}/diff/{b}` | V2a-e | `aidocs/41` |
| `GET /v2/lab-journal/{appId}/render`/`/notebooks` (duplicate listing — same as J1) | | |
| `GET /users/me`, `PATCH /users/me`, `PUT /users/me/avatar`, `GET /users/{appId}`, `GET /users/{appId}/avatar` | U1a-U1e | `aidocs/36` |
| `GET /v2/templates/{...}/processes/...` (process runtime; subset of PR1) | | `aidocs/40 §2` |
| `POST /v2/timeseries/{appId}/detect-anomalies`, `GET /v2/data-objects/{appId}/similar`, `POST /v2/search/natural`, `POST /v2/lab-journal/assist`, `POST /v2/semantic-annotations/suggest`, `POST /v2/collections/{appId}/export?aiAssist=true`, the snap-dashboards tool-use catalogue | AI1a-AI1l | `aidocs/43` |
| `POST /v2/timeseries/{appId}/reingest`, `GET /v2/file-references/{appId}/versions{,/N}`, `POST /v2/file-references/{appId}/payload`, `DELETE /v2/file-references/{appId}/versions/N`, `GET /v2/collections/{appId}?snapshot=` (extension) | PV1a-f | `aidocs/46` |
| `POST /v2/file-containers/{containerAppId}/upload-url`, `POST /v2/file-containers/{containerAppId}/upload-url/commit`, `GET /v2/file-containers/{containerAppId}/files/{oid}/download-url` | FS1c — ✓ shipped | `aidocs/45 §7` |
| `GET /v2/artifacts/{type}/{id}/url` | FS1g | `aidocs/45` |
| `GET /v2/admin/features`, `PATCH /v2/admin/features/{name}` | DX7 / A3b / `aidocs/22 §4.6` — ✓ shipped | — |
| `GET /v2/processes`, `POST /v2/processes/import` | PR1a, PR1c | `aidocs/40 §2` |
| `POST /v2/hdf-containers`, `GET /v2/hdf-containers/{appId}{,/file,/datasets/{path}/value}`, `POST /v2/data-objects/{id}/hdf-references`, `POST /api-keys/{id}/hsds-token` | A5a-e | `aidocs/35` |
| `POST /v2/sql/timeseries` | P10a | `aidocs/29` |

This list is **maintained alongside the design docs that propose
each endpoint**; if you add a new design doc that introduces
`/v2/` paths, add a row here in the same PR.

### 14a.4 Convention reminder

- `/v2/<kind>-references/{appId}/...` per-payload-kind read/write
- `/v2/<kind>-containers/{appId}/...` per-container CRUD
- `/v2/admin/...` admin-role gated
- `/users/me` and `/users/{appId}` for profile (no `/v2` prefix —
  matches `aidocs/36 §6`'s decision to put profile at the top
  level for stability)
- `/v2/artifacts/{type}/{id}/url` for any non-payload-kind blob

Plugins (per `aidocs/47 §2.1`) get their own `/v2/<kind-name>-...`
namespace; core enforces the shape.

## 15. API versioning policy

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| `/shepard/api/...` byte-frozen with upstream | n/a (it IS upstream) | enforced via `CLAUDE.md` standing rule | **✓** | `CLAUDE.md` / `aidocs/34` |
| `/v2/...` reserved for this fork's additive surface | n/a | enforced; all new endpoints in design docs go here | **✓** (rule) / 📐 (endpoints follow as designed) | `CLAUDE.md` |
| Generated clients split (5.x compat tag vs 6.x next tag) | single track | TBD | 📐 (planned, `aidocs/40 §4`) | `aidocs/40 §4` |

## 16. Documentation

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| GitHub Pages docs site (Jekyll under `docs/`) | none | shipped at https://noheton.github.io/shepard/ | **✓ ↑** | `docs/` |
| Live researcher-facing vision doc | none | shipped (`aidocs/42-vision.md`, Live status) | **✓ ↑** | `aidocs/42` |
| Live ecosystem doc (SPW + sTC + others) | none | shipped (`aidocs/40-ecosystem.md`) | **✓ ↑** | `aidocs/40` |
| Upstream upgrade-path tracker (admin-facing) | n/a | shipped (`aidocs/34-upstream-upgrade-path.md`, Live) | **✓ ↑** | `aidocs/34` |
| **This** fork-vs-upstream feature matrix (contributor-facing) | n/a | this doc | **✓ ↑** | this doc |
| LUMEN-inspired showcase seed + analysis notebook (expanded to 25 channels × 15 runs; 2 hold days; 4 new signal profiles: valve, tank, gimbal, combustion) | none | shipped (PR #1001; expanded in CI2 commit — `N_RUNS=15`, 25 channels, `examples/seed-showcase/data/generate.py`) | **✓ ↑** | PR #1001 + CI2 |
| Upstream-current parallel import script (`import_upstream.py`) for the same showcase data | n/a (the upstream itself) | shipped | **✓ ↑** | PR #1001 |
| **In-app user docs** — Nuxt `/help` route serving `docs/*.md` from the same source as the Pages site | none | TBD; same source, two presentations | 📐 (queued, D1a) | `aidocs/49` |
| **Playwright screenshot pipeline** capturing against a CI-booted compose stack, committing PNGs to `docs/assets/screenshots/` | none | TBD; closes 9-month-old screenshot-placeholder backlog | 📐 (queued, D1b) | `aidocs/49 §3` |
| Task-shaped help pages (upload-data / share-collection / export-rocrate / process-step) for casual users | none | TBD | 📐 (queued, D1c) | `aidocs/49 §2.2` |
| Version-stamped in-app docs ("Help for shepard X.Y") | n/a | TBD | 📐 (queued, D1d) | `aidocs/49 §2.3` |

## 16a. Experiment orchestration

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| `shepard-experiment-coordinator` service driving manufacturing experiments end-to-end (PLC / SPS / KUKA / OPC/UA / KUKA RSI) | none | TBD | 📐 (queued, EXP1a) | `aidocs/50` |
| Three timing strategies — pre-seed (eager) / JIT (lazy default) / post-process (staged) | n/a | TBD | 📐 (queued, EXP1d/EXP1e + JIT in EXP1a) | `aidocs/50 §2 / §5` |
| Checkpoint + restart-whole + restart-at-step on top of V2 snapshots | n/a | TBD | 📐 (queued, EXP1f + EXP1g) | `aidocs/50 §6` |
| KUKA RSI telemetry routing into shepard TimeseriesReferences | n/a | TBD | 📐 (queued, EXP1i) | `aidocs/50 §4.2` |
| Operator UI for live experiment monitoring + restart controls | none | TBD | 📐 (queued, EXP1j) | `aidocs/50 §3.3` |
| Recipe storage as `templateKind = "EXPERIMENT_RECIPE"` in `__templates` (T1) | n/a | TBD | 📐 (queued, EXP1k) | `aidocs/50 §3.2` |

## 17. Companion ecosystem

| Tool | Upstream version | This fork status | Notes |
|---|---|---|---|
| `shepard-process-wizard` (desktop JavaFX) | upstream-only | unchanged compat (frozen API); future absorption into shepard core via PR1 designed | `aidocs/40 §2` |
| `shepard-timeseries-collector` (Java OPC/UA + MQTT + KUKA RSI) | upstream-only | 10 prioritised improvements documented; some need shepard-side dependencies (P14 ✓ shipped, A1b ✓ shipped, L2c queued) | `aidocs/40 §3` |
| Generated clients (`python` / `typescript` / `java`) | upstream OpenAPI | unchanged for `/shepard/api/`; `/v2/` will need a parallel client crank when L2d lands | `aidocs/40 §4` |
| `shepard-frontend` | upstream-only | `aidocs/33` analysis covers UX improvements; W11–W2 design ranked | `aidocs/33` |
| `shepard-dataship` (publication pipeline) | upstream-only | parked under `aidocs/16` X1 | `aidocs/16` X1 |

---

## Headline state of progress

**Shipped on this fork (vs upstream 5.2.0):** 6 DB-resilience improvements, 5 config/cache improvements, 1 API-key auth feature (L5), 4 security fixes (M2/M4/M5 + the L2a additive identifier substrate), 5 endpoint-additive features (P3, P14, R2/b/c/d/d2), the GitHub Pages docs site with three deploy guides, the **25-channel LUMEN showcase seed + notebook** (15 runs, 2 hold days, 4 signal profiles), **demo auto-seeder** service with bootstrap flow, **Playwright e2e test suite**, API Docs nav link, and **two Live tracking docs** (`aidocs/34` admin-facing + this matrix contributor-facing).

**In flight (agents dispatched):** none currently — C5/C5b/L2b/L2c have all landed; the remaining L2 chain (L2d/L2e) is gated on P4 + H4.

**Designed and queued (substantial):** the entire L2 chain after L2a (b/c/d/e), unified search + pagination (`aidocs/13`), semantic-annotation expansion (`aidocs/14`), HDF5/HSDS (A5), Templates (T1), Process design+runtime (PR1), Git integration (G1), User profile (U1), Lab journal v2 + Jupyter (J1), Snapshots (V2), AI features w/ snap-dashboards killer feature (AI1), Admin CLI L1 Phase 2+ (Phase 1 already shipped — see §13 row above), permission-system evolutions (F1-F8), provenance (`aidocs/30`).

**Headline next-horizon line items** (per `aidocs/42` vision):
1. Snap dashboards (AI1e) — the killer feature
2. HDF5 / HSDS (A5)
3. Templates + processes (T1 + PR1)
4. User profile + ORCID (U1)

---

## 18. Newly designed (2026-05-12 batch)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| **Instance-Admin role + bootstrap** (`instance-admin` tier, `:HAS_ROLE` Neo4j edge, configurable OIDC roles-claim path, file-on-disk bootstrap token, `/v2/admin/...` REST surface) | n/a | backend slice shipped (PR #1037; A0 + C3 + F8) | **✓ ↑** | `aidocs/51` / `aidocs/16 A0` |
| **AAS backend integration** (Adapter shim at `/v2/aas/...` mapping Shell / Submodel / SubmodelElement → Collection / DataObject / Reference; conformance targets IDTA Nameplate + TechnicalData + TimeSeriesData) | n/a | design done; **AAS1-well-known shipped** (capability self-description at `/v2/aas/.well-known/aas-server`) | 🚧 (in-flight, AAS1a queued) | `aidocs/52` / `aidocs/16 AAS1-well-known` |
| **`FileReference` → `FileBundleReference` rename + `FileGroup` sub-node** (camera cyclic-capture grouping; legacy `:FileReference` label + `/shepard/api/.../fileReferences/...` wire shape stay frozen; new `/v2/bundles/{appId}/groups/...` shelf surfaces the new shape; V21 + V22 migrations idempotent + fail-fast) | n/a | **FR1a shipped** (rename, V21/V22 migrations, default-group auto-create on new bundles, full `/v2/bundles/...` REST surface, 81 new tests, JaCoCo / SpotBugs green) | **✓ ↑** | `aidocs/53` / FR1a PR |
| **Singleton `FileReference` (single-file primitive, FR1b)** + `/v2/files/...` shelf + V23 opt-in carve-out migration | n/a | **FR1b shipped** (new `:SingletonFileReference` entity + DAO + service, shared `_shepard_files` Mongo namespace, `/v2/files/{appId}/{,content}` shelf with HTTP-range support, V23 Java-based migration gated by `shepard.migration.split-singletons.enabled` + V23_R rollback + V24 constraint, 76 new tests, JaCoCo / SpotBugs green) | **✓ ↑** | `aidocs/53 §1.8` / FR1b PR |
| **Video as a first-class payload kind** (dedicated PayloadStorage plugin; segments + HLS manifest; navigation by video-time + wall-clock; live ingest via sibling `shepard-video-collector` or MediaMTX sidecar) | n/a | design done | 📐 (queued, VID1a) | `aidocs/53` |
| **Templates as a first-class admin entity** (`:ShepardTemplate` Neo4j entity in admin-only subgraph, JSON DSL bodies, copy-on-write versioning, admin-gated CRUD at `/v2/templates`) | n/a | design done | 📐 (queued, T1a) | `aidocs/54` |
| **PROV-O provenance + activity capture + dashboard + metadata4ing (m4i) content-neg** (`:Activity` Neo4j entity, JAX-RS request filter, `GET /v2/provenance/{activities,count,stats,entity/{appId}}` query endpoints, per-Collection activity sparkline UI, PROV-N JSON + PROV-O JSON-LD + metadata4ing-profile JSON-LD content-negotiation) | n/a | **PROV1a/b/c/d/f/g/h shipped** — capture filter, query endpoints, stats aggregation with cumulative integral, retention TTL, PROV-N JSON content negotiation, the per-Collection Vue dashboard (sparkline + cumulative overlay + action-kind histogram + time-range picker), **and m4i content-negotiation** (`Accept: application/ld+json` for PROV-O; `; profile=metadata4ing` for the engineering-research flavour with `m4i:ProcessingStep` / `m4i:InvestigatedObject` / `m4i:Person` subtypes; 406 RFC 7807 on unknown profile). Instance-admin dashboard (PROV1e) queued. | **✓ ↑** | `aidocs/55`, `aidocs/64 §3.2` / PROV1a-c commits + PROV1d + PROV1g + PROV1h (this PR) |
| Network-drive mount (read-only WebDAV at `/v2/webdav/...`, FileBundle directory tree + synthetic per-entity files + CSV-default timeseries) | none | design done; default-off | 📐 (queued, MNT1a) | `aidocs/61` |
| **v2 API simplification + output profiles + MCP-friendly OpenAPI** (flat appId-indexed paths for single-entity endpoints, `?profile=metadata\|relations\|all` projections, `x-mcp-tool-name` + `x-mcp-side-effects` extensions per operation, ArchUnit fence for admin ↔ `@RolesAllowed("instance-admin")`) | n/a | design done | 📐 (queued, V2S1a) | `aidocs/56` |
| **OpenAPI client generators — dual baseline** (Kiota new baseline for `/v2/` shelf, OpenAPI Generator still-maintained legacy for byte-frozen `/shepard/api/...` shelf, Hey API as TS-only tactical secondary; ADR-0022) | n/a | **CG1a shipped** (Kiota baseline wired for Python + TypeScript + Java under `clients-v2/`; CI workflow `.github/workflows/clients-kiota.yml` regenerates on every release tag) + **CG1b shipped** (OpenAPI-Generator pipeline retargeted from combined `openapi.json` → `openapi_v1.json` via new build-time slicer; per-language `v1-scope-smoke-test.sh` regression fence; dual-posture READMEs); CG1c (PyPI / npm / Maven Central publishing) queued; CG1d (Go / Rust / C#) deferred | **✓ ↑** (CG1a + CG1b) | `aidocs/57` / `aidocs/63` ADR-0022 |
| **Tree drag-and-drop** (lefthand tree; move default, copy on modifier; cycle-prevention server-side) | n/a | design done | 📐 (queued, UI1a) | `aidocs/58 §2` |
| **Navigable Collection graph view** (cytoscape.js; per-user layout persistence; entity-kind filtering) | n/a | design done | 📐 (queued, UI2a) | `aidocs/58 §3` |
| **`@`-mention autocomplete for internal entity citations** (TipTap mention extension + `GET /v2/search?q=…&kinds=…`; opaque `[entity:<appId>]` syntax; orphan-handling) | n/a | design done | 📐 (queued, UI3a) | `aidocs/58 §4` |
| **`:CollectionProperties` properties-node** (folds template-info + default-FC-strategy + cross-cutting Collection config into one place; replaces the `default_filecontainer` hack) | n/a | design done | 📐 (queued, CP1a) | `aidocs/58 §5` |
| **RO (Relation Ontology) added to the pre-seed bundle** (`obo-relations.ttl`, SHA-256 pinned, CC0 licence, LUMEN seed uses RO terms post-ONT1c) | n/a | ONT1a shipped (bundle + manifest + tests); ONT1c (LUMEN seed cites RO terms) + ONT1d (frontend ontology-picker) queued | **✓ ↑** (ONT1a) | `aidocs/58 §6` + `aidocs/48` |
| **metadata4ing (NFDI4Ing v1.4.0) added to the pre-seed bundle** (`metadata4ing.ttl`, SHA-256 pinned, CC BY 4.0; engineering-research extension of PROV-O — `m4i:ProcessingStep` subtype of `prov:Activity`, `m4i:Method`, `m4i:Tool`, `m4i:InvestigatedObject`, `m4i:NumericalVariable` + QUDT units, `m4i:Person`/`Organization` subtypes of prov equivalents) | n/a | ONT1b shipped (bundle + manifest + tests; bundle count now 10); unblocks future PROV1h slice that lifts `/v2/provenance/*` rendering to `m4i:`-flavoured shapes via `Accept: application/ld+json; profile=metadata4ing` | **✓ ↑** (ONT1b, this commit) | `aidocs/16` ONT1b |
| **Helmholtz Unhide publish plugin** (`/v2/unhide/feed.jsonld` schema.org + metadata4ing JSON-LD feed for HKG / Unhide harvester; `:UnhideConfig` admin-configurable singleton + admin REST + `shepard-admin unhide ...` CLI parity for master toggle / feedPublic / contactEmail / harvest-key rotate / revoke; RFC 7807 envelopes for the disabled-feed / missing-key / read-only-field paths; **UH1b** — each feed entry inlines a `m4i:hasProcessingStep` array of the most-recent N `:Activity` rows targeting the Collection, rendered via PROV1h's `ProvJsonLdRenderer.renderActivityAsM4iNode(...)` so the m4i shape lives in one place; **UH1c** — each feed entry cites the current KIP1a Publication via `schema:identifier` (PropertyValue PID) + `schema:url` (the `.well-known/kip` resolver) + `m4i:hasIdentifier`) | n/a | **UH1a + UH1b + UH1c shipped** — first `shepard-plugin-*` module ever. **PM1a phase 3 flipped UH1a to a real drop-in JAR**: `shepard-plugin-unhide-${revision}.jar` declared as a backend `<dependency>` via the default-active `with-plugins` Maven profile; the source-stitch via build-helper is gone. Harvest-key plaintext returned exactly once at mint, never logged (only the SHA-256 fingerprint), never enters `:Activity`. V30 migration ships the appId-unique constraint on the singleton label. Phase 1 lists every non-deleted Collection (per-Collection `publishToHelmholtzKG` toggle deferred to UH1d). UH1b provenance window tuned via `shepard.unhide.feed.provenance-window` (default 5, hard cap 100; buffer-sizing CLAUDE.md exception). UH1c reuses `PublicationDAO.findByEntityAppId` from KIP1a; plugin never reads `:Publication` directly. Both extensions fail-soft on DAO errors (log + omit field; never fail the page). UH1d (frontend toggle + admin tile), UH1e (SHACL self-test) queued. | **✓ ↑** (UH1a + UH1b + UH1c + PM1a, this commit) | `aidocs/67` + `aidocs/16` UH1a/UH1b/UH1c/PM1a |
| **3D Geometry & FEM Annotator** (`shepard-plugin-cad`) — `CadReference` PayloadKind; dual Three.js/vtk.js renderer; cascadio WASM + pythonocc sidecar conversion (STEP/IGES/BDF/INP/CGNS); URDF bundles; `GeometryAnnotation` model (point/region/surface/FEM node-set); scene graph export (glTF/USD/SDF/VTU); Paradigms + Pandora DLR-internal adapter hooks | n/a | design done | 📐 (queued, CAD1a; gated on FS1a) | `aidocs/78` |
| **CPACS Annotator** (`shepard-plugin-cpacs`) — `CpacReference` PayloadKind; TiGL sidecar (CPACS XML → GLB); `:CpacComponent` hierarchy; `CpacAnnotation` XPath-anchored; `needsReview` on mesh-hash change; non-geometry CPACS data (aero tables, mass) | n/a | design done | 📐 (queued, CPACS1a; gated on FS1a + CAD1b) | `aidocs/79` |
| **RCE Integration** (`shepard-plugin-rce`) — `Shepard Sink` + `Shepard Source` RCE tool integrations; OpenLineage → PROV-O `:Activity` per component execution; CPACS-typed MDO provenance chain; MDO data bus (parameter-sweep Collections + batch ingest); `:RceConfig` singleton | n/a | design done | 📐 (queued, RCE1a; RCE1b gated on PROV1a) | `aidocs/80` |
| **Matrix notification + MCP bot** (`shepard-plugin-matrix-notify`) — Matrix room as N10 notification sink; maubot + MCP server generated from `/v2/` OpenAPI spec for researcher chat; `x-mcp-tool-name` extensions already in `aidocs/56` | n/a | concept (design pending) | 📐 (concept, MTX1) | `aidocs/16` MTX1 |
| **Spatial Data Binding** (SB1) — `:DataBinding` Neo4j node linking `GeometryAnnotation` to any Reference; five display modes (BADGE / HEATMAP / GLYPH / JOINT_ANGLE / DEFORMATION); time scrubber; SSE live mode; semantic edge types `[:CHARACTERISES]` / `[:VALIDATES]` / `[:INSPECTS]` / `[:LOCATES_SENSOR]`; ZLP-specific data chains | n/a | design done | 📐 (queued, SB1a; gated on CAD1b + PROV1a) | `aidocs/81` |
| **Point Cloud + Live Overlay** (PC1 + SB2) — `PointCloudReference` (Potree streaming); ICP alignment; surface-annotation matching (PC1e); eight overlay modes incl. UT C-scan (SB2g) + POSE_6D (SB2h); SA1/RDK1/IL1 ingest pipeline SPIs | n/a | design done | 📐 (queued, PC1a; gated on CAD1a) | `aidocs/83` |
| **Live Digital Twin** (DT1) — moving-frame annotations (`urdfLinkId`); `DigitalTwinScene` composite; WebSocket CBOR state stream; interactive WebRTC server-side render; historical replay; `DigitalTwinSnapshot` (alarm-triggered / manual) | n/a | design done | 📐 (queued, DT1a; gated on CAD1b + SB1d) | `aidocs/84` |
| **Time Reference Model** (TM1) — `timeReference: enum WALL_CLOCK \| EXPERIMENT_RELATIVE` + `wallClockOffset: long` (nanoseconds epoch of DAQ t=0) on `TimeseriesReference`; both fields mutable via `PATCH /v2/timeseries/{appId}` so an offset can be corrected when a better anchor is discovered (ffprobe, SA sync signal, manual); V-series Cypher backfill of `timeReference="WALL_CLOCK"` on existing rows; `wallClockOffsetSource` provenance tag; enables cross-source alignment with video (SB2a), SA 6D data (SB2h), and multi-channel timeline (SB2d) | n/a | design done | 📐 (queued, TM1a; ungated) | `aidocs/16` TM1 |
| **Camera-to-scene calibration** (VID2) — click-based 2D→3D correspondence picker (user marks N point pairs in a video frame + the matching 3D CAD point; sparse ok, skip-able features; EPNP+RANSAC PnP solver via Open3D sidecar); stores `[:CAMERA_ALIGNED_TO {matrix:[16], rmse, K:[9]}]` edge on `VideoStream→CadReference`; unlocks SB2a CAMERA_FRUSTUM viewer mode, 3D annotation → video pixel projection overlay, and DT1 live camera viewport | n/a | design sketch | 📐 (queued, VID2a; gated on VID1a + PC1b sidecar) | `aidocs/16` VID2 |
| **DBpedia Databus rich-reference plugin** (preview / description / title fetched + 24h-cached) | n/a | design done | 📐 (queued, REF1a; off-by-default until v1) | `aidocs/58 §7` |
| **GraphRAG on shepard** (embeddings per DataObject / Collection / lab-journal entry / Reference; similarity endpoint `GET /v2/search/similar?to=<appId>`; native Neo4j 5.13+ vector index — no extra service) | n/a | design done | 📐 (queued, GR1a) | `aidocs/58 §8` + `aidocs/43` |
| **HMC Kernel Information Profile baseline** (`Minter` SPI in core, all minter impls ship as plugins post-KIP1h; `MinterRegistry` discovery + optional posture — 503 RFC 7807 when no minter is wired, resolver still works against existing rows; `:Publication` entity + `HAS_PUBLICATION` edge + `versionNumber` property; `POST /v2/{kind}/{appId}/publish` with Writer/Manager auth + idempotent re-POST + `?force=true` re-mint as version-bump; unauthenticated `GET /v2/.well-known/kip/{pid-suffix}` returning the public HMC KIP JSON-LD record with `digitalObjectVersion: "v<n>"`; `PublishableKindRegistry` for URL-segment dispatch — adding bundles/files/lab-journal-entries doesn't change the URL shape; V29 + V31 migrations; **KIP1g (resolver as plugin) + KIP1h (minter as plugin + versioned PIDs Phase 1) shipped**) | n/a | **KIP1a + KIP1g + KIP1h shipped** — KIP1a baseline (KIP1b folded in). KIP1g extracts the HMC-flavoured resolver + record JSON-LD shape into `plugins/kip/` (`shepard-plugin-kip`). KIP1h (this PR) extracts the renamed `MockMinter` → `LocalMinter` into `plugins/minter-local/` (`shepard-plugin-minter-local`), relaxes the `MinterRegistry` fail-fast to optional posture, and ships versioned PIDs Phase 1 (local format `shepard:<instance.id>:<kind>:<appId>:v<n>`; `Publication.versionNumber`; V31 backfill stamps `versionNumber=1` on legacy rows). The `Minter` SPI seam stays in core; every impl ships as a plugin per CLAUDE.md heuristic #3. Test counts: backend 2266 → 2322 (+56 net after `MockMinter`-cases removal); plugin-kip 6 → 8 (+2); plugin-minter-local 12 (new). Phase 2 (`:EntityVersion` graph) queued as ENT1 umbrella. | **✓ ↑** (KIP1a + KIP1g + KIP1h, this commit) | `aidocs/66` / `aidocs/16 KIP1a` + `KIP1g` + `KIP1h` |
| **Local PID minter plugin (versioned PIDs)** (`shepard-plugin-minter-local` — default minter for fresh shepard installs; mints stable `shepard:<instance.id>:<kind>:<appId>:v<n>` PIDs; renamed from the pre-KIP1h in-core `MockMinter`; ships as a drop-in JAR per CLAUDE.md heuristic #3 "SPIs in core, adapters in plugins"; the `LocalMinter` `@ApplicationScoped` bean is picked up by Quarkus's build-time CDI scanner via the backend's `with-plugins` Maven profile) | n/a | **KIP1h shipped** — `LocalMinter` reads `MintRequest.versionNumber` (computed by `PublishService` as `findLatestVersionNumber + 1`), encodes as `v<n>` segment in the PID. Same inputs → same PID (no epoch-millis timestamp; PIDs are stable). `shepard.instance.id` reused as the namespace segment; fallback to `local` with a startup WARN if unset. 12 `LocalMinterTest` cases. | **✓ ↑** (KIP1h, this commit) | `aidocs/66` / `aidocs/16 KIP1h` |
| **DataCite DOI minter plugin** (`shepard-plugin-minter-datacite` — real DOIs against DataCite Fabrica test or production; sibling JAR to `shepard-plugin-minter-local` under `plugins/minter-datacite/`; `:DataciteMinterConfig` singleton + `/v2/admin/minters/datacite/` REST surface + `shepard-admin minters datacite ...` CLI parity; AES-GCM credential cipher keyed off `shepard.instance.id`; versioned-PID respect via `MintRequest.versionNumber()` + DataCite `IsNewVersionOf` / `HasVersion` chain) | n/a | **KIP1d shipped** — admin-runtime config (idiom: A3b + UH1a + N1c2 — single-instance `:DataciteMinterConfig` node, seed-on-first-start from `shepard.minters.datacite.*`, runtime PATCH wins). Five admin REST endpoints: `GET/PATCH /config` (RFC 7396), `POST/DELETE /credential`, `POST /test-connection`. Twelve CLI verbs (status, enable, disable, set-{api-url, prefix, repository-id, publisher, landing-page-base, state, password}, clear-password, test-connection) — `set-password` reads stdin / tty only, never on the command line. Network hardening: 10s connect + 30s request timeout, one retry on 5xx / network error with 1s backoff, `MinterException` on second failure → 500 `publish.minter.failed`. Default `apiBaseUrl=https://api.test.datacite.org` (Fabrica test, safe for fresh install). `V33` migration adds the uniqueness constraint on `:DataciteMinterConfig.appId`. 126 plugin tests across 9 classes (`DataciteMinterTest` 30 + `DataciteMinterConfigServiceTest` 24 + `DataciteAdminRestTest` 17 + `DataciteCommandsTest` 22 + `CredentialCipherTest` 12 + `DataciteIoTest` 7 + `MintersAdminCliCommandProviderTest` 5 + `DataciteMinterConfigTest` 5 + `DataciteMinterPluginManifestTest` 4). | **✓ ↑** (KIP1d, this commit) | `aidocs/66 §6` / `aidocs/16 KIP1d` |
| **HMC KIP — Vue "Publish" button** (frontend surface for KIP1a; `PublishButton.vue` + `PublishModal.vue` under `frontend/components/context/publish/` mounted on the Collection + DataObject detail panes; SPDX licence picker `CC-BY-4.0 / CC-BY-SA-4.0 / CC0-1.0 / MIT / Apache-2.0 / LGPL-3.0 / GPL-3.0`; post-publish snackbar with copy-to-clipboard for both the PID and the resolver URL) | n/a | **KIP1e shipped** — pure frontend slice; calls existing KIP1a endpoints via a new `usePublishEntity` composable (raw `fetch` until the OpenAPI Generator regeneration for KIP1a lands the `PublishApi` client). Scope-downs: existing-publication-state display deferred (no list-publications endpoint on KIP1a + `BasicEntityIO` frozen), licence wire-shape deferred (KIP1a's POST takes no body — the dropdown is informational and forwards the SPDX id on submit for when the backend grows a body), i18n not wired in this Nuxt app, no frontend test suite exists. | **✓ ↑** (KIP1e, this PR) | `aidocs/66 §7` / `aidocs/16 KIP1e` |

---

## Cross-references

- **Companion docs:** `aidocs/34` (admin-facing upgrade path), `aidocs/16` (live backlog), `aidocs/42` (researcher-facing vision), `aidocs/00-index` (full design corpus index).
- **Standing rules** in `CLAUDE.md`: API-version policy, vision-currency, upstream-upgrade-path tracking, this matrix.
