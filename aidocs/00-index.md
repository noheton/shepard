# aidocs — Index

Design notes, analysis docs, and strategy docs for this fork of
[`gitlab.com/dlr-shepard/shepard`](https://gitlab.com/dlr-shepard/shepard).

**Always-current live docs (root):**

| File | Purpose |
|---|---|
| [`roadmap.md`](roadmap.md) | Time-horizon view — shipped / near / mid / long-term |
| [`16-dispatcher-backlog.md`](16-dispatcher-backlog.md) | **Live backlog** — authoritative status per item (queued / done / blocked) |
| [`34-upstream-upgrade-path.md`](34-upstream-upgrade-path.md) | **Operator ledger** — per-change admin impact, migration scripts, breakage flags |
| [`42-vision.md`](42-vision.md) | **Researcher-facing vision** — what shepard is and where it's going |
| [`44-fork-vs-upstream-feature-matrix.md`](44-fork-vs-upstream-feature-matrix.md) | **Progress matrix** — fork vs upstream 5.2.0 by capability area |

---

## `platform/` — Core architecture & API design

| File | Topic |
|---|---|
| [`11-implementation-plan.md`](platform/11-implementation-plan.md) | Master phased delivery plan |
| [`19-architecture-feedback.md`](platform/19-architecture-feedback.md) | Architecture fragility ranking and honest critique |
| [`20-epic-roadmap.md`](platform/20-epic-roadmap.md) | Multi-week epic catalogue (predecessor to `roadmap.md`) |
| [`23-api-critique.md`](platform/23-api-critique.md) | API surface critique — P-series items |
| [`24-permission-system-review.md`](platform/24-permission-system-review.md) | Permission model deep-dive — F-series design |
| [`25-neo4j-id-migration-design.md`](platform/25-neo4j-id-migration-design.md) | UUID appId migration design (L2 series) |
| [`26-crud-consistency.md`](platform/26-crud-consistency.md) | CRUD consistency audit — P21 series |
| [`29-p10-implementation-design.md`](platform/29-p10-implementation-design.md) | SQL-over-HTTP timeseries endpoint (P10) design |
| [`32-long-running-process-pattern.md`](platform/32-long-running-process-pattern.md) | Async long-running operation pattern |
| [`47-dev-experience-and-plugin-system.md`](platform/47-dev-experience-and-plugin-system.md) | Plugin SPI + PayloadKind/PayloadStorage seam (PL1, PM1) |
| [`51-instance-admin-role.md`](platform/51-instance-admin-role.md) | Instance-admin role design (A0) |
| [`56-v2-api-simplification-output-profiles-mcp.md`](platform/56-v2-api-simplification-output-profiles-mcp.md) | v2 API simplification, output profiles, MCP endpoint |
| [`63-architecture-decision-log.md`](platform/63-architecture-decision-log.md) | ADR catalogue (ADR-0001 to latest) |
| [`68-plugin-vs-core-overview.md`](platform/68-plugin-vs-core-overview.md) | Plugin vs in-tree decision guide |
| [`69-runtime-plugin-cdi.md`](platform/69-runtime-plugin-cdi.md) | Runtime CDI plugin integration (PM1b3) design |
| [`A3c-namespace-migration.md`](platform/A3c-namespace-migration.md) | `shepard.spatial-data.*` → `shepard.infrastructure.spatial.*` migration note |

---

## `data/` — Payload kinds & data model

| File | Topic |
|---|---|
| [`12-timescaledb-performance-analysis.md`](data/12-timescaledb-performance-analysis.md) | TimescaleDB performance analysis + continuous aggregates |
| [`35-hdf5-hsds-implementation-design.md`](data/35-hdf5-hsds-implementation-design.md) | HDF5/HSDS sidecar design (A5 series) |
| [`37-lab-journal-and-jupyter-design.md`](data/37-lab-journal-and-jupyter-design.md) | Lab journal v2 + Jupyter integration (J1 series) |
| [`41-snapshots-design.md`](data/41-snapshots-design.md) | Point-in-time snapshot design (V2 series) |
| [`45-gridfs-to-s3-evaluation.md`](data/45-gridfs-to-s3-evaluation.md) | GridFS vs S3 evaluation + FS1 plugin SPI design |
| [`46-payload-versioning-design.md`](data/46-payload-versioning-design.md) | Per-payload byte versioning (PV1 series) |
| [`50-experiment-orchestration.md`](data/50-experiment-orchestration.md) | Experiment coordinator service (EXP1 series) |
| [`53-file-reference-rename-video-content.md`](data/53-file-reference-rename-video-content.md) | Video content / FileReference rename design |
| [`78-cad-geometry-annotator.md`](data/78-cad-geometry-annotator.md) | CAD geometry annotator design (CAD1 series) |
| [`79-cpacs-annotator.md`](data/79-cpacs-annotator.md) | CPACS annotator design |
| [`81-spatial-data-binding.md`](data/81-spatial-data-binding.md) | Spatial data binding (SB series) |
| [`83-pointcloud-and-live-overlay.md`](data/83-pointcloud-and-live-overlay.md) | Point cloud + live overlay (PC1 series) |
| [`84-live-digital-twin.md`](data/84-live-digital-twin.md) | Live digital twin (DT1 series) |
| [`85-coordinate-frame-tree.md`](data/85-coordinate-frame-tree.md) | Coordinate frame tree (CST1 series) — TF-tree, ICP chaining, Isaac Sim / ROS export |
| [`86-scene-drive-and-replay.md`](data/86-scene-drive-and-replay.md) | Scene drive, data linking, replay sessions, URDF export (DR1 series) |

---

## `workflows/` — Research workflows & data lifecycle

| File | Topic |
|---|---|
| [`30-provenance-and-lineage-design.md`](workflows/30-provenance-and-lineage-design.md) | Provenance and lineage design (PROV1 series) |
| [`31-rocrate-export-optimisation.md`](workflows/31-rocrate-export-optimisation.md) | RO-Crate export optimisation (R2 series) |
| [`36-user-profile-and-settings-design.md`](workflows/36-user-profile-and-settings-design.md) | User profile + settings design (U1 series) |
| [`38-git-integration-design.md`](workflows/38-git-integration-design.md) | Git reference integration design (G1 series) |
| [`39-templates-design.md`](workflows/39-templates-design.md) | Template system design v1 (T1 series) |
| [`54-templates-as-first-class-entity.md`](workflows/54-templates-as-first-class-entity.md) | Templates as first-class `ShepardTemplate` entity (T1a revised) |
| [`55-provenance-and-activity-overhaul.md`](workflows/55-provenance-and-activity-overhaul.md) | Provenance + Activity graph overhaul |
| [`64-provenance-architecture.md`](workflows/64-provenance-architecture.md) | Provenance architecture (PROV1a) |

---

## `semantics/` — Search, knowledge graph & AI

| File | Topic |
|---|---|
| [`13-search-improvements.md`](semantics/13-search-improvements.md) | Unified search v2 design (E2 epic, P7 series) |
| [`14-semantic-improvements.md`](semantics/14-semantic-improvements.md) | Semantic annotation model improvements (L7, P8) |
| [`43-ai-opportunities.md`](semantics/43-ai-opportunities.md) | AI opportunities survey + Lumen agent design (AI1 series) |
| [`48-internal-semantic-repository-via-neosemantics.md`](semantics/48-internal-semantic-repository-via-neosemantics.md) | n10s internal semantic repository (N1 series) |
| [`65-admin-configurable-ontology-preseed.md`](semantics/65-admin-configurable-ontology-preseed.md) | Admin-configurable ontology preseed + custom bundles (N1c2) |

---

## `integrations/` — External system integrations

| File | Topic |
|---|---|
| [`40-ecosystem.md`](integrations/40-ecosystem.md) | Ecosystem overview + process wizard |
| [`52-aas-backend-integration.md`](integrations/52-aas-backend-integration.md) | Asset Administration Shell (AAS) integration (AAS1) |
| [`60-shepard-edge.md`](integrations/60-shepard-edge.md) | Shepard Edge — offline / edge deployment |
| [`61-shepard-mount-as-network-drive.md`](integrations/61-shepard-mount-as-network-drive.md) | Network-drive mount via FUSE / WebDAV |
| [`66-hmc-kip-integration.md`](integrations/66-hmc-kip-integration.md) | Helmholtz Metadata Collaboration + KIP integration |
| [`67-unhide-publish-plugin.md`](integrations/67-unhide-publish-plugin.md) | Unhide publish plugin design (UH1 series) |
| [`72-invenio-publishing-plugin.md`](integrations/72-invenio-publishing-plugin.md) | InvenioRDM publishing plugin |
| [`77-databus-moss-federation.md`](integrations/77-databus-moss-federation.md) | Databus + MOSS federation plugin (R1/MB1 series) |
| [`80-rce-integration.md`](integrations/80-rce-integration.md) | RCE (Remote Component Environment) integration |

---

## `ops/` — Tooling, clients & frontend

| File | Topic |
|---|---|
| [`22-admin-cli-draft.md`](ops/22-admin-cli-draft.md) | Admin CLI design (`shepard-admin`) — all verb/command families |
| [`27-convenience-clients-design.md`](ops/27-convenience-clients-design.md) | `shepard-py` / `shepard-ts` convenience client design (P16) |
| [`28-paradigms-and-clients-synthesis.md`](ops/28-paradigms-and-clients-synthesis.md) | Client paradigms synthesis + SSE/presign/streaming invariants |
| [`33-frontend-workflow-analysis.md`](ops/33-frontend-workflow-analysis.md) | Frontend workflow analysis (Nuxt 3 / Vuetify 3) |
| [`49-in-app-user-docs.md`](ops/49-in-app-user-docs.md) | In-app `/help` route + two-track docs structure (D1 series) |
| [`57-openapi-client-generator-evaluation.md`](ops/57-openapi-client-generator-evaluation.md) | OpenAPI client generator evaluation (P17) |
| [`58-ui-and-graph-ergonomics.md`](ops/58-ui-and-graph-ergonomics.md) | UI/UX and graph ergonomics improvements |
| [`59-performance-testing-and-tuning.md`](ops/59-performance-testing-and-tuning.md) | Performance testing + tuning design (P10–P20 slice) |
| [`85-ui-overhaul-design.md`](ops/85-ui-overhaul-design.md) | UI overhaul — critique, opportunities, prioritised roadmap (QW/UI series) |
| [`86-ui-changelog.md`](ops/86-ui-changelog.md) | Living UI change log — per-PR record of user-visible frontend changes |
| [`87-collection-container-duality.md`](ops/87-collection-container-duality.md) | Collection/Container duality — design rationale, UX improvements, marketing argument |

---

## `strategy/` — Stakeholders, landscape & funding

| File | Topic |
|---|---|
| [`70-competitor-landscape-and-feature-ideas.md`](strategy/70-competitor-landscape-and-feature-ideas.md) | Competitor landscape + feature ideas from the broader ecosystem |
| [`71-fork-adoption-as-upstream.md`](strategy/71-fork-adoption-as-upstream.md) | Fork adoption strategy — path to becoming the upstream reference |
| [`73-dlr-stakeholder.md`](strategy/73-dlr-stakeholder.md) | DLR stakeholder map |
| [`74-dlr-bt-stakeholder.md`](strategy/74-dlr-bt-stakeholder.md) | DLR BT department stakeholder details |
| [`75-dfg-eresearch-funding.md`](strategy/75-dfg-eresearch-funding.md) | DFG e-research funding context |
| [`76-shepard-users-and-citations.md`](strategy/76-shepard-users-and-citations.md) | Known users + citation tracking |
| [`82-zlp-augsburg-stakeholder.md`](strategy/82-zlp-augsburg-stakeholder.md) | ZLP Augsburg stakeholder details |

---

## `archive/` — Historical snapshots & superseded docs

Early-phase analysis snapshots and superseded plans. Kept for auditability.

| File | Topic |
|---|---|
| `01-repo-overview.md` | Initial repo layout snapshot (2026-05-05) |
| `02-cluster-map.md` | GitLab cluster map snapshot |
| `03-issues-status.md` | GitLab issues snapshot |
| `04-reconciliation.md` | Issue reconciliation snapshot |
| `05-dependency-report.md` | Dependency report snapshot |
| `06-code-quality.md` | Code quality snapshot |
| `07-security-issues.md` | Security issues snapshot (resolved — see `34-upstream-upgrade-path.md`) |
| `08-first-issues.md` | First-issues triage snapshot |
| `09-ready-to-close.md` | Issues-ready-to-close snapshot |
| `10-cleanup-plan.md` | Early cleanup plan (superseded by `16-dispatcher-backlog.md`) |
| `15-phase-0-status.md` | Phase 0 status snapshot |
| `17-startup-wait-audit.md` | Startup wait audit (A1d shipped) |
| `18-pagination-inventory.md` | Pagination inventory (L6 shipped) |
| `21-user-interest-gauge.md` | User interest gauge (early stakeholder read) |
