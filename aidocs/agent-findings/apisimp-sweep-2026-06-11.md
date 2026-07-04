---
stage: concept
last-stage-change: 2026-06-11
---

# APISIMP — v2 surface simplification sweep (2026-06-11)

Second incremental audit of the `/v2` REST surface, run after the 2026-06-10 sweep
actioned five major findings (SA-CONT-DELETE, PV-UNIFY, TSCONT-APPID-KEY,
PAGINATION-UNIFY, ERROR-ENVELOPE-UNIFY — all shipped or in-flight). This pass
scans the residual surface after those waves completed.

Scope: `backend/.../v2/**` + `plugins/*/.../v2/**` (+ plugin `@Path`s).
Research-and-backlog only; no production Java/Vue touched.

## What I found

### Finding 1 — ThermographyConfigAdminRest not on the generic registry

`backend/.../v2/admin/thermography/resources/ThermographyConfigAdminRest.java`
serves `GET|PATCH /v2/admin/thermography/config` — a bespoke admin config REST
class following the exact pattern that V2CONV-A4 (2026-06-03) was designed to
supersede. The generic `AdminConfigRest` + `ConfigDescriptor<T>` SPI is fully
shipped; all other config resources have been migrated (V2CONV-A4: ror,
sql-timeseries, jupyter, semantic; V2CONV-A7-PLUGIN-ADMIN-CONFIG slices 1–5:
unhide, video, AAS, v1-compat, ePIC, DataCite, jupyter-plugin). This one was
added by MFFD-NDT-ADMIN-CONFIG-1 after the A4 wave, landing after the migration
pass.

No FE caller on the bespoke `/v2/admin/thermography/config` path found
(grep `frontend/`). The path change to `/v2/admin/config/thermography` is a
wire break for any direct admin-pane caller, but since there is none, the
migration is backend-only.

**Filed as `APISIMP-THERMO-ADMIN-CONFIG` (S).**

### Finding 2 — FC + SDC linked-data-objects still numeric-keyed

`FileContainerLinkedDataObjectsRest` (`GET /v2/file-containers/{containerId}/linked-data-objects` +
`DELETE /v2/file-containers/{containerId}`) and
`StructuredDataContainerLinkedDataObjectsRest` (`GET /v2/structured-data-containers/{containerId}/linked-data-objects` +
`DELETE /v2/structured-data-containers/{containerId}`) both accept
`@PathParam("containerId") long containerId` — the raw Neo4j OGM id.

The timeseries equivalent was fully migrated in APISIMP-TSCONT-APPID-KEY (2026-06-11).
These two resources were added by CC1b / DI1 (safe-delete) and predate the
TSCONT migration; they were not in scope at the time.

FE callers using numeric ids:
- `frontend/composables/containers/useFileContainerLinkedDataObjects.ts:37`
  (`${containerId}/linked-data-objects` — numeric `containerId`)
- `frontend/composables/containers/useStructuredDataContainerLinkedDataObjects.ts:37`
  (same pattern)
- `frontend/composables/containers/useContainerReferencedByCollections.ts:37,43`
  (constructs URL for FILE and STRUCTUREDDATA with a `containerId: string | number`)

Note: `useContainerReferencedByCollections` already handles TIMESERIES with
the post-TSCONT-APPID-KEY `containerAppId`; its comment at line 39–41 explicitly
notes callers must pass the appId for timeseries — the same constraint now
applies to file and structured containers.

**Filed as `APISIMP-FC-SDC-LINKED-DO-APPID` (S).**

### Finding 3 — TimeseriesChannelAnnotationRest annotationId numeric leak

`TimeseriesChannelAnnotationRest` (`backend/.../v2/timeseriescontainer/resources/`):
The DELETE endpoint `DELETE /v2/timeseries-containers/{containerAppId}/channels/{channelShepardId}/annotations/{annotationId}`
takes `@PathParam("annotationId") @NotNull Long annotationId` (line 130) — a
Neo4j OGM node id for the `SemanticAnnotation` entity. The `containerAppId` (line 69)
was already migrated in APISIMP-TSCONT-APPID-KEY-4, but the `annotationId`
parameter inside the sub-resource was left numeric.

The GET (`listAnnotations`) and POST (`createAnnotation`) paths are clean
(no numeric annotation id in path). Only the DELETE has this residual leak.

No FE caller for the DELETE endpoint found (grep `frontend/`, `e2e/`).
Backend-only migration; `AnnotatableTimeseriesService` needs a
`deleteAnnotationByAppId(String containerAppId, String channelShepardId, String annotationAppId)`
service method.

**Filed as `APISIMP-CHANNEL-ANNOT-APPID` (S).**

### Clean-check — patterns from the 2026-06-10 sweep

| Check | Status |
|---|---|
| `@Path(Constants.SHEPARD_API + …)` in `/v2/**` code | **Clean** — zero new additions. The spatiotemporal plugin's `SpatialDataPointRest` + `SpatialDataReferenceRest` on `Constants.SHEPARD_API` are the frozen upstream-byte-compat v1 paths; their v2 parity shelf is tracked as `PLUGIN-V2-001`. |
| `@QueryParam("size")` on pagination endpoints | **In-flight** — the 7 endpoints covered by APISIMP-PAGINATION-UNIFY-1 (PR #1847) still show `"size"`; PR is waiting on CodeQL race resolution. No NEW untracked pagination-size uses found. |
| Bespoke `*ConfigRest` on `/v2/admin/*/config` | **One found** — `ThermographyConfigAdminRest` (F1 above). No others. |
| Numeric `@PathParam Long` in v2 | **Two residuals** — `FileContainerLinkedDataObjectsRest` + `StructuredDataContainerLinkedDataObjectsRest` (F2) + `TimeseriesChannelAnnotationRest`'s `annotationId` (F3). No new numeric path params otherwise. |
| `ProvenanceRest` + `SemanticTermSearchRest` `limit` → `pageSize` | **Shipped** — APISIMP-PAGINATION-UNIFY-2 merged (#1848). |
| RFC-7807 error envelopes | **Shipped** — APISIMP-ERROR-ENVELOPE-UNIFY all 8 slices merged. |
| RESEED-FIND-KRL-CDI | **Shipped** — PR #1815 merged 2026-06-10. Backlog row status correction noted. |

## Opportunities

1. **`APISIMP-THERMO-ADMIN-CONFIG`** — S, low risk. Only backend; path changes; no FE caller.
   The `ThermographyConfigDescriptor` pattern follows the other A7 descriptors exactly.
   Natural next-smallest APISIMP dispatch.

2. **`APISIMP-FC-SDC-LINKED-DO-APPID`** — S, medium spread. Backend + FE migration.
   The same two-step shape as TSCONT-APPID-KEY: (a) backend path-param type change +
   service `findByAppId` addition; (b) FE composable update. Can be a single PR.

3. **`APISIMP-CHANNEL-ANNOT-APPID`** — S, low risk. No FE caller; backend-only.
   Add `deleteAnnotationByAppId` to `AnnotatableTimeseriesService`; change path param type.

## Gaps & blockers

- `APISIMP-PAGINATION-UNIFY-1` (#1847) stuck on CodeQL summary race flake.
  All substantive analyses (java-kotlin, javascript-typescript) pass.
  Recommend operator re-run of the CodeQL check or a force-merge with
  `merge_override: security_alerts`. Failing check is the aggregator wrapper,
  not a real finding.
- `PLUGIN-V2-001` (spatial `/v2/` parity shelf) remains queued; the
  spatiotemporal v1 frozen surfaces stay as-is until that row ships.

## What surprised me

- `ThermographyConfigAdminRest` still uses `io.quarkus.logging.Log` (line 8)
  — the same class-scanning bug fixed for vis-trace3d in RESEED-FIND-RENDER-PNG-LOG.
  However, this class is in the **main backend module** (not a separate plugin jar),
  so Quarkus's bytecode transformer does apply here and the Log usage is safe.
  No action needed; noted for completeness.
- The linked-data-objects numeric-id issue is more impactful than it looks:
  `useContainerReferencedByCollections` is called from the container list page for
  every visible row — passing numeric ids to the file/structured endpoints means
  every container list row relies on the legacy OGM-id surface.
- After this sweep: the residual numeric-id surface in `/v2/` is now only these
  three findings plus the spatiotemporal frozen v1 endpoints (PLUGIN-V2-001).

## Second-pass findings (2026-06-11, post-merge of #1852)

After APISIMP-FC-SDC-LINKED-DO-APPID shipped (#1852, `df8b242e`), a second scan
ran across the full `/v2` surface. Findings and observations:

### Finding 4 — `SnapshotListPageIO` response field named `size`, request param renamed to `pageSize` by #1847

`SnapshotListRest` (lines 89–94) defines a nested `SnapshotListPageIO` record:
```java
public record SnapshotListPageIO(
  List<SnapshotListItemIO> items,
  long total,
  int page,
  int size      // ← response body field name
) {}
```

PR #1847 (`APISIMP-PAGINATION-UNIFY-1`) renames `@QueryParam("size")` →
`@QueryParam("pageSize")` but does NOT rename the `SnapshotListPageIO.size`
record field. After #1847 merges: request param is `?pageSize` but the
JSON response body still contains `"size": 50`. Inconsistent to callers
who now read `pageSize` in the request docs but see `size` in the response.

AC: rename `SnapshotListPageIO.size` → `pageSize`; update FE consumer
`useSnapshotList.ts` if it reads `.size` off the response envelope.

**Filed as `APISIMP-SNAPSHOT-RESP-SIZE` (XS); pairs with/blocked by #1847.**

### Scan observations — intentional legacy debt (not filed)

These surfaces were examined and noted as intentional technical debt that
is already tracked or explicitly documented inline — no new rows filed:

| Surface | State | Why not filed |
|---|---|---|
| `TimeseriesChannelV2IO.id` (int, Postgres serial) | Legacy, explicit | Schema says "Legacy numeric channel id (Postgres serial). Will be deprecated once shepardId adoption is complete." Tracked under TS-ID migration (`aidocs/platform/87`). |
| `TimeseriesChannelV2IO.containerId` (long, Postgres FK) | Intentional | Postgres FK, not a Neo4j OGM id; retained for callers joining the Postgres `timeseries` table. |
| `ContainerSummaryIO.id` (long, Neo4j OGM) | Legacy, explicit | Schema says "Neo4j OGM id — use for legacy navigation routes." Needed until CONTAINER-V2-ROUTE migration clears (V2-SWEEP row). |
| `DataObjectDetailV2IO.referenceIds[]` (numeric longs) | Legacy, explicit | Javadoc at line 30 tags these as "Legacy fields." Migration deferred pending REFS-V2-PANELS sweep row. |
| Plugin ref resources (git preview/checkUpdate, video upload/download) | Correct per PLUGIN-PERKIND-CRUD-CLEANUP | Shipped 2026-06-09: per-kind CRUD deleted; only domain-specific special ops remain. Not a finding. |

### Post-second-pass status

After APISIMP-THERMO-ADMIN-CONFIG + APISIMP-FC-SDC-LINKED-DO-APPID +
APISIMP-CHANNEL-ANNOT-APPID all shipped, the residual findings are:

| Finding | Row | Size | State |
|---|---|---|---|
| Pagination `?size` vs `?pageSize` (7 endpoints) | APISIMP-PAGINATION-UNIFY-1 | M | PR #1847 — CodeQL race ⚠️ |
| `SnapshotListPageIO.size` → `pageSize` (response body) | APISIMP-SNAPSHOT-RESP-SIZE | XS | queued; pairs with #1847 |
| `ContainerSummaryIO.id` OGM exposure | (implicit) | S | blocked by CONTAINER-V2-ROUTE |
| `DataObjectDetailV2IO` numeric refIds | (implicit) | M | blocked by REFS-V2-PANELS |
| Spatial plugin v1 frozen surface | PLUGIN-V2-001 | M | queued |

## Cross-references
- `aidocs/platform/191-v2-surface-convergence.md` (V2CONV SSOT)
- `aidocs/agent-findings/apisimp-sweep-2026-06-10.md` (prior sweep — base findings)
- `aidocs/16` rows: `APISIMP-THERMO-ADMIN-CONFIG`, `APISIMP-FC-SDC-LINKED-DO-APPID`,
  `APISIMP-CHANNEL-ANNOT-APPID`, `APISIMP-SNAPSHOT-RESP-SIZE`, `PLUGIN-V2-001`,
  `APISIMP-PAGINATION-UNIFY`
- Files: `backend/.../v2/admin/thermography/resources/ThermographyConfigAdminRest.java`,
  `backend/.../v2/filecontainer/resources/FileContainerLinkedDataObjectsRest.java`,
  `backend/.../v2/structureddatacontainer/resources/StructuredDataContainerLinkedDataObjectsRest.java`,
  `backend/.../v2/timeseriescontainer/resources/TimeseriesChannelAnnotationRest.java:130`,
  `backend/.../v2/snapshot/resources/SnapshotListRest.java:89-94`,
  `frontend/composables/containers/useFileContainerLinkedDataObjects.ts`,
  `frontend/composables/containers/useStructuredDataContainerLinkedDataObjects.ts`,
  `frontend/composables/containers/useContainerReferencedByCollections.ts`

## Third-pass findings (2026-06-11, post-merge of UI-GAP-3 #1854)

Third scan after all second-pass rows shipped. Scope: full `/v2` REST + plugin
REST surfaces on current `main`.

Verified-clean (agent findings cross-checked, all already shipped or intentional):

| Agent finding | Actual state |
|---|---|
| `TimeseriesContainerSemanticAnnotationRest` numeric `@PathParam` | GONE — deleted by APISIMP-SA-CONT-DELETE (#1834) |
| `FileContainerSemanticAnnotationRest` numeric `@PathParam` | GONE — deleted by APISIMP-SA-CONT-DELETE |
| `StructuredDataContainerSemanticAnnotationRest` numeric `@PathParam` | GONE — deleted by APISIMP-SA-CONT-DELETE |
| `TimeseriesChannelAnnotationRest.annotationId` (Long) | Now `String annotationAppId` — APISIMP-CHANNEL-ANNOT-APPID (#1851) |
| `TimeseriesContainerTemporalAnnotationRest.containerId` (Long) | Now `String containerAppId` — APISIMP-TSCONT-APPID-KEY-4 (#1846) |
| `JupyterConfigPublicRest` not on config registry | Intentional — public read-only endpoint (any-auth), not an admin-only config surface |
| `TimeseriesAnnotationRest` / `AnomalyDetectionRest` path pattern | Correct — keyed on `String refAppId`, under `/v2/timeseries-references/` |
| `ThumbnailRest ?size` | Intentional image-dimension param, not pagination |
| `ProjectsRest` pagination | Already uses `page` + `pageSize` — no action |
| Spatial plugin v1 paths | PLUGIN-V2-001 already queued |

### Finding 5 — `ProjectIO` / `SubCollectionItemIO` / `ProjectByAnnotationItemIO` expose legacy `Long id`

`backend/.../v2/project/io/ProjectIO.java:34` carries:
```java
@Schema(description = "Legacy long identifier of the underlying Collection (for upstream callers).")
private Long id;
```
`SubCollectionItemIO.java:26` and `ProjectByAnnotationItemIO.java:31` have the same
field with identical "Legacy long id" schema description.

`ProjectsDAO.java:116,214,325` actively populates these via `io.setId(asLong(row.get("shepardId")))`.

The `/v2/projects/` surface is a **fork-only** surface (not present in upstream
`openapi-5.4.0.json`). The "for upstream callers" justification does not apply —
upstream has no `/v2/projects/` endpoint to call. No frontend composable reads
`.id` off a project response (grep `frontend/` returns zero hits for
`project.id` / `projectData.id`). The field is purely additive dead weight.

AC: drop `Long id` from all three IO classes; drop the three `io.setId(...)` calls
in `ProjectsDAO`; `mvn verify` green. No FE change. Wire break on `/v2/projects/`
(pre-prod, not in any stable release).

**Filed as `APISIMP-PROJECT-IO-DROP-LEGACY-ID` (XS).**

## Third-pass summary

| Finding | Row | Size | State |
|---|---|---|---|
| `ProjectIO.Long id` dead legacy field | APISIMP-PROJECT-IO-DROP-LEGACY-ID | XS | queued |
| All prior findings | shipped | — | — |
