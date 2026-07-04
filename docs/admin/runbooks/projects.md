---
layout: default
title: "Runbook — Mark a Collection as a Project + bundle sub-Collections"
description: "Promote a Collection to a Project, set funder/programme metadata, and bundle non-exclusive child Collections via urn:shepard:partOf — using only the v2 annotation endpoints."
stage: feature-defined
last-stage-change: 2026-06-02
audience: instance-admin
host: operator-machine
tested: "— (procedure derived from PROJ-PREDICATES-1 + PROJ-REST-1 spec; lands with the same PR)"
---

# Mark a Collection as a Project + bundle sub-Collections

> **When to use this runbook**: A research effort spans multiple Collections
> (one per process step, mission phase, test campaign quarter, …) and an
> operator needs the Projects top-nav row, the sub-Collections panel, and
> the cross-Collection by-annotation roll-up to start working.

A Project in Shepard is *not* a new entity kind. It is an ordinary
`:Collection` carrying three semantic annotations:

| Predicate | Cardinality | Meaning |
|---|---|---|
| `urn:shepard:project = "true"` | 0..1 | Role marker — makes the Collection appear at `/projects` and enables the sub-Collections panel on its detail page. |
| `urn:shepard:partOf = <projectAppId>` | 0..N | On a sub-Collection — declares the parent Project. A Collection may carry multiple `partOf` annotations to be a member of multiple Projects. |
| `urn:shepard:programme = <text>` | 0..N | On a Project — free-text funding-line label (e.g. `"Clean Aviation JU"`, `"DLR Project Line 4"`). |

The constraints are SHACL-enforced at write time via
`PROJ-SEMA-WRITE-GATE-1`. The runtime gate returns HTTP 422 with a
structured `application/problem+json` body when:

- the value of `urn:shepard:project` is anything other than the literal `"true"`,
- the target of `urn:shepard:partOf` is not itself a Project,
- `urn:shepard:partOf` self-references its own subject,
- `urn:shepard:programme` is set on a Collection that is not a Project.

---

## Procedure

You can drive this via the v2 REST endpoints (`curl`), the v2 admin CLI
(when shipped), or the in-app annotation dialog. All three call the same
`POST /v2/annotations` endpoint. The examples below use `curl`.

> **Prerequisite**: a Shepard API key with Write permission on every
> Collection involved. Export it as `SHEPARD_API_KEY` and the host as
> `SHEPARD_HOST` (e.g. `https://shepard.nuclide.systems`).

### Step 1 — promote a Collection to a Project

Find the Collection's appId (visible on its detail page header or via
`GET /v2/collections`). Then:

```bash
curl -X POST "$SHEPARD_HOST/v2/annotations" \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "018f9c5a-7e26-7000-a000-000000000001",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:project",
    "objectLiteral": "true",
    "sourceMode": "human"
  }'
```

A 201 response confirms the marker is in place. The Collection now:
- appears at `GET /v2/projects`,
- resolves at `GET /v2/projects/{appId}` (200 instead of 404),
- shows in the top-nav `/projects` page.

### Step 2 — add funder / programme metadata (optional)

For every programme line, repeat:

```bash
curl -X POST "$SHEPARD_HOST/v2/annotations" \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "018f9c5a-7e26-7000-a000-000000000001",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:programme",
    "objectLiteral": "Clean Aviation JU",
    "sourceMode": "human"
  }'
```

The programme chip appears in the Project's row at `/projects` and as a
chip on the sub-Collections panel.

### Step 3 — bundle a sub-Collection

For every child Collection, set its parent:

```bash
curl -X POST "$SHEPARD_HOST/v2/annotations" \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "018f9c5a-7e26-7000-a000-000000000010",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:partOf",
    "objectLiteral": "018f9c5a-7e26-7000-a000-000000000001",
    "sourceMode": "human"
  }'
```

Repeat for every sub-Collection. The same Collection can be a member of
**multiple** Projects — add one annotation per parent.

The child Collection now appears as a tile in the Project's sub-Collections
panel and the panel's child-count badge increments.

### Step 4 — verify

```bash
# Project envelope
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_HOST/v2/projects/018f9c5a-7e26-7000-a000-000000000001" | jq

# Sub-Collections
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_HOST/v2/projects/018f9c5a-7e26-7000-a000-000000000001/sub-collections" | jq
```

You should see `subCollectionCount`, `aggregateDoCount`, and the
`programmes` array populated.

---

## MFFD bootstrap

For the MFFD seed corpus there is a one-shot Python script that does
steps 0–4 idempotently:

```bash
SHEPARD_HOST=https://shepard.nuclide.systems \
SHEPARD_API_KEY=<your-key> \
  python3 examples/mffd-showcase/scripts/seed-mffd-collections.py --dry-run

SHEPARD_HOST=https://shepard.nuclide.systems \
SHEPARD_API_KEY=<your-key> \
  python3 examples/mffd-showcase/scripts/seed-mffd-collections.py
```

The script also creates the four MFFD UserGroups
(`mffd-afp-team`, `mffd-welding-team`, `mffd-ndt-team`, `mffd-cell-team`)
per `aidocs/integrations/119-mffd-collection-layout.md §1` and stamps the
two programme labels on the Project.

---

## Rollback

To un-Project a Collection, delete the `urn:shepard:project` annotation.
The Collection stays in place; only the Project affordances disappear:

```bash
# Find the annotation's appId
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_HOST/v2/annotations?subjectAppId=<projectAppId>&predicateIri=urn:shepard:project" | jq

# Delete it
curl -X DELETE -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_HOST/v2/annotations/<annotation-appId>"
```

Any child Collections that still carry `urn:shepard:partOf` pointing at the
former Project will become orphaned — the SHACL gate prevents *new*
`partOf` annotations targeting them, but existing ones survive until
explicitly deleted.

---

## Cross-references

- Design: `aidocs/integrations/121-project-and-subcollections.md`
- MFFD application: `aidocs/integrations/119-mffd-collection-layout.md §8`
- User-facing reference: `docs/reference/projects.md`
- Backlog (shipped 2026-06-02): `aidocs/16` rows
  `PROJ-PREDICATES-1`, `PROJ-REST-1`, `PROJ-REST-2`,
  `PROJ-SEMA-WRITE-GATE-1`, `PROJ-MCP-1`, `PROJ-MCP-2`,
  `PROJ-PANEL-1`, `PROJ-NAV-1`, `PROJ-BADGE-1`.
