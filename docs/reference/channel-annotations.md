---
title: Channel annotations — reference
audience: user
---

# Channel annotations reference

**Feature IDs:** AI1b, TS-SEMANTIC-REST (APISIMP-CONT-NS-COLLAPSE-4)  
**API surface:** `/v2/containers/{appId}/channels/{channelShepardId}/annotations`  
**MCP tools:** `list_channel_annotations`, `create_channel_annotation`

---

## Overview

**Channel annotations** attach semantic terms to individual timeseries channels.
They are predicate-object pairs drawn from the same seeded vocabularies as
DataObject annotations, but they are anchored to a specific *channel* within a
TimeseriesContainer — not to the container or its parent DataObject as a whole.

Two kinds of channel annotations coexist:

| Kind | Written by | Endpoint family |
|------|-----------|-----------------|
| **Identity annotations** | TS-SEMANTIC-01 dual-write (automatic, one per non-blank 5-tuple field) | same endpoints — read as `list`, delete by `annotationAppId` |
| **Research annotations** | Researchers or AI agents (manual) | `create` + `delete` |

Channel annotations are distinct from:

- **Container (temporal) annotations** — time-range labels on a TimeseriesContainer
  (`GET/POST/DELETE /v2/containers/{appId}/annotations`); see
  `docs/reference/container-annotations.md`.
- **DataObject annotations** — entity-level metadata on a DataObject
  (`POST /v2/annotations`); see `docs/reference/semantic-annotations.md`.

---

## Neo4j entity model

```
(:TimeseriesContainer {appId, …})
    │
    └─[:HAS_ANNOTATABLE_TIMESERIES]──▶ (:AnnotatableTimeseries {appId=channelShepardId, …})
                                              │
                                              └─[:HAS_ANNOTATION]──▶ (:SemanticAnnotation {
                                                                            appId,
                                                                            propertyIRI,
                                                                            propertyName,
                                                                            valueIRI,
                                                                            valueName,
                                                                            numericValue?,
                                                                            unitIRI?
                                                                       })
```

The `:AnnotatableTimeseries` bridge node has its `appId` set to the channel's
`channelShepardId` (UUID v7). This is the handle used in all REST and MCP calls.

A channel must have an `:AnnotatableTimeseries` node before annotations can be
written. The TS-SEMANTIC-01 dual-write creates this node automatically whenever
a channel is written via the normal timeseries upload path. Channels created
before TS-SEMANTIC-01 shipped do not have this node; `list` returns an empty
array for them; `create` returns 404.

---

## Annotation shape

```json
{
  "appId": "019e8f11-0000-7000-8000-000000000007",
  "propertyIRI": "https://w3id.org/pmd/co/hasRole",
  "propertyName": "hasRole",
  "valueIRI": "urn:shepard:timeseries:role:vibration",
  "valueName": "vibration",
  "numericValue": null,
  "unitIRI": null
}
```

| Field | Type | Writable | Description |
|-------|------|----------|-------------|
| `appId` | UUID v7 | — | Stable identifier for the annotation. Use for `DELETE`. |
| `propertyIRI` | string | ✓ (create) | Predicate IRI. Must exist in the referenced vocabulary. |
| `propertyName` | string | — | Human-readable label for the predicate, resolved from the vocabulary. |
| `valueIRI` | string | ✓ (create) | Object IRI. Must exist in the referenced vocabulary. |
| `valueName` | string | — | Human-readable label for the value. |
| `numericValue` | number? | — | Numeric rendering of the value, when the vocabulary term carries one (QA-1). |
| `unitIRI` | string? | — | QUDT unit IRI when `numericValue` is set (QA-1). |
| `propertyRepositoryId` | number | ✓ (create) | Neo4j id of the vocabulary owning the `propertyIRI`. Use `list_vocabularies` or `GET /v2/semantic/repositories`. |
| `valueRepositoryId` | number | ✓ (create) | Neo4j id of the vocabulary owning the `valueIRI`. |

> **Migration note:** `propertyRepositoryId` / `valueRepositoryId` are the
> current wire names (numeric OGM ids). APISIMP-MCP-VOCAB-NUMERIC-ARGS (PR #1959)
> migrates the **MCP** `create_channel_annotation` tool to accept
> `propertyVocabAppId` / `valueVocabAppId` (UUID v7 strings) instead. The REST
> endpoint wire shape will be updated in a subsequent APISIMP pass. Until then
> the REST body still takes numeric ids.

---

## REST endpoints

All three endpoints are under the unified container surface
(`/v2/containers`, not the removed per-kind `/v2/timeseries-containers`).

### List channel annotations

```
GET /v2/containers/{containerAppId}/channels/{channelShepardId}/annotations
```

**Permission:** Read on the TimeseriesContainer.

**Path params:**

| Param | Description |
|-------|-------------|
| `containerAppId` | UUID v7 of the TimeseriesContainer. |
| `channelShepardId` | UUID v7 of the channel (the `shepardId` field from `GET /v2/timeseries-references/{refAppId}/channels`). |

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| 200 | `SemanticAnnotationIO[]` | Array of annotations (may be empty). |
| 401 | problem+json | Unauthenticated. |
| 403 | problem+json | No Read permission. |
| 404 | problem+json | Container or channel not found. |
| 415 | problem+json | Container kind has no channel-annotation concept (non-timeseries kind). |

**Example:**

```bash
curl -H "X-API-KEY: $KEY" \
  https://shepard.example.org/v2/containers/019e7244-0000-7000-8000-000000000001/channels/019e7244-0000-7000-8000-000000000042/annotations
```

```json
[
  {
    "appId": "019e8f11-0000-7000-8000-000000000007",
    "propertyIRI": "https://w3id.org/pmd/co/hasRole",
    "propertyName": "hasRole",
    "valueIRI": "urn:shepard:timeseries:role:vibration",
    "valueName": "vibration",
    "numericValue": null,
    "unitIRI": null
  }
]
```

---

### Create a channel annotation

```
POST /v2/containers/{containerAppId}/channels/{channelShepardId}/annotations
Content-Type: application/json
```

**Permission:** Write on the TimeseriesContainer.

**Request body:**

```json
{
  "propertyIRI": "https://w3id.org/pmd/co/hasRole",
  "propertyRepositoryId": 1,
  "valueIRI": "urn:shepard:timeseries:role:vibration",
  "valueRepositoryId": 2
}
```

**Workflow to build the request:**

1. `GET /v2/semantic/repositories` — list vocabularies; note the `id` (numeric)
   of the vocabulary that contains your property IRI (`propertyRepositoryId`)
   and the vocabulary that contains your value IRI (`valueRepositoryId`).
2. Browse predicates in the vocabulary (or use `search_predicates` MCP tool) to
   find `propertyIRI`.
3. Browse values in the vocabulary (or use `search_values` MCP tool) to find
   `valueIRI`.

Both `propertyIRI` and `valueIRI` are validated against their respective
vocabulary at write time; mismatched or missing IRIs return 422.

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| 201 | `SemanticAnnotationIO` | Created annotation. |
| 400 | problem+json | `channelShepardId` blank or body malformed. |
| 401 | problem+json | Unauthenticated. |
| 403 | problem+json | No Write permission. |
| 404 | problem+json | Container or channel not found; or channel has no `:AnnotatableTimeseries` node yet (channel predates TS-SEMANTIC-01). |
| 415 | problem+json | Container kind has no channel-annotation concept. |
| 422 | problem+json | `propertyIRI` or `valueIRI` not found in the specified vocabulary. |

---

### Delete a channel annotation

```
DELETE /v2/containers/{containerAppId}/channels/{channelShepardId}/annotations/{annotationAppId}
```

**Permission:** Write on the TimeseriesContainer.

**Responses:**

| Status | Body | Description |
|--------|------|-------------|
| 204 | — | Deleted. |
| 401 | problem+json | Unauthenticated. |
| 403 | problem+json | No Write permission. |
| 404 | problem+json | Container, channel, or annotation not found. |
| 415 | problem+json | Container kind has no channel-annotation concept. |

---

## MCP tools

Two MCP tools are available for AI-agent workflows (Claude / Shepard MCP server).

### `list_channel_annotations`

```
list_channel_annotations(containerAppId, channelShepardId) → JSON array
```

Returns the same array as `GET …/annotations`. A channel with no
`:AnnotatableTimeseries` node returns an empty array (no error).

### `create_channel_annotation`

```
create_channel_annotation(
  containerAppId,
  channelShepardId,
  propertyIRI,
  propertyRepositoryId,   ← numeric; migrating to propertyVocabAppId (PR #1959)
  valueIRI,
  valueRepositoryId       ← numeric; migrating to valueVocabAppId (PR #1959)
) → JSON annotation object
```

Typical agent workflow:

```
1. get_data_object(dataObjectAppId)
       → containers.timeseries[0].containerAppId  ← use this

2. list_channels(containerAppId)
       → rows[i].shepardId                        ← channelShepardId

3. list_vocabularies()
       → find vocab with relevant terms; note numeric id

4. search_predicates(vocabId, "role")
       → pick propertyIRI

5. search_values(vocabId, "vibration")
       → pick valueIRI

6. create_channel_annotation(containerAppId, channelShepardId,
       propertyIRI, vocabId, valueIRI, vocabId)
```

---

## TS-SEMANTIC-01 identity annotations

When a channel is first written through the normal timeseries upload/ingest path,
the TS-SEMANTIC-01 dual-write service creates one `:SemanticAnnotation` per
non-blank 5-tuple field:

| 5-tuple field | Vocabulary predicate |
|---------------|----------------------|
| `measurement` | `urn:shepard:timeseries:identity:measurement` |
| `device` | `urn:shepard:timeseries:identity:device` |
| `location` | `urn:shepard:timeseries:identity:location` |
| `symbolicName` | `urn:shepard:timeseries:identity:symbolicName` |
| `field` | `urn:shepard:timeseries:identity:field` |

These identity annotations appear in `list_channel_annotations` alongside
manually added ones. They can be deleted via `DELETE …/annotations/{appId}` if
needed, but doing so removes the only identity handle pointing from the semantic
graph to the physical channel.

---

## Related docs

- `docs/reference/container-annotations.md` — temporal (time-range) labels on a container
- `docs/reference/semantic-annotations.md` — entity-level annotations on DataObjects
- `docs/reference/semantic-repositories.md` — vocabulary management (add/seed predicates and values)
- `docs/reference/containers.md` — container kinds and the unified `/v2/containers` surface
