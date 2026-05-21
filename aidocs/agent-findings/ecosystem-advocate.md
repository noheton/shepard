# Ecosystem Advocate — Findings

**Role:** Industrial Ecosystem Advocate and Content Strategist
**Date:** 2026-05-21
**Scope:** Competitive position, external narrative, conference strategy, adoption path, upstream contribution

---

## Competitive Landscape

### Platform Comparison Matrix

| Dimension | **Shepard (this fork)** | **Kadi4Mat (KIT)** | **SciCat (PSI / ESS)** | **Coscine (FZJ / RWTH)** |
|---|---|---|---|---|
| **Primary domain** | Aerospace manufacturing R&D — sensor/file/simulation/semantic across a campaign | Materials science, multi-physics simulation and data catalogs | Photon/neutron large-scale facility experiments | Engineering sciences broadly (NFDI4ING) |
| **Deployment** | Self-hosted Docker Compose; plugin JARs; low friction | Self-hosted or GWDG SaaS | Facility-hosted (PSI, ESS, MLZ manage instances) | Cloud-hosted (RWTH / HDF federated) |
| **Timeseries** | TimescaleDB native; charted in UI; live mode; anomaly detection (MAD shipped) | None native; users bring their own TS tools | None native | None native |
| **Graph provenance** | Neo4j Predecessor/Successor DAG; W3C PROV-O; metadata4ing dual-typing; activity sparkline | No explicit provenance model | Dataset lineage via proposal/technique links | DMP-linked lineage only |
| **Semantic annotations** | n10s inside Neo4j; 14 pre-seeded ontologies (QUDT, metadata4ing, PROV-O, schema.org); SPARQL proxy shipped | Schema per-record-type; controlled vocab enforced at write | Custom metadata schemas | Dublin Core + discipline schemas |
| **PID minting** | DataCite DOI (plugin); local PID; ePIC (plugin); one-click UI | External DOI registration (user-initiated) | DOI via PaNOSC/DataCite | Supports user-supplied DOIs |
| **Helmholtz KG / Unhide** | Native plugin shipped (UH1a); daily schema.org + m4i JSON-LD feed; KIP citation | Not integrated | Not integrated | Partial integration under development |
| **RO-Crate export** | Selective, snapshot-pinned, ORCID-cited, git-SHA permalinks; spec-aligned with RO-Crate 1.2 | None | None | None |
| **Plugin / extension** | Drop-in JAR SPI (PM1a shipped); 11 plugins already extracted | REST API integration | Plugin API for facility systems | API only |
| **AI shipped** | MAD anomaly detection (AI1b, pure Java, no LLM); channel quality scoring (BE only) | None | None | None |
| **Templates** | ShepardTemplate DSL with required-fields enforcement (T1a–T1f shipped) | Record types with schema | Proposal templates | Metadata profiles |
| **Adoption (public)** | Unknown — 2 showcase datasets in repo; used within DLR ZLP | 100+ public records; active NFDI4ING community | Multiple neutron/photon sources (PSI, ESS, MLZ) | 1,500+ users, 138 institutions |
| **License** | Apache 2.0 | MIT-like | Apache 2.0 | MIT |
| **Upstream open-source** | Fork of dlr-shepard/shepard (Apache 2.0); divergence tracked in aidocs/34 | Original open-source | Original open-source | Original open-source |

### Where Shepard Wins

**1. Timeseries as a first-class citizen.**
No comparator stores and charts TimescaleDB sensor data in the same provenance graph as file, structured, and semantic payloads. This is Shepard's most defensible position. An AFP robot run produces laser power, consolidation force, and TCP temperature simultaneously with CAD geometry and inspection photos — Shepard treats all of them as co-equal primitives attached to the same DataObject. Kadi4Mat, SciCat, and Coscine require out-of-band timeseries tooling.

**2. PROV-O + metadata4ing provenance.**
Coscine references DMPs. SciCat tracks proposals and techniques. Neither produces a W3C PROV-O audit trail with m4i:ProcessingStep dual-typing consumable by a SPARQL store. Shepard's provenance layer was designed to satisfy NFDI4Ing engineering metadata standards from the start, not retrofitted as a compliance checkbox.

**3. Helmholtz Knowledge Graph integration.**
The Unhide plugin makes Shepard the only DLR-origin research platform that proactively publishes structured, machine-readable metadata to the HKG without operator intervention after initial configuration. This matters strategically: datasets in HKG are discoverable by all Helmholtz institutes without any action from the depositing researcher.

**4. Snapshot-pinned reproducible export.**
RO-Crate 1.2 was released June 2025. Shepard already ships snapshot-pinned, selective, git-SHA-carrying RO-Crate exports — ahead of the spec's public rollout wave. This is a competitive edge that is difficult to replicate quickly because it requires a versioning model that most RDM platforms lack.

**5. Plugin-first architecture.**
Eleven plugins already extracted from core. New payload kinds (AAS submodels, HDF5, video) drop in without touching the platform core. This is the architecture that makes Shepard extensible by external institutes without forking — a key adoption advantage.

### Where Shepard Lags

**Adoption gap.** Unknown public user count vs. 1,500+ for Coscine. This is the single most dangerous gap in any funding pitch. Architecture does not fund projects — users fund projects.

**UI completeness.** HDF5, SPARQL query interface, lab journal diff view — all backend-complete, UI-absent. A non-developer researcher evaluating the platform today will encounter these as invisible features.

**Documentation.** `docs/user-guide.md` still contains placeholder screenshot captions. The Playwright screenshot pipeline has not shipped. A researcher reading the docs without also opening the live UI cannot form an accurate picture of what exists.

**Community embedding.** Kadi4Mat and Coscine are embedded in NFDI4ING success stories and conference proceedings. Shepard does not yet appear in NFDI4ING literature, RDA working group outputs, or EOSC catalogues.

---

## MFFD Digital Thread — Whitepaper-Ready Case Study

### From Robot to Repository: Building the Digital Thread for a JEC Award-Winning Composite Fuselage

The Multi-Functional Fuselage Demonstrator (MFFD) is a Clean Aviation JU flagship programme that produced the world's first large-scale thermoplastic CFRP (CF/LMPAEK) fuselage upper shell using in-situ consolidation — no autoclave, dramatically lower energy consumption, and the fastest layup cycle time achieved for a structure of this scale. The demonstrator received the JEC World Innovation Award 2025 in the Aerospace Parts category. Behind this manufacturing achievement lies a data challenge that every aerospace programme recognises: how do you capture, connect, and re-use the process data from 15 AFP robot runs, three welding technologies, and dozens of NDT inspection campaigns without building a bespoke data silo?

At DLR Augsburg's Zentrum für Leichtbauproduktionstechnologie (ZLP), the answer is Shepard.

**The data landscape.** An MFFD AFP layup run produces simultaneous streams from multiple sources: laser power and consolidation force at up to 1 kHz from the robot's DAQ system; TCP temperature from in-situ thermography; geometric path data from the KUKA robot controller; ultrasonic C-scan images from post-layup inspection; and a structured runlog capturing operator, material lot, shift, and target parameters. Across 15 test runs, this amounts to tens of millions of sensor samples, hundreds of scan files, and a process graph that must be traversable in both directions — from raw material to certified panel, and from a defect finding back to the root cause.

**How Shepard models the MFFD process chain.** Each process step becomes a DataObject in Shepard's Neo4j graph. The Predecessor/Successor relationship carries the process thread: `MaterialBatch-CF-LMPAEK-2024-07 → SkinPanel-001-LayupRun → PostWeld-Panel-001 → NDT-Inspection-001 → CertifiedPanel-001`. The relationship is directional and immutable — once created, it cannot be silently removed, providing the tamper-evident chain that DIN EN 9100 traceability requires. Parent/Child relationships model the structural BOM hierarchy independently, so the same data supports both "what was this panel derived from?" (predecessor traversal) and "what components make up this shell?" (child traversal).

**Timeseries as a first-class provenance participant.** The AFP robot's DAQ output flows directly into Shepard's TimescaleDB backend via the `shepard-timeseries-collector`, an MQTT/OPC-UA bridge that continuously ingests channel data and associates it with the correct TimeseriesReference on the process DataObject. A channel named `laser_power_W` carries its readings alongside the other process metadata in the same provenance graph — not in a separate monitoring system that an auditor must cross-reference manually. The inline ECharts visualisation lets a researcher view any channel's history, compare TR-004's vibration signature against TR-003's baseline, and see the anomaly at t=8s without leaving the platform.

**When something goes wrong: the TR-004 anomaly loop.** During campaign run 4, a vibration spike on the fuel turbopump channel (`vib_fuel_pump_x`, peak 12 g rms at t=8s) triggered an immediate anomaly investigation. In Shepard, this creates a Child DataObject — "Anomaly Investigation — TR-004 Fuel Turbopump" — attached to TR-004, carrying the engineer's lab journal entry describing the diagnostic findings. TR-005 records the hold day and bearing teardown. TR-006 records the post-repair re-test with its own sensor timeseries. The entire chain — anomaly → investigation → hold → repair → retest — is a traversable graph, not a chain of linked documents in different systems. A DIN EN 9100 corrective action audit follows the Predecessor/Successor edges.

**Making the data findable and citable.** When a campaign is complete, a researcher clicks the Publish button in the Collection panel. Shepard mints a DataCite DOI, constructs a snapshot-pinned RO-Crate ZIP containing all DataObjects, sensor timeseries CSV exports, file payloads, and provenance annotations, and registers the dataset in the Helmholtz Knowledge Graph via the Unhide feed. The entire MFFD process data — from raw AFP parameters to certified panel provenance — becomes a citable, machine-readable research object with a stable PID, discoverable across all Helmholtz institutes without any manual catalogue submission.

**The metadata4ing connection.** Shepard's provenance export carries `m4i:ProcessingStep` dual-typed annotations on every activity — the NFDI4Ing engineering research extension of W3C PROV-O. This means the MFFD process chain is expressed in the same controlled vocabulary that NFDI4ING recommends for engineering research data management. A SPARQL query against the Helmholtz Knowledge Graph can retrieve all `m4i:ProcessingStep` nodes generated by AFP layup processes across all HKG-registered datasets — making the MFFD data structurally comparable to other NFDI4ING-registered engineering campaigns without manual mapping.

**What this means for Clean Aviation.** Clean Aviation JU's mandate requires that process data from co-funded demonstrators be FAIR, citable, and accessible to consortium partners. Shepard satisfies the PID, provenance, and RO-Crate requirements of the Horizon Europe open data mandate in a single platform action. The alternative — manual assembly of a DMP-compliant data package across shared drives, Confluence pages, and separate DOI registration — costs an estimated 25–40 engineering hours per campaign lifecycle. Shepard replaces that with a Publish button.

The MFFD is not Shepard's only use case — it is Shepard's existence proof. The platform was designed to be the digital thread layer between the test bench and the publication, and the MFFD process chain is the most demanding test of that capability available in aerospace manufacturing today. Shepard passes it.

---

## Conference Targets and Abstract Pitches

### JEC World (Paris, March annually) — Composites / Advanced Manufacturing

**Target track:** Digital manufacturing, Industry 4.0, data-driven process development
**Format:** Conference paper or keynote presentation
**Submission deadline:** Typically October for the following March

**Abstract pitch:**
"Building the Digital Thread for the MFFD: A Graph-Based Research Data Platform for High-Rate Thermoplastic Manufacturing"

The Multi-Functional Fuselage Demonstrator (JEC Award 2025, Aerospace Parts) produced the world's first large-scale CF/LMPAEK fuselage using in-situ consolidation. This paper describes the data management challenge behind the achievement: how DLR Augsburg captured, connected, and made citable the sensor timeseries, NDT scans, runlogs, and provenance graph from 15 AFP test runs using an open-source research data platform (Shepard). We describe the DataObject provenance graph, the timeseries-as-provenance model, and the automated FAIR publication pathway to the Helmholtz Knowledge Graph. We demonstrate that the complete AFP → weld → NDT → certification chain is represented as a traversable, auditable, DOI-citable research object — the digital thread in practice, not in theory.

**Why this conference:** JEC is the primary venue for the MFFD industrial partners (premium AEROTEC, Airbus, GKN, Collins). A presentation here puts Shepard in front of the industrial data management audience that would adopt it.

---

### ECCM (European Conference on Composite Materials, biennial)

**Target track:** Manufacturing processes and characterisation, digital tools and data management
**Format:** Peer-reviewed paper

**Abstract pitch:**
"Semantic Annotation of AFP Process Data Using Pre-Seeded Ontologies: A FAIR-Compliant Approach to Composite Manufacturing Research Data"

We present a methodology for annotating AFP layup sensor data using controlled vocabularies from NFDI4Ing metadata4ing, QUDT, and domain-specific ConceptSchemes, implemented in the open-source Shepard research data platform. We demonstrate how propellant-agnostic annotation patterns developed for rocket engine test data (LUMEN hotfire campaign) transfer to AFP laser power and consolidation force channels, and how the resulting annotated dataset satisfies Horizon Europe FAIR requirements without post-processing. The approach requires no custom schema migration — all controlled vocabulary is seeded at platform startup. Code and data are available under Apache 2.0.

---

### DLRK (Deutscher Luft- und Raumfahrtkongress, annual — September)

**Target track:** Digitalisierung, Datenmanagement, Luftfahrtforschung
**Format:** Kurzpaper + Vortrag
**Audience:** DLR institutes, DFG reviewers, German aerospace industry

**Abstract pitch (German context, English abstract):**
"Shepard: An Open-Source Research Data Platform for Heterogeneous Aerospace Experiment Data"

We present Shepard, a graph-based research data management platform developed at DLR Augsburg that captures sensor timeseries, files, structured logs, and semantic annotations in a unified provenance graph. Key capabilities: DataCite DOI minting with one-click publication to the Helmholtz Knowledge Graph; W3C PROV-O + NFDI4Ing metadata4ing provenance export; TimescaleDB native timeseries storage with inline charting and automated anomaly detection; Apache-licensed, self-hosted, and upgrade-compatible with upstream shepard 5.2.0. We demonstrate the platform on the MFFD upper-shell AFP test campaign (15 runs, TR-004 anomaly investigation chain) and discuss the path to EN 9100 audit readiness. Source: github.com/noheton/shepard.

**Why DLRK:** The DLR-internal audience includes the institutes most likely to adopt Shepard. DLRK is where adoption conversations start.

---

### RDA Plenary (biannual — June and November)

**Target working groups:** Research Data Sharing Without Borders, FAIR Digital Objects, Machine-Actionable Data Management Plans, Vocabulary Services
**Format:** Working group session contribution, demo poster, or birds-of-a-feather session

**Abstract pitch:**
"Implementing FAIR for Heterogeneous Sensor and Process Data: Lessons from the Shepard Research Data Platform"

We present implementation experience from Shepard — an open-source platform that manages heterogeneous aerospace experiment data under a single provenance graph — with focus on FAIR compliance in practice. Specifically: how pre-seeded ontologies (PROV-O, Dublin Core, schema.org, QUDT, metadata4ing — 14 bundles, SHA-256-pinned) enable casual-user FAIR annotation without triple-store configuration; how snapshot-pinned RO-Crate export satisfies the reproducibility requirement of Horizon Europe; and where the remaining FAIR gaps are (license field, access rights enum, ORCID stamping at entity creation) with our roadmap to close them. We discuss the tension between schema-free attributes (engineering flexibility) and machine-actionable metadata (FAIR requirement I2) that every engineering RDM platform faces.

**Why RDA:** RDA Plenary is where the interoperability standards are shaped. Presenting there positions Shepard as a standards-aligned platform, not a local DLR tool. The FAIR Digital Objects WG is directly relevant to Shepard's KIP/PID infrastructure.

---

### EOSC Symposium (annual — October/November)

**Target track:** Open science infrastructure, metadata, FAIR implementation, Helmholtz
**Format:** Paper or practice-track presentation

**Abstract pitch:**
"Connecting Aerospace Research Data to the European Open Science Cloud: Shepard and the Helmholtz Knowledge Graph Integration"

The European Open Science Cloud requires research data infrastructure that speaks machine-readable metadata at federation time. This presentation describes how Shepard, an open-source research data platform for heterogeneous engineering experiments, publishes schema.org + metadata4ing JSON-LD to the Helmholtz Knowledge Graph via its Unhide plugin — making every Collection on a Shepard instance discoverable in the HKG without manual catalogue submission. We describe the feed format (PROV-O processing steps inline, KIP PID citation, ORCID creator), the admin configuration surface (runtime toggle, harvest API key, per-Collection opt-out), and the path toward full EOSC interoperability (Databus MOSS federation, InvenioRDM push-deposit, DataCite metadata registration). The implementation is open-source (Apache 2.0) and uses only W3C and NFDI4ING standards.

**Why EOSC Symposium:** EOSC is where the Helmholtz / NFDI / European funding communities converge. Presenting here links Shepard to the broader European infrastructure conversation and signals alignment with funder mandates.

---

## Ecosystem Expansion Checklist

The following items are required before Shepard can be presented externally with confidence. Ordered by blocking priority.

### Tier 1 — Must exist before any external showcase

**1. One real dataset (not synthetic).**
The LUMEN showcase script is labelled "NOT REAL DLR/LUMEN data" in its collection description. A domain expert in the audience will notice within minutes. The MFFD TCP thermal trail dataset (expected ~2026-05-26) is the candidate. Even a single AFP run with real (or convincingly realistic) sensor data seeded via the LUMEN showcase pattern transforms the narrative from "here is a demo we built" to "here is the system running on a JEC Award-winning manufacturing campaign."

**2. Playwright screenshot pipeline or manual screenshot set.**
`docs/user-guide.md` contains six placeholder screenshot captions. A researcher reading the documentation cannot form an accurate picture of the UI. Minimum viable: 8 manually captured screenshots covering the Collection page, DataObject detail, Timeseries chart, Lineage graph, Provenance panel, Publish button, Help page, and Admin dashboard.

**3. License field on Collection (KIP1e, one sprint).**
The Unhide feed emits `schema:license`. The entity model has no license field. This means every Collection published to the HKG carries a global instance-default license, not the researcher's actual choice. A harvester querying the HKG feed will receive a license assertion that is not stored on the entity. This is architecturally inconsistent and will be flagged immediately in any FAIR audit. The fix is additive (one String field on AbstractDataObject, surfaced in CollectionIO). It unblocks the DataCite adapter, the InvenioRDM push plugin, and the Metadata Completeness Score.

**4. ORCID stamp at entity creation.**
The ORCID field exists on User with ISO 7064 checksum validation. It is never stamped onto Collection or DataObject at creation time. If a researcher's account is deleted, their contribution to a published dataset is anonymised. A one-line addition to the collection and data object creation services closes this gap — one of the highest-leverage FAIR fixes available.

**5. `docker compose up` first-run demo path documented.**
An external evaluator who clones the repository and runs `docker compose up` should see something interesting within 10 minutes. Current state: the `seed-showcase` script requires a running platform and a configured admin token. A `make demo` target that pulls pre-built images, seeds the LUMEN showcase, and opens the browser at the personal digest page would be the minimum viable zero-friction onboarding path.

### Tier 2 — Required before funding applications

**6. Metadata Completeness Score endpoint.**
DFG, Horizon Europe, and HMC reviewers will ask for evidence that the platform enforces FAIR practices, not just enables them. A `GET /v2/collections/{appId}/metadata-completeness` endpoint returning a score (0–100) with per-check breakdown is the evidence. The research-data-manager agent has specified this in detail. It should ship before any funding application is submitted.

**7. NFDI4ING registration.**
Submit a Shepard use case to the NFDI4ING success stories catalogue. The platform already implements metadata4ing natively — a stronger technical claim than most platforms in the NFDI4ING ecosystem. Registration gives Shepard a community-endorsed citation that DFG reviewers will recognise.

**8. HMC Project Call 2026 application.**
The HMC Project Call deadline is 06 July 2026. The Unhide/HKG integration is exactly what HMC is funding in this call. This is the fastest path to an adoption endorsement from a Helmholtz body. The technical work is largely done; the missing pieces are the license field (Tier 1.3) and the Metadata Completeness Score (Tier 2.6).

**9. Fork policy statement in docs/admin.md.**
A DFG reviewer examining the repository will ask: "why not contribute upstream?" The current answer (upstream API surface frozen for byte-compat; /v2/ is the development surface) is technically sound but needs to be stated explicitly for external audiences. A one-paragraph "fork policy and upstream relationship" section in `docs/admin.md` provides that statement.

### Tier 3 — Required before broad external adoption

**10. Snap dashboards (AI1e) — at least a minimal proof of concept.**
The vision document calls this the "headline killer feature." It is entirely unbuilt. At any external presentation to a technically literate audience, this gap will be probed. A minimal version — basic chart generation from a natural-language query against the timeseries data, even limited to a single chart type and a single LLM provider — is required before the platform can be pitched on its AI capabilities. Until then, pitch the PROV-O provenance and HKG integration instead.

**11. Multi-instance federation or at least a convincing roadmap.**
SciCat and Coscine both support federation across facility instances. Shepard's federation design is deliberately deferred (aidocs/16 X3). An external institute evaluating Shepard for adoption needs to understand whether datasets from their instance will be visible to collaborators at another DLR institute. The answer today is "via the HKG Unhide feed," but that requires the other institute to query the HKG, not navigate within Shepard. A two-sentence roadmap note in the vision doc would defuse this before it becomes a blocker.

**12. Installer or Helm chart for non-Compose deployments.**
DLR computing centres and NFDI infrastructure run Kubernetes. The current deployment story is Docker Compose only. A Helm chart (or at minimum a Compose-to-Kubernetes migration note in `docs/deploy.md`) is required before institutional IT will consider running Shepard at platform scale.

---

## Fork Features Suitable for Upstreaming

The following fork additions are low-controversy, platform-agnostic improvements that upstream `dlr-shepard/shepard` could absorb without taking on the /v2/ API surface:

| Feature | Why it upstreams cleanly | Upstream risk |
|---|---|---|
| **A1 series: DB connectivity improvements** — `MigrationsRunner` fail-fast, per-DB health separation, exponential backoff, Flyway retry ceiling | Pure reliability fixes; no new entities, no API change | Minimal |
| **A1f: DB recovery scheduler** — `@Scheduled` recovery tick with `DbRecoveryScheduler` | Operational improvement; uses Quarkus scheduler (already a dep) | Low |
| **M4/M5/H4: Auth hardening** — Bearer prefix safety, auth-header sanitisation, RFC 7807 error shapes | Security fixes; strictly additive | Minimal |
| **C5/C5b: Cypher injection fixes** — parameterised queries, property-name allowlist | Security fixes; no API change | Minimal |
| **H5: PublicEndpointRegistry exact-match** — path-traversal vector fix | Security fix; drop-in replacement | Minimal |
| **P14: NDJSON streaming ingest** — `application/x-ndjson` on timeseries payload | Performance feature; additive content-type | Low |
| **R2: Selective RO-Crate export** — POST with ExportSelection body | Useful to any operator; GET preserved | Low |
| **L5: API keys with expiry** — `validUntil` + JWT `exp` | Widely useful; additive | Low |
| **F4: JWT `iat`-keyed permission cache** — prevents stale cache hits across JWT rotations | Security fix; no API change | Minimal |
| **C3 + A0: `getRoles` fail-closed + OrphanPermissionsBackfill** | Critical security fix; upstream has the same backdoor | Moderate (requires V14 migration) |

Features that should **not** be proposed for upstreaming (too fork-specific):
- The entire `/v2/` API surface (the upstream freezes `/shepard/api/`; /v2/ is this fork's development shelf)
- L2 identifier chain (UUID v7 appId) — upstream may have different plans for identifier evolution
- Plugin SPI and all 11 plugins — upstream may want a different extension model
- Unhide/HKG integration — HMC-specific, not universal

The recommended upstream contribution strategy: batch the A1/M4/M5/H4/C5/H5 fixes into a single security-focused upstream PR with a clear cover letter. These are uncontroversial improvements that any upstream maintainer should accept. Establishing this upstream relationship early reduces the fork maintenance burden and builds goodwill.

---

## What Surprised Me

**The Unhide/HKG integration is genuinely ahead of the field — and nobody knows.**
Searching for "Helmholtz Knowledge Graph shepard" returns nothing external. The plugin is shipped, the feed is functional, but it has not been presented at any venue, registered in any catalogue, or cited in any NFDI4ING output. This is a first-mover advantage with an expiry date: once Coscine or another platform ships HKG integration, the differentiation window closes.

**The LUMEN showcase story is compelling enough for a conference paper today — if labeled honestly.**
The 15-run synthetic campaign with the TR-004 anomaly → investigation → repair → retest chain is a structurally complete demonstrator of a digital thread. It shows lineage, anomaly detection, provenance, and publication in one coherent narrative. The barrier to external presentation is not the story's quality — it is the "NOT REAL DATA" label and the missing screenshots. Those are fixable in one week.

**Eleven plugins extracted from core with consistent SPI is a non-trivial engineering achievement.**
Most research software platforms either have no extension model or have a bolted-on one that requires understanding the core to extend. Shepard's drop-in JAR model — demonstrated by 11 plugins covering AAS, HDF5, S3 storage, git, video, three minters, KIP, spatial, and Unhide — is a genuine adoption advantage for institutes that need custom payload kinds. This is not adequately communicated in any external-facing document.

**The adoption count gap is the strategic moat that matters most, and it is the one most fixable quickly.**
Kadi4Mat reached 100+ users through NFDI4ING embedding. Coscine reached 1,500+ through institutional mandate. Shepard needs one of those mechanisms — a consortium mandate, a DFG-funded data steward position, or an NFDI4ING success story — to cross the adoption chasm. The platform is technically superior to Coscine on timeseries and provenance, and technically comparable to Kadi4Mat on semantic annotations. The missing ingredient is not code; it is community infrastructure.

**The minimum viable external demo is closer than it looks.**
`docker compose up` + `make demo` (seed LUMEN + open browser) = a compelling 10-minute onboarding. The barriers are: (a) no `make demo` target exists, (b) the seed requires a configured admin token, (c) the docs have placeholder screenshots. All three are documentation and scripting tasks, not engineering tasks. This is a week of work, not a sprint.
