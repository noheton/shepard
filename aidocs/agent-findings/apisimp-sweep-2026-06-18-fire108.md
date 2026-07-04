---
stage: deployed
last-stage-change: 2026-06-18
---

# APISIMP sweep — fire-108 (2026-06-18)

**Trigger:** Scheduled fire-108 — post-fire-107 check.  
**Scope:** All `/v2/` REST resources for new numeric-id leaks, bespoke admin configs, inconsistent pagination params, plain-string error bodies.  
**Previous sweep:** fire-106 (filed APISIMP-TYPED-PRED-ID + APISIMP-PAGINATION-PARAM-NAMING)

---

## Surface status

| Category | Finding |
|---|---|
| Numeric ids on wire | APISIMP-TYPED-PRED-ID (CRITICAL, XS) — fix in PR #1985, READY ✅ |
| Bespoke admin configs | None found; all admin configs through generic `/v2/admin/config/{feature}` ✅ |
| Pagination param naming | APISIMP-PAGINATION-PARAM-NAMING (MINOR, XS) — fix in PR #1986, promoted READY this fire ✅ |
| Plain-string error bodies | None new found ✅ |
| Path param shape anomalies | None new found ✅ |

**Result: CLEAN — no new APISIMP findings this sweep.**

---

## READY PRs promoted this fire

| PR | Slice | All CI green? |
|---|---|---|
| **#1986** | `APISIMP-PAGINATION-PARAM-NAMING` — `size` → `pageSize` in `UserGroupV2Rest` | ✅ 11/11 checks |

---

## Dispatch this fire

**PRED-V2-SHAPE (backend-only XS slice)**

The v2 predecessors/successors endpoint (`GET /v2/…/data-objects/{appId}/predecessors`)
returns `DataObjectSummaryIO` which is missing `createdAt` and `createdBy`. The
relationships panel uses these fields for sort-by-date and display — it currently falls
back to the v1 `getAllDataObjects` call rather than the v2 predecessor shelf.

Fix: add `createdAt` (Date, ISO-8601, NON_NULL) and `createdBy` (String display name,
NON_NULL) to `DataObjectSummaryIO`.

- File changed: `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectSummaryIO.java`
- New test: `DataObjectSummaryIOCreatedFieldsTest` (4 assertions)
- Backlog row `PRED-V2-SHAPE` updated: in-progress

Frontend migration (PRED-V2-SHAPE-2) is deferred: `deletePredecessor` in
`useUpdateDataObjectPredecessor.ts` still relies on `predecessorIds: number[]`
(patching the full DataObject via v1 shape), making the full frontend migration an M,
not an XS. Filed note in backlog row.

---

## Blocked items (unchanged)

| ID | Blocker |
|---|---|
| APISIMP-PERMISSION-AUDIT-NEO4J-ID | Gate: confirm L2 migration complete |
| APISIMP-TSCHANNEL-CONTAINER-ID slice 2 | Gated on TS-IDb/c (TimescaleDB appId migration) |
