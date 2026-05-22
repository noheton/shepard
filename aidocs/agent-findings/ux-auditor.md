---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# UX Auditor — Discovery Report

**Auditor:** UX/Performance agent  
**Date:** 2026-05-21  
**Scope:** Full frontend codebase audit  
**Codebase snapshot:** `main` branch as of 2026-05-21

---

## What I found

The Shepard frontend is a Nuxt 3 + Vuetify 3 application organised around five core route families:

- `/` — PersonalDigest (authenticated) or LandingPage
- `/collections/[collectionId]` — Collection detail with `collection` layout
- `/collections/[collectionId]/dataobjects/[dataObjectId]` — DataObject detail
- `/containers/timeseries|files|structureddata|spatialdata/[containerId]` — Container views
- `/search` — Advanced search (JSON DSL)
- `/me`, `/admin`, `/help`, `/about` — Supplementary pages

The layout system uses a fixed left sidebar (`CollectionSidebar.vue`) alongside a scrollable main panel. Navigation inside a collection is via lazy-loading treeview. Navigation between entity types (Collection → Container) requires breaking out of the collection layout entirely.

The most recently shipped features (timeseries charting, lineage graph, provenance graph, personal digest, curated channel view) are solid individually. The audit below surfaces where they interact poorly, where scale breaks them, and where each of the four personas runs into friction walls.

---

## UX Audit & Friction Matrix

### Persona 1: Interdisciplinary Researcher

**Goal:** Correlate sensor channels across two test runs. Find TR-004's anomaly vs TR-003's baseline.

| Component | File | Line | Finding | Severity |
|---|---|---|---|---|
| Global search | `components/layout/HeaderBar.vue` | 39–71 | Search hits **collections only**. A researcher typing "TR-004" gets no result unless they already know which collection contains it. No DataObject, no channel, no container search in the header. | HIGH |
| Collection Lineage Graph | `components/context/collection/CollectionLineageGraph.vue` | 16 | Calls `useFetchAllDataObjects` with no cap. For a collection with 500+ process-step DataObjects (MFFD production scale), this fetches all of them into memory, then pushes every node to ECharts force layout simultaneously. No clustering, no semantic zoom, no maximum. Observable lag starts around 200 nodes. | HIGH |
| DataObject ProvGraph | `components/context/data-object/DataObjectProvGraph.vue` | 27 | Also calls `useFetchAllDataObjects` independently — the same full collection fetch fires again when navigating to a DataObject detail page. Two O(N) fetches for a single page view. | MEDIUM |
| Provenance scope | `components/context/data-object/DataObjectProvGraph.vue` | 64–205 | The graph shows **agents (users) who acted**, not the process-chain ancestry. A researcher asking "what raw AFP run produced TR-004?" needs predecessor → predecessor → predecessor traversal. The current graph is a who-did-what, not a what-was-made-from-what view. Compliance Auditor persona is equally affected. | HIGH |
| Timeseries side-by-side | No component | — | There is no split-screen or overlay view for comparing two DataObject timeseries references. A researcher comparing TR-004 vibration with TR-003 baseline must open two browser tabs, mentally align timestamps, and reconcile axis scales. No shared time-axis zoom. | HIGH |
| Channel chart persist | `components/container/timeseries/TimeseriesAllChannelsChart.vue` | 11–29 | `MAX_CHANNELS = 8` is a hard cap for the non-curated view. A container with 200 channels (typical AFP DAQ) silently drops 192. The chip at line 353 mentions the truncation but there is no click-to-expand-all shortcut in the chart toolbar; the user must go to the "Edit channels" flow instead. | MEDIUM |

**Researcher friction verdict:** The missing cross-DataObject timeseries overlay is the single sharpest pain point. The lineage graph is the best existing provenance surface, but it breaks at MFFD scale and conflates structural BOM (parent/child) with process-chain (predecessor/successor) in a single undifferentiated force layout.

---

### Persona 2: Data Curator

**Goal:** Bulk-annotate 50 AFP channel entities with `unit=g_rms` after a recording session. Set status READY on all 15 LUMEN test runs.

| Component | File | Line | Finding | Severity |
|---|---|---|---|---|
| Annotation dialog | `components/context/semantic/annotation/add-dialog/AddAnnotationDialog.vue` | 131–150 | One annotation per dialog open. No multi-target, no bulk-apply. Tagging 50 channels: 50 dialog opens × 7 clicks each = ~350 interactions. No batch primitive anywhere in the codebase. | CRITICAL |
| CollectionDataObjectsPanel status filter | `components/context/collection/CollectionDataObjectsPanel.vue` | 156–157 | Status filter is client-side only (applied to the current page, line 196–199). A curator filtering for all IN_REVIEW objects in a 500-item collection only sees the current 25-item page filtered, not all matching rows across pages. The server-side `name` filter exists but the status filter does not pass through to the backend. | HIGH |
| DataObject selection | `components/context/collection/CollectionDataObjectsPanel.vue` | 35–120 | The DataObjects table has no row selection (no checkboxes). Every bulk action — change status, add annotation, relate predecessor — requires opening each DataObject individually. | HIGH |
| Sidebar tree filter | `components/context/sidebar/CollectionSidebar.vue` | 30–91 | Tree filter is **client-side, loaded-nodes-only** (the comment at line 35 admits this). A curator typing "TR-004" in a collection where the "Test Runs" group has never been expanded gets zero results. Silent failure: no indication that unexpanded children exist but were not searched. | HIGH |
| Attribute batch edit | `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | 200–220 | Attributes must be edited one DataObject at a time via the Edit dialog. No bulk-set across selected rows. | MEDIUM |

**Curator friction verdict:** The absence of row selection and bulk annotation is the defining gap. A curator tagging a 50-channel container with a shared unit is doing repetitive dialog-open-type-save work 50 times. This single gap pushes power users to the Python client, which is a correct fallback but undermines the "casual user first" promise.

---

### Persona 3: Compliance Auditor

**Goal:** A defect surfaces during NDT inspection of the MFFD upper shell. Trace which AFP robot run, which operator, which timestamp, which material batch produced the affected ply.

| Component | File | Line | Finding | Severity |
|---|---|---|---|---|
| Provenance graph scope | `components/context/data-object/DataObjectProvGraph.vue` | 86–123 | Predecessor graph is **truncated at 6 predecessors** (`slice(0, 6)` at lines 86, 106). A multi-step process chain (ply → layup run → robot session → material batch) will silently drop ancestors beyond 6. Hover tooltip on truncated edges gives no "load more" affordance. | CRITICAL |
| Process-chain traversal | No component | — | There is no UI-level recursive predecessor walk. An auditor tracing a 4-hop defect chain must manually navigate DataObject by DataObject, expanding Provenance on each, remembering the path. The ECharts graph resets on each new DataObject. No "keep history" or breadcrumb path. | CRITICAL |
| Provenance graph vs log | `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | 310–332 | The "Provenance" panel defaults to the structured log view, which is time-ordered (who did what). The force-directed graph is the second tab. Neither view is oriented toward "tracing ancestry upstream" — they both answer "what happened to this DataObject" not "what was this DataObject derived from". | HIGH |
| Export for audit | `pages/collections/[collectionId]/index.vue` | 69–89 | RO-Crate export exists at collection level. There is no export that produces a linear audit trail (process A → B → C with timestamps, operators, material references) in a format an auditor can hand to a certification body without post-processing. | MEDIUM |

**Auditor friction verdict:** The 6-predecessor cap is a hard blocker for any process chain deeper than 2 hops. DIN EN 9100 traceability requires end-to-end lineage from raw material to finished part. The current graph is visually interesting but auditor-unfriendly: it shows who acted, not where the data came from.

---

### Persona 4: Shop Floor IME (Industrial Manufacturing Engineer)

**Goal:** Monitor live AFP layup progress on a ruggedized terminal. Gloved hands, ambient noise, 60 cm viewing distance. Real-time channel readout of compaction force and laser temperature.

| Component | File | Line | Finding | Severity |
|---|---|---|---|---|
| Mobile / touch | All | — | There is a mobile nav drawer (`components/layout/HeaderBar.vue` lines 139–182) and responsive breakpoints on the personal digest grid. However, all data-entry surfaces — annotation dialogs, attribute editors, the treeview — use compact Vuetify density and small touch targets. No glove-friendly mode, no enlarged-tap-target mode, no fixed-layout dashboard. | HIGH |
| Live mode UX | `components/container/timeseries/TimeseriesAllChannelsChart.vue` | 255–295 | Live mode exists and is well-implemented (setInterval, visibility pause, in-flight guard). However, turning it on requires navigating to `/containers/timeseries/[id]` — there is no mechanism to pin a live channel readout to a persistent dashboard visible without navigating away. The "home" PersonalDigest page shows static collection cards, not live channel feeds. | HIGH |
| Big-number display | No component | — | There is no large-text numeric readout component (e.g., current value of compaction_force at 128pt font size). The IME needs a glanceable dashboard showing 4–6 current values. The current timeseries chart is line-chart only; point-in-time value is hover-only. | HIGH |
| Keyboard/touch navigation | No shortcut system | — | No global keyboard shortcuts at all (the grep found only dialog-level Esc/Enter handlers). No `j/k` DataObject navigation, no `/` to focus global search, no `n` to create. A ruggedized terminal without a mouse has no keyboard-driven path through the data hierarchy. | MEDIUM |

**IME friction verdict:** Shepard has no shop-floor mode at all. The live timeseries chart is a strong foundation, but without a pinnable real-time dashboard surface and large-format readouts, an IME monitoring an AFP run cannot practically use the current UI during production.

---

## Performance Risks

### Risk 1: Double full-collection DataObject fetch per DataObject detail page

**Files:** `composables/context/useFetchAllDataObjects.ts` (lines 13–61), called from `CollectionLineageGraph.vue` (line 16) and `DataObjectProvGraph.vue` (line 27).

`useFetchAllDataObjects` exhaustively paginates with `size=200` in a while loop. At 200 DataObjects it fires 1 request; at 600 it fires 3; at 2000 it fires 10, each awaited sequentially. Both graphs invoke this independently — two serial paginating fetches on the same collection, both triggered when the DataObject detail page renders.

At LUMEN scale (15 DataObjects) this is sub-millisecond. At MFFD scale (300+ process steps) this is 2 × ceil(N/200) blocking round-trips before either graph renders. Neither graph has a skeleton or progressive render — they show a spinner until fully resolved.

**Mitigation path:** Shared singleton reactive ref for the collection's DataObjects. One fetch feeds both graphs. Server-side graph endpoint (adjacency list only, no metadata payloads) would reduce data volume further.

---

### Risk 2: N per-row API calls when expanding channels in the measurements table

**Files:** `composables/container/useFetchChannelPreview.ts` (lines 17–39), called from `components/container/timeseries/ChannelPreviewChart.vue`.

Each expanded row in `TimeseriesMeasurementsTable` fires an independent `getTimeseries` call with `start=0, end=now, groupBy=1s`. A container with 200 channels expanded simultaneously fires 200 concurrent queries, each returning up to years of 1-second bucketed data. There is no fetch cache, no shared time-range, and no in-flight deduplication. The first 200 rows expanding rapidly would generate a cascade of 200 overlapping requests.

**Mitigation path:** Lazy-load only the visible row (IntersectionObserver), debounce row-expand events, or batch channel previews into a single multi-channel query if the backend supports it.

---

### Risk 3: ECharts force layout with unbounded node count

**File:** `components/context/collection/CollectionLineageGraph.vue` (lines 34–125).

The `chartOption` computed creates one ECharts node per DataObject, no cap, no clustering. ECharts force layout runs in JS and is DOM-bound. Observed browser freeze in force simulations begins around 300–500 nodes. At MFFD scale (500+ DataObjects modelling individual AFP passes, stringer welds, cleat attachments) the graph becomes unresponsive. The `layoutAnimation: true` flag (line 104) keeps the simulation running continuously.

**Mitigation path:** Cap at 200 visible nodes with a "zoom to subgraph" affordance; or switch to a dagre/hierarchy layout for the process-chain view which is semantically a DAG, not a free-force network; or use a canvas-only renderer with WebWorker simulation.

---

### Risk 4: Client-side status filter pagination gap

**File:** `components/context/collection/CollectionDataObjectsPanel.vue` (lines 156–199).

The name search is server-side (line 53 passes `name` to the API). The status filter (`statusFilter`) is applied client-side at line 196 against only the currently-loaded page of 25. A collection with 500 DataObjects and 40 IN_REVIEW items across pages shows the 2–3 IN_REVIEW items that happen to be on the visible page, giving the impression that almost none are in review. The filter silently under-reports.

**Mitigation path:** Pass `status` as a server-side filter parameter if the backend supports it; or note the limitation in the UI (e.g., "Filter applies to current page only").

---

### Risk 5: Channel edit mode renders all checkboxes unvirtualized

**File:** `pages/containers/timeseries/[containerId]/index.vue` (lines 251–261).

The "Edit channels" mode renders one `v-checkbox` per measurement using `v-for`, with no virtualization. A container with 200 channels renders 200 checkboxes into the DOM simultaneously. Vuetify checkboxes each mount a `<label>`, `<input>`, and ripple overlay — at 200 that is ~1200 DOM nodes added at once.

**Mitigation path:** Replace with a virtualized list (`v-virtual-scroll`) or a searchable multi-select that lazy-renders visible items.

---

## Opportunities

### Opportunity 1 (highest impact): Server-side scoped entity search in the global bar

**What it fixes:** Researcher can type "TR-004" in the global search bar and jump directly to the DataObject, bypassing collection browsing. Curator can find "compaction_force" channel without navigating through the container tree. Auditor can jump directly to a specific process step by name.

**What it requires:** Backend: extend the header search composable (`useCollectionSearch`) to optionally include DataObject, Channel, and Container results. A single composite search endpoint or parallel requests. Frontend: group results by entity type in the `v-autocomplete` dropdown. The header autocomplete (`HeaderBar.vue` lines 39–71) already has the plumbing; it just needs the wider scope.

**Magnitude:** Every session starts with a search intent. Scoped search converts "navigate-then-search" (3–5 clicks) into "search-then-arrive" (1 action). This reduces time-to-context from ~15s to ~2s for experienced users who know what they want.

---

### Opportunity 2 (highest impact): Row selection + bulk actions in CollectionDataObjectsPanel

**What it fixes:** Curator can select 15 test runs, click "Set status" → READY. Curator can select 50 channels and open a bulk annotation dialog. Auditor can select a predecessor chain subset and export it as a sub-RO-Crate.

**What it requires:** Add `v-checkbox` to each row in `CollectionDataObjectsPanel.vue`. Add a bulk-action toolbar that appears when selection is non-empty (change status, add annotation, delete, export). The annotation dialog already exists; it needs a `targets: Annotated[]` variant. Status change is a single PATCH per DataObject.

**Magnitude:** Bulk annotation of 50 channels drops from ~350 clicks to ~5 (select all → annotate → search term → confirm). Bulk status set for 15 test runs drops from 15 × 5 = 75 interactions to ~3.

---

### Opportunity 3 (highest impact): Side-by-side timeseries comparison view

**What it fixes:** Researcher selects TR-003 and TR-004, picks 3 shared channels, gets a split panel with synchronised time-axis zoom. Anomaly at t=8s on TR-004 is immediately visible against TR-003's normal curve.

**What it requires:** A comparison route `/collections/[id]/compare?a=[doId]&b=[doId]` or a split-pane component on the DataObject detail page. The `TimeseriesChart` component already accepts an array of `TimeseriesSeries` — multi-DataObject overlay is a data-fetching change, not a chart-library change. The curated channel view already persists channel selections per container; reuse that to offer "compare selected channels across two datasets."

**Magnitude:** Removes the current two-tab workaround. The LUMEN anomaly showcase story (TR-004 turbopump vibration spike) is the canonical demo; this feature makes that demo demonstrable without leaving the app.

---

## Ideas

### Idea A: Ancestor walk — "Trace provenance upstream" button

A dedicated traversal UI in the DataObject Provenance panel. The user clicks "Trace upstream" and sees a linear timeline of ancestors: DataObject → Predecessor → Predecessor's Predecessor, each shown as a collapsible card. This is a depth-first path, not a force-graph — it answers the auditor's question directly. The backend already has predecessor IDs on every DataObject; the walk is a recursive client-side fetch chain or a new `/v2/data-objects/{appId}/ancestor-chain?depth=10` endpoint.

### Idea B: Pinnable live channel tiles on the personal digest page

The PersonalDigest page (`components/context/home/PersonalDigest.vue`) shows collection cards. A user could "pin" up to 4 live channel readouts from watched containers. Each tile shows: channel name, current value (large), unit (if annotated), a 5-minute sparkline, and an ingest indicator. Powered by the existing live-mode infrastructure in `TimeseriesAllChannelsChart`. This is the Shop Floor IME's entry point into the platform without navigating.

### Idea C: Annotation suggestion from channel name

When the user opens AddAnnotationDialog and types in the property field, the ontology search (300ms debounce, `useTermSearch`) already works. The idea: pre-populate the search with the channel's `symbolicName` or `field` value so that a channel named `compaction_force` immediately surfaces `m4i:NumericalVariable` + `QUDT:ForceUnit` suggestions. Zero added backend cost; pure UX pre-fill heuristic.

### Idea D: Lineage graph semantic zoom levels

Replace the single force layout in `CollectionLineageGraph.vue` with a zoom-aware representation:
- Zoom out (>50 nodes visible): cluster DataObjects by their `status`, showing 5 cluster nodes.
- Zoom mid: show individual DataObjects, edges collapsed to "N predecessor hops".
- Zoom in (click a node): expand to the DataObject detail card inline, with a mini provenance graph.

ECharts supports custom symbol renderers and level-of-detail; this does not require a library swap.

### Idea E: Advanced mode surfaced as a keyboard shortcut

Advanced mode is currently buried in the Profile page (a full navigation away). A `?` or `Ctrl+Shift+D` shortcut that toggles it inline — with a toast confirmation ("Advanced mode ON") — would make the developer/power-user workflow faster and model the toggle as a transient session setting rather than a profile preference. The toggle is already optimistic in `useAdvancedMode.ts` (lines 33–50).

---

## Real-World Impact

| Finding | Estimated interaction reduction |
|---|---|
| Bulk annotation (Opportunity 2) | 50-channel annotation: ~350 clicks → ~5 clicks (~98% reduction) |
| Scoped global search (Opportunity 1) | Navigate-to-DataObject: 15s + 5 clicks → 2s + 1 action |
| Side-by-side compare (Opportunity 3) | Dual-tab manual alignment → single view, shared time axis |
| Ancestor walk (Idea A) | 4-hop audit trace: 4 × navigate + 4 × expand Provenance → single linear view |
| Pinnable live tiles (Idea B) | IME monitoring: requires dedicated terminal route → dashboard on home page |

Audit velocity for compliance use: a DIN EN 9100 lineage trace from defect to raw material currently requires manually navigating each predecessor node. With Idea A and the ancestor-walk endpoint, the same trace is a single panel open. On a campaign with a 6-hop chain, that is 12 navigation actions → 1.

---

## Gaps & Blockers

**Search scoping (G1):** The backend's collection search API (`useCollectionSearch`) is the only scoped search surface the header uses. Extending global search to DataObjects requires a backend endpoint or a composite client-side fetch that would be slow at scale. This is likely a moderate backend investment before the Opportunity 1 frontend change delivers.

**Bulk annotation API (G2):** `AddAnnotationDialog` calls `props.annotated.addAnnotation(...)` once per submit. No batch annotation endpoint is visible in the composables. A "annotate N targets at once" flow requires either a backend bulk endpoint or N sequential calls (acceptable at 50; unacceptable at 5000).

**Status filter server-side (G3):** `usePagedDataObjects` does not pass a `status` parameter to the backend. The backend may not yet support status-filter on the DataObject list endpoint. Until it does, the client-side filter gap is structural.

**CollectionSidebar Containers section (G4):** `CollectionSidebar.vue` at line 314 gates the entire Containers section behind `v-if="advancedMode"`. This means basic-mode users have no sidebar path to containers at all. Per the project's stated rule ("advanced mode is a strict superset of basic mode"), this is a violation: basic mode should show containers but with less detail, not hide them entirely. The fix is to show the Containers section in both modes and gate only the advanced sub-items (e.g., container IDs, raw metadata) behind `advancedMode`.

**Ancestor-walk endpoint (G5):** The DataObjectProvGraph is limited to direct predecessors fetched in a single provenance query (50 activities, line 46). A recursive ancestor walk requires either: (a) a new `/v2/data-objects/{appId}/ancestor-chain` backend endpoint returning the full ancestor path, or (b) client-side recursive fetching (N sequential calls per hop). Option (b) is feasible for auditor use cases where latency tolerance is higher.

**IME dashboard (G6):** A shop-floor mode requires either a separate route/layout (`/dashboard`) or a composable that makes the PersonalDigest "pin channel" concept persistent. The notification system (NTF1) and watched-container system (WATCH1) already provide the plumbing; the gap is purely a frontend composition task.

---

## What Surprised Me

**Two parallel force-graph implementations that share no code.** `CollectionLineageGraph.vue` and `DataObjectProvGraph.vue` are both ECharts `graph` + force layout components built independently. They share the same colour palette constants (both hardcode `"#4097CC"`, `"#FCA54D"`, `"#7ECA8F"`), the same label truncation pattern (16 chars + "…"), and the same `roam: true` + `draggable: true` config. Neither imports the other or a shared graph composable. A `useLineageGraph(nodes, edges)` composable would unify them.

**The sidebar tree and the main panel DataObjects table are two parallel navigations of the same data.** The treeview in `CollectionSidebar.vue` lazy-loads the collection's DataObjects by parent structure. The `CollectionDataObjectsPanel` in the main area fetches the same DataObjects via a server-paginated flat list with name search. Both update on the same `collectionId`. A curator using the search in the sidebar will miss items not yet expanded; a curator using the main panel's search gets server-side results. This dual-surface pattern means the same "find TR-004" action has two paths with different completeness guarantees and no visible explanation of the difference.

**Advanced Search is a raw JSON DSL editor.** The page at `/search` opens with an `initialJson` example pre-populated in a text field. The route-level comment offers a JSON editor toggle. This is a developer tool wearing a "Search" label. For a product that explicitly targets casual researchers who touch it once a month, a form-driven query builder (AND/OR conditions on name/status/date/attribute) would serve 95% of search intents. The JSON DSL can remain as an "Advanced" mode variant.

**The global header search (`HeaderBar.vue:39`) hits collections only.** This is the most-visible entry point for finding anything in the system. A researcher who knows the DataObject name "AFP_Run_Layer_047" must browse into the right collection, expand the right tree node, or use the per-collection main panel search — all slower paths than a global name search. The autocomplete infrastructure is already in place; the constraint is purely scope.

**`useFetchAllDataObjects` is invoked on every DataObject detail page, twice.** This was the most operationally surprising finding: a composable designed for the graph visualisations pulls the entire collection into memory on every DataObject navigation. At LUMEN scale (15 items) this is invisible. The function was clearly designed with the lineage use case in mind, but it will become the dominant latency source for any collection above ~100 DataObjects — silently, since both graph panels sit in collapsed expansion panels below the fold.
