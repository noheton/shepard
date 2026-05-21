# Strategy Advisor — Cross-Agent Debate
**Date:** 2026-05-21
**Author:** Strategy Aligner & Executive Advisor (Phase 2)
**Inputs:** All 8 agent proposal documents (ux-auditor, data-ontologist, api-scrutinizer, manufacturing-quality, research-data-manager, strategy-advisor, analytics-ai, ecosystem-advocate)

---

## Top 5 I'm championing (with specific funding/adoption/moat rationale)

### #1 — CHAMPION: API Hygiene Sprint (referenceIds + typed container arrays + ProblemJson + pagination)
**Sources:** api-scrutinizer P1/P5/P7/P8; strategy-advisor P12; ecosystem-advocate EP-07

**Rationale:** This is the most asymmetric effort/value trade in the entire proposal set. The
`referenceIds` bug is a **live blocker today** for every machine caller — the MCP server, any
generated SDK, the AI manifest generator, and every agentic workflow we pitch to Clean Aviation
program managers. An institute evaluator who runs `make demo`, pulls a DataObject, tries to
navigate to its timeseries, and gets a 404 will not adopt Shepard. The bug costs one day to
fix. The full hygiene sprint (typed container arrays + unified pagination envelope + ProblemJson
global mapper + human-readable OpenAPI tags) is M effort and unlocks every downstream proposal
in this stack.

**What no other agent weighted correctly:** the API is the product for machine callers. We pitch
"agentic ingest" and "MCP-native exploration" as differentiators at NFDI4ING and Clean Aviation
meetings — but both collapse the moment a caller gets a 404 from a misnamed ID field. Fixing the
API surface IS the adoption gate for the programmatic audience. The ecosystem advocate called out
the MCP bug. The API scrutinizer traced it to the `referenceIds` naming. Nobody else calculated
that this single field is the reason the MCP demo fails live.

**Funding/adoption/moat:** Every DLR institute evaluator will hit this bug in their first week. It
is not a deep architectural issue — it is a naming error that signals the API surface was never
exercised from the outside. Fixing it is a public statement of API seriousness. The ProblemJson
global mapper automatically improves all 11 plugins at once — multiplier effect with zero per-plugin
effort.

**Sequencing note:** This goes first, before any feature work. No other proposal delivers clean
value until the API surface is trustworthy.

---

### #2 — CHAMPION: FAIR Metadata Spine (license + ORCID stamp + accessRights)
**Sources:** rdm P1/P2/P3; strategy-advisor P1; data-ontologist P7; api-scrutinizer P6; analytics-ai P10

**Rationale:** Every major funding body requires machine-readable license and creator identity as
mandatory PID metadata fields. HMC KIP v1.1 requires `schema:license`. DataCite Metadata Schema
4.5 requires `Creators` with ORCID URI. Horizon Europe Art. 17 requires access rights
classification. All three fields are literally missing from `AbstractDataObject.java` today.

This is not a feature. It is a **compliance gap** that, if unclosed before the HMC 06 July 2026
deadline, means the HMC submission package will demonstrate a FAIR spine that lies: the Unhide
feed already asserts `schema:license` from a global fallback, not from an entity field. An HMC
reviewer who queries the feed and finds a collection-level license assertion backed by a default
config string — not by an entity field — will flag it. The strategy advisor's own findings called
this a "FAIR spine that lies."

**Funding/adoption/moat:** Three additive Neo4j fields + one-line ORCID stamp at creation time
+ frontend edit panel. Effort: S. Unlock: Unhide feed becomes honest, KIP PID minting gains a
valid `policy` field, the publisher plugin (shepard-plugin-publisher) can submit correct DataCite
metadata, and the metadata completeness score widget (all four agents proposed variants of this)
has real data to compute from.

**What no other agent weighted correctly:** The RDM agent designed these fields correctly. The
data ontologist proposed them (P7). The API scrutinizer called for a completeness endpoint (P6).
The strategy advisor named them in P1. Five agents noticed the same gap independently — that
convergence is the strongest signal in the entire proposal set. The question is not whether to
ship it; the question is why it has not shipped already.

---

### #3 — CHAMPION: TS-IDa/IDb — Channel AppId Migration
**Sources:** api-scrutinizer P9; strategy-advisor P5; analytics-ai P9 (embedded in sequencing)

**Rationale:** The 5-tuple channel identity problem is simultaneously a developer ergonomics failure,
an ML pipeline stability risk, a live-window performance regression (full table scan per request),
and a stored-chart-view breakage waiting to happen. TS-IDa (mint UUIDs on existing Timeseries
nodes) is a single idempotent Cypher migration — one line, zero risk. TS-IDb (expose `appId` in
the channel list response) is one field added to the DAO response mapper — zero breaking changes,
backward compatible.

**Why this is strategic and not just infrastructure:** Every AI proposal in the analytics-ai stack
— manifest generation, semantic embedding, anomaly detection, supervised labelling — requires
stable channel addressing. A channel rename today breaks every training script, every MCP tool
call, every import manifest silently. The MFFD AFP dataset arriving ~2026-05-26 is the first real
production corpus. If it is ingested before TS-IDb ships, every ML pipeline built against it will
use pipe-separated 5-tuples. Once there are 300+ channels in the system, the migration cost
multiplies. Ship it now, before the real data arrives.

**Funding/moat:** This is the infrastructure change that makes "stable, ML-addressable timeseries
channels" a true claim. No competitor (Kadi4Mat, SciCat, Coscine) addresses the ML pipeline
stability problem for timeseries at channel granularity. Shepard can be the first to say "every
channel has a stable UUID that survives renames and schema changes." That is a moat.

---

### #4 — CHAMPION: `make demo` One-Liner + Conference-Mode Story Layer
**Sources:** ecosystem-advocate EP-01/EP-04; strategy-advisor P11 (MFFD seed)

**Rationale:** These two proposals are undervalued by every other agent because they do not add
features — they expose the features that already exist to people who cannot currently see them.

The situation is this: Shepard has 15 test runs, a turbopump vibration anomaly, an investigation
chain, a repair, and a re-test. This is a genuinely compelling demo story. It is entirely invisible
to anyone who clones the repo and runs `docker compose up`. They see a login screen, log in as
`admin`, see an empty instance, and leave. The `make demo` target and the conference-mode story
layer together convert the first 10 minutes from "where is anything?" to "watch this."

**Funding/adoption argument:** A funding body reviewer who can be sent a URL and sees a live demo
with a guided narrative is infinitely more convincing than a slide deck. At 2–3 days of effort
(S), this is the highest-ROI investment in the ecosystem that can ship before the HMC deadline.
The NFDI4ING spotlight page (ecosystem-advocate EP-09) compounds this — the docs exist, someone
needs to write them, and 3 days of writing can be cited in an HMC submission as evidence of
community engagement.

**What the ecosystem advocate got right that others missed:** EP-04 (conference-mode story layer)
is not a vanity feature. It is the mechanism that makes the platform's provenance graph legible to
a non-technical stakeholder in 60 seconds. A program manager at Clean Aviation JU who sees
"TR-004 anomaly at t=8s → investigation → repair → TR-006 re-test, all in the graph" in a guided
walkthrough will fund a follow-on study. The same manager staring at an unnarrated DataObjects
table will not.

---

### #5 — CHAMPION: Ancestor-Chain API + Flat DataObject Lookup
**Sources:** api-scrutinizer P2/P4; manufacturing-quality P5; research-data-manager P11;
analytics-ai P6; ux-auditor P5; strategy-advisor P3

**Rationale:** This is the most cross-cited missing operation in the entire proposal set. Every
agent found it from a different direction:
- The UX auditor found the 6-predecessor truncation in `DataObjectProvGraph.vue` (lines 86/106)
- The manufacturing quality agent found the missing EN 9100 §7.8.2 audit trace capability
- The API scrutinizer found the missing flat `GET /v2/data-objects/{appId}` endpoint
- The analytics agent found the missing predecessor walk needed for provenance gap detection
- The RDM agent found the incomplete FAIR I3 traversal (qualified references not using PIDs)

Six agents, five different entry points, one missing endpoint family. That is the signal.

**Strategic case:** The ancestor-chain endpoint + flat DataObject lookup together close the
"provenance graph is beautiful but unreachable" problem. Right now Shepard's graph model is its
strongest moat claim — no competitor has PROV-O + Neo4j traversal at this depth. But that moat
is invisible if the only way to traverse the graph is the 6-node-capped force layout in the
frontend. The API must expose what the graph knows. `GET /v2/data-objects/{appId}/ancestor-chain`
is the public face of the provenance moat.

**The `REDACTED` node design:** The api-scrutinizer's detail on cross-collection permission handling
(nodes the caller cannot read are replaced with `{"depth": N, "redacted": true, "appId": "..."}`)
is the right answer. Chain depth is visible even when content is restricted — the auditor can see
the chain exists without seeing the content. This is a meaningful feature for EASA Part 21 G
scenarios.

---

## Top 3 I'm challenging (with the strategic cost the proposing agent didn't account for)

### CHALLENGE #1: `shepard-plugin-publisher` (Zenodo push-deposit) before HMC deadline
**Sources:** rdm P6; strategy-advisor P2; ecosystem-advocate EP-05

**The proposal:** Ship a full Zenodo push-deposit plugin before or alongside the HMC 06 July 2026
submission.

**Why I'm challenging it:** The publisher plugin has hard dependencies on Proposals 1 + 3 + 4 from
the RDM agent (license, accessRights, embargoEndDate, fundingReferences) — all of which need to
ship first. The plugin itself is L effort (2–3 weeks minimum, with an async job pattern, admin
config singleton, CLI parity, and integration tests against the Zenodo sandbox). The HMC deadline
is 06 July 2026 — 6 weeks away. Stacking the FAIR metadata spine (P1/P2/P3) + funder references
(P4) + completeness score endpoint (P5) + the publisher plugin itself in 6 weeks while also
preparing the HMC submission narrative is a schedule that requires everything to go right.

**The strategic cost the agents didn't account for:** A broken or incomplete publisher plugin
demo at an HMC presentation is worse than no publisher plugin demo. Zenodo sandbox behavior is
not the same as production Zenodo. An async job that fails silently during a live demo, or a
metadata record that Zenodo rejects because the SPDX identifier format is wrong, is a credibility
loss. The RDM agent acknowledged the dependency chain but did not model the integration test time.

**My redirect:** Target the publisher plugin for the wave after HMC — Q3 2026. What the HMC
submission actually needs is: (a) a live Unhide/HKG feed that is honest (requires FAIR metadata
spine, not the publisher plugin), (b) a PID-minted LUMEN collection via KIP (already functional),
and (c) a completeness score demonstrating FAIR-readiness. Those three together make the HMC
case. The publisher plugin is the "next step we'll demonstrate post-award" narrative, not a
prerequisite.

---

### CHALLENGE #2: Shop Floor Template Mode (large-target, touch-first) in the current sprint
**Sources:** manufacturing-quality P7; strategy-advisor P6; ux-auditor P7

**The proposal:** A full shop-floor rendering mode with 80px status buttons, barcode scanner URL
scheme, QR code generation, and Template DSL extension.

**Why I'm challenging it:** This is L effort (4+ weeks) targeting a very specific hardware context
(ruggedized 27" touchscreen at MFFD Augsburg). The strategic context matters here: the MFFD AFP
dataset is arriving ~2026-05-26, and the dataset itself is more important to the funding narrative
than the shop-floor rendering mode. A PI pitching to Clean Aviation JU does not need the
shop-floor mode to be live — they need the data to be in the system with CHAMEO annotations and
a traceable ancestor chain. The shop-floor mode matters for *operational adoption* by IMEs, not
for *funding-level demonstration*.

**The strategic cost the manufacturing quality agent didn't account for:** Shop-floor UI requires
real user testing with real IMEs in real shop-floor conditions. A shop-floor mode that has never
been tested on a gloved operator at 85 dB will fail in the field — and a failed demo at an MFFD
sprint review is worse than no demo. The manufacturing quality agent documented the requirements
correctly (P7) but did not account for the iteration cost of hardware-context UX work.

**My redirect:** Ship the two highest-value shop-floor primitives that cost S/M effort and work
on any device: (a) the "Raise NCR / Place Hold" quick-action button (the manufacturing quality
agent's two-field form — this is a composable reuse of existing DataObject creation, not a new
rendering system), and (b) the large-target status advance chip (a Vuetify chip variant, not a
full rendering mode). These two deliver 80% of the IME value at 20% of the effort. The full
shop-floor Template DSL extension goes into a dedicated P-series sprint after the real dataset
is live and IME feedback has been collected.

---

### CHALLENGE #3: Controlled-Vocabulary Annotation Enforcement Plugin (metadata-profiles) now
**Sources:** rdm P10; ecosystem-advocate EP-06; data-ontologist (implied by annotation playbook)

**The proposal:** `shepard-plugin-metadata-profiles` — domain-specific required-field enforcement
with admin UI, status-transition hook SPI, and built-in profile seeding.

**Why I'm challenging it:** This is L effort and requires a design decision that the proposal
punts: where does the profile definition live? The RDM agent proposes a `MetadataProfile` Neo4j
entity in the plugin. The ecosystem advocate proposes a `CollectionSchema` StructuredDataReference
or new Neo4j entity (explicitly noting "design doc needed"). The manufacturing quality agent wants
status-transition gates per process step. These three proposals want the same enforcement
mechanism but disagree on the data model.

**The strategic cost all three agents didn't account for:** Building an enforcement mechanism on
top of an unresolved schema design question will produce a plugin that must be rewritten when the
design is settled. The Templates system (T1a–T1f, shipped) already provides required-field hints
at DataObject creation time. The right sequencing is: (1) validate that Templates with required
attributes are sufficient for the MFFD process step use case, (2) if not, write a design doc that
resolves the three competing models, (3) then ship the enforcement plugin. Rushing the plugin now
risks duplicating the Templates SPI or creating a parallel enforcement mechanism that conflicts
with it.

**My redirect:** DEFER to Q3 2026. The two near-term alternatives that close the most urgent gap
without a new design decision: (a) extend the existing Templates DSL with a `requiredAttributes`
list that blocks status advancement (this is entirely within the existing T1 framework, no new
plugin needed — it is a Templates improvement, not a new plugin), and (b) ship the annotation
suggestion drawer (ux-auditor P3) which uses AI to nudge users toward the right annotations
without hard-enforcing them. Enforcement without friction is better than enforcement that blocks
researchers at 2 AM.

---

## The unified ingest story (how SPW + sTC + hotfolder + dataship fit together strategically)

The four ingest components are being proposed piecemeal. Here is how they actually form a
coherent story for external audiences — and what the strategic sequencing should be.

**The problem they collectively solve:**
Data enters Shepard from four sources today, each with its own friction point:
1. **SPW (process wizard)** — process parameter data from CFRP welding runs. Desktop app, no
   browser. Strategic gap: the IME cannot use Shepard without a separate desktop tool.
2. **sTC (timeseries collector)** — live sensor streams from instruments. Already the primary
   ingest path. Strategic gap: configuration requires IT expertise; new instruments take days to
   wire up, not minutes.
3. **hotfolder (shepard-plugin-hotfolder)** — file drop from instruments that write to disk.
   Strategic gap: currently requires Node-RED, which is a separate tool with its own maintenance
   burden.
4. **dataship** — publication pipeline to external repos. Strategic gap: Zenodo push requires
   manual export.

**The unified narrative (for a Clean Aviation program manager):**

> "An AFP robot run completes. The TCP temperature timeseries flows directly into Shepard via sTC
> — no IT ticket, no configuration file edit, just a URL. The runlog file drops into the hotfolder
> and is ingested automatically. SPW's process parameters are captured in-browser, no desktop app
> required. The MFFD quality engineer opens Shepard, sees the new DataObject, runs the ancestor
> chain to confirm material traceability, raises an NCR if the NDT scan shows a delamination, and
> approves the panel for the next process step. When the campaign is complete and the data is
> FAIR-ready, one click pushes the collection to Zenodo with a DataCite DOI. That DOI goes in the
> Clean Aviation deliverable."

That is the pitch. Every ingest component is one sentence in that pitch. The question is what
needs to be true for each sentence to be demonstrable.

**Strategic sequencing for the ingest stack:**

| Component | What's needed to make it demonstrable | Priority |
|---|---|---|
| sTC | Cleaner config UI (10 improvements in aidocs/40 §3); auto-discovery of Prometheus endpoints | HIGH — this is the current live path; reliability is the adoption gate |
| hotfolder | Ship shepard-plugin-hotfolder; document as Node-RED replacement | HIGH — lowers instrument integration to "drop files in a folder" |
| SPW browser | In-browser process design replaces desktop client; must not break existing SPW users | MEDIUM — in-browser is superior for adoption but must be non-breaking for existing DLR ZLP production use |
| dataship → Zenodo | FAIR metadata spine must ship first; then plugin; then Zenodo demo | MEDIUM-LOW — critical for FAIR compliance but not a blocker for the ingest story |

**The critical coordination point for SPW:**
The SPW transition to in-browser is strategically superior (no desktop install = lower adoption
friction) but carries a real migration risk. DLR ZLP is using SPW in production for CFRP welding
process control (Vistein et al., ICINCO 2023). The transition must be staged:
1. Phase 1: browser-SPW runs alongside desktop-SPW; data model is identical; users can choose.
2. Phase 2: browser-SPW reaches feature parity; desktop-SPW is soft-deprecated with a clear
   migration guide in `aidocs/34`.
3. Phase 3: desktop-SPW is archived; browser-SPW is the only path.

Skipping Phase 1 and shipping browser-SPW as the replacement will break production workflows
at DLR ZLP and destroy exactly the institutional trust Shepard needs to expand.

**The ingest SPI (`shepard-plugin-ingest`) unification:**
The analytics agent and API scrutinizer both noted that multiple ingest paths (sTC, hotfolder,
SPW) each have their own configuration patterns and failure modes. The ingest SPI is the right
architectural answer: one plugin interface, multiple source adapters. This is a medium-term
infrastructure goal (Q3 2026), not a prerequisite for any of the above. Ship the individual
components first; unify under the SPI once the patterns are established. Do not let the SPI
design block the hotfolder or SPW-browser from shipping.

---

## HMC 06 July 2026 sprint plan (exactly what, in what order)

The HMC Project Call 2026 submission is a **synthesis milestone**, not a feature release. What
the reviewers need to see is evidence that Shepard is a FAIR-spine-ready platform for Helmholtz
research data. The evidence package requires three things:
1. **A live, FAIR-compliant dataset** (the LUMEN collection, PID-minted, with license and creator)
2. **A working HKG feed** (Unhide plugin emitting `schema:license` from a real entity field)
3. **A completeness score** (demonstrating the platform enforces FAIR metadata before minting)

Everything else in the proposal set that is not on this critical path is a Q3 2026 deliverable.

**Sprint plan (6 weeks, 2026-05-21 to 2026-07-04):**

**Week 1-2: Foundation (non-negotiable blockers)**
- API hygiene sprint: `referenceIds` fix + typed container arrays + ProblemJson global mapper +
  OpenAPI tag cleanup. Effort: M. Rationale: nothing else delivers clean value until the API is
  trustworthy. Ship in Week 1.
- FAIR metadata fields: `license` + `createdByOrcid` + `accessRights` + `embargoEndDate` on
  `AbstractDataObject`. Four additive Neo4j fields + IO updates + frontend panel. Effort: S.
  Ship in Week 1 alongside the API hygiene.
- TS-IDa migration (Neo4j, idempotent): mint UUIDs on existing Timeseries nodes. Effort: XS.
  Ship in Week 1.

**Week 2-3: FAIR evidence layer**
- Metadata completeness score: `MetadataCompletenessService` + `GET /v2/collections/{appId}/
  metadata-completeness` + Collection sidebar ring widget. Effort: M. Ship in Week 2.
- Unhide plugin fix: wire `schema:license` from `Collection.license` field (requires Week 1
  FAIR fields to be live). Add feed-validation admin endpoint. Effort: S. Ship in Week 2.
- LUMEN seed FAIR upgrade: add `license`, `createdByOrcid`, `accessRights`, `funder` to all
  15 LUMEN DataObjects. Add QUDT unit annotations to all 25 channels. Promote lot IDs to
  DataObject attributes + create MaterialBatch predecessor DataObjects. Effort: S. Ship Week 2.

**Week 3-4: Provenance moat + ancestor chain**
- Flat `GET /v2/data-objects/{appId}` endpoint: additive, unblocks MCP + agent traversal +
  ancestor chain. Effort: S. Ship in Week 3.
- `GET /v2/data-objects/{appId}/ancestor-chain`: bounded Cypher traversal, permission-gated
  `REDACTED` nodes, frontend "Trace upstream" panel replacing 6-node truncation. Effort: M.
  Ship in Week 3-4.

**Week 4-5: Import loop completion + demo layer**
- `POST /v2/import/jobs`: implement the missing execute endpoint (commitId → async job → NTF1
  notification). The validate → execute loop has been a dead end. Effort: M. Ship Week 4.
- `make demo` one-liner: Makefile target + seed auto-run + demo account + companion README.
  Effort: S. Ship Week 4 (ties the LUMEN seed upgrade from Week 2 into a runnable demo).
- Conference-mode story layer: `?story=true` flag, TR-004 anomaly banner, guided narrative panel.
  Effort: M. Ship Week 5.

**Week 5-6: NFDI4ING spotlight + HMC package**
- NFDI4ING spotlight page (`docs/reference/nfdi4ing.md` + blog post draft). Effort: S. Ship
  Week 5.
- CHAMEO + SSN/SOSA ontology manifest entries (two TTL file references, SHA-256-pinned, no
  code change). Effort: S. Ship Week 5.
- TS-IDb: expose `appId` in channel list/get responses. Frontend switchover to appId addressing
  across `useFetchTimeseries`, `useFetchChannelPreview`, `ShowTimeseriesReferenceDialog`. Effort:
  M. Ship Week 5-6.
- HMC submission package: assemble narrative, cite live demo URL, cite LUMEN PID, cite NFDI4ING
  spotlight page, cite completeness score ≥ 80 on the LUMEN collection. Week 6.

**What does NOT go in the HMC sprint:**
- shepard-plugin-publisher (Zenodo push): deferred to Q3 2026.
- Shop-floor Template mode: deferred to Q3 2026.
- shepard-plugin-calibration: deferred to Q4 2026.
- Semantic embedding (Proposal 8, analytics-ai): deferred until ~200 DataObjects in system.
- AnnotatableFile bridge: deferred to Q3 2026 (correct structural work, wrong timing).
- Metadata profiles plugin: deferred to Q3 2026 (design doc needed first).

**What ships in the HMC sprint but not as a headline (parallel track, lower risk):**
- Channel unit picker at creation (ux-auditor P9): S effort, no dependencies, pure goodness.
  Any week.
- Channel quality score surface (analytics-ai P1): S effort, frontend-only, closes a "BE only"
  row in the feature matrix. Any week.
- Bulk row selection + action toolbar (ux-auditor P1 / manufacturing-quality P8): M effort, high
  curator value. Week 3-4 if bandwidth allows; otherwise Q3.
- Human-readable OpenAPI tags (api-scrutinizer P8): XS effort. Week 1 alongside API hygiene.

---

## My overall priority stack (ordered by: funding gates → adoption gates → moat builders → nice-to-have)

### Tier 0: Non-negotiable HMC blockers (must ship by 2026-07-04)
1. **FAIR metadata spine** — `license` + `createdByOrcid` + `accessRights` (S effort; unlocks
   KIP, Unhide, publisher, completeness score)
2. **API hygiene sprint** — `referenceIds` rename + typed container arrays + ProblemJson mapper
   + OpenAPI tags (M effort; unlocks every machine caller)
3. **Metadata completeness score** — endpoint + sidebar ring + publish gate (M effort; the
   measurable FAIR evidence the HMC submission needs)
4. **Unhide plugin fix** — entity-level `schema:license` (S effort; makes the HKG feed honest)
5. **LUMEN seed FAIR upgrade** — license/ORCID/funder/units on all 15 runs + MaterialBatch
   predecessors (S effort; the seed is the demo)

### Tier 1: Adoption gates (ship Q2-Q3 2026)
6. **`make demo` one-liner** (S effort; first-session adoption gate for external evaluators)
7. **Ancestor-chain API + flat DataObject lookup** (M effort; makes the provenance moat
   accessible; closes EN 9100 §7.8.2 auditor gap)
8. **TS-IDa/IDb migration** (S+M effort; before real MFFD data arrives; unblocks all ML pipelines)
9. **`POST /v2/import/jobs`** (M effort; closes the validate→execute dead-end; unblocks agentic
   ingest)
10. **Global entity search** (S-M effort; first-session UX for researchers handed a DataObject name)
11. **Conference-mode story layer** (M effort; makes the demo legible to non-technical audiences)

### Tier 2: Moat builders (ship Q3 2026)
12. **CHAMEO + SSN/SOSA ontology bundle** (S effort; closes the MFFD defect vocabulary gap;
    makes CHAMEO-annotated data discoverable in HKG)
13. **QuantifiedAnnotation (numeric annotations)** (M effort; enables range queries; closes
    the most-cited data model gap across all agents)
14. **MFFD domain vocabulary pack** (S effort; SKOS process vocabulary for AFP/welding; makes
    LUMEN freetext annotations into traversable IRIs)
15. **Bulk row selection + action toolbar** (M effort; closes the 350-click curator workflow gap)
16. **Quality status vocabulary + predecessor gate** (M effort; makes Shepard defensible as a
    quality record system before the MFFD real data arrives; required before NCR workflows)
17. **Channel unit picker at creation** (S effort; closes the FAIR I2 gap at the most natural
    moment; no dependencies)
18. **Provenance gap detector** (M effort; the automated version of the EN 9100 audit walk;
    graph analytics with no ML dependency)
19. **NFDI4ING spotlight page + blog post** (S effort; the highest-ROI content investment;
    the technical work is done, someone needs to write it)

### Tier 3: Significant but dependent (ship Q3-Q4 2026)
20. **shepard-plugin-publisher (Zenodo push-deposit)** — depends on Tier 0 FAIR spine; L effort
21. **AnnotatableFile bridge** — M effort; structural analog of AnnotatableTimeseries; correct
    but timing-sensitive (ship after CHAMEO so the new terms are available in the picker)
22. **HDF5 container browser UI** — L effort; closes "BE ✓ / UI pending" for a shipped backend
23. **Semantic embedding for DataObject discovery** — L effort + plugin-ai dependency; triggers
    at ~200 DataObjects (MFFD data arrival is the natural trigger)
24. **AI audit narrative generator** — M effort; depends on plugin-ai TEXT + ancestor-chain API
25. **AI PDF auto-annotation endpoint** — M effort; depends on plugin-ai STRUCTURED
26. **FAIR-compliant PID tombstone (410 + tombstone body)** — M effort; correct FAIR A2 behavior
27. **AAS plugin: semantic mapping extension** — M effort; makes AAS shells carry CHAMEO/QUDT
    semantic IDs; strategically important for Catena-X but not HMC-critical
28. **Annotation label refresh job** — S effort; prevents silent search breakage on ontology
    version upgrades; ship before the MFFD vocabulary pack triggers a re-seed

### Tier 4: Deferred (Q4 2026 or design doc needed first)
29. **shepard-plugin-calibration (Equipment Registry)** — L effort; correct for Nadcap but requires
    NTF1 + coordination with AAS plugin team
30. **Shop-floor Template mode (full rendering system)** — L effort; ship Tier 2 quick-action
    button first; full mode needs real IME testing feedback
31. **Metadata profiles plugin** — L effort; design doc needed first to resolve conflict with
    Templates system
32. **Snap dashboards MVP** — M+M effort (plugin-ai foundation is a 2-3 sprint prerequisite);
    most compelling long-term feature, but premature until plugin-ai is established
33. **Supervised anomaly labelling UI** — M effort; correct for building a training corpus, but
    requires real data volume (triggers at ~200 DataObjects)
34. **Funder references field** (as a full List<FundingReference> value type with ROR autocomplete)
    — M effort; the `fundingReferences: List<String>` variant (simpler) ships with Tier 0; the
    full value-type version is Tier 4
35. **Causal annotation edge (TimeseriesAnnotation → StructuredDataRecord)** — S effort; correct
    for PLUTO but narrow use case; ship after PLUTO use case is more developed

### Cross-cutting invariants (enforce throughout, no sprint milestone)
- Every new feature: test coverage ≥ 70% line (the CI gate enforces this; do not negotiate it)
- Every new endpoint: ProblemJson error shape (once the global mapper ships in Tier 0)
- Every new plugin: `install.md` + `reference.md` + `quickstart.md` (the CLAUDE.md requirement;
  the AAS plugin is currently missing `install.md` — fix in Week 1 alongside the API hygiene)
- Every feature that ships a runtime knob: `:*Config` + admin REST + CLI parity (the CLAUDE.md
  pattern; the quality gate toggle is an example)
- aidocs/34, aidocs/42, aidocs/44 updates accompany every PR that materially changes behavior
  (enforce at PR review, not as a separate sprint)

---

_Generated by the Strategy Aligner & Executive Advisor role | 2026-05-21_
