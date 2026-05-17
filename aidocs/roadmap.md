# Shepard Fork — Roadmap

**Last updated:** 2026-05-17  
**Fork baseline:** upstream `gitlab.com/dlr-shepard/shepard 5.2.0`  
**Live backlog:** [`16-dispatcher-backlog.md`](16-dispatcher-backlog.md)  
**Feature matrix:** [`44-fork-vs-upstream-feature-matrix.md`](44-fork-vs-upstream-feature-matrix.md)

This document is the **time-horizon view** of the fork.  The backlog is the
authoritative source for individual item status; this doc synthesises it into
a picture a PI or a new contributor can read in five minutes.

---

## Shipped (main, as of 2026-05-17)

### Infrastructure & security
- Bounded DB startup with exponential backoff (A1 series); per-DB health checks; graceful degradation (A1c)
- Automated DB recovery scheduler (A1f); parallel DB connectivity checks (P1)
- Cypher injection hardening — parameterised queries + allowlist everywhere (C5, C5b)
- RFC 7807 error responses (H4); `/v2/` routing shelf + split OpenAPI (`v1.json` / `v2.json`) (P4, P4c)
- Instance-admin role, dual-source (IdP + Neo4j) role check, bootstrap-token mechanism (A0)
- Permission cache with TTL/LRU + cache warming (A4, A4c); Neo4j-DOWN fail-closed guard (F5)
- JWT cache-key stamped with `iat` to prevent stale-permission replays (F4)
- Semi-permanent API keys with expiry (L5); `PublicEndpointRegistry` path-traversal fix (H5)

### Data model & API
- Application-generated UUIDs (`appId`) on all 28 entity types; Cypher reads via `appId` (L2a–L2c)
- Concurrent aggregate runtime feature toggles + admin API (A3, A3b, DX7)
- Pagination on 11 of 38 endpoints; NDJSON streaming ingest for timeseries (L6, P14)
- PATCH on Collection, DataObject, LabJournalEntry (P21 series)
- `revision` field on all versionable entities (V2a)
- RO-Crate selective export: per-payload selection, column/time-window picks, metadata redaction (R2–R2d2)
- OpenAPI `@Schema(name=…)` lint on all IO classes (P17b)

### Payload kinds
- **File storage SPI** (FS1a): `FileStorage` interface + GridFS default adapter; `shepard-admin storage status`
- **Presigned S3 URLs** (FS1c, FS1f, FS1g): frontend bypasses backend for large uploads; RO-Crate delivers via presigned GET
- **HDF5/HSDS sidecar** (A5a, A5b): `HdfContainer` CRUD; permission bridge; off-by-default profile
- **Git references** (G1a–G1d): loose-link, tracked-artifact (GitLab/GitHub/Gitea), per-user PAT cache
- **Lab journal** (J1a): CommonMark+GFM markdown render endpoint; `contentFormat: MARKDOWN`
- **Timeseries time reference** (TM1a): `timeReference` (WALL_CLOCK / EXPERIMENT_RELATIVE) + mutable `wallClockOffset` + `wallClockOffsetSource`; V37 backfill
- **Timeseries quality scoring** (AI1c): completeness/coverage/stability heuristic; background job (off by default)

### Semantic layer
- n10s plugin in Neo4j; `INTERNAL` semantic repository type; N10s bootstrap hook (N1a)
- Pre-seeded ontologies: PROV-O, DC, schema.org, FOAF, QUDT, OM-2, W3C Time, GeoSPARQL (N1b)
- Admin CLI for ontology refresh (N1c); runtime-configurable ontology bundles + custom-bundle upload (N1c2)

### User profile & provenance
- ORCID field + mod-11-2 validator; `PATCH /v2/users/me` (U1a)
- `displayName` override + `effectiveDisplayName` derivation; cryptic-username shortening (U1b)
- Per-user preferences (`GET/PATCH /v2/users/me/preferences`) — theme, language, JupyterHub URL, etc. (U1d)
- Role-in-context chip in collection sidebar header (U1c2)
- Provenance capture (PROV1 series): `:Activity` on all mutations; provenance architecture (PROV1a+)
- Unhide publish plugin shipped as the first real `shepard-plugin-*` module (UH1a)

### Payload versioning (PV1a, partial)
- `PayloadVersion` Neo4j entity + DAO; SHA-256 capture on file upload; `V41` uniqueness constraint

### Snapshots (V2a–V2e + UI1a)
- `revision` field on all versionable entities (V2a)
- `Snapshot` + `SnapshotEntry` model; `POST/GET /v2/collections/{appId}/snapshots` (V2b)
- Snapshot-pinned DataObject list (V2c); snapshot diff (V2e)
- **Snapshots UI** (UI1a): create/list/delete/diff panel on collection detail page (owner/manager only)

### Templates (T1a–T1f + UI2a)
- `:ShepardTemplate` Neo4j entity; admin CRUD at `/v2/templates`; JSON DSL body; copy-on-write versioning
- Server-side DataObject instantiation; YAML import/export (T1e, T1f)
- **Templates admin browser** (UI2a): `v-data-table` + create/edit/retire dialogs in `/admin` page

### UI overhaul (QW1–QW6, UI1a, UI2a, UI3a, UI8)
- Global search bar in header (QW1); sidebar data-object filter (QW2)
- JupyterHub URL in user profile (QW3); admin health dashboard (QW6)
- Git credentials shortcut in GitReferencesPane (QW4); publish tooltip (QW5)
- **RO-Crate download button** on collection pages (UI8)
- **Video inline viewer** (UI3a): `VideoStreamReferencesPane` with native `<video>` + ffprobe metadata chips
- LUMEN demo: 7 real elib.dlr.de publications + "Rocket Engine Hot-Fire Test Run" template seeded

### Demo & documentation
- Live demo at `https://shepard.nuclide.systems` with LUMEN-inspired showcase (alice/bob/admin)
- GitHub Pages site at `https://noheton.github.io/shepard/` with deploy guides and user docs
- cspell config for domain-specific vocabulary; README demo credentials
- Collection/Container duality design doc (aidocs/ops/87)

### Plugin system
- `PluginManifest` SPI + `PluginRegistry`; drop-in `/deployments/plugins/` JAR directory (PM1a)
- Admin REST (`GET/PATCH /v2/admin/plugins`) + CLI (`plugins list/enable/disable`) (PM1b)
- JAR signature verifier + semver-range compat enforcement + `DEGRADED` state (PM1b2)
- Plugin manifest metadata enrichment + dependency topological sort (PM1c)
- CLI extensibility SPI (`AdminCliCommandProvider`) + Unhide CLI moved into plugin (PM1d)
- Persistent runtime overrides (`PluginRuntimeOverride` Neo4j entity, survives restart) (PM1e)

### Developer experience
- `ShepardTestStack` unified testcontainer resource: Neo4j + MongoDB + PostgreSQL in parallel (DX1)
- `ShepardTestFixtures` typed builders for Collection / DataObject / User / Permissions (DX2)
- Admin CLI Phase 1: `shepard-admin features / health / migrations status` (L1 Phase 1)

---

## Near-term (ungated or gates cleared — Q2–Q3 2026)

| ID | Capability | Size | Gate |
|---|---|---|---|
| AI1r | Shepard experiment ontology (`shepard-experiment.ttl`) — 7 SKOS concept schemes (ExperimentPhase, MeasurementRole, QualityFlag, DefectType, InspectionMethod, ManufacturingProcess, SensorRole); uploaded via N1c2 | S | none |
| A2 | Decompose monolithic `TimeseriesRest` / `FileRest` / `CollectionRest` into JAX-RS sub-resources | L | DX1 ✓ |
| N1f | `/v2/semantic/{repoAppId}/sparql` SPARQL proxy (read-only, shepard auth) | M | none |
| ~~VID1a~~ | ~~Video upload: MP4/MOV/AVI/MKV/WebM; ffprobe wall-clock extraction; `VideoStreamReference` entity~~ — **shipped** (2026-05-17), UI3a inline viewer also shipped | S | ✓ |
| L4 | Search-as-you-type + ontology tree/graph view | M | none |
| L7 | Semantic annotations on file / structured / spatial payloads (not just DataObject) | L | none |
| P4b | OpenAPI client tree-shaking / code-splitting | S | none |
| FS1b | `shepard-plugin-file-s3` (AWS SDK v2, any S3-compatible endpoint) | M | FS1a ✓ |
| IX1a | `ExternalServiceStack`: Fuseki/Databus testcontainer for integration tests | M | DX1 ✓ |
| DX5 | Quarkus Neo4j Dev Service + OpenAPI hot-reload | M | none |

---

## Mid-term (gated — Q3–Q4 2026)

| ID | Capability | Gate |
|---|---|---|
| L2d | `/v2/` endpoints expose `appId` natively as the primary identifier | H4 ✓ |
| VID2 | Click-based camera–scene calibration (PnP solver via Open3D sidecar); stores `[:CAMERA_ALIGNED_TO]` matrix; unlocks 3D frustum + annotation projection | VID1a + PC1b |
| A5c–e | HDF5 phases 3–5: `HdfReference`, file-download fallback, auth bridge (h5pyd ergonomics) | A5a ✓ |
| U1c–e | User profile frontend split (`/me` route), preferences pane, avatar upload | U1a ✓, U1b ✓ |
| J1b–d | Lab journal: inline `.ipynb` render, "Open in Jupyter" deep link, edit history | J1a ✓ |
| G1c | Git pinned snapshot + RO-Crate `SoftwareSourceCode` integration (reproducible exports) | G1b ✓ |
| T1a+ | Templates system: `ShepardTemplate` entity → `AttributeSpec` → `FileSlot` → instantiation flow | — |
| PR1 | Process runtime: `ProcessDefinition` + `ProcessRun` stepper, SPW XML importer | T1b |
| V2b–e | Snapshots: freeze + pinned read path + diff tool + RO-Crate pin | V2a ✓ |
| AI1b | Anomaly detection: rolling-median + isolation-forest, `dlr:anomaly` annotation | none |
| AI1a | AI plumbing: per-user `ai.apiKey`/`ai.baseUrl`; admin fallback; `LlmClient`; sensitivity toggle | U1a ✓ |
| AI1e | Snap dashboards (Lumen): chat sidebar, closed tool-use catalogue, Vega-Lite rendering, `DashboardReference` save | AI1a + L2c ✓ |
| AI1q | Lumen SPARQL tool: `query_knowledge_graph(sparql)` in dashboard catalogue | AI1e + N1f |
| P10 | `POST /sql/timeseries` curated SQL-over-HTTP, three content types, streaming, caps | C5 ✓ |
| F1/F2 | Declarative `@Authz` annotation + Group/sharing model | — |
| R1 | Databus + MOSS federation plugin (`shepard-plugin-databus`) | PROV1a ✓ |
| PM1b3 | True runtime CDI plugin loading via Vert.x router (no Maven profile required) | PM1b2 ✓ |

---

## Long-term / vision (2027+)

| ID | Capability |
|---|---|
| PC1 | Point cloud integration: PLY/E57/LAS ingest, Open3D sidecar, ICP-based alignment |
| SB2 | Sensor binding v2: `CAMERA_FRUSTUM` overlay, timeseries-on-model projection, digital-twin camera viewport |
| DT1 | Live digital twin: real-time telemetry stream → 3D model deformation replay |
| EXP1 | Experiment coordinator service: OPC/UA trigger, sTC integration, KUKA, PLC writeback |
| PL1 / PV1 | Payload-kind SPI + payload versioning (SHA-256 dedup, per-Collection retention) |
| P11 | Apache Arrow Flight / DuckDB analytical endpoint |
| AAS1 | AAS registry sync plugin |
| UH1b+ | Unhide plugin phases: scheduled harvest, SHACL validation, reverse-index |
| D1 | In-app user docs (`/help` route, Playwright screenshot pipeline) |
| R1 (MOSS) | MOSS module (`shepard` indexer) + Databus federation fully live |
| AI1f–l | Natural-language search, annotation suggestion, auto-summarisation, notebook scaffolding |

---

## Blocking points (as of 2026-05-17)

The following items are the most critical gates. Clearing one typically unblocks
a cascade of mid-term work:

1. ~~**VID1a**~~ — **shipped** (2026-05-17); VID2, TM1 spatial+temporal, SB2 now unblocked
2. **AI1a** (AI plumbing) — needed before AI1d/e/f/g/h (the entire LLM feature set)
3. ~~**T1a**~~ — **T1a–T1f shipped** (2026-05-17); templates + processes, experiment coordinator unblocked
4. **N1f** (SPARQL proxy) — needed before AI1q (Lumen SPARQL tool)
5. **F1/F2** (auth annotation + groups) — needed before advanced sharing and OPA seam
