---
title: Admin config registry — reference
audience: operator
---

# Admin config registry reference

**Feature ID:** V2CONV-A4  
**Route:** `/v2/admin/config/{feature}`  
**Role required:** `instance-admin`  
**Design doc:** `aidocs/platform/191-v2-surface-convergence.md §6`

---

## Overview

The **admin config registry** is the single runtime-configurable surface for all
Shepard feature knobs. Instead of per-feature bespoke `/v2/admin/<feature>/config`
endpoints, every feature registers a `ConfigDescriptor` bean; the generic
`AdminConfigRest` serves them all at `/v2/admin/config/{feature}`.

Adding a new runtime-configurable feature requires only a new `ConfigDescriptor`
bean — no new REST class.

**Key properties:**

- **RFC 7396 merge-patch** semantics: absent field = leave alone, explicit `null` = clear, value = replace.
- **`application/problem+json`** (RFC 7807) on all 4xx responses.
- **`ProvenanceCaptureFilter`** records every 2xx PATCH as a `:Activity` node in Neo4j — admin config changes are audited automatically.
- **No startup abort** on a missing descriptor — the registry is fail-soft; a missing feature → `Optional.empty()`.

---

## Endpoints

### List features

```
GET /v2/admin/config
Authorization: Bearer <instance-admin JWT>
```

Returns one row per registered feature — the `{feature}` path key plus a human-readable description.

**Response 200:**

```json
[
  { "feature": "semantic",       "description": "Semantic / ontology runtime config: preseed, disabled bundles, annotation mode and policies." },
  { "feature": "jupyter",        "description": "JupyterHub integration: master enable toggle and hub URL." },
  { "feature": "sql-timeseries", "description": "Caps for the SQL-over-timeseries query surface: maxRows and maxDuration." },
  ...
]
```

**Status codes:**

| Code | Meaning |
|------|---------|
| 200 | Feature list. Empty array if no descriptors are registered. |
| 403 | Caller lacks `instance-admin` role. |

---

### Read a feature config

```
GET /v2/admin/config/{feature}
Authorization: Bearer <instance-admin JWT>
```

Returns the current config shape for `{feature}`.

**Status codes:**

| Code | Meaning |
|------|---------|
| 200 | Current config (shape varies by feature — see §Feature reference below). |
| 403 | Caller lacks `instance-admin` role. |
| 404 | No feature registered under `{feature}` (RFC 7807 body). |

---

### Patch a feature config

```
PATCH /v2/admin/config/{feature}
Content-Type: application/merge-patch+json   (or application/json)
Authorization: Bearer <instance-admin JWT>

{ "fieldToChange": "newValue", "fieldToClear": null }
```

RFC 7396 merge-patch: send only the fields you want to change. Absent fields are left unchanged. Explicit `null` clears the field (where supported by the descriptor).

**Status codes:**

| Code | Meaning |
|------|---------|
| 200 | Updated config shape (same as GET). |
| 400 | Validation failure declared by the descriptor (RFC 7807 body). |
| 403 | Caller lacks `instance-admin` role. |
| 404 | No feature registered under `{feature}` (RFC 7807 body). |

---

## Feature reference

### `semantic`

Semantic / ontology runtime config: preseed, disabled bundles, annotation mode, and policies.

```bash
curl -s -H "Authorization: Bearer $TOKEN" https://shepard.example.org/v2/admin/config/semantic
```

Patchable fields include: ontology preseed toggle, disabled-bundle list, annotation write-mode
(`human` / `ai` / `collaborative`). See `SemanticConfigService` + `SemanticConfigIO`.

---

### `jupyter`

JupyterHub integration: master enable toggle and hub URL.

```bash
# Enable JupyterHub integration
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": true, "hubUrl": "https://jupyter.example.org"}' \
  https://shepard.example.org/v2/admin/config/jupyter
```

Patchable fields: `enabled` (Boolean), `hubUrl` (String or null to clear).

---

### `sql-timeseries`

Caps for the SQL-over-timeseries query surface.

Patchable fields: `maxRows` (int, max rows returned per query), `maxDuration` (ISO-8601 duration string, e.g. `PT30S`).

---

### `ror`

Instance-level Research Organization Registry (ROR) identifier and organization name.

```bash
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"rorId": "https://ror.org/04bwf3e34", "organizationName": "DLR"}' \
  https://shepard.example.org/v2/admin/config/ror
```

Patchable fields: `rorId` (String, full ROR URI), `organizationName` (String).

---

### `thermography`

Thermography analysis runtime config: quality-score threshold (°C) and heatmap grid dimensions.

Patchable fields: `qualityScoreThreshold` (double, °C), `heatmapGridRows` (int), `heatmapGridCols` (int).

---

### `aas` (plugin)

AAS plugin: registry URL, API key, base URL, and enabled toggle.

Patchable fields: `enabled` (Boolean), `registryUrl` (String or null), `registryApiKey` (String or null — write-only; never returned in GET), `baseUrl` (String or null).

---

### `ai` (plugin)

LLM capability slot configurations. PATCH body is an object keyed by capability name.

```bash
# Enable the EMBEDDING slot pointing at a local TEI instance
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"EMBEDDING": {"enabled": true, "endpointUrl": "http://tei:8080"}}' \
  https://shepard.example.org/v2/admin/config/ai
```

Capability names: `TEXT`, `FAST_TEXT`, `IMAGE_GEN`, `VISION`, `EMBEDDING`, `STRUCTURED`,
`TRANSCRIPTION`, `MODERATION`. Per-slot patchable fields: `enabled` (Boolean), `endpointUrl`
(String or null), `model` (String or null).

---

### `unhide` (plugin)

Helmholtz Unhide integration: feed toggle, public mode, contact email.

Patchable fields: `enabled` (Boolean), `feedPublic` (Boolean), `contactEmail` (String or null).

Note: the `harvestApiKeyHash` field is read-only in PATCH. To mint or rotate the harvest API key
use the sister endpoints at `POST /v2/admin/unhide/harvest-key/rotate` etc.

---

### `minter-datacite` (plugin)

DataCite Fabrica minter: enabled toggle, API base URL, handle prefix, repository ID, and credentials.

---

### `minter-epic` (plugin)

ePIC handle service minter: enabled toggle, API base URL, and handle prefix.

---

### `legacy-v1` (plugin)

v1 API compat surface: enabled toggle and deprecation-header suppression.

---

### `video` (plugin)

Video plugin: ffprobe probe toggle and per-upload file-size cap.

---

## Adding a new configurable feature

Implement `ConfigDescriptor<T>` and annotate it `@ApplicationScoped`. On `StartupEvent` the
`ConfigRegistry` discovers all `ConfigDescriptor` beans via CDI and registers them. No new REST
class needed.

```java
@ApplicationScoped
public class MyFeatureConfigDescriptor implements ConfigDescriptor<MyFeatureConfigIO> {
  @Override public String featureName()  { return "my-feature"; }
  @Override public String description()  { return "My feature: toggle and threshold."; }
  @Override public MyFeatureConfigIO currentShape() { return service.current(); }
  @Override public MyFeatureConfigIO applyMergePatch(JsonNode patch) throws ConfigPatchException {
    // apply RFC-7396 semantics, return updated shape
  }
}
```

The feature is then available at `GET|PATCH /v2/admin/config/my-feature` immediately on the next
startup, with role-check, problem-JSON, and provenance capture included at no extra cost.

---

## Error shapes

All 4xx responses from this surface are RFC 7807 `application/problem+json`:

```json
{
  "type": "/problems/admin.config.unknown-feature",
  "title": "Unknown config feature",
  "status": 404,
  "detail": "No runtime-configurable feature is registered under 'xyz'. List the available features with GET /v2/admin/config."
}
```

Known problem type URIs:

| URI | When |
|-----|------|
| `/problems/admin.config.unknown-feature` | `{feature}` not registered |
| `/problems/admin.config.malformed-patch` | PATCH body is not a JSON object |
| `/problems/unhide.config.read-only-field` | Attempting to PATCH `harvestApiKeyHash` on the `unhide` feature |
| Descriptor-specific | Validation failures declared by individual `ConfigDescriptor.applyMergePatch` |

---

## Audit trail

Every 2xx PATCH is automatically captured as a `:Activity` node in Neo4j by
`ProvenanceCaptureFilter` (PROV1a). The resource does not call `ProvenanceService.record()`
itself — the filter handles it. To query recent admin config changes:

```cypher
MATCH (a:Activity)
WHERE a.resourcePath STARTS WITH '/v2/admin/config' AND a.httpMethod = 'PATCH'
RETURN a ORDER BY a.timestamp DESC LIMIT 20
```
