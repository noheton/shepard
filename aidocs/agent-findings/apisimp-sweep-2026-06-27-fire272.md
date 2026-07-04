---
stage: fragment
last-stage-change: 2026-06-27
audience: [contributor]
---

# APISIMP Sweep — 2026-06-27 (fire-272)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` (88 REST classes) +
`plugins/*/src/**` (20 plugin REST resources). Date: 2026-06-27.

## Category Sweeps — No New Findings

**Category A (Per-kind list endpoints not unified under `?kind=`):** Clean.
All per-kind reference list endpoints retired as HTTP 410 Gone.
`ReferencesV2Rest` and `ContainersV2Rest` handle all list operations via `?kind=`.

**Category B (Bespoke `*ConfigRest` not on generic `/v2/admin/config/{feature}`):** Clean.
`ThermographyConfigDescriptor`, `TimeseriesQualityScoringConfigDescriptor`, and all other
features delegate via `AdminConfigRest`. `JupyterConfigPublicRest` at `/v2/jupyter/config`
is intentional (public read; auth concern only). Minter admin resources (`DataciteAdminRest`,
`EpicAdminRest`) expose credential-rotation sister endpoints, not duplicate config GET/PATCH.

**Category C (Numeric Neo4j IDs leaking into the wire):** Clean in v2 and non-spatiotemporal
plugins. `ContainersV2Rest:724-726` `Long start/end` are nanosecond epoch timestamps.
`ProvenanceRest` `Long since/until` are epoch milliseconds. All IO `Long` fields are
metrics/timestamps, not node IDs. `PermissionAuditEntryIO.neo4jNodeId` already tracked
as APISIMP-PERMISSION-AUDIT-NEO4J-ID.

**Category F (Endpoints superseded by `POST /v2/shapes/render`):** Clean.
`ShapesRenderRest` is the canonical render endpoint.

**Category G (Forbidden `@Path(Constants.SHEPARD_API + ...)` in v2):** Clean.
Zero v2 core resources use `Constants.SHEPARD_API`. Only known residual: spatiotemporal
plugin (`SpatialDataPointRest.java:51` + `SpatialDataReferenceRest.java:40`) — frozen
upstream-compat surface, already tracked APISIMP-V1-PATH-RESIDUAL-1 + APISIMP-NUMERIC-ID-BATCH-2.

**Category H (Response verbosity — Long id fields no caller reads):** Clean.
No v2 IO class exposes a bare Neo4j node id as `Long id`. All `Long` fields are
legitimate metrics or epoch values.

## New Findings

### Finding 1 — APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY

**Category:** D — Pagination inconsistency (missed endpoint from envelope migration)
**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/CollectionSnapshotRest.java:196-202`

`CollectionSnapshotRest.list()` (`GET /v2/collections/{collectionAppId}/snapshots`)
accepts `page` and `pageSize` params but returns a plain `List<SnapshotIO>` array:

```java
List<SnapshotIO> rows = snapshotService.listByCollection(collectionAppId, safePage, safeSize)...
return Response.ok(rows).build();  // line 202 — no envelope, no X-Total-Count
```

No `PagedResponseIO` envelope, no `X-Total-Count` header, no `total` in the body.
Its sibling `SnapshotListRest` at `GET /v2/snapshots` returns the conformant
`SnapshotListPageIO { items, total, page, pageSize }` shape. APISIMP-PAGINATION-ENVELOPE
reported "fully shipped" (PR #2102-2105) but `CollectionSnapshotRest.list()` was missed —
it remained shape-C (plain array, no total) throughout the entire envelope migration wave.

**Fix:** Change `snapshotService.listByCollection(collectionAppId, safePage, safeSize)` to
also retrieve `snapshotService.countByCollection(collectionAppId)` for the total; wrap in
`new PagedResponseIO<>(rows, total, safePage, safeSize)`; add `.header("X-Total-Count", total)`.
AC: `GET /v2/collections/{appId}/snapshots?page=0&pageSize=10` returns `{items, total, page,
pageSize}` envelope; `X-Total-Count` header present; 1 regression test confirming envelope
fields; `mvn verify -pl backend` green.

**Size:** XS
**Caller impact:** Any FE caller reading `.length` off the plain array response must migrate
to `.items` + `.total`. The `SnapshotsPane.vue` component (or its composable) is the likely
caller; needs audit.
**Filed as:** `APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY`

---

### Finding 2 — APISIMP-SEARCH-BAD-REQUEST-PLAIN-STRING

**Category:** E — Error envelope inconsistency
**File:** `backend/src/main/java/de/dlr/shepard/v2/search/resources/SearchV2Rest.java:107`

When the required query param `q` is absent or blank, `SearchV2Rest` returns a
plain `text/plain` string body:

```java
if (q == null || q.isBlank()) {
  return Response.status(Response.Status.BAD_REQUEST).entity("Query parameter 'q' is required.").build();
}
```

This is distinct from the in-flight APISIMP-SEARCH-PAGESIZE-HEADER (fire-271), which
covers `pageSize` validation and `X-Total-Count` — the `q`-missing 400 path is not
addressed there. Every other 4xx in v2 resources uses `problem()` producing RFC 7807
`application/problem+json`. A client that inspects `Content-Type` or tries to JSON-parse
a 400 from this endpoint will receive `text/plain` text instead of a machine-readable
problem document.

**Fix:** Replace with the `problem()` helper pattern:
```java
return problem(PROBLEM_TYPE_BAD_REQUEST, "Bad Request",
    Response.Status.BAD_REQUEST,
    "Query parameter 'q' is required and may not be blank.");
```
Add `PROBLEM_TYPE_BAD_REQUEST = "/problems/search.q-required"` constant;
add 1 regression test (`search_missingQ_returnsProblemJson` checks
`Content-Type: application/problem+json` and `status == 400`).
AC: `GET /v2/search` with no `?q=` returns 400 with `Content-Type: application/problem+json`;
`mvn verify -pl backend` green. Can land in the same PR as APISIMP-SEARCH-PAGESIZE-HEADER.

**Size:** XS
**Caller impact:** Low — clients hitting this path receive a usable 400 either way. The
fix is a correctness/consistency improvement: generic error interceptors that key on
`Content-Type: application/problem+json` will now correctly identify this as a structured error.
**Filed as:** `APISIMP-SEARCH-BAD-REQUEST-PLAIN-STRING`

## Summary

Swept 88 v2 REST classes (core + 20 plugin) across all 8 APISIMP categories.
The surface is stable; 162+ prior APISIMP findings have been resolved.

Found 2 new actionable findings, both XS:
- **APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY** — `CollectionSnapshotRest.list()` missed
  during the envelope migration wave; returns plain array with no total/envelope.
- **APISIMP-SEARCH-BAD-REQUEST-PLAIN-STRING** — `SearchV2Rest` returns `text/plain` string
  on 400 (missing `q`); all other v2 4xx responses use `application/problem+json`.

Both can be resolved alongside the in-flight APISIMP-SEARCH-PAGESIZE-HEADER (fire-271)
in a single micro-PR to complete the error-envelope and pagination-envelope waves.
