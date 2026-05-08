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
| Cypher injection — second wave (`*ReferenceDAO` family + `GenericDAO` + `VersionDAO` + `SemanticAnnotationDAO`) | injectable | TBD; same shape as C5 | 📐 (queued, C5b) | `aidocs/16` C5b |
| CORS allowlist instead of `origins=*` | wildcard | TBD | 📐 (queued) | `aidocs/07` C2 |
| Default-credential placeholders that fail at startup if not changed | accept shipped defaults | TBD | 📐 (queued) | `aidocs/07` H8 |
| OIDC `realm_access.roles` claim path configurable (multi-IdP) | hard-coded Keycloak shape | TBD | 📐 (queued, F8) | `aidocs/22 §4.11a.4` |
| Permission system: declarative `@Authz` annotation | path-segment switch | TBD | 📐 (queued, F1) | `aidocs/24` F1 / P5 |
| Group-based sharing model (`Group` node) | none | TBD | 📐 (queued, F2) | `aidocs/24` F2 |
| Permission audit log (Postgres) | none | TBD | 📐 (queued, F3) | `aidocs/24` F3 |

## 4. Identifiers (the L2 chain)

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Additive `appId` (UUID v7) on every Neo4j node-write | none | shipped via `HasAppId` mixin on 28 labels; minted by `GenericDAO` seam; `V11` per-label unique constraints | **✓ ↑** | L2a (commit `fec7979`) |
| Backfill `appId` for pre-L2a rows (`V12`) | n/a | shipped — chunked 10k rows per batch, idempotent, operator-run rollback file | **✓ ↑** | L2b (cherry-pick `796bc11`) |
| Read path uses `WHERE e.appId = $appId` | uses `id()` | TBD; gated on C5 | 📐 (queued, gated on C5) | L2c / `aidocs/25` |
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
| Pre-seeded common ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL) inside the existing Neo4j | none — operator must wire an external triple store | TBD | 📐 (queued, N1a + N1b) | `aidocs/48` |
| `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector` (Cypher / SPARQL via `n10s`) | none | TBD | 📐 (queued, N1a) | `aidocs/48 §3` |
| `shepard-admin semantic refresh-ontologies` CLI | none | TBD | 📐 (queued, N1c) | `aidocs/48` + `aidocs/22 §4.x` |
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
| HDF5 / HSDS as a payload kind (`HdfContainer` / `HdfReference`); `h5pyd` parity | none | TBD; HSDS sidecar + shared-Keycloak token relay | 📐 (queued, A5) | `aidocs/35` |
| Git integration (`GitReference`); 3 modes (loose / tracked / pinned-snapshot); commit-SHA in RO-Crate | none | TBD | 📐 (queued, G1) | `aidocs/38` |
| Templates feature (Templates Collection of DataObject blueprints; per-Collection allow-list) | none | TBD; replaces / supersedes upstream-aspirational L3 | 📐 (queued, T1) | `aidocs/39` |
| Process design + runtime in shepard core (`ProcessDefinition` + browser-hosted stepper) | SPW desktop only | TBD | 📐 (queued, PR1) | `aidocs/40 §2` |
| Snapshots (point-in-time, immutable, reproducible reads) | `Version` is a marker only | TBD; logical snapshots backed by entity revisions | 📐 (queued, V2) | `aidocs/41` |

## 10. User profile + settings

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| ORCID iD on user profile (#29) | none | TBD; mod 11-2 checksum, no network | 📐 (queued, U1a) | `aidocs/36` |
| `displayName` override + audit-trail render switch (#694) | username only | TBD | 📐 (queued, U1b) | `aidocs/36` |
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
| Channel-quality scoring | none | TBD | 📐 (queued, AI1c) | `aidocs/43 §3.2` |
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
| Admin CLI (`shepard-admin`) — read-only commands | none | designed; phased L1 | 📐 (queued) | `aidocs/22` |
| Admin CLI cleanup of soft-deleted entities (TTL) | none | designed | 📐 (queued, L1 phase 2) | `aidocs/22 §4.1` |
| Admin CLI RO-Crate import / export | none | designed | 📐 (queued, L1 phase 3) | `aidocs/22 §4.7` |
| Admin CLI feature-toggle inspection / flipping (incl. profile-bound) | none | designed | 📐 (queued) | `aidocs/22 §4.6 / §4.6a` |
| `shepard-admin init` TUI wizard for first-run `.env` (Lanterna) | none | designed | 📐 (queued, L1 phase 1) | `aidocs/22 §4.11` |
| Universal TUI mode for every command (auto-fill from server state) | none | designed | 📐 (queued) | `aidocs/22 §4.x` |
| Env-driven auth discovery (`SHEPARD_HOST` / `SHEPARD_API_KEY`) for the CLI | none | designed | 📐 (queued, L1 phase 1) | `aidocs/22 §3.4` |
| Init wizard's OIDC sub-flow (Keycloak / Pocket ID / external w/ auto-discovery) | none | designed; depends on F8 (configurable claim path) for non-Keycloak | 📐 (queued) | `aidocs/22 §4.11a` |

## 13a. File storage backend

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Files stored in MongoDB GridFS (1 MiB chunks, one bucket per FileContainer) | **=** | **=** (today) | **=** | `FileService.java` |
| Pluggable `FileStorage` interface (GridFS default, S3-compatible opt-in via MinIO / S3 / Azure Blob / Ceph) | none | TBD | 📐 (queued, FS1a-b) | `aidocs/45` |
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

## 13c. Plugin system + dev experience

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Storage-backend plugin SPI (`PayloadKind` + `PayloadStorage`); new payload kinds drop in as plugins | none | TBD | 📐 (queued, PL1a) | `aidocs/47 §2` |
| Pilot migration: `spatial` → `shepard-plugin-spatial-postgis` | n/a | TBD | 📐 (queued, PL1b) | `aidocs/47 §3` |
| HDF5/HSDS (A5a) ships as a plugin from day 1 | none | TBD | 📐 (queued, PL1c) | `aidocs/35` + `aidocs/47` |
| Git references (G1a) ships as a plugin from day 1 | none | TBD | 📐 (queued, PL1d) | `aidocs/38` + `aidocs/47` |
| Codegen archetype `mvn shepard:scaffold-payload-kind` | none | TBD | 📐 (queued, DX3) | `aidocs/47 §2.5` |
| `make dev` single-command bootstrap (init wizard + compose up + smoke) | none | TBD | 📐 (queued, DX4) | `aidocs/47 §4.4` |
| Unified `ShepardTestStack` testcontainer resource | none | TBD | 📐 (queued, DX1) | `aidocs/47 §4.1` |
| BI integrations — Grafana data-source plugin + Superset SQLAlchemy recipe | none | TBD; "SQL win" via P10 (C5 cleared) | 📐 (queued, DX8) | `aidocs/47 §4.8` + `aidocs/29` |

## 14. RO-Crate optimisation

| Capability | Upstream | This fork | Status | Refs |
|---|---|---|---|---|
| Selective export ✓ (see §5 above) | GET-only | **✓** (R2 series shipped) | **✓ ↑** | §5 |
| Streaming RO-Crate export for large Collections | possible OOM | TBD | 📐 (queued) | `aidocs/31` |
| Long-running export pattern (job-id polling) | synchronous only | TBD | 📐 (queued) | `aidocs/32` |
| Reproducible-by-snapshot exports | n/a (no snapshots) | TBD; lands at V2d | 📐 (queued, V2d) | `aidocs/41 §5` |

## 14a. API structure — endpoint-by-endpoint delta

The `/shepard/api/...` surface stays byte-frozen with upstream 5.2.0
per `CLAUDE.md`. This section enumerates **what's been added to
`/v2/...`** (or designed for it). Endpoints marked `📐` are in
the design corpus; `🚧` is in flight; `✓` ships.

### 14a.1 What `/shepard/api/...` looks like (upstream parity)

Every upstream 5.2.0 path that exists today still exists with
identical wire shape. No comprehensive enumeration here — the
upstream OpenAPI is the canonical reference. **Two operator-visible
behaviour changes** preserve the wire shape but tighten internals:

| Endpoint group | Internal change | User-visible? |
|---|---|---|
| `/shepard/api/.../search` | Cypher now parameter-bound (C5); identifiers whitelisted | No — request body unchanged |
| `/shepard/api/.../healthz` | Per-DB up/down state in body (A1b) | Additive fields only |
| `/shepard/api/timeseriesContainers/{id}/payload` | Now also accepts `application/x-ndjson` (P14) | Additive content-type |
| `/shepard/api/collections/{id}/export` (GET) | Unchanged | No |

### 14a.2 New `/v2/...` endpoints — shipped

| Endpoint | Status | Origin |
|---|---|---|
| `POST /v2/collections/{appId}/export` (body-form ExportSelection) | ✓ shipped | R2 / `aidocs/16` |
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
| `GET /v2/admin/features`, `PATCH /v2/admin/features/{name}` | DX7 / A3b / `aidocs/22 §4.6` | `aidocs/47` |
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
| Free-test-deploy guide for Oracle Cloud Free Tier | none | shipped (`docs/deploy-oracle-free.md`) | **✓ ↑** | PR #1004 |
| Self-hosted-behind-Zoraxy guide (incl. existing-host dev workflow §5a) | none | shipped (`docs/deploy-self-hosted-zoraxy.md`) | **✓ ↑** | PR #1005 / PR #1016 |
| Live researcher-facing vision doc | none | shipped (`aidocs/42-vision.md`, Live status) | **✓ ↑** | `aidocs/42` |
| Live ecosystem doc (SPW + sTC + others) | none | shipped (`aidocs/40-ecosystem.md`) | **✓ ↑** | `aidocs/40` |
| Upstream upgrade-path tracker (admin-facing) | n/a | shipped (`aidocs/34-upstream-upgrade-path.md`, Live) | **✓ ↑** | `aidocs/34` |
| **This** fork-vs-upstream feature matrix (contributor-facing) | n/a | this doc | **✓ ↑** | this doc |
| LUMEN-inspired showcase seed + analysis notebook | none | shipped (`examples/seed-showcase/`) | **✓ ↑** | PR #1001 |
| Upstream-current parallel import script (`import_upstream.py`) for the same showcase data | n/a (the upstream itself) | shipped | **✓ ↑** | PR #1001 |

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

**In flight (agents dispatched):** C5 (Cypher injection fix; gates L2c) and L2b (V12 backfill of `appId`).

**Designed and queued (substantial):** the entire L2 chain after L2a (b/c/d/e), unified search + pagination (`aidocs/13`), semantic-annotation expansion (`aidocs/14`), HDF5/HSDS (A5), Templates (T1), Process design+runtime (PR1), Git integration (G1), User profile (U1), Lab journal v2 + Jupyter (J1), Snapshots (V2), AI features w/ snap-dashboards killer feature (AI1), Admin CLI (L1), permission-system evolutions (F1-F8), provenance (`aidocs/30`).

**Headline next-horizon line items** (per `aidocs/42` vision):
1. Snap dashboards (AI1e) — the killer feature
2. HDF5 / HSDS (A5)
3. Templates + processes (T1 + PR1)
4. User profile + ORCID (U1)

---

## Cross-references

- **Companion docs:** `aidocs/34` (admin-facing upgrade path), `aidocs/16` (live backlog), `aidocs/42` (researcher-facing vision), `aidocs/00-index` (full design corpus index).
- **Standing rules** in `CLAUDE.md`: API-version policy, vision-currency, upstream-upgrade-path tracking, this matrix.
