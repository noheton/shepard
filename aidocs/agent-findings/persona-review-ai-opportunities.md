---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Persona Review — Analytics & AI Opportunities Specialist

**Reviewer.** Applied ML engineer / data scientist, evaluating
Shepard's evolving "trio" of design moves: SHACL-shape templates,
shape-driven views/workspace, and the AI-provenance shape (F(AI)²R).

**Snapshot date.** 2026-05-22.

**Scope note.** The brief named three docs (98-mffd-process-shapes,
100-mffd-views-workspace, 101-view-shapes-and-spi) and a `shapes/
mffd-ai-annotation.shacl.ttl` file. None of those exist in the
repo today. The closest live designs are:

- `aidocs/semantics/95-shacl-templates-and-individuals.md` Parts
  1–3 (shapes as **templates / views / agent contracts**) — this
  IS the trio in current form.
- `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 15
  (F(AI)²R AI provenance capture, TPL9 series) — the AI shape in
  current form. There's no separate `mffd-ai-annotation.shacl.ttl`
  yet; the constraint shape (`fair2r:Claim` ⇒ `prov:wasGeneratedBy
  minCount 1`) is described in prose only.
- `aidocs/semantics/43-ai-opportunities.md` — the AI1 series
  framing (AI1a plumbing, AI1b anomaly, AI1d similarity, AI1e snap
  dashboards, AI1r experiment ontology, AI1q SPARQL tool).

The review is against the **live design** at those locations.
I flag the gap between the live design and the brief's referenced
files as `[NEEDS-CLARIFICATION]` §7.

---

## 1. Verdict — does the trio enable AI value or block it?

**Enables, decisively, with caveats.** This is the cleanest
substrate for AI in scientific RDM that I've seen at this stage of
design. Three structural reasons:

1. **Shapes-as-agent-contracts (Part 3) is the right
   abstraction.** It collapses "tool catalogue" and "data schema"
   into one artefact. The LLM's `instantiate_template(shapeAppId,
   payload)` call is validated by exactly the same SHACL the
   form is validated by — no separate JSON-Schema-for-MCP path
   that can drift from the form-path. The Qwen-LUMEN-wrong-
   container failure mode goes away by construction.
2. **Named individuals (Part 4) make every DataObject SPARQL-
   addressable.** Embeddings, graph-features, anomaly markers,
   cross-source joins all key off the same stable IRI
   (`shepard-instance/<id>/dataobject/<appId>`). No 5-tuple
   smell, no derived URLs.
3. **F(AI)²R (Part 15) makes AI auditable from day one rather
   than retrofitted.** The `fair2r:Claim` ⇒ `wasGeneratedBy`
   invariant is the single piece I'd want most in an ML
   regulatory pipeline, and it's already designed.

The caveats are real but bounded:

- The embedding endpoint is **described** (AI1d depends on AI1a's
  `/v1/embeddings`) but the **storage shape** for vectors is not
  decided. pgvector vs in-Neo4j vs separate vector DB is open.
- Reproducibility metadata captured today (`X-AI-Agent`,
  `X-AI-Prompt-Hash`, `X-AI-Verification`) is **good for
  attribution** but **thin for ML reproducibility** — no
  temperature, top-p, random seed, or sampler params. Doable as
  additive fields; not yet there.
- The "shape" referenced in the brief
  (`mffd-ai-annotation.shacl.ttl`) doesn't exist as a vendored
  TTL yet. The TPL9a slice plans to vendor `provenance.ttl` from
  `github.com/noheton/f-ai-r`; the MFFD-specific shaping on top
  is a TPL9c gap.

---

## 2. Opportunity matrix

For each opportunity: how well the live trio supports it today,
data readiness against `examples/lumen-showcase` + `examples/mffd-
showcase`, model complexity needed, and user-facing value.

| # | Opportunity | Feasible against trio | Data readiness | Model complexity | User value |
|---|---|---|---|---|---|
| 1 | **Anomaly detection on timeseries** (AI1b) | High — pure ML, no LLM, no trio dependency. Anomalies become `dlr:anomaly` annotations on the source TimeseriesRef, which become first-class named individuals via Part 4. | High — LUMEN TR-004 vibration spike at t=8s is a built-in fixture; `examples/lumen-showcase/notebooks/anomaly-analysis.ipynb` already implements rolling-median ± k·MAD. Generalises trivially. | Low (rolling-median / IQR / isolation forest, CPU-only). Autoencoder is the next rung; not needed for v1. | High — directly answers "did anything weird happen?" without manual triage. The headline demo. |
| 2 | **Auto-annotation from file content** (AI1h + AI1g) | Medium — needs the AI plugin to *propose* and the SHACL shape (Part 1) to *constrain* the proposal to vocabulary terms. Suggestion-only; user accepts. F(AI)²R captures the prompt + verification state. | Low — Shepard treats files as opaque blobs today. PDF/CSV/HDF5 extraction is an unimplemented adapter. The annotation target vocabulary (`shpe:DefectType`, `shpe:InspectionMethod`, AI1r) IS shipped — but the bridge from "file content" to "suggested annotation" is the gap. | Medium-high — depends on LLM + structured-output (Anthropic `tool_use` / OpenAI `response_format=json_schema`). Domain-tuning improves yield but isn't required to start. | High — eliminates 80% of "type the same five attributes per DataObject" tedium. The TR-006 `propellant: LOX/LH2` keystrokes are exactly this class. |
| 3 | **Semantic embedding / similarity search** (AI1d) | High in spirit, **missing the storage half**. Part 4 gives stable individual IRIs to embed; the SPARQL surface (AI1q) lets the LLM consume similarity. But: **where do the vectors live?** Postgres pgvector? Neo4j as a list property? Separate vector DB? Not decided. | Medium — DataObject attributes + lab journal text are present in LUMEN/MFFD seeds; chunking strategy is open. | Low (sentence-transformers small models, CPU). 384-d MiniLM is the obvious default. | High — "find the closest historical match for this anomaly" is the daily-driver query a senior researcher actually values. |
| 4 | **Provenance gap detection on the graph** | High — this is *exactly* what Part 4 + Part 15 enable. SPARQL `SELECT ?do WHERE { ?do prov:wasGeneratedBy ?act . MINUS { ?act prov:used ?input }}` finds orphans. AI1q (`query_knowledge_graph` tool) lets Lumen ASK questions like "which DataObjects in this Collection have no calibration link?" | High — the seeded ontology + named individuals are already SPARQL-shaped; no further data prep needed. The MFFD seed deliberately includes 12-step DAG which is rich enough to test on. | Trivial (deterministic SPARQL; ML optional for "what's the *next-most-likely* missing edge"). | Medium-high — auditor-grade. The DIN EN 9100 readiness story leans on this. |
| 5 | **LLM-generated import manifests** (`AgentContextIO` + future) | Medium — Part 3 (shapes as agent contracts) is the right substrate, but the **import-side** `instantiate_template` MCP tool isn't shipped. The `GET /v2/import/context` endpoint exists; the round-trip (context → LLM → manifest → validate against shape → commit) needs the seal-and-validate plumbing already designed in the importer. | Medium — MFFD `examples/mffd-showcase/seed.py` produces canonical good manifests; train/eval pairs exist. | Medium (function-calling against the shape catalogue; structured output). | High for agentic ingest; the shepard-plugin-importer story rides on this. |
| 6 | **Training-data curation / publishable corpus** | Mixed — trio gives lineage (Part 4) and verification status (Part 15), which is exactly the metadata FAIR4ML wants. **Blocker**: no `license`, no `embargo_until`, no per-DataObject sensitivity flag on the entity today; can't tell "is this clearable to publish?" without those fields. | Low for IP-restricted MFFD data (DLR industrial IP); High for LUMEN-style synthetic data. | n/a (curation = filtering + export pipeline, not modelling). | High *if* the licensing story lands. Otherwise blocked. |

**Net.** Opportunities 1 + 4 are shippable now against the trio.
Opportunity 3 needs **one architectural decision** (vector
storage). Opportunities 2 + 5 are well-shaped but need adapter
work. Opportunity 6 is blocked on non-AI prerequisites (license
field, embargo, sensitivity flag — same gap the FAIR/RDM review
calls out independently).

---

## 3. What works

- **The SHACL-shape-as-three-things trick is the single biggest
  AI affordance in this design.** A new payload kind ships zero
  MCP code: the agent contract (Part 3) generates from the same
  shape that drives the form (Part 1) and the view (Part 2). For
  an ML pipeline that wants to *write* into Shepard (REBAR /
  MLflow / OpenLineage mirror), the surface is auto-generated.
  This collapses an entire category of integration work.

- **Named individuals (Part 4) give every entity a stable IRI**
  that an embedding model, a graph-features model, or a SPARQL
  tool can address. The 5-tuple-channel pain that hurts
  timeseries pipelines today is solved upstream of the AI work.

- **F(AI)²R (Part 15) gets the *attribution* dimension of
  reproducibility right.** Specifically: the `X-AI-Agent` header
  maps cleanly to a `fair2r:AIAgent` named individual; the
  `X-AI-Prompt-Hash` is content-addressed; the verification
  ladder (`verif:unverified` → `verif:human-confirmed`) is
  surfaced as a UI affordance. For EU AI Act Article 50, this
  is the right shape.

- **The no-parentless-claim SHACL invariant is structurally
  correct.** `sh:property [sh:path prov:wasGeneratedBy ;
  sh:minCount 1]` on `fair2r:Claim` makes it *impossible* to
  land an AI annotation without an Activity. That's how
  invariants should work — not as a process discipline, as a
  validation gate.

- **Snap dashboards (AI1e) sit on the right side of the
  hallucination tradeoff.** The LLM emits a *Vega-Lite spec*,
  not Python; the spec renders deterministically; the user sees
  the structured query before the chart. No code sandbox, no
  blind execution. This is the architecturally honest version
  of "AI builds you a dashboard."

### Does the AI-annotation shape capture reproducibility?

**Partially.** The shape captures the *attribution* triple
(agent, prompt hash, verification state) but **not** the *sampling
configuration* triple (temperature, top-p, seed). Concretely:

| Reproducibility dimension | F(AI)²R / Part 15 covers? |
|---|---|
| Which model? | ✅ `fair2r:AIAgent` individual per model (`agent:claude-opus-4-7`) |
| Which prompt? | ✅ `X-AI-Prompt-Hash` (sha256) + optional `fair2r:Prompt` entity |
| Which version? | ⚠ Implied by the agent IRI (`claude-opus-4-7`) but model-version drift inside that label is invisible |
| Which sampling params (T, top-p, top-k)? | ❌ Not captured anywhere |
| Which random seed? | ❌ Not captured anywhere |
| Which context (RAG inputs)? | ⚠ "optional, plugin-contributed" — design but no slice |
| Which tool calls executed? | ⚠ AI1q says "every SPARQL call is logged to `:Activity`" — but tool-call sequence per pass is not bundled |
| What was the response? | ⚠ Stored output is the annotation; full LLM reply is not retained |

For *citation*, the current shape suffices. For *reproducibility
under fine-tuning* or *bisecting a regression in model
behaviour*, it's thin. Easy to extend — additive fields on the
`fair2r:AuthoringPass` activity.

---

## 4. What's missing

- **No embedding endpoint shape decided.** AI1d says
  "calls `/v1/embeddings` against the configured endpoint" —
  fine for the *inference call*, but the storage shape is
  open. Recommend: native `EmbeddingReference` as a new
  payload-kind sibling to `TimeseriesReference`, backed by
  pgvector (Shepard already uses Postgres). Channel identity
  is the DataObject's appId-derived IRI from Part 4. One
  vector per DataObject for v1; per-chunk later.

- **No model registry / model card storage.** F(AI)²R captures
  the agent IRI but not the *model card*. For REBAR + FAIR4ML
  flow, Shepard needs a place to store the trained-model
  artefact AND its FAIR4ML SHACL-shape-validated metadata.
  Recommend: `:Model` as a domain class with a SHACL shape (TPL1
  scaffolding), stored as a `StructuredData` payload referencing
  the model file in `FileContainer`. This is also where the
  REBAR/MLflow integration plugs in.

- **No confidence-aware view dispatch.** A view (Part 2) renders
  the same fields whether the value came from a human or from an
  unverified AI. The F(AI)²R verification-state pill (TPL9d) is
  designed but **per-property, not per-view**. A "hide
  unverified AI fields" toggle on the Collection view would close
  the loop for users who want a "human-only" reading pass.

- **No GWDG/SAIA-shaped integration adapter.** AI1a's plumbing is
  OpenAI-compatible, which SAIA (LiteLLM proxy) speaks. But the
  *recommended path* for DLR deployments — admin preconfigures
  capability slots with SAIA endpoint + key-obtain-instructions
  text + optional shared key — is in memory only, not in the
  design. Recommend folding the per-capability slot pattern into
  AI1a explicitly so DLR-flavour installs ship with SAIA as the
  default endpoint.

- **No shape for the AI-suggestion → user-accept → annotation
  flow.** AI1h ("semantic annotation suggestion") describes the
  endpoint shape but the *intermediate* "rejected suggestion"
  state isn't captured. For training data (active learning
  loop), rejected suggestions are as valuable as accepted ones.
  Recommend: every suggestion writes a `fair2r:Claim` with
  `verif:unverified`; on user decision the activity transitions
  to `verif:human-confirmed` (accept) or
  `verif:user-rejected` (a new rung).

---

## 5. Arguments for different paths

### Q1. AI annotations as separate reference type vs. inline

**Path A (inline) — current design.** AI-generated annotations
look like any other `SemanticAnnotation`, distinguished only by
the `fair2r:Claim` type + the `wasGeneratedBy` activity edge.

- **Pro.** One annotation type to query, render, export. Existing
  facet/view code reuses. The verification ladder works
  uniformly. RO-Crate export carries the AI flag via the
  property, not the structure.
- **Con.** A view that wants to *exclude AI annotations* has to
  filter at query time, not at storage time. Permission grants
  on "AI suggestions only" are awkward.

**Path B (separate ref type) — `AISemanticAnnotation` subtype.**
A new payload-kind for AI-generated annotations.

- **Pro.** Clean storage separation; clean per-type permission.
- **Con.** Doubles the annotation surface; every consumer (MCP,
  RO-Crate, Unhide, views) needs a branch. F(AI)²R's whole point
  is that "Claim" is a *role*, not a separate class.

**Lean: Path A.** F(AI)²R is right about this — *every*
annotation is a claim, AI-generated or not; the difference is
the activity that produced it. Path B duplicates without
benefit.

### Q2. Embedding storage — `EmbeddingReference` payload-kind vs. pgvector container

**Path A (`EmbeddingReference` payload-kind).** Sibling to
TimeseriesReference / FileReference. Backed by pgvector under
the hood.

- **Pro.** Fits the existing payload-kind SPI (47§2). Plugin-
  first. Permissions, views, MCP tools all reuse. The vector is
  *of* a DataObject, lives *with* the DataObject's lineage.
- **Con.** New SPI plumbing.

**Path B (separate `:VectorIndex` container).** A Collection-
scoped container that batch-embeds all DataObjects in the
Collection on demand.

- **Pro.** Fits batch/ANN workflows; one index per Collection.
- **Con.** Decoupled from per-DataObject lineage. The vector
  isn't "of" a DataObject in the graph.

**Lean: Path A** for per-DataObject vectors (semantic similarity,
"find similar runs"), with **Path B as a future v2** for
Collection-scoped ANN over thousands of vectors. They're not
mutually exclusive — Path B builds on Path A's vectors.

### Q3. Shape-driven prompt templates vs. plugin-supplied prompt registry

**Path A (shape-driven).** The SHACL shape includes a
`shepard-ai:promptTemplate` annotation property: "when an AI is
asked to fill this shape, use this prompt template (Jinja, with
shape-derived field names as variables)."

- **Pro.** One place for everything about a payload kind. Adding
  a new shape ships a new auto-annotation prompt for free. The
  prompt versions with the shape (git-ingested per Part 12).
- **Con.** Couples ontology authoring to prompt engineering;
  same person now owns both.

**Path B (plugin registry).** `shepard-plugin-ai` ships a
registry of named prompt templates; shapes reference them by
ID.

- **Pro.** Decouples ontology authoring (semantic team) from
  prompt engineering (ML team). Prompts can iterate
  independently of ontology releases.
- **Con.** Two artefacts to keep in sync; risk of orphan prompts.

**Lean: Path A first, Path B as the *override* mechanism.**
Default prompt = derived from the shape's `rdfs:comment` +
field metadata; plugin can override with a richer template
when needed. This matches the "ontology drives the UI" thesis —
the same shape that drives the form drives the auto-fill prompt.

---

## 6. f(ai)²r adequacy

Comparing F(AI)²R vocabulary at <https://noheton.org/f-ai-r/ns#>
(via memory + doc 95 Part 15) to what the live design captures:

| f(ai)²r predicate / class | Carried by Part 15? | Notes |
|---|---|---|
| `fair2r:AIAgent` | ✅ | One named individual per model. Required header `X-AI-Agent`. |
| `fair2r:HumanResearcher` | ✅ | OIDC user via `prov:actedOnBehalfOf`. |
| `fair2r:Claim` | ✅ | Every AI-generated `SemanticAnnotation`. SHACL invariant `wasGeneratedBy minCount 1`. |
| `fair2r:Source` | ⚠ | Mentioned (§Open questions: "vendored sources") but no shape / storage / MCP tool yet. |
| `fair2r:Prompt` | ✅ | `:StructuredData` document with text + hash + system prompt + RAG context. "optional, plugin-contributed" — defer-OK. |
| `fair2r:Transcript` | ⚠ | `X-AI-Session-ID` header is defined, but the transcript *storage* isn't designed. Snap-dashboards (AI1e) saves the transcript with the `DashboardReference` — that's the only path so far. |
| `fair2r:AuthoringPass` | ✅ | MCP `POST/PATCH/PUT` activities. |
| `fair2r:AuditPass` | ✅ | MCP `GET` when caller identifies as AI. |
| `fair2r:verificationState` | ✅ | Per-claim, with UI pill (TPL9d) and ladder widget (TPL9f). |
| `fair2r:contradicts` | ❌ | Not in the design. Useful for "this annotation contradicts that one" — defer-OK. |
| `fair2r:repairs` | ✅ | Each verification-ladder promotion creates a `fair2r:repairs` Activity. |
| Verification ladder rungs (`verif:unverified | needs-research | lit-retrieved | ai-confirmed | human-confirmed | source-vendored | lit-read`) | ✅ | All seven as named individuals. |
| **Reproducibility metadata (T, top-p, seed, top-k, max-tokens)** | ❌ | Not in any header; not in the activity shape. Gap. |
| **Tool-call provenance (which tools fired during the pass)** | ⚠ | AI1q logs SPARQL calls to `:Activity` — but tool calls per AuthoringPass aren't bundled into one auditable record. |
| **Cost / token usage** | ❌ | Not captured. For operator billing / SAIA quota tracking, would want it. |

**Summary.** The *attribution* dimension is fully covered.
The *reproducibility* dimension is half-covered — agent and prompt
yes, sampling params no. The *cost / quota* dimension is absent.
The first gap is a 1-day additive change (extend the `X-AI-*`
header set + the activity properties); the second is a separate
NTF1 / operator-observability story.

---

## 7. `[NEEDS-CLARIFICATION]`

### NC-1. Where do the "trio" docs live?

The brief named `aidocs/semantics/98-mffd-process-shapes.md`,
`aidocs/platform/100-mffd-views-workspace.md`,
`aidocs/platform/101-view-shapes-and-spi.md`, and
`shapes/mffd-ai-annotation.shacl.ttl`. None exist in the repo.
The concepts are in `aidocs/semantics/95-shacl-templates-and-individuals.md`
Parts 1–3 + Part 15.

**Options.**
- **A.** Treat 95 Parts 1–3 + 15 as canonical, write the review
  against them, flag the doc naming gap. (Chosen for this review.)
- **B.** Treat the named docs as designs in flight from a worktree
  that hasn't merged yet, surface the question, wait.
- **C.** Split doc 95 into the four named files retroactively;
  match the brief's filenames.

**Lean: A for the review; C as a follow-up.** Doc 95 is 1,500+
lines and increasingly hard to navigate; splitting Parts 1–3 into
98 / 100 / 101 and Part 15 into a vendored TTL would improve
discoverability. But that's a refactor, not a content change.

### NC-2. Embedding storage — payload-kind or container?

See §5 Q2. The design needs a decision before AI1d ships.

**Options.**
- **A.** `EmbeddingReference` payload-kind, pgvector backend.
- **B.** `:VectorIndex` Collection-scoped container.
- **C.** Both; A for per-DO, B for batch ANN.

**Lean: C (start with A).**

### NC-3. Reproducibility metadata in F(AI)²R headers?

See §6 last rows. Today's `X-AI-*` headers cover attribution,
not sampling.

**Options.**
- **A.** Add `X-AI-Temperature`, `X-AI-Top-P`, `X-AI-Seed`,
  `X-AI-Max-Tokens` as additive headers on TPL9b.
- **B.** Defer; capture inside the `fair2r:Prompt` entity body
  as an extension.
- **C.** Both — headers for the hot path, full record in the
  Prompt entity.

**Lean: C.** Headers are cheap; the Prompt entity covers
provider-specific extras.

### NC-4. Shape-driven prompts — vendored on the shape itself?

See §5 Q3. The design has no `shepard-ai:promptTemplate` slot
on shapes today.

**Options.**
- **A.** Add `shepard-ai:promptTemplate` as a SHACL annotation
  property; render via Jinja.
- **B.** External registry in `shepard-plugin-ai`.
- **C.** Hybrid (Lean per §5 Q3).

**Lean: C.**

### NC-5. SAIA / GWDG as default for DLR-flavour installs?

The memory note (`project_ai_plugin_config.md`) says SAIA is the
recommended DLR provider; AI1a's "BYOK + admin fallback" pattern
supports it but doesn't ship it as a default.

**Options.**
- **A.** Vanilla AI1a ships disabled; DLR-flavour overlay (per
  `project_dlr_ui_flavor`) pre-configures SAIA.
- **B.** AI1a ships a `dlr-saia` profile out of the box.

**Lean: A.** Keep the upstream story clean; DLR-flavour overlays
add the seed.

---

## 8. Top 3 changes for AI to be a first-class lens

### #1 — Decide and ship the embedding storage shape (closes AI1d)

Without a place to put vectors, similarity search, "find runs like
this one," graph-features ML, and any retrieval-augmented LLM
workflow stall. The decision is one architectural call (per §5 Q2
and NC-2): **`EmbeddingReference` as a payload-kind, pgvector
backend**. One sprint to ship. Unlocks AI1d + future AI1f (NL
search reranking) + RAG over Collection content.

### #2 — Add sampling-params + tool-call provenance to F(AI)²R (closes the reproducibility half)

Today's `X-AI-*` headers cover *who* and *what prompt*, not
*how* (temperature, seed, sampler). One day of additive header
work on TPL9b + a small extension to the `fair2r:AuthoringPass`
SHACL shape. Unblocks EASA Learning Assurance evidence packs
(REBAR integration) and FAIR4ML model-card export.

### #3 — Ship `shepard-ai:promptTemplate` as a SHACL annotation property (closes auto-annotation)

The single biggest user-value win in §2 (Opportunity #2,
auto-annotation) hinges on a credible prompt-per-shape story.
Adding `shepard-ai:promptTemplate` to the shape vocabulary lets
every new payload-kind ship its own auto-annotation prompt for
free, with the shape, in git. Pair with `shepard-plugin-ai` as
the override mechanism. This is the move that turns "ontology
drives the UI" into "ontology drives the *AI surface*" — the
strategic claim Shepard is making anyway.

---

**Filed by.** Analytics & AI Opportunities Specialist
(persona).
**Inputs read.** `aidocs/semantics/43-ai-opportunities.md`;
`aidocs/semantics/95-shacl-templates-and-individuals.md` Parts
1–3, 4, 15; memory files `project_fair2r_integration.md`,
`project_ai_plugin_config.md`, `project_rebar_integration.md`.
**Inputs missing** (per NC-1): the four files named in the
brief (`98-mffd-process-shapes.md`, `100-mffd-views-workspace.md`,
`101-view-shapes-and-spi.md`, `shapes/mffd-ai-annotation.shacl.ttl`).
