# AI Opportunities — Traditional ML + LLM Integration

**Scope.** Survey of where AI fits inside shepard, split between
**traditional ML** (deterministic, supervised, well-understood) and
**LLM integration** (generative, probabilistic, newer trade-offs).
Lays out architectural constraints, per-opportunity scoping, and
phasing notes. **No commitment to ship any of these — this is a
catalogue + recommendation framework.**

**Status.** Concept survey.
**Snapshot date.** 2026-05-08.
**Originating items.** User request "generate a new document for ai
opportunities regarding traditional ml approaches as well as
integration of LLMs."

---

## 1. Why this is its own design doc

Three reasons it gets a dedicated page rather than a section
elsewhere:

1. **Cross-cutting.** AI touches search (`aidocs/13`), semantic
   annotations (`aidocs/14`), lab journal (`aidocs/37`), RO-Crate
   exports (`aidocs/31`), provenance (`aidocs/30`). Putting AI
   thoughts inside any one of those buries them.
2. **Non-zero infrastructure cost.** Every AI feature implies a
   model artifact, a GPU/CPU budget, and an audit story.
   Aggregating these decisions in one place lets the operator say
   yes-or-no once.
3. **Compliance gravity.** DLR-internal research data has explicit
   "no third-party processing" constraints in many institutes. The
   architectural shape (§4) determines whether a feature is
   shippable at all.

## 2. Architectural constraints (read first)

These apply to every entry in §3 and §5. Any AI feature that can't
satisfy the relevant constraint is not shippable in shepard's
default deployment — it can be opt-in only, with operator-side
config.

| Constraint | Why | Shape |
|---|---|---|
| **Data residency** | DLR research data must not leave operator-controlled infrastructure by default | Every AI feature ships in two modes: (1) **local** — bundled model running in a sidecar on the operator's infra; (2) **hosted** — opt-in, operator-configured external endpoint (OpenAI, Anthropic, Mistral, …) with a per-Collection toggle and an audit log. **Local is always the default**; hosted requires explicit per-deployment + per-Collection opt-in. |
| **Cost / GPU** | Local inference for 70B-class LLMs needs GPU; most shepard hosts don't have one | Local-mode features default to **small models** that run on CPU (≤ 8B params for LLMs; ~100MB for traditional ML). Larger models behind operator opt-in. |
| **Auditability** | Researchers need to know which output was AI-assisted vs human-authored | Every AI-emitted artifact (lab journal entry, semantic annotation, search-rank score) carries an `aiGenerated: bool` + `model: string` + `confidence: float` triple in its metadata. Visible in the UI; queryable via search. |
| **No silent override of human authorship** | Researchers' hand-written work must never be overwritten by AI output | AI features are **suggestion-only** — the user sees the suggestion and accepts / edits / rejects. No "auto-apply" mode in v1 for any text-generating feature. |
| **Reproducibility** | RO-Crate exports must remain reproducible (`aidocs/41` snapshots) | AI-generated metadata is captured at write time and frozen with the snapshot. Re-running inference later may produce different output; the snapshot keeps the original. |

## 3. Traditional ML opportunities

Ranked by leverage × shippability with current shepard primitives.

### 3.1 Anomaly detection on timeseries

**Existing pattern.** `examples/seed-showcase/notebooks/anomaly-analysis.ipynb`
implements a rolling-median ± k·MAD detector by hand. Generalising
this into a service that any user can invoke against any
`TimeseriesReference` is a straightforward extraction.

**Shape.** New `POST /v2/timeseries/{appId}/detect-anomalies` endpoint;
body specifies algorithm + parameters (rolling-median, IQR, isolation
forest, autoencoder); response is a list of `(timestamp, score)`
pairs. Run on the backend in a small Python sidecar (per §4 — the
AI sidecar pattern).

**Output.** Optionally creates a `dlr:anomaly` semantic annotation
on the source TimeseriesReference, with `aiGenerated: true` and the
model name.

**Why first.** Highest user value (we already wrote one!), smallest
infra footprint (CPU-only, scikit-learn / numpy), clear acceptance
test against the showcase fixture. **Recommended as ML series
phase 1.**

### 3.2 Channel-quality scoring

For each TimeseriesReference, compute a quality score: dropouts per
minute, sample-rate consistency, value-range outliers, sensor
saturation. Surface as a `qualityScore: float` attribute and a
companion `qualityReport: StructuredData` with the per-metric
breakdown.

**Shape.** Background job triggered on TimeseriesReference create or
on operator-scheduled basis. No new endpoint surface; just an
additional automatically-emitted attribute.

**Why useful.** Search-by-quality (`qualityScore < 0.5` finds
suspect runs) without manual triage.

### 3.3 Forecasting / extrapolation

For continuous metrics (say, accumulated test hours, sensor wear),
project forward: linear / exponential / Prophet-style seasonal
decomposition. Surface as a sibling TimeseriesReference with the
prefix `forecast-` and `aiGenerated: true`.

**Shape.** `POST /v2/timeseries/{appId}/forecast` with horizon +
algorithm. Returns a new TimeseriesReference rather than mutating
the source.

**Caveat.** Forecasting on noisy real-world telemetry is famously
unreliable. Ship behind a feature flag with prominent UI warning;
not on the default-on path.

### 3.4 Outlier detection in structured data / attributes

Cluster DataObjects by their attribute vector; flag outliers per
Collection. "TR-004 has unusual `vibration_max` for runs in this
campaign" lights up as an attention-marker in the UI.

**Shape.** Background job, on-demand or scheduled. Output is an
`outlierScore` attribute on each DataObject.

**Why useful.** Catches the next TR-004 vibration anomaly without
the operator manually reviewing every run.

### 3.5 Clustering / similarity search

For DataObjects in the same Collection (or across), compute
embedding vectors from their attributes + lab journal text; serve
"most-similar runs" as a ranked list.

**Shape.** Embedding job per DataObject (sentence-transformers
small models, CPU-runnable). New `GET /v2/data-objects/{appId}/similar`
endpoint; returns ranked appIds.

**Why useful.** Researcher debugging an anomaly can find the closest
historical match without remembering it themselves. Connects to the
unified search work in `aidocs/13`.

### 3.6 Search-rank improvement (learn-to-rank)

Today's search is keyword-based. A learn-to-rank classifier trained
on click-through data (per-user, per-result) reranks results.

**Shape.** Click-through capture in the existing search frontend;
periodic batch retrain; rerank applied at query time when
`?rerank=true` is set.

**Caveat.** Useful only at scale (hundreds of users × thousands of
queries / month). Defer until measurable.

## 4. The shepard-AI sidecar pattern

Mirrors the HSDS sidecar from `aidocs/35`. A new `shepard-ai`
container runs Python (FastAPI + scikit-learn / sentence-transformers
/ a small LLM); shepard's backend brokers requests to it.

```
infrastructure/docker-compose.yml profile: ai
└─ shepard-ai service
   ├─ /infer/anomaly
   ├─ /infer/quality
   ├─ /infer/embedding
   └─ /infer/llm/...    # see §5
```

**Storage.** Models live in a Docker volume `/opt/shepard/ai-models/`.
Ships with a small default set; operator can mount additional
models.

**Auth.** Internal-only; not exposed to the internet. Shepard
backend authenticates with a per-deployment shared secret. Same
audit hooks as the main backend.

**GPU.** `runtime: nvidia` opt-in via `SHEPARD_AI_USE_GPU=true`.
Falls back to CPU if not set. Per-feature requirements documented;
anomaly detection / quality / forecasting / clustering all run on
CPU in seconds for typical timeseries sizes.

## 5. LLM integration opportunities

**Hard ground rule** (per §2): **default to local, small
models** (Llama / Mistral / Qwen 7-8B class). Hosted-model usage
(GPT-4o, Claude, Gemini) requires per-Collection opt-in plus
operator config; never default. The hosted path exists only for
operators who explicitly accept the data-residency trade-off.

### 5.1 Natural-language search

"Show me TR-004's anomaly trace" → the LLM translates to a search
query and renders the result. Operates on the OpenAPI catalogue
+ the user's permission scope.

**Shape.** `POST /v2/search/natural` with a free-text query. Returns
the structured search query the LLM generated *and* the results.
The structured query is editable — user can refine without restating
their intent.

**Risk.** LLM hallucinates a query that returns wrong results.
Mitigation: always show the generated structured query; user sees
exactly what was asked. **Not autonomous** — user-in-the-loop.

### 5.2 Lab journal authoring assist

In the lab-journal editor (`aidocs/37`), the user types a few
words; LLM offers an expansion ("complete this sentence" / "summarise
the timeseries trace I just attached"). Accept-edit-reject UX.
Generated paragraphs carry `aiGenerated: true`.

**Shape.** New endpoint `POST /v2/lab-journal/assist` with `prompt`
+ `context` (the surrounding entry + attached refs). Returns a
suggestion; the editor renders it inline as a "ghost" text the
user can accept with Tab.

### 5.3 Auto-summarisation of run outcomes

Given a DataObject + its references + its lab journal entries,
produce a one-paragraph plain-language summary. Useful for the
"what happened in TR-004?" overview.

**Shape.** Background job, output stored as a `summary` attribute
with `aiGenerated: true`. Rebuilt when the DataObject's
last-modified-at changes (debounced).

### 5.4 Semantic annotation suggestion

User attaches a TimeseriesReference; the LLM suggests phase-of-burn
annotations (`precool`, `ignition`, ...) by analysing the trace +
the channel name. User accepts / rejects.

**Shape.** `POST /v2/semantic-annotations/suggest` with a target
entity. Returns suggestions ranked by confidence. Accepted
suggestions become real annotations with `aiGenerated: true`.

**Risk.** Hallucinated annotations pollute the ontology. Mitigation:
suggestion-only; user must accept; suggestions outside the target
ontology are filtered out.

### 5.5 RO-Crate description generation

When exporting an RO-Crate (`aidocs/31`), the LLM drafts the
`description` field for each entity in the manifest based on its
attributes + lab journal. Operator reviews before export.

**Shape.** New `?aiAssist=true` query param on
`POST /v2/collections/{appId}/export`. The export pipeline pauses
at draft-description for operator review; on commit, descriptions
ship with `aiGenerated: true` attribution in the RO-Crate metadata.

### 5.6 Conversational interface to provenance

"Where did this dataset come from? Show me everything that fed
into TR-006." The LLM walks the lineage graph (`aidocs/30`) and
produces a narrative + a graph view.

**Shape.** Browser-side chat interface; backend exposes the
underlying lineage queries. The LLM is purely a retrieval-and-render
layer — it doesn't make claims; it summarises graph traversal
output.

### 5.7 Code-generation for analysis (notebook scaffolding)

Given a DataObject the user is looking at, the LLM generates a
Jupyter notebook scaffolded with `shepard_client` calls to load
the references and render typical plots. Saves to the user's
preferred Jupyter (`aidocs/36 §3.2 editor.preferredJupyter`).

**Shape.** "Open in Jupyter with starter notebook" button on the
DataObject view. The starter notebook is generated, attached to the
DataObject as a `FileReference`, and the deep-link from
`aidocs/37 J1c` opens it.

### 5.8 Anti-features (deliberately out of scope)

- **Auto-classifier on PI-private data.** No model training on
  cross-tenant data; per-deployment fine-tuning on operator's own
  data is opt-in only.
- **AI-generated permissions changes.** Permission grants stay
  human-authored.
- **AI-driven export decisions.** RO-Crate `redactFields` choices
  stay operator-authored.
- **Autonomous ingestion.** No "the AI decided to add this dataset
  for you" flows. Human in the loop, always.

## 6. Phasing — AI series (call it AI1)

| ID | Slice | Shape | Size | Gate |
|---|---|---|---|---|
| **AI1a** | shepard-AI sidecar pattern: profile-bound `ai` service in compose, FastAPI shell, model-volume mount, broker auth from backend. CPU only. **No models yet** — just the plumbing. | Infra | M | None |
| **AI1b** | Anomaly detection (§3.1) — `POST /v2/timeseries/{appId}/detect-anomalies`. Rolling-median + isolation-forest. Optionally writes `dlr:anomaly` annotations. | ML | M | AI1a |
| **AI1c** | Channel-quality scoring (§3.2) — background job, automatic `qualityScore` attribute. | ML | S | AI1a |
| **AI1d** | Embedding-based similarity (§3.5) + `GET /v2/data-objects/{appId}/similar`. | ML | M | AI1a |
| **AI1e** | LLM sidecar: bundle a small local LLM (Qwen2.5-7B or Mistral-7B equivalent), expose `/infer/llm/...`. | LLM | M | AI1a |
| **AI1f** | Natural-language search (§5.1). | LLM | M | AI1e + `aidocs/13` (unified search) |
| **AI1g** | Lab journal authoring assist (§5.2). | LLM | M | AI1e + `aidocs/37` J1a |
| **AI1h** | Semantic annotation suggestion (§5.4). | LLM | M | AI1e + `aidocs/14` |
| **AI1i** | Auto-summarisation (§5.3). | LLM | S | AI1e |
| **AI1j** | RO-Crate description generation (§5.5). | LLM | S | AI1e + `aidocs/31` |
| **AI1k** | Conversational lineage (§5.6). | LLM | M | AI1e + `aidocs/30` |
| **AI1l** | Notebook scaffolding (§5.7). | LLM | S | AI1e + `aidocs/37` J1c |
| **AI1m** | (deferred) Forecasting (§3.3) — only if real demand surfaces. | ML | M | parked |
| **AI1n** | (deferred) Outlier detection in attribute vectors (§3.4). | ML | S | parked |
| **AI1o** | (deferred) Search-rank learning (§3.6) — gated on having scale. | ML | L | parked |
| **AI1p** | (deferred) Hosted-model bridge — opt-in proxy to OpenAI / Claude / Mistral hosted endpoints with per-Collection toggle + audit log. | Infra | M | parked |

Recommended order: **AI1a → AI1b → AI1c → AI1d → AI1e → AI1g →
AI1f → AI1h → AI1i**. AI1a is the gate for everything; AI1b is the
fastest demonstrable win (we already have the algorithm in the
showcase); AI1d unlocks similarity which improves search before any
LLM exists. AI1e is the LLM gate.

## 7. Risks

- **Hallucination on scientific claims.** Mitigated by §2's
  "suggestion-only" rule for text-generating features. The LLM
  cannot create or modify entities autonomously.
- **Vendor lock-in (LLM).** Mitigated by §4's "local model
  default." Switching to a different small open-weights LLM is a
  config change.
- **Compliance drift.** Operator might enable hosted-model mode
  thinking it's local. Mitigated by §5's per-Collection toggle —
  the choice surfaces every time data is exported, not buried in
  global config.
- **GPU envy.** Operators read about LLMs and assume they need
  GPUs; they don't, for the recommended local 7-8B models.
  Document expected throughput on CPU clearly in `docs/admin.md`.
- **Reproducibility.** AI inference is not bit-stable across model
  versions. Captured outputs (annotations, summaries) are frozen
  at write time and travel with snapshots (`aidocs/41`); re-inference
  is a separate explicit action.

## 8. Cross-references

- **aidocs:** `aidocs/13` (unified search — AI1f's substrate),
  `aidocs/14` (semantic annotations — AI1h's target),
  `aidocs/30` (lineage — AI1k's substrate),
  `aidocs/31` (RO-Crate exports — AI1j's hookup),
  `aidocs/35` (HSDS sidecar pattern — model for §4 AI sidecar),
  `aidocs/36 §3.2` (`editor.preferredJupyter` — AI1l's deep link),
  `aidocs/37` (lab journal — AI1g hooks here),
  `aidocs/41` (snapshots — AI outputs are snapshot-frozen).
- **Backlog:** new **AI1** umbrella + AI1a-AI1p sub-IDs in
  `aidocs/16`. All gated on AI1a.
- **Vision:** `aidocs/42` "Where it's going" gains an AI bullet
  when AI1a ships.
