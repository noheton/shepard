---
stage: feature-defined
last-stage-change: 2026-05-26
audience: [contributor, admin]
---

# TPL9 — f(ai)²r AI provenance capture (PROV-O extension for AI transparency)

**Status:** feature-defined  
**Task ID:** #89  
**Related:** `aidocs/43 §AI1`, `aidocs/semantics/99-promptlog-design.md` (PROMPT1),  
`backend/src/main/java/de/dlr/shepard/spi/ai/Fair2rPredicates.java`

---

## 1. Motivation

Shepard already captures provenance for human mutations via `ProvenanceCaptureFilter`
(every 2xx write → `:Activity` node). Two new pressure points demand an AI-specific
extension:

1. **EU AI Act Article 50 (effective 2026-08-02):** Providers of AI-generated content
   must make the AI origin transparent to recipients. When an AI agent calls Shepard's
   MCP surface and causes a write — upload a file, create a DataObject, add an
   annotation — that write must carry a traceable "this was AI-generated" flag.

2. **f(ai)²r vocabulary (Krebs 2026, CC-BY-4.0):** An independent research
   proposal for extending FAIR data principles to AI-generated artefacts. Vocabulary
   namespace: `https://w3id.org/fair2r/v1#`. The intent is that every AI interaction
   that produces a persisted artefact is typed as a `fair2r:AuthoringPass` (a subtype
   of `prov:Activity`) carrying provenance predicates such as `usedModel`,
   `promptHash`, `resultedInWrite`, etc. `Fair2rPredicates.java` is the SSOT for
   these constant names.

**TPL9's scope is narrow**: capture that *an inbound MCP call* was made by an AI
agent, and record the minimum f(ai)²r predicates in the provenance chain. Full
prompt-body storage, transcript lineage, and SHACL RDF emission belong to PROMPT1
(`aidocs/semantics/99`); TPL9 is the structural plumbing that makes PROMPT1 possible.

### Distinction: outbound vs. inbound AI provenance

The `plugins/ai/` plugin already writes `:AiActivity` nodes for **outbound** LLM
calls Shepard makes on behalf of users (AI1 — anomaly detection, annotation
suggestions, etc.). TPL9 is the **inbound** half: recording when an AI *agent* calls
Shepard's MCP tool surface. These are different provenance facts and must not be
conflated.

---

## 2. Vocabulary

The canonical vocabulary for AI provenance in Shepard is **f(ai)²r**. The SSOT is
`de.dlr.shepard.spi.ai.Fair2rPredicates`. All predicate name strings used at capture
sites MUST come from that class.

Key predicates for TPL9 capture:

| Predicate constant | IRI suffix | Meaning |
|---|---|---|
| `USED_MODEL` | `usedModel` | Model identifier the inference was performed by |
| `USED_PROVIDER` | `usedProvider` | Provider the inference was performed against |
| `PROMPT_HASH` | `promptHash` | SHA-256 hash of the user instruction |
| `CAPABILITY` | `capability` | Capability slot the call was routed through |
| `INPUT_TOKENS` | `inputTokens` | Input token count |
| `OUTPUT_TOKENS` | `outputTokens` | Output token count |
| `INJECTION_FLAGGED` | `injectionFlagged` | Whether a pre-flight injection scan flagged this |
| `RESULTED_IN_WRITE` | `resultedInWrite` | Whether the call produced a persisted artefact |
| `INVOKED_BY` | `invokedBy` | Plugin/agent identifier of the caller |
| `ASSOCIATED_USER` | `associatedUser` | User the call was associated with |
| `WAS_STREAMED` | `wasStreamed` | Whether the response was assembled from an SSE stream |

TPL9 adds one new predicate constant to `Fair2rPredicates`: `AI_ACTION_TYPE` —
the semantic type of the AI action (see `AiActivityType` enum in §3).

---

## 3. Activity types (`AiActivityType`)

The `de.dlr.shepard.v2.ai.AiActivityType` enum classifies what kind of AI-driven
action the MCP invocation represented. Values mirror the functional categories
in `aidocs/43`:

| Value | Meaning |
|---|---|
| `ANNOTATION_SUGGESTION` | The agent suggested or applied semantic annotations |
| `IMPORT_MANIFEST_GENERATION` | The agent generated or applied an import manifest |
| `SPARQL_GENERATION` | The agent generated and executed a SPARQL query |
| `ANOMALY_DETECTION` | The agent ran an anomaly-detection routine on timeseries |
| `SEMANTIC_ENRICHMENT` | The agent performed semantic enrichment or vocabulary alignment |
| `CHAT_RESPONSE` | The agent produced a conversational response (may or may not write) |

Callers set this in the `X-AI-Activity-Type` request header. When absent, the
capture bean uses `CHAT_RESPONSE` as the default (least-specific assumption).

---

## 4. Capture points

### Primary: `McpToolSupport.run()` + `X-AI-Agent` header

The canonical capture point is `McpToolSupport.run(String toolName, Callable<T> body)`,
which wraps every MCP tool invocation. After a successful body execution, `run()`
inspects the current Vert.x `RoutingContext` (via `CurrentVertxRequest`) for the
`X-AI-Agent` header. If present, it delegates to `AiProvenanceCapture.record(...)`.

Header protocol (all optional; capture is best-effort when headers are absent):

| Header | Meaning |
|---|---|
| `X-AI-Agent` | Identifier of the calling AI agent or framework (`claude`, `openai-assistant`, etc.) |
| `X-AI-Model` | Model identifier (e.g. `claude-opus-4-7`) |
| `X-AI-Prompt-Hash` | SHA-256 of the user instruction (privacy default) |
| `X-AI-Activity-Type` | `AiActivityType` value for this call (default `CHAT_RESPONSE`) |

A future variant of `McpAuthFilter` can extract and validate these at the HTTP
boundary before the routing context reaches `run()` — that is a PROMPT1 concern.

### No-op posture when header absent

When `X-AI-Agent` is absent, `AiProvenanceCapture.record(...)` is not called. This
avoids double-writes with the existing `ProvenanceCaptureFilter` (which already stamps
a standard `:Activity` row for every 2xx mutation regardless of AI origin).

### Future capture points (PROMPT1)

PROMPT1 (`aidocs/semantics/99`) will add richer capture: full prompt-body storage,
`:PromptRun` identity nodes, pgvector embeddings, OTel GenAI spans. TPL9's
`AiProvenanceCapture` bean is intentionally minimal so PROMPT1 can replace or
supplement it without ripping out cross-cutting logic.

---

## 5. PROV-O graph shape

TPL9 records a supplementary `:Activity` node with `actionKind = "AI_ACTION"` via
the existing `ProvenanceService.record()` call chain. The f(ai)²r metadata
(model, promptHash, activityType, etc.) is encoded in the `summary` field as a
compact JSON-like string (pending the full SHACL substrate from PROMPT1).

When PROMPT1 ships its `:PromptRun` substrate, the TPL9 capture will be upgraded to:

```
(:PromptRun)-[:PROV_WAS_ASSOCIATED_WITH]->(:User)
(:PromptRun)-[:PROV_USED]->(:AiCapabilityConfig)
(:DataObject)-[:PROV_WAS_GENERATED_BY]->(:PromptRun)
```

Properties on the `:Activity` node (post-TPL9, pre-PROMPT1):

```
actionKind:    "AI_ACTION"
targetKind:    <target entity label, if known>
targetAppId:   <target entity appId, if available from call context>
agentUsername: <JWT-authenticated user who sent the MCP call>
summary:       "AI_ACTION type=<AiActivityType> model=<modelId> agent=<agentId>"
method:        "MCP"
path:          "/v2/mcp/<toolName>"
status:        200   (only recorded on success — same as ProvenanceCaptureFilter)
```

---

## 6. Wire plan

### New classes

| Class | Package | Notes |
|---|---|---|
| `AiActivityType` | `de.dlr.shepard.v2.ai` | Enum — 6 values (§3) |
| `AiProvenanceCapture` | `de.dlr.shepard.v2.ai` | `@ApplicationScoped` CDI bean |

### Modified classes

| Class | Change |
|---|---|
| `McpToolSupport` | Inject `AiProvenanceCapture` + `CurrentVertxRequest`; call `record()` after successful body |
| `Fair2rPredicates` | Add `AI_ACTION_TYPE = "aiActionType"` constant |

### Not modified

- `ProvenanceService` — called as-is via `AiProvenanceCapture` delegation
- `McpContextBridge` — not touched; principal propagation is orthogonal
- `McpAuthFilter` — not touched at TPL9 scope

### Plugin-first exception

Per `CLAUDE.md §"Always: think plugin-first for new features"`, the default for new
features is a plugin. `AiProvenanceCapture` is justified as **core** because:

1. It is **cross-cutting transport infrastructure** at the MCP layer — same
   justification as `ProvenanceCaptureFilter` being in core rather than a plugin.
2. It has no external dependencies (delegates to `ProvenanceService` already in core).
3. Making it a plugin would require core to depend on the plugin SPI at the
   transport boundary — the inverted dependency is architecturally unsound.

The AI plugin (`plugins/ai/`) already correctly lives outside core for its LLM
provider implementation. TPL9's capture bean is the thin hook that wires the
infrastructure; the richer prompt-storage semantics (PROMPT1) will live in the
`shepard-plugin-promptlog` plugin.

---

## 7. Frontend surface

TPL9 has **no dedicated frontend surface** in this slice. The capture writes to the
existing `:Activity` stream, which surfaces in:

- `AdminActivityLogPane.vue` — admin-level activity viewer (already shipped)

When PROMPT1 ships, it will add a dedicated `/admin/ai/prompt-runs` surface.

---

## 8. EU AI Act Article 50 mapping

| Article 50 obligation | TPL9 mechanism | Gap / deferral |
|---|---|---|
| **Art. 50(1)**: AI system must inform users that they are interacting with an AI | `X-AI-Agent` header on inbound calls signals AI origin; recorded in `:Activity.summary` | Full user-visible badge (e.g. `aiGenerated=true` on DataObject) deferred to PROMPT1 |
| **Art. 50(2)**: Operators of chatbots must disclose AI nature | MCP clients SHOULD include `X-AI-Agent` header; the capture records it | Enforcement (rejecting calls without the header) is a future `McpAuthFilter` concern |
| **Art. 50(3)**: AI-generated content disclosure on synthetic media | Out of scope for Shepard's text-and-data use case |  |
| **Art. 50(4)**: Prohibition on certain forms of deep fake disclosure failure | Out of scope |  |

The MFFD import script (`examples/mffd-showcase/scripts/mffd-import-v15.py`) already
emits per-DO `fair2r:modeOfProduction "ai"` triples to close the Art. 50 deadline of
2026-08-02 at the script layer. TPL9 adds the backend infrastructure that closes the
gap at the API layer, enabling long-term compliance without script-side annotations.

---

## 9. Tests

Minimum test obligation for this slice:

- `AiProvenanceCaptureTest` (unit): no-op when `provenanceService` returns null,
  records correctly when header present, records correctly with metadata map, handles
  null toolName gracefully.
- `McpToolSupportTest` (unit): verify `run()` does NOT call `AiProvenanceCapture`
  when no routing context; verify it DOES call it when `X-AI-Agent` is set in a
  mock routing context.

Integration test (deferred, tracked as `TPL9-IT` in `aidocs/16`): an MCP tool call
with `X-AI-Agent: test-agent` → verify an `:Activity` row with `actionKind="AI_ACTION"`
and `summary` containing `type=` lands in Neo4j.

---

## 10. Migration

No Neo4j migration required. The `actionKind = "AI_ACTION"` value is a new string
on an existing `String` property — existing queries that filter by known `actionKind`
values are unaffected. Operators who query `:Activity` nodes by `actionKind` should
note the new value appears after TPL9 ships.

---

## 11. Changelog

| Version | Date | Change |
|---|---|---|
| 0.1 | 2026-05-26 | Initial design doc — feature-defined stage |
