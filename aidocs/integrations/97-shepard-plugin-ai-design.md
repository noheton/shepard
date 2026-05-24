---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor + maintainer
supersedes: aidocs/platform/86-ai-plugin-design.md
---

# 97 — `shepard-plugin-ai` v6 SSOT — local-first AI capability

**Status.** Design ready for v6 implementation wave.
**Predecessor.** [`aidocs/platform/86-ai-plugin-design.md`](../platform/86-ai-plugin-design.md) (decommissioned in the same commit). 86 introduced the capability registry, the layered call-stack, the prompt-injection defence, the loopback MCP shape, and the `:AiActivity` provenance row — **all five carry forward unchanged**. What this doc inverts is the **deployment default**: 86 recommended a LiteLLM proxy in front of OpenAI; v6 ships a TEI sidecar in front of an open-weights model so the out-of-the-box install needs zero credentials, zero egress, zero sign-ups.
**Depends on.** PluginManifest SPI (`aidocs/platform/47 §2.6` — PM1f sidecar declarations), `:AIConfig` admin pattern (A3b), MCP loopback (`aidocs/integrations/30`), `:Activity` provenance (PROV1a), the collapsed-PG architecture (synthesis T2).
**First consumer.** `shepard-plugin-wiki-writer` (TEXT) + the MCP `search_by_embedding` tool (EMBEDDING).

---

## §0 — Reading instructions

shepard-plugin-ai v6 ships AI capability with **zero external dependencies as the default**. Local embeddings work the moment `docker compose up` finishes; cloud providers (OpenAI, SAIA, GWDG, Anthropic, Azure, Cohere) are admin-gated opt-ins behind `:AIConfig.allowExternal=true`. This doc is the SSOT for the plugin family — design audience contributor + maintainer.

This commitment crystallises a new standing rule for `CLAUDE.md`:

> **Always: ship a working local default for every AI capability.** Every AI surface MUST work out-of-the-box with zero external dependencies. External providers are admin-gated opt-ins. **Why:** IP protection (MFFD-class workloads), reproducibility (model swap discipline), operator-friction reduction. **How to apply:** every new AI capability ships with a `LocalXxxProvider` SPI adapter as the default; cloud adapters are explicit opt-in.

Treat that rule as load-bearing throughout the design — every section can be read as "how do we honour MUST-Tier-0 here?" The two narrow exceptions (where the rule relaxes) are called out explicitly in §9 (Tier 0 chat is hard) and §14 (vendor-neutral surface vs operator simplicity).

Read 86 alongside this doc for the parts unchanged: §3 capability registry, §5 call-stack layering, §6 prompt-injection three-layer defence, §7 MCP loopback, §8 `:AiActivity` provenance shape.

---

## §1 — TL;DR + v6 positioning

shepard's first-class AI capability is **local by default, opt-in cloud**. A fresh `make dev` install boots a CPU-only `shepard-embeddings` sidecar with `jina-embeddings-v2-base-de` already loaded; embed-and-search works against the LUMEN demo without any operator action. When a DLR institute wants GPU throughput, they swap to Tier 1 (local GPU sidecar) or Tier 2 (institute-shared SAIA/GWDG endpoint); when an external lab wants OpenAI, they flip `allowExternal=true`. **The local path is the default forever** — not the fallback.

| Tier | Where | Hardware | Egress | When |
|---|---|---|---|---|
| **0 MUST** | TEI/Ollama CPU sidecar in the default compose | 8 GB RAM, no GPU | none | every install — the baseline |
| **1** | TEI/vLLM GPU sidecar on same host | 24 GB GPU (RTX 4090 / A6000) | none | DLR institute with own GPU |
| **2** | networked GPU (SAIA, GWDG, k8s-shared) | shared | DLR-internal LAN only | DLR institutes opted into SAIA |
| **3** | external cloud (OpenAI, Anthropic, Azure) | provider | public internet | external labs explicitly opting in |

**Two MUSTs:**

1. **MUST-Tier-0.** Tier 0 ships in the default compose; works without GPU, sign-ups, API keys, external network egress.
2. **MUST-provenance.** Every AI interaction lands as a typed `:Activity` (f(ai)²r capture per `project_fair2r_integration.md`).

**Differentiator.** Most RDM platforms either skip AI entirely (Kadi4Mat, openBIS, SciCat) or hard-bind to OpenAI (the YC-AI-startup default). shepard does neither — the local sidecar gives embedding-based discovery to every install while preserving operator choice over how aggressive to escalate.

---

## §2 — Reuse-first survey

Per `feedback_reuse_before_reimplement.md`. Every library/spec/pattern considered before designing a single line of Java.

| Component | What it gives | What's missing | Decision | Decisive reason |
|---|---|---|---|---|
| **TEI** ([huggingface/text-embeddings-inference](https://github.com/huggingface/text-embeddings-inference)) | OpenAI-compatible HTTP, Rust-native, Apache-2.0, supports BGE / e5 / jina / nomic / mxbai, ONNX + Candle backends | only embeddings (no chat); single-model-per-process | **adopt as Tier 0 sidecar** | purpose-built throughput; Apache-2.0; OpenAI-compat means the adapter is trivial |
| **Ollama** ([ollama.com/library](https://ollama.com/library)) | one-binary local LLM runtime, model registry, OpenAI-compatible since v0.1.30, MIT | per-request model load is slow; CPU embedding throughput < TEI | **adopt as alternative sidecar** (operator picks) | many DLR ops already run Ollama for chat; sharing it for embeddings reduces footprint |
| **vLLM** ([docs.vllm.ai](https://docs.vllm.ai/)) | Apache-2.0, PagedAttention, GPU-shared LLM+embeddings, OpenAI-compatible | GPU-only; heavyweight for embedding-only workloads | **defer to Tier 1+** | mention as Phase 2 chat option; not Tier 0 |
| **llama.cpp HTTP server** ([ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp/blob/master/examples/server/README.md)) | MIT, `--embedding` mode, runs on hilariously small hardware | smaller embedding catalogue; CPU embedding throughput < TEI | **defer** | mention as Tier 0 alternative if TEI proves too heavy for the smallest deploys |
| **LangChain4j** ([docs.langchain4j.dev](https://docs.langchain4j.dev/)) | Apache-2.0 Java; unified `EmbeddingModel` + `ChatLanguageModel` abstractions; built-in providers; document loaders | abstraction surface is broad — we use ~20% | **adopt as Java-side abstraction** | stack alignment (Quarkus) + future chat work reuses the same beans |
| **Quarkus LangChain4j** ([docs.quarkiverse.io/quarkus-langchain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/)) | Quarkus extension; CDI-injectable `EmbeddingModel` + `ChatLanguageModel` beans; build-time wiring | tracks LangChain4j upstream cadence | **adopt** | first-class Quarkus integration; build-time dev-services support |
| **Spring AI** ([docs.spring.io/spring-ai](https://docs.spring.io/spring-ai/reference/)) | Apache-2.0; comparable abstraction | requires Spring dependencies | **reject** | the fork's stack is Quarkus; Spring AI means a parallel DI tree |
| **DJL** ([djl.ai](https://djl.ai/)) | Apache-2.0; in-process ONNX runtime; no sidecar | adds 200+ MB to the backend JAR; model-loading inside the JVM steals heap from the OGM | **reject for Tier 0** | mention as fallback for air-gapped environments that explicitly cannot run a sidecar |
| **pgvector** ([github.com/pgvector/pgvector](https://github.com/pgvector/pgvector)) | PostgreSQL extension, PostgreSQL License; HNSW + IVFFlat indexes; cosine + L2 + inner-product distance | tunable but defaults need attention | **adopt as embedding substrate** | synthesis T2 collapses to one PG; pgvector lands as `shepard_ai` schema on the same instance |
| **Postgres FTS (`tsvector`)** | PostgreSQL-native keyword search; multi-language dictionaries | keyword-only; no semantic | **adopt alongside pgvector** | complementary; hybrid retrieval (BM25 + vector rerank) is standard practice |
| **Weaviate / Pinecone / Qdrant / Chroma** | mature vector DBs; richer filtering | new substrate; new ops surface | **reject** | violates synthesis AP-X9 (sibling-substrate split where a schema split would suffice); pgvector covers the workload |
| **OpenAI SDK** | reference client for OpenAI | not portable to local backends | **N/A** | we speak the OpenAI HTTP wire protocol directly via a thin Java client |
| **LiteLLM proxy** ([berriai/litellm](https://github.com/BerriAI/litellm)) | multi-provider gateway, cost tracking, rate limits | extra process; not local-by-default | **keep as Tier 3 recommendation** | 86 §13 already recommended this for multi-provider Tier 3 deployments; v6 keeps the recommendation but no longer leans on it as the default |

**What we build.** Three thin Java adapters (`LocalTeiEmbeddingProvider`, `LocalOllamaEmbeddingProvider`, `OpenAiCompatibleEmbeddingProvider`); one `EmbeddingProvider` SPI; one TEI `SidecarSpec` declaration per PM1f; the `shepard_ai` pgvector schema + Flyway migrations; the `:AIConfig` extension + admin REST + CLI parity; the `search_by_embedding` MCP tool; the f(ai)²r `:Activity` capture wrapper. **What we adopt.** Everything else.

---

## §3 — The four-tier provider model

| Tier | Stand-in | Hardware | Throughput (tok/s, single batch) | Egress | Pick when |
|---|---|---|---|---|---|
| **0 MUST** | `shepard-embeddings` TEI sidecar, jina-v2-base-de (768d) | 8 GB RAM, x86 8-core or ARM 4-core | 100–300 (cite [TEI benchmarks README](https://github.com/huggingface/text-embeddings-inference#docker)) | none | every install — fresh `make dev`, MFFD-class IP-sensitive, air-gap, evaluation, Raspberry Pi |
| **1** | `shepard-embeddings-gpu` TEI sidecar, BGE-M3 (1024d) | 24 GB VRAM (RTX 4090 / A6000 / L40S) | 3000–5000 (cite [TEI GPU benchmarks](https://github.com/huggingface/text-embeddings-inference#cuda)) | none | DLR institute with own GPU; MFFD backfill in hours not days |
| **2** | OpenAI-compatible client → networked endpoint | shared k8s GPU | varies by deployment | DLR-internal LAN | DLR-wide SAIA/GWDG path; institute pool of GPUs |
| **3** | OpenAI-compatible client → cloud | provider | provider SLA | public internet | external labs explicitly opting in; production-grade quality needs that local models can't yet match |

**Tier 0.** Default for every install. Sidecar declared by `AiPluginManifest.sidecars()` per PM1f; renderer produces compose snippet; operator pastes. Model swap is a `:AIConfig` PATCH plus a backfill. MFFD-scale (≈155 M tokens of source text — `description` + `summary` + lab journal markdown + first 8 KB of every text file) takes **~9 days** on a modern 8-core x86 at 200 tok/s sustained. Acceptable for first backfill; thereafter live writes are ~50–500 docs/day at single-digit-second latency.

**Tier 1.** Same shape — different image tag (`ghcr.io/huggingface/text-embeddings-inference:cuda-1.5`) + `runtime: nvidia` in the SidecarSpec. Same Java adapter, same wire shape. MFFD backfill drops to **~9–14 h**.

**Tier 2.** SAIA (DLR Scientific AI Assistant) + GWDG AI services both expose OpenAI-compatible endpoints. The same `OpenAiCompatibleEmbeddingProvider` adapter works; `:AIConfig.externalEmbeddingProviderUrl` points at the DLR-internal hostname; `allowExternal=true` because the term "external" in the config field is wire-shape-driven not network-topology-driven (a DLR-internal SAIA endpoint is still "external" from the plugin's perspective — it lives outside the plugin process).

**Tier 3.** OpenAI text-embedding-3-small at $0.020/1M tokens ([OpenAI pricing](https://openai.com/api/pricing/)) — backfill MFFD = **$3.10**; steady-state ≈ **$2/year**. **The cost is not the driver for self-hosting.** The drivers are IP sovereignty (embedding-leak risk for MFFD CFRP process data), reproducibility (model deprecations break retrieval continuity — OpenAI deprecated `text-embedding-ada-002` in Jan 2024, breaking every downstream index), and operator-friction reduction (the first-run install must work without sign-ups).

**Escalation triggers.** Tier 0 → 1: backfill > 24 h on regular re-embedding cycles. Tier 1 → 2: more than one institute on the same DLR site wants to share GPU. Tier 2 → 3: external collaborator without DLR-internal LAN access.

---

## §4 — Tier 0 reference architecture

**The headline.** A TEI CPU sidecar declared by `AiPluginManifest.sidecars()` per PM1f, model preloaded into a named volume, healthcheck that probes a real embed before reporting up, mem_limit per STACK-AUDIT-001 hygiene.

### 4.1 SidecarSpec declaration (concrete)

```java
@Override
public List<SidecarSpec> sidecars() {
  return List.of(
    new SidecarSpec(
      "embeddings",                                                            // id
      "ghcr.io/huggingface/text-embeddings-inference:cpu-1.5",                 // explicit tag; no `latest` per PM1f
      List.of(new PortSpec(8080, "http")),
      List.of(new VolumeSpec("shepard_embedding_models", "/data")),
      Map.of(
        "MODEL_ID",          "jinaai/jina-embeddings-v2-base-de",
        "REVISION",          "main",
        "MAX_BATCH_TOKENS",  "16384",
        "MAX_CONCURRENT_REQUESTS", "32",
        "HF_HOME",           "/data"
      ),
      new HealthcheckSpec(
        // probe a real embed — model-loaded matters, not just listening
        "curl -fsS -X POST http://localhost:8080/embed -H 'content-type: application/json' -d '{\"inputs\":\"healthcheck\"}' >/dev/null",
        Duration.ofSeconds(45),    // first load can take 30s
        Duration.ofSeconds(10),
        5
      ),
      List.of(),                   // no post-init; TEI auto-downloads model on first request
      Map.of(
        "SHEPARD_AI_EMBEDDING_PROVIDER",   "local-tei",
        "SHEPARD_AI_EMBEDDING_ENDPOINT",   "http://{{sidecar.host}}:8080",
        "SHEPARD_AI_EMBEDDING_MODEL_ID",   "jinaai/jina-embeddings-v2-base-de",
        "SHEPARD_AI_EMBEDDING_DIM",        "768"
      )
    )
  );
}
```

### 4.2 Model choice — `jinaai/jina-embeddings-v2-base-de`

- 137 M parameters, 768d, Apache-2.0 — see [model card](https://huggingface.co/jinaai/jina-embeddings-v2-base-de)
- Bilingual German + English (the load-bearing fit for MFFD: ZLP Augsburg working language is German, JEC publications are English, customer documentation is bilingual)
- 8192-token context window (handles full lab-journal markdown entries without chunking; chunking is still applied for files > 8 KB)
- MTEB DE/EN sub-track recall ≥ 80% top-5 on Wikipedia-DE (cite [MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard), DE+EN dense-retrieval subtracks)

Alternatives considered:

- `BAAI/bge-m3` (568 M, 1024d, MIT, [model card](https://huggingface.co/BAAI/bge-m3)) — better recall (≥85% top-5) but 4× the CPU cost; lands as the **Tier 1 default**
- `intfloat/multilingual-e5-large-instruct` — comparable to BGE-M3; Tier 1 alternative
- `mxbai-embed-large-v1` — English-only; rejected (German-blind for MFFD)
- `all-MiniLM-L6-v2` — smallest viable; 384d; English-only and weaker on technical text; rejected

### 4.3 Hardware floor

| Spec | Tier 0 baseline | Source |
|---|---|---|
| RAM (sidecar) | 500 MB resident + model | TEI README — jina-v2-base-de pinned to ~500 MB |
| CPU | 1 core idle, 4 cores under backfill | TEI benchmarks |
| Disk (model cache volume) | 600 MB (model) + 200 MB (overhead) | HF model card weights |
| Cold-start latency | 10–30 s (model load on first request) | TEI startup logs — mitigated by healthcheck-with-probe-embed |
| Tok/s (Pi 5 ARM64, 4-core busy) | 30–80 | TEI ARM64 benchmark thread |
| Tok/s (modern x86, 8-core busy) | 200–500 single batch; 500–1000 with batching | TEI x86 benchmark README |

The "Pi 5 test." A first-time evaluator on a Raspberry Pi 5 (16 GB) running `make dev` should see embeddings working. This is the smoke test for MUST-Tier-0: if a Pi can't run it, MUST is violated.

### 4.4 mem_limit + STACK-AUDIT-001

Per the synthesis AP-X10 sidecar-hygiene line, every plugin-declared sidecar carries a `mem_limit` rendered into the compose snippet. The Tier 0 spec lands at `mem_limit: 2g` (model resident + batch headroom + Rust runtime + 30% slack). The `SidecarsAssembler` enforces this; a plugin shipping a SidecarSpec without explicit memory will fail render-time validation post-PM1g (filed under `PM1g-SIDECAR-MEMLIMIT-ENFORCEMENT`).

---

## §5 — The EmbeddingProvider SPI

### 5.1 Interface

```java
package de.dlr.shepard.spi.ai;

public interface EmbeddingProvider {

  /** Stable provider identifier; "local-tei", "local-ollama", "openai", "saia", "gwdg", "azure". */
  String providerId();

  /** Model identifier as understood by the provider; e.g. "jinaai/jina-embeddings-v2-base-de". */
  String modelId();

  /** Output vector dimensions (load-bearing — pgvector typed columns depend on this). */
  int dimensions();

  /** True if the provider runs inside the deploy boundary (no public-internet egress). */
  boolean isLocal();

  /** Single-document embedding. */
  EmbeddingResult embed(String text);

  /** Batched embedding; provider may split internally. Order preserved. */
  List<EmbeddingResult> embed(List<String> texts);

  /** Health probe; cached for shepard.ai.health.cache-ttl (default PT30S). */
  HealthStatus health();

  /** USD estimate for tokens; 0.0 for local providers; real for cloud. Drives :Activity.cost_usd. */
  double costEstimateUsd(int tokenCount);
}

public record EmbeddingResult(float[] vector, int tokenCount, Duration latency) {}
```

### 5.2 Three shipping adapters

| Class | Talks to | Activates when |
|---|---|---|
| `LocalTeiEmbeddingProvider` | in-stack `shepard-embeddings:8080` (TEI OpenAI-compat endpoint) | `:AIConfig.embeddingProviderId = "local-tei"` (default) |
| `LocalOllamaEmbeddingProvider` | in-stack `ollama:11434/api/embeddings` | `:AIConfig.embeddingProviderId = "local-ollama"` |
| `OpenAiCompatibleEmbeddingProvider` | configured external URL (SAIA, GWDG, OpenAI, Azure, Cohere, …) | `:AIConfig.embeddingProviderId = "external"` AND `allowExternal = true` |

All three speak the same OpenAI `/v1/embeddings` HTTP shape; the difference is the endpoint URL, the auth header, the model identifier convention, and the `isLocal()` return.

### 5.3 `:AIConfig` (extends the 86 design)

86 introduced `:AiGlobalConfig` + per-capability `:AiCapabilityConfig`. v6 extends `:AiGlobalConfig` with the embedding fields and renames it `:AIConfig` for symmetry with the other A3b configs (`:FeatureToggleRegistry`, `:SemanticConfig`, `:UnhideConfig`):

| Field | Default | Notes |
|---|---|---|
| `embeddingProviderId` | `"local-tei"` | MUST-Tier-0 baseline |
| `embeddingModelId` | `"jinaai/jina-embeddings-v2-base-de"` | resolved from `embedding_models` table |
| `embeddingDim` | `768` | computed from model registry; not user-settable |
| `allowExternalEmbeddings` | `false` | flip-to-true required before any external adapter activates |
| `externalEmbeddingProviderUrl` | `null` | required when `allowExternal=true`; stored as plain text |
| `externalEmbeddingApiKey` | `null` | stored as plain text in v0 (v0b adds encryption-at-rest per 86 §3); never logged; masked as `***` in GET responses |
| `embeddingBatchSize` | `32` | provider-side batch cap |
| `embeddingTimeout` | `PT30S` | per-request timeout |

Selection logic: `EmbeddingProviderRegistry.active()` reads `:AIConfig`, looks up the registered `EmbeddingProvider` bean by `providerId`, validates `dimensions() == embeddingDim`, returns it. External providers refuse activation unless `allowExternal=true`.

### 5.4 Admin REST + CLI parity

| Endpoint | Method | Purpose |
|---|---|---|
| `/v2/admin/ai/embeddings/config` | GET | current `:AIConfig` shape (apiKey masked) |
| `/v2/admin/ai/embeddings/config` | PATCH | RFC 7396 merge-patch; `@RolesAllowed("instance-admin")` |
| `/v2/admin/ai/embeddings/test` | POST | end-to-end probe — embeds "hello world" with current provider, returns `{latency_ms, dim, providerId, modelId, tokenCount}` |
| `/v2/admin/ai/embeddings/models` | GET | list `embedding_models` table |
| `/v2/admin/ai/embeddings/models/{id}/activate` | POST | swap active model + start parallel-model backfill (§14) |

CLI parity per L1 baseline + `feedback_admin_runbooks_pattern.md`:

```
shepard-admin ai embeddings status
shepard-admin ai embeddings test
shepard-admin ai embeddings set-provider local-tei|local-ollama|external
shepard-admin ai embeddings set-model <model-id>
shepard-admin ai embeddings backfill --kind data_object [--since DATE] [--dry-run]
shepard-admin ai embeddings models list
```

All commands accept `--output=human|json --url=... --api-key=...` per the L1 shared-flag convention.

---

## §6 — pgvector substrate (`shepard_ai` schema)

Lands on the collapsed PG instance after synthesis T2 (one PG + N schemas + PgBouncer pool-per-schema). New schema: `shepard_ai`. Migrations live in `plugins/ai/src/main/resources/db/ai/migration/` and are picked up by Flyway via the plugin SPI migration descriptor.

### 6.1 DDL

```sql
-- V1.0.0__pgvector_init.sql
CREATE SCHEMA IF NOT EXISTS shepard_ai;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
GRANT USAGE ON SCHEMA shepard_ai TO shepard;
ALTER ROLE shepard SET search_path TO shepard, public;

-- V1.0.1__embedding_models.sql
CREATE TABLE shepard_ai.embedding_models (
  model_id        TEXT PRIMARY KEY,
  provider_id     TEXT NOT NULL,
  dim             INT  NOT NULL CHECK (dim BETWEEN 64 AND 4096),
  is_local        BOOLEAN NOT NULL,
  registered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  registered_by   TEXT NOT NULL
);

INSERT INTO shepard_ai.embedding_models (model_id, provider_id, dim, is_local, registered_by) VALUES
  ('jinaai/jina-embeddings-v2-base-de', 'local-tei',   768,  true,  'plugin-seed'),
  ('BAAI/bge-m3',                       'local-tei',   1024, true,  'plugin-seed'),
  ('text-embedding-3-small',            'openai',      1536, false, 'plugin-seed'),
  ('text-embedding-3-large',            'openai',      3072, false, 'plugin-seed')
ON CONFLICT (model_id) DO NOTHING;

-- V1.0.2__embeddings_768.sql  (per-dim table; one per active model dim)
CREATE TABLE shepard_ai.embeddings_768 (
  id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  source_kind  TEXT  NOT NULL CHECK (source_kind IN
                  ('data_object','lab_journal_entry','file_chunk','annotation','activity','collection')),
  source_appid UUID  NOT NULL,                  -- foreign reference to Neo4j-side appId; NO FK by design (substrate split)
  chunk_id     INT,                              -- nullable; non-null for chunked sources (file_chunk)
  model_id     TEXT  NOT NULL REFERENCES shepard_ai.embedding_models(model_id),
  embedding    VECTOR(768) NOT NULL,
  text_snippet TEXT,                              -- first 280 chars of embedded text (debug aid)
  token_count  INT   NOT NULL,
  source_hash  TEXT  NOT NULL,                   -- sha256(normalised source text) for staleness
  embedded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source_kind, source_appid, chunk_id, model_id)
);

CREATE INDEX embeddings_768_hnsw
  ON shepard_ai.embeddings_768
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

CREATE INDEX embeddings_768_source     ON shepard_ai.embeddings_768 (source_kind, source_appid);
CREATE INDEX embeddings_768_model      ON shepard_ai.embeddings_768 (model_id);
CREATE INDEX embeddings_768_appid_only ON shepard_ai.embeddings_768 (source_appid);  -- for kNN-then-permission post-filter joins

-- Symmetric per-dim sibling tables are created on demand by the model-registry side-effect:
--   embeddings_1024 (BGE-M3), embeddings_1536 (OpenAI 3-small), embeddings_3072 (OpenAI 3-large)
```

### 6.2 Why per-dim tables (not one polymorphic table)

pgvector's `VECTOR` type is dimension-typed at the column level; the HNSW index is dimension-typed. Mixing 768d + 1024d + 1536d rows in one table would force `vector(N)` where `N = max_dim` and waste storage, **and** force per-row distance calculation across heterogeneous vectors. Per-dim tables keep each HNSW index cleanly typed and let the model-registry layer route writes/reads by `model_id → dim → table`. Cost: a small table dispatcher in `EmbeddingRepository`; benefit: 3–4× storage saving and clean parallel-model swap (§14).

### 6.3 Index choice — HNSW over IVFFlat

HNSW gives ≥98% recall at 10× lower latency than IVFFlat at MFFD scale, per [pgvector benchmarks](https://github.com/pgvector/pgvector#hnsw-vs-ivfflat). IVFFlat needs periodic re-clustering after large bulk inserts; HNSW is incremental. The trade is build-time + memory — both acceptable for the 310k-row MFFD scale (table size projection below).

| Param | Default | Notes |
|---|---|---|
| `m` | 16 | edges per node; pgvector default |
| `ef_construction` | 64 | build-time accuracy; raise for high-recall workloads |
| `ef_search` | 40 | query-time accuracy; SET per session for tunable recall/latency trade |

### 6.4 Scale projection at MFFD shape

| Source kind | Row count | Avg tokens | Total tokens |
|---|---|---|---|
| `data_object` (description + summary) | 17 000 | 200 | 3.4 M |
| `lab_journal_entry` (markdown) | 2 000 | 800 | 1.6 M |
| `file_chunk` (first 8 KB of every text-like file) | 12 000 × ~3 chunks | 1500 | 54 M |
| `annotation` (free-text values, only non-vocabulary) | 5 000 | 50 | 0.25 M |
| `activity` (summaries — opt-in, off-default) | 284 000 | 30 | 8.5 M |
| `collection` (description) | 200 | 300 | 0.06 M |
| **Total embedding rows** | ~67 000 | — | ~68 M |

Storage at 768d: 67 k rows × (768 × 4 B + ~200 B overhead) ≈ **220 MB raw + ~600 MB HNSW index** ≈ **0.8 GB total per active model**. At 1024d (BGE-M3): ≈ 1.1 GB. Cite [pgvector HNSW sizing notes](https://github.com/pgvector/pgvector#index-options).

### 6.5 RLS strategy

**v0:** app-layer post-filter. Cypher fetches each candidate's `:DataObject{appId:$appId}`, runs `PermissionsService.hasReadPermission(user, appId)`, drops candidates the caller can't read. Vulnerable to existence-leak (the candidate count itself reveals row presence) but the snippet/UUID stays unleaked. Acceptable for v0.

**v1+:** Postgres RLS predicate calling a Neo4j foreign-data wrapper (FDW). Synthesis report notes the shape but defers it; file as `AI-V6-PGVECTOR-RLS-DESIGN`. The FDW shape is non-trivial — postgres_fdw doesn't speak Cypher; the candidate is a custom FDW or a permission-projection materialised in `shepard_ai.permission_view`.

### 6.6 Why no FK back to Neo4j

Per `feedback_db_review_all_stores.md` cross-substrate references stay loose. The PG instance and the Neo4j instance are independent processes with independent backup cadences; a foreign key would force ordering on deletes that violates the "delete in Neo4j first" pattern shipped with SM1. Staleness sweep (§14) reconciles drift periodically.

---

## §7 — f(ai)²r typed Activity capture

Per `project_fair2r_integration.md` + `feedback_ai_human_collab_provenance.md`: **every embedding generation lands as a typed `:Activity`**. Shepard's MUST-provenance commitment + the EU AI Act Art-50 transparency obligation for AI-generated artefacts converge on the same shape — the `:Activity` row IS the audit record.

### 7.1 Single-embed shape

```cypher
CREATE (a:Activity {
  appId:            $uuidv7,
  actionKind:       'EMBED',
  agentUsername:    'shepard.actor.local-tei',     // synthetic AI agent username
  agentMode:        'AI',                          // per project_ai_human_collab_provenance.md
  targetKind:       'DataObject',
  targetAppId:      $sourceAppId,
  startedAtMillis:  $startMs,
  endedAtMillis:    $endMs,
  summary:          'Embedded DataObject via local-tei/jina-embeddings-v2-base-de'
})
SET a.attrs = {
  model_id:      'jinaai/jina-embeddings-v2-base-de',
  dim:           768,
  token_count:   $tokens,
  embedding_id:  $pgRowId,
  is_local:      true,
  cost_usd:      0.0
}
```

### 7.2 Batch shape

For backfill (1k–10k rows per batch), one `:Activity{actionKind:'EMBED_BATCH'}` per batch with rollup stats (`row_count`, `total_tokens`, `total_cost_usd`, `provider`, `model_id`). Per-row `:Activity{actionKind:'EMBED'}` rows are **opt-in** via `:AIConfig.captureBatchPerRowActivity` (default `false`) — at 67k rows the per-row capture costs 67k extra Neo4j writes per backfill. The batch row is the default audit grain.

### 7.3 Read-path: `GET /v2/provenance/entity/<appId>`

Existing provenance resource (NEO-AUDIT-001 closes the typed-edge gap; the read path stays additive). Post-shipment, a researcher querying `GET /v2/provenance/entity/<dataobject-appid>` sees the embedding history — which agent embedded the row when, with which model, at what cost, with what latency. This is the EU AI Act Art-50 transparency hook: any user can demand "what AI touched this artefact?" and get a typed answer.

### 7.4 Why this matters

86 §8 already pinned the `:AiActivity` shape for write-back-causing calls. v6 inherits that shape (renamed `:Activity{actionKind in [EMBED, EMBED_BATCH, CHAT, QUERY_RAG, SUMMARISE, CLASSIFY]}` for symmetry with the rest of the provenance graph) and extends it with the cost/dim/model-id columns that embedding workloads need.

---

## §8 — Connected answers — the RAG flow

The "connected answer" promise: pgvector **finds**, Neo4j **connects**, LLM (optional, Tier 1+) **phrases**. Each step is observable in the provenance trail.

### 8.1 Sequence — "What anomaly happened in TR-004?"

1. **User asks** via MCP / UI / `/v2/search/embedding` REST.
2. **Backend embeds the question** via active `EmbeddingProvider`. Tier 0 path: HTTP `POST shepard-embeddings:8080/embed → 768d vector` in 50–150 ms.
3. **PG kNN query:**
   ```sql
   SELECT source_kind, source_appid, chunk_id, text_snippet,
          1 - (embedding <=> $1) AS similarity
   FROM shepard_ai.embeddings_768
   WHERE model_id = $2
     AND 1 - (embedding <=> $1) > 0.7
   ORDER BY embedding <=> $1
   LIMIT 20;
   ```
   At MFFD scale on a moderately-sized PG: **P95 < 50 ms** with HNSW (cite [pgvector benchmark write-up](https://github.com/pgvector/pgvector#performance)).
4. **App-layer permission post-filter** (drops candidates the caller can't read).
5. **Neo4j graph walk** — single batched Cypher fetches each surviving candidate's `:DataObject` + `:Annotations` + recent `:Activity` rows + `:Predecessor`/`:Successor` neighbours.
6. **Result composition:**
   - **Tier 0 deploy (no chat):** return ranked snippets with cite-back UUIDs; UI / MCP client composes the prose.
   - **Tier 1+ chat configured:** compose answer via active `ChatProvider`; cite-back UUIDs embedded in the response.
7. **Composite `:Activity{actionKind:'QUERY_RAG'}`** links the embedding `:Activity`, the chat `:Activity` (if any), and the surfaced DataObject UUIDs in `targets[]`. Single-row provenance for the query as a whole.

### 8.2 What this enables

- **MCP path** — `search_by_embedding` returns ranked candidates without an LLM. Claude/Cody/Continue compose the answer from Shepard data; Shepard provides the substrate.
- **UI path** — "Find similar" affordance on the DataObject + LabJournal detail pages; ranked neighbours with similarity %.
- **Wiki-writer path** — at Tier 1+, the wiki-writer plugin (86 §12) calls `search_by_embedding` to gather context, then composes a wiki page via `ChatProvider`.

The cite-back UUIDs are the load-bearing piece: every retrieved snippet carries the source `appId` so the calling layer can hyperlink back to the canonical entity. **No hallucinated references** — the LLM works against retrieved evidence with concrete IDs.

---

## §9 — ChatProvider SPI (Phase 2 — sketch only)

86's `LlmProvider` is the chat-side stability contract; v6 splits it into the symmetric two-SPI shape (`EmbeddingProvider` for §5, `ChatProvider` for here) so capability-specific tuning is independent. Sketch:

```java
public interface ChatProvider {
  String providerId();
  String modelId();
  int contextWindow();
  boolean isLocal();
  ChatResult chat(List<Message> history, ChatParams params);   // synchronous
  Stream<ChatChunk> chatStream(List<Message> history, ChatParams params);   // SSE / streaming
  HealthStatus health();
  Cost costEstimate(int inputTokens, int outputTokens);
}
```

Same four-tier model. **The honest bit:** Tier 0 chat is **HARD**. The smallest usable instruction-tuned models are 3–8 B parameters; CPU inference on those at usable interactive latency requires either quantisation tricks (Q4_K_M GGUF via llama.cpp — ~5 tok/s on modern x86) or "good enough for batch" tolerance (1–3 tok/s for a multi-paragraph wiki page = 30–60 seconds per generation — unusable interactively).

**Where MUST-Tier-0 relaxes for chat.** The relaxation is explicit:

- **Embeddings MUST work at Tier 0** — non-negotiable; the §4 sidecar shipment honours this.
- **Chat MAY require Tier 1+** — Wiki-writer + similar features are documented as "Tier 1+ recommended" in their install pages. The SAIA/GWDG path is the easiest Tier 1+ option for DLR operators; the same `OpenAiCompatibleChatProvider` adapter works against either.

Three adapters planned: `LocalOllamaChatProvider`, `LocalVllmChatProvider`, `OpenAiCompatibleChatProvider`. Per-call slot config (endpoint, model, temperature, max_tokens, guardrails) inherits the 86 §3 capability-registry shape unchanged.

---

## §10 — Performance + scale numbers

Every claim cites a primary source.

### 10.1 Tier 0 throughput (CPU)

| Hardware | Single-batch tok/s | Batched (size 32) tok/s | Source |
|---|---|---|---|
| Modern x86 8-core (AMD 7950X) | 200–300 | 500–1000 | [TEI x86 benchmarks](https://github.com/huggingface/text-embeddings-inference#docker) |
| Raspberry Pi 5 (ARM 4-core) | 30–80 | 80–200 | community TEI ARM thread (HF discussion) |
| Apple M1 (8-core) | 250–400 | 600–1200 | HF model card benchmark column |

### 10.2 Tier 1 throughput (GPU)

| Hardware | tok/s | Source |
|---|---|---|
| RTX 4090 | 3000–5000 | [TEI GPU benchmarks](https://github.com/huggingface/text-embeddings-inference#cuda) |
| A6000 | 4000–7000 | TEI GPU benchmarks |
| A100 80 GB | 8000–15000 | TEI GPU benchmarks |

### 10.3 MFFD backfill timing (68 M tokens)

| Tier | Hardware | Estimated time |
|---|---|---|
| 0 (8-core x86, batch 32) | 1000 tok/s | **~19 h** |
| 0 (Pi 5, batch 32) | 150 tok/s | **~5 days** |
| 1 (RTX 4090, batch 64) | 4000 tok/s | **~5 h** |
| 1 (A6000) | 6000 tok/s | **~3 h** |
| 2 (SAIA/GWDG networked GPU) | varies | hours to a day |
| 3 (OpenAI text-embedding-3-small) | API-rate-limited | ~24 h at 5 RPM/min budget |

(Note the §3 9-day figure was for the early 155 M-token estimate including all activity rows; §10.3 uses the refined §6.4 figure of 68 M tokens with `activity` opt-in defaulting off.)

### 10.4 Storage at MFFD scale

| Model | Active rows | Per-row | HNSW overhead | Total per active model |
|---|---|---|---|---|
| jina-v2-base-de (768d) | 67 000 | 768 × 4 B + 200 B = 3.3 KB | ~2.2× | ~0.8 GB |
| BGE-M3 (1024d) | 67 000 | 1024 × 4 B + 200 B = 4.3 KB | ~2.2× | ~1.1 GB |
| OpenAI 3-small (1536d) | 67 000 | 1536 × 4 B + 200 B = 6.3 KB | ~2.2× | ~1.6 GB |

Cite [pgvector index sizing notes](https://github.com/pgvector/pgvector#index-options).

### 10.5 Query latency

P95 < 50 ms at 67k rows with HNSW (`ef_search=40`); P99 < 100 ms. Cite [Crunchy Data pgvector HNSW benchmark](https://www.crunchydata.com/blog/hnsw-indexes-with-postgres-and-pgvector) for the order-of-magnitude.

### 10.6 MTEB recall (DE+EN sub-tracks)

| Model | Top-5 recall (Wikipedia-DE) | Top-5 recall (MIRACL-en) | Source |
|---|---|---|---|
| jina-v2-base-de | ≥ 80% | ≥ 78% | [MTEB leaderboard](https://huggingface.co/spaces/mteb/leaderboard), `de` + `en` sub-tracks |
| BGE-M3 | ≥ 85% | ≥ 83% | MTEB leaderboard `multilingual` |
| OpenAI 3-small | ≥ 82% | ≥ 80% | MTEB leaderboard (community submissions) |

### 10.7 Cost reality (Tier 3)

OpenAI `text-embedding-3-small`: **$0.020 / 1 M tokens** ([pricing](https://openai.com/api/pricing/)). MFFD backfill (68 M tokens) = **$1.36**; steady-state ≈ **$1/year**. **The cost is not the driver for self-hosting**; the drivers are IP, reproducibility, friction (§3 closing).

---

## §11 — Default compose changes

Diff against `infrastructure/docker-compose.yml`:

```yaml
services:
  shepard-embeddings:
    image: ghcr.io/huggingface/text-embeddings-inference:cpu-1.5
    container_name: shepard-embeddings
    restart: unless-stopped
    environment:
      MODEL_ID: jinaai/jina-embeddings-v2-base-de
      REVISION: main
      MAX_BATCH_TOKENS: "16384"
      MAX_CONCURRENT_REQUESTS: "32"
      HF_HOME: /data
    volumes:
      - shepard_embedding_models:/data
    ports: []                                    # in-stack only — no host exposure
    networks: [shepard-net]
    mem_limit: 2g                                # STACK-AUDIT-001 hygiene
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS -X POST http://localhost:8080/embed -H 'content-type: application/json' -d '{\"inputs\":\"healthcheck\"}' >/dev/null"]
      interval: 45s
      timeout: 10s
      retries: 5

  shepard-backend:
    environment:
      SHEPARD_AI_EMBEDDING_PROVIDER: local-tei
      SHEPARD_AI_EMBEDDING_ENDPOINT: http://shepard-embeddings:8080
      SHEPARD_AI_EMBEDDING_MODEL_ID: jinaai/jina-embeddings-v2-base-de
      SHEPARD_AI_EMBEDDING_DIM: "768"
    depends_on:
      shepard-embeddings:
        condition: service_healthy               # backfill orchestration waits for embeddings ready

volumes:
  shepard_embedding_models:                      # ~800 MB after first model load
```

**Posture per PM1f.** This block is **rendered by `SidecarsAssembler`** from `AiPluginManifest.sidecars()` — not hand-written into central compose. The diff above is what an operator pastes after running `scripts/activate-plugin-sidecars.sh ai`. This is the second PM1f use case after spatial + hdf5 — fold into the same PM1f migration PR if scheduling allows (filed as `PM1f-MIGRATION-AI-2026-05-24`).

---

## §12 — Plugin shape

```
plugins/ai/
├── pom.xml
├── docs/
│   ├── reference.md             # full SPI + REST + config + entities + CLI
│   ├── quickstart.md            # "How do I search by similarity?"
│   └── install.md               # operator: sidecar declaration + admin REST + config
├── src/main/java/de/dlr/shepard/plugins/ai/
│   ├── AiPluginManifest.java                                    # PluginManifest + sidecars()
│   ├── AiPayloadKind.java                                       # entity package registration
│   ├── embedding/
│   │   ├── EmbeddingProvider.java                               # SPI (interface)
│   │   ├── EmbeddingResult.java
│   │   ├── LocalTeiEmbeddingProvider.java                       # default
│   │   ├── LocalOllamaEmbeddingProvider.java
│   │   ├── OpenAiCompatibleEmbeddingProvider.java
│   │   ├── EmbeddingProviderRegistry.java                       # selects active from :AIConfig
│   │   └── EmbeddingBackfillService.java                        # batched, resumable, :Activity-emitting
│   ├── chat/                                                    # Phase 2 — sketch only
│   │   ├── ChatProvider.java
│   │   ├── LocalOllamaChatProvider.java
│   │   ├── LocalVllmChatProvider.java
│   │   └── OpenAiCompatibleChatProvider.java
│   ├── persistence/
│   │   ├── EmbeddingRepository.java                             # routes by dim → per-dim table
│   │   ├── EmbeddingRow.java
│   │   └── EmbeddingModelRegistry.java
│   ├── rest/
│   │   ├── AdminAiEmbeddingsRest.java                           # /v2/admin/ai/embeddings/*
│   │   └── SearchByEmbeddingRest.java                           # /v2/search/embedding
│   ├── mcp/
│   │   └── SearchByEmbeddingTool.java                           # registers with MCP registry
│   ├── provenance/
│   │   └── EmbeddingActivityCapture.java                        # writes :Activity per embed
│   └── config/
│       └── AiConfig.java                                        # :AIConfig OGM entity (replaces :AiGlobalConfig)
├── src/main/resources/
│   ├── META-INF/services/de.dlr.shepard.plugin.PluginManifest
│   └── db/ai/migration/
│       ├── V1.0.0__pgvector_init.sql
│       ├── V1.0.1__embedding_models.sql
│       └── V1.0.2__embeddings_768.sql
└── src/test/java/de/dlr/shepard/plugins/ai/
    ├── AiPluginManifestSidecarsTest.java                        # PM1f shape validation
    ├── LocalTeiEmbeddingProviderTest.java                       # WireMock-backed
    ├── EmbeddingRepositoryIT.java                               # @QuarkusTest + pgvector testcontainer
    ├── AdminAiEmbeddingsRestTest.java
    ├── EmbeddingBackfillServiceTest.java                        # resume semantics + :Activity capture
    └── SearchByEmbeddingMcpToolTest.java
```

Existing `plugins/ai/` scaffold (`AiCapabilityConfig`, `LlmProviderImpl`, `OpenAiCompatClient`, `AiAdminRest`) **stays in place and is realigned by `AI-V6-012`**; see §14.

---

## §13 — MCP integration

Per `aidocs/integrations/30 §3` tool-registration shape. New MCP tool:

```json
{
  "name": "search_by_embedding",
  "description": "Semantic search across Shepard entities by embedding similarity. Returns ranked candidates (DataObjects, LabJournalEntries, file chunks, annotations) with similarity scores and back-pointer appIds. Use when a keyword search would miss synonyms or paraphrases. Combine with get_data_object / get_collection for connected answers.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query":        { "type": "string",  "description": "Free-text query; embedded server-side via the configured provider." },
      "top_k":        { "type": "integer", "minimum": 1, "maximum": 50, "default": 10 },
      "source_kinds": {
        "type": "array",
        "items": { "enum": ["data_object","lab_journal_entry","file_chunk","annotation","activity","collection"] },
        "default": ["data_object","lab_journal_entry"]
      },
      "min_similarity": { "type": "number", "minimum": 0, "maximum": 1, "default": 0.7 }
    },
    "required": ["query"]
  }
}
```

Output: array of `{source_kind, source_appid, similarity, text_snippet, dataobject_appid?, lab_journal_appid?}`. The MCP client (Claude / Cody / Continue) chains this with `get_data_object` / `get_lab_journal` for the full content. Tool descriptions follow [MCP spec best practice](https://modelcontextprotocol.io/specification) (verb-first, capability-not-implementation, declared limits).

---

## §14 — Honest concerns + open questions

| # | Concern | Mitigation | Backlog |
|---|---|---|---|
| 1 | **Cold-start latency** — TEI takes 10–30s to load the model on first request | Healthcheck includes a real-embed probe; sidecar isn't UP until model is loaded | folded into AI-V6-003 |
| 2 | **Model swap cost** — switching `model_id` makes existing embeddings unreachable until backfill completes | Parallel-model strategy: run both old + new `model_id` rows simultaneously during transition; cut over via `:AIConfig.embeddingModelId` flip once new backfill complete; GC old rows after grace window | AI-V6-013 |
| 3 | **RLS gap** — app-layer post-filter is racy under concurrent permission changes | Acceptable for v0 (existence-leak only, content stays protected); v1 considers PG RLS + Neo4j FDW or materialised permission view | AI-V6-PGVECTOR-RLS-DESIGN |
| 4 | **Source-hash staleness** — embeddings drift from source as text mutates | Background job in the AI plugin recomputes `source_hash` on a configurable cadence (default daily); admin can trigger via REST; flagged rows get re-embedded | AI-V6-014 |
| 5 | **Cross-language consistency** — bilingual queries may underperform monolingual in jina-v2 | Document the trade in install.md; suggest translation-then-embed for production cross-lingual workloads | AI-V6-015 |
| 6 | **Tier 0 chat is hard** — CPU inference on 3–8B models is 1–5 tok/s | Explicit relaxation in §9; Wiki-writer install.md documents "Tier 1+ recommended"; SAIA/GWDG is the easiest path for DLR ops | folded into ChatProvider Phase 2 |
| 7 | **TEI vs Ollama** — both ship; which is recommended baseline? | Recommend TEI (purpose-built for embeddings, higher throughput per resource). Ollama is for operators already running it for chat | docs/install.md decision matrix |
| 8 | **Vendor neutrality vs operator simplicity** — three adapters means three maintenance surfaces | The MUST-Tier-0 commitment dictates at least two (LocalTei + LocalOllama); cloud adapter is shared infrastructure via OpenAI-compat wire | acknowledged |
| 9 | **Existing `plugins/ai/` scaffold does NOT satisfy MUST-Tier-0** — `AiCapabilityConfig.endpointUrl + apiKey` are required fields; out-of-box the TEXT capability is unusable until admin configures external | Plugin code stays untouched per prompt scope; realignment filed as separate row | **AI-V6-012** |
| 10 | **Audit-fleet gap** — pgvector + embedding-pipeline readiness is not in synthesis §7's blind-spot list but should have been | File the audit before v0 ships; substrate-direct (`psql shepard_ai`), pool sizing, HNSW build-time at MFFD scale, mem_limit profiling under sustained query load | **AI-V6-010** |
| 11 | **SAIA migration path** — when SAIA ships its OpenAI-compatible DLR-internal endpoint, what changes? | `OpenAiCompatibleEmbeddingProvider` works unchanged; admin PATCHes `:AIConfig.externalEmbeddingProviderUrl`; flips `allowExternal=true` (note: "external" is wire-shape-driven not network-topology-driven). Documented in install.md | docs/install.md |
| 12 | **Text-only scope** — image embeddings + audio embeddings + multimodal are future work | Out of v6 scope; flag for Phase 3 (multimodal plugin); CLIP-style image embeddings would land as `embeddings_512` per-dim table | AI-V6-FUTURE-MULTIMODAL |
| 13 | **86's BYOK chain (`User → Instance`)** — does it carry forward? | Yes, unchanged. v6 adds the local-provider tier above instance-level external; the chain reads `Local-default → User-override → Instance-external → 503-unconfigured` | folded into AI-V6-005 |
| 14 | **`storePromptText` field from 86 §8** — applies to embeddings too? | Yes — `:AIConfig.storeQueryText` controls whether `search_by_embedding` query text lands in `:Activity.attrs.query` or a SHA-256 hash. Default `false` (privacy-default) | folded into AI-V6-006 |

---

## §15 — v0 / v1 / v2 milestone breakdown

### v0 (MVP — the "MUST-Tier-0 ships" milestone)

**Scope.** TEI sidecar in default compose via `SidecarsAssembler`; `LocalTeiEmbeddingProvider` + `OpenAiCompatibleEmbeddingProvider` adapters; pgvector `shepard_ai` schema + Flyway migrations; `:AIConfig` admin REST + CLI parity; embedding backfill orchestration with resume semantics; `search_by_embedding` MCP tool; f(ai)²r `:Activity` capture; docs trinity (reference + quickstart + install).

**Test obligations** per `feedback_always_write_tests.md`:
- `AiPluginManifestSidecarsTest` — PM1f shape validates render-time
- `LocalTeiEmbeddingProviderTest` — WireMock-backed HTTP shape
- `EmbeddingRepositoryIT` — pgvector testcontainer; HNSW index sanity + parallel-model isolation
- `AdminAiEmbeddingsRestTest` — config CRUD + test endpoint + auth gates
- `EmbeddingBackfillServiceTest` — resume + idempotency + `:Activity` capture
- `SearchByEmbeddingMcpToolTest` — tool registration + input validation + permission post-filter

**Acceptance.** `make dev` → run LUMEN seed → `shepard-admin ai embeddings backfill --kind data_object` completes → `curl /v2/search/embedding?q=propellant+anomaly` returns ranked top-5 with TR-004 in position 1. **Without a single API key configured anywhere.**

**Docs trinity** per `feedback_plugins_ship_own_docs.md`: `plugins/ai/docs/{reference,quickstart,install}.md` — all three updated this milestone.

### v1 (the "ergonomic Tier 0 + parallel-model swap" milestone)

`LocalOllamaEmbeddingProvider`; "Find similar" frontend affordance on DataObject + LabJournal + Collection detail pages; parallel-model swap workflow with read-cut-over; staleness-sweep background job (`AiStalenessSweepScheduler`); admin REST for re-embedding orchestration; metrics (`shepard_ai_embeddings_total`, `shepard_ai_embedding_latency_seconds`, `shepard_ai_query_latency_seconds`) per `feedback_shepard_measures_itself.md`.

### v2 (the "chat lands" milestone)

`ChatProvider` SPI; `LocalOllamaChatProvider` + `LocalVllmChatProvider` + `OpenAiCompatibleChatProvider`; integration with Wiki-writer (86 §12); MCP `ask_with_context` tool composing kNN-retrieval + chat; PG RLS predicate evaluation (or materialised permission view); guardrails-prefix migration from 86 §6 unchanged.

Per CLAUDE.md "plugin-first": every milestone touches `plugins/ai/`; nothing leaks into `backend/`'s closed surface (auth, permissions, identity, runtime SPI registry).

---

## §16 — Decisions log

| Decision | Alternatives | Decisive constraint | Cut |
|---|---|---|---|
| Tier 0 model = jina-v2-base-de | all-MiniLM-L12-v2 (English-only); BGE-small (English+Chinese-focus) | DE+EN bilingual hard requirement for MFFD | jina-v2 |
| Tier 0 sidecar = TEI | llama.cpp; Ollama; DJL in-process | purpose-built throughput + Apache-2.0 + OpenAI-compat wire | TEI |
| Vector index = HNSW | IVFFlat | recall + incremental insert (MFFD scale 67k → 300k as Activity opt-in flips) | HNSW |
| Vector substrate = pgvector | Qdrant; Weaviate; Pinecone; Chroma | synthesis AP-X9 — no new substrates; T2 collapses to one PG | pgvector |
| Java abstraction = Quarkus LangChain4j | Spring AI; hand-rolled HTTP | stack alignment (Quarkus is the fork's runtime); future chat work reuses beans | Quarkus LangChain4j |
| Sidecar over in-process | DJL with bundled ONNX runtime | classpath isolation + Tier 0 simplicity + 200 MB JAR-size penalty rejected | sidecar |
| Per-dim tables | one polymorphic VECTOR(max_dim) table | HNSW is dimension-typed; storage cost; parallel-model swap clean | per-dim |
| No FK to Neo4j | application-enforced FK | substrate split (cross-substrate refs stay loose per `feedback_db_review_all_stores.md`) | no FK; staleness sweep reconciles |
| Default `allowExternal=false` | Default `true` for "easy demo" | MUST-Tier-0 commitment; IP sovereignty default | `false` |
| Default `captureBatchPerRowActivity=false` | per-row capture always | 67k extra Neo4j writes per backfill is observability theatre at this scale | batch grain default |
| Capability registry from 86 | inline per-call config | 86's contract carries forward — adopt unchanged | inherit 86 §3 |

---

## §17 — Backlog rows

New `AI-V6-*` section to add to `aidocs/16-dispatcher-backlog.md`:

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| AI-V6-001 | v0 plugin scaffold realignment + TEI sidecar declaration (PM1f) | M | queued | Pair with `PM1f-MIGRATION-AI-2026-05-24` |
| AI-V6-002 | pgvector schema migration (`shepard_ai`, models registry, embeddings_768) | S | queued | Gated on synthesis T2 PG-collapse |
| AI-V6-003 | `LocalTeiEmbeddingProvider` adapter + WireMock tests | S | queued | Healthcheck-with-probe-embed included |
| AI-V6-004 | `OpenAiCompatibleEmbeddingProvider` (gated by `allowExternal=true`) | S | queued | Same wire for SAIA/GWDG/OpenAI/Azure |
| AI-V6-005 | `:AIConfig` singleton + admin REST + CLI parity (`shepard-admin ai embeddings`) | M | queued | A3b pattern; merges existing `:AiGlobalConfig` from 86 |
| AI-V6-006 | Embedding backfill orchestration with resume + `:Activity` batch capture | M | queued | Per-row capture opt-in via `:AIConfig.captureBatchPerRowActivity` |
| AI-V6-007 | `search_by_embedding` MCP tool + REST `/v2/search/embedding` | S | queued | Cite-back UUIDs in response |
| AI-V6-008 | "Find similar" frontend affordance (DataObject + LabJournal + Collection detail) | M | queued (v1) | Vue3 + Vuetify3 ranked-list component |
| AI-V6-009 | `ChatProvider` SPI design + Phase 2 adapter shipment | L | designed (v2) | Sketched in §9; full design doc when first consumer (Wiki-writer) blocks |
| AI-V6-010 | pgvector + embedding-pipeline readiness audit (closes synthesis-§7-shaped blind spot) | M | queued | **Must complete before v0 ships** — substrate-direct probes per `feedback_db_audit_snappy.md` |
| AI-V6-011 | CLAUDE.md standing-rule formalisation ("Always: ship a working local default for every AI capability") | XS | queued | Same PR as v0 ships |
| AI-V6-012 | Realign existing `plugins/ai/` scaffold to v6 local-default (split `endpointUrl` semantics; default `transport=LOCAL_TEI`; make `apiKey` optional) | M | queued | Without this, MUST-Tier-0 isn't real — out-of-box TEXT is unusable. Filed by §14 #9 |
| AI-V6-013 | Parallel-model swap workflow (run both old + new model_id; cut-over via `:AIConfig`; GC old rows after grace) | M | queued (v1) | Read-path filters on `:AIConfig.embeddingModelId` |
| AI-V6-014 | Staleness-sweep background job (`source_hash` mismatch → re-embed queue) | S | queued (v1) | Configurable cadence; default daily |
| AI-V6-015 | Cross-language consistency documentation + translation-then-embed pattern | XS | queued | docs/reference.md addendum |
| AI-V6-PGVECTOR-RLS-DESIGN | PG RLS predicate calling Neo4j FDW OR materialised permission view | L | design open | v1+ work; race in v0's app-layer post-filter is acceptable for now |
| AI-V6-FUTURE-MULTIMODAL | Image embeddings (CLIP-style) + audio + multimodal — `embeddings_512` per-dim table | L | future | Out of v6 scope; flag for Phase 3 |
| PM1f-MIGRATION-AI-2026-05-24 | Fold AI sidecar into the PM1f sidecar-declaration migration alongside spatial + hdf5 | S | queued | Synthesis AP-X10 closes for AI in the same PR |
| PM1g-SIDECAR-MEMLIMIT-ENFORCEMENT | `SidecarsAssembler` rejects SidecarSpec without explicit `mem_limit` | S | queued | STACK-AUDIT-001 hygiene; applies to all PM1f users |

---

## §18 — Vision currency

Per CLAUDE.md "Always: keep `aidocs/42-vision.md` current". The v6 AI shipment requires these edits to land in the same PR as the v0 (AI-V6-001) shipment:

1. **Move "AI search + RAG" from "Where it's going (near horizon)" to "What's in the box (today)"** with the line:
   > *"Semantic search (vector + keyword hybrid) — ships local-default via `shepard-plugin-ai` (TEI sidecar + jina-v2-base-de, zero external dependencies). Optional escalation to GPU sidecar, SAIA/GWDG, or OpenAI."*
2. **Add a new "Cross-cutting features" bullet under §"f(ai)²r in practice"**:
   > *"Every embedding, chat, classification, and summarisation lands as a typed `:Activity` row with model identifier, token count, cost (when applicable), and latency. Closes EU AI Act Art-50 transparency for AI-touched artefacts."*
3. **Update the §"Plugin ecosystem" line on `shepard-plugin-ai`** from the 86-shaped "LLM gateway plugin" to:
   > *"`shepard-plugin-ai` — local-default AI capability surface. Embeddings + chat + classification via swappable provider adapters (LocalTei, LocalOllama, OpenAiCompatible). MUST: ships with zero external dependencies; cloud is admin-gated opt-in."*
4. **Do NOT add a new payload-kind-table row** — embeddings are indexing infrastructure, not a payload kind. (Audit check.)
5. **Do NOT modify the §"Honest gaps" section yet** — leave the AI-as-gap line until v0 actually ships; flip it then.

The `aidocs/44-fork-vs-upstream-feature-matrix.md` change: flip the AI row from `📐 designed` to `🚧 in-flight` on the same PR as v0 lands; flip to `✓ shipped` when AI-V6-001 + AI-V6-002 + AI-V6-003 are all merged.

The `aidocs/34-upstream-upgrade-path.md` change: new row noting that v6 adds the `shepard-embeddings` sidecar to the default compose; admin upgrading from upstream sees the new service; `:AIConfig` defaults are safe (local-only); operators wanting external must explicitly flip `allowExternal=true`.

---
