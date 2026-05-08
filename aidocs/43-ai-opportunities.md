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
| **Data residency** | DLR research data must not leave operator-controlled infrastructure unless someone explicitly opts in | shepard ships **zero AI models**. AI features are **disabled by default** and only light up when an inference endpoint is configured. Three-tier resolution per request: (1) the **user's own** OpenAI-compatible endpoint from `/me` settings (`ai.apiKey` / `ai.baseUrl` / `ai.model`); (2) the **admin's optional fallback** (`shepard.ai.fallback.*`) — typically a self-hosted Ollama / vLLM / llama.cpp endpoint, but can also be a shared institute-wide hosted account; (3) **AI features hidden** with a clear UI message pointing at `/me` settings. Per-Collection sensitivity toggle can additionally block AI calls for explicitly-flagged data even when an endpoint is configured. See §4.1 for the contract and §4.2 for the resolution rule. |
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

## 4. The inference-endpoint contract

shepard does **not** ship inference models. It ships the
**plumbing** — chat UI, tool-use catalogue, Vega-Lite renderer,
secret-class settings, audit hooks — and talks to whatever the
operator and users configure.

### 4.1 BYOK + admin fallback (the auth/endpoint hierarchy)

Two configuration surfaces:

**Per-user (the primary path).** Each user can configure their own
inference endpoint in `/me` settings (`aidocs/36 §3.2`):

| Setting | Type | Stored | Notes |
|---|---|---|---|
| `ai.apiKey` | secret-class string | encrypted at rest (`aidocs/36 §3.3`) | Never returned in `GET /users/me`, only `aiApiKeyPresent: bool`. Re-enter to change. |
| `ai.baseUrl` | URL | plaintext | OpenAI-compatible. Examples: `https://api.openai.com/v1`, `https://api.anthropic.com/v1`, `https://api.mistral.ai/v1`, `https://openrouter.ai/api/v1`, `http://localhost:11434/v1` (Ollama), `http://vllm:8000/v1` (vLLM), `https://<azure>.openai.azure.com/openai/deployments/{deployment}` |
| `ai.model` | string | plaintext | Model id at the chosen provider (`gpt-4o-mini`, `claude-sonnet-4-5`, `llama3.1:8b`, etc.). |

**Per-deployment (admin fallback).** Operator may configure a
fallback endpoint that users without their own key can use:

| Config key | Notes |
|---|---|
| `shepard.ai.fallback.enabled` | bool, default `false`. When false, users without `ai.apiKey` get AI features hidden in the UI. |
| `shepard.ai.fallback.baseUrl` | OpenAI-compatible endpoint. Typically a self-hosted Ollama / vLLM / llama.cpp on the same network, or an institute-wide commercial account. |
| `shepard.ai.fallback.apiKey` | Optional. Read from a Quarkus secret (file or env), never logged. |
| `shepard.ai.fallback.model` | Default model name for the fallback. |

The fallback is **opt-in for the operator**. A vanilla shepard
install ships with `shepard.ai.fallback.enabled = false` and AI
features stay invisible until either the operator turns the
fallback on or individual users add their own key.

### 4.2 Resolution rule (per request)

```
on(any AI feature invocation by user U):
  if U has ai.apiKey set:
    use U's (apiKey, baseUrl, model)
  elif shepard.ai.fallback.enabled:
    use admin's fallback endpoint
    log: "fallback used by user=<U>"  (operator visibility)
  else:
    return 403 with body: "AI features require a personal API key. Visit /me settings."
```

The 403 is **expected and silent in the UI** — frontend simply
hides the AI controls. The third state (no endpoint configured at
all) is the default state on a fresh install — no chat icon, no
suggestion buttons, no surprises.

### 4.3 OpenAI-compatible contract

The `/v1/chat/completions` shape is the lingua franca; almost every
modern provider speaks it. shepard's LLM client is a thin wrapper
around this shape — no provider-specific SDKs.

For **non-LLM ML features** (anomaly detection, quality scoring,
similarity embeddings — §3.1 / §3.2 / §3.5), the admin fallback
also serves as the embedding endpoint via `/v1/embeddings`. A user
without a key can still consume the operator's local embedding
model if the fallback is configured.

### 4.4 Optional: operator-deployed local inference

For operators who want fully self-hosted AI (no third-party calls
ever), the recommended pattern is a **separate inference container
the admin chooses** — not a shepard subsystem. Three turn-key
options for the `ai` docker-compose profile:

```yaml
# Choose ONE and set shepard.ai.fallback.baseUrl to point at it.
profiles: ["ai"]
services:
  ollama:
    image: ollama/ollama:latest
    # ... (downloads models on first run)
  # OR
  vllm:
    image: vllm/vllm-openai:latest
    # ... (GPU required)
  # OR
  llamacpp:
    image: ghcr.io/ggerganov/llama.cpp:server
    # ... (CPU acceptable for 7B-class)
```

shepard's docs ship one example for each in `docs/admin.md`. Picking
which one is the operator's call — shepard doesn't lock in.

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

### 5.8 Snap dashboards — LLM-driven analysis chat (the headline feature)

The killer feature: a Claude-chat-style sidebar where the user
describes the analysis they want and the LLM **builds an inline
dashboard** by walking shepard's data + generating chart specs.

**The user-side experience.**

```
User: Show me vibration_max vs rpm_fuel_pump for the last 7 fired
      runs in this Collection. Flag any outliers above 10 g rms.
LLM:  [walks the Collection: 7 fired runs found, fetching channels...]
      [renders inline chart]
      I found 7 runs. TR-004 is 2.5σ above the others —
      vibration_max = 12.7 g rms vs the cohort median of 4.2 g.
      The other six fall within 3.6–5.1 g.

      [Refine ▾] [Save dashboard] [Open in Jupyter] [Show data]

User: Now exclude TR-004 and group by test_engineer.
LLM:  [updates the chart in place]
      ...
```

**The architectural shape.**

The LLM has **tool-use** against a **closed catalogue** of shepard
operations. Each tool has a typed schema; the LLM cannot invoke
arbitrary code. Tool catalogue (post-AI1q):

| Tool | Returns | Auth scope |
|---|---|---|
| `search_data_objects` | List of DataObjects matching attribute filters | User's permission scope |
| `read_attributes` | Attribute values for a DataObject | User's read permission |
| `fetch_timeseries` | Aggregated values for `(timeseriesRef, channel, start, end, agg, bucket)` | User's read permission |
| `fetch_structured` | StructuredData document content | User's read permission |
| `list_lab_journal` | Lab journal entries on an entity | User's read permission |
| `render_chart` | A Vega-Lite v5 spec → inline rendered SVG | n/a (rendering only) |
| `render_table` | A row-oriented JSON dataset → inline table | n/a |

Every tool call is **logged with the user's identity** and the
resulting access goes through the same permission cache as direct
API calls — the LLM cannot escalate.

**Why Vega-Lite (not Python plotting).** Vega-Lite is a JSON
chart-spec language. The LLM emits a spec; the frontend renders.
**No code execution sandbox required**, no Python sidecar growth
needed, and the spec is human-editable in a Refine pane.
Expressiveness covers ~90% of what dashboards do (line / bar /
scatter / heatmap / facet / brush / tooltip). Cases that don't
fit (FFT, custom transforms, scientific computations) fall back
to AI1l notebook-scaffolding (§5.7).

**Save / share.** The "Save dashboard" button persists the
conversation transcript + the final Vega-Lite spec + the data refs
as a new entity:

- Stored as a **`DashboardReference`** under the active Collection
  (new payload-kind sibling to FileReference / TimeseriesReference).
- The spec re-renders against live data on every read — the same
  `?snapshot={appId}` query param from `aidocs/41` makes the
  dashboard reproducible against a snapshot.
- Permissions inherit from the Collection.
- Goes into RO-Crate exports as a `Dashboard` entity carrying the
  spec + the data refs (reproducible by downstream consumers with
  any Vega-Lite renderer).

**Iteration loop.** The chat keeps history. Each user message
triggers another LLM turn that may refine the existing spec
(in-place chart update) or create a new one (new chart appended).
The user can also click **Refine** to edit the Vega-Lite spec by
hand — escape hatch for power users.

**The "wow" sentence.** A new researcher opens shepard, sees the
chat icon, types *"compare the chamber pressure across all fired
runs"*, and 4 seconds later has a publication-quality faceted
chart with the LLM's narration. **No notebook, no `pip install`,
no asking a colleague how the data is shaped.** That's what
"killer feature" looks like in this context.

**Scope discipline.** The LLM **does not**:

- Mutate any shepard entity (no creates / writes / annotations from
  chat — those go through the dedicated assistive endpoints in
  §5.1 / §5.2 / §5.4 with their own confirmation UX).
- Run arbitrary code in the sandbox.
- Expose tool-use that bypasses permissions (every tool is scoped
  to the user's read permissions).
- Persist anything without an explicit "Save dashboard" click.

### 5.9 Anti-features (deliberately out of scope)

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
| **AI1a** | **AI plumbing slice.** `ai.apiKey` / `ai.baseUrl` / `ai.model` per-user settings (`aidocs/36 §3.2`); `shepard.ai.fallback.*` admin config; OpenAI-compatible `LlmClient` wrapper around `/v1/chat/completions` + `/v1/embeddings`; resolution rule from §4.2; per-Collection sensitivity toggle. **No models bundled, no inference container.** Without configuration, AI controls stay hidden in the UI. | Infra | M | aidocs/36 U2-coupled (secret-class settings infra) |
| **AI1b** | Anomaly detection (§3.1) — `POST /v2/timeseries/{appId}/detect-anomalies`. Rolling-median + isolation-forest. Pure-Python, no LLM call. **Independent of AI1a** — ships even on installs without an LLM endpoint. Optionally writes `dlr:anomaly` annotations. | ML | M | None |
| **AI1c** | Channel-quality scoring (§3.2) — background job, automatic `qualityScore` attribute. Pure heuristics, no LLM. **Independent of AI1a.** | ML | S | None |
| **AI1d** | Embedding-based similarity (§3.5) + `GET /v2/data-objects/{appId}/similar`. **Requires AI1a** because it calls `/v1/embeddings` against the configured endpoint (BYOK or admin fallback). | ML | M | AI1a |
| **AI1e** | First LLM-driven feature: **snap dashboards (§5.8)** — chat sidebar with tool-use catalogue (`search_data_objects` / `read_attributes` / `fetch_timeseries` / `fetch_structured` / `list_lab_journal` / `render_chart` / `render_table`); inline Vega-Lite v5 rendering; `DashboardReference` save shape. **The killer-feature slice.** | LLM | L | AI1a + L2c (so tool-use addresses entities by stable `appId`) |
| **AI1f** | Natural-language search (§5.1). | LLM | M | AI1a + `aidocs/13` (unified search) |
| **AI1g** | Lab journal authoring assist (§5.2). | LLM | M | AI1a + `aidocs/37` J1a |
| **AI1h** | Semantic annotation suggestion (§5.4). | LLM | M | AI1a + `aidocs/14` |
| **AI1i** | Auto-summarisation (§5.3). | LLM | S | AI1a |
| **AI1j** | RO-Crate description generation (§5.5). | LLM | S | AI1a + `aidocs/31` |
| **AI1k** | Conversational lineage (§5.6). | LLM | M | AI1a + `aidocs/30` |
| **AI1l** | Notebook scaffolding (§5.7). | LLM | S | AI1a + `aidocs/37` J1c |
| **AI1m** | (deferred) Forecasting (§3.3) — only if real demand surfaces. | ML | M | parked |
| **AI1n** | (deferred) Outlier detection in attribute vectors (§3.4). | ML | S | parked |
| **AI1o** | (deferred) Search-rank learning (§3.6) — gated on having scale. | ML | L | parked |
| **AI1p** | Per-provider OpenAPI nuances (Azure deployment URL paths, Anthropic-style `messages` endpoint adapter, etc.). Ships incrementally as users hit them. | Infra | S each | AI1a |

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
