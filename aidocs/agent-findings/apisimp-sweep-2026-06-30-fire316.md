---
stage: deployed
last-stage-change: 2026-06-30
---

# API Simplification Sweep — fire-316 (2026-06-30)

## Context

All prior APISIMP rows are done, deferred, or blocked. The fire-313 sweep
(`aidocs/agent-findings/apisimp-sweep-2026-06-30.md`) filed two rows:
`APISIMP-ANNOTATION-MERGE-PATCH-HTTP` (shipped fire-315, PR #2193) and
`APISIMP-PAGESZ-VALIDATION-INCONSISTENCY` (shipped fire-314, PR #2192). The
current queue is empty except for `APISIMP-PERMISSION-AUDIT-NEO4J-ID`
(blocked on L2 migration clean confirmation). This fire-316 sweep ran a
fresh full-surface scan before deciding the next slice.

Prior sweep: `aidocs/agent-findings/apisimp-sweep-2026-06-30.md`
(filed 2 rows, both now shipped).

## Sweep scope

- `backend/src/main/java/de/dlr/shepard/v2/` — all v2 REST resource classes
- `plugins/*/src/main/java/` — all plugin v2 resources
- Focus: bespoke response envelopes not using `PagedResponseIO`, fake/missing
  pagination, unbounded list responses, stale OpenAPI spec text, tombstone
  endpoints still on classpath, redundant sub-resources, numeric param leaks,
  timeseries 5-tuple surface smell

## Findings

### Finding 1 — `SnapshotListRest`: bespoke `SnapshotListPageIO` record instead of `PagedResponseIO`

**File**: `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotListRest.java:99-104,184`

`SnapshotListRest` declares its own inner `SnapshotListPageIO` record:
```java
public record SnapshotListPageIO(
  List<SnapshotListItemIO> items,
  long total,
  int page,
  int pageSize
) {}
```
and returns `new SnapshotListPageIO(filtered, total, safePage, safeSize)`.
The field layout is identical to `PagedResponseIO<SnapshotListItemIO>` but it
is a different Java type — the OpenAPI generator emits a distinct
`SnapshotListPageIO` schema rather than the standard `PagedResponseIO` envelope.
`CollectionSnapshotRest` (the sibling per-collection snapshot list) correctly
uses `PagedResponseIO<SnapshotListItemIO>`. This is the last bespoke list
envelope on the v2 surface (the fire-313 sweep confirmed all other list
endpoints are on `PagedResponseIO`).

**New backlog row**: `APISIMP-SNAPSHOT-LIST-ENVELOPE` (XS, queued).

Fix: import and return `PagedResponseIO<SnapshotListItemIO>`; delete the
`SnapshotListPageIO` record; update `@APIResponse` schema reference.

---

### Finding 2 — `ReferencesV2Rest.listReferences()`: fake `PagedResponseIO` wrapping an in-memory full load

**File**: `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java`

`GET /v2/references?kind=...&dataObjectAppId=...` loads the entire reference
list for a DataObject into memory and wraps it in a `PagedResponseIO` with
`total=refs.size(), page=0, pageSize=refs.size()`. No `page` or `pageSize`
`@QueryParam` parameters are accepted. The endpoint *looks* paginated to
consumers (the envelope is present) but performs no actual cursor/offset
pagination. A DataObject with 1,000 references would load all 1,000 into a
single response without any way to page.

**New backlog row**: `APISIMP-REFS-FAKE-PAGINATION` (M, queued).

Fix: add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page`
and `@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize`
to the method; thread them to a service-layer or DAO call that fetches only the
requested slice; pass the unfiltered count to `PagedResponseIO.total`.

---

### Finding 3 — `DataObjectV2Rest`: predecessors/successors/children endpoints return unbounded bare `List<>`

**File**: `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java`

`GET /v2/data-objects/{appId}/predecessors`, `…/successors`, and `…/children`
return bare `List<DataObjectSummaryIO>` responses with no `PagedResponseIO`
envelope and no pagination parameters. For a DataObject with a deep lineage
chain (LUMEN TR-004 investigation sub-tree or the MFFD AFP → NDT → Rework DAG)
the service traverses the entire OGM lazy relationship set and returns it all in
one response body. Contrast with every other v2 list endpoint which envelops
results in `PagedResponseIO`.

**New backlog row**: `APISIMP-DO-RELATIONSHIP-LISTS-UNBOUND` (M, queued).

Fix: add `page`/`pageSize` params; return `PagedResponseIO<DataObjectSummaryIO>`
with `total` from a count query; add service/DAO slice methods. Note: the design
choice of whether to page graph traversals (vs. bounded depth limit) deserves a
brief ADR.

---

### Finding 4 — `DataObjectV2Rest.getDataObject()`: stale OpenAPI description mentions suppressed numeric fields

**File**: `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java`

The `@Operation(description=...)` for `GET /v2/data-objects/{appId}` references
`referenceIds[]` and `childrenIds[]` as if they appear in the JSON response body.
`DataObjectDetailV2IO` suppresses these fields via `@JsonIgnoreProperties` — they
are never emitted. The stale description is misleading: a consumer reading the
OpenAPI spec will expect fields that never arrive.

**New backlog row**: `APISIMP-DO-V2-STALE-SPEC-TEXT` (S, queued).

Fix: remove or correct the description passage that mentions `referenceIds[]` and
`childrenIds[]`; verify no other operation descriptions reference suppressed fields.

---

### Finding 5 — `FileReferenceV2Rest`: tombstone class (all endpoints 410 Gone) still on classpath

**File**: `backend/src/main/java/de/dlr/shepard/v2/file/resources/FileReferenceV2Rest.java`

`FileReferenceV2Rest` is a "retired" class where every endpoint unconditionally
returns HTTP 410 Gone. It is still compiled, loaded, and included in the OpenAPI
document, inflating the API surface with defunct endpoints that will never return
200. The per-kind file reference path has been superseded by the generic
`GET /v2/references/{appId}` surface. There is no benefit to keeping the class
on the classpath; it adds noise to the OpenAPI spec and risks confusing consumers
who see endpoints listed that always fail.

**New backlog row**: `APISIMP-FILEREF-V2-TOMBSTONE-DELETE` (XS, queued).

Fix: delete `FileReferenceV2Rest.java` and its test counterpart; verify no
other class imports or references it; run `mvn verify -pl backend`.

---

### Finding 6 — `ContainersV2Rest.listChannels()`: `pageSize` has no `@Max` cap

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:640`

`GET /v2/containers/{appId}/channels` (the timeseries channel list) accepts
`@QueryParam("pageSize") @DefaultValue("200") @PositiveOrZero int pageSize`.
The `@PositiveOrZero` allows arbitrarily large `pageSize` values (e.g. `pageSize=100000`)
with no server-side cap. The main container list at line 638 uses `@Max(200)`.
Note: this endpoint was explicitly called out in the fire-313 sweep as
"acceptable as-is given its different semantics" (default 200 = "all channels
at once"). However the lack of any upper cap is still an API hygiene issue —
a caller can request millions of rows.

**New backlog row**: `APISIMP-CHANNEL-PAGESZ-MAX` (XS, queued).

Fix: add `@Max(500)` (not the standard 200, since channel lists legitimately
want "all channels" which can exceed 200 for dense timeseries containers).
Document the higher cap in `@Parameter(description=...)`.

---

### Finding 7 — All other v2 surface

Full scan confirms:

- **No forbidden `@Path(Constants.SHEPARD_API...)`** in v2 namespace — confirmed
  zero occurrences across all v2 resource classes.
- **No new numeric Neo4j IDs in paths/params** — all `@PathParam`/`@QueryParam`
  use String `appId` (spatial plugin frozen upstream-compat still deferred per
  APISIMP-NUMERIC-ID-BATCH-2).
- **`PagedResponseIO` near-universal** — Finding 1 (`SnapshotListRest`) is the
  last bespoke envelope on the v2 surface. Three intentional exceptions remain:
  `ShapesPredicatesRest` (bounded config/migration data), `AdminConfigRest`
  config list (bounded feature set), `PublicationsListRest` (documented
  pagination-intentionally-absent design).
- **`ReferencesV2Rest` fake pagination** — Finding 2 (see above).
- **RFC 7807 error shapes** — consistent across all v2 4xx/5xx responses.
- **HTTP methods** — all correct after fire-315 `@PATCH` fix on `SemanticAnnotationV2Rest`.
- **`FileReferenceV2Rest` tombstone** — Finding 5 (see above).
- **`FileBundleReferenceRest` root GET** — pre-existing finding noted in prior
  sweep; the root `GET /v2/bundles/{bundleAppId}` duplicates generic references
  endpoint but is not new; deferred as low-priority cleanup.
- **`ContainersV2Rest` 5-tuple live-window** — endpoint still accepts both
  `shepardId` and all 5 timeseries tuple params; this is an intentional
  migration-window dual-accept, not a new finding.

## Summary

| Finding | Severity | Action |
|---|---|---|
| 1 — `SnapshotListRest` bespoke `SnapshotListPageIO` envelope | XS | **New row** `APISIMP-SNAPSHOT-LIST-ENVELOPE` (XS, queued) |
| 2 — `ReferencesV2Rest` fake pagination (full in-memory load) | M | **New row** `APISIMP-REFS-FAKE-PAGINATION` (M, queued) |
| 3 — `DataObjectV2Rest` predecessors/successors/children unbounded bare List<> | M | **New row** `APISIMP-DO-RELATIONSHIP-LISTS-UNBOUND` (M, queued) |
| 4 — `DataObjectV2Rest.getDataObject()` stale spec text mentioning suppressed fields | S | **New row** `APISIMP-DO-V2-STALE-SPEC-TEXT` (S, queued) |
| 5 — `FileReferenceV2Rest` tombstone class still on classpath | XS | **New row** `APISIMP-FILEREF-V2-TOMBSTONE-DELETE` (XS, queued) |
| 6 — `ContainersV2Rest.listChannels()` no `@Max` on `pageSize` | XS | **New row** `APISIMP-CHANNEL-PAGESZ-MAX` (XS, queued) |
| 7 — All other v2 surface | n/a | Clean: no new numeric IDs, no new v1 paths, RFC 7807 consistent |

**Six new backlog rows filed.** Smallest dispatchable slice next fire:
`APISIMP-SNAPSHOT-LIST-ENVELOPE` (XS — one file edit, delete inner record,
swap return type, update `@APIResponse` schema annotation, one test update).
