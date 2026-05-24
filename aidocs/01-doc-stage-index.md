<!--
AUTO-GENERATED — DO NOT EDIT BY HAND.

Regenerate with:

    python3 scripts/regenerate-doc-stage-index.py

Inputs: every `aidocs/**/*.md` file's YAML front-matter `stage:` field.
Taxonomy: see `aidocs/00-doc-stages.md`.
-->

# aidocs — Doc stage index

This is the **stage-grouped index** of every `aidocs/*.md` design doc in
this fork. The canonical taxonomy is in
[`00-doc-stages.md`](00-doc-stages.md); each section below lists every doc
whose front-matter `stage:` token matches that stage.

A doc with `stage: deployed, upgrade-v5:v6` appears in both the `deployed`
section and the `upgrade-overlay` section.

**Companion ledgers:**

- `aidocs/34-upstream-upgrade-path.md` — upstream-admin-facing upgrade ledger
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — contributor-facing feature matrix
- `aidocs/16-dispatcher-backlog.md` — actionable backlog rows


## Histogram

| stage | count |
|---|---|
| `fragment` | 39 |
| `concept` | 14 |
| `idea` | 10 |
| `feature-defined` | 76 |
| `audited-by-personas` | 56 |
| `feedback-implemented` | 1 |
| `tests-implemented` | 4 |
| `deployed` | 58 |
| `decommissioned` | 47 |
| `upgrade-vX:vY` (overlay) | 0 |
| **total docs** | **312** |
| **UNTAGGED** | **7** |

## UNTAGGED (needs `stage:` front-matter)

These files have no `stage:` field. Add one per the taxonomy in `aidocs/00-doc-stages.md`.

| doc | title | last-touched |
|---|---|---|
| [`aidocs/agent-findings/backend-logs-sift-2026-05-24.md`](agent-findings/backend-logs-sift-2026-05-24.md) | Backend logs sift — 2026-05-24 ~06:00 CEST | 2026-05-24 |
| [`aidocs/agent-findings/persona-api-scrutinizer-gh-pm-2026-05-23.md`](agent-findings/persona-api-scrutinizer-gh-pm-2026-05-23.md) | Persona — API Scrutinizer (Minimalist) — GH-PM adoption review | 2026-05-23 |
| [`aidocs/agent-findings/persona-audit-logstore-2026-05-23.md`](agent-findings/persona-audit-logstore-2026-05-23.md) | Persona audit — log-store-sidecar design (`aidocs/integrations/94`) | 2026-05-23 |
| [`aidocs/agent-findings/persona-reluctant-senior-gh-pm-2026-05-23.md`](agent-findings/persona-reluctant-senior-gh-pm-2026-05-23.md) | Reluctant Senior Researcher — audit of `aidocs/strategy/85` | 2026-05-23 |
| [`aidocs/agent-findings/persona-strategy-aligner-gh-pm-2026-05-23.md`](agent-findings/persona-strategy-aligner-gh-pm-2026-05-23.md) | Persona — Strategy Aligner & Executive Advisor on GH-PM adoption (2026-05-23) | 2026-05-23 |
| [`aidocs/agent-findings/worktree-consolidation-triage-2026-05-23.md`](agent-findings/worktree-consolidation-triage-2026-05-23.md) | Worktree Consolidation Triage — 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/83-github-features-leverage.md`](strategy/83-github-features-leverage.md) | GitHub features — what shepard uses, deliberately skips, and why | 2026-05-23 |

## fragment (39)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/00-synergy-index.md`](agent-findings/00-synergy-index.md) | 00 — Synergy index | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-ai-accountability.md`](agent-findings/synergy-2026-05-23-ai-accountability.md) | S-08 — AI accountability dashboard: MCP × Permission audit log × F(AI)²R | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-channel-as-individual.md`](agent-findings/synergy-2026-05-23-channel-as-individual.md) | S-01 — Channel-as-individual: HSDS HDF5 × AAS TimeSeriesData × sTC | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md`](agent-findings/synergy-2026-05-23-openlineage-fair2r.md) | S-02 — OpenLineage RunEvent × F(AI)²R × PROV-O: EASA evidence for free | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-pidinst-three-exports.md`](agent-findings/synergy-2026-05-23-pidinst-three-exports.md) | S-05 — One PID, three exports: PIDINST × SOSA/SSN × AAS Nameplate | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-roundtrip-wiki.md`](agent-findings/synergy-2026-05-23-roundtrip-wiki.md) | S-03 — Round-trip wiki: Confluence import × Wiki-writer × Snapshot chain | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-shacl-driven-mcp.md`](agent-findings/synergy-2026-05-23-shacl-driven-mcp.md) | S-07 — SHACL × MCP tools × ShapesValidateRest: one validator, two surfaces | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-snapshots-garage-gap.md`](agent-findings/synergy-2026-05-23-snapshots-garage-gap.md) | S-06 — Snapshots × Garage S3 (no versioning): Shepard absorbs the gap | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/synergy-2026-05-23-trace3d-video-sync.md`](agent-findings/synergy-2026-05-23-trace3d-video-sync.md) | S-04 — Trace3D × Video × DataBinding: synchronized 3D-trace + camera-PiP | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/12-timescaledb-performance-analysis.md`](data/12-timescaledb-performance-analysis.md) | TimescaleDB Timeseries Integration — Performance Analysis & Mitigations | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/69-timeseries-upstream-migration.md`](data/69-timeseries-upstream-migration.md) | Timeseries Schema — Migration from Upstream | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/88-thumbnail-spi.md`](data/88-thumbnail-spi.md) | TH1 — File Thumbnail SPI | 2026-05-23 | 2026-05-23 |
| [`aidocs/input/input_raw.md`](input/input_raw.md) | Set up configuration | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/22-admin-cli-draft.md`](ops/22-admin-cli-draft.md) | Admin CLI — Candidate-Function Draft | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/27-convenience-clients-design.md`](ops/27-convenience-clients-design.md) | Convenience Clients — `shepard-py` and `shepard-ts` (P16) | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/28-paradigms-and-clients-synthesis.md`](ops/28-paradigms-and-clients-synthesis.md) | 28. Paradigms-and-Clients Synthesis — The Integrated Proposal | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/33-frontend-workflow-analysis.md`](ops/33-frontend-workflow-analysis.md) | 33 — Frontend / UI Workflow Analysis + Suggestions | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/86-ui-changelog.md`](ops/86-ui-changelog.md) | 86 — UI Change Log | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/11-implementation-plan.md`](platform/11-implementation-plan.md) | Implementation Plan — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/19-architecture-feedback.md`](platform/19-architecture-feedback.md) | 19 — Critical Architectural Feedback | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/20-epic-roadmap.md`](platform/20-epic-roadmap.md) | Epic Roadmap — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/23-api-critique.md`](platform/23-api-critique.md) | API Critique — Usability, Redundancies, Paradigms, Client Generation | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/24-permission-system-review.md`](platform/24-permission-system-review.md) | 24 — Permission-System Review | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/26-crud-consistency.md`](platform/26-crud-consistency.md) | 26. REST API CRUD Consistency Inventory | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/29-p10-implementation-design.md`](platform/29-p10-implementation-design.md) | P10 — `POST /sql/timeseries` Implementation Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/32-long-running-process-pattern.md`](platform/32-long-running-process-pattern.md) | Long-Running Process Pattern — Async Jobs | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/A3c-namespace-migration.md`](platform/A3c-namespace-migration.md) | A3c: infrastructure-vs-feature toggle namespace split | 2026-05-23 | 2026-05-23 |
| [`aidocs/reference/v5-openapi-summary.md`](reference/v5-openapi-summary.md) | v5 source — OpenAPI 5.4.0 summary (legacy-compat ground truth) | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/13-search-improvements.md`](semantics/13-search-improvements.md) | Search — Improvements & Unification Proposal | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/14-semantic-improvements.md`](semantics/14-semantic-improvements.md) | Semantic Annotations — Improvements & Knowledge-Graph Path | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/70-competitor-landscape-and-feature-ideas.md`](strategy/70-competitor-landscape-and-feature-ideas.md) | aidocs/70 — Competitor landscape & feature ideas for shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/71-fork-adoption-as-upstream.md`](strategy/71-fork-adoption-as-upstream.md) | aidocs/71 — Fork adoption as upstream: feasibility, verification, and cost-benefit | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/73-dlr-stakeholder.md`](strategy/73-dlr-stakeholder.md) | aidocs/73 — shepard: Stakeholder brief for DLR | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/74-dlr-bt-stakeholder.md`](strategy/74-dlr-bt-stakeholder.md) | aidocs/74 — shepard: Stakeholder brief for DLR-BT (Stuttgart) | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/82-zlp-augsburg-stakeholder.md`](strategy/82-zlp-augsburg-stakeholder.md) | aidocs/82 — shepard: Stakeholder brief for DLR ZLP Augsburg | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/86-shepard-predecessor-systems.md`](strategy/86-shepard-predecessor-systems.md) | Predecessor systems at DLR ZLP Augsburg — continuity of field before Shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/95-damast-space-transport-data-management.md`](strategy/95-damast-space-transport-data-management.md) | DaMaST — Data Management for Space Transport | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/96-hokitep-zlp-extension-and-joint-usecase.md`](strategy/96-hokitep-zlp-extension-and-joint-usecase.md) | HoKiTeP — ZLP extension and joint use-case orchestration | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/31-rocrate-export-optimisation.md`](workflows/31-rocrate-export-optimisation.md) | 31 — RO-Crate Export Optimisation | 2026-05-23 | 2026-05-23 |

## concept (14)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/data/45-gridfs-to-s3-evaluation.md`](data/45-gridfs-to-s3-evaluation.md) | GridFS → S3 — Evaluation | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/50-experiment-orchestration.md`](data/50-experiment-orchestration.md) | Experiment Orchestration — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/53-file-reference-rename-video-content.md`](data/53-file-reference-rename-video-content.md) | FileReference Rename + Video Payload Kind — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/52-aas-backend-integration.md`](integrations/52-aas-backend-integration.md) | AAS Backend Integration — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/60-shepard-edge.md`](integrations/60-shepard-edge.md) | shepard Edge — Concept Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/61-shepard-mount-as-network-drive.md`](integrations/61-shepard-mount-as-network-drive.md) | shepard Mount as a Network Drive — Concept Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/49-in-app-user-docs.md`](ops/49-in-app-user-docs.md) | In-App User Docs — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/57-openapi-client-generator-evaluation.md`](ops/57-openapi-client-generator-evaluation.md) | OpenAPI Client Generator Evaluation — `/v2/` Codegen | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/58-ui-and-graph-ergonomics.md`](ops/58-ui-and-graph-ergonomics.md) | UI & Graph Ergonomics — Design Cluster | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/56-v2-api-simplification-output-profiles-mcp.md`](platform/56-v2-api-simplification-output-profiles-mcp.md) | `/v2/` API Simplification — Flat Paths, Output Profiles, MCP-Friendly OpenAPI | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md`](semantics/48-internal-semantic-repository-via-neosemantics.md) | Internal Semantic Repository via Neosemantics — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/40-spw-stc-integration-design.md`](workflows/40-spw-stc-integration-design.md) | shepard-process-wizard + shepard-timeseries-collector integration design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/54-templates-as-first-class-entity.md`](workflows/54-templates-as-first-class-entity.md) | Templates as a First-Class Entity — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/55-provenance-and-activity-overhaul.md`](workflows/55-provenance-and-activity-overhaul.md) | Provenance and Activity Overhaul — Design | 2026-05-23 | 2026-05-23 |

## idea (10)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/98-thesis-perspective.md`](98-thesis-perspective.md) | 98 — Shepard as a thesis at a German university (perspective + viability) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v5-metadata-enrichment-survey-2026-05-23.md`](agent-findings/v5-metadata-enrichment-survey-2026-05-23.md) | v5 OpenAPI — metadata enrichment survey for the MFFD importer (v15.10 candidates) | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/106-requirements-traceability.md`](platform/106-requirements-traceability.md) | 106 — Requirements traceability — research direction | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/68-plugin-vs-core-overview.md`](platform/68-plugin-vs-core-overview.md) | 68 — Plugin-vs-core architecture overview | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/43-ai-opportunities.md`](semantics/43-ai-opportunities.md) | AI Opportunities — Traditional ML + LLM Integration | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/97-tpl3-upper-ontology-bootstrap.md`](semantics/97-tpl3-upper-ontology-bootstrap.md) | 97 — TPL3 — Upper-ontology bootstrap migration + starter kit | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/101-diva-project-context.md`](strategy/101-diva-project-context.md) | 101 — DIVA: Drone Integration via AAS (project context) | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/75-dfg-eresearch-funding.md`](strategy/75-dfg-eresearch-funding.md) | aidocs/75 — DFG e-Research-Technologien: Antragsfähigkeit und Konzeptskizze | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/76-shepard-users-and-citations.md`](strategy/76-shepard-users-and-citations.md) | aidocs/76 — shepard: Known users, citations, and ecosystem | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/30-provenance-and-lineage-design.md`](workflows/30-provenance-and-lineage-design.md) | Provenance and Data Lineage — Design Exploration | 2026-05-23 | 2026-05-23 |

## feature-defined (76)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/aas-edc-reuse-survey-2026-05-23.md`](agent-findings/aas-edc-reuse-survey-2026-05-23.md) | AAS + EDC reuse survey for `shepard-plugin-aas` + `shepard-plugin-edc` | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/bug-148-do-perms-seeded-2026-05-24.md`](agent-findings/bug-148-do-perms-seeded-2026-05-24.md) | BUG-148 — DataObject Permissions seeding: WORKS AS DESIGNED | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/file-storage-routing-audit-2026-05-24.md`](agent-findings/file-storage-routing-audit-2026-05-24.md) | File-storage routing audit — Garage vs Mongo GridFS, 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/garage-and-docker-stack-audit-2026-05-24.md`](agent-findings/garage-and-docker-stack-audit-2026-05-24.md) | Garage S3 + Docker stack audit — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/mffd-v16-ui-screenshots-2026-05-23/README.md`](agent-findings/mffd-v16-ui-screenshots-2026-05-23/README.md) | MFFD v16 UI screenshots — first live look at the digital-thread tree | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/mongodb-substrate-audit-2026-05-24.md`](agent-findings/mongodb-substrate-audit-2026-05-24.md) | MongoDB substrate audit — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/persona-digital-native-gh-pm-2026-05-23.md`](agent-findings/persona-digital-native-gh-pm-2026-05-23.md) | Persona: Digital Native Researcher — GH-PM audit (2026-05-23) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/plugin-design-audit-2026-05-24.md`](agent-findings/plugin-design-audit-2026-05-24.md) | Plugin design audit — Spatial + Tables + HDF5, 2026-05-24 | 2026-05-24 | — |
| [`aidocs/agent-findings/postgres-pgbouncer-substrate-audit-2026-05-24.md`](agent-findings/postgres-pgbouncer-substrate-audit-2026-05-24.md) | Postgres + PgBouncer substrate audit — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md`](agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md) | RDM-2026-05-24-004 — Provenance panel empty: root-cause + Bucket D fix | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md`](agent-findings/rdm-scrutinizer-2026-05-24.md) | RDM Scrutinizer — FAIR + DMP + Publication Readiness, 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/research-network-orcid-anchor-2026-05-23.md`](agent-findings/research-network-orcid-anchor-2026-05-23.md) | Findings — ORCID anchor + general platform sweep for Florian Krebs (DLR ZLP Augsburg) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/tib-hannover-outreach-2026-05-23.md`](agent-findings/tib-hannover-outreach-2026-05-23.md) | TIB Hannover outreach — Step 1 ("Used by" entry) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ts-design-audit-2026-05-24.md`](agent-findings/ts-design-audit-2026-05-24.md) | TimescaleDB design audit — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md`](agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md) | UI Hypothesis Re-check 2026-05-24 — UI-018 + UI-019 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24.md`](agent-findings/ux-scrutinizer-workflows-2026-05-24.md) | UX Scrutinizer — Workflow + Click Minimisation, 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/data/35-hdf5-hsds-implementation-design.md`](data/35-hdf5-hsds-implementation-design.md) | HDF5 / HSDS Implementation Design (E7 → A5 series) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/46-payload-versioning-design.md`](data/46-payload-versioning-design.md) | Payload Versioning — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/78-cad-geometry-annotator.md`](data/78-cad-geometry-annotator.md) | aidocs/78 — 3D Geometry & FEM Annotator (`shepard-plugin-cad`) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/79-cpacs-annotator.md`](data/79-cpacs-annotator.md) | aidocs/79 — CPACS Annotator (shepard-plugin-cpacs) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/81-spatial-data-binding.md`](data/81-spatial-data-binding.md) | aidocs/81 — Spatial Data Binding: Linking Geometry to Measurements | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/82-spatial-perf-evaluation.md`](data/82-spatial-perf-evaluation.md) | Spatial-data performance evaluation (PostGIS reassessment) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/83-pointcloud-and-live-overlay.md`](data/83-pointcloud-and-live-overlay.md) | aidocs/83 — Point Cloud Integration and Live Overlay Modalities | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/84-live-digital-twin.md`](data/84-live-digital-twin.md) | aidocs/84 — Live Digital Twin: Moving Objects, Production Cell Scene, and State Streaming | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/85-coordinate-frame-tree.md`](data/85-coordinate-frame-tree.md) | aidocs/85 — Coordinate Frame Tree (CST1) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/86-scene-drive-and-replay.md`](data/86-scene-drive-and-replay.md) | aidocs/86 — Scene Drive, Data Linking, and Replay (DR1 series) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/89-stale-channel-admin-design.md`](data/89-stale-channel-admin-design.md) | 89 — Stale timeseries channel admin tool (ADMIN-STALE-CH) | 2026-05-23 | 2026-05-23 |
| [`aidocs/frontend/01-user-research-findings-2024.md`](frontend/01-user-research-findings-2024.md) | User research findings — 2024-06/07 interview round | 2026-05-23 | 2026-05-23 |
| [`aidocs/frontend/100-cross-instance-prov-ui.md`](frontend/100-cross-instance-prov-ui.md) | 100 — Cross-instance provenance: client-side rendering design | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/66-hmc-kip-integration.md`](integrations/66-hmc-kip-integration.md) | 66 — HMC Kernel Information Profile integration | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/67-unhide-publish-plugin.md`](integrations/67-unhide-publish-plugin.md) | 67 — Unhide publish plugin (Helmholtz Knowledge Graph integration) | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/70-home-showcase-mqtt-design.md`](integrations/70-home-showcase-mqtt-design.md) | home-showcase — MQTT → shepard collector + demo (HOME1) | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/72-invenio-publishing-plugin.md`](integrations/72-invenio-publishing-plugin.md) | aidocs/72 — InvenioRDM publishing plugin (`shepard-plugin-invenio`) | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/77-databus-moss-federation.md`](integrations/77-databus-moss-federation.md) | aidocs/77 — Databus + MOSS federation layer for shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/80-rce-integration.md`](integrations/80-rce-integration.md) | aidocs/80 — RCE Integration: Data Distribution with Provenance Tracking (`shepard-plugin-rce`) | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/81-jupyterhub-integration.md`](integrations/81-jupyterhub-integration.md) | 81 — JupyterHub Integration | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/82-confluence-import.md`](integrations/82-confluence-import.md) | 82 — Confluence Data Center Space Export → Shepard Import | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/83-rebar-airflow-integration.md`](integrations/83-rebar-airflow-integration.md) | 83 — ReBAR / Airflow Integration | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/84-process-orchestrator-plugin.md`](integrations/84-process-orchestrator-plugin.md) | 84 — shepard-plugin-process-orchestrator | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/92-mffd-real-data-import-strategy.md`](integrations/92-mffd-real-data-import-strategy.md) | 92 — MFFD real-data import strategy | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/95-shepard-plugin-importer-patterns-from-v15.md`](integrations/95-shepard-plugin-importer-patterns-from-v15.md) | 95 — shepard-plugin-importer: patterns from v15.x MFFD field experience | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/96-metrology-spatial-analyzer.md`](integrations/96-metrology-spatial-analyzer.md) | 96 — Metrology integration: Spatial Analyzer + Leica trackers as a Shepard payload kind | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/103-v1-compat-plugin-extraction.md`](platform/103-v1-compat-plugin-extraction.md) | `shepard-plugin-v1-compat` — design for extracting the | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/103a-v1-compat-marker-plugin.md`](platform/103a-v1-compat-marker-plugin.md) | `shepard-plugin-v1-compat` — **Phase 1 marker plugin** design | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/30-mcp-plugin-design.md`](platform/30-mcp-plugin-design.md) | 30 — shepard-plugin-mcp: Full-Parity MCP Endpoint | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/51-instance-admin-role.md`](platform/51-instance-admin-role.md) | Instance-Admin Role — Design (A0 + C3 + F8) | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/63-architecture-decision-log.md`](platform/63-architecture-decision-log.md) | 63 — Architecture Decision Log | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/69-runtime-plugin-cdi.md`](platform/69-runtime-plugin-cdi.md) | 69 — Runtime plugin CDI integration (deferred PM1b3) | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/71-collection-watches-design.md`](platform/71-collection-watches-design.md) | Collection `:watches` Container — Design (WATCH1) | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/86-ai-plugin-design.md`](platform/86-ai-plugin-design.md) | `shepard-plugin-ai` — AI Platform Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/88-quarkus-mcp-server-migration.md`](platform/88-quarkus-mcp-server-migration.md) | 88 — Native Quarkus MCP Server: Replacing the Python Sidecar | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/91-appid-uri-scheme.md`](platform/91-appid-uri-scheme.md) | 91 — appId URI Scheme: HTTPS Persistent Identifiers | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/65-admin-configurable-ontology-preseed.md`](semantics/65-admin-configurable-ontology-preseed.md) | 65 — Admin-configurable ontology pre-seeding (with custom-bundle support) | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/94-metadata4ing-integration-design.md`](semantics/94-metadata4ing-integration-design.md) | 94 — Deepening metadata4ing (m4i) integration into Shepard's semantic graph | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/96-upper-ontology-alignment.md`](semantics/96-upper-ontology-alignment.md) | 96 — Upper-ontology alignment (BFO 2020 + IOF Core + IAO + PROV-O) | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/fair4ml-fair4ai-fai2r-2026-05-23.md`](semantics/fair4ml-fair4ai-fai2r-2026-05-23.md) | FAIR4ML × FAIR for AI × f(ai)²r — comparative survey + alignment plan | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/100-shepard-bt-zlp-rollout-plan.md`](strategy/100-shepard-bt-zlp-rollout-plan.md) | Shepard rollout plan — BT / ZLP Augsburg cells | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/102-institute-youtube-profile.md`](strategy/102-institute-youtube-profile.md) | 102 — DLR Institute of Structures and Design: YouTube channel as vision + use-case source | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/103-research-network.md`](strategy/103-research-network.md) | Shepard research network — DLR eLib + external-peer reconstruction | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/104-author-research-profile.md`](strategy/104-author-research-profile.md) | Author research profile — Florian Krebs, DLR ZLP Augsburg | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/87-dlr-zlp-positioning.md`](strategy/87-dlr-zlp-positioning.md) | DLR ZLP Augsburg — institutional positioning and the substrate Shepard serves | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/88-nfdi4ing-alignment.md`](strategy/88-nfdi4ing-alignment.md) | 88 — NFDI4Ing alignment: positioning Shepard inside the German engineering RDM federation | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/89-genai-methodology-and-reflexivity.md`](strategy/89-genai-methodology-and-reflexivity.md) | Generative AI as research method — Krebs's stated position and this project's observed practice | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/90-hmc-phase-2-positioning.md`](strategy/90-hmc-phase-2-positioning.md) | 90 — HMC Phase 2 positioning: Shepard's pre-committed work-packages at DLR ZLP | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/91-forinfpro-semantically-driven-analytics.md`](strategy/91-forinfpro-semantically-driven-analytics.md) | ForInfPro and the semantically-driven analytics use case | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/92-aerospace-x-regulatory-context.md`](strategy/92-aerospace-x-regulatory-context.md) | 92 — Aerospace-X and the aerospace regulatory context | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/93-management-context-and-compliance.md`](strategy/93-management-context-and-compliance.md) | Shepard inside the DLR management context — institutional stack, governance frame, compliance gates | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/94-federation-and-dataspaces.md`](strategy/94-federation-and-dataspaces.md) | 94 — Federation and dataspaces: where Shepard sits in Manufacturing-X / Aerospace-X / Catena-X | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/ai-fork-production-readiness-2026-05-23.md`](strategy/ai-fork-production-readiness-2026-05-23.md) | Is the AI-collaborative `noheton/shepard` fork production-ready? | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/requirements-alignment-damast-ux-mffd-2026-05-23.md`](strategy/requirements-alignment-damast-ux-mffd-2026-05-23.md) | Requirements alignment — DaMaST workshop × UX 5-phase journey × MFFD-focused gap analysis | 2026-05-23 | 2026-05-23 |
| [`aidocs/sustainability/00-energy-estimation-log.md`](sustainability/00-energy-estimation-log.md) | 00 — Energy + CO₂ estimation log per commit | 2026-05-23 | 2026-05-23 |
| [`aidocs/sustainability/01-methodology.md`](sustainability/01-methodology.md) | 01 — Energy / CO₂ estimation methodology | 2026-05-23 | 2026-05-23 |
| [`aidocs/ux/73-personal-landing-page.md`](ux/73-personal-landing-page.md) | 73 — Personal Landing Page | 2026-05-23 | 2026-05-23 |
| [`aidocs/ux/74-auto-refresh-stale-session.md`](ux/74-auto-refresh-stale-session.md) | 74 — Auto-Refresh on Stale Session | 2026-05-23 | 2026-05-23 |
| [`aidocs/ux/76-uplot-timeseries-chart.md`](ux/76-uplot-timeseries-chart.md) | 76 — uPlot Timeseries Chart | 2026-05-23 | 2026-05-23 |
| [`aidocs/ux/78-containerless-basic-mode.md`](ux/78-containerless-basic-mode.md) | 78 — Container-hiding Basic Mode | 2026-05-23 | 2026-05-23 |

## audited-by-personas (56)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md`](agent-findings/ai-ontology-mapping-survey-2026-05-23.md) | AI-assisted ontology mapping — research survey + adoption recommendation | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/aidocs-consolidation-survey.md`](agent-findings/aidocs-consolidation-survey.md) | aidocs consolidation survey — single source of truth enforcement | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/analytics-ai.md`](agent-findings/analytics-ai.md) | Applied ML & Data Science — Shepard Platform Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/api-annoyances.md`](agent-findings/api-annoyances.md) | API annoyances — running log | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/api-scrutinizer-v14-import.md`](agent-findings/api-scrutinizer-v14-import.md) | API Scrutinizer — v14 → v15 import-script review | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/api-scrutinizer.md`](agent-findings/api-scrutinizer.md) | API Scrutinizer — Shepard /v2/ Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/data-ontologist-prov-o-v15.md`](agent-findings/data-ontologist-prov-o-v15.md) | PROV-O fragment design for v15 MFFD batch import | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/data-ontologist.md`](agent-findings/data-ontologist.md) | Data Ontologist — Discovery Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/dlr-ontology-catalog.md`](agent-findings/dlr-ontology-catalog.md) | DLR Ontology & Model Initiative Catalogue | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/docs-pages-refresh.md`](agent-findings/docs-pages-refresh.md) | GH Pages docs refresh — pre-push survey + fix log | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/easa-ai-regulatory-positioning.md`](agent-findings/easa-ai-regulatory-positioning.md) | EASA AI Regulatory Positioning for Shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/easa-data-management-learning-assurance.md`](agent-findings/easa-data-management-learning-assurance.md) | EASA Data Management + Learning Assurance — deep dive (Shepard scope) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ecosystem-advocate.md`](agent-findings/ecosystem-advocate.md) | Ecosystem Advocate — Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ecosystem-tools.md`](agent-findings/ecosystem-tools.md) | Ecosystem Tools — Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/eu-machinery-regulation-2023-1230.md`](agent-findings/eu-machinery-regulation-2023-1230.md) | EU Machinery Regulation 2023/1230 — Shepard implications | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/garage-activation-runbook.md`](agent-findings/garage-activation-runbook.md) | Garage activation runbook — findings & gotchas | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/gh-lean-traceability-consult-2026-05-23.md`](agent-findings/gh-lean-traceability-consult-2026-05-23.md) | gh-lean-traceability-consult — 2026-05-23 | 2026-05-23 | — |
| [`aidocs/agent-findings/manufacturing-quality.md`](agent-findings/manufacturing-quality.md) | Manufacturing Quality Readiness Assessment | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/mffd-cleanup-2026-05-22.md`](agent-findings/mffd-cleanup-2026-05-22.md) | MFFD-Dropbox (collection 515365) cleanup — 2026-05-22 | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-audit-ontology-mapping-2026-05-23.md`](agent-findings/persona-audit-ontology-mapping-2026-05-23.md) | Persona audit — AI-assisted ontology mapping survey (round 1) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-audit-promptlog-2026-05-23.md`](agent-findings/persona-audit-promptlog-2026-05-23.md) | Persona audit — PromptLog design (aidocs/semantics/99) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-audit-views-as-shapes-2026-05-23.md`](agent-findings/persona-audit-views-as-shapes-2026-05-23.md) | Persona audit — views-as-shapes design (docs 95 + 98) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-manufacturing-quality-2026-05-24.md`](agent-findings/persona-manufacturing-quality-2026-05-24.md) | Persona audit — Industrial Manufacturing & Quality Engineer (live, 2026-05-24) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/persona-rdm-gh-pm-2026-05-23.md`](agent-findings/persona-rdm-gh-pm-2026-05-23.md) | Persona review — Research Data Manager / FAIR Steward | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-reluctant-senior-2026-05-24.md`](agent-findings/persona-reluctant-senior-2026-05-24.md) | Reluctant Senior Researcher — re-walk on live, 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/persona-review-ai-opportunities.md`](agent-findings/persona-review-ai-opportunities.md) | Persona Review — Analytics & AI Opportunities Specialist | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-api-scrutinizer.md`](agent-findings/persona-review-api-scrutinizer.md) | API Scrutinizer — review of the `/v2/views` + view-shapes proposal | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-digital-native.md`](agent-findings/persona-review-digital-native.md) | Persona Review: Digital Native Researcher (2026-05-22) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-ime-aqe.md`](agent-findings/persona-review-ime-aqe.md) | Persona review — Industrial Manufacturing & Quality Engineer (IME/AQE) on the SHACL trio | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-ontologist.md`](agent-findings/persona-review-ontologist.md) | Persona review — Data & Process Ontologist on the SHACL trio | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-rdm.md`](agent-findings/persona-review-rdm.md) | Persona review — Research Data Manager (FAIR Steward) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/persona-review-reluctant-senior.md`](agent-findings/persona-review-reluctant-senior.md) | Reluctant Senior Researcher — review of the MFFD shapes / views / SPI proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/repo-task-sweep-2026-05-23.md`](agent-findings/repo-task-sweep-2026-05-23.md) | Repo task sweep — 2026-05-23 | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/research-data-manager.md`](agent-findings/research-data-manager.md) | Shepard FAIR Compliance Evaluation | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/shacl-changeover-non-ts.md`](agent-findings/shacl-changeover-non-ts.md) | SHACL changeover (non-TS scope) — implementation log | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/sidecars-spi-extension.md`](agent-findings/sidecars-spi-extension.md) | PM1f — `PluginManifest.sidecars()` SPI extension + file-s3 Garage declaration | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/strategy-advisor.md`](agent-findings/strategy-advisor.md) | Shepard — Strategic Advisor Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/test-cleanup-2026-05-22.md`](agent-findings/test-cleanup-2026-05-22.md) | Backend test cleanup — 2026-05-22 | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/trace3d-spike.md`](agent-findings/trace3d-spike.md) | Trace3D view — spike: library shortlist + view-shape input contract | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ui-annoyances.md`](agent-findings/ui-annoyances.md) | UI annoyances — running log | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ui-scrutinizer-2026-05-24.md`](agent-findings/ui-scrutinizer-2026-05-24.md) | UI Scrutinizer — live-shepard systematic walk, 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ux-auditor.md`](agent-findings/ux-auditor.md) | UX Auditor — Discovery Report | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ux-progress-indicators-sweep.md`](agent-findings/ux-progress-indicators-sweep.md) | UX progress indicators — Playwright-driven sweep (task #136) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15-import-implementation.md`](agent-findings/v15-import-implementation.md) | v15 MFFD-import — implementation report | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15-review-data-ontologist.md`](agent-findings/v15-review-data-ontologist.md) | v15 review — Data & Process Ontologist lens | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15-review-ime-aqe.md`](agent-findings/v15-review-ime-aqe.md) | v15 review — IME + AQE lens | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15-review-rdm.md`](agent-findings/v15-review-rdm.md) | v15 import review — RDM / FAIR Data Steward lens | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15-review-reluctant-senior.md`](agent-findings/v15-review-reluctant-senior.md) | v15 import — review from the reluctant senior researcher | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15.1-implementation.md`](agent-findings/v15.1-implementation.md) | v15.1 MFFD-import — implementation report | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/v15.2-implementation.md`](agent-findings/v15.2-implementation.md) | v15.2 — Smart warmup phase (IMPORT-W1/W2/W3) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/vis-plugin-survey-addendum-cad-fem.md`](agent-findings/vis-plugin-survey-addendum-cad-fem.md) | Visualization plugin survey — addendum: CAD + FEM | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/vis-plugin-survey.md`](agent-findings/vis-plugin-survey.md) | Visualization plugin survey | 2026-05-23 | 2026-05-23 |
| [`aidocs/integrations/94-log-store-sidecar-design.md`](integrations/94-log-store-sidecar-design.md) | 94 — Log-store-with-shape sidecar design | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/95-shacl-templates-and-individuals.md`](semantics/95-shacl-templates-and-individuals.md) | 95 — SHACL templates, named individuals, and ontology-driven UI | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/98-shapes-views-and-process-model.md`](semantics/98-shapes-views-and-process-model.md) | 98 — Shapes, views, and the MFFD process model | 2026-05-23 | 2026-05-23 |
| [`aidocs/semantics/99-promptlog-design.md`](semantics/99-promptlog-design.md) | 99 — PromptLog: prompts as first-class Shepard artefacts | 2026-05-23 | 2026-05-23 |

## feedback-implemented (1)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/rdm-002-orcid-input-2026-05-24.md`](agent-findings/rdm-002-orcid-input-2026-05-24.md) | RDM-002 — ORCID input on `/me/profile` (FAIR R1) | 2026-05-24 | 2026-05-24 |

## tests-implemented (4)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/bug-lj-v1-coll-id-fix-2026-05-24.md`](agent-findings/bug-lj-v1-coll-id-fix-2026-05-24.md) | BUG-LJ-V1-COLL-ID — fix report | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/prov-resolver-fix-2026-05-24.md`](agent-findings/prov-resolver-fix-2026-05-24.md) | PROV-RESOLVER-PATHWALK + PROV-V1-NUMERIC-LOOKUP — fix report | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-003-004-section-landing-fix-2026-05-24.md`](agent-findings/ui-003-004-section-landing-fix-2026-05-24.md) | UI-2026-05-24-003 + UI-2026-05-24-004 fix — section index landings + Unauthorized view | 2026-05-24 | 2026-05-24 |
| [`aidocs/integrations/93-mffd-import-v15-requirements.md`](integrations/93-mffd-import-v15-requirements.md) | 93 — MFFD real-data import (v15) requirements | 2026-05-23 | 2026-05-23 |

## deployed (58)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/00-doc-stages.md`](00-doc-stages.md) | 00 — Doc lifecycle stages (taxonomy SSOT) | 2026-05-23 | 2026-05-23 |
| [`aidocs/00-index.md`](00-index.md) | aidocs — Index | 2026-05-23 | 2026-05-23 |
| [`aidocs/100-ui-annoyances.md`](100-ui-annoyances.md) | 100 — Shepard UI annoyances (live captured) | 2026-05-23 | 2026-05-23 |
| [`aidocs/16-dispatcher-backlog.md`](16-dispatcher-backlog.md) | 16 — Dispatcher Backlog | 2026-05-23 | 2026-05-24 |
| [`aidocs/34-upstream-upgrade-path.md`](34-upstream-upgrade-path.md) | Upstream upgrade path — `dlr-shepard/shepard 5.2.0` → `noheton/shepard main` | 2026-05-23 | 2026-05-24 |
| [`aidocs/40-ecosystem.md`](40-ecosystem.md) | 40 — Shepard ecosystem | 2026-05-23 | 2026-05-23 |
| [`aidocs/41-synergy-sweep.md`](41-synergy-sweep.md) | 41 — Synergy sweep: collapse-where-generalisation-helps | 2026-05-23 | 2026-05-23 |
| [`aidocs/42-vision.md`](42-vision.md) | shepard — Vision (for researchers) | 2026-05-23 | 2026-05-22 |
| [`aidocs/44-fork-vs-upstream-feature-matrix.md`](44-fork-vs-upstream-feature-matrix.md) | Fork vs Upstream — Feature Matrix | 2026-05-23 | 2026-05-24 |
| [`aidocs/97-shepard-pipelines.md`](97-shepard-pipelines.md) | 97 — Shepard-pipelines: modern REBAR, Shepard-native | 2026-05-23 | 2026-05-23 |
| [`aidocs/99-api-annoyances.md`](99-api-annoyances.md) | 99 — Shepard API annoyances (structural clunkiness) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/audience-frontmatter-retrofit-2026-05-23.md`](agent-findings/audience-frontmatter-retrofit-2026-05-23.md) | Audience-persona front-matter retrofit (DOCS-3A9) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/gh-pm-adoption-synthesis-2026-05-23.md`](agent-findings/gh-pm-adoption-synthesis-2026-05-23.md) | Synthesis — GH-PM (policy doc 85) adoption decisions | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md`](agent-findings/gh-pm-backfill-plan-2026-05-23.md) | GH-PM5 backfill — plan + execution log (2026-05-23) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/lic1-shipped-2026-05-24.md`](agent-findings/lic1-shipped-2026-05-24.md) | LIC1 shipped — license + accessRights end-to-end (2026-05-24) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/mffd-import-slowness-diagnose-2026-05-23.md`](agent-findings/mffd-import-slowness-diagnose-2026-05-23.md) | MFFD cube3 import slowness — diagnostic | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/ops-cleanup-2026-05-24.md`](agent-findings/ops-cleanup-2026-05-24.md) | ops-cleanup-2026-05-24 — un-ignore neo4j migrations + worktree env bootstrap | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ops-hygiene-bundle-2026-05-24.md`](agent-findings/ops-hygiene-bundle-2026-05-24.md) | OPS hygiene bundle — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ops-migration-healthcheck-2026-05-24.md`](agent-findings/ops-migration-healthcheck-2026-05-24.md) | ops-migration-healthcheck-2026-05-24 — readiness gate on Neo4j migration-chain integrity | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/persona-audit-admin-stale-ch-2026-05-23.md`](agent-findings/persona-audit-admin-stale-ch-2026-05-23.md) | Persona audit — ADMIN-STALE-CH (Stale timeseries channel admin tool) | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`](agent-findings/plugin-docs-gap-audit-2026-05-23.md) | Plugin documentation gap audit — 2026-05-23 | 2026-05-23 | 2026-05-23 |
| [`aidocs/agent-findings/rdm-001-cite-this-dataset-2026-05-24.md`](agent-findings/rdm-001-cite-this-dataset-2026-05-24.md) | RDM-001 — "Cite this dataset" card on Collection landing (2026-05-24) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/rdm-005-metadata-completeness-2026-05-24.md`](agent-findings/rdm-005-metadata-completeness-2026-05-24.md) | RDM-005 — Metadata Completeness Score widget (live) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-002-header-search-fix-2026-05-24.md`](agent-findings/ui-002-header-search-fix-2026-05-24.md) | UI-002 — Header-search dropdown fix (2026-05-24) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-005-watches-me-silenced-2026-05-24.md`](agent-findings/ui-005-watches-me-silenced-2026-05-24.md) | UI-005 — silence `/watches/me` 404 spam on collection landing | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-006-007-009-015-home-polish-2026-05-24.md`](agent-findings/ui-006-007-009-015-home-polish-2026-05-24.md) | Home + file-row polish pass — UI-006/007/009/015 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-011-collections-list-fix-2026-05-24.md`](agent-findings/ui-011-collections-list-fix-2026-05-24.md) | UI-011 — Collections list page column enrichment | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-011e-do-count-fix-2026-05-24.md`](agent-findings/ui-011e-do-count-fix-2026-05-24.md) | UI-011e — `# DOs` column shows 0 everywhere on `/collections` — fix | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-013-help-search-anchors-2026-05-24.md`](agent-findings/ui-013-help-search-anchors-2026-05-24.md) | UI-2026-05-24-013 — in-page help search + per-heading anchors (CLOSED) | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-016-017-polish-2026-05-24.md`](agent-findings/ui-016-017-polish-2026-05-24.md) | UI-016 + UI-017 cosmetic polish — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-020-labjournal-bulk-fix-2026-05-24.md`](agent-findings/ui-020-labjournal-bulk-fix-2026-05-24.md) | UI-020 — Lab-journal bulk fetch closes per-DataObject N+1 | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ui-021-sd-list-fix-2026-05-24.md`](agent-findings/ui-021-sd-list-fix-2026-05-24.md) | UI-021 — `/containers/structureddata` "shows 0 rows" — close as WAI/misreport | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ux-pattern-d-count-badges-2026-05-24.md`](agent-findings/ux-pattern-d-count-badges-2026-05-24.md) | UX Pattern D — count badges on DataObject reference panels | 2026-05-24 | 2026-05-24 |
| [`aidocs/agent-findings/ux-polish-bundle-2026-05-24.md`](agent-findings/ux-polish-bundle-2026-05-24.md) | UX polish bundle — 2026-05-24 | 2026-05-24 | 2026-05-24 |
| [`aidocs/case-study-2026-05-19.md`](case-study-2026-05-19.md) | Case Study: One Month of AI-Assisted Fork Development | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/00-model-inventory.md`](data/00-model-inventory.md) | 00 — Model inventory (SSOT) | 2026-05-23 | 2026-05-24 |
| [`aidocs/data/37-lab-journal-and-jupyter-design.md`](data/37-lab-journal-and-jupyter-design.md) | Lab Journal Reassessment + Jupyter Feasibility — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/41-snapshots-design.md`](data/41-snapshots-design.md) | Snapshots — Design (versioning, reloaded) | 2026-05-23 | 2026-05-23 |
| [`aidocs/data/68-timeseries-data-model-tuning.md`](data/68-timeseries-data-model-tuning.md) | TimescaleDB Data Model Tuning — Recommendations | 2026-05-23 | 2026-05-23 |
| [`aidocs/handover-2026-05-19.md`](handover-2026-05-19.md) | Shepard Fork — Handover Document | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/59-performance-testing-and-tuning.md`](ops/59-performance-testing-and-tuning.md) | Performance testing + auto-tuning — design | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/75-api-integration-test-suite.md`](ops/75-api-integration-test-suite.md) | API-Level Integration Test Suite — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/77-k6-performance-metrics.md`](ops/77-k6-performance-metrics.md) | k6 Per-Endpoint SLO Matrix + `/v2/` Coverage | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/85-ui-overhaul-design.md`](ops/85-ui-overhaul-design.md) | 85 — UI Overhaul: Critique, Opportunities, and Roadmap | 2026-05-23 | 2026-05-23 |
| [`aidocs/ops/87-collection-container-duality.md`](ops/87-collection-container-duality.md) | 87 — Collection / Container Duality: Design Discussion | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/25-neo4j-id-migration-design.md`](platform/25-neo4j-id-migration-design.md) | Neo4j ID Migration — Design (L2) | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/47-dev-experience-and-plugin-system.md`](platform/47-dev-experience-and-plugin-system.md) | Dev Experience + Storage-Backend Plugin System — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/85-openapi-llm-readable.md`](platform/85-openapi-llm-readable.md) | OpenAPI documentation standard — LLM-readable | 2026-05-23 | 2026-05-23 |
| [`aidocs/platform/87-timeseries-appid-migration.md`](platform/87-timeseries-appid-migration.md) | 87 — Timeseries appId Migration (TS-ID) | 2026-05-23 | 2026-05-23 |
| [`aidocs/plugins/69-aas-plugin-extraction-design.md`](plugins/69-aas-plugin-extraction-design.md) | AAS → plugin extraction — Design (AAS1-plugin) | 2026-05-23 | 2026-05-23 |
| [`aidocs/reading-list.md`](reading-list.md) | Reading list | 2026-05-23 | 2026-05-23 |
| [`aidocs/roadmap.md`](roadmap.md) | Shepard Fork — Roadmap | 2026-05-23 | 2026-05-23 |
| [`aidocs/strategy/85-github-project-management-policies.md`](strategy/85-github-project-management-policies.md) | 85 — GitHub project-management policies (traceability SSOT) | 2026-05-23 | 2026-05-23 |
| [`aidocs/ux/82-basic-vs-advanced-mode-matrix.md`](ux/82-basic-vs-advanced-mode-matrix.md) | 82 — Basic vs Advanced Mode Feature Matrix | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/36-user-profile-and-settings-design.md`](workflows/36-user-profile-and-settings-design.md) | User Profile & Settings — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/38-git-integration-design.md`](workflows/38-git-integration-design.md) | Git Integration — Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/39-templates-design.md`](workflows/39-templates-design.md) | Templates — Implementation Design | 2026-05-23 | 2026-05-23 |
| [`aidocs/workflows/64-provenance-architecture.md`](workflows/64-provenance-architecture.md) | 64 — Provenance architecture (what's shipped + where it's going) | 2026-05-23 | 2026-05-23 |

## decommissioned (47)

| doc | title | last-stage-change | last-touched |
|---|---|---|---|
| [`aidocs/agent-findings/predecessor-history-correction-2026-05-23.md`](agent-findings/predecessor-history-correction-2026-05-23.md) | Predecessor history — standing correction | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/01-repo-overview.md`](archive/01-repo-overview.md) | Repo Overview — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/02-cluster-map.md`](archive/02-cluster-map.md) | Cluster Map — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/03-issues-status.md`](archive/03-issues-status.md) | Issues Status — shepard (GitLab open items) | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/04-reconciliation.md`](archive/04-reconciliation.md) | Reconciliation — GitHub mirror vs GitLab authoritative | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/05-dependency-report.md`](archive/05-dependency-report.md) | Dependency Report — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/06-code-quality.md`](archive/06-code-quality.md) | Code Quality — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/07-security-issues.md`](archive/07-security-issues.md) | Security Issues — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/08-first-issues.md`](archive/08-first-issues.md) | First Issues — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/09-ready-to-close.md`](archive/09-ready-to-close.md) | Ready to Close on GitHub — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/10-cleanup-plan.md`](archive/10-cleanup-plan.md) | Cleanup Plan — shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/15-phase-0-status.md`](archive/15-phase-0-status.md) | Phase 0 — Housekeeping Status | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/17-startup-wait-audit.md`](archive/17-startup-wait-audit.md) | 17 — Startup Wait / Retry Audit | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/18-pagination-inventory.md`](archive/18-pagination-inventory.md) | Pagination — Inventory & Sized Rollout Plan | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/21-user-interest-gauge.md`](archive/21-user-interest-gauge.md) | User / Scientific Interest Gauge — HDF5, Tabular Storage, KG Interfaces | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/ai-in-science-policy-alignment.md`](archive/agent-findings-sessions-2026-05/ai-in-science-policy-alignment.md) | AI-in-Science policy alignment — does Shepard's AI design match the prevailing authoritative consensus? | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/analytics-ai-debate.md`](archive/agent-findings-sessions-2026-05/analytics-ai-debate.md) | Analytics & AI Opportunities Specialist — Phase 2 Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/analytics-ai-proposals.md`](archive/agent-findings-sessions-2026-05/analytics-ai-proposals.md) | AI & Analytics Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/api-scrutinizer-debate.md`](archive/agent-findings-sessions-2026-05/api-scrutinizer-debate.md) | API Scrutinizer — Phase 2 Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/api-scrutinizer-proposals.md`](archive/agent-findings-sessions-2026-05/api-scrutinizer-proposals.md) | API Scrutinizer — Concrete Improvement Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/data-ontologist-debate.md`](archive/agent-findings-sessions-2026-05/data-ontologist-debate.md) | Data & Process Ontologist — Debate Output | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/data-ontologist-proposals.md`](archive/agent-findings-sessions-2026-05/data-ontologist-proposals.md) | Data Ontologist — Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/database-antipatterns-audit.md`](archive/agent-findings-sessions-2026-05/database-antipatterns-audit.md) | Database anti-pattern audit — Neo4j + Postgres/Hibernate + MongoDB/GridFS + PostGIS + Garage S3 | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/database-schema-research-multi-db.md`](archive/agent-findings-sessions-2026-05/database-schema-research-multi-db.md) | Database schema research — multi-substrate sweep (Neo4j + Postgres + MongoDB + PostGIS + Garage) | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/easa-ai-compliance.md`](archive/agent-findings-sessions-2026-05/easa-ai-compliance.md) | EASA AI Concept Paper Issue 2 — Compliance Gap Analysis | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/ecosystem-advocate-debate.md`](archive/agent-findings-sessions-2026-05/ecosystem-advocate-debate.md) | Ecosystem Advocate Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/ecosystem-advocate-proposals.md`](archive/agent-findings-sessions-2026-05/ecosystem-advocate-proposals.md) | Ecosystem Advocate — Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/industrial-robotics-ontology-audit.md`](archive/agent-findings-sessions-2026-05/industrial-robotics-ontology-audit.md) | Industrial Robotics + ZLP-Domain Ontology Audit (Shepard scope) | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/manufacturing-quality-debate.md`](archive/agent-findings-sessions-2026-05/manufacturing-quality-debate.md) | Manufacturing & Quality Engineering — Cross-Agent Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/manufacturing-quality-proposals.md`](archive/agent-findings-sessions-2026-05/manufacturing-quality-proposals.md) | Manufacturing & Quality Engineering — Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/mffd-paper-198366.md`](archive/agent-findings-sessions-2026-05/mffd-paper-198366.md) | MFFD Paper 198366 — Comprehensive Extraction | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/performance-baseline-2026-05-21.md`](archive/agent-findings-sessions-2026-05/performance-baseline-2026-05-21.md) | Shepard performance baseline — 2026-05-21 | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/persona-debate-round.md`](archive/agent-findings-sessions-2026-05/persona-debate-round.md) | Persona Debate Round — Reluctant Senior vs. Digital Native | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/persona-digital-native.md`](archive/agent-findings-sessions-2026-05/persona-digital-native.md) | Persona: Digital Native Researcher — Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/persona-reluctant-senior.md`](archive/agent-findings-sessions-2026-05/persona-reluctant-senior.md) | Reluctant Senior Researcher — Persona Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/plugin-registry-trust.md`](archive/agent-findings-sessions-2026-05/plugin-registry-trust.md) | Plugin Registry Trust Mechanisms — Convenience-First Survey | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/quarkus-ecosystem.md`](archive/agent-findings-sessions-2026-05/quarkus-ecosystem.md) | Quarkus Extension Ecosystem — Opportunities for Shepard | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/quarkus-mcp-kadi-gaps.md`](archive/agent-findings-sessions-2026-05/quarkus-mcp-kadi-gaps.md) | Quarkus MCP, Kadi4Mat, and RDM Ecosystem Gaps — Findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/research-data-manager-debate.md`](archive/agent-findings-sessions-2026-05/research-data-manager-debate.md) | Research Data Manager — Cross-Agent Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/research-data-manager-proposals.md`](archive/agent-findings-sessions-2026-05/research-data-manager-proposals.md) | Proposal 1: KIP1e — License Field on All Entities (Core, foundational) | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/strategy-advisor-debate.md`](archive/agent-findings-sessions-2026-05/strategy-advisor-debate.md) | Strategy Advisor — Cross-Agent Debate | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/strategy-advisor-proposals.md`](archive/agent-findings-sessions-2026-05/strategy-advisor-proposals.md) | Strategy Advisor — Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/timescaledb-schema-research.md`](archive/agent-findings-sessions-2026-05/timescaledb-schema-research.md) | TimescaleDB schema research — is the schema the MFFD ingest bottleneck? | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/ux-auditor-debate.md`](archive/agent-findings-sessions-2026-05/ux-auditor-debate.md) | UX Auditor Debate — Core Tech & UX Lens | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/ux-auditor-proposals.md`](archive/agent-findings-sessions-2026-05/ux-auditor-proposals.md) | UX Auditor — Feature Proposals | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/agent-findings-sessions-2026-05/v1-compat-live-validation.md`](archive/agent-findings-sessions-2026-05/v1-compat-live-validation.md) | V1COMPAT.0 Phase 1 — live validation findings | 2026-05-23 | 2026-05-23 |
| [`aidocs/archive/platform/68-v2-baseline-v1-compat-layer.md`](archive/platform/68-v2-baseline-v1-compat-layer.md) | V2 baseline + `/shepard/api/` as compat layer — Design (V2BASE) | 2026-05-23 | 2026-05-23 |
