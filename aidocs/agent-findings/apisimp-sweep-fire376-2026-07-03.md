---
stage: fragment
last-stage-change: 2026-07-03
author: fire-376-apisimp-sweep-agent
---

# APISIMP Sweep — fire-376 (2026-07-03)

Fresh sweep of v2 REST resource files not already confirmed clean by earlier fires.
Criteria applied: per-kind sprawl (#1), bespoke admin config (#2), numeric-id leaks (#3),
pagination inconsistency (#4), missing ProblemJson (#5), uncapped list/body (#8/#9),
redundant implementation (#10).

Fire-375 ran a sweep but never committed the results. This fire re-runs that sweep and
commits the report and backlog rows.

---

## Findings

### APISIMP-INDEPENDENCE-PROOF-INPUT-CAP

**File:** `backend/src/main/java/de/dlr/shepard/v2/quality/io/IndependenceProofRequestIO.java`
**Criterion:** #9 (unbounded body input)
**Severity:** MAJOR

`POST /v2/quality/independence-proof` accepts two lists of DataObject appIds (`setA`,
`setB`) with no server-side size constraint. The service (`IndependenceProofService`)
runs three expensive Neo4j operations using these lists verbatim:

1. `SHARED_ANCESTORS` Cypher query — `$setA` and `$setB` are passed as parameter lists;
   a 10-hop ancestor walk is launched from every item in both sets simultaneously.
2. `fetchAttributeMaps(setA)` — one full MATCH over all `setA` nodes.
3. `fetchAttributeMaps(setB)` — one full MATCH over all `setB` nodes.

A caller providing 5 000 appIds in each list forces Neo4j to expand 10-hop
`has_successor` paths from every one of those 5 000 start nodes. The in-memory
intersection is then performed after the queries return, amplifying the cost.

No `@NotNull`, `@Size(max=...)`, or early-reject guard exists anywhere in the
IO class or the REST resource entry point.

```java
// IndependenceProofRequestIO.java — current (no size constraint)
private List<String> setA;
private List<String> setB;
```

**Proposed fix:** Add JSR-380 constraints to both fields:

```java
@NotNull
@Size(min = 1, max = 500, message = "setA must contain 1–500 elements")
private List<String> setA;

@NotNull
@Size(min = 1, max = 500, message = "setB must contain 1–500 elements")
private List<String> setB;
```

Add `@Valid` to the `POST` body parameter in the REST resource. Quarkus/RESTEasy
returns RFC 7807 400 automatically on constraint violation.

**Size:** XS

---

### APISIMP-CROSS-TIMELINE-UNCAPPED-COLLECTIONS

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionCrossTimelineRest.java`
**Criterion:** #9 (uncapped multi-value query parameter)
**Severity:** MAJOR

`GET /v2/collections/timeline?collections=` accepts an unbounded list of Collection
appIds (multi-value `@QueryParam`, also split on commas). The handler at lines 138–159:

1. Parses the full list via `parseIds(collectionsRaw)` — no size check.
2. Loops over every appId, calling `entityIdResolver.resolveLong(appId)` (Neo4j lookup)
   and `permissionsService.isAccessTypeAllowedForUser(...)` (another Neo4j lookup) for
   each one.
3. Calls `timelineDAO.aggregateMulti(collectionAppIds)` with the full unbounded list.

```java
// line 120 — no @Size, no @Max
@QueryParam("collections") List<String> collectionsRaw,
```

A caller supplying 1 000 Collection appIds drives 2 000 serial Neo4j queries in the
permission-check loop before the aggregation query fires. The timeline feature is
designed for 2–5 Collections ("MFFD Q1 + Q2 shells on the same axis, or LUMEN test
blocks"). There is no legitimate use case for hundreds of Collections in a single
swimlane response.

**Proposed fix:** After `parseIds(collectionsRaw)` returns, add an early-reject guard:

```java
private static final int MAX_COLLECTIONS = 20;

// in crossTimeline():
List<String> collectionAppIds = parseIds(collectionsRaw);
if (collectionAppIds.isEmpty()) { ... existing 400 ... }
if (collectionAppIds.size() > MAX_COLLECTIONS) {
  return problem(PT_BAD_REQUEST, "Too many collections",
    Response.Status.BAD_REQUEST,
    "At most " + MAX_COLLECTIONS + " collections may be merged in one call; got " +
    collectionAppIds.size());
}
```

Update `@APIResponse(responseCode = "400")` description to mention the cap.
Document `MAX_COLLECTIONS = 20` in `@Operation(description=...)`.

**Size:** XS

---

## Files Scanned — Clean

| File | Status |
|------|--------|
| `v2/quality/resources/IndependenceProofRest.java` | Clean — proper auth (`@Authenticated`), proper `@Valid` on request body (but IO class itself has no `@Size` — see finding above); proper `ProblemJson` 400 on missing-DO |
| `v2/collection/resources/CollectionCrossTimelineRest.java` | Finding above; RFC 7807 error helper present |
| `v2/timeseries/resources/CrossDoBulkDataRest.java` | Clean — `CrossDoBulkDataRequestIO` has `@Size(max = 100)` + `@Positive`; properly bounded |
| `v2/anomaly/resources/AnomalyDetectionRest.java` | Clean — `pageSize` capped with `@Min(1) @Max(200)`; proper ProblemJson; proper auth |
| `v2/sql/resources/SqlTimeseriesRest.java` | Clean — `max-rows` / `max-duration` caps enforced by `SqlTimeseriesConfigService`; operator-configurable ceiling |
| `v2/publish/resources/FlatPublicationsRest.java` | Clean — `@Min(1) @Max(200)` on `pageSize`; proper ProblemJson |
| `v2/labjournal/resources/DmpSnippetV2Rest.java` | Clean — list bounded by project membership (dozens), not user-controlled input |
| `v2/export/rep/RepExportV2Rest.java` | Clean — proper auth, `GET /latest` stubs 404 deliberately |
| `v2/watches/resources/CollectionWatchesRest.java` | Clean — `@Min(1) @Max(200)` on `pageSize` |
| `v2/notifications/resources/NotificationRest.java` | Clean — `@Min(1) @Max(200)` on `pageSize` |
| `v2/dataobject/resources/DataObjectBatchV2Rest.java` | Clean — `MAX_BATCH_SIZE = 500` enforced at line ~160 |
| `v2/instance/InstanceCapabilitiesRest.java` | Clean — bounded by installed plugin count (not user input); already fixed in fire-361 |

---

## Summary Table

| Slug | File | Criterion | Severity | Size |
|------|------|-----------|----------|------|
| APISIMP-INDEPENDENCE-PROOF-INPUT-CAP | `IndependenceProofRequestIO` | #9 unbounded body | MAJOR | XS |
| APISIMP-CROSS-TIMELINE-UNCAPPED-COLLECTIONS | `CollectionCrossTimelineRest` | #9 uncapped query param | MAJOR | XS |
