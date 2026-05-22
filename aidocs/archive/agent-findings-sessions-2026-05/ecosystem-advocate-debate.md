# Ecosystem Advocate Debate
**Author:** Industrial Ecosystem Advocate (cross-agent debate, Phase 2)
**Date:** 2026-05-21
**Inputs:** All 8 agent proposal documents (UX, Ontology, API, Manufacturing-Quality, RDM, Strategy, Analytics-AI, Ecosystem)

---

## Preamble: my lens

My job is to argue from external visibility — what a DLR Augsburg neighbour, a Clean Aviation JU programme manager, or a researcher discovering Shepard via the Helmholtz Knowledge Graph would see, feel, and conclude. Technical correctness is necessary but not sufficient for me to champion something. The question I keep asking: **will this unlock a new conversation, a new adopter, or a new conference abstract?** If yes and the effort is proportionate, I champion it. If the feature is internally correct but invisible to anyone outside the project team, I challenge or redirect.

---

## Top 5 I'm championing

### C1 — EP-01 `make demo` (Ecosystem, S effort)

**Audience unlocked:** Every potential adopter who reaches the GitHub repository, every conference presenter who needs a fallback when the live system is unavailable, every DLR institute that was told "try it yourself."

**Narrative it unlocks:** "Clone the repo, run one command, see a real anomaly investigation in under 10 minutes." This is the single sentence that collapses the adoption chasm. Every other proposal — the FAIR spine, the AAS calibration plugin, the lineage walk API — assumes the evaluator has already decided to invest time. `make demo` reaches the people who have not decided yet.

**Why the other agents undervalue it:** The API scrutinizer, RDM agent, and manufacturing quality agent focus on existing users who have committed to the system. They all implicitly assume someone runs a Shepard instance already. The cold-start evaluator — the PI at DLR Stuttgart reading the NFDI4ING newsletter, the ESOC engineer told "DLR Augsburg has something for telemetry data" — appears nowhere in their proposal lists. EP-01 is for that person.

**Ecosystem cost of not doing it:** Every other external-facing investment (NFDI4ING spotlight page, FAIR scorecard, public collection card) will be read by people who cannot verify the claims. The EP-01 `make demo` target converts the claims into experience. Without it, the ecosystem investments are marketing. With it, they are invitations.

**What I would add to the current EP-01 spec:** The `README-demo.md` must include the three-sentence conference pitch ("Shepard is a research data management system for experimental science. In this demo, watch a simulated rocket engine anomaly — TR-004 turbopump vibration at t=8s — and follow the investigation, repair, and re-test chain that an EN 9100 auditor would trace."), the URL of the public HKG feed for the seeded collection, and a QR code. The seed should also pre-configure the Unhide feed so the demo collection appears immediately in the HKG harvester test endpoint — making "data is in the Knowledge Graph" demonstrable without additional steps.

---

### C2 — EP-09 NFDI4ING / metadata4ing Spotlight Page (Ecosystem, S effort, time-sensitive)

**Audience unlocked:** NFDI4ING community members, HMC project call reviewers, the metadata4ing working group (whose ontology Shepard already uses natively). Deadline: 06 July 2026 for the HMC Project Call.

**Narrative it unlocks:** "Shepard is the first RDM system to natively dual-type experimental process steps to metadata4ing in a graph database." This claim is accurate, verifiable, and differentiating. No competing system (Kadi4Mat, SciCat, Coscine, openBIS) can make the same claim with the same specificity. The claim requires no new development — the technical work is done. The gap is a documented, linkable page that lets a programme reviewer verify it.

**Why the other agents undervalue it:** The RDM agent is focused on FAIR field additions (correct, but month-scale). The strategy advisor mentions NFDI4ING registration but treats it as a Phase 2 task. Neither recognises that the 06 July 2026 HMC deadline makes EP-09 the highest-ROI 3-day investment in the entire proposal set. A spotlight page filed before the deadline with a worked LUMEN example costs 3–4 days and can unlock a funding track worth orders of magnitude more in project budget.

**Ecosystem cost of not doing it:** Missing the HMC Project Call 2026 window means the next entry point is the next call cycle. The metadata4ing leadership is concentrated in a small working group that actively tracks which systems implement their vocabulary — being named in that group's community updates is a discovery multiplier that no internal feature can replicate.

**Concrete additions I want:** The spotlight page must include a live API example — specifically, `GET /v2/semantic/terms/search?q=ProcessingStep` returning the metadata4ing IRI, paired with the LUMEN seed command that creates a DataObject annotated with that IRI. Reproducible claims beat descriptive claims with this audience.

---

### C3 — EP-03 Embed-and-Share Collection Card (Ecosystem, M effort)

**Audience unlocked:** Publication authors citing Shepard datasets, conference poster presenters with QR codes, project websites that need a data landing page.

**Narrative it unlocks:** "Every Shepard Collection has a citable URL." This is the moment Shepard crosses from internal tool to citeable repository. SciCat has it. Kadi4Mat has it. Coscine has it. Without it, Shepard cannot appear as the "FAIR data repository" in a data availability statement in a journal paper — which is the canonical way an external researcher discovers a platform for the first time.

**Why the other agents undervalue it:** The UX auditor focuses on the researcher experience inside the tool. The API scrutinizer focuses on the developer experience consuming the API. Neither focuses on the reader experience — the person arriving from a paper's data availability statement or a conference poster QR code. EP-03 serves that reader. Without a public landing page, the citation in the paper resolves to a 401 or a login wall. That is the worst possible first impression for external adoption.

**Important nuance the EP proposal misses:** The public landing page at `/public/collections/{appId}` needs to resolve correctly from a DOI redirect. If the KIP plugin mints an ePIC PID that resolves to a Shepard URL, that URL must not require authentication. Currently it does. EP-03 is also the fix for the FAIR A2 gap — accessible metadata even when access to the data itself requires authentication. The "Request Access" button on RESTRICTED collections handles this gracefully.

**Upstreaming candidate:** The core public-landing-page mechanism (auth-bypass render for OPEN collections, 410 tombstone for retired) is generic enough to upstream. The upstream shepard project would benefit from it; it requires no fork-specific assumptions.

---

### C4 — API Scrutinizer Proposal 1 + 2 (referenceIds fix + flat DataObject GET, S effort each)

**Audience unlocked:** Every developer building an integration against the `/v2/` surface — MCP servers, Python pipelines, the sTC collector, the MFFD AFP ingest scripts. This is the developer-experience audience that determines whether the ecosystem grows by external contributions or stays DLR-internal.

**Narrative it unlocks:** "The Shepard API is correct by default — you can read a DataObject response and follow the IDs in it without getting 404s." This sounds like table stakes, and it is. But right now it is broken. The `referenceIds` field in `DataObjectIO` is the live bug that produces 404s for every MCP server and AI agent that reads it. This is the most damaging ecosystem issue in the entire proposal set because it affects the first thing any new API consumer tries to do: read a DataObject and navigate to its containers.

**Why this pair specifically:** API Proposal 1 (typed container arrays) and Proposal 2 (flat GET by appId) are joined at the hip from the ecosystem perspective. Proposal 1 fixes what you get back when you read a DataObject. Proposal 2 fixes how you get to a DataObject when you have its ID from somewhere else (a notification, a provenance trail, a colleague's message). Together they make the DataObject the navigable primary unit of the API, which is what it is supposed to be.

**Why I'm championing these over the API agent's other proposals:** Proposals 7 (ProblemJson), 8 (OpenAPI tags), and 5 (pagination) are correct and important. But their ecosystem impact is diffuse — they improve the experience of someone already building an integration. Proposals 1 and 2 unblock someone trying to start an integration. First-start friction is categorically more damaging to ecosystem growth than ongoing friction.

**Conference story:** At any developer-facing talk (EOSC Symposium, RDA Plenary), the demo that shows an LLM agent successfully reading TR-004's DataObject and navigating to its vibration channel — without a 404 — is the demo that earns trust. Proposals 1 and 2 make that demo possible. Without them, the demo produces a 404 in the first step and the talk ends badly.

---

### C5 — Data Ontologist Proposal 5 (MFFD + PLUTO Domain Vocabulary Pack, S effort)

**Audience unlocked:** MFFD consortium partners, CHAMEO / EMMO working group members, PLUTO operations team, any researcher who finds Shepard data via a SPARQL query for `mffd:AFP_InSituConsolidation` or `pluto:LEOP`.

**Narrative it unlocks:** "Shepard annotations are interoperable with domain vocabularies, not just generic FAIR terms." The vocabulary pack is the moment Shepard's semantic layer becomes searchable by domain experts who don't know Shepard exists. A materials scientist querying the HKG for CHAMEO-annotated datasets would find MFFD data. A satellite operations researcher querying for PLUTO mission phase annotations would find PLUTO data. Without the vocabulary pack, both datasets are invisible to that search path.

**Why S effort deserves a top-5 spot:** Three TTL files. No backend code changes. Immediate effect: the annotation picker gains domain-specific terms that researchers recognise without explanation. The LUMEN seed update (also S) makes the showcase demonstrate real semantic depth rather than freetext strings. The combined cost is under one week. The combined payoff is: (a) the MFFD showcase gains CHAMEO defect annotation capability immediately, (b) the PLUTO showcase gains mission phase vocabulary, (c) both datasets become discoverable by domain-specific SPARQL queries in the HKG, and (d) the strategy advisor's "LUMEN is a clever fiction" risk is materially reduced.

**What the ontologist missed:** The TTL files should include `skos:mappingRelations` to EMMO (Elementary Multiperspective Material Ontology), which is the broader ontology that CHAMEO is a module of. EMMO alignment is increasingly expected by NFDI4MatWerk and by the Catena-X consortium for digital product passports. One extra triple per concept, zero extra effort.

---

## Top 3 I'm challenging

### X1 — Analytics-AI Proposal 8: Semantic Embedding for DataObject Discovery (L effort)

**The proposal:** Store 384–1536-dim embeddings in pgvector for every DataObject, run a nightly job, expose a "similar experiments" panel. The analytics agent rates this "High" feasibility and "High" value.

**My challenge:** At the current corpus size (15 LUMEN DataObjects, awaiting MFFD AFP data), this feature is a demo that performs with synthetic precision because there are too few items to distinguish signal from coincidence. At 50 DataObjects (2 weeks post-MFFD data), nearest-neighbour search will return results that are trivially obvious (the other LUMEN runs). The minimum corpus for this to be non-trivially useful is approximately 200–500 DataObjects of genuine diversity — which requires months of real data ingestion from multiple institutes.

**The ecosystem cost the analytics agent doesn't account for:** If Shepard ships a "Similar experiments" panel now and it returns TR-003 as most similar to TR-004 because they are both rocket tests, the feature trains users to distrust it. A discredited feature is harder to re-introduce than an absent one. The analytics agent acknowledges "ship at ~200 DataObjects" in a sequencing note but then includes the proposal in the first-wave list anyway.

**My redirect:** Invest the embedding infrastructure effort (pgvector table, nightly job, HNSW index) as backend groundwork only — no frontend panel. Expose a `GET /v2/data-objects/{appId}/similar` endpoint behind a feature toggle that defaults off. Re-enable and surface the panel when the corpus crosses 200 DataObjects. This preserves the technical investment without the risk of a discrediting demo. The conference story becomes "we have the embedding infrastructure ready; here's what happens when 200 real AFP runs are loaded" — which is more credible than a live demo on 15 runs.

---

### X2 — Analytics-AI Proposal 7: AI Audit Narrative Generator (M effort, hard dep on plugin-ai TEXT)

**The proposal:** `POST /v2/collections/{appId}/generate-audit-report` sends the ancestor chain to an LLM and returns a Markdown audit narrative that an EASA auditor can use for EN 9100 §10.2 compliance.

**My challenge:** This proposal conflates two very different audiences. An EASA Part 21 G auditor does not accept LLM-generated text as a compliance record. The EN 9100 standard requires that records be "legible, readily identifiable and retrievable" — and that they accurately represent the facts of a process. An LLM audit narrative has hallucination risk. The manufacturing quality agent (from whose domain this proposal draws its authority) explicitly does not champion this feature, and for good reason: a compliance record that could be challenged as AI-generated introduces legal uncertainty that most aerospace quality systems cannot afford.

**The ecosystem cost the analytics agent doesn't account for:** If Shepard ships this and a DLR ZLP IME uses an AI-generated audit narrative in a certification submission, and the narrative contains a subtle inaccuracy, the incident damages not just Shepard's reputation but DLR's. This is not a theoretical risk — LLMs reliably produce confident summaries of graph data that contain small inaccuracies in sequence and timing. The EASA audience is precisely the audience that cannot tolerate small inaccuracies.

**My redirect:** The DMP snippet generator variant — `GET /v2/collections/{appId}/dmp-snippet` generating a DFG/Horizon Europe Data Management Plan section — is the correct application of the same LLM TEXT capability. DMP text is advisory, not a compliance record. A researcher writing a DMP is expected to review and edit AI-generated text. The error mode is a researcher submitting a slightly wrong DMP section, not a certification body rejecting a non-conformance report. Ship the DMP snippet first. If the DMP snippet builds trust over 6 months, reconsider the audit narrative with an explicit "AI-assisted draft — requires human review before submission" disclaimer and a provenance trail showing what the LLM received.

---

### X3 — Manufacturing Quality Proposal 7: Shop Floor Template Mode (L effort)

**The proposal:** Extend the ShepardTemplate DSL with `shopFloorMode: true`, triggering an 80px-target touch-first rendering path with QR generation and barcode scan-to-find.

**My challenge:** This is the right feature. The audience is real. The problem is real. My challenge is specifically with sequencing and scope. The proposal estimates L effort (4 weeks) and lists it as shipping order position 10 — after the predecessor gate, status vocabulary, bulk status, ancestor chain, annotatable file, and NCR auto-raise. That sequencing is correct. But the ecosystem risk is that "shop floor mode" becomes the last thing that ships before the MFFD AFP data arrives, which means the first time a shop floor IME sees the interface, it is in production use with real data — not in a demo environment where rough edges can be tolerated.

**The ecosystem cost the manufacturing quality agent doesn't account for:** Shop floor adoption is the most visible failure mode for a research data platform. If an AFP robot operator at DLR Augsburg encounters a touch target that is 40px instead of 80px on a gloved touchscreen, they stop using the tool and tell every other operator on the floor. Negative word-of-mouth in a small production team is faster than any adoption curve. The feature requires user testing with actual gloved operators on actual ruggedized hardware before deployment — which is not captured in the 4-week effort estimate.

**My redirect:** Scope the first delivery to the two highest-value elements only: (a) the large-target "Advance Status" button (achievable in 2 days as a responsive CSS addition to the existing status picker, no DSL extension required), and (b) the "Raise NCR / Place Hold" rapid form (1 week, reuses existing DataObject creation). Both can ship ahead of the full shop floor mode and can be validated by the operators before the full template rendering path is built. The full shop floor DSL and QR generation should wait for user testing feedback. This reduces L to M, removes the sequencing bottleneck, and reduces the reputational risk of shipping an untested touch interface to a production floor.

---

## The unified ingest narrative

**The problem today:** An external evaluator reads the Shepard README and asks "how do I get my data in?" The answer is currently: "it depends on your data type — OPC/UA uses sTC, MQTT uses sTC, files use Node-RED or the file upload API, timeseries from a Python script use the REST API, structured data has its own endpoint, and if you want to import an existing dataset you write an ImportManifest JSON by hand." That answer sends them to four different tools with four different mental models.

**The narrative we want:** "You have a measurement source. You have files. You have structured records. Shepard has one ingest story: tell us what you have, and we handle the rest."

**How the current proposals compose into that story:**

The unified ingest narrative requires four pieces to click together, and they are all in the proposal set but not connected:

1. **shepard-plugin-hotfolder** (the missing explicit proposal — the ecosystem advocate document names it in the context, but no agent wrote a proposal for it): This is the ingest story for "I have a measurement device that writes files." It replaces Node-RED for file-based ingest and gives the researcher a folder to watch, not a graph to configure. Without this proposal explicitly on the roadmap, the file-based ingest story remains "configure Node-RED" — which is a tool that requires a systems administrator and repels researchers. I am flagging that this proposal is absent from all 8 agent proposal documents and should be added as a tracked item.

2. **API Scrutinizer Proposal 3 (POST /v2/import/jobs)**: The execute leg of the import flow. Without it, the import API is a planning tool with no execution path. This is CRITICAL for the ingest narrative — validating a manifest and then having no way to run it is the architectural equivalent of a runway with no aircraft. This proposal has consensus across 4 agents (API scrutinizer, analytics-AI, strategy advisor, UX auditor) but none of them frame it as the linchpin of the ingest narrative. It is.

3. **Analytics-AI Proposal 9 (LLM Import Manifest Generator)**: Once `POST /v2/import/jobs` exists, the manifest generator closes the loop for researchers who cannot write JSON by hand. The narrative becomes: "describe your data in plain language, review the generated manifest, click import." This is the sTC story for researchers, not for systems engineers. The sTC collector requires MQTT/OPC-UA knowledge. The manifest generator requires a description of your files. The second is accessible to a researcher; the first is not.

4. **Strategy Advisor Proposal S2 (Agentic Ingest Pipeline)**: The cross-cutting narrative that connects TS-IDb (stable channel IDs) + import/jobs + manifest generator into a single demo: "drop a directory of AFP runlog files, get DataObjects in Shepard." This is the demo that makes the ingest story concrete at a conference.

**The pitch to an external adopter (three paragraphs):**

Shepard has one ingest story: if you have sensor data, the sTC collector connects OPC/UA, MQTT, or direct timeseries feeds and writes channels into Shepard's TimescaleDB backend — no code required, just a YAML configuration. If you have files — NDT scans, runlog JSONs, CAD geometry, lab reports — the hotfolder plugin watches a directory and imports files as FileBundle containers, annotating each with metadata extracted from the filename pattern you define. If you have a structured dataset from a prior experiment, the import API accepts a manifest that describes your collection structure; you can write the manifest by hand, generate it from a directory listing, or — if the AI plugin is configured — have an LLM draft it from a plain-language description of your data.

All three paths converge on the same Shepard data model: Collections, DataObjects, typed containers, semantic annotations, provenance. Once your data is in Shepard, the same exploration, annotation, and publication tools work regardless of how the data arrived. Your sTC-collected timeseries and your hotfolder-imported NDT scans are first-class citizens in the same provenance graph.

For organisations with existing ingest tooling (Node-RED, custom Python, LabVIEW pipelines), the `POST /v2/collections/{appId}/data-objects` and container creation endpoints are the integration surface. The API is documented at `/api/openapi` on any running Shepard instance.

**What is missing from this narrative today:** The hotfolder plugin proposal is absent from all 8 agent documents. The import/jobs execute endpoint is absent from the running instance. The manifest generator requires the AI plugin. Until import/jobs ships and the hotfolder proposal is written, the unified ingest narrative has a gap in the middle (file-based ingest) and a dead end at the end (validate-with-no-execute). Fixing these two gaps is the highest-leverage ecosystem investment after `make demo`.

---

## Upstreaming candidates from all proposals

The following proposals contain changes that are generic to the upstream `dlr-shepard/shepard` codebase — they make no fork-specific assumptions, introduce no new entities unique to this fork, and would improve any Shepard instance.

| Priority | Proposal | Why upstream | Effort to upstream |
|---|---|---|---|
| **1** | API Scrutinizer P7 — ProblemJson ExceptionMapper | Pure correctness fix; upstream has the same inconsistency; zero behaviour change | XS — one file, one PR |
| **2** | API Scrutinizer P8 — Human-readable OpenAPI @Tag names | Zero behaviour change; improves SDK codegen for all Shepard deployments | XS — mechanical rename |
| **3** | API Scrutinizer P5 Part B — `?status=` server-side filter on DataObject list | Additive Cypher predicate; no schema change; useful for any Shepard instance | S — one DAO method, one param |
| **4** | UX Auditor P9 — Channel unit picker (QUDT combobox in channel creation) | Uses existing QUDT infrastructure; no fork-specific ontology; improves any timeseries dataset | S — composable + dialog change |
| **5** | Ecosystem P3 — Public Collection card / landing page | Public discoverability is generically useful; FAIR principle A |  M — auth-bypass route + render |
| **6** | RDM Proposal 1 — license field on AbstractDataObject | FAIR foundation; upstream has the same gap; feeds KIP plugin which upstream ships | S — additive field |
| **7** | RDM Proposal 2 — createdByOrcid stamp at creation | One-line addition to creation service; upstream `User.orcid` validation exists; attribution is generic | S — one service call |
| **8** | API Scrutinizer P1 — typed container arrays in DataObjectV2IO | Bug fix for the referenceIds live 404; upstream should not ship the same bug | S — IO class extension |

**Items that should NOT be upstreamed (fork-specific):**
- `/v2/` routing and all endpoints under it (fork's development surface per CLAUDE.md API policy)
- MFFD/PLUTO domain vocabulary TTL files (domain-specific, not generically useful)
- Quality gate / predecessor-status gate (fork-specific manufacturing workflow)
- AAS plugin calibration submodel extension (fork-specific IDTA alignment)
- AI plugin integration points (fork-specific SAIA/GWDG dependency)

**Process note:** The upstream contribution bundle (EP-08) is the right vehicle for the first four items. Filing them as a bundle signals DLR as a responsible steward. Individual XS/S PRs can ship in parallel without blocking each other.

---

## My overall priority stack

Ordered by: **external visibility** first, then **adoption unlock**, then **conference story**, then **upstream value**.

### Tier 1 — Do these before anything else (each ≤ 1 week, each has no external dependencies)

| Rank | Proposal | External visibility gain | Adoption unlock |
|---|---|---|---|
| 1 | **EP-01 `make demo`** | Evaluators can verify claims without a login | Institute adoption (cold-start) |
| 2 | **EP-09 NFDI4ING Spotlight Page** | HMC Project Call 2026 (deadline 06 July 2026) | NFDI4ING community discovery |
| 3 | **API P1 + P2 (referenceIds + flat GET)** | Developer-facing: first API call no longer 404s | MCP server, sTC, MFFD ingest scripts |
| 4 | **API P7 + P8 (ProblemJson + tag cleanup)** | SDK codegen produces usable class names | External SDK users, AI agent callers |
| 5 | **Data-Ontologist P5 (Domain Vocab Pack)** | CHAMEO/PLUTO annotations discoverable in HKG | MFFD consortium, satellite operations |

### Tier 2 — First sprint deliverables (M effort, fund-body-facing by HMC deadline)

| Rank | Proposal | Conference story | Funding pathway |
|---|---|---|---|
| 6 | **RDM P1 + P2 + P3 (license, ORCID stamp, accessRights)** | "FAIR-compliant from creation" | Horizon Europe, DFG, Clean Aviation JU |
| 7 | **API P3 (POST /v2/import/jobs execute)** | "Agentic ingest is real, not planned" | Clean Aviation JU (automation KPI) |
| 8 | **EP-02 FAIR Scorecard Widget** | "Shepard tells you what's missing before you publish" | HMC Project Call 2026 |
| 9 | **API P4 + Strategy P3 (ancestor-chain endpoint + UI)** | "Trace any defect to raw material in one API call" | EN 9100 / EASA audit readiness |
| 10 | **UX P9 (Channel unit picker, QUDT combobox)** | "Every channel is machine-readable at creation" | FAIR I2, Horizon Europe interoperability |

### Tier 3 — Demo-completing work (L effort or AI-plugin-gated)

| Rank | Proposal | Conference story |
|---|---|---|
| 11 | **EP-10 Side-by-Side Timeseries Comparison** | The TR-004 anomaly demo moment — "watch it spike across all 6 runs" |
| 12 | **EP-03 Embed-and-Share Collection Card** | "Every dataset has a citable URL" |
| 13 | **Strategy P8 (Quality Plugin Foundation — status machine + gate)** | "Shepard enforces quality gates, not just records them" |
| 14 | **EP-05 shepard-plugin-publisher (Zenodo)** | "One-click Zenodo deposit from inside Shepard" |
| 15 | **EP-12 MFFD AFP Robot Run Onboarding** | "Real aerospace data — the JEC Award panel, in Shepard" |

### Tier 4 — High value but gated on plugin-ai or corpus size

| Rank | Proposal | Gate |
|---|---|---|
| 16 | **Analytics-AI P4 (PDF auto-annotation)** | plugin-ai STRUCTURED |
| 17 | **Analytics-AI P9 (LLM manifest generator Phase 2)** | plugin-ai STRUCTURED + import/jobs |
| 18 | **RDM P6 (shepard-plugin-publisher with DataCite push)** | Proposals 1+3+4 fields shipped |
| 19 | **Analytics-AI P11 (Unhide AI enrichment)** | plugin-ai TEXT + unhide plugin |
| 20 | **Analytics-AI P8 (semantic embedding)** | 200+ DataObjects in system + plugin-ai EMBEDDING |

### The one proposal I want added to the tracking board that no agent wrote

**`shepard-plugin-hotfolder`** — File-based ingest without Node-RED. The ecosystem context provided for this debate names it explicitly as "the adoption story for 'I have a measurement device that writes files.'" No agent wrote a proposal for it. Without it, the unified ingest narrative has a gap at its most common researcher scenario. Someone needs to write a design doc, a feature proposal, and an aidocs/44 row. This is flagged as a **tracking gap**, not a blocker for anything else — but it should be written before the MFFD data arrives so the ingest story is complete for the first real-data demo.

---

*Ecosystem Advocate (Phase 2 debate) | Generated 2026-05-21*
