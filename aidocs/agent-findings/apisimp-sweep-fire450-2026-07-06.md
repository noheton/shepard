---
stage: fragment
last-stage-change: 2026-07-06
---

# APISIMP sweep — fire-450 — 2026-07-06

Triggered because no named APISIMP row was dispatchable this fire:
`APISIMP-SNAP-PINNED-IN-MEMORY-PAGING` is in PR #2368 (blocked on spurious CodeQL
check); `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE` blocked on TS-IDb/c migration.

Scope: full scan of `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`
annotations for residual sprawl not already tracked.

Previously-tracked findings not re-filed here: F3-1/F3-2 (numeric IDs — tracked as
APISIMP-TSCHANNEL-CONTAINER-ID-WIRE, APISIMP-TSCHANNEL-INT-ID-DEPRECATE ✅,
APISIMP-NUMERIC-ID-BATCH-2 ⛔); F4-1 (DO list Content-Range — tracked as
APISIMP-DO-LIST-CONTENT-RANGE ✅); F4-3 (provenance cursor — APISIMP-PROVENANCE-CURSOR-UNDOCUMENTED ✅);
F4-4 (notification transport envelope — APISIMP-NOTIF-TRANSPORT-LIST-ENVELOPE ✅);
F1-4 (CrossDoBulkDataRest path — APISIMP-CROSS-TS-BULK-PATH ✅).

---

## F1 — Per-kind endpoints not unified under `?kind=`

### F1-1 — `FileBundleReferenceRest` not converged to `/v2/references?kind=file-bundle` **[NEW → APISIMP-BUNDLE-REF-KIND-UNIFY]**

- File: `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java`
- Class `@Path` puts CRUD under `/v2/bundles/...` — a separate namespace from the
  unified `/v2/references?kind=*` surface (contrast: VideoStreamReference ✅,
  SpatialDataReference ✅, URIReference ✅, StructuredDataReference ✅).
  Source comment explicitly labels it "NOT yet converged."
  Groups sub-resource (`/v2/bundles/{appId}/groups`) would nest as
  `/v2/references/{appId}/groups`.
- Size: **L** (groups sub-resource has its own paging + permission surface; requires
  frontend migration of FileBundleReferenceApi callers).

### F1-2 — `FileContainerStatsRest` outside kind dispatcher **[NEW → APISIMP-FILE-CONTAINER-STATS-UNIFY]**

- File: `backend/src/main/java/de/dlr/shepard/v2/filecontainer/resources/FileContainerStatsRest.java` line 39
- Single-method class at `/v2/file-containers/{appId}/stats`, self-labeled V2-EXCEPTION.
  `ContainersV2Rest` kind dispatcher handles all other per-container ops; `stats`
  should be `GET /v2/containers/{appId}/stats` with kind routing.
- Size: **S**

### F1-3 — `StructuredDataContainerStatsRest` outside kind dispatcher **[NEW → APISIMP-STRUCT-CONTAINER-STATS-UNIFY]**

- File: `backend/src/main/java/de/dlr/shepard/v2/structureddatacontainer/resources/StructuredDataContainerStatsRest.java` line 39
- Identical pattern to F1-2 — single stats GET self-labeled V2-EXCEPTION.
- Size: **S**

### F1-5 — `GitReferenceRest` per-kind per-DO path not yet unified **[NEW → APISIMP-GIT-REF-KIND-UNIFY]**

- File: `plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java`
- CRUD lives at `/v2/data-objects/{dataObjectAppId}/git-references` — old per-kind
  per-DO pattern. Sibling `GitReferenceActionsRest` already uses `/v2/references/{appId}/preview`
  and `/v2/references/{appId}/check-update` — the migration direction is established.
  `VideoStreamReferenceKindHandler` (plugins/video) is the migration template.
- Size: **M**

---

## F2 — Bespoke admin config REST outside registry pattern

### F2-1 — DataciteAdminRest / EpicAdminRest credential sub-resource diverges from ConfigDescriptor **[NEW → APISIMP-MINTER-CRED-CONFIG-UNIFY]**

- Files:
  - `plugins/minter-datacite/.../DataciteAdminRest.java` — `@Path("/v2/admin/minters/datacite")`
  - `plugins/minter-epic/.../EpicAdminRest.java` — `@Path("/v2/admin/minters/epic")`
- Both minters correctly wire a `ConfigDescriptor` into the generic
  `PATCH /v2/admin/config/minter-{type}` surface AND also expose a separate
  `GET/PUT /credential` sub-resource at the bespoke path. Operator must discover
  two admin paths per minter. Fix: include credential fields in the existing
  `ConfigDescriptor` payload under a write-only masked representation (`****` on read).
  `POST /test-connection` stays as a standalone action — legitimately not config CRUD.
- Size: **S**

---

## F4 — Inconsistent pagination params or response envelopes

### F4-2 — `UserGroupV2Rest` — two wire shapes on the same URL depending on `?q` **[NEW → APISIMP-USER-GROUP-LIST-DUAL-SHAPE]**

- File: `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java` lines 110–122
- `GET /v2/user-groups` without `?q` returns `PagedResponseIO<UserGroupV2IO>`.
  `GET /v2/user-groups?q=<text>` returns a bare `List<UserGroupV2IO>`.
  Same URL, two wire shapes — client must branch its deserializer.
  Fix: always return `PagedResponseIO`; when `?q` is set, use
  `total=items.size(), page=0, pageSize=items.size()`. Alt: promote search to
  `GET /v2/user-groups/search?q=`.
- Size: **S**

---

## F5 — Response fields no real caller reads

### F5-1 — `AnnotationIO` — four legacy alias fields double every annotation payload **[NEW → APISIMP-ANNOTATION-ALIAS-FIELDS]**

- File: `backend/src/main/java/de/dlr/shepard/v2/annotations/io/AnnotationIO.java` lines 88–98
- Every annotation response serializes both v6 canonical fields (`predicateIri`,
  `predicateLabel`, `objectLiteral`, `objectIri`) AND four legacy aliases
  (`propertyName`, `propertyIri`, `valueName`, `valueIri`). Aliases are always
  non-null (set lines 137–140). On a collection with thousands of annotations this
  roughly doubles payload size. The 11 `useShepardApi(AnnotationApi)` call sites
  (per V2-SWEEP-002-2 tracker) still read the alias names. Fix: mark aliases
  `@Deprecated + @Schema(deprecated=true)` and schedule removal after confirming
  all callers have migrated to v6 field names.
- Size: **S**

---

## F6 — Endpoints superseded / tombstoned

### F6-1 — `VideoStreamReferenceV2Rest` 410 tombstone class can be deleted **[NEW → APISIMP-VIDEO-TOMBSTONE-DELETE]**

- File: `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2Rest.java` lines 70, 103
- APISIMP-VIDEO-STREAMREF-PATH (✅ fire-93) migrated all CRUD to the unified
  `/v2/references?kind=video` surface and replaced endpoints with 410 stubs.
  Both stubs confirmed no active callers (all frontends migrated). Class is now dead
  weight in the API surface and OpenAPI doc. Fix: verify zero callers via grep, then
  delete the class entirely.
- Size: **S**

---

## F8 — N+1 calling patterns

### F8-1 — `CrossDoBulkDataRest` — 3–4 serial DB round-trips per DataObject **[NEW → APISIMP-CROSS-DO-BULK-N-PLUS-ONE]**

- File: `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkDataRest.java` lines 132–181
- For each of up to 100 DataObject appIds, the handler serially calls:
  (1) `permissionsService.isAccessAllowedForDataObjectAppId` (Neo4j),
  (2) `dataObjectDAO.findByAppId` (Neo4j),
  (3) `crossDoChannelResolver.resolveChannelsByPredicate` (Neo4j + Postgres),
  (4) LTTB query per resolved channel (TimescaleDB).
  100 DOs → up to 400 serial DB round-trips. `PermissionsService.filterAllowedForUser(ids, ...)`
  already exists and is used by `SqlTimeseriesRest`. Fix: batch steps 1–2 before the
  per-DO resolution loop.
- Size: **M**

### F8-2 — `CollectionCrossTimelineRest` — serial Neo4j permission loop before batched aggregation **[NEW → APISIMP-CROSS-TIMELINE-PERM-BATCH]**

- File: `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionCrossTimelineRest.java` lines 154–163
- Before the correctly-batched `timelineDAO.aggregateMulti(collectionAppIds)`, each
  collection appId is individually `entityIdResolver.resolveLong` + permission-checked
  in serial — up to `MAX_COLLECTIONS` × 2 Neo4j calls. Fix: replace the serial loop
  with a single `permissionsService.filterAllowedForUser(allCollectionLongIds, ...)`.
  APISIMP-CROSS-TIMELINE-UNCAPPED-COLLECTIONS (✅ fire-377) added the size cap;
  this finding is the orthogonal serial-loop inefficiency on the still-capped path.
- Size: **S**

---

## Summary of new rows filed

| Row ID | Category | Size | File |
|---|---|---|---|
| APISIMP-BUNDLE-REF-KIND-UNIFY | Per-kind path | L | `FileBundleReferenceRest.java` |
| APISIMP-FILE-CONTAINER-STATS-UNIFY | Per-kind path | S | `FileContainerStatsRest.java` |
| APISIMP-STRUCT-CONTAINER-STATS-UNIFY | Per-kind path | S | `StructuredDataContainerStatsRest.java` |
| APISIMP-GIT-REF-KIND-UNIFY | Per-kind path | M | `GitReferenceRest.java` (plugin) |
| APISIMP-MINTER-CRED-CONFIG-UNIFY | Bespoke admin config | S | `DataciteAdminRest.java`, `EpicAdminRest.java` |
| APISIMP-USER-GROUP-LIST-DUAL-SHAPE | Pagination envelope | S | `UserGroupV2Rest.java` |
| APISIMP-ANNOTATION-ALIAS-FIELDS | Dead/legacy fields | S | `AnnotationIO.java` |
| APISIMP-VIDEO-TOMBSTONE-DELETE | Tombstone cleanup | S | `VideoStreamReferenceV2Rest.java` (plugin) |
| APISIMP-CROSS-DO-BULK-N-PLUS-ONE | N+1 pattern | M | `CrossDoBulkDataRest.java` |
| APISIMP-CROSS-TIMELINE-PERM-BATCH | N+1 pattern | S | `CollectionCrossTimelineRest.java` |
