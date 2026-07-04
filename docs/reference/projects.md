---
layout: default
title: "Projects — bundles of non-exclusive child Collections"
description: "A Project is a Collection that bundles one or more sub-Collections via the urn:shepard:partOf annotation. Use Projects to represent multi-step research efforts: a manufacturing campaign, a mission, a quarter-by-quarter hot-fire programme."
stage: feature-defined
last-stage-change: 2026-06-02
audience: user
---

# Projects

A **Project** in Shepard is a `:Collection` that has been marked with a
single semantic annotation, `urn:shepard:project = "true"`. That marker
unlocks three things:

1. The Collection appears at `/projects` in the top navigation.
2. Its detail page renders a **sub-Collections panel** showing every
   Collection that declares it as a parent.
3. A dedicated REST namespace, `/v2/projects/{appId}`, exposes
   Project-flavoured envelopes (programme metadata, aggregate counts,
   cross-Collection by-annotation roll-up) that the regular
   `/v2/collections/` surface does not.

There is no separate "Project" entity type. A Project is always a
Collection underneath — every existing Collection action (DataObject
list, snapshots, exports, permissions) keeps working.

## When to use a Project

Use a Project whenever a research effort spans more than one Collection
and you want a single entrypoint for it. Examples from the live
showcases:

- **MFFD Upper Shell** bundles five process-step Collections
  (`mffd-afp-tapelaying`, `mffd-bridge-welding`, `mffd-spot-welding`,
  `mffd-ndt-thermography`, `mffd-cell`) under one umbrella that carries
  the `Clean Aviation JU` + `DLR Project Line 4` programme labels.
- **LUMEN** would bundle each quarter's hot-fire test campaign so an
  auditor can land on the test programme and drill into TR-001 → TR-015
  one quarter at a time.
- **PLUTO** would bundle mission-phase Collections (Integration &
  Test, Launch, LEOP, Commissioning, Nominal Operations) so the
  mission-level dashboard lives at the Project.

## Non-exclusive bundling

A Collection can be a member of **multiple** Projects. Add one
`urn:shepard:partOf` annotation per parent — there is no
single-parent constraint. The sub-Collections panel shows an
"also in N other Projects" chip on each row whose Collection belongs
to more than one Project.

This is useful when a cross-cutting research initiative wants to gather
Collections that already belong to a programme — e.g. a "Composites
across DLR" initiative that pulls in MFFD step Collections without
forcing a hierarchy.

## Funder / programme metadata

The `urn:shepard:programme` annotation carries a free-text funder or
DLR-internal programme name. It is only valid on a Collection that is
itself a Project. A Project can declare more than one programme — for
instance both a EU JU funder line and a DLR-internal project line.

The values are intentionally free-text in this first cut. When the
same value appears on three or more Projects we may promote it to a
controlled term (see `aidocs/integrations/121 §10.2`); for now the
simplest path is to just write it.

## Top-nav entry — /projects

The top navigation carries a **Projects** entry between Collections
and Containers. The page lists every Project on the instance, with:

- name and description
- the programme labels (chip strip)
- sub-Collection count and aggregated DataObject count
- last-activity timestamp (max across sub-Collections)

A side filter lets you narrow to a single programme value when the list
grows.

## Sub-Collections panel

On a Collection's detail page, when the Collection is a Project, the
**Sub-Collections** panel renders above the DataObjects list. It shows:

- the Project's programme chips (a small strip)
- one tile per sub-Collection, with the child's name, DataObject count,
  owner UserGroup, and last-activity timestamp
- "also in N Projects" chips on any sub-Collection that belongs to
  more than one Project

Click a tile to open the child Collection's detail page.

The panel hides itself entirely when the Collection has no
`urn:shepard:project` marker. There is no UI cost to having the
component mounted on every Collection-detail page — it only renders for
Projects.

## REST surface

The `/v2/projects/{appId}` namespace is the canonical Project surface.
The underlying Collection (whether it's a Project or not) is always
still reachable via `/v2/collections/{appId}`.

| Endpoint | Returns |
|---|---|
| `GET /v2/projects` | Array of every Project's appId (ordered by name). |
| `GET /v2/projects/{appId}` | Project envelope: name, description, programmes, subCollectionCount, aggregateDoCount, lastActivityMillis. 404 when `{appId}` is not a Project. |
| `GET /v2/projects/{appId}/sub-collections` | Programme strip + list of sub-Collections with per-row counts and `alsoMemberOf` chips. |
| `GET /v2/projects/{appId}/by-annotation/{predicate}/{value}` | Every DataObject across the Project's sub-Collections whose annotation matches `{predicate} = {value}`. Supports pagination via `?page=N&pageSize=K`. |

For the write path (marking a Collection as a Project, declaring
`partOf`, adding `programme`) use the existing
`POST /v2/annotations` endpoint — see the
[admin runbook](../admin/runbooks/projects.md).

## MCP tools

Claude / agent clients have four MCP tools:

- `list_projects` — every Project appId
- `get_project` — single Project envelope
- `get_project_sub_collections` — child Collections + programme strip
- `query_project_by_annotation` — cross-Collection roll-up

These are the agent-shaped wrapping of the REST endpoints above.

## Constraints (SHACL)

The annotation write gate enforces:

- `urn:shepard:project` value must be the literal `"true"` (rejected
  with 422 if you write `"false"` or an IRI),
- `urn:shepard:partOf` target must itself be a Project — i.e. it must
  already carry `urn:shepard:project = "true"`,
- `urn:shepard:partOf` cannot self-reference its own subject,
- `urn:shepard:programme` is only valid on a Project Collection.

A violation returns HTTP 422 with a `application/problem+json` body that
names the failing constraint.

## See also

- Operator runbook: [Mark a Collection as a Project + bundle sub-Collections](../admin/runbooks/projects.md)
- Design: `aidocs/integrations/121-project-and-subcollections.md`
- MFFD application: `aidocs/integrations/119-mffd-collection-layout.md`
