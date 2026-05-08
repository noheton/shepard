# aidocs — Reading Order & Index

This directory holds the AI-assisted analysis and design notes for
shepard. **Each file has a stable numeric prefix** — refs like
`aidocs/16-dispatcher-backlog.md` stay valid across history; the
**chapter grouping** is the recommended reading order and the way
related docs cluster topically.

---

## Table of contents

- [Reading paths](#reading-paths) — pick the path that matches your role
- [Chapter A. Situation reports](#chapter-a--situation-reports) — `01–10`
- [Chapter B. Plan & live work](#chapter-b--plan--live-work) — `11`, `15`, `16`
- [Chapter C. Architecture, operations, roadmap](#chapter-c--architecture-operations-roadmap) — `12`, `17`, `19`, `20`
- [Chapter D. API surface & clients](#chapter-d--api-surface--clients) — `18`, `23`, `26`, `27`, `28`, `29`, `31`, `32`
- [Chapter E. Search, semantics, knowledge graph, lineage](#chapter-e--search-semantics-knowledge-graph-lineage) — `13`, `14`, `30`
- [Chapter F. Identity, auth, permissions, identifiers](#chapter-f--identity-auth-permissions-identifiers) — `24`, `25`
- [Chapter G. Demand, frontend & operator tooling](#chapter-g--demand-frontend--operator-tooling) — `21`, `22`, `33`
- [Sub-pages: migration / operator notes](#sub-pages-migration--operator-notes)
- [Cross-chapter interlocks](#cross-chapter-interlocks) — items that thread through several chapters
- [Snapshot date and provenance](#snapshot-date-and-provenance)

---

## Reading paths

Three suggested paths through the corpus, by role:

- **New to the repo.** Read **01** for layout and tech stack → **11**
  for the master plan → skim **20** for the strategic 6-month picture
  → bookmark **16** as the live ledger and dip into the chapter that
  matches the next concrete task.
- **Maintainer planning a release.** Open **16** (the live backlog) and
  follow each item to its design doc in C–G. Pay attention to the
  [cross-chapter interlocks](#cross-chapter-interlocks) section below
  before sequencing dependent work.
- **Architect or reviewer doing depth.** Read **19** (architecture
  feedback) for the honest fragility ranking → **23**, **24**, **25**
  for the API / permissions / identifier deep-dives → **28** for the
  integrated paradigms-and-clients synthesis.

**The single live source-of-truth** for "what's queued / dispatched /
done / blocked / parked" is `16-dispatcher-backlog.md`. This index does
not duplicate state; it points at the design docs the backlog
references.

---

## Chapter A — Situation reports

What is in the repo today, what is open on GitLab, what is mirrored on
GitHub, where the gaps are. **Audience: anyone new + the maintainers
doing triage.**

| # | File | Purpose | Audience |
|---|---|---|---|
| 01 | [`01-repo-overview.md`](01-repo-overview.md) | Top-level scope, layout, goals, constraints, tech stack | Anyone new to the repo |
| 02 | [`02-cluster-map.md`](02-cluster-map.md) | Cluster view of related issues / MRs / files; epics; cross-cutting concerns | Maintainers planning work |
| 03 | [`03-issues-status.md`](03-issues-status.md) | Per-issue gauge across all 166 open GitLab items (effort / complexity / value / staleness / implementation status) | Triage / planning |
| 04 | [`04-reconciliation.md`](04-reconciliation.md) | GitHub mirror vs GitLab authoritative — sync state, gaps, mirror artifacts | Mirror operators |
| 05 | [`05-dependency-report.md`](05-dependency-report.md) | Dependency landscape (Renovate, version pins, upgrade pressure) | Renovate / supply-chain |
| 06 | [`06-code-quality.md`](06-code-quality.md) | TODO / FIXME inventory, weak tests, dead code | Code-quality cleanup |
| 07 | [`07-security-issues.md`](07-security-issues.md) | Critical (C1-C5), High (H1-H8), Medium (M1-M12), Low findings | Security work |
| 08 | [`08-first-issues.md`](08-first-issues.md) | Small, fresh, high-value issues to ship first | New contributors / Phase 2 |
| 09 | [`09-ready-to-close.md`](09-ready-to-close.md) | Items ready for closure with confidence levels and draft comments | Maintainer pass |
| 10 | [`10-cleanup-plan.md`](10-cleanup-plan.md) | Original GitHub-mirror cleanup plan | Reference |

---

## Chapter B — Plan & live work

The master plan and the live ledger that tracks delivery against it.
**Key takeaway:** start every working session at `16` so you know what
just landed and what's blocked.

| # | File | Purpose |
|---|---|---|
| 11 | [`11-implementation-plan.md`](11-implementation-plan.md) | Phased plan (Phase 0 housekeeping → Phase 6 backlog closure). The single document the team should drive against. |
| 15 | [`15-phase-0-status.md`](15-phase-0-status.md) | Progress note for Phase 0 housekeeping (what was done, what remains, research-closure check). |
| 16 | [`16-dispatcher-backlog.md`](16-dispatcher-backlog.md) | **The live ledger.** Every backlog item from `input/input_raw.md` and from the design docs in C–G with status (queued / dispatched / done / blocked / parked), commit hashes, and per-round dispatch logs. |

---

## Chapter C — Architecture, operations, roadmap

Performance, reliability, deployment, and the strategic catalogue of
epics. **Key takeaway:** `19` is honest about today's fragilities; `20`
sequences fixes across two parallel tracks; `12` and `17` ground both
in measured behaviour.

| # | File | Topic | Status |
|---|---|---|---|
| 12 | [`12-timescaledb-performance-analysis.md`](12-timescaledb-performance-analysis.md) | Schema, ingest, query, JDBC, ops; **§11** covers streaming the read path end-to-end and aligning the timeseries `id` (used by semantic annotations) with the 5-tuple (used by data endpoints) | Active — V1.8.0 schema fixes merged; sprint-1 mitigations open |
| 17 | [`17-startup-wait-audit.md`](17-startup-wait-audit.md) | Audit of Mongo / Flyway / JDBC startup wait/retry semantics against the Neo4j 60s ceiling from A1 (configuration-only follow-up) | Audit |
| 19 | [`19-architecture-feedback.md`](19-architecture-feedback.md) | Critical architectural review: where shepard is strong vs fragile; cross-cutting concerns; risks for the proposed `13` / `14` work; 6-month recommendation list mapped to backlog IDs in `16`; "deliberately don't do" list. Ground-truthed against post-Round-3 code state | Review |
| 20 | [`20-epic-roadmap.md`](20-epic-roadmap.md) | Strategic catalogue of 14 candidate epics across foundations, search/semantics, data types, performance, UX/ecosystem, KG interfaces — each with goal, scope, dependencies, T-shirt size, and a map to backlog IDs in `16`. Includes a Mermaid dependency graph and a two-track 6-month plan | Roadmap |

---

## Chapter D — API surface & clients

The REST surface (today vs. evolved), client generation, and the
convenience-layer plan. **Key takeaway:** `28` is the integrated
synthesis — read it before any of `23` / `26` / `27` to avoid getting
stuck in the per-slice details. The convenience wrapper (`27`, P16) is
the multiplexer that keeps the four-surface picture (REST core + SQL
side door + S3-presigned + SSE) hidden from end-users; without it the
"better" answer would just shift the clunkiness to the client side.

| # | File | Topic | Status |
|---|---|---|---|
| 18 | [`18-pagination-inventory.md`](18-pagination-inventory.md) | Inventory of every list-returning REST endpoint with current pagination state, row-count risk, frontend usage, and a sized rollout plan; convention recommendation that diverges from `13`'s cursor proposal for the existing list surface | Research |
| 23 | [`23-api-critique.md`](23-api-critique.md) | API critique: clunkiness pain points, redundancies, per-slice paradigm fit (SQL-over-HTTP, S3-presigned, SSE), client generation, schema source-of-truth (stay annotation-generated OpenAPI). Spawns IDs P5–P20 | Review |
| 26 | [`26-crud-consistency.md`](26-crud-consistency.md) | CRUD consistency table: 29 resources × 153 endpoints (85 GET / 33 POST / 10 PUT / 25 DELETE / **0 PATCH**); 28% have full CRUD; inconsistencies inventory; top-5 cleanup wins mapped to A2 / P5 / P9 | Inventory |
| 27 | [`27-convenience-clients-design.md`](27-convenience-clients-design.md) | `shepard-py` and `shepard-ts` convenience layer (P16) — `Client` shape, 14-line vs 3-line worked example, "no new dependencies" interrogation (true with `[pandas]` / `[excel]` extras), pagination iterator, three workflow helpers (`to_pandas`, `to_excel`, `ro_crate`), TypeScript counterpart, 4-phase rollout | Design |
| 28 | [`28-paradigms-and-clients-synthesis.md`](28-paradigms-and-clients-synthesis.md) | **Integrated synthesis** of `23 §4` (paradigms) + `§5` (client generation): four surfaces (REST core + SQL side door for bulk reads + S3-presigned for blobs + SSE for change-feeds), one schema source, one generator, one wrapper per language as multiplexer. Maintenance-cost ledger fits the budget; 12-month critical path. Spawns P22 + P23 | Synthesis |
| 29 | [`29-p10-implementation-design.md`](29-p10-implementation-design.md) | P10 (`POST /sql/timeseries`) implementation design — JSON DSL request body, Cypher-first permission flow via `filterAllowedForUser` (post-P2), three-format content negotiation (CSV / JSON / NDJSON; Arrow deferred to P11), 1M-row + PT60S caps, file-level layout under `data/timeseries/sql/`, **gated on C5**. 3-phase rollout (P10a / P10b / P10c) | Design |
| 31 | [`31-rocrate-export-optimisation.md`](31-rocrate-export-optimisation.md) | RO-Crate export performance: bottleneck inventory (whole-tree single-transaction walk, `byte[]`-materialised payloads, full-ZIP buffered into `ByteArrayOutputStream` before first byte, per-entity permission checks), 10 ranked optimisations (O1–O10), measurement plan (benchmark harness at S/M/L sizes + Micrometer phase timers), 4–6-phase rollout. **Top 3 ROI: O1 stream the ZIP / O6 batch permissions / O2 async export job** (the last gated on `aidocs/32`) | Performance |
| 32 | [`32-long-running-process-pattern.md`](32-long-running-process-pattern.md) | Shared async-job pattern modelled on P3's `migration_progress` precedent: single Postgres `job` table, small `JobService` surface, `quarkus-scheduler`-driven worker against a bounded virtual-thread pool, `202 + Location: /jobs/{id}`, polling or SSE for progress, explicit `DELETE` for cooperative cancellation. Decision rule: per-request budgets (≤PT30S reads, ≤PT5M writes); over-budget falls through to a job. First two adopters: **R2 export** (pairs with `aidocs/31` O3) and **P14 NDJSON ingest** | Pattern |

---

## Chapter E — Search, semantics, knowledge graph, lineage

Discoverability, annotation model generalisation, the triplestore
path, and provenance / lineage. **Key takeaway:** `12 §11.B → 13 → 14`
is a deliberate triplet — identifier discipline first, then unified
search, then the annotation model. **`30` rides `14`'s triplestore**:
PROV-O lineage triples live alongside the annotation triples once
`14 §5` lands, with a Neo4j-relations fallback until then.

| # | File | Topic | Status |
|---|---|---|---|
| 13 | [`13-search-improvements.md`](13-search-improvements.md) | One unified search endpoint replacing today's five; richer query syntax (predicate JSON + fulltext + raw escape hatches: SPARQL/SQL); searching by semantic annotation; cursor pagination + streaming | Proposal |
| 14 | [`14-semantic-improvements.md`](14-semantic-improvements.md) | Generalising semantic annotations to file / structured / spatial payloads; label vs IRI discipline; search-as-you-type for terms; triplestore (n10s on Neo4j → GraphDB / RDF4J) for SPARQL, reasoning, KG export, FAIR | Proposal |
| 30 | [`30-provenance-and-lineage-design.md`](30-provenance-and-lineage-design.md) | Provenance + data lineage design. Three kinds of provenance (operational / derivation / publication); decision is **OpenLineage *and* PROV-O** via one ~150-LoC mapping layer; capture via JAX-RS filter (operational) + optional `derivedFrom` DTO field (derivation) + RO-Crate `provenance.json` riding R2's `ExportSelection` (publication); query at `GET /entities/{kind}/{id}/lineage` with per-node permission redaction via P2's `filterAllowedForUser`; emission via `aidocs/32`'s job pattern. Spawns sub-IDs **R3a–R3e** (~6.5 eng-weeks). **L2 coupling explicit** — bundle R3a with L2a | Design |

### Dependency graph among Chapter E + `12 §11.B`

```
12 §11.B (id alignment)
   ↓
13 (cross-store search) ──► 14 (annotation model)
   ↓                          ↓
13 §6 cross-store planner ──► 14 §5 triplestore
```

---

## Chapter F — Identity, auth, permissions, identifiers

Who can do what, and how things are named. The Neo4j-id migration is
in this chapter (not Architecture) because identifier choice cuts
through the auth and permission caches more than through performance.
**Key takeaway:** L2's read-path switch and any future
parameterised-Cypher refactor are tightly coupled — see the
[interlocks](#cross-chapter-interlocks) below.

| # | File | Topic | Status |
|---|---|---|---|
| 24 | [`24-permission-system-review.md`](24-permission-system-review.md) | Per-entity discretionary access model in Neo4j; fragilities ranked by blast-radius (C3 fail-open default, path-segment-switch dispatch, missing admin role, no group sharing, cache-key blindness, no audit trail, cross-DB consistency under degraded Neo4j); sized evolutions F1–F7 + L8 unpacking; verdicts on policy-as-code (no, but design F1 to allow drop-in) and row-level security in data DBs (no for v1) | Review |
| 25 | [`25-neo4j-id-migration-design.md`](25-neo4j-id-migration-design.md) | Migration from deprecated `id()` (and `elementId()`) to **application-generated IDs** (UUID v7). 5-phase rollout with Cypher-injection / cache-key / path-segment-switch interlocks. **L2c is gated on C5**, **L2d is gated on P4 + H4**. Spawns L2a–L2e | Design |

---

## Chapter G — Demand, frontend & operator tooling

Demand-side research, the operator-facing CLI design, and the
frontend-workflow analysis. **Key takeaways:** (a) of the three
candidate development directions in `21` (HDF5, tabular, KG), **KG is
the strongest demand signal**; HDF5 carries a hard `h5py`/`h5pyd`
compatibility constraint (recorded in epic E7); tabular is thin and
should be deferred until separated into "interface vs storage";
(b) the admin CLI (`22`) is design-only — Phase 1 unblocks once A0
(admin role mechanism) is decided; (c) `33` corrects an earlier
mistaken assumption: **the frontend stack is Nuxt 3 / Vue 3 /
Vuetify 3, not Angular** — verified against `frontend/package.json`.

| # | File | Topic | Status |
|---|---|---|---|
| 21 | [`21-user-interest-gauge.md`](21-user-interest-gauge.md) | Gauge of repo-internal demand signals for HDF5/HSDS, tabular/relational storage, and knowledge-graph interfaces. Verdicts: **KG interfaces** strongest, **HDF5** low-medium with hard `h5py`/`h5pyd` compatibility constraint (per epic E7), **tabular** thin (defer). Recommends a lightweight survey + interview plan | Research |
| 22 | [`22-admin-cli-draft.md`](22-admin-cli-draft.md) | Candidate-function design for a future `shepard-admin` CLI: goals/non-goals, auth model (blocked on A0), per-command catalogue, framework recommendation (Java + Picocli), distribution, 4-phase rollout | Draft |
| 33 | [`33-frontend-workflow-analysis.md`](33-frontend-workflow-analysis.md) | Frontend (Nuxt 3 / Vue 3 / Vuetify 3) workflow analysis: top 8–12 user-facing flows with click-cost + API-call inventory; friction by workflow with code citations; **top-5 fixes ranked** (W11 search-as-you-type / W6+W7 unify "Add data" / W7 live upload progress / W8 "Download as Excel" / W2-W3-W5 inline editing); how each post-R2 / P10 / P12 / P13 / P16 backend change simplifies the frontend; **Playwright over Cypress** for R5; R8 DLR-CD theming guidance | Review |
| 34 | [`34-upstream-upgrade-path.md`](34-upstream-upgrade-path.md) | **Live ledger** of every merged change on `main` that an admin upgrading from upstream `dlr-shepard/shepard 5.2.0` needs to know about. Status legend (ZERO / CONFIG / AWARE / BREAKING), per-change row with config and migration impact, operator quick-start, "what's coming" for the L2 chain, image-build notes. Standing rule lives in `CLAUDE.md` at the repo root | Live |
| 35 | [`35-hdf5-hsds-implementation-design.md`](35-hdf5-hsds-implementation-design.md) | Implementation design for backlog series **A5** (HDF5 / HSDS support), refining epic E7. Picks **HSDS sidecar + shared-Keycloak token relay** out of three architectures, defines the URL hierarchy (mirrors HSDS so `h5pyd` works unchanged), permission bridge (shepard graph → HSDS POSIX-style ACL, shepard is source of truth), POSIX/S3/MinIO storage choice, A5a–A5e phasing with A5a → A5d → A5e → A5b → A5c order, `h5pyd` parity test strategy, and the `V13__Add_appId_constraint_HdfContainer_HdfReference.cypher` migration shape. Gated on L2c | Design done |
| 36 | [`36-user-profile-and-settings-design.md`](36-user-profile-and-settings-design.md) | Implementation design for the **U-series** (user profile + account settings). Closes #29 (ORCID, mod 11-2 checksum validation), addresses #694 (display first/last name not username) and #628 (cryptic Keycloak usernames). **Reassesses the Configuration UI** — recommends a **split** (`/me` for personal, `/admin` for shared/admin) over merging. Avatar via tiered shepard-upload → IdP `picture` → Gravatar precedence chain. Preferences inventory (theme, language, timeZone, defaultPageSize, defaultLandingPage; secret-class entries for `git.pat`/`git.host` and `editor.preferredJupyter` slot for the upcoming Jupyter feature). `SettingDescriptor` enum + typed map keeps the schema additive. `/users/me` PATCH uses `application/merge-patch+json` per P21x. U1a–U1f phasing, recommended order U1a → U1b → U1c → U1d → U1e | Design done |
| 37 | [`37-lab-journal-and-jupyter-design.md`](37-lab-journal-and-jupyter-design.md) | Implementation design for the **J-series** (lab journal v2 + Jupyter integration). Markdown body interpretation (CommonMark + GFM, sanitised), inline `.ipynb` static render via client-side nbviewer (defers live kernel as out-of-scope for v1), "Open in Jupyter" deep link consuming `editor.preferredJupyter` from `aidocs/36 §3.2`, append-only edit history. Closes #507 perf via L6 pagination dependency. New endpoints land at `/v2/lab-journal/{appId}/render` and `/v2/lab-journal/{appId}/notebooks` per the API-version policy. J1a–J1f phasing | Design done |
| 38 | [`38-git-integration-design.md`](38-git-integration-design.md) | Concept design for the **G-series** (git integration / artifact tracking). New `GitReference` payload-kind alongside FileReference / TimeseriesReference. Three modes: (a) loose link, (b) tracked artifact via per-user `git.pat` from `aidocs/36 §3.2`, (c) pinned snapshot for reproducible RO-Crate exports (`aidocs/31` integration emits `SoftwareSourceCode` with immutable commit SHA). Per-host adapters (GitLab/GitHub/Gitea), no JGit dep, file-fetch only via host REST APIs. All endpoints land under `/v2/` per the API-version policy. G1a–G1f phasing, G1a ships with zero new dependencies | Design done |
| 39 | [`39-templates-design.md`](39-templates-design.md) | Implementation design for the **T-series** (templates) — reconciles backlog L3 ("YAML-defined") with issue #630 ("Templates Collection of DataObject blueprints" + Figma) and bakes in the user constraint that **Collection owners decide which templates from the global repository are allowed in their Collection**. Recommendation: ship #630 storage shape primary, YAML as optional admin interchange tool. New `__templates` Collection auto-created at start, admin-edited only. `AttributeSpec` (required/type/enum) + `FileSlot` (required/MIME) for typed blueprints. `Collection.allowedTemplateAppIds` for the per-Collection allow-list. Instances pinned via `templateVersion`. `[:CREATED_FROM_TEMPLATE]` graph edge + sneaker attribute. All 9 open decisions explicitly resolved. Gated on L2c. T1a–T1h phasing | Design done |
| 40 | [`40-ecosystem.md`](40-ecosystem.md) | shepard ecosystem inventory + integration recommendations. Section 2 details **bringing process design + runtime into shepard core** as a templates extension (PR-series), retiring shepard-process-wizard's desktop runtime over time while keeping it as a design-side assistant. Section 3 lists 10 prioritised improvements for shepard-timeseries-collector (NDJSON via P14, REST control surface, A1b health-shape, L2c-aware containers, OpenLineage emission, MQTT 5, Modbus/REST sources, Java 21 LTS, schema-aware sources, per-source backpressure). Cross-tool concerns (OpenAPI versioning split, key-expiry handling) | Concept done |
| 41 | [`41-snapshots-design.md`](41-snapshots-design.md) | Snapshots design — make point-in-time, immutable, reproducible reads of an entire Collection subtree a first-class concept on top of today's `Version` marker. Picks **logical snapshots backed by entity revisions** (option C) over deep-copy / COW; storage cost is O(entities-in-scope), not O(payload bytes). New `Snapshot` + `SnapshotEntry` Neo4j model; reads via `?snapshot={appId}` query param rewrite each entity reference to the pinned revision. RO-Crate export against a snapshot is reproducible by construction. V2a–V2f phasing; V2a unblocks revision counter (independent value) before snapshots themselves land at V2b. Gated on L2c | Design done |
| 42 | [`42-vision.md`](42-vision.md) | **Live vision doc for researchers** — explains shepard in one paragraph, who it's for, what's in the box today, the cross-cutting features, what it isn't (not a PLM, not a HPC scheduler, not a code repo), and where it's going. Reading-path links to `docs/admin.md` (operator), the OpenAPI (API user), the showcase notebooks (worked example), and the design corpus. The standing rule in `CLAUDE.md` makes this doc's update mandatory in any user-visible PR — a stale vision is worse than no vision | Live |
| 43 | [`43-ai-opportunities.md`](43-ai-opportunities.md) | AI opportunities — traditional ML (anomaly detection, channel-quality scoring, forecasting, outlier detection, similarity search, learn-to-rank) + LLM integration with **snap dashboards (§5.8) as the killer feature** — Claude-chat-style sidebar with closed tool-use catalogue, inline Vega-Lite v5 rendering, no code-exec sandbox. **shepard ships zero AI models**; the `LlmClient` talks to whatever OpenAI-compatible endpoint the user (BYOK via `ai.apiKey` / `ai.baseUrl` / `ai.model`) or admin (`shepard.ai.fallback.*`) configures. Resolution rule: user-key → admin-fallback → AI features hidden. AI1 series with 16 sub-IDs (AI1a–AI1p); AI1b/AI1c are LLM-independent and ship anywhere; AI1e is the killer-feature snap-dashboards slice gated on AI1a + L2c | Survey |
| 44 | [`44-fork-vs-upstream-feature-matrix.md`](44-fork-vs-upstream-feature-matrix.md) | **Live feature matrix** comparing this fork against upstream `dlr-shepard/shepard 5.2.0` across 17 areas (DB resilience, config, auth, identifiers, API additions, search, semantic, provenance, payload kinds, profile, journal, AI, admin, RO-Crate, API versioning, docs, ecosystem). Status legend `✓` shipped / `📐` designed / `🚧` in-flight / `↑` extends-upstream. Different audience from `aidocs/34` — that doc is admin-facing migration ledger; this is contributor-facing progress tracker with both shipped AND designed-not-yet-shipped work. Updates in same PR as any feature/design landing per `CLAUDE.md` standing rule | Live |
| 45 | [`45-gridfs-to-s3-evaluation.md`](45-gridfs-to-s3-evaluation.md) | Evaluation of migrating shepard's primary file storage from MongoDB GridFS to S3-compatible object storage (S3 / MinIO / Azure Blob / Ceph). Recommendation: **pluggable `FileStorage` interface** with GridFS staying default and S3 as opt-in; presigned-URL `/v2/` endpoints unblock W1 (frontend uploads, RO-Crate delivery, long-running result URLs) without forcing a migration. Closes the long-open issue #27. FS1a–FS1h phasing with FS1a as a behaviour-preserving refactor that ships independently. Migration runway covers greenfield, big-bang, and dual-store-with-background-sweep modes | Design done |
| 46 | [`46-payload-versioning-design.md`](46-payload-versioning-design.md) | Payload versioning — extends V2 entity-revision snapshots (`aidocs/41`) down to the **payload bytes** (file content, structured docs, geometry, timeseries). New `PayloadVersion` Neo4j sub-node per re-upload; SHA-256 dedup avoids no-op churn; cross-store atomicity via payload-first / Neo4j-second + orphan-byte GC sweep. Snapshots pin **dual-axis** `(entityRevision, payloadVersion)` → byte-identical RO-Crate exports. PV1a–PV1h phasing; per-payload-kind shape per `FileReference` (easy), `StructuredDataReference` (easy), `SpatialDataReference` (PostGIS row groups), `TimeseriesReference` (re-ingest as new version, separate from append-only writes). FS1 S3 backend is the cheapest implementation platform | Design done |
| 47 | [`47-dev-experience-and-plugin-system.md`](47-dev-experience-and-plugin-system.md) | Dev experience + storage-backend plugin SPI. **§1.0 casual-user north star** (every roadmap item judged against "does this make the casual path easier?") drives the design. **§2 PayloadKind / PayloadStorage SPI** generalises the FS1 FileStorage interface to all payload kinds; new payload kinds drop in as plugins (HDF5/HSDS, Git, future kinds) instead of a 12-file PR-archeology dive. **§3 migrates existing storage-bound feature flags** (`spatial`, future `hdf`, future `files-s3`) to plugins. **§4 dev-ex improvements**: unified test-resource (DX1), shared fixtures (DX2), codegen archetype `mvn shepard:scaffold-payload-kind` (DX3), `make dev` (DX4), Quarkus dev-mode polish (DX5), RFC 7807 (DX6), feature-toggle introspection (DX7), **§4.8 BI integrations** (Grafana plugin + Superset SQLAlchemy URI — the "SQL win" via P10). PL1a–PL1g + DX1–DX8 phasing | Design done |
| 48 | [`48-internal-semantic-repository-via-neosemantics.md`](48-internal-semantic-repository-via-neosemantics.md) | Use **neosemantics (`n10s`)** to host general-purpose ontologies (PROV-O / Dublin Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL) **inside shepard's existing Neo4j**. New `SemanticRepositoryType.INTERNAL` enum value + `InternalSemanticConnector`; namespace separation via the `Resource` label + `WHERE NOT n:Resource` write-side filter. **Pre-seeded ontologies bundled** (~13 MB total, SHA-256 pinned, no runtime fetch). LUMEN demo seed switches from placeholder IRIs to real ontology IRIs (PROV-O for authorship, QUDT for units, SKOS scheme for phase-of-burn + severity). N1a–N1g phasing; closes the casual-user "I shouldn't need a triple store to annotate `g rms`" friction | Design done |

---

## Sub-pages: migration / operator notes

Short, action-oriented notes that hang off a specific landed item but
don't warrant their own numbered slot. Each one references the chapter
doc that frames it:

| File | Topic | Hangs off |
|---|---|---|
| [`A3c-namespace-migration.md`](A3c-namespace-migration.md) | Operator note for the `shepard.spatial-data.*` → `shepard.infrastructure.spatial.*` rename: aliasing window, deprecation warning, planned removal in v6.0 | A3c (Chapter F config namespace) |

---

## Cross-chapter interlocks

A handful of items in `16-dispatcher-backlog.md` thread through several
chapters; record them here so a reader who picks up one chapter knows
where the rest of the story lives. **Sequence dependent work against
this list, not against numeric ordering.**

- **C5** (Cypher / SQL injection — string-concatenated query
  construction) is escalated in Chapter B's backlog as a security item.
  It **gates P7** (unified `/search/v2`, Chapter E + D) **and L2c**
  (Chapter F's read-path switch becomes a SQL-injection vector once
  entity ids become UUID strings, per `25 §5`). Therefore: fix C5
  *before* either ships.
- **A0** (admin role mechanism) is in the backlog as a needs-decision
  item. It unblocks **A3b** (Chapter D's `/admin/features` endpoint),
  **P3c** (Chapter D's `/temp/migrations/*` hardening), and **C3**
  (Chapter F's fail-open fallback fix needs a way to authorise
  legitimate admin paths).
- **L2** (Chapter F application-generated IDs) couples to **§11** of
  `12-timescaledb-performance-analysis.md` (Chapter C), to the
  cache-key shape from **A4** (Chapter F), and to the
  path-segment-switch fragility called out in **24 §3.2** (Chapter F)
  and **25 §5** (Chapter F).
- **P16** (convenience clients, Chapter D `27`) is the **multiplexer**
  that hides Chapter D's four-surface picture from end-users; Chapter D
  doesn't fully deliver on its user-friendliness promise without P16.
- **R2** (RO-Crate selectivity, Chapter D-adjacent — backlog only) ties
  Chapter D (export endpoint shape) to Chapter F (subscriptions
  URL-pattern matcher needed for **R2d2**). The `metadata.subscriptions`
  boolean is functional only after R2d2 lands; the prior R2d phase
  records it but emits no documents.

---

## Snapshot date and provenance

Files snapshot 2026-05-04 unless noted in their own header (rounds 1–6
docs are 2026-05-05 / 2026-05-06 / 2026-05-07). Sources of truth:

- GitLab `gitlab.com/dlr-shepard/shepard` for issues / MRs (authoritative).
- GitHub `github.com/noheton/shepard` for the mirror.
- Code refs are `file_path:line_number` against `develop` HEAD at the
  snapshot date.

**Conventions for adding new docs:**

- Preserve numeric prefixes (links and external refs depend on them).
- New docs go at the next free integer; new situation reports renumber
  by inserting and updating cross-references.
- Sub-page operator notes follow the `<source-id>-<topic>.md` pattern
  (e.g. `A3c-namespace-migration.md`); register them under
  [Sub-pages](#sub-pages-migration--operator-notes) above.
- When a doc spans several chapters, file it under its **primary**
  chapter and record the cross-link in
  [Cross-chapter interlocks](#cross-chapter-interlocks).
