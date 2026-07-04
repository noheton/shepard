---
stage: deployed
last-stage-change: 2026-06-17
---

# APISIMP sweep — fire-93 (2026-06-17)

**Context:** fire-93 dispatch sweep. Primary named APISIMP rows (APISIMP-FILE-PATH-RETIRE-2,
APISIMP-KIND-DISCRIMINATOR Slice 3 / B8) are blocked on #1966 merge. Six other PRs are
READY for orchestrator merge (#1966, #1967, #1968, #1970, #1971, #1972). This sweep
scans for net-new findings on the live v2 REST surface.

**Scope:** `backend/src/main/java/de/dlr/shepard/v2/**` + `plugins/*/src/main/java/**`.

**Checks applied:**
1. Per-kind endpoints not yet unified under `?kind=`
2. Bespoke admin `*ConfigRest` not on generic `/v2/admin/config/{feature}` registry
3. Numeric Neo4j ids leaking into `@PathParam`/`@QueryParam`/response bodies
4. Inconsistent pagination param names or error envelopes
5. HTTP 4xx/5xx responses returning bare String entities instead of `ProblemJson`
6. Stale `@Operation` descriptions referencing tombstoned paths
7. Forbidden `@Path(Constants.SHEPARD_API + ...)` additions

---

## Finding 1 — MAJOR/M: v2 surface missing `kind=structured-data` handler

**ID:** `APISIMP-STRUCTURED-DATA-KIND`
**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java`
**Severity:** MAJOR
**Size:** M

`/v2/references?kind=structured-data` and `/v2/containers?kind=structured-data` have no
registered `ReferenceKindHandler` or `ContainerKindHandler`. The only paths to create
and upload structured-data payloads are the frozen v1 trio:

- `POST /shepard/api/structuredDataContainers` (numeric `collectionId`)
- `POST /shepard/api/structuredDataContainers/{id}/payload` (numeric `id`)
- `POST /shepard/api/collections/{id}/data-objects/{doId}/structuredDataReferences` (numeric ids)

This forces every v2 caller (the fork's own importers, plugins, the frontend) to fall back
to v1 with numeric-id resolution. BTKVS-A1-SEED-V2REFS notes explicitly: "POST /v2/references
registers NO structureddata kind handler — verified in source + live OpenAPI 2026-06-12."
P24 (backlog line 305) and BTKVS-A1 note further: the MFFD bridge-welding import fell back to
v1 (numeric ids resolved lazily) for EVERY structured ref because no v2 path exists.

**Fix:** Add `StructuredDataReferenceKindHandler` implementing `ReferenceKindHandler` (kind=`structured-data`):
- `create()` → `POST /v2/references?kind=structured-data` + optional payload upload
- `supportsContentUpload()` + `uploadContent()` for structured payload bytes  
- Companion `StructuredDataContainerKindHandler` for `POST /v2/containers?kind=structured-data`
- Frontend composable update: `useCreateStructuredDataReference.ts` using `useV2ShepardApi`

**AC:** `POST /v2/references?kind=structured-data` creates a structured-data reference
by appId; `PUT /v2/references/{appId}/content` uploads payload; all operations use appId
(not numeric id); `mvn verify -pl backend` (full incl. plugins) + FE typecheck green;
existing v1 paths UNCHANGED (frozen upstream-compat surface).

**First-refs:**
- `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java`
- `backend/src/main/java/de/dlr/shepard/v2/references/spi/ReferenceKindHandler.java`
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`
- `aidocs/16-dispatcher-backlog.md` row P24 (prior tracker)
- `aidocs/16-dispatcher-backlog.md` row BTKVS-A1-SEED-V2REFS (confirmed gap)

---

## Findings skipped (already tracked, excepted, or not actionable)

| Candidate | Why skipped |
|---|---|
| Spatiotemporal plugin v1 paths (`SpatialDataReferenceRest`, `SpatialDataPointRest`) | Explicitly excepted in CLAUDE.md §"plugin backends build on /v2/": "frozen upstream-byte-compat REST surface that the plugin inherited … keeps those v1 resources unchanged." SPATIAL-V6-003 + PLUGIN-V2-001 track the v2 sibling shelf |
| `FileContainerKindHandler.java:183` bare String 503 | Already filed as APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING; PR #1973 READY |
| `FileBundleReferenceRest` at `/v2/bundles` | Already tracked as APISIMP-KIND-DISCRIMINATOR Slice 3 (B8), blocked on #1966 |
| `CollectionSceneGraphRest` at `/v2/collections/{appId}/scene-graph` | Correctly dissolved by B4: endpoint kept for compat but now stores MAPPING_RECIPE ShepardTemplate appId. Comment in class documents the V2CONV-B4 intent. Not a residual issue |
| `UserGroupV2Rest` Java variable `size` vs wire param `pageSize` | Wire param `@QueryParam("pageSize")` is correct. Java variable naming only; already corrected by APISIMP-USERGROUP-PAGESIZE (PR #1954, shipped fire-58) |
| `CrossDoBulkDataRest` at `/v2/data-objects/cross-timeseries-bulk` | Correct path post-APISIMP-CROSS-TS-BULK-PATH (PR #1952, shipped fire-56) |
| `TimeseriesAnnotationRest` / `VideoAnnotationRest` path collision | Already filed as APISIMP-ANNOTATION-SUBRESOURCE-COLLISION; PR #1971 READY |

---

## Backlog status corrections (fire-93)

Three rows were stale — updated in aidocs/16:

| Row | Old status | New status |
|---|---|---|
| APISIMP-VIDEO-STREAMREF-PATH | ⏳ queued (unblocked fire-76) | 🟢 PR #1970 READY |
| APISIMP-GIT-REF-PATH | ⏳ queued (unblocked fire-76) | 🟢 PR #1967 READY |
| APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING | ⏳ queued (fire-91 sweep) | 🟢 PR #1973 READY |

---

## Net-new rows filed in aidocs/16

| Row ID | Severity | Size | One-line AC |
|---|---|---|---|
| `APISIMP-STRUCTURED-DATA-KIND` | MAJOR | M | `POST /v2/references?kind=structured-data` creates a structured-data reference by appId; v1 paths unchanged |
