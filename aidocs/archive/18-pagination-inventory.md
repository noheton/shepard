# Pagination — Inventory & Sized Rollout Plan

**Scope.** A research artefact closing out backlog item **L6** ("Output
Control / Pagination" — `aidocs/input/input_raw.md:689-691`). The forward-
looking unified-search proposal (`13-search-improvements.md` §2.6) calls
for cursor pagination on a future `POST /search/v2`; this document asks
the prior question — *what is the state of pagination across every
list-returning endpoint we ship today?* — and turns that inventory into
sized work items.

This is a sister document to `12-timescaledb-performance-analysis.md`
(read-path streaming) and `13-search-improvements.md` (search surface).
Pagination is the third leg of the heap-blow-up tripod: even after the
streaming work in perf §11.A and a unified search in §2, every
non-search list endpoint (Collections, DataObjects, FileReferences,
DataObjectReferences, …) materialises an unbounded `List<…>` into the
JSON serialiser. Some of those are bounded by entity semantics (a few
hundred references per DataObject). Some are not (every Collection on a
big shepard instance, every File in a busy FileContainer).

**Status legend.** Same convention as the perf doc:
🟥 HIGH / 🟧 MED / 🟨 LOW / 🟪 ARCH.

---

## 1. The current pagination convention

Two helper classes, one query-param triple:

- `QueryParamHelper.java` (`backend/src/main/java/de/dlr/shepard/common/util/QueryParamHelper.java:13-77`)
  — fluent bag of optional knobs: `name`, `pagination`,
  `parentId`, `predecessorId`, `successorId`, `orderByAttribute`,
  `orderDesc`. Built at the resource boundary and threaded into the
  service / DAO layer.
- `PaginationHelper.java` (`PaginationHelper.java:1-20`) — `(page,
  size)` with a derived `getOffset() = page * size`. Pure offset/limit;
  no cursor, no sort-key bound.
- Constants: `Constants.QP_PAGE = "page"`, `Constants.QP_SIZE = "size"`
  (`Constants.java`). No `offset`, `limit`, or `cursor` constant exists.

The convention is **stable and consistent** wherever it is applied:
every endpoint that opts into it uses the same `?page=N&size=M`
triple, the same `orderBy / orderDesc` companion, and the same
`QueryParamHelper` chain at the call site (e.g. `TimeseriesRest.java:101-115`,
`FileRest.java:82-99`, `CollectionRest.java:75-93`, `StructuredDataRest.java:76-94`,
`SpatialDataPointRest.java:80-95`, `SemanticRepositoryRest.java:59-77`,
`UserGroupRest.java:140-155`, `DataObjectRest.java:73-105`,
`BasicReferenceRest.java:71-91`).

**Friction points already visible:**

- The control flow `if (page != null && size != null) params =
  params.withPageAndSize(...)` is duplicated verbatim ~10 times across
  resources. `input_raw.md:1462` flags this as the
  "duplicated logic" hot spot; the recommendation there is to move it
  into a `@QueryParamsBean`.
- Pagination is **offset/limit** today. `13-search-improvements.md` §2.6
  argues for cursor pagination on the v2 search endpoint and calls out
  "rows may be skipped or duplicated when results shift" under
  concurrent writes. That argument applies equally to the list
  endpoints inventoried below — but a wholesale cursor migration would
  be a breaking change for every generated client.
- `SpatialDataPointRest` already diverges: `?limit=…&skip=…`
  (`SpatialDataPointRest.java:245-246`). One more dialect.

---

## 2. Inventory

Methodology: enumerated every `*Rest.java` under
`backend/src/main/java/de/dlr/shepard/.../endpoints/`; for each method
with `@GET` returning a collection (array `@Schema`, `List<…>`,
`Stream<…>`, or a payload type whose body is a list), recorded:

- HTTP path (resolved from class-level + method-level `@Path`).
- Whether `?page` / `?size` (or any pagination knob) is currently
  bound.
- Worst-case row count (educated guess from entity semantics).
- Frontend / client usage (sampled from `frontend/composables/`,
  `frontend/components/`, `frontend/pages/`).

### 2.1 Summary table

Risk = row-count × frontend usage. Priority = inverse of (paginated?).

| # | Method + Path | Return | Paginated today? | Worst-case rows | FE/client usage | Risk | Priority |
|---|---|---|---|---|---|---|---|
| 1 | `GET /collections` | `List<CollectionIO>` | ✅ `page,size,name,orderBy,orderDesc` | medium (10²–10⁴) | heavy (collections list page, many composables) | 🟧 | done |
| 2 | `GET /collections/{cid}` (returns nested `List<DataObject>` inside) | `CollectionIO` w/ embedded list | ❌ | medium | heavy | 🟧 | high (eager-fetch concern, see §5.6) |
| 3 | `GET /collections/{cid}/dataObjects` | `List<DataObjectIO>` | ✅ `page,size,name,parentId,predecessorId,successorId,orderBy` | high (10³–10⁶ in a flat collection) | heavy | 🟧 | done |
| 4 | `GET /collections/{cid}/versions` | `List<VersionIO>` | ❌ | low (10⁰–10²) | toggle-gated FE | 🟨 | low |
| 5 | `GET /collections/{cid}/dataObjects/{did}/references` (BasicReference) | `List<BasicReferenceIO>` | ✅ `page,size,name,orderBy` | medium (10²–10⁴) | medium | 🟧 | done |
| 6 | `GET /collections/{cid}/dataObjects/{did}/fileReferences` | `List<FileReferenceIO>` | ❌ | medium (10²–10³) | heavy (data-references panes) | 🟧 | **high** |
| 7 | `GET /collections/{cid}/dataObjects/{did}/uriReferences` | `List<URIReferenceIO>` | ❌ | low–medium | medium | 🟨 | medium |
| 8 | `GET /collections/{cid}/dataObjects/{did}/dataObjectReferences` | `List<DataObjectReferenceIO>` | ❌ | medium | heavy | 🟧 | **high** |
| 9 | `GET /collections/{cid}/dataObjects/{did}/collectionReferences` | `List<CollectionReferenceIO>` | ❌ | low–medium | medium | 🟨 | medium |
| 10 | `GET /collections/{cid}/dataObjects/{did}/timeseriesReferences` | `List<TimeseriesReferenceIO>` | ❌ | medium | heavy | 🟧 | **high** |
| 11 | `GET /collections/{cid}/dataObjects/{did}/structuredDataReferences` | `List<StructuredDataReferenceIO>` | ❌ | medium | medium | 🟧 | medium |
| 12 | `GET /collections/{cid}/dataObjects/{did}/spatialDataReferences` | `List<SpatialDataReferenceIO>` | ❌ | low | low (feature-toggled) | 🟨 | low |
| 13 | `GET …/structuredDataReferences/{rid}/payload` | `List<StructuredDataPayload>` | ❌ | medium (10²–10⁴) | medium | 🟧 | medium |
| 14 | `GET …/spatialDataReferences/{rid}/payload` | `List<SpatialDataPointIO>` | ❌ | high (10³–10⁶) | low | 🟧 | medium (but defer with feature) |
| 15 | `GET /collections/{cid}/dataObjects/{did}/.../semanticAnnotations` (Collection, DataObject, BasicRef variants) | `List<SemanticAnnotationIO>` | ❌ | low–medium (10⁰–10²) | medium | 🟨 | low |
| 16 | `GET /timeseriesContainers/{cid}/timeseries/{tsid}/semanticAnnotations` | `List<SemanticAnnotationIO>` | ❌ | low (10⁰–10²) | medium | 🟨 | low |
| 17 | `GET /timeseriesContainers` | `List<TimeseriesContainerIO>` | ✅ | medium | heavy | 🟧 | done |
| 18 | `GET /timeseriesContainers/{cid}/timeseries` | `List<TimeseriesIO>` | ❌ (but `?measurement,device,location,…` filters) | high (10³–10⁵ per container) | heavy | 🟥 | **highest** |
| 19 | `GET /timeseriesContainers/{cid}/available` (deprecated) | `List<Timeseries>` | ❌ | high | low (legacy) | 🟨 | leave (deprecated, see §5.4) |
| 20 | `GET /timeseriesContainers/{cid}/payload` | `TimeseriesWithDataPoints` (list-shaped) | ❌ | very high (10⁴–10⁸) | heavy | 🟪 | leave — handled by perf §11.A streaming |
| 21 | `GET /timeseriesContainers/{cid}/export` | binary CSV stream | already streaming | very high | medium | — | leave |
| 22 | `GET /fileContainers` | `List<FileContainerIO>` | ✅ | medium | heavy | 🟧 | done |
| 23 | `GET /fileContainers/{cid}/payload` | `List<ShepardFile>` | ❌ | high (10³–10⁵ files in one container) | heavy | 🟥 | **highest** |
| 24 | `GET /structuredDataContainers` | `List<StructuredDataContainerIO>` | ✅ | medium | heavy | 🟧 | done |
| 25 | `GET /structuredDataContainers/{cid}/payload` | `List<StructuredData>` | ❌ | high (10³–10⁵ docs per container) | heavy | 🟥 | **highest** |
| 26 | `GET /spatialDataContainers` | `List<SpatialDataContainerIO>` | ✅ | low–medium | low (feature-toggled) | 🟨 | done |
| 27 | `GET /spatialDataContainers/{cid}/payload` | `List<SpatialDataPointIO>` | ✅ `?limit&skip` (custom dialect, see §3) | very high (10⁴–10⁷) | low | 🟧 | high (rename to convention) |
| 28 | `GET /semanticRepositories` | `List<SemanticRepositoryIO>` | ✅ | low (10⁰–10²) | medium | 🟨 | done |
| 29 | `GET /userGroups` | `List<UserGroupIO>` | ✅ | low (10⁰–10²) | medium | 🟨 | done |
| 30 | `GET /users/{name}/apikeys` | `List<ApiKeyIO>` | ❌ | low (10⁰–10¹) | low (user-scoped) | 🟨 | low |
| 31 | `GET /users/{name}/subscriptions` | `List<SubscriptionIO>` | ❌ | low (10⁰–10²) | low | 🟨 | low |
| 32 | `GET /labJournalEntries?dataObjectId=…` | `List<LabJournalEntryIO>` | ❌ | low–medium | medium | 🟨 | medium |
| 33 | `POST /search` | `ResponseBody` (mixed) | ❌ | high | heavy | 🟥 | leave — covered by `13-search-improvements.md` §2 (unified v2) |
| 34 | `POST /search/collections` | `CollectionSearchResult` | ✅ | high | heavy | — | leave — see §13 |
| 35 | `POST /search/containers` | `ContainerSearchResult` | ✅ (optional) | medium | heavy | — | leave — see §13 |
| 36 | `POST /search/users` | `UserSearchResult` | ❌ | low (10⁰–10²) | medium | 🟨 | leave — see §13 |
| 37 | `POST /search/usergroups` | `UserGroupSearchResult` | ❌ | low | medium | 🟨 | leave — see §13 |
| 38 | `GET /timeseriesContainers/{cid}/timeseries/{tsid}/.../metrics` | `List<MetricsIO>` | ❌ | low (one per aggregate) | medium | 🟨 | low |

**Headline counts** (38 list-returning endpoints in scope):

- 11 paginated today (29%): items 1, 3, 5, 17, 22, 24, 26, 27, 28, 29 + the
  three search variants 34/35.
- 27 not paginated today (71%) — split as follows:
  - 4 deliberately not (search v1 in §13, deprecated `available`,
    streaming payload §20, CSV export §21).
  - 23 candidates for some sized treatment.

Endpoints out of scope (single-entity GETs, POST/PUT, payload byte
streams) are not counted; the surface is large but uniform.

---

## 3. Convention recommendation

**Recommendation: extend the existing `?page&size&orderBy&orderDesc`
convention to all candidate endpoints. Defer cursor pagination for a
later major-version cut, *or* introduce it only on the unified search
endpoint in `13-search-improvements.md`.**

Reasoning:

1. **Consistency wins.** 11 endpoints already speak the
   offset/limit dialect; clients (frontend composables, generated
   Python/Java/TS) are written against it. A second dialect (cursor)
   in parallel doubles the surface area for two release cycles.
2. **The cursor argument is real but costly.** §2.6 of the search doc
   is correct that offset pagination skips/duplicates rows under
   concurrent writes. But:
   - For the entity surfaces here (Collection, DataObject, references,
     containers), write concurrency is much lower than for search hits.
   - Cursor pagination requires every endpoint to commit to a stable
     `(orderBy, id)` cursor, which the Neo4j-backed `findAllX`
     queries don't all support today (see §4.3 below — graph traversal
     ordering is not free).
3. **The `13-search-improvements.md` proposal is forward-only.** It
   targets a *new* `POST /search/v2` envelope. Nothing prevents v2
   from adopting cursors while the existing list endpoints keep
   `page/size`. The two conventions can coexist as long as the
   distinction is "search v2 = cursor; everything else = page+size".
4. **Breaking change cost.** Switching the existing 11 paginated
   endpoints from `page/size` to `cursor/limit` would force a major
   version bump on the OpenAPI spec, regenerate every client, and
   require migration on every frontend page that currently iterates
   pages.

**Concrete decisions baked into this recommendation:**

- Drop `?limit&skip` from `GET /spatialDataContainers/{cid}/payload`
  (`SpatialDataPointRest.java:245-246`) — rename to `?page&size` for
  consistency, keep the old names as aliases for one minor version
  with a `@Deprecated` annotation.
- Where a list endpoint is too small to bother (item count bounded by
  entity semantics, e.g. annotations on a single collection), leave
  unpaginated but **add a hard server-side cap** (e.g. 1000) and
  document it. This is the cheapest mitigation for tail latency.
- Refactor the duplicated `if (page != null && size != null)
  params.withPageAndSize(...)` plumbing into a `@BeanParam`-shaped
  `QueryParamsBean` (`input_raw.md:1481`). One-time cost, every new
  endpoint becomes cheaper. Mark this as a prerequisite for the
  rollout.

**Gap with `13-search-improvements.md`:** The forward-looking design
chose cursor pagination; this recommendation diverges by keeping
offset pagination on the existing list endpoints. The gap is
intentional and bounded — search v2 is the only place where cursor
shows up — but should be explicit in the eventual rollout commit
message and the OpenAPI changelog.

---

## 4. Sized rollout — per endpoint

Sizing scale (from task brief):

- **Trivial (≤30 min)**: query already supports pagination; just plumb
  the params through.
- **Small (≤2 h)**: query needs `LIMIT`/`SKIP`; small test additions;
  client regen.
- **Medium (≤1 day)**: graph traversal needs an `ORDER BY` for stable
  pagination; potentially DAO refactor.
- **Large (multi-day)**: streaming or tightly client-coupled.

### 4.1 Trivial — already wired or one-line plumbing

| Endpoint | Why trivial |
|---|---|
| Item 27 (`SpatialDataPointRest` payload) | Already accepts `?limit&skip`; just rename/alias to `?page&size`. |

### 4.2 Small — `LIMIT`/`SKIP` on a flat query, plus tests + regen

These are the headline candidates. All read from a flat collection
(MongoDB collection, JPA list, or a single Neo4j relationship hop)
where `SKIP / LIMIT` is well-defined.

| Endpoint | Effort | Notes |
|---|---|---|
| Item 6 `getAllFileReferences` | S | DAO returns `List<FileReference>` from a single Neo4j hop; add `params.pagination` plumbing through `fileReferenceService.getAllReferencesByDataObjectId(...)`; mirror to `URIReference`, `DataObjectReference`, `CollectionReference`, `TimeseriesReference`, `SpatialDataReference`, `StructuredDataReference`. |
| Items 7, 8, 9, 10, 11, 12 | S each | Same shape as item 6; one DAO method, one resource method, one test. ~6 endpoints × 1.5 h each. |
| Item 23 `getAllFiles` | S | `FileContainer.getFiles()` is a JPA `@OneToMany`; replace with a paged `JPQL` query. Test against a container with 10⁴+ files. |
| Item 25 `getAllStructuredDatas` | S | Same as item 23 but the payload list is in MongoDB; service holds the connection, slot in a `.skip().limit()` on the cursor. |
| Item 32 `getLabJournalsByCollection` | S | Already orders newest-first; add page/size to the JPA query. |
| Item 30 `getAllApiKeys`, item 31 `getAllSubscriptions`, item 4 `getVersions` | S each | Tiny entities, low row counts; rolled in for consistency, not urgency. |

### 4.3 Medium — graph traversal needs stable `ORDER BY`

| Endpoint | Effort | Notes |
|---|---|---|
| Item 18 `getTimeseriesOfContainer` | M | Currently filters in Java with `.stream().filter()` after fetching everything (`TimeseriesRest.java:274-287`). Pagination at the resource layer would still load everything. Needs a real DAO query that pushes filters and `LIMIT`/`OFFSET` into Neo4j; non-trivial because the filter columns (`measurement`, `device`, `location`, `symbolicName`, `field`) live on the timeseries entity but the access predicate lives on the container. Order by `timeseries_id` for stability. |
| Item 13 `getStructuredDataPayload` (on a reference) | M | The reference fans out to ≥1 container and pulls the union; pushing `LIMIT` requires deciding the merge order. Either define `ORDER BY structuredData.oid` and `LIMIT n` after the union, or paginate per-container (multiple round-trips). |
| Item 14 `getSpatialDataPayload` (on a reference) | M | Same shape as item 13, on PostGIS. Feature-gated; defer behind item 27 work. |
| Item 2 `GET /collections/{cid}` (the embedded list) | M | The CollectionIO body embeds all DataObjects + their references; this is the eager-fetch problem. Real fix is to *not* embed (clients fetch sub-resources on demand) — that is an API contract change, not pagination. Mark as 🟪 ARCH and surface in §5.6. |

### 4.4 Large — leave for the streaming / search-v2 work

| Endpoint | Why deferred |
|---|---|
| Item 20 `GET /timeseriesContainers/{cid}/payload` | Already in scope of perf §11.A.1 (`StreamingOutput`-style NDJSON). Pagination is the wrong tool — the result set is one timeseries window; clients want the whole window. Streaming is the answer. |
| Item 21 `GET …/export` | Already streams CSV. No pagination needed. |
| Items 33–37 (search v1) | Covered by `13-search-improvements.md` §2 (unified v2 with cursors). Touching these now is wasted work. |

---

## 5. Suggested rollout

### 5.1 Round-1 prerequisite (1 ticket, ≤4 h)

Refactor `QueryParamHelper` plumbing into a JAX-RS `@BeanParam`
(`PaginationQueryParams`) — eliminates the 10× duplicated `if (page
!= null && size != null) ...` boilerplate (`input_raw.md:1481`).
Touches every `*Rest.java` that already paginates. Pure refactor;
no behaviour change. Lands ahead of the new endpoint changes so
each subsequent ticket is one line.

### 5.2 Round-1 — top-3 to do first (highest row-risk × FE coupling)

1. **Item 18 `GET /timeseriesContainers/{cid}/timeseries`** — M.
   This is the biggest exposure: a busy timeseries container has
   thousands of distinct series, the filter today runs in Java post-
   fetch, and the frontend lists them in dropdowns. Push the filter
   and pagination into Neo4j. Estimate: 1 d.
2. **Item 23 `GET /fileContainers/{cid}/payload`** — S.
   Files-per-container is the canonical "list grew over a year and
   nobody noticed". JPA-side fix; one query, one DAO test.
   Estimate: 2 h.
3. **Item 25 `GET /structuredDataContainers/{cid}/payload`** — S.
   Same shape as item 23 on MongoDB; symmetric work, fits in the
   same sprint slot. Estimate: 2 h.

### 5.3 Round-2 — reference endpoints (one batch, ~1 d)

Items 6, 7, 8, 9, 10, 11 — all six DataObject-scoped reference
endpoints share the DAO shape (`getAllReferencesByDataObjectId(...)`).
A single ticket adds an overload taking `QueryParamHelper`, exposes
the params on each `*ReferenceRest.java`, regenerates clients.
This is a pure batch op — six near-identical patches. Item 12
(spatialDataReferences) joins this batch behind the feature toggle.

### 5.4 Top-3 to defer

1. **Item 19 `GET /timeseriesContainers/{cid}/available`** — already
   `@Deprecated(forRemoval = true)` (`TimeseriesRest.java:211`).
   Deleting it is faster than paginating it. Coordinate with !80
   closure.
2. **Item 4 `GET /collections/{cid}/versions`** — toggle-gated
   (`@IfBuildProperty(VersioningFeatureToggle.TOGGLE_PROPERTY)`,
   `CollectionVersioningRest.java:39`). Versioning is itself under
   re-evaluation in Cluster B; pagination here is premature.
3. **Item 14, 27 (spatial)** — feature is experimental
   (`@EndpointDisabled(name = "shepard.spatial-data.enabled")`,
   `SpatialDataPointRest.java:47`). Defer until after the spatial
   feature decision is made; folding into the rest of the convention
   then is cheap.

### 5.5 Leave alone

- All search v1 endpoints (items 33–37) — owned by
  `13-search-improvements.md` §2.
- The streaming `payload` on timeseries (item 20) — owned by
  `12-timescaledb-performance-analysis.md` §11.A.
- The CSV export (item 21) — already streams.

### 5.6 Cross-cutting note (not part of pagination)

Item 2 — `GET /collections/{cid}` — returning a Collection with
embedded DataObjects + nested incoming references is the single
biggest "JSON the client doesn't want all of" surface, and pagination
isn't the lever. The right lever is to make embedded children
opt-in (`?expand=dataObjects` or `?include=...`), aligning with the
`include` design in `13-search-improvements.md` §2.1. Track this
under a separate "API ergonomics" backlog item, not L6.

---

## 6. Open questions

- **`@BeanParam` vs explicit `@QueryParam` arguments.** `@BeanParam`
  is the cleanest refactor but Quarkus + `quarkus-openapi-generator`
  has historically rendered `@BeanParam`-bound parameters slightly
  differently in the generated client. Verify with a spike before
  Round-1. (Maintainer call.)
- **OpenAPI version bump.** Adding `?page&size` to existing endpoints
  is a backwards-compatible additive change — but the generated
  Java/Python clients gain new method overloads that *do* count as
  ABI-breaking for callers that import method handles by signature.
  Check `clients/java/config.yaml` and the Python client equivalent
  before deciding whether to bump the patch version or the minor.
- **Cursor stability under deletes** (the perf doc §2.6 concern).
  If we *do* eventually move list endpoints to cursor pagination,
  what is the contract when a row in the cursor's `(orderBy, id)`
  has been deleted? Postgres-style "skip and continue" is the
  reasonable default; document it explicitly.
- **`limit/skip` aliasing on the spatial endpoint.** Keep the old
  param names as aliases for one or two minor versions, or do a
  hard switch at a major bump? The spatial feature is experimental
  enough that a hard switch is defensible, but cheap aliasing
  removes the question. (Maintainer call.)
- **Hard caps on un-paginated low-volume lists.** For the ~10
  endpoints we propose to leave un-paginated (annotations, versions,
  apikeys, subscriptions), should the server enforce an upper cap
  (say 1000) and return 400 when an entity *somehow* accumulates
  more, or just leave it? A cap is a one-line guard rail with low
  cost; recommend yes. Confirm with maintainers.
- **Frontend impact of changing item 18's behaviour.** The current
  `getTimeseriesOfContainer` does Java-side filtering of the full
  list; clients that pass partial filters and then filter client-
  side will see fewer results when the server starts filtering.
  Verify the frontend `useTimeseriesContainer` composable doesn't
  rely on the over-fetch.
- **L6 location in the dispatcher backlog.** The brief references
  `aidocs/16-dispatcher-backlog.md` but that file does not exist in
  the snapshot tree. Confirm where the dispatcher backlog lives —
  this doc references "L6" by its definition in
  `input_raw.md:689-691` only.

---

## 7. References

- Convention helpers: `QueryParamHelper.java:1-77`,
  `PaginationHelper.java:1-20`, `Constants.java:QP_PAGE/QP_SIZE`.
- Already-paginated resources: `TimeseriesRest.java:101-115`,
  `FileRest.java:82-99`, `StructuredDataRest.java:76-94`,
  `CollectionRest.java:75-93`, `DataObjectRest.java:73-105`,
  `SpatialDataPointRest.java:80-95` (containers),
  `SemanticRepositoryRest.java:59-77`, `UserGroupRest.java:140-155`,
  `BasicReferenceRest.java:71-91`, `SearchRest.java:100-127` /
  `SearchRest.java:144-162`.
- Non-paginated resources: `FileRest.java:163-179` (getAllFiles),
  `StructuredDataRest.java:184-202` (getAllStructuredDatas),
  `TimeseriesRest.java:248-288` (getTimeseriesOfContainer),
  `CollectionVersioningRest.java:48-66`,
  every `*ReferenceRest.java` `getAll…` method
  (e.g. `FileReferenceRest.java:62-96`, `URIReferenceRest.java:58-88`,
  `DataObjectReferenceRest.java:62-96`,
  `CollectionReferenceRest.java:62-96`,
  `TimeseriesReferenceRest.java:61-92`,
  `StructuredDataReferenceRest.java:62-95` and `:185-212`,
  `SpatialDataReferenceRest.java:59-94` and `:184-208`),
  every semantic-annotation `getAllAnnotations` method
  (e.g. `CollectionSemanticAnnotationRest.java:40-56`,
  `DataObjectSemanticAnnotationRest.java:55-79`,
  `BasicReferenceSemanticAnnotationRest.java:59-89`,
  `AnnotatableTimeseriesRest.java:51-74`),
  `SubscriptionRest.java:47-67`, `ApiKeyRest.java:49-71`,
  `LabJournalEntryRest.java:58-81`,
  `TimeseriesReferenceMetricsRest.java:55-100`.
- Companion docs: `12-timescaledb-performance-analysis.md` §11.A
  (streaming), `13-search-improvements.md` §2.6 (cursor).
- Source cluster of the pagination-duplication concern:
  `aidocs/input/input_raw.md:1462`, `:1481-1482`.
- Backlog item L6: `aidocs/input/input_raw.md:689-691` (Output Control
  / Pagination, under "Core").
