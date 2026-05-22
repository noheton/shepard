---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Strategy Advisor — Feature Proposals
**Date:** 2026-05-21  
**Synthesized from:** All six peer agent findings (UX, Ontology, API, Manufacturing, RDM, AI)  
**Lens:** Strategic positioning — funding pathways, adoption, competitive moat, publishable evidence

---

## Proposal 1: FAIR Metadata Spine — License, ORCID Stamp, Access Rights

**Strategic rationale:** Every major funding body (Horizon Europe Art. 17, DFG, Clean Aviation JU, HMC KIP v1.1) requires machine-readable license and creator identity as mandatory PID metadata. Three peer agents (RDM, API scrutinizer, strategy) independently flag that the Unhide HKG feed already asserts `schema:license` but `Collection.java` has no `license` field — the feed is making a promise the entity model cannot keep. This is the single change that moves FAIR from "strong infrastructure" to "actually compliant" for a funding reviewer. It closes the difference between the platform's composite FAIR score (~1.7/3) and a defensible FAIR-ready claim (~2.5/3).

**What it looks like:** Three additive fields on `AbstractDataObject` (and exposed on `Collection`):
- `license` — SPDX identifier string (e.g. `"CC-BY-4.0"`), nullable, surfaced on the Collection sidebar with an inline SPDX picker
- `createdByOrcid` — stamped from `User.orcid` at entity creation time; preserved if the user is later deleted
- `accessRights` — enum: `OPEN / EMBARGOED / RESTRICTED`; `embargoEndDate` (ISO-8601, nullable) appears when EMBARGOED is selected

A "Metadata Completeness Score" widget (0–100, shown as a progress ring on the Collection sidebar) gates PID minting at ≥60 points and HKG feed inclusion at ≥80. The DFG/Horizon Europe DMP snippet endpoint (`GET /v2/collections/{appId}/dmp-snippet`) auto-generates the relevant DMP sections from these fields.

**Plugin or core?** Core — these are identity-layer fields that every plugin and export surface depends on.

**Effort:** S–M (3 additive Neo4j fields + no migration required + 1 sprint of frontend work for the sidebar widget and score endpoint)

**Domain impact:** Closes the Horizon Europe, DFG, and HMC KIP compliance gaps in one move. Unblocks the Unhide HKG feed from emitting a fabricated license value. Enables the publisher plugin (Proposal 2) to map fields deterministically.

**Cross-finding hook:** RDM agent (§3 gap table R1.1/R1.2/A1.2, §4 completeness score spec), API scrutinizer (Unhide feed integrity), strategy advisor (FAIR score risk), manufacturing quality (CERTIFIED status needs a compliant publication record).

---

## Proposal 2: Publisher Plugin — Push-Deposit to Zenodo / InvenioRDM

**Strategic rationale:** Horizon Europe requires deposit in a "certified repository." DFG requires a data publication statement. Currently a researcher must manually export an RO-Crate ZIP, then upload it to Zenodo. This two-step, manual process is the adoption friction point that keeps Shepard from being cited as the compliant repository in a DMP. Making deposit a single button in the UI eliminates the friction and gives funders a machine-readable DOI as evidence of compliance — directly from the same tool that captured the data. This is the capability that lets a PI answer the "where is your data?" audit question without leaving Shepard.

**What it looks like:** A "Publish to repository" button on the Collection detail page that opens a dialog: choose repository (Zenodo, InvenioRDM), confirm license/access rights (pre-filled from Proposal 1 fields), set embargo if applicable, submit. A 202 Accepted response, then a status chip on the Collection sidebar ("Submitted to Zenodo — pending DOI"). On completion, the minted DOI appears as a PID badge. Powered by a `POST /v2/collections/{appId}/publish` async endpoint. The resulting DOI is stored as a `Publication` record.

**Plugin or core?** Plugin (`shepard-plugin-publisher`) — repository adapters have independent release cadences and auth schemes. Core ships the `RepositoryAdapter` SPI interface; the plugin ships Zenodo and InvenioRDM adapters.

**Effort:** M (Zenodo adapter is well-documented REST; async flow reuses existing ExportService and NTF1 notification infrastructure once it ships; InvenioRDM adapter follows the same pattern)

**Domain impact:** Transforms the MFFD showcase from "data in Shepard" to "data in Zenodo with a DOI you can cite in a Clean Aviation deliverable." Makes the LUMEN dataset publishable with one action. Directly answers the Horizon Europe open-data mandate.

**Cross-finding hook:** RDM agent (§5 full plugin design spec), strategy advisor (Risk 4 — single-showcase dependency), manufacturing quality (RO-Crate as submission to certification authority).

---

## Proposal 3: Lineage Walk API + Audit Trail Export

**Strategic rationale:** The manufacturing quality, UX, and API agents all converge on the same structural gap: there is no directed ancestor-walk endpoint. A compliance auditor tracing a defect on the MFFD upper shell must manually navigate 4–6 DataObject detail pages, expanding provenance panels at each step. For EN 9100 Section 7.8.2 and EASA Part 21 G, this is not auditable as a machine-readable trail — it is a walkthrough that exists only in the auditor's memory. A single `GET /v2/data-objects/{appId}/ancestor-chain?depth=10` endpoint, plus a frontend "Trace upstream" panel, converts a multi-hour audit walkthrough into a sub-10-second query. This is also the capability that makes the MFFD showcase publishable as evidence — "we can trace any defect back to raw material in a single API call" is a claim that funds projects.

**What it looks like:** 
- Backend: `GET /v2/data-objects/{appId}/ancestor-chain?depth=10&includeContainers=false` returns a flat ordered list of ancestor DataObjects (name, appId, status, semantic annotations, key attributes) walking the `HAS_PREDECESSOR` edge recursively up to `depth` hops. Implemented as a bounded Cypher path query (`MATCH path=(n)-[:HAS_PREDECESSOR*1..{depth}]->() WHERE n.appId = $appId RETURN path`). O(depth) graph traversal, not an exhaust-all-pages fetch.
- Frontend: A "Trace upstream" tab in the DataObject Provenance panel showing a vertical timeline of ancestors — each as a collapsible card with status chip, key attributes, and a "Jump to" link. The 6-predecessor truncation bug (UX agent finding, `DataObjectProvGraph.vue` lines 86/106) is fixed as part of this work.
- Export: `GET /v2/data-objects/{appId}/ancestor-chain?format=ro-crate` produces a linear audit trail RO-Crate (the "audit package" an EASA conformity submission needs).

**Plugin or core?** Core — this is a fundamental graph traversal on the existing data model.

**Effort:** M (Cypher query is straightforward; the frontend timeline is a new component but a simpler one than the current force-graph; the RO-Crate export variant reuses ExportService)

**Domain impact:** EN 9100 audit velocity: 4-hop lineage trace from 12 manual navigation steps to a single panel open. EASA Part 21 G: machine-readable conformity trail as a downloadable artifact. Competitive moat: no comparator (Kadi4Mat, SciCat, Coscine) can produce a depth-bounded ancestor walk with semantic annotations in a single API call.

**Cross-finding hook:** UX auditor (Opportunity 1 ancestor walk, Auditor persona), manufacturing quality (§2 EN 9100 Table row 7.8.2, §4 rework loop design), API scrutinizer (missing `GET /v2/data-objects/{appId}` flat endpoint is a related gap), data ontologist (material batch as graph node, Gap 5).

---

## Proposal 4: Semantic Spine Completion — CHAMEO + SSN/SOSA + Numeric Annotations

**Strategic rationale:** The data ontologist found that the semantic infrastructure is dramatically ahead of its usage patterns. CHAMEO (defect vocabulary) and SSN/SOSA (sensor network vocabulary) are the two missing bundles that directly block the MFFD use case from being semantically complete. Without CHAMEO, NDT defect classification is freetext — not queryable, not interoperable, not FAIR. Without SSN/SOSA, a timeseries channel cannot declare what physical quantity it observes, making cross-system sensor data discovery impossible. These are additive manifest entries that require no migration. The second part — extending `SemanticAnnotation` to carry an optional `numericValue` + `unitIRI` — is the structural fix that makes `target_thrust_kN = 25 kN` machine-readable instead of a string comparison. This enables range queries ("find all test runs with target thrust > 20 kN") and cross-domain SPARQL inference, which is the capability that would distinguish Shepard's knowledge graph from a tagged document store.

**What it looks like:**
- Add CHAMEO and SSN/SOSA to `ontologies-manifest.json` (two TTL entries, no code change). The annotation picker immediately gains defect types (`CHAMEO:LocalPorosityDefect`, `CHAMEO:FiberMisalignment`) and sensor metadata terms (`sosa:Sensor`, `sosa:observes`, `sosa:FeatureOfInterest`).
- Extend `SemanticAnnotation` with optional `numericValue` (Double) + `unitIRI` (String). The frontend AddAnnotationDialog gains a "Numeric value" field that appears when the property is a `qudt:QuantityKind` subclass. Existing IRI-pair annotations are unchanged.
- Channel unit annotation made mandatory at channel creation time: the `AddChannelDialog` requires a QUDT unit selection (searchable combobox, same as the existing annotation picker). The selected IRI creates an `AnnotatableTimeseries` annotation automatically. This closes the gap where 25 LUMEN channels have known units that never reach Neo4j.

**Plugin or core?** Core — ontology manifest is core infrastructure; `SemanticAnnotation` is a core entity.

**Effort:** S (manifest entries) + M (SemanticAnnotation model extension + migration + frontend)

**Domain impact:** MFFD: NDT defect annotation becomes interoperable with CHAMEO-aware tools. LUMEN: all 25 channels become unit-annotated, enabling cross-run unit-aware SPARQL queries. FAIR I2 (controlled vocabulary) and I1 (formal knowledge representation) both improve materially. A researcher querying the Helmholtz Knowledge Graph for "ultrasonic C-scan defect data from CF/LMPAEK" would find MFFD datasets via CHAMEO IRI matching — currently impossible.

**Cross-finding hook:** Data ontologist (Opportunity 3, Gap 6, channel unit gap), manufacturing quality (§2 EN 9100 Nadcap gap, §8 semantic ontology strength), RDM agent (I2 gap), analytics-AI (channel metadata gap for embedding quality).

---

## Proposal 5: Timeseries AppId Migration (TS-IDa + TS-IDb) — Unblock the ML and API Stack

**Strategic rationale:** The API scrutinizer, analytics-AI, and UX agents all identify the 5-tuple channel identity (`measurement`, `device`, `location`, `symbolicName`, `field`) as a structural blocker. Every ML pipeline that touches timeseries data must assemble and track these five fields — one rename breaks every training script silently. Every live-window API call loads all channels for a container into JVM memory and filters in Java (a full table scan per request). The stored chart-view selection uses pipe-separated 5-tuple strings that break on any channel rename. The design doc (`aidocs/87`) is already written and identifies TS-IDa (mint UUIDs on existing Timeseries nodes) and TS-IDb (expose appId in response) as the zero-risk additive first two phases. These two phases unblock everything else: the live-window endpoint fix, the ML pipeline, the semantic embedding feature, and the import manifest generator. This is the highest-leverage backend-infrastructure investment available.

**What it looks like:** 
- TS-IDa: A Neo4j migration that calls `SET n.appId = randomUUID()` on all existing `:Timeseries` nodes that lack an appId. Idempotent. Zero-risk — additive only.
- TS-IDb: Expose `appId` in the channel list/get response (`GET /v2/timeseries-containers/{appId}/timeseries`). All existing 5-tuple fields remain present; appId is an additional field. Zero breaking changes.
- Frontend update: `useFetchTimeseries.ts`, `useFetchChannelPreview.ts`, and `ShowTimeseriesReferenceDialog.vue` switch to using appId as the channel reference key. The stored `selectedChannels` in `TimeseriesContainerChartViewIO` migrates from pipe-separated 5-tuple to appId array.
- Live-window endpoint fix: Replace the full container scan with a parameterized `findByAppId(timeseriesAppId)` lookup — one index hit instead of a full table scan.

**Plugin or core?** Core — this is a data model migration.

**Effort:** S (TS-IDa migration is one cypher line + test) + S (TS-IDb additive field is one line in the DAO response mapper) + M (frontend switchover across 5 files)

**Domain impact:** ML pipelines become stable across channel renames. Live-window queries drop from O(N channels) table scans to O(1) index lookups. Import manifest generation (the "agentic MFFD ingest" story) becomes possible with stable appIds. This is infrastructure that multiplies the value of every other timeseries feature.

**Cross-finding hook:** API scrutinizer (5-tuple problem, live-window cost, selectedChannels format), analytics-AI (5-tuple ML pipeline tax — "bigger blocker than it appears"), UX auditor (channel preview performance, Risk 2), data ontologist (channel unit annotation at creation time).

---

## Proposal 6: Shop Floor Mode — Process Position Indicator + Rapid NCR Raise

**Strategic rationale:** The manufacturing quality agent documented seven shop-floor UI requirements that Shepard does not meet. The UX agent found no glove-friendly mode, no big-number readouts, and no keyboard-shortcut system. Together these findings mean Shepard cannot be used on the MFFD production floor without a dedicated operator workaround. This is a strategic gap because the DLR ZLP MFFD programme is the platform's strongest funding narrative — but if the IMEs who run the AFP robot cannot use Shepard at their terminal, the "digital thread for aerospace manufacturing" claim is incomplete. This proposal addresses the two highest-value requirements that are architecturally feasible without new backend primitives: a process-chain position indicator and a rapid NCR-raise button.

**What it looks like:**
- **Process Chain Position Bar:** A compact horizontal stepper shown at the top of any DataObject detail page when the DataObject is part of a Predecessor/Successor chain. Shows: [Material Prep] → **[AFP Layup ←YOU ARE HERE]** → [NDT Inspection] → [Weld]. Each step chip shows the predecessor DataObject's status as a colored dot (green=READY, amber=IN_REVIEW, red=NCR_OPEN). Implemented client-side by walking the DataObject's `predecessorIds` and `successorIds` arrays. The step sequence is inferred from the chain topology, not from a separate process definition (which would add backend complexity).
- **Rapid NCR Raise:** A sticky "Raise NCR / Place Hold" button in the DataObject page header (large target, visible without scrolling). Tapping it opens a minimal 3-field form: defect description (textarea), severity (chip selector: Minor/Major/Critical), assign-to (user search). On submit, creates a Child DataObject with `status=NCR_OPEN` and the three fields pre-populated as attributes, and fires a NTF1 notification to the assigned user. The full NCR DataObject is then available for annotation and enrichment. This does not require the full `shepard-plugin-quality` NCR entity — it works with today's DataObject model while the quality plugin is being designed.
- **Large-target status advance button:** A full-width "Advance Status →" button on mobile viewport (Vuetify breakpoint xs–sm) that replaces the compact status dropdown. Shows the current status and the next valid transition as a tap target ≥ 80px tall.

**Plugin or core?** Core (UI components); the full NCR entity lifecycle goes in `shepard-plugin-quality` (Proposal 8).

**Effort:** M (Process Chain Position Bar: client-side computation, moderate new component; Rapid NCR Raise: small form, reuses existing DataObject creation and notification hooks)

**Domain impact:** Enables actual use on the MFFD shop floor. Makes the "digital thread for aerospace manufacturing" claim demonstrable in a live production environment, not just in a researcher's browser. Directly maps to Clean Aviation JU program-level evidence requirements (process documentation during manufacturing).

**Cross-finding hook:** Manufacturing quality (§7 shop floor UI requirements, §3 NCR workaround), UX auditor (Shop Floor IME persona, Idea B pinnable live tiles), analytics-AI (anomaly notification pipeline — the notification that fires after an IME raises an NCR).

---

## Proposal 7: Snap Dashboards MVP — Chart Generation Over Provenance Context

**Strategic rationale:** The vision document calls snap dashboards the "headline killer feature" while it is entirely unbuilt (📐 queued, AI1e). The strategy advisor finding is unambiguous: if Shepard pitches AI-driven analysis to a technically literate Clean Aviation program manager before this ships, it will be embarrassing. The AI agent found that the two genuine near-term AI wins (PDF annotation and manifest generation) both require the `shepard-plugin-ai` STRUCTURED capability as a hard prerequisite. This proposal defines the minimum viable snap dashboard — not the full conversational lineage system, but a feature that a researcher can demonstrate in under 60 seconds and that a funding reviewer will understand immediately: "describe what you want to see, and the system generates a chart."

**What it looks like:** A "Chart from description" button in the TimeseriesAllChannelsChart toolbar. Clicking it opens a text field: "Show me fuel pump vibration on TR-004 compared to TR-003 baseline, 5 seconds around the anomaly." The system uses the `shepard-plugin-ai` STRUCTURED capability to parse the request against the collection's context (channel names, DataObject names from `GET /v2/import/context`) and generates a Vega-Lite spec that renders inline. The user can accept ("Add to lab journal") or dismiss. The AI activity is recorded as a `TimeseriesAnnotation` with `aiGenerated=true` and the prompt stored.

This is deliberately narrower than the full AI1e snap-dashboard design: no tool-use catalogue, no SPARQL calls, no multi-turn conversation. Just: text → chart spec → render. The LUMEN anomaly demo (TR-004 vibration spike at t=8s) is the canonical test case — it should work on the first public demo.

**Plugin or core?** Plugin-gated (`shepard-plugin-ai` STRUCTURED capability required, fails gracefully when unconfigured). The chart rendering is core (Vega-Lite is already planned; ECharts is already in place).

**Effort:** L (requires `shepard-plugin-ai` foundation to ship first — 2–3 sprint prerequisite — then the MVP chart feature is 1 sprint; total effort M+M in sequence)

**Domain impact:** Transforms "we plan to have AI" into "here is AI working." Directly demonstrates the LUMEN TR-004 anomaly story in a single natural-language query. Opens the conversation with Clean Aviation program managers about data-driven process monitoring. Removes the most significant gap between the vision document's claims and the shipped platform.

**Cross-finding hook:** Strategy advisor (Risk 5 — snap dashboards are load-bearing for the narrative), analytics-AI (Opportunity 5 — LLM manifest generation shares the STRUCTURED capability; Opportunity 1 — anomaly detection provides the ground truth for the demo), UX auditor (Opportunity 3 — side-by-side timeseries comparison is the prerequisite chart type).

---

## Proposal 8: Quality Plugin Foundation — Status Machine + Predecessor Gate

**Strategic rationale:** The manufacturing quality agent found that the status field is stored as a plain `String` in Neo4j with no server-side enforcement — any status value can be written in any direction by any Write user. `PUBLISHED → DRAFT` is as easy as `DRAFT → PUBLISHED`. This is the single largest gap between Shepard's current design and aerospace quality record requirements (EN 9100 §8.6, §8.7). The strategic case for closing it now (rather than later) is that the MFFD AFP dataset is expected ~2026-05-26 — the first real production-grade dataset. If it is ingested without status machine enforcement, the first quality records will be structurally non-compliant and retroactively fixing them will require a data migration. The minimum viable quality foundation — a configurable predecessor-status gate and five new status values — can ship as a light backend change without the full `shepard-plugin-quality` entity model.

**What it looks like:**
- **New status values:** `NCR_OPEN`, `ON_HOLD`, `REJECTED`, `CERTIFIED` added to the frontend dropdown and `AbstractDataObjectIO` enum hint. No schema migration needed (status is already a freetext String). Each new value gets a distinct Vuetify chip color in the DataObjects table.
- **Predecessor-status gate:** A configurable rule in `DataObjectService.createDataObject`: if any direct predecessor has status in `{NCR_OPEN, ON_HOLD}`, return `409 Conflict` with a body explaining which predecessor is blocking. Toggled via `FeatureToggleRegistry` (default OFF for existing installs; operators enable it explicitly). This prevents a successor process step from being created while an NCR or hold is open.
- **Immutability guard on PUBLISHED:** When a DataObject's current status is `PUBLISHED` or `CERTIFIED`, a PATCH that would change the status to `DRAFT` or `IN_REVIEW` requires the `instance-admin` role. Regular Write users can no longer silently un-publish a quality record. This is a service-layer guard, not a schema change.
- **Status transition audit:** Each status change is captured as a PROV-O activity via the existing `ProvenanceCaptureFilter` (already automatic for all PATCH operations). No new infrastructure needed.

**Plugin or core?** Core for the status machine and gate. The full NCR entity lifecycle (`:NonConformance` node, disposition workflow, role-gated transitions, NTF1 integration) is `shepard-plugin-quality` — but this proposal is the prerequisite slice that can ship without the plugin.

**Effort:** S–M (status enum additions are trivial; predecessor gate is a 1-week backend change with tests; immutability guard is a role check in the PATCH handler)

**Domain impact:** Makes Shepard defensible as a quality record system for research programmes (not yet EASA-certifiable, but no longer structurally non-compliant). Directly enables the MFFD AFP dataset to be ingested with quality-aware status semantics. Sets the stage for `shepard-plugin-quality` by establishing the transition guard pattern.

**Cross-finding hook:** Manufacturing quality (§2 EN 9100 table — CRITICAL gap on §8.6 release of products, §2 status enforcement finding), UX auditor (status filter is client-side only — server-side status enables server-side filtering as a follow-on), data ontologist (status ON_HOLD for TR-005 in the LUMEN seed).

---

## Proposal 9: Global Entity Search — DataObjects, Channels, Containers

**Strategic rationale:** The UX auditor found that the global search bar hits collections only. A researcher who knows the DataObject name "AFP_Run_Layer_047" must browse into the right collection, expand the tree, or use per-collection search — all paths that require knowing the collection first. This defeats the purpose of a global search. The competitive comparators (Kadi4Mat, Coscine) have global entity search as a baseline feature. For adoption at a new DLR institute, the first thing a researcher does is search for something — if the first search fails, adoption fails. This is the lowest-effort change with the highest new-user impact.

**What it looks like:** The global search autocomplete in `HeaderBar.vue` (already backed by a `v-autocomplete` with a 400ms debounce) returns grouped results: Collections, DataObjects, Timeseries Channels, Containers. Each group shows up to 3 results with a "See all →" link. Selecting a result navigates directly to the entity (using appId routing for DataObjects; the existing Collection detail route for Collections). Backend: a composite endpoint `GET /v2/search?q={term}&types=collections,data-objects,channels&limit=5` or parallel requests to existing collection/DataObject list endpoints with a name filter. The DataObject name filter already exists in `usePagedDataObjects` — the gap is wiring it through the header composable.

**Plugin or core?** Core — this is a search surface on existing entities.

**Effort:** S–M (backend composite endpoint is the main work; frontend autocomplete change is straightforward given existing infrastructure)

**Domain impact:** Converts "navigate-then-search" (15 seconds, 5 clicks) to "search-then-arrive" (2 seconds, 1 action). Makes Shepard immediately usable for a researcher who was handed a DataObject name by a colleague. Critical for adoption at institutes where researchers are not already familiar with the collection structure.

**Cross-finding hook:** UX auditor (Opportunity 1, highest-impact finding, all four personas), API scrutinizer (missing flat `GET /v2/data-objects/{appId}` endpoint — global search is the UX expression of this gap), strategy advisor (Risk 1 adoption chasm — first-session experience is determinative).

---

## Proposal 10: Bulk Annotation + Row Selection in DataObjects Table

**Strategic rationale:** The UX auditor found that annotating 50 channels with a shared unit requires 350 interactions (50 dialog opens × 7 clicks each). This is the single highest-friction task for data curators, and it is precisely the task that FAIR data preparation requires: a researcher finishing a test campaign needs to annotate every DataObject with campaign metadata, every channel with a unit, and every inspection result with a quality flag. The Python client is the correct fallback, but a tool that forces power users to the API for batch operations undermines the "casual user first" promise. The UX agent rated this CRITICAL; the data ontologist noted that 15 LUMEN DataObjects all carry the same `propellant: "LOX/LCH4"` attribute as independent freetext copies — bulk annotation would have prevented this redundancy.

**What it looks like:** Row selection checkboxes in `CollectionDataObjectsPanel`. A floating action bar appears when any rows are selected, offering: "Set status" (bulk status PATCH), "Add annotation" (opens the existing `AddAnnotationDialog` with a `targets: Annotated[]` variant), "Export selection" (sub-RO-Crate of selected DataObjects). For the annotation dialog, the target list shows "Applying to N selected DataObjects" and submits N sequential PATCH calls (acceptable at ≤50; a progress indicator shows for larger counts). Server-side status filter (`?status=IN_REVIEW`) is added to the DataObject list endpoint as a prerequisite, replacing the broken client-side filter.

**Plugin or core?** Core — this is a UI pattern change on existing entities.

**Effort:** M (row selection + action bar is straightforward Vuetify; `AddAnnotationDialog` multi-target variant is the main engineering work; status filter backend extension is small)

**Domain impact:** 50-channel annotation: ~350 clicks → ~5 clicks (98% reduction). Bulk status set for 15 LUMEN test runs: 75 interactions → 3. This is the change that makes Shepard viable for post-campaign data curation without forcing researchers to the Python client. It directly accelerates FAIR metadata preparation and makes the MFFD dataset annotation task tractable.

**Cross-finding hook:** UX auditor (Opportunity 2, CRITICAL rating, Curator persona), data ontologist (propellant string redundancy across 15 DataObjects — structural illustration of the bulk annotation gap), RDM agent (metadata completeness requires annotating many objects consistently).

---

## Proposal 11: MFFD Domain Seed — Real Data Ingestion + Showcase Templates

**Strategic rationale:** The strategy advisor finding is the most direct: the narrative "Shepard is the digital thread for the JEC Award-winning MFFD fuselage" currently has no working demo. The LUMEN seed is explicitly labeled "NOT REAL DLR/LUMEN data." The MFFD TCP thermal trail dataset is expected ~2026-05-26. Until a real (or convincingly realistic) AFP run dataset is seeded and demonstrable, every pitch to a Clean Aviation program manager, every DFG proposal, and every NFDI4ING success story is vapor. This proposal treats the MFFD data ingestion as a strategic deliverable, not just a data engineering task.

**What it looks like:**
- An MFFD-specific `seed.py` (or extension of the existing LUMEN seed pattern) that ingests: the TCP thermal trail timeseries (laser power, consolidation force, TCP temperature, kinematic logs), at least one NDT scan file, a runlog JSON, and an equipment calibration reference. The seed uses the process chain topology described by the data ontologist: Material Batch → AFP Layup Run → NDT Inspection → (conditional) Rework or Weld.
- A `ShepardTemplate` for each of the four MFFD process steps (AFP Layup, Ultrasonic Weld, NDT Inspection, Resistance Weld) with required attributes based on AS9102 (part number, material lot ID, equipment serial, operator, date). The templates enforce a minimum required-field structure at DataObject creation time.
- Semantic annotations using the existing `shepard-experiment.ttl` vocabulary (ManufacturingProcess:AFP, InspectionMethod:UltrasonicCScan, QualityFlag:QualityPass/Fail) applied to all seeded DataObjects.
- The anomaly detection endpoint (AI1b) run on any timeseries channels where anomalies are present, with `createAnnotations=true`, to demonstrate the AI-assisted QC workflow.
- A `README.md` in the seed directory that describes the demo story a presenter should tell and labels synthetic data explicitly at the Collection level (not buried in the description).

**Plugin or core?** Seed/tooling (not a platform feature, but a strategic asset).

**Effort:** M (seed script: 1–2 weeks of data engineering; templates: 2 days; annotations: 1 day; dependent on MFFD data arrival ~2026-05-26)

**Domain impact:** Transforms the LUMEN showcase from "synthetic fiction" to "real aerospace manufacturing data." Creates a live demo that can be shown at JEC World, DLRK, and NFDI4ING conferences. Provides the publishable evidence needed for a case study paper. Directly addresses the adoption chasm — institutes will adopt a platform they can see working on data they recognize.

**Cross-finding hook:** Strategy advisor (Risk 4 single-showcase dependency, Recommendation 1), manufacturing quality (Templates + process chain topology), data ontologist (MFFD entity blueprint §2.1, material batch as Predecessor node), analytics-AI (MFFD AFP data as first real training corpus).

---

## Proposal 12: API Hygiene Sprint — referenceIds Fix + Pagination Consistency + ProblemJson

**Strategic rationale:** This is a cross-cutting infrastructure proposal synthesizing the API scrutinizer's top findings. Three issues directly impede any caller building on the `/v2/` surface: (1) `DataObjectIO.referenceIds` is the live bug that caused 404s in the MCP server — any AI agent or SDK consumer reading a DataObject will misuse this field; (2) two coexisting pagination shapes (`page`+`size` vs `limit`) force callers to context-switch between endpoint groups; (3) four different error shapes make error handling unreliable. These are not glamorous, but they are the friction that determines whether an external developer's first encounter with the API ends in a working integration or a support request. The API is the most-exposed surface for any DLR institute adoption or external contributor.

**What it looks like:**
- **referenceIds fix (CRITICAL, 1 day):** In the `/v2/` DataObject response, replace `referenceIds: long[]` with three typed appId arrays: `timeseriesReferenceAppIds: string[]`, `fileReferenceAppIds: string[]`, `structuredDataReferenceAppIds: string[]`. The `DataObjectListItemV2IO` subclass pattern already provides the precedent. The `referenceIds` field remains in the v1 frozen surface.
- **Pagination consistency (M):** All list endpoints adopt `page` + `size`. `ProvenanceRest.listActivities` switches from `?limit` to `?page`+`?size` with a `totalCount` field on every list response envelope. A shared `PagedResponse<T>` generic wrapper enforces this across all `/v2/` list endpoints.
- **ProblemJson everywhere (S):** Register a `ExceptionMapper<WebApplicationException>` that wraps all unhandled exceptions in RFC 7807 `ProblemJson`. The three hand-rolled error shapes (plain string, inline JSON string, `ApiError`) become dead code paths. The mapper is already in the codebase for some endpoints — making it the default is a configuration change plus removal of the hand-rolled variants.
- **OpenAPI tag cleanup (S):** Replace internal task codes (`TS_LIVE1`, `CC1b`, `IMP1`, etc.) in `@Tag` annotations with human-readable names (`Timeseries Live Window`, `Collections`, `Import`, etc.).

**Plugin or core?** Core.

**Effort:** S (referenceIds rename) + M (pagination consistency) + S (ProblemJson default) + S (tag cleanup) = total M, parallelizable across contributors

**Domain impact:** Eliminates the live 404 bug in any MCP/AI agent integration. Makes the API surface consistent enough for SDK codegen without post-processing. Removes the documentation debt of four error shapes. This is the prerequisite for any serious external SDK — and an external SDK is the fastest path to adoption at other DLR institutes.

**Cross-finding hook:** API scrutinizer (all four critical/major findings), analytics-AI (5-tuple as ML pipeline tax — appId arrays in the DataObject response enable channel addressing by appId), strategy advisor (adopter count gap — external SDK lowers the first-integration cost).

---

## Synthesis: Cross-Agent Proposals

### Proposal S1 (synthesizes UX + Ontology + Manufacturing + RDM): FAIR-Complete MFFD Demo Sprint

This is not an additional feature — it is a sequencing recommendation that synthesizes Proposals 1, 3, 4, and 11 into a single demonstrable milestone. The four proposals together close the gap between "Shepard stores MFFD data" and "Shepard stores MFFD data, annotates it with CHAMEO defect vocabulary, traces any defect back to raw material in one API call, stamps a DataCite DOI with a license, and publishes it to the Helmholtz Knowledge Graph." That is a publishable case study milestone. The strategic recommendation is to treat these four proposals as a single sprint target (or back-to-back sprints) rather than independent features — because the narrative value of the combined milestone is multiplicative, not additive.

**Proposals in sequence:** 1 (license/ORCID — 1 sprint) → 4 (CHAMEO + numeric annotations — 1 sprint) → 11 (MFFD seed — 1–2 weeks, gated on data arrival) → 3 (ancestor walk — 1 sprint). Total: ~6–8 weeks of focused work. Output: a live demo where a visitor to JEC World 2027 can see the MFFD AFP data, annotate a defect with CHAMEO terms, trace it to the raw material batch in one click, and receive a DOI-minted RO-Crate with a license and creator ORCID. That is the case study paper.

**Funding pathway activated:** HMC Project Call 2026 (deadline 06 July 2026), Horizon Europe Article 17 compliance, NFDI4ING success story registration.

---

### Proposal S2 (synthesizes API + AI + UX): Agentic Ingest Pipeline

This cross-cutting proposal synthesizes Proposals 5 (TS-IDb), 12 (API hygiene), and the analytics-AI finding on manifest generation. The `GET /v2/import/context` endpoint already returns collection fingerprint + semantic vocabulary. Once appIds are stable (TS-IDb) and the `POST /v2/import/jobs` endpoint is implemented (API scrutinizer's CRITICAL finding), an LLM agent can: (1) fetch context, (2) generate an `ImportManifestIO` JSON from a directory of AFP runlog files, (3) validate the manifest, (4) execute the import. This is the "zero-manual-data-entry" MFFD ingest story that makes the platform viable for researchers who are not Python programmers.

**The missing piece is `POST /v2/import/jobs`.** The validate endpoint produces a commitId that expires in 24 hours and can never be redeemed. The analytics-AI agent rated this CRITICAL. This single endpoint implementation, combined with TS-IDb (stable channel appIds in manifests) and a minimal `shepard-plugin-ai` foundation, delivers the agentic ingest story.

**Proposals in sequence:** 5 (TS-IDa/IDb — 1 sprint) → 12 (referenceIds + import/jobs — 1 sprint) → AI plugin foundation (2–3 sprints) → manifest LLM generator (1 sprint). Total: ~6–8 weeks. Output: a researcher drops a directory of AFP runlog files into the import flow, an LLM generates the manifest, the import executes, and the DataObjects appear in Shepard with semantic annotations pre-populated from the runlog content.

**Funding pathway activated:** Clean Aviation JU — "automated data capture from manufacturing systems" is a direct KPI enabler. NFDI4ING — agentic data management is a 2026 thematic priority.

---

## Priority Table

| # | Proposal | Effort | Strategic Goal | Cross-agent |
|---|---|---|---|---|
| 1 | FAIR Metadata Spine | S–M | Funding compliance | RDM + API + Strategy |
| 12 | API Hygiene Sprint | M | Adoption + developer experience | API + AI + Strategy |
| 5 | TS-IDa/IDb Migration | S+M | ML + API stability | API + AI + UX |
| 9 | Global Entity Search | S–M | Adoption (first-session) | UX + API + Strategy |
| 10 | Bulk Annotation | M | Curator productivity | UX + Ontology + RDM |
| 3 | Lineage Walk API | M | EN 9100 + FAIR I3 + competitive moat | UX + MfgQuality + API |
| 8 | Quality Plugin Foundation | S–M | Manufacturing credibility | MfgQuality + UX |
| 11 | MFFD Domain Seed | M | Demo + case study | Strategy + Ontology + MfgQuality |
| 4 | Semantic Spine Completion | S+M | FAIR I2 + CHAMEO | Ontology + RDM + MfgQuality |
| 2 | Publisher Plugin | M | Horizon Europe compliance | RDM + Strategy |
| 6 | Shop Floor Mode | M | MFFD production use | MfgQuality + UX |
| 7 | Snap Dashboards MVP | M+M | Funding narrative + AI claim | AI + UX + Strategy |
| S1 | FAIR-Complete MFFD Sprint | L (combined) | Case study paper + HMC 2026 | All |
| S2 | Agentic Ingest Pipeline | L (combined) | Clean Aviation KPI | API + AI + Strategy |

**Recommended first wave (parallel tracks):**
- Track A (infrastructure, low risk): Proposals 12 → 5 → 9
- Track B (FAIR compliance, funding-critical): Proposals 1 → 4
- Track C (manufacturing use case): Proposals 8 → 11
- Track D (UX, adoption): Proposals 10 → 6

Snap Dashboards MVP (Proposal 7) and Publisher Plugin (Proposal 2) unblock after Track B completes.
