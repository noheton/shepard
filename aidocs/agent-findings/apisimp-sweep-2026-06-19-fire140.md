---
stage: fragment
last-stage-change: 2026-06-19
---

# APISIMP sweep — 2026-06-19 fire-140

Targeted audit of the v2 REST surface for remaining API simplification opportunities
not already filed as APISIMP-* rows. Covers `@QueryParam` documentation gaps, numeric
ID leaks, pagination inconsistencies, and response IO issues.

## Scope

Scanned every file returned by:
```
grep -rn "@QueryParam" backend/src/main/java/de/dlr/shepard/v2/ --include="*.java" -l
```

Cross-checked each finding against the existing backlog (APISIMP-* rows, fire-114 through
fire-139 PRs, APISIMP-DO-CHAIN-DEPTH-PARAM and APISIMP-CONTAINERS-THUMBNAIL-SIZE-PARAM
from this fire session already filed).

---

## Finding 1 — APISIMP-DO-CHAIN-DEPTH-PARAM (already filed, confirming)

Already filed in this fire session (fire-140, PR #2024).

`DataObjectV2Rest.java:792,829` — `@QueryParam("depth")` on both `predecessorChain()` and
`successorChain()` missing `@Parameter`. Clamped server-side to [1,50] but OpenAPI schema
is bare.

**Status:** in-progress (PR #2024)

---

## Finding 2 — APISIMP-CONTAINERS-THUMBNAIL-SIZE-PARAM (already filed, confirming)

Already filed in this fire session (fire-140).

`ContainersV2Rest.java:1341` — `@QueryParam("size") Integer size` on `getThumbnail()` missing
`@Parameter`. Valid values 64/200/400; any other normalised to 400. OpenAPI schema is bare.

**Status:** queued

---

## Finding 3 — APISIMP-SA-LIST-FIND-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/annotations/resources/SemanticAnnotationV2Rest.java`
**Lines:** 145–150 (`list()`) and 195–198 (`find()`)

`SemanticAnnotationV2Rest` is the primary annotation query surface, but all 10 query params
across its two GET methods carry no `@Parameter` annotations.

`list()` (lines 145–150):
- `@QueryParam("subjectAppId")` — optional UUID v7 filter; if present, read-access is
  gated on that entity.
- `@QueryParam("subjectKind")` — optional kind discriminator (`DataObject`, `Collection`,
  `TimeseriesContainer`, `FileContainer`, `StructuredDataContainer`).
- `@QueryParam("predicateIri")` — optional IRI filter (e.g. `urn:shepard:spatial:axis`).
- `@QueryParam("vocabId")` — optional vocabulary UUID v7 to narrow predicates.
- `@QueryParam("page")` / `@QueryParam("pageSize")` — server-side clamps to [1, 200] at
  `MAX_PAGE_SIZE` (line 79). Supplying `?pageSize=500` silently returns 200 results.

`find()` (lines 195–198):
- `@QueryParam("q")` — required for the `/annotations/find` endpoint; 400 returned if blank.
- `@QueryParam("vocabId")` — optional narrow.
- `@QueryParam("page")` / `@QueryParam("pageSize")` — same 200-cap applies.

**What's wrong:** An MCP agent or SDK caller relying on the OpenAPI spec for the annotation
search surface sees bare params with no description, no accepted kinds list, no cap warning.
The `q` param is effectively required (400 if blank) but the schema marks it optional.

**Fix:** Add `@Parameter` to all 10 params across both methods. Document the `subjectKind`
accepted values (5 entity kinds). Mark `q` as `required=true`. Document the `pageSize` cap
of 200 on both methods.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 4 — APISIMP-CONTAINERS-CREATE-LIST-FORCE-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java`
**Lines:** 143, 337, 390–391, 565–566, 875–882

Five separate endpoint parameter sets missing `@Parameter` in `ContainersV2Rest`, not yet
covered by the two ContainersV2Rest PRs already filed (which covered `start`/`end`/`downsample`/`maxPoints` at lines 650–656).

**143** — `create()`: `@QueryParam("kind") String kind` — required (400 if absent/blank),
accepts any installed container kind name (`file`, `timeseries`, `structureddata`,
plugin-defined). OpenAPI schema is bare; callers cannot enumerate valid values.

**337** — `delete()`: `@QueryParam("force") @DefaultValue("false") boolean force` — when
false, returns 409 with `SafeDeleteConflict` body if the container has active DataObject
references; when true, force-deletes. The 409 behaviour is documented in the `@APIResponse`,
but the `force` param itself has no `@Parameter` so the schema shows an undescribed boolean.

**390–391** — `list()`: `@QueryParam("kind") String kind` (required, 400 if absent) and
`@QueryParam("name") String name` (optional, substring filter). Same kind-name enum issue as
the create endpoint.

**565–566** — `listChannels()`: `@QueryParam("page")` and `@QueryParam("pageSize")` — default
`0` and `200` respectively. No server-side cap documented.

**875–882** — `getLiveWindow()`: the live-window endpoint accepts the full 5-tuple channel
address **plus** `shepardId` UUID v7 as an alternative key **plus** two behaviour params:
- `@QueryParam("shepardId") UUID shepardId`
- `@QueryParam("measurement") String measurement`
- `@QueryParam("device") String device`
- `@QueryParam("location") String location`
- `@QueryParam("symbolicName") String symbolicName`
- `@QueryParam("field") String field`
- `@QueryParam("windowSeconds") @DefaultValue("300") @Min(1) @Max(3600) int windowSeconds`
- `@QueryParam("withBoundaryPoints") @DefaultValue("true") boolean withBoundaryPoints`

None carry `@Parameter`. The 5-tuple vs. `shepardId` disambiguation is described in the
`@Operation` prose; the per-param schema is bare. `windowSeconds` is bean-validated to
[1, 3600] (`@Min(1) @Max(3600)`) but the schema does not echo this constraint.

**Fix:** Add `@Parameter` to all 15 params above. For `kind` params, document accepted values
and note plugins may extend the set. For `getLiveWindow`, cross-reference the TS-ID migration
(prefer `shepardId`; 5-tuple is legacy). For `windowSeconds`, surface the [1, 3600] constraint.
Add 7 reflection regression tests (one per distinct param group).

**Size:** S  
**Wire change:** DOC-ONLY

---

## Finding 5 — APISIMP-DO-LIST-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java`
**Lines:** 189–194

`DataObjectV2Rest.list()` has 6 `@QueryParam` params with no `@Parameter` annotations:

- `@QueryParam("name")` — optional substring filter on `DataObject.name`.
- `@QueryParam("status")` — optional status filter. The service accepts any string; no enum
  enforcement at the REST layer, but the valid values are the `DataObjectStatus` enum members
  (`DRAFT`, `IN_REVIEW`, `READY`, `PUBLISHED`, `ARCHIVED`). An unknown value returns 0 results
  silently (filter just never matches). The OpenAPI schema is bare and gives no hint of valid values.
- `@QueryParam("page")` / `@QueryParam("pageSize")` — server silently clamps `pageSize` to
  [1, 200] (line 220 `Math.min(Math.max(pageSize, 1), 200)`). A caller supplying `?pageSize=500`
  gets 200 items with no indication why. Default pageSize 50.
- `@QueryParam("include")` — power-user enrichment param. Accepted values: `time-bounds`
  (triggers 2 extra DB round-trips to populate `timeBoundsStart`/`timeBoundsEnd` on each item),
  `full` (opts back into the full wire shape suppressed by the list-diet optimisation). Any other
  value is silently ignored. The `@Operation` description documents `?include=time-bounds` and
  `?include=full` in prose (lines 153–165); the param schema is bare.
- `@QueryParam("fields")` — sparse field projection. Comma-separated field names. Unknown fields
  return 400 with a `ProblemJson` body listing the unknown field. Valid fields are the members of
  `DataObjectListItemV2IO`. The `@Operation` documents this feature in prose; the param schema
  is bare.

**What's wrong:** The most-called list endpoint in the API has 6 bare params. The `status` filter
silently returns zero results for invalid values (a footgun — a typo in `?status=draft` instead
of `DRAFT` returns empty, and there is no indication why). The `pageSize` cap is silent.

**Fix:** Add `@Parameter` to all 6 params. Document `status` valid values (`DRAFT`, `IN_REVIEW`,
`READY`, `PUBLISHED`, `ARCHIVED`). Document `pageSize` silent cap to 200. Document `include`
accepted values and their cost notes. Document `fields` validation. Add 6 reflection regression tests.

**Size:** S  
**Wire change:** DOC-ONLY

---

## Finding 6 — APISIMP-IMPORT-CONTEXT-DIAGNOSTICS-PARAMS-UNDOCUMENTED

**Files:**  
- `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportV2Rest.java:207–208`
- `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportDiagnosticsV2Rest.java:116–117`

**ImportV2Rest.java** — `getContext()` at `GET /v2/import/context`:
- `@QueryParam("collectionAppId") String collectionAppId` — required (400 if absent/blank);
  UUID v7 of the target Collection. No `@Parameter`.
- `@QueryParam("includeSemanticGraph") @DefaultValue("false") boolean includeSemanticGraph` —
  when true, the response includes current semantic annotations on Collection DataObjects (used
  by AI agents to choose consistent annotation terms). The `@Operation` description explains
  this toggle in prose; the per-param schema is bare.

**ImportDiagnosticsV2Rest.java** — `getEvents()` at `GET /v2/import/diagnostics/{runId}`:
- `@QueryParam("level") String level` — optional severity filter. Valid values: `INFO`, `WARN`,
  `ERROR` (from `ImportDiagnosticEvent` constants). An invalid value returns 400 with a plain-text
  error body (not RFC 7807). The `@Operation` description lists valid values in prose; the param
  schema is bare, so a generated SDK cannot enumerate them.
- `@QueryParam("phase") String phase` — optional phase filter. Valid values: `WARMUP`,
  `DO_CREATE`, `REF_ATTACH`, `FILE_UPLOAD`, `COMPLETE`. Same bare-schema issue.

**Fix:** For `ImportV2Rest`: add `@Parameter(required=true, description="UUID v7 appId of
the target Collection.")` to `collectionAppId`; add `@Parameter(description="When true,
include current semantic annotations on the Collection's DataObjects in the response...")` to
`includeSemanticGraph`. For `ImportDiagnosticsV2Rest`: add `@Parameter(description="Severity
filter. Accepted values: INFO, WARN, ERROR. Returns 400 for other values. Omit to return all
severity levels.")` to `level`; analogously document `phase`. Add 4 reflection regression tests.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 7 — APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE

**File:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java`
**Line:** 155

`CollectionV2Rest.list()` uses `@QueryParam("size")` (not `"pageSize"`) for its page-size
parameter, inconsistent with every other paginated v2 endpoint which was migrated to `pageSize`
by APISIMP-PAGINATION-UNIFY-RECREATE (PR #1887, merged 2026-06-15).

```java
@QueryParam(Constants.QP_NAME) String name,
@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
@QueryParam("size") @DefaultValue("50") @PositiveOrZero int size   // ← should be "pageSize"
```

The backlog row APISIMP-PAGINATION-UNIFY-RECREATE claims this file as a target
(`CollectionV2Rest.java:156`) and PR #1887 as shipped, but the rename did not land in the
current `main`. The variable is also named `size` internally, which the APISIMP-PAGINATION-PARAM-NAMING
fix (#1986) addressed in `UserGroupV2Rest` — same issue here.

The `@APIResponse` description at line 151 also still says `?page` and `?size` in its template.

**Fix:** Rename `@QueryParam("size")` → `@QueryParam("pageSize")` and method param `size` →
`pageSize` in `list()`; update the `@APIResponse` description if it references the old param
name; update the frontend caller `frontend/composables/collection/usePagedCollections.ts` (or
equivalent) if it passes `size:`; add 1 regression test asserting the `?pageSize=` form works.

**Size:** XS  
**Wire change:** WIRE CHANGE — callers currently using `?size=N` will have their param silently
ignored after the rename (no backward-compat shim needed since this is a dev-only v2 surface,
not yet in production at scale). Coordinate with any known callers.

---

## Finding 8 — APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE

**File:** `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java`
**Line:** 426

`FileBundleReferenceRest.listGroupFiles()` at `GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files`
uses `@QueryParam("size")` (not `"pageSize"`). Same issue as Finding 7 — this endpoint is
the high-cardinality ImageBundle pagination path designed for MFFD AFP process frames.

```java
@QueryParam("page") Integer page,
@QueryParam("size") Integer size,     // ← should be "pageSize"
```

The `@Operation` description at line 413 explicitly says `"size — page size. Default 200; min 1;
max 1000."` — the prose uses `size`, not `pageSize`. No `@Parameter` annotations either (bare
params). The value is clamped server-side (not rejected).

Note: the existing `FileBundleReferenceRest` also has `@QueryParam("force") boolean force` at
line 380 (`deleteGroup()`) and `@QueryParam("page")` / `@QueryParam("size")` at the list
method with no `@Parameter` either — all bare.

**Fix:** Rename `@QueryParam("size")` → `@QueryParam("pageSize")` on `listGroupFiles()`;
add `@Parameter` to `force`, `page`, and `pageSize` on the affected methods; update
`@Operation` description prose to say `pageSize`; add 3 reflection regression tests.

**Size:** XS  
**Wire change:** WIRE CHANGE — `?size=N` for image bundle pagination will silently have no
effect after rename. Frontend caller `ImageBundleViewer.vue` or equivalent must be updated.
Check `frontend/` for `listGroupFiles` or `/groups/` calls.

---

## Finding 9 — APISIMP-COLLECTION-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/CollectionSnapshotRest.java`
**Lines:** 183–184

`CollectionSnapshotRest.list()` at `GET /v2/collections/{collectionAppId}/snapshots` accepts
`@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` with no `@Parameter` annotations.
Server silently clamps `pageSize` to [1, 200] at line 191 (`Math.min(Math.max(pageSize, 1), 200)`).
A caller supplying `?pageSize=500` gets 200 results with no indication why.

This is the companion snapshot listing endpoint to `SnapshotListRest.list()` (covered by
APISIMP-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED, PR #1999). Both expose the same silent cap pattern.

**Fix:** Add `@Parameter(description="Zero-based page index (default 0).")` to `page`; add
`@Parameter(description="Page size (default 50). Server-side cap: 200 — values above 200
are silently clamped.")` to `pageSize`; add 2 reflection regression tests. AC: OpenAPI schema
for `GET /v2/collections/{collectionAppId}/snapshots` documents both params including the cap.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 10 — APISIMP-INSTANCE-ADMIN-AUDIT-FILTER-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java`
**Lines:** 226–231

`InstanceAdminRest.permissionAuditLog()` at `GET /v2/admin/permission-audit/log` accepts 6
filter + pagination params with no `@Parameter` annotations:

- `@QueryParam("entityAppId") String entityAppId` — optional UUID v7 filter; narrows results to
  grants/revokes on a specific entity. No description in the OpenAPI schema.
- `@QueryParam("actor") String actor` — optional username filter (the Keycloak username that
  performed the grant/revoke/update action).
- `@QueryParam("from") String from` — optional ISO-8601 instant string (lower bound on
  `occurred_at`). Returns 400 if the value is not a valid ISO-8601 instant — this is documented
  in the `@APIResponse` but not in the `@Parameter` schema, so client generators mark it as a
  plain unconstrained string.
- `@QueryParam("to") String to` — optional ISO-8601 instant string (upper bound). Same issue.
- `@QueryParam("page") @DefaultValue("0") int page` and `@QueryParam("pageSize") @DefaultValue("50") int pageSize`
  — standard pagination; no server-side cap documented.

**Fix:** Add `@Parameter` to all 6 params. For `from`/`to`: document ISO-8601 format requirement
and the 400 when invalid. For `entityAppId`: note it is a UUID v7 (DataObject, Collection, or any
`HasAppId` entity). Add 6 reflection regression tests. AC: OpenAPI schema for `permissionAuditLog`
shows all 6 params; `from`/`to` descriptions mention ISO-8601 and 400 behaviour.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 11 — APISIMP-SPARQL-QUERY-PARAM-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticSparqlRest.java`
**Line:** 206

`SemanticSparqlRest.queryGet()` at `GET /v2/semantic/{repoAppId}/sparql` accepts
`@QueryParam("query") String query` with no `@Parameter` annotation.

The `@Operation` description and `@APIResponse(responseCode="400")` document the constraints in
prose: empty or mutation-form queries (CONSTRUCT / INSERT / DELETE / UPDATE) return 400. But the
OpenAPI param schema shows an undescribed optional string. A client generator cannot know `query`
is required or that mutation forms are rejected.

This is the W3C SPARQL 1.1 Protocol GET form. The parameter is standard (`?query=`) but the
constraints (no mutations, minimum 1 character) are Shepard-specific and need documenting.

**Fix:** Add `@Parameter(required=true, description="SPARQL 1.1 SELECT or ASK query string.
Mutation forms (CONSTRUCT, INSERT, DELETE, UPDATE) return 400. Minimum 1 character.")` to the
`query` param; add 1 reflection regression test (`queryGet_queryParamIsDocumented`). AC: OpenAPI
schema marks `query` as required with description; `mvn verify -pl backend` green.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 12 — APISIMP-LAB-JOURNAL-ENTRY-PAGINATION-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java`
**Lines:** 103–104

`CollectionLabJournalEntriesRest.list()` at `GET /v2/collections/{collectionAppId}/lab-journal-entries`
has `@QueryParam("page") @PositiveOrZero Integer page` and
`@QueryParam("pageSize") @PositiveOrZero Integer pageSize` with no `@Parameter` annotations.

These params were added by APISIMP-LABJOURNAL-MISSING-PAGINATION (PR #1991, merged fire-114),
which correctly added optional pagination parameters (null → return all, preserving the prior
bulk-fetch contract). However, that PR did not add `@Parameter` annotations — the params are
correctly wired but the OpenAPI schema is still bare.

A caller relying on the schema cannot tell: (a) that params are optional with null meaning
"no pagination", (b) what the effective page size is when both are null (all entries), or (c)
what happens when `page` is provided without `pageSize` (or vice versa).

**Fix:** Add `@Parameter(description="Zero-based page index. Omit (along with pageSize) to
return all entries without pagination.")` to `page`; add `@Parameter(description="Page size.
Omit (along with page) to return all entries without pagination. When only one of page/pageSize
is provided, pagination uses the present value with a default for the other.")` to `pageSize`;
add 2 reflection regression tests. AC: OpenAPI schema documents the null/all-entries contract.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Finding 13 — APISIMP-USERGROUP-ORDERBY-PARAMS-UNDOCUMENTED

**File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java`
**Lines:** 89–90

`UserGroupV2Rest.listUserGroups()` has method-level `@Parameter` annotations (JAX-RS style,
placed on the method signature before `public Response`) for all four query params including
`orderBy` and `orderDesc`. However, the `@Parameter` annotations carry only `name=` and no
`description=`:

```java
@Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
@Parameter(name = Constants.QP_ORDER_DESC)
```

This means the OpenAPI schema shows `orderBy` and `orderDesc` as valid but undescribed params.
A caller cannot tell: what values `orderBy` accepts (it is `UserGroupAttributes` enum, values:
`NAME`, `CREATED_AT`, `UPDATED_AT`), or that `orderDesc` defaults to ascending when absent.

**Fix:** Add `description=` to both `@Parameter` annotations — e.g.
`@Parameter(name="orderBy", description="Sort field. Accepted values: NAME, CREATED_AT,
UPDATED_AT (enum de.dlr.shepard.v2.users.io.UserGroupAttributes). Ascending by default.")` and
`@Parameter(name="orderDesc", description="When true, sort descending. Default false (ascending).")`.
Add 2 reflection regression tests. AC: OpenAPI schema shows descriptions for both sorting params.

**Size:** XS  
**Wire change:** DOC-ONLY

---

## Summary table

| Row ID | File | Lines | What's wrong | Size | Wire? |
|--------|------|-------|-------------|------|-------|
| APISIMP-SA-LIST-FIND-PARAMS-UNDOCUMENTED | `SemanticAnnotationV2Rest.java` | 145–150, 195–198 | 10 bare @QueryParam params across list()+find(); `q` is effectively required but unmarked; `pageSize` cap of 200 silent | XS | DOC-ONLY |
| APISIMP-CONTAINERS-CREATE-LIST-FORCE-PARAMS-UNDOCUMENTED | `ContainersV2Rest.java` | 143, 337, 390–391, 565–566, 875–882 | 15 bare @QueryParam params: create(kind), delete(force), list(kind/name), listChannels(page/pageSize), getLiveWindow(5-tuple + shepardId + windowSeconds + withBoundaryPoints) | S | DOC-ONLY |
| APISIMP-DO-LIST-PARAMS-UNDOCUMENTED | `DataObjectV2Rest.java` | 189–194 | 6 bare @QueryParam params; `status` silently returns empty for invalid values; `pageSize` clamped to 200 silently; `include`/`fields` power-user params completely undocumented in schema | S | DOC-ONLY |
| APISIMP-IMPORT-CONTEXT-DIAGNOSTICS-PARAMS-UNDOCUMENTED | `ImportV2Rest.java:207–208`, `ImportDiagnosticsV2Rest.java:116–117` | 207–208 / 116–117 | 4 bare @QueryParam params; `collectionAppId` is required but unmarked; `level`/`phase` filters return 400 for invalid values but schema shows unconstrained strings | XS | DOC-ONLY |
| APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE | `CollectionV2Rest.java` | 155 | `@QueryParam("size")` instead of `"pageSize"` — missed by APISIMP-PAGINATION-UNIFY-RECREATE (PR #1887 claims fix but rename didn't land on main) | XS | WIRE CHANGE |
| APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE | `FileBundleReferenceRest.java` | 380, 426 | `@QueryParam("size")` instead of `"pageSize"` on listGroupFiles(); force param undocumented; no @Parameter on any of these | XS | WIRE CHANGE |
| APISIMP-COLLECTION-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED | `CollectionSnapshotRest.java` | 183–184 | page/pageSize bare; pageSize clamped to 200 silently (same pattern as SnapshotListRest covered by PR #1999) | XS | DOC-ONLY |
| APISIMP-INSTANCE-ADMIN-AUDIT-FILTER-PARAMS-UNDOCUMENTED | `InstanceAdminRest.java` | 226–231 | 6 bare @QueryParam params; `from`/`to` must be ISO-8601 (400 otherwise) but schema shows plain strings | XS | DOC-ONLY |
| APISIMP-SPARQL-QUERY-PARAM-UNDOCUMENTED | `SemanticSparqlRest.java` | 206 | `@QueryParam("query")` effectively required, mutation forms rejected with 400; schema shows undescribed optional string | XS | DOC-ONLY |
| APISIMP-LAB-JOURNAL-ENTRY-PAGINATION-PARAMS-UNDOCUMENTED | `CollectionLabJournalEntriesRest.java` | 103–104 | pagination params added by PR #1991 but `@Parameter` not added; null-means-all-entries contract undocumented | XS | DOC-ONLY |
| APISIMP-USERGROUP-ORDERBY-PARAMS-UNDOCUMENTED | `UserGroupV2Rest.java` | 89–90 | method-level @Parameter exists but has no `description=`; `orderBy` enum values and `orderDesc` default undocumented | XS | DOC-ONLY |

**Total new findings: 11** (all genuinely new, not covered by any previously filed APISIMP-* row)

## Priority order

1. **APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE** (F7) — wire change; affects Collection list callers silently if the rename ever lands without a shim. Should have landed in PR #1887 but didn't.
2. **APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE** (F8) — wire change; the MFFD ImageBundle pagination path (designed for high-cardinality AFP frames) uses `?size=` while the rest of the API says `?pageSize=`.
3. **APISIMP-DO-LIST-PARAMS-UNDOCUMENTED** (F5) — the most-called list endpoint; `status` param silently returns empty on typo (footgun).
4. **APISIMP-CONTAINERS-CREATE-LIST-FORCE-PARAMS-UNDOCUMENTED** (F4) — 15 params across 5 methods; the `getLiveWindow` 5-tuple + shepardId disambiguation is especially important for SDK callers and MCP agents.
5. **APISIMP-SA-LIST-FIND-PARAMS-UNDOCUMENTED** (F3) — primary annotation search surface used by MCP tools; `q` effectively required but marked optional.
6. All DOC-ONLY XS findings (F6, F9, F10, F11, F12, F13) — batch into one PR.
