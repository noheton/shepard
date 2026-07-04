---
stage: fragment
date: 2026-07-03
author: claude-sonnet-4-6
fire: fire-379
---

# APISIMP sweep — fire-379 (2026-07-03)

Sweep of all `/v2` REST resources and plugin REST resources for residual API-sprawl
findings. Prior sweeps (fire-373 through fire-378) covered list-envelope inconsistency,
uncapped batch inputs, and uncapped list parameters. This sweep focuses on annotation
list endpoints that were missed in prior passes.

## Surfaces scanned

| File | Status |
|------|--------|
| `v2/references/resources/ReferenceAnnotationRest.java` | **FINDING §MINOR-1** |
| `v2/containers/resources/ContainersV2Rest.java` (channel annotations) | **FINDING §MINOR-2** |
| `v2/containers/resources/ContainersV2Rest.java` (temporal annotations) | **FINDING §MINOR-3** |
| `v2/notifications/resources/NotificationRest.java` | CLEAN — `@Max(200)` on `pageSize` |
| `v2/project/resources/ProjectsRest.java` | CLEAN — `@Max(200)` on `pageSize` |
| `v2/labjournal/resources/CollectionLabJournalEntriesRest.java` | CLEAN — `@Max(200)` on `pageSize` |
| `v2/snapshot/resources/SnapshotListRest.java` | CLEAN — `Math.min(pageSize, 200)` clamp |
| `v2/provenance/resources/ProvenanceRest.java` | CLEAN — time-cursor pagination, intentional 1000 cap |
| `v2/publication/resources/PublicationsListRest.java` | CLEAN — deprecated low-cardinality list, documented |
| `v2/events/resources/CollectionEventsRest.java` | CLEAN — SSE stream, no list endpoint |
| `v2/admin/resources/OntologyGitSourceRest.java` | CLEAN — `@Max(200)` present |
| `plugins/video/.../VideoStreamReferenceV2Rest.java` | CLEAN — 410 tombstone only |
| `plugins/git/.../GitReferenceRest.java` | CLEAN — 410 tombstone only |
| `plugins/unhide/.../UnhideAdminRest.java` | CLEAN — key-mint only, no list |
| `plugins/wiki-writer/.../WikiWriterRest.java` | CLEAN — single POST |
| `plugins/aas/.../AasAdminRest.java` | CLEAN — single POST |

## Findings

### §MINOR-1 — APISIMP-REF-ANNOTATION-LIST-UNCAPPED

**File**: `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferenceAnnotationRest.java:149`

`ReferenceAnnotationRest.list()` (`GET /v2/references/{appId}/annotations`) calls
`r.handler().listAnnotations(appId)` and wraps all rows in a fake-page
`PagedResponseIO(rows, rows.size(), 0, rows.size())` — no `@QueryParam("page")` /
`@QueryParam("pageSize")` params exist and no cap is applied.

A reference with thousands of annotations (e.g. auto-annotated MFFD/AFP timeseries
channels with per-timestamp labels) dumps the full unbounded list in a single response.

**Fix**: add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page` and
`@QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(1000) int limit` to `list()`;
compute `int from = Math.min(page * limit, rows.size()); int to = Math.min(from + limit, rows.size());`;
return `new PagedResponseIO<>(rows.subList(from, to), rows.size(), page, limit)` +
`.header("X-Total-Count", rows.size())`.

**Note**: The HEAD branch already has pagination params (`@QueryParam("page") @DefaultValue("0") @Min(0) int page`
and `@QueryParam("limit") @DefaultValue("200") @Min(1) @Max(1000) int limit`) but the 642be5a
merge conflict in the file left both versions present with conflict markers — the file
currently has conflict markers and will not compile. The fix in this ticket is to resolve
the conflict in favour of the HEAD pagination branch.

**New backlog row**: `APISIMP-REF-ANNOTATION-LIST-UNCAPPED`

---

### §MINOR-2 — APISIMP-CONTAINER-CHANNEL-ANNOTATIONS-UNCAPPED

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:~1004`

`ContainersV2Rest.listChannelAnnotations()` (`GET /v2/containers/{appId}/channels/{channelShepardId}/annotations`)
returns the handler result directly with no `page`/`pageSize` params accepted at the REST
layer and no cap.

**Fix**: accept `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page` and
`@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize`; thread them to
the handler or slice the returned list.

**New backlog row**: `APISIMP-CONTAINER-CHANNEL-ANNOTATIONS-UNCAPPED`

---

### §MINOR-3 — APISIMP-CONTAINER-TEMPORAL-ANNOTATIONS-UNCAPPED

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:~1099`

`ContainersV2Rest.listTemporalAnnotations()` (`GET /v2/containers/{appId}/temporal-annotations`)
returns the handler result directly with no cap. Temporal annotations (event markers) can grow
unboundedly in long-running MFFD process chains.

**Fix**: same pattern as §MINOR-2.

**New backlog row**: `APISIMP-CONTAINER-TEMPORAL-ANNOTATIONS-UNCAPPED`

---

## Priority note

The highest-value fix is **§MINOR-1** (`APISIMP-REF-ANNOTATION-LIST-UNCAPPED`) because:
- The file already has unresolved conflict markers — it will not compile on the 642be5a branch
- The HEAD pagination implementation is already written; only the conflict resolution is needed
- Dispatched as STEP 3 of fire-379

§MINOR-2 and §MINOR-3 require locating the exact method signatures in `ContainersV2Rest.java`
(a large file) before implementing; size XS each.
