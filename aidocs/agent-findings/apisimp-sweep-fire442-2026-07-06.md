---
stage: deployed
last-stage-change: 2026-07-06
---
# APISIMP Sweep — fire-442 (2026-07-06)

Sweep scope: all REST resource files under
`backend/src/main/java/de/dlr/shepard/v2/` and
`plugins/*/src/main/java/` (plugin REST surfaces).

Skipped (already tracked / shipped per task brief):
APISIMP-AAS-SHELL-DO-LOAD-CAP, APISIMP-REFANNOT-IN-MEMORY-PAGING,
APISIMP-TS-CONT-ANNOT-IN-MEMORY-PAGING, APISIMP-COLL-CONTAINERS-BARE-LIST,
APISIMP-DQR-LIST-IN-MEMORY-PAGING, APISIMP-USER-SEARCH-NO-PAGINATION,
APISIMP-TSCHANNEL-CONTAINER-ID-WIRE, APISIMP-PERMISSION-AUDIT-NEO4J-ID.

---

## Sweep findings (clean passes)

| Pattern checked | Result |
|---|---|
| `subList` in-memory paging | All occurrences are either shipped (backlog rows ✅) or legitimate bounded caps (diff caps, import event caps). No new violation. |
| Unbounded `findAll()` | HDF admin rebuild-acls: bounded by provisioned containers. No new unbounded write path. |
| Fake-paged wrappers `(list, list.size(), 0, list.size())` | Plugin registry (admin, bounded ✅); container version/linked lists (APISIMP-CONTAINER-LIST-ENVELOPES ✅); predecessor/successor chains (depth-bounded, documented ✅); provenance cursor endpoints (cursor pagination design ✅). One new violation — see F1 below. |
| Numeric Neo4j id leaks in path/query params or IO | `TimeseriesChannelV2IO.id`/`containerId` marked `@Schema(deprecated=true)` with tracked rows. `@JsonIgnoreProperties` on CollectionV2IO/DataObjectDetailV2IO/DataObjectListItemV2IO all correct. No new numeric-id leak. |
| Inconsistent pagination param names (`?size` vs `?pageSize`) | Zero occurrences. `Constants.QP_SIZE` is not used in any v2 resource. |
| Missing `@Parameter` on required params | Zero occurrences. Every `@QueryParam` that previously lacked an annotation has been addressed in earlier sweeps. |
| `UserSearchV2Rest` bare list | Shipped (APISIMP-USER-SEARCH-NO-PAGINATION, fire-437, PR #2325). |

---

## F1 — APISIMP-USERGROUP-SEARCH-FAKE-PAGED (MINOR, Size XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java:114`

**Description:**
When the caller supplies `?q=`, `listUserGroups()` (line 109-114) calls
`searchService.searchByText(q)` with no SKIP/LIMIT and wraps the full result in a
fake-paged envelope:

```java
return Response.ok(new PagedResponseIO<>(items, count, 0, count == 0 ? 1 : (int) count)).build();
```

`page` is hardcoded to `0`; `pageSize` equals `count` (total result count). The
`?page=` and `?pageSize=` query params the caller supplied are silently ignored on
the search code path. The response shape implies paginable data that is never paged,
and `pageSize = count` produces a structurally misleading envelope (`pageSize: 47`,
`total: 47`, `page: 0` for every result set regardless of requested page size).

User groups are admin-bounded — cardinality is typically tens to low hundreds —
so the unbounded `searchByText()` call is not a heap hazard. The violation is
the misleading envelope, not a performance risk.

This was introduced in SEARCH-V2-4-PRE (PR #2273, fire-394) which specified the
search path should return `PagedResponseIO<UserGroupV2IO>` but the implementation
chose the fake-paged shape for simplicity. No subsequent sweep caught it.

**Fix:**
Replace the fake-paged return with a plain JSON array on the `?q=` path, matching
the `APISIMP-GIT-CRED-ADMIN-FAKE-PAGED` resolution pattern:

```java
if (q != null && !q.isBlank()) {
  List<UserGroupV2IO> items = searchService.searchByText(q).stream()
    .map(UserGroupV2IO::new)
    .toList();
  return Response.ok(items).build();
}
```

Update the `@APIResponse` for the search path to declare `SchemaType.ARRAY` with
`implementation = UserGroupV2IO.class`, matching the existing `@Parameter(name = "q")`
annotation's intent (a search returning a list, not a paged resource).

**AC:**
- `GET /v2/user-groups?q=eng` returns a plain JSON array, not a `{items, total, page, pageSize}` envelope.
- `GET /v2/user-groups` (no `q`) continues to return the proper paged envelope.
- OpenAPI schema for the `?q=` path shows `type: array`.
- `mvn verify -pl backend` green.

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-USERGROUP-SEARCH-FAKE-PAGED | MINOR | XS | new — untracked |

**Surface verdict:** The v2 REST surface is clean at fire-442. One new minor
violation found (fake-paged wrapper on user-group search path). All previous
APISIMP-* patterns have been resolved or are explicitly tracked.
