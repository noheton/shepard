---
stage: concept
last-stage-change: 2026-07-10
---

# APISIMP — v2 surface simplification sweep (2026-07-10, fire-526)

Systematic audit of the `/v2` REST surface following exhaustion of the previous
APISIMP queue (APISIMP-LABJOURNAL-PATHPARAM merged as PR #2459, fire-525). Scope:
every `@Path`, `@PathParam`, `@QueryParam`, and `@Max` annotation in
`backend/src/main/java/de/dlr/shepard/v2/**` plus one pass over admin
`*ConfigRest` classes to confirm V2CONV-A4 completion.

---

## Confirmed: no remaining issues in three categories

**Bespoke admin `*ConfigRest` classes — NONE (V2CONV-A4 complete).** All formerly
separate config REST classes (semantic, jupyter, sql-timeseries, ror, provenance,
autosweep, quality-scoring) have been deleted. Config surfaces now flow through
`ConfigDescriptor` implementations in the generic `AdminConfigRest` at
`/v2/admin/config/{feature}`. `AdminFeaturesRest.java` is already tombstoned
(every method returns `410 Gone` with a `Location` header to the generic surface).
V2CONV-A4 may be marked ✓ shipped.

**Numeric id leaks — NONE.** No `@PathParam` or `@QueryParam` binding accepts
`Long`/`long`/`Integer` for entity identifiers. Consistent pattern: take `String
appId`, resolve to `long ogmId` via `EntityIdResolver.resolveLong()`. The only
`Long` query params (`start`, `end` in `ContainersV2Rest`) are nanosecond-epoch
timestamps, not Neo4j entity IDs.

**Pagination query param naming — CONSISTENT.** Every v2 endpoint uses
`@QueryParam("page")` (zero-based) and `@QueryParam("pageSize")`. No `limit` /
`offset` / `size` variants in pagination contexts.

---

## Category 1 — Non-standard `@PathParam` names (XS candidates filed this fire)

These are single-occurrence or small single-class violations that follow the exact
same pattern as previously merged normalizations (fire-506 through fire-525). Each
is a standalone XS PR.

| APISIMP row | Class | Current param | Lines |
|---|---|---|---|
| APISIMP-COLLEVENTS-PATHPARAM | `CollectionEventsRest` | `{collectionAppId}` | 47, 83, 92, 98 |
| APISIMP-COLLWATCHERS-PATHPARAM | `CollectionWatchersRest` | `{collectionAppId}` | 89, 128, 157, 178 |
| APISIMP-CONTAINERPUBSTATE-PATHPARAM | `ContainerPublicationStateRest` | `{containerAppId}` | 99, 140 |
| APISIMP-NOTEBOOK-PATHPARAM | `NotebookRest` | `{dataObjectAppId}` | 95, 119 |
| APISIMP-VOCABBROWSE-PATHPARAM | `VocabularyBrowseRest` | `{entityAppId}`, `{vocabId}` | 164, 228 |
| APISIMP-MEROLE-PATHPARAM | `MeRoleInRest` | `{collectionAppId}` | 88 |
| APISIMP-COLLWATCHES-PATHPARAM | `CollectionWatchesRest` | `{collectionAppId}` (class) | 109, 160 |

**Dispatched this fire:** `APISIMP-COLLEVENTS-PATHPARAM` (1 file, 4 touch-points).
NOTE: line 73 of `CollectionEventsRest.java` references `collectionAppId` as an SSE
event payload field name in the `@Operation` description — that string MUST NOT be
changed (it documents the wire shape of the SSE event data, not the path param).

---

## Category 2 — Oversized `@Max` page caps (XS candidate filed this fire)

Standard v2 pagination cap is `@Max(200)`. One XS violation filed this fire:

| APISIMP row | Class | Param | Current @Max | Line |
|---|---|---|---|---|
| APISIMP-BUNDLEGROUPS-PAGECAP | `BundleGroupsV2Rest` | `pageSize` | 1000 | 369 |

---

## Category 3 — Non-standard @PathParam names (S/M candidates, future fires)

Larger multi-class or multi-method normalizations deferred as S/M rows:

**APISIMP-COLL-APPID (M)** — `{collectionAppId}` as class-level primary path
variable across 10 collection sub-resource classes. The largest cluster:
`CollectionLabJournalEntriesRest`, `CollectionDQRRest`, `DataObjectV2Rest`,
`DataObjectCollectionScopedRdfRest`, `SnapshotPinnedReadRest`,
`TemplateInstantiationRest` — plus `CollectionWatchersRest`, `CollectionWatchesRest`,
`MeRoleInRest`, `CollectionEventsRest` (the last four cleared by XS rows filed above).

**APISIMP-DO-APPID (M)** — `{dataObjectAppId}` as secondary param in the two-segment
`/collections/{appId}/data-objects/{dataObjectAppId}/...` chain
(`DataObjectV2Rest.java`, 9 method bindings). Must come AFTER APISIMP-COLL-APPID
renames the outer param — two `{appId}` segments in one URL are ambiguous. Once
outer is `{appId}`, inner uses a qualifier like `{id}`.

**APISIMP-ANNOT-APPID (S)** — `{annotationAppId}` as secondary variable in
`ContainersV2Rest.java` (lines 1105, 1223, 1258, 1292) and
`ReferenceAnnotationRest.java` (lines 222, 251, 281).

**APISIMP-GROUP-APPID (S)** — `{groupAppId}` as secondary param in
`BundleGroupsV2Rest.java` bundle-group sub-resource routes (lines 227, 267, 313,
359, 413).

**APISIMP-TEMPLATE-APPID (XS)** — `{templateAppId}` as primary param in
`TemplateExcelExportRest.java:127` and `TemplateFormRest.java:107`.

**APISIMP-MINOR-APPIDS (XS)** — `{dqrAppId}` (`CollectionDQRRest.java:168`),
`{predecessorAppId}` (`DataObjectV2Rest.java:752`).

---

## Category 4 — Oversized @Max page caps (future fires)

| File | Param | @Max | Lines | Notes |
|---|---|---|---|---|
| `ProvenanceRest.java` | `pageSize` | 1000 | 134, 202, 248, 301, 347, 382 | 6 endpoints; "cursor window" semantics |
| `SnapshotPinnedReadRest.java` | `pageSize` | 2000 | 133 | Default 500 |
| `SnapshotRest.java` | `pageSize` | 1000 | 156 | Default 200 |
| `InstanceAdminRest.java` | `pageSize` | 500 | 210, 271 | Already filed: APISIMP-ADMIN-AUDIT-PAGECAP (#2455) |
| `ShapesPredicatesRest.java` | `pageSize` | 500 | 107 | Default 200 |

Domain caps that are NOT pagination (intentional, do not file):
`maxItems` (`SnapshotDiffRest:133` 20000; `ImportDiagnosticsV2Rest:135` 10000;
`CollectionDQRRest:206` 5000), `maxPoints` (`ContainersV2Rest:741` 5000 LTTB),
`windowSeconds` (`ContainersV2Rest:979` 3600), `size` thumbnail pixels
(`ContainersV2Rest:1486` 2048).

---

## Category 5 — Design-level observations (S, no PR yet)

**APISIMP-SNAPSHOT-DIFF-PATH (S)** — `SnapshotDiffRest` path
`/v2/snapshots/{aAppId}/diff/{bAppId}` implies snapshot A owns snapshot B as a
sub-resource. A diff belongs to neither. Preferred shape:
`GET /v2/snapshots/diff?a={aAppId}&b={bAppId}` (symmetric query params). Requires
a wire-shape change — needs a deprecation window and OpenAPI version note.

**APISIMP-JUPYTER-PUB-DUPL (S)** — `JupyterConfigPublicRest.java` at
`/v2/jupyter/config` duplicates `GET /v2/admin/config/jupyter` to allow non-admin
authenticated reads. Elimination path: add a `nonAdminReadable` flag to
`ConfigDescriptor` so `AdminConfigRest` can serve certain features without the
`instance-admin` gate.

---

## Summary

- 7 non-standard primary `@PathParam` violations filed as XS rows; 1 dispatched (APISIMP-COLLEVENTS-PATHPARAM)
- 1 oversized `@Max` cap filed as XS row (APISIMP-BUNDLEGROUPS-PAGECAP)
- 0 bespoke admin `*ConfigRest` classes remaining (V2CONV-A4 confirmed complete)
- 0 numeric id leaks on the API surface
- 0 pagination query param naming inconsistencies
- Several S/M candidates documented for future fires (APISIMP-COLL-APPID, APISIMP-DO-APPID, etc.)
