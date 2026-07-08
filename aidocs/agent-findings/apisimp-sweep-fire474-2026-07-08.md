---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — fire-474 — 2026-07-08

**Scope**: All 37 REST resource files under `backend/src/main/java/de/dlr/shepard/v2/`  
**Fire**: 474  
**Date**: 2026-07-08

## Context

All previously named APISIMP backlog rows are either ✅ done, ⛔ blocked (waiting for L2
migration), or ⏳ operator-decision-needed (DQR-ORPHAN, LEDGER-ANCHOR-ORPHAN).  PR #2401
(`APISIMP-PAGE-PARAM-MIN0`) is in-flight (CI running on rebased branch). This sweep
covers the remaining surface.

## Findings

### §A1 — Missing `X-Total-Count` response header on paginated list endpoints (12 files)

**Severity**: S  
**Row filed**: `APISIMP-XTOTALCOUNT-BATCH` in `aidocs/16`

Every paginated v2 list endpoint that computes a real `total` count and wraps items in
`PagedResponseIO<T>` **should** also emit `X-Total-Count: <total>` as a response header.
This lets lightweight clients (CLI tools, frontend composables that only need the count,
cache invalidators) read the total without deserialising the JSON body.

The pattern is already established: `DataObjectV2Rest.list()` (line 368) emits
`X-Total-Count`, `VocabularyBrowseRest.listVocabularies()` (line 116) emits it,
`VocabularyBrowseRest.listTermsForVocabulary()` (line 232) emits it. But a sweep of
every list endpoint found 17 call sites across 12 files that compute `total` correctly
and return `PagedResponseIO` but omit the header.

**Affected endpoints and files (all: add `.header("X-Total-Count", total)` before `.build()`):**

| File | Line | Endpoint | Variable |
|------|------|----------|----------|
| `SemanticAnnotationV2Rest.java` | 202 | `GET /v2/annotations` | `total` |
| `SemanticAnnotationV2Rest.java` | 258 | `GET /v2/annotations/find` | `total` |
| `DataObjectV2Rest.java` | 701–703 | `GET /v2/data-objects/{id}/predecessors` | `total` (int) |
| `DataObjectV2Rest.java` | 810 | `GET /v2/data-objects/{id}/successors` | `total` (int) |
| `DataObjectV2Rest.java` | 854 | `GET /v2/data-objects/{id}/children` | `total` (int) |
| `CollectionSnapshotRest.java` | 207 | `GET /v2/collections/{id}/snapshots` | `total` (long) |
| `SnapshotListRest.java` | 180 | `GET /v2/snapshots` | `total` (long) |
| `SnapshotRest.java` | 173 | `GET /v2/snapshots/{id}/manifest` | `total` (int) |
| `CollectionTemplatesRest.java` | 109 | `GET /v2/collections/{id}/templates` (listAllowed) | `total` (long) |
| `CollectionTemplatesRest.java` | 134 | `GET /v2/collections/{id}/templates` (listUsed) | `total` (long) |
| `ShepardTemplateRest.java` | 100 | `GET /v2/templates` | `total` (long) |
| `UserGroupV2Rest.java` | 113 | `GET /v2/user-groups?q=…` (filter branch) | `(long) items.size()` |
| `UserGroupV2Rest.java` | 121 | `GET /v2/user-groups` (paginated branch) | `total` (long) |
| `UserSearchV2Rest.java` | 87 | `GET /v2/users?q=…` | `paged.total()` |
| `BundleGroupsV2Rest.java` | 145 | `GET /v2/references/{id}/groups` | `total` (long) |
| `ShapesPredicatesRest.java` | 114 | `GET /v2/shapes/predicates` | `total` (long) |
| `VocabularyBrowseRest.java` | 169 | `GET /v2/semantic/vocabularies/{id}/predicates` | `total` (long) |

**Also (dump-all endpoints that wrap in PagedResponseIO but omit the header):**

| File | Line | Endpoint | Expression |
|------|------|----------|------------|
| `InstanceAdminRest.java` | 123 | `GET /v2/admin/instance-admins` | `(long) grants.size()` |
| `SemanticAdminRest.java` | 275 | `GET /v2/admin/semantic/ontologies` | `(long) rows.size()` |
| `PluginsAdminRest.java` | 131 | `GET /v2/admin/plugins` | `(long) rows.size()` |
| `NotificationTransportRest.java` | 88 | `GET /v2/admin/notifications/transports` | `(long) items.size()` |

### §A2 — `AdminUserGitCredentialRest.list` returns bare `List<>` instead of `PagedResponseIO`

**Severity**: XS  
**Row filed**: `APISIMP-GIT-CRED-PAGED` in `aidocs/16`

`GET /v2/admin/users/{username}/git-credentials` (line 236) returns
`Response.ok(items).build()` where `items` is `List<AdminGitCredentialListItemIO>`.
The `@APIResponse` at line 216 declares `SchemaType.ARRAY`, making this the only admin
list endpoint not wrapped in `PagedResponseIO`. Admin-only endpoint so this is
wire-safe to normalise. Per-user credential count is always tiny (< 10), so no DB-side
pagination is needed — wrap with `total = items.size(), page = 0, pageSize = items.size()`.

## Not-findings (explicitly verified)

- **Numeric `Long` path/query params**: None found in `/v2/` resources. All IDs are `String appId`. The `Long start`/`Long end` params in `ContainersV2Rest` are Unix-epoch timestamps — correct.
- **Bespoke admin config endpoints**: All are now behind `AdminConfigRest` + `ConfigDescriptor`. No unmigrated bespoke configs found.
- **`@Min(0)` on page params**: 6 files still have this, covered by in-flight PR #2401 (`APISIMP-PAGE-PARAM-MIN0`).
- **`SemanticTermSearchRest.java:244`**: `total = results.size()` because the n10s fulltext index cannot do a count-only query — known architectural limitation, not a bug.
- **`BundleGroupsV2Rest.java:376`** (`PagedFilesIO`): Non-standard envelope flagged in fire-473 sweep as wire-breaking to normalise; deferred by design.
- **Dead `SchemaType` imports**: All remaining usages are live (ARRAY for multi-item, STRING for text/binary). Fire-470 PR #2398 cleaned the dead ones.
