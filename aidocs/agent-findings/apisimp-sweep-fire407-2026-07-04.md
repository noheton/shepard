---
stage: concept
last-stage-change: 2026-07-04
---

# APISIMP Sweep — fire-407 (2026-07-04)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline:** Last sweep fire-404 filed `APISIMP-SUMMARY-IO-NUMERIC-ID` and closed `APISIMP-STATS-NUMERIC-ID`, `APISIMP-SEMANTIC-CONTENT-TYPE`. The `APISIMP-SUMMARY-IO-NUMERIC-ID` row shipped this fire as PR #2291 (✅ merged). Prior fire also confirmed PR #2290 (APISIMP-IO-STALE-TS-REF-PATHS) was merged by operator.

**What merged into main this fire (fire-407):**
- PR #2290 (merged by operator before this fire) — APISIMP-IO-STALE-TS-REF-PATHS: stale Javadoc path refs
- PR #2291 (merged this fire) — APISIMP-SUMMARY-IO-NUMERIC-ID: remove `private Long id` from `DataObjectSummaryIO`

---

## Axes confirmed clean this sweep

| Axis | Verdict |
|---|---|
| Forbidden `@Path(Constants.SHEPARD_API + ...)` additions in v2/ | ✅ zero found |
| Per-kind endpoints NOT unified under `?kind=` | ✅ `StructuredDataContainerStatsRest` V2-EXCEPTION is documented; no new violations |
| Bespoke admin `*ConfigRest` not on generic registry | ✅ `JupyterConfigPublicRest` is a public GET-only read (not a PATCH config surface); all mutable admin config goes through `AdminConfigRest` |
| Numeric Neo4j id leaks in `@PathParam`/`@QueryParam` | ✅ clean (APISIMP-SUMMARY-IO-NUMERIC-ID fixed by PR #2291; `APISIMP-PERMISSION-AUDIT-NEO4J-ID` remains intentionally L2-blocked) |
| `@QueryParam("size")` pagination misname | ✅ clean — `ContainersV2Rest.java:1458` uses `@QueryParam("size")` for thumbnail pixel size (documented "Accepted values: 64, 200, 400"), not pagination |
| Error envelope consistency (problem+json) | ✅ APISIMP-SEMANTIC-CONTENT-TYPE shipped (fire-404, PR #2285); surface clean |
| IO classes exposing numeric Long id on wire | ✅ clean after PR #2291 |
| Response list envelopes (PagedResponseIO) | ✅ all list endpoints use standard envelope after APISIMP-ONTOLOGY-BUNDLES-LIST-ENVELOPE, APISIMP-REFERENCES-LIST-ENVELOPE, APISIMP-PROVENANCE-LIST-ENVELOPE |
| `operationId` coverage | ✅ no new missing operationIds found |
| Frontend v1 callers | ✅ APISIMP-DELETE-REFS-V2 shipped (fire-402, PR #2286); no new v1-caller violations found |

---

## Findings (1 new row filed)

### F1 — APISIMP-PAGE-VALIDATION-GUARDS (MINOR, XS)

**What:** Four v2 list endpoints declare `@DefaultValue("0") int page` without a `@PositiveOrZero` (or `@Min(0)`) guard. Two of these also have `pageSize` without `@Min`/`@Max` Bean Validation (relying on server-side clamping instead). The gaps are cosmetic — all affected endpoints already do `Math.max(page, 0)` clamping in service calls and document soft-clamping behaviour — but the inconsistency with the rest of the surface (which uses hard BV annotations) means OpenAPI clients cannot derive the valid range from the spec.

| File | Line | `page` guard | `pageSize` guard | Note |
|---|---|---|---|---|
| `SnapshotListRest.java` | 132 | `@DefaultValue("0")` only — no `@PositiveOrZero` | `@Max(200) @Min(1)` ✅ | Clamps in service |
| `CollectionSnapshotRest.java` | 187 | `@DefaultValue("0")` only — no `@PositiveOrZero` | `@Max(200) @Min(1)` ✅ | Clamps in service |
| `SemanticTermSearchRest.java` | 215 | `@DefaultValue("0")` only — no `@PositiveOrZero` | `@DefaultValue("20")` only — no `@Min`/`@Max` | Doc says "cap: 50, clamped" |
| `ProjectsRest.java` | 230 | `@DefaultValue("0")` only — no `@PositiveOrZero` | `@Max(500) @Min(1)` ✅ | Clamps in service |

**Fix:** Add `@PositiveOrZero` after `@DefaultValue("0")` on each `page` param. For `SemanticTermSearchRest.java:211` add `@Min(1) @Max(50)` on `pageSize`. No runtime behaviour change — clamping stays; the annotations make the contract machine-readable for OpenAPI clients.

**AC:** All four `page` params have `@PositiveOrZero`; `SemanticTermSearchRest.pageSize` has `@Min(1) @Max(50)`; `mvn verify -pl backend` green; no frontend change needed (no wire-shape change).

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-PAGE-VALIDATION-GUARDS (this fire) | MINOR | XS | queued |
| APISIMP-DQR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-LEDGER-ANCHOR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-PERMISSION-AUDIT-NEO4J-ID | MINOR | XS | blocked (L2 migration) |

**Surface health:** Very clean. The v2 REST surface has absorbed ~36 APISIMP fixes since fire-172. The one new finding this fire (`APISIMP-PAGE-VALIDATION-GUARDS`) is cosmetic — no wire-shape change, no runtime-behaviour change, just annotation completeness.

**Recommended next dispatch:** APISIMP-PAGE-VALIDATION-GUARDS (XS, 4 files, annotation-only, no frontend change, 5 min fix).
