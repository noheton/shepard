---
stage: fragment
last-stage-change: 2026-06-12
---

# APISIMP sixth-pass sweep — 2026-06-12

Scope: v2 REST surface post-APISIMP-SWEEP-2026-06-12-2. Focused on response-body
numeric-id leaks in IO base classes + the permission audit log endpoint.

## What I found

**Remaining pagination `size` (not new):** The 7 endpoints covered by APISIMP-PAGINATION-UNIFY-1
(#1847, stuck ⚠️) still carry `@QueryParam("size")` on main. Not a new finding — already tracked.

**`BasicEntityIO` / `BasicReferenceIO` — systemic numeric-id base classes:**

`BasicEntityIO` (`backend/src/main/java/de/dlr/shepard/common/neo4j/io/BasicEntityIO.java:22`)
exposes `private Long id` in all v2 entity responses (Collections, DataObjects, References,
Containers, …). `BasicReferenceIO` (`context/references/basicreference/io/BasicReferenceIO.java:16`)
adds `private long dataObjectId` (parent DataObject's numeric Neo4j id) in all v2 reference
responses. Both classes are shared with the v1 surface — removing fields directly would break the
frozen v1 wire shape. The correct fix is a v2-specific subclass that omits the fields; v1 responses
stay on the original base. These are M–L design-first tasks.

**`PermissionAuditLogEntryIO` — Postgres BIGSERIAL id in wire response:**

`backend/src/main/java/de/dlr/shepard/v2/admin/io/PermissionAuditLogEntryIO.java:22`
exposes `private long id` (Postgres BIGSERIAL PK from `permission_audit_log`). The table
migration (`V1.10.0__add_permission_audit_log_table.sql`) has no `app_id` column. The endpoint
is `GET /v2/admin/permission-audit/log` (admin-only, F3). Fix: SQL migration adds
`app_id UUID NOT NULL DEFAULT gen_random_uuid()` + unique index; IO exposes `appId: String`,
drops `id: long`. S-sized.

## Findings

| # | Slug | Size | File | Wire impact |
|---|---|---|---|---|
| 1 | APISIMP-BASICENTITY-DROP-ID | M–L | `BasicEntityIO.java:22` | `id: Long` in ALL v2 entity responses; v2-specific subclass needed |
| 2 | APISIMP-BASICREF-DATAOBJECTID | S–M | `BasicReferenceIO.java:16` | `dataObjectId: long` in ALL v2 reference responses; v2-specific subclass needed |
| 3 | APISIMP-PERM-AUDIT-LOG-APPID | S | `PermissionAuditLogEntryIO.java:22` + `V1.10.0` migration | Postgres BIGSERIAL in admin audit-log wire response |

## Skipped (already filed or not actionable)

- APISIMP-CONTAINER-SUMMARY-IO-DROP-ID — already filed (blocked by CONTAINER-V2-ROUTE)
- APISIMP-PAGINATION-UNIFY-1 — already in pending PR #1847
- `PermissionAuditEntryIO.id` — Neo4j orphan-node diagnostic, required for nodes that lack appId;
  decommission-after-L2
- AAS plugin `@QueryParam("size")` — IDTA REST standard pagination, Tier-3 allowlisted plugin
- `ThumbnailRest @QueryParam("size")` — image pixel dimension, not pagination

## Rows filed in aidocs/16

APISIMP-BASICENTITY-DROP-ID, APISIMP-BASICREF-DATAOBJECTID, APISIMP-PERM-AUDIT-LOG-APPID.
