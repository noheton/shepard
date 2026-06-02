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

## Hero scene-graph link (COLL-SCENE-1, 2026-06-02)

A Collection can carry a single primary `:DigitalTwinScene`
("hero scene") that renders at the top of its detail page — useful
for surfacing the physical robot cell, test bench, or instrument
layout the Collection's DataObjects were produced in (e.g. the
MFFD `MFZ.rdk`-derived URDF appears above every track produced in
that cell).

The link is a scalar `sceneGraphAppId` property on the Collection
itself, with a dedicated `/v2/collections/{appId}/scene-graph`
resource for mutations:

- `GET /v2/collections/{appId}/scene-graph` — returns the linked
  scene's identity tuple (`sceneGraphAppId`, `name`, `description`,
  `rootFrameAppId`, `sourceFileAppId`, `frameCount`, `jointCount`).
  Returns 404 when the Collection has no scene linked. Requires
  Read on the Collection.
- `PUT /v2/collections/{appId}/scene-graph` — body
  `{"sceneGraphAppId":"<scene-uuid>"}`. Requires Write on the
  Collection AND Read on the target scene (the two-sided gate
  prevents linking a private scene the caller cannot themselves
  read). 200 on success, 400 missing body, 403 / 404 per the
  permission/existence checks.
- `DELETE /v2/collections/{appId}/scene-graph` — clears the link.
  Does **not** delete the `:DigitalTwinScene` itself — that stays
  addressable via `/v2/scene-graphs/{appId}`. Requires Write on the
  Collection. Idempotent (204 even when no link was set).

### UI

The Collection landing page mounts a `CollectionSceneGraphHeader`
band above the hero image when the link is set, rendering the
URDF via the existing URDF viewer (~360px tall, full-bleed). When
no scene is linked, writers see a "Link scene-graph" CTA that
opens a picker scoped to scenes the user can read; readers see
nothing. A dangling pointer (the scene was deleted out from under
the link) renders the standard 404 empty-state for the band
rather than crashing the page.

### What this does NOT do

The Collection→Scene link is a render affordance only. It does
**not** widen the scene's permission surface: the scene-side
walk (scene → FileReference → DataObject → Collection) defined
by `SceneGraphPermissionService` remains the source-of-truth for
who can read or edit the scene itself. A user who can write to
Collection A but cannot read scene B is blocked from linking B
to A by the PUT gate. Hand-built scenes (no `sourceFileAppId`)
stay admin-only regardless of how many Collections link to them.

### Related gaps (out of scope today)

- **RDK → URDF conversion** (`MFFD-RDK-URDF-CONVERTER` in
  `aidocs/16`) — for now an operator uploads a URDF (or runs
  `examples/mffd-rdk-urdf-showcase/scenegraph/build_mffd_scene.py`).
- **Direct RDK upload** (`MFFD-RDK-DIRECT-1`) — fallback path.
- **Multiple scenes per Collection** — `:Collection` carries one
  primary scene; multi-scene Collections (e.g. an AAS-shape
  registry) are a follow-on design.

## See also

- `data-objects.md` — same `license` + `accessRights` fields, same shape.
- `aidocs/semantics/98-shapes-views-and-process-model.md §4.1` — the FAIR-1
  motivation and the deferred items (embargo-date, PID strategy).
- `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md` GAP-6 — the
  COLL-SCENE-1 origin story.
- `aidocs/34-upstream-upgrade-path.md` — operator upgrade notes.
