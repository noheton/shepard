---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Quarkus Extension Ecosystem — Opportunities for Shepard

**Date:** 2026-05-21  
**Scope:** Quarkus 3.x extension landscape assessed against Shepard's current pom.xml (Quarkus 3.27.2 LTS) and roadmap (`aidocs/44`)  
**Method:** pom.xml audit + Quarkiverse docs + GitHub release tracking + aidocs roadmap cross-reference

---

## What I found

### Current Quarkus footprint (backend/pom.xml, Quarkus 3.27.2 LTS)

Already present and well-configured:

| Extension | Version | Role |
|---|---|---|
| quarkus-rest + quarkus-rest-jackson | BOM | REST surface (JAX-RS v3) |
| quarkus-rest-client-jackson | BOM | Outbound HTTP (Unhide, DataCite, etc.) |
| quarkus-mongodb-client | BOM | MongoDB (file container metadata) |
| quarkus-hibernate-orm-panache + quarkus-flyway | BOM | Postgres ORM + migrations |
| quarkus-jdbc-postgresql | BOM | Postgres (TimescaleDB) |
| quarkus-cache | BOM | In-process cache (Caffeine-backed) |
| quarkus-micrometer-registry-prometheus | BOM | Metrics (Prometheus scrape endpoint) |
| quarkus-smallrye-openapi | BOM | OpenAPI 3.0 docs |
| quarkus-smallrye-health | BOM | `/q/health` readiness/liveness |
| quarkus-scheduler | BOM | In-process cron scheduling |
| quarkus-container-image-docker | BOM | Docker image build |
| quarkus-jacoco | BOM | JaCoCo coverage gate |

Third-party (non-Quarkus, already present): neo4j-ogm 5.0.3, neo4j-cypher-dsl 2025.2.4, neo4j-migrations 3.2.1, jjwt 0.11.5 (custom JWT filter), uuid-creator 6.1.1, ro-crate-java 2.1.0, commonmark 0.24.0, opencsv 5.12.0, archunit-junit5 1.3.0.

### Plugin ecosystem (`with-plugins` Maven profile, pom.xml lines 781–963)

Ten plugin JARs are added to the backend's **compile-time classpath** at build time: `shepard-plugin-unhide`, `-kip`, `-minter-local`, `-minter-datacite`, `-minter-epic`, `-spatial`, `-hdf5`, `-git`, `-file-s3`, `-aas`, `-video`. Quarkus's build-time CDI scanner discovers `@Path` resources from these JARs. This is the mechanism that makes `@Tool` on plugin CDI beans viable (see MCP assessment below).

### Confirmed absences (relevant to roadmap gaps)

| Missing extension | Roadmap item blocked |
|---|---|
| `quarkus-opentelemetry` | Observability — traces missing entirely; Micrometer-OTel bridge unavailable |
| `quarkus-oidc` | Auth uses hand-rolled `JWTFilter` + jjwt; no quarkus-managed OIDC flow |
| `io.quarkiverse.amazonservices:quarkus-amazon-s3` | FS1b plugin hand-rolls AWS SDK v2; no Dev Services, no native compile |
| `quarkus-messaging-kafka` | NTF1 notification system has no transport layer |
| `quarkus-langchain4j:*` | `shepard-plugin-ai` (aidocs/86) has no SDK backing yet |
| `quarkus-kubernetes` / `quarkus-helm` | No generated K8s manifests |
| `quarkus-redis-client` | `quarkus-cache` is in-process only — won't scale horizontally |
| `quarkus-quartz` | `quarkus-scheduler` has no persistence/cluster coordination |
| `quarkus-mcp-server-http` | MCP served by a separate Python FastMCP sidecar process |

### Python FastMCP sidecar (current MCP state)

`aidocs/platform/30-mcp-plugin-design.md` proposes a `shepard-plugin-mcp` Python Docker image with a `McpToolProvider` SPI (`mcp_tools.py` per plugin, discovered via `SHEPARD_PLUGIN_DIRS` env). Transport: SSE. Bearer token forwarding to backend. Full tool inventory designed in §3 of that doc.

---

## Opportunities (ordered by value)

### 1. Migrate Python MCP sidecar → `quarkus-mcp-server-http` 1.12.1

**Maven:** `io.quarkiverse.mcp:quarkus-mcp-server-http:1.12.1`  
**MCP spec:** 2025-11-25 (Streamable HTTP transport — SSE is deprecated as of MCP 2025-03-26)  
**Stability:** 1.11.0 marked stable; 1.12.x adds OpenTelemetry tracing

**What it gives Shepard:**
- `@Tool` annotation on any CDI bean method — no configuration, no runtime registry
- `@ToolArg` for parameter descriptions (populates the tool schema agents see)
- Injectable context per tool: `McpLog`, `McpConnection`, `RequestId`
- Security: `@Authenticated` / `@RolesAllowed` via quarkus-oidc bearer token — same Keycloak setup Shepard already uses
- Build-time tool discovery: all tools aggregated from backend + all plugin JARs at compile time

**Why the with-plugins model makes this viable:**  
Quarkus's build-time CDI scanner discovers `@Path` REST resources from plugin JARs on the compile-time classpath (the `with-plugins` profile). It would discover `@Tool` methods identically. A plugin CDI bean with `@ApplicationScoped` + `@Tool` methods would be registered into the MCP server at build time — no runtime reflection, no Python SPI loader. This makes the Python `McpToolProvider` SPI in aidocs/30 redundant.

**Example (what a plugin tool looks like):**
```java
@ApplicationScoped
public class TimeseriesMcpTools {

    @Inject TimeseriesV2Service timeseriesService;

    @Tool(description = "List all timeseries channels for a container. Returns channelCount, timeRange, and appId for each channel. Call get_channel_data next.")
    List<ChannelSummaryIO> listChannels(
        @ToolArg(description = "AppId (UUID v7) of the timeseries container") String containerAppId
    ) {
        return timeseriesService.listChannelsByContainerAppId(containerAppId);
    }
}
```

**Migration path:**
- MCP-1a: collections + data object tools — `CollectionMcpTools`, `DataObjectMcpTools` in backend
- MCP-1b: timeseries tools — `TimeseriesMcpTools` in backend or timeseries plugin
- MCP-1c: file + structured data tools
- Plugin contributions: each `shepard-plugin-*` adds its own `*McpTools` CDI bean — `VideoMcpTools`, `SpatialMcpTools`, etc. — picked up by `with-plugins` at build time

**What goes away:** the Python sidecar container, `SHEPARD_PLUGIN_DIRS` env var, the `McpToolProvider` SPI, a separate Docker image, a separate Zoraxy proxy rule. MCP becomes a Quarkus module. The `/mcp` path remains; Zoraxy still proxies — just to the Java backend.

**Effort:** Medium (MCP-1a + MCP-1b in ~1 sprint; plugin tools additive after that)

---

### 2. `quarkus-langchain4j` 1.10.0 — implement `shepard-plugin-ai` (aidocs/86)

**Maven (core):** `io.quarkiverse.langchain4j:quarkus-langchain4j:1.10.0`  
**Providers available:** Anthropic (Claude), OpenAI, Ollama, Azure OpenAI, Gemini, Jlama (local), HuggingFace, watsonx.ai  
**Vector stores in Shepard's existing stack:**
- `quarkus-langchain4j-pgvector` — uses existing Agroal datasource; auto-creates table; JSONB metadata
- `quarkus-langchain4j-neo4j` — uses existing Neo4j; no additional infrastructure

**What it gives Shepard:**

The aidocs/86 capability model (TEXT, FAST_TEXT, IMAGE_GEN, VISION, EMBEDDING, STRUCTURED) maps directly to langchain4j's `@RegisterAiService` declarative interface pattern. The admin-configurable capability slots (`GET/PATCH /v2/admin/ai/capabilities/{capability}`) become runtime config properties that override the langchain4j provider binding.

```java
// Declares the capability; admin config selects the model at runtime
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface TextCapabilityService {
    String complete(@SystemMessage String systemPrompt, @UserMessage String userInstruction);
}
```

The layered prompt structure from aidocs/86 §5 (guardrailsPrefix → pluginSystemPrompt → guardrailsSuffix → trustedContext → untrustedDocuments → userInstruction) is implementable as a structured `AiMessage` builder wrapping the declarative service.

**Embedding for semantic DataObject search:** EMBEDDING capability → `EmbeddingStore<TextSegment>` backed by either PgVector or Neo4j (no new infrastructure). Semantic search over DataObject name + description + attribute values. First consumer: `search_data_objects` MCP tool returning semantically ranked results.

**MCP client mode:** langchain4j 1.10.0 includes an MCP client — the loopback case from aidocs/86 §7 (shepard-plugin-ai acting as MCP client to shepard-plugin-mcp for wiki-writer) is supported natively.

**Effort:** Medium-high (implementing the capability config model + provenance `:AiActivity` node); the SDK integration itself is low friction

---

### 3. `quarkus-opentelemetry` — close the observability gap

**Maven:** `io.quarkus:quarkus-opentelemetry` (BOM-managed, Quarkus 3.x)  
**Quarkus 3.27.2 new:** Micrometer-OTel bridge — existing Micrometer metrics flow through OTel pipeline without changing instrumentation code

**Current gap:** Shepard has Prometheus metrics (Micrometer) but zero distributed tracing. A slow request touching Neo4j → TimescaleDB → MongoDB is opaque — no span breakdown.

**What it gives:**
- Traces default-on for all incoming REST requests and outbound REST client calls
- Micrometer-OTel bridge: existing `@Timed`/`@Counted` metrics automatically bridge to OTel without code changes
- Dev Services: auto-starts a LGTM stack (Grafana + Loki + Tempo + Prometheus) in dev mode — immediate dashboards locally
- `quarkus-opentelemetry` + Tempo → distributed trace of: REST request → Neo4j OGM → TimescaleDB → MinIO, all in one trace

**Effort:** Low (add one dep; OTLP exporter endpoint is the only config; zero code changes for existing instrumentation)

---

### 4. `quarkus-messaging-kafka` — transport layer for NTF1 notification system

**Maven:** `io.quarkus:quarkus-messaging-kafka` (BOM-managed)  
**Current NTF1 state (aidocs/platform):** `NotificationProducer` SPI designed; no transport exists yet

**What it gives:**
- SmallRye Reactive Messaging: `@Outgoing("notifications")` on a producer bean; `@Incoming("notifications")` on the delivery service
- Decouples the event producer (any backend service emitting a storage nag, import result, or analysis completion) from the delivery channel (SMTP, Matrix, in-app SSE)
- Durable: events survive a backend restart (Kafka offset)
- Retry queue for failed deliveries (quarkus-quartz handles the retry schedule — see below)
- Dev Services: auto-starts a Kafka broker for local development

**Effort:** Medium (topology design + `NotificationProducer` SPI binding); Kafka infra is the operational addition

---

### 5. `quarkus-vault` 4.7.0 — secrets management for API keys

**Maven:** `io.quarkiverse.vault:quarkus-vault:4.7.0` (March 2026)  
**Relevant to:** shepard-plugin-ai API keys (aidocs/86 — "encrypted at rest, never returned in GET"), DataCite/ePIC minter credentials, Keycloak client secrets

**What it gives:**
- KV secrets store: API keys injected as `@ConfigProperty` at startup; never in compose env vars
- Transit encryption-as-a-service: the "apiKey encrypted at rest" requirement in aidocs/86 is implementable as a Vault Transit encrypt/decrypt call, no custom crypto
- Database credential rotation: TimescaleDB + MongoDB credentials auto-rotated; Quarkus reconnects automatically
- PKI: TLS cert issuance for inter-service mTLS
- Dev Services: auto-starts Vault in dev mode

**Effort:** Medium (Vault operator setup is the majority; Quarkus integration is low friction)

---

### 6. `quarkus-redis-client` — distributed permission cache

**Maven:** `io.quarkus:quarkus-redis-client` (BOM-managed; 3.34.0 latest)  
**Current gap:** `quarkus-cache` (Caffeine) is in-process — each backend pod maintains its own permission cache, so a permission revocation must invalidate N caches independently or wait for TTL expiry

**What it gives:**
- Shared permission/role cache across all backend pods
- Invalidation on write: `PermissionsService.revokeRole(userId)` publishes an invalidation event to Redis; all pods clear immediately
- Also useful for: import plan TTL (aidocs IMP1's `commitId` + plan expiry stored in Redis instead of in-memory), deduplication tokens for idempotent writes

**Effort:** Low-medium (cache-aside pattern over existing PermissionsService; Redis infra addition)

---

### 7. `quarkus-amazon-s3` (Quarkiverse) — replace hand-rolled AWS SDK v2

**Maven:** `io.quarkiverse.amazonservices:quarkus-amazon-s3:3.12.1`  
**Current state:** `shepard-plugin-file-s3` (FS1b) hand-rolls AWS SDK v2 directly

**What it gives:**
- Dev Services: auto-starts LocalStack S3 (or MinIO) for tests — no hand-rolled MinIO test container needed
- Reactive async client: `S3AsyncClient` injected via CDI; eliminates manual SDK builder configuration
- Native compilation: required for GraalVM native image if that path is ever taken
- Consistent credential resolution with other AWS Quarkiverse extensions (unified `quarkus.aws.*` config)

**Effort:** Low (mechanical replacement of SDK instantiation in FS1b plugin)

---

### 8. `quarkus-quartz` — persistent/clustered scheduling

**Maven:** `io.quarkus:quarkus-quartz` (BOM-managed; adds Quartz on top of `quarkus-scheduler`)  
**Current gap:** `quarkus-scheduler` fires in-process; a pod restart loses scheduled jobs; two pods both fire the same job

**What it gives:**
- Quartz-backed `@Scheduled` — job state persisted in existing Postgres (Flyway migration adds the tables)
- Cluster-aware: only one pod fires any given job (leader election via DB row lock)
- Relevant to: NTF1 notification retry queue, FS1e orphan sweep, import plan expiry cleanup

**Effort:** Low (add dep + Flyway migration for Quartz tables; existing `@Scheduled` beans unchanged)

---

### 9. `quarkus-kubernetes` + `quarkus-helm` — generated deployment manifests

**Maven:** `io.quarkus:quarkus-kubernetes` (BOM-managed); `io.quarkiverse.helm:quarkus-helm:1.x` (Quarkiverse)

**What it generates at build time:**
- `Deployment` / `Service` / `Ingress` with health probes auto-wired from `quarkus-smallrye-health` endpoints
- `ConfigMap` for application config; `Secret` refs for credentials
- Helm chart with parameterized values (image tag, replicas, resource limits)

**Reality check (honest scoping):** quarkus-kubernetes generates the manifest for the Quarkus backend only. The six stateful dependencies each need separate treatment:
- Neo4j: community Helm chart (`neo4j/neo4j`) or enterprise operator
- TimescaleDB: CloudNativePG operator with TimescaleDB extension, or Patroni Helm chart
- MongoDB: MongoDB Community Kubernetes Operator (Helm chart available)
- MinIO: MinIO Operator (`minio-operator/minio`)
- Keycloak: Bitnami Keycloak Helm chart or Keycloak Operator
- Python MCP sidecar (if not migrated): hand-written Deployment

A realistic K8s timeline: Phase 1 — add `quarkus-kubernetes` to generate the backend Deployment + `quarkus-kubernetes-config` for ConfigMap/Secret mounting; Phase 2 — compose a parent Helm umbrella chart referencing community charts for each stateful dep.

**Effort:** Low for Phase 1 (add dep, configure image name); High for Phase 2 (stateful dep operators)

---

## quarkus-mcp-server assessment (should we migrate the Python sidecar?)

**Verdict: Yes, migrate. The case is structural, not cosmetic.**

### Why the build-time-only caveat is not a blocker

The most common objection to `quarkus-mcp-server` for a plugin-based system is: "if tool discovery is build-time only, how do plugins register their own tools?" The answer for Shepard is that this objection doesn't apply. The `with-plugins` Maven profile already puts all plugin JARs on the compile-time classpath. Quarkus's build-time CDI scanner discovers `@Path` REST resources from those JARs — it would discover `@Tool` methods identically. This is not a workaround; it's the existing architecture.

The Python `McpToolProvider` SPI in aidocs/30 (`mcp_tools.py` per plugin, discovered via `SHEPARD_PLUGIN_DIRS` at runtime) exists to solve a problem that Shepard's Java plugin model already solves at compile time.

### Structural advantages of migration

| Dimension | Python sidecar (current design) | quarkus-mcp-server |
|---|---|---|
| Process count | 2 (backend + Python sidecar) | 1 |
| Auth | Bearer token forwarded from Python to backend | `@RolesAllowed` natively on `@Tool` methods; same Keycloak |
| Plugin tool registration | Runtime `SHEPARD_PLUGIN_DIRS` scan + `mcp_tools.py` SPI | CDI bean in plugin JAR, discovered at build time |
| Type safety | Python dict → JSON → Java round-trip | Direct Java service injection |
| Transport | SSE (deprecated as of MCP 2025-03-26) | Streamable HTTP (MCP 2025-11-25 spec) |
| OTel tracing | Manual | 1.12.x adds built-in OTel spans per tool call |
| Error handling | Python exception → stringified | Java exception → structured MCP error response |
| Test surface | Python unit tests separate | JUnit 5 + QuarkusTest; same test harness as backend |

### Migration path and tool inventory

The full tool inventory from aidocs/30 §3 maps directly to Java CDI beans:

```
MCP-1a  CollectionMcpTools + DataObjectMcpTools           → backend module
        list_collections, get_collection, create_collection, update_collection
        list_data_objects, get_data_object (containers breakdown, not referenceIds)
        get_predecessor_chain, get_successor_chain, get_children

MCP-1b  TimeseriesMcpTools                                 → backend or timeseries plugin
        list_timeseries_containers, list_channels
        get_channel_data (LTTB downsampling, maxPoints=1000)
        get_channel_summary, compare_channels

MCP-1c  FileMcpTools + StructuredDataMcpTools              → backend
        list_file_containers, list_files, get_file_text
        list_structured_containers, get_structured_data

MCP-1d  AnnotationMcpTools + LabJournalMcpTools            → backend
MCP-1e  ImportMcpTools                                     → backend
MCP-1f  DiscoveryMcpTools (list_tools, describe_instance)  → backend

Per plugin: VideoMcpTools (shepard-plugin-video), SpatialMcpTools (shepard-plugin-spatial)
            → CDI bean in plugin JAR, auto-discovered by with-plugins at build time
```

### The referenceIds fix (aidocs/30 §4) is a Java concern, not a Python concern

The documented bug — agents receiving `referenceIds: [331, 335, 337]` (DataObjectReference node IDs, not DataObject IDs) and calling `get_data_object(331)` → 404 — is fixed identically whether the MCP layer is Python or Java: the `get_data_object` tool response must break out `containers.timeseries`, `containers.files`, `containers.structuredData` and omit `referenceIds`. In Java this is just a response shaping decision in `DataObjectMcpTools`.

### What happens to the Python McpToolProvider SPI

The aidocs/30 Python SPI becomes redundant. The design doc should be updated: the `McpToolProvider` interface and `SHEPARD_PLUGIN_DIRS` env var are removed from the design; plugin tool contribution is documented as "add a CDI bean with `@Tool` methods to the plugin JAR."

The `/mcp` path on `shepard.nuclide.systems` (via Zoraxy virtual directory rule) and the Keycloak `mcp-client` PKCE client remain unchanged — those are routing and auth concerns independent of the implementation language.

---

## Kubernetes deployment path

### Phase 1 — Backend manifest generation (Low effort)

Add to `backend/pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes-config</artifactId>
</dependency>
```

At build time (`mvn package -Dquarkus.kubernetes.deploy=false`), Quarkus generates:
- `target/kubernetes/kubernetes.yml` — Deployment + Service with health probes from `/q/health/ready` and `/q/health/live` (auto-wired from `quarkus-smallrye-health`)
- Resource limits, replica count, image pull policy all set via `application.properties`
- `quarkus-kubernetes-config` mounts a ConfigMap at startup and reads Secrets for DB URLs + credentials

Add `io.quarkiverse.helm:quarkus-helm:1.x` for a generated Helm chart with parameterized `values.yaml`.

### Phase 2 — Stateful dependency operators (High effort)

| Dependency | Recommended approach |
|---|---|
| Neo4j | `neo4j/neo4j` Helm chart (community) or Neo4j Kubernetes Operator (enterprise) |
| TimescaleDB | CloudNativePG operator + TimescaleDB extension (production-grade HA); alternative: TimescaleDB Helm chart via `timescale/timescaledb-single` |
| MongoDB | MongoDB Community Kubernetes Operator (`mongodb/community-operator`) |
| MinIO | MinIO Operator (`minio-operator/minio-operator`) — tenant CR maps to PVCs and distributed nodes |
| Keycloak | Keycloak Operator (Red Hat, upstream) or Bitnami Keycloak Helm chart |
| HSDS (HDF5) | Custom Deployment (HSDS is NCAR/HDFGroup maintained; no official Helm chart) |

### Phase 3 — Umbrella chart

A parent Helm chart with sub-chart dependencies (`Chart.yaml` `dependencies:` entries) referencing each community chart + the Quarkus-generated backend chart. Shared values override image tags, storage class, ingress host, and secret refs.

### Realistic timeline

Phase 1 is a sprint of work (one developer, one PR). Phase 2 requires operator expertise and environment-specific tuning for persistent volumes, storage classes, and HA configuration — plan 2–3 sprints per stateful dep if starting from scratch, or adopt community Helm charts and tune defaults.

K8s is not the current deploy target (compose is the production setup), but Phase 1 is worth doing now: the generated manifests are the starting point for any institute wanting to run Shepard on their own cluster, and they serve as accurate documentation of the app's runtime requirements.

---

## What surprised me

**1. The Python sidecar uses a deprecated transport.**  
SSE as a standalone MCP transport was deprecated in MCP spec 2025-03-26 (replaced by Streamable HTTP). The aidocs/30 design was written before this deprecation and has not been updated. Any new MCP implementation should start with Streamable HTTP. The `quarkus-mcp-server-http` extension implements the 2025-11-25 spec (Streamable HTTP only); the old `quarkus-mcp-server-sse:1.7.3` is still available but deprecated.

**2. quarkus-mcp-server's "build-time" caveat is a non-issue for Shepard specifically.**  
This is the objection that would kill adoption in a dynamically-loaded plugin system. Shepard's `with-plugins` Maven profile already sidesteps it — plugins are compile-time dependencies, not runtime-discovered JARs. The build-time model is actually a feature here: tool schemas are validated at compile time, not discovered at runtime with potential failures.

**3. Neo4j is a first-class langchain4j vector store.**  
Shepard already has Neo4j. The `quarkus-langchain4j-neo4j` embedding store means semantic DataObject search (EMBEDDING capability in aidocs/86) can be implemented without adding any new infrastructure. The choice between pgvector (Postgres, also already present) and Neo4j as the embedding store is a performance tuning decision, not an infrastructure decision.

**4. quarkus-vault 4.7.0 was released in March 2026 — it is fully current.**  
The aidocs/86 requirement "apiKey encrypted at rest, never returned in GET" is precisely what Vault Transit provides. This is not speculative; the extension is production-ready and recent.

**5. langchain4j includes an MCP client.**  
The loopback case from aidocs/86 §7 — where `shepard-plugin-ai` acts as MCP client back to the Shepard MCP server (so the wiki-writer's LLM call can fetch channel stats it actually needs) — is supported natively in langchain4j 1.10.0's MCP client module. No custom MCP client implementation needed.

**6. quarkus-mcp-server 1.12.x adds per-tool OTel tracing.**  
Combined with `quarkus-opentelemetry`, every MCP tool invocation would produce a trace span. For an AI agent traversing the LUMEN TR-004 anomaly investigation, this means the exact tool-call sequence and latency breakdown is observable in Grafana Tempo. This is qualitatively better than anything achievable with the Python sidecar.
