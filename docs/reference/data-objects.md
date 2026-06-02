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
| `status` | `string` (enum) | Standard lifecycle: `DRAFT` \| `IN_REVIEW` \| `READY` \| `PUBLISHED` \| `ARCHIVED`. Quality-engineering branch (MFG1 / QM1a, role-gated on write): `NCR_OPEN` \| `ON_HOLD` \| `REJECTED` \| `CERTIFIED` \| `CONCESSION_PENDING`. See [Quality lifecycle](#quality-lifecycle-statuses-mfg1--qm1a) below. |
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

## Quality lifecycle statuses (MFG1 / QM1a)

Beyond the standard lifecycle (`DRAFT → IN_REVIEW → READY → PUBLISHED → ARCHIVED`),
DataObjects can carry one of five **quality-engineering statuses**. Writing any of
these requires the caller to hold the `quality-engineer` Keycloak / Neo4j role
(403 without it). Any user can read a DataObject that carries one of these statuses.

| Status | Meaning | Typical predecessor / successor |
|---|---|---|
| `NCR_OPEN` | A non-conformance has been raised; the artefact is awaiting investigation. | After a process step's NDT inspection fails. |
| `ON_HOLD` | Production has been paused while the cause is investigated. | After `NCR_OPEN`; before disposition is decided. |
| `CONCESSION_PENDING` *(QM1a)* | The disposition decision (use-as-is concession) is pending approval. | Between `NCR_OPEN` and `CERTIFIED` / `REJECTED`. |
| `REJECTED` | The artefact has been disposed as scrap or rework; not usable as-built. | Terminal for the failing branch. |
| `CERTIFIED` | The disposition is closed; the artefact has been accepted (potentially with concession). | Final state for the accepted branch. |

A typical EN 9100 §8.7 disposition window looks like:

```
READY  →  NCR_OPEN  →  ON_HOLD  →  CONCESSION_PENDING  →  CERTIFIED
                                                       ↘  REJECTED
```

Each status renders as a distinctly-coloured chip in the UI — `CONCESSION_PENDING`
is amber-outlined with a shield-alert icon so an auditor can spot disposition-window
artefacts at a glance.

The accompanying **Disposition record** template (QM1c, seeded by Neo4j
migration `V103`) is a `STRUCTURED_RECIPE` carrying the EN 9100 §8.7 fields:
`ncr_id`, `defect_type`, `disposition ∈ {use-as-is, rework, scrap, concession}`,
`approver_orcid`, `approver_username`, `decided_at`, `notes`. Attach it as a
`StructuredDataReference` on the DataObject that carries the NCR_OPEN /
CONCESSION_PENDING status.

## Typed predecessor relationships (PROV1k + QM1b)

Every predecessor edge carries a PROV-O / FAIR²R relationship type so the
"rework loop" in a process chain is queryable without reading attribute
strings. The four values are:

| Relationship type | Vocabulary | Maps to QM1b `transitionKind` | Meaning |
|---|---|---|---|
| `prov:wasInformedBy` | PROV-O | `normal` | Generic informational dependency. Default for any predecessor where no other type is set. |
| `prov:wasRevisionOf` | PROV-O | `re-test` | The successor is a direct revision / correction (e.g. TR-006 corrects TR-004 after repair). |
| `fair2r:repairs` | FAIR²R | `rework` | Rework / NCR-repair relationship (e.g. TR-005 is the repair action for TR-004's anomaly). |
| `fair2r:concession` *(QM1b)* | FAIR²R | `concession` | The successor was accepted under a concession ("use-as-is") after the predecessor failed acceptance. |

**Set the relationship type at create time:**

```http
POST /v2/collections/{cid}/data-objects
{
  "name": "TR-005",
  "typedPredecessors": [
    {"predecessorAppId": "01930a2b-…-tr-004", "relationshipType": "fair2r:repairs"}
  ]
}
```

**Or annotate an already-linked edge (QM1b):**

```http
PATCH /v2/collections/{cid}/data-objects/{did}/predecessors/{predAppId}
{"relationshipType": "fair2r:concession"}
```

The PATCH endpoint requires Write permission on the parent Collection. The
predecessor edge must already exist (404 otherwise); use the create / merge-patch
paths to add new predecessor links.

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

## Synchronised player (MFFD-MULTIPLAYER-1)

When a DataObject carries at least two distinct payload kinds (e.g.
timeseries + video, or timeseries + thermography + spatial), the
**Synchronised player** panel mounts above the per-kind tabs on the
DataObject detail page. It exposes a single time cursor that every payload
tile reads from and writes to:

- **Toolbar**: play / pause, rewind to start, playback rate (0.5×, 1×, 2×,
  4×), a scrubber over the intersected playable range, and a `t / tEnd`
  readout in seconds.
- **Tile grid** in canonical order: timeseries → video → thermography →
  spatial. Tiles appear only for payload kinds the DataObject actually
  carries.
- **Hide rule**: the panel does not render when fewer than two distinct
  payload kinds are present.

The cursor and playable range are computed by
`useSyncedTimeCursor` (`frontend/composables/context/useSyncedTimeCursor.ts`):
the range is the **intersection** of every constraining tile's range, so the
scrubber only spans times where every tile has data.

**What syncs end-to-end today**: the timeseries tile (chart marker writes
the cursor on hover; redraws on cursor change) and the video tile (native
controls write the cursor; cursor writes seek the video). The thermography
and spatial tiles render informational summaries today and link to follow-up
backlog rows (`MFFD-MULTIPLAYER-THERMO-1`, `MFFD-MULTIPLAYER-SPATIAL-1`)
that will wire genuine sync in a follow-up PR.

See `help/synchronised-player.md` for the user task page.

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
- `help/synchronised-player.md` — user task page for the synchronised
  multi-payload player.
