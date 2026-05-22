---
stage: decommissioned
last-stage-change: 2026-05-23
---

# UX Auditor — Feature Proposals

**Author:** Core Tech & UX Auditor (synthesis from all peer agent findings)
**Date:** 2026-05-21
**Scope:** 10 concrete feature proposals, synthesized across UX audit + all six peer findings

---

## P1: Bulk Row-Selection + Action Toolbar in CollectionDataObjectsPanel

**Problem it solves.** A data curator annotating 50 AFP channel DataObjects with `unit=g_rms` currently opens the annotation dialog 50 times — roughly 350 clicks. Status-setting for 15 test runs requires 75 interactions. There is no row selection anywhere in the DataObjects table. The client-side status filter (ux-auditor §Curator, line 156–199) silently under-reports matching rows across pages because the filter is not passed to the backend.

**What it looks like.** A checkbox column appears at the left of the DataObjects table. Checking any row reveals a sticky action toolbar above the table with four actions: "Set status" (opens a single-select of all status values), "Add annotation" (opens AddAnnotationDialog with `targets: DataObjectRef[]`, applying the annotation to all selected items), "Export selected" (RO-Crate scoped to the selection), and "Clear selection." When the status filter is active, a dismissable chip reads "Filter applies to visible page — click to search all pages" and clicking switches to server-side status filtering via an updated `usePagedDataObjects` composable call. In advanced mode the toolbar gains "Bulk-edit attributes" (set one key-value pair across all selected).

**Plugin or core?** Core — this is cross-cutting table infrastructure touching an existing core primitive (DataObject CRUD), not a new payload kind.

**Effort estimate.** M — frontend checkboxes + toolbar are a week; the backend needs one new query param (`?status=`) on the DataObject list endpoint and a batch-annotation endpoint (or N sequential annotation POSTs capped at 50). Basic mode shows the toolbar; advanced mode adds "Bulk-edit attributes."

**Domain impact.** General researcher (primary), MFFD manufacturing curator (secondary — 50-channel AFP tagging is the motivating use case).

**Cross-finding hook.** Manufacturing-quality §8 requires bulk status transitions in the NCR workflow (NCR_OPEN → NCR_DISPOSITIONED across a set of affected DataObjects). This toolbar is the UX vehicle for that transition without a dedicated NCR plugin. RDM §4 notes that the Metadata Completeness Score will need per-DataObject inline-fix affordances — the toolbar "Set status" + "Add annotation" are the two most common fix actions the score would recommend. Analytics-ai §3 quick-win (PDF auto-annotation) surfaces suggestions per DataObject; bulk-accept of suggestions across a selection is the natural follow-on.

---

## P2: HDF5 Container Browser (UI for a shipped backend)

**Problem it solves.** HDF5 containers (A5a + A5b + A5d) are fully shipped on the backend — create, read, delete, download, permission bridge — and have been for months. The feature matrix marks the row `⚙ BE ✓ / UI pending` and the strategy advisor (§2) calls this the most dangerous visible gap. A researcher who needs HDF5 cannot use it from the web UI at all. The `plugins/hdf5/docs/reference.md` documents the backend surface but there is no quickstart or install doc.

**What it looks like.** A new `HdfContainerPane.vue` component (parallel to `FileContainerPane.vue`) rendered on the DataObject detail page when the DataObject has at least one HDF5 reference. The pane shows: (a) a dataset tree — folders and leaf datasets rendered in a `v-treeview`, expandable on demand; (b) a dataset preview panel — for scalar and 1D array datasets: show value(s) inline; for 2D array datasets: show a `v-data-table` with the first 100 rows and a "Download full dataset" button; for 3D+ arrays: show shape metadata and download only. (c) a metadata sidebar showing the HDF5 group/dataset attributes as a key-value list with annotation affordance (matching the pattern in `AnnotatableTimeseries`). The create flow adds "HDF5 Container" to the "Add container" dropdown on the DataObject page. In basic mode: show the dataset tree and download. In advanced mode: add the attribute metadata sidebar and the raw HSDS URL link.

**Plugin or core?** Plugin (`plugins/hdf5/`) — the backend is already plugin-extracted (PL1c). The Vue components should live in the plugin's frontend bundle once the plugin SPI supports UI contribution (currently it doesn't — so interim: the components live in `frontend/components/container/hdf5/` and are gated by `shepard.plugins.hdf5.enabled`). Add `quickstart.md` and `install.md` to `plugins/hdf5/docs/` in the same PR.

**Effort estimate.** L — dataset tree + preview table + attribute sidebar + create dialog is three to four weeks of frontend. The backend fetch path exists; the composable layer is the new work.

**Domain impact.** General researcher (primary), PLUTO satellite data (HDF5 is the standard format for satellite telemetry — CCSDS, ESA Level-2 products). MFFD simulation outputs (FEM results often delivered as HDF5).

**Cross-finding hook.** Strategy advisor risk 2 explicitly names HDF5 as one of three "most-visible backend-only features" to close before the next external presentation. Analytics-ai §5 training data inventory notes that MFFD AFP data arriving ~2026-05-26 may include HDF5-format simulation results; the browser would let researchers see them immediately.

---

## P3: Annotation Suggestion Drawer (UX for the PDF Auto-Annotation Quick Win)

**Problem it solves.** Analytics-ai §3 specifies a PDF auto-annotation endpoint (`POST /v2/data-objects/{appId}/suggest-annotations`) that returns suggested attribute key-value pairs and ontology annotation candidates with confidence scores. The backend quick-win (1 sprint once `shepard-plugin-ai` ships) needs a UX vehicle. Without a well-designed frontend surface, users will never discover the feature, and the suggestions will pile up unreviewed.

**What it looks like.** After a user uploads any PDF or Markdown file to a FileBundle attached to a DataObject, a dismissable yellow banner appears at the top of the DataObject detail page: "AI found 4 metadata suggestions from your uploaded report — Review." Clicking opens a right-side drawer (not a blocking modal — the user can still see the DataObject behind it). The drawer shows two sections:

*Attribute suggestions:* A `v-list` with each suggested key-value pair displayed as a chip with the key and value, a confidence bar (coloured green >0.8, amber 0.5–0.8, red <0.5), and an accept/reject toggle. Accepted suggestions are staged locally.

*Annotation suggestions:* A similar list with property label + value label from the ontology, the IRI pair shown in monospace when the user expands the row, confidence bar, and accept/reject toggle. "Open in Add Annotation Dialog" pre-fills the fields.

"Apply accepted" at the bottom of the drawer fires the existing attribute-update and annotation-create endpoints for the accepted items only. The `aiActivityAppId` from the suggestion response is written as a provenance trail (per analytics-ai §3 design). A "Don't suggest again for this file" link dismisses and stores a preference.

In basic mode: drawer appears automatically after upload, with simple accept/reject toggles and no IRI details. In advanced mode: IRI monospace, confidence float value, "Explain this suggestion" tooltip (calls a second AI endpoint if configured).

**Plugin or core?** Core frontend surface (the drawer), gated behind `shepard-plugin-ai` being configured. The backend endpoint lives in core per the quick-win spec. The drawer visibility is `v-if="aiPluginAvailable && hasSuggestions"`.

**Effort estimate.** M — the drawer itself is a week of frontend. Gated on `shepard-plugin-ai` Phase 0 (2–3 sprints of backend work per analytics-ai §3). The frontend can be built against a stub endpoint today and wired when the backend ships.

**Domain impact.** General researcher (primary). MFFD manufacturing (secondary — PDF test reports from AFP robot runs map directly to this use case per analytics-ai §7 real-world impact).

**Cross-finding hook.** This proposal is the direct UX delivery for analytics-ai §3 quick-win spec. Data-ontologist §6 Idea B ("channel unit annotation as mandatory field at channel creation") and §6 Idea D ("cross-ConceptScheme search sidebar") share the AddAnnotationDialog surface — the suggestion drawer can pre-populate the existing annotation picker rather than duplicating UI. The drawer's "Apply accepted" path is the same bulk-apply target as P1's bulk toolbar.

---

## P4: Metadata Completeness Ring on Collection Sidebar

**Problem it solves.** The RDM agent (§4) specifies a `MetadataCompletenessScore` (0–100) computed from: name/description presence, license SPDX field, accessRights enum, creator ORCID stamp, funder reference, PID minted, and at least one semantic annotation. This score is the DMP compliance signal funding bodies ask for — and it is entirely absent from the current UI. A researcher submitting a Horizon Europe data management plan has no machine-readable indicator of whether their Shepard Collection meets the minimum bar.

**What it looks like.** On the Collection detail page sidebar, below the existing metadata panel, a compact circular progress ring (64px diameter) shows the score as a percentage, colour-coded: red <50, amber 50–79, green ≥80. Below the ring, two lines: "FAIR Score: 72/100" and "3 checks failing." Clicking the ring or "3 checks failing" expands an inline checklist with each check shown as a row: green checkmark or red ×, the check label (e.g., "Usage license (SPDX)"), and an inline action link ("Add license →" opens the Collection edit dialog with the license field focused; "Add ORCID to your profile →" opens `/me` in a new tab). In advanced mode the checklist shows the raw point values and the field paths. A `GET /v2/collections/{appId}/metadata-completeness` endpoint backs it (per RDM §4 wire shape spec). In basic mode only the ring and the failing-check count are shown — clicking reveals the checklist with simplified labels.

**Plugin or core?** Core — the score is computed from core entity fields (license, orcid, accessRights) and the PID publication table. It is not a plugin payload kind.

**Effort estimate.** M — the endpoint itself is one service + one REST resource (backend, 1 week); the ring + checklist is 1 week of frontend. Blocked on the `license`, `accessRights`, and `createdByOrcid` fields being added to `AbstractDataObject` (the lowest-friction FAIR gap per RDM §8, each a one-line additive Neo4j field + IO update). Those field additions are S/days each. The ring can ship as a placeholder (returning a stub score) while the field additions land in parallel.

**Domain impact.** General researcher (primary — every Horizon Europe and DFG-funded collection benefits), PLUTO satellite (FAIR mandate per Welzmüller et al. and the associated DLR eLib publication).

**Cross-finding hook.** This is the primary UX delivery for RDM §4 (DMP compliance feature spec). Strategy advisor §2 notes that the Unhide feed emits `schema:license` from a field that doesn't exist yet — the ring's "Add license" action is the fix path. The RDM agent's §8 near-term opportunity 1 (add `license` field to `AbstractDataObject`) is the prerequisite; the ring is the visibility surface that makes the field's absence *felt* rather than invisible.

---

## P5: Ancestor-Chain Traversal Panel ("Trace upstream" provenance)

**Problem it solves.** The UX audit §Auditor persona found that: (1) the DataObjectProvGraph is truncated at 6 predecessors (slice(0,6) at lines 86, 106), (2) there is no recursive predecessor walk UI, and (3) neither the force-directed graph nor the structured log answers "what was this derived from." The manufacturing-quality agent (§2) requires end-to-end lineage from raw material to finished part for EN 9100 §7.8.2 traceability. A 4-hop audit trace currently requires 4 × navigate + 4 × expand Provenance.

**What it looks like.** A new "Ancestors" tab on the DataObject detail page (alongside "Provenance," "Containers," "Lab Journal"). The tab renders a vertical timeline: the current DataObject at the bottom, its direct predecessors above it, their predecessors above those, up to a configurable depth (default 6, max 20, controlled by a `?depth=N` query param on the backend). Each card in the timeline shows: name, status chip (colour-coded), creation date, and a one-line summary of attributes. Clicking any ancestor card navigates to it (the timeline rebuilds around the new DataObject). A "Download as lineage CSV" button exports the chain as `name, appId, status, date, predecessor_appId` — usable for a DIN EN 9100 audit package without post-processing. The backend endpoint is `GET /v2/data-objects/{appId}/ancestor-chain?depth=N` returning an ordered list of `DataObjectSummaryIO` objects. In basic mode: timeline is show-only, no depth control. In advanced mode: depth slider, "Show semantic annotations" toggle (adds annotation chips to each card), and "Export as lineage CSV."

**Plugin or core?** Core — predecessor traversal is fundamental to the DataObject graph model. The new endpoint is a thin Cypher traversal on top of existing Neo4j relationships.

**Effort estimate.** M — the backend endpoint is a Cypher `MATCH (d:DataObject)-[:HAS_PREDECESSOR*1..N]->(ancestor)` with depth parameter and permission gate (1 week backend + tests). The timeline Vue component is 1 week frontend. Total 2 weeks.

**Domain impact.** MFFD manufacturing (primary — EN 9100 requires this chain; the calibration traceability from manufacturing-quality §6 depends on it), PLUTO satellite (command → response causal chain), general researcher (provenance navigation).

**Cross-finding hook.** Manufacturing-quality §2 identifies "no UI-level recursive predecessor walk" as a CRITICAL gap for EN 9100 §7.8.2 traceability. Data-ontologist §3 (Opportunity 2 — material batch as first-class graph node) proposes that lot IDs become Predecessor-linked DataObjects; the ancestor chain timeline is the UI that makes that graph traversable. API-scrutinizer §Missing Operations flags "no flat DataObject GET by appId" — the ancestor-chain endpoint motivates adding a `GET /v2/data-objects/{appId}` flat endpoint as a prerequisite (the endpoint needs to look up the DataObject by appId without requiring the caller to know the Collection).

---

## P6: Side-by-Side Timeseries Comparison View

**Problem it solves.** A researcher comparing TR-004 (vibration anomaly) against TR-003 (baseline) must open two browser tabs, mentally align timestamps, and reconcile axis scales. No shared time-axis zoom exists. The strategy advisor (§4 ROI) estimates anomaly identification takes 2–4 hours per run with manual scanning. The LUMEN showcase story (TR-004 turbopump vibration spike at t=8s) is the platform's canonical demo — it is not currently demonstrable as a single-view experience.

**What it looks like.** On the DataObject detail page, a "Compare" button appears in the header row when at least one TimeseriesReference exists. Clicking opens a comparison picker: a `v-autocomplete` that searches for other DataObjects in the same Collection (scoped to DataObjects that have at least one TimeseriesReference). Selecting a second DataObject opens a split-panel view: the left panel shows the current DataObject's curated channel chart; the right panel shows the selected DataObject's chart. Both charts share a time axis (the x-axis is aligned to wall-clock time using the `wallClockOffset` from the TM1a model where set, otherwise experiment-relative from t=0). A "Lock axes" toggle keeps both charts at the same x-zoom and y-scale. A "Channel overlay" mode collapses the two panels into one, rendering each channel from DataObject A and the matching-named channel from DataObject B as solid vs. dashed lines on the same axis. The URL becomes `/collections/{cId}/compare?a={doAppId}&b={doAppId}` — shareable. In basic mode: split-panel only, no overlay. In advanced mode: add overlay toggle, channel subset picker (pick which channels appear in both panels), and "Export comparison as CSV" (two columns per channel, one per DataObject, time-aligned).

**Plugin or core?** Core — comparison is a viewer over existing TimeseriesReference data, not a new payload kind.

**Effort estimate.** L — URL routing + comparison picker + split-panel layout + shared-axis synchronization is three to four weeks. The `TimeseriesChart` component already accepts an array of series; the multi-DataObject fetch composable is the new piece. TM1a's `wallClockOffset` field must be surfaced in the UI (currently `⚙ BE ✓ / UI pending`) to make wall-clock alignment work — that is a prerequisite S/days task.

**Domain impact.** General researcher (primary — the canonical use case is any two-run comparison), MFFD manufacturing (secondary — comparing a re-test run against the failed run), LUMEN demo (the TR-004 vs TR-003 story is the platform's funding-pitch demo per strategy advisor §1).

**Cross-finding hook.** Strategy advisor §1 states that the "inline timeseries charting" vision claim is the one accurate one — this feature extends it to the cross-DataObject comparison the vision implies but doesn't deliver. Analytics-ai §8 (anomaly digest on collection) notes that engineers need to understand anomalies in context of normal runs; the comparison view is the manual version of what the anomaly digest automates.

---

## P7: Shop-Floor Quick-Action Bar (NCR / Hold, Process Position, Status)

**Problem it solves.** The manufacturing-quality agent (§7) specifies eight shop-floor UI requirements missing from the current interface. The three highest-friction ones are: (a) no large-target status button (current `v-select` is 32px; shop floor needs 80px minimum), (b) no "Raise NCR / Place Hold" shortcut (currently requires navigating to child DataObject creation with multiple fields — too slow for a gloved operator who spotted a defect), and (c) no process-chain position indicator (the operator needs to know "where is this component in the process chain right now"). The current UI is designed for researchers at a desk, not IMEs on a 27" ruggedized touchscreen.

**What it looks like.** On the DataObject detail page, below the title and status, a new "Quick Actions" bar renders when the user has Write permission. In basic mode (the mode that shop-floor operators would use) the bar is always visible and contains three large-target elements:

1. **Status advance button** (full-width, 80px height, colour-coded to current status): shows the current status and a single "Advance to [next status]" CTA. The "next status" is determined by a configurable status-machine definition in the ShepardTemplate if one is linked, otherwise defaults to the standard progression (DRAFT → IN_REVIEW → READY → PUBLISHED). A secondary "Set status…" link opens the full status picker for non-linear transitions.

2. **"Raise NCR / Place Hold" button** (large red, 72px height): opens a two-field form — a type selector (NCR / Hold / Rework) and a mandatory short description. Submitting creates a child DataObject using a built-in NCR template with `status: NCR_OPEN`, the description in `attributes.description`, and the current DataObject as parent. A `shex:QualityFail` annotation is automatically applied to the child. This is the no-code NCR creation path — no navigation to the child creation page required.

3. **Process-chain stepper** (horizontal linear stepper, 48px height): reads the DataObject's Predecessor chain (up to 10 hops, cached) and renders the chain as a linear sequence of named boxes. The current DataObject is highlighted. Each box shows the status colour. This gives the IME "AFP → Weld → NDT → ..." with the current position visible at a glance.

In advanced mode the bar gains a "QR code" button (generates a printable QR pointing to the DataObject's `/v2/` URL) and a "Calibration status" chip that reads from a linked Equipment DataObject (if the convention from manufacturing-quality §6 is followed — a `USED_EQUIPMENT` attribute key on the DataObject).

**Plugin or core?** Core frontend — these are UX wrappers over existing status PATCH, child DataObject creation, and ancestor-chain fetching (P5's endpoint). The NCR template is a ShepardTemplate definition (T1 series) that the operator creates once per project.

**Effort estimate.** L — the status button is S/days; the NCR quick-raise form + auto-template is M (needs the NCR template to exist and the `status: NCR_OPEN` value to be added to the enum); the process-chain stepper reuses P5's ancestor-chain endpoint (P5 is a prerequisite). Total with P5 as a prerequisite: 2–3 weeks of new frontend work.

**Domain impact.** MFFD manufacturing (primary — the shop-floor IME persona is the motivating scenario), general researcher (secondary — the status advance button is useful for any curation workflow).

**Cross-finding hook.** This is the direct delivery for manufacturing-quality §7 shop-floor UI requirements. Manufacturing-quality §5 proposes adding `NCR_OPEN`, `ON_HOLD`, `REWORK`, `REJECTED`, `CERTIFIED` status values — the "Raise NCR" quick button is the UX for creating a child DataObject with `NCR_OPEN` status without requiring the operator to know the status vocabulary. The data-ontologist (§6, Idea C) proposes an Equipment DataObject template; the "Calibration status" chip in advanced mode reads from that template pattern, creating a UX incentive for operators to adopt the Equipment convention.

---

## P8: Global Entity Search (DataObjects, Channels, Containers)

**Problem it solves.** The global header search (`HeaderBar.vue:39`) hits collections only. A researcher who knows the DataObject name "AFP_Run_Layer_047" must browse into the right collection, expand the tree, or use per-collection search — all slower paths. The UX audit §Researcher persona measures this as 15s + 5 clicks vs. 2s + 1 action. This is the highest-frequency entry point for experienced users.

**What it looks like.** The existing `v-autocomplete` in the header expands its scope. The dropdown now shows results in three sections: "Collections" (existing), "DataObjects" (new — name match, shows collection name as subtitle), "Timeseries channels" (new — channel `symbolicName` match, shows measurement + container name as subtitle). Selecting a DataObject result navigates to `/collections/{cId}/dataobjects/{doAppId}`. Selecting a channel result navigates to `/containers/timeseries/{containerId}` with the channel name pre-highlighted in the measurements table. The search is debounced at 300ms. Results are limited to 5 per category. A "Search all →" footer link navigates to `/search` with the query pre-populated. In basic mode: Collections + DataObjects only. In advanced mode: adds Channels and a "Containers" section.

**Plugin or core?** Core — this extends the existing header composable (`useCollectionSearch`). The backend needs either a composite `/v2/search/global?q=` endpoint or parallel requests. The planned P7 search unification (`aidocs/13`) is the structural fix; this feature can land as a parallel-request implementation in the meantime.

**Effort estimate.** M — frontend composable extension is S/days; the backend needs a new DataObject name-search endpoint (the per-collection list already accepts `?name=` — a collection-agnostic variant is a new service method + REST resource, 1 week backend). Channel search requires the Timeseries name-search endpoint (another week). Total: 2–3 weeks.

**Domain impact.** General researcher (primary — all personas benefit from direct jump-to). No domain-specific angle; this is pure workflow acceleration.

**Cross-finding hook.** API-scrutinizer §Missing Operations flags "no flat DataObject GET by appId without knowing the Collection appId" as MAJOR — the global search endpoint creates the query path that enables this. Strategy advisor §1 notes the sidebar tree and main panel DataObjects table are "two parallel navigations of the same data" with different completeness guarantees — the global search supersedes both for jump-to navigation, reducing the dual-surface confusion.

---

## P9: Channel Unit Picker at Channel Creation (Annotation Pre-fill)

**Problem it solves.** The data-ontologist (§1.4 and Idea B) found that the LUMEN seed's 25 timeseries channels have known, well-defined units (bar, K, g_rms, m/s²) but these units are never stored in Neo4j — they exist only in Python seed documentation. QUDT and OM-2 are both seeded in the internal semantic repository. The `AnnotatableTimeseries` bridge exists and supports semantic annotation on individual channels. The unit information simply has no UI path to get into the system at the moment a channel is created. Without units, physical quantities are not machine-readable and range queries ("find all test runs with target thrust > 20 kN") break.

**What it looks like.** In the channel creation form (wherever a new `Timeseries` entry is added — the `AddChannelDialog` or the measurement table row creation flow), a new optional "Unit" field appears as a QUDT-backed `v-combobox`. The user types "bar" or "Newton" and sees matching QUDT terms from the internal n10s repo via the existing `GET /v2/semantic/terms/search?q=` endpoint (the same endpoint the `AddAnnotationDialog` IRI autocomplete uses). Selecting a term automatically creates an `AnnotatableTimeseries` annotation with `propertyIRI = qudt:hasUnit` and `valueIRI = [selected QUDT IRI]`. The selected term label is stored as the display value. In the timeseries measurements table, if a channel has a unit annotation, its column header shows the unit abbreviation in parentheses: "compaction_force (N)". In basic mode: the unit field is shown with a simple "what unit?" placeholder and the QUDT autocomplete. In advanced mode: additionally exposes the raw IRI field (free-text override for non-QUDT vocabularies like OM-2).

**Plugin or core?** Core — this is an annotation pre-fill pattern on an existing core entity (Timeseries). No new payload kind.

**Effort estimate.** S — the `AddAnnotationDialog` already has the QUDT autocomplete infrastructure (N1e). The unit field in the channel form is a thin wrapper: the combobox + the auto-create annotation call on submit. Backend: no new endpoint needed. Frontend: 2–3 days.

**Domain impact.** MFFD manufacturing (primary — AFP sensor channels without units are FAIR-non-compliant and fail EN 9100 measurement traceability), general researcher (secondary — any quantitative experiment benefits), PLUTO (telemetry channels need units for interoperability with CCSDS standards).

**Cross-finding hook.** This is the direct UX delivery for data-ontologist §6 Idea B. RDM §2 I2 gap ("no bridge between `attributes` map and ontology terms") names unit annotation as the highest-frequency missing bridge — this proposal creates the bridge at the most natural moment (channel creation). Analytics-ai §5 training data inventory notes that channel metadata without units degrades ML pipeline quality; adding units at creation time directly improves the training data. The data-ontologist §4.3 (QUDT vs OM-2 house rule) recommends QUDT as primary — this proposal enforces that by defaulting to the QUDT combobox.

---

## P10: Video Plugin — Multi-Track Annotation Editor with Scrub Preview

**Problem it solves.** The video plugin (VID1b-annotation) shipped a 16px colour-coded timeline bar below the video player showing annotation intervals on hover. This is a display surface, not an editing surface. Creating or editing a video annotation requires opening the raw API or navigating to a separate dialog. For MFFD use cases (annotating phases of an AFP robot run video — "fiber placement," "roller pass," "defect visible at t=47s"), an operator needs to be able to mark intervals directly by scrubbing the video, not by entering start/end seconds manually. Additionally, the `plugins/video/docs/` directory has only `install.md` and `reference.md` — the `quickstart.md` required by CLAUDE.md is missing.

**What it looks like.** The `VideoStreamReferencesPane.vue` gains a new "Edit annotations" mode toggle in the player header. When active:

1. The timeline bar below the player becomes interactive: the user can click-drag to create a new annotation interval (the bar cursor changes to a crosshair; dragging creates a translucent selection). Releasing opens a micro-form inline: label (text field, autocomplete from existing labels on this video), description (optional), confidence (0.0–1.0 slider, defaults to 1.0). Submitting POSTs to the existing annotation endpoint.

2. Existing annotation intervals become clickable: clicking an interval selects it (shows a "drag handles" on each end, a small edit pencil icon, and a trash icon). Dragging a handle PATCHes the `startSeconds`/`endSeconds`. The trash icon DELETEs.

3. A scrub thumbnail strip appears below the timeline bar (10 thumbnail frames evenly spaced across the video duration, statically rendered from the video element via Canvas API at mount time). This lets the operator visually locate frames without playing the video.

4. The annotation list panel (currently a separate expansion panel) gains an inline row-click that seeks the player to that annotation's start time.

As a separate deliverable in the same PR: add `plugins/video/docs/quickstart.md` (covering: add a video reference, play it, add a phase annotation, export the DataObject with annotations in the RO-Crate) — this is the missing CLAUDE.md-required doc.

**Plugin or core?** Plugin (`plugins/video/`) — all changes are to the plugin's frontend bundle and its docs.

**Effort estimate.** M — the Canvas-API thumbnail strip is 2 days; the click-drag interval creation with inline form is a week; the drag handles for editing are another 3–4 days. The quickstart doc is S/hours. Backend: no new endpoints needed (existing annotation CRUD handles everything). Total: 2 weeks.

**Domain impact.** General researcher (primary — any video-annotated experiment benefits), MFFD manufacturing (secondary — AFP run video with phase annotations maps directly to the process-step DataObject structure), LUMEN (TR-004 thermal images and test-run video analysis).

**Cross-finding hook.** Plugin improvement is a task requirement; this directly improves an existing shipped plugin. The missing `quickstart.md` is a CLAUDE.md compliance issue that this proposal fixes in the same PR. The scrub-annotation UX pattern (mark a time interval → create a structured annotation) is the same interaction model proposed in P3 for document suggestions — establishing a consistent "mark → annotate → confirm" UX idiom across time-bound and document-bound media.

---

## Summary Priority Matrix

| # | Proposal | Effort | Domain | Blocking dependency |
|---|---|---|---|---|
| P1 | Bulk row selection + action toolbar | M | General / MFFD | Backend `?status=` param on DataObject list |
| P2 | HDF5 container browser | L | General / PLUTO / MFFD sim | None — BE fully shipped |
| P3 | Annotation suggestion drawer | M | General / MFFD | `shepard-plugin-ai` Phase 0 |
| P4 | Metadata completeness ring | M | General / PLUTO FAIR | `license`, `accessRights`, `createdByOrcid` fields on `AbstractDataObject` |
| P5 | Ancestor-chain traversal panel | M | MFFD / PLUTO / General | New `/v2/data-objects/{appId}/ancestor-chain` endpoint |
| P6 | Side-by-side timeseries comparison | L | General / MFFD / LUMEN demo | TM1a UI (wall-clock offset display, days) |
| P7 | Shop-floor quick-action bar | L | MFFD manufacturing | P5 ancestor-chain endpoint; NCR_OPEN status value |
| P8 | Global entity search | M | General | Backend DataObject name-search (collection-agnostic) |
| P9 | Channel unit picker at creation | S | MFFD / General / PLUTO | None — infrastructure exists |
| P10 | Video multi-track annotation editor + quickstart.md | M | General / MFFD | None — BE annotation CRUD exists |

**Top 3 by effort × user value (highest leverage first):**

1. **P9 — Channel unit picker** (S effort, MFFD + FAIR compliance, no dependencies, closes the single most-cited ontology gap)
2. **P1 — Bulk row selection** (M effort, eliminates the curator's 350-click workflow, unblocks NCR bulk transitions)
3. **P4 — Metadata completeness ring** (M effort, directly addresses DFG/Horizon Europe funding mandate gap, makes the platform's FAIR claims visible and actionable)
