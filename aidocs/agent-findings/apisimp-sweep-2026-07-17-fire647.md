---
stage: fragment
last-stage-change: 2026-07-17
---

# APISIMP sweep — fire-647 / 2026-07-17

Scanned: `backend/src/main/java/de/dlr/shepard/v2/` (all REST resources + IO classes).
Prior sweeps covered: fire-608 through fire-641. This sweep focuses on admin listing
endpoints, envelope-shape inconsistencies, and the DQR evaluation path.

Skipped as duplicates: `APISIMP-PERM-AUDIT-NUMID` (already filed as `APISIMP-PERM-AUDIT-NEO4J-ID`).

---

## Finding 1 — APISIMP-ADMINCONFIG-INMEM-PAGE (S, IN-MEM-PAGE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java:96`

`listFeatures()` calls `registry.all()` to materialise every `ConfigDescriptor` in the
JVM-resident SPI registry, converts them all to `ConfigFeatureIO` rows, takes
`rows.size()` as total, then slices with `rows.subList((int)from, ...)`. The full list
is allocated on every request even when the caller asks for a single page.

**Fix:** Add `ConfigFeatureRegistry.count()` and `ConfigFeatureRegistry.list(long skip, int limit)` that slice the backing list internally. In `listFeatures()`, call `count()` for total and `list(page * pageSize, pageSize)` for the slice.

**AC:** `GET /v2/admin/config/features?page=1&pageSize=1` returns exactly 1 item and
`X-Total-Count` equals the full registry size; a registry with 200 features does not
allocate a 200-element list on a page-1 request.

---

## Finding 2 — APISIMP-SEMADMIN-ONTOL-INMEM-PAGE (S, IN-MEM-PAGE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java:289`

`listOntologies()` calls `configService.listMerged(manifest)` which returns every
built-in plus every user-uploaded `OntologyBundle` into a single `ArrayList`, converts
all of them to `OntologyBundleIO`, then `subList`s to the requested page. The full merge
is materialised on every paged request.

**Fix:** Add `OntologyBundleConfigService.countMerged(manifest)` and
`.listMerged(manifest, skip, limit)` overloads. The built-in slice and the DAO slice
can each be fetched with `skip`/`limit`; delegate to the DAO's existing SKIP/LIMIT
support for the user-bundle portion.

**AC:** `GET /v2/admin/semantic/ontologies?page=0&pageSize=5` materialises at most 5
`OntologyBundleIO` objects; `X-Total-Count` reflects the total count without allocating
a list of that size.

---

## Finding 3 — APISIMP-PLUGINS-INMEM-PAGE (S, IN-MEM-PAGE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/plugins/PluginsAdminRest.java:141`

`list()` calls `registry.list()` to get all registered `PluginEntry` objects, maps them
all to `PluginEntryIO` (including an `isEnabled()` call per entry), then computes
`rows.size()` for total and slices with `rows.subList()`. All plugins are fully evaluated
and mapped on every paginated request.

**Fix:** Add `PluginRegistry.count()` and `PluginRegistry.list(long skip, int limit)`.
In `list()`, call `count()` for total, then call `list(page * pageSize, pageSize)` and
map only the slice. This avoids calling `isEnabled()` for all plugins when only a page
is needed.

**AC:** `GET /v2/admin/plugins?page=0&pageSize=10` with 200 registered plugins only
calls `isEnabled()` 10 times; `X-Total-Count` is 200.

---

## Finding 4 — APISIMP-PROV-CURSOR-ENVELOPE (S, INCON-ENVELOPE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:226,369`

Both `listActivities()` (line 226) and `listEntityActivities()` (line 369) use
cursor-based pagination (`since`/`until` epoch-ms) but wrap results in
`PagedResponseIO(rows, rows.size(), 0, rows.size())`. This means:
- `total` always equals the window-result count (never the true DB total)
- `page` is always `0` regardless of which cursor window was fetched
- callers expecting offset-based semantics (`ceil(total/pageSize)` pages) get nonsense

Note: the entity-scoped variants at lines 160 and 312 were addressed by
`APISIMP-PROV-CURSOR-PAGED-WRAP` (PR #2408); these are the global-level variants.

**Fix:** Introduce a `CursorPageIO<T>` record `{ List<T> items; int pageSize; boolean hasMore; Long nextCursor; }` and return it from cursor-mode endpoints. Remove `PagedResponseIO` from these methods; emit `X-Has-More` and `X-Next-Cursor` headers.

**AC:** `GET /v2/provenance/activities?pageSize=100` response body has no `total` field;
`hasMore:true` when more rows exist; `nextCursor` is the epoch-ms of the last row.

---

## Finding 5 — APISIMP-DO-CHAIN-ENVELOPE (S, INCON-ENVELOPE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java:887,931`

`predecessorChain()` (line 887) and `successorChain()` (line 931) both return
`PagedResponseIO(result, result.size(), 0, result.size())`. The `page` parameter is
always `0` and `pageSize` equals `items.length`, so `total` always equals `items.length`.
No pagination is possible and the envelope is structurally misleading.

The chain methods were wrapped in `PagedResponseIO` by `APISIMP-DO-CHAIN-MISSING-PAGE-PARAMS`
(fire-327, PR #2204) for shape consistency, but the resulting envelope actively implies
paginability that doesn't exist.

**Fix:** Return a plain `List<DataObjectSummaryIO>` with a `X-Total-Count` header, OR
a thin `{ items, depth }` envelope. If `PagedResponseIO` is kept for schema tooling
reasons, add an `@Operation` note explicitly stating pagination is not supported.

**AC:** Predecessor/successor chain response shape clearly signals non-paged semantics;
`mvn verify -pl backend` green.

---

## Finding 6 — APISIMP-CONTAINER-BULK-ENVELOPE (S, INCON-ENVELOPE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:831`

The bulk-channel-data endpoint returns `PagedResponseIO(out, out.size(), 0, out.size())`.
The result set is bounded by the request body's `channelAppIds` list — there is no
pagination: `page=0` is hardcoded and `pageSize=out.size()`. The envelope misleads
callers into thinking pagination parameters would select a different page.

**Fix:** Return a plain `BulkChannelDataIO { List<CrossDoSeriesIO> items; }` record or
a simple array. If `PagedResponseIO` must be retained, set `total` to the requested
`channelAppIds` count and document that all requested channels are returned in one shot.

**AC:** `POST .../channels/bulk-data` response body has no misleading `page` or
`pageSize` fields, or carries a clear indicator that all requested channels are
returned in one response.

---

## Finding 7 — APISIMP-SNAPSHOT-LIST-TOTAL (M, INCON-ENVELOPE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotListRest.java:187`

`total` is set to `snapshotService.countAll()` (or `countByCollection()`) — the
unfiltered DB count — but `items` is post-filtered to only the snapshots whose parent
Collection the caller can Read. A caller computing `ceil(total/pageSize)` gets the
wrong page count: e.g. if the DB has 100 snapshots but the caller can only read 20,
the first page may return 8 items, `total=100`, and the caller infers 2 more pages but
those will yield fewer items than `pageSize` (or be empty).

**Fix:** Either (a) compute total as the permission-visible snapshot count — add
`SnapshotService.countVisible(caller, collectionAppId)` that runs the ACL walk in
Cypher; or (b) switch to cursor-based pagination with `X-Has-More` so clients do not
compute page counts from `total`.

**AC:** A caller with Read on 20 of 100 snapshots receives a `total` that, when used in
`ceil(total/pageSize)`, correctly predicts the number of non-empty pages they will
traverse; `mvn verify -pl backend` green.

---

## Finding 8 — APISIMP-DQR-EVAL-INMEM (M, IN-MEM-PAGE)

**File:** `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:205`

`evaluate()` calls `service.evaluate(collectionAppId, caller)` which runs every enabled
DQR against every DataObject in the Collection and returns all results as a
`List<DQRResultIO>` in memory. Only after the full evaluation does the REST layer
truncate with `all.subList(0, maxItems)`. A Collection with 50,000 DataObjects and 5
DQRs produces 250,000 result objects in the JVM before the response is capped at 5,000.

Note: `APISIMP-DQR-EVALUATE-BARE-LIST` (fire-371) added the response cap and
`DQRResultsIO` envelope but left the full-evaluation-then-slice pattern intact.

**Fix:** Add an early-exit parameter to `DQRService.evaluate(collectionAppId, caller, maxItems)`:
stop accumulating results after `maxItems+1` rows to detect truncation without full
evaluation. The `total` field is then the actually-computed count capped at
`maxItems+1`, not the full list size. Document that `total` may be approximate
(`>= maxItems`) when truncated.

**AC:** `evaluate()` on a Collection with 250,000 DQR result rows stops after
materialising `maxItems+1=5001` rows; heap allocation is proportional to `maxItems`,
not to the Collection's full DataObject count.
