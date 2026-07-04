---
stage: fragment
last-stage-change: 2026-06-14
---
# APISIMP Sweep — fire-40 (2026-06-14)

Scope: all Java files under `backend/src/main/java/de/dlr/shepard/v2/` and
`plugins/*/src/main/java/`.

Skipped (frozen upstream-compat): `SpatialDataPointRest`,
`SpatialDataReferenceRest` (APISIMP-NUMERIC-ID-BATCH-2 / APISIMP-V1-PATH-RESIDUAL-1,
already tracked and deferred).

Context: follows fire-34 (last sweep). Fires 35–39 dispatched all fire-34 rows
(APISIMP-FILEREF-416-BARE-RESPONSE, APISIMP-PUBLISH-REP-EXPORT-URN-FORMAT, plus
surrounding rows). Core v2 surface is now clean across all eight prior sweep
patterns. This fire scans for residuals introduced since fire-34 by the MFFD-RENDER-AFP
and APISIMP batch waves (fires 35–39).

---

## Findings

### Finding 1: APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS

**Files (3 plugin files, 4 occurrences):**

| File | Line | Absolute URI |
|---|---|---|
| `plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java` | 105 | `"https://shepard.dlr.de/problems/git.adapter.unsupported-host"` |
| `plugins/kip/src/main/java/de/dlr/shepard/plugins/kip/resources/KipResolverRest.java` | 117, 126 | `"https://shepard.dlr.de/problems/kip.pid.not-found"` |
| `plugins/v1-compat/src/main/java/de/dlr/shepard/plugins/v1compat/filters/LegacyV1GateFilter.java` | 45 | `"https://shepard.dlr.de/problems/v1-disabled"` (constant) |

**Pattern:** Pattern 8 — inconsistent error type URNs (non-standard absolute URL
format).

**What's wrong:** Three plugin files still use full `https://shepard.dlr.de/problems/…`
absolute URLs as the RFC 7807 `type` URI, embedding an operator-specific hostname in
the error contract. The fire-35 fix (`APISIMP-PUBLISH-REP-EXPORT-URN-FORMAT`, PR #1913)
normalized `PublishRest.java` and `RepExportV2Rest.java` to relative `/problems/…` paths
but did not sweep the plugin cluster. All ~95 other v2 error responses already use the
relative form.

Notes:
- `GitReferenceRest` was touched by `APISIMP-GIT-REF-PROBLEM-TYPE` (fire-32, shipped
  2026-06-12) which migrated from `LinkedHashMap` to `ProblemJson` — but the absolute
  URI was inadvertently carried forward.
- `KipResolverRest` was touched by `APISIMP-KIP-PROBLEM-HELPER` (fire-32, shipped
  2026-06-12) for the same reason.
- `LegacyV1GateFilter` in the v1-compat plugin is a filter, not a REST resource; the
  constant feeds its `Response.status(503).entity(new ProblemJson(PROBLEM_TYPE_V1_DISABLED, …))`
  body.

**Fix:** 4-occurrence string-literal replacement across 3 files:
1. `GitReferenceRest.java:105` — `"https://shepard.dlr.de/problems/git.adapter.unsupported-host"` → `"/problems/git.adapter.unsupported-host"`
2. `KipResolverRest.java:117,126` — `"https://shepard.dlr.de/problems/kip.pid.not-found"` → `"/problems/kip.pid.not-found"` (both occurrences)
3. `LegacyV1GateFilter.java:45` — constant value `"https://shepard.dlr.de/problems/v1-disabled"` → `"/problems/v1-disabled"`

AC: zero occurrences of `https://shepard.dlr.de/problems/` in any plugin file;
`mvn verify` (full including plugins) green.

**Proposed row ID:** `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS`

**Size:** XS (4 string literal changes across 3 plugin files)

---

## Clean areas

| Pattern | Result |
|---|---|
| Pattern 1: Numeric entity ids in v2 response IO | CLEAN — two tracked rows (APISIMP-TYPED-PREDECESSOR-NUMERIC-ID PR #1916, APISIMP-TSCHANNEL-CONTAINER-ID PR #1917) already have READY PRs; no new leaks found |
| Pattern 2: Plain-string error bodies (non-RFC 7807) | CLEAN — `ShepardExceptionMapper` wraps all `NotFoundException`/`ForbiddenException`/`WebApplicationException` throws; no bare string responses found |
| Pattern 3: Empty 4xx response bodies | CLEAN — batches 1–16 + misc fixes; zero empty-body 4xx in v2 core or plugin cluster |
| Pattern 4: Bespoke `*ConfigRest` not on generic registry | CLEAN — all admin config routes through `AdminConfigRest`; verified again |
| Pattern 5: New `@Path(Constants.SHEPARD_API + ...)` in v2/plugins | CLEAN — zero occurrences (excluding frozen spatiotemporal) |
| Pattern 6: Redundant/verbose response fields | CLEAN — `ContainerRefIO` and `DataObjectSummaryIO` numeric fields dropped by fire-27/fire-32 PRs |
| Pattern 7: Per-kind endpoints not under `?kind=` | BY DESIGN — `GitReferenceRest` retains preview + check-update; `VideoStreamReferenceV2Rest` retains upload + download; generic CRUD on `/v2/references?kind=` |
| Pattern 8: Non-standard absolute URL error type URNs | 1 new finding (plugin cluster residual after fire-35 fix) — see Finding 1 above |

---

## Verification notes (fires 35–39 wave)

The fire-40 scan confirmed that all 13 findings from the initial fire-40 scouting
(before the local branch reset) were correctly already covered by fires 27–39:

| Prior finding | Covered by |
|---|---|
| F1–F4 (timeseries container numeric ids) | APISIMP-TSCONT-APPID-KEY ✓ shipped 2026-06-11 |
| F5–F6 (structured-data/file container numeric ids) | APISIMP-FC-SDC-LINKED-DO-APPID ✓ shipped 2026-06-11 |
| F7 (ContainerRefIO numeric body) | APISIMP-CONTAINERREF-DROP-NUMERIC ✓ shipped 2026-06-14 |
| F8 (DataObjectSummaryIO numeric id) | APISIMP-DO-SUMMARY-IO-DROP-LEGACY-ID ✓ shipped 2026-06-12 |
| F9 (plain string errors FileReferenceV2Rest) | APISIMP-FILEREF-V2-PROBLEM-BODIES ✓ shipped 2026-06-13 |
| F10 (plain string errors temporal annotation) | APISIMP-EMPTY-BODIES-BATCH-12 ✓ shipped 2026-06-13 |
| F11 (empty body git credential rotate) | APISIMP-EMPTY-BODIES-BATCH-15 ✓ shipped 2026-06-13 |
| F12 (empty body avatar PUT) | APISIMP-EMPTY-BODIES-BATCH-3 ✓ shipped 2026-06-13 |
| F13 (pagination normalize) | APISIMP-PAGINATION-UNIFY-RECREATE PR #1887 (rebased fire-40) |

---

## Summary

- **1 genuine new finding** (fire-40) — `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS`
- 12 of 13 initial scouting findings verified already covered by fires 27–39 wave
- PR #1887 (APISIMP-PAGINATION-UNIFY-RECREATE) successfully rebased onto main in
  this fire; CodeQL flake is pre-existing and not caused by this PR
- Smallest dispatchable next fire: `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS` (XS, 4 string literals in 3 files)
