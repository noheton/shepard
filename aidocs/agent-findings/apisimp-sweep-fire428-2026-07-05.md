---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP Sweep — fire-428 (2026-07-05)

Post-merge sweep after PR #2313 closed the last named APISIMP row
(`APISIMP-VOCABULARY-BROWSE-IN-MEMORY-PAGING` — predicate-list SKIP/LIMIT fix).
No further dispatchable named rows existed; per pipeline STEP 2e a fresh sweep
was run across all `/v2/` REST resources and plugin code.

Scope: `backend/src/main/java/de/dlr/shepard/v2/` (all REST resources + services),
`plugins/*/src/main/java/` (plugin REST + services), and the
`backend/src/main/java/de/dlr/shepard/context/` service layer. Searched for:

- Residual `subList`-based in-memory pagination behind a `?page=`/`?pageSize=` param
- Unbounded `findAll()` / `findAll*()` calls powering a paginated response
- Numeric-id leaks on v2 endpoints
- Inconsistent error shapes

## §F1 — APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING (new, actionable)

**File:** `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/services/UnhideFeedService.java:122–153`

`buildFeed()` (`GET /v2/unhide/feed.jsonld`, paged JSON-LD feed for the
Helmholtz Unhide harvester) calls `collectionDAO.findAll()` at line 122 —
loading **all** `Collection` entities from Neo4j into memory regardless of
the requested page window — then applies three Java-level transforms before
slicing with `subList(from, to)`:

```java
// line 122
List<Collection> all = new ArrayList<>(collectionDAO.findAll());
all.removeIf(c -> c == null || c.isDeleted());          // filter deleted
all.removeIf(c -> {                                       // filter KG opt-out
    var props = collectionPropertiesDAO.findByCollectionAppId(c.getAppId());
    return props.isPresent() && !props.get().isPublishToHelmholtzKG();
});
all.sort((a, b) -> { /* createdAt ASC, appId ASC */ }); // sort
int total = all.size();
int from = Math.min(clampedPage * clampedSize, total);
int to = Math.min(from + clampedSize, total);
List<Collection> window = all.subList(from, to);        // line 153
```

**Severity:** Medium-high. The Unhide harvester pages through the entire
feed on every harvesting cycle. As the number of Collections grows, each
page request loads the full Collection graph — the same O(N) anti-pattern
fixed for `CollectionV2Rest` in fire-421 (PR #2307). The per-Collection
`collectionPropertiesDAO.findByCollectionAppId()` call inside `removeIf`
compounds this: N+1 queries for the filter pass.

**Proposed fix:**

1. Add a new Cypher query to `CollectionDAO` (or the plugin's own DAO if it
   maintains a separate one) that pushes the filter + sort + pagination into
   the DB:

   ```cypher
   MATCH (c:Collection)
   WHERE NOT c.deleted = true
   OPTIONAL MATCH (c)-[:HAS_PROPERTIES]->(p:CollectionProperties)
   WITH c, p
   WHERE p IS NULL OR p.publishToHelmholtzKG <> false
   ORDER BY c.createdAt ASC, c.appId ASC
   SKIP $skip LIMIT $limit
   RETURN c, p
   ```

2. Add a matching `countForUnhideFeed()` companion query (WHERE + COUNT, no
   SKIP/LIMIT) to drive `totalEntries` in the feed metadata.

3. Update `UnhideFeedService.buildFeed()` to call the bounded pair instead of
   `collectionDAO.findAll()` + Java filter + `subList`.

**Acceptance criteria:** (a) `GET /v2/unhide/feed.jsonld?page=0&pageSize=10`
issues one bounded Cypher query (not `MATCH (c:Collection) RETURN c`); (b)
`totalEntries` in the feed metadata reflects the filtered count; (c) `mvn verify
-pl backend` green; (d) no wire-shape change on the feed endpoint.

**Size:** M (Cypher JOIN on `CollectionProperties` via `OPTIONAL MATCH` +
`ORDER BY` + `SKIP/LIMIT` + companion COUNT query).

**Row filed:** `APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING` in `aidocs/16`.

---

## Sites NOT filed (and why)

| Site | Reason not filed |
|---|---|
| `ReferenceAnnotationRest.java:164` — `subList` in `listAnnotations()` | Already resolved: `APISIMP-REF-ANNOTATION-LIST-NO-PAGINATION` (fire-362) added `?page=`/`?pageSize=` with bounded `subList`; the slice is now properly guarded. |
| `TimeseriesContainerKindHandler.java:526,564` — in-memory list slice | Intentional SPI default fallback in `ContainerKindHandler` default methods. Handler-specific bounded overrides exist for DB-backed kinds; the SPI default is the documented fallback for handlers that do not implement bounded queries (see `ContainerKindHandler.java:174` comment). |
| `ReferenceKindHandler.java:156` — in-memory list slice | Same SPI default fallback pattern as above. Documented at `ReferenceKindHandler.java:156`; DB-aware handlers override both `countByDataObject` and bounded `listByDataObject(skip,limit)`. |
| `SnapshotPinnedReadRest.java:151–154` — `subList` on `listDataObjectAppIds` | Already fixed: `APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED` (fire-372, PR #2247). The `@DefaultValue("500") @Max(2000)` bounded params are in place; the `subList` is now properly bounded by the validated `pageSize`. |
| `CollectionDQRRest.java:102,208` | Flagged as `APISIMP-DQR-ORPHAN` (decision row — zero frontend callers; operator must decide ship vs. decommission). No new paging issue beyond the existing orphan status. |
| `ImportDiagnosticsV2Rest.java:152` | Diagnostics list is bounded at the service layer (hard cap on diagnostic rows per import job); the `subList` slice is a secondary guard, not the primary pagination path. Low urgency; filing deferred. |
| `ContentMcpTools.java:215` / `SearchMcpTools.java:240` | MCP tool layer applies its own item cap (`MAX_ITEMS = 20`) before any list result reaches the tool output. Not a REST pagination issue; deferred. |
| `SnapshotDiffRest.java:207,211,215` | Diff payload is bounded by the snapshot manifest size (itself bounded since fire-422 PR #2308). The diff result set is a derivative of two bounded manifests; in-memory intersection is acceptable at current snapshot sizes. |

---

## Summary

| Finding | Status |
|---|---|
| F1 APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING | Filed — dispatch next fire (size M) |
| All fire-422 F2–F5 in-memory paging sites | ✅ fully dispatched (merged fires 424–428) |
| All other inspected sites | Not filed — either already fixed, SPI default, or operator-decision-needed |
