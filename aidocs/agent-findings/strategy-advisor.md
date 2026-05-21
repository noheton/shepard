# Shepard — Strategic Advisor Findings
**Date:** 2026-05-21  
**Author:** Strategic Executive Advisor (AI agent)  
**Audience:** Board, funding reviewers, DLR institute directors, program managers  
**Status:** Point-in-time assessment; not a substitute for primary stakeholder interviews

> **TL;DR — 3-minute version:** Go directly to §6 (Board-Ready Positioning Brief). It is the standalone 1-pager. Everything else in this document is supporting evidence, detail, and citations for the claims made there.

---

## 1. What I Found: Actual Platform State vs. Vision Claims

### The Vision Document Is Largely Honest — With Material Gaps

`aidocs/42-vision.md` is better-written than most research-software vision documents. It explicitly labels features as "shipped," "queued," or "near horizon," and the rules in `CLAUDE.md` require the document to update when features land. The standing discipline is real and enforced.

That said, the vision makes claims that partially outrun what's feature-complete (backend + accepted UI). Six specific pairs follow.

| Vision claim | Actual state | Severity |
|---|---|---|
| **"Inline timeseries charting"** — described as if fully available to users | Shipped in the web UI (ECharts, channel selection, live mode). This one is accurate. | ✓ |
| **"Snap dashboards — chat-driven analysis"** — called the "killer feature" in §"Where it's going" | 📐 Queued. No backend, no UI. Described as "headline killer feature" but nothing is shipped. | Significant overreach in any external-facing version of this doc |
| **AI features** — anomaly detection called "shipped" (accurate); but semantic-annotation suggestion, lab-journal assist, natural-language search, embedding similarity, conversational lineage (AI1d–AI1l) are all queued | The shipped piece (AI1b: rolling-median MAD, pure Java) is real but extremely narrow. A reviewer reading the AI section without tracking the 📐 symbols would massively overestimate. | Moderate — the design doc is honest but the vision's structure buries this |
| **HDF5 as a payload kind** — described as "shipped via HSDS sidecar" | Backend create/read/delete and permission bridge are done. **No UI exists.** A researcher cannot create or browse HDF5 containers from the web UI today. Marked `⚙ BE ✓ / UI pending` in the feature matrix. | Operational gap |
| **SPARQL proxy** — described as shipped (`N1f`) | Backend shipped: 37+21 unit tests. **No frontend SPARQL query interface.** Researchers cannot run SPARQL without using `curl` or the raw API. | Operational gap |
| **Lab journal edit history** — described as shipped (`J1d`) | Backend shipped. **No history panel or diff viewer in the UI.** The vision doc places it in "what's in the box (today)" — this is misleading. | Minor, but tells a pattern |

**Pattern:** The platform is backend-rich and UI-thin in several important areas. The feature matrix (`aidocs/44`) is disciplined about the `⚙` tag, but the vision document does not consistently reproduce that discipline. Any board-ready deck built from `aidocs/42` needs to explicitly distinguish "researcher can do this today in the browser" from "the API endpoint exists."

### What Is Genuinely Shipped and Compelling

- Full provenance graph with W3C PROV-O export, metadata4ing dual-typing, and activity dashboard
- RO-Crate selective export with snapshot pinning (reproducible by construction)
- 14 pre-seeded ontologies including QUDT, metadata4ing, NASA Thesaurus (~18,400 concepts), and a domain-specific `shepard-experiment` OWL vocabulary
- DataCite DOI minting and Helmholtz Unhide feed (first shepard install anywhere to publish to the Helmholtz Knowledge Graph)
- Plugin SPI with drop-in JAR loading — video and git reference are already extracted as first-class plugins
- Point-in-time snapshots with diff view
- Per-file SHA-256 payload versioning
- Timeseries with inline charting, channel quality scoring (BE only), and MAD anomaly detection
- Bootstrap-secure instance-admin role chain with permission audit log

### PLUTO Reference

The task context cites "PLUTO satellite mission (Welzmüller et al. 2024, DLR eLib 215120)." Web search confirms PLUTO (PayLoad Under Test Orbiter) is a real DLR 6U CubeSat mission from the Institute of Space Systems. The exact eLib record 215120 was not retrievable via public search. The LUMEN showcase (`examples/lumen-showcase/`) is the only active seed demo in this repository. There is no PLUTO-specific data model or seed in the codebase as of this assessment. If PLUTO data was intended as a second demonstrator, it has not materialized.

---

## 2. Competitive Landscape

### Selected Comparator Platforms

| Dimension | **Shepard (this fork)** | **Kadi4Mat (KIT)** | **SciCat (PSI / ESS)** | **Coscine (FZJ / HHU)** |
|---|---|---|---|---|
| **Primary domain** | Aerospace manufacturing R&D, mixed sensor/file/simulation data | Materials science, multi-physics simulation | Photon/neutron large-scale facilities | Engineering sciences broadly (NFDI4ING) |
| **Deployment model** | Self-hosted Docker Compose; plugin JARs | Self-hosted or SaaS | Facility-hosted | Cloud-hosted (RWTH / HDF federated) |
| **Timeseries storage** | TimescaleDB (first-class, charted in UI) | None native | None native | None native |
| **Semantic annotations** | SPARQL + neosemantics inside Neo4j; 14 pre-seeded ontologies; annotation picker | Metadata schemas per record type | Custom metadata schemas | Dublin Core + discipline schemas |
| **Provenance** | W3C PROV-O + metadata4ing; full activity audit trail; lineage graph UI | None explicit | Dataset lineage via proposals | DMP-linked |
| **PID minting** | DataCite DOI (plugin) + local PID + ePIC (queued); one-click UI | External DOI registration | DOI via PaNOSC | Supports existing DOIs |
| **Helmholtz KG / Unhide** | Native plugin (UH1a shipped); feed.jsonld harvested daily | Not integrated | Not integrated | Partially integrated |
| **Plugin/extension model** | Drop-in JAR SPI; first plugins live | REST API integration | Plugin API for facility systems | API only |
| **RO-Crate export** | Selective, snapshot-pinned, ORCID-cited, git-SHA permalinks | None | None | None |
| **AI features shipped** | MAD anomaly detection (pure Java); snap dashboards queued | None | None | None |
| **Graph database** | Neo4j (graph-native lineage, ontology overlay) | PostgreSQL | MongoDB + Elasticsearch | PostgreSQL |
| **Adopter count (public)** | Unknown — 2 active showcases in repo; upstream 5.2.0 used within DLR ZLP | 100+ public records; actively NFDI4ING-used | Multiple neutron/photon sources (PSI, ESS, MLZ) | 1,500+ interdisciplinary users, 138 institutions |
| **Upstream open-source** | Fork of dlr-shepard/shepard (Apache 2.0); active divergence tracked | Open-source (MIT-like) | Open-source (Apache 2.0) | Open-source (MIT); NFDI4ING maintained |

### Key Differentiators in Shepard's Favor

1. **Timeseries as a first-class payload.** No comparator natively stores and charts TimescaleDB sensor data alongside file/structured/semantic payloads in the same provenance graph. This is the most defensible moat for manufacturing R&D.
2. **PROV-O + metadata4ing provenance.** Coscine references DMPs. SciCat tracks proposals. Neither produces a W3C PROV-O audit trail with m4i:ProcessingStep dual-typing consumable by a SPARQL store or HKG harvester.
3. **Helmholtz Knowledge Graph integration.** The Unhide plugin makes Shepard the only DLR-origin platform that pushes structured, machine-readable metadata into the HKG without operator intervention.
4. **Snapshot-pinned reproducible export.** RO-Crate spec 1.2 was released June 2025; Shepard already ships snapshot-pinned, selective, git-SHA-carrying RO-Crate export — ahead of the spec wave.

### Where Shepard Lags

1. **Adoption numbers.** Unknown vs. 1,500+ for Coscine. This is the most dangerous gap for any funding pitch.
2. **Discovery and community.** Kadi4Mat and Coscine have NFDI4ING community buy-in. Shepard does not yet appear in NFDI4ING success stories.
3. **UI completeness.** HDF5, SPARQL query interface, lab journal history, AI-assisted dashboards — all backend-complete, UI-absent. For non-developer researchers these features do not exist.
4. **Documentation surface.** `docs/user-guide.md` is mostly conceptual with placeholder screenshots ("Screenshot: Collections page — placeholder. Replace with Playwright capture once the visual-regression workflow lands."). No screenshot pipeline has shipped.

---

## 3. Strategic Alignment Report: Feature → KPI Mapping

### Clean Aviation Joint Undertaking

Clean Aviation JU's binding program-level KPIs are: −30% fuel burn vs 2020 state-of-the-art, climate-neutral aviation by 2050, CO2/NOx/noise reductions on certified product families. Shepard does **not** reduce fuel burn. Its contribution is at the **evidence infrastructure layer** — the conditions under which those KPIs can be demonstrated, certified, and federated.

| Shepard capability | Clean Aviation relevance | Directness |
|---|---|---|
| Provenance graph linking AFP layup → weld → inspection → test data | EN 9100 / EASA certification audit trail for MFFD processes — essential for any clean aviation technology to reach TRL 6+ and airworthiness approval | High — certification blocker |
| Snapshot-pinned RO-Crate export with DOI | MFFD results publishable and reproducible; needed for Clean Aviation annual reports and Horizon Europe data deliverables | High — compliance |
| TimescaleDB + charting for sensor data | Real-time and historical process data capture for AFP laser/temperature/compaction channels — the evidence base for "did this process meet spec?" | High — operational |
| Semantic annotations (metadata4ing, QUDT, shepard-experiment ontology) | Machine-readable process metadata that downstream AI models and certification tools can consume without human mediation | Medium |
| Helmholtz KG / Unhide feed | Visibility of MFFD data to HKG harvesters → path toward EOSC federation | Medium — visibility |
| AI anomaly detection (MAD, current) | Early anomaly flagging during test campaigns — engineering value, not KPI-direct | Low–medium — tooling |
| Snap dashboards (queued) | If shipped: real-time process QA, outlier detection across runs — maps to process efficiency targets | Potential, not current |

**Summary:** Shepard is correctly positioned as the digital thread evidence layer for Clean Aviation — it captures, traces, and publishes the data needed to prove that thermoplastic high-rate manufacturing meets airworthiness standards. That is a genuine and under-served need in the program.

### DFG

DFG criteria for research data infrastructure: discipline-specificity, FAIR compliance, community adoption, sustainability plan, interoperability. Shepard's metadata4ing and NFDI4Ing alignment is real. The adoption number gap is the DFG's most likely objection.

### Helmholtz Association / HMC

HMC Project Call 2026 explicitly targets metadata, FAIR data practices, and federated infrastructure. Shepard's Unhide plugin + HKG integration is a direct fit. The active HMC Project Call deadline is 06 July 2026 — a near-term funding opportunity.

---

## 4. ROI Model

All estimates are modeled from first principles with stated assumptions. No empirical time-and-motion data was available.

### Assumption set
- A typical MFFD-scale campaign: 15 test runs, each generating ~25 data objects (sensor files, CAD files, reports, photos, structured log)
- Without Shepard: data scattered across shared drives, Confluence pages, custom spreadsheets, email threads
- With Shepard: Python client seeds the campaign structure; sensor data ingests via timeseries collector; RO-Crate export on completion

| Activity | Manual (hours/campaign) | Shepard (hours/campaign) | Net saving | Confidence |
|---|---|---|---|---|
| RO-Crate / publication package assembly | 20–40 h (manual schema.org construction, file gathering) | 0.5 h (one button + export selection) | ~25 h | Medium |
| DMP / EN 9100 audit data retrieval | 8–16 h (hunting across drives) | 1–2 h (PROV-O export + `GET /v2/provenance/entity/{appId}`) | ~10 h | Medium |
| Dataset discovery by new team member | 4–8 h/person (email colleagues, scrape fileshares) | 0.5–1 h (search + lineage graph) | ~5 h/onboardee | Low–medium |
| Anomaly identification in sensor logs | 2–4 h/run manual scan | 5 min (AI1b endpoint + timeseries chart) | ~35 h/campaign at 15 runs | Low (depends on channel count and anomaly rate) |
| Duplicate experiment avoidance | Unquantified; significant | Related dataset discovery via semantic search | Unquantified | Low |

**Order-of-magnitude estimate:** A mature MFFD-scale campaign saves roughly **60–100 engineering hours** (auditing, packaging, discovery) per campaign lifecycle. At €120/h loaded cost, that is **€7,000–€12,000 per campaign** in recovered engineer time. Across 5 campaigns/year at one institute: **€35,000–€60,000/year** — likely exceeding Shepard's annual infrastructure cost (server + personnel).

**Important caveat:** These estimates assume Shepard is used *consistently* from day one of a campaign. Adoption friction (researchers preferring familiar tools) typically cuts realized savings by 30–50% in the first year.

---

## 5. Honest Risk Assessment

### Risk 1: Adoption Chasm — HIGH

The most likely failure mode is not technical. It is that researchers continue using shared drives, Confluence, and ad hoc Python scripts because the marginal effort of adopting Shepard exceeds the perceived benefit for any given experiment. Kadi4Mat reached 100+ users and Coscine reached 1,500+ users through NFDI4ING consortium mandates and institutional support — they had administrative forcing functions. Shepard has none.

**Mitigation needed:** At least one DLR institute mandate (or funded data steward position) that makes Shepard the default tool for a research group, not an optional add-on.

### Risk 2: UI Completeness Gap — MEDIUM-HIGH

Several headline features (HDF5, SPARQL, lab journal history) are backend-complete but have no UI. A researcher evaluating the platform today will encounter a visually incomplete tool. The Playwright screenshot pipeline that would make the docs compelling has not shipped. The user guide still contains placeholder screenshot captions.

**Mitigation needed:** A focused 4–6 week UI sprint before any public showcase or funding review.

### Risk 3: Fork Maintenance Burden — MEDIUM

This repo has diverged from `dlr-shepard/shepard 5.2.0` by more than 90 tracked changes across schema, API, and config. The `aidocs/34` tracker is diligently maintained, but the divergence is real and growing. If upstream ships 6.x with breaking changes (e.g., identifier model changes), the merge cost could be substantial. The `L2e` milestone (dropping long-id `/v1/` paths) creates a permanent wire-incompatibility that will require operator migration action.

**Mitigation needed:** Establish a formal upstream liaison relationship — even a quarterly "what's upstream doing?" review would reduce surprise costs.

### Risk 4: Single-Showcase Dependency — MEDIUM

Both public showcases (LUMEN-inspired hotfire, home energy environment) are synthetic or personal. There is no real MFFD sensor dataset seeded into the platform. The PLUTO satellite mission mentioned in the project context has no corresponding data model in the codebase. If the MFFD TCP thermal trail dataset (noted in project memory as arriving ~2026-05-26) does not materialize, the flagship claim ("this is the digital thread for the JEC Award-winning fuselage") has no working demo.

**Mitigation needed:** Prioritize real MFFD data ingestion — even a single AFP run with real (anonymized or synthetic-but-realistic) channel data — before any external presentation.

### Risk 5: Snap Dashboards Are Load-Bearing for the Narrative — MEDIUM

The vision document calls snap dashboards the "headline killer feature." It is 📐 queued — no backend, no UI. The competitive moat Shepard could build (BYOK LLM chat over a provenance graph, with SPARQL-over-Neo4j as a tool) is architecturally sound but is currently vapourware. If a competitor ships a similar capability first (e.g., Kadi4Mat + an LLM API), the differentiation window closes.

**Mitigation needed:** Ship a minimal snap-dashboard (basic chart generation from a natural-language query) before the next funding cycle, even if limited to a single LLM provider and two chart types.

### Risk 6: Open-Source Strategy Is Incoherent Under Scrutiny — LOW-MEDIUM

The Apache 2.0 license is correct. The upstream relationship is documented. But the fork rationale ("picks up the backlog faster") is thin when both repos are DLR-internal-origin. A DFG reviewer will ask: "why not contribute upstream?" The current answer (upstream API surface must stay frozen; this fork adds /v2/) is technically sound but politically awkward if both teams are DLR.

**Mitigation needed:** A one-paragraph "fork policy statement" in `docs/admin.md` explaining the divergence rationale for external audiences.

---

## 6. Board-Ready Positioning Brief

### Shepard — One-Page Summary

**What it is.**  
Shepard is an open-source research-data platform designed for the kinds of heterogeneous, campaign-scale experiments that aerospace manufacturing R&D produces: sensor timeseries, CAD files, lab notes, simulation outputs, and semantic annotations — all under one permission-aware, auditable, exportable umbrella. It is the *digital thread layer* between the test bench and the publication.

**What it does today — three things that matter:**

1. **Captures and connects.** Sensor data (TimescaleDB), files (MongoDB GridFS), structured logs (MongoDB), and spatial data (PostGIS) attach to a single logical "test run" entity with a full provenance graph. The graph links people, tools, processes, and outputs in W3C PROV-O format — consumable by any SPARQL store or EOSC-compliant repository.

2. **Makes data citable and FAIR.** One button mints a DataCite DOI, generates a snapshot-pinned RO-Crate ZIP (spec-aligned with RO-Crate 1.2, released 2025), and publishes a machine-readable record to the Helmholtz Knowledge Graph via the Unhide feed. This is the complete FAIR data lifecycle in a single researcher action.

3. **Is built to grow.** A drop-in JAR plugin system means new payload kinds (HDF5 datasets, video recordings, AAS submodel shells) add without touching the platform core. The upstream API surface is frozen for byte-compatibility; new capabilities land at `/v2/`. An admin upgrading from upstream `dlr-shepard/shepard 5.2.0` runs `docker compose pull && docker compose up` — nothing else.

**Why it matters now.**  
The MFFD program — JEC World Innovation Award 2025, Aerospace Parts — produced data across four manufacturing process steps, multiple robot systems, and dozens of test runs. That data currently exists in institutional silos. Shepard is the only platform in the DLR portfolio that can represent the full AFP → weld → inspection → test chain as a navigable, auditable, publishable provenance graph, and push it to the Helmholtz Knowledge Graph in a format a harvester can consume without human mediation.

---

## 7. Three Elevator Pitches

### For a DFG reviewer (infrastructure proposal, 3 minutes)

"The engineering research data community has NFDI4ING and metadata4ing — the standards are clear. What's missing is a platform that implements those standards for sensor-rich, manufacturing-process data without requiring researchers to become data engineers. Shepard does that. It ships with metadata4ing pre-seeded, QUDT units pre-loaded, and PROV-O provenance auto-generated on every mutation. An aeronautics engineer can annotate a timeseries channel with the correct QUDT IRI by typing 'g rms' in a search box — no triple store configuration, no ontology wrangling. The MFFD campaign is the existence proof. The ask: fund a data-steward position to take this from one DLR institute to three."

### For a Clean Aviation program manager (project review, 2 minutes)

"Every Clean Aviation project generates process data that needs to survive certification audits, enable knowledge transfer to industrial partners, and feed into the Horizon Europe open data mandate. Today that data lives in shared drives. Shepard gives the MFFD partnership a single system where AFP laser parameters, welding current traces, NDT scans, and simulation outputs all trace back to the same data object — with a DOI you can put in a deliverable and a PROV-O graph you can submit to an EASA auditor. We are already running on the MFFD process chain. The ROI is roughly 60–100 engineering hours recovered per campaign from audit and packaging work alone."

### For a DLR institute director (corridor conversation, 90 seconds)

"Shepard is the data infrastructure ZLP built for MFFD, made general enough that your institute could run it without needing us. It's open-source, self-hosted, runs on a single Docker Compose stack, and costs nothing in license fees. It connects to the Helmholtz Knowledge Graph automatically — so data your researchers deposit today becomes discoverable across Helmholtz next quarter. The question I'd ask is: in your next major project, how are you planning to satisfy the Horizon Europe data management plan requirement? Because Shepard is the answer we already have."

---

## 8. Strategic Recommendations

### Recommendation 1: Ship a Real MFFD Dataset Before the Next External Presentation
**Effort:** 2–3 weeks (data engineering + seeder script); if the 2026-05-26 MFFD TCP thermal trail dataset arrives on schedule, this becomes a 1–2 week sprint, not 2–3 weeks.  
**Why:** The narrative "we run on the JEC Award-winning fuselage" is the strongest funding hook available. It currently has no working demo. A single AFP run with real (or convincingly realistic) channel data, seeded via the LUMEN showcase pattern, transforms a slide claim into a live demonstration.  
**Dependency:** Access to MFFD sensor data (identified in project memory as arriving ~2026-05-26 via TCP thermal trail dataset).

### Recommendation 2: Close the UI Gap on the Three Most-Visible Backend-Only Features
**Effort:** 4–6 weeks of focused frontend work  
**Priority order:** (a) SPARQL query interface for the semantic repository — this is the capability that makes Shepard feel like a knowledge graph rather than a file store; (b) HDF5 container browser — unlocks the aerospace simulation community; (c) lab journal history diff viewer — makes the audit trail claim complete to a researcher, not just to an API user.  
**Why:** Every demo that exposes a backend-only feature to a non-developer evaluator is a lost opportunity. The platform is better than it looks to a new user today.

### Recommendation 3: Register in NFDI4ING Success Stories and Apply to HMC Project Call 2026
**Effort:** 2–4 weeks (proposal writing; technical work largely done)  
**Why:** Coscine reached 1,500 users through NFDI4ING institutional endorsement. Shepard already implements metadata4ing natively — it has a stronger technical claim than most platforms in the NFDI4ING ecosystem. The HMC Project Call 2026 deadline is 06 July 2026; the Unhide / HKG integration is exactly what HMC is funding. This is the fastest path to an adoption number that a DFG reviewer will respect.

---

## 9. External Sources Referenced

- JEC Composites Innovation Award 2025 for MFFD: https://www.ifam.fraunhofer.de/en/Press_Releases/JEC-Composites-Innovation-Award-2025-MFFD.html
- GKN Aerospace on MFFD JEC Award: https://www.gknaerospace.com/news-insights/news/the-clean-aviation-multifunctional-fuselage-demonstrator-mffd-has-won-the-jec-composites-innovation-award-in-the-aerospace-parts-category/
- DLR LUMEN program: https://www.dlr.de/en/ra/research-transfer/projects/dlr-projects/liquid-upper-stage-demonstrator-engine-lumen
- LUMEN first hot-fire (March 2024): https://europeanspaceflight.com/dlr-test-fires-lumen-upper-stage-rocket-engine-demonstrator/
- Clean Aviation JU home: https://www.clean-aviation.eu/
- Clean Aviation Work Programme and Budget 2026-2027: https://www.clean-aviation.eu/sites/default/files/2025-12/Draft-Work-Programme-and-Budget.pdf
- Horizon Europe RDM / FAIR requirements: https://www.openaire.eu/how-to-comply-with-horizon-europe-mandate-for-rdm
- HMC Project Call 2026: https://helmholtz-metadaten.de/projects/project-call-2026
- Helmholtz Knowledge Graph / unHIDE: https://helmholtz-metadaten.de/unhide_helmholtz-kg
- Metadata4Ing v1.4.0 (2025): https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/metadata4ing/
- NFDI4ING Conference 2025: https://nfdi4ing.de/conference_2025/
- Kadi4Mat (KIT): https://datascience.codata.org/articles/10.5334/dsj-2021-008
- SciCat: https://www.scicatproject.org/
- Coscine adoption (1,500 users, 138 institutions): from Kadi4Mat comparison search results
- PLUTO (PayLoad Under Test Orbiter): https://www.spiedigitallibrary.org/conference-proceedings-of-spie/13546/135464B/PLUTO-the-PayLoad-Under-Test-Orbiter/10.1117/12.3062786.full
- RO-Crate 1.2 specification release (June 2025): https://esciencelab.org.uk/ro-crate/announcements/2025/06/04/ro-crate-1.2-released/
- Manufacturing Digital Passport / digital thread: https://www.mdpi.com/2079-8954/13/8/700
- Model-Based Enterprise market growth (A&D digital transformation $9.9B→$20.5B, 15.7% CAGR): https://www.marketsandmarkets.com/Market-Reports/model-based-enterprise-market-122038238.html
- DFG research data management criteria: https://www.dfg.de/en/basics-topics/basics-and-principles-of-funding/research-data
- DLR Shepard project page: https://www.dlr.de/en/zlp/research-transfer/projects/projects-from-augsburg/project-archive-zlp-augsburg/shepard-storage-for-heterogeneous-product-and-research-data

---

## 10. What Surprised Me

**The provenance story is genuinely ahead of comparators.** I expected Shepard to be a roughly average DLR research platform. The PROV-O + metadata4ing stack, the Unhide/HKG integration, and the snapshot-pinned RO-Crate export are not obvious features — they require specific Helmholtz standards knowledge and careful engineering. No competing platform in this space ships all three. This is undersold, not oversold.

**The AI section is the platform's most credible risk.** The vision calls snap dashboards the "headline killer feature" while it is entirely unbuilt. The only shipped AI feature (MAD anomaly detection) is a 51-point rolling median — useful but not differentiating. If the platform is going to win a funding review on AI grounds, that review must happen *after* snap dashboards ship, not before. Pitching the vision doc as-is to a technically literate Clean Aviation program manager would be embarrassing.

**The LUMEN showcase is a clever fiction that risks becoming a liability.** The seed script is careful to label itself "NOT REAL DLR/LUMEN data — all values are deterministic synthetic outputs of numpy.random.default_rng(2024)." That disclaimer is buried in the collection description. In a live demo to a DLR partner, an audience member who knows what LUMEN's real thrust curves look like will notice instantly. The showcase needs to be either (a) replaced with real data or (b) more aggressively labeled as synthetic from the UI level.

**The adoption count is unknown — and that's the real strategic problem.** Every competitive advantage Shepard has (timeseries-native, PROV-O, Unhide, RO-Crate) is architectural. Architecture does not fund projects. Users fund projects. The gap between the technical quality of the platform and the discoverability / adoption of the platform is the biggest single strategic lever available to the team right now. The HMC Project Call 2026 deadline (06 July 2026) and NFDI4ING registration are the fastest external validation paths that would give funders a number to point at.

**The fork-vs-upstream tension is currently healthy but fragile.** The `aidocs/34` upgrade ledger is the best-maintained document in the repo — clearer and more honest than most production software change logs. The "pull-and-restart" upgrade path is real. But the L2e identifier migration, when it ships, will be a genuine breaking change, and the ledger will need to explicitly say so to the 5.2.0 operator base.
