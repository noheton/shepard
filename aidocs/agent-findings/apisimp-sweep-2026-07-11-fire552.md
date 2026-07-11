---
stage: deployed
last-stage-change: 2026-07-11
---

# APISIMP Sweep — fire-552 (2026-07-11)

Triggered by: all named APISIMP rows through fire-552 are shipped (APISIMP-OID-PATHPARAM-REPLACE
slice 3 merged as PR #2490, sha `d1f77a4a3`). Sweep runs against the full v2 REST surface.

Last shipped before this sweep: `APISIMP-OID-PATHPARAM-REPLACE` (all 3 slices, PR #2490, fire-552).

## Scope

Full-surface agent scan: 490 `@Path` annotations under `de.dlr.shepard.v2`. Agent ran 108 tool
calls over ~11 minutes. Clean categories: per-kind path split (0 violations), bespoke `*ConfigRest`
classes (0), `@Path(Constants.SHEPARD_API + ...)` in v2 (0), superseded render endpoints (0).

---

## Finding 1 — APISIMP-DO-DEPRECATED-COUNT-FIELDS (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectDetailV2IO.java:37-38`

**What's wrong:** `DataObjectDetailV2IO` extends `DataObjectIO` and overrides `@JsonIgnoreProperties`
to suppress numeric Neo4j ID fields. But three `@Deprecated` count fields declared on `DataObjectIO`
(`timeseriesReferenceCount`, `fileBundleCount`, `structuredDataReferenceCount`) are NOT in the
ignore list, so they leak into every `GET /v2/dataobjects/{appId}` response even though v2 has
replaced them with `timeseriesCount`, `fileCount`, `structuredDataCount` on `DataObjectListItemV2IO`.
Frontend reads `d.fileCount` (v2 field) — none of the three deprecated fields are read by the
current frontend.

Current ignore list:
```java
@JsonIgnoreProperties({"id", "collectionId", "referenceIds", "successorIds",
  "predecessorIds", "childrenIds", "parentId", "incomingIds"})
```

Missing: `"timeseriesReferenceCount", "fileBundleCount", "structuredDataReferenceCount"`.

**Fix:** Add the three deprecated field names to the `@JsonIgnoreProperties` annotation on
`DataObjectDetailV2IO`. No other changes needed.

**Size:** XS (one-line change, no migration)

---

## Finding 2 — APISIMP-NOTEBOOK-LIST-PAGINATE (S) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/NotebookRest.java`, line ~193

**What's wrong:** `GET /v2/collections/{appId}/notebooks` returns a bare
`Response.ok(List<NotebookReferenceIO>)` with no pagination wrapper and no `page`/`pageSize`
query params. Every other v2 list endpoint uses `PagedResponseIO`. A collection with many linked
notebooks returns an unbounded JSON array payload.

**Fix:** Wrap the return value in `PagedResponseIO<NotebookReferenceIO>`. Add standard
`@QueryParam("page") @DefaultValue("0") @Min(0) int page` and
`@QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize` query params.
Apply SKIP/LIMIT in the DAO query or slice at the service layer.

**Size:** S (one REST file + DAO query + one test update)

---

## Finding 3 — APISIMP-PROBLEM-GENERIC-THROWS (S) — NEW

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/admin/storage/FileMigrationRest.java:90,98,162,171,176,179`
- `backend/src/main/java/de/dlr/shepard/v2/sql/resources/SqlTimeseriesRest.java:209,231`

**What's wrong:** Two REST classes throw JAX-RS exceptions directly (`BadRequestException`,
`NotFoundException`, `WebApplicationException`) instead of using `ProblemResponse.problem()`.
The `ShepardExceptionMapper` converts these to `application/problem+json`, but assigns a generic
type URL (`/problems/not_found_entity`, `/problems/bad_request`). Callers cannot distinguish
"migration bad provider" from "collection not found" from the `type` URL alone, breaking RFC 7807
machine-readability.

**Fix:** In `FileMigrationRest` (6 throw sites) and `SqlTimeseriesRest` (2 throw sites), replace
direct throws with `return problem(PROBLEM_TYPE_CONSTANT, title, status, detail)` using
resource-specific type URL constants (e.g. `/problems/storage.migration.bad-provider`,
`/problems/sql.timeseries.bad-query`).

**Size:** S (2 REST files, 8 call sites — mechanical replacement with no logic change)

---

## Finding 4 — APISIMP-BASIC-ENTITY-ID-HIDE (M) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/common/neo4j/io/BasicEntityIO.java:22`

**What's wrong:** `BasicEntityIO.id` (the Neo4j OGM node ID, a `Long`) is declared on the base
class that ALL entity IO classes — both v1 and v2 — inherit from. Since v2 entity shapes inherit
`BasicEntityIO`, every `GET /v2/…` response currently includes the internal Neo4j node ID as `"id"`.
`DataObjectDetailV2IO` already suppresses it via `@JsonIgnoreProperties({"id", ...})`, but other
v2 IO classes (e.g. `CollectionV2IO`, `ContainerV2IO` base, `ReferenceV2IO` base) do NOT have
an explicit ignore — they rely on `BasicEntityIO` not suppressing `id`.

**Blocker:** `BasicEntityIO` is shared with the v1 surface. Annotating `@JsonIgnore` on
`BasicEntityIO.id` directly would break the v1 wire shape (v1 clients receive `id` today and
CLAUDE.md forbids changing v1 wire shape). The correct fix is a v2-specific intermediate base
class (e.g. `BasicV2EntityIO extends BasicEntityIO`) that annotates `@Schema(hidden=true)
@JsonIgnore` on a `getId()` override, and have all v2 IO classes extend `BasicV2EntityIO`.

**Size:** M (new base class + update imports/extends in ~15 v2 IO classes + test)

---

## Finding 5 — APISIMP-SEMANNT-NUMERIC-IDS (M) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/context/semantic/io/SemanticAnnotationIO.java:18,43,47`

**What's wrong:** `SemanticAnnotationIO` exposes `long propertyRepositoryId` and
`long valueRepositoryId` (semantic repository entry IDs, numeric Neo4j OGM ids) in every
annotation response. These are actively read by `frontend/components/semantic/AnnotationDialog.vue:235-236`
to identify vocabulary terms for edit/delete operations. No UUID v7 replacement fields exist yet.

**Fix:** (a) Add `String propertyVocabularyEntryAppId` and `String valueVocabularyEntryAppId`
UUID v7 fields to `SemanticAnnotationIO`. (b) Populate from the vocabulary entry's `appId` via
the repository loader. (c) Migrate `AnnotationDialog.vue` to read the new UUID fields. (d)
Deprecate `propertyRepositoryId` and `valueRepositoryId` (keep for one deprecation window).

**Size:** M (IO class + two service/DAO calls to resolve appIds + frontend migration + test)

---

## What was NOT found

- New `@Path(Constants.SHEPARD_API + …)` in v2: 0 violations.
- Numeric `Long` path params in v2: 0 (only string `"id"` in `PluginsAdminRest` which is a
  plugin string name, not a numeric Neo4j ID).
- `@Max(1000)` on non-cursor endpoints: 0 (ProvenanceRest cursor semantics justify the higher cap).
- ProvenanceRest `?limit=` vs `?pageSize=`: intentional per APISIMP-PROV-CURSOR-PAGECAP —
  cursor semantics warrant a different param name; not filed.
- Remaining `"oid"` in response bodies: `ContainerV2IO.payload.oid` (container's own MongoDB ID,
  actively used by frontend for container-level addressing), `ContentMcpTools` file/structured-data
  oid (needed for v1 content fetch). Both are tracked but not filed this sweep since they require
  paired v2 content-retrieval endpoint work.

## New rows filed

| Row | Size | Status |
|-----|------|--------|
| APISIMP-DO-DEPRECATED-COUNT-FIELDS | XS | ⏳ queued |
| APISIMP-NOTEBOOK-LIST-PAGINATE | S | ⏳ queued |
| APISIMP-PROBLEM-GENERIC-THROWS | S | ⏳ queued |
| APISIMP-BASIC-ENTITY-ID-HIDE | M | ⏳ queued |
| APISIMP-SEMANNT-NUMERIC-IDS | M | ⏳ queued |

## Next sweep trigger

After APISIMP-DO-DEPRECATED-COUNT-FIELDS ships: check `DataObjectListItemV2IO` for any
remaining deprecated field leaks. After APISIMP-BASIC-ENTITY-ID-HIDE ships: verify zero `"id"`
fields appear in v2 entity responses (except explicitly v1-compatible shapes).
