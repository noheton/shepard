---
stage: fragment
last-stage-change: 2026-06-14
---
# APISIMP Sweep — fire-32 (2026-06-14)

Scope: all Java files under `backend/src/main/java/de/dlr/shepard/v2/` and `plugins/*/src/main/java/`.
Skipped (frozen upstream-compat): `SpatialDataPointRest`, `SpatialDataReferenceRest` (APISIMP-NUMERIC-ID-BATCH-2 / APISIMP-V1-PATH-RESIDUAL-1, already tracked and deferred).
Context: follows fire-31 (APISIMP-COLLECTIONIO-NUMERIC-FILECONTAINER shipped), fire-29 (APISIMP-IMPORTV2REST-422-CONTENT-TYPE shipped), fire-26/27 (APISIMP-CONTAINERREF-DROP-NUMERIC + APISIMP-WATCH-DROP-OGMID shipped).

---

## Findings

### Finding 1: APISIMP-TYPED-PREDECESSOR-NUMERIC-ID — `TypedPredecessorSummaryIO.predecessorId` leaks numeric legacy id

**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/TypedPredecessorSummaryIO.java:28`
**Pattern:** Pattern 1 (numeric Neo4j entity id in v2 response IO) / Pattern 6 (numeric field alongside appId without deprecation)
**What's wrong:** `TypedPredecessorSummaryIO` carries both `predecessorAppId` (UUID v7, correct) and `predecessorId` (long, legacy sequential `shepardId` from `VersionableEntity.getShepardId()`). The `predecessorId` field is described as "Numeric shepardId of the predecessor DataObject" — this is the internal sequential counter, not a substrate identity. The frontend (`useFetchTypedPredecessors.ts:15`) reads it to cross-join with `DataObjectIO.predecessorIds: long[]` on the DataObject detail page (`dataobjects/[dataObjectId]/index.vue:327`). The coupling keeps the v2 surface tied to the v1 numeric predecessor-id body. `predecessorAppId` should be sufficient for the join; the frontend should cross-reference by appId, not by numeric id.
**Fix:** (a) Mark `predecessorId` `@Schema(deprecated=true)` in `TypedPredecessorSummaryIO`; (b) In `useFetchTypedPredecessors.ts:15` change `predecessorId: number` → `predecessorId?: number`; (c) In `dataobjects/[dataObjectId]/index.vue:327,337` cross-join on `predecessorAppId` instead of `predecessorId` — requires resolving the DataObject detail's predecessor list by appId (the detail already carries `typedPredecessorSummaries[].predecessorAppId`). Note: this can only land cleanly once `BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH` ships (predecessorAppIds on the write body) and the treeview has dropped its numeric predecessor dependency.
**Proposed row ID:** `APISIMP-TYPED-PREDECESSOR-NUMERIC-ID`
**Size:** S

---

### Finding 2: APISIMP-TSCHANNEL-CONTAINER-ID — `TimeseriesChannelV2IO.containerId` exposes Postgres FK on v2 wire

**File:** `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/io/TimeseriesChannelV2IO.java:41`
**Pattern:** Pattern 1 (substrate-internal id in v2 response)
**What's wrong:** `TimeseriesChannelV2IO` (served on `GET /v2/timeseries-containers/{containerAppId}/channels`) carries `long containerId` (line 41) — the Postgres serial FK linking the channel row to the container's TimescaleDB row. No parallel `containerAppId` field exists. The `shepardId` (UUID) field on the channel itself is the correct v2 identity; the `int id` field is an admitted legacy field (comment: "will be deprecated once `shepardId` adoption is complete"). The `containerId` has no mentioned callers in v2; the container is already addressed via the `{containerAppId}` path param. The field is part of the TS-ID migration work (aidocs/platform/87) — but its presence on the current v2 wire is undocumented.
**Fix:** (a) Add `@Schema(deprecated=true)` to `containerId` in `TimeseriesChannelV2IO`; (b) Add a companion `String containerAppId` field resolved from the `TimeseriesContainer.appId` (currently not in the Postgres row — must be joined via the container lookup service). This is gated on the TS-ID migration (TS-IDb/c adding `container_app_id` to the Postgres schema). Short-term: document the field as deprecated in the Schema annotation and file the companion-field addition as part of the TS-ID migration task.
**Proposed row ID:** `APISIMP-TSCHANNEL-CONTAINER-ID`
**Size:** S (annotation only); M (if adding containerAppId companion field requires DB migration)

---

### Finding 3: APISIMP-WIKIWRITE-LJERID — `WikiWriteResponseIO.labJournalEntryId` exposes Neo4j OGM id on v2 wire

**File:** `plugins/wiki-writer/src/main/java/de/dlr/shepard/plugins/wikiwriter/io/WikiWriteResponseIO.java:21`
**Pattern:** Pattern 1 (numeric entity id in v2 response IO)
**What's wrong:** `WikiWriteResponseIO` (served on `POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write`) carries `private long labJournalEntryId` — the Neo4j OGM internal id of the created `LabJournalEntry`. The Javadoc comment explicitly says: "Clients can use this with the upstream `GET /shepard/api/.../labJournalEntries/{id}` endpoint." — i.e. it exists to serve a v1 caller from a v2 response. `LabJournalEntry` has an `appId` (confirmed: `LabJournalRenderRest.java` uses `findByAppId(appId)`) so a v2-native appId is available. Set in `WikiWriterService.java:137–142` via `entry.getId()` (Neo4j OGM id, not appId).
**Fix:** (a) Add `private String labJournalEntryAppId` to `WikiWriteResponseIO`; (b) In `WikiWriterService.java:137` populate it from `entry.getAppId()`; (c) Mark `labJournalEntryId` `@Deprecated` with a note pointing to `labJournalEntryAppId`. Remove `labJournalEntryId` after L2e deprecation window.
**Proposed row ID:** `APISIMP-WIKIWRITE-LJERID`
**Size:** XS

---

### Finding 4: APISIMP-SAFEDELETE-CONFLICT-CONTENT-TYPE — SafeDeleteConflict 409 missing `application/problem+json` in 3 files

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/structureddatacontainer/resources/StructuredDataContainerLinkedDataObjectsRest.java:114`
- `backend/src/main/java/de/dlr/shepard/v2/filecontainer/resources/FileContainerLinkedDataObjectsRest.java:115`
- `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/resources/TimeseriesContainerLinkedDataObjectsRest.java:119`

**Pattern:** Pattern 3 (error response missing `application/problem+json` content-type)
**What's wrong:** All three safe-delete endpoints return HTTP 409 CONFLICT with a `SafeDeleteConflict` body, but none set `.type("application/problem+json")`. The class-level `@Produces(MediaType.APPLICATION_JSON)` means callers receive `Content-Type: application/json` for the 409. The pattern established by the APISIMP empty-bodies batches is to set `type("application/problem+json")` on every 4xx response. `SafeDeleteConflict` is a structured domain response (similar to `ImportPlanIO` for 422) — the content-type annotation is the only thing missing.
**Fix:** In each of the three files, add `.type("application/problem+json")` to the 409 CONFLICT return: `return Response.status(Status.CONFLICT).type("application/problem+json").entity(new SafeDeleteConflict(...)).build();`
**Proposed row ID:** `APISIMP-SAFEDELETE-CONFLICT-CONTENT-TYPE`
**Size:** XS

---

### Finding 5: APISIMP-SHAPEBUILD-BAD-REQUEST-TYPE — `ShapesBuildRest` 400 missing `application/problem+json`

**File:** `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesBuildRest.java:100–102, 112–114`
**Pattern:** Pattern 3 (error response missing `application/problem+json` content-type)
**What's wrong:** `POST /v2/shapes/build` returns HTTP 400 with `ShapeBuildResponseIO.invalid(...)` body at two sites (null body guard line 100, DSL parse failure line 112) without setting `Content-Type: application/problem+json`. The `@Produces(MediaType.APPLICATION_JSON)` class annotation sends `application/json`. `ShapeBuildResponseIO` is a structured domain response (analogous to `ImportPlanIO`) — the content-type annotation is missing.
**Fix:** Add `.type("application/problem+json")` to both 400 returns in `build()`. Also consider adding a `"type": "urn:shepard:error:shapes.invalid-dsl"` field to `ShapeBuildResponseIO` for full RFC 7807 conformance (optional follow-up).
**Proposed row ID:** `APISIMP-SHAPEBUILD-BAD-REQUEST-TYPE`
**Size:** XS

---

### Finding 6: APISIMP-SNAPSHOTDIFF-SELF-DIFF-BAD-REQUEST — `SnapshotDiffRest` self-diff guard returns bare 400

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotDiffRest.java:112`
**Pattern:** Pattern 3 (error response missing body and `application/problem+json`)
**What's wrong:** At line 112, when `aAppId.equals(bAppId)` (self-diff guard), the method returns `Response.status(Response.Status.BAD_REQUEST).build()` — no body, no content-type. Every other 4xx in this file uses the `problem()` helper defined at line 67. The self-diff case is the only outlier. Callers get a bodyless 400 with no explanation.
**Fix:** Replace the bare `build()` with `problem("urn:shepard:error:validation", "Bad Request", Response.Status.BAD_REQUEST, "aAppId and bAppId must be different — cannot diff a snapshot against itself.")` using the existing `problem()` helper at line 67.
**Proposed row ID:** `APISIMP-SNAPSHOTDIFF-SELF-DIFF-BAD-REQUEST`
**Size:** XS

---

### Finding 7: APISIMP-VIDEO-STORAGE-EXCEPTION-TYPE — `VideoStreamReferenceV2Rest` storage-exception handlers use bare String body

**File:** `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2Rest.java:140, 143, 145, 210, 212, 215`
**Pattern:** Pattern 3 (error response with wrong body type — string instead of `ProblemJson`)
**What's wrong:** The upload path (lines 139–145) and download path (lines 209–215) have catch blocks for `StorageNotInstalledException`, `StorageException`, and `IOException` that return `Response.status(status).entity(ex.getMessage()).build()` — a bare String entity, not a `ProblemJson`, and no `application/problem+json` content-type. APISIMP-EMPTY-BODIES-BATCH-16 (PR #1897) fixed the empty-body 4xx cases in this file but did not address these storage-exception handlers (they have a body — the exception message string). A `problem()` helper already exists at line 285 of this file.
**Fix:** Replace each of the 6 catch-block returns with `problem(Response.Status.SERVICE_UNAVAILABLE/INTERNAL_SERVER_ERROR, ex.getMessage())` using the existing `problem()` helper at line 285. For 503 `StorageNotInstalledException` use `SERVICE_UNAVAILABLE`; for 500 `StorageException`/`IOException` use `INTERNAL_SERVER_ERROR`.
**Proposed row ID:** `APISIMP-VIDEO-STORAGE-EXCEPTION-TYPE`
**Size:** XS

---

## Clean areas

| Pattern | Result |
|---------|--------|
| Pattern 2: `?size`/`?page-size` pagination (8 sites) | EXISTING — tracked as `APISIMP-PAGINATION-UNIFY-RECREATE` (PR #1887 open) |
| Pattern 4: Bespoke `*ConfigRest` not on generic registry | CLEAN — all admin config operations route through `AdminConfigRest` → `ConfigRegistry`. Plugin admin endpoints (Epic, Datacite, AAS, AI, Unhide) all have corresponding `ConfigDescriptor` beans. |
| Pattern 5: New `@Path(Constants.SHEPARD_API + ...)` additions | CLEAN — zero new usages in v2 or plugin packages outside known-deferred spatiotemporal frozen paths. |
| Pattern 7: Per-kind endpoints not under `?kind=` | BY DESIGN — `VideoStreamReferenceV2Rest` (`/v2/data-objects/.../video-stream-references`) and `GitReferenceRest` (`/v2/data-objects/.../git-references`) are documented as retaining only domain-specific operations (binary upload + range-download for video; preview + check-update for git) not expressible via the generic `?kind=` surface. Generic CRUD for both is served via `/v2/references?kind=video` and `/v2/references?kind=git`. |

---

## Summary

- **7 findings total**
- Smallest dispatchable: `APISIMP-WIKIWRITE-LJERID` (XS) — one field + one setter call
- Most impactful: `APISIMP-TYPED-PREDECESSOR-NUMERIC-ID` (S) — numeric id join coupling between v1 and v2 surfaces
- Blocked: `APISIMP-TSCHANNEL-CONTAINER-ID` companion `containerAppId` field — gated on TS-ID migration (TS-IDb/c)
