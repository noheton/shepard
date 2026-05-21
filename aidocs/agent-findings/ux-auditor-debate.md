# UX Auditor Debate — Core Tech & UX Lens
## Phase 2: Multi-Agent Cross-Proposal Debate

*Persona: Core Tech & UX Auditor — frontend engineer, interaction designer, performance engineer*
*Scope: 78 proposals across 8 agent files, argued from the UX domain lens*
*Critical deadline: HMC Project Call 2026 — 06 July 2026 (~6 weeks)*

---

## Top 5 I'm Championing

### CHAMPION 1 — FAIR Metadata Spine + Completeness Ring
**Proposals merged:** RDM-P1, RDM-P2, RDM-P3, RDM-P4, RDM-P5, API-P6, Analytics-P10, Ecosystem-EP-02, UX-P4 (Metadata Completeness Ring on sidebar)

**Why from UX:** Six agents converged on this independently. From a UX standpoint, this is rare alignment that signals a user pain point severe enough that every lens sees it. The FAIR Metadata Spine is the *only* cluster with a hard external deadline: HMC Project Call 06 July 2026. Without `license`, `createdByOrcid`, `accessRights`, and `fundingReferences` fields, researchers cannot complete the checklist that unlocks funding. This is not a quality-of-life issue — it is a blocker for the platform's core institutional mandate.

**UX implementation note:** The four fields (license, ORCID, access rights, funder references) land on the DataObject creation dialog and the Collection creation dialog. The completeness ring (UX-P4) is a sidebar widget — a colored donut that turns green at 100%. The ring is the *only* element that needs to be in advanced mode; the four FAIR fields themselves belong in basic mode, because a researcher who skips them is harming their own FAIR score, not accessing expert functionality. Any implementation that hides these fields behind an advanced-mode toggle is wrong.

**Effort:** S–M total for all four fields + ring widget. RDM rated P1–P4 individually as S each. The ring is M (composable from computed field checks). This sprint can ship in 3 weeks if scoped tightly. That lands comfortably before 06 July.

**Verdict:** CHAMPION. Ship this first. Nothing else is as time-constrained.

---

### CHAMPION 2 — API-P1 (referenceIds fix) + API-P2 (flat DataObject endpoint)
**Proposals:** API-P1, API-P2

**Why from UX:** This is a live bug that breaks every agentic and programmatic workflow. `referenceIds: long[]` in `DataObjectV2IO` contains `BasicReference` node IDs, not `appIds`. Any client — human-built, AI-built, MCP tool — that passes these IDs to `GET /v2/data-objects/{id}` gets a 404. The API scrutinizer rated this CRITICAL. From a UX standpoint, a 404 that returns no explanation is the worst possible dead end. Users blame themselves.

API-P2 (flat `GET /v2/data-objects/{appId}` that returns everything in one call, no nested container traversal) is the complement. Currently reaching a DataObject's containers requires 3–5 sequential API calls. Every frontend component that tries to display a DataObject holistically is making N+1 calls. This is why the DataObject detail page feels sluggish.

**Effort:** S for both. These are response shape changes and a new single-entity endpoint. No schema migrations. No plugin dependencies.

**UX impact:** Fixes the MCP tool dead end (a user asking an AI assistant about TR-004 gets an answer instead of a 404). Halves the perceived load time on the DataObject detail page. Unblocks every other proposal that traverses from DataObject to containers.

**Verdict:** CHAMPION. Smallest effort, highest multiplier. Ship this in week 1 of any sprint.

---

### CHAMPION 3 — Bulk Row Selection + Server-Side Status Filter
**Proposals merged:** UX-P1, MfgQ-P8, Strategy-P10, API-P5

**Why from UX:** Four agents identified this. The current workflow for curating 15 test runs requires opening each DataObject individually, changing its status, saving, navigating back, opening the next. For 15 items that is 15 × ~4 clicks = 60 clicks minimum. At MFFD scale (hundreds of process steps per fuselage panel run), this workflow becomes unusable.

The server-side status filter (API-P5) is the prerequisite. Currently `CollectionDataObjectsPanel.vue` filters by status client-side — meaning it must fetch all DataObjects first. At 1,000 DataObjects, the initial page load sends a request for all 1,000 items to filter 15 of them client-side. That is the scalability failure.

**Effort correction:** UX-P1 rated this M. I agree with M for the bulk toolbar. The server-side filter is S (a `?status=` query param on the list endpoint + one computed variable change in the panel component). These are separable: ship the server-side filter first (S), then the bulk toolbar (M).

**UX implementation note:** The bulk toolbar pattern (checkbox per row, floating action bar at bottom of screen when ≥1 selected) is standard. Vuetify `v-data-table` supports it natively. The action bar must offer: set status (dropdown), add annotation, export selected, delete selected (destructive, requires confirmation dialog). Do not implement this as a modal — a persistent bottom bar keeps the table visible.

**Verdict:** CHAMPION. The S-effort filter ships immediately; the M-effort toolbar follows.

---

### CHAMPION 4 — Ancestor-Chain Endpoint + Linear Timeline UI
**Proposals merged:** API-P4, UX-P5, MfgQ-P5, Strategy-P3, Analytics-P6, RDM-P11

**Why from UX:** Six agents converged on this. The Predecessor/Successor chain in Neo4j already stores the test run lineage (TR-001 → ... → TR-004 → investigation → TR-005 hold → TR-006 re-test). The problem is that today this chain is only visible in `DataObjectProvGraph.vue`, which is hard-capped at `slice(0, 6)` — it silently drops predecessors 7 and beyond. A researcher looking at TR-006 cannot see TR-004 unless they traverse manually.

`GET /v2/data-objects/{appId}/ancestor-chain` is the missing endpoint. It walks the Predecessor chain from the given node back to the root and returns the ordered list with status, timestamp, and link to each ancestor. This is purely a Cypher traversal — one MATCH query with variable-length path (`[:PREDECESSOR_OF*]`).

The UX surface is a linear timeline strip (horizontal, collapsible) that sits below the DataObject header. Each node in the chain is a chip with status color and click-to-navigate. This is not the full DAG graph view — it is the fast linear trail for "how did we get here?" The full DAG (`DataObjectProvGraph.vue`) remains for cases where branching is important.

**Effort correction:** The endpoint is M (Cypher query + resource method). The timeline UI strip is M. Total is M+M = genuine L sprint item. Do not underestimate the UI work — the strip must handle chains of 15+ nodes gracefully (horizontal scroll + collapsed summary).

**Verdict:** CHAMPION. Six-agent convergence + fixes a hard `slice(0,6)` bug. But schedule it after FAIR fields and API fixes, because it has no HMC deadline dependency.

---

### CHAMPION 5 — Channel Quality Score Surface (Analytics-P1)
**Proposals:** Analytics-P1

**Why from UX:** This is the easiest win on the entire list and the most invisible. The AI quality score for timeseries channels (`AI1c`) has already shipped on the backend. There is no UI surface for it. A researcher looking at a noisy channel has no idea the system already computed a quality score for it. The analytics agent rated this S effort with zero dependencies.

From a UX standpoint, surfacing an already-computed metric is lower risk than shipping a new computation. The UI change is adding a colored badge (green/amber/red) to each channel row in `TimeseriesMeasurementsTable.vue` and a sort-by-quality-score option. That is a day of frontend work, not a sprint.

**Why this over everything else for slot 5:** The side-by-side comparison (UX-P6, Ecosystem-EP-10) is L effort and I have serious concerns about the timeline estimate (addressed below). The ancestor-chain covers the provenance story. The Channel Quality Score Surface is S effort, immediately user-visible, and makes the AI investment tangible to researchers who currently see no AI output anywhere in the UI.

**Verdict:** CHAMPION. Ship it in the same sprint as the API fixes — it is genuinely small.

---

## Top 3 I'm Challenging

### CHALLENGE 1 — MfgQ-P7: Shop Floor Template Mode (`shopFloorMode: boolean`)
**Verdict:** REDIRECT

**The proposal:** Add a `shopFloorMode: boolean` flag to the Template DSL. When true, the template renders as a simplified form — large touch targets, no sidebar, status visible top-right, NCR raise in header.

**My challenge:** This proposal forks the form renderer. Maintaining two rendering paths for the same template DSL is the fastest way to introduce divergence bugs. In six months, a feature added to the normal form view will be missing from shop-floor mode. A field added in shop-floor mode will cause the normal renderer to throw. The `v-if="!advancedMode"` antipattern that is explicitly banned in our CLAUDE.md ("advanced MUST be strict superset of basic") gets reinvented here at the template level.

The actual shop-floor requirements are:
- Large touch targets (≥ 48px minimum tap target per WCAG 2.5.5)
- High contrast (Vuetify dark theme already exists)
- NCR raise accessible without sidebar

None of these require a template DSL fork. They require:
1. A global Vuetify density setting ("comfortable" → "compact" → "shop-floor") that scales touch targets, persisted per user profile
2. A sticky "Raise NCR" FAB (floating action button) on DataObject detail pages, visible when status is DRAFT or IN_REVIEW, always regardless of mode

The Template DSL should describe *data structure*, not *render mode*. A `shopFloorMode` flag in the DSL couples data semantics to presentation concerns — the wrong layer.

**Redirect:** Implement Vuetify density preference (S effort), sticky NCR FAB (S effort), and rewrite MfgQ-P7 as a pure CSS/density concern with no DSL changes. The manufacturing quality engineer's underlying requirement is valid; the implementation vector is wrong.

---

### CHALLENGE 2 — MfgQ-P9: AI Anomaly → NCR Auto-Create at Confidence ≥ 0.8
**Verdict:** REDIRECT

**The proposal:** When the AI anomaly detector fires with confidence ≥ 0.8, automatically create an NCR DataObject with status NCR_OPEN, linked as a child of the flagged DataObject.

**My challenge:** This is alarm fatigue by design. At confidence ≥ 0.8, the system will fire on:
- Transient sensor spikes that are equipment artifacts, not anomalies
- Expected exceedances that the test engineer already noted
- Channels with high variance by definition (combustion pressure during ignition transient)

Each auto-created NCR DataObject requires a human to open it, review it, and either close it (not a real NCR) or escalate it. At 15 test runs × N channels, a researcher will spend more time closing phantom NCRs than reviewing real ones. The moment researchers start routinely closing NCRs without reading them, the NCR system has failed as a safety mechanism.

The deeper UX problem: auto-created DataObjects that contain no human judgment are a provenance pollution risk. An auditor looking at the lineage graph sees NCR DataObjects that were machine-generated. Under EN 9100, a non-conformance record must be initiated by a responsible person. An auto-created NCR with a machine author does not satisfy this.

**Redirect:** The correct flow is:
1. AI fires → NTF1 notification to the responsible engineer: "Anomaly detected in [channel] at [timestamp], confidence 0.8. [Review] [Dismiss] [Raise NCR]"
2. Engineer clicks "Raise NCR" → pre-filled NCR creation dialog (channel, timestamp, AI confidence pre-populated)
3. Engineer reviews pre-fill, adjusts if needed, submits → NCR DataObject created with human author

This is one extra click for the engineer, removes all alarm fatigue, and produces NCRs with human provenance. The Analytics-P2 (anomaly-to-notification bridge) already covers the notification side. MfgQ-P9 should be replaced by a "1-click NCR raise from notification" action — not auto-creation.

---

### CHALLENGE 3 — Analytics-P11: Unhide Plugin AI-Enhanced Feed Enrichment (LLM-writes public descriptions)
**Verdict:** CHALLENGE + conditional DEFER

**The proposal:** Use the AI plugin to generate human-readable dataset descriptions for the Helmholtz Knowledge Graph Unhide feed, enriching the metadata that appears in public search results.

**My challenge:** This proposal outsources researcher judgment to an LLM for public-facing institutional metadata. The Helmholtz Knowledge Graph is indexed by OpenAIRE and visible to funding bodies. A researcher who publishes a dataset to HKG and discovers that its public description was written by an LLM — and differs from what they intended — has a legitimate complaint about institutional misrepresentation.

The trust failure is not that LLMs write bad descriptions (they often write fluent descriptions). The trust failure is the lack of a review gate. The proposal does not specify whether researchers see and approve the generated description before it is emitted to the Unhide feed. If they don't, this feature will produce LLM-authored public metadata under the researcher's name without their explicit review.

**Conditional acceptance:** This feature is acceptable *only* with an explicit per-collection review step:
1. LLM generates suggested description → stored as a draft field on the Collection, never auto-emitted
2. Researcher sees "Your public Unhide description (AI draft — review before publishing)" in the Collection settings panel
3. Researcher edits, approves, publishes → description emits to feed

Without the review gate, defer this feature entirely. The reputational risk to DLR researchers outweighs the discoverability gain.

---

## Merges I'm Calling

### Merge 1 — The FAIR Fields Sprint (ship as one PR)
RDM-P1 (license) + RDM-P2 (ORCID stamp) + RDM-P3 (access rights + embargo) + RDM-P4 (funder references + ROR autocomplete) + Ontologist-P7 (FAIR fields on AbstractDataObject) + Strategy-P1 (FAIR Metadata Spine) + Analytics-P10 (FAIR score endpoint) + UX-P4 (completeness ring widget)

**Why merge:** These are four backend field additions on `AbstractDataObject`, one endpoint (`GET /v2/collections/{appId}/metadata-completeness`), and one frontend widget. They share a single migration file. Shipping them separately across multiple PRs creates a week of merge conflicts and N separate migration files that must execute in the right order. One PR, one migration, one ring widget. The completeness ring is the UX motivator that makes the other fields feel purposeful — ship them together.

**Owner split:** Backend (field additions + endpoint) can be parallelized with frontend (ring widget + form fields in creation dialogs). Two engineers in parallel, one PR at merge time.

### Merge 2 — The Ancestor-Chain Cluster (one endpoint, one UI surface)
API-P4 + UX-P5 + MfgQ-P5 + Strategy-P3 (Lineage Walk API) + Analytics-P6 + RDM-P11

**Why merge:** Six proposals, all asking for `GET /v2/data-objects/{appId}/ancestor-chain`. The endpoint is the same in every proposal. The UI surfaces differ: UX-P5 wants a linear timeline strip; MfgQ-P5 wants it visible in the process gate view; Analytics-P6 wants it for audit trail export. These are all additive uses of the same endpoint. Ship the endpoint once. Then the three UI surfaces (timeline strip, process gate widget, export download) can ship in sequence without touching the backend again.

### Merge 3 — Status Vocabulary + Predecessor Gate (one migration, one state machine)
MfgQ-P2 (extended status vocabulary: NCR_OPEN, ON_HOLD, REWORK, REJECTED, CERTIFIED, SUPERSEDED) + Ontologist-P6 (Quality Status extension) + Strategy-P8 (Quality Plugin Foundation) + MfgQ-P1 (Predecessor-Status Gate)

**Why merge:** The status vocabulary extension and the predecessor gate are tightly coupled — you cannot enforce "cannot advance to CERTIFIED until all predecessors are CERTIFIED" without first defining CERTIFIED. These must ship in the same migration. If they ship separately, there is a window where the new statuses exist but the gate is not enforced, which is worse than either the old or new behavior.

**Scope note:** Keep status vocabulary on the `AbstractDataObject` entity (in-tree). The predecessor gate logic belongs in a `shepard-plugin-quality` plugin (plugin-first doctrine). The migration that adds the status enum values is in-tree; the gate enforcement is a plugin. This keeps the core entity model expressive without coupling quality workflow logic to the platform core.

### Merge 4 — CHAMEO + SSN/SOSA Ontology Manifest (two TTL entries, one PR)
Ontologist-P3 + MfgQ-P10 + Strategy-P4 (partial: semantic spine completion)

**Why merge:** These are two entries in the ontology manifest (`V49__Bootstrap...` migration or a successor migration file). There is no reason to ship CHAMEO and SSN/SOSA in separate PRs. One migration file, two `CREATE` statements, one PR. The only review-time question is whether the TTL source URLs are stable (use the canonical W3C/OMG URIs, not mirrored copies).

### Merge 5 — API Hygiene (one sprint, four fixes, no dependencies between them)
API-P1 (referenceIds fix) + API-P2 (flat DataObject endpoint) + API-P5 (pagination envelope + status filter) + API-P7 (ProblemJson ExceptionMapper) + API-P8 (human-readable OpenAPI tags)

**Why merge:** All five are S–XS effort. None depend on each other. None depend on any other proposal. Shipping them separately creates five separate PRs that each touch the API layer, generating five code reviews for trivial changes. One "API hygiene sprint" PR is faster to review, faster to test, and faster to deploy. The only risk is that P7 (ProblemJson) requires an `ExceptionMapper` change that could affect error handling across all endpoints — isolate that change to its own commit within the PR for bisectability.

---

## UX Complexity Underestimated

### Underestimate 1 — Side-by-Side Timeseries Comparison (UX-P6, Ecosystem-EP-10)
**Stated effort:** L (3–4 weeks)
**My estimate:** 6–8 weeks

**Why:** The proposals treat this as "render two charts side by side." The actual implementation requires:
1. A synchronized x-axis zoom/pan across both charts (a custom event bus or a shared Plotly/Chart.js domain state — neither is trivial in Vue 3 reactive model)
2. A channel picker that lets users select channels from different DataObjects and different Timeseries containers, across collections
3. Y-axis normalization options (raw values, percent of max, z-score) — without normalization, comparing a pressure channel (MPa) to a temperature channel (K) on the same axis is meaningless
4. URL-serializable comparison state (so a researcher can share a comparison view link)
5. Performance: fetching two channels' full time ranges simultaneously at 1 Hz resolution × 15 minutes = 900 points each, manageable; at 1 kHz × 15 minutes = 900,000 points each, not manageable without server-side downsampling

Item 1 alone (synchronized zoom) has eaten 2-week sprints on data visualization projects with dedicated charting engineers. This is not a 3-week feature. Do not commit to shipping this before September without a dedicated charting engineer on it.

### Underestimate 2 — Shop Floor Template Mode (MfgQ-P7, already CHALLENGED above)
**Stated effort:** L (4 weeks)
**My estimate:** 10–12 weeks (if pursued as DSL fork — which I've redirected)

The redirected version (density preference + sticky NCR FAB) is S effort (3–5 days). If the team ignores the redirect and forks the DSL, the 10–12 week estimate holds because they will spend 4 weeks building the fork, then 6–8 weeks fixing divergence bugs.

### Underestimate 3 — Public Landing Page + Embed Card (Ecosystem-EP-03)
**Stated effort:** M (1 week)
**My estimate:** 2–3 weeks

**Why:** A public landing page for a Collection that is accessible without authentication requires:
1. A new Nuxt route (`/public/collections/{appId}`) with no auth guard
2. A `publicationState` or `accessRights` check on the backend before serving any data (cannot expose restricted Collections via this route)
3. An OpenGraph meta tag implementation for the embed card (title, description, thumbnail — Nuxt 3 `useHead()` + `useSeoMeta()`)
4. A CORS policy decision: if the embed card iframe is hosted on a third-party site, the API must allow cross-origin requests for public Collection endpoints
5. A design decision on what the card contains (Collection title, FAIR score, DataObject count, last updated) — every field is a separate API call or must be denormalized into a summary endpoint

The 1-week estimate assumes no auth guard work and no CORS policy work. Those assumptions will not survive the first security review.

### Underestimate 4 — PDF Auto-Annotation Suggestions (Analytics-P4)
**Stated effort:** M (2–3 weeks for plugin-ai integration)
**My estimate:** M for the integration; XL for the feature to be useful

**Why:** The plugin-ai integration (calling an LLM API with a PDF's extracted text and requesting suggested annotations) is genuinely M effort. The problem is the quality of suggestions on scientific PDFs is highly variable without prompt engineering tuned to the domain. A researcher who sees "suggested annotations: [document_type: report, author: various, year: 2024]" from a hotfire test report will turn off the feature immediately. The XL effort is in the prompt template engineering, the domain-specific few-shot examples, and the UI for accepting/rejecting individual suggestions. Do not commit to a timeline that only includes the integration plumbing.

### Underestimate 5 — Video Plugin Multi-Track Annotation Editor (UX-P10)
**Stated effort:** M (2 weeks)
**My estimate:** Canvas thumbnail strip alone is 3+ days; full multi-track editor is L (4–6 weeks)

The thumbnail strip (render video frames at 1-second intervals as a horizontal filmstrip) requires either:
- Server-side frame extraction (FFmpeg, new backend endpoint, async job) — M effort alone
- Client-side canvas rendering (HTMLVideoElement + requestAnimationFrame + OffscreenCanvas) — fragile across browsers, especially Safari

A multi-track annotation editor (multiple annotation tracks overlaid on the timeline, drag-to-create annotation spans, track colors, label editing) is closer to building a mini video editing timeline than adding a UI component. This is L effort minimum. The M estimate assumes the video player already exists and annotations are text boxes; the reality is the video player (UX-P10 presupposes the video plugin ships first) plus the timeline editor is a significant surface.

---

## The Ingest Ecosystem Gap — What No Agent Addressed

This is the unique contribution the UX lens surfaces that all seven other agents missed entirely.

**The gap:** The ingest ecosystem — SPW (JavaFX desktop app, retiring), hotfolder (Node-RED, described internally as "shoddy"), sTC (OPC-UA + MQTT collector), and the planned Ansible/IPC dashboard — has **zero proposals** from any agent addressing the UX of *getting data into Shepard in the first place*. Every proposal assumes data is already in Shepard. For a new institute evaluating adoption, "how do I get my data in?" is question 1.

**Specific UX failures not covered by any proposal:**

1. **Hotfolder routing-rules admin UI:** The hotfolder watcher (Node-RED) has no UI for defining "files matching pattern X go to Collection Y." Operators configure this in Node-RED flows directly. A researcher who wants to onboard a new instrument must file a Node-RED configuration request with IT. This is a 3-day latency on a 30-second task.

2. **SPW browser stepper migration UX:** SPW (JavaFX) is retiring. What replaces its guided "select files → map to DataObject → preview → upload" stepper? If the replacement is the existing file upload dialog, researchers lose the guided mapping step and will produce DataObjects with missing metadata.

3. **"What's flowing in right now" operator dashboard:** A researcher running a live test on sTC (OPC-UA) has no way to see "I am currently ingesting data into TR-015 at 142 Hz." There is no live status view in the Shepard UI for active ingest jobs. Ansible/IPC was proposed as the address for this but it's an external tool, not a Shepard UI feature.

4. **JupyterHub artifact Collection picker:** The J2e design (notebook artifacts auto-save to Shepard) requires a Collection picker inside JupyterHub. This widget does not exist. A researcher running a Python analysis in JupyterHub who wants to save their results back to Shepard must leave JupyterHub, find the correct Collection in Shepard, note the appId, return to JupyterHub, and pass the appId to the Shepard client. This is a workflow break.

**Proposal not in any agent file:** Shepard needs a lightweight **Ingest Status Panel** — a page (or sidebar widget) that shows:
- Active ingest jobs (source: sTC/hotfolder/SPW/import API), with real-time byte count and target DataObject
- Recent ingest completions (last 24h), with DataObject links
- Ingest errors (format mismatch, target Collection not found, permission denied) with actionable resolution links

This is M effort (WebSocket or SSE stream from backend, one new Vue component) and has a direct effect on researcher confidence in data completeness — "did my test data actually land?"

---

## My Overall Priority Stack

*Ordered by: (HMC deadline weight) × (UX impact per effort) × (blocks other proposals)*

### Sprint 1 — "Make the deadline + fix the live bugs" (weeks 1–3)

| Priority | Work | Effort | Rationale |
|----------|------|--------|-----------|
| 1a | API-P1: referenceIds fix | XS | Live bug; blocks every agent traversal and MCP tools |
| 1b | API-P2: flat GET /v2/data-objects/{appId} | S | N+1 call fix; immediately visible performance |
| 1c | API-P5: server-side status filter + pagination envelope | S | Scalability prerequisite for bulk toolbar |
| 1d | API-P7: ProblemJson ExceptionMapper | S | Error clarity; no dependencies |
| 1e | API-P8: human-readable OpenAPI tags | XS | Trivial; do it while touching the API layer |
| 2 | FAIR fields sprint (license, ORCID, access rights, funder + ROR) | M | HMC deadline prerequisite |
| 3 | Completeness Ring widget (UX-P4) | M | Ties together the FAIR fields; HMC deadline |
| 4 | Channel Quality Score Surface (Analytics-P1) | S | Zero deps; surfaces existing AI work; high visibility |

**Sprint 1 output:** HMC deadline met. Live bugs fixed. Existing AI investment visible to users. API clients (MCP, programmatic) no longer 404ing on referenceIds.

---

### Sprint 2 — "Scale the curator workflow" (weeks 4–6)

| Priority | Work | Effort | Rationale |
|----------|------|--------|-----------|
| 5 | Bulk row selection + action toolbar (UX-P1 / MfgQ-P8) | M | 4-agent convergence; curator workflow |
| 6 | Status vocabulary extension (MfgQ-P2 + Ontologist-P6 + Strategy-P8) | M | Prereq for quality gate; one migration |
| 7 | CHAMEO + SSN/SOSA manifest (Ontologist-P3 + MfgQ-P10) | S | Two TTL entries; semantic spine |

**Sprint 2 output:** Bulk curation unlocked. Status vocabulary ready for quality gate. Semantic ontology complete enough for MFFD annotations.

---

### Sprint 3 — "Provenance + quality" (weeks 7–10)

| Priority | Work | Effort | Rationale |
|----------|------|--------|-----------|
| 8 | Ancestor-chain endpoint (API-P4 / all 6-agent cluster) | M | One Cypher query; unblocks all UI uses |
| 9 | Linear timeline strip UI (UX-P5 companion) | M | Primary UX for ancestor-chain endpoint |
| 10 | Predecessor-Status Gate (MfgQ-P1 + shepard-plugin-quality) | M | EN 9100 quality progression lock |
| 11 | Provenance Gap Detector (Ontologist-P8 / Analytics-P3) | M | Cypher-based; no ML deps |

**Sprint 3 output:** Full provenance trail navigable from any DataObject. Quality gates enforced. Gap detection visible to researchers.

---

### Sprint 4 — "Ingest + discovery" (weeks 11–14)

| Priority | Work | Effort | Rationale |
|----------|------|--------|-----------|
| 12 | Ingest Status Panel (new — not in any agent file) | M | Critical adoption signal for new institutes |
| 13 | Global Entity Search (UX-P8 / Strategy-P9) | M | 2-agent convergence; replaces manual navigation |
| 14 | QuantifiedAnnotation (Ontologist-P1 / Analytics-P5) | M | Numeric values + units; CHAMEO prerequisite |
| 15 | AnnotatableFile bridge (Ontologist-P4 / MfgQ-P6) | M | File-level semantic annotation; NDT scan use case |

**Sprint 4 output:** New institute onboarding unblocked. Annotation expressiveness complete.

---

### Sprint 5 — "Plugin ecosystem + publishing" (weeks 15–20)

| Priority | Work | Effort | Rationale |
|----------|------|--------|-----------|
| 16 | LLM Import Manifest Generator Phase 1 (Analytics-P9 / API-P3) | M | POST /v2/import/jobs fixes critical dead end; LLM phase is additive |
| 17 | shepard-plugin-publisher Zenodo-first (RDM-P6 / Strategy-P2 / Ecosystem-EP-05) | L | 3-agent convergence; FAIR publishing loop closes |
| 18 | PDF Auto-Annotation Suggestions (Analytics-P4) | M+tuning | Ship integration; budget tuning sprint separately |
| 19 | FAIR-Compliant PID Tombstone (RDM-P7) | M | HTTP 410 Gone; PID registry hygiene |

**Sprint 5 output:** Full FAIR publishing loop (ingest → annotate → completeness check → publish to Zenodo). PID lifecycle managed.

---

### Deferred (not in 20-week horizon without dedicated resourcing)

| Proposal | Why deferred |
|----------|-------------|
| Side-by-side timeseries comparison (UX-P6, EP-10) | 6–8 weeks real effort; needs dedicated charting engineer |
| Video multi-track annotation editor (UX-P10) | L–XL; video plugin must ship first |
| Semantic embedding discovery (Analytics-P8) | L; needs pgvector setup + plugin-ai |
| AI audit narrative generator (Analytics-P7) | M+; plugin-ai dep; low urgency |
| Embed-and-share Collection card (EP-03) | 2–3 weeks; auth/CORS complexity underestimated |
| Conference-mode demo layer (EP-04) | Novel UX surface; not in any critical path |
| AI Anomaly → NCR Auto-Create (MfgQ-P9) | CHALLENGED → redirected to NTF1 notification + 1-click NCR |
| Unhide AI-Enhanced Feed Enrichment (Analytics-P11) | CHALLENGED → conditional on per-collection review gate being shipped first |
| Shop Floor Template Mode DSL fork (MfgQ-P7) | CHALLENGED → redirected to density preference + sticky NCR FAB (S effort, Sprint 2) |

---

*Written by: Core Tech & UX Auditor*
*Proposals reviewed: 78 across 8 agent files*
*Date: 2026-05-21*
