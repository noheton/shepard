---
stage: deployed
last-stage-change: 2026-07-06
---

# APISIMP Sweep — fire-445 (2026-07-06)

Scanned: `backend/src/main/java/de/dlr/shepard/v2/**` REST resources + plugin REST files.
Found 6 new violations not yet tracked in the backlog.

---

## Finding 1 — APISIMP-SNAP-PINNED-IN-MEMORY-PAGING

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotPinnedReadRest.java:156`
**Endpoint:** `GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects`
**Size:** M

`getDataObjects()` calls `snapshotService.listDataObjectAppIds(snapshot)`, which calls
`snapshotDAO.getEntryAppIds(snapshot.getId())` — an unbounded Cypher query loading every
`SnapshotEntry` object into a `Set<String>`. The full set is then passed as a Cypher
parameter list to `filterDataObjectAppIds()`, re-querying Neo4j for all of them. Finally,
the REST layer slices with `subList(from, to)` at line 159. A paged DAO overload
`findEntriesBySnapshot(id, skip, limit)` exists at line 194 but is never called.
For snapshots with thousands of DataObjects this is an unbounded Neo4j + heap load.

**AC:** Replace `listDataObjectAppIds` call with the paged path using `findEntriesBySnapshot(id, skip, limit)`;
add `countDataObjectAppIds(Snapshot)` to the service; remove `subList()` from the REST method.

---

## Finding 2 — APISIMP-VOCAB-USED-BY-BARE-LIST

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/VocabularyBrowseRest.java:219`
**Endpoint:** `GET /v2/semantic/vocabularies/used-by/{entityAppId}`
**Size:** S

`listVocabulariesUsedBy()` returns a bare `List<VocabularyIO>` with no `PagedResponseIO`
envelope and no `page`/`pageSize` params. With `scope=collection` the DAO walks
`[:HAS_DATAOBJECT*0..]` descendants and could return vocabularies from hundreds of
DataObjects. The sibling `GET /v2/semantic/vocabularies` (line 107) correctly uses
`PagedResponseIO` with SKIP/LIMIT; this endpoint does not.

**AC:** Add `page`/`pageSize` params with `@Parameter`, push SKIP/LIMIT to
`vocabularyDAO.findVocabulariesUsedByEntity`, wrap response in `PagedResponseIO`.

---

## Finding 3 — APISIMP-COLL-TEMPLATES-ANNOT-MISSING-PARAM

**File:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/CollectionTemplatesRest.java:100–101, 125–126`
**Endpoints:** `GET /v2/collections/{appId}/templates/allowed`, `GET /v2/collections/{appId}/templates/used`
**Size:** XS

Both endpoints accept `page` and `pageSize` `@QueryParam` fields but neither carries
`@Parameter(description=...)` annotations. The OpenAPI generator emits these params with
no description and no schema constraints, making them opaque to API consumers. Sibling
endpoints in `ShepardTemplateRest` and `CollectionLabJournalEntriesRest` carry proper
`@Parameter` annotations.

**AC:** Add `@Parameter(description="…")` before `@QueryParam("page")` and
`@QueryParam("pageSize")` in both methods.

---

## Finding 4 — APISIMP-TEMPLATE-TAGS-BARE-LIST

**File:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/ShepardTemplateRest.java:304`
**Endpoint:** `GET /v2/templates/tags`
**Size:** S

Returns `Response.ok(dao.listDistinctTags(kind)).build()` — a bare `List<String>` with no
`PagedResponseIO` envelope, no `total` field, and no `page`/`pageSize` params. A 500-tag
server-side cap is implemented inside the DAO but callers cannot know the total count or
page beyond 500. The rest of `/v2/templates` (`list`, `listAllowed`, `listUsed`) consistently
uses `PagedResponseIO`.

**AC:** Add `page`/`pageSize` params; push SKIP/LIMIT to `dao.listDistinctTags`; add
`countDistinctTags(kind)` DAO query; wrap response in `PagedResponseIO<String>`.

---

## Finding 5 — APISIMP-TEMPLATE-IMPORT-BARE-LIST

**File:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/TemplatePortabilityRest.java:177, 198, 260`
**Endpoint:** `POST /v2/templates/import`
**Size:** S

All three return paths (empty/null body, empty parsed list, successful import) return bare
`List<ShepardTemplateIO>` via `Response.ok(...).build()`. Clients cannot tell how many
entries were created vs skipped vs updated without counting the list. Every other mutation
endpoint on the v2 surface returns a typed wrapper.

**AC:** Introduce `TemplateImportResultIO { List<ShepardTemplateIO> items, int created, int updated, int skipped }`;
all three return paths must return it.

---

## Finding 6 — APISIMP-REFANNOT-PAGE-MISSING-PARAM

**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java:156–157`
**Endpoint:** `GET /v2/references/{appId}/annotations`
**Size:** XS

`page` (line 156) and `pageSize` (line 157) are `@QueryParam` fields with `@DefaultValue`,
`@Min`, and `@Max` validation, but neither carries `@Parameter(description=...)`. The inline
Javadoc mentions them but the OpenAPI spec generator emits them without description or
schema constraints. Same pattern as the already-fixed CHANNEL-ANNOTATION-MISSING-SCHEMA.

**AC:** Add `@Parameter(description="Zero-based page index (default 0).")` before
`@QueryParam("page")` and `@Parameter(description="Items per page (1–1000). Default 200.")`
before `@QueryParam("pageSize")`.

---

## Summary

| APISIMP ID | Size | Endpoint | Problem |
|---|---|---|---|
| APISIMP-SNAP-PINNED-IN-MEMORY-PAGING | M | `GET /v2/collections/{appId}/snapshots/{snapId}/data-objects` | In-memory `subList` paging; unbounded DAO load |
| APISIMP-VOCAB-USED-BY-BARE-LIST | S | `GET /v2/semantic/vocabularies/used-by/{entityAppId}` | Bare list, no PagedResponseIO |
| APISIMP-COLL-TEMPLATES-ANNOT-MISSING-PARAM | XS | `/v2/collections/{appId}/templates/allowed+used` | Missing `@Parameter` annotations |
| APISIMP-TEMPLATE-TAGS-BARE-LIST | S | `GET /v2/templates/tags` | Bare list, no PagedResponseIO |
| APISIMP-TEMPLATE-IMPORT-BARE-LIST | S | `POST /v2/templates/import` | Untyped bare list response |
| APISIMP-REFANNOT-PAGE-MISSING-PARAM | XS | `GET /v2/references/{appId}/annotations` | Missing `@Parameter` annotations |

Smallest dispatchable: **APISIMP-COLL-TEMPLATES-ANNOT-MISSING-PARAM** + **APISIMP-REFANNOT-PAGE-MISSING-PARAM** (batch XS, 4 annotation additions).
