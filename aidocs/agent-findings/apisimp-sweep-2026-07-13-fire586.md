---
stage: fragment
last-stage-change: 2026-07-13
---

# APISIMP sweep — fire-586 (2026-07-13)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings confined
to the fork's development surface; the frozen `/shepard/api/` surface was not examined.
Previous sweep: fire-582 (apisimp-sweep-2026-07-13-fire582.md).

Context: All four APISIMP rows filed in fire-582 are now merged (GIT-REFERENCE-EPOCH-MS-TO-ISO,
SHAPES-PREDICATES-PROBLEM-JSON, DO-EMPTY-LIST-ENVELOPE, PLUGINS-ADMIN-APPID-PATH). No named
dispatchable row remains, triggering this follow-up sweep.

---

## §Findings — 2 new fake-paged list endpoints in ContainersV2Rest

### F1 — `GET /v2/containers/{appId}/files/{fileName}/versions` fake-paged

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:522`

```java
List<PayloadVersionIO> versionList = versionsOpt.get();
return Response.ok(new PagedResponseIO<>(versionList, versionList.size(), 0, versionList.size()))
    .build();
```

The endpoint wraps the full result in `PagedResponseIO` but accepts no `?page=` or `?pageSize=`
query parameters. Callers always receive the full list with `total == items.size()`, `page == 0`,
`pageSize == N` — the pagination envelope is structurally present but semantically inert.

This is the incomplete follow-up from `APISIMP-CONTAINER-LIST-ENVELOPES` (fire-324), which added
the wrapper as a quick conformance fix without wiring real pagination logic.

**Fix options:**
- Add real `@QueryParam("page") @DefaultValue("0") int page` + `@QueryParam("pageSize") @DefaultValue("50") int pageSize` params and slice `versionList` with `subList`.
- If versions are naturally small (< 20 per file in practice), drop the `PagedResponseIO` wrapper and return a plain array `List<PayloadVersionIO>`.

**Severity**: MINOR — no caller can paginate, but the dataset is naturally bounded (file versions accumulate slowly). First option preferred for consistency.

---

### F2 — `GET /v2/containers/{appId}/linked-data-objects` fake-paged

**File**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:603`

```java
List<DataObjectIO> linkedList = linkedOpt.get();
return Response.ok(new PagedResponseIO<>(linkedList, linkedList.size(), 0, linkedList.size()))
    .build();
```

Same pattern: `PagedResponseIO` envelope with no `?page=`/`?pageSize=` params. At MFFD scale
(a TimescaleDB container linked to dozens of DataObjects per process step), this will return
unbounded payloads. Unlike the versions endpoint, the linked-DataObject set can grow
proportionally to the collection size.

**Fix options:**
- Add real `?page=`/`?pageSize=` params and subList-slice. For MFFD-scale use, real pagination is preferred.
- Alternative: drop the envelope, return plain array, document as "returns all" in the OpenAPI summary.

**Severity**: MINOR-MEDIUM — bounded in current demos but unbounded under MFFD workload. Real pagination preferred.

---

## §Green — areas confirmed clean (inherited from fire-582)

| Area | Result |
|------|--------|
| **Epoch-ms in core v2 IO** | ✅ Complete — all absolute timestamps are ISO 8601 strings. |
| **References surface** | ✅ Unified under `?kind=` discriminator. |
| **Generic config registry** | ✅ 17 ConfigDescriptors on `GET\|PATCH /v2/admin/config/{feature}`. |
| **Notifications surface** | ✅ Clean — no bespoke admin REST. |
| **Pagination consistency** | ✅ `PagedResponseIO` used consistently across main list endpoints. |
| **Admin bespoke REST** | ✅ Remaining bespoke admin REST is legitimate (admin-only operations with no generic pattern). |

---

## New rows filed

| Row ID | Size | Status |
|--------|------|--------|
| `APISIMP-CONTAINER-VERSIONS-FAKE-PAGED` | XS | ⏳ queued |
| `APISIMP-CONTAINER-LINKED-DO-FAKE-PAGED` | XS | ⏳ queued |
