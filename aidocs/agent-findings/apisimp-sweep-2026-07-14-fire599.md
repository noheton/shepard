---
stage: deployed
last-stage-change: 2026-07-14
---

# APISIMP Sweep — 2026-07-14 (fire-599)

**Scope:** Full v2 REST surface + admin endpoints + spatiotemporal plugin.
**Prior sweep:** fire-594 (`apisimp-sweep-2026-07-14-fire594.md`) — all F1-F8 rows dispatched or shipped.

---

## What I found

Ten new findings across three categories:

| # | ID | Category | Size | File | Blocked? |
|---|-----|----------|------|------|----------|
| 1 | APISIMP-ADMIN-ONTOLOGIES-NO-PAGINATION | B (fake-paged) | XS | `SemanticAdminRest.java:276` | No |
| 2 | APISIMP-ADMIN-INSTANCE-ADMINS-NO-PAGINATION | B (fake-paged) | XS | `InstanceAdminRest.java:119` | No |
| 3 | APISIMP-ADMIN-GIT-CREDENTIALS-NO-PAGINATION | B (fake-paged) | XS | `AdminUserGitCredentialRest.java:237` | No |
| 4 | APISIMP-ADMIN-PLUGINS-NO-PAGINATION | B (fake-paged) | XS | `PluginsAdminRest.java:132` | No |
| 5 | APISIMP-PROVENANCE-STATS-BUCKET-ARRAY | D (timestamp) | S | `ProvenanceStatsIO.java:52,58` | No |
| 6 | APISIMP-SPATIAL-CONTAINER-ID-RESPONSE | A (numeric id) | S | `SpatialDataReferenceIO.java:25,75` | Yes — v1 frozen |
| 7 | APISIMP-SPATIAL-CONTAINER-ID-PATHPARAM | A (numeric id) | M | `SpatialDataPointRest.java:247` | Yes — v1 frozen |
| 8 | APISIMP-SPATIAL-STARTTIME-ENDTIME-IO | D (timestamp) | XS | `SpatialDataReferenceIO.java:50,53` | Yes — v1 frozen |
| 9 | APISIMP-SPATIAL-STARTTIME-ENDTIME-QUERYPARAM | D (timestamp) | S | `SpatialDataPointRest.java:251-252` | Yes — v1 frozen |
| 10 | APISIMP-SPATIAL-POINT-TIMESTAMP-ISO | D (timestamp) | XS | `SpatialDataPointIO.java:17` | Yes — v1 frozen |

Immediately dispatchable: findings 1–5.
Blocked until SPATIAL-V6-003 + PLUGIN-V2-001 ships a `/v2/spatial*/{appId}` sibling: findings 6–10.

---

## Finding 1 — APISIMP-ADMIN-ONTOLOGIES-NO-PAGINATION

**Category B — fake-paged admin endpoint**

`SemanticAdminRest.java:276`:
```java
return Response.ok(new PagedResponseIO<>(rows, rows.size(), 0, rows.size())).build();
```

`GET /v2/admin/semantic/ontologies` returns a `PagedResponseIO` wrapper but
`page` and `pageSize` query params are absent from the method signature. The
`total`/`page`/`pageSize` fields in the response are always `(N, 0, N)` — a
fake-paged pattern. Callers that check `total > pageSize` will incorrectly infer
there are more pages. At DLR-scale ontology counts (< 50 bundles), the
materialisation is harmless but the contract is misleading.

**Fix:** Add `@QueryParam("page") @PositiveOrZero @DefaultValue("0") int page` and
`@QueryParam("pageSize") @Min(1) @Max(200) @DefaultValue("50") int pageSize` to
`listOntologies()`; slice with `rows.subList(fromIdx, toIdx)` before wrapping.

---

## Finding 2 — APISIMP-ADMIN-INSTANCE-ADMINS-NO-PAGINATION

**Category B — fake-paged admin endpoint**

`InstanceAdminRest.java:119`:
```java
return Response.ok(new PagedResponseIO<>(grants, grants.size(), 0, grants.size())).build();
```

`GET /v2/admin/instance-admins` fetches all grants via
`instanceAdminService.listInstanceAdmins()` and wraps the full list as page 0 of 1.
An instance with many admin grants (rare but possible) materialises all of them
into JVM heap on every call.

**Fix:** Same pattern: add `page`/`pageSize` params; pass to service or slice in
the resource.

---

## Finding 3 — APISIMP-ADMIN-GIT-CREDENTIALS-NO-PAGINATION

**Category B — fake-paged admin endpoint**

`AdminUserGitCredentialRest.java:237`:
```java
return Response.ok(new PagedResponseIO<>(items, items.size(), 0, items.size())).build();
```

`GET /v2/admin/users/{username}/git-credentials` fetches all credentials for a
user via `gitCredentialDAO.findAllByUser(username)` and wraps the full list as
page 0 of 1. A user can have many credentials; the fake-paged shape prevents
safe iteration.

**Fix:** Same pattern.

---

## Finding 4 — APISIMP-ADMIN-PLUGINS-NO-PAGINATION

**Category B — fake-paged admin endpoint**

`PluginsAdminRest.java:132`:
```java
return Response.ok(new PagedResponseIO<>(rows, rows.size(), 0, rows.size())).build();
```

`GET /v2/admin/plugins` returns all plugin entries as page 0 of 1. Plugin count
is bounded by the deployment config so this is low-severity, but the fake-paged
shape is still inconsistent with the rest of the v2 admin surface.

**Fix:** Same pattern.

---

## Finding 5 — APISIMP-PROVENANCE-STATS-BUCKET-ARRAY

**Category D — timestamp Long**

`ProvenanceStatsIO.java:52,58`:
```java
private List<long[]> buckets;   // each entry is [bucketStartMillis, count]
private List<long[]> cumulative; // each entry is [bucketStartMillis, runningTotal]
```

Both `buckets` and `cumulative` embed the bucket start timestamp as `long`
epoch-milliseconds in position 0 of each `long[]`. The `@Schema` description
says `"bucketStartMillis"` explicitly, so callers must know the unit. Consistent
with the existing `since`/`until` ISO conversion (already `String` fields on the
same IO), the bucket timestamps should also be ISO 8601. This also prevents the
sparkline data from being correctly rendered by generic time-series widgets that
expect ISO strings.

**Option A (preferred for consistency):** Change the array element from epoch-ms
to ISO-string by switching the type to `List<Object[]>` where `[0]` is an ISO
8601 string and `[1]` is a `long` count. The `@Schema` examples update to show
`["2026-01-01T00:00:00Z", 42]`.

**Option B (simpler wire shape):** Wrap each bucket as a named `record BucketIO(String t, long count)`.
Cleaner for OpenAPI generation; schema is self-describing.

Option B is preferred for OpenAPI ergonomics but either is acceptable.

**Blocked on:** none — this is in `backend/src/main/java/…v2/provenance/io/`.

---

## Finding 6 — APISIMP-SPATIAL-CONTAINER-ID-RESPONSE ⚠️ BLOCKED

**Category A — numeric id leak in response**

`SpatialDataReferenceIO.java:25,75`:
```java
private long spatialDataContainerId;   // line 25

// line 75 in constructor:
this.spatialDataContainerId = ref.getSpatialDataContainer() != null
    ? ref.getSpatialDataContainer().getId() : -1;
```

`GET /v2/dataobjects/{appId}/references` (and the v1 collection path) returns
`SpatialDataReferenceIO` with `spatialDataContainerId` set to the Neo4j internal
`Long getId()`. A caller receiving this response has no `appId` for the referenced
spatial container — they must make a secondary call knowing only the numeric id.
`-1` as a sentinel for "no container" is also non-standard.

**BLOCKED:** `SpatialDataReferenceIO` is the response shape for
`/collections/{collectionId}/dataObjects/{dataObjectId}/spatialDataReferences`
which appears in `openapi-5.4.0.json` — frozen upstream-compat surface. Changing
`spatialDataContainerId` type on the existing resource would break third-party
clients.

**Fix path (SPATIAL-V6-003):** Ship a sibling `/v2/spatial-data-references/{appId}`
with `spatialDataContainerAppId: string` (UUID v7) in place of the numeric id.
The existing v1 resource stays frozen.

---

## Finding 7 — APISIMP-SPATIAL-CONTAINER-ID-PATHPARAM ⚠️ BLOCKED

**Category A — numeric id as path param**

`SpatialDataPointRest.java:247,285`:
```java
@PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) @NotNull @PositiveOrZero Long containerId
```

`GET /shepard/api/spatialDataContainers/{spatialDataContainerId}/payload` and
`POST .../payload` use a Neo4j numeric `Long` as the path parameter. This is the
v1 path exposed in `openapi-5.4.0.json`.

**BLOCKED:** Class-level `@Path(Constants.SHEPARD_API + "/" + Constants.SPATIAL_DATA_CONTAINERS)`
— this is the frozen v1 surface. No modification to the `@PathParam` type or value.

**Fix path (PLUGIN-V2-001):** New `/v2/spatial-data-containers/{appId}/payload`
endpoints in a separate resource class.

---

## Finding 8 — APISIMP-SPATIAL-STARTTIME-ENDTIME-IO ⚠️ BLOCKED

**Category D — timestamp Long**

`SpatialDataReferenceIO.java:50,53`:
```java
private Long startTime;  // epoch-ms
private Long endTime;    // epoch-ms
```

Epoch-ms Longs without unit annotation. These persist the filter bounds saved
on the `SpatialDataReference` entity and appear in the v1 response shape.

**BLOCKED:** Same frozen surface as Finding 6. Fix on the v2 sibling shelf
with `startTime`/`endTime` as ISO 8601 strings.

---

## Finding 9 — APISIMP-SPATIAL-STARTTIME-ENDTIME-QUERYPARAM ⚠️ BLOCKED

**Category D — timestamp Long**

`SpatialDataPointRest.java:251-252`:
```java
@QueryParam("startTime") @PositiveOrZero Long startTime,
@QueryParam("endTime") @PositiveOrZero Long endTime,
```

Query params for time-range filtering on the data-point query endpoint. Epoch-ms
without unit annotation (though `SpatialDataPointIO.java` describes nanoseconds
for the point timestamp — the startTime/endTime here are milliseconds, creating a
within-the-same-plugin unit inconsistency).

**BLOCKED:** Same frozen v1 path as Finding 7. Fix on the v2 sibling endpoint
with ISO 8601 strings.

---

## Finding 10 — APISIMP-SPATIAL-POINT-TIMESTAMP-ISO ⚠️ BLOCKED

**Category D — timestamp Long**

`SpatialDataPointIO.java:17`:
```java
@Schema(description = "Time in nanoseconds since unix epoch. If no value is provided, the current server time is used.")
private Long timestamp;
```

Nanosecond-precision timestamp as a raw Long. The description says "nanoseconds
since unix epoch" so the unit is documented, but the type is not self-describing
and inconsistent with ISO 8601 convention across the v2 surface. Constructor line
46 converts: `Instant.now().toEpochMilli() * 1_000_000`. The nanosecond precision
may be genuinely required for sub-millisecond spatial data resolution.

**BLOCKED:** `SpatialDataPointIO` is consumed by the frozen v1 `POST
/shepard/api/spatialDataContainers/{id}/payload`. Fix on the v2 sibling with
consideration for whether nanosecond precision must be preserved as a numeric or
can be an ISO 8601 string with nanosecond fractional seconds (ISO supports 9-digit
sub-second: `2026-07-14T12:00:00.123456789Z`).

---

## Opportunities

1. **Batch the 4 fake-paged admin fixes** (findings 1–4) in a single XS PR — all
   in `backend/.../v2/admin/`. Estimated 1 hour.
2. **ProvenancStats bucket rename** (finding 5) is an independent S-size PR; could
   go with the admin batch or separately.
3. **Spatial findings 6–10** become dispatchable once SPATIAL-V6-003 ships the
   `/v2/spatial-data-containers/{appId}` sibling resource. File them now so
   SPATIAL-V6-003 can reference them as unblocked by its completion.

---

## Gaps & blockers

- **SPATIAL-V6-003 (PLUGIN-V2-001):** Until the v2 sibling endpoint for spatial
  data exists, all 5 spatial findings are unactionable without touching the frozen
  surface. The SPATIAL-V6-003 design doc should cite all five findings.
- **ProvenanceStats bucket shape:** If Option B (`record BucketIO`) is chosen,
  it is a wire-breaking change for any caller parsing the `long[]` array. The
  current `ProvenanceStatsIO` is under `/v2/provenance/stats` (not the frozen
  surface), so the breaking change is in scope but needs an `aidocs/34` row.

---

## What surprised me

The spatiotemporal plugin's `SpatialDataPointIO.timestamp` is nanoseconds while
`SpatialDataReferenceIO.startTime`/`endTime` are milliseconds — both are `Long`
with no unit annotation on the field itself. A caller hitting
`GET /v2/dataobjects/{appId}/references` and then
`GET /shepard/api/spatialDataContainers/{id}/payload?startTime=…` must know to
multiply the reference's epoch-ms by 1,000,000 to convert to the point query's
nanoseconds. This is an undocumented unit impedance mismatch within the same plugin
that will silently return empty results on wrong-unit queries. The v2 sibling
should convert both to ISO 8601 at the resource boundary, eliminating the mismatch.
