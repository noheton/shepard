---
stage: concept
last-stage-change: 2026-06-30
---

# APISIMP Sweep — 2026-06-30 (fire-325)

Scan of the live `/v2` REST surface in `backend/src/main/java/de/dlr/shepard/v2/` and plugin `@Path` blocks. Checks: per-kind endpoint consolidation, numeric id leaks, unbounded list endpoints, pagination consistency, stale OpenAPI descriptions, forbidden v1-surface additions.

## CRITICAL (0)

No critical findings.

- No new `@Path(Constants.SHEPARD_API + ...)` additions to the frozen v1 surface.
- No numeric Neo4j node ids leaking via `@PathParam` / `@QueryParam` / response IO.
- No per-kind path prefixes (`/v2/file-containers/`, `/v2/timeseries-references/`, etc.) still active.

## MAJOR (2)

### M1 — Stale path references in IO Javadoc (6 files)

IO classes whose Javadoc still references the old per-kind path prefixes abolished by V2CONV-A2/A3. Developer-facing only (wire shape is correct) but misleads implementors reading the code.

| File | Line | Old path | Correct path |
|---|---|---|---|
| `v2/dataobject/io/DataObjectDetailV2IO.java` | 123, 135 | `/v2/timeseries-containers/{appId}` | `/v2/containers/{appId}` |
| `v2/timeseriescontainer/io/TimeseriesContainerChartViewIO.java` | 9 | `GET/PATCH /v2/timeseries-containers/{id}/chart-view` | `GET/PATCH /v2/containers/{appId}/chart-view` |
| `v2/timeseriescontainer/io/SpatialRolesIO.java` | 8 | `GET /v2/timeseries-containers/{containerId}/channels/spatial-roles` | `GET /v2/containers/{appId}/channels/spatial-roles` |
| `v2/file/io/PayloadVersionIO.java` | 8 | `GET /v2/file-containers/{containerAppId}/files/{originalName}/versions` | `GET /v2/containers/{appId}/files/{fileName}/versions` |
| `v2/timeseriescontainer/io/BulkChannelDataRequestIO.java` | 14 | `POST /v2/timeseries-containers/{containerId}/channels/data/bulk` | `POST /v2/containers/{appId}/channels/data/bulk` |
| `v2/timeseriescontainer/io/CopyIngestRequestIO.java` | 12 | `POST /v2/timeseries-containers/{containerId}/channels/{shepardId}/data/ingest` | `POST /v2/containers/{appId}/channels/{appId}/data/ingest` |

**Filed as:** `APISIMP-IO-STALE-PATH-REFS` (XS)

### M2 — `listDataObjects` uses header-based pagination inconsistent with body envelope pattern

`DataObjectV2Rest.listDataObjects()` (`GET /v2/data-objects`) uses `Content-Range` + `X-Total-Count` response headers for pagination metadata (lines 249–325), while every other paginated endpoint in the v2 surface returns `PagedResponseIO<T>` body envelopes (`{items, total, page, pageSize}`). This is the only exception in the v2 surface. Callers (the frontend `useListDataObjects.ts` composable) must handle the response differently from all other list endpoints.

**Filed as:** `APISIMP-DO-LIST-CONTENT-RANGE` (S) — assess/document; migration may be a later slice.

## MINOR (1)

### m1 — `predecessorChain`/`successorChain` lack optional pagination params

`DataObjectV2Rest.predecessorChain()` (line 844) and `successorChain()` (line 882) return bare `List<DataObjectSummaryIO>` without page/pageSize params. The depth is capped server-side (max 50 via `@DefaultValue("10") int depth`) so result sets are bounded. Low urgency but inconsistent with sibling endpoints `/predecessors`, `/successors`, `/children` which received `PagedResponseIO` wrapping in APISIMP-DO-RELATIONSHIP-LISTS-UNBOUND (fire-324, PR #2201).

**Filed as:** `APISIMP-DO-CHAIN-MISSING-PAGE-PARAMS` (S)

## Positive findings (no action needed)

- All paginated list endpoints use consistent `page` / `pageSize` params (APISIMP-PAGINATION-PARAM-STYLE done).
- All list endpoints that were unbounded have been wrapped in `PagedResponseIO` (APISIMP-PAGINATION-ENVELOPE-* series done, plus fire-324 batch).
- No bespoke `*ConfigRest` classes outside `AdminConfigRest` (V2CONV-A4 complete; `JupyterConfigPublicRest` is intentional — exposes public read-only view without admin role gate, documented in Javadoc).
- No encoding of `Long` database ids in `@PathParam` or `@QueryParam` on v2 endpoints.
- Plugin admin configs fully on the generic registry (V2CONV-A7-PLUGIN-ADMIN-CONFIG done).
- No duplicate rendering endpoints; `ShapesRenderRest` and `LabJournalRenderRest` are at distinct paths.
- `Constants.SHEPARD_API` surface untouched.

## Next dispatch

Smallest filed row: **`APISIMP-IO-STALE-PATH-REFS`** (XS) — update 6 IO Javadoc strings, direct-to-main.
