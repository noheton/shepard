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
| Migration progress monitoring endpoint | none | `GET /migrations/progress` (P3) | **✓ ↑** | P3 (commit `7cc74b8`) |

## 2. Configuration / feature toggles

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Build-time vs runtime feature toggle mechanism | `@IfBuildProperty` only | `@ConditionalOnFeature` + runtime-toggleable | **✓ ↑** | A3 |
| Runtime feature-toggle admin API (`GET /v2/admin/features`, `PATCH /v2/admin/features/{name}`) | none | `FeatureToggleRegistry` + `AdminFeaturesRest`; `@RolesAllowed("instance-admin")`; in-process override, not persisted across restart | **✓ ↑** | A3b |
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
| Pre-seeded common ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL / OBO Relation Ontology / NFDI4Ing metadata4ing) inside the existing Neo4j | none — operator must wire an external triple store | `OntologySeedService` ships ten SHA-256-pinned Turtle bundles under `backend/src/main/resources/ontologies/`; manifest at `ontologies-manifest.json`. Bundles are minimum-viable stubs carrying each ontology's canonical IRI prefix; full canonical content lands when N1c's refresh CLI replaces them in bulk. Toggles: `shepard.semantic.internal.preseed-ontologies.{enabled,skip-bundles}` (default on). ADR-0019 records default-on rationale. RO bundle added by ONT1a as the ninth; metadata4ing added by ONT1b as the tenth (engineering-research extension of PROV-O). | **✓ ↑** (N1b + ONT1a + ONT1b) | `aidocs/48 §4` + `aidocs/58 §6` |
| `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector` (Cypher / SPARQL via `n10s`) | none | shipped — compose installs `n10s`; `N10sBootstrapHook` runs post-A1e; fail-soft when plugin absent. **Testcontainer-level integration test deferred** (unit coverage via mocked OGM session is in place). | **✓ ↑** (N1a, this commit) | `aidocs/48 §3` |
| `shepard-admin semantic refresh-ontologies` CLI | none | New `POST /v2/admin/semantic/refresh-ontologies` endpoint (instance-admin gated) + matching `shepard-admin semantic refresh-ontologies [--bundles=…] [--force]` CLI subcommand. Walks the bundled-ontology manifest, fetches each bundle's pinned canonical URL, recomputes SHA-256, and re-imports via `n10s.rdf.import.inline` when the hash differs. Best-effort per bundle (partial failures land in `errors[]` with 200 OK overall). Reuses the `shepard.semantic.internal.*` config namespace — no new keys. Coverage: backend 87%/74% line/branch on `OntologyRefreshService`, 100%/92% on `SemanticAdminRest`; CLI command 92% line / 92% branch. | **✓ ↑** (N1c, this commit) | `aidocs/48 §4` + `aidocs/22 §7.1` |
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
| Snapshots (point-in-time, immutable, reproducible reads) | `Version` is a marker only | TBD; logical snapshots backed by entity revisions | 📐 (queued, V2) | `aidocs/41` |

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
| Markdown body interpretation (CommonMark + GFM) | plain text | TBD | 📐 (queued, J1a) | `aidocs/37` |
| Inline `.ipynb` static render | none | TBD | 📐 (queued, J1b) | `aidocs/37` |
| "Open in Jupyter" deep link via `editor.preferredJupyter` | none | TBD | 📐 (queued, J1c) | `aidocs/37` |
| Edit history (append-only revisions) | write-once | TBD | 📐 (queued, J1d) | `aidocs/37` |
| Display perf for large lab-journal lists (#507) | flat scroll | TBD; gated on L6 pagination | 📐 (queued) | `aidocs/37` / #507 |

## 12. AI features

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| OpenAI-compatible BYOK + admin-fallback infrastructure (per-user `ai.apiKey` / `ai.baseUrl` / `ai.model`) | none | TBD; **shepard ships zero models** | 📐 (queued, AI1a) | `aidocs/43 §4` |
| Anomaly detection on timeseries (rolling-median + isolation-forest) | none | TBD; pure-Python, LLM-independent | 📐 (queued, AI1b) | `aidocs/43 §3.1` |
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
| **Two file-storage plugins** — `shepard-plugin-file-gridfs` (default) + `shepard-plugin-file-s3` (MinIO / S3 / Azure Blob / Ceph) — co-existing as first-class supported backends; operator picks per install via `shepard.payload.file.backend` | none | TBD | 📐 (queued, FS1a-b) | `aidocs/45 §3.2` + `aidocs/47 §3.2` |
| Presigned-URL `/v2/` endpoints for upload + download (frees backend from being the bytes proxy) | none | TBD | 📐 (queued, FS1c) | `aidocs/45 §4` |
| MinIO sidecar profile in compose (operator one-line switch) | none | TBD | 📐 (queued, FS1d) | `aidocs/45 §9` |
| `shepard-admin files migrate` CLI (greenfield / big-bang / dual-store-with-background-sweep) | none | TBD | 📐 (queued, FS1e) | `aidocs/45 §6` |
| Frontend large-file uploads via presigned PUT (P12) | proxied through backend | TBD | 📐 (queued, FS1f) | `aidocs/45 §9` / `aidocs/33` |
| RO-Crate export delivery via presigned URL (closes #27 / O3) | proxied | TBD | 📐 (queued, FS1g) | `aidocs/45` / `aidocs/31 §O3` |
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
| Container images published to GHCR (`ghcr.io/noheton/shepard-{backend,frontend}:{latest,sha-<7>,vX.Y.Z}`) | upstream publishes to gitlab.com | shipped via `.github/workflows/build-images.yml` | **✓ ↑** | `.github/workflows/build-images.yml` |
| **CodeQL** SAST (Java + JS/TS, `security-extended` query set) | none | shipped via `.github/workflows/codeql.yml`; weekly + per-PR; SARIF → Code Scanning | **✓ ↑** | `.github/workflows/codeql.yml` |
| **Trivy** container scan on every published GHCR image (CRITICAL+HIGH, ignore-unfixed) | none | shipped in `build-images.yml`; SARIF → Code Scanning per-image | **✓ ↑** | `.github/workflows/build-images.yml` |
| **SBOM** (CycloneDX) per published image via `anchore/sbom-action`; uploaded as artifact + attached to GitHub releases | none | shipped in `build-images.yml` | **✓ ↑** | `.github/workflows/build-images.yml` |
| **Dependency-review** (PR-time licence + new-CVE check) banning GPL/AGPL/SSPL families with `.github/dependency-review-config.yml` allowlist | none | shipped in `security.yml` | **✓ ↑** | `.github/workflows/security.yml` |
| Storage-backend plugin SPI (`PayloadKind` + `PayloadStorage`); new payload kinds drop in as plugins | none | TBD | 📐 (queued, PL1a) | `aidocs/47 §2` |
| Pilot migration: `spatial` → `shepard-plugin-spatial-postgis` | n/a | TBD | 📐 (queued, PL1b) | `aidocs/47 §3` |
| HDF5/HSDS (A5a) ships as a plugin from day 1 | none | TBD | 📐 (queued, PL1c) | `aidocs/35` + `aidocs/47` |
| Git references (G1a) ships as a plugin from day 1 | none | TBD | 📐 (queued, PL1d) | `aidocs/38` + `aidocs/47` |
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
| `POST /v2/files/{containerAppId}/upload-url`, `GET /v2/files/{appId}/download-url`, `GET /v2/artifacts/{type}/{id}/url` | FS1c, FS1g | `aidocs/45` |
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
| LUMEN-inspired showcase seed + analysis notebook | none | shipped (`examples/seed-showcase/`) | **✓ ↑** | PR #1001 |
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

**Shipped on this fork (vs upstream 5.2.0):** 6 DB-resilience improvements, 5 config/cache improvements, 1 API-key auth feature (L5), 4 security fixes (M2/M4/M5 + the L2a additive identifier substrate), 5 endpoint-additive features (P3, P14, R2/b/c/d/d2), the GitHub Pages docs site with three deploy guides, the LUMEN showcase seed + notebook, and **two Live tracking docs** (`aidocs/34` admin-facing + this matrix contributor-facing).

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
| **Helmholtz Unhide publish plugin** (`/v2/unhide/feed.jsonld` schema.org + metadata4ing JSON-LD feed for HKG / Unhide harvester; `:UnhideConfig` admin-configurable singleton + admin REST + `shepard-admin unhide ...` CLI parity for master toggle / feedPublic / contactEmail / harvest-key rotate / revoke; RFC 7807 envelopes for the disabled-feed / missing-key / read-only-field paths) | n/a | **UH1a shipped** — first `shepard-plugin-*` module ever; sources stitched into the backend uber-jar via build-helper (Phase 1 / ADR-0023 path; Phase 2 packaged-JAR drop-in when PluginManifest SPI lands). Harvest-key plaintext returned exactly once at mint, never logged (only the SHA-256 fingerprint), never enters `:Activity`. V29 migration ships the appId-unique constraint on the singleton label. Phase 1 lists every non-deleted Collection (per-Collection `publishToHelmholtzKG` toggle deferred to UH1d). UH1b (extend body with m4i `hasProcessingStep` fragments — gated on PROV1h), UH1c (cite KIP record per entity — gated on KIP1a), UH1d (frontend toggle + admin tile), UH1e (SHACL self-test) queued. | **✓ ↑** (UH1a, this commit) | `aidocs/67` + `aidocs/16` UH1a |
| **DBpedia Databus rich-reference plugin** (preview / description / title fetched + 24h-cached) | n/a | design done | 📐 (queued, REF1a; off-by-default until v1) | `aidocs/58 §7` |
| **GraphRAG on shepard** (embeddings per DataObject / Collection / lab-journal entry / Reference; similarity endpoint `GET /v2/search/similar?to=<appId>`; native Neo4j 5.13+ vector index — no extra service) | n/a | design done | 📐 (queued, GR1a) | `aidocs/58 §8` + `aidocs/43` |
| **HMC Kernel Information Profile baseline** (`Minter` SPI in core, `MockMinter` default impl, `MinterRegistry` discovery + fail-fast; `:Publication` entity + `HAS_PUBLICATION` edge; `POST /v2/{kind}/{appId}/publish` with Writer/Manager auth + idempotent re-POST + `?force=true` re-mint; unauthenticated `GET /v2/.well-known/kip/{pid-suffix}` returning the public HMC KIP JSON-LD record; `PublishableKindRegistry` for URL-segment dispatch — adding bundles/files/lab-journal-entries doesn't change the URL shape; V29 migration; ePIC + DataCite plugin shapes designed for KIP1c/d per ADR-0023 drop-in-JAR shape) | n/a | **KIP1a shipped** (61 new tests, 94.5% line / 80.4% branch on `de.dlr.shepard.publish.*` + `de.dlr.shepard.v2.publish.*`; KIP1b folded in) | **✓ ↑** (KIP1a, this PR) | `aidocs/66` / `aidocs/16 KIP1a` |

---

## Cross-references

- **Companion docs:** `aidocs/34` (admin-facing upgrade path), `aidocs/16` (live backlog), `aidocs/42` (researcher-facing vision), `aidocs/00-index` (full design corpus index).
- **Standing rules** in `CLAUDE.md`: API-version policy, vision-currency, upstream-upgrade-path tracking, this matrix.
