---
stage: deployed
last-stage-change: 2026-07-16
fire: fire-627
---

# APISIMP Sweep — 2026-07-16 (fire-627)

Post-fire-626 sweep. This fire merged PR #2595 (NOTEBOOK-INMEM-PAGE) and PR #2596
(VOCAB-USED-BY-INMEM), closing the last two rows filed by fire-623. No named APISIMP
row was dispatchable; this sweep was run to find the next slice.

Previous sweeps: fire-622, fire-623, fire-612, fire-577, fire-572, fire-570.

---

## Scope

97 v2 REST resource files checked against:
- Per-kind endpoint sprawl
- Bespoke `*ConfigRest` outside generic registry
- Numeric Neo4j id leaks in `@PathParam` / `@QueryParam` / IO body
- Inconsistent pagination param names
- Missing `X-Total-Count` on paged endpoints (runtime + @APIResponse declaration)
- Fake `PagedResponseIO` (no real paging params)
- In-memory pagination anti-patterns (load-all + subList)
- Missing `@Header` in `@APIResponse` on paged endpoints
- Forbidden `@Path(Constants.SHEPARD_API + …)` additions

## Clean-gate result

- **Forbidden v1 paths**: zero new `@Path(Constants.SHEPARD_API + …)` in v2 namespace.
- **Numeric Long `@PathParam`/`@QueryParam`**: zero new leaks.
- **Bespoke admin ConfigRest**: none. Generic registry is the only pattern.
- **Pagination param names**: all paged endpoints use `page` + `pageSize` consistently.
- **Per-kind endpoint sprawl**: `ContainersV2Rest` and `ReferencesV2Rest` consistently use `?kind=`.
- **`@Max` outliers**: no pageSize caps above 200 in v2 REST files.
- **VOCAB-USED-BY-INMEM** (previously stale on this fire's first scan): confirmed FIXED in PR #2596 (merged this fire).

---

## Finding F1 — APISIMP-CONTAINERS-XCOUNT-GAPS (size: XS)

**`ContainersV2Rest.getBulkChannelData` missing `X-Total-Count` header at runtime;
`listChannelAnnotations` + `listTemporalAnnotations` missing `@Header` in `@APIResponse`.**

### F1a — runtime gap: `getBulkChannelData`

`POST /v2/containers/{appId}/channels/data/bulk`
(`ContainersV2Rest.java:829`):

```java
return Response.ok(new PagedResponseIO<>(out, out.size(), 0, out.size()))
    .build();
```

The `PagedResponseIO` wrapper signals pagination semantics, but the `X-Total-Count`
header is never emitted. Every other paged v2 endpoint sends it; this one breaks
the contract silently.

**Fix:** `.header("X-Total-Count", (long) out.size())` before `.build()` (one-liner).
Also add `headers = @Header(...)` to the `@APIResponse(responseCode="200")` block
at line ~791.

### F1b — OAS doc gap: `listChannelAnnotations` + `listTemporalAnnotations`

Both `@APIResponse(responseCode="200")` blocks carry
`content = @Content(schema = @Schema(implementation = PagedResponseIO.class))`
but are missing `headers = @Header(name = "X-Total-Count", ...)`.

The `TimeseriesContainerKindHandler` emits the header at runtime (via `result.get()`
which is a `Response` built by the handler), so there is no runtime regression.
The OpenAPI spec is incomplete: code-generated clients silently discard the header.

**Fix:** Add `headers = @Header(name = "X-Total-Count", description = "Total count before paging.", schema = @Schema(implementation = Long.class))` to both `@APIResponse` blocks.

**AC:** All three `@APIResponse` blocks formally declare `X-Total-Count`. `getBulkChannelData`
emits the header at runtime. `mvn verify -pl backend` green.

**First refs:**
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:791` (getBulkChannelData @APIResponse)
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:829` (runtime)
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1040` (listChannelAnnotations @APIResponse)
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1162` (listTemporalAnnotations @APIResponse)

---

## Finding F2 — APISIMP-NOTIF-TRANSPORT-INMEM (size: S)

**`NotificationTransportRest.listNotificationTransports()` loads all transports in memory**

`GET /v2/admin/notifications/transports` (`NotificationTransportRest.java:98`):

```java
List<NotificationTransportReadIO> items = service.listAll()
    .stream()
    .map(NotificationTransportReadIO::from)
    .toList();
long from = (long) page * pageSize;
List<NotificationTransportReadIO> slice = from >= items.size()
    ? List.of()
    : items.subList((int) from, (int) Math.min(from + pageSize, items.size()));
```

Same load-all + subList pattern as NOTEBOOK-INMEM-PAGE / VOCAB-USED-BY-INMEM.
`service.listAll()` calls `dao.findAll()` with no SKIP/LIMIT. The collection is
naturally bounded (few transports per deployment), so this is low severity — but
it's the same technical debt shape.

**Fix:** Add `countAll()` + `listPaged(skip, limit)` to `NotificationTransportDAO`,
delegate to the service layer, and remove the in-memory slice from the REST handler.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/resources/NotificationTransportRest.java:98`;
`backend/src/main/java/de/dlr/shepard/v2/notifications/transport/services/NotificationTransportService.java:33`.

---

## Finding F3 — APISIMP-TEMPLATE-TAGS-INMEM (size: S)

**`ShepardTemplateRest.tags()` loads all distinct tags then slices in memory**

`GET /v2/templates/tags` (`ShepardTemplateRest.java:318`):

```java
List<String> all = dao.listDistinctTags(kind);
long total = all.size();
int skip = (int) Math.min((long) page * pageSize, total);
List<String> slice = all.subList(skip, (int) Math.min((long) skip + pageSize, total));
```

`dao.listDistinctTags(kind)` returns all distinct tag strings (DISTINCT Cypher result,
no LIMIT). An instance with many templates and diverse tag vocabularies loads the full
string list on every tag-autocomplete keystroke. Severity is moderate — tag counts
are bounded by template count but could be O(thousands) on large deployments.

**Fix:** Add `countDistinctTags(kind)` + `listDistinctTagsPaged(kind, skip, limit)`
to the template DAO, add `SKIP $skip LIMIT $limit` to the Cypher, and remove the
in-memory slice from the REST handler.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/ShepardTemplateRest.java:318`;
`backend/src/main/java/de/dlr/shepard/v2/template/daos/ShepardTemplateDAO.java` (add new methods).

---

## Filed rows

- `APISIMP-CONTAINERS-XCOUNT-GAPS` (XS) → `aidocs/16` — dispatch this fire
- `APISIMP-NOTIF-TRANSPORT-INMEM` (S) → `aidocs/16` — queued
- `APISIMP-TEMPLATE-TAGS-INMEM` (S) → `aidocs/16` — queued
