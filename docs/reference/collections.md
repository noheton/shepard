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

## Metadata completeness score (FAIR4)

`GET /v2/collections/{appId}/metadata-completeness` returns a server-authoritative
0–100 completeness score with a 9-check breakdown.  External harvesters
(OpenAIRE, Helmholtz Databus) can query this endpoint to filter
DMP-grade Collections without requiring a browser session.

**Score bands:**

| Range | Meaning |
|---|---|
| `< 50` | Not publication-ready |
| `50–79` | Missing key FAIR fields |
| `≥ 80` | DMP-grade |

**Checks included:**

| checkId | Points | Condition |
|---|---|---|
| `name` | 10 | Collection `name` is non-blank |
| `description` | 15 | `description` ≥ 50 characters |
| `license` | 20 | SPDX `license` string set |
| `accessRights` | 10 | `accessRights` set |
| `creatorOrcid` | 10 | Creator has ORCID set in their profile |
| `semanticAnnotation` | 10 | ≥ 1 semantic annotation on any DataObject |
| `labJournal` | 5 | ≥ 1 lab-journal entry on any DataObject |
| `keywords` | 5 | ≥ 1 keyword-predicate annotation |
| `dataObjects` | 15 | ≥ 1 DataObject in the Collection |

**Example response:**

```json
GET /v2/collections/018f9c5a-7e26-7000-a000-000000000099/metadata-completeness
Authorization: Bearer <token>

{
  "collectionAppId": "018f9c5a-7e26-7000-a000-000000000099",
  "score": 80,
  "maxScore": 100,
  "percentage": 80.0,
  "checks": [
    { "checkId": "name",    "label": "Collection has a name",  "passed": true,  "weight": 10, "hint": "..." },
    { "checkId": "license", "label": "License (SPDX) set",     "passed": true,  "weight": 20, "hint": "..." },
    { "checkId": "keywords","label": "At least one keyword annotation", "passed": false, "weight": 5, "hint": "..." }
  ]
}
```

**Auth:** Read permission on the Collection.
Returns `401` when unauthenticated, `403` when no Read access, `404` for unknown `appId`.

The Collection sidebar also displays this score as a secondary chip next to the
client-side completeness gauge.  Divergence between the two chips is diagnostic
(e.g. stale frontend bundle vs. newer backend).

## See also

- `data-objects.md` — same `license` + `accessRights` fields, same shape.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.1` — the FAIR-1
  motivation and the deferred items (embargo-date, PID strategy).
- `aidocs/34-upstream-upgrade-path.md` — operator upgrade notes.
