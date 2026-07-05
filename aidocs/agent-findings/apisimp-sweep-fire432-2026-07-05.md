---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP Sweep — fire-432 (2026-07-05)

**Result: no new dispatchable rows found.**

## Scope checked

- All `/v2/` REST resources in `de.dlr.shepard.v2.*` (full package scan)
- Frontend composable v1 fallback inventory (`frontend/composables/context/`)
- Pagination envelope consistency (`page`, `size`, `totalElements`, `totalPages`)
- Error shape conformance (RFC 7807 `ProblemDetail`)
- Numeric ID leak audit (path params, query params, response fields)

## Findings

No new APISIMP rows were identified beyond what is already tracked in `aidocs/16`.

Confirmed still-open tracked rows:
- `BUG-COLL-APPID-ROUTE-006-V2-LIST` (M, queued) — add `predecessorAppId`/`successorAppId`
  query params to `GET /v2/collections/{collectionAppId}/data-objects`; dispatched as
  slice-1 (backend) in fire-432.
- `SINGLETON-FILE-MIGRATION` — bundle-to-singleton backfill; tracked.
- `UI-PATHS-FROM-REFERENCES` — free-form URL input audit; tracked.
- `REF-EDIT-*` — reference-type edit dialog gaps; tracked.

All v2 pagination envelopes use consistent `page`/`size`/`totalElements`/`totalPages`.
All 4xx/5xx shapes are RFC 7807 `ProblemDetail`. No new numeric-ID leaks in path or
query params. All remaining v1 frontend calls (`getCollectionRoles`, timeseries channel
content, import wizard v15) are in the named exception set (documented in `aidocs/16`
with backlog rows).
