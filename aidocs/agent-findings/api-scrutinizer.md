# API Scrutinizer — Shepard /v2/ Findings

**Audited:** 2026-05-21  
**Surface:** `/v2/` REST resources under `backend/src/main/java/de/dlr/shepard/v2/`  
**Critic stance:** the best API is the smallest API that solves the problem.

---

## What I Found

Sixty-plus REST files across 20+ sub-packages. The surface is well-structured by primitive (collection, dataobject, timeseries, file, bundle, snapshot, provenance, importer, template, admin, notification, labjournal, semantic, sql, watch). The code is readable, the auth pattern is consistent, and the merge-patch (RFC 7396) shape is applied uniformly to the mutation endpoints. Those are real positives.

The problems fall into four categories:

1. **Legacy ID leakage everywhere** — `id`, `collectionId`, `dataObjectId`, `referenceIds`, `childrenIds`, `successorIds`, `predecessorIds`, `incomingIds`, `timeseriesContainerId`, `defaultFileContainerId` — all Neo4j OGM longs — bleed through into /v2/ responses even though the point of /v2/ is appId-keyed.

2. **referenceIds misidentification** — the single worst live bug. `DataObjectIO.referenceIds` contains IDs of `BasicReference` nodes (TimeseriesReference, FileReference, etc.), not IDs of DataObjects. Any caller treating them as `dataObjectId` values gets 404s. This happened in production with the MCP server.

3. **5-tuple channel addressing** — live on five v1 endpoints and partially dragged into v2 (live-window, anomaly detection fallback). Design doc `aidocs/platform/87-timeseries-appid-migration.md` exists and is well-written; the migration is not yet in-flight.

4. **Pagination inconsistency** — `page`/`size` vs `limit` used interchangeably with no shared envelope.

---

## Keep / Change / Remove / Merge Table

### Collections and DataObjects

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /v2/collections` | CHANGE | MAJOR | Response includes `id` (long), `dataObjectIds[]` (long[]), `incomingIds[]` (long[]). These are useless to a /v2/ caller who can't do anything with them. |
| `GET /v2/collections/{appId}` | CHANGE | MAJOR | Same as above. `dataObjectIds` long array forces a v2 caller to switch to v1 surface to fetch DataObjects by those IDs. The fix is `/v2/collections/{appId}/data-objects` which already exists — but `CollectionIO` still advertises the long array. |
| `GET /v2/collections/{appId}/data-objects` | CHANGE | CRITICAL | `DataObjectIO.referenceIds` — see the referenceIds Problem section. Also emits `collectionId` (long), `successorIds[]` (long[]), `predecessorIds[]` (long[]), `childrenIds[]` (long[]), `parentId` (Long), `incomingIds[]` (long[]) — none of these are usable from /v2/. |
| `GET /v2/collections/{appId}/data-objects/{doAppId}` | CHANGE | CRITICAL | Same shape issues. `referenceIds` is actively harmful. `timeseriesContainerId` inside any referenced `TimeseriesReferenceIO` is also a raw long. |
| `GET /v2/collections/{appId}` `CollectionIO.defaultFileContainerId` | CHANGE | MAJOR | Exposes raw Neo4j long ID for the default FileContainer. No /v2/ endpoint takes a `Long containerId` for file containers — callers are stranded. |

### Timeseries — 5-tuple surface (v1, frozen but still painful)

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /shepard/api/timeseriesContainers/{id}/payload` | KEEP (v1 frozen) | CRITICAL | Requires all 5 of `measurement`, `device`, `location`, `symbolicName`, `field` as required query params. This is the most-used timeseries read path. |
| `POST /shepard/api/timeseriesContainers/{id}/payload` (NDJSON) | KEEP (v1 frozen) | CRITICAL | Same 5-tuple required on query string for streaming ingest. |
| `GET /shepard/api/timeseriesContainers/{id}/export` | KEEP (v1 frozen) | CRITICAL | Same 5-tuple on every export call. |
| `GET /v2/timeseries-containers/{appId}/channels/live-window` | CHANGE | MAJOR | Accepts the 5-tuple as optional query params. This is a /v2/ endpoint; it should accept `timeseriesAppId` directly once TS-IDa/IDb ships. The partial-match fallback (filter until one channel matches) invites ambiguous-channel 400s. |
| `POST /v2/timeseries-references/{refAppId}/detect-anomalies` | CHANGE | MINOR | Same 5-tuple filter fields in the request body — but they're optional and auto-select works for single-channel refs. Lower priority than live-window. |
| `GET /shepard/api/timeseriesContainers/{id}/available` | REMOVE | MINOR | `@Deprecated(forRemoval=true)`. Has been deprecated; callers should use `/timeseries` sibling. |

### Timeseries — Container stats and chart view

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /v2/timeseries-containers/{containerId}/stats` | CHANGE | MAJOR | `{containerId}` is a Neo4j long, not an appId. Every other /v2/ timeseries-container endpoint uses `{containerAppId}`. Inconsistent within the same resource group. |
| `GET /v2/timeseries-containers/{containerId}/linked-data-objects` | CHANGE | MAJOR | Same — raw long `containerId` path param. |
| `DELETE /v2/timeseries-containers/{containerId}` | CHANGE | MAJOR | Same. |
| `GET /v2/file-containers/{containerId}/...` | CHANGE | MAJOR | All file-container and structured-data-container /v2/ endpoints still use Long `containerId`, not appId. |
| `GET /v2/timeseries-containers/{appId}/chart-view` | KEEP | — | Uses `containerAppId` (appId) correctly. The outliers above should match this. |

### Linked-data-objects vs referenced-containers

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /v2/timeseries-containers/{id}/linked-data-objects` | KEEP | — | Good traversal endpoint. Fix the Long ID issue above. |
| `GET /v2/file-containers/{id}/linked-data-objects` | KEEP | — | Same pattern, same fix. |
| `GET /v2/structured-data-containers/{id}/linked-data-objects` | KEEP | — | Same. |
| `GET /v2/collections/{appId}/referenced-containers` | KEEP | — | Inverse traversal. Good, but the two directions are asymmetric: one returns DataObjectIO shape with long id arrays, the other returns ContainerSummaryIO with appId. |

### Import

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `POST /v2/import/validate` | CHANGE | MAJOR | `collectionAppId` is in the request body. The sibling `GET /v2/import/context` takes `collectionAppId` as a **query parameter**. Two endpoints in the same resource class, inconsistent parameter placement. |
| `GET /v2/import/context` | CHANGE | MINOR | `collectionAppId` as required query param is undiscoverable — no validator-enforced `@NotBlank` at the JAX-RS layer; the check is manual and returns a hand-rolled 400 string. |
| `POST /v2/import/jobs` | MISSING | CRITICAL | Referenced in javadoc and OpenAPI descriptions of validate and getPlan. Not implemented. Callers can validate and see a commitId but cannot execute. The validate endpoint is functionally dead without this. |

### Watches vs Watchers — naming collision

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /v2/collections/{appId}/watches` | CHANGE | MAJOR | This is the **user-watches-Collection** endpoint (CW1). Lists human watchers. |
| `GET /v2/collections/{appId}/watched-containers` | CHANGE | MAJOR | This is the **Collection-watches-Container** endpoint (WATCH1). Completely different concept. |
| Both | CHANGE | MAJOR | The names are too similar for two orthogonal concepts. A caller reading the OpenAPI listing will confuse them. Rename one: e.g. `/v2/collections/{appId}/subscribers` (CW1) vs `/v2/collections/{appId}/pinned-containers` (WATCH1). |

### Provenance

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `GET /v2/provenance/activities` | CHANGE | MAJOR | Three overloads of the same path (`application/json`, `application/prov+json`, `application/ld+json`) are declared as **seven separate Java methods** (activities ×3, entity ×3, count ×2). Each duplicates the same auth gate, agent-filter normalization, and `isAdmin` branching verbatim. JAX-RS content negotiation could collapse this to one method per path; instead the surface has tripled code paths that must be kept in sync. The OpenAPI spec emits three separate operation objects for the same path, confusing codegen. |
| `GET /v2/provenance/count` | MERGE | MINOR | A second count endpoint (`/count` as JSON-LD) adds a fourth content-negotiation variant to an already crowded resource. Low caller benefit. |
| `GET /v2/provenance/activities` pagination | CHANGE | MAJOR | Uses `?limit` (default 100, max 1000). All other list endpoints use `?page`+`?size`. Same resource (ProvenanceRest) has both: `listActivities` uses `limit`, while `stats` and `count` are unbounded. Callers must switch mental models between endpoint groups. |

### Admin

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `PATCH /v2/admin/features/{name}` | CHANGE | MINOR | Changes are not persisted across restarts (documented inline). That is fine, but the endpoint does not return a `Cache-Control: no-store` header — monitoring scripts that cache the response will see stale toggle state. |
| `GET /v2/admin/metrics` | KEEP | — | Clean, admin-only, single purpose. |

### OpenAPI tag pollution

| Issue | Severity | Finding |
|---|---|---|
| `@Tag(name = "...")` internal codes | MAJOR | 15+ REST classes carry `@Tag` values that embed design-doc task codes: `TS_LIVE1`, `CC1b`, `CC2`, `WATCH1`, `IMP1`, `SA-CONT`, `CW1`, `TS_CHART_VIEW1`, `TS_STATS1`, `FS1c`, `FS1g`, and others. These are version-control breadcrumbs for contributors — they appear verbatim in the generated OpenAPI spec under the `tags` grouping that every consumer and codegen tool sees. A generated SDK will have `TagTsLive1Api`, `TagImp1Api`, etc. as class names. The fix is a one-pass rename to human-readable tag names (`Timeseries live window`, `Import`, `File storage`, etc.) matching the resource's REST concept, not the internal tracking code. |

### File upload

| Endpoint | Verdict | Severity | Finding |
|---|---|---|---|
| `POST /v2/files` | CHANGE | MINOR | `parentDataObjectAppId` is a query parameter on a multipart upload. Callers must encode it in the URL while the file goes in the body. The appId should either be in a body field or in a path segment like `/v2/data-objects/{appId}/files`. The current shape requires URL construction before knowing the content type. |

---

## The referenceIds Problem

### How it manifests

`DataObjectIO.referenceIds` is described in the field-level Javadoc as "legacy long ids of all references — timeseries, file, structured-data — attached to this DataObject." The field is named `referenceIds`.

An AI agent (or any caller) reading a `DataObjectIO` response sees `referenceIds: [331, 335, 337, 1077]` and reasonably assumes these are IDs of related DataObjects, because the field name and the surrounding field (`childrenIds`, `parentId`, `successorIds`, `predecessorIds`) are all about DataObject-to-DataObject relationships.

These are **not** DataObject IDs. They are the Neo4j shepherd IDs of `BasicReference` nodes — `TimeseriesReference`, `FileReference`, `StructuredDataReference`.

> **Clarification:** The task brief described these as "DataObjectReference node IDs." That is approximate and misleading in a different direction. `DataObjectReference` is a *separate* entity: it models the DO-to-DO link (the `predecessor`/`successor` / `incoming` edge). It is exposed as `incomingIds[]` in the same response. `referenceIds` does not contain `DataObjectReference` IDs either — it contains `BasicReference` subtype IDs (the container attachment edges). The practical effect for any caller is the same: you cannot use these IDs to look up DataObjects or containers by passing them to any /v2/ endpoint. But the two are different things, and conflating them in the fix PR would be a mistake. The upstream v1 endpoints that consume them are:

- `GET /shepard/api/collections/{cid}/data-objects/{doid}/timeseriesReferences`
- `GET /shepard/api/collections/{cid}/data-objects/{doid}/fileReferences`
- `GET /shepard/api/collections/{cid}/data-objects/{doid}/structuredDataReferences`

None of these take a bare reference id. The caller must know the collection id, data object id, AND (if they want a specific reference) the reference id — but `referenceIds` gives them the reference id without the context needed to use it.

When the MCP server called `get_data_object(data_object_id=331)` (treating 331 as a DataObject ID), it got a 404 because 331 is a `TimeseriesReference` shepherd ID, not a DataObject shepherd ID.

### The fix

Three changes, in priority order:

1. **Rename the field.** `referenceIds` → `attachedReferenceIds` or, better, `containerReferenceNodeIds` (but even that is misleading because they're not container IDs). The honest name is `referenceNodeIds` to signal "these are reference edge nodes, not the containers or data objects they point at."

2. **Add typed sub-arrays to the v2 response.** The list-item IO (`DataObjectListItemV2IO`) already shows the right instinct: three separate count fields (`timeseriesCount`, `fileCount`, `structuredDataCount`). The single-object response should return three appId arrays: `timeseriesReferenceAppIds[]`, `fileReferenceAppIds[]`, `structuredDataReferenceAppIds[]`. These are actionable — each can be passed directly to the v2 reference endpoints.

3. **Remove the raw long array from the v2 response entirely** (not from v1). The v2 `DataObjectIO` shape should not emit `referenceIds: long[]` at all. v1 callers who need the long IDs can still get them from the v1 endpoints that remain frozen. The v2 single-object endpoint should return appId arrays.

The current situation: `referenceIds` exists in `DataObjectIO` which is shared between v1 (frozen surface) and v2 (new surface). The `DataObjectListItemV2IO` subclass correctly adds count fields without touching the base class. The same pattern applies to the appId arrays: a `DataObjectV2IO` subtype (or projection) should shadow the `referenceIds` field with three properly-typed appId arrays.

---

## The 5-tuple Problem

### Every place it appears

**v1 frozen surface (cannot remove, can only add alternatives):**

| Location | Method | Required? |
|---|---|---|
| `GET /shepard/api/timeseriesContainers/{id}/payload` | GET | All 5 required |
| `POST /shepard/api/timeseriesContainers/{id}/payload` (NDJSON variant) | POST | All 5 required as query params |
| `GET /shepard/api/timeseriesContainers/{id}/export` | GET | All 5 required |
| `GET /shepard/api/timeseriesContainers/{id}/timeseries` | GET | All 5 optional (filter) |

**v2 surface (should be fixed):**

| Location | Method | Notes |
|---|---|---|
| `GET /v2/timeseries-containers/{appId}/channels/live-window` | GET | All 5 optional; filter logic lives in Java code, not DB — loads ALL channels for the container and filters in memory |
| `POST /v2/timeseries-references/{refAppId}/detect-anomalies` body | POST | 5 optional filter fields in request body; auto-selects if single channel |
| `TimeseriesContainerChartViewIO.selectedChannels` | (stored) | Pipe-separated 5-tuple strings stored as the channel key format |

**Frontend (outside this audit's scope but cited in 87-timeseries-appid-migration.md):**

`ShowTimeseriesReferenceDialog.vue`, `ChannelPreviewChart.vue`, `useFetchTimeseries.ts`, `useFetchTimeseriesAnnotations.ts`, `useFetchChannelPreview.ts` — all pass the full 5-tuple on every call.

### The cost

- Every channel operation requires constructing the same 5-tuple correctly from multiple sources (the reference metadata, the caller's context, the channel list). One wrong character in any of the five fields produces a silent empty result, not an error.
- The live-window endpoint loads ALL channels for the container into JVM memory and filters in Java. For a container with 10,000 channels, that is a full table scan per request.
- The `selectedChannels` array in `TimeseriesContainerChartViewIO` stores pipe-separated 5-tuple strings. Any channel rename propagates silently — the stored selection no longer matches the renamed channel.
- The design doc (87) recommends: ship TS-IDa (mint UUIDs on existing nodes) and TS-IDb (expose appId in response) first. Neither is in-flight. Those two phases are zero-risk and unblock everything else.

---

## Missing Operations

| What callers need | What they must do today | Severity |
|---|---|---|
| Execute a validated import (the actual import after `POST /v2/import/validate`) | Nothing — `POST /v2/import/jobs` does not exist. The validate endpoint produces a commitId that expires in 24h but can never be redeemed. | CRITICAL |
| Get a DataObject by appId without knowing the Collection appId | Cannot do it. `GET /v2/collections/{cA}/data-objects/{dA}` requires both. There is no `GET /v2/data-objects/{appId}` flat endpoint. Callers who have a DataObject appId (from a notification, a provenance trail, a template instantiation response) must first discover which Collection it belongs to. | MAJOR |
| List all DataObjects across all Collections (search) | Cannot do it directly. Must paginate through all Collections, then paginate through each Collection's DataObjects. There is no global DataObject search endpoint. | MAJOR |
| Get a TimeseriesReference by its appId directly | Only PATCH is available at `/v2/timeseries-references/{appId}`. There is no GET at that path. To read a reference, callers must go through the v1 surface (`/shepard/api/collections/{cid}/data-objects/{doid}/timeseriesReferences`), which requires knowing both collection and dataobject long IDs. | MAJOR |
| Add a DataObject predecessor/successor relationship | Cannot do it from /v2/. `predecessorIds` and `successorIds` appear in the response shape but there is no PATCH or PUT to set them. Must use v1 surface. | MAJOR |
| Bulk-delete DataObjects | No batch delete. Must issue N DELETE calls. For large collections (1000+ DataObjects), this is expensive and fragile. | MINOR |
| Get collection or DataObject permissions on /v2/ | The comment in CollectionV2Rest says "Permissions / roles — covered by a dedicated `/v2/permissions/{collectionAppId}` resource (Phase C)." Phase C is not shipped. | MINOR |

---

## Pagination Consistency

Two completely different pagination shapes coexist in `/v2/`:

**Shape A — page + size (majority):**
```
?page=0&size=50
```
Used by: `CollectionV2Rest`, `DataObjectV2Rest`, `CollectionSnapshotRest`, `NotificationRest`, `ShepardTemplateRest`.

Default: page=0, size=50. Cap: size capped at 200 server-side (enforced in code, not validated by `@Max`).

**Shape B — limit (minority):**
```
?limit=100
```
Used by: `ProvenanceRest` (listActivities, listEntityActivities, count), `TimeseriesLiveWindowRest` uses `windowSeconds` (a different concept entirely).

There is no shared `page` field, no `totalCount` field, no `nextPageToken`. Callers cannot know when they've reached the end of a page without counting the returned elements and comparing to `size`. The notifications endpoint caps at 200 with no pagination at all.

**What it should be:** pick one shape and enforce it. The `page`+`size` shape is the majority; provenance should adopt it. All list responses should include `totalCount` (or at minimum a `hasMore: boolean`) so clients know whether to fetch the next page.

---

## Error Shape Consistency

Four distinct error shapes are in use across /v2/:

**1. RFC 7807 ProblemJson** (`application/problem+json`):
Used by: `InstanceRorConfigRest`, `SqlTimeseriesConfigRest`, `PluginsAdminRest`, `SemanticAdminRest`, `ProvenanceRest` (406 only).

Shape: `{"type": "uri", "title": "string", "status": int, "detail": "string"}`.

**2. ApiError** (plain JSON, `application/json`):
Used by: `AdminFeaturesRest`, `InstanceAdminRest`, `NotificationRest`.

Shape: `{"status": int, "error": "string", "message": "string"}`.

**3. Plain string** (`text/plain` or untyped `application/json`):
Used by: `AnomalyDetectionRest`, `ImportV2Rest`, `CollectionSnapshotRest`, `TimeseriesAnnotationRest`, `TemplateInstantiationRest`, `FileBundleReferenceRest`, `TimeseriesLiveWindowRest`, and many others.

Shape: raw string in the response body. Not valid JSON when the Content-Type is `application/json`.

**4. Inline JSON string** (hand-rolled JSON in a string):
Used by: `AdminUserOrcidRest` (`"{\"error\":\"Invalid ORCID format...\"}`"), `AdminUserGitCredentialRest` (`"{\"error\":\"host is required\"}"`).

Shape: JSON-in-a-string. Works by accident when Jackson doesn't re-serialize.

The majority of the surface (plain string errors) is the worst offender: a caller parsing the response as JSON gets a parse error on the error body precisely when something went wrong — the worst moment to add parsing complexity.

**What it should be:** one shape, everywhere. `ProblemJson` (RFC 7807) is already in the codebase. Make it the default by registering a `ExceptionMapper<WebApplicationException>` that wraps unhandled exceptions in a ProblemJson body. The three remaining hand-rolled shapes become dead code.

---

## Top 3 Changes for Developer Experience

### 1. Fix referenceIds (CRITICAL, do it this sprint)

Rename `DataObjectIO.referenceIds` to `referenceNodeIds` at minimum. In the /v2/ single-object and list responses, replace the long array with three appId arrays: `timeseriesReferenceAppIds`, `fileReferenceAppIds`, `structuredDataReferenceAppIds`. This is the only live bug causing active 404s in the MCP server and any other agent that reads DataObjects. The shape is frozen in v1; only the v2 projection needs to change, and `DataObjectListItemV2IO` already shows the correct precedent for how to do that without touching the base class.

### 2. Ship TS-IDa + TS-IDb (MAJOR, one sprint, zero risk)

Mint UUIDs on all existing `Timeseries` nodes (the Neo4j migration in 87-timeseries-appid-migration.md is already written) and expose `appId` in the channel list/get responses. This is additive and safe. It unblocks the live-window endpoint fix, the channel-chart-view stored selection format fix, and the anomaly detection cleanup. The design doc already exists. The migration script already exists in the doc. Nothing is blocking this except prioritization.

### 3. Implement POST /v2/import/jobs (CRITICAL)

The import surface is currently a dead end. The validate endpoint produces a commitId that expires but cannot be used. The importer plugin design (aidocs/importer_plugin) and the seed showcase depend on this. Every MCP server request that wants to import data has no executable path. Either implement the jobs endpoint or remove the commitId from the validate response and the plan retrieval endpoint — don't advertise a contract you can't fulfill.

---

## The 1 Endpoint That Needs a Design Doc Before Anyone Touches It

`DELETE /v2/timeseries-containers/{containerId}` (and the same for file-containers and structured-data-containers).

These endpoints perform "safe delete" — they refuse to delete if active references exist and return a `SafeDeleteConflict` response listing the linked DataObjects. That is a sound design. But:

- The path parameter is a Neo4j Long (`containerId`), not an appId. Changing to appId is a breaking change on the /v2/ surface.
- The safe-delete conflict response embeds full `DataObjectIO` objects, including all their long ID arrays, `referenceIds`, etc.
- The delete cascades are undocumented (what gets deleted? what is orphaned?).
- There is no concept of "force delete" — callers who have permission to delete but have orphaned references are permanently blocked.
- Three copies exist (timeseries, file, structured-data) with identical patterns but no shared superclass, so fixing one requires fixing three.

This needs a design doc that covers: appId vs Long migration path, cascade semantics, force-delete option, and the orphan notification hook (storage management design).

---

## What Surprised Me

**The duplicate count fields.** `DataObjectIO` already has `timeseriesReferenceCount`, `fileBundleCount`, `structuredDataReferenceCount`, `videoStreamReferenceCount` (four integer fields, computed from the loaded reference list). `DataObjectListItemV2IO` extends `DataObjectIO` and adds `timeseriesCount`, `fileCount`, `structuredDataCount` (three long fields, computed from a separate DB query). When the list endpoint responds, callers receive SEVEN count-like fields on every item: four from the base class and three from the subclass. The two sets use different names and different types (int vs long) for the same concepts. Neither set is wrong in isolation; together they are a trap.

**The import validate endpoint has no execute.** The entire IMP1 surface exists to produce a commitId, and the commitId expires in 24 hours. The execute leg (`POST /v2/import/jobs`) is referenced in three separate places in the codebase but is not implemented. The validate endpoint is the most elaborate dead-end in the API.

**`/v2/collections/{appId}/watches` vs `/v2/collections/{appId}/watched-containers` are orthogonal concepts with similar names.** One is "which users are watching this collection for notifications." The other is "which containers has this collection pinned for its live-data panel." They sit one URL segment apart and have nothing in common except the surrounding resource. A caller browsing the OpenAPI spec will require careful reading to distinguish them.

**The live-window endpoint loads all channels for a container in JVM memory.** `TimeseriesLiveWindowRest` calls `timeseriesRepository.list("containerId", containerId)` — a full table scan — and then filters in Java. For a container with thousands of channels this is quadratic in the worst case (one full scan per live-window poll). The correct fix is a parameterized DB query and arrives naturally once TS-IDb ships (`findByAppId(timeseriesAppId)` — one index lookup).

**The surface is fragmented into N REST classes per primitive.** The timeseries-container concept alone is split across at least five classes: `TimeseriesContainerStatsRest`, `TimeseriesContainerChartViewRest`, `TimeseriesLiveWindowRest`, `LinkedDataObjectsRest` (timeseries variant), and `TimeseriesContainerV2Rest`. Similarly: 4 snapshot files, 4 template files, 3 lab-journal files. Each feature addition minted a new class rather than extending an existing one. This is not wrong on its own — small focused classes are fine — but it means the inconsistencies (Long vs appId, `?limit` vs `?page`+`?size`, plain string errors vs ProblemJson) propagated into each new file independently, because there was no base class enforcing the shared contract. The solution is shared parent classes or JAX-RS interceptors for the cross-cutting concerns (pagination shape, error shape, ID type), not necessarily merging the files.
