---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP sweep — fire-416 (2026-07-05)

Triggered: no queued APISIMP rows after merging PR #2300 (APISIMP-LAB-JOURNAL-HISTORY-IN-MEMORY-PAGING).
Scope: all `/v2/` REST resources, focusing on in-memory paging, fake PagedResponseIO, and uncapped list endpoints.

## What I found

### Finding F1 — Axis 1 (In-memory paging): `CollectionWatchersRest.java` → **NEW** `APISIMP-COLLECTION-WATCHERS-IN-MEMORY-PAGING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/resources/CollectionWatchersRest.java:81–98`

The `GET /v2/collections/{collectionAppId}/watches` endpoint declares `@QueryParam("page") @PositiveOrZero int page` and `@QueryParam("pageSize") @Min(1) @Max(200) int pageSize`, promising real paginated responses. However:

```java
List<CollectionWatcherIO> all = service.list(collectionAppId, caller);
// service.list() → dao.findByCollectionAppId() — full Cypher MATCH with no LIMIT
long total = all.size();
int from = (int) Math.min((long) page * pageSize, total);
int to = (int) Math.min((long) from + pageSize, total);
return Response.ok(new PagedResponseIO<>(all.subList(from, to), total, page, pageSize))
```

`CollectionWatcherDAO.findByCollectionAppId()` executes:
```cypher
MATCH (w:CollectionWatcher) WHERE w.collectionAppId = $collectionAppId RETURN w ORDER BY w.since ASC
```
No `SKIP`/`LIMIT`. A collection with 10,000 followers (LUMEN hotfire collection after public release) materialises all 10,000 `CollectionWatcher` nodes before Java slices to page-0's 50.

**Fix:** Add `countByCollectionAppId(String)` + `findByCollectionAppId(String, int skip, int limit)` to `CollectionWatcherDAO`. Update `CollectionWatcherService.list()` to accept skip+limit and delegate. Update REST layer to call `service.count()` + bounded `service.list()`.

**Backlog row:** `APISIMP-COLLECTION-WATCHERS-IN-MEMORY-PAGING` — filed fire-416.

---

### Finding F2 — Axis 1 (In-memory paging): `CollectionWatchesRest.java` → **NEW** `APISIMP-COLLECTION-WATCHES-IN-MEMORY-PAGING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/watches/resources/CollectionWatchesRest.java:109–118`

`GET /v2/collections/{collectionAppId}/watched-containers` (WATCH1) has the identical pattern: declares `page`/`pageSize` query params but calls `service.list(collectionAppId)` which delegates to:

```cypher
MATCH (w:Watch) WHERE w.collectionAppId = $collectionAppId RETURN w ORDER BY w.since ASC
```

in `WatchDAO.findByCollectionAppId()` — no `SKIP`/`LIMIT`. Java `subList` follows.

**Fix:** Add `countByCollectionAppId(String)` + `findByCollectionAppId(String, int skip, int limit)` to `WatchDAO`. Update `WatchService` and `CollectionWatchesRest` similarly.

**Backlog row:** `APISIMP-COLLECTION-WATCHES-IN-MEMORY-PAGING` — filed fire-416.

---

### Finding F3 — Axis 1 (In-memory paging): `FileBundleReferenceRest.listGroupFiles()` → **NEW** `APISIMP-FILEBUNDLE-LISTGROUPFILES-IN-MEMORY-PAGING`

**File:** `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java:448–479`

`GET /v2/references/{appId}/files` (the list-files-in-group endpoint) calls `group.getFiles()` — an OGM lazy-load on the `FileBundleReference` entity that loads **all** file nodes from Neo4j — then slices with Java `subList`. Uses bare `Integer page` with manual null checks (`if (page == null || page < 0) page = 0`) rather than `@DefaultValue`+`@PositiveOrZero`.

This is the critical one for MFFD: an ImageBundle carrying 5,000 PNG frames loads all 5,000 OGM `File` nodes before returning page-0's 50. At scale this exhausts the OGM session heap.

**Fix:** Push `SKIP/LIMIT` to a dedicated `FileDAO.findByBundleAppId(String, int skip, int limit)` + `countByBundleAppId(String)` Cypher pair. Replace `group.getFiles().subList()` with bounded DAO call. Normalise param annotations to `@DefaultValue`+`@PositiveOrZero`/`@Min`/`@Max`.

**Backlog row:** `APISIMP-FILEBUNDLE-LISTGROUPFILES-IN-MEMORY-PAGING` — filed fire-416.

---

### Finding F4 — `OntologyAlignmentRest` — **ALREADY SHIPPED** (APISIMP-ALIGNMENT-UNBOUNDED, PR #2295, fire-411)

The sweep agent flagged `GET /v2/semantic/ontology/alignment` as returning an unbounded list. This was already addressed: backlog row 3935 confirms `✅ merged fire-411 (PR #2295, sha: 31e189e)`. Not a new finding.

---

### Finding F5 — `NotificationTransportRest` — **ALREADY SHIPPED** (APISIMP-NOTIFICATIONS-FAKE-PAGINATION, PR #2298, fire-415)

The sweep agent flagged `GET /v2/admin/notifications/transports` as wrapping in `PagedResponseIO` with no pagination params. This was addressed by PR #2298: the fake wrapper was dropped; the endpoint now returns a plain JSON array. Backlog row 3939 confirms. Not a new finding.

---

### Finding F6 — Axis 3 (param-annotation inconsistency): `NotificationRest.java` + `CollectionLabJournalEntriesRest.java` → **NEW** `APISIMP-PAGE-PARAM-ANNOTATION-INCONSISTENCY`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationRest.java:66–87` — `page`/`pageSize` declared + annotated; `service.listForUser()` loads **all** notifications then Java slices (same in-memory pattern as F1/F2, but smaller practical scale since service caps at 200 rows)
- `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java:127–151` — `entriesDAO.findByCollectionAppId()` loads all entries, orphan-filter loop then Java slices (bounded SKIP/LIMIT push complicated by orphan-skip, but still doable: push bounded query + post-filter + top-up loop)

Both are in-memory paging, secondary to F1–F3 in urgency. `NotificationRest` has an existing service-level `200` cap which partially mitigates. `CollectionLabJournalEntriesRest` is more exposed on large MFFD collections.

**Backlog row:** `APISIMP-PAGE-PARAM-ANNOTATION-INCONSISTENCY` — filed fire-416, tracking both endpoints together.

---

## Summary

| ID | Status | Target |
|----|--------|--------|
| F1 APISIMP-COLLECTION-WATCHERS-IN-MEMORY-PAGING | 🆕 filed | `CollectionWatchersRest.java:91–97` |
| F2 APISIMP-COLLECTION-WATCHES-IN-MEMORY-PAGING | 🆕 filed | `CollectionWatchesRest.java:112–118` |
| F3 APISIMP-FILEBUNDLE-LISTGROUPFILES-IN-MEMORY-PAGING | 🆕 filed | `FileBundleReferenceRest.java:448–479` |
| F4 APISIMP-ALIGNMENT-UNBOUNDED | ✅ already shipped PR #2295 | — |
| F5 APISIMP-NOTIFICATIONS-FAKE-PAGINATION | ✅ already shipped PR #2298 | — |
| F6 APISIMP-PAGE-PARAM-ANNOTATION-INCONSISTENCY | 🆕 filed | `NotificationRest.java:77`, `CollectionLabJournalEntriesRest.java:127` |

F1 selected as the fire-416 implementation slice (XS, clean DAO pattern, no orphan-filter complication).
