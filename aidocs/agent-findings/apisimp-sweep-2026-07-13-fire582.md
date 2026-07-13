---
stage: fragment
last-stage-change: 2026-07-13
---

# APISIMP sweep — fire-582 (2026-07-13)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings confined
to the fork's development surface; the frozen `/shepard/api/` surface was not examined.
Previous sweep: fire-572 (apisimp-sweep-fire572-2026-07-13.md).

---

## §Green — areas confirmed clean

| Area | Result |
|------|--------|
| **Epoch-ms in core v2 IO** | ✅ Complete. All absolute timestamp fields in `backend/…/v2/**/*IO.java` are ISO 8601 strings. Only `millis` fields remaining are durations (`uptimeMillis`, `bucketMillis`, `httpMeanRequestMillis`) — correctly numeric. |
| **References surface** | ✅ Unified. `ReferencesV2Rest` + `ReferenceKindHandler` SPI. No per-kind REST classes; `?kind=` discriminator on POST/GET. All path params are `String appId`. |
| **Generic config registry** | ✅ Complete. 17 `ConfigDescriptor` implementations (9 in-tree + 8 plugins) on `GET|PATCH /v2/admin/config/{feature}`. No remaining bespoke `*ConfigRest` classes that duplicate this pattern. |
| **Notification endpoints** | ✅ Clean. `NotificationRest`, `NotificationAdminRest`, `NotificationTransportRest` all use `String appId` path params. Wire timestamps (`createdAt`, `expiresAt`, `lastTestedAt`) are ISO 8601 strings via `toIso()` helpers. |
| **Pagination naming** | ✅ Consistent across 25+ endpoints: `?page` (0-based) + `?pageSize`. `ProvenanceRest`'s cursor `?limit` is a deliberate design difference (already tracked as `APISIMP-PROVENANCE-LIMIT-TO-PAGESIZE`, decision row). |
| **Admin bespoke REST** | ℹ️ 17 non-config admin REST classes exist outside `AdminConfigRest`. All are legitimately operation-oriented (one-shots, migrations, CRUD) or read-only introspection — not config registries. No new rows filed. |

---

## §F1 — Plugin epoch-ms field: `GitReferenceIO.resolvedAtMillis`

`plugins/git/src/main/java/de/dlr/shepard/context/references/git/io/GitReferenceIO.java:60`

```java
@Schema(readOnly = true, nullable = true,
  description = "Epoch-millis when `resolvedSha` was captured. Null in mode (a) v1.")
private Long resolvedAtMillis;
```

The in-tree epoch-ms campaign converted all core v2 IO classes. The git plugin was not in
scope. `resolvedAtMillis` is an absolute timestamp (when the SHA was resolved), not a
duration — it belongs in the ISO 8601 campaign. The pattern is identical to `OntologyAlignmentIO.createdAt`
(converted fire-578) and `ActivityIO.createdAt` (converted fire-576/577).

**Fix:** rename `resolvedAtMillis` → `resolvedAt`, change type to `String`, convert in
constructor via `src.getResolvedAtMillis() == null ? null : Instant.ofEpochMilli(src.getResolvedAtMillis()).toString()`.
Update `openapi.json` and the TypeScript client type.

**→ Row filed:** `APISIMP-GIT-REFERENCE-EPOCH-MS-TO-ISO` (XS)

---

## §F2 — Plain-String 400 in `ShapesPredicatesRest`

`backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesPredicatesRest.java:120–122`

```java
return Response.status(Response.Status.BAD_REQUEST)
    .entity("Unknown substrate. Allowed values: neo4j, timescaledb, postgres, garage.")
    .build();
```

Prior sweeps (`APISIMP-SHAPES-DEDUP-MISSED`, fire-481; `APISIMP-PROBLEM-HELPER-BYPASS-3/4`,
fire-483/484) covered `ShapesRenderRest`, `ShapesBuildRest`, and 86 other files. `ShapesPredicatesRest`
was missed. This returns `text/plain` content-type, not `application/problem+json`. Clients
testing error shape consistency fail here.

**Fix:** Add `import static de.dlr.shepard.v2.common.ProblemResponse.problem;` and replace
the two-line chain with `return problem(Response.Status.BAD_REQUEST, "Unknown substrate. Allowed values: neo4j, timescaledb, postgres, garage.")`.

**→ Row filed:** `APISIMP-SHAPES-PREDICATES-PROBLEM-JSON` (XS)

---

## §F3 — Raw `"[]"` early-return in `DataObjectV2Rest`

`backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java:261,272,281`

Three early-return paths return `Response.ok("[]", MediaType.APPLICATION_JSON)` with a
`Content-Range: dataobjects */0` header when a referenced entity (parent, predecessor,
successor) is not found. The normal list path returns a `PagedResponseIO` envelope with
`total`, `page`, `pageSize`, and `content` fields. Clients that destructure the
`PagedResponseIO` shape receive an inconsistent body on these paths (a JSON array instead
of an object).

**Fix:** Extract a zero-page `PagedResponseIO` response: `PagedResponseIO.empty(page, pageSize)`
(or `new PagedResponseIO<>(0L, page, pageSize, List.of())`). Return it with a `Content-Range`
header to match the existing pattern.

**→ Row filed:** `APISIMP-DO-EMPTY-LIST-ENVELOPE` (XS)

---

## §F4 — `/{id}` path param in `PluginsAdminRest`

`backend/src/main/java/de/dlr/shepard/v2/admin/plugins/PluginsAdminRest.java:137,166`

```java
@Path("/{id}")
public Response patch(@PathParam("id") String id, PluginPatchIO body, ...) {
```

Every other admin endpoint in `v2/admin/` uses `{appId}` as the path-param name (confirmed
across all 17 bespoke admin REST classes). `PluginsAdminRest` is the sole outlier using `{id}`.
The field is typed `String` (correct — plugin IDs are string keys, not numeric), but the
naming breaks OpenAPI client consistency: generated clients produce `patchPluginById` vs.
`patchPluginByAppId` for every other resource.

**Fix:** Rename `@Path("/{id}")` → `@Path("/{appId}")` and `@PathParam("id") String id` →
`@PathParam("appId") String appId`. Update the method's internal variable from `id` to
`appId`. One-line service call change.

**→ Row filed:** `APISIMP-PLUGINS-ADMIN-APPID-PATH` (XS)

---

## §Noted — not filed

| Item | Why not filed |
|------|---------------|
| `VideoStreamReferenceIO.wallClockTimestamp: Long` (nanosecond epoch) | Nanosecond precision for TM1 temporal alignment. Needs coordinated migration with the TS nanosecond surface (same family as `start`/`end` in `ContainersV2Rest`). Deferred until TS-IDb/c migration unblocks. |
| `SpatialDataReferenceIO.startTime / endTime: Long` | Spatiotemporal plugin uses frozen v1 upstream-compat surface. Conversion requires SPATIAL-V6-003 v2 sibling shelf — already planned. |
| Export fragmentation (3 classes on `/v2/collections`) | JAX-RS sub-resource pattern is idiomatic for domain grouping. Not sprawl. |
| `maxItems` on 3 non-paginated cap endpoints | Deliberately non-paginated design (single-response capped result set). Different from `page+pageSize` pagination. No row needed. |

---

## Summary

| Finding | Severity | Row filed |
|---------|----------|-----------|
| §F1 `GitReferenceIO.resolvedAtMillis` epoch-ms | Low | `APISIMP-GIT-REFERENCE-EPOCH-MS-TO-ISO` (XS) |
| §F2 `ShapesPredicatesRest` plain-String 400 | Low | `APISIMP-SHAPES-PREDICATES-PROBLEM-JSON` (XS) |
| §F3 `DataObjectV2Rest` raw `"[]"` early-return | Low | `APISIMP-DO-EMPTY-LIST-ENVELOPE` (XS) |
| §F4 `PluginsAdminRest` `/{id}` naming | Low | `APISIMP-PLUGINS-ADMIN-APPID-PATH` (XS) |

All four are XS — none blocked by other migrations. Any can be next-dispatched once the
current open PRs (#2541) are resolved.
