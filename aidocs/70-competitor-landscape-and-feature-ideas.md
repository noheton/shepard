# aidocs/70 — Competitor landscape & feature ideas for shepard

**Date:** 2026-05-16  
**Scope:** Active RDM / ELN / data-workbench tools relevant to shepard's positioning  
**Not in scope:** Publication repositories (InvenioRDM, Zenodo, Dataverse, DSpace) — shepard is a data *workbench* for active work, not a publication endpoint.

---

## 1. Positioning anchor

shepard's niche is **active research data management** — the phase between raw instrument output and eventual archival/publication. It is closer to a structured file cabinet + provenance graph + semantic layer than to an ELN. The right comparison class is therefore:

- Tools researchers use during an experiment or analysis campaign
- Tools that organize, annotate, and relate heterogeneous payloads (files, time-series, spatial data, …)
- Tools that expose data programmatically for pipelines and instruments

Not the right comparison class: InvenioRDM, Zenodo, DataCite-native repos — those are the *destination* after the workbench phase is done.

---

## 2. Landscape summary

### 2.1 TwinStash (DLR Braunschweig / move technology GmbH)

**What it is:** DLR-internal digital-twin storage and application-service hub, originally built for flight test data (DLR fleet of 13 research aircraft). Now being commercialized for industrial asset management via move technology GmbH (partnership announced April 2026).

**Tech stack:** MongoDB, Python client + web GUI, Plotly for time-series visualization.

**Key capabilities:**
- Hierarchical navigation: project → flight → aircraft/asset
- Stores raw sensor streams, 3D scans/models, measurement configs, flight test cards, simulation outputs
- Rich trajectory visualization (2D + 3D)
- Up to 3,000 parameters per aircraft; makes data available shortly after landing
- OCR + table extraction from handwritten flight test cards
- AI-assisted automated flight test report generation (< 1 min initial draft)
- Integration with external data sources: METAR weather, ADS-B air traffic
- Explicit FAIR-principles alignment

**Relation to shepard:**
- Both are DLR products targeting active RDM
- TwinStash is domain-specific (aviation/asset telemetry); shepard is domain-agnostic
- TwinStash's AI-report and OCR automation are ahead of what shepard does today
- Potential: cross-pollination or plugin for aviation domain if DLR teams align

**Feature ideas from TwinStash:**
- Automated narrative report generation from structured payload data (AI + template)
- OCR ingestion of physical lab notes / handwritten forms as a payload kind
- External-data annotation: attach real-time context feeds (weather, ADS-B, METAR equivalent for other domains) as linked payloads on a collection or experiment
- Asset/instrument metadata model as a first-class entity (see §3 below)

---

### 2.2 RSpace (ResearchSpace Ltd, formerly RSpace ELN)

**What it is:** Open-source research orchestrator combining ELN, sample management, and FAIR workflow integration. Institutional adoption at Harvard, TU Delft, Max Delbrück Center.

**Key capabilities:**
- Hierarchical ELN (Projects → Notebooks → Experiments) with chemistry workflows
- Sample management with IGSN ID publication and mobile offline sync
- PID-in-workflow: ORCID, DataCite, RRID, IGSN, PIDINST, RAiD, ROR
- RO-Crate export for FAIR compliance
- 20+ research tool integrations
- Enterprise SSO, institutional access control
- Cross-storage orchestration: unified view across institutional storage silos

**Gaps vs shepard:**
- shepard has stronger semantic/ontology layer, pluggable payload SPI, provenance graph
- RSpace has richer sample/inventory management, PID embedding, institutional SSO

**Feature ideas from RSpace:**
- **Sample/Inventory entity** as a first-class payload-adjacent concept (distinct from `Collection` or `DataObject`; has IGSN, quantity, location, provenance chain)
- **PID stamping in workflows**: embed ORCID + DataCite DOI resolution inline in experiment provenance records
- **Offline-first sync** for mobile data collection (field samples, instrument readings while disconnected)
- **Cross-storage unified view**: allow a single `FileContainer` to federate objects from multiple backends (GridFS + S3 + future SeaweedFS) transparently

---

### 2.3 eLabFTW

**What it is:** Free, open-source (AGPLv3) ELN + lab inventory management system. Self-hosted, researcher-centric, explicitly anti-vendor-lock-in. ELN Consortium member.

**Key capabilities:**
- Experiment, protocol, and resource storage in a single system
- Digital signing and certified timestamping for legal/regulatory use
- Fine-grained per-entry ACL + team groups (multi-team single instance)
- Equipment booking/scheduling system built in
- `.eln` portable file format (ELN Consortium interoperability standard)
- WCAG v2.1 / RGAA v4 accessibility compliance
- 21+ language translations
- Bug bounty program; regular code audits
- No paywalled features

**Gaps vs shepard:**
- shepard has semantic graph, typed payload kinds, S3 migration, provenance tracking
- eLabFTW has timestamping/signing, booking system, `.eln` interoperability, stronger ACL granularity

**Feature ideas from eLabFTW:**
- **Certified timestamping / digital signing** on `DataObject` or `Experiment` entities (RFC 3161 TSA integration — legal/regulatory value for pharma/space/aviation)
- **Equipment booking system**: `Instrument` entity with calendar-based reservation, linked to the experiments that used it; natural provenance connection
- **`.eln` import/export** as an interoperability on-ramp/off-ramp (ELN Consortium format is gaining traction as the "PDF of lab notebooks")
- **Per-entry ACL granularity**: today shepard's permissions are at collection/data-object level; eLabFTW's model suggests value in per-payload-instance read/write grants
- **Team groups**: sub-group structures within a shepard collection (project team vs. external collaborators with view-only on a subset)

---

### 2.4 EPAM Indigo ELN v2.0

**What it is:** Free (GPL v3) open-source ELN for chemistry research. Proven EPAM product with regulatory compliance focus. Tech: MongoDB + Ketcher (structure editor) + Indigo engine + BingoDB (molecular search).

**Key capabilities:**
- Projects → Notebooks → Experiments → Components hierarchy
- Template-based experiment creation (expandable without code changes)
- Chemistry: molecular structure drawing (Ketcher), stoichiometry tables, reagent lookup, reaction search
- Compound/batch registration with unique IDs, yield, purity, hazards
- ACL at Project/Notebook/Experiment level
- Electronic signature integration (SAFE BioPharma standard)
- SDF import/export, PDF print of experiment records
- "My Reagent list" (bookmarked reagents from internal + external databases)

**Feature ideas from Indigo ELN:**
- **Template-based experiment components** that researchers can instantiate without developer involvement — this is the "no-code payload composition" idea (see also aidocs/39 templates design, aidocs/54 templates-as-first-class-entity)
- **Chemical structure as a payload kind** (Ketcher/RDKit rendering plugin; structure search via Indigo or RDKit) — natural shepard plugin candidate
- **Reagent / consumable registry** within a collection (quantity-tracked, hazard-annotated, linked to experiments that consumed them)
- **Compound/batch unique IDs** — analogous to shepard's `appId` but domain-specific; worth a `SampleReference` payload kind

---

### 2.5 OpenBIS (ETH Zürich)

**What it is:** Open-source LIMS + ELN + data management platform for materials science and systems biology. Long-established ETH product.

**Key capabilities:**
- Hierarchical space/project/experiment/sample/dataset model
- Sample/object type registry with per-type metadata schemas
- Dataset attachment with versioning
- Openbis Jupyter integration for computational experiments
- REST + JSON-RPC API
- Master data management (type registry editable by admins at runtime)
- "Dropbox" automated data ingestion from instruments via file-system watchers

**Feature ideas from OpenBIS:**
- **Instrument dropbox** pattern: file-system or S3-prefix watcher that auto-ingests new files from instruments into a designated collection, applying a template payload schema (big win for lab automation)
- **Master data registry** for payload types: admin-editable schema for custom `DataObject` subtypes without server restart (runtime extensibility beyond the plugin SPI)

---

### 2.6 Renku (EPFL / Swiss Data Science Center)

**What it is:** Reproducible and collaborative data science platform. Auto-generates a Knowledge Graph from project activity (git + data lineage).

**Key capabilities:**
- Git-native project structure with automatic lineage tracking
- Knowledge Graph derived from code + data activity (no manual annotation)
- Connects datasets, code versions, execution environments
- Integrates with GitLab, Jupyter, R

**Feature ideas from Renku:**
- **Automatic lineage from git activity** — when shepard's git integration (G1) commits code, auto-create provenance edges in the Neo4j graph linking code revision → data objects produced
- **Environment snapshot attachment**: pin the compute environment (Docker image, conda lockfile) alongside the data objects it produced

---

### 2.7 DMP tools (RDMO, ARGOS, DMPonline)

The German-community standard is **RDMO** (Research Data Management Organiser). Several NFDI consortia mandate RDMO plans.

**Feature idea:**
- **DMP linkage**: allow a `Collection` to reference an RDMO plan ID; surface DMP-declared storage locations and retention policies as metadata constraints on the collection (the "commit to RDMO plan" workflow)

---

### 2.8 inst.dlr — DLR instrument metadata registry

**URL:** <https://helmholtz.software/software/instdlr>

**What it is:** A DLR-internal PIDINST-compliant instrument metadata database. Designed to give researchers a persistent, machine-readable record of every scientific instrument and its configuration history. Implemented in Python (FastAPI + MongoDB); deployed as a container; Apache 2.0 licensed.

**Key capabilities:**
- Implements the **PIDINST schema** (the W3C community group's standard for persistent identifiers for instruments)
- Machine-to-machine CRUD via REST API (JSON / CSV / Excel); API key + Helmholtz AAI auth
- Schema validation: every instrument record is validated against the PIDINST schema before storage
- Human-readable search for instrument records
- Tracks **configuration changes** ("paper trail") so data can be tied to the exact instrument state at measurement time

**Relation to shepard's EQ1 feature idea:**  
Rather than building a standalone instrument entity from scratch, shepard should treat inst.dlr as the **source of truth** for instrument metadata and federate against it:

- A shepard `Instrument` entity stores the inst.dlr PID (`pidinst:<id>`) and caches a snapshot of the PIDINST record
- Provenance edge: every `DataObject` produced by a measurement gets `wasGeneratedBy: Instrument(pidinst:<id>)` in the Neo4j graph
- The `shepard-plugin-instdlr` plugin syncs the local cache when inst.dlr records update
- Booking / scheduling remains in shepard (inst.dlr has no booking concept); PIDINST metadata comes from inst.dlr

**Feature ideas from inst.dlr:**
- **PIDINST federation**: resolve `pidinst:` URIs to inst.dlr records and embed them as a linked payload on experiments
- **Configuration-change provenance**: when an instrument's config changes in inst.dlr, auto-create a provenance edge in shepard's graph linking the old config → new config → measurements produced under each
- **Instrument search / filter**: surface inst.dlr's human-readable search within shepard's "pick instrument" UI when creating a measurement

---

### 2.9 Helmholtz software ecosystem (helmholtz.software/projects)

Other Helmholtz-stack projects worth tracking for integration:

| Project | Relevance to shepard |
|---|---|
| **Base4NFDI** | Basic interoperable services for NFDI — potential common auth / PID / storage layer |
| **DAPHNE4NFDI** | Photon & neutron science data; FAIR data standards; potential payload-kind overlap |
| **Tango Controls** | SCADA / distributed control for scientific equipment; natural "instrument dropbox" signal source (IL1) |
| **HIFIS** | Helmholtz federated IT services; identity / storage federation layer |

---

## 3. Feature matrix — shepard vs. key competitors

Rows = feature areas. `●` = present/strong, `◑` = partial/limited, `○` = absent.  
**upstream 5.2.0** = `gitlab.com/dlr-shepard/shepard` at the 5.2.0 release (the fork baseline).  
**this fork (now)** = `noheton/shepard main` as of 2026-05-16.  
**shepard 6.0** = planned state after Tier 1 + Tier 2 features from §4 land (see §4 for detail and aidoc IDs).

| Feature area | upstream 5.2.0 | this fork (now) | shepard 6.0 | RSpace | eLabFTW | Indigo ELN | TwinStash | OpenBIS |
|---|---|---|---|---|---|---|---|---|
| **Structured payload types** (file, time-series, spatial, HDF5, git, …) | ◑ (file + timeseries + spatial; no HDF5/git) | ● | ● | ◑ | ○ | ○ | ◑ | ◑ |
| **Plugin / SPI extensibility** | ○ | ● | ● | ○ | ○ | ◑ | ○ | ◑ |
| **Semantic / ontology layer** | ◑ (n10s present; no preseed, no admin UI) | ● | ● | ○ | ○ | ○ | ○ | ○ |
| **Provenance / lineage graph** | ◑ (PROV-O capture, no batch permissions) | ● | ● | ◑ | ○ | ○ | ○ | ◑ |
| **S3 / object-storage backend** | ○ (GridFS only) | ● | ● | ◑ | ○ | ○ | ○ | ◑ |
| **REST + programmatic API** | ● | ● | ● | ● | ● | ◑ | ● | ● |
| **Git integration** | ○ | ◑ | ● | ○ | ○ | ○ | ○ | ◑ |
| **RO-Crate export** | ◑ (synchronous, proxied) | ◑ (+ presigned S3 delivery FS1g) | ● | ● | ○ | ○ | ○ | ○ |
| **PID embedding** (ORCID, DOI, IGSN, …) | ◑ | ◑ | ● | ● | ○ | ○ | ○ | ◑ |
| **ELN / experiment notebook UX** | ○ | ○ | ◑ | ● | ● | ● | ◑ | ◑ |
| **Sample / inventory management** | ○ | ○ | ◑ | ● | ● | ◑ | ○ | ● |
| **Equipment booking** | ○ | ○ | ◑ | ○ | ● | ○ | ○ | ○ |
| **Digital signing / timestamping** | ○ | ○ | ◑ | ○ | ● | ◑ | ○ | ○ |
| **Chemical structure search** | ○ | ○ | ○ | ○ | ○ | ● | ○ | ○ |
| **Automated report generation** | ○ | ○ | ◑ | ○ | ○ | ○ | ● | ○ |
| **OCR / paper form ingestion** | ○ | ○ | ○ | ○ | ○ | ○ | ● | ○ |
| **Instrument dropbox / auto-ingest** | ○ | ○ | ● | ○ | ○ | ○ | ◑ | ● |
| **`.eln` interoperability format** | ○ | ○ | ● | ○ | ● | ○ | ○ | ○ |
| **Mobile / offline-first** | ○ | ○ | ○ | ◑ | ○ | ○ | ○ | ○ |
| **Multi-team instance** | ● | ◑ | ◑ | ● | ● | ◑ | ◑ | ● |
| **Admin-configurable at runtime** | ○ (build-time toggles only) | ● | ● | ◑ | ◑ | ○ | ○ | ◑ |
| **NFDI / Helmholtz integration** | ○ | ● | ● | ○ | ○ | ○ | ◑ | ○ |
| **DMP / RDMO linkage** | ○ | ○ | ◑ | ○ | ○ | ○ | ○ | ○ |
| **PIDINST / instrument registry** | ○ | ○ | ● | ○ | ○ | ○ | ○ | ○ |

---

## 4. Prioritised feature ideas for shepard

Sorted by impact × fit with shepard's data-workbench identity. Each maps to a plausible aidocs design or plugin slot.

### Tier 1 — High fit, high value

**4.1 Instrument dropbox / auto-ingest  (new aidoc: IL1)**
A file-system or S3-prefix watcher that auto-ingests new files from instruments into a pre-configured collection. Operator defines: source path/prefix, target collection, payload template. Files land with provenance edge `wasGeneratedBy: InstrumentDropbox`. Natural plugin (`shepard-plugin-dropbox`). This removes the biggest friction point for lab automation: manual upload after each instrument run.

**4.2 `.eln` import/export  (extend RO-Crate plugin or new plugin)**
The ELN Consortium `.eln` format (a ZIP with a JSON-LD manifest + attached files) is becoming the interchange standard between ELN systems. Implementing `.eln` export (alongside RO-Crate) turns shepard into a FAIR on-ramp for labs already using eLabFTW / RSpace. Import gives a migration path *into* shepard.

**4.3 Sample/Inventory entity  (new aidoc: SI1)**
A `Sample` payload kind (or first-class entity adjacent to `DataObject`) with: IGSN ID, quantity (amount + unit), location (freezer/shelf hierarchy), hazard annotations, provenance chain, and a link to experiments that used/produced it. Enables labs that currently track samples in spreadsheets to move to shepard. Plugin-first candidate (`shepard-plugin-sample-inventory`).

**4.4 Equipment / instrument entity + booking + inst.dlr integration  (new aidoc: EQ1)**
Rather than building a standalone instrument registry from scratch, shepard should **federate with inst.dlr** (`helmholtz.software/software/instdlr`) as the PIDINST-compliant source of truth for DLR instrument metadata. The recommended design:

- **`shepard-plugin-instdlr`** (plugin): pulls instrument records from inst.dlr REST API by PIDINST PID; caches a local snapshot in a `PluginInstrument` Neo4j entity.
- **Provenance edge**: every `DataObject` produced by a measurement gets `wasGeneratedBy: Instrument(pidinst:<id>)` automatically via the plugin — instrument lineage is PIDINST-native.
- **Configuration-change tracking**: when inst.dlr records a calibration or config change, the plugin creates a provenance edge linking old config → new config → measurements produced under each.
- **Booking** (stays in shepard, inst.dlr has no booking concept): calendar-based reservation against the local `PluginInstrument` entity; reservation is linked to the experiment planning entity (aidocs/50).
- **Tango Controls signal**: Tango Controls (Helmholtz SCADA system) is a natural signal source for the instrument dropbox (IL1) — instruments emit measurement files via Tango; the dropbox plugin picks them up and stamps the inst.dlr PID from the booking record.

For labs not running inst.dlr, the plugin degrades gracefully: instruments can be entered manually as local `PluginInstrument` records with no PIDINST PID, and the booking / provenance flows work identically.

### Tier 2 — Good fit, medium effort

**4.5 Digital signing / certified timestamping  (extend Activity/Provenance)**
RFC 3161 TSA integration on `DataObject` or `Experiment` entities. Produces a timestamped signature stored as a linked payload. Relevant for regulatory contexts (pharma, aviation certification, space). Natural extension of the PROV1a provenance layer.

**4.6 Automated narrative report generation  (AI plugin, new aidoc: AR1)**
Given a collection + its provenance graph + attached payloads, generate a human-readable experiment summary report (Markdown/PDF). Uses the MCP/LLM layer already explored in aidocs/43 and aidocs/56. TwinStash does this for flight tests; the same pattern applies to any domain.

**4.7 PID-in-workflow stamping**
When a researcher exports or publishes a collection, auto-resolve and embed their ORCID (from user profile), mint or link a DataCite DOI, and stamp any sample with its IGSN. This completes the provenance chain from instrument → workbench → repository and is a natural capstone for the Unhide/publish plugin (aidocs/67).

**4.8 DMP linkage (RDMO integration)**
Allow a `Collection` to declare an RDMO plan ID. Surface DMP-declared retention, storage location, and sharing constraints as metadata on the collection. Relevant for NFDI consortia and German institutional compliance.

### Tier 3 — Interesting, lower priority

**4.9 Chemical structure payload kind  (plugin)**
A `ChemicalStructure` payload kind backed by RDKit or Ketcher rendering + Indigo/PostgreSQL for substructure search. Natural plugin. High value for chemistry/pharma labs; no change to core.

**4.10 External context annotation feeds**
Attach real-time or historical external data (weather API, METAR, ADS-B, stock concentration logs) as a linked `TimeseriesReference` payload on a collection or experiment. TwinStash does this for aviation; generalised as a `ContextFeed` plugin.

**4.11 Offline-first mobile ingest**
PWA or mobile client that buffers field measurements, photos, and GPS coordinates offline and syncs to shepard when back online. High value for field science (ecology, geology, remote sensing) — a segment RSpace targets.

---

## 5. Competitive positioning summary

shepard's structural advantages that no competitor matches simultaneously:

1. **Typed payload SPI** — new payload kinds drop in as JARs without touching core
2. **Semantic layer** — ontology-aware relationships, SPARQL-accessible via neosemantics
3. **Provenance graph** — PROV-O-aligned lineage is first-class, not bolted on
4. **S3-native** — presigned upload/download, migration, FAIR storage from day one
5. **Admin-configurable at runtime** — feature toggles, ontology preseed, S3 config without restart
6. **NFDI / Helmholtz-aware** — Unhide publish integration, HMC-KIP alignment (aidocs/66, 67)

shepard's gaps to address (in priority order from §4): instrument dropbox → `.eln` interop → sample/inventory → equipment booking (via inst.dlr federation) → timestamping.

---

## 6. See also

- `aidocs/42-vision.md` — researcher-facing vision (update when features land)
- `aidocs/43-ai-opportunities.md` — AI layer design
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — per-feature status tracker
- `aidocs/47-dev-experience-and-plugin-system.md` — plugin SPI reference
- `aidocs/50-experiment-orchestration.md` — experiment entity design
- `aidocs/67-unhide-publish-plugin.md` — publication integration
- `https://helmholtz.software/software/instdlr` — inst.dlr PIDINST instrument registry (EQ1 integration target)
- `https://helmholtz.software/projects` — Helmholtz software ecosystem overview
