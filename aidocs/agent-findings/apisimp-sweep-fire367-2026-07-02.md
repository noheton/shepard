---
stage: fragment
last-stage-change: 2026-07-02
---

# APISIMP REST Surface Sweep — fire-367 (2026-07-02)

Axes scanned: 4 (fake-paged wrappers · uncapped lists · admin list inconsistency ·
transient-list wrapper mismatch). Scope: `backend/src/main/java/de/dlr/shepard/v2/**/*.java`.

---

## Axis 4 — Inconsistent / broken pagination (9 findings)

### F1 — APISIMP-SNAPSHOT-MANIFEST-FAKE-PAGED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotRest.java` |

`GET /v2/snapshots/{snapshotAppId}/manifest` wraps the entire snapshot entry list in
`new PagedResponseIO<>(entries, entries.size(), 0, entries.size())` — always `page=0`
and `pageSize=entries.size()`. A collection snapshot of 50 000 DataObjects yields a
50 000-row unbounded payload with no `?page=`/`?pageSize=` parameters.

**Fix:** Add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page` and
`@QueryParam("pageSize") @DefaultValue("200") @Min(1) @Max(1000) int pageSize`;
slice `entries` with `subList(from, to)`.

---

### F2 — APISIMP-DQR-EVALUATE-BARE-LIST

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java` |

`POST /v2/collections/{collectionAppId}/dqr/evaluate` returns a bare `List<DQRResultIO>`
with no size cap. A collection with 10 000 DataObjects and 5 enabled DQRs generates a
50 000-row array in a single synchronous response.

**Fix:** Add a server-side cap (default 5 000); return a `DQRResultsIO` envelope with
`{ results: [...], truncated: true/false, total: N }`.

---

### F3 — APISIMP-IMPORT-DIAG-EVENTS-BARE-LIST

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportDiagnosticsV2Rest.java` |

`GET /v2/import/diagnostics/{runId}` returns `Response.ok(events).build()` where
`events` is `List<EventIO>` with no size cap. No `?limit=` parameter. Unlike the
sibling `GET /v2/import/runs` which wraps in `PagedResponseIO`.

**Fix:** Add `@QueryParam("limit") @DefaultValue("5000") @Min(1) @Max(10000) int limit`;
slice events; add `X-Truncated: true` header when full list exceeds limit.

---

### F4 — APISIMP-NOTEBOOK-LIST-FAKE-PAGED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/NotebookRest.java` |

`GET /v2/data-objects/{dataObjectAppId}/notebooks` wraps all `.ipynb` file references
in `new PagedResponseIO<>(result, result.size(), 0, result.size())`. No `?page=`/
`?pageSize=` parameters. The `PagedResponseIO` shape implies navigable pages that
don't exist. Notebooks per DataObject are naturally small (< 20).

**Fix:** Strip the fake-paged wrapper; return `List<NotebookReferenceIO>` directly
with `SchemaType.ARRAY`. One-line change.

---

### F5 — APISIMP-ADMIN-CONFIG-LIST-BARE

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java` |

`GET /v2/admin/config` returns a bare `List<ConfigFeatureIO>` via
`Response.ok(rows).build()` while every other admin list endpoint wraps in
`PagedResponseIO`. The `@Schema` annotation says `SchemaType.ARRAY` while siblings
use `implementation = PagedResponseIO.class`. Generated clients handling all admin
list endpoints uniformly will fail to parse this one.

**Fix:** Wrap in `new PagedResponseIO<>(rows, rows.size(), 0, rows.size())` (fake-paged
is fine — config features are bounded to ≤ 15 entries); update `@Schema`.

---

### F6 — APISIMP-INSTANCE-ADMIN-FAKE-PAGED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java` |

`GET /v2/admin/instance-admins` and `GET /v2/admin/permission-audit` both use
`new PagedResponseIO<>(items, items.size(), 0, items.size())` with no `?page=`/
`?pageSize=` parameters. The `permission-audit` endpoint calls
`permissionAuditService.listOrphans()` which may return many records on a
poorly-migrated instance.

**Fix:** Add `?page=`/`?pageSize=` (default/max appropriate to each list) and slice.
Alternatively for `listInstanceAdmins` (typically ≤ 5 rows) drop `PagedResponseIO`
entirely and return a bare typed array.

---

### F7 — APISIMP-GIT-CRED-ADMIN-FAKE-PAGED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/users/AdminUserGitCredentialRest.java` |

`GET /v2/admin/users/{username}/git-credentials` returns
`new PagedResponseIO<>(items, items.size(), 0, items.size())`. No `?page=`/
`?pageSize=` params. A user has a small, bounded number of credentials.

**Fix:** Strip the fake-paged wrapper; return `List<AdminGitCredentialListItemIO>`
directly with `SchemaType.ARRAY`. Same pattern as APISIMP-NOTEBOOK-LIST-FAKE-PAGED.

---

### F8 — APISIMP-IMPORT-RUNS-FAKE-PAGED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportDiagnosticsV2Rest.java` |

`GET /v2/import/runs` returns `new PagedResponseIO<>(runs, runs.size(), 0, runs.size())`.
The list of in-memory diagnostic run summaries is transient (evicted after 24 h,
reset on restart). `PagedResponseIO` implies paginable data that cannot exist. The
`total` always equals `pageSize`.

**Fix:** Replace with `Response.ok(runs).build()` and `SchemaType.ARRAY` in the
`@APIResponse` schema. The list is bounded by in-flight imports (typically < 50).

---

### F9 — APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotPinnedReadRest.java` |

`GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects`
embeds the full `List<String>` of DataObject appIds in `SnapshotDataObjectsIO` with
no pagination or size cap. A 50 000-DataObject collection snapshot serialises ~3 MB
of UUID strings in one response.

**Fix:** Add `?page=`/`?pageSize=` (default 500, max 2000); slice; add
`totalDataObjects` and pagination fields to `SnapshotDataObjectsIO`.

---

## Summary table

| ID | Severity | Size | Description |
|---|---|---|---|
| APISIMP-SNAPSHOT-MANIFEST-FAKE-PAGED | MINOR | S | Snapshot manifest unbounded — no `?page=`/`?pageSize=` params |
| APISIMP-DQR-EVALUATE-BARE-LIST | MINOR | S | DQR evaluate returns uncapped bare list (up to 50 000 rows) |
| APISIMP-IMPORT-DIAG-EVENTS-BARE-LIST | MINOR | XS | Import diagnostics event list uncapped, no `?limit=` |
| APISIMP-NOTEBOOK-LIST-FAKE-PAGED | MINOR | XS | Notebook list fake-paged with no navigation params |
| APISIMP-ADMIN-CONFIG-LIST-BARE | MINOR | XS | `GET /v2/admin/config` bare list while siblings use `PagedResponseIO` |
| APISIMP-INSTANCE-ADMIN-FAKE-PAGED | MINOR | XS | Two InstanceAdminRest lists fake-paged; permission-audit potentially large |
| APISIMP-GIT-CRED-ADMIN-FAKE-PAGED | MINOR | XS | Git credential admin list fake-paged with no navigation params |
| APISIMP-IMPORT-RUNS-FAKE-PAGED | MINOR | XS | Import runs list fake-paged on a transient in-memory list |
| APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED | MINOR | S | Snapshot pinned DO list unbounded — up to 3 MB for large snapshots |
