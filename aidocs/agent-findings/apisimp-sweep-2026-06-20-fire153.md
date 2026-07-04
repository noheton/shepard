---
stage: fragment
last-stage-change: 2026-06-20
---

# APISIMP sweep — 2026-06-20 fire-153

Targeted confirming audit of the v2 REST surface. Objective: verify that all
`@QueryParam` documentation gaps are either merged or covered by in-flight PRs,
and identify any genuinely new findings missed by prior sweeps.

## Scope

- All `*Rest.java` files under `backend/src/main/java/de/dlr/shepard/v2/`
- Python AST scan: `@QueryParam` params lacking `@Parameter` within same method
- Java annotation scan: `@Operation` blocks without explicit `operationId`
- Cross-check against existing APISIMP-* backlog rows, in-flight PRs
  (#2011/#2012/#2016/#2018/#2021/#2022/#2023/#2024 + earlier), and
  V2-SWEEP-001-SPEC-FIX notes.

---

## Result: queue confirmed exhausted — no new rows filed

The scan produced 105 apparently undocumented `@QueryParam` params. After cross-checking
each against in-flight PRs (all currently red due to annotation NPE, will heal after
#2035 merges), every finding is already addressed:

| File | Params | Coverage |
|------|--------|----------|
| `ContainersV2Rest.java:565-566` (`listChannels`) | `page`, `pageSize` | PR #2012 (in-flight) |
| `ContainersV2Rest.java:875-882` (`getLiveWindow`) | `shepardId`, `measurement`, `device`, `location`, `symbolicName`, `field`, `windowSeconds`, `withBoundaryPoints` | PR #2012 (in-flight) |
| `CollectionV2Rest.java:160-161` | `page`, `pageSize` | PR #2016 (in-flight) |
| `CollectionLabJournalEntriesRest.java:103-104` | `page`, `pageSize` | PR #2018 (in-flight) |
| `ReferencesV2Rest.java:124-125,401-403` | `kind`, `dataObjectAppId`, `fileKind` | PR #2022 (in-flight) |
| `InstanceAdminRest.java:226-231` | `entityAppId`, `actor`, `from`, `to`, `page`, `pageSize` | PR #2009 (READY ✅) |
| `ShapesApplicableRest.java:111` | `focusAppId` | PR #1993 (READY ✅) |
| `DataObjectV2Rest.java:792,829` | `depth` | PR #2024 (in-flight) |
| `FileReferenceV2Rest.java:77-78` | `parentDataObjectAppId`, `name` | Retired endpoint (returns 410 Gone) |
| `CollectionTimelineRest.java:131` | `binSizeDays` | Has `@Parameter` (multi-line annotation; scanner false-positive) |
| `ShapesPredicatesRest.java:92` | `substrate` | Has `@Parameter` (multi-line annotation; scanner false-positive) |

**Conclusion:** The APISIMP `@Parameter` documentation queue is exhausted. All
undocumented params are either: (a) covered by an in-flight NPE-red PR that heals
after #2035 merges, (b) in a READY PR awaiting orchestrator merge, or (c) retired
endpoints or false-positives from multi-line annotation scanning.

---

## operationId stability — context note (no new row)

After V2-SWEEP-001-SPEC-FIX (2026-06-11), which added explicit `operationId` to
119 colliding methods, 62 REST files still have operations using synthesized
(method-name-derived) IDs. These are currently unique and the client builds
correctly. Risk: future method renames silently break the synthesized ID.
Decision: not filing a new row — the stable-operationId concern is tracked in the
V2-SWEEP-001-SPEC-FIX notes and surfaces only when a client regen is triggered.

---

## Next dispatch: MFFD-RENDER-NDT-GRID (fire-154 or later)

With APISIMP exhausted and V2-SWEEP-004 in-flight (#2034), the next dispatchable
priority-2 item is MFFD demonstrator work (MFFD-RENDER-NDT-GRID, M). Blocked
dependencies: none (MAPPING_RECIPE + render surface are shipped). However, MFFD-RENDER-NDT-GRID
is M-size and complex — see fire-154 assessment.
