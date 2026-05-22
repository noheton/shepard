# Ecosystem Advocate — Feature Proposals

_Lens: external adoption, demo experience, competitive differentiation, conference presence, upstream strategy._
_Grounded in findings from 7 peer agents (ux-auditor, data-ontologist, api-scrutinizer, manufacturing-quality, research-data-manager, strategy-advisor, analytics-ai)._

---

## EP-01 — `make demo` One-Liner

**Problem it solves:** The strategy-advisor agent identified that Shepard has no runnable external demo. A potential adopter must read docs, configure env vars, and seed data manually. The ecosystem-advocate analysis confirms the "minimum viable external demo" is the most fixable non-code gap. The LUMEN showcase Python seed script (`examples/lumen-showcase/seed.py`) exists but is not discoverable or self-contained.

**What it looks like:** A single `make demo` target at repo root that:
1. Pulls pre-built images from GHCR (no local build required)
2. Starts all services via `infrastructure/docker-compose.override.yml`
3. Runs `examples/lumen-showcase/seed.py` automatically
4. Prints `Shepard is live at http://localhost:8090 — log in with demo/demo` to stdout
5. A companion `README-demo.md` at repo root (linked from the main `README.md`) explains what the demo shows: the TR-004 anomaly → investigation → repair → retest story, what to click, what to expect.

The demo account (demo/demo) is pre-created by the seed and has READ permissions on the LUMEN collection.

**Plugin or core?** Core — Makefile target + seed script update + README.

**Effort:** S (2–3 days). The infrastructure is all there. The work is wiring and testing the single command end-to-end.

**Domain impact:** Every conference presentation, every pilot conversation, every cold-read of the GitHub repo. First-impressions multiplier. Eliminates the "I tried to run it and gave up" adoption failure mode.

**Cross-finding hook:** strategy-advisor §"Honest Risk" (adoption gap most fixable non-code gap); ux-auditor §"Dead ends" (nothing guides a new user after login); ecosystem-advocate §"Ecosystem Expansion Checklist Tier 1."

---

## EP-02 — Live FAIR Scorecard Widget

**Problem it solves:** The research-data-manager agent scored Shepard's current FAIR compliance at ~1.7/3 composite. The specific blockers are visible — missing `license` field, no accessRights enum, ORCID not stamped at creation time, no embargo support. A researcher opening a Collection today has no indication of what FAIR metadata is missing. This is a competitive disadvantage vs. Coscine, which surfaces a NFDI-aligned metadata completeness indicator.

**What it looks like:** A compact "FAIR health" panel on the Collection detail page (basic mode visible — this is not an advanced-only feature). Four colored indicators (F/A/I/R), each with a tooltip listing the 1–3 specific fields that are blocking full compliance. Clicking an indicator opens the relevant metadata edit dialog directly. The panel computes from: presence of license, presence of PID, presence of at least one ORCID-linked creator, presence of at least one semantic annotation with a controlled-vocabulary IRI, and accessRights value.

On the backend: a `GET /v2/collections/{appId}/fair-score` endpoint returns a structured JSON object `{f: {score: 2, max: 3, gaps: [...]}, a: {...}, i: {...}, r: {...}}`. The gaps list contains machine-readable codes (`"license_missing"`, `"no_pid"`, etc.) that the frontend maps to human strings.

**Plugin or core?** Core — the FAIR dimensions are universal to all Shepard deployments.

**Effort:** M (1 week backend + 3 days frontend). The score logic is straightforward enumeration of field presence; the UI is a small panel component.

**Domain impact:** FAIR compliance is a mandatory checkbox for DFG, Horizon Europe, and Clean Aviation JU project reporting. Surfacing gaps in-app converts a reporting afterthought into an in-workflow nudge. Direct response to the HMC Project Call 2026 criterion (deadline 06 July 2026).

**Cross-finding hook:** research-data-manager §"FAIR Gap Analysis" (KIP1e not shipped); data-ontologist §"Annotation Playbook" (no vocabulary enforcement); strategy-advisor §"Clean Aviation KPI Mapping."

---

## EP-03 — Embed-and-Share Collection Card

**Problem it solves:** There is currently no way to share a Shepard Collection with an external audience without giving them a login. A conference poster, a publication supplementary page, or a project website has no embeddable artifact. Kadi4Mat and SciCat both offer public dataset landing pages. Shepard's Unhide/HKG feed (`GET /v2/unhide/feed.jsonld`) proves the metadata is already serializable — it just isn't surfaced as a human-readable public page.

**What it looks like:** Two surfaces:
1. **Public landing page** at `/public/collections/{appId}` (no auth required). Renders Collection name, description, FAIR scorecard (read-only), PID/DOI badge, license badge, creator ORCIDs, and a DataObject count. DataObject list is visible only if accessRights = OPEN. A "Request Access" button (email-based) appears for RESTRICTED collections.
2. **Embed card** — an `<iframe>`-embeddable `GET /public/collections/{appId}/card` endpoint that returns a self-contained HTML card (no JS framework) with the same summary fields. Width: 480px, height: 200px. Suitable for embedding in a wiki, a DLR eLib publication page, or a conference poster QR code target.

For DRAFT/IN_REVIEW collections, the public page shows "This dataset is not yet published."

**Plugin or core?** Core — this is foundational discoverability infrastructure.

**Effort:** M (1 week). The metadata is all available; this is a rendering and auth-bypass surface, not new data work.

**Domain impact:** Every publication that cites Shepard data, every conference poster with a QR code, every project website. This is the "data has a home on the web" moment that transforms Shepard from an internal tool to a citeable data repository.

**Cross-finding hook:** research-data-manager §"Accessible" gap (no public landing page); ecosystem-advocate §"Competitive Landscape" (SciCat and Kadi4Mat both have public pages); api-scrutinizer §"Missing Operations" (no public read surface).

---

## EP-04 — Conference-Mode Demo Narrative Layer

**Problem it solves:** The LUMEN seed dataset is labeled "NOT REAL DLR/LUMEN DATA" — a necessary disclaimer that also deflates its impact in a live demo. A conference presenter needs a guided narrative: "watch what happens when I click TR-004" — but today, TR-004 looks like any other DataObject. The anomaly, the investigation branch, the repair, and the re-test are all in the graph, but nothing surfaces the story to a first-time viewer.

**What it looks like:** A lightweight "Story Mode" toggle (hidden behind `?story=true` query param or an instance-admin feature flag) that:
1. Adds a persistent top-of-page breadcrumb narrative banner: e.g., "You are viewing the LUMEN Engine Test Campaign — 15 hot-fire runs. TR-004 recorded an anomaly at t=8s. Follow the investigation chain →"
2. Highlights TR-004 in the DataObjects list with a `status: ANOMALY_DETECTED` badge and a colored left border.
3. On the TR-004 DataObject detail page, adds a "Story" panel explaining what happened, what the vibration spike looked like, and the corrective action chain — with direct links to the investigation DataObject, the TR-005 hold, and TR-006 re-test.
4. The story content is injected via a `story.json` file in the seed script, stored as a special-purpose `ShepardStory` StructuredDataReference.

This is not a general feature — it is a demo-layer that can be turned off for production instances. The `story.json` format is documented so other demo datasets (MFFD AFP robot run) can use the same narrative layer.

**Plugin or core?** Core feature flag, story content in seed.

**Effort:** M (1 week). The banner and highlight logic is frontend-only; the `ShepardStory` StructuredDataReference is a new container type (or a special annotation convention).

**Domain impact:** Every live demo, every trade show, every funding review where someone opens a laptop and says "let me show you." Converts a database browser into a guided narrative.

**Cross-finding hook:** strategy-advisor §"LUMEN Showcase Publishable" (story needs to be told, not just seeded); ux-auditor §"Dead ends" (no guidance after login); ecosystem-advocate §"Ecosystem Expansion Checklist Tier 1."

---

## EP-05 — shepard-plugin-publisher (Zenodo / InvenioRDM Export)

**Problem it solves:** The research-data-manager agent specified a `shepard-plugin-publisher` design (Zenodo/InvenioRDM/DaRa/B2SHARE adapters). Today, publishing a Shepard dataset to a public repository requires manual export and re-upload. KIP (HMC Kernel Information Profile) + DOI minting is in-place, but there is no push-to-external-repo pathway. Coscine has direct Zenodo integration. This gap is visible to FAIR data stewards and funding body evaluators.

**What it looks like:** A plugin that adds:
1. `GET /v2/admin/publisher/config` + `PATCH` — configure target repo (Zenodo or InvenioRDM URL, API token in runtime `:PublisherConfig`)
2. `POST /v2/collections/{appId}/publish` — triggers async job: validates license + accessRights, generates DataCite XML metadata, calls RO-Crate export (V2d), uploads to target repo, retrieves DOI, writes DOI back to Collection `pid` field, emits `PUBLISH_COMPLETE` notification
3. A "Publish to Repository" button on the Collection page (advanced mode only, gated on `license` and `accessRights` both set)
4. A publish status indicator showing the last publication timestamp and the resolved DOI badge

The plugin ships with a Zenodo adapter by default; InvenioRDM is a second adapter (Zenodo is InvenioRDM underneath, so most of the HTTP client is shared). DaRa and B2SHARE are stubs.

**Plugin or core?** Plugin — `shepard-plugin-publisher`. The core exports (RO-Crate V2d) stay core; only the external push adapters are plugin.

**Effort:** L (2–3 weeks). The RO-Crate export already exists; the plugin adds the push layer, async job management, and config UI.

**Domain impact:** Directly closes the "R: 0.9/3" FAIR gap. Enables DFG/Horizon data management plan compliance ("data will be deposited in Zenodo"). Conference demo moment: live publish from Shepard to Zenodo in 2 clicks.

**Cross-finding hook:** research-data-manager §"Plugin-Publisher Design Spec"; strategy-advisor §"Clean Aviation KPI Mapping" (open data deposition required); api-scrutinizer §"Missing Operations" (no publish endpoint).

---

## EP-06 — Controlled-Vocabulary Annotation Enforcement (Annotation Playbook UI)

**Problem it solves:** The data-ontologist agent found that `attributes` on DataObject are a free-text `Map<String,String>` with zero schema enforcement, while `SemanticAnnotation` stores IRI pairs that most researchers never use because the UI buries them. This means annotation keys like `bench`, `propellant`, `test_engineer` across 15 LUMEN DataObjects are semantically invisible to any harvester or search engine. Kadi4Mat has a metadata schema system (KTS — Kadi4Mat Template System) that enforces key vocabulary per collection type. This is one of Kadi4Mat's most cited advantages.

**What it looks like:** A "Collection Annotation Schema" feature:
1. A Collection-level schema editor (advanced mode) that defines: required attribute keys, optional attribute keys, per-key type (string / number / datetime / controlled-vocab), and per-key vocabulary (inline list or SPARQL endpoint for IRI lookup).
2. When a researcher creates or edits a DataObject, required keys are pre-populated as empty fields with placeholder text and a red asterisk. Controlled-vocab keys show a dropdown populated from the defined vocabulary (e.g., `propellant: [LOX/RP-1, LOX/LH2, N2O4/UDMH]`).
3. On the FAIR scorecard (EP-02), collections without a schema score lower on Interoperability.
4. The schema is stored as a `CollectionSchema` StructuredDataReference (or a new Neo4j entity — design doc needed).

This is not a new annotation *type* — it enforces the existing `attributes` dict and optionally suggests semantic IRIs for each key.

**Plugin or core?** Core — schema enforcement is universal.

**Effort:** L (2–3 weeks for full round-trip; S for a read-only validation-warning approach as interim).

**Domain impact:** NFDI4ING metadata completeness requirement. CHAMEO annotation compliance for MFFD characterization data. Closes the largest single gap in the "Interoperable" FAIR dimension. This is the feature that moves Shepard from "annotatable" to "annotation-governed."

**Cross-finding hook:** data-ontologist §"Annotation Playbook" + §"Vocabulary Conflict Resolution"; research-data-manager §"Interoperable" gap (I: 2.0/3); ux-auditor §"AddAnnotationDialog pain" (painfully manual for 100 objects).

---

## EP-07 — MCP Toolset (LLM-Native Shepard Client)

**Problem it solves:** The api-scrutinizer agent documented an active bug: the MCP server passed `referenceIds` to `get_data_object` and got 404s because `referenceIds` contains `BasicReference` node IDs, not `DataObject` IDs. This naming confusion exists in `DataObjectIO`. Beyond the bug, there is currently no documented, tested MCP tool surface that an LLM agent (Claude, GPT-4o, or a local model via GWDG/SAIA) can reliably use to explore Shepard data. The analytics-ai agent identified LLM-generated import manifests as a high-feasibility quick win — but this requires a working MCP toolset first.

**What it looks like:**
1. Fix `referenceIds` → rename to `dataObjectReferenceNodeIds` in `DataObjectIO` (or remove and replace with a `GET /v2/data-objects/{appId}/references` endpoint that returns proper `DataObject` appIds). This is the bug fix — it belongs in the same PR.
2. A documented `shepard-mcp` tool specification (`docs/reference/mcp-tools.md`) covering: `list_collections`, `get_collection`, `list_data_objects`, `get_data_object`, `get_timeseries_channels`, `get_timeseries_window`, `get_file`, `create_annotation`, `suggest_annotations`. Each tool has a worked JSON example.
3. The existing MCP server (wherever it lives) is updated to match the spec and regression-tested against the LUMEN dataset.
4. A `POST /v2/data-objects/{appId}/suggest-annotations` endpoint (the analytics-ai quick-win spec: sends DataObject metadata to the configured AI plugin, returns 3–5 suggested key-value pairs with confidence scores).

**Plugin or core?** Core (API fix + MCP spec). The `suggest-annotations` endpoint depends on `shepard-plugin-ai`.

**Effort:** M (1 week for bug fix + spec + suggest-annotations endpoint; MCP server test harness adds another 3 days).

**Domain impact:** Enables the "agentic import manifest" workflow (analytics-ai §"LLM Manifest Generation"). Makes Shepard a first-class citizen in LLM-native scientific workflows — a growing conference topic at RDA and EOSC Symposium. Positions Shepard ahead of Kadi4Mat and SciCat, neither of which has a documented MCP tool surface.

**Cross-finding hook:** api-scrutinizer §"referenceIds Active Bug" + §"Missing Operations"; analytics-ai §"Quick-Win Spec" (suggest-annotations); ecosystem-advocate §"Where Shepard Lags" (no LLM-native tool surface documented).

---

## EP-08 — Upstream Contribution Bundle (A1 + Security Fixes)

**Problem it solves:** The ecosystem-advocate analysis identified 10 candidates for upstreaming to `gitlab.com/dlr-shepard/shepard`. Currently none have been contributed back. This is a missed opportunity: upstreaming low-controversy fixes builds goodwill with the upstream maintainers, reduces the long-term merge-conflict surface, and signals to potential adopters that DLR is a responsible steward of the open-source relationship. The strategy-advisor noted the upstream relationship as a risk ("this fork could drift into an unmaintainable divergence").

**What it looks like:** A curated bundle of 4–6 PRs to upstream, in priority order:
1. **A1 series UX improvements** (sentence-case buttons, cursor pointer on rows, search empty states, sidebar type cast hoist) — pure frontend, zero API change, zero risk to upstream.
2. **Security hardening** (SpotBugs findings, OWASP dependency updates, Trivy fixes) — upstream benefits directly; framed as "security hygiene PRs."
3. **`UserinfoService` fix** — whatever the current `M backend/src/main/java/de/dlr/shepard/auth/security/UserinfoService.java` change contains (read the diff before upstreaming to confirm it's compat-safe).
4. **Pagination consistency** — if the page+size vs. limit inconsistency (api-scrutinizer MAJOR finding) affects upstream endpoints, the fix belongs upstream too.
5. **OpenAPI @Tag cleanup** — removing numeric tag codes from generated SDK class names is a pure improvement with no behavior change.

Each PR is filed against upstream's main branch, references the DLR fork, and includes a test. The bundle is tracked in `aidocs/34-upstream-upgrade-path.md` as a new "Upstream Contributions" section.

**Plugin or core?** N/A — this is a contribution process, not a Shepard feature.

**Effort:** M (1 week to prepare PRs, test against upstream, and file). Ongoing maintenance: ~1 PR/month cadence.

**Domain impact:** Institutional credibility. Signals DLR as a responsible open-source actor. Reduces merge-conflict surface over time. Makes it easier for other institutes to adopt the fork knowing that DLR maintains upstream compatibility. Directly supports the "low-friction upgrade path" CLAUDE.md mandate.

**Cross-finding hook:** strategy-advisor §"Open-Source Strategy" (upstream relationship is a risk); ecosystem-advocate §"Upstream Contribution Candidates" (10-item table in Document A); api-scrutinizer §"Pagination Inconsistency" (MAJOR).

---

## EP-09 — NFDI4ING / metadata4ing Spotlight Page

**Problem it solves:** Shepard already ships metadata4ing support (m4i:ProcessingStep dual-typing, QUDT units, PROV-O export) and integrates with the Helmholtz Knowledge Graph via the Unhide plugin. This is genuinely ahead of the field — the strategy-advisor noted "nobody knows." The gap is not technical; it is content. There is no page, blog post, or demo that shows an NFDI4ING community member why Shepard is relevant to their work.

**What it looks like:** A documentation page at `docs/reference/nfdi4ing.md` (and matching `docs/help/nfdi4ing-quickstart.md`) that covers:
1. What metadata4ing terms Shepard uses natively (m4i:ProcessingStep, m4i:NumericalVariable, QUDT units table)
2. How the Unhide/HKG integration works: what the JSON-LD feed looks like, how HKG harvests it, how a researcher finds their data in the Knowledge Graph
3. A worked example using the LUMEN dataset: annotate TR-004 with `m4i:ProcessingStep`, publish, view in HKG
4. A "conformance table" showing which NFDI4ING metadata4ing properties Shepard populates automatically vs. requires manual entry

Separately: a short (400-word) blog post draft targeting the NFDI4ING community newsletter / HMC blog, framing Shepard as "the first research data management system to natively dual-type experimental process steps to metadata4ing in its graph database."

**Plugin or core?** Core docs + content.

**Effort:** S (3–4 days to write the docs + blog post draft; 1 day to validate the worked example against a running instance).

**Domain impact:** Directly relevant to the HMC Project Call 2026 (deadline 06 July 2026). Positions Shepard for NFDI4ING community discovery. The blog post is conference-ready content for DLRK and RDA Plenary. This is the highest-ROI content investment available: the technical work is already done.

**Cross-finding hook:** strategy-advisor §"ROI Model" (institutional visibility); research-data-manager §"Interoperable" (metadata4ing compliance); data-ontologist §"Annotation Playbook" (metadata4ing gap in ontology seeding); ecosystem-advocate §"Where Shepard Wins" (HKG/Unhide ahead of field).

---

## EP-10 — Side-by-Side Timeseries Comparison View

**Problem it solves:** The ux-auditor identified "no side-by-side timeseries comparison" as a top-5 highest-impact UX change. The analytics-ai agent noted that the LUMEN demo's most compelling moment — "compare the vibration channel across TR-001 through TR-006 and watch TR-004 spike" — is not currently possible in the UI. A researcher must export CSVs and use matplotlib. Kadi4Mat has a basic chart comparison view. SciCat does not. This is a differentiation opportunity.

**What it looks like:** A "Compare Channels" mode accessible from:
1. The Collection DataObjects list: checkboxes on 2–4 DataObjects → "Compare Timeseries" action in the bulk toolbar (requires EP's bulk-action capability, or a simpler "pin this DataObject" mechanism)
2. The Timeseries container page: a "Compare with…" button that opens a Collection-scoped DataObject picker

The comparison view renders a multi-panel chart (one panel per selected DataObject) with synchronized time axes. The same channel name is selected across all panels (e.g., "vibration_z" for TR-001, TR-002, TR-003, TR-004, TR-005, TR-006). The AI1b anomaly score is overlaid as a colored band on each panel if available.

State is persisted in the URL query string (DataObject appIds + selected channel + time range) so the view is shareable — a presenter can paste the URL into a slide.

**Plugin or core?** Core — this is a foundational analytics UX feature.

**Effort:** L (2 weeks frontend; 1 week backend for a `GET /v2/timeseries/compare` batched query endpoint that returns multiple channel windows in one response, avoiding N+1 requests).

**Domain impact:** The demo moment that makes a funding body lean forward. "Watch what happens at t=8 seconds across all 6 test runs." Also directly useful for MFFD: compare AFP layup temperature profiles across multiple panel production runs to identify process drift.

**Cross-finding hook:** ux-auditor §"Top 5 Changes" (#2); analytics-ai §"Anomaly Detection Opportunity" (AI1b overlay); strategy-advisor §"LUMEN Showcase Publishable" (the comparison is the publishable figure); api-scrutinizer §"5-Tuple Problem" (batched query reduces friction).

---

## EP-11 — Plugin Gallery Page (In-App + docs.shepard)

**Problem it solves:** Shepard ships 11 plugins, but there is no in-app or external page that lists them, explains what each does, and shows how to enable one. A new deployer reading the GitHub README has no way to discover that `shepard-plugin-spatial` or `shepard-plugin-hdf5` exists. This is a compounding adoption problem: the plugin model is the fork's primary differentiator, but it is invisible to external evaluators.

**What it looks like:**
1. **In-app plugin gallery** at `/admin/plugins` (instance-admin only): lists all discovered plugins (enabled and disabled), shows each plugin's name, description, version, enabled/disabled toggle, and a link to the plugin's docs. The data comes from `GET /v2/admin/plugins` (a new endpoint that iterates the ServiceLoader registry and returns plugin metadata from each plugin's `PluginDescriptor`).
2. **External gallery page** at `docs/reference/plugins.md` (already referenced in CLAUDE.md): a manually maintained table of all 11 plugins with 1-sentence description, install difficulty (S/M/L), and link to each plugin's `docs/` folder.
3. Each plugin's `docs/install.md` links back to the gallery page.

The `PluginDescriptor` interface (or annotation) is a 5-minute addition to the plugin SPI: `id`, `name`, `description`, `version`, `docsUrl`. Every plugin implements it; the admin endpoint aggregates them.

**Plugin or core?** Core (admin endpoint + in-app gallery UI) + conventions (PluginDescriptor per-plugin).

**Effort:** M (1 week: PluginDescriptor interface + admin endpoint + in-app page + docs/reference/plugins.md).

**Domain impact:** Removes the "I didn't know that existed" adoption failure. Makes the plugin model legible to non-contributors. A conference presenter can say "Shepard has 11 plugins — here's the gallery" and click through. Directly supports the CLAUDE.md "plugin-first" mandate by making the plugin ecosystem visible.

**Cross-finding hook:** ecosystem-advocate §"Ecosystem Expansion Checklist Tier 1"; strategy-advisor §"Plugin Architecture as Differentiator"; ux-auditor §"Missing Affordances" (no plugin discoverability in admin UI).

---

## EP-12 — MFFD AFP Robot Run — First Real Dataset Onboarding

**Problem it solves:** The LUMEN dataset is synthetic and labeled as such. The MFFD AFP robot run dataset (arriving ~2026-05-26 per project memory) is the first real DLR industrial dataset that can be seeded into Shepard. Its onboarding is not just a data import — it is the platform's first genuine stress test with real AFP timeseries (TCP thermal trail, robot axis positions, temperature field), real CHAMEO annotations, and real provenance from a process that produced an actual CFRP panel.

This is the demo that makes everything real: JEC World presentations can reference actual measured data; the FAIR scorecard runs against an actual production dataset; the comparison view (EP-10) shows actual process drift between panel runs.

**What it looks like:**
1. A `examples/mffd-afp/seed.py` that ingests the real dataset (or a cleared synthetic approximation if the real data cannot be open-sourced) with: Collection per panel production campaign, DataObject per robot run, TimeseriesReference for TCP temperature channel, SpatialDataReference for the 3D thermal trail, FileReference for the raw NDT scan PDF, SemanticAnnotations using CHAMEO + metadata4ing + QUDT terms.
2. A `examples/mffd-afp/README.md` explaining the process: what AFP is, what the thermal trail shows, what "good" vs. "anomalous" looks like in this dataset.
3. A `docs/help/mffd-afp-walkthrough.md` that guides a researcher through exploring the dataset in Shepard — the task page equivalent of the LUMEN story.
4. The seed is gated on a `MFFD_DATA_PATH` env var so it only runs when the data is available. The `make demo` target (EP-01) uses LUMEN; an `MFFD_DATA_PATH=/path/to/data make demo-mffd` target uses the AFP dataset.

**Plugin or core?** Seed script + docs — no code change to core. Exercises: spatial plugin, semantic annotation, RO-Crate export, FAIR scorecard.

**Effort:** M (1 week, dependent on data availability). Blocked on dataset arrival (~2026-05-26).

**Domain impact:** The single highest-impact deliverable for the next funding review. Real data, real process, real CFRP panel. JEC World 2025 Innovation Award story told in a live demo. Makes every other feature proposal more credible because there is a real dataset to demonstrate it on.

**Cross-finding hook:** strategy-advisor §"LUMEN Showcase Publishable" (real data is the upgrade from synthetic); data-ontologist §"MFFD Entity Blueprint" (AFP process chain annotation design); manufacturing-quality §"EN 9100 Gap Table" (first real audit trail stress test); analytics-ai §"Training Data Inventory" (AFP thermal data as first ML training candidate).

---

## Priority Order

| # | Proposal | Effort | Impact | Start Condition |
|---|----------|--------|--------|-----------------|
| 1 | EP-01 `make demo` | S | Multiplier | None |
| 2 | EP-07 MCP Toolset + referenceIds fix | M | Bug fix + unlock | None |
| 3 | EP-09 NFDI4ING Spotlight Page | S | HMC deadline | None |
| 4 | EP-02 FAIR Scorecard Widget | M | FAIR compliance | None |
| 5 | EP-03 Embed-and-Share Collection Card | M | Discoverability | None |
| 6 | EP-10 Side-by-Side Timeseries Comparison | L | Demo moment | None |
| 7 | EP-11 Plugin Gallery | M | Adoption | None |
| 8 | EP-05 shepard-plugin-publisher | L | FAIR R dimension | EP-02 shipped |
| 9 | EP-06 Controlled-Vocab Annotation | L | NFDI4ING compliance | Schema design doc |
| 10 | EP-04 Conference-Mode Story Layer | M | Presentation | EP-01 shipped |
| 11 | EP-08 Upstream Contribution Bundle | M | Credibility | PRs drafted |
| 12 | EP-12 MFFD AFP Onboarding | M | Real data | Dataset arrival |

EP-01 (make demo) and EP-07 (MCP + bug fix) are the two items with no blockers and immediate multiplier effects. EP-09 is time-sensitive (HMC deadline 06 July 2026). Everything else can be sequenced in parallel tracks: FAIR track (EP-02, EP-05, EP-06), Demo track (EP-03, EP-04, EP-10, EP-12), Ecosystem track (EP-08, EP-09, EP-11).
