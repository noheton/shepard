---
stage: deployed
last-stage-change: 2026-06-14
---

# APISIMP Sweep — 2026-06-14 (fire 23)

**Surface scanned:** `/v2/` REST resources in `backend/src/main/java/de/dlr/shepard/v2/` and
`plugins/*/src/main/java/` (all `@Path` annotations). Targeted sweep following fire-20 comprehensive
sweep (which filed batches 1–16 + residuals).

**Not re-filed (already deferred):**
- `APISIMP-NUMERIC-ID-BATCH-2` — spatiotemporal plugin frozen upstream-compat surface
- `APISIMP-V1-PATH-RESIDUAL-1` — same
- `APISIMP-PAGINATION-UNIFY-RECREATE` — in-flight PR #1887 (blocked on CodeQL summary check)

---

## What I found

| Category | Result |
|----------|--------|
| `ApiError` in `/v2/` | ✅ CLEAN — 0 remaining instances |
| `@PathParam` numeric types in `/v2/` | ✅ CLEAN — all use `String appId` |
| `@QueryParam("size")`/`"page-size"` | ⚠️ 8 endpoints tracked in PR #1887 (in-flight) |
| Forbidden `SHEPARD_API` in `/v2/` | ✅ CLEAN |
| New `/v2/` endpoints since fire-20 | ✅ CLEAN — only RFC 7807 envelope fixes |
| `aidocs/**` missing `stage:` | ✅ CLEAN |
| **Numeric `id` field in v2 IO response** | ⚠️ **1 new finding** — `PermissionAuditEntryIO.java:21` |

---

## Opportunities

### APISIMP-PERMAUDIT-NUMERIC-ID (NEW — filed in aidocs/16)

`PermissionAuditEntryIO.java:21` exposes `private long id;` (Neo4j internal node ID) in the
`GET /v2/admin/permission-audit` response. This is the only remaining numeric Neo4j ID in a v2
response IO class (all others were cleaned in the fire-20 APISIMP-BASICENTITY-DROP-ID batches).

**Context:** The field serves a legitimate diagnostic purpose — entities without a permissions edge
(`BasicEntity` nodes lacking `:has_permissions`) are listed, and some pre-migration entities may have
`appId = null`. The numeric `id` gives operators a handle to identify those entities in the graph.

**Fix:** Rename `private long id;` → `private Long neo4jNodeId;` (boxed type, same nullable semantics
as `appId`). Update `@Schema` description and the `PermissionAuditService` constructor call.
This preserves triage value while making the semantics explicit: `neo4jNodeId` is an implementation
detail exposed only in this diagnostic admin endpoint when `appId` is null.

**AC:** `PermissionAuditEntryIO` has no field named `id`; field `neo4jNodeId` carries the existing
Neo4j internal id value; `mvn verify -pl backend` green.

**Size:** XS. **Files:** `v2/admin/io/PermissionAuditEntryIO.java:21`, `v2/admin/services/PermissionAuditService.java:59`.

---

## Ideas

- Once `APISIMP-PAGINATION-UNIFY-RECREATE` (#1887) merges, run a targeted check that 0 v2 endpoints
  still use `?size` or `?page-size`.
- The `PermissionAuditLogEntryIO` (distinct from `PermissionAuditEntryIO`) should also be reviewed
  for numeric ID exposure — spot-checked clean (uses `appId` + `resourcePath` + timestamp only).

---

## Real-world impact

- Low: `GET /v2/admin/permission-audit` is an admin-only endpoint surfacing orphan entities.
  The rename is a wire-shape change on an admin endpoint (pre-production v2 surface → no shim needed).
- Operator consuming this endpoint will need to update from `.id` to `.neo4jNodeId` — document in PR.

---

## Gaps & blockers

- PR #1887 CodeQL summary check remains the only open blocker. Both analysis jobs pass. Operator
  needs to dismiss the pre-existing code scanning alert to unblock the queue.

---

## What surprised me

The v2 surface is now very clean. After the fire-20 batch, only one numeric-ID leak remains in a
response IO class, and it's in a diagnostic admin endpoint with a legitimate (but improvable) reason.
The `ApiError` cleanup is complete. Pagination still has 8 endpoints in-flight but that's tracked.
