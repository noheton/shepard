---
title: Persona audit — PromptLog design (aidocs/semantics/99)
stage: audited-by-personas
last-stage-change: 2026-05-23
audience: contributors, design reviewers, PROMPT1 implementers
audits: aidocs/semantics/99-promptlog-design.md (commit 6677e494)
personas: Analytics & AI (Role 8, primary) · RDM (Role 5) · API Scrutinizer (Role 3) · Digital Native (Role 10)
verdict-summary: 4× ACCEPT-WITH-CHANGES (escalations on OQ-1 + PII/hash coherence + endpoint redundancy)
---

# Persona audit — PromptLog design (aidocs/semantics/99)

**Audit trigger.** Stage advancement `feature-defined → audited-by-personas`
per `feedback_persona_audit_triggers.md` (AI/ML shape — primary Analytics &
AI #8; secondary trio RDM #5 + API Scrutinizer #3 + Digital Native #10 per
the scope table in `feedback_agents_argue_and_consult.md`).

**Doc under audit.** `aidocs/semantics/99-promptlog-design.md` (1051
lines, committed 6677e494). PROMPT1 backlog row (`aidocs/16` L1033).

**Audit method.** Each persona's lens (per `CLAUDE.md §"Specialized
agent roles"` Roles 1-10) applied to the doc with at least 3 external
citations per persona (per `feedback_agents_use_research_tools.md`).
Findings element-anchored to §/line ranges (per the
`feedback_persona_audit_triggers.md` addendum). Verdict ladder:
ACCEPT / ACCEPT-WITH-CHANGES / REVISE / REJECT.

**Headline result.** All four personas land at **ACCEPT-WITH-CHANGES**.
Three escalations need user input before PROMPT-a starts:

1. **OQ-1 (leaf-vs-chain)** — block-editor wire shape; default in doc
   is *leaf*. Audit recommends *leaf with chain-id sidecar* (see
   §reconciliation).
2. **PII × hash-only coherence bug** — §11 Risk 2 and §13 OQ-4 are in
   tension (RDM lens).
3. **Endpoint redundancy** — `templates/{id}/runs` + `agents/{id}/runs`
   duplicate `GET /runs?template_id=…&agent_id=…` (API Scrutinizer
   lens).

---

## §1 Analytics & AI (Role 8) — primary lens

**Brief (per `CLAUDE.md` Role 8):** Applied ML engineer / data
scientist; lens is "where does AI create compounding value here?".
Watches for ML opportunity, training-data IP, embedding-substrate
choice, eval harness fitness, cost/throughput realism.

### 1.1 Findings

| ID | § anchored | Finding | Lens-cite | Severity |
|---|---|---|---|---|
| AI-1 | §4 + §10 PROMPT-c | **pgvector vs. pgvectorscale not distinguished.** Doc says "pgvector is already in stack" — but plain pgvector + HNSW degrades sharply above ~10M vectors; pgvectorscale (Timescale StreamingDiskANN) is the production-grade choice for the 10-100M range. PROMPT-c should specify *pgvectorscale*, not bare pgvector. | Analytics & AI; cite [pgvectorscale 50M benchmark](https://www.tigerdata.com/blog/pgvector-is-now-as-fast-as-pinecone-at-75-less-cost) (471 QPS @ 99% recall, 28× lower p95 vs Pinecone s1) | MAJOR |
| AI-2 | §10 PROMPT-c "1536-dim or 3072-dim" | **Embedding dimension undermotivated.** Doc lists 1536 (OpenAI ada-002) and 3072 (OpenAI 3-large) but the §8 manifest sidecar uses `sentence-transformers/all-MiniLM-L6-v2` (384-dim). Three different choices appear, none reconciled. OQ-3 partially addresses this but doesn't commit. | Analytics & AI; cite [Best Vector DBs 2026](https://www.firecrawl.dev/blog/best-vector-databases) — dimension choice drives index cost, recall, model lock-in | MAJOR |
| AI-3 | §11 Risk 1 | **RAG body-size estimate (50–200 KB Souza et al.) is in the doc but doesn't feed PROMPT-h `cost_table`.** The `body_retention_days` knob exists; a `body_size_warn_threshold_kb` knob doesn't. At 1M runs × 200 KB = 200 GB/month, Garage *can* hold it but auditing prompts for over-size is operationally vital. | Analytics & AI + Reluctant Senior (storage-cost surprise = adoption killer) | MINOR |
| AI-4 | §10 PROMPT-e + §8 sidecar | **promptfoo dependency on remote service is unflagged.** Doc adopts promptfoo as MIT eval harness; doesn't mention the [open issue #5808](https://github.com/promptfoo/promptfoo/issues/5808) — promptfoo sends data to a remote API by default unless explicitly disabled. PROMPT-e must pin `--no-remote-calls` or equivalent + document in `plugins/promptlog/docs/install.md`. | Analytics & AI; cite [promptfoo issue #5808](https://github.com/promptfoo/promptfoo/issues/5808) | MAJOR |
| AI-5 | §1.1 OpenLLMetry row | **Stale licensing fact.** OpenLLMetry's parent (Traceloop) was **acquired by ServiceNow in March 2026** per [morphllm coverage](https://www.morphllm.com/openllmetry). Apache-2.0 license persists, but governance changed. Doc should note this — it affects "very active" trajectory claim in §1.1 table. | Analytics & AI + Strategy Aligner | MINOR |
| AI-6 | §4 table "Embedding pgvector" | **No discussion of embedding refresh policy.** If `embedding_model` changes (admin config OQ-3), every existing PromptRun has stale embeddings. No re-embed migration path defined. | Analytics & AI | MAJOR |
| AI-7 | §7 evidence pack | **Cost-accounting source data goes through §11 Risk 5 `cost_table` admin field — but no per-run model-version pinning is captured.** If a model is silently rev'd by the provider (Claude Opus 4.7 → 4.8 mid-month), the cost_table can't reconcile. Need `gen_ai.response.model` (what the provider actually returned) as a separate field alongside `gen_ai.request.model`. | Analytics & AI; cite [OTel GenAI spec](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/) — both attributes are defined | MAJOR |

### 1.2 Counter-evidence (per discipline)

The Postgres-only substrate claim in §4 needs the strongest scrutiny.
Langfuse moved off Postgres-only specifically *because* their own
single-Postgres deployments hit query bottlenecks at ~100k events/day
([Langfuse v3 evolution](https://langfuse.com/blog/2024-12-langfuse-v3-infrastructure-evolution)).
The doc's §4.1 cites a 10-100 writes/sec scenario which is 1-2 orders
of magnitude below that threshold, so the claim *holds for the
expected workload* — but PROMPT-j's load-test threshold should be
"100k events/day sustained", not the vague "1k writes/sec" the doc
states. Tightening the success criterion makes PROMPT-j a real gate.

### 1.3 Opposing-lens paragraph

The Reluctant Senior would push back on the embedding/similarity
slice (PROMPT-c) entirely: *"I never asked for similarity search; my
prompts are unique to my domain."* The §5.2 "what would change my
mind" already says demote to SPARQL canned query if < 10 calls/month.
**Promote that test to a hard PROMPT-c acceptance criterion** — ship
behind a feature flag, measure, retire if dormant. Per
`project_basic_advanced_mode.md`, similarity sits in *advanced*; the
default-off posture aligns with `feedback_basic_advanced_superset.md`.

### 1.4 Verdict — **ACCEPT-WITH-CHANGES**

Strong overall direction. The substrate-split decision (Postgres +
Garage + pgvectorscale) is correct for the workload. PROV-O + OTel
GenAI is the right wire-format choice (saves invention cost,
auto-instruments via existing SDKs). Evaluation harness adoption
(promptfoo) is sound *modulo AI-4*.

**What would change my mind:** if a real Shepard MFFD demo
collection produces ≥ 10k AI-tagged DataObjects in a quarter with no
clear similarity-search use case from researchers, demote PROMPT-c
to optional plugin extension and reclaim the 3 dev-days for PROMPT-d
chain support which is more universally needed.

**Highest-priority change request:** AI-4 (promptfoo remote-call
gotcha) — security/IP concern, must be flagged before PROMPT-e ships.

---

## §2 Research Data Manager (Role 5) — FAIR + GDPR lens

**Brief (per `CLAUDE.md` Role 5):** RDM Steward; lens is FAIR
compliance, DMP fulfillment, EU AI Act + GDPR conformance, immutable
provenance vs. erasure rights, controlled-vocabulary discipline.

### 2.1 Findings

| ID | § anchored | Finding | Lens-cite | Severity |
|---|---|---|---|---|
| RDM-1 | §11 Risk 2 + §13 OQ-4 | **Coherence bug between hash-only default and PII redaction default.** §11 says default is hash-only (body not stored unless admin opts in). §13 OQ-4 then asks whether PII redaction should be default-on. **If the body isn't stored, redaction is a no-op** — these decisions are wired together, not independent. Either: (a) commit to hash-only default + drop OQ-4 entirely, or (b) commit to body-stored default + flip OQ-4 to default-on. Current shape is incoherent. | RDM (primary) + API Scrutinizer; cite [data443 — Why Logging AI Prompts Creates Compliance Risk](https://data443.com/blog/why-logging-ai-prompts-creates-compliance-risk/) recommending metadata-only-plus-hash by default | **CRITICAL** |
| RDM-2 | §11 Risk 2 hard-delete path | **`prov:wasInvalidatedBy` chain not fully spec'd.** Doc claims erasure "deletes the body, replaces with `prov:wasInvalidatedBy` Activity, keeps the run as evidence-of-call." But the §7 Article 50 SPARQL query joins `?prompt schema:text ?promptText` — that triple **vanishes post-erasure**. The evidence pack must show "run happened" without the body. SPARQL query needs an `OPTIONAL` clause; the JSON-LD shape needs an `erased` flag on `fair2r:Prompt`. | RDM + Data Ontologist; cite [Springer 2024 — Right to be Forgotten in Cloud Data Lakes](https://link.springer.com/chapter/10.1007/978-3-031-53963-3_16) on metadata-preserving erasure patterns | MAJOR |
| RDM-3 | §13 OQ-7 (AuditPass body storage) | **Reading MCP calls re-introduce the body problem.** OQ-7 punts on whether `fair2r:AuditPass` stores the *result* of a read. If a researcher's MCP agent reads a DataObject containing PHI, the read-result body could be 100s of MB. Default must mirror the hash-only AuthoringPass posture. | RDM; cite [OWASP LLM Top 10 LLM06](https://owasp.org/www-project-top-10-for-large-language-model-applications/) | MAJOR |
| RDM-4 | §10 PROMPT-i "evidence pack" | **PROMPT-i format options "jsonld\|datacite" — but no DataCite mapping spec'd.** DataCite schema 4.5 doesn't natively express PROV-O agent chains. Either drop `datacite` from the format list, or commit to a specific PROV-O → DataCite `RelatedIdentifier`/`Contributor` mapping (and cite the mapping authority). | RDM; cite [DataCite Schema 4.5](https://schema.datacite.org/meta/kernel-4.5/) lacks AI-agent role types | MINOR |
| RDM-5 | §3.1 vocab table | **F(AI)²R reference is to `noheton/f-ai-r` (external repo). FAIR-Reusable mandates persistent identification.** Is f-ai-r minted with a w3id or PURL? Audit recommends pinning to a specific commit hash + adding "if upstream moves, Shepard hosts mirror" clause. | RDM; cite [FAIR Principles (Wilkinson 2016)](https://www.nature.com/articles/sdata201618) R1.3 "data meet domain-relevant community standards" | MINOR |
| RDM-6 | §9.3 read-MCP calls | **`fair2r:AuditPass` vocabulary not in `aidocs/95 Part 15` (verify).** Doc cites it as if defined but TPL9 only defines `AuthoringPass`. Either add `AuditPass` to TPL9b or rename in §9.3. | RDM + Data Ontologist | MAJOR |
| RDM-7 | §11 cost_table snapshot | **GWDG-SAIA pricing snapshot has no validity-window field.** A cost field where the snapshot date is missing leaks "$X claimed cost" without "from pricing dated YYYY-MM-DD." FAIR-Interoperability needs that timestamp. | RDM | MINOR |

### 2.2 Counter-evidence

The doc's claim that EU AI Act Article 50 *drives* the design is
worth scrutiny. The May 2026 Commission Guidelines on Article 50
([Bird & Bird summary](https://www.twobirds.com/en/insights/2026/taking-the-eu-ai-act-to-practice-reading-the-commissions-draft-article-50-guidelines))
focus on **watermarking + content labeling** of AI-generated synthetic
media, **not** on prompt logging per se. The "Article 50 evidence
pack" framing in §7 is somewhat overstated. The *real* regulatory hook
for prompt logging is **Article 53 GPAI documentation obligations**
(model card + training-data summary, August 2026 deadline). Audit
recommends:

- §7 reframed as "Article 50 + Article 53 dual obligation" — Article
  50 is the *labeling* obligation (watermark / mark synthetic output);
  Article 53 is the *documentation* obligation prompt logs feed.
- §10 PROMPT-i evidence-pack scope clarified — for Article 53 the
  prompt logs are *internal documentation*; for Article 50 they're
  *content provenance for labels*.

This isn't a blocker but the design's regulatory anchoring needs
tightening.

### 2.3 Opposing-lens paragraph

The Analytics & AI lens would object to over-defensive hash-only
default: *"I can't run anomaly detection on hashes."* Counter: the
admin-opt-in mode for full body capture (RDM-1 resolution path b)
solves this — research collections that *want* prompt analytics flip
the bit, regulated collections stay hash-only. The pattern is
**per-Collection PromptLog mode**, not a global switch. This should
be the resolution direction.

### 2.4 Verdict — **ACCEPT-WITH-CHANGES**

Strong PROV-O + F(AI)²R foundation. The vocabulary discipline (§3
"no new ontology") is excellent. The substrate split serves FAIR
reusability well. **But RDM-1 is a blocking coherence bug** — the
PII×hash-only contradiction must be resolved before PROMPT-a starts.

**What would change my mind:** if the Commission Article 50
Guidelines (final form expected post-3 June 2026 consultation) add an
explicit prompt-log requirement, then OQ-4 PII default-on becomes
non-negotiable — flip immediately and absorb the friction cost.

**Highest-priority change request:** RDM-1 (resolve hash-only vs.
redaction coherence — pick one, default per-Collection).

---

## §3 API Scrutinizer (Role 3) — wire-shape lens

**Brief (per `CLAUDE.md` Role 3):** Minimalist API critic. Lens is
"smallest API that solves the problem"; watches for redundancy,
inconsistency, leaky abstraction, verbosity, missing operations.

### 3.1 Findings

| ID | § anchored | Finding | Lens-cite | Severity |
|---|---|---|---|---|
| API-1 | §5 table rows 9-10 | **`GET /v2/promptlog/templates/{id}/runs` and `GET /v2/promptlog/agents/{id}/runs` are redundant with `GET /v2/promptlog/runs?template_id=…&agent_id=…`.** The doc already specifies those filter params on the main list endpoint (row 6). Sub-resource pattern adds caching benefit per [Stoplight API Patterns](https://blog.stoplight.io/api-design-patterns-for-rest-web-services?hs_amp=true) but creates two ways to answer the same question — exactly the redundancy criterion #1 of the API Scrutinizer brief. **Recommend: drop both sub-resource endpoints; document the canonical filter pattern.** | API Scrutinizer; cite [Speakeasy — Filtering Responses](https://www.speakeasy.com/api-design/filtering-responses) recommending one canonical surface per query shape | MAJOR |
| API-2 | §5 rows 1-2 | **`POST /runs` vs. `POST /chains` shape relationship not specified.** Is `chains` a wrapper that internally creates N `runs` + a Transcript? Or independent? Or does `runs` accept an optional `parent_run_id` making `chains` redundant? Wire shape unclear — needs explicit JSON-Schema for each request body. | API Scrutinizer; cite [Redocly OpenAPI parameter types](https://redocly.com/blog/openapi-parameter-types) on shape-clarity discipline | MAJOR |
| API-3 | §5 RFC 7807 codes column | **Only 3 RFC 7807 codes specified across 10 endpoints.** The "no error codes specified" rows (7 of 10) suggest "no 4xx errors possible" — false for any real endpoint. Need: `PROMPTRUN_NOT_FOUND`, `TEMPLATE_HASH_CONFLICT` (specified), `BODY_FETCH_FAILED` (Garage outage), `EMBEDDING_SERVICE_UNAVAILABLE`, `CHAIN_CYCLE_DETECTED`, `PII_REDACTION_REQUIRED`. | API Scrutinizer + per A0/H4 RFC 7807 discipline in CLAUDE.md | MAJOR |
| API-4 | §9.1 McpProvenanceExtension header set | **Five `X-AI-*` headers cited as "per TPL9b" — but TPL9b spec needs cross-check.** `X-AI-Agent`, `X-AI-Prompt-Hash`, `X-AI-Prompt-ID`, `X-AI-Session-ID`, plus the implicit `X-AI-Claim-Status` (in `headerStatus(inv)`). These should be a single registered header family with a canonical doc location. | API Scrutinizer + Data Ontologist | MAJOR |
| API-5 | §3 SHACL shapes | **`shp:inputMessages` / `shp:outputMessages` typed as `xsd:string` (JSON-encoded).** That's a *stringly-typed JSON-in-JSON* anti-pattern. Should be `rdf:JSON` (RDF 1.2 datatype) or a structured shape. | API Scrutinizer + Data Ontologist; cite [RDF 1.2 JSON literal](https://www.w3.org/TR/rdf12-concepts/#section-json) | MINOR |
| API-6 | §5 pagination | **Pagination envelope not specified.** Doc says "shaped per A0/H4… pagination envelope" but doesn't state which envelope. Shepard has two patterns historically (v1 cursor vs v2 limit/offset). Pick one, cite it. | API Scrutinizer + Reluctant Senior (consistency criterion) | MINOR |
| API-7 | §5 row 8 `?k=10` | **`k` default of 10 hardcoded in URL example — should be in OpenAPI parameter schema with explicit min/max/default.** A caller passing `?k=10000` would explode Postgres. | API Scrutinizer | MINOR |
| API-8 | §10 PROMPT-i evidence-pack | **Endpoint `GET /v2/promptlog/evidence-pack?collection=…&format=…` not in §5 table.** Section §10 introduces an endpoint that §5 (the surface table) doesn't list. Either move it into §5 or note "deferred to PROMPT-i sub-design." | API Scrutinizer | MINOR |

### 3.2 Counter-evidence

The 10-endpoint count is defensible at the size Shepard's v2 already
runs (`v2/data-objects/` 15 endpoints; `v2/timeseries/` ~20). The
*correct* objection isn't count; it's the **redundancy axis** —
API-1 above. With API-1 resolved, the count drops to 8 endpoints,
each with one canonical role. That's lean.

### 3.3 Opposing-lens paragraph

The Reluctant Senior would want fewer URLs to memorise, supporting
API-1. The Digital Native would want filter-based composability —
*also* supporting API-1 (one filter pattern, infinite combinations).
Both secondary lenses converge on dropping the two sub-resource
endpoints. The original argument *for* the sub-resources was caching;
the [Stoplight pattern](https://blog.stoplight.io/api-design-patterns-for-rest-web-services?hs_amp=true)
notes this benefit but isn't worth the surface duplication at
Shepard's scale.

### 3.4 Verdict — **ACCEPT-WITH-CHANGES**

Surface is mostly clean. ADOPT OTel GenAI as the body shape is
correct — saves Shepard from inventing a wire format. The §7
four-column mapping (PromptLog ↔ OTel ↔ OpenLineage ↔ PROV-O) is
exactly the kind of cross-standard scaffolding the API Scrutinizer
asks for. RFC 7807 + `@RolesAllowed` discipline is committed (§5
preamble). API-1 + API-2 + API-3 are the substantive fixes.

**What would change my mind:** if PROMPT-c (similarity search)
acceptance shows clients consistently filtering by *both* `model` and
`template_id`, the `agents/{id}/runs` sub-resource shape *might* earn
its caching benefit. But ship the canonical filter pattern first;
prove the redundancy is needed before adding it.

**Highest-priority change request:** API-1 (drop redundant
sub-resource endpoints).

---

## §4 Digital Native (Role 10) — API-first researcher lens

**Brief (per `CLAUDE.md` Role 10):** 28-year-old API-first postdoc.
Lens is "can I use this from a Jupyter notebook in 5 lines of
Python?" Watches for missing SDK, opaque auth, unfindable docs, the
gh-CLI feasibility test.

### 4.1 Findings

| ID | § anchored | Finding | Lens-cite | Severity |
|---|---|---|---|---|
| DN-1 | §5 entire | **No Python SDK example anywhere.** The doc shows curl-shaped REST endpoints, SPARQL queries, Java handler code — but the persona who *uses* this every day writes Python. Need a "5-line Python: log my prompt" worked example in §15 docs commitment + `quickstart.md`. | Digital Native; cite [OpenLLMetry Python client](https://pypi.org/project/opentelemetry-instrumentation-langchain/) — the existing standard | MAJOR |
| DN-2 | §6 + §9 | **MCP integration auto-records, OTel SDK auto-records, but the "I'm writing a Python script that calls Claude SDK directly" path isn't documented.** Three ingestion paths exist (MCP, OTel collector, manual `POST /runs`); only the first two are shown working. The bare-Python case is the most common dev workflow. | Digital Native; cite [Anthropic Python SDK docs](https://docs.anthropic.com/en/api/client-sdks) | MAJOR |
| DN-3 | §9.1 header set | **`X-AI-Agent`, `X-AI-Prompt-Hash`, etc. — but no Python helper to generate them.** A digital native won't manually compute SHA256 of every prompt + set 5 headers. Need a `shepard-promptlog-python` package or at least a `requests` + `httpx` cookbook snippet. | Digital Native + API Scrutinizer | MAJOR |
| DN-4 | §10 PROMPT-f MCP | **MCP integration depends on `shepard-plugin-mcp` (per project_mcp_path.md) — but PROMPT-f deps don't say "needs MCP plugin shipped first."** Hidden dependency. | Digital Native + Strategy Aligner | MINOR |
| DN-5 | §12 acceptance criteria #1 | **"Wire fidelity test in CI" — doesn't say which OTel SDK version is used as the reference fixture.** Test fixture should pin to *both* v1.36 (legacy `gen_ai.prompt`) and v1.38+ (`gen_ai.input.messages`) shapes per the doc's own Risk 3. | Digital Native + API Scrutinizer; cite [OTel semconv releases](https://github.com/open-telemetry/semantic-conventions/releases) | MINOR |
| DN-6 | §5 + §6 chains-as-DAG | **`GET .../{id}/lineage` returns "predecessor + successor PromptRuns" but format unspecified.** JSON-LD with PROV-O? Adjacency-list JSON? GraphML? Pick a shape that *visualises in a notebook* (vis.js graph, Mermaid, dagre — pick one). | Digital Native; cite [project_mffd_seed_demo.md] dagre choice | MINOR |
| DN-7 | §10 PROMPT-h CLI | **`shepard-admin promptlog ...` CLI shape unspecified.** Per memory `project_dlr_institutional_strategy.md` D4=MCP/Claude, the CLI must be MCP-tool-callable, not just human-typed. Need explicit `--output=json` flag + tool annotations. | Digital Native + Analytics & AI | MINOR |

### 4.2 The 5-line Python test (per `CLAUDE.md` Role 10 brief)

What the Digital Native wants to write (post-PROMPT-b):

```python
from shepard_promptlog import PromptLog
log = PromptLog(token=os.environ["SHEPARD_TOKEN"])
run_id = log.record_anthropic(client, messages=[...], collection_id="42")
similar = log.similar(run_id, k=5)
```

This requires:

1. A `shepard-promptlog` Python package (PROMPT-c follow-up).
2. Auto-instrumentation that knows the Anthropic SDK call shape.
3. A `similar` method that talks to `GET .../{id}/similar`.

Today's doc neither commits to nor disqualifies this. **Recommend:
add a `PROMPT-c2` slice for the Python client package** (2 days,
parallel to PROMPT-c).

### 4.3 Opposing-lens paragraph

The Reluctant Senior would push back: *"I don't write Python."* Fair
— but Python is the API-side gravity well; everyone (R, Julia, Go,
Bash) hits a REST endpoint, and Python users specifically need the
SDK ergonomics for adoption velocity. The Reluctant Senior's UI-side
workflow (block editor §6, MCP integration §9) is fully covered;
adding a Python SDK doesn't subtract from that.

### 4.4 Verdict — **ACCEPT-WITH-CHANGES**

Architectural shape is API-first-friendly (REST, OTel, standard
headers). But the *missing Python SDK* is the gap between
"architecturally clean" and "I can use it Tuesday morning." DN-1 +
DN-2 + DN-3 cluster around the same fix: ship a thin Python client
alongside PROMPT-c.

**What would change my mind:** if the `pip install
opentelemetry-instrumentation-anthropic` (OpenLLMetry's Anthropic
integration) already emits to a Shepard receiver with zero
configuration, then the Python SDK is the OTel SDK and Shepard
documents the env-var setup. Cheaper than building a custom package.
*Validate this in PROMPT-b acceptance.*

**Highest-priority change request:** DN-1 + DN-2 (5-line Python
worked example + bare-Python Anthropic SDK path).

---

## §5 Cross-persona reconciliation

### 5.1 Synthesis matrix

| Concern | AI #8 | RDM #5 | API #3 | DN #10 | Combined verdict |
|---|---|---|---|---|---|
| Substrate choice (Postgres + Garage + pgvector) | ✓ (note pgvectorscale) | ✓ | n/a | ✓ | **ACCEPT** — clarify pgvectorscale in §4 |
| Ten endpoints + sub-resources | n/a | n/a | ⚠ drop 2 | ⚠ also drop | **ACCEPT-WITH-CHANGES** — drop sub-resource pair |
| Hash-only default | ✓ allows opt-in | ⚠ conflicts with OQ-4 | ✓ | ⚠ blocks anomaly-detection | **ESCALATION (RDM-1)** — per-Collection mode |
| OTel GenAI wire format | ✓ | ✓ | ✓ | ✓ | **ACCEPT** — unanimous |
| PROV-O ontology consumption | ✓ | ✓ | ✓ | ✓ | **ACCEPT** — unanimous |
| Block-editor leaf-vs-chain (OQ-1) | n/a | ✓ chain queryable | ⚠ wire-shape implication | ⚠ visualisation needs both | **ESCALATION** — recommend "leaf-id + chain-id sidecar" |
| Python SDK | ✓ helps prove | ✓ helps DMP fulfillment | n/a | **REQUIRED** | **NEW REQUIREMENT** — add PROMPT-c2 |
| EU AI Act framing | n/a | reframe Art 50 + Art 53 | n/a | n/a | **ACCEPT-WITH-CHANGES** — tighten §7 |
| Embedding dimension OQ-3 | ⚠ commit | n/a | ⚠ commit | ⚠ commit | **ESCALATION** — pick MiniLM-L6 (384) for v1 |
| Cost table (Risk 5) | ⚠ model-rev pinning | ⚠ snapshot date | n/a | n/a | **ACCEPT-WITH-CHANGES** — add `cost_table.snapshot_date` + `gen_ai.response.model` |

### 5.2 ESCALATIONS (require user input before PROMPT-a starts)

#### ESC-1 — OQ-1 leaf-vs-chain (block editor wire shape)

**Doc default:** block carries `promptRunAppId` (the *leaf* run).
**Audit recommendation:** **block carries BOTH `promptRunAppId` (leaf,
for the hot path) AND `promptChainAppId` (transcript-id, for chain
queries)**. Two strings cost ~80 bytes per block; the cognitive cost
of "is this the leaf or the chain?" forever is much higher.

| Persona | Position | Reasoning |
|---|---|---|
| Data Ontologist (lightweight consult per memory) | leaf-only correct via PROV `wasInformedBy` traversal | minimises wire size |
| RDM | both — chain context is the audit unit | Article 53 documentation wants the conversation, not the leaf |
| API Scrutinizer | both — but make the chain-id `null` when no chain exists | composability |
| Digital Native | both — visualisation needs the chain anchor | a notebook plotting "show me the conversation that produced this block" needs the chain in one call |

**Verdict:** **adopt leaf + chain dual-id**, with OpenAPI shape
`{ "promptRunAppId": "...", "promptChainAppId": "..."|null }`.
Resolves OQ-1; tightens §6.1 block wire-shape; unblocks PROMPT-g.

#### ESC-2 — RDM-1 PII × hash-only coherence

**Doc state:** §11 Risk 2 commits to hash-only default; §13 OQ-4
treats redaction default as open.

**Audit recommendation:** **per-Collection PromptLog mode.** Each
Collection chooses one of three modes at creation (admin-overridable):

| Mode | Body storage | Redaction | Use case |
|---|---|---|---|
| `hash-only` (default for new Collections) | hash only | n/a (no body) | regulated data (PHI, IP-restricted, default-safe) |
| `body-redacted` | body stored + redacted at ingest | default-on | research analytics on prompt content with PII guard |
| `body-raw` | body stored as-is | default-off | air-gapped instances, internal DLR research with explicit approval |

**Rationale:** matches the per-Collection feature-flag pattern Shepard
already uses (TPL2c gate); matches Anthropic's [ZDR mode](https://platform.claude.com/docs/en/build-with-claude/prompt-caching);
unblocks both regulated and analytics use cases without one global
switch.

**Resolves:** RDM-1 (CRITICAL), AI-affected scenarios, OQ-4 (kill the
open question — answered by mode choice).

#### ESC-3 — API-1 endpoint redundancy

**Audit recommendation:** drop `GET .../templates/{id}/runs` and `GET
.../agents/{id}/runs`. Use `GET /runs?template_id=…` and `GET
/runs?agent_id=…`. Two fewer endpoints, one canonical query pattern.

Document the canonical filter pattern in the OpenAPI spec; ship the
sub-resource pattern only if real callers ask for it post-launch
(per §5.2's existing "what would change my mind").

### 5.3 NEW REQUIREMENTS (not escalations — direct add to PROMPT1)

| ID | Slice | Days | Trigger |
|---|---|---|---|
| **PROMPT-c2** | `shepard-promptlog` Python SDK (thin wrapper over OTel auto-instrumentation + manual REST helpers) + `quickstart.md` 5-line example | 2 | DN-1 + DN-2 + DN-3 |
| **PROMPT-h2** | Per-Collection PromptLog mode field (ESC-2 resolution) on `:Collection` + admin UI/CLI to set per-Collection mode | 2 | RDM-1 ESC-2 |
| **PROMPT-a addendum** | Drop sub-resource endpoints (API-1); spec full RFC 7807 code set (API-3); add `gen_ai.response.model` field separate from request.model (AI-7); pin pgvectorscale not pgvector (AI-1) | 0 (in-scope refinement) | API-1, API-3, AI-7, AI-1 |

### 5.4 Confirmed defaults (closed open questions)

| Open Q | Audit resolution |
|---|---|
| OQ-1 (leaf-vs-chain) | **Leaf + chain dual-id** (ESC-1) |
| OQ-3 (embedding model) | **MiniLM-L6-v2 (384-dim) for v1** — local + zero dep + no GWDG cost; revisit when local embedding quality fails an MFFD use case |
| OQ-4 (PII default) | **Resolved by per-Collection mode** (ESC-2) |
| OQ-5 (evidence pack on critical path) | **Keep on critical path** — Article 53 docs obligation needs it; PROMPT-i stays a→b→i |
| OQ-6 (m4i:realizesMethod required?) | **Optional on Template, required on Run** — ad-hoc prompts ok at template time; the *invocation* must declare a method for audit |
| OQ-7 (AuditPass result body) | **Hash-only by default**, mirroring AuthoringPass (RDM-3) |

OQ-2 (ClickHouse threshold) remains open — PROMPT-j measurement is
the right answer; no audit override needed.

---

## §6 Lessons + meta

- **The hash-only × redaction coherence bug** is the kind of cross-§
  contradiction a single persona would miss but the synthesis matrix
  catches. Worth keeping the matrix shape in future audits.
- **Three of the audit's biggest finds (AI-4 promptfoo remote call,
  AI-5 OpenLLMetry acquisition, AI-1 pgvectorscale vs. pgvector)
  came from web research, not codebase inspection.** Confirms the
  `feedback_agents_use_research_tools.md` discipline pays off.
- **The Digital Native lens is the strongest single proxy for adoption
  velocity.** When the persona can't write 5-line Python on day one,
  the feature is architecturally clean but operationally lifeless.
  Worth promoting "5-line Python test" to a default acceptance
  criterion for every API-shaped design.

---

## §7 External citations used (per ≥3-per-persona discipline)

**Analytics & AI (Role 8):**
- [Tiger Data — pgvectorscale vs Pinecone benchmark](https://www.tigerdata.com/blog/pgvector-is-now-as-fast-as-pinecone-at-75-less-cost)
- [Firecrawl — Best Vector Databases 2026](https://www.firecrawl.dev/blog/best-vector-databases)
- [promptfoo issue #5808 — remote-call default](https://github.com/promptfoo/promptfoo/issues/5808)
- [morphllm — Traceloop/OpenLLMetry acquired by ServiceNow March 2026](https://www.morphllm.com/openllmetry)
- [OTel GenAI spans spec — request.model vs response.model](https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/)
- [Langfuse v3 evolution — Postgres-only deprecated](https://langfuse.com/blog/2024-12-langfuse-v3-infrastructure-evolution)

**RDM (Role 5):**
- [data443 — Why Logging AI Prompts Creates Compliance Risk](https://data443.com/blog/why-logging-ai-prompts-creates-compliance-risk/)
- [Springer 2024 — Right to be Forgotten in Cloud Data Lakes](https://link.springer.com/chapter/10.1007/978-3-031-53963-3_16)
- [OWASP LLM Top 10 (2025) LLM06](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- [Bird & Bird — May 2026 Article 50 Guidelines summary](https://www.twobirds.com/en/insights/2026/taking-the-eu-ai-act-to-practice-reading-the-commissions-draft-article-50-guidelines)
- [DataCite Schema 4.5](https://schema.datacite.org/meta/kernel-4.5/)

**API Scrutinizer (Role 3):**
- [Stoplight — API Design Patterns for REST](https://blog.stoplight.io/api-design-patterns-for-rest-web-services?hs_amp=true)
- [Speakeasy — Filtering Responses Best Practices](https://www.speakeasy.com/api-design/filtering-responses)
- [Redocly — OpenAPI parameter types](https://redocly.com/blog/openapi-parameter-types)
- [W3C RDF 1.2 JSON literal](https://www.w3.org/TR/rdf12-concepts/#section-json)

**Digital Native (Role 10):**
- [PyPI opentelemetry-instrumentation-langchain](https://pypi.org/project/opentelemetry-instrumentation-langchain/)
- [Anthropic Python SDK docs](https://docs.anthropic.com/en/api/client-sdks)
- [OpenTelemetry semantic-conventions releases](https://github.com/open-telemetry/semantic-conventions/releases)
- [Promptfoo Python Integration](https://www.promptfoo.dev/docs/integrations/python/)

---

## §8 Verdict summary (machine-readable)

```yaml
audits: aidocs/semantics/99-promptlog-design.md
commit: 6677e494
audited-at: 2026-05-23
personas:
  - role: 8  # Analytics & AI
    verdict: ACCEPT-WITH-CHANGES
    findings: [AI-1, AI-2, AI-3, AI-4, AI-5, AI-6, AI-7]
    blocker: AI-4  # promptfoo remote-call default
  - role: 5  # RDM
    verdict: ACCEPT-WITH-CHANGES
    findings: [RDM-1, RDM-2, RDM-3, RDM-4, RDM-5, RDM-6, RDM-7]
    blocker: RDM-1  # CRITICAL — PII × hash-only coherence
  - role: 3  # API Scrutinizer
    verdict: ACCEPT-WITH-CHANGES
    findings: [API-1, API-2, API-3, API-4, API-5, API-6, API-7, API-8]
    blocker: API-1  # endpoint redundancy
  - role: 10  # Digital Native
    verdict: ACCEPT-WITH-CHANGES
    findings: [DN-1, DN-2, DN-3, DN-4, DN-5, DN-6, DN-7]
    blocker: DN-1  # missing 5-line Python
escalations:
  - ESC-1: leaf-vs-chain → leaf+chain dual-id
  - ESC-2: hash-only × redaction → per-Collection mode
  - ESC-3: drop sub-resource endpoints (API-1)
new-requirements:
  - PROMPT-c2: Python SDK (2 days)
  - PROMPT-h2: per-Collection mode field (2 days)
gate-flip-eligible: true  # all 4 personas ≥ ACCEPT-WITH-CHANGES
next-stage: audited-by-personas → (after feedback-implemented) tests-implemented
```
