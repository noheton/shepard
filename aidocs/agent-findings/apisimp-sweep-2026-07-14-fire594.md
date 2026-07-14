---
stage: fragment
last-stage-change: 2026-07-14
---

# API-Simplification Sweep — fire-594 (2026-07-14)

Sweep triggered: no named APISIMP/V2CONV rows were dispatchable (all
existing rows are either ✅ complete, blocked on L2e/TS-ID migration,
or decision-gate rows). Fresh scan of the `/v2/` REST surface and MCP
tool layer.

## Stale rows confirmed done in code (not yet marked ✅)

Verified against `main` before writing new rows. PRs were operator-merged
without updating the backlog entry status.

| Row | Verified location | Evidence |
|-----|-------------------|----------|
| APISIMP-CONTAINER-VERSIONS-FAKE-PAGED | `ContainersV2Rest.java:525` | Returns plain `List<PayloadVersionIO>` via `Response.ok(versionsOpt.get()).build()` — no `PagedResponseIO` wrapper |
| APISIMP-CONTAINER-LINKED-DO-FAKE-PAGED | `ContainersV2Rest.java:596–598` | `@QueryParam("page")` + `@QueryParam("pageSize")` wired; in-memory slice present |
| APISIMP-DO-TIME-BOUNDS-NS-TO-ISO | `DataObjectListItemV2IO.java:83,94` | `private String timeBoundsStart` / `timeBoundsEnd` — ISO strings |
| APISIMP-ACTIVITY-EPOCH-MS-TO-ISO | `ActivityIO.java:42,45` | `private String startedAt` / `endedAt` — ISO strings |
| APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP | `ProvenanceRest.java` | No `legacyEntityId` param or tombstone rejection branch found |
| APISIMP-PROVENANCE-LIMIT-TO-PAGESIZE | `ProvenanceRest.java:138,210,260,317,367` | `@QueryParam("pageSize")` throughout; `?limit=` tombstone returns 400 with rename hint |
| APISIMP-SHAPES-PREDICATES-PROBLEM-JSON | `ShapesPredicatesRest.java:121–122` | `ProblemResponse.problem(BAD_REQUEST, "Unknown substrate…")` — RFC 7807 shape |

## New findings (8 rows)

### F1 — APISIMP-SNAPSHOT-LIST-SIZE-DOC (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/CollectionSnapshotRest.java:168–169`

```java
// current (wrong):
"Pagination: omit `page` / `size` to get the first 50; supply both " +
"to paginate. `size` capped at 200 server-side.\n\n" +

// actual @QueryParam at line 191:
@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
```

The `@Operation` description says `size` but the actual query-parameter name
is `pageSize`. Pure documentation inconsistency — no runtime behaviour change
needed, just fix the two string literals.

---

### F2 — APISIMP-MCP-PROVENANCE-EPOCH-MS (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/mcp/ProvenanceMcpTools.java:169–170`

`query_activity_log` MCP tool declares `@ToolArg Long sinceMillis` and
`@ToolArg Long untilMillis`. The rest of the v2 MCP surface uses ISO 8601
strings for time arguments (e.g. `get_timeseries_data` after APISIMP-MCP-TS-NANOS-ISO).
LLM callers must know the epoch is milliseconds; ISO strings are self-describing
and consistent with the REST wire format.

Fix: rename args to `since`/`until`, change type to `String`, parse via
`Instant.parse(since).toEpochMilli()` at the call site. Update lines 134, 143–144
`@Operation` description and lines 186–187 response echo dict.

---

### F3 — APISIMP-LJE-NUMERIC-PARENT-ID (S)

**Files:**
- `LabJournalEntryRest.java:94` — `permissionsService.isAccessTypeAllowedForUser(dataObject.getId(), …)`
- `LabJournalEntryRest.java:135` — same pattern
- `LabJournalEntryRest.java:171` — same pattern
- `LabJournalHistoryRest.java:128` — same pattern
- `LabJournalRenderRest.java:117` — same pattern

Five call sites pass the Neo4j numeric `dataObject.getId()` to
`isAccessTypeAllowedForUser()` instead of the appId-keyed
`isAccessAllowedForDataObjectAppId(dataObject.getAppId(), …)`. The
numeric-id permission check is the v1 pattern; the v2 permission method
keyed on `appId` is the correct seam.

Fix: replace all five call sites with `isAccessAllowedForDataObjectAppId(dataObject.getAppId(), accessType, caller)`.

---

### F4 — APISIMP-MCP-TS-NANOS-ISO (S)

**Files:**
- `TimeseriesMcpTools.java:144–145` — `@ToolArg Long startNanos` / `Long endNanos`
- `TimeseriesMcpTools.java:345–346` — second pair for the update tool
- `ReferencesMcpTools.java:133–134` — `@ToolArg Long start` / `Long end` for timeseries window
- `ReferencesMcpTools.java:183–184` — second pair for update

Four MCP tool definitions expose nanosecond `Long` arguments for timeseries
window bounds. `TimeseriesMcpTools.java:339` doc even says "Divide by 1_000_000
for ms" — a clear signal that callers have to know the unit. Line 192–193 echoes
`startNanos`/`endNanos` back in the response dict.

Fix: change all eight arg types to `String` ISO 8601; convert at the MCP
boundary via `Instant.parse(s).toEpochSecond() * 1_000_000_000L + nano` or
equivalent. Remove the divide-by-million note from the docstring.

---

### F5 — APISIMP-COLLECTION-PERMISSIONS-PUT-VS-PATCH (M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionPermissionsRest.java:104`

`PUT /v2/collections/{appId}/permissions` uses `@PUT` while container
permissions (`PATCH /v2/containers/{appId}/permissions`) use `@PATCH` with
RFC 7396 merge-patch semantics. PUT requires the full resource state to be
sent; PATCH allows partial updates. Both endpoints in practice replace the
full permissions object, so the semantic difference is small — but the verb
inconsistency breaks OpenAPI client ergonomics and violates the RFC 7396
convention established for all other mutable config endpoints.

Fix: change `@PUT` to `@PATCH`, add `@Consumes("application/merge-patch+json")`,
update `@Operation` description.

---

### F6 — APISIMP-PROVENANCE-TRIPLED-HANDLER (M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java`

Three endpoint groups (`/activities`, `/entities`, `/relations`) each have three
`@GET` overloads differentiated only by `@Produces` annotation (`application/json`,
`application/activity+json`, `application/ld+json`). This yields 9 near-identical
method bodies with duplicated auth, filter validation, service calls, and response
assembly. Adding a new field or fixing a bug requires touching 9 methods instead of 3.

Fix: extract one private `buildActivitiesResponse(...)` / `buildEntitiesResponse(...)` /
`buildRelationsResponse(...)` helper per group; the three `@Produces` overloads each
call the helper and return. Content negotiation is still handled by JAX-RS via the
annotation — no wire change.

---

### F7 — APISIMP-LINKED-DO-IN-MEMORY-PAGE (M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java` (around `getLinkedDataObjects`)

`getLinkedDataObjects` (wired by APISIMP-CONTAINER-LINKED-DO-FAKE-PAGED) fetches
ALL linked DataObjects from the DAO then slices with `subList(fromIdx, toIdx)` in
memory. At MFFD scale (a single CFRP container may link to hundreds of process-step
DataObjects) this materialises the full list on every page request.

Fix: Add `int skip, int limit` parameters to the DAO-layer `listLinkedDataObjects`
method and issue a `SKIP $skip LIMIT $limit` Cypher clause; remove the in-memory
`subList` from the REST handler.

---

### F8 — APISIMP-SEARCH-IN-MEMORY-MERGE-PAGE (L)

**File:** `backend/src/main/java/de/dlr/shepard/v2/search/resources/SearchV2Rest.java:139–182`

`SearchV2Rest` fetches ALL matching Collections (via `searchService.searchCollections(…)`)
and ALL matching DataObjects (via `searchService.searchDataObjects(…)`) into JVM heap,
merges the two lists, then slices a page with `subList`. At scale — a collection with
10 000 DataObjects and a broad keyword — this materialises tens of thousands of entities
per request and stresses the JVM heap.

Fix: either (a) add a server-side result cap (e.g. max 1 000 combined results per call,
document it), or (b) push `skip`/`limit` into both service methods so the DAO issues
`SKIP`/`LIMIT` at query time. Option (a) is XS; option (b) is the correct long-term shape
but requires DAO changes.

---

## Dispatch decision

**Dispatched this fire:** APISIMP-SNAPSHOT-LIST-SIZE-DOC (F1, XS) — 2-line
description-string fix in `CollectionSnapshotRest.java`. No runtime change.
Branch: `APISIMP-SNAPSHOT-LIST-SIZE-DOC-fire594`.

Remaining 7 rows filed in `aidocs/16` for future fires.
