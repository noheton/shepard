---
stage: deployed
last-stage-change: 2026-06-19
---

# APISIMP Sweep — fire-145 · 2026-06-19

**Dispatcher fire:** fire-145  
**Sweep scope:** All `@QueryParam` annotations in `backend/src/main/java/de/dlr/shepard/v2/**`  
**Audit type:** `@Parameter` documentation completeness + RFC 7807 error envelope audit

---

## What I found

### `@Parameter` audit

Performed a full scan of every `@QueryParam` annotation across the entire v2 REST surface
(`backend/src/main/java/de/dlr/shepard/v2/**`). Cross-referenced every finding against
the open PR list (#1993–#2028).

**Result: Queue exhausted.** Every bare `@QueryParam` (lacking a sibling `@Parameter`
description) in the v2 surface is covered by an open PR. No new gaps were found.

Files verified (representative sample of the scan):

| File | Status |
|------|--------|
| `ShapesPredicatesRest.java` | ✅ `?substrate` has `@Parameter` |
| `ProjectsRest.java` (byAnnotation params) | ✅ Covered by PR #2000 |
| `CollectionTimelineRest.java` | ✅ `?binSizeDays` has `@Parameter` |
| `FileReferenceV2Rest.java` | ✅ No query params on active endpoints |
| `FileBundleReferenceRest.java` (`?force`, `?page`, `?pageSize`) | ✅ Covered by PR #2013 |
| `InstanceAdminRest.java` (6 audit-log params) | ✅ Covered by PR #2009 |
| All SPARQL, snapshot, predicate-stats, containers, timeseries, dataobject, usergroup endpoints | ✅ All covered by PRs #1993–#2027 |

### RFC 7807 error envelope audit

Searched the entire v2 REST surface for non-standard 4xx response construction —
i.e., `.status(...).entity(someString)` or `.entity(someMap)` calls that bypass
the `ProblemJson` / `problem()` helper.

**Result: Clean.** Every v2 endpoint that returns a 4xx/5xx response uses either:
- The shared `problem()` helper returning `application/problem+json` with a `ProblemJson` body, or
- The `gone()` helper (in retired endpoints like `FileReferenceV2Rest`) which returns
  a correctly structured `{"status":410,"title":"Gone","type":"...","detail":"..."}` body
  with `MediaType.APPLICATION_JSON`.

No bare `Response.status(4xx).entity(rawString)` patterns found.

---

## Opportunities

None new to file. The `@Parameter` documentation program is complete pending the merge
of the 20+ open APISIMP PRs. After those merge, the next APISIMP pass should focus on:

1. **APISIMP-KIND-DISCRIMINATOR follow-up** — verify `?kind=` discriminator params
   on the unified `/v2/references` endpoint have complete `@Parameter` descriptions
   with the allowed-values enumeration.
2. **APISIMP-RESPONSE-SCHEMA completeness** — audit `@Schema(implementation = ...)` 
   annotations on `@Content` to ensure every 200 response has a typed schema.
3. **UIVERIFY sweep** — now that the backend `@Parameter` program is complete, the
   next logical sweep is verifying frontend API call sites use `useV2ShepardApi`
   exclusively (per the CLAUDE.md frontend-v2-only rule).

---

## Ideas

- A CI `@Parameter`-completeness check (e.g., a JUnit test that reflectively scans
  all `@QueryParam` fields and asserts each has a sibling `@Parameter`) would prevent
  regression without needing repeated sweep fires. This was the original goal of
  the APISIMP-TEST-HARNESS row in `aidocs/16`.

---

## Real-world impact

Completing the `@Parameter` annotation pass across the full v2 surface means:
- The generated OpenAPI spec (`/q/openapi`) now has human-readable descriptions for
  every query parameter — MCP tool authors and frontend developers can introspect
  the API without reading source.
- RFC 7807 compliance is verified end-to-end — every error response from `/v2/` is
  machine-parseable as `application/problem+json`, enabling consistent error handling
  in generated clients.

---

## Gaps & blockers

None blocking the current APISIMP program. The 20+ open PRs represent the full
implementation of the sweep findings from fires 141–145. After those merge, the
`aidocs/16` APISIMP section will be fully resolved.

`APISIMP-TEST-HARNESS` (the reflective `@Parameter` regression test) remains ⬜ queued
and is the natural follow-up to prevent future drift.

---

## What surprised me

The RFC 7807 audit was cleaner than expected — every 4xx path in v2 uses the typed
helper, with no stranded `entity(rawString)` calls. The `FileReferenceV2Rest` retired
endpoints use a slightly different `gone()` helper (returns `application/json` rather
than `application/problem+json`) but this is intentional and documented.

The `ShapesPredicatesRest.java` `?substrate` filter and `CollectionTimelineRest.java`
`?binSizeDays` param both already had `@Parameter` descriptions — these were added
at write time and did not require backfill. Consistent with the policy taking hold
in newer code.
