---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Analytics & AI Opportunities Specialist — Phase 2 Debate

**Role:** Analytics & AI Opportunities Specialist (Role 8)
**Task:** Argue all 96+ cross-agent proposals from the AI/ML domain lens
**Verdicts:** CHAMPION / CHALLENGE / REDIRECT / MERGE / DEFER

---

## Top 5 I'm championing (with specific ML value unlocked)

### CHAMPION #1 — api-scrutinizer #9 / strategy #5: TS-IDa + TS-IDb (stable channel appIds)

**Proposed by:** API Scrutinizer (as CRITICAL), Strategy Advisor (as parallel track)
**Their framing:** DX fix for the 5-tuple ergonomics problem; "each channel needs a stable identifier"
**My reframe:** This is not a DX fix. This is the ML addressing prerequisite.

Every ML pipeline must track channel identity across runs. Today's 5-tuple — `{measurement, device, location, symbolicName, field}` — means one rename on the IPC silently breaks every training script, every anomaly detector, every feature-engineering pipeline. There is no ML model that can survive an unannounced column rename in its input schema.

TS-IDa is a single idempotent Cypher line:
```cypher
MATCH (t:Timeseries) WHERE t.appId IS NULL SET t.appId = randomUUID()
```
TS-IDb exposes that appId in channel list/get responses. The live-window endpoint switches from a full container scan to a `findByAppId` index lookup — a latency drop from O(n) to O(1) at scale.

**ML value unlocked:** Stable channel addressing → persistent feature column identity → reproducible training datasets → reproducible model evaluation. Without this, every ML experiment is one device rename away from silent corruption. This ships before any ML feature. No exceptions.

**Effort:** S (TS-IDa: 1 day migration + test; TS-IDb: 2–3 days API + manifest lift)
**My verdict:** CHAMPION — elevate to ML critical path item #1

---

### CHAMPION #2 — api-scrutinizer #1: Typed container reference arrays

**Proposed by:** API Scrutinizer (CRITICAL severity)
**Their framing:** `referenceIds: long[]` is a leaky Neo4j abstraction — emits internal `BasicReference` node IDs, not container appIds. Causes live 404s.
**My reframe:** This is the ML navigation layer. Every AI agent, every LLM-driven ingest script, every anomaly attribution workflow must navigate from DataObject to its timeseries containers. Today that navigation is broken by design.

The confirmed live bug: `DataObjectIO.referenceIds` emits flat `long[]` of `BasicReference` *edge* node IDs — not DataObject IDs, not container appIds. An MCP tool passing those values to `get_data_object` gets a 404. The fix is typed arrays already established in `DataObjectListItemV2IO`:
```
timeseriesReferenceAppIds: string[]
fileReferenceAppIds: string[]
structuredDataReferenceAppIds: string[]
gitReferenceAppIds: string[]
videoStreamReferenceAppIds: string[]
hdfContainerAppIds: string[]
```

**ML value unlocked:** Any agent traversing the graph (manifest generator, anomaly attributor, doc-annotation-suggest workflow, provenance gap detector) must navigate DataObject → containers. Right now they hit a 404 wall. Fixing this is not a DX improvement — it is the prerequisite for every agentic workflow in the stack.

**Effort:** S (2–3 days; pattern already in `DataObjectListItemV2IO`)
**My verdict:** CHAMPION — ship immediately; blocks all agentic ML workflows

---

### CHAMPION #3 — api-scrutinizer #3: POST /v2/import/jobs

**Proposed by:** API Scrutinizer (MAJOR severity), Strategy Advisor (proposal S2 "Agentic Ingest Pipeline")
**Their framing:** The plan-seal pattern is exposed but the execute step is missing; a `commitId` that expires in 24h with no execute endpoint is a dead end.
**My reframe:** Without this endpoint, the agentic ingest loop is a planning demo. An LLM can generate a valid import manifest, POST it to validate, receive a commitId — and then do nothing. The loop is open.

The fix: `POST /v2/import/jobs` returns:
```json
{
  "jobAppId": "01968...",
  "status": "QUEUED",
  "commitId": "...",
  "estimatedDataObjects": 15,
  "agentContext": { "generatedByAiActivityAppId": "..." }
}
```
Polling: `GET /v2/import/jobs/{jobAppId}`. The `agentContext.generatedByAiActivityAppId` field traces AI-generated manifests through the provenance graph — every DataObject created by an LLM carries a `wasGeneratedBy` link back to the AI activity.

**ML value unlocked:** Closes the agentic ingest loop. Without it, ML pipelines that generate structured metadata from file content (doc-annotation-suggest, manifest-gen-llm) cannot write their outputs back to Shepard. The insight without the write-back is worthless. Also: `AgentContextIO` already carries `generatedByAiActivityAppId` — the provenance infrastructure is ready; only the execute endpoint is missing.

**Effort:** M (the planning half already exists; execution + job status polling is the missing M slice)
**My verdict:** CHAMPION — closes the loop on every agentic proposal in the stack

---

### CHAMPION #4 — data-ontologist #1: QuantifiedAnnotation (numeric value + unitIRI)

**Proposed by:** Data Ontologist (highest-priority structural gap)
**Their framing:** Extend `SemanticAnnotation` with `numericValue: Double` and `unitIRI: String` for FAIR I2 (Interoperable with formal knowledge representation).
**My reframe:** This is not a FAIR compliance item. This is feature engineering at ingest time.

Today, annotation values are freetext strings. A researcher writes `"thrust": "20.5 kN"`. An ML pipeline cannot use that. With QuantifiedAnnotation:
- Cypher range query: `WHERE a.numericValue > 20.0 AND a.unitIRI = "http://qudt.org/vocab/unit/KiloN"`
- Cross-run numeric comparison: "show me all test runs where peak chamber pressure exceeded 95 bar"
- ML feature column: `thrust_kN` is a proper float, not a string to parse

The UX Auditor's proposal #9 (channel unit picker at creation) is the input surface for this data. The Research Data Manager's FAIR compliance score depends on it. The Manufacturing Quality agent's calibration traceability depends on it. But from the ML lens: this is the moment annotation stops being a label and starts being a feature.

**Migration:** Additive — `SET n.numericValue = null, n.unitIRI = null` on existing `:SemanticAnnotation` nodes. Zero-risk.

**ML value unlocked:** Float-valued annotations with SI units → ML feature columns at zero cost. Enables threshold-based anomaly attribution ("this run failed because chamber pressure was 3σ above mean"). Enables supervised classifiers that train on process parameter distributions, not freetext strings.

**Effort:** S (Neo4j migration + two nullable fields on entity + API lift + QUDT autocomplete already exists in the UI)
**My verdict:** CHAMPION — the highest-leverage structural addition per effort spent

---

### CHAMPION #5 — data-ontologist #3 / mfg-quality #10: CHAMEO + SSN/SOSA ontology seeds

**Proposed by:** Data Ontologist (proposal 3), Manufacturing Quality (proposal 10) — independently
**Their framing:** Two additive entries to `ontologies-manifest.json`; no migration; no code changes; gives annotation picker CHAMEO defect types and SSN/SOSA sensor terms.
**My reframe:** This is the label space for supervised defect classifiers.

A supervised anomaly classifier needs a target vocabulary. Without CHAMEO, a researcher annotating TR-004 writes "vibration spike" in a freetext field — each researcher writes it differently, no two labels are the same, the supervised corpus is unsupervisable. With CHAMEO's `chameo:Deviation` → `chameo:VibrationAnomaly` → `chameo:StructuralResonance` hierarchy, every label maps to a controlled term. The training CSV from my proposal #12 (anomaly labelling UI) emits `chameo:VibrationAnomaly` as the class label, not freetext.

SSN/SOSA gives sensor identity (sensor, observable property, feature of interest) — the metadata that lets a classifier know "this is the accelerometer at the turbopump bearing, not the manifold pressure sensor." That's the difference between a model that learns sensor-specific failure modes and one that muddles all sensors together.

Cross-agent consensus signal: two independent agents arrived at the same proposal from different directions (semantic data model vs. quality engineering). That convergence is evidence of genuine gap, not proposal inflation.

**ML value unlocked:** Controlled defect label vocabulary → supervised classifier target space → reproducible training datasets → classifiers that generalize across DLR institutes (because everyone uses the same label URIs).

**Effort:** XS (two JSON entries in `ontologies-manifest.json`; no code, no migration, no schema change)
**My verdict:** CHAMPION — highest ROI item in the entire proposal stack; ship this week

---

## Top 3 I'm challenging (with the data-readiness gap the proposing agent missed)

### CHALLENGE #1 — strategy #7: Snap Dashboards MVP ("chart from description")

**Proposed by:** Strategy Advisor
**Their pitch:** Text field → "show me thrust vs. time for TR-004" → STRUCTURED AI capability → Vega-Lite spec → inline render. Demo-ready in one sprint. Compelling to a funding body.
**The data-readiness gap they missed:** This is data-debt theatre.

The Strategy Advisor assumes the AI can navigate from a text prompt to the correct channel. But today:
- Channel identity requires 5 fields (5-tuple); TS-IDa/IDb not shipped → AI must enumerate all channels to find "thrust" → O(n) scan on every prompt
- `referenceIds` bug means the AI cannot navigate from DataObject to timeseries container without hitting 404s → it cannot even start the channel enumeration
- `POST /v2/import/jobs` doesn't exist → the AI cannot write the dashboard config back
- `ImportContextIO.generationContext` is incomplete → the AI has no reliable list of what channels actually exist in a given collection

At 15 DataObjects with these gaps, the demo will produce one of two outcomes: (a) a fabricated Vega-Lite spec with channel names the AI invented from context clues, or (b) a failure mode where the AI asks for clarification 3 times and the user gives up. Neither is fundable.

**The correct redirect:** Ship TS-IDa + TS-IDb + referenceIds fix + POST /v2/import/jobs first. Then the AI has stable channel addresses, working navigation, and a write-back path. At that point, Snap Dashboards is genuinely achievable in one sprint and genuinely compelling.

**My verdict:** REDIRECT — prerequisite chain is TS-IDa → TS-IDb → typed reference arrays → POST /v2/import/jobs. Ship those four items first (combined effort: ~3 weeks), then Snap Dashboards becomes a real feature rather than a confidence trick.

---

### CHALLENGE #2 — analytics-ai #8: Semantic embedding for DataObject discovery (own proposal)

**Proposed by:** Analytics & AI Opportunities Specialist (me, prior session)
**My own pitch:** Nightly background job embeds `name + description + attribute values + annotation labels`; pgvector column on Postgres; "find DataObjects similar to this one."
**The data-readiness gap I should have been harder on:** At 15 DataObjects, semantic embedding is a party trick.

Embedding 15 DataObjects and finding "similar" ones in a corpus of 15 is not machine learning — it is a lookup table with extra steps. The nearest neighbor of any DataObject in a 15-item corpus is trivially determined by text overlap alone; cosine similarity on 15 vectors is not a useful signal. Shipping this feature now teaches bad habits: the team builds infra for a feature that can't demonstrate value, the feature sits unused, and the next team to evaluate Shepard sees "semantic search" that returns nonsensical results at demo time.

The honest corpus trigger is ~200 DataObjects. The MFFD AFP robot dataset arriving ~2026-05-26 is the moment this becomes defensible. The JupyterHub J2e notebooks auto-saved with `wasGeneratedBy` links add meta-corpus signal on top.

**The correct defer condition:** Ship the `DataObjectEmbedding` Postgres table schema and the nightly job skeleton now (zero user-visible surface, not a "feature"), but gate the UI surface (the search box, the "similar DataObjects" panel) behind `FeatureToggle("ai.semantic-search.enabled", default false)`. Enable when corpus ≥ 200 DataObjects.

**My verdict:** DEFER (UI surface) / REDIRECT (infrastructure skeleton) — build the plumbing quietly; flip the toggle when the MFFD AFP dataset lands

---

### CHALLENGE #3 — mfg-quality #9: AI Anomaly → NCR Auto-Raise

**Proposed by:** Manufacturing Quality Engineer
**Their pitch:** When MAD detector confidence ≥ 0.8 + `createAnnotations=true`, automatically create a child DataObject with `status: NCR_OPEN` + `shex:QualityFail` annotation. Gated by feature toggle.
**The data-readiness gap they missed:** The MAD detector's false-positive rate on real MFFD data is completely unknown.

MAD (median absolute deviation) is an unsupervised rolling-window detector. It has no concept of domain-specific "normal" — it flags deviations from the local median regardless of whether the deviation is a measurement artifact, a sensor glitch, a legitimate process variation within spec, or an actual defect. On synthetic LUMEN data (TR-004 vibration spike at t=8s, peak 12g rms), it works because the spike is designed to be detectable. On real AFP layup data with thermal gradients, fiber tension transitions, and planned process steps that look like anomalies, the false-positive rate could be 30–60% before any tuning.

Auto-creating NCR DataObjects from an untuned unsupervised detector pollutes quality records. A DIN EN 9100 audit finding against Shepard for generating spurious NCRs would be catastrophic for adoption. The feature toggle gates deployment but does not gate the underlying detection quality problem.

**The prerequisite they missed:** The supervised anomaly labelling UI (analytics-ai #12) must ship first. Researchers need to label real anomalies (TR-004 style) and non-anomalies on the actual MFFD AFP dataset. That corpus trains a threshold-calibrated model. Only when precision ≥ 0.95 on the validation split (human-labelled, `labelSource: HUMAN`) should auto-NCR be considered.

**My verdict:** CHALLENGE — defer mfg-quality #9 until supervised labelling corpus exists; shipping auto-NCR from unsupervised MAD on real manufacturing data before calibration is the fastest way to make Shepard untrusted in quality engineering contexts

---

## The ML critical path (what must ship before any ML feature can work end-to-end)

The following items are in strict dependency order. Nothing in the ML feature stack works correctly without the item before it.

### Step 1: TS-IDa — Mint channel appIds (Neo4j migration)
**What:** `MATCH (t:Timeseries) WHERE t.appId IS NULL SET t.appId = randomUUID()` — idempotent, additive, zero risk.
**Why it blocks everything:** Without stable channel IDs, every ML pipeline is one field rename away from silent corruption. Training datasets reference channels by 5-tuple; a device rename silently changes the mapping; the model continues training on wrong inputs.
**Effort:** S — 1 day migration + test

### Step 2: TS-IDb — Expose channel appIds in API + manifests
**What:** Channel list and get responses include `appId`; live-window endpoint accepts appId parameter; `ImportContextIO` includes channel appIds.
**Why it blocks everything:** ML pipelines must be able to address channels by stable ID, not by 5-tuple. The import manifest generator (Snap Dashboards, agentic ingest) must be able to reference channels by appId in its output.
**Effort:** S — 2–3 days API + manifest lift

### Step 3: Typed container reference arrays (api-scrutinizer #1)
**What:** Replace `referenceIds: long[]` with `timeseriesReferenceAppIds: string[]` + typed arrays per container type in `DataObjectV2IO`.
**Why it blocks everything:** Every agentic workflow (manifest gen, doc-annotation-suggest, anomaly attribution, provenance gap detector) navigates DataObject → containers. Today they hit 404s. This is a live production bug.
**Effort:** S — 2–3 days; pattern established in `DataObjectListItemV2IO`

### Step 4: POST /v2/import/jobs (api-scrutinizer #3)
**What:** Execute endpoint for validated import manifests; returns jobAppId + polling endpoint; carries `agentContext.generatedByAiActivityAppId`.
**Why it blocks everything:** Without the execute step, agentic ingest is a planning demo. LLM-generated manifests, doc-annotation-suggest outputs, and manifest-gen-llm outputs cannot be written back to Shepard. The agentic loop is open.
**Effort:** M — planning half exists; execution + job status polling is the new slice

### Step 5: QuantifiedAnnotation + unit-at-creation UI (ontologist #1 + UX #9)
**What:** `numericValue: Double` + `unitIRI: String` on `SemanticAnnotation`; QUDT autocomplete in annotation creation UI.
**Why it matters here:** Float-valued features with SI units are the ML feature engineering layer. Until annotations carry numeric values, every feature column derived from annotations is a string-to-float parse with all the failure modes that implies.
**Effort:** S — additive migration + two nullable fields + UI already has QUDT autocomplete

### Step 6: shepard-plugin-ai foundation (TEXT / STRUCTURED / EMBEDDING / FAST_TEXT)
**What:** The plugin capability slots for invoking LLM inference. SAIA/GWDG as recommended provider for DLR; local override via `FLO_AI_KEY` for demo.
**Why it comes here:** Steps 1–5 are ML infrastructure that any pipeline can use. Step 6 is the foundation for LLM-backed features (doc-annotation-suggest, manifest-gen-llm, Snap Dashboards, audit narrative gen). It should not ship before the addressing and navigation layers are correct — otherwise LLM features ship that can navigate but not persist.
**Effort:** M — plugin SPI already designed; implementation is provider adapter + capability dispatch

### Step 7: Unified ingest SPI schema metadata (sTC i6 + hotfolder schema-aware lift)
**What:** Per-channel metadata emitted at ingest: unit, value range, hysteresis, sample rate, sensor type. Stored as `SemanticAnnotation` with QUDT units (now that step 5 exists).
**Why it comes here:** This is the ML feature engineering layer at source. Once channels carry schema metadata at ingest, ML pipelines do not need to discover units from documentation or from researcher memory. The ingest SPI is the upstream producer; QuantifiedAnnotation (step 5) is the storage format.
**Effort:** M — ingest SPI design; sTC i6 schema-aware source emission; hotfolder manifest enrichment

---

## Edge vs. cloud inference (which proposals belong on the IPC vs. in Shepard backend)

The home-showcase collector confirms the edge story is aspirational today: `collector.py` is a stateless MQTT→Shepard bridge with no local inference, no anomaly detection, no schema awareness. The IPC Grafana dashboard is likewise a display surface, not a compute surface. This section describes what should run where — the current state vs. the target state.

### Run at the edge (IPC / sTC level)

**AI1b MAD anomaly detector** — should run locally on the IPC, pre-push. The current implementation runs server-side on historical data. The right architecture: MAD runs on the rolling window in the collector (or in a sidecar process on the IPC), flags channels in real time, and pushes `qualityScore` alongside the measurement values. Central Shepard receives the flag as a structured annotation, not as a raw timeseries to post-process.

**AI1c channel quality scoring** — same argument. Channel-level signal quality (dropout rate, clipping, range violations) is best computed at the source. The collector already batches; adding per-batch quality metrics is a few lines.

**Schema tagging at ingest (sTC i6)** — emitting `{unit, range, hysteresis, sampleRate}` per channel at the IPC level, not inferred from documentation later. This is the ML feature engineering layer at source; it belongs as close to the sensor as possible.

**Provenance gap detector (partial)** — the local half: "did I emit all expected channels this batch?" The collector can detect missing channels (expected 12, received 9) and tag the batch with a `shepard:IncompleteBatch` annotation before push. The full graph-level gap detection (are there DataObjects with no timeseries container?) belongs in the cloud.

### Run in the central Shepard backend (cloud)

**doc-annotation-suggest** — PDF/report parsing requires LLM inference with large context windows. The IPC has no LLM capability. This is a cloud-only feature: file uploaded → plugin-ai TEXT call → suggested key-value pairs returned to UI.

**manifest-gen-llm (Snap Dashboards prerequisite)** — generating an import manifest from a directory listing + ImportContextIO requires STRUCTURED capability. Cloud only; IPC has no LLM.

**audit-narrative-gen** — generating human-readable provenance statements from the graph. Pure Cypher traversal + TEXT capability. Cloud only.

**Semantic embedding + similarity search** — EMBEDDING capability + pgvector queries on Postgres. Cloud infrastructure; IPC has no vector database.

**Helmholtz Unhide enrichment** — metadata enrichment for published datasets. Cloud only; IPC has no public network context.

**Provenance gap detector (full graph)** — Cypher traversal over the full DataObject graph looking for orphaned containers, missing NDT gates, broken predecessor chains. Cloud only; requires full Neo4j access.

### Either edge or cloud (decide by latency requirement)

**AI Anomaly → NCR routing (mfg-quality #9, deferred)** — when eventually shipped after supervised calibration: real-time flagging belongs at the edge (IPC detects → local flag); NCR creation belongs in cloud (DataObject with status NCR_OPEN is a graph write requiring full Shepard context). The right split: IPC flags with `qualityScore < 0.2` + channel ID; Shepard cloud receives flag, checks against quality gate config, decides whether to auto-create NCR.

**Anomaly labelling UI (analytics-ai #12)** — label creation happens in cloud (Shepard UI); label export for training happens in cloud; but the resulting model (once trained) should be deployable as an edge classifier to enable real-time flagging.

---

## My overall priority stack (ordered by: ML infrastructure first, then features)

### Tier 0 — Ship now (no prerequisites, zero risk, maximum unblocking value)

| Item | Source | Effort | What it unblocks |
|------|--------|--------|-----------------|
| CHAMEO + SSN/SOSA ontology seeds | ontologist #3, mfg-quality #10 | XS | Supervised classifier label vocabulary |
| TS-IDa (mint channel appIds) | api-scrutinizer #9 | XS/S | All ML pipeline addressing |
| Typed container reference arrays | api-scrutinizer #1 | S | All agentic graph navigation |

These three items share one property: zero breaking changes, zero new dependencies, zero feature risk. CHAMEO+SSN/SOSA is two JSON entries. TS-IDa is one idempotent Cypher migration. Typed arrays is a pattern already established in `DataObjectListItemV2IO`. Together they are ~1 week of work and they unblock every ML feature in the stack.

### Tier 1 — Sprint 1 (small, high-leverage, no blockers)

| Item | Source | Effort | What it unblocks |
|------|--------|--------|-----------------|
| TS-IDb (expose appIds in API) | api-scrutinizer #9 | S | ML pipeline channel addressing |
| QuantifiedAnnotation + unit-at-creation | ontologist #1, UX #9 | S | ML feature columns with SI units |
| RDM #12 (LUMEN seed FAIR upgrade) | rdm #12 | S | Labellable, publishable demo corpus |
| analytics-ai #12 (anomaly labelling UI, skeleton) | analytics-ai | S | Supervised corpus factory |

### Tier 2 — Sprint 2 (medium effort, closes key loops)

| Item | Source | Effort | What it unblocks |
|------|--------|--------|-----------------|
| POST /v2/import/jobs | api-scrutinizer #3, strategy S2 | M | Agentic ingest write-back |
| shepard-plugin-ai foundation (TEXT + STRUCTURED) | analytics-ai | M | doc-annotation-suggest, manifest-gen |
| ontologist #12 (CausalAnnotationEdge) | ontologist | S | Anomaly attribution traceability |
| rdm #5 (metadata completeness score) | rdm | S | FAIR gate before publish |

### Tier 3 — Sprint 3 (features now that infrastructure exists)

| Item | Source | Effort | What it unlocks |
|------|--------|--------|-----------------|
| doc-annotation-suggest (AI1e) | analytics-ai | M | Auto-annotation from file content |
| manifest-gen-llm (AI1f) | analytics-ai | M | LLM-generated import manifests |
| UX #6 (side-by-side timeseries comparison) | ux-auditor | M | Multi-run comparison at MFFD scale |
| Ingest SPI schema metadata (sTC i6) | ecosystem context | M | Feature engineering at source |

### Tier 4 — When corpus ≥ 200 DataObjects (MFFD AFP dataset arrival)

| Item | Source | Effort | Gate condition |
|------|--------|--------|----------------|
| Semantic embedding + similarity search (UI surface) | analytics-ai #8 | M | Corpus ≥ 200 DataObjects |
| Snap Dashboards MVP | strategy #7 | M | TS-IDa + TS-IDb + typed refs + import/jobs |
| shepard-plugin-ai EMBEDDING capability | analytics-ai | M | After TEXT + STRUCTURED proven |
| Provenance gap detector (full graph) | analytics-ai | S | After POST /v2/import/jobs |

### Tier 5 — After supervised labelling corpus exists (defer, not skip)

| Item | Source | Effort | Gate condition |
|------|--------|--------|----------------|
| AI Anomaly → NCR Auto-Raise | mfg-quality #9 | M | Precision ≥ 0.95 on human-labelled validation split |
| mfg-quality #4 (AI quality gate) | mfg-quality | M | Same gate as above |
| JupyterHub J2e (notebooks → Shepard) | ecosystem context | L | After plugin-ai foundation |

### Training data producers — the undervalued tier (run in parallel with everything)

These items are not ML features. They are corpus builders. Every ML feature in tiers 3–5 trains on the output of these items. They should run in parallel with all tiers above, not sequentially:

- **analytics-ai #12** (anomaly labelling UI) — the supervised corpus factory. Every drag-to-select label is a training example.
- **rdm #12** (LUMEN seed FAIR upgrade) — promotes seed data to labelable, QUDT-annotated, publishable corpus.
- **ontologist #5 + rdm #5** together — material batch graph nodes + FAIR metadata completeness create the graph-structure training signal for provenance models.
- **JupyterHub J2e** (notebooks auto-saved with `wasGeneratedBy`) — meta-learning corpus: "how did researchers analyse this type of anomaly?" No other agent framed this as training data.
- **sTC i6 schema-aware ingest** — every future measurement arrives with units, ranges, and sensor type. The corpus improves automatically from the day this ships.

---

## Cross-agent consensus signals (where multiple agents independently converged)

The following proposals were independently generated by 2+ agents from different lenses. Independent convergence is the strongest signal that a gap is real, not a proposal artifact:

| Proposal | Agents | Consensus |
|----------|--------|-----------|
| CHAMEO + SSN/SOSA | ontologist #3, mfg-quality #10 | Strongest signal — ontology lens and quality engineering lens arrived independently |
| TS-IDa/IDb (stable channel IDs) | api-scrutinizer #9, strategy #5 | Strong signal — API ergonomics and strategic narrative both require it |
| POST /v2/import/jobs | api-scrutinizer #3, strategy S2 | Strong signal — API completeness and strategic demo both require it |
| QuantifiedAnnotation | ontologist #1, rdm (FAIR I2) | Strong signal — semantic model and FAIR compliance both require numeric annotations |
| Side-by-side comparison | ux-auditor #6, ecosystem #10 | Moderate — UX and ecosystem narrative both surface this as a flagship demo feature |
| referenceIds fix | api-scrutinizer #1, ecosystem EP-07 | Strong — API scrutiny and MCP tooling both identified the live 404 bug |

---

## Summary verdict table

| Proposal | Source | Verdict | Tier | Key reason |
|----------|--------|---------|------|------------|
| TS-IDa (mint channel appIds) | api-scrutinizer #9 | CHAMPION | 0 | ML addressing prerequisite |
| Typed container reference arrays | api-scrutinizer #1 | CHAMPION | 0 | ML navigation layer; live 404 bug |
| CHAMEO + SSN/SOSA seeds | ontologist #3, mfg #10 | CHAMPION | 0 | Supervised classifier label space |
| QuantifiedAnnotation | ontologist #1 | CHAMPION | 1 | Feature engineering at ingest |
| POST /v2/import/jobs | api-scrutinizer #3 | CHAMPION | 2 | Closes agentic ingest loop |
| TS-IDb (expose appIds) | api-scrutinizer #9 | CHAMPION | 1 | Stable ML addressing in API |
| Anomaly labelling UI | analytics-ai #12 | CHAMPION | 1 | Supervised corpus factory |
| LUMEN seed FAIR upgrade | rdm #12 | CHAMPION | 1 | Labelable + publishable demo |
| plugin-ai foundation | analytics-ai | CHAMPION | 2 | LLM feature prerequisite |
| CausalAnnotationEdge | ontologist #12 | CHAMPION | 2 | Anomaly attribution traceability |
| Metadata completeness score | rdm #5 | CHAMPION | 2 | FAIR gate; also ML corpus quality signal |
| Snap Dashboards MVP | strategy #7 | REDIRECT | 4 | Data-debt theatre without TS-IDa/IDb + typed refs + import/jobs |
| Semantic embedding (UI surface) | analytics-ai #8 | DEFER | 4 | Premature at 15 DataObjects; ship at ≥200 |
| AI Anomaly → NCR Auto-Raise | mfg-quality #9 | CHALLENGE | 5 | MAD false-positive rate unknown on real MFFD data; needs supervised corpus first |
| Side-by-side timeseries comparison | ux-auditor #6 | CHAMPION | 3 | Requires TM1a wallClockOffset + TS-IDb; ML value: multi-run feature comparison |
| JupyterHub J2e | ecosystem context | CHAMPION | 5 | Meta-learning corpus; notebooks = training data |
| sTC i6 schema-aware ingest | ecosystem context | CHAMPION | 3 | Feature engineering at source |
| Ingest SPI unified | ecosystem context | CHAMPION | 3 | Channel schema metadata = ML feature layer |
