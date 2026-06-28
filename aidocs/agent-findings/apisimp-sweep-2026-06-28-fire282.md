---
stage: deployed
last-stage-change: 2026-06-28
---

# APISIMP sweep — fire-282 (2026-06-28)

Scope: remaining plugin REST surfaces not covered by fire-280/281. All
`plugins/*/src/main/java/**/*Rest*.java` files that were not yet scanned.

## Surfaces checked

| Plugin | Resource | Verdict |
|---|---|---|
| `aas` | `AasRegistrationAdminRest` (`GET /v2/admin/aas/registrations`) | ⚠️ Finding 1 — filed as row 3828 |
| `aas` | `AasWellKnownRest` (`GET /v2/aas/.well-known/aas-server`) | ✅ single-item, bounded |
| `aas` | `AasAdminRest` (`POST /v2/admin/aas/import-idta-templates`) | ✅ mutation only, no list |
| `hdf5` | `HdfAdminRest` (`POST /v2/admin/hdf/rebuild-acls`) | ✅ mutation only |
| `minter-datacite` | credential + test-connection endpoints | ✅ no list |
| `minter-epic` | credential + test-connection endpoints | ✅ no list |
| `spatiotemporal` | `SpatialPromoteRest` (`POST /v2/spatial/promote`) | ✅ mutation only |
| `wiki-writer` | `WikiWriterRest` (`POST /v2/data-objects/{appId}/wiki-write`) | ✅ mutation only |
| `v1-compat` | `LegacyV1StatsAdminRest` (`GET /v2/admin/legacy/v1/stats`) | ✅ bounded snapshot |

## §Finding 1 — APISIMP-AAS-REGISTRATIONS-LIST-ENVELOPE (row 3828) — XS

**Location:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/admin/resources/AasRegistrationAdminRest.java`

**Problem:** `GET /v2/admin/aas/registrations` previously returned a plain
`List<AasRegistrationIO>` via `Response.ok(rows).build()` — no
`PagedResponseIO` envelope, no `total`, no server-side page cap. In practice
the list is bounded by collection count, but nothing in the API contract
enforced that.

**Fix (PR #2148):**
- Added `MAX_PAGE_SIZE = 200` constant
- Added `?page` (default 0) and `?pageSize` (default 50, capped at 200) query params
- Added `countAll(): long` and `listAll(int page, int pageSize)` to `AasRegistrationDAO`
- Response now returns `PagedResponseIO<AasRegistrationIO>{items, total, page, pageSize}`
- 9 unit tests (7 pre-existing updated + 2 new: page-size cap, negative-page clamp)

## Status

| Row | Title | Size | Status |
|---|---|---|---|
| 3828 | APISIMP-AAS-REGISTRATIONS-LIST-ENVELOPE | XS | 🔄 in-progress (PR #2148, fire-282) |

## Sweep completeness

The plugin surface is now fully swept through fire-282. No additional unbounded
list endpoints remain untracked in plugin REST resources.
