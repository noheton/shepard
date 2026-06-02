---
stage: feature-defined
last-stage-change: 2026-06-02
---

# 121 — Project & Sub-Collections API

Allows a Collection to be annotated as a "project" grouping, with child
Collections declared as members via semantic annotations. The back-end
provides a single read endpoint for browsing the project/sub-collection
structure without requiring the caller to assemble it from raw annotations.

## Background

Shepard Collections are the top-level grouping primitive. For multi-campaign
research programmes (e.g. Clean Aviation JU / MFFD programme) it is useful to
treat one Collection as a "project hub" and mark other Collections as members.
This is modelled purely via semantic annotations — no new graph edge type,
no schema migration — so it is forward-compatible with any Collection.

The predicates:

| Predicate | Applied to | Meaning |
|---|---|---|
| `urn:shepard:project` | Parent Collection | Value `"true"` marks it as a project hub |
| `urn:shepard:programme` | Parent Collection | Value is the programme name (repeatable) |
| `urn:shepard:partOf` | Child Collection | Value is the parent Collection's `appId` |

## §3 REST surface

### §3.1 GET `/v2/collections/{collectionAppId}/sub-collections`

Returns the sub-collection membership view for a given parent Collection.

**Auth:** Read permission on the parent Collection.

**Response shape (200 OK):**

```json
{
  "parentAppId": "<this collection appId>",
  "parentIsProject": true,
  "programmes": ["Clean Aviation JU", "DLR Project Line 4"],
  "subCollections": [
    {
      "appId": "...",
      "id": 42,
      "name": "LUMEN Hot-Fire Campaign Q3",
      "heroImage": null,
      "doCount": 8251,
      "lastActivity": null,
      "ownerGroup": null,
      "alsoMemberOf": ["<other-project-appId>"]
    }
  ]
}
```

**Empty case (200 OK — parent exists but no children):**

```json
{
  "parentAppId": "...",
  "parentIsProject": false,
  "programmes": [],
  "subCollections": []
}
```

**Error cases:**

| Code | Condition |
|---|---|
| 401 | No JWT / API-key present |
| 403 | Caller lacks Read permission on the parent |
| 404 | No Collection with that appId |

**Trim vs. full shape:**

- Default (or `?include=trim`): returns `SubCollectionEntryIO` for each child.
- `?include=full`: reserved for PROJ-REST-1b. Currently accepted but returns
  the same trim shape — documented in the resource Javadoc.

**Cypher:**

```cypher
MATCH (c:Collection {appId: $parentAppId})
OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(proj:SemanticAnnotation
  {predicate: 'urn:shepard:project', value: 'true'})
OPTIONAL MATCH (c)-[:HAS_SEMANTIC_ANNOTATION]->(prog:SemanticAnnotation
  {predicate: 'urn:shepard:programme'})
OPTIONAL MATCH (child:Collection)-[:HAS_SEMANTIC_ANNOTATION]->(p:SemanticAnnotation
  {predicate: 'urn:shepard:partOf', value: $parentAppId})
WHERE child <> c
OPTIONAL MATCH (child)-[:HAS_SEMANTIC_ANNOTATION]->(also:SemanticAnnotation
  {predicate: 'urn:shepard:partOf'})
WHERE also.value <> $parentAppId
OPTIONAL MATCH (child)-[:HAS_DATAOBJECT]->(do:DataObject)
WHERE do.deleted IS NULL OR do.deleted = false
WITH c, proj,
     collect(DISTINCT prog.value) AS programmes,
     child,
     collect(DISTINCT also.value) AS alsoMemberOf,
     count(DISTINCT do) AS doCount
RETURN
  (proj IS NOT NULL) AS isProject,
  programmes,
  child.appId       AS childAppId,
  id(child)         AS childId,
  child.name        AS childName,
  child.heroImage   AS heroImage,
  doCount,
  alsoMemberOf
ORDER BY childName ASC
```

**Implementation files:**

- Resource: `de.dlr.shepard.v2.collection.resources.CollectionSubCollectionsRest`
- DAO: `de.dlr.shepard.v2.collection.daos.SubCollectionsDAO`
- IO: `de.dlr.shepard.v2.collection.io.SubCollectionsIO`
      `de.dlr.shepard.v2.collection.io.SubCollectionEntryIO`
- Tests: `de.dlr.shepard.v2.collection.resources.CollectionSubCollectionsRestTest`

**Backlog row:** `PROJ-REST-1` in `aidocs/16-dispatcher-backlog.md`.

## §4 Future slices

- **PROJ-REST-1b** — `?include=full` returns a full `CollectionIO` for each child.
- **PROJ-REST-2** — `POST /v2/collections/{appId}/sub-collections` creates a
  `urn:shepard:partOf` annotation on a child Collection (or accepts the child's
  appId and wires the annotation via the annotations API).
- **PROJ-REST-3** — Permission-filter sub-collections to only those the caller
  may Read (current behaviour returns all children visible to Cypher regardless
  of per-child permissions).

## §5 No migration required

No Neo4j migration. The feature is pure read-only projection over existing
`:SemanticAnnotation` nodes. Operators who want to use the project/sub-collection
structure simply annotate their Collections via the existing
`POST /v2/annotations` endpoint or the UI annotation dialog.
