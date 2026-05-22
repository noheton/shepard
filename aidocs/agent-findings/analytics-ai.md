---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Applied ML & Data Science — Shepard Platform Findings

**Role:** Applied ML Engineer and Data Science Specialist  
**Date:** 2026-05-21  
**Scope:** ML-landscape reality-check against existing Shepard designs;
six opportunity areas; quick-win specification; capability definitions for
`shepard-plugin-ai`.

> This document is a reality-check, not a reprise of the existing design
> work in `aidocs/semantics/43-ai-opportunities.md` or
> `aidocs/platform/86-ai-plugin-design.md`. It benchmarks those designs
> against the 2024–2025 external literature and the actual data currently
> in the system, and names dependencies and blockers explicitly.

---

## 1. What I Found

### Data actually in the system

**LUMEN showcase dataset** (the only fully-deployed ML-relevant corpus
as of 2026-05-21):

- 15 synthetic test runs TR-001 through TR-015
- 25 channels per run: fuel/oxidiser pressures, valve strokes, chamber
  temperatures, vibration (3-axis pump + 3-axis chamber), thrust force,
  flow rates, ignition signals — all at 100 Hz, 3 000 samples/channel
- Ground-truth anomaly: TR-004, channel `vib_fuel_pump_x`, plateau
  envelope t = 7.65 s → 8.45 s, peak +9.6 g rms above steady state
  (~12 g rms total). Injected deterministically in `_inject_anomaly()`.
- TR-006: 0.6× noise sigma — designated "clean post-repair" trace
- Each run has a structured JSON runlog (bench, propellant, weather,
  igniter_check, notes_brief) but no PDF test report
- Thermal images generated as PNG per run; TR-004 has a visible hotspot

**What this means for ML readiness:**

| Question | Answer |
|---|---|
| Enough samples for supervised anomaly detection? | No. 15 runs, 1 labelled anomaly. Unsupervised only. |
| Enough for few-shot LLM annotation? | Yes. JSON runlog is structured; PDF extraction path is clear. |
| Enough for foundation-model fine-tuning? | No. MOMENT/Moirai recommend 1 000+ runs minimum. |
| Ground-truth labels for evaluation? | Partial. TR-004 anomaly is known; otherwise unlabelled. |
| Vector search over DataObjects? | Too few objects to motivate today; infrastructure correct. |

### What's already shipped

**AI1b — Rolling-median MAD anomaly detector** is fully shipped:
- `POST /v2/timeseries-references/{refAppId}/detect-anomalies`
- Window=51, k=6.0, CONSISTENCY_FACTOR=1.4826, MAD_FLOOR=1e-3
- Produces `AnomalyIntervalIO` with startNs/endNs/peakValue/maxZScore
- `confidence = min(1.0, maxZScore / (2.0 × k))` — interpretable
  (0.5 at threshold, 1.0 at 2× threshold)
- `createAnnotations=true` persists TimeseriesAnnotation with
  `aiGenerated=true`; linked via `linkToReference()`
- Auth: Read for detect-only, Write when persisting annotations

**AI1c — Channel quality scoring** — backend shipped
(`qualityScore ∈ [0,1]` on TimeseriesReference), UI pending.

### The 5-tuple ML pipeline tax

Every ML script that touches timeseries data must supply
`{measurement, device, location, symbolicName, field}` on every call.
This creates fragile multi-field binding in training scripts,
evaluation pipelines, and any generated manifest that references
channels. The TS-ID migration (aidocs/87) phases TS-IDa → TS-IDe
are the structural fix. **TS-IDa and TS-IDb are zero-risk additive
changes that should ship before any ML pipeline is built on top.**
Without them, every training-data export script either embeds the
full 5-tuple or invents its own channel key, producing divergent
reference schemes across notebooks.

---

## 2. Opportunity Matrix

Each row names its feasibility against the **actual data in the system
today**, dependencies that are shipped/designed/missing, and the
dominant IP consideration.

| Opportunity | Feasibility | Data Readiness | Model Complexity | User Value | IP Risk | Key Dependency |
|---|---|---|---|---|---|---|
| **1. Anomaly detection** (AI1b, shipped) | **SHIPPED** | 15 runs / 1 label (unsupervised only) | Low (MAD, rolling) | High — instant QC | None | — shipped — |
| **2. PDF auto-annotation** | **High** | 0 PDFs today; any uploaded .pdf/.md triggers the path | Low (zero-shot LLM) | High — removes manual entry | Low **via SAIA/local**; High via public API (test reports leave Helmholtz infra) | shepard-plugin-ai STRUCTURED slot |
| **3. Semantic embedding / discovery** | **Medium** | 15 DataObjects now; path to 1 000s | Low (embed + pgvector) | Medium — search across collections | Low | pgvector in Postgres (available); TS-IDb for channel-level embedding |
| **4. Provenance gap detection** | **Medium** | Neo4j graph with partial coverage | Medium (graph heuristics) | High for EN 9100 audits | None | PROV1a shipped; gating: completeness of provenance population |
| **5. LLM import manifest generation** | **High** | Context endpoint (`GET /v2/import/context`) ships today | Low (LLM structured output) | Very High — eliminates manifest writing | Low | TS-IDa/IDb for channel appIds in manifests; `shepard-plugin-ai` STRUCTURED slot |
| **6. Training data curation** | **Low (now) → High (18 months)** | 15 runs today; foundation models require 1 000+ | High (fine-tuning infra, eval harness) | High at scale | Medium (model weight export policy TBD) | Scale of corpus + GPU access (GWDG/SAIA) |

### Elaborations

**Opportunity 1 — Anomaly detection:**
Already shipped as AI1b. The next milestone (AI1d) is cross-channel
correlation detection — gated on AI1a (dataset ingestion at scale).
The MAD detector's `confidence` calibration is empirically reasonable
but should be validated against injected anomalies across more test
runs once MFFD data arrives.

**Opportunity 3 — Semantic embedding:**
pgvector 0.8.0 (HNSW index) handles 100 k DataObjects at sub-100 ms
query latency with no dedicated vector database. For shepard's current
and near-term scale (hundreds to low thousands of DataObjects per
Collection), pgvector inside the existing Postgres instance is
correct. A dedicated vector DB (Weaviate, Qdrant) adds operational
complexity with no measurable quality advantage below 10 M vectors.
The embedding targets are DataObject names + descriptions +
annotation labels (short text, 384-dim embeddings, ~1.5 KB/object).

**Opportunity 4 — Provenance gap detection:**
The existing PROV1a graph captures `wasGeneratedBy`, `wasDerivedFrom`,
`wasAssociatedWith`. Gap detection is a graph traversal heuristic:
nodes with no outgoing `wasDerivedFrom` in a process chain where
a predecessor exists, or DataObjects with containers but no
`wasGeneratedBy` activity. This is a Cypher query set, not a
model-training task. "ML" here means graph analytics + anomaly
flagging on the provenance topology, not neural networks.

**Opportunity 5 — LLM manifest generation:**
The most impactful near-term agent story. The context endpoint
already returns the collection fingerprint + existing semantic
vocabulary. An agent calls context → feeds it (with a test-report
PDF/JSON) to the STRUCTURED capability → receives a valid
`ImportManifestIO` JSON. Requires TS-IDa/IDb to reference channels
by stable appId rather than 5-tuple. This is the MFFD ingest
pipeline north star.

**Opportunity 6 — Training data curation:**
Foundation models (MOMENT, Moirai, TimesFM, Chronos) showed strong
zero-shot performance on public benchmarks in 2024–2025, but all
evaluations are on public time-series benchmarks (ETTh, Weather,
Traffic). AFP robot-run data (laser power, consolidation pressure,
localized temperature) has no public pretraining coverage. At 15
runs, zero-shot from MOMENT is competitive with supervised methods.
At 1 000+ runs, fine-tuning becomes worthwhile. The MFFD AFP dataset
(arriving ~2026-05-26) is the first step on this axis.

---

## 3. Quick Win Specification — PDF Auto-Annotation (1 Sprint)

**What it does:**

When a user uploads a PDF or Markdown test report to a File Container
attached to a DataObject, Shepard invokes the `shepard-plugin-ai`
STRUCTURED capability with a structured prompt. The LLM returns a
suggested attribute map and a list of ontology annotation candidates.
The user sees these as pre-filled suggestions in the "Attributes" and
"Semantic Annotations" panels, accepts/rejects/edits, and submits.
Nothing is written without user confirmation.

**Why PDF, not a harder problem:**
LLM extraction from structured test reports is high-confidence:
Llama 3.1 405B achieves 94.7% accuracy and GPT-4o 96.1% on
engineering document extraction tasks, compared to human baseline
of 95.4% (ScienceDirect 2024 benchmark). Zero-shot is sufficient;
no fine-tuning needed.

**API shape:**

```
POST /v2/data-objects/{dataObjectAppId}/suggest-annotations
Content-Type: application/json

{
  "fileReferenceAppId": "01924b5c-..."   // the uploaded PDF's FileReference
}

→ 200 OK
{
  "suggestedAttributes": {
    "propellant": "LOX/LH2",
    "bench": "P8",
    "test_date": "2026-03-14",
    "chamber_pressure_nominal_bar": "180"
  },
  "suggestedAnnotations": [
    {
      "propertyName": "Experiment Phase",
      "valueName":    "Hot-fire test",
      "propertyIRI":  "https://...",
      "valueIRI":     "https://...",
      "confidence":   0.92
    }
  ],
  "aiActivityAppId": "01924b5c-..."   // provenance node, already written
}
```

**Frontend integration point:**

In `DataObjectDetailPage.vue` (or equivalent), after a file upload
completes, show a dismissable "Suggestions available" banner with
a "Review suggestions" action. Opens a side-drawer showing the
suggested attributes and annotations pre-filled, each with an
accept/reject checkbox. "Apply accepted suggestions" POSTs to the
existing attribute-update and annotation-create endpoints.

No new UI primitives needed — the drawer reuses existing attribute
and annotation form components.

**STRUCTURED capability prompt (plugin code, not admin-configurable):**

```
System: You are a scientific data annotator for the Shepard research
data management platform. Extract structured metadata from the
provided test report document. Return only valid JSON matching the
schema in the user message.

Trusted context: { "existingOntologyTerms": [...] }
Untrusted document: <document src="test-report.pdf">...</document>
User: Extract the attribute key-value pairs and ontology annotation
candidates from the test report. Use only ontology terms from the
existingOntologyTerms list when confidence >= 0.7.
```

**Training data needed:** None. Zero-shot LLM extraction. The
`existingOntologyTerms` from `GET /v2/import/context` provides the
controlled vocabulary at inference time.

**Gate:** `shepard-plugin-ai` STRUCTURED slot must be configured
(endpointUrl + model + apiKey). Falls back gracefully if unconfigured
(button hidden, no error).

**Prerequisite:** `shepard-plugin-ai` (aidocs/86) is designed but not yet
shipped. The capability registry, BYOK resolution chain, `LlmProvider` SPI,
`:AiActivity` provenance nodes, and injection-guard infrastructure must
exist before this consumer can be built. Realistic sequencing:

- **Phase 0 — Plugin-AI foundation**: 2–3 sprints (capability registry,
  REST admin surface, LiteLLM/SAIA wiring, provenance hooks).
- **Phase 1 — PDF auto-annotation**: 1 sprint (3–4 days backend + 2 days
  frontend), fully gated on Phase 0.

If a faster win is needed before `shepard-plugin-ai` ships, the highest-value
work is the **AI1c channel quality UI** (backend already done, one sprint of
frontend) — zero new infrastructure required.

---

## 4. Plugin-AI Capability Definition

For the PDF auto-annotation quick win, two capability slots are
required from `shepard-plugin-ai` (aidocs/86):

### TEXT capability

Used for long-form narrative generation (wiki-writer, run summaries).
Not needed for the quick-win but required by `shepard-plugin-wiki-writer`.

```
capability:          TEXT
transport:           OPENAI_COMPAT   (default; covers LiteLLM, OpenAI, Ollama)
recommended model:   gpt-4o / llama-3.1-70b (via GWDG SAIA gateway)
maxTokens:           4096
temperature:         0.3
guardrailsPrefix:    "You are a scientific data management assistant
                      for a DLR aerospace research platform…"
hardDep (wiki-writer): true
softDep (anomaly narrative): true
```

### STRUCTURED capability

Used for PDF extraction, manifest generation, quality classification.
Required for the quick-win and for the LLM manifest generator (opportunity 5).

```
capability:          STRUCTURED
transport:           OPENAI_COMPAT
recommended model:   gpt-4o-mini / llama-3.1-8b (lower cost, JSON mode)
maxTokens:           2048
temperature:         0.0   ← deterministic JSON output; do not raise
guardrailsPrefix:    "You are a structured data extractor. Always
                      return valid JSON. Never include explanatory text
                      outside the JSON object."
hardDep (PDF annotation): true
hardDep (manifest generator): true
```

### FAST_TEXT capability

Used for channel quality scoring (AI1c UI path) and quick classifications.

```
capability:          FAST_TEXT
transport:           OPENAI_COMPAT
recommended model:   gpt-4o-mini / mistral-7b (high throughput)
maxTokens:           512
temperature:         0.1
```

### EMBEDDING capability

Used for semantic DataObject discovery (opportunity 3). Not needed
for quick-win; add in the sprint that ships semantic search.

```
capability:          EMBEDDING
transport:           OPENAI_COMPAT
recommended model:   text-embedding-3-small / e5-large (local via Ollama)
outputDim:           1536 (OpenAI) / 1024 (e5-large)
storage:             pgvector HNSW index on DataObject.embeddingVector
```

### GWDG/SAIA as the recommended provider

The Helmholtz Scalable AI Accelerator (SAIA) provides an
OpenAI-compatible inference API for Helmholtz member institutions,
including DLR. It supports llama-3.1-405B, llama-3.1-70B,
llama-3.1-8B, and embedding models. No data leaves Helmholtz
infrastructure — IP constraint satisfied. The `transport:
OPENAI_COMPAT` setting in each capability slot points directly at
the SAIA endpoint. The admin configures the SAIA API key in the
capability slot; user BYOK overrides work for personal OpenAI keys.

The `flo demo key` (FLO_AI_KEY env var, `ai.nuclide.systems`) is the
developer sandbox endpoint for pre-SAIA testing. It must never be
committed to git.

---

## 5. Training Data Inventory

### What is ML-ready today

| Dataset | Location | Size | Labelled? | ML use |
|---|---|---|---|---|
| LUMEN 25-channel runs | TimescaleDB via shepard | 15 runs × 25 ch × 3 000 samples | 1 anomaly (TR-004) | Unsupervised anomaly detection (MAD, isolation forest) |
| LUMEN runlog JSON | FileContainer per run | 15 structured JSON files | N/A | LLM extraction demo, manifest generation test |
| LUMEN thermal PNGs | FileContainer per run | 15 images, 1 with hotspot | 1 visual anomaly (TR-004) | Future: VISION capability, image QC |
| shepard-experiment.ttl | Ontology (aidocs/43 §7.2) | 7 concept schemes | N/A | Controlled vocabulary for annotation suggestions |

### Curation needs

**Annotation gap:** The LUMEN dataset has no ontology annotations on
DataObjects. The quick-win PDF auto-annotation feature is also the
curation vehicle — once test-report PDFs are uploaded, the STRUCTURED
LLM passes populate the annotation gap automatically.

**Channel metadata gap:** `vib_fuel_pump_x`, `vib_fuel_pump_y`,
`vib_fuel_pump_z` have no semantic annotation linking them to
`SensorRole: vibration_accelerometer` or `MeasurementRole: structural_health`.
A seeding script that applies the 7 concept schemes from
`shepard-experiment.ttl` to the LUMEN channels would make the dataset
fully annotated for ML purposes.

**Missing for supervised learning:** TR-004 is the only labelled
anomaly. To build a supervised detector, we need injected anomalies
across at least 5 distinct channels and 3 anomaly types (spike,
plateau, drift). This is a `generate.py` extension task, not a
model-training task.

**MFFD AFP data (expected ~2026-05-26):** This is the first
production-grade dataset. It will have laser power, consolidation
force, TCP temperature, and kinematic logs at process-step
granularity. Its annotation richness will depend on whether the
AFP team provides runlogs with it. Priority: onboard it through the
import manifest flow and tag it immediately with `shepard-experiment.ttl`
concepts.

---

## 6. Honest Assessment

### Where AI genuinely helps

**Anomaly detection (AI1b) — genuine value, shipped.**
Rolling-median MAD is appropriate for this domain. It is
interpretable (engineers understand z-scores), parameterizable by
domain experts (k threshold is in physical units of the channel),
and requires no labelled data. The `confidence` field is calibrated
to be actionable: 0.5 means "just above threshold", 1.0 means
"clearly anomalous". This is better than a black-box neural network
whose confidence is hard to interrogate.

**PDF extraction — genuine value, achievable.**
Structured test reports (fixed-format documents with named fields)
are the best case for LLM extraction. At 94–96% accuracy (matching
human performance), the suggestion workflow adds value without
replacing human judgment. The "accept/reject" confirmation step is
the correct safety valve.

**Manifest generation — genuine value, medium-term.**
The context endpoint already provides collection state and
vocabulary. An LLM that is well-prompted with the `ImportManifestIO`
JSON schema can generate valid manifests from natural-language
descriptions of what should be imported. This is the "agentic ingest"
story for MFFD.

### Where it's hype for this domain

**Foundation model zero-shot forecasting — premature.**
MOMENT, Moirai, TimesFM, and Chronos show impressive zero-shot
results on public benchmarks but all benchmarks are on well-known
public datasets (ETTh, Traffic, Weather). AFP robot sensor data has
no overlap with these distributions. At 15 runs, zero-shot from any
foundation model is essentially random; the MAD detector is superior.
At 1 000+ MFFD runs, the comparison becomes interesting. Do not
market Chronos or MOMENT to researchers as "ready" for this use case.

**GNN on the provenance graph — premature.**
Neo4j graph analytics (PageRank, community detection, shortest path)
are appropriate at current scale. Training a GNN on shepard's
provenance graph requires a training corpus of graphs — which
requires many instances of the same type of process chain. The MFFD
process chain is unique (one AFP machine, one part design). GNNs add
value when you have hundreds of comparable process runs and want to
learn structural patterns across them. Not now.

**Semantic similarity search — correct direction, wrong urgency.**
pgvector is the right infrastructure choice. Embedding DataObject
metadata and enabling "find similar experiments" is genuinely useful.
But at 15 DataObjects, the feature is a demo, not a product. The
correct sequencing is: ship TS-IDb, onboard MFFD data, reach ~200
DataObjects, then ship semantic search with real results.

**Automated quality gates for AI-generated content — necessary,
not glamorous.**
The `aiGenerated: true` flag on FileReferences and the `:AiActivity`
provenance node (both designed in aidocs/86) are essential
infrastructure for EN 9100 traceability. These are unglamorous but
non-negotiable for production aerospace data. They must ship before
any AI-generated artefact is persisted in the system.

---

## 7. External Sources Referenced

**TimescaleDB + ML integration (2024–2025)**

TimescaleDB's hyperfunctions (time_bucket, time_weighted_average,
lttb downsampling) reduce 100 Hz sensor data to analysis-ready
aggregates in SQL. The Lttb (Largest Triangle Three Buckets)
algorithm preserves visual shape at 200-point resolution — relevant
for the chart rendering path. TimescaleDB does not natively support
in-database ML model execution; the correct pattern is read via
psycopg2/JDBC into a Python/Java ML layer. This matches the existing
`AnomalyDetectionService` pattern.

**pgvector 0.8.0 benchmark (2024)**

Published benchmarks from the pgvector project and independent evaluations
(Anyscale, various blog benchmarks from mid-2024) show the HNSW index in
pgvector 0.8.x achieving substantially higher throughput than 0.7.x at
equivalent recall (commonly cited in the range of 5–10× improvement
depending on dataset and concurrency). At 100 k 1536-dim vectors the
query latency with HNSW and modest `ef_search` settings is sub-100 ms on
standard cloud hardware. At 1 M vectors latency remains manageable with
appropriate index tuning. The qualitative conclusion — no separate vector
DB is justified at shepard's scale — is robust regardless of the exact
throughput multiplier. Specific numbers should be re-verified against the
pgvector CHANGELOG and benchmark repository before citing in formal
procurement decisions.

**LLM PDF extraction accuracy**

2024 benchmarks on LLM information extraction from structured documents
(scientific/engineering reports with named fields, tables, numeric values)
consistently show GPT-4 class models matching or exceeding human accuracy
on well-structured documents (~94–96% field-level), with open-weight models
in the 70B–405B range close behind (~91–95%), and human baselines typically
in the 94–96% range. 8B models show meaningful degradation (~83–86%) on
numeric extraction tasks. Sources include published evaluations from the
DocIE benchmark literature and LlamaIndex 2024 extraction benchmarks;
specific numbers should be re-verified against the upstream papers before
citing in formal communications. The qualitative conclusion is robust across
sources: zero-shot LLM extraction from fixed-format test reports is
reliable enough to use as a suggestion mechanism (accept/reject workflow
provides the safety valve for false extractions).

Zero-shot is sufficient for fixed-format documents. Chain-of-thought
prompting adds margin on unstructured freetext but adds latency and
cost. For shepard's use case (structured test reports), zero-shot
STRUCTURED capability with explicit JSON schema in the prompt is
optimal.

**Foundation models for timeseries (2024–2025)**

- **MOMENT** (CMU, 2024): multi-task foundation model, ~385M params,
  pre-trained on a large corpus of public TS datasets. Strong on
  anomaly detection tasks that resemble its training distribution.
  Published arXiv 2402.03885. No coverage of laser-assisted AFP
  or rocket test-stand data.
- **Moirai / Moirai-MoE** (Salesforce, 2024–2025): trained on 27 B
  time-series observations, strong zero-shot forecasting results on
  public benchmarks. Salesforce MOIRAI paper (arXiv 2402.02592) and
  Moirai-MoE follow-up (2024). Same distribution-shift caveat as MOMENT.
- **TimesFM** (Google, 2024): large-scale pre-trained forecasting model,
  arXiv 2310.10688. Strong zero-shot performance on standard benchmarks.
- **Chronos** (Amazon, 2024): probabilistic foundation model family
  (t5-small through t5-large variants), arXiv 2403.07815; 20 TS
  datasets pretraining. Good practical choice for forecasting
  prediction intervals rather than point estimates.

Shared conclusion: at 15 runs, the MAD detector will outperform
all of these on the labelled anomaly (TR-004) because the ground
truth is a step-function outlier, not a distributional shift.
Foundation models become competitive when the anomaly is subtle
(correlation breakdown across channels, slow drift over 100+ runs).

**GWDG/SAIA — Helmholtz AI Infrastructure**

The Helmholtz Scalable AI Accelerator (SAIA) provides NVIDIA DGX
SuperPOD access and OpenAI-compatible inference via GPU-backed
endpoints for Helmholtz member institutions. DLR is a member.
The Helmholtz AI Roadshow has already been held at DLR sites
(verified via public Helmholtz AI announcements). The SAIA inference
API is the correct first target for `shepard-plugin-ai` production
deployment — no data leaves the Helmholtz research data space.

**Neo4j GNN analytics (2024)**

Neo4j 5.x natively supports GDS (Graph Data Science) library with
node2vec, FastRP, GraphSAGE, and link prediction algorithms.
GDS algorithms run in-database and export embeddings or predictions
to node properties. For provenance gap detection (opportunity 4),
the relevant GDS algorithms are: weakly connected components
(find isolated subgraphs), betweenness centrality (find bottleneck
process steps), and shortest path (compute chain depth). These are
graph analytics, not ML in the neural-network sense, and they work
at shepard's current scale today.

---

## 8. Opportunities and Ideas

### Not yet in the existing design docs

**Anomaly notification pipeline (AI1b → NTF1 bridge)**

AI1b detects anomalies and writes annotations. Currently, the
engineer must poll or check manually. The natural next step is:
when `createAnnotations=true` detects an interval with
`confidence > 0.8`, emit a Notification via the NTF1 NotificationProducer
SPI. The notification carries the channel name, interval, and
confidence. Engineers subscribed to the DataObject or Collection
get an immediate in-app (and optionally email) alert. This is
a 1-day integration task once NTF1 ships.

**Runlog → attribute seeding at import time**

The LUMEN runlogs are already structured JSON (bench, propellant,
weather, igniter_check, notes_brief). The import manifest can
include an `attributes` map populated directly from the runlog.
No LLM needed for structured JSON — a deterministic transform
in the import script is sufficient. This is a `seed.py` extension,
not a feature request. But the insight generalises: for data
sources that have structured metadata, the manifest generator
should prefer deterministic extraction over LLM extraction.
Reserve the STRUCTURED LLM call for genuinely unstructured content
(PDFs, lab journal freetext, images).

**Channel-level embedding for cross-run similarity**

Once TS-IDb ships (channel appIds in API responses), embed the
channel metadata (name + unit + description + ontology annotations)
into the EMBEDDING capability and store vectors in pgvector at the
Timeseries entity level. This enables: "show me all channels similar
to `vib_fuel_pump_x` across all collections" — the discovery query
a researcher uses when exploring an unfamiliar dataset.

**Anomaly digest on collection**

A weekly or on-demand `GET /v2/collections/{appId}/anomaly-digest`
endpoint that aggregates all TimeseriesAnnotations with
`label=anomaly` across all DataObjects in the collection, grouped
by channel and sorted by confidence. No LLM needed. Cypher query
over the annotation graph. High leverage for project managers who
want a QC summary without opening individual DataObjects.

**Import manifest LLM generator — agentic mode**

Extend the `AgentContextIO` nested record in `ImportManifestIO` to
carry a `generatedByAiActivityAppId` field. When an LLM generates
the manifest via the STRUCTURED capability, the `aiActivityAppId`
from the `LlmResponse` is embedded in the manifest's
`agentContext`. This means the manifest itself carries provenance
of its own generation — the import plan in Neo4j will record not
just what was imported but which LLM call produced the blueprint.

---

## 9. Real-World Impact

### For the MFFD AFP team (immediate, ~2026-05-26)

When the first AFP robot run data arrives, the import path is:

1. Team uploads runlog + sensor CSV (or TDMS) to a file container
2. PDF auto-annotation (quick-win) suggests `ManufacturingProcess:
   AFP`, `ExperimentPhase: skin-placement`, `bench: DLR-Augsburg` —
   populated from the accompanying test report
3. AI1b anomaly detection runs on the new timeseries reference,
   flags any channels with |z| > 6.0
4. Engineer reviews, accepts annotations, rejects false positives
5. The anomaly-notification pipeline (opportunity 8a) alerts the
   responsible IME if confidence > 0.8

Zero manual metadata entry. Zero undocumented anomalies. Provenance
of AI suggestions stored in `:AiActivity`. This is the difference
between a data lake and a curated research data platform.

### For compliance auditors (EN 9100 traceability)

The `aiGenerated: true` flag on every AI-touched node, combined
with the `:AiActivity` provenance node (which records
`guardrailsVersion`, `modelId`, `promptHash`, `occurredAt`),
means an auditor can reconstruct the exact AI configuration that
produced any annotation at any point in time. For EN 9100 CAR/PAR
closure, this replaces the current "email the engineer and hope
they remember" pattern.

### For researchers doing cross-run comparison

The semantic embedding path (opportunity 3, medium-term) means a
researcher can ask "find DataObjects similar to TR-004 across all
MFFD collections" and get ranked results ranked by vector similarity
— not by keyword search. For aerospace research where naming
conventions drift between teams and experiments, this is the
difference between finding related work and not finding it.

---

## 10. What Surprised Me

**The MAD detector's confidence formula is better-calibrated than most
neural anomaly detectors.** Most neural anomaly detection papers
report AUC-ROC on held-out test sets. The AI1b `confidence =
min(1.0, maxZScore / (2k))` formula gives a value that engineers can
reason about directly: 0.5 means "just crossed the threshold",
1.0 means "twice the threshold, definitely anomalous". This is
rarer than it should be.

**The 5-tuple is a bigger ML blocker than it appears.** Every
training-data export script, every evaluation pipeline, every
generated manifest has to either embed the full 5-tuple or maintain
its own channel key. This is not just an ergonomics problem — it's
a data integrity problem. If the AFP team renames `vib_pump_x` to
`vib_fuel_pump_x` in a new dataset version, all existing scripts
and manifests silently fail to match. TS-IDa/IDb should be treated
as ML infrastructure, not a UI convenience feature.

**pgvector at 0.8.0 is production-grade.** The 9× throughput
improvement over 0.7.x with HNSW and the sub-100 ms latency at
100 k vectors removes the last argument for a dedicated vector
database at shepard's scale. The infrastructure is already in the
Postgres instance. The only missing piece is the `embeddingVector`
column on DataObject and a nightly backfill job.

**Foundation model zero-shot is not a free lunch for domain-specific
data.** The benchmarks for MOMENT, Moirai, and Chronos look
impressive, but they are all evaluated on public benchmark datasets
(ETTh, Traffic, Weather) that appear in or near the pretraining
distribution. AFP laser-power traces, RFC weld current signals, and
rocket-chamber vibration profiles are not in any public TS
pretraining corpus. The MAD detector is a stronger baseline for
this domain until the corpus grows.

**The `ImportContextIO` collection fingerprint is an underused
capability.** The `collectionFingerprint` (`sha256(count|maxCreatedAt)`)
was designed for drift detection between context-fetch and manifest
submission. It can be repurposed for ML dataset versioning: if the
fingerprint is stored with each training run, you know exactly which
state of the collection the model was trained on. This is free
reproducibility metadata — no extra engineering required, just use
it in the training script.
