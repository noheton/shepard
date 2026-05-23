---
title: PromptLog — prompts as first-class Shepard artefacts
stage: audited-by-personas
last-stage-change: 2026-05-23
audience: contributors, AI engineers, design reviewers
audited-by: aidocs/agent-findings/persona-audit-promptlog-2026-05-23.md (Roles 8/5/3/10; 4× ACCEPT-WITH-CHANGES; escalations ESC-1/2/3)
---

# 99 — PromptLog: prompts as first-class Shepard artefacts

**Status.** Design — ready for slice planning.
**Backlog row.** `aidocs/16-dispatcher-backlog.md` → **PROMPT1** (§FOCUS-MIG).
**Origin.** User idea (2026-05-23): *"view from prompt as storable shape
with promptlog substrate"* — promote prompts to first-class Shepard
artefacts.

**Couples to:**

- `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 15
  (TPL9, F(AI)²R verification ladder) — this design is the **substrate
  realisation** of `fair2r:Prompt` + `fair2r:Transcript`.
- `aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md`
  (S-02) — every PromptRun is *also* an OpenLineage RunEvent and a
  PROV-O Activity. The synergy delivers EU AI Act Article 50 evidence
  by composition.
- `aidocs/integrations/83-rebar-airflow-integration.md` —
  `shepard-plugin-mlops` receives OpenLineage; PromptLog receives
  prompt events from the same path.
- `project_block_editor.md` — every block carries the prompt that
  generated it; PromptLog is the storage backend for block
  attribution.
- `aidocs/platform/87-timeseries-appid-migration.md` + the active
  substrate-split arc — Neo4j stays identity-only; body lives in
  Postgres + Garage. Same discipline applies here.

**One-sentence claim:**

> **PromptLog is the substrate realisation of `fair2r:Prompt` +
> `fair2r:Transcript`** — promoting them from the "optional,
> plugin-contributed `:StructuredData` blob" that TPL9 currently
> describes into a first-class queryable payload kind with versioning,
> evaluation, chain lineage, and similarity search, on the substrate
> split this fork already commits to.

---

## §1 Reuse survey (mandatory first section)

`feedback_reuse_before_reimplement.md` is explicit: survey before
designing. This section is the survey, the trade-off, and the
recommendation. The recommendation is **ADOPT-the-pattern + BUILD the
thin semantic adapter** — explained below.

### 1.1 Landscape

| System | Licence | Activity | Prompt versioning | Eval | Substrate | Wire format | Notes |
|---|---|---|---|---|---|---|---|
| **Langfuse** | MIT | very active (1.5k+ GH stars/mo trajectory) | first-class (Git-style; deploy via labels) | yes (LLM-as-judge + custom) | Postgres (metadata) + ClickHouse (traces) + S3 (bodies) + Redis (queue) | own SDK, no OTel-native at receive | Closest-fit incumbent; OSS in full, self-hostable; ClickHouse is a heavy dep | [langfuse.com/handbook/product-engineering/architecture](https://langfuse.com/handbook/product-engineering/architecture) |
| **LangSmith** | proprietary | active | yes | yes | proprietary | own SDK | LangChain/LangGraph-coupled; not OSS — disqualified for adoption | [langsmith](https://www.langchain.com/langsmith) |
| **Helicone** | Apache-2.0 | active | minimal (prompt store via cloud only) | basic | Postgres + Clickhouse + S3 | proxy (`base_url` swap) | Cleanest install (proxy); shallower depth — call-level only | [helicone.ai](https://www.helicone.ai/) |
| **Arize Phoenix** | ELv2 (source-available) | very active | yes | strong (drift, embeddings) | OTel-native | OpenTelemetry GenAI | **OTel-first**; ELv2 is *not* OSI-approved; can't be "offered as a service" | [phoenix.arize.com](https://phoenix.arize.com/) |
| **OpenLLMetry (Traceloop)** | Apache-2.0 | active | no (logging only) | no | OTel collector | OpenTelemetry GenAI semantic conventions (drives the spec) | The wire-format SSOT — *not* a UI/registry product | [traceloop/openllmetry](https://github.com/traceloop/openllmetry) |
| **MLflow Prompt Registry** | Apache-2.0 | active (Databricks-backed) | strong (immutable commits, diff) | yes (LLM judges + custom metrics) | MLflow tracking store (sqlite / Postgres / file backends) | MLflow REST | Closest model-of-prompts to a Git-style VCS; ML-pipeline-centric | [mlflow.org/docs/latest/genai/prompt-registry/](https://mlflow.org/docs/latest/genai/prompt-registry/) |
| **OpenLineage RunEvent** | Apache-2.0 | active (LF AI&Data) | no (job-level only) | no | event-bus + Marquez | OpenLineage RunEvent spec | Already targeted by S-02; prompt-level granularity is *out of scope*, RunEvent is the *outer* envelope | [openlineage.io](https://openlineage.io/docs/spec/run-cycle/) |
| **promptfoo** | MIT (Anthropic + OpenAI both use) | very active | yes (declarative YAML) | strong (red-team, comparison) | local YAML / SQLite | own CLI format | CLI/CI-first; *not* a server — disqualified as substrate, but **adopt as eval test harness** | [promptfoo/promptfoo](https://github.com/promptfoo/promptfoo) |

### 1.2 What the literature says

The academic ground is well-tilled:

- **Procko et al. (2024), *Prompt Provenance: Toward Traceable LLM
  Interactions*** — proposes the *Prompt Provenance Model* (PPM)
  extending W3C PROV to treat prompts as first-class entities with
  defined relations to user intent, retrieval sources, system
  messages, and generated artefacts.
  [SSRN 5682942](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=5682942)

- **Souza et al. (2025), *PROV-AGENT: Unified Provenance for Tracking
  AI Agent Interactions in Agentic Workflows*** (arXiv 2508.02866) —
  extends W3C PROV with agent-, LLM-, and tool-call subclasses.
  Prompts fill `prov:used`; LLM responses fill `prov:generated`. ORNL
  authorship; DoE-funded — directly applicable to DLR's regulated
  research context.
  [arxiv.org/abs/2508.02866](https://arxiv.org/abs/2508.02866)

- **Pina et al. (2019), *Provenance Data in the Machine Learning
  Lifecycle in Computational Science and Engineering*** (arXiv
  1910.04223) — PROV-ML demonstrates PROV-O is sufficient for the
  ML-pipeline case; no new ontology is required for the prompt /
  agent split.

The pattern is **converged**: PROV-O is the right upper ontology;
prompts are `prov:Entity` (with a sub-type for the Plan-shape);
invocations are `prov:Activity`; the LLM is `prov:SoftwareAgent`.
Shepard's TPL9 design already commits to exactly this vocabulary
(`fair2r:Prompt ⊑ prov:Plan`, `fair2r:Transcript ⊑ prov:Entity`,
`fair2r:AIAgent ⊑ prov:SoftwareAgent`). **No new vocabulary is
needed**; this design only adds the substrate realisation.

### 1.3 The recommendation

**ADOPT** the OTel + Langfuse + PROV-O *pattern* — the substrate
split, the wire format, the ontology. **BUILD** the thin Shepard
semantic adapter that bridges them to the existing PROV-O graph and
F(AI)²R vocabulary.

Concretely:

- **Wire format: ADOPT OpenTelemetry GenAI semantic conventions**
  (v1.38.0+). Use `gen_ai.input.messages`, `gen_ai.output.messages`,
  `gen_ai.system_instructions`, `gen_ai.provider.name`,
  `gen_ai.request.model`. *Don't invent a Shepard prompt wire format.*
  This makes every existing OTel-instrumented client (OpenLLMetry,
  Phoenix instrumentations, vendor SDKs) auto-emit Shepard-compatible
  data.

- **Storage pattern: ADOPT Langfuse's substrate split** — metadata in
  Postgres, body in object store, embeddings in a vector column —
  *but realised on the substrates Shepard already runs* (Postgres +
  Garage + pgvector), NOT Langfuse's stack (Postgres + ClickHouse +
  S3 + Redis). ClickHouse is deferred (see §4); Redis is deferred
  (Shepard's existing queue infra suffices).

- **Ontology: ADOPT F(AI)²R + PROV-O + m4i** — the vocabulary is
  already pre-seeded by TPL9a + N1c2. PromptLog *consumes* it; does
  not redefine it.

- **Eval harness: ADOPT promptfoo** — the eval-runner CLI lands as a
  sidecar in `shepard-plugin-promptlog` for declarative eval suites.

- **BUILD only:** the thin Shepard semantic adapter (Neo4j identity
  node `:PromptRun` + Postgres body + `POST /v2/promptlog/runs`
  endpoint + PROV-O typed-Activity write at receive time). Roughly
  ~12–15 dev-days net (see §10).

#### Reasoning

The honest argument *against* "just deploy Langfuse alongside" is
that **only Shepard sees the PROV-O graph that already contains the
DataObjects the prompt produced**. A Langfuse trace is opaque to
Shepard's `:Activity`/`:DataObject` nodes; a Shepard PromptRun joins
the prompt to the DataObject the LLM created in one query. That join
is the value the literature attributes to PROV-O-rooted provenance
(Souza et al. 2025; Procko et al. 2024) and that Langfuse
*structurally cannot deliver* — it's a downstream observability
product, not a research-data substrate.

The argument *for* not building from scratch is overwhelming:
substrate decisions (Postgres + object store + pgvector), wire
format (OTel GenAI), and ontology (PROV-O + F(AI)²R) are all settled.
Shepard's contribution is *the join*, not *another logger*.

#### What would change my mind

If `shepard-plugin-mlops` ships first and adds a Langfuse
compatibility layer downstream (Langfuse-as-export-target rather than
Langfuse-as-substrate), the BUILD scope shrinks further — the
PromptLog substrate becomes "Langfuse-shape Postgres tables in
Shepard's DB, ingested via the OL receiver." That collapses
PROMPT-a + PROMPT-b. Worth re-evaluating after PROMPT-a lands.

**Lens citation:** API Scrutinizer (no new wire format), Reluctant
Senior (no new infra — uses what's there), Analytics & AI (uses the
existing pgvector substrate the team already runs).

---

## §2 The core question — what does it mean to make a prompt a Shepard payload kind?

### 2.1 Why prompts shouldn't live in `attributes`

The reductio answer is: "stash the prompt JSON in
`DataObject.attributes['ai_prompt']`." Three things break:

1. **No versioning.** Every prompt edit overwrites silently. The EU
   AI Act Article 50 evidence pack requires the prompt that was
   *actually used* — not the most recent rewrite. *Lens: RDM (FAIR
   reusability)*.

2. **No queryability across DataObjects.** "Show me every artefact
   generated by prompts mentioning *anomaly detection*" requires a
   full-table scan across attribute blobs. *Lens: Analytics & AI*.

3. **No graph identity.** A prompt is *used by* multiple Activities
   over its lifetime (re-runs, re-evaluations, fine-tuning data
   curation). If it's a string buried in an attribute, every reuse is
   invisible. PROV-O's whole point is that an Entity has identity.
   *Lens: Data Ontologist*.

The right answer is the same answer Shepard gave for timeseries,
files, structured data: **promote it to a payload kind, with its own
substrate, identity, and SHACL shape**.

### 2.2 Where the substrate pays off

| Scenario | Attributes-only cost | PromptLog substrate cost |
|---|---|---|
| Find all artefacts generated by a specific model version | full attribute scan, ~O(n) DataObjects | indexed Postgres query on `model` column |
| Reproduce an LLM call exactly | un-versioned, possibly impossible | replay PromptRun by appId — exact params + body |
| Show the prompt that generated a block in the editor | string look-up on the block's attributes | foreign-key join — single row |
| EU AI Act Article 50 audit: "list every AI-generated artefact in collection X with the prompt that produced it" | NOT POSSIBLE without re-ingest | one SQL JOIN across `:Activity` × PromptRun |
| Similarity search: "find prompts like *this one* that succeeded" | NOT POSSIBLE | pgvector cosine query on `embedding` column |
| Cost / token accounting per Collection | NOT POSSIBLE | sum aggregation on PromptRun columns |

### 2.3 Trade-off across personas

- **API Scrutinizer:** "another payload kind" is more API surface —
  N+1 endpoints, new docs, new client SDK methods. Counter-argument:
  the alternative is *attribute blobs that no caller can query
  consistently* — strictly worse for callers downstream.
- **Reluctant Senior:** "I don't use AI. Why do I pay for this?"
  Counter-argument: PromptLog is feature-flagged per Collection (per
  TPL2c gate). Senior researchers see zero UI surface until they
  opt-in or until F(AI)²R-tagged data enters their Collection.
- **Analytics & AI:** strongly in favour — every existing pain point
  (which prompt produced this annotation? what was the cost of
  collection X?) becomes a query.
- **Data Ontologist:** strongly in favour — closes the
  "no-parentless-claim invariant" of TPL9 with a real entity, not a
  string.
- **RDM:** strongly in favour — FAIR reusability mandates this
  level of provenance for AI-touched artefacts.

**Opposing-lens paragraph:** Reluctant Senior pushes back loudest.
The mitigation is *invisibility unless used*: no UI badges, no
extra columns, no behaviour change on Collections that never see an
AI interaction. The PromptLog substrate exists; if no one writes to
it, no one sees it. This is the same posture Shepard takes on
semantic annotations today (N1b shipped; admin must enable per
ontology).

### 2.4 What would change my mind

If TPL9 ships and the F(AI)²R `:Activity` chain captures enough that
no downstream caller needs *the prompt itself* (just "this came from
Claude Opus 4.7 at time T"), then PromptLog is over-built. Test:
after TPL9b ships, ask three real callers (block-editor, MCP
auto-annotation plugin, REBAR mlops) "do you need the prompt
*content* or just the agent + verification state?" If all three say
"agent + state is enough," PromptLog scope shrinks to a hash-only
mode and the body store goes away.

---

## §3 Data model — prompt as a typed Shepard individual

### 3.1 Vocabulary alignment (no new ontology)

PromptLog *consumes* the existing TPL9 + PROV-AGENT vocabulary. No
new RDF classes are coined here.

| Existing class | Source | Role in PromptLog |
|---|---|---|
| `fair2r:Prompt ⊑ prov:Plan` | aidocs/95 Part 15 | A *template* — the prompt text + system prompt + parameters that drove one or more LLM calls. Versioned. |
| `fair2r:Transcript ⊑ prov:Entity` | aidocs/95 Part 15 | A *recording* — the multi-turn conversation log of one user session with the LLM. |
| `fair2r:AuthoringPass ⊑ prov:Activity` | aidocs/95 Part 15 | The LLM invocation Activity itself. |
| `fair2r:AIAgent ⊑ prov:SoftwareAgent` | aidocs/95 Part 15 | The LLM as agent (one named individual per model version). |
| `fair2r:Claim ⊑ prov:Entity` | aidocs/95 Part 15 | What the LLM generated (an annotation, a block, a structured-data doc). |
| `m4i:realizesMethod` | metadata4ing (aidocs/94) | Links a PromptRun to the *method* it executes (anomaly detection, summarisation, …). |

### 3.2 New SHACL shapes (refinements, not additions)

Four SHACL shapes operate over the existing classes. They're
**templates** in the TPL1 sense — agent contracts (TPL1d) and view
recipes (TPL2). Shape pattern follows aidocs/95 Part 1.

#### `shp:PromptTemplateShape` (the *Plan* — what `fair2r:Prompt` is)

```turtle
shp:PromptTemplateShape
  sh:targetClass fair2r:Prompt ;
  sh:property [ sh:path schema:name              ; sh:datatype xsd:string ; sh:minCount 1 ; sh:order 1 ] ;
  sh:property [ sh:path shp:promptText           ; sh:datatype xsd:string ; sh:minCount 1 ; sh:order 2 ] ;
  sh:property [ sh:path shp:systemPrompt         ; sh:datatype xsd:string ; sh:order 3 ] ;
  sh:property [ sh:path shp:templateVersion      ; sh:datatype xsd:string ; sh:minCount 1 ; sh:order 4 ] ;
  sh:property [ sh:path shp:templateHash         ; sh:datatype xsd:string ; sh:minCount 1 ; sh:order 5 ] ;  # sha256
  sh:property [ sh:path m4i:realizesMethod       ; sh:nodeKind sh:IRI    ; sh:order 6 ] ;
  sh:property [ sh:path shp:parameterSchema      ; sh:datatype xsd:string ; sh:order 7 ] .  # JSON-Schema for variables
```

#### `shp:PromptRunShape` (the *Activity* — one LLM invocation)

```turtle
shp:PromptRunShape
  sh:targetClass fair2r:AuthoringPass ;
  sh:property [ sh:path prov:used                ; sh:class fair2r:Prompt   ; sh:minCount 1 ; sh:order 1 ] ;
  sh:property [ sh:path prov:wasAssociatedWith   ; sh:class fair2r:AIAgent  ; sh:minCount 1 ; sh:order 2 ] ;
  sh:property [ sh:path shp:modelId              ; sh:datatype xsd:string   ; sh:minCount 1 ; sh:order 3 ] ;
  sh:property [ sh:path shp:provider             ; sh:datatype xsd:string   ; sh:minCount 1 ; sh:order 4 ] ;  # openai|anthropic|saia-gwdg|…
  sh:property [ sh:path shp:temperature          ; sh:datatype xsd:float    ; sh:order 5 ] ;
  sh:property [ sh:path shp:maxTokens            ; sh:datatype xsd:integer  ; sh:order 6 ] ;
  sh:property [ sh:path shp:inputMessages        ; sh:datatype xsd:string   ; sh:order 7 ] ;  # JSON, OTel gen_ai.input.messages shape
  sh:property [ sh:path shp:outputMessages       ; sh:datatype xsd:string   ; sh:order 8 ] ;  # JSON, OTel gen_ai.output.messages shape
  sh:property [ sh:path shp:inputTokens          ; sh:datatype xsd:integer  ; sh:order 9 ] ;
  sh:property [ sh:path shp:outputTokens         ; sh:datatype xsd:integer  ; sh:order 10 ] ;
  sh:property [ sh:path shp:costCents            ; sh:datatype xsd:decimal  ; sh:order 11 ] ;
  sh:property [ sh:path prov:startedAtTime       ; sh:datatype xsd:dateTime ; sh:minCount 1 ; sh:order 12 ] ;
  sh:property [ sh:path prov:endedAtTime         ; sh:datatype xsd:dateTime ; sh:order 13 ] ;
  sh:property [ sh:path prov:wasInformedBy       ; sh:class fair2r:AuthoringPass ; sh:order 14 ] ;  # parent run (chain link)
  sh:property [ sh:path prov:generated           ; sh:nodeKind sh:IRI       ; sh:order 15 ] ;     # the DataObject / Claim it produced
  sh:property [ sh:path fair2r:claimStatus       ; sh:in (fair2r:unverified fair2r:ai-confirmed fair2r:human-confirmed) ; sh:order 16 ] .
```

#### `shp:PromptChainShape` (a *multi-step DAG* — one user session)

```turtle
shp:PromptChainShape
  sh:targetClass fair2r:Transcript ;
  sh:property [ sh:path schema:name              ; sh:datatype xsd:string  ; sh:minCount 1 ] ;
  sh:property [ sh:path shp:hasRun               ; sh:class fair2r:AuthoringPass ; sh:minCount 1 ] ;
  sh:property [ sh:path prov:wasAssociatedWith   ; sh:class fair2r:HumanResearcher ; sh:minCount 1 ] ;
  sh:property [ sh:path shp:sessionStartedAt     ; sh:datatype xsd:dateTime ; sh:minCount 1 ] ;
  sh:property [ sh:path shp:totalCostCents       ; sh:datatype xsd:decimal ] .
```

#### `shp:PromptEvaluationShape` (score + notes — promptfoo-shaped)

```turtle
shp:PromptEvaluationShape
  sh:targetClass shp:PromptEvaluation ;
  sh:property [ sh:path shp:evaluatesRun         ; sh:class fair2r:AuthoringPass ; sh:minCount 1 ] ;
  sh:property [ sh:path shp:metricName           ; sh:datatype xsd:string  ; sh:minCount 1 ] ;  # exact-match|llm-judge|cosine|…
  sh:property [ sh:path shp:metricScore          ; sh:datatype xsd:decimal ; sh:minCount 1 ] ;
  sh:property [ sh:path shp:judgeAgent           ; sh:class fair2r:AIAgent ] ;
  sh:property [ sh:path prov:wasGeneratedBy      ; sh:class prov:Activity  ; sh:minCount 1 ] .
```

### 3.3 Field-by-field rationale

The Run shape is the load-bearing one. Each field maps directly to a
named source — *no field is invented from scratch*:

| Field | Source | Why we need it |
|---|---|---|
| `modelId`, `provider` | OTel `gen_ai.request.model`, `gen_ai.provider.name` | Reproducibility — exact agent identity |
| `temperature`, `maxTokens` | OTel `gen_ai.request.*` | Same — non-deterministic params must be recorded |
| `inputMessages`, `outputMessages` | OTel `gen_ai.input.messages`, `gen_ai.output.messages` (v1.38+) | The actual content — opt-in per OTel spec due to size/privacy |
| `inputTokens`, `outputTokens` | OTel `gen_ai.usage.input_tokens` / `output_tokens` | Cost accounting, capacity planning |
| `costCents` | derived | Operator-facing — answers "what did this Collection cost in AI fees?" |
| `wasInformedBy` | PROV-O native | Chain link — multi-turn conversations |
| `generated` | PROV-O native | The artefact produced (DataObject, Claim, Block) |
| `claimStatus` | F(AI)²R | The verification ladder — TPL9f closes the loop |

**Lens citation:** API Scrutinizer (no field is decorative;
everything has a named caller). Data Ontologist (vocabulary is
adopted from PROV-O + F(AI)²R + OTel — three external standards, no
DLR-original ontology).

---

## §4 Substrate decision — where the bits live

`feedback_db_review_all_stores.md` + `feedback_shacl_single_source_of_truth.md`
+ the active substrate-split arc say: **Neo4j carries identity only;
domain data lives in the substrate best suited to its access
pattern.** Apply that discipline.

| Layer | Substrate | What lives there | Why |
|---|---|---|---|
| Identity | **Neo4j** | `:PromptRun` node with `appId` (shepardId) only; edges to `:Activity` / `:DataObject` / `:AIAgent` | Graph traversal — the join from DataObject to its generating prompt is a 1-hop edge |
| Metadata + params | **Postgres** | full row: `model`, `provider`, `temperature`, `cost`, `started_at`, `claim_status`, `evaluation_score` | Indexed columnar filters (date range, model, score) — Neo4j is wrong here |
| Body | **Garage (S3)** | `input_messages.json`, `output_messages.json`, full transcript | Variable-size blobs; cheap-by-default storage; same pattern as FileBundle |
| Embedding | **pgvector (Postgres extension)** | 1536-dim or 3072-dim embedding of the prompt text | Similarity search — "find prompts like this one." pgvector is already in stack (no new sidecar). |
| Ontology mirror | **N10s / SHACL graph** | TTL projection of the same shape for SPARQL-side queries | TPL4 dual-write — SPARQL is the truth substrate for ontology-level queries per `feedback_shacl_single_source_of_truth.md` |

### 4.1 Why not ClickHouse?

Langfuse moved trace storage from Postgres to ClickHouse in v3
because their *write throughput* (10k+ events/sec on a single
production instance) exceeds Postgres practical limits. Shepard's
expected throughput is 2–4 orders of magnitude lower (research
workload, not consumer SaaS):

- LUMEN-scale (~15 test runs × few AI annotations each = ~50 prompts
  for an entire campaign)
- MFFD-scale (~12 process steps × low-hundreds of AI annotations
  across project lifetime)
- MCP-driven exploration (~10s of prompts per researcher per day)

A single Postgres instance handles 10–100 writes/sec without strain.
ClickHouse is **deferred**, with a clean upgrade path: the schema
columns ClickHouse would index are the same Postgres columns,
documented in §10's PROMPT-d "scale" slice.

**Counter-evidence:** if Shepard adds a streaming MQTT ingest path
that auto-annotates every sensor reading with an LLM call (no plan to
do this — but the home-showcase MQTT pattern shows it's *possible*),
the throughput jumps by 3 orders of magnitude. The plugin design
keeps Clickhouse a runtime-pluggable substrate option per
`feedback_plugins_declare_sidecars.md`.

### 4.2 Why not MongoDB?

The body is *immutable once written* (per the no-mutation principle
in `feedback_mutate_after_snapshot.md`) and accessed by appId. That
fits S3-shaped object storage perfectly; Mongo's query layer is
unused. Garage (which Shepard adopted in ADR-0024 for S3 workloads
per memory `project_storage_s3_garage.md`) is the right call. No
new substrate.

### 4.3 The block-editor case (forward reference to §6)

If every block-edit becomes a PromptRun (the maximalist case), the
write rate scales with block-edit rate, not test-run rate. Single
researcher editing actively: ~1 prompt/min in busy moments. Still
trivially Postgres-shaped. Postgres + Garage stays correct.

**Lens citation:** Reluctant Senior (no new infra to learn — the same
substrates as every other Shepard payload kind). Analytics & AI (the
substrate split *enables* the pgvector similarity search the senior
doesn't yet know they want).

#### What would change my mind

A real measurement — not a guess — that Postgres can't keep up.
PROMPT-d slice (§10) is a load test. If 1k writes/sec on a single
Postgres instance fails for an actual Shepard caller, *then* declare
ClickHouse as a plugin sidecar. Until then, YAGNI.

---

## §5 REST surface

Endpoints under `/v2/promptlog/`. All shaped per A0/H4 (RFC 7807 errors,
`@RolesAllowed` enforcement, pagination envelope). Pairs with
`feedback_ui_api_parity.md` — each endpoint names the UI page it
serves.

| Method + Path | Purpose | UI page | RFC 7807 codes |
|---|---|---|---|
| `POST /v2/promptlog/runs` | Record one LLM invocation. Body: OTel GenAI shape (`gen_ai.input.messages`, …). Returns appId + 201. | (none — receiver) | `INVALID_GEN_AI_SHAPE` |
| `POST /v2/promptlog/chains` | Record a multi-step chain (one transcript). Pairs with S-02 (the OpenLineage RunEvent receiver writes here). | (none — receiver) | `CHAIN_RUN_NOT_FOUND` |
| `POST /v2/promptlog/templates` | Register / version a `fair2r:Prompt` (Plan). Returns immutable version-id. | `pages/promptlog/templates/index.vue` (admin: list + edit) | `TEMPLATE_HASH_CONFLICT` |
| `POST /v2/promptlog/evaluations` | Score a run (manual or via promptfoo CLI bridge). | `pages/promptlog/runs/[id]/index.vue` (Eval tab) | — |
| `GET /v2/promptlog/runs/{appId}` | Fetch one run incl. body resolution from Garage. | `pages/promptlog/runs/[id]/index.vue` | — |
| `GET /v2/promptlog/runs` | List with filters: `model`, `provider`, `agent_id`, `started_after`, `started_before`, `min_score`, `template_id`, `claim_status`. Paginated. | `pages/promptlog/runs/index.vue` (the discovery surface) | — |
| `GET /v2/promptlog/runs/{appId}/lineage` | Predecessor + successor PromptRuns across the chain (PROV `wasInformedBy`). | `pages/promptlog/runs/[id]/lineage.vue` (chain DAG view) | — |
| `GET /v2/promptlog/runs/{appId}/similar?k=10` | Embedding-similarity search via pgvector. | `pages/promptlog/runs/[id]/similar.vue` (rec-style sidebar) | — |
| ~~`GET /v2/promptlog/templates/{appId}/runs`~~ | **DROPPED 2026-05-23** per persona-audit ESCALATION-PROMPT-3 (API Scrutinizer): redundant with `GET /v2/promptlog/runs?template_id=…`. UI binding `pages/promptlog/templates/[id]/runs.vue` calls the canonical filter query instead. | — | — |
| ~~`GET /v2/promptlog/agents/{appId}/runs`~~ | **DROPPED 2026-05-23** per persona-audit ESCALATION-PROMPT-3: redundant with `GET /v2/promptlog/runs?agent_id=…`. UI binding `pages/promptlog/agents/[id]/runs.vue` calls the canonical filter query instead. | — | — |

### 5.1 Argument for the surface size

API Scrutinizer pushback: ten endpoints is a lot. Counter-argument:

- Three of them (`POST runs`, `POST chains`, `POST evaluations`) are
  *receivers* — auto-populated by the OL receiver + the MCP filter +
  the promptfoo sidecar. No human types these.
- Four of them (`runs/{id}`, `runs/`, `runs/{id}/lineage`,
  `runs/{id}/similar`) are the **basic-mode** discovery surface — what
  the researcher actually clicks.
- Three of them (`templates`, `templates/{id}/runs`, `agents/{id}/runs`)
  are the **advanced/admin** views (per `feedback_basic_advanced_superset.md`).

This is fewer endpoints than `v2/data-objects/` (15) or
`v2/timeseries/` (~20).

### 5.2 What would change my mind

If the similarity endpoint sees < 10 calls/month over the first 6
months in production, demote it to a SPARQL canned-query (it lives in
N1f's SPARQL UI per TPL9h). One fewer route, no UI page.

**Lens citation:** API Scrutinizer (count and shape).

---

## §6 The block-editor connection

`project_block_editor.md` already declares: *"every block authorship +
edit is a `fair2r:AuthoringPass` Activity."* PromptLog is the storage
backend for that Activity.

### 6.1 Wire shape

A block in the editor carries a single field:

```jsonc
{
  "blockId": "01923d70-...",
  "kind": "shepard:TextBlock",
  "content": "...",
  "attributedTo": "shepard:user:fkrebs",
  "generatedBy": {            // optional — only if AI-touched
    "promptRunAppId": "0192fd02-7000-...",
    "agent": "agent:claude-opus-4-7",
    "claimStatus": "unverified"
  }
}
```

When the block is rendered, the UI looks up `promptRunAppId` via
`GET /v2/promptlog/runs/{appId}` (cached per session) to display the
F(AI)²R badge + "Show prompt" affordance.

### 6.2 The chain-vs-run reference — RESOLVED 2026-05-23

aidocs/95 Part 15 + `project_block_editor.md` didn't resolve: when a
multi-turn conversation produces one block, does the block reference
*the single PromptRun that produced the final text* (the leaf of the
chain) or *the whole PromptChain* (the entire transcript)?

**RESOLVED 2026-05-23** (user OK on ESCALATION-PROMPT-1 from
persona-audit-promptlog-2026-05-23.md): the block carries **both**:

```typescript
interface BlockPromptRef {
  promptRunAppId: string;             // the leaf — REQUIRED
  promptChainAppId: string | null;    // the whole transcript — null for single-turn
}
```

Costs ~80 bytes per block on the wire; saves the forever-cognitive-cost
("is the chain reachable from here without an extra round-trip?")
that all four personas (Analytics & AI + RDM + API Scrutinizer +
Digital Native) preferred dual over leaf-only.

For single-turn invocations (the common case for `shepard.summarise`
or `shepard.suggest_attributes`), `promptChainAppId = null` and the
shapes degenerate to leaf-only at the wire level. For multi-turn
chains (RAG + tool-use + re-prompts), both are populated.

Chain lookup is still served by `GET /v2/promptlog/runs/{appId}/lineage`
— the dual-id is for *direct reachability*, not for replacing the
lineage endpoint.

### 6.3 Cross-Shepard reference

The block is a `shepard:Block` (per project_block_editor.md), the
PromptRun is a `:PromptRun` Neo4j node. The reference is a
`prov:wasGeneratedBy` edge stored in Neo4j; the wire shape is the
`promptRunAppId` field. Same join pattern as DataObject →
DataObjectReference.

**Lens citation:** Data Ontologist (the block/prompt edge is
PROV-native; no new relation type needed).

---

## §7 OpenLineage × F(AI)²R synergy (S-02)

S-02 is the multiplier. Every PromptRun is *simultaneously*:

- an **OpenTelemetry GenAI** span on the wire (the SDK emits this),
- an **OpenLineage RunEvent** at the workflow boundary (Airflow / MLflow
  emits this when an LLM call sits inside a DAG),
- a **PROV-O `prov:Activity`** in Shepard's semantic store,
- a **F(AI)²R `:AuthoringPass`** with verification status.

The four-column mapping is the EU AI Act Article 50 evidence-pack
contract:

| PromptLog field | OTel GenAI attribute | OpenLineage RunEvent field | F(AI)²R / PROV-O term |
|---|---|---|---|
| `modelId` | `gen_ai.request.model` | `job.facets.llm.model` | `prov:wasAssociatedWith` → `fair2r:AIAgent` |
| `provider` | `gen_ai.provider.name` | `producer` URI | `fair2r:AIAgent` IRI prefix |
| `temperature`, `maxTokens` | `gen_ai.request.*` | `run.facets.parameters` | `prov:value` on the `fair2r:Prompt` parameter slot |
| `inputMessages` | `gen_ai.input.messages` | `inputs[].facets.llm_input` | `prov:used` → `fair2r:Prompt` |
| `outputMessages` | `gen_ai.output.messages` | `outputs[].facets.llm_output` | `prov:generated` → `fair2r:Claim` |
| `systemPrompt` | `gen_ai.system_instructions` | `job.facets.prompt.system` | property of `fair2r:Prompt` (the Plan) |
| `inputTokens`, `outputTokens` | `gen_ai.usage.input_tokens` / `output_tokens` | `run.facets.usage` | numeric properties of `fair2r:AuthoringPass` |
| `costCents` | (derived) | `run.facets.cost` | numeric property of `fair2r:AuthoringPass` |
| `startedAt`, `endedAt` | span `start_time` / `end_time` | `eventTime`, `runStart`/`runComplete` | `prov:startedAtTime`, `prov:endedAtTime` |
| `wasInformedBy` (parent run) | OTel `parent_span_id` | RunEvent `parentRunFacet` | `prov:wasInformedBy` |
| `claimStatus` | (Shepard extension) | (none) | `fair2r:claimStatus` |
| Generated DataObject IRI | (Shepard extension) | `outputs[]` | `prov:generated` |

### 7.1 What this delivers

Every existing OTel-instrumented client (Anthropic SDK, OpenAI SDK,
LangChain via OpenLLMetry, Phoenix instrumentations) auto-emits the
data PromptLog ingests. Every existing OpenLineage-emitting DAG
(Airflow + the receiver from `aidocs/integrations/83`) auto-records
the run-level envelope. No DAG author touches F(AI)²R.

The single endpoint that closes the EU AI Act Article 50 loop:

```sparql
PREFIX fair2r: <https://noheton.github.io/f-ai-r/ns#>
PREFIX prov:   <http://www.w3.org/ns/prov#>
PREFIX schema: <http://schema.org/>

SELECT ?artefact ?promptRun ?agent ?model ?status ?promptText
WHERE {
  ?artefact prov:wasGeneratedBy ?promptRun .
  ?promptRun a fair2r:AuthoringPass ;
             prov:wasAssociatedWith ?agent ;
             prov:used ?prompt ;
             fair2r:claimStatus ?status .
  ?agent fair2r:realizesModel ?model .
  ?prompt schema:text ?promptText .
  FILTER (?status != fair2r:human-confirmed)
}
```

This is the *exact* JSON-LD evidence pack an EASA Learning Assurance
auditor ingests (per `aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md`).

**Lens citation:** RDM (the FAIR-for-AI claim becomes operational).

---

## §8 Plugin design — `shepard-plugin-promptlog`

Per `CLAUDE.md` plugin-first heuristic: PromptLog is a payload kind →
**plugin from day one**. Argued explicitly:

- It's a *new payload kind* (item 1 in the plugin-first heuristic).
- The OTel + Langfuse + promptfoo dependency tree is heavy (eval
  harness, embedding model client, JSON-Schema validator) — keeping
  it out-of-tree shrinks the core's classpath.
- A second team will almost certainly want a variant (Langfuse-export
  plugin, custom-eval plugin) — the plugin shape gives them the seam.

### 8.1 Module layout

```
plugins/promptlog/
  ├── pom.xml
  ├── PromptLogPluginManifest.java        // ShepardPlugin SPI
  ├── shapes/
  │   ├── prompt-template.shacl.ttl
  │   ├── prompt-run.shacl.ttl
  │   ├── prompt-chain.shacl.ttl
  │   └── prompt-evaluation.shacl.ttl
  ├── docs/
  │   ├── reference.md
  │   ├── quickstart.md
  │   └── install.md
  ├── frontend/
  │   ├── components/PromptLogChainGraph.vue
  │   ├── components/PromptLogRunCard.vue
  │   └── pages/promptlog/*.vue
  └── src/main/java/de/dlr/shepard/plugin/promptlog/
      ├── rest/PromptLogRest.java          // /v2/promptlog/*
      ├── entities/PromptRun.java          // Neo4j @Node
      ├── pg/PromptRunRowMapper.java       // Postgres jsonb row
      ├── pg/V01__Create_promptrun.sql     // Flyway
      ├── embeddings/PromptEmbeddingService.java
      ├── eval/PromptfooSidecarBridge.java
      └── otel/OtelGenAiReceiver.java      // optional OTel collector endpoint
```

### 8.2 Manifest sidecars

Per `feedback_plugins_declare_sidecars.md` — the plugin declares its
infra:

```yaml
# plugins/promptlog/manifest.yml
id: shepard-plugin-promptlog
requires:
  - shepard-plugin-mlops      # for OL RunEvent receiver
  - postgres                  # uses Shepard's existing Postgres
  - garage                    # uses Shepard's existing Garage
sidecars:
  - name: pgvector
    kind: postgres-extension
    optional: false
    install: "CREATE EXTENSION IF NOT EXISTS vector;"
  - name: promptfoo
    kind: cli-tool
    optional: true
    image: ghcr.io/promptfoo/promptfoo:0.99
  - name: embedding-service
    kind: http-service
    optional: true
    image: ghcr.io/dlr-shepard/embedding-service:0.1
    env:
      - MODEL: sentence-transformers/all-MiniLM-L6-v2
```

Three sidecars, two of them optional. The embedding-service is
deferred (PROMPT-c) so the v1 release runs with only `pgvector`
turned on.

### 8.3 SPI hooks

Plugin contributes:

- A `PayloadKind` registration (id `promptlog/run`).
- A `OneLineageReceiver` adapter that classifies GenAI spans (per
  S-02's `ProducerClassifier`) and writes PromptRuns.
- An `AiActivityCapture` implementation (replaces the default
  `:Activity`-only implementation from TPL9 with one that *also*
  writes a PromptRun row).
- An admin config singleton `:PromptLogConfig` per the
  `aidocs/65` admin-configurable pattern (knobs: `body_retention_days`,
  `embedding_model`, `pii_redaction_mode`).

**Lens citation:** plugin-first heuristic (this is item 1 — new
payload kind = plugin from day one).

---

## §9 MCP integration

Per memory `project_mcp_path.md` + `aidocs/platform/30-mcp-plugin-design.md`,
every Shepard MCP tool invocation goes through the MCP gateway
(`shepard.nuclide.systems/mcp/sse`). The gateway runs
`McpAuthFilter` + `ProvenanceCaptureFilter` (PROV1a).

### 9.1 Integration sketch

`ProvenanceCaptureFilter` already emits a `:Activity` per request.
For MCP-tagged requests (`X-AI-Agent: <agent-iri>` header per TPL9b),
the filter does *one additional thing*:

```java
// shepard-plugin-promptlog :: McpProvenanceExtension
@Override
public void onMcpAuthenticated(McpInvocation inv, Activity activity) {
    if (inv.headers().get("X-AI-Agent") == null) return;  // not AI-tagged
    PromptRun run = PromptRun.builder()
        .appId(activity.appId())                  // SAME appId — they're the same Activity
        .agent(inv.headers().get("X-AI-Agent"))
        .promptHash(inv.headers().get("X-AI-Prompt-Hash"))
        .promptId(inv.headers().get("X-AI-Prompt-ID"))   // optional reference to fair2r:Prompt
        .sessionId(inv.headers().get("X-AI-Session-ID"))
        .toolName(inv.toolName())
        .inputMessages(inv.toolArgsAsOtelMessages())
        .outputMessages(inv.toolResultAsOtelMessages())
        .startedAt(activity.startedAt())
        .endedAt(activity.endedAt())
        .claimStatus(headerStatus(inv).orElse(fair2r.unverified))
        .build();
    promptLogService.record(run);                 // Postgres + Neo4j edge + Garage body
}
```

The MCP tool's caller (Claude Code, Claude Desktop, any
F(AI)²R-compliant agent) sets the X-AI-* headers as TPL9b already
specifies. PromptLog turns those headers into a queryable row.

### 9.2 What this delivers

**Every MCP tool call becomes auditable downstream.** A researcher's
SPARQL query "every annotation Claude added to my Collection this
week" is one join:

```sparql
SELECT ?annotation ?promptRun ?prompt
WHERE {
  ?annotation prov:wasGeneratedBy ?promptRun .
  ?promptRun  a fair2r:AuthoringPass ;
              prov:wasAssociatedWith agent:claude-opus-4-7 ;
              prov:used ?prompt ;
              prov:startedAtTime ?t .
  FILTER (?t > "2026-05-16T00:00:00Z"^^xsd:dateTime)
}
```

The "MCP black-box" problem (the user can't see what the agent did
on their behalf) goes away.

### 9.3 Edge case — MCP calls that don't reach an LLM

Some MCP tool calls are pure read-ops (e.g. `get_data_object`). They
don't represent an LLM invocation; they represent the *agent* doing
a read on behalf of a human. Per TPL9: these become
`fair2r:AuditPass` activities, NOT `AuthoringPass`. PromptLog still
records them (the `X-AI-*` headers still flow), but as a different
activity kind. Same shape, different sub-class.

**Lens citation:** Analytics & AI (the SPARQL query above is the
core agentic-audit demo). API Scrutinizer (zero new MCP endpoints —
this is pure infra under existing routes).

---

## §10 Implementation roadmap

Slices follow the TPL series pattern. Sub-row IDs land in
`aidocs/16-dispatcher-backlog.md` PROMPT1.

| Slice | Scope | Days | Depends on |
|---|---|---|---|
| **PROMPT-a** | Plugin scaffolding: `plugins/promptlog/` module, `:PromptRun` Neo4j entity, Postgres `prompt_run` table + Flyway V01, `POST /v2/promptlog/runs` + `GET /v2/promptlog/runs/{appId}` + paginated list. Shape TTLs. Subsumes **TPL9e** (replaces "plugin-contributed `:StructuredData` blob" with first-class storage). | 4 | TPL9b (X-AI-Agent header propagation in `ProvenanceCaptureFilter`); plugin-SPI infrastructure |
| **PROMPT-b** | OTel GenAI receiver + body storage in Garage: ingest spans matching `gen_ai.*` attrs; resolve body refs at fetch time. | 3 | PROMPT-a; `shepard-plugin-mlops` baseline (per aidocs/83 §1) |
| **PROMPT-c** | pgvector similarity: embedding-service sidecar + `GET .../{id}/similar?k=N` + frontend rec sidebar. | 3 | PROMPT-a |
| **PROMPT-d** | Chain support: `POST /v2/promptlog/chains` + `wasInformedBy` traversal + `GET .../{id}/lineage` + frontend DAG view (reuses `CollectionLineageGraph.vue` dagre-replacement pattern per memory `feedback_reuse_trusted_code.md`). | 4 | PROMPT-a |
| **PROMPT-e** | Evaluation endpoints + promptfoo sidecar bridge + frontend eval tab. | 3 | PROMPT-a |
| **PROMPT-f** | MCP `McpProvenanceExtension` — record every MCP tool call as a PromptRun. | 2 | PROMPT-a; `shepard-plugin-mcp` |
| **PROMPT-g** | Block-editor integration — `generatedBy` field in block JSON + UI lookup. | 2 | PROMPT-a; block-editor MVP |
| **PROMPT-h** | Admin config singleton + UI: `:PromptLogConfig` per aidocs/65, knobs for body retention / redaction mode / embedding model + `shepard-admin promptlog ...` CLI. | 3 | PROMPT-a |
| **PROMPT-i** | EU AI Act Article 50 evidence-pack export endpoint: `GET /v2/promptlog/evidence-pack?collection=...&format=jsonld\|datacite` — emits the JSON-LD shape an auditor ingests verbatim per §7. | 3 | PROMPT-a; the m4i export work in `aidocs/semantics/94 §M4I-d` |
| **PROMPT-j** | Load test — 1k writes/sec on single Postgres; if it fails, declare Clickhouse sidecar deferred-not-blocking. Result is a measurement, not a code change. | 2 | PROMPT-b |

**Total: ~29 days.** Critical path: a→b→i (gets EU AI Act Article 50
evidence pack live by Aug 2026 deadline). c, d, e, f, g, h, j run in
parallel after a lands.

### 10.1 PROMPT-a → PROMPT-i critical path

The deadline-shaped path is `a → b → i` (~10 days; ~2.5 calendar
weeks with one engineer; ~1.5 weeks with two). Article 50 takes
effect 2 August 2026 (per aidocs/95 §15.55). Today is 2026-05-23 —
plenty of headroom if PROMPT-a lands within the next 4 weeks.

**Lens citation:** Reluctant Senior (no slice forces a behaviour
change on Collections without AI use — every slice is additive).

---

## §11 Risks + counter-evidence

### Risk 1 — Storage explosion (the body problem)

Average LLM exchange: 2–10 KB input + 1–5 KB output = 5–15 KB body
per PromptRun. At 100k runs/month (very high estimate — much more
than current expected throughput), that's 1.5 GB/month of bodies.
Indefinite retention = ~18 GB/year. Garage handles this trivially.

**Counter-evidence:** the academic literature on RAG-heavy
agents (Souza et al. 2025 PROV-AGENT) reports per-task contexts of
50–200 KB when full RAG retrieval is bundled into the prompt. At
those sizes, 1M runs/month becomes 50–200 GB/month. Mitigation: the
admin-config knob `body_retention_days` (default: indefinite; ops can
set 90/180/365 days as policy). Also: bodies are content-addressed
by SHA256, so identical prompts are stored once.

### Risk 2 — PII in prompts × GDPR right-to-erasure

This is the live one. Researchers paste experimental data into
prompts; if that data contains personal identifiers (operator names,
patient codes, location data), GDPR Article 17 right-to-erasure
applies. But immutable provenance is *the* PROV-O invariant.
Conflict: irresistible force × immovable object.

**Mitigations** (defence in depth, per literature):

- **Default: hash-only mode for the body store.** The bodies are SHA256-
  hashed at receive time; the hash, not the body, is stored. Body is
  *only* persisted if `:PromptLogConfig.body_storage_mode = full`
  (admin opt-in). This is the OTel spec's recommendation (input /
  output messages are *opt-in* — see [OTel GenAI spans
  spec](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/)).
- **PII redaction at ingest** (per literature: "implement redaction
  at ingestion and retrieval, and log removals" —
  [predictionguard PII guide](https://predictionguard.com/blog/pii-detection-redaction-llm-pipelines-regulated-industries)).
  Plugin sidecar `pii-redactor` (deferred to PROMPT-k) inspects the
  body, redacts named-entity matches, and stores a `redaction_log`
  Activity per PROV-O.
- **Hard delete on erasure request.** PROV-O immutability applies to
  the *Activity* (the fact that the call happened) — not the body.
  Erasure deletes the body, replaces with a `prov:wasInvalidatedBy`
  Activity, keeps the run as evidence-of-call.

**Counter-evidence:** "Compliance theater" critique (per the
[Gravitee PII blog](https://www.gravitee.io/blog/how-to-prevent-pii-leaks-in-ai-systems-automated-data-redaction-for-llm-prompt)
+ [Kiteworks LLM data leakage guide](https://www.kiteworks.com/cybersecurity-risk-management/prevent-llm-data-leakage-controls/)) —
redaction systems are reversible via citations, logs, or jailbreak
prompts. Mitigation: red-team the *whole* system, not just the
redactor; the SHACL-shape-enforced "no body without admin opt-in"
default is the structural fix.

### Risk 3 — OTel spec churn

OpenTelemetry GenAI deprecated `gen_ai.prompt` and `gen_ai.completion`
in v1.38.0 (replaced by `gen_ai.input.messages` /
`gen_ai.output.messages`), per [OpenLLMetry issue 3515](https://github.com/traceloop/openllmetry/issues/3515).
If Shepard pins to a deprecated convention, day-1 ship is stale.

**Mitigation:** version-pin to v1.38.0+ in the receiver. Test fixture
runs both legacy and current conventions; the receiver accepts both
and normalises to the current. PROMPT-b acceptance criteria
explicitly include "ingest both v1.36 and v1.38+ shapes."

### Risk 4 — Helicone / LangSmith commercial OSS conflict

Helicone and LangSmith have commercial cloud offerings; their OSS
versions are deliberately less full-featured to drive cloud uptake.
If Shepard *adopts* their OSS code, the commercial vendor's roadmap
diverges and the OSS fork goes stale. (Real concern; happens to many
"OSS-core" projects.)

**Mitigation:** the recommendation in §1 is **adopt the *pattern*,
NOT the code**. Langfuse's substrate split (Postgres + object store +
analytics tier) is the *architecture* we're copying. We don't ship
Langfuse code in the plugin. Zero risk of upstream divergence.

### Risk 5 — Cost accounting drift

`costCents` requires per-model pricing tables that change weekly
(Anthropic, OpenAI, GWDG/SAIA all update). If Shepard hard-codes,
the cost field is wrong. If Shepard queries vendor APIs, that's a
runtime dep.

**Mitigation:** ship a `:PromptLogConfig.cost_table` admin field
(default: latest GWDG-SAIA + Anthropic snapshot at release time;
operators override per their org pricing). Same admin-config pattern
as `aidocs/65`.

**External citations for this section:**

- [OWASP LLM Top 10 (2025)](https://owasp.org/www-project-top-10-for-large-language-model-applications/) — risk LLM06 (sensitive information disclosure) directly motivates the hash-only default.
- [EU AI Act Article 50 service desk](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-50) — the obligation that drives this whole design.
- [Transparency as Architecture (arXiv 2603.26983)](https://arxiv.org/pdf/2603.26983) — the structural-compliance critique; pure provenance is not enough, UI surfacing matters (already covered by §6 + TPL9d).

---

## §12 Acceptance criteria

1. **Round-trip preservation.** A PromptRun POSTed via OTel GenAI shape
   is retrievable byte-identical (modulo hash if hash-only mode is on)
   via `GET .../{appId}`. Wire fidelity test in CI.
2. **PROV-O graph join works.** Given a DataObject `do`, the SPARQL
   query `SELECT ?prompt WHERE { ?do prov:wasGeneratedBy/prov:used ?prompt }`
   returns the prompt text in one query. Acceptance test against a
   seeded MFFD dataset.
3. **EU AI Act Article 50 evidence pack endpoint emits JSON-LD that
   validates against the F(AI)²R shape.** Conformance test against the
   F(AI)²R reference repo (`github.com/noheton/f-ai-r`).
4. **MCP tool call auto-records a PromptRun.** Run `claude-code` against
   the deployed MCP endpoint; verify a row appears in `prompt_run`
   keyed by the same appId as the captured `:Activity`.
5. **No behaviour change on Collections without AI use.** Reluctant
   Senior test: import a non-AI Collection (LUMEN raw seed), navigate
   every page, observe no PromptLog UI elements rendered.

---

## §13 Open questions

- ~~**OQ-1 (§6).** Block-editor: does each block carry a single
  `promptRunAppId` (leaf-of-chain) or the whole `promptChainAppId`?~~
  **RESOLVED 2026-05-23** (user OK on ESCALATION-PROMPT-1): block carries
  BOTH (`promptRunAppId` required + `promptChainAppId|null`). See §6.2
  for the resolution. PROMPT-c may proceed.
- **OQ-2 (§4).** When does ClickHouse become non-deferrable? Concrete
  threshold (writes/sec? table cardinality?) so the trigger is
  measurable, not subjective.
- **OQ-3 (§8).** Embedding-service sidecar: ship our own (controlled,
  GPU-light, MiniLM-L6) or call out to GWDG/SAIA's hosted embedding
  endpoint (no GPU, but per-call cost + tenant)? Memory
  `project_ai_plugin_config.md` says SAIA is the recommended DLR
  provider — likely lean that direction.
- ~~**OQ-4 (§11).** PII redaction: ship as a default-on sidecar or a
  default-off opt-in?~~ **RESOLVED 2026-05-23** (user decision per
  persona-audit-promptlog-2026-05-23.md ESCALATION-PROMPT-2 CRITICAL):
  ship a **per-Collection `:Collection.promptLogMode` field** with three
  modes — `hash-only` (default for new Collections; safe baseline +
  GDPR-friendly), `body-redacted` (analytics-grade — PII-redaction
  sidecar runs at ingest, redacted body stored), `body-raw` (air-gapped
  or internal-only Collections where full body retention is needed for
  EU AI Act Art-53 GPAI documentation). Matches TPL2c per-Collection
  feature-flag pattern. Resolves the `§11 hash-only default × redaction
  default` coherence issue surfaced by the RDM persona audit. New
  sub-row **PROMPT-h2** filed: per-Collection mode field on
  `:Collection` schema + Vuetify selector. Migration: existing
  Collections default to `hash-only`; admin can flip per Collection.
- **OQ-5 (§5).** Should `/v2/promptlog/evidence-pack` be on the
  Article 50 deadline path, or can it ship as a follow-up to PROMPT-i?
  Argument for follow-up: TPL9h already proposes the SPARQL canned
  queries; the evidence-pack endpoint is just a curated SPARQL
  wrapper.
- **OQ-6 (§3).** Should `m4i:realizesMethod` be required (sh:minCount
  1) or optional on the PromptTemplate shape? Required = stronger
  EASA evidence; optional = lower friction for ad-hoc prompts.
- **OQ-7 (§9).** `fair2r:AuditPass` for read-MCP calls — store the
  *result* of the read? Or just record "agent X read DataObject Y"?
  Storing the result re-introduces the body-storage cost question.

---

## §14 Cross-cutting synergies (per `feedback_synergy_agent_standing.md`)

- **S-02 (`synergy-2026-05-23-openlineage-fair2r.md`).** PromptLog is
  the substrate that *materialises* the synergy — without PromptLog,
  S-02 is a classification rule over `:Activity` nodes; with
  PromptLog, the prompt body itself is in the graph.
- **TPL9 (aidocs/95 Part 15).** PromptLog *replaces* the "plugin-
  contributed `:StructuredData` document" in TPL9's prompt-storage
  row. The TPL9e slice becomes "subsumed by PROMPT-a." (Land the
  rename in the same PR that creates PROMPT1 sub-rows.)
- **M4I-b (aidocs/semantics/94 §6).** PromptLog's
  `m4i:realizesMethod` predicate uses the same vocabulary
  metadata4ing fixes in M4I-b. The two slices share the m4i pre-seed.
- **AI1c (`shepard-plugin-ai`).** When the AI plugin
  auto-annotates a DataObject, it writes a PromptRun. PromptLog is
  the storage backend; AI1c is the caller.
- **AI1e (snap-dashboards).** Stores the prompt history of the
  agent that produced a dashboard snapshot — moves from per-snapshot
  `:StructuredData` to a real PromptRun.
- **MCP30 (`shepard-plugin-mcp`).** Every MCP call writes a
  PromptRun (§9). The MCP plugin and PromptLog are
  bi-directionally synergistic — MCP feeds PromptLog; PromptLog
  enables `every MCP call is auditable` claim.
- **REBAR/MLOPS 83.** OpenLineage receiver pipes prompt-bearing
  RunEvents to PromptLog (§7). The PROMPT-b slice has the mlops
  baseline as an explicit dependency.
- **G1 (Git integration).** Prompt templates are versioned text —
  they can ride the same git-ingest pipeline TPL5 proposes for
  ontologies. *Future:* `git pull` a prompt-template repo, get
  versioned `fair2r:Prompt` individuals for free.
- **N1f (SPARQL UI).** The Article 50 evidence-pack SPARQL is one
  canned query in the SPARQL UI — same surface as the
  TPL9h queries.
- **block-editor.** Direct dependency for PROMPT-g.

The synergy multiplier (memory `feedback_synergy_agent_standing.md`
SPI): rows that compound with PROMPT1 = **9** (S-02, TPL9, M4I-b,
AI1c, AI1e, MCP30, REBAR/MLOPS83, G1/TPL5, N1f, block-editor — for a
backlog row the user proposed as a standalone idea, this is
high-leverage).

---

## §15 Doc-trio commitment

Per `CLAUDE.md` plugin-doc rule — the plugin's three documentation
artefacts land with PROMPT-a:

- `plugins/promptlog/docs/reference.md` — endpoint catalogue + shape
  catalogue + `:PromptLogConfig` field reference.
- `plugins/promptlog/docs/quickstart.md` — "Submit your first
  PromptRun in 2 minutes" + "Find the prompt that generated this
  annotation" worked example.
- `plugins/promptlog/docs/install.md` — Postgres + pgvector +
  Garage prerequisites + the optional sidecars.

---

## §16 What this design does NOT do

To respect scope discipline:

- It does not redefine F(AI)²R or PROV-O. TPL9 already does this; we
  consume.
- It does not require an MCP rebuild. The integration hook is in
  `ProvenanceCaptureFilter`, which is shipped infrastructure.
- It does not bundle a UI overhaul. The UI is the standard list +
  detail + lineage pattern Shepard already uses for every payload kind.
- It does not block on the substrate-split arc. PromptLog can ship
  on today's Postgres + Garage with no migration of existing
  payloads.
- It does not commit Shepard to ClickHouse, Redis, or a fork of
  Langfuse code. The OSS license risk is sidestepped by
  pattern-adoption rather than code-adoption.

---

## §17 References

External sources cited in this doc (URLs verified 2026-05-23):

- [Langfuse architecture](https://langfuse.com/handbook/product-engineering/architecture) — substrate split (Postgres metadata + ClickHouse traces + S3 bodies + Redis queue)
- [Langfuse v3 infrastructure evolution](https://langfuse.com/blog/2024-12-langfuse-v3-infrastructure-evolution) — the move from Postgres-only to multi-tier substrate; throughput thresholds
- [OpenTelemetry GenAI semantic conventions (v1.38+ spec)](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/) — the wire format adopted in §3 + §7
- [OpenLLMetry issue 3515 — deprecation of gen_ai.prompt/completion](https://github.com/traceloop/openllmetry/issues/3515) — risk 3 evidence
- [MLflow Prompt Registry](https://mlflow.org/docs/latest/genai/prompt-registry/) — Git-style commit pattern for prompt versioning
- [Procko et al. (2024), *Prompt Provenance*, SSRN 5682942](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=5682942) — PROV extension for prompts
- [Souza et al. (2025), *PROV-AGENT*, arXiv 2508.02866](https://arxiv.org/abs/2508.02866) — full agent + LLM + tool-call PROV-O extension (ORNL)
- [Pina et al. (2019), *Provenance Data in the ML Lifecycle*, arXiv 1910.04223](https://arxiv.org/pdf/1910.04223) — PROV-O is sufficient (no new ontology needed)
- [EU AI Act Article 50 service desk](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-50) — the regulatory deadline driving this
- [Transparency as Architecture, arXiv 2603.26983](https://arxiv.org/pdf/2603.26983) — pure provenance is insufficient, UI surfacing matters
- [PROV-O W3C TR](https://www.w3.org/TR/prov-o/) — foundational
- [promptfoo](https://github.com/promptfoo/promptfoo) — adopted eval CLI (now Anthropic + OpenAI co-used; MIT)
- [OpenLineage Run Cycle spec](https://openlineage.io/docs/spec/run-cycle/) — the workflow-envelope synergy partner
- [Phoenix (Arize) — OTel-native LLM observability](https://phoenix.arize.com/) — alternative substrate path (rejected due to ELv2)
- [Helicone proxy](https://www.helicone.ai/) — alternative receiver path (rejected due to depth)
- [Anthropic prompt caching + ZDR](https://platform.claude.com/docs/en/build-with-claude/prompt-caching) — ZDR-mode reference for the no-storage default

Internal (Shepard) anchors:

- `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 15
  (TPL9, F(AI)²R verification ladder) — the vocabulary this design consumes
- `aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md` —
  the S-02 synergy that gives this design its EU AI Act evidence path
- `aidocs/integrations/83-rebar-airflow-integration.md` — the
  `shepard-plugin-mlops` baseline this depends on
- `project_block_editor.md` — the §6 caller
- `project_mcp_path.md` — the §9 hook site
- `aidocs/65-admin-configurable-ontology-preseed.md` — the admin-config
  pattern PROMPT-h follows
- `aidocs/semantics/94-metadata4ing-integration-design.md` — m4i M4I-b
  shared pre-seed
- `feedback_db_review_all_stores.md` + `feedback_shacl_single_source_of_truth.md`
  — substrate-split discipline applied in §4
- `feedback_plugins_declare_sidecars.md` — §8 sidecar declaration
  pattern
- `feedback_reuse_before_reimplement.md` — §1 reuse-survey discipline
