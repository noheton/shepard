---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP sweep — fire-477 (2026-07-08)

Scope: full v2 REST surface; focus on residual bare-list responses without
`X-Total-Count` header, following the fire-475 APISIMP-XCOUNT-BATCH-2 batch
that closed F1–F4. All fire-475 queued/in-flight rows now resolved; this sweep
finds one new XS candidate and confirms the surface is otherwise clean.

## Context — fire-475 row close-out

| Row | Status after fire-477 |
|---|---|
| APISIMP-PROV-CURSOR-PAGED-WRAP (F6) | ✅ merged (PR #2408, SHA 6e45bece) |
| APISIMP-SHAPES-BUILD-400-NOTRFC7807 (F7) | ✅ merged (fire-476, PR #2407) |
| APISIMP-MAXPOINTS-BOXED (A2) | ✅ merged (PR #2406, SHA df9b99d) |
| APISIMP-IMPORT-RUNS-BARE (F8) | ⛔ deferred (intentional design — fire-368 decision) |
| APISIMP-BUNDLE-FILES-PAGEFMT (F5) | 🔴 deferred (wire-breaking) |

## Findings

### F1 — APISIMP-ADMIN-CONFIG-NO-XCOUNT (XS) 🔄 in-flight

`AdminConfigRest.java:90` — `GET /v2/admin/config` returns
`Response.ok(rows).build()` where `rows` is a `List<ConfigFeatureIO>`.
No `X-Total-Count` header is emitted. This is the only remaining v2 list
endpoint without the header after the fire-475 batch closed everything else.

Fix: chain `.header("X-Total-Count", (long) rows.size())` on the response
builder. One line; no service change needed. The registry count is always
small (≤20 in practice) but the header convention must be uniform.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java:90`

## Not-findings / known-good

- `CollectionWatchersRest.java` — `listCollectionWatches` already emits
  `X-Total-Count` and wraps in `PagedResponseIO`. Clean.
- `ShapesApplicableRest.java` — returns `ShapesApplicableResponseIO` wrapper
  (not a bare list). Clean.
- `CollectionPermissionsRest.java` — returns single `Roles` object, not a list.
- `ImportDiagnosticsV2Rest.java:192` — already deferred as
  APISIMP-IMPORT-RUNS-BARE (fire-368 decision; bare array intentional).
- `ImportDiagnosticsV2Rest.java:154` — events endpoint uses intentional
  `X-Truncated: true` design for in-memory cap; not a paged list.
- `AdminFeaturesRest.java` — already a 410 tombstone. Not a list endpoint.
- All other list endpoints inspected already carry `X-Total-Count` or
  `PagedResponseIO`.

## Batch PR: (none this fire — single XS finding)

F1 implemented as standalone PR `APISIMP-ADMIN-CONFIG-NO-XCOUNT`.
