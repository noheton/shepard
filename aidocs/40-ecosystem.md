---
stage: deployed
last-stage-change: 2026-05-23
---

# 40 — Shepard ecosystem

**Status.** Living document. Updated whenever a repo enters or leaves
the ecosystem, a plugin design lands, or a standard is adopted.
**Snapshot date.** 2026-05-21.
**Audience.** Contributors, operators, evaluators, funders. Sister
docs: `aidocs/42-vision.md` (researcher pitch),
`aidocs/44-fork-vs-upstream-feature-matrix.md` (per-feature progress),
`aidocs/34-upstream-upgrade-path.md` (operator-facing change ledger).

Shepard is one node in a wider ecosystem: an upstream code base at
`gitlab.com/dlr-shepard`, a fork that adds feature surface
(`github.com/noheton/shepard`), a family of adjacent DLR-internal
tools that already exchange data with Shepard, a queue of designed
plugins that will turn Shepard from a monolith into an extensible
platform, and a layer of community standards and proposals Shepard
reads from and (in places) contributes back to. This document
enumerates that ecosystem so the position of the fork is legible to
new contributors, operators considering adoption, and funders
auditing the surface area.

---

## 1. The upstream — `gitlab.com/dlr-shepard`

The upstream organisation hosts the canonical shepard release line.
The fork (§3) tracks its `5.2.0` baseline.

Repositories (queried 2026-05-21 via the GitLab API):

| Repo | Default branch | Archived | Role |
|---|---|---|---|
| `dlr-shepard/shepard` | `develop` | active | The consolidated mono-repo: backend + frontend + documentation. The fork's `5.2.0` baseline. |
| `dlr-shepard/architecture` | `main` | archived | Pre-mono-repo architecture notes. Historical. |
| `dlr-shepard/backend` | `main` | archived | Pre-mono-repo backend. Historical. |
| `dlr-shepard/frontend` | `main` | archived | Pre-mono-repo frontend. Historical. |
| `dlr-shepard/documentation` | `main` | archived | Pre-mono-repo docs. Historical. |
| `dlr-shepard/deployment` | `main` | archived | Pre-mono-repo Docker Compose recipes. Historical. |
| `dlr-shepard/shepard-timeseries-collector` (sTC) | `main` | active | OPC UA / MQTT / fieldbus → Shepard timeseries-container collector. Configured per-source via YAML. Companion service, not bundled with the backend. |
| `dlr-shepard/shepard-process-wizard` | `main` | active | GUI for modelling and running a templated process in Shepard. Pre-dates the in-platform Templates work (T1 series). |
| `dlr-shepard/processcontrol` | `main` | active | GUI for process control using Shepard plus an optional machine controller (Allen-Bradley / PLC-style). |
| `dlr-shepard/publication` | `main` | active | Helper for bundling Shepard releases to Zenodo. |
| `dlr-shepard/shepard-edc-proxy` | `main` | archived | Eclipse Dataspace Connector proxy — superseded. |
| `dlr-shepard/shepard-releases` | `main` | archived | Release-automation Python script. Superseded by GitLab CI. |
| `dlr-shepard/management/security-policy-project` | `main` | active | Auto-generated security policy. Infra. |
| `dlr-shepard/gitlab-profile` | `main` | active | Group README on GitLab. |

The fork (§3) tracks `dlr-shepard/shepard` on the consolidated
mono-repo path. The other active repos (sTC, process-wizard,
process-control, publication) are companion services that talk to
either upstream or the fork through the public REST API — they are
not forked here.

---

## 2. DLR-internal Shepard ecosystem

Adjacent tooling at DLR (mostly DLR Augsburg ZLP, plus the
cross-institute REBAR cooperation). All on `gitlab.dlr.de` unless
otherwise noted. Repos behind DLR authentication are flagged
explicitly.

| Tool | Repo | Stack | Status | Role |
|---|---|---|---|---|
| **`data_reference_generator` (DRG)** | `gitlab.dlr.de/zlp-augsburg/data_reference_generator` [DLR-internal] | FastAPI + Vue.js + asyncua + pyinstaller | Active (in-cell binary at MFZ Augsburg) | OPC UA → Shepard bridge for the AFP cell. Auto-creates the Project/Experiment/Step hierarchy as manufacturing progresses; subscribes to PLC variables `VC_PLYNUMBER` / `VC_TRACKNO`; registers timeseries references against the channels declared in `timeseries.json`. Established the 5-tuple channel descriptor (`measurement`, `device`, `location`, `symbolicName`, `field`) that the rest of Shepard inherited. Reference implementation for `shepard-plugin-industrial` (§4). |
| **`shepard-dataship`** | `gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship` [DLR-internal] | Python 3.11+ / NiceGUI / OpenAI SDK / uv | Active, v0.x — currently running an MFFD export | Browser wizard publishing Shepard collections to the DBpedia Databus. DataSource SPI (Shepard / Filesystem); SHA-256 + byte-size on every distribution Part; SPARQL-driven DALICC license + Databus group/artifact picker; AI-assisted metadata fill; JSON-LD deposition preview; one-click `POST /api/register`. Reference implementation for the Databus adapter of the planned `shepard-plugin-publish` (§4). Today bound to the upstream v1 API; switching to v2 + appId is the near-term integration. |
| **`shepard-stc-config-helper`** | `gitlab.dlr.de/zlp-augsburg/inner-source/shepard-stc-config-helper` [DLR-internal] | Python + uv | Active | Generates sTC YAML config from a list of OPC UA node IDs (source + sink blocks, optional Grafana dashboard). Fills the gap left by Shepard's missing "wire this container to a live OPC UA source" UI. Today writes numeric container IDs; needs an update after the TS-appId migration (`aidocs/platform/87`). |
| **`infusion-analysis` / ODIX** | local at ZLP; not fully public [DLR-internal] | Jupyter + `shepard-client >5.0.0` + `sparqlwrapper` + pandas + networkx | Early prototype; loop demonstrated in production | Semantic process-analysis loop over Liquid Resin Infusion data: ontology-stored constraints (`:hasConstraint` min/max) checked against timeseries metrics; out-of-band readings flagged with `:causesDefect` consequences (e.g. `:DrySpot`). The hand-coded precedent for SHACL-driven QA (`aidocs/semantics/95 §14ee`). Targets `frontend.bt-au-cube3.intra.dlr.de` — a separate Shepard instance from the ZLP general install. |
| **`rebar-infrastructure`** | `gitlab.dlr.de/rebar/rebar-infrastructure` [DLR-internal] | Apache Airflow + MLflow + MinIO + Marquez / OpenLineage + Postgres + Redis + Celery, docker-compose | Active reference (last commit 2024); pattern, not necessarily integration target | Cross-institute (BT-BGF, BT-ZAP, FK/ZLP, KI, MO, RM, SC-IVS, WF) MLOps cooperation. Airflow DAGs as the pipeline unit, MLflow for tracking, MinIO for artefacts, Marquez emitting OpenLineage. Shepard is the FAIR-publish + regulatory-evidence wrapper on top: REBAR runs the computation, Shepard captures the lineage. Integration via OpenLineage/MLflow/FAIR4ML standards, **not** REBAR-specific — `shepard-plugin-mlops` (§4) is the generic adapter. Server: `bt-au-rebar.intra.dlr.de`. |
| **`instdlr` (INST.DLR)** | Helmholtz Cloud, maintained by Federico Díaz Capriles (DLR); DOI `10.5281/zenodo.15180781` | FastAPI + MongoDB + Caddy | Mature, citable | PIDINST-schema instrument registry. Mints PIDs for physical instruments (manufacturer, model, serial number, measured-variable). The missing link for EN 9100 calibration traceability — a Shepard `SemanticAnnotation` with `propertyIRI = schema:instrument` and `valueIRI = <instdlr-handle>` ties the data to its calibrated source instrument. |

Both active named Shepard instances at the institute level are part
of the picture: `shepard BT` at Augsburg / ZLP and `shepard RY`
(likely Cologne / WF). The DataHub strategy treats them as peers
publishing into Databus alongside InvenioRDM, Geoserver, GitLab, and
the Industrial Data Spaces (`project_dlr_institutional_strategy`).

---

## 3. The fork — `github.com/noheton/shepard`

This repository. Tracks upstream `5.2.0` (the version pinned in
`infrastructure/docker-compose.yml`). The standing rule in `CLAUDE.md`
keeps the upstream API surface (`/shepard/api/...`) byte-frozen; all
fork additions land at `/v2/...`. The full delta lives in
`aidocs/34-upstream-upgrade-path.md` (admin-facing) and
`aidocs/44-fork-vs-upstream-feature-matrix.md` (contributor-facing).

Header-level summary of what's been added (see `aidocs/42-vision.md`
for the live researcher-facing list):

- **Resilience.** Bounded migration wait, fail-fast `MigrationsRunner`,
  per-DB health, automated DB recovery scheduler, RFC 7807 error envelopes,
  graceful-degradation 503 + `Retry-After` when an optional DB is down.
- **Identifiers.** UUID-v7 `appId` on every node-write (the L2 chain);
  the `/v2/` shelf is appId-native.
- **Auth + admin.** Single `instance-admin` role tier; bootstrap-token
  mechanism; permission audit log; nuclear instance reset; API keys with
  `validUntil` + roles claim.
- **Plugin SPI.** `PluginManifest` interface + drop-in
  `backend/plugins/*.jar` discovery + per-plugin runtime toggle
  (PM1a shipped). First plugins under the new shape:
  `shepard-plugin-unhide` (UH1), `shepard-plugin-minter-local` and
  `shepard-plugin-minter-datacite` (KIP1h/d), `shepard-plugin-ai`
  (AI1a), `shepard-plugin-wiki-writer` (WW1).
- **Provenance + publication.** Every mutation → PROV-O `:Activity`;
  three export shapes (plain JSON / PROV-N JSON / PROV-O JSON-LD) +
  the metadata4ing flavour; HMC KIP publish surface with DOI minting
  via DataCite; Helmholtz Unhide JSON-LD feed.
- **Snapshots, templates, lab journal, payload versioning, video,
  HDF5 (HSDS), git references, file-storage SPI, internal semantic
  repository with pre-seeded ontologies.**

---

## 4. Planned plugins (designed but not yet built)

The plugin family is the structural fix for "where do new features
live?" Per the plugin-first rule in `CLAUDE.md`, any new payload kind,
external integration, or cross-cutting capability defaults to a
plugin. The roadmap as of this snapshot:

| Plugin | Status | Role | Design refs |
|---|---|---|---|
| **`shepard-plugin-unhide`** | ✓ shipped (UH1a–c) | Helmholtz Knowledge Graph publish — `/v2/unhide/feed.jsonld` schema.org + metadata4ing feed with PROV-O inlined and KIP citation | `aidocs/integrations/67` |
| **`shepard-plugin-minter-local`** | ✓ shipped (KIP1h) | Default local PID minter — versioned `shepard:<instance>:<kind>:<appId>:v<n>` | `aidocs/integrations/66` |
| **`shepard-plugin-minter-datacite`** | ✓ shipped (KIP1d) | Real DOIs via DataCite (Fabrica test + production); `IsNewVersionOf` / `HasVersion` chains | `aidocs/integrations/66` |
| **`shepard-plugin-ai`** | ⚙ AI1a shipped (LlmProvider SPI + capability slots) | TEXT / FAST_TEXT / STRUCTURED capabilities; OpenAI-compatible; BYOK + admin-fallback; `:AiActivity` provenance | `aidocs/platform/86`, `aidocs/semantics/43` |
| **`shepard-plugin-wiki-writer`** (WW1) | ✓ shipped | Generate Markdown lab-journal entry / Confluence-shaped Markdown from DataObject + Collection context; user PAT + URL in profile | `project_wiki_writer_plugin` |
| **`shepard-plugin-mcp`** | ✓ shipped (task #30) | MCP server surface — Shepard tools callable from Claude / agentic clients via `/mcp/sse` (Zoraxy virtual-dir routed; Keycloak public PKCE client) | `aidocs/platform/30`, `aidocs/platform/88` |
| **`shepard-plugin-publish`** | 📐 designed | Unified publication surface absorbing UH1 (Helmholtz Unhide) + Dataship (DBpedia Databus) + planned OpenAIRE / re3data / DataCite / InvenioRDM adapters; per-registry sub-modules under `adapter/<registry>` | `aidocs/integrations/67`, `aidocs/integrations/72`, `aidocs/integrations/77`, `project_dataship_federation` |
| **`shepard-plugin-mlops`** | 📐 designed | OpenLineage receiver + MLflow Tracking poller + S3/MinIO artefact pointer mirroring + FAIR4ML SHACL shape; agnostic across Airflow / Prefect / Dagster / ZenML / Databricks. REBAR is the reference architecture, not the integration target | `aidocs/integrations/83`, `project_rebar_integration` |
| **`shepard-plugin-industrial`** | 📐 designed | Generalisation of DRG: SHACL-shape-declared OPC UA / Modbus / PROFINET subscription; auto-create hierarchy on trigger conditions; OpenLineage emission. Pairs with the importer plugin and the sTC config helper | `aidocs/semantics/95 §15 TPL15` (planned), `project_drg_tooling` |
| **`shepard-plugin-importer`** | 📐 designed | Library-of-importers: built-in importers + git-referenced importer modules; admin sets default library repo; user picks importer in UI. The "agentic import" path | `project_importer_plugin` |
| **`shepard-plugin-tables`** (TableContainer) | 📐 designed | Teable-inspired native Postgres tables with REST + SQL surface; joins with timeseries | `project_table_container`, `casestudy_table_container` |
| **`shepard-plugin-spatial`** | 📐 designed | First-class spatial payload extension; PostGIS toggle generalised to a plugin | `aidocs/16` task #79 |
| **`shepard-plugin-matrix`** | 📐 designed | Matrix notification adapter (NotificationProducer); Matrix REST API (no SDK); E2E encryption is the key decision | `project_matrix_plugin`, `project_notification_system` |
| **`shepard-plugin-imagebundle`** | 📐 designed | EXIF-sorted image bundles with async preview / MP4 timelapse output; depends on video plugin | `project_imagebundle_design` |
| **`shepard-plugin-file-s3`** (FS1b) | 📐 designed | S3-compatible file backend (AWS S3 / R2 / B2 / Wasabi / Garage / SeaweedFS / Ceph RGW / MinIO) via the FileStorage SPI; presigned URLs unlock direct large-file transfer (FS1c) | `aidocs/integrations/45` (FS series) |
| **`shepard-plugin-aas`** (AAS1) | 📐 designed | Shepard as an Asset Administration Shell (Plattform Industrie 4.0) repository backend; IDTA Nameplate + TechnicalData + TimeSeriesData first | `aidocs/integrations/52` |
| **`shepard-plugin-hdf`** (PL1c) | ⚙ A5a/b/d shipped (no UI) | HDF5 / HSDS sidecar payload kind; `h5pyd` parity for existing analysis code | `aidocs/integrations/35` |
| **`shepard-plugin-video`** (VID1) | ⚙ VID1a shipped | First-class video payload; HLS + live ingest via MediaMTX sidecar + frame extraction queued | `aidocs/integrations/53` |
| **`shepard-plugin-git`** (G1) | ⚙ G1a–d shipped (UI in-flight) | Pinned git commit + path for analysis code provenance; loose / tracked / pinned snapshot modes | `aidocs/integrations/38` |
| **`shepard-plugin-dbpedia-ref`** (REF1a) | 📐 designed | DBpedia rich-reference plugin — annotation autocomplete and label resolution against the public DBpedia SPARQL endpoint | `aidocs/16` REF1a |
| **`shepard-plugin-minter-epic`** (KIP1c) | 📐 designed | ePIC handle minter — sibling of the DataCite minter for instances that want PIDs without DOI semantics | `aidocs/integrations/66` |
| **`shepard-plugin-airflow`** | 📐 designed (subset of mlops) | Airflow operators `ShepardReadOp` / `ShepardWriteOp` / `ShepardProvOp`; marquez-bridge sidecar translating OpenLineage events to Predecessor/Successor edges | `aidocs/integrations/83` Mode A/B/C |
| **`shepard-plugin-invenio-publish`** | 📐 designed | InvenioRDM push-deposit adapter — sub-module of `shepard-plugin-publish` | `aidocs/integrations/72` |

---

## 5. Adjacent community proposals (non-DLR)

These are referenced and consumed by Shepard but are not DLR work.
Citation hygiene matters here — confusing the author's personal
research with DLR work would be inappropriate.

| Resource | URL | License | Role for Shepard |
|---|---|---|---|
| **F(AI)²R reference implementation** | `github.com/noheton/f-ai-r` | MIT (code / RDF) + CC-BY-4.0 (manuscript) | Working PROV-O extension for AI-assisted research: classes (`AIAgent`, `HumanResearcher`, `Claim`, `Source`, `Prompt`, `Transcript`, `AuthoringPass`, `AuditPass`), verification ladder (`unverified` → `human-confirmed`), no-parentless-claim invariant. Same author as this fork; explicit personal-capacity declaration in the repo README. |
| **F(AI)²R originating paper** ("Obscurity Is Dead") | `github.com/noheton/Obscurity-Is-Dead` | CC-BY-4.0 | §8 of *Obscurity Is Dead: AI-Assisted Hacking, Key to Interoperability or Security Nightmare?* (Krebs, 2026) proposes F(AI)²R as a candidate FAIR-for-AI-assisted-research extension. §9.5 explicitly declares the work non-DLR. |

Shepard's relationship to F(AI)²R is structurally the same as its
relationship to PROV-O or DataCite: it adopts an emerging community
standard as a vendor-tier ontology and implements its invariants
via SHACL. Implementation lives at TPL9 in
`aidocs/semantics/95-shacl-templates-and-individuals.md` Part 15
(slices TPL9a–TPL9h, ~17 days).

---

## 6. Standards consumed

Shepard reads from these standards. Where pre-seeded into the
internal semantic repository, the bundle is listed (N1a/N1b/ONT1a/
ONT1b shipped per `aidocs/semantics/48` + `aidocs/semantics/65`).

| Layer | Standard | Shipped pre-seed? | Role |
|---|---|---|---|
| Provenance | **PROV-O** (W3C) | ✓ N1b | Activity capture; required for the no-parentless-claim invariant |
| Provenance | **metadata4ing (m4i)** (NFDI4Ing) | ✓ ONT1b | Engineering-research extension of PROV-O — `m4i:ProcessingStep`, `m4i:Method`, `m4i:Tool`, `m4i:InvestigatedObject`, `m4i:NumericalVariable` |
| Cross-cutting | **OBO Relation Ontology (RO)** | ✓ ONT1a | `part_of`, `has_part`, `derives_from`, `participates_in`, `has_input`, `has_output` — process-graph predicates |
| Identity | **Dublin Core** | ✓ N1b | Basic creator / date / rights vocabulary |
| Identity | **schema.org** | ✓ N1b | Dataset / Publication / Person — the Unhide feed shape |
| Identity | **FOAF** | ✓ N1b | Person / Agent for prov:Agent compatibility |
| Units | **QUDT** | ✓ N1b | Quantity / Unit IRIs |
| Units | **OM-2** (Ontology of Measure) | ✓ N1b | Alternative unit vocabulary; used by ODIX |
| Time | **W3C Time Ontology** | ✓ N1b | Time intervals and instants |
| Space | **GeoSPARQL** | ✓ N1b | Geometry IRIs for spatial annotations |
| Upper | **BFO 2020** | 📐 planned (TPL3) | Upper-level foundation for the upper-aligned design |
| Upper | **IAO** | 📐 planned (TPL3) | Information-artifact classes (Dataset / Document / Plan) |
| Materials | **EMMO** (European Materials Modelling Council) | 📐 planned | DLR is a co-developer; MFFD materials domain natively expressible |
| Materials | **CHAMEO** (CHADA / MaterialsDigital) | 📐 planned | Characterisation methodology — instruments, methods, samples |
| Sensors | **SOSA / SSN** (W3C) | 📐 planned | Sensor / Observation / Sample / Actuator vocabulary; planned upgrade for the timeseries semantic layer |
| Publication | **DataCite** | ✓ (consumed by KIP1d) | DOI metadata schema |
| Publication | **DCAT** (W3C) | partial (via schema.org) | Data catalogue vocabulary |
| MLOps | **OpenLineage** (LF AI & Data) | 📐 planned (mlops plugin) | RunEvent / Dataset / Job — the bridge to PROV-O for ML pipelines |
| MLOps | **FAIR4ML** (RDA) | 📐 planned (mlops plugin) | Model-card / training-config metadata |
| MLOps | **MLflow Tracking REST** | 📐 planned (mlops plugin) | De-facto API everyone speaks (MLflow, ZenML, ClearML, Databricks, Vertex AI underneath) |
| Industrial | **OPC UA** | active (via DRG + sTC) | The fieldbus Shepard reads from |
| Industrial | **Asset Administration Shell** (IDTA) | 📐 planned (AAS1) | Plattform Industrie 4.0 — Nameplate / TechnicalData / TimeSeriesData |
| AI provenance | **F(AI)²R** | 📐 planned (TPL9 vendor-tier) | AI-as-source vocabulary; see §5 |
| Aerospace | **CPACS** | 📐 candidate (`aidocs/agent-findings/dlr-ontology-catalog.md`) | DLR-maintained aircraft configuration schema; SKOS adapter planned |

For regulatory positioning relative to EASA Learning Assurance and
the EU AI Act, see `aidocs/agent-findings/easa-ai-regulatory-positioning.md`.

---

## 7. Standards Shepard contributes back to (potential)

These are the open contribution paths the fork has on its medium
horizon. Each becomes credible once the corresponding feature lands.

| Standard / community | What Shepard could contribute | When credible |
|---|---|---|
| **CPACS-OWL** | An SKOS adapter ontology bringing CPACS aircraft-configuration semantics into the OWL world. DLR-maintained, currently XSD-only. See `aidocs/agent-findings/dlr-ontology-catalog.md §Opportunity 2`. | After TPL3 (BFO/IAO/EMMO bootstrap) lands |
| **F(AI)²R** | The first operational platform implementing the proposal end-to-end. Reference implementation feedback loop; SHACL invariants validated at scale. | TPL9 ships |
| **OpenLineage** | A SHACL-shape-driven Receiver pattern showing how OpenLineage events map to PROV-O for the FAIR-publish side. | `shepard-plugin-mlops` ships |
| **NFDI4Ing m4i** | Real-world usage feedback against the metadata4ing flavour at the Unhide feed + provenance-export endpoints. Already in production. | Shipped today (ONT1b) |
| **Helmholtz Metadata Collaboration (HMC) Aeronautics, Space and Transport hub** | A working publication pipeline (`shepard-plugin-unhide`) other DLR institutes can adopt. | Shipped today (UH1) |
| **PIDINST** (RDA) | A reference SemanticAnnotation pattern linking measurement DataObjects to PIDINST instrument PIDs via `schema:instrument`. | Once instdlr integration lands |
| **upstream `dlr-shepard/shepard`** | Many fork additions (RFC 7807 error envelopes, fail-fast migrations, per-DB health, permission cache, secret-leak fixes) are upstream candidates. See `aidocs/34` for the change ledger. | Ad hoc per PR |

---

## 8. Integration architecture

```
              ┌─────────────────────────────────────────────────────────┐
              │  Community standards                                   │
              │  PROV-O · BFO · IAO · EMMO · CHAMEO · m4i · RO · QUDT  │
              │  schema.org · DataCite · OpenLineage · FAIR4ML         │
              │  AAS / IDTA · SOSA/SSN · CPACS · F(AI)²R               │
              └────────────────┬───────────────────────┬────────────────┘
                               │ consumed              │ contributed back
                               ▼                       ▲
   ┌──────────────────────────────────────────────────────────────┐
   │  Upstream: gitlab.com/dlr-shepard/shepard (5.2.0, frozen)    │
   │  /shepard/api/...   byte-stable upstream surface             │
   └──────────────────┬───────────────────────────────────────────┘
                      │ tracked + extended
                      ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  Fork: github.com/noheton/shepard (this repo)                │
   │  /v2/...   development shelf (appId-native)                  │
   │                                                              │
   │  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
   │  │ Core            │  │ Plugin SPI      │  │ MCP surface  │ │
   │  │ Neo4j · Mongo · │  │ PluginManifest  │  │ /mcp/sse     │ │
   │  │ Postgres ·      │  │ FileStorage SPI │  │ (Claude /    │ │
   │  │ TimescaleDB ·   │  │ LlmProvider SPI │  │  agents)     │ │
   │  │ MinIO ·         │  │ Minter SPI      │  │              │ │
   │  │ HSDS (opt-in)   │  │ PayloadKind SPI │  │              │ │
   │  └─────────────────┘  └────────┬────────┘  └──────────────┘ │
   └────────────┬──────────────┬────┴──────────────┬─────────────┘
                │              │                   │
   ┌────────────▼──┐  ┌────────▼─────────┐  ┌──────▼──────────────┐
   │ Inbound       │  │ Plugins (queued) │  │ Outbound (publish)  │
   │               │  │                  │  │                     │
   │ DRG (OPC UA)  │  │ industrial       │  │ shepard-plugin-     │
   │ sTC           │  │ importer         │  │ unhide  →  HMC      │
   │ stc-config-   │  │ tables           │  │ minter-datacite     │
   │   helper      │  │ spatial          │  │   →  DOIs           │
   │ Airflow / DAGs│  │ matrix           │  │ Dataship  →         │
   │   (OpenLineage│  │ imagebundle      │  │   DBpedia Databus   │
   │   events)     │  │ file-s3          │  │ publish (designed)  │
   │ MLflow / MinIO│  │ aas              │  │   → OpenAIRE /      │
   │ git repos     │  │ hdf · video · git│  │     re3data /       │
   │ Confluence    │  │ dbpedia-ref      │  │     InvenioRDM      │
   │ instdlr (PID) │  │ minter-epic      │  │ wiki-writer  →      │
   │               │  │ airflow          │  │   Confluence        │
   │               │  │ invenio-publish  │  │                     │
   │               │  │ mlops            │  │                     │
   │               │  │ ai               │  │                     │
   │               │  │ mcp (shipped)    │  │                     │
   └───────────────┘  └──────────────────┘  └─────────────────────┘
```

The shape: a frozen upstream surface, a fork that extends through
`/v2/`, a plugin SPI seam that absorbs all new payload kinds and
integrations, and two flows around it — inbound from
industrial / pipeline / git / catalogue sources, outbound to
publication / federation registries. The MCP surface sits across the
top as the agentic-client entry point.

---

## 9. How the ecosystem composes (one MFFD trace)

A single MFFD process step traces through the ecosystem end-to-end:

1. **AFP cell** at MFZ Augsburg runs a tape-layup ply. The cell's
   PLC publishes `VC_PLYNUMBER` / `VC_TRACKNO` and the sensor channels
   declared in `timeseries.json` (R20 robot, TPS tape-placement head,
   MTLH material loader, location MFZ).
2. **DRG** subscribes to those OPC UA variables, auto-creates the
   Project / Experiment / Step hierarchy in Shepard as the run
   progresses, and writes the channel definitions as
   `TimeseriesReference` rows on the new DataObject.
3. **sTC** continuously streams the sensor channels into the same
   TimeseriesContainer; **stc-config-helper** generated the YAML
   that wired it up.
4. **Shepard captures the activity** as a PROV-O `:Activity` node
   (PROV1a), linking the DataObject to the operator's OIDC identity
   and to the cell's `:Agent` instrument PID resolved through
   **instdlr**.
5. **ODIX-shaped semantic constraints** (`:hasConstraint`,
   `:causesDefect`, once TPL1+3 ship) flag any out-of-band reading
   on the channels — `mffd:ConsolidationForce` below spec,
   `mffd:TCPTemperature` above spec.
6. **REBAR-shaped Airflow DAG** (via `shepard-plugin-mlops`) reads
   the DataObject as a DataFrame, trains a defect-prediction model,
   writes the trained model back as a FAIR4ML-shaped reference, and
   emits OpenLineage events that become Predecessor/Successor edges
   in the Shepard graph automatically.
7. **Snapshot + RO-Crate export** pins the entire trace as an
   immutable ZIP — reproducible by construction (V2d).
8. **`shepard-plugin-unhide`** advertises the Collection in the
   Helmholtz Knowledge Graph as `schema:Dataset` + `m4i:Dataset` with
   PROV-O inlined and the KIP-minted DataCite DOI as
   `schema:identifier`.
9. **`shepard-plugin-publish` → Dataship adapter** publishes a
   structured release to the DBpedia Databus with a SHA-256-pinned
   distribution manifest.
10. **`shepard-plugin-wiki-writer`** drafts the run-up note for the
    institute Confluence wiki; the human reviewer signs off via the
    F(AI)²R verification-ladder UI (TPL9d), promoting the AI-drafted
    claims from `verif:unverified` to `verif:human-confirmed`.

What the architecture buys is that every step happens against the
same identity graph, every artefact lands as a typed PROV-O activity,
and every external system the loop touches (DRG, sTC, Airflow,
Confluence, Databus, HMC) plugs in through a documented seam — not a
bespoke shim.

---

## 10. How to contribute / extend

Per the plugin-first rule in `CLAUDE.md`, contributions default to
plugins. The decision tree:

1. **New payload kind?** Plugin from day one. The PayloadKind SPI
   exists for exactly this. (HDF5, video, AAS submodels, tables —
   all plugins under the new shape, per ADR-0023.)
2. **New external integration?** Plugin shape. They have their own
   release cadence, dependency tree, and failure modes; isolating
   them from core is the structural fix. (Unhide, DataCite,
   Confluence, Matrix — all plugins.)
3. **New cross-cutting infrastructure?** In-tree interface + plugin
   implementations. (FileStorage SPI + GridFS / S3 plugins;
   LlmProvider SPI + OpenAI / Anthropic / SAIA / GWDG plugins;
   Minter SPI + local / DataCite / ePIC plugins.)
4. **Domain feature touching existing payload kinds?** Default
   in-tree, but prefer plugin if there's a clean seam.

Exceptions (in-tree by necessity): the auth perimeter
(`PermissionsService`, `JWTFilter`); identity primitives (`appId`,
`User`, `Collection`, `DataObject` core graph); the SPI registry
itself.

Plugin authoring guide: `aidocs/platform/47-dev-experience-and-plugin-system.md`.
Each plugin ships its own `plugins/<plugin-id>/docs/{reference,quickstart,install}.md`
trio — auto-discovered by the `/help` route once it lands.

To bring a new tool into the ecosystem documented above: open a PR
that adds a row to §2 (DLR-internal) or §5 (community) with the
repo URL, stack, status, and integration mechanism; cite this doc
in the same PR's `aidocs/34` row.

---

## 11. Open questions / not in scope

**Adjacent but not Shepard ecosystem** (sometimes confused for it):

- **Kadi4Mat** (KIT) — RDM platform; complementary rather than
  competitive per `project_competitive_position`. Different
  identity model, different payload kinds.
- **openBIS** (ETH Zürich) — RDM platform; sample-management
  focused.
- **SciCat** (PSI / ESS) — RDM platform; large-facility focused.
- **NOMAD** (FAIR-DI) — RDM platform; materials-science focused.

These are peers, not parts of Shepard's surface. The competitive
position vs. each is in `aidocs/agent-findings/ecosystem-advocate.md`.

**Inferred but not directly verifiable** (DLR-internal repos behind
authentication that this snapshot couldn't enumerate):

- The full list of DLR-internal Shepard deployments beyond
  `bt-au-cube1` / `bt-au-cube3` / `bt-au-rebar` and the named
  `shepard BT` / `shepard RY` pair.
- The status of any non-public contribution branches against
  upstream `dlr-shepard/shepard`.
- Whether the `data_reference_generator-v2-api-integration` branch
  has been merged upstream of the operator-facing DRG binary.

**Explicit non-goals for Shepard** (so they stay out of the
ecosystem diagram):

- Shepard as a PLM, an HPC scheduler, a code repository, an OLTP
  store, or a real-time control system. See `aidocs/42 §What
  shepard is not` for the principled list.
- Federation across Shepard instances at the database layer
  (`aidocs/16` X3, parked) — federation goes via Databus / Unhide /
  shared IRIs, not direct.

---

## Sources

- Upstream organisation: <https://gitlab.com/dlr-shepard>
- Fork repository: <https://github.com/noheton/shepard>
- Documentation site (GitHub Pages mirror): <https://noheton.github.io/shepard/>
- `aidocs/34-upstream-upgrade-path.md` — admin-facing change ledger
- `aidocs/42-vision.md` — researcher-facing live vision
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — contributor progress matrix
- `aidocs/integrations/67-unhide-publish-plugin.md` — HMC Unhide plugin
- `aidocs/integrations/72-invenio-publishing-plugin.md` — InvenioRDM publish plugin
- `aidocs/integrations/77-databus-moss-federation.md` — Databus / MOSS federation
- `aidocs/integrations/83-rebar-airflow-integration.md` — REBAR / Airflow / OpenLineage
- `aidocs/integrations/81-jupyterhub-integration.md` — JupyterHub integration
- `aidocs/integrations/82-confluence-import.md` — Confluence import
- `aidocs/integrations/52-aas-backend-integration.md` — AAS backend
- `aidocs/integrations/45-file-storage-spi.md` (FS series) — S3 + GridFS via FileStorage SPI
- `aidocs/integrations/35-hdf-hsds.md` (A5 series) — HDF5 via HSDS
- `aidocs/integrations/53-video-and-bundles.md` (VID1 / FR1 series)
- `aidocs/integrations/66-hmc-kip-integration.md` — HMC KIP minting
- `aidocs/integrations/38-git-references.md` (G1 series) — git artifact tracking
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI
- `aidocs/platform/30-mcp-plugin-design.md`, `aidocs/platform/86-ai-plugin-design.md`, `aidocs/platform/88-quarkus-mcp-server-migration.md` — MCP + AI surfaces
- `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md` — internal semantic repository
- `aidocs/semantics/65-admin-configurable-ontology-preseed.md` — pre-seeded ontologies
- `aidocs/semantics/95-shacl-templates-and-individuals.md` Parts 11–15 — plugin shapes, git ingestion, DLR ontology landscape, network organisation, F(AI)²R (TPL9)
- `aidocs/semantics/43-ai-opportunities.md` — AI plugin design
- `aidocs/agent-findings/dlr-ontology-catalog.md` — DLR ontology landscape audit
- `aidocs/agent-findings/ecosystem-tools.md` — DRG / Dataship / stc-config-helper / infusion-analysis / instdlr / ForInfPro deep dive
- `aidocs/agent-findings/ecosystem-advocate.md` — competitive landscape vs. Kadi4Mat / openBIS / SciCat / NOMAD
- `aidocs/agent-findings/easa-ai-regulatory-positioning.md` — regulatory framing (EASA + EU AI Act)
- F(AI)²R reference implementation: <https://github.com/noheton/f-ai-r>
- F(AI)²R originating paper ("Obscurity Is Dead"): <https://github.com/noheton/Obscurity-Is-Dead>
- DRG (DLR-internal): `gitlab.dlr.de/zlp-augsburg/data_reference_generator`
- shepard-dataship (DLR-internal): `gitlab.dlr.de/zlp-augsburg/inner-source/shepard-dataship`
- shepard-stc-config-helper (DLR-internal): `gitlab.dlr.de/zlp-augsburg/inner-source/shepard-stc-config-helper`
- rebar-infrastructure (DLR-internal): `gitlab.dlr.de/rebar/rebar-infrastructure`; docs at `rebar.pages.gitlab.dlr.de/rebarpages/`
- instdlr (INST.DLR): Helmholtz Cloud, DOI `10.5281/zenodo.15180781`
