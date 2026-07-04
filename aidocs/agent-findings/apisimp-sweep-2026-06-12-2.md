---
stage: concept
last-stage-change: 2026-06-12
---
# APISIMP Sweep — 2026-06-12 (pass 2)

**Date:** 2026-06-12
**SSOT:** `aidocs/platform/191-v2-surface-convergence.md`
**Previous sweep:** `aidocs/agent-findings/apisimp-sweep-2026-06-12.md`

Fifth incremental audit of the `/v2` REST surface. Run after:
- PR #1862 merged (`a95e119`): APISIMP-NOTIF-TEST-RESP-ENVELOPE, APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID, APISIMP-PLACEHOLDER-REGISTRY-STALE-PATH all shipped.
- V2CONV-A7-SVDX-REST-DISSOLVE merged (`42dca7d`): `/v2/svdx` namespace deleted.

Scope: `backend/.../v2/**` + `plugins/*/.../v2/**` + `frontend/` callers.
Research-and-backlog only; one new row filed.

## Verifications

### V1 — SVDX dissolve is clean

`plugins/fileformat-svdx/src/main/java/de/dlr/shepard/v2/svdx/resources/` no longer exists. No frontend caller for `/v2/svdx/ingest` found (grepped all `frontend/**`). `SvdxCsvTransformExecutor` is wired via META-INF services. `SvdxManifestParser` annotate-on-upload path is untouched. No migration needed. ✓

### V2 — No new `@PathParam Long` leaks in v2

`grep -r "@PathParam.*Long" backend/.../v2/**` returns zero hits (excluding the allowed spatiotemporal upstream-compat exception and v1-compat plugin whose job is the frozen surface). ✓

### V3 — No bespoke `*ConfigRest` classes in v2

`AdminConfigRest.java` is the only `/v2/admin/config` resource. All plugin-specific admin config routes migrated under V2CONV-A7-PLUGIN-ADMIN-CONFIG. ✓

### V4 — No `@QueryParam("limit")` remaining in v2

Zero hits from grep. All limit-style pagination migrated to `pageSize` in APISIMP-PAGINATION-UNIFY slices 1+2. ✓

### V5 — Remaining `@QueryParam("size")` in v2 are tracked or non-pagination semantic

| Class | Line | Semantic | Status |
|---|---|---|---|
| `CollectionV2Rest` | 156 | pagination | tracked — PR #1847 (APISIMP-PAGINATION-UNIFY-1) |
| `DataObjectV2Rest` | 186 | pagination | tracked — PR #1847 |
| `SnapshotListRest` | 123 | pagination | tracked — PR #1847 |
| `CollectionSnapshotRest` | 183 | pagination | tracked — PR #1847 |
| `TimeseriesContainerChannelsRest` | 105 | pagination | tracked — PR #1847 |
| `InstanceAdminRest` | 224 | pagination | tracked — PR #1847 |
| `FileBundleReferenceRest` | 431 | pagination | tracked — PR #1847 |
| `ThumbnailRest` | 69 | pixel size (Integer) | **not pagination** — no action |

`ThumbnailRest` accepts `?size` as a pixel dimension hint, not a page size. No row needed.

### V6 — Plugin REST paths clean

No plugin REST class (excluding spatiotemporal and v1-compat) uses `@PathParam Long` or `@Path(Constants.SHEPARD_API + ...)`. ✓

### V7 — Three XS rows confirmed shipped in `a95e119` (#1862)

| Row | Status |
|---|---|
| APISIMP-NOTIF-TEST-RESP-ENVELOPE | ✓ shipped |
| APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID | ✓ shipped |
| APISIMP-PLACEHOLDER-REGISTRY-STALE-PATH | ✓ shipped |

---

## New Finding

### Finding 1 — `ContainerSummaryIO.id` numeric wire leak on `/v2/collections/.../referenced-containers`

**Severity:** MINOR (no live bug; legacy navigation shim; blocked by CONTAINER-V2-ROUTE)
**Location:**
- `backend/.../v2/collection/io/ContainerSummaryIO.java:13` — `private long id`
- `backend/.../v2/collection/daos/CollectionContainersDAO.java:41` — populates `id`
- `frontend/components/context/collection/CollectionContainersPanel.vue:17` — `c.id` in `containerPath()`

**Row filed:** `APISIMP-CONTAINER-SUMMARY-IO-DROP-ID`

`ContainerSummaryIO` is the wire shape for
`GET /v2/collections/{collectionAppId}/referenced-containers`. It carries both
`appId: String` (UUID v7) and `id: long` (Neo4j OGM id). The schema comment
explicitly says: _"Neo4j OGM id — use for legacy navigation routes."_

The frontend `CollectionContainersPanel.vue:17` builds the container detail route:

```ts
function containerPath(c: ContainerSummary): string {
  // Detail-page route is `/containers/<segment>/<id>/`.
  return `/containers/${urlSegmentForContainerType(c.containerType)}${c.id}/`;
}
```

This propagates the numeric id into the URL, which is the antipattern
CONTAINER-V2-ROUTE is designed to eliminate.

**Blocker:** The container detail pages (`pages/containers/{type}/[containerId]/index.vue`)
currently fetch the container via v1-generated `getXContainer({ containerId: numericId })`.
Dropping `id` from the wire before CONTAINER-V2-ROUTE flips those accessors to
appId-keyed GET would break all container navigation. This row must ship in lockstep
with CONTAINER-V2-ROUTE (or as the immediate follow-up once container routes
accept appId).

**Fix when unblocked:**
1. Drop `private long id` + constructor param + getter/setter from `ContainerSummaryIO.java`.
2. Remove `id` population from `CollectionContainersDAO.java:41`.
3. Update `CollectionContainersPanel.vue:17` `containerPath()` to use `c.appId` instead of `c.id`.
4. Ensure container route segments accept UUID strings before merging.

---

## MFFD-STRINGER-SVDX-INGEST-1 path stale

`aidocs/16` row `MFFD-STRINGER-SVDX-INGEST-1` says _"Reuses `POST /v2/svdx/ingest`"_ —
that endpoint is now deleted (V2CONV-A7-SVDX-REST-DISSOLVE). The correct path for
SVDX ingestion is now `POST /v2/mappings/{templateAppId}/materialize` with
`SvdxCsvIngestShape` IRI. The row needs an in-place note update (no aidocs/16
row of its own — it's MFFD domain, not an APISIMP finding). Row updated in aidocs/16.

---

## Summary

| Finding | Severity | Row filed | Action |
|---|---|---|---|
| SVDX dissolve clean | N/A | — | verification pass |
| No new numeric PathParam | N/A | — | verification pass |
| No new bespoke ConfigRest | N/A | — | verification pass |
| No remaining limit pagination | N/A | — | verification pass |
| ThumbnailRest `?size` is pixel size | N/A | — | no action |
| ContainerSummaryIO.id wire leak | MINOR | APISIMP-CONTAINER-SUMMARY-IO-DROP-ID | queued; blocked-by CONTAINER-V2-ROUTE |
| MFFD-STRINGER-SVDX-INGEST-1 stale path | doc | — | inline aidocs/16 update |
