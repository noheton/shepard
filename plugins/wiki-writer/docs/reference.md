---
title: Wiki Writer plugin
weight: 95
---

# shepard-plugin-wiki-writer — reference

The wiki-writer plugin (`WW1`) uses the `LlmProvider` SPI (TEXT capability)
to generate a Markdown lab journal entry for a DataObject, summarising its
metadata and its relationship to sibling DataObjects in the same Collection.
The entry is written as a `LabJournalEntry` node linked to the DataObject.

## Endpoint

### `POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write`

Generates and writes an AI lab journal entry.

**Auth:** Authenticated (`@Authenticated`). Write permission on the parent
Collection is required (same as editing a DataObject).

**Request body** (optional, JSON):

| Field | Type | Default | Notes |
|---|---|---|---|
| `extraInstruction` | String | `null` | Appended to the user instruction layer. E.g. `"Focus on anomalies."` |
| `maxTokens` | Integer | `1024` | Clamped to `[128, 4096]`. |

**Response 200:**

```json
{
  "labJournalEntryId": 42,
  "generatedSummary": "# Track-001 AFP Layup\n\n…",
  "activityAppId": "019666a3-0000-7000-0000-000000000001",
  "inputTokens": 841,
  "outputTokens": 312
}
```

| Field | Notes |
|---|---|
| `labJournalEntryId` | Neo4j OGM id of the created `LabJournalEntry`. Fetch via the existing lab journal API. |
| `generatedSummary` | Raw Markdown from the LLM. |
| `activityAppId` | AppId of the `:AiActivity` provenance node written by `shepard-plugin-ai`. |
| `inputTokens` | Token count from the API response. |
| `outputTokens` | Token count from the API response. |

**Errors:**

| Status | Reason |
|---|---|
| `401` | No JWT or `X-API-KEY` header. |
| `403` | Caller lacks Write permission on the Collection. |
| `404` | No Collection or DataObject with the given appId. |
| `503` | `LlmProvider` is absent or TEXT capability is not configured. |

## Prompt structure

The plugin assembles a layered prompt (system + user role, via the AI plugin's
injection-defence ordering):

**System role (trusted):**
```
You are a technical documentation assistant for a research data management system.
You receive structured metadata about a research DataObject and its sibling DataObjects
in the same Collection. Your task is to write a concise, well-structured Markdown lab
journal entry for the target DataObject.
Write in a neutral, scientific tone.
Include: a brief summary of what the DataObject represents, its status, key attributes,
and how it relates to other DataObjects in the Collection (predecessors, successors, siblings).
If the DataObject has a description, incorporate it.
Do not invent data that is not present in the metadata.
Output Markdown only — no preamble, no explanation outside the entry itself.
```

**System role (trusted context — assembled by the plugin):**
```markdown
## Collection
- Name: MFFD Upper Shell — AFP Layup Campaign
- Status: IN_REVIEW

## Target DataObject
- Name: Track-001 Q1 AFP Run
- AppId: 019666a3-…
- Status: READY
- Attributes:
  - bench: AFP Cell 2
  - material_batch: CF-LMPAEK-2024-03

## Sibling DataObjects in Collection
- Track-002 Q1 AFP Run (status: READY) — Second ply group run, same batch
…
## Predecessors
- Ply-Group-01 Setup (status: READY)
```

**User role:**
```
Write a Markdown lab journal entry for DataObject "Track-001 Q1 AFP Run".
Use the metadata provided above. Keep the entry concise and factual.
```
Plus `extraInstruction` if provided.

## Soft dependency on shepard-plugin-ai

The wiki-writer plugin activates and registers its REST endpoint regardless of
whether `shepard-plugin-ai` is on the classpath. When the provider is absent
(or the TEXT capability is not configured), `POST .../wiki-write` returns 503
with a clear error message:

```json
{"error": "LLM TEXT capability is not configured. Deploy shepard-plugin-ai and configure the TEXT capability."}
```

## LabJournalEntry

The entry is created via `LabJournalEntryService.createLabJournalEntry(dataObjectOgmId, text)`.
It appears in the DataObject's lab journal panel in the frontend, indistinguishable
from a manually-written entry except by the author attribution (`AI-generated`
badge — planned for v0b).
