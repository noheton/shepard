---
stage: deployed
last-stage-change: 2026-07-11
---

# APISIMP Sweep — fire-545 (2026-07-11)

Triggered by: all named APISIMP/V2CONV rows through fire-544 are shipped.
Last shipped before this sweep: `APISIMP-SNAP-MANIFEST-PAGEPARAM` (PR #2478) and
`APISIMP-PUBLICATION-GONE-PARAMS` (PR #2479, merged fire-545).

## Scope

Scanned: `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` (31 active files) +
plugin REST files. Agent ran full sweep with 65 tool calls over ~9 minutes.

## Summary

The v2 REST surface is largely clean. Three findings remain; two are new rows; one is
already in the backlog as blocked/deferred.

---

## Finding 1 — APISIMP-PROV-CURSOR-PAGECAP (S) — NEW

**Files:** `ProvenanceRest.java` lines 134, 202, 248 (three `/activities` variants) and
301, 347, 382 (three `/entity/{appId}` variants).

**What's wrong:** All six cursor-window parameters still use `@QueryParam("pageSize")
@Max(1000)`. Backlog rows APISIMP-PROV-CURSOR-INCONSISTENT (claimed shipped fire-357)
and APISIMP-ENTITY-PROV-PAGESIZE (claimed shipped fire-359) both state the rename
`pageSize` → `limit` was applied, but the live code contradicts this.

```java
// ProvenanceRest.java:134 (and 5 identical sites)
@QueryParam("pageSize") int pageSize,  // ← should be @QueryParam("limit")
```

**Fix:** Rename `@QueryParam("pageSize")` → `@QueryParam("limit")` on all six methods.
Update `ListActivitiesRequest` / `ListEntityActivitiesRequest` in `ProvenanceApi.ts`.
Add six annotation-presence assertions in `ProvenanceRestParamAnnotationTest`.

**Size:** S (six mechanical replacements in one file + client update)

---

## Finding 2 — APISIMP-SQL-TIMESERIES-PATH (M) — already tracked, deferred

`SqlTimeseriesRest.java` line 74: `@Path("/v2/sql/timeseries")` — single-endpoint
namespace. Already tracked in aidocs/16 at line 4582 as deferred, blocked on
container kind-discriminator surface. No new row filed.

---

## Finding 3 — APISIMP-NAME-ALIAS-RETIRE (XS) — NEW

**Files:**
- `ContainersV2Rest.java:467`
- `CollectionV2Rest.java:176`
- `DataObjectV2Rest.java:212`

**What's wrong:** All three list endpoints still carry `@Deprecated @QueryParam("name")
String nameLegacy` backward-compat aliases added by the fire-510 APISIMP-*-NAME-TO-Q
rows. These were documented as "one release cycle" migration shims; the window has
elapsed. The aliases keep `?name=` alive in generated clients and add noise to the
OpenAPI spec.

**Fix:** Delete all three `nameLegacy` params and their fallback branches. Add one
reflection-based test per file asserting the param is gone.

**Size:** XS

---

## What was NOT found

- `@Parameter` missing on pagination params: 0 remaining across all 31 active paginated
  REST files. The only missing site is `PublicationsListRest` (410 tombstone — now fully
  cleaned, APISIMP-PUBLICATION-GONE-PARAMS shipped fire-545).
- Numeric Neo4j ID leaks in IO response bodies: no new unreported leaks. One known
  `neo4jNodeId` field (`PermissionAuditEntryIO.java:28`) is the existing blocked row
  APISIMP-PERMISSION-AUDIT-NEO4J-ID. One `@JsonIgnore @Schema(hidden=true)` field in
  `TypedPredecessorSummaryIO.java` is not serialized. One Postgres FK in
  `TimeseriesChannelV2IO.java:49` is tracked in-file.
- Bespoke `*ConfigRest` admin classes: 0 remaining (V2CONV-A4-ADMIN-CONFIG-REGISTRY
  fully shipped).
- `@Path(Constants.SHEPARD_API + ...)` in v2 package: 0 violations.

## New rows filed

| Row | Size | Status |
|-----|------|--------|
| APISIMP-PROV-CURSOR-PAGECAP | S | ⏳ queued |
| APISIMP-NAME-ALIAS-RETIRE | XS | ⏳ queued |

## Next sweep trigger

After APISIMP-NAME-ALIAS-RETIRE ships: check the `X-Total-Count` deprecation window.
The header is still emitted on ~12 list endpoints alongside `PagedResponseIO.total`
during the deprecation window. Once client migration is confirmed, file
`APISIMP-XTOTALCOUNT-DEPRECATION-CLOSE` (XS) to silently drop the headers.
