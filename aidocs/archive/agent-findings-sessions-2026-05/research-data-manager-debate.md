---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Research Data Manager — Cross-Agent Debate
**Author:** Research Data Manager & FAIR Data Steward  
**Date:** 2026-05-21  
**Mandate:** Argue from FAIR/funding-mandate lens across all 8 agent proposals  
**HMC deadline:** 06 July 2026 (~6 weeks)

---

## Top 5 I'm Championing

### Champion 1: FAIR Metadata Spine (license + ORCID stamp + accessRights + funder)
**Proposals:** RDM 1–4, Data-Ontologist 7, API-Scrutinizer 6, Strategy 1, Analytics-AI 10

**FAIR dimensions closed:** R1.1 (usage license), R1.2 (creator provenance), A1.2 (access classification machine-readable), R1.3 (funder attribution)

**Funding mandates unlocked:**
- Horizon Europe Art. 17 mandatory open-access data — requires `license` and `accessRights` per dataset
- DFG Research Data Guidelines requirement 12 — machine-readable license and creator attribution
- Clean Aviation JU data deliverable specifications — funder attribution required in DataCite record
- HMC KIP v1.1 mandatory fields — `policy` (= license) is hard-required; the KIP plugin today emits this from an instance-default fallback that the entity model cannot support per-entity

**Why I champion it above all else:** Every single other proposal in this list that touches FAIR compliance hits the same wall — the entity model has no `license`, `accessRights`, `createdByOrcid`, or `fundingReferences` field. The Unhide plugin asserts `schema:license` against a field that does not exist. The publisher plugin (RDM Proposal 6, Strategy Proposal 2, Ecosystem EP-05) cannot build a valid Zenodo submission. The metadata completeness score (RDM Proposal 5) cannot compute R1.1 or R1.2. The NFDI4ING spotlight page (Ecosystem EP-09) would describe capabilities that are half-true. Four additive fields. One sprint of backend work. Cascades into every other FAIR-improving feature this list contains.

**Dataship question (resolved here):** `shepard-dataship` (un-parked Python pipeline) maps naturally onto the `ZenodoAdapter` inside `shepard-plugin-publisher`. Dataship should become the Python-client implementation of the `RepositoryAdapter` SPI — not a competing tool. The plugin provides the in-app button and async job; dataship provides a CLI equivalent and a scriptable path for power users. Both consume the same entity fields (license, accessRights, funder). They are not duplicates; they are the same push-deposit capability at different interaction layers. Ship the metadata fields first; then the plugin and the dataship adapter converge on the same wire format.

**Ingest SPI note:** Every source adapter (hotfolder, JupyterHub J2e, unified ingest SPI) must carry these four fields in its routing rule / session context at ingest time. Minimum viable ingest FAIR record: `creator` (API key owner ORCID or instrument appId), `license` (default from admin config, overridable), `accessRights` (default OPEN, overridable per rule). If the hotfolder routing rule cannot carry this, the ingest is not FAIR — it produces anonymous, unlicensed data.

---

### Champion 2: Metadata Completeness Score + DMP Snippet + Publish Gate
**Proposals:** RDM 5, API-Scrutinizer 6, Analytics-AI 10, UX P4, Strategy 1

**FAIR dimensions closed:** Closes the feedback loop across F, A, I, R — the score is the single instrument that makes FAIR compliance *felt* by a researcher rather than invisible

**Funding mandate it closes:** HMC Project Call 2026 — a demonstrable FAIR compliance signal (not just claimed) is the evaluation criterion. The completeness ring on the Collection sidebar is the artefact a reviewer can see. The publish gate (score ≥ 60 to mint PID) is the enforcement mechanism that prevents funding-noncompliant datasets from being cited in deliverables.

**Why I champion this over "just add the fields":** Adding license and ORCID fields without surfacing their absence creates a dark pattern — researchers will not fill them in because they cannot see what they are missing. The ring makes the gap visible. The checklist with inline fix-links makes closing the gap frictionless. The DMP snippet endpoint converts the populated fields into copy-paste text for the DMP form — this is the moment a researcher stops thinking of Shepard as "where my data lives" and starts thinking of it as "the tool that does my DMP for me." That is adoption stickiness.

**HMC dependency:** The HMC KIP validator will run against the HKG feed. The feed validation endpoint (RDM Proposal 9 — `GET /v2/admin/unhide/feed-validation`) should be championed alongside this score: it reports which Collections would fail KIP mandatory-field checks *before* the harvester runs, giving operators a shift-left signal. This is a direct HMC deadline deliverable.

---

### Champion 3: Ancestor-Chain Lineage Walk + FAIR I3 Qualified References in Export
**Proposals:** RDM 11, API-Scrutinizer 4, Manufacturing 5, Analytics-AI 6, Strategy 3, UX P5

**FAIR dimensions closed:** I3 (references use qualified references), F2 (data described with rich metadata including provenance), R1.2 (detailed provenance)

**Funding mandate it closes:** FAIR principle I3 is increasingly scrutinised in OpenAIRE/EOSC validators. A dataset that claims rich provenance but whose predecessors are addressed by opaque internal IDs (`appId`) rather than globally resolvable identifiers fails I3. The ancestor chain endpoint is the prerequisite for the RO-Crate export to emit PIDs (rather than appIds) for linked entities (RDM Proposal 8 — qualified PID references). These two proposals together close I3 properly.

**Why I champion this beyond just EN 9100:** Every other agent champions this for compliance (Manufacturing) or graph UI (UX). I champion it because it is the provenance story's user-facing evidence. The PLUTO paper (Welzmüller et al., eLib 215120) argues that research data systems must be able to express mission-phase causal chains. The ancestor chain endpoint is the API expression of that argument. When the RO-Crate export uses PIDs in `relatedItem` for linked ancestors, a harvester from another Shepard instance (or EOSC) can traverse cross-dataset provenance. That is FAIR I3 done properly, not just structurally present.

**Redaction handling matters:** The API-Scrutinizer's proposal to return `{"redacted": true, "appId": "..."}` for chain members the caller cannot read is the correct approach for FAIR A1: authentication should not prevent the *existence* of a provenance link from being known, only its content. This is FAIR-principled design, not just a security convenience.

---

### Champion 4: Domain Vocabulary Pack (CHAMEO + SSN/SOSA + MFFD/PLUTO ConceptSchemes) + QUDT Unit Picker
**Proposals:** Data-Ontologist 3+5, Manufacturing 10, UX P9, Strategy 4

**FAIR dimensions closed:** I1 (formal knowledge representation language), I2 (vocabularies follow FAIR principles — controlled terms are findable by IRI)

**Funding mandate it closes:** NFDI4ING/HMC conformance — CHAMEO is the W3C/NFDI-endorsed defect characterisation vocabulary; its absence from Shepard's manifest while claiming MFFD NDT support is the credibility gap. FAIR I2 for sensor data requires formal sensor descriptions (SSN/SOSA). The MFFD/PLUTO vocabulary packs are a prerequisite for the HKG feed to carry domain-specific terms that NFDI4ING community members can query.

**Why I champion this before the publisher plugin:** There is no point pushing a Zenodo deposit that carries freetext annotation values (`bench: "P3-Lampoldshausen"`) when the IRI-based equivalent (`lumen:FacilityP3Lampoldshausen`) is three TTL files away. CHAMEO and SSN/SOSA are S-effort additive manifest entries. The MFFD/PLUTO vocabulary pack is also S. Together they transform the HKG feed from a structurally correct but semantically thin document into a genuinely interoperable knowledge graph fragment. The QUDT unit picker (UX P9) is the UI expression of this: it ensures units enter the graph at channel-creation time rather than being retrofitted later.

**HMC-specific:** The HKG harvester indexes `m4i:hasProcessingStep` values. If MFFD process steps are currently freetext attributes (`process_step: "AFP_InSituConsolidation"`), they are not indexed. Once the `mffd-process.ttl` vocabulary ships and the seed is updated to use IRIs, they appear in HKG search results for NFDI4ING users querying by manufacturing process type. That discoverability is the HMC deadline deliverable.

---

### Champion 5: Tombstone / FAIR A2 + Publish Gate Hardening
**Proposals:** RDM 7, Manufacturing 2 (immutability lock on CERTIFIED/PUBLISHED)

**FAIR dimensions closed:** A2 (metadata remain accessible even when data is no longer available)

**Funding mandate it closes:** FAIR A2 is the most-cited gap in OpenAIRE validation results. A PID that 404s after dataset retirement fails Horizon Europe Art. 17's retention requirement ("data must remain accessible for the duration of the project and at least 5 years after"). The current `KIP1f retire()` sets `digitalObjectMutability = "retired"` but the endpoint returns 404. A 410 Gone with a tombstone JSON-LD body is the standards-correct response.

**Why I champion this even though it is not glamorous:** The publisher plugin (Champion 1's downstream) will mint DOIs for MFFD and PLUTO datasets. Those DOIs will be cited in publications and deliverables. If a dataset needs to be retired (e.g., embargo lift reveals a data quality issue, or it is superseded by a corrected version), the DOI must still resolve — to a tombstone with the retirement reason, the `isReplacedBy` successor PID, and the license at time of publication. Without this, Shepard cannot be cited as a certified repository in Horizon Europe DMPs. The immutability lock on `CERTIFIED`/`PUBLISHED` (Manufacturing Proposal 2) is the companion: it prevents silent un-publication that would orphan cited PIDs.

---

## Top 3 I'm Challenging

### Challenge 1: Metadata Profile Enforcement Plugin (RDM Proposal 10 / Ecosystem EP-06)
**Proposing agents:** RDM agent (Proposal 10), Ecosystem Advocate (EP-06)

**The claim:** A `MetadataProfile` plugin enforces required attributes and annotation predicates per DataObject type, blocking status advancement if mandatory fields are missing. The RDM agent proposes this as the structural fix for the attributes–annotations duality gap.

**The compliance gap the proposing agents missed:** Mandatory field enforcement through a status-gate plugin is the *right architecture* — but both proposals under-specify what happens to the FAIR fields (license, accessRights, ORCID) that are not `attributes` map entries and not semantic annotation predicates. They are typed fields on `AbstractDataObject`. The MetadataProfile plugin as designed enforces `requiredAttributes` (Map keys) and `requiredAnnotationPredicates` (IRI list). It does not enforce `license != null` before status advances to `IN_REVIEW`. This means a Collection can advance to `PUBLISHED` with no license and still satisfy all configured MetadataProfile checks — because license is not an attribute key and not an annotation predicate.

**My verdict:** REDIRECT. This plugin is correct in its direction but must be co-designed with the FAIR Metadata Spine (Champion 1). The MetadataProfile validation hook must also inspect the four FAIR entity fields. The simplest fix: the `MetadataCompletenessService` (Champion 2) becomes the gating mechanism for status advancement above `IN_REVIEW`, not a separate profile engine. The MetadataProfile plugin then covers only the domain-specific attributes (fiber_lot_id, ply_count, bench) while the completeness service covers the universal FAIR fields. Two gates, clearly separated concerns — but they must both fire, not just one.

Additionally, neither proposal addresses what happens during data migration when existing DataObjects without mandatory attributes need to advance. The plugin should default to `warn-only` mode (log + notification, no block) until explicitly flipped to `enforce` mode by the admin, following the FeatureToggle pattern. Without this, the plugin would silently block all existing DataObjects from advancing status on day one of deployment. That is a breaking upgrade, which violates the CLAUDE.md upgrade-path mandate.

---

### Challenge 2: Snap Dashboards / AI Audit Narrative Generator (Strategy Proposal 7, Analytics-AI Proposal 7)
**Proposing agents:** Strategy Advisor (Proposal 7 — "Chart from description"), Analytics-AI (Proposal 7 — "AI Audit Narrative Generator")

**The claim:** A natural-language-to-chart feature and an LLM-generated audit narrative are high-value near-term AI features that demonstrate the "Shepard + AI" story for funding reviewers.

**The compliance gap the proposing agents missed:** An AI-generated audit narrative submitted to an EN 9100 auditor or an EASA Part 21 G conformity reviewer is not an audit document — it is an AI-generated summary with no verifiable chain of custody. EN 9100 §10.2 requires that corrective action records be *documented information* — specifically, information that is "controlled" per §7.5.2 (reviewed and approved before use, protected from unintended alteration). An LLM-generated Markdown blob that the researcher optionally saves to a Lab Journal entry is not controlled documented information. If Shepard presents it as an audit trail, it misleads operators about what the document's legal standing is.

**The deeper problem:** The audit narrative as specified in Analytics-AI Proposal 7 uses the LLM's TEXT capability with a "You are a DIN EN 9100 audit report generator" system prompt. The LLM will produce plausible-sounding audit language. A quality engineer who does not read the full output carefully may believe this is the authoritative chain-of-custody record. It is not — it is a summary derived from graph traversal. The actual chain-of-custody is the ancestor-chain endpoint's structured JSON, signed by the system (via PROV-O activity nodes), not the prose the LLM derived from it.

**My verdict:** CHALLENGE as currently specified. The ancestor-chain traversal (Champion 3) produces the structured, system-signed provenance record that is the genuine audit trail. The LLM narrative is a *summary of that record for human readability*. The feature is valuable, but it must be labeled clearly: "AI-generated summary — not a controlled document under EN 9100 §7.5.2. The authoritative record is the structured provenance data accessible at [endpoint]." The UI must show this disclaimer prominently. The Lab Journal entry, if saved, must carry the same disclaimer as a mandatory annotation. Without these safeguards, this feature creates legal and compliance risk for operators.

Additionally: both proposals hard-depend on `shepard-plugin-ai` TEXT capability, which is 2–3 sprints away. Neither should be on the HMC critical path.

---

### Challenge 3: Conference-Mode Demo Narrative Layer (Ecosystem EP-04)
**Proposing agents:** Ecosystem Advocate (EP-04)

**The claim:** A "Story Mode" toggle (`?story=true`) adds narrative banners and a `status: ANOMALY_DETECTED` badge to TR-004, creating a guided demo experience backed by a `ShepardStory` StructuredDataReference seeded into the LUMEN dataset.

**The compliance gap the proposing agents missed:** This proposal introduces a `status: ANOMALY_DETECTED` badge on TR-004 that is *different from* TR-004's actual `status` value in the graph. If the status field shows `DRAFT` (or whatever it is) in the DataObject edit panel but `ANOMALY_DETECTED` in the badge, a researcher who evaluates the platform during a demo will make incorrect assumptions about what the status field means and what values are supported. This creates a false impression of a feature that does not exist — specifically, a status value that is automatically set by the anomaly detection system (AI1b does not currently set status; it creates annotations). When they deploy Shepard themselves, that badge will not appear.

**The deeper problem for FAIR:** A `ShepardStory` StructuredDataReference is a non-standard container type created specifically for demo purposes. If this convention is not explicitly marked as demo-only metadata, it risks polluting the FAIR metadata of the LUMEN dataset. The RO-Crate export would include the story content unless explicitly excluded. A harvester would ingest it as part of the Collection's metadata. That is not a minor issue — it degrades the quality of the HKG feed for the LUMEN dataset.

**My verdict:** REDIRECT. The demo narrative layer is a valid UX idea but must not touch entity fields (status) or entity metadata (StructuredDataReference). The correct implementation: (a) the narrative content lives entirely in a `docs/help/lumen-walkthrough.md` page (already planned per the in-app docs structure), (b) the `?story=true` banner reads from a static JSON file bundled with the frontend at build time — not from a database entity, (c) the TR-004 "highlighted" treatment uses the *actual* AI1b anomaly annotation already present on TR-004 (if `createAnnotations=true` was run during seeding), not a fabricated status value. This way the demo layer shows real data correctly, not fictional status values.

---

## The HMC Critical Path (Ordered, ~6 Weeks to 06 July 2026)

The HMC Project Call 2026 evaluation requires demonstrable evidence of:
1. FAIR-compliant datasets in the HKG feed (entity-level license + ORCID + access rights)
2. A working Helmholtz Knowledge Graph feed with correct metadata4ing terms
3. A metadata completeness signal visible to researchers in-app
4. Controlled vocabulary annotations (CHAMEO, metadata4ing, QUDT) used in real datasets

**Week 1 (now → 2026-05-28): Foundation fields — no excuses**

1. `license` (SPDX String) on `AbstractDataObject` — one additive field, cascade to KIP plugin, Unhide plugin, ExportService. S effort. Zero blockers. This is day 1 work.
2. `createdByOrcid` stamped at creation time from `User.orcid`. One line in creation service. S effort. Zero blockers.
3. `accessRights` enum + `embargoEndDate`. Two additive fields + validation rule. S–M effort. Zero blockers.
4. `fundingReferences` (List<FundingReference>). M effort (value type + serialization + ROR autocomplete in frontend). Start now, land by end of week 2.

**Week 2 (2026-05-28 → 2026-06-04): Completeness score + feed hardening**

5. `GET /v2/collections/{appId}/metadata-completeness` endpoint — `MetadataCompletenessService` scoring rubric. M effort. Blocked only on week 1 fields.
6. Metadata completeness ring on Collection sidebar — red/amber/green with checklist. M effort. Parallel to step 5.
7. `GET /v2/admin/unhide/feed-validation` — reports which Collections would fail HMC KIP mandatory-field checks. S effort. Blocked on step 5 (uses the same completeness service).
8. Unhide plugin: source `schema:license` from `Collection.license` (with fallback to instance default). S effort. Blocked on step 1.

**Week 3 (2026-06-04 → 2026-06-11): Vocabulary + seed upgrade**

9. CHAMEO + SSN/SOSA manifest entries — two TTL entries, SHA-256 pinned. S effort. Zero blockers.
10. `mffd-process.ttl` + `pluto-mission.ttl` + `lumen-facility.ttl` vocabulary packs. S effort. Zero blockers.
11. QUDT unit picker at channel creation (UX P9). S effort (reuses AddAnnotationDialog infrastructure).
12. LUMEN seed FAIR upgrade: license, ORCID IRI, funder, accessRights, unit annotations on 25 channels, MaterialBatch DataObjects for LOX/LCH4 lots. S effort.

**Week 4 (2026-06-11 → 2026-06-18): DMP tooling + qualified references**

13. `GET /v2/collections/{appId}/dmp-snippet` — Markdown block pre-populated from entity fields (no LLM dependency). S effort. Blocked on week 1 fields.
14. `GET /v2/data-objects/{appId}/ancestor-chain?depth=N` endpoint — depth-bounded Cypher traversal. M effort. Zero blockers.
15. RO-Crate export: use PIDs (not appIds) for linked predecessor/successor entities when a Publication record exists. S–M effort. Blocked on step 14 (uses PublicationService.findPidForEntity).

**Week 5 (2026-06-18 → 2026-06-25): Tombstone + publish gate hardening**

16. 410 Gone tombstone response for retired entities. M effort. Requires `retiredAt` + `retiredReason` on Publication, tombstone JSON-LD body in entity endpoint.
17. `PublishService` publish gate at completeness score ≥ 60 (admin-configurable). S effort. Blocked on step 5.
18. `successorPid` on Publication — emitted in tombstone's `isReplacedBy` field. S effort. Alongside step 16.

**Week 6 (2026-06-25 → 2026-07-04, landing before deadline): HMC submission prep**

19. NFDI4ING spotlight page (`docs/reference/nfdi4ing.md` + help quickstart). S effort. Write after vocabulary pack (step 10) is seeded so worked example is accurate.
20. Validate HKG feed against HMC KIP v1.1 mandatory fields using the feed-validation endpoint (step 7). Fix any remaining gaps.
21. `shepard-plugin-publisher` Zenodo adapter — if bandwidth allows (L effort, may slip to post-deadline). Minimum viable alternative: document the manual RO-Crate export + Zenodo upload path in the NFDI4ING spotlight page, with a clear "push-deposit button coming Q3 2026" note. The DMP can reference Zenodo as the deposit target even if the automation is not yet live.

**What is NOT on this critical path (and why):**

- `shepard-plugin-ai` (2–3 sprint prerequisite) — no path to HMC deadline
- Snap dashboards, audit narrative generator, semantic search — all require plugin-ai
- AnnotatableFile bridge — important but not HMC-blocking
- Shop floor UI (P7, Manufacturing proposals) — EN 9100 compliance, not HMC compliance
- QualityStatus extension — valuable, not HMC-blocking

---

## Merges I'm Calling

### Merge A: All "FAIR entity fields" proposals into one PR
**Merge:** RDM Proposals 1+2+3+4 + Data-Ontologist Proposal 7 + API-Scrutinizer Proposal 6 fields-only layer + Strategy Proposal 1 fields-only layer + Analytics-AI Proposal 10 fields-only layer

All eight proposals add between 2 and 5 fields to `AbstractDataObject`. They share the same migration pattern (additive `SET n.field = null` on existing nodes), the same IO class (`AbstractDataObjectIO`), the same Neo4j migration file, and the same cascade targets (KIP plugin, Unhide plugin, ExportService, frontend edit dialog). Shipping them in five separate PRs creates five separate migrations, five separate reviews, and five opportunities for merge conflicts in the same file. One PR, one migration, one review — with each field clearly documented in the migration comment.

The one field that must NOT be merged here: `accessRights` enforcement in PublishService (the block on RESTRICTED entities) — that is a behaviour change that warrants its own PR after the fields land.

### Merge B: Metadata Completeness Score + DMP Snippet + Unhide Feed Hardening
**Merge:** RDM Proposal 5 (score endpoint) + RDM Proposal 9 (Unhide plugin improvements) + API-Scrutinizer Proposal 6 (score endpoint, same spec) + UX P4 (completeness ring frontend) + Analytics-AI Proposal 10 (score gate in PublishService)

These five proposals specify the same `GET /v2/collections/{appId}/metadata-completeness` endpoint with the same nine-check scoring rubric. They are the same feature described by five different agents. One backend service, one endpoint, one frontend widget. The Unhide plugin's feed-validation endpoint (`GET /v2/admin/unhide/feed-validation`) is a natural addition to the same PR because it uses the same MetadataCompletenessService.

### Merge C: Ancestor Chain + Flat DataObject Lookup + Provenance Gap Detector
**Merge:** API-Scrutinizer Proposal 4 (ancestor chain endpoint) + API-Scrutinizer Proposal 2 (flat GET /v2/data-objects/{appId}) + Analytics-AI Proposal 6 (same ancestor chain spec) + Manufacturing Proposal 5 (same ancestor chain + DAG view) + RDM Proposal 11 (same ancestor chain, different UI) + Data-Ontologist Proposal 8 (provenance gap detector — depends on ancestor chain infrastructure) + Strategy Proposal 3 (same ancestor chain + RO-Crate export variant)

Five agents propose the same two endpoints. The flat `GET /v2/data-objects/{appId}` is a 1–2 day prerequisite for the ancestor chain (which needs to resolve appIds without collection context). Ship them together. The provenance gap detector adds a Cypher query set over the same graph traversal infrastructure. The RO-Crate audit-trail export variant (`?format=ro-crate` on the ancestor chain endpoint) reuses ExportService and the same chain result.

### Merge D: CHAMEO + SSN/SOSA + Domain Vocabulary Packs + QUDT Unit Picker
**Merge:** Data-Ontologist Proposals 3+5 + Manufacturing Proposal 10 + UX P9 + Strategy Proposal 4 (ontology portion)

These all touch `ontologies-manifest.json`, `backend/src/main/resources/ontologies/`, and the AddAnnotationDialog/AddChannelDialog frontend. One PR, one manifest review, one regression test run against n10s ingestion. The QUDT unit picker (UX P9) belongs in the same PR because its autocomplete calls the same `GET /v2/semantic/terms/search?q=` endpoint that CHAMEO and SSN/SOSA terms will now populate.

### Merge E: Tombstone + Immutability Lock + Publish Gate
**Merge:** RDM Proposal 7 (tombstone) + Manufacturing Proposal 2 immutability-lock portion + Analytics-AI Proposal 10 publish-gate layer

The tombstone (410 Gone response) and the immutability lock (reject PATCH that un-publishes a PUBLISHED/CERTIFIED entity) are both enforced at the same code site: the entity PATCH handler and the publication endpoint. The publish gate (score ≥ threshold before PID mint) is in PublishService. All three belong in the same PR because they collectively define "what PUBLISHED means" — a definition that must be internally consistent and cannot be partially shipped.

---

## My Overall Priority Stack

The following ordering is from my FAIR/RDM lens. It is NOT identical to any individual agent's stack, because I am weighting by: (a) HMC deadline pressure, (b) funding mandate compliance, (c) cascade value (how many downstream features does this unblock), (d) reversibility risk (can we ship it wrong and still recover?).

**Tier 0 — Do these before anything else (no blockers, days of effort, irreversible value)**
1. FAIR entity fields — license, createdByOrcid, accessRights, embargoEndDate, fundingReferences on AbstractDataObject (Merge A)
2. CHAMEO + SSN/SOSA + domain vocabulary packs + QUDT unit picker (Merge D)
3. LUMEN seed FAIR upgrade + MaterialBatch DataObjects + unit annotations (RDM Proposal 12, Data-Ontologist Proposal 2)

**Tier 1 — HMC critical path, ship in weeks 2–4**
4. Metadata Completeness Score + ring + DMP snippet + Unhide feed hardening (Merge B)
5. Ancestor chain endpoint + flat DataObject lookup + provenance gap detector (Merge C)
6. Tombstone + immutability lock + publish gate (Merge E)
7. NFDI4ING spotlight page + docs (Ecosystem EP-09)

**Tier 2 — High FAIR value, ship in weeks 4–8**
8. Typed container reference arrays in DataObjectV2IO (API-Scrutinizer Proposal 1) — CRITICAL blocker for every AI agent and every script that reads DataObjects; S effort; should be Tier 0 but does not directly affect HMC FAIR score
9. Qualified PID references in RO-Crate and Unhide feed (RDM Proposal 8) — blocked on ancestor chain (step 5); closes FAIR I3 properly
10. shepard-plugin-publisher Zenodo adapter (RDM Proposal 6, Ecosystem EP-05) — blocked on Tier 0 fields; L effort; may slip post-07 July but should be in active development by then
11. AnnotatableFile bridge (Data-Ontologist Proposal 4, Manufacturing Proposal 6) — closes FAIR I3 at file granularity; needed before CHAMEO defect annotation is practically useful on NDT scans
12. Numeric semantic annotations + range queries (Data-Ontologist Proposal 1, Analytics-AI Proposal 5) — closes the attributes-vs-annotations duality gap for quantitative measurements

**Tier 3 — Important but not FAIR-blocking**
13. QuantifiedAnnotation numeric field + AnnotatableFile bridge (pre-req for CHAMEO at file level)
14. QualityStatus extension + predecessor gate + role-gated transitions (Manufacturing Proposals 1+2+6 as unified quality foundation)
15. Bulk row selection + action toolbar (UX P1, Manufacturing Proposal 8) — high UX value; not FAIR-blocking
16. Global entity search (UX P8, Strategy Proposal 9) — adoption-critical; not FAIR-blocking
17. API hygiene: ProblemJson everywhere + OpenAPI tag cleanup + pagination consistency (API-Scrutinizer Proposals 5+7+8) — developer experience; not FAIR-blocking but prerequisite for any SDK
18. TS-IDa/IDb migration (API-Scrutinizer Proposal 9, Strategy Proposal 5) — ML infrastructure; critical for analytics roadmap, not for HMC

**Tier 4 — Deferred (require plugin-ai or are non-FAIR-blocking)**
19. PDF auto-annotation suggestions (API-Scrutinizer 10, Analytics-AI 4) — requires plugin-ai
20. Snap dashboards MVP (Strategy Proposal 7) — requires plugin-ai; CHALLENGE flag applies
21. AI audit narrative generator (Analytics-AI 7) — requires plugin-ai; CHALLENGE flag applies; must carry disclaimer
22. Semantic embedding / DataObject similarity search (Analytics-AI 8) — requires plugin-ai + corpus size
23. Unhide AI-enhanced feed enrichment (Analytics-AI 11) — requires plugin-ai; nice-to-have
24. Equipment registry / calibration plugin (Data-Ontologist 10, Manufacturing 4) — L effort; important for Nadcap; not HMC-blocking
25. Shop floor template mode (Manufacturing 7, Strategy 6) — important for MFFD production adoption; not FAIR-blocking
26. Annotation label refresher (Data-Ontologist 9) — data integrity utility; low urgency
27. Causal edge on TimeseriesAnnotation (Data-Ontologist 12) — PLUTO-specific; low urgency
28. Supervised anomaly labelling UI (Analytics-AI 12) — builds ML corpus; deferred until corpus is larger

**The dataship / publisher plugin choice (final position):**
`shepard-dataship` should be maintained as a Python CLI implementation of the same `RepositoryAdapter` SPI that the in-app publisher plugin uses. The two tools share the same metadata mapping table (license → DataCite license, ORCID → DataCite creator, funder → DataCite fundingReference). They are not alternatives — they serve different user personas (CLI power user vs. in-app researcher). Coordinate their metadata mapping tables so they produce identical Zenodo submissions. Ship the metadata fields (Tier 0) before either tool, because both consume the same four fields.

**The JupyterHub J2e FAIR note:**
When a JupyterHub notebook is auto-saved to Shepard, the resulting DataObject (or FileBundle) must carry at minimum: `createdByOrcid` (from the authenticated user), `license` (inheritable from the parent Collection's default), and a semantic annotation `prov:wasGeneratedBy` linking to the notebook execution activity. The "Jupyter Analysis" template should pre-populate these from session context. Without ORCID and license on the notebook DataObject, the analysis product is not FAIR even if the source data is.

**The hotfolder ingest SPI note:**
The routing rule DSL for `shepard-plugin-hotfolder` must support these fields as first-class routing metadata:
- `creator: "{instrument_orcid_or_api_key_owner}"` (required)
- `license: "CC-BY-4.0"` (required, with instance-admin default)
- `accessRights: "OPEN|EMBARGOED|RESTRICTED"` (default OPEN)

If these fields cannot be carried in the routing rule, hotfolder ingestion produces FAIR-non-compliant datasets by design. That is a structural problem that must be fixed before the hotfolder plugin ships to production at any Helmholtz institute. A hotfolder that ingests AFP measurement data without creator attribution and without a license is not a FAIR ingest pipeline — it is a data silo with a file-drop.
