# 26. REST API CRUD Consistency Inventory

**Date:** 2026-05-05  
**Scope:** 29 `*Rest.java` endpoint classes in `backend/src/main/java/de/dlr/shepard/`  

## Summary

The Shepard REST API surface comprises **29 resources** across **153 total endpoints**: 85 GET, 33 POST, 10 PUT, 25 DELETE, 0 PATCH. Only **8 resources (28%)** implement full CRUD; **6 resources have no DELETE verb**, and **3 lack CREATE**. The surface exhibits significant inconsistency: no PATCH verbs in use (all updates via PUT), path-id types drift (Long vs String vs composite 5-tuple), permissions endpoints exist as `/permissions` sub-resources on only 6 of 25 container-like resources, multiple "by-X" endpoint patterns, and status-code inconsistency (201 vs 200 on Create). The annotation scaffold is standardized (~4 class-level decorators per resource), but endpoint sprawl within individual resources (up to 13 methods in TimeseriesRest) is uncontrolled.

## Resource × CRUD Matrix

| Resource | Create | Read one | Read list | Update (PATCH) | Replace (full PUT) | Delete | Bulk | Other endpoints |
|----------|:------:|:--------:|:---------:|:--------------:|:------------------:|:------:|:----:|:---------------:|
| ApiKey | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| UserGroup | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE | N | +4 perm/role |
| User | ✗ | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✗ | N | 0 |
| Search | ✓ POST (5×) | ✗ | ✗ | ✗ | ✗ | ✗ | N | 0 (pure action) |
| Subscription | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| Versionz | ✗ | ✗ | ✓ GET | ✗ | ✗ | ✗ | N | 0 (read-only) |
| Collection | ✓ POST | ✓ GET | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE | N | +3 (perm, role, export) |
| CollectionVersioning | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✗ | N | 0 (pure list) |
| DataObject | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE | N | 0 |
| LabJournalEntry | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE | N | 0 |
| BasicReference | ✗ | ✗ | ✓ GET | ✗ | ✗ | ✓ DELETE | N | 0 |
| CollectionReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +1 payload |
| DataObjectReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +1 payload |
| FileReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +2 (payload, payload/{oid}) |
| SpatialDataReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +1 payload |
| StructuredDataReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +2 (payload, payload/{oid}) |
| TimeseriesReferenceMetrics | ✗ | ✗ | ✓ GET (2×) | ✗ | ✗ | ✗ | N | 0 (pure metrics) |
| TimeseriesReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | +2 (payload, query) |
| URIReference | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| AnnotatableTimeseries | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| BasicReferenceSemanticAnnotation | ✓ POST | ✗ | ✓ GET | ✗ | ✗ | ✓ DELETE | N | 0 |
| CollectionSemanticAnnotation | ✓ POST | ✓ GET | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| DataObjectSemanticAnnotation | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| SemanticAnnotation | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | N | 0 (empty stub) |
| SemanticRepository | ✓ POST | ✗ | ✓ GET (paginated) | ✗ | ✗ | ✓ DELETE | N | 0 |
| File | ✓ POST (2×) | ✓ GET | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE (2×) | N | +4 (role, payload, payload/{oid}, query) |
| SpatialDataPoint | ✓ POST (2×) | ✗ | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE | N | +1 (query) |
| StructuredData | ✓ POST (2×) | ✗ | ✓ GET (paginated) | ✗ | ✓ PUT | ✓ DELETE (2×) | N | +4 (perm, role, payload, query) |
| Timeseries | ✓ POST (3×) | ✗ | ✓ GET (9×) | ✗ | ✓ PUT | ✓ DELETE | N | +8 (versioning, payload, available, timeseries, metrics, CSV, query) |

**Legend:** ✓ = present, ✗ = missing. "Paginated" per `aidocs/18-pagination-inventory.md`. Counts in parentheses denote multiplicity (e.g., "✓ POST (3×)" = three POST endpoints).

---

## Inconsistencies Inventory

### 1. Missing Verbs

**6 resources lack DELETE:**
- User: `backend/src/main/java/de/dlr/shepard/auth/users/endpoints/UserRest.java:33-47` (read-only, no mutate)
- Search: `backend/src/main/java/de/dlr/shepard/common/search/endpoints/SearchRest.java:65-185` (pure action endpoints, no state mgmt)
- Versionz: `backend/src/main/java/de/dlr/shepard/common/versionz/endpoints/VersionzRest.java:27` (metadata read-only)
- CollectionVersioning: `backend/src/main/java/de/dlr/shepard/context/collection/endpoints/CollectionVersioningRest.java:48-88` (no per-version DELETE)
- TimeseriesReferenceMetrics: `backend/src/main/java/de/dlr/shepard/context/references/timeseriesreference/endpoints/TimeseriesReferenceMetricsRest.java:55-102` (metrics-only, read-only)
- SemanticAnnotation: `backend/src/main/java/de/dlr/shepard/context/semantic/endpoints/SemanticAnnotationRest.java` (empty stub)

**3 resources lack CREATE:**
- User, Search, Versionz (above, plus CollectionVersioning lacks POST for new versions).
- Rationale: User and Versionz are read-only; Search and CollectionVersioning are derived/action endpoints.

### 2. PATCH vs PUT Inconsistency

**0 PATCH endpoints across the entire API.** All partial updates use **PUT** (full replacement semantics in operation, even if partially populated DTO). This violates REST convention: PATCH should signal partial/merge semantics, PUT should signal full replacement.

Examples:
- `DataObjectRest.java:162-188`: PUT `/{collectionId}/dataObjects/{dataObjectId}` with partial DTO — should use PATCH.
- `TimeseriesRest.java:472-498`: PUT `/{containerId}` with partial DTO — should use PATCH.
- `File.java:306-330`: PUT `/{id}/payload/{oid}` — unclear semantics (full or partial).

**Recommendation:** P5 (API cleanup). Adopt PATCH for partial updates (merge) and restrict PUT to full replacement.

### 3. Path-ID Type Drift

| ID Type | Usage | Examples |
|---------|-------|----------|
| **Long (numeric)** | 61 uses (dominant) | `COLLECTION_ID`, `DATA_OBJECT_ID`, containers | CollectionRest:96, DataObjectRest:108 |
| **String (UUID/name)** | 6 uses | `USERNAME`, `APIKEY_UID` | ApiKeyRest:82, UserRest:33 |
| **Integer (dimension)** | 3 uses | `TIMESERIES_ID` | TimeseriesRest:306 |
| **Composite key (5-tuple)** | ∞ | `(measurement, device, location, symbolicName, field)` | TimeseriesRest:336-348 |

**Issue:** Timeseries endpoints use a composite 5-tuple key (QueryParam, not PathParam), breaking typical `/{id}` patterns.

Examples:
- `TimeseriesRest:336-348`: GET `/containers/{containerId}/payload?measurement=X&device=Y&location=Z&symbolicName=A&field=B` (5 required query parameters = composite key).
- All other resources use single numeric or string ID in path.

**Citation:** `aidocs/23-api-critique.md` §2.1 (timeseries composite keys identified as design anomaly).

### 4. Status Code Inconsistency on Create

**Dominant pattern: 201 Created** (24 POST endpoints), but **one pattern variance:**
- CollectionRest:253: `Response.ok(...).status(Status.CREATED)` — returns 201 but body suggests 200.
- ApiKeyRest:123: `Response.ok(...).status(Status.CREATED)` — mixed semantics.

All DELETE endpoints consistently return **204 No Content** (25 DELETE endpoints).

**Impact:** Minor; clients expect 201 on success. No confusion observed.

### 5. "By-X" Endpoint Fragmentation

**Zero explicit `/by*` endpoints found.** However, **query-param filtering** is used for "by-X" semantics:

- TimeseriesRest:266-288: GET `/{containerId}/timeseries?measurement=X&device=Y&location=Z&symbolicName=A&field=B` (5 filter dims).
- SpatialDataPointRest:286-309: GET `/{containerId}/payload?point=X` (coordinate filter).
- File:181-209: GET `/{containerId}/payload?oid=X` (object filter).

**No fragmentation at endpoint level** (no `/byId`, `/byName`, `/byNeo4jId` variants), but **implicit fragmentation via query params** (each resource defines its own filter schema).

**Recommendation:** Standardize query-param naming convention (e.g., `?filter[measurement]=X` vs `?measurement=X`).

### 6. Sub-Resource Nesting Inconsistency

**Two patterns observed:**

1. **Hierarchical nesting** (consistent):
   - DataObjectRest: `/collections/{collectionId}/dataObjects/{dataObjectId}` (two-level).
   - CollectionSemanticAnnotationRest: `/collections/{collectionId}/semanticAnnotations/{annotationId}` (two-level).
   - TimeseriesRest: `/timeseriesContainers/{containerId}/timeseries/{timeseriesId}` (two-level).

2. **Flat + query-param delegation** (inconsistent):
   - FileRest: `/fileContainers/{id}` + `/fileContainers/{id}/payload/{oid}` (mixed: some nested, some payload-only).
   - StructuredDataRest: `/structuredDataContainers/{id}` + `/structuredDataContainers/{id}/payload/{oid}` (same).

**No conflict at present** (both styles coexist); **no `?collection=…` flat query param style** observed. 

### 7. Permissions Sub-Resource Shape

**6 of 25 container-like resources expose permissions:**
- Collection: GET/PUT `/{id}/permissions` (PermissionsIO) + GET `/{id}/roles` (Roles) — `CollectionRest:170-214` & `216-233`.
- UserGroup: GET/PUT `/{id}/permissions` — `UserGroupRest:157-210`.
- File: GET/PUT `/{id}/permissions` + GET `/{id}/roles` — `FileRest:162-209` (implicit in path structure).
- SpatialDataPoint: GET/PUT `/{id}/permissions` + GET `/{id}/roles` — `SpatialDataPointRest` (line TBD).
- StructuredData: GET/PUT `/{id}/permissions` + GET `/{id}/roles` — `StructuredDataRest` (line TBD).
- Timeseries: GET/PUT `/{id}/permissions` — `TimeseriesRest` (line TBD).

**19 resources have NO permissions endpoint**, including:
- All Reference types (BasicReferenceRest, CollectionReferenceRest, etc.) — no permission sub-resource.
- All Semantic annotations — no permission sub-resource.
- Search, Versionz, User — not applicable (read-only or singleton).

**Inconsistency:** No clear contract for which resources should expose permissions. Those that do follow `/{id}/permissions` pattern consistently (PermissionsIO shape is uniform). **Citation:** `aidocs/16-dispatcher-backlog.md` **P9** (per-entity permissions route).

### 8. Annotation Scaffold Redundancy

**All 29 Rest classes use a standard scaffold of ~4 class-level annotations:**

```java
@Path(...)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class XyzRest { ... }
```

**Exception:** Timeseries (4 decorators), File (4), StructuredData (5) also include `@Transactional` or `@Context` (SecurityContext).

**Count:** 29 × 4 = 116 identical class-level annotations across the codebase.

**Citation:** `aidocs/23-api-critique.md` §3 identifies this as "per-kind annotation rest fragmentation" — 4 per-kind rests (Per-*-Rest, authz, permissions, metrics) creating 4 scaffold clones. **Recommendation:** A2 (monolithic Rest decomposition) from `aidocs/16-dispatcher-backlog.md` would consolidate these.

### 9. Endpoint Sprawl Within Resources

**Endpoint count per resource (sorted descending):**

| Resource | Count | Concern |
|----------|-------|---------|
| Timeseries | 13 | Multiple GET variants (available, timeseries, metrics, payload), CSV export, versioning |
| File | 11 | Payload/{oid} nesting, multiple DELETE paths |
| StructuredData | 11 | Payload/{oid}, dual POST/DELETE, permission/role sub-resources |
| SpatialDataPoint | 9 | Payload with query-params, spatial queries |
| Collection | 9 | Permissions, roles, export, versioning |
| **Others** | 2–4 | Typical CRUD-only (create, read, list, update, delete) |

**Issue:** No architectural limit on endpoint count. Timeseries::13 endpoints blur responsibility (container management + timeseries payload management + query/aggregation + versioning).

**Recommendation:** P5 (API cleanup). Decompose large resources into sub-resources or separate endpoint classes (e.g., TimeseriesPayloadRest, TimeseriesMetricsRest per `aidocs/16` **A2**).

---

## Top 5 Cleanup Wins

### 1. **Introduce PATCH for partial updates** (P5)
   - Replace all PUT endpoints that accept partial DTOs with PATCH.
   - Audit 10 PUT endpoints (Collection, DataObject, LabJournalEntry, File, SpatialDataPoint, StructuredData, Timeseries, UserGroup) for semantic mismatch.
   - **Impact:** Restored REST conformance; client code clarity.

### 2. **Standardize permissions endpoint availability** (P9)
   - Extend GET/PUT `/{id}/permissions` + GET `/{id}/roles` to all 19 resources currently lacking them.
   - Currently: 6 resources (Collection, UserGroup, File, SpatialDataPoint, StructuredData, Timeseries).
   - Missing: All References, all Semantic annotations, Search, Versionz.
   - **Impact:** Uniform permission management; enables role-based access control (RBAC) across all resources.

### 3. **Decompose monolithic TimeseriesRest (13 endpoints → 3 classes)** (A2)
   - Split into TimeseriesContainerRest (CRUD), TimeseriesPayloadRest (data ingestion/export), TimeseriesQueryRest (aggregation/metrics).
   - **Affected file:** `backend/src/main/java/de/dlr/shepard/data/timeseries/endpoints/TimeseriesRest.java:84-498`.
   - **Impact:** Reduced cognitive load; clearer responsibility; easier to extend metrics/aggregation.

### 4. **Adopt uniform permission sub-resource shape** (P5)
   - Standardize PermissionsIO and Roles DTOs across all resources (already consistent in implementation).
   - Enforce via OpenAPI schema constraint or integration test.
   - **Impact:** Predictable API surface; easier tooling (e.g., auto-generated SDKs).

### 5. **Resolve Timeseries composite-key pattern to single-ID or decomposed path** (P5)
   - Current: GET `/containers/{id}/payload?measurement=X&device=Y&location=Z&symbolicName=A&field=B`.
   - Option A: Create `/timeseries/{5-tupleId}` with internal hashing.
   - Option B: Restrict to `/containers/{id}/timeseries/{timeseriesId}` (nesting, as per TimeseriesReference pattern).
   - **Citation:** `aidocs/23-api-critique.md` §2.1.
   - **Impact:** Uniform `/{id}` pattern; easier caching and link generation.

---

## Notes & References

- **Pagination:** 38 list endpoints identified in `aidocs/18-pagination-inventory.md` (reuse count; not re-inventoried here).
- **Existing critique:** `aidocs/23-api-critique.md` already catalogs PATCH/PUT confusion, timeseries composite keys, and annotation redundancy — this doc tabulates scope and adds actionable metrics.
- **Backlog alignment:** Wins map to P5 (API cleanup), P9 (per-entity permissions), and A2 (monolithic Rest decomposition) from `aidocs/16-dispatcher-backlog.md`.
- **Epoch:** Snapshot 2026-05-05. Timeseries composite-key pattern and SemanticAnnotation stub remain as-is.

