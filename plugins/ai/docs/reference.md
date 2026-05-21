---
title: AI plugin (LLM provider)
weight: 90
---

# shepard-plugin-ai — reference

The AI plugin (`AI1`) connects Shepard to any OpenAI-compatible LLM endpoint
and exposes the `LlmProvider` SPI that other plugins (`wiki-writer`, future
`auto-annotator`) call to generate text.

## Architecture

```
Plugin calls        Backend SPI              shepard-plugin-ai
────────────        ─────────────            ──────────────────
WikiWriterService  ──▶ LlmProvider.complete ──▶ LlmProviderImpl
                       (TEXT capability)          │
                                                  ├── AiCapabilityConfigService
                                                  │     (reads :AiCapabilityConfig from Neo4j)
                                                  ├── OpenAiCompatClient
                                                  │     (MicroProfile REST client, dynamic URL)
                                                  └── AiActivityDAO
                                                        (writes :AiActivity provenance node)
```

## Neo4j entities

### `:AiCapabilityConfig`

One node per `AiCapability` slot. Seeded on first access by
`AiCapabilityConfigService.getOrSeed()`.

| Property | Type | Notes |
|---|---|---|
| `appId` | String (UUID v7) | Stable identifier; unique per node. |
| `capability` | String | Enum name: `TEXT`, `FAST_TEXT`, `IMAGE_GEN`, `VISION`, `EMBEDDING`, `STRUCTURED`. |
| `endpointUrl` | String | Base URL of the OpenAI-compatible API (no trailing `/`). |
| `model` | String | Model identifier sent as `model` in chat completions. |
| `apiKey` | String | Stored plain-text v0. Masked as `***` in GET responses. |
| `transport` | String | Transport kind — only `OPENAI_COMPAT` in v0. |
| `guardrailsPrefix` | String | Prepended before every plugin system prompt for this slot. |
| `guardrailsSuffix` | String | Appended after every plugin system prompt for this slot. |
| `maxTokens` | Integer | Overrides `LlmRequest.maxTokens` when set. |
| `temperature` | Double | Overrides `LlmRequest.temperature` when set. |
| `enabled` | Boolean | Master toggle. Default `false` after seeding. |

### `:AiActivity`

Written on every successful `LlmProvider.complete()` call. Provides
tamper-evident provenance (what was sent, what it cost, which model ran it).

| Property | Type | Notes |
|---|---|---|
| `appId` | String (UUID v7) | Returned as `activityAppId` in `LlmResponse`. |
| `capability` | String | Which slot was used (`TEXT`, …). |
| `pluginId` | String | Always `"ai"` in v0. |
| `modelId` | String | Runtime model name from `AiCapabilityConfig`. |
| `provider` | String | Derived from endpoint URL: `openai`, `anthropic`, `ollama`, `litellm`, `azure`, or `openai-compat`. |
| `promptHash` | String | SHA-256 (hex) of the assembled prompt. Enables deduplication. |
| `inputTokens` | Integer | From the API response `usage.prompt_tokens`. |
| `outputTokens` | Integer | From the API response `usage.completion_tokens`. |
| `occurredAt` | Long | `System.currentTimeMillis()` at call time. |

## Prompt assembly

Layers are assembled in this order (injection-defence):

```
SYSTEM role
  1. guardrailsPrefix (from :AiCapabilityConfig)
  2. pluginSystemPrompt (from LlmRequest)
  3. guardrailsSuffix  (from :AiCapabilityConfig)
  4. trustedContext    (from LlmRequest — application-assembled facts)

USER role
  5. ---BEGIN UNTRUSTED DOCUMENTS--- ... ---END UNTRUSTED DOCUMENTS---
     (each doc wrapped in ---BEGIN DOCUMENT--- / ---END DOCUMENT--- delimiters)
  6. userInstruction (from LlmRequest)
```

Untrusted user-supplied content is structurally isolated in the user role so
it cannot override system instructions.

## Admin REST endpoints

All endpoints require the `instance-admin` role.

### `GET /v2/admin/ai/capabilities`

List config for all known `AiCapability` slots.

**Response 200:**
```json
[
  {
    "appId": "019666a3-...",
    "capability": "TEXT",
    "endpointUrl": "https://api.openai.com/v1",
    "model": "gpt-4o-mini",
    "apiKey": "***",
    "apiKeySet": true,
    "transport": "OPENAI_COMPAT",
    "guardrailsPrefix": null,
    "guardrailsSuffix": null,
    "maxTokens": null,
    "temperature": null,
    "enabled": true
  }
]
```

Note: `apiKey` is always masked as `***` in GET responses. Use `apiKeySet` to
check whether a key is stored. Sending `"***"` back in a PATCH leaves the
stored key unchanged.

### `GET /v2/admin/ai/capabilities/{capability}`

Fetch config for one slot. `{capability}` is case-insensitive. A slot that
has never been configured is seeded as `enabled=false` on first access and
returned.

**Errors:** `404` if `{capability}` is not a known `AiCapability` name.

### `PATCH /v2/admin/ai/capabilities/{capability}`

RFC 7396 merge-patch. Absent fields are left unchanged.

**Patchable fields:** `endpointUrl`, `model`, `apiKey`, `transport`,
`guardrailsPrefix`, `guardrailsSuffix`, `maxTokens`, `temperature`, `enabled`.

**Request example — connect a TEXT slot to OpenAI:**
```json
PATCH /v2/admin/ai/capabilities/TEXT
Content-Type: application/json

{
  "endpointUrl": "https://api.openai.com/v1",
  "model": "gpt-4o-mini",
  "apiKey": "sk-...",
  "enabled": true
}
```

**Response 200:** Updated config shape (same as GET).  
**Errors:** `403` insufficient role; `404` unknown capability name.

## LlmProvider SPI

Other plugins call the SPI via CDI `Instance<LlmProvider>`:

```java
@Inject Instance<LlmProvider> llmProvider;

if (!llmProvider.isResolvable() || !llmProvider.get().isAvailable(AiCapability.TEXT)) {
    return Response.status(503).build();
}

LlmRequest req = LlmRequest.builder(AiCapability.TEXT)
    .pluginSystemPrompt("You are a …")
    .trustedContext("DataObject name: " + name)
    .userInstruction(userText)
    .maxTokens(1024)
    .temperature(0.3)
    .build();

LlmResponse resp = llmProvider.get().complete(req);
String text = resp.text();
String activityAppId = resp.activityAppId();
```

`LlmException` (unchecked) is thrown on any non-recoverable provider error.

## Neo4j migration

`V58__AiCapabilityConfig_constraint.cypher` — creates a uniqueness constraint
on `:AiCapabilityConfig(appId)`. Runs automatically on startup via
`MigrationsRunner`. Safe to re-run (`IF NOT EXISTS`).
