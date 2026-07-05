---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP sweep — fire-420 (2026-07-05)

Triggered: no queued APISIMP rows after merging PR #2304 (APISIMP-PAGE-PARAM-ANNOTATION-INCONSISTENCY).
Scope: all `/v2/` REST resources, focusing on residual in-memory paging after fire-416 – fire-419 wave.

## What I found

### Finding F1 — Axis 1 (In-memory paging): `ProjectsRest.list()` → **NEW** `APISIMP-PROJECTS-LIST-IN-MEMORY-PAGING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/project/resources/ProjectsRest.java:96–103`

`GET /v2/projects` declares `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page` and
`@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize`, promising a real paged
response. However:

```java
List<String> all = projectsService.listProjectAppIds();
long total = all.size();
int from = (int) Math.min((long) page * pageSize, total);
int to = (int) Math.min((long) from + pageSize, total);
return Response.ok(new PagedResponseIO<>(all.subList(from, to), total, page, pageSize))
```

`projectsService.listProjectAppIds()` → `projectsDAO.findAllProjectAppIds()` executes:
```cypher
MATCH (proj:SemanticAnnotation { propertyIRI: $predProject })
WHERE proj.valueName = 'true' AND proj.subjectKind IN ['Collection', null]
WITH DISTINCT proj.subjectAppId AS appId
MATCH (c:Collection {appId: appId})
WHERE (c.deleted IS NULL OR c.deleted = false)
RETURN c.appId AS appId ORDER BY c.name
```
No `SKIP`/`LIMIT`. All project appIds are loaded before Java slices to the requested page.

The OpenAPI description even self-references `APISIMP-PROJECTS-LIST-NO-PAGINATION` (the filing note),
confirming this was already noted but never actioned.

**Urgency:** Low — a DLR instance today has O(10–50) projects, so the DB round-trip cost is trivial.
However the contract violation is present: the endpoint promises bounded queries via its pagination
params but issues none, and the pattern scales badly if Shepard instances adopt large project trees.

**Fix:** Add `countAllProjectAppIds()` (single COUNT Cypher) + `findAllProjectAppIds(int skip, int limit)`
(existing query + `SKIP $skip LIMIT $limit`) to `ProjectsDAO`. Update `ProjectsService.listProjectAppIds()`
to accept `(int skip, int limit)` overload (or add `count()` + bounded variant). Update
`ProjectsRest.list()` to call `service.count()` + bounded `service.list(skip, pageSize)`.

**Backlog row:** `APISIMP-PROJECTS-LIST-IN-MEMORY-PAGING` — filed fire-420.

---

### Finding F2 — Axis 1 (In-memory paging): `CollectionV2Rest.list()` → **NEW** `APISIMP-COLLECTIONS-IN-MEMORY-PAGING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java:172–190`

`GET /v2/collections` accepts `page`/`pageSize` but:

```java
var all = collectionService.getAllCollections(params).stream()
    .map(CollectionV2IO::new)
    .toList();
int total = all.size();
int from = (int) Math.min((long) page * pageSize, total);
int to = (int) Math.min((long) from + pageSize, total);
return Response.ok(new PagedResponseIO<>(all.subList(from, to), total, page, pageSize))
```

`CollectionService.getAllCollections()` loads **every Collection the caller can read** before Java slices.
The inline comment explicitly acknowledges `APISIMP-PAGINATION-ENVELOPE-1` — but no such backlog row
was filed.

**Complexity:** Medium. The current permission filtering happens in Java after loading all collections.
Pushing SKIP/LIMIT to the DB requires either (a) Cypher that joins the permission graph server-side,
or (b) accepting that `total` in the paged envelope counts only post-permission-filter collections (which
requires pre-filtering before pagination). Option (b) is the pragmatic path: pre-compute the
permission-filtered list of collection IDs at query time (the PermissionsService cache is already warm),
then issue a bounded `MATCH (c:Collection) WHERE c.appId IN $ids … SKIP $skip LIMIT $limit`.

**Urgency:** Medium — a single-tenant instance with thousands of collections (e.g. MFFD large deployment)
loads all before paging. Low today; grows linearly with collection count.

**Backlog row:** `APISIMP-COLLECTIONS-IN-MEMORY-PAGING` — filed fire-420. Size M.

---

### Finding F3 — Axis 1 (In-memory paging): `FlatPublicationsRest` + `PublicationsListRest` → **NEW** `APISIMP-PUBLICATIONS-IN-MEMORY-PAGING`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/publish/resources/FlatPublicationsRest.java:125–138`
- `backend/src/main/java/de/dlr/shepard/v2/publish/resources/PublicationsListRest.java:145`

Both publication list endpoints call `publicationDAO.findByEntityAppId(entityAppId)` — which returns
all `Publication` nodes for the entity, then slice in Java:

```java
List<Publication> rows = publicationDAO.findByEntityAppId(entityAppId);
// …stream().map(...)…
long total = all.size();
int from = (int) Math.min((long) page * pageSize, total);
int to   = (int) Math.min((long) from + pageSize, total);
return Response.ok(new PagedResponseIO<>(all.subList(from, to), ...))
```

**Urgency:** Very low — publications per entity are typically 1–5 (append-only DOI/ePIC mint history).
In practice this never materialises more than a handful of rows. Filed for completeness.

**Fix:** Add `countByEntityAppId(String)` + bounded `findByEntityAppId(String, int skip, int limit)`
to `PublicationDAO`. Update both REST callers.

**Backlog row:** `APISIMP-PUBLICATIONS-IN-MEMORY-PAGING` — filed fire-420. XS.

---

## Summary

| ID | Status | Target |
|----|--------|--------|
| F1 APISIMP-PROJECTS-LIST-IN-MEMORY-PAGING | 🆕 filed | `ProjectsRest.java:96–103`, `ProjectsDAO.findAllProjectAppIds()` |
| F2 APISIMP-COLLECTIONS-IN-MEMORY-PAGING | 🆕 filed | `CollectionV2Rest.java:172–190` |
| F3 APISIMP-PUBLICATIONS-IN-MEMORY-PAGING | 🆕 filed | `FlatPublicationsRest.java:125`, `PublicationsListRest.java:145` |

F1 selected as the fire-420 implementation slice (XS, clean DAO pattern, no permission complexity).
