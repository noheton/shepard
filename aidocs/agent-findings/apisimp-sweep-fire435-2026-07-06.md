---
stage: fragment
last-stage-change: 2026-07-06
---
# APISIMP Sweep — fire-435 (2026-07-06)

Scope: full scan of `backend/src/main/java/de/dlr/shepard/v2/` REST surfaces for
API simplification violations (unbounded queries, missing pagination envelopes,
bare arrays, inconsistent error shapes).

## Overall health

After fires 300–434 the v2 surface is clean on the major categories:

- **PagedResponseIO envelope**: all list endpoints audited across fires 380–434
  now return `{items, total, page, pageSize}`. No bare-array returns found on
  the endpoints checked this fire.
- **Bounded Cypher / SKIP+LIMIT**: all in-memory-paging rows filed in fires
  416–429 are ✅ merged. No remaining `subList()` patterns found in the REST
  handlers surveyed today.
- **Error shape**: sampled 6 REST resources — all return `application/problem+json`
  via `ProblemJson` on 4xx paths.

## Findings

### F1 — APISIMP-USER-SEARCH-NO-PAGINATION

**File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserSearchV2Rest.java:67`

`GET /v2/users?q=` (operation `searchUsersV2`) returns a bare `List<UserIO>` JSON
array — no `page`/`pageSize` query params, no `PagedResponseIO` envelope, no
`X-Total-Count` header.

The underlying `UserSearchService.search()` issues an unbounded Cypher query via
`SearchDAO.findUsers()` with no `LIMIT` clause. For a deployment with thousands of
users, a query like `?q=a` can return thousands of rows in one shot.

**Wire violation:** every other v2 list endpoint returns
`{"items":[…],"total":N,"page":P,"pageSize":PS}`. This endpoint returns `[…]`.
Frontend and MCP callers expecting the standard envelope will crash or silently
show zero results.

**Fix shape:**
1. Add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page` and
   `@QueryParam("pageSize") @DefaultValue("20") @Min(1) @Max(100) int pageSize`
   to `searchUsers()`.
2. Add bounded DAO overloads `findUsers(String query, int skip, int limit)` and
   `countUsers(String query)` (or slice at the service layer if the underlying
   search framework supports it).
3. Return `Response.ok(new PagedResponseIO<>(users, total, page, pageSize)).build()`.
4. Add 2 regression tests: pagination params accepted; envelope returned.

**AC:** `GET /v2/users?q=alice&page=0&pageSize=10` returns
`{"items":[…],"total":N,"page":0,"pageSize":10}` envelope; `mvn verify -pl backend`
green.

**Filed as:** `APISIMP-USER-SEARCH-NO-PAGINATION` in `aidocs/16-dispatcher-backlog.md`.
