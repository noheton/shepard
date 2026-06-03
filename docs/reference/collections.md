---
layout: default
title: Collections (reference)
permalink: /reference/collections/
---

# Collections

A `Collection` is shepard's top-level container: a named bucket of
DataObjects, plus the metadata that makes the bucket discoverable,
governable, and citable. Both the upstream-compatible
`/shepard/api/collections` surface and this fork's `/v2/collections`
surface are available; new fields described below are additive (absent
from the wire when unset, so upstream clients keep working).

## Core fields

| Field | Type | Description |
|---|---|---|
| `id` | `long` | Neo4j-internal identifier. Stable per node. |
| `appId` | `string` | UUID v7. The L2 native identifier used everywhere on `/v2/`. |
| `name` | `string` | Required. The human-readable label. |
| `description` | `string` | Optional rich-text body. |
| `status` | `string` (enum) | `DRAFT` \| `IN_REVIEW` \| `READY` \| `PUBLISHED` \| `ARCHIVED`. |
| `attributes` | `Map<String, String>` | Free-text key-value annotations. |
| `createdAt` / `createdBy` | timestamp / user | Set on insert; immutable. |
| `updatedAt` / `updatedBy` | timestamp / user | Set on every PUT/PATCH. |
| `dataObjectIds` | `long[]` | Server-side computed, read-only. |
| `permissions` | object | Owner, group, ACL. See user-profile reference. |

## Fork additions

These fields are absent from upstream `gitlab.com/dlr-shepard/shepard 5.2.0`.
They are emitted with `@JsonInclude(NON_NULL)` so an unset field never
appears on the wire, preserving byte-compatibility with upstream clients.

| Field | Type | Default | Description |
|---|---|---|---|
| `heroImageUrl` | `string` | `null` | Public URL of a banner image rendered above the title. URL-only (no upload). |
| `license` | `string` | `null` | SPDX license identifier (FAIR-1). See *License* below. |
| `accessRights` | `string` (enum) | `null` | `OPEN` \| `RESTRICTED` \| `CLOSED` \| `EMBARGOED` (FAIR-1). See *Access rights* below. |

## License (FAIR-1)

The `license` field carries an SPDX license identifier expression
(e.g. `CC-BY-4.0`, `MIT`, `Apache-2.0`, `ODbL-1.0`) or the special
value `PROPRIETARY` for non-SPDX in-house terms. The field is free
text on the wire — the UI surfaces a curated list of ~28 common open
licenses as autocomplete suggestions but does not constrain the
typed value. The backend stays permissive (plain String) for
additive forward-compatibility.

Closes FAIR R1.1 ("data are released with a clear and accessible data
usage license"). See `aidocs/semantics/98-shapes-views-and-process-model.md §4.1`
for the funder-review rationale (DFG, EU Horizon Europe, Clean
Aviation JU all reject collections without a license).

### Recommended values

- **`CC-BY-4.0`** — default for open research data with attribution.
- **`CC0-1.0`** — when releasing into the public domain.
- **`PROPRIETARY`** — internal DLR data with in-house redistribution terms.

## Access rights (FAIR-1)

The `accessRights` field carries one of four enum values:

- **`OPEN`** — public, no access restrictions.
- **`RESTRICTED`** — access conditional; requires authentication or approval.
- **`CLOSED`** — closed access. Metadata-only externally, full data internal.
- **`EMBARGOED`** — restricted now, becomes open at a future date.
  (The future date is not yet a separate field; record it in `attributes`
  pending the embargo-date PR.)

Backed by `dcat:accessRights` in the export-shape mapping. The enum
is enforced client-side by the v-select; server-side the value is
stored as a plain String for forward-compat.

Closes FAIR A1.2 ("the protocol allows for an authentication and
authorisation procedure, where necessary").

## Setting license and access rights

### REST (PATCH / PUT)

Both fields ride on the standard update payload:

```bash
curl -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MFFD Q1 layup runs",
    "license": "CC-BY-4.0",
    "accessRights": "RESTRICTED"
  }' \
  https://shepard.example.org/v2/collections/42
```

To clear a field, send `null` (or omit it — the server treats both
the same on PUT).

### UI

In the Edit Collection dialog the two fields are exposed as a
license autocomplete (with SPDX suggestions) and an access-rights
v-select (showing color-coded chips). They are visible in **both**
basic and advanced mode — FAIR-mandatory fields are never hidden
in the basic shelf.

The Collection detail page renders both fields as small chips below
the title (when set). The collection list table has a dedicated
"Access" column showing the access-rights chip per row, so an
auditor can scan a page of collections at a glance.

## Metadata completeness score (RDM-005a(d))

`GET /v2/collections/{appId}/completeness`

Returns a server-side authoritative metadata completeness score for the
collection. Mirrors the same 9-check / 100-point logic shown in the
`MetadataCompletenessCard` widget on the Collection landing page, but
accessible from the CLI, CI quality gates, and harvest feeds (re3data,
OpenAIRE, Helmholtz Databus) without a browser session.

**Auth:** Read permission on the Collection required. Returns `401` when
unauthenticated, `403` when the caller lacks Read, `404` when the appId
is unknown.

**Response shape (`CollectionCompleteness`):**

| Field | Type | Description |
|---|---|---|
| `score` | `int` | Achieved score, 0–100. Sum of `points` from all passing checks. |
| `maxScore` | `int` | Always 100. Included so callers can compute percentage coverage without hard-coding the ceiling. |
| `band` | `string` | `"error"` (score < 50), `"warning"` (50 ≤ score < 80), `"success"` (score ≥ 80). Mirrors the Vuetify colour token used on the card. |
| `checks` | `CompletenessCheck[]` | Always exactly 9 entries in the same order as the client widget. |

**`CompletenessCheck` entry:**

| Field | Type | Description |
|---|---|---|
| `id` | `string` | Stable slug (e.g. `"name"`, `"description"`, `"license"`). |
| `label` | `string` | Human-readable label. |
| `passed` | `boolean` | Whether this check passed for the collection. |
| `points` | `int` | Points awarded when `passed = true`. Sum across all checks = `maxScore`. |

**The 9 checks, in order:**

| id | Points | Condition |
|---|---|---|
| `name` | 10 | Collection `name` is non-blank. |
| `description` | 15 | `description` is ≥ 50 characters. |
| `license` | 20 | `license` field is set and non-blank (FAIR R1.1). |
| `accessRights` | 10 | `accessRights` field is set and non-blank (FAIR A1.2). |
| `creatorOrcid` | 10 | `createdByOrcid` is stamped on the collection (FAIR R1.2). |
| `semanticAnnotation` | 10 | ≥ 1 v6-style semantic annotation with `subjectAppId` = this collection's appId. |
| `labJournal` | 5 | ≥ 1 lab-journal entry linked to this collection. |
| `keywords` | 5 | Always `false` server-side (conservative — keyword annotation endpoint pending). |
| `dataObjects` | 15 | Collection contains ≥ 1 DataObject. |

**Example:**

```bash
curl -fsS \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/collections/018f9c5a-7e26-7000-a000-000000000010/completeness
```

```json
{
  "score": 40,
  "maxScore": 100,
  "band": "error",
  "checks": [
    { "id": "name",              "label": "Name",                  "passed": true,  "points": 10 },
    { "id": "description",       "label": "Description (≥50 chars)","passed": true,  "points": 15 },
    { "id": "license",           "label": "License",               "passed": false, "points": 20 },
    { "id": "accessRights",      "label": "Access Rights",         "passed": false, "points": 10 },
    { "id": "creatorOrcid",      "label": "Creator ORCID",         "passed": false, "points": 10 },
    { "id": "semanticAnnotation","label": "Semantic Annotation",   "passed": false, "points": 10 },
    { "id": "labJournal",        "label": "Lab Journal Entry",     "passed": false, "points":  5 },
    { "id": "keywords",          "label": "Keywords",              "passed": false, "points":  5 },
    { "id": "dataObjects",       "label": "Data Objects",          "passed": true,  "points": 15 }
  ]
}
```

**CI / quality-gate usage:**

```bash
# Fail CI when completeness score is below 80 (green band)
SCORE=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  .../v2/collections/$COLL_APP_ID/completeness | jq '.score')
[ "$SCORE" -ge 80 ] || { echo "Collection FAIR score $SCORE < 80 — aborting"; exit 1; }
```

## See also

- `data-objects.md` — same `license` + `accessRights` fields, same shape.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.1` — the FAIR-1
  motivation and the deferred items (embargo-date, PID strategy).
- `aidocs/34-upstream-upgrade-path.md` — operator upgrade notes.
