---
layout: default
title: Data Objects (reference)
permalink: /reference/data-objects/
---

# Data Objects

A `DataObject` is a node in a Collection's content graph: an
individual experimental run, manufacturing step, simulation case, or
publication artefact. DataObjects compose into provenance chains via
`Predecessor` / `Successor` / `Parent` / `Child` relationships, and
they own typed `References` to payload containers (timeseries,
files, structured data, video).

Both the upstream-compatible `/shepard/api/collections/{id}/dataObjects`
surface and this fork's `/v2/collections/{appId}/data-objects` surface
are available; the new fields described below are additive.

## Core fields

| Field | Type | Description |
|---|---|---|
| `id` | `long` | Neo4j-internal identifier. |
| `appId` | `string` | UUID v7. L2 native identifier. |
| `name` | `string` | Required. |
| `description` | `string` | Optional rich-text body. |
| `status` | `string` (enum) | `DRAFT` \| `IN_REVIEW` \| `READY` \| `PUBLISHED` \| `ARCHIVED`. |
| `attributes` | `Map<String, String>` | Free-text key-value annotations. |
| `collectionId` | `long` | The owning collection. |
| `parentId` | `long` (nullable) | Parent in the hierarchy. |
| `predecessorIds` / `successorIds` | `long[]` | Lineage chain (e.g. TR-004 → TR-005). |
| `childrenIds` | `long[]` | Direct children (e.g. anomaly-investigation sub-tree). |
| `referenceIds` | `long[]` | Payload references (NOT DataObject ids — distinct types). |
| `timeseriesReferenceCount`, `fileBundleCount`, `structuredDataReferenceCount`, `videoStreamReferenceCount` | `int` | Server-computed counts. |

## Fork additions (LIC1 — FAIR-1)

| Field | Type | Default | Description |
|---|---|---|---|
| `license` | `string` | `null` | SPDX license identifier (e.g. `CC-BY-4.0`, `MIT`, `Apache-2.0`, `PROPRIETARY`). |
| `accessRights` | `string` (enum) | `null` | `OPEN` \| `RESTRICTED` \| `CLOSED` \| `EMBARGOED`. |

Both fields use `@JsonInclude(NON_NULL)` so they are absent from the
wire when unset, preserving byte-compatibility with upstream v5.2.0
clients. The backend stores them as plain String — enum enforcement
is currently client-side (the v-select in the create / edit dialogs).

See `collections.md` for the full vocabulary description: it applies
verbatim to DataObjects.

## Why both Collection AND DataObject carry these fields

Funder review (DFG, EU Horizon Europe, Clean Aviation JU) requires
a `dcterms:license` and `dcat:accessRights` at the dataset granularity
the funder considers atomic. Some projects publish at Collection
granularity (one license for the whole bucket); others publish at
DataObject granularity (per-run licensing, common when a test
campaign has mixed-IP runs). The data model supports both:

- **Collection-level only**: leave DataObject fields `null`; the
  Collection's license applies transitively to all members.
- **DataObject-level override**: set the field on the specific run.
  When both are set, the DataObject value wins for that node.

The DataCite / RO-Crate export shape (see `aidocs/72`) consumes
DataObject-level values when present, falling back to Collection
defaults.

## Setting license and access rights

### REST

Both fields ride on the standard update payload:

```bash
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "TR-004",
    "license": "PROPRIETARY",
    "accessRights": "CLOSED"
  }' \
  https://shepard.example.org/v2/collections/$COLLECTION_APPID/data-objects/$DO_APPID
```

### UI

In the Edit Data Object dialog the two fields appear alongside
description and status. The Create Data Object dialog exposes them
in step 2 (Attributes). Both forms use:

- `LicenseInput` — v-autocomplete with SPDX suggestions; free-text
  override allowed.
- `AccessRightsInput` — v-select strictly limited to the four enum
  values; clearing the field sets `null`.

On the Data Object detail page both values render as small chips
below the title when set.

## Time-bounds sparklines in the DataObjects list

When a DataObject has `timeBoundsStart` and/or `timeBoundsEnd` set, the
DataObjects panel on the Collection landing page renders a small SVG bar in the
row, scaled across the collection's time window. Hovering the bar shows the
human-readable start–end range.

The sparkline column appears automatically when at least one DataObject in the
list carries time-bound metadata. DataObjects without bounds render an empty slot
in that column — they stay fully usable; the column just gives a quick visual
overview of when each run's data was recorded.

`timeBoundsStart` / `timeBoundsEnd` are ISO 8601 timestamps. Set them
programmatically on PUT/PATCH or via the Edit dialog in the UI (they appear in
the "Attributes" section of the dialog). Shepard does **not** infer them from
timeseries payload automatically — set them explicitly.

## `?fields=` query parameter (DB-OPT5)

The v2 list endpoint
`GET /v2/collections/{collectionAppId}/data-objects` supports two
payload-diet knobs to keep the wire small on collections with thousands
of DataObjects:

### Default-trim (no query param)

By default, the list response drops fields that the collection-detail
UI never renders. Three groups go quiet on the wire:

| Field | Why dropped |
|---|---|
| `description` | Heavy CommonMark string; only shown on the detail page. |
| `attributes` | Heavy key-value map; only shown on the detail page. |
| `timeseriesReferenceCount` | Deprecated `int` sibling of the v2 `timeseriesCount` (`long`). |
| `fileBundleCount` | Deprecated `int` sibling of the v2 `fileCount`. |
| `structuredDataReferenceCount` | Deprecated `int` sibling of the v2 `structuredDataCount`. |

These remain available on the detail endpoint:
`GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`.

The response carries a single header so a caller can verify the diet
mode at a glance:

```
X-Shepard-Payload-Diet: default-trim
```

### `?include=full` — opt back in

Pass `?include=full` to get the pre-DB-OPT5 wire shape, including the
fields above. This is a transitional safety valve; future versions may
drop the deprecated `int` counts unconditionally.

```
GET /v2/collections/{appId}/data-objects?include=full
X-Shepard-Payload-Diet: full
```

### `?fields=foo,bar,baz` — explicit allow-list

Pass `?fields=` (flat CSV, GitHub REST `fields=` convention) to ask for
only the named top-level fields. The endpoint always includes `id`,
`appId`, and `name` (resource-identity guarantees), even when not
listed.

Example — the exact field-set the Vue collection-detail panel uses:

```
GET /v2/collections/{appId}/data-objects?fields=id,appId,name,status,createdAt,referenceIds,childrenIds,incomingIds,timeseriesCount,fileCount,structuredDataCount,timeBoundsStart,timeBoundsEnd
X-Shepard-Payload-Diet: fields
```

Unknown field names short-circuit the request with HTTP 400 before any
database call:

```json
{
  "title": "Unknown field in ?fields= query parameter",
  "detail": "Field 'bogusField' does not exist on DataObjectListItemV2.",
  "status": 400
}
```

Dotted-path nested selection (e.g. `attributes.bench`) is not
supported in this iteration — the default-trim already drops the heavy
nested fields; track DB-OPT5-NESTED in `aidocs/16` for the follow-up.

### When to use which

| Caller | Recommended shape | Reason |
|---|---|---|
| Frontend collection-detail panel | `?fields=` | Smallest wire, highest cache locality. |
| MCP agent crawling for full context | `?include=full` | Needs `attributes` + `description`. |
| Operator / debugging via `curl` | default | Conservative trim; readable. |
| Bulk ETL exporters | `?fields=` listing only what the export needs | Bandwidth + latency. |

## See also

- `collections.md` — sibling page documenting the same fields at the
  Collection level.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.1` — funder-review
  rationale and the deferred items.
- `examples/mffd-showcase/scripts/diagnostics/measure-payload-diet.sh` —
  one-shot diagnostic to measure the payload-diet impact on a live
  Shepard instance.
- `provenance.md` — Predecessor/Successor chain semantics.
- `nfdi4ing-federation.md` — m4i federation runbook (M4I-e).
