---
stage: decommissioned
last-stage-change: 2026-05-23
---

# AI & Analytics Feature Proposals
**Author:** Analytics & AI Opportunities Specialist (cross-peer synthesis)
**Date:** 2026-05-21
**Input:** analytics-ai.md + all six peer discovery reports + aidocs/platform/86-ai-plugin-design.md

Proposals are ordered by sequencing priority (dependencies first), not by impact rank.
Each proposal names the cross-peer hook that motivated it.

---

## Proposal 1 — Channel Quality Score Surface (AI1c UI)

**Name:** `channel-quality-surface`

**Problem it solves:**
The rolling-median MAD anomaly detector (AI1b) is shipped and produces a `qualityScore ∈ [0,1]` field on every `TimeseriesReference`. The backend computation is complete. Zero researchers can see it — there is no UI surface anywhere in the codebase. The feature matrix (aidocs/44) lists AI1c as `⚙ BE ✓ / UI pending`.

Cross-peer hook: Manufacturing Quality Agent found that "9.1.1 Monitoring, measurement, analysis and evaluation" is graded MINOR gap — the only reason it's minor is that the gap is UI-only. That is precisely what this proposal closes.

**What it looks like:**
- **Researcher view:** A colour-coded quality badge next to each channel row in `TimeseriesMeasurementsTable.vue`. Green (≥ 0.8), amber (0.5–0.8), red (< 0.5). Hovering the badge shows the score and the last-evaluated timestamp.
- **Chart view:** An optional quality overlay on `TimeseriesAllChannelsChart.vue` — a thin horizontal band at the top of each channel strip showing the score as a heat-map colour.
- **DataObject panel:** A "Channel health" summary chip on the timeseries container card: "3 / 25 channels below quality threshold."
- **Technical:** No new backend. The score is already on `TimeseriesReference`; the `useFetchTimeseriesReferences` composable just needs to surface it. Frontend-only change. Vitest unit tests on the badge component.

**Plugin or core?** Core (no AI plugin required — score is pre-computed in Java).

**Effort:** S (2–3 days frontend + tests).

**Domain impact:** MFFD anomaly detection (immediate — surfaces channel health on AFP layup runs), general researcher productivity.

**Cross-finding hook:** UX Auditor identified the timeseries table as a primary pain surface. This adds actionable QC information without adding navigation steps.

---

## Proposal 2 — Anomaly-to-Notification Bridge (AI1b → NTF1)

**Name:** `anomaly-notify`

**Problem it solves:**
AI1b detects anomalies on demand (POST endpoint, user-initiated). The engineering value of anomaly detection depends on engineers learning about anomalies without polling. Today an engineer must remember to run the endpoint or check the annotation panel. For MFFD production monitoring, a vibration spike on the AFP consolidation roller that goes unnoticed for 30 minutes means scrapped plies.

Cross-peer hook: Manufacturing Quality Agent found no "notification trigger" for NCR status changes (§3, gap 5). That gap is the same architectural hole: events happen in the graph but nothing tells anyone. The Notification system (NTF1) is the designed fix; this proposal wires AI1b to it.

**What it looks like:**
- **Researcher view:** When `createAnnotations=true` on a detect-anomalies call produces an interval with `confidence > 0.8`, a notification appears in the notification bell. Message: "Anomaly detected on channel `vib_fuel_pump_x` in run TR-004 (confidence: 0.94, peak z-score: 11.8). [View channel]"
- **Admin config:** A new `:AiNotificationConfig` field `anomalyConfidenceThreshold` (default 0.8); PATCH endpoint at `/v2/admin/ai/notification-config`.
- **Email opt-in:** Users subscribed to the DataObject (CW1 watch) receive an optional email notification via the NTF1 email channel if they have enabled it in profile preferences.
- **Technical:** Add a `NotificationProducer` call inside `AnomalyDetectionService.persistAnnotations()` when confidence exceeds threshold. The notification payload includes `dataObjectAppId`, `timeseriesReferenceAppId`, `channelName`, `intervalStartNs`, `intervalEndNs`, `confidence`, `peakZScore`. NTF1 must ship first (hard dependency).

**Plugin or core?** Core (AI1b is core; NTF1 bridge is a service-level call).

**Effort:** S (1 day backend once NTF1 ships). NTF1 is the gating dependency.

**Domain impact:** MFFD anomaly detection (high — turns passive detection into active alerting), PLUTO telemetry (same pattern: detect → alert on-orbit).

**Cross-finding hook:** Analytics AI findings §8 ("Anomaly notification pipeline") identified this as a 1-day task. Manufacturing Quality Agent confirmed the notification gap independently (§3, gap 5).

---

## Proposal 3 — Provenance Gap Detector (graph analytics, no model training)

**Name:** `prov-gap-detector`

**Problem it solves:**
The Manufacturing Quality Agent found that the LUMEN predecessor chain does not enforce that TR-006 cannot be created before TR-005 is resolved (§3). The Research Data Manager found that the PROV-O graph captures Shepard-level actions but not manufacturing-level actions. Both point to a common root: there is no way to query "does this DataObject have the expected provenance shape for its type?"

Cross-peer hook: Manufacturing Quality Agent §2 EN 9100 §8.6 (Release gate) is CRITICAL. The Data Ontologist found that TR-005 `status = null` in the LUMEN showcase — a hold state with no graph-queryable signal. The API Scrutinizer identified a missing `GET /v2/data-objects/{appId}` flat endpoint that would be needed to support this feature's results page.

**What it looks like:**
- **Researcher / Auditor view:** A "Provenance health" panel on the Collection detail page, accessible via a toolbar button. It shows a table: each DataObject, an expected provenance shape (derived from its `digitalObjectType` or Template type), and a list of gaps: "Missing: `wasGeneratedBy` activity", "Missing: Predecessor link before Successor", "Open NCR annotation with no closed_at".
- **Admin config:** Provenance shape rules are defined as a JSON configuration at `/v2/admin/provenance-rules` — a set of Cypher-pattern conditions per `digitalObjectType`. Default rules ship for the standard lifecycle (DRAFT → READY must have at least one provenance activity; PUBLISHED must have a PID link).
- **Technical:** The gap detector is a set of Cypher queries against the Neo4j graph, scheduled or on-demand. No ML model. Uses the Neo4j GDS weakly-connected-components algorithm to find isolated subgraphs in the provenance DAG. Returns a `ProvenanceHealthIO` report per DataObject. `GET /v2/collections/{appId}/provenance-health` endpoint.

**Plugin or core?** Core (`/v2/` endpoint, Cypher queries over existing graph).

**Effort:** M (1 week backend + Cypher query set + light frontend table component).

**Domain impact:** MFFD anomaly detection (EN 9100 §8.6 gate), PLUTO telemetry (completeness check on commanding session DataObjects), general researcher productivity.

**Cross-finding hook:** Closes the Manufacturing Quality Agent's CRITICAL gap on "Release of products" (§2). Complements the UX Auditor's Idea A (ancestor walk) — the gap detector is the automated version of what the auditor does manually.

---

## Proposal 4 — PDF / Document Auto-Annotation Suggestions (quick-win)

**Name:** `doc-annotation-suggest`

**Problem it solves:**
The Data Curator persona (UX Auditor §Persona 2) spends ~350 clicks to annotate 50 channels after a recording session. The Data Ontologist found that LUMEN's `test_engineer`, `bench`, and `propellant` are free-text strings that could be semantic annotations. The Research Data Manager found that the `createdByOrcid` field is never stamped on entities — the test_engineer name is the only attribution record.

All three agents converge on the same root cause: metadata entry is manual, one-at-a-time, with no suggestions from the content already in the system.

Cross-peer hook: Directly addresses UX Auditor Opportunity 2 (bulk annotation) via a complementary path — rather than requiring the curator to bulk-annotate, the system suggests annotations from uploaded content, reducing the per-item effort before bulk action is even needed.

**What it looks like:**
- **Researcher view:** After uploading a PDF or structured JSON to a File Container, a dismissable banner appears: "Suggestions available based on uploaded document — Review now." Clicking opens a side-drawer with pre-filled attribute key-value pairs and ontology annotation candidates, each with a confidence indicator and accept/reject toggle. "Apply accepted" POSTs to existing attribute-update and annotation-create endpoints. Nothing is written without user confirmation.
- **Technical:** `POST /v2/data-objects/{dataObjectAppId}/suggest-annotations` body: `{ "fileReferenceAppId": "..." }`. Backend fetches the file bytes from MinIO, sends them to the `shepard-plugin-ai` STRUCTURED capability with the prompt pattern from analytics-ai.md §3. Response: `{ "suggestedAttributes": {}, "suggestedAnnotations": [{ "propertyName", "valueName", "propertyIRI", "valueIRI", "confidence" }], "aiActivityAppId": "..." }`. The `aiActivityAppId` links to the `:AiActivity` provenance node for EN 9100 auditability. Feature is hidden (button not rendered) when `shepard-plugin-ai` STRUCTURED capability is not configured.

**Plugin or core?** Consumer is core (`/v2/` endpoint); LLM call goes through `shepard-plugin-ai` STRUCTURED slot.

**Effort:** M (3–4 days backend + 2 days frontend). Hard dependency: `shepard-plugin-ai` must ship first.

**Domain impact:** MFFD anomaly detection (annotating AFP run reports at upload time), general researcher productivity (eliminates manual metadata entry for common document types).

**Cross-finding hook:** Data Ontologist §1.1 found that `propellant: "LOX/LCH4"` is repeated 15 times as freetext — one typo breaks all queries. This proposal turns the repeated value into a suggestion sourced from the document, making the annotation consistent without curator effort. Research Data Manager Opportunity 5 (update seed to use ORCID IRIs) is the aspirational end-state; this proposal is the user-facing path to reach it incrementally.

---

## Proposal 5 — Numeric Semantic Annotation + Unit Inference (ontology model + AI assist)

**Name:** `numeric-annotation-field`

**Problem it solves:**
The Data Ontologist §Gap 1 identified the single highest-priority structural gap in the data model: `SemanticAnnotation` can only store IRI pairs, not numeric values. `target_thrust_kN = 25` stored as a string in `attributes` is not queryable as a number, not unit-aware, and cannot be inferred against. Range queries ("find all test runs within 10% of rated thrust") require client-side string parsing.

Cross-peer hook: Manufacturing Quality Agent §2 EN 9100 §7.6 (calibration traceability) requires `calibration_valid_until` as a machine-readable date — currently expressible only as a freetext attribute. Research Data Manager §3 R1.3 gap notes that `attributes["test_engineer"]` is not machine-actionable. The QUDT and OM-2 ontologies are already seeded — the vocabulary to express units exists; only the carrier field is missing.

**What it looks like:**
- **Researcher view:** In `AddAnnotationDialog.vue`, when the user selects a property that the ontology marks as `m4i:NumericalVariable` or `qudt:QuantityKind`, the dialog shows an additional row: a numeric input field and a QUDT unit browser (autocomplete). The combined value is stored as `(propertyIRI, numericValue, unitIRI)` rather than `(propertyIRI, valueIRI)`.
- **AI assist layer:** The `doc-annotation-suggest` feature (Proposal 4) uses the STRUCTURED capability to extract numeric values + units from PDFs. When the extracted value is numeric, it is returned in a `numericValue` + `unitIRI` shape rather than as a string. The AI extracts "thrust = 25 kN" and maps it to `{ numericValue: 25.0, unitIRI: "qudt:KiloNewton" }`.
- **Technical:** Extend `SemanticAnnotation.java` with optional `numericValue` (Double) + `unitIRI` (String) fields. Add a Neo4j migration (idempotent `SET` for new properties on existing nodes, which defaults to null). Extend `SemanticAnnotationIO`. Add a Cypher range-query helper to `SemanticAnnotationDAO`: `findByPropertyAndNumericRange(propertyIRI, min, max)`. No existing annotations are affected — the extension is additive.

**Plugin or core?** Core (model change + migration; AI assist uses plugin-ai STRUCTURED slot).

**Effort:** M (1 week backend migration + DAO + IO + 2 days frontend dialog extension). Independent of plugin-ai for the model change; AI-assist integration requires plugin-ai.

**Domain impact:** MFFD anomaly detection (calibration date ranges, thrust tolerances), PLUTO telemetry (commanding parameter bounds), general researcher productivity (numeric range search across collections).

**Cross-finding hook:** Directly closes Data Ontologist Gap 1 ("Numeric measurement cannot be a semantic annotation value"). Enables the Research Data Manager's Opportunity 1 (add `license` SPDX field) to also benefit from controlled vocabulary suggestions.

---

## Proposal 6 — Ancestor Chain Endpoint + Audit Trail Export

**Name:** `ancestor-chain-api`

**Problem it solves:**
The UX Auditor §Persona 3 (Compliance Auditor) identified two CRITICAL gaps: the predecessor graph is truncated at 6 nodes in the UI, and there is no UI-level recursive predecessor walk. The API Scrutinizer found that "Add a DataObject predecessor/successor relationship" is a missing operation from `/v2/` — and the reverse traversal (walk backwards) is equally absent. The Manufacturing Quality Agent §2 graded EN 9100 §7.8.2 (traceability) MAJOR precisely because "no directed lineage walk API exists."

Cross-peer hook: All four non-AI agents independently cited the same missing traversal capability. This is the most cross-cutting non-AI gap in the platform, and it has a clean AI hook: the ancestor chain, once available as an API response, becomes the input context for an AI-generated audit narrative (Proposal 7 below).

**What it looks like:**
- **Auditor view:** On the DataObject Provenance panel, a "Trace upstream" button triggers a linear timeline of ancestors. Each ancestor is shown as a collapsible card: name, status, creation date, associated operator (from PROV-O activity), key attributes. The chain can be exported as a PDF or RO-Crate sub-package.
- **Technical:** `GET /v2/data-objects/{appId}/ancestor-chain?depth=20&includeActivities=true` returns an ordered list of `AncestorNodeIO` from the queried DataObject back to the root (no predecessors). Each node carries: `appId`, `name`, `status`, `attributes`, `semanticAnnotations`, and the `ActivityIO` records that connect it to its successor. Implemented as a depth-bounded Cypher traversal: `MATCH path = (root)-[:HAS_SUCCESSOR*..20]->(target {appId: $appId}) RETURN nodes(path), relationships(path)`. The `depth` cap prevents runaway traversal on circular graphs (which should not exist but cannot be assumed).

**Plugin or core?** Core (`/v2/` endpoint, pure Cypher).

**Effort:** M (1 week backend + Cypher + IO shapes + light frontend component). No AI dependency.

**Domain impact:** MFFD anomaly detection (EN 9100 traceability walk from defect to raw material), PLUTO telemetry (chain from anomaly investigation back to commanding session), general researcher productivity.

**Cross-finding hook:** Unblocks Proposal 7 (AI audit narrative). Also closes the API Scrutinizer's MAJOR missing operation "Get DataObject by appId without knowing Collection appId" — the ancestor chain endpoint operates on appId alone, establishing the pattern for a flat `/v2/data-objects/{appId}` endpoint.

---

## Proposal 7 — AI Audit Narrative Generator

**Name:** `audit-narrative-gen`

**Problem it solves:**
The Manufacturing Quality Agent §2 found that EN 9100 §10.2 (nonconformity and corrective action) requires a "machine-readable causal link, not a free-text journal entry." The Research Data Manager §4 proposed a `GET /v2/collections/{appId}/dmp-snippet` endpoint. Both converge on the same need: structured data in the graph should produce human-readable, standards-aligned text documents automatically — not require a curator to write them from scratch.

Cross-peer hook: Strategy Advisor §2 found that "the provenance story is genuinely ahead of comparators" but "undersold." The audit narrative feature is the mechanism that makes the provenance story legible to a non-technical auditor without them understanding SPARQL or navigating the graph UI.

**What it looks like:**
- **Auditor view:** On the Collection Lineage Graph page, a "Generate audit report" button is available to users with Read permission. After ~10 seconds, a Markdown document appears in a side panel: "This report covers the process chain from [raw material DataObject] to [finished part DataObject]. The chain consists of N steps across M operators over D days. Step 3 (AFP Layup Run 007, 2026-03-14) carried a QualitySuspect flag; the subsequent Rework step (2026-03-16) resolved it with annotation QualityPass. No open NCRs remain on the chain." The document can be exported as PDF (via browser print) or saved as a Lab Journal entry on the Collection DataObject.
- **Technical:** `POST /v2/collections/{appId}/generate-audit-report` body: `{ "fromDataObjectAppId": "...", "toDataObjectAppId": "...", "depth": 20 }`. Backend calls `GET /v2/data-objects/{appId}/ancestor-chain` (Proposal 6), collects the full chain, retrieves PROV-O activities for each hop, and sends the structured result to `shepard-plugin-ai` TEXT capability with a fixed system prompt: "You are a DIN EN 9100 audit report generator for an aerospace manufacturing digital thread. Summarise the following process chain as a formal audit trail document..." The LLM output is returned to the client as Markdown. The `:AiActivity` node is created (provenance of AI-generated text). The user can optionally POST the returned Markdown to `POST /v2/lab-journal/{collectionAppId}/entries` to persist it.
- **DMP narrative variant:** `GET /v2/collections/{appId}/dmp-snippet` (Research Data Manager Opportunity 8) uses the same TEXT capability but with a different prompt targeting DFG / Horizon Europe DMP section 3 ("Data description") and section 5 ("Archiving and long-term accessibility"). Returns Markdown pre-populated from Collection fields.

**Plugin or core?** Core endpoint; TEXT capability via `shepard-plugin-ai` (hard dependency).

**Effort:** M (2–3 days backend + 1 day frontend panel; hard dep on Proposal 6 and plugin-ai).

**Domain impact:** MFFD anomaly detection (EN 9100 audit readiness — turns a graph traversal into a certification document), general researcher productivity (DMP writing automation).

**Cross-finding hook:** Closes the Strategy Advisor's finding that "the provenance story is genuinely ahead of comparators" but "undersold" — the audit narrative is the user-facing articulation of the provenance graph's value. Also directly implements the Research Data Manager's DMP snippet request (§4 and Opportunity 8).

---

## Proposal 8 — Semantic Embedding for DataObject Discovery

**Name:** `semantic-search-embedding`

**Problem it solves:**
The Research Data Manager §2 found that the Predecessor/Successor provenance graph "is FAIR-ready infrastructure that nobody is advertising." The Data Ontologist §3.3 found that `propellant: "LOX/LCH4"` is repeated 15 times as freetext strings — meaning a cross-collection query for "LOX/LCH4 experiments" relies on exact string matching, not semantic equivalence. The Strategy Advisor §2 noted "dataset discovery by new team member: 4–8 h/person without Shepard."

The global header search (UX Auditor Opportunity 1) only hits collections. A researcher who wants "find DataObjects similar to TR-004 vibration anomaly" has no path.

**What it looks like:**
- **Researcher view:** A "Similar experiments" panel on the DataObject detail page, showing the top-5 semantically similar DataObjects across all accessible Collections. Each result shows: name, Collection context, status, a similarity score (displayed as percentage match), and the annotation that drove the similarity. A "Search by description" global search bar below the header autocomplete accepts free-text queries and returns DataObjects ranked by embedding similarity.
- **Technical:** A nightly background job embeds each DataObject's `name + description + attribute values + annotation labels` (concatenated as a short text) using the `shepard-plugin-ai` EMBEDDING capability. Embeddings (384-dim or 1536-dim depending on configured model) are stored in a new `embeddingVector` column (pgvector `vector(1536)`) on a `DataObjectEmbedding` Postgres table (keyed by `dataObjectAppId`). Queries use the HNSW index. `GET /v2/data-objects/{appId}/similar?limit=5` returns ranked results. The nightly job logs a summary `:AiActivity` node. The `collectionFingerprint` from `ImportContextIO` is stored with each embedding batch to track which corpus version was used — enabling reproducibility of "which DataObjects were similar when the search was run."

**Plugin or core?** Core endpoint and Postgres table; EMBEDDING capability via `shepard-plugin-ai`.

**Effort:** L (1 week backend: Postgres migration, nightly job, HNSW index, REST endpoint; 1 week frontend: similar-panel + global search integration; hard dep on plugin-ai EMBEDDING slot).

**Domain impact:** General researcher productivity (cross-collection discovery), MFFD anomaly detection (find prior AFP runs similar to the one that showed delamination).

**Cross-finding hook:** Data Ontologist Gap 5 (material batch buried in JSON, not graph-traversable) and Research Data Manager I3 gap (entity-to-entity links not using PIDs) both limit Shepard's cross-collection discoverability. Semantic embedding is the user-facing bridge while those structural gaps are fixed at the model layer.

**Sequencing note:** Ship at ~200 DataObjects in the system. At 15 (current LUMEN), the feature is a demo, not a product. The MFFD AFP dataset arrival (~2026-05-26) is the trigger to begin implementation.

---

## Proposal 9 — LLM Import Manifest Generator (Agentic Ingest)

**Name:** `manifest-gen-llm`

**Problem it solves:**
The API Scrutinizer §Missing Operations found that `POST /v2/import/jobs` does not exist — the validate endpoint produces a commitId that expires but cannot be redeemed. This means the entire IMP1 surface is currently a dead end. The MFFD AFP team's ingest workflow depends on manifests; writing them by hand is the primary friction point.

Cross-peer hook: The Data Curator (UX Auditor §Persona 2) currently has no path to bulk-import a directory of AFP run files without writing a Python script. The Analytics AI findings §8 ("Import manifest LLM generator — agentic mode") identified the `AgentContextIO` as an underused capability. The Strategy Advisor found "every MCP server request that wants to import data has no executable path" — both the manifest generator and the execute endpoint are needed.

**What it looks like:**
- **Researcher view (agentic):** On the Collection detail page, an "Import data" button opens a panel. The researcher types: "I have 15 AFP layup runs. Each run has a CSV timeseries file, a runlog JSON, and 3 NDT scan images. The timeseries channels are laser_power_W, roller_force_N, tcp_temp_K." The system responds with a preview of the generated import manifest — DataObject names inferred from file names, container types inferred from file extensions, attribute keys inferred from the runlog schema, channel names from the header row of the CSV. The researcher reviews, edits inline, and clicks "Validate and import."
- **Technical phase 1 (non-AI):** Implement `POST /v2/import/jobs` (the missing execute endpoint). This is a prerequisite; it unlocks the validate → execute flow that the API Scrutinizer flagged as CRITICAL. The manifest generator cannot run without it.
- **Technical phase 2 (AI):** `POST /v2/import/generate-manifest` body: `{ "collectionAppId": "...", "description": "...", "files": [{ "name": "AFP_Run_001.csv", "sizeBytes": 1048576 }] }`. Backend fetches `GET /v2/import/context` to get the collection fingerprint and existing vocabulary, constructs a STRUCTURED capability call with the `ImportManifestIO` JSON schema embedded in the prompt, and returns a draft manifest. The draft includes `agentContext.generatedByAiActivityAppId` so the manifest's AI origin is traceable in provenance. The researcher then calls `POST /v2/import/validate` with the draft (or their edited version) and `POST /v2/import/jobs` to execute.
- **TS-ID dependency:** Once TS-IDa/IDb ship, channel references in the generated manifest use `timeseriesAppId` instead of the 5-tuple. The generator should prefer the stable appId form.

**Plugin or core?** Phase 1 (execute endpoint): core, no AI. Phase 2 (manifest generation): core endpoint, STRUCTURED capability via plugin-ai.

**Effort:** Phase 1: S (1 week — implement the missing execute endpoint). Phase 2: M (2 weeks — manifest generator with STRUCTURED capability). Sequential: phase 1 must precede phase 2.

**Domain impact:** MFFD anomaly detection (primary ingest path for AFP runs), MCP/agent workflow (the agentic import scenario from analytics-ai.md §3).

**Cross-finding hook:** Closes the API Scrutinizer's CRITICAL missing operation "Implement POST /v2/import/jobs." Directly implements the "agentic ingest north star for MFFD" from analytics-ai.md §6.

---

## Proposal 10 — FAIR Metadata Completeness Score + Publish Gate

**Name:** `fair-completeness-score`

**Problem it solves:**
The Research Data Manager §2 gave Shepard an overall FAIR composite score of ~1.7/3, with R (Reusability) at 0.9/3. The most actionable sub-gap: no `license` field exists on Collection or DataObject (KIP1e not shipped), creator ORCID is not stamped at creation time, and funder references are absent. The Unhide plugin already emits `schema:license` but has no source entity field — it sends an instance-default fallback, which is architecturally inconsistent.

Cross-peer hook: Strategy Advisor §3 found that "usage license on entity" fails all four funding mandates (Horizon Europe, Clean Aviation JU, DFG, HMC KIP). This is the single field that unblocks compliance with all four simultaneously.

**What it looks like:**
- **Researcher view:** A compact progress ring on the Collection detail page sidebar (or header area): "FAIR score: 72/100." Clicking expands a checklist: green checkmarks for passed criteria, amber/red X marks for gaps, each with an inline "Fix this →" link. The ring is also visible on the Collections list page as a column. Below a configurable threshold (default 60), a "Publish" button tooltip warns: "FAIR score too low to publish — click to see gaps."
- **Technical:** Add `license` (SPDX String, nullable), `accessRights` enum (OPEN/EMBARGOED/RESTRICTED), `embargoEndDate` (ISO-8601 String, nullable), and `fundingReferences` (List<String>, nullable) to `AbstractDataObject` — four additive Neo4j fields, no migration needed for existing nodes (null defaults). Add `createdByOrcid` (String, nullable) to `AbstractDataObject`, stamped from `User.orcid` at entity creation time in the creation service. New `MetadataCompletenessService` implements the scoring rubric (full spec in research-data-manager.md §4). `GET /v2/collections/{appId}/metadata-completeness` returns score + check list. `PublishService.publish()` checks score ≥ configurable threshold before minting PID; returns HTTP 422 with the completeness body on failure.
- **AI assist layer:** The `doc-annotation-suggest` endpoint (Proposal 4) is extended to also populate the new FAIR fields from uploaded documents: license from a rights statement in the PDF, funder from grant acknowledgement text. These are additional STRUCTURED capability extraction targets alongside the attribute and annotation suggestions.

**Plugin or core?** Core (entity model change + scoring service + publish gate). AI assist uses plugin-ai STRUCTURED slot (optional, degrades gracefully).

**Effort:** M (1 week backend: four new fields + Neo4j properties + scoring service + publish gate; 2 days frontend: completeness ring widget + checklist component). Independent of plugin-ai for the core feature.

**Domain impact:** General researcher productivity (DMP compliance), MFFD anomaly detection (Clean Aviation JU data mandate compliance), PLUTO telemetry (DFG research data statement compliance).

**Cross-finding hook:** Directly closes Research Data Manager gaps R1.1, R1.2, A1.2. Implements Research Data Manager §4 Metadata Completeness Score feature spec in full. Also unblocks the Unhide plugin's `schema:license` emission from a real entity field rather than an instance-default fallback.

---

## Proposal 11 — Unhide Plugin: AI-Enhanced Feed Enrichment

**Name:** `unhide-ai-enrichment`

**Problem it solves:**
The `shepard-plugin-unhide` plugin emits a feed consumed by the Helmholtz Knowledge Graph. The Research Data Manager found that `m4i:hasProcessingStep` links and `schema:creator` are emitted, but `schema:license` has no source (KIP1e gap). The Strategy Advisor found the HKG integration is Shepard's strongest external differentiator — but the feed's machine-readable richness is limited by what fields are populated on entities.

This proposal targets the unhide plugin specifically: once `shepard-plugin-ai` ships, the feed generator can use the STRUCTURED capability to auto-generate a `schema:description` field (rich, human-readable summary of each Collection for the HKG harvester) and a `schema:keywords` list (inferred from semantic annotations), increasing the Collection's discoverability in HKG search.

Cross-peer hook: Strategy Advisor §4 found the "adoption chasm" as Risk 1 — external discoverability of Shepard-hosted collections is the mitigation. The HKG feed is the external discovery surface; richer feed entries are the lever.

**What it looks like:**
- **Admin view:** A new toggle in the Unhide plugin admin config: "AI-enhanced feed descriptions" (default OFF). When ON, each feed entry for a Collection includes a generated `schema:description` (max 300 words, generated from name + attributes + semantic annotations via TEXT capability) and `schema:keywords` (list of 5–10 terms from annotation labels, generated via FAST_TEXT capability).
- **Technical:** In `UnhideCollectionFeedBuilder` (or equivalent), after building the standard feed entry, if the toggle is enabled, invoke `llmProvider.complete(AiCapability.FAST_TEXT, ...)` with the collection metadata as trusted context and a fixed prompt: "Generate 5–10 search keywords for this research collection, ordered by specificity. Return as a JSON array of strings." Append the result to the feed entry as `schema:keywords`. The TEXT call for the description runs asynchronously and is cached (keyed by Collection `appId` + `revision`). Cache invalidation on Collection update. The `:AiActivity` provenance node is created for each generated description, ensuring auditability of AI-generated HKG content.

**Plugin or core?** Extends `shepard-plugin-unhide` with an optional dependency on `shepard-plugin-ai`. The plugin declares `@AiCapabilityRequirement(capability = FAST_TEXT, hardDep = false)` and `@AiCapabilityRequirement(capability = TEXT, hardDep = false)` — both soft dependencies so the unhide plugin works without plugin-ai.

**Effort:** M (1 week in the unhide plugin: feed builder extension + caching + toggle config; hard dep on plugin-ai soft capability integration pattern being established first).

**Domain impact:** General researcher productivity (HKG discoverability), MCP/agent workflow (richer feed entries improve external agent discovery of Shepard collections).

**Cross-finding hook:** This is the proposal that specifically improves an existing plugin (unhide) with AI capabilities, as required by the task instructions. Strategy Advisor Recommendation 3 (register in NFDI4ING success stories and apply to HMC Project Call 2026) depends on HKG discoverability — richer feed entries directly support that recommendation.

---

## Proposal 12 — Supervised Anomaly Labelling UI (building the training corpus)

**Name:** `anomaly-labelling-ui`

**Problem it solves:**
The analytics-ai.md §5 (Training Data Inventory) found that TR-004 is the only labelled anomaly in the system. The MAD detector (AI1b) is unsupervised and works well at 15 runs. At 100+ MFFD runs, a supervised detector (threshold-calibrated per channel, anomaly-type-aware) would outperform the MAD detector for subtle drift-type anomalies and cross-channel correlations. The prerequisite for supervised detection is a labelling corpus — and the channel quality score (Proposal 1) is the starting signal.

Cross-peer hook: The UX Auditor §Persona 2 (Data Curator) identified bulk annotation as the highest-friction task in the platform. The Manufacturing Quality Agent found that `shex:QualityFail` and `shex:QualitySuspect` annotations are available in the ontology but never applied to individual time intervals (only to DataObjects). This proposal closes both gaps simultaneously.

**What it looks like:**
- **Researcher/Curator view:** In the `TimeseriesAllChannelsChart.vue` chart, a "Label mode" toggle activates drag-to-select on the time axis. Dragging a region on a channel creates a candidate `TimeseriesAnnotation` with a dropdown: `Normal / Anomaly / Artifact / Uncertain`. Selecting "Anomaly" offers a sub-type dropdown (Spike / Plateau / Drift / Cross-channel correlation) derived from the LUMEN ontology `shex:AnomalyType` concept scheme. The label is saved to the existing `TimeseriesAnnotation` entity with `aiGenerated: false` (human label) and `label: "anomaly"` with a `subtype` attribute. A "Label history" sidebar shows all labels on the current channel, colour-coded by type.
- **Technical:** Reuse the existing `TimeseriesAnnotation` Neo4j entity. Add a `subtype` field (String, optional) and a `labelSource` enum (`HUMAN / AI_GENERATED`). The chart's drag-select fires `POST /v2/timeseries-references/{refAppId}/annotations` with the new fields. The labelling mode is gated behind advanced mode (`v-if="advancedMode"`). A `GET /v2/collections/{appId}/annotation-summary` endpoint aggregates human labels across all timeseries references for export as a training dataset (CSV format: `[dataObjectAppId, channelName, startNs, endNs, label, subtype, confidence]`).
- **Training pipeline hook:** The summary endpoint's CSV output is the training dataset for future supervised models. `collectionFingerprint` from `ImportContextIO` is embedded in the CSV header as a dataset version tag — connecting model training provenance to the exact corpus state.

**Plugin or core?** Core (extends existing `TimeseriesAnnotation`; chart interaction is frontend-only). No plugin-ai dependency.

**Effort:** M (1 week frontend: drag-select + label dropdown in chart + label history sidebar; 2 days backend: `subtype` + `labelSource` fields + annotation summary endpoint).

**Domain impact:** MFFD anomaly detection (builds the labelled corpus for future supervised detection on AFP channels), PLUTO telemetry (labels telemetry anomaly types for mission analysis), general researcher productivity (structured anomaly labelling workflow replaces ad hoc notes).

**Cross-finding hook:** Analytics AI findings §5 ("Missing for supervised learning: TR-004 is the only labelled anomaly") explicitly called for injected anomalies across 5+ channels. This proposal provides the in-app labelling UI that lets researchers label real anomalies as they discover them, building the corpus organically rather than requiring synthetic injection. Manufacturing Quality Agent §2 EN 9100 §9.1.1 (monitoring and evaluation) is directly addressed — labelled anomaly intervals become the auditable evidence base for process monitoring.

---

## Sequencing Summary

Dependencies flow in this order:

```
1. channel-quality-surface (S, zero deps)
2. anomaly-notify (S, dep: NTF1)
3. provenance-gap-detector (M, zero AI deps)
4. ancestor-chain-api (M, zero AI deps — unblocks proposals 7, 9)
5. manifest-gen-llm phase 1 — execute endpoint (S, zero AI deps, CRITICAL)
6. numeric-annotation-field — model change only (M, zero AI deps)
7. fair-completeness-score — core fields only (M, zero AI deps)

[plugin-ai foundation must ship — estimated 2–3 sprints]

8. doc-annotation-suggest (M, dep: plugin-ai STRUCTURED)
9. anomaly-labelling-ui (M, zero AI deps — can run in parallel with 8)
10. numeric-annotation-field — AI assist layer (S add-on, dep: plugin-ai STRUCTURED)
11. fair-completeness-score — AI assist layer (S add-on, dep: plugin-ai STRUCTURED)
12. manifest-gen-llm phase 2 — LLM generator (M, dep: plugin-ai STRUCTURED + ancestor-chain-api)
13. audit-narrative-gen (M, dep: plugin-ai TEXT + ancestor-chain-api)
14. unhide-ai-enrichment (M, dep: plugin-ai TEXT + FAST_TEXT, extends unhide plugin)
15. semantic-search-embedding (L, dep: plugin-ai EMBEDDING, trigger: ~200 DataObjects in system)
```

The first seven proposals deliver concrete value with zero dependency on `shepard-plugin-ai`. They close EN 9100 gaps, surface existing backend features, and build the structural foundations that the AI-dependent proposals need. The AI-dependent proposals (8–15) form a second wave that lands after the plugin foundation is established.
