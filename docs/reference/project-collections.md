---
title: Project Collections (reference)
description: How to organise Collections into Projects using semantic annotations — marking a Project root, adding sub-Collections, and tagging with a research programme.
permalink: /reference/project-collections/
layout: default
audience: user
---
# Project Collections

A **Project** in Shepard is an ordinary Collection that carries a single semantic
annotation marking it as a project root. Sub-collections are linked to it via a
second annotation. No special backend type is needed — the pattern works today via
the standard annotation endpoints.

Membership is **non-exclusive**: a Collection can belong to multiple Projects at once,
which lets cross-cutting research initiatives gather relevant Collections without
forcing a rigid hierarchy.

---

## Marking a Collection as a Project

Add a semantic annotation to the Collection you want to designate as a Project:

| Field | Value |
|---|---|
| Predicate | `urn:shepard:project` |
| Value (literal) | `true` |

### Via the Annotation dialog

1. Open the Collection's detail page.
2. Click **Annotate** (the annotation icon in the header or the toolbar).
3. In the predicate field type `urn:shepard:project`; select it when it appears in the autocomplete.
4. In the value field enter `true`.
5. Click **Save**.

The Collection now appears in the `/projects` top-level list and gains a "Project" chip
on Collection list views.

### Via REST

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "<collection-appId>",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:project",
    "objectLiteral": "true"
  }' \
  https://shepard.example.org/v2/annotations
```

Response `201 Created` includes the annotation's own `appId` — save it if you later
want to remove the Project marker.

---

## Adding a sub-Collection

On each Collection you want to include in a Project, add a `urn:shepard:partOf`
annotation whose value is the Project Collection's `appId`.

| Field | Value |
|---|---|
| Predicate | `urn:shepard:partOf` |
| Value (literal) | The Project Collection's `appId` (UUID v7) |

**Non-exclusive:** you can add multiple `urn:shepard:partOf` annotations to the same
Collection — one per Project it belongs to. This is the normal way to include shared
work (e.g. an anomaly-investigation Collection) in more than one Project simultaneously.

**SHACL constraint:** the target of `urn:shepard:partOf` must itself carry
`urn:shepard:project = true`. Shepard rejects an annotation that points at a Collection
that is not a Project.

### Via the Annotation dialog

1. Open the sub-Collection's detail page.
2. Click **Annotate**.
3. Enter predicate `urn:shepard:partOf` and the Project's `appId` as the value.
4. Click **Save**.

The sub-Collection now appears as a tile in the Project Collection's Sub-collections
panel, and shows a "member of …" chip in Collection list views.

### Via REST

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "<sub-collection-appId>",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:partOf",
    "objectLiteral": "<project-collection-appId>"
  }' \
  https://shepard.example.org/v2/annotations
```

---

## Tagging a Project with its research programme

On the Project Collection itself, add a `urn:shepard:programme` annotation for
each funding line or programme the Project is accounted under.

| Field | Value |
|---|---|
| Predicate | `urn:shepard:programme` |
| Value (literal) | Free-text programme name |

A Project can carry multiple `urn:shepard:programme` annotations — one per programme
line. Example values: `"Clean Aviation JU"`, `"DLR Project Line 4 (Composites)"`,
`"Horizon Europe — Cluster 5"`.

**SHACL constraint:** `urn:shepard:programme` is only valid on a Collection that already
carries `urn:shepard:project = true`. Shepard rejects a programme annotation on an
ordinary (non-Project) Collection.

### Via the Annotation dialog

1. Open the Project Collection's detail page.
2. Click **Annotate**.
3. Enter predicate `urn:shepard:programme` and the programme name as the value.
4. Click **Save**.

Programme names appear as chips in the Sub-collections panel header and in the
`/projects` listing, and are filterable on the Projects page.

### Via REST

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectAppId": "<project-collection-appId>",
    "subjectKind": "Collection",
    "predicateIri": "urn:shepard:programme",
    "objectLiteral": "Clean Aviation JU"
  }' \
  https://shepard.example.org/v2/annotations
```

---

## Removing a sub-Collection from a Project

Delete the specific `urn:shepard:partOf` annotation from the sub-Collection. This
removes the membership link without affecting the sub-Collection itself or the Project.

### Via the Annotation dialog

1. Open the sub-Collection's detail page.
2. Open the **Annotations** panel.
3. Find the `urn:shepard:partOf` annotation pointing at the Project you want to
   remove it from.
4. Click the **Delete** (trash) icon on that annotation row and confirm.

When a Collection has multiple `urn:shepard:partOf` annotations (member of several
Projects), deleting one removes membership from that Project only; the others remain.

### Via REST

```bash
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/annotations/<annotation-appId>
```

Use the `appId` of the specific `urn:shepard:partOf` annotation you want to remove.
To find it:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://shepard.example.org/v2/annotations?subjectAppId=<sub-collection-appId>&subjectKind=Collection"
```

Filter the result for `predicateIri == "urn:shepard:partOf"` and the `objectLiteral`
matching the Project you want to unlink.

---

## Listing sub-Collections

The dedicated endpoint returns all sub-Collections of a Project in one call, without
iterating annotations manually:

```bash
GET /v2/collections/{appId}/sub-collections
```

Response:

```json
{
  "parentAppId": "<project-collection-appId>",
  "parentIsProject": true,
  "programmes": ["Clean Aviation JU", "DLR Project Line 4 (Composites)"],
  "subCollections": [
    {
      "appId": "...",
      "name": "MFFD — AFP Tapelaying",
      "doCount": 8251,
      "lastActivity": "2026-05-30T14:22:00Z",
      "ownerGroup": "mffd-afp-team",
      "alsoMemberOf": ["<other-project-appId>"]
    }
  ]
}
```

`alsoMemberOf` lists the `appId` values of any other Projects a child Collection also
belongs to — useful for identifying shared or cross-cutting Collections at a glance.

---

## REST API quick reference

| Operation | HTTP method + path |
|---|---|
| Mark a Collection as a Project | `POST /v2/annotations` with `predicateIri: "urn:shepard:project"`, `objectLiteral: "true"` |
| Add a sub-Collection to a Project | `POST /v2/annotations` with `predicateIri: "urn:shepard:partOf"`, `objectLiteral: "<project.appId>"` |
| Tag a Project with a programme | `POST /v2/annotations` with `predicateIri: "urn:shepard:programme"`, `objectLiteral: "<programme name>"` |
| List sub-Collections of a Project | `GET /v2/collections/{appId}/sub-collections` |
| Remove a sub-Collection from a Project | `DELETE /v2/annotations/{annotation-appId}` (the `partOf` annotation's own appId) |
| List all Projects | `GET /v2/collections?annotationFilter=urn:shepard:project=true` (or browse `/projects` in the UI) |

---

## Permission model

Mutating any of the three project predicates (`urn:shepard:project`,
`urn:shepard:partOf`, `urn:shepard:programme`) requires either:

- the `instance-admin` role, **or**
- write access to **both** the Project Collection and the sub-Collection being linked.

This prevents a user from silently adding someone else's Collection to their Project
without the other Collection's owner being able to object.

---

## Example: MFFD project setup

The MFFD upper shell programme uses this pattern to give researchers a single entry
point into all six process-step Collections:

1. Create (or designate) a Collection named `MFFD Upper Shell — Project`.
2. Mark it as a Project: `urn:shepard:project = true`.
3. Add two programme annotations: `urn:shepard:programme = "Clean Aviation JU"` and
   `urn:shepard:programme = "DLR Project Line 4 (Composites)"`.
4. On each of the six step Collections (AFP Tapelaying, Ultrasonic Welding, …), add
   `urn:shepard:partOf = <mffd-project-appId>`.

The researcher's workflow becomes:

1. Navigate to `/projects`.
2. Click the **MFFD Upper Shell** row.
3. Land on the Project Collection detail page, which shows the six step tiles in the
   Sub-collections panel with programme chips above them.
4. Click any tile to navigate into that step's Collection.

---

## See also

- [Semantic annotations (reference)](/reference/semantic-annotations/) — full
  reference for the annotation endpoints and data model.
- [Collections (reference)](/reference/collections/) — Collection fields, access rights,
  license, and lineage graph.
- `aidocs/integrations/121-project-and-subcollections.md` — design document for the
  Project-as-Collection pattern, including the SHACL constraints and Cypher migration.
- `aidocs/integrations/119-mffd-collection-layout.md` — MFFD-specific application of
  this pattern.
