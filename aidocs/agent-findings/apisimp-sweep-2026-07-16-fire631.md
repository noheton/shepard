---
stage: deployed
last-stage-change: 2026-07-16
fire: fire-631
---

# APISIMP Sweep — 2026-07-16 (fire-631)

Post-fire-630 sweep. This fire merged PR #2599 (TEMPLATE-TAGS-INMEM), closing the last
fire-627 row. No named APISIMP row was dispatchable; this sweep was run to find the next slice.

Previous sweeps: fire-627, fire-622, fire-623, fire-612, fire-577, fire-572, fire-570.

---

## Scope

Full v2 REST surface + SPI defaults + MCP tools + semantic resources. Checked against:
- In-memory pagination anti-patterns (load-all + subList)
- Fake `PagedResponseIO` (no real paging params or total stuck at results.size())
- Missing count before paged query (total = results.size() when results are already sliced)
- Per-kind endpoint sprawl
- Numeric Neo4j id leaks in `@PathParam` / `@QueryParam` / IO body
- Forbidden `@Path(Constants.SHEPARD_API + …)` additions

## Clean-gate result

- **Forbidden v1 paths**: zero new `@Path(Constants.SHEPARD_API + …)` in v2 namespace.
- **Numeric Long `@PathParam`/`@QueryParam`**: zero new leaks.
- **`DataObjectV2Rest.java:887,931`** (`predecessorChain`, `successorChain`): returns
  `PagedResponseIO<>(result, result.size(), 0, result.size())` — intentionally non-paged.
  The `depth` param caps the chain at 50 hops; description explicitly says "chains are
  bounded by the depth param, not paged." **Not a finding** — intentional contract.
- **`UserGroupV2Rest.java:111`**: fake-paged in the `?q=` search code path only; the
  non-search path already uses proper `countAllUserGroups()` + DAO-paged query.
  Low-severity (free-text search result sets are bounded in practice), but inconsistent.

---

## Finding F1 — APISIMP-REFS-INMEM-PAGING (size: M)

**`ReferenceKindHandler.java:141-161` — SPI default methods load all references then subList**

`ReferencesV2Rest.list()` (line 551) delegates to `handler.listByDataObject(appId, kind, skip, limit)`
and `handler.countByDataObject(appId, kind)`. The `ReferenceKindHandler` SPI declares these as
default methods that implement pagination in Java:

```java
// line 141: countByDataObject default
default long countByDataObject(String collectionAppId, String dataObjectAppId) {
  return listByDataObject(collectionAppId, dataObjectAppId).size(); // loads ALL
}

// line 156: listByDataObject(skip, limit) default
default List<R> listByDataObject(String collectionAppId, String dataObjectAppId, int skip, int limit) {
  List<R> all = listByDataObject(collectionAppId, dataObjectAppId); // loads ALL
  int from = (int) Math.min((long) skip, all.size());
  int to = (int) Math.min((long) from + limit, all.size());
  return all.subList(from, to);
}
```

A comment at line 146 already flags this as `APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING`.

All seven concrete handlers (`FileReferenceKindHandler`, `UriReferenceKindHandler`,
`CollectionReferenceKindHandler`, `DataObjectReferenceKindHandler`,
`FileBundleReferenceKindHandler`, `StructuredDataReferenceKindHandler`,
`TimeseriesReferenceKindHandler`) only implement the unparameterised
`listByDataObject(collectionAppId, dataObjectAppId)` — none override the count/paged
variants. Every paginated `GET /v2/references?...&page=X&pageSize=Y` request triggers
a full load of all references for that DataObject.

**Fix:** Each handler overrides `countByDataObject` and `listByDataObject(…, int skip, int limit)`
with a DAO method using Cypher `SKIP $skip LIMIT $limit`. Start with the two
highest-traffic handlers (`kind=file` and `kind=uri`). The container SPI already solved
this in `TimeseriesContainerKindHandler`, `FileContainerKindHandler`, and
`StructuredDataContainerKindHandler` — mirror that pattern.

**AC:** `GET /v2/references?kind=uri&dataObjectAppId=<id-with-50-refs>&page=0&pageSize=1`
returns `total=50`, not `total=1`. Neo4j `PROFILE` on that request shows a single
SKIP/LIMIT Cypher, not a full match.

**First refs:**
- `backend/src/main/java/de/dlr/shepard/v2/references/spi/ReferenceKindHandler.java:141`
- `backend/src/main/java/de/dlr/shepard/v2/references/spi/ReferenceKindHandler.java:161`
- `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java:551`
- **Blocked by**: None.

---

## Finding F2 — APISIMP-URDF-INMEM-PAGING (size: S)

**`AccessibleUrdfService.java:71-101` — loads up to 5 000 URDF candidates then subList**

`GET /v2/references/urdf` (the URDF picker autocomplete endpoint) calls
`singletonFileReferenceDAO.findAllUrdfCandidates()` which fetches up to `MAX_CANDIDATES = 5_000`
URDF singleton FileReferences with no server-side `q` filter or `LIMIT`. The service then
streams through a permission filter (`filterAllowedDataObjectAppIds`), applies an optional
name substring filter in Java, and finally calls `visible.subList(from, to)` (line 99-101).

Cost: every autocomplete keystroke on the "Visualise in 3D → URDF" picker fetches up to
5 000 rows even if `q=kr210` matches only 3 files.

**Fix:** Push the `q` filter and the `LIMIT` into `findAllUrdfCandidates(q, limit)` as
Cypher `WHERE name CONTAINS $q … LIMIT`. Permission filtering cannot be pushed fully to
DB, but the candidate set is dramatically reduced before that step.

**AC:** With > 200 URDF FileReferences in DB, `GET /v2/references/urdf?q=kr210&pageSize=10`
must not fetch all 200+ before filtering; Neo4j query log must show a bounded query.

**First refs:**
- `backend/src/main/java/de/dlr/shepard/v2/references/services/AccessibleUrdfService.java:71`
- `backend/src/main/java/de/dlr/shepard/v2/references/services/AccessibleUrdfService.java:101`
- **Blocked by**: None.

---

## Finding F3 — APISIMP-TERM-SEARCH-FAKE-TOTAL (size: XS)

**`SemanticTermSearchRest.java:246` — total always ≤ pageSize because it is set from results.size()**

`GET /v2/semantic/terms/search?q=…&page=0&pageSize=50` runs a properly SKIP/LIMIT-bounded
Cypher and returns at most 50 rows. Line 246 then sets:

```java
return Response.ok(new PagedResponseIO<>(results, results.size(), effectivePage, effectiveLimit))
    .header("X-Total-Count", (long) results.size())
    .build();
```

`results.size()` is always ≤ `effectiveLimit` (≤ 50). A client requesting page 0 of a
500-term ontology receives `{total: 50, page: 0, pageSize: 50}` and cannot tell whether
there are more pages — `total == pageSize` is ambiguous. Requesting page 1 is the only
way to discover the next page, which breaks standard pagination UX.

**Fix:** Add a sibling count Cypher (`RETURN count(r) AS total`, no SKIP/LIMIT, same
`WHERE` predicate) for both the fulltext and CONTAINS paths. Execute the count query
before the paged query; use the result in the `PagedResponseIO` envelope and the
`X-Total-Count` header. The count is a secondary read, so exceptions fall back to 0L
(same fail-soft pattern as `runSearch`).

**AC:** Seed 60 ontology terms matching `"mat"`.
`GET /v2/semantic/terms/search?q=mat&page=0&pageSize=50` must return
`total >= 60` (not `total == 50`). `mvn verify -pl backend` green.

**First refs:**
- `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticTermSearchRest.java:246`
- `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticTermSearchRest.java:98` (`FULLTEXT_CYPHER`)
- `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticTermSearchRest.java:131` (`CONTAINS_CYPHER`)
- **Blocked by**: None.

---

## Finding F4 — APISIMP-ADMIN-INMEM-PAGING (size: XS, low priority)

**`AdminUserGitCredentialRest.java:245-256` + `InstanceAdminRest.java:127-134`
— admin list endpoints slice in memory**

Both admin list endpoints load the full record set into a `List`, compute `.size()` as
total, then call `.subList()`. Same in-memory pattern as the earlier NOTIF-TRANSPORT-INMEM
/ TEMPLATE-TAGS-INMEM / NOTEBOOK-INMEM-PAGE fixes.

Both datasets are bounded by design (git credentials per user < 20;
instance-admin grants < 100 on any realistic instance) so performance impact is negligible.
Low-severity, but shape is inconsistent with the rest of the v2 admin surface.

**Fix:** Push `SKIP $skip LIMIT $limit` to the underlying Neo4j DAOs.

**AC:** Existing tests pass; no new `listAll()` call sites added.

**First refs:**
- `backend/src/main/java/de/dlr/shepard/v2/admin/users/AdminUserGitCredentialRest.java:245`
- `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:127`
- **Blocked by**: None. Lowest priority of the four.

---

## Filed rows

- `APISIMP-TERM-SEARCH-FAKE-TOTAL` (XS) → `aidocs/16` — **dispatched this fire** (branch `APISIMP-TERM-SEARCH-FAKE-TOTAL`)
- `APISIMP-URDF-INMEM-PAGING` (S) → `aidocs/16` — queued
- `APISIMP-REFS-INMEM-PAGING` (M) → `aidocs/16` — queued
- `APISIMP-ADMIN-INMEM-PAGING` (XS) → `aidocs/16` — queued (low priority)

---

## What surprised me

`SemanticTermSearchRest` is one of the more carefully written v2 resources — it has a
fulltext-index fast path with CONTAINS fallback, proper SKIP/LIMIT in both Cypher strings,
robust exception handling, and a comprehensive test suite with 14 tests. The fake-total bug
is the sole remaining correctness gap: a single `results.size()` substitution at line 246.
The same gap appears in the semantically similar `SemanticSparqlRest` (which doesn't paginate
at all), but the term-search endpoint is the only one that promises pagination via `page`/`pageSize`
params without honouring the total count.
