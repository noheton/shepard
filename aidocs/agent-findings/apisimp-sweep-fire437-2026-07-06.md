---
stage: deployed
last-stage-change: 2026-07-06
---
# APISIMP Sweep — fire-437 (2026-07-06)

Scope: full scan of `backend/src/main/java/de/dlr/shepard/v2/` and plugin REST
surfaces for API simplification violations. Focuses on residual in-memory paging,
unbounded queries, bare list returns, and numeric-id wire leaks after fire-435's
`APISIMP-USER-SEARCH-NO-PAGINATION` was shipped in fire-437.

---

## F1 — APISIMP-AAS-SHELL-DO-LOAD-CAP (CRITICAL, Size S)

**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:157–159`

`GET /v2/aas/shells/{aasId}` calls `dataObjectDAO.findTopLevelByCollectionAppId(appId)`
with no LIMIT, loading the **entire** top-level DataObject list for the Collection into
the JVM heap before applying `SHELL_MAX_SUBMODELS = 500` via in-memory `subList(0, 500)`.
A Collection with 50 k DataObjects materialises the full Neo4j result set then discards
99 % of it. The sibling `/shells/{aasId}/submodels` endpoint (line 205) already does
this correctly with a DB-side paged DAO call.

**Fix:** Pass `SHELL_MAX_SUBMODELS + 1` as a hard cap to the DAO so at most 501 rows
are ever hydrated; derive `truncated` from `size > SHELL_MAX_SUBMODELS`.

**AC:** `getShell()` never hydrates more than `SHELL_MAX_SUBMODELS + 1` DataObjects;
`X-Shepard-Truncated` semantics unchanged; `mvn verify -pl backend` green.

---

## F2 — APISIMP-REFANNOT-IN-MEMORY-PAGING (CRITICAL, Size M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java:160–168`
**SPI default:** `backend/src/main/java/de/dlr/shepard/v2/references/spi/ReferenceKindHandler.java:250`

`GET /v2/references/{appId}/annotations` calls `handler.listAnnotations(appId)` which
loads **all** annotations from the store then pages in Java via `rows.subList(from, to)`.
`TimeseriesReferenceKindHandler.listAnnotations()` fetches every `TimeseriesAnnotation`
row for the reference in one unbounded query. A timeseries reference recording anomaly
labels at millisecond granularity can accumulate millions of rows; every page-1 request
forces a full DB scan and full heap materialisation regardless of the requested page.

**Fix:** Replace `ReferenceKindHandler.listAnnotations()` with a paginated SPI method
`listAnnotations(refAppId, skip, limit)` whose default implementation and
`TimeseriesReferenceKindHandler` override push SKIP+LIMIT down to SQL/Cypher.

**AC:** DB delivers only the requested slice; in-memory `subList` removed; `mvn verify
-pl backend` green.

---

## F3 — APISIMP-TS-CONT-ANNOT-IN-MEMORY-PAGING (CRITICAL, Size M)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/handlers/TimeseriesContainerKindHandler.java:518–530` and `:557–568`

Both `listChannelAnnotations` and `listTemporalAnnotations` fetch the full annotation
list with no DB-level bounds then page in Java via `all.subList(from, to)`. A container
capturing telemetry at 1 kHz can accumulate tens of millions of temporal annotations;
every page-1 request forces a full table scan and full heap materialisation.

**Fix:** Replace both service/DAO call sites with paginated variants that accept
`skip`/`limit` and translate them to SQL `OFFSET`/`FETCH`.

**AC:** DB delivers only the requested slice for both endpoints; in-memory `subList`
removed; `mvn verify -pl backend` green.

---

## F4 — APISIMP-COLL-CONTAINERS-BARE-LIST (MAJOR, Size S)

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionContainersRest.java:99–100`

`GET /v2/collections/{collectionAppId}/referenced-containers` executes an unbounded
Cypher walk (`Collection → DataObject → Reference → Container`) via
`CollectionContainersDAO.findByCollectionAppId()` and returns the result as a raw
`List<ContainerSummaryIO>` via `Response.ok(containers)` — no pagination envelope, no
`pageSize` cap, no `X-Total-Count` header. A large collection materialises all results
in one Cypher round-trip and serialises the entire array.

**Fix:** Accept `?page`/`?pageSize` with `@Max(200)` default 50; add `SKIP $skip LIMIT
$limit` + a separate `COUNT` query to the DAO; wrap response in `PagedResponseIO`.

**AC:** `GET /v2/collections/{appId}/referenced-containers?page=0&pageSize=20` returns
`{"items":[…],"total":N,"page":0,"pageSize":20}`; `mvn verify -pl backend` green.

---

## F5 — APISIMP-DQR-LIST-IN-MEMORY-PAGING (MAJOR, Size S)

**File:** `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:98–104`
**Service:** `backend/src/main/java/de/dlr/shepard/v2/quality/services/DataQualityRequirementService.java:53`

`GET /v2/collections/{collectionAppId}/dqr` calls `service.list()` which executes
`dao.findByCollectionAppId()` without any SKIP/LIMIT, streams all DQR nodes into Java,
then pages in-memory via `all.subList(from, to)`. `page`/`pageSize` params are accepted
and validated (`@Max(200)`) but never passed to the DAO. Although per-collection DQR
counts are typically small today, this is the same structural defect as previously-fixed
items and will regress if DQR definitions are imported in bulk.

**Fix:** Add a paginated overload `DataQualityRequirementDAO.findByCollectionAppId(appId,
skip, limit)` that pushes SKIP+LIMIT to Cypher; wire `skip = page * pageSize` and `limit
= pageSize` through the service and REST layer; remove in-memory `subList`.

**AC:** In-memory `subList` removed; `mvn verify -pl backend` green.

---

## F6 — APISIMP-TSCHANNEL-CONTAINER-ID-WIRE (MINOR, Size M, blocked)

**File:** `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/io/TimeseriesChannelV2IO.java:48–49`

`TimeseriesChannelV2IO` exposes `long containerId` — a Postgres serial FK — directly
on the `/v2/` wire. The field carries `@Schema(deprecated=true)` but is not
`@JsonIgnore`d, so it is serialised in every channel listing response. Unlike the
companion `int id` field (tracked as APISIMP-TSCHANNEL-INT-ID-DEPRECATE), `containerId`
leaks the internal Postgres container row PK, violating the "no numeric internal IDs on
the v2 surface" contract. An in-code comment (`APISIMP-TSCHANNEL-CONTAINER-ID`)
acknowledges a migration blocker: adding `container_app_id UUID` to the `timeseries`
table (TS-IDb/c).

**Fix (after TS-IDb/c):** Annotate `containerId` with `@JsonIgnore` (matching the
`predecessorId` field in `TypedPredecessorSummaryIO`) or remove it; replace with
`containerAppId` on the wire.

**AC:** `containerId` not serialised; `containerAppId` (UUID) present; `mvn verify
-pl backend` green. **Blocked on TS-IDb/c migration.**

---

## Summary

| Row | Severity | Size | Status |
|-----|----------|------|--------|
| APISIMP-AAS-SHELL-DO-LOAD-CAP | CRITICAL | S | queued |
| APISIMP-REFANNOT-IN-MEMORY-PAGING | CRITICAL | M | queued |
| APISIMP-TS-CONT-ANNOT-IN-MEMORY-PAGING | CRITICAL | M | queued |
| APISIMP-COLL-CONTAINERS-BARE-LIST | MAJOR | S | queued — **next fire** |
| APISIMP-DQR-LIST-IN-MEMORY-PAGING | MAJOR | S | ✅ shipped fire-439 (branch APISIMP-DQR-LIST-IN-MEMORY-PAGING-1) |
| APISIMP-TSCHANNEL-CONTAINER-ID-WIRE | MINOR | M | blocked (TS-IDb/c) |

**Smallest dispatchable slice next fire:** `APISIMP-COLL-CONTAINERS-BARE-LIST` (MAJOR, S).
Simple REST+DAO fix: add pagination params + `SKIP/LIMIT` to one endpoint.
