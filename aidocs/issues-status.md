# Issues Status — shepard (GitLab open items)

Snapshot date: 2026-05-04. Source: GitLab (authoritative). 166 open issues, 23 open MRs.

## Method

Each open item gauged on six axes from Wave 1-3 evidence:

- **Effort**: XS (<0.5d), S (<2d), M (<1w), L (<2w), XL (>2w)
- **Complexity**: low / medium / high
- **Value**: low / medium / high (user impact + security + unblocking)
- **Confidence**: high / medium / low (in the gauge itself)
- **Test risk**: from coverage map — modules timeseries 0.37, spatialdata 0.26, neo4j 0.29, search 0.38, labJournal 0.20, frontend 0.0 are HIGH
- **Staleness**: fresh (<6mo) / stale (6-12mo) / very stale (12+mo)
- **Implementation status**: DONE / PARTIAL / NOT_IMPLEMENTED / SUPERSEDED / BLOCKED / UNCLEAR

GitLab labels (`issue::*`, `journey::*`, `status::*`, `td-priority::*`) inform but don't override the gauge.

## Status distribution (open items)

| Implementation status | Count |
|---|---|
| DONE (verify-and-close) | ~12 |
| PARTIAL | ~22 |
| NOT_IMPLEMENTED | ~95 |
| SUPERSEDED candidates | ~8 |
| BLOCKED | ~6 |
| UNCLEAR | ~23 |

Staleness: 35 fresh, 86 stale (6-12 months), 45 very stale (12+ months).

Open MR breakdown: 1 bit-rotted (`!498`, 402 days idle); 19 Renovate dependency MRs; 5 active draft feature MRs (`!80`, `!763`, `!788`, `!800`, `!808`, `!809`).

## Cluster: Active timeseries / 5.4.2 / Sprint 23

| iid | Type | Title | Status | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| #710 | feature | Support additional parameters for timeseries reference | DONE | XS | low | med | high | HIGH | fresh | `TimeseriesReferenceRest.java:267-275` |
| #711 | feature | Provide column-based export of timeseries | DONE | XS | low | med | high | MED | fresh | `CsvFormat.java`, `CsvColumnLineProvider.java` |
| #712 | refactor | Refactor referenced/annotated timeseries | PARTIAL | L | high | high | high | HIGH | fresh | MR !809 in flight |
| #713-#716 | refactor | Sub-tasks of #712 refactor | PARTIAL | M each | high | med | med | HIGH | fresh | Sprint 23 cluster |
| #717 | bug | Timeseries metadata access without permissions | NOT_IMPLEMENTED | S | med | high | high | HIGH | fresh | `TimeseriesService.getTimeseriesById:80` still gates via container; security-relevant |
| #718 | chore | Dependency Updates | PARTIAL | XS | low | med | high | n/a | fresh | Standing meta-bucket |
| #720 | feature | Make column export available in frontend | NOT_IMPLEMENTED | M | med | med | med | HIGH (FE=0) | fresh | Backend ready (#711); UI absent |
| #721 | bug | Race condition default file container | NOT_IMPLEMENTED | S | med | high | high | HIGH | fresh | `FileContainerService` lacks synchronization; data-corruption risk |
| #696-#707 | bug | Audit trail, TS plot fixes, search bugs | varies | S each | low-med | med | med | mixed | fresh | ~10 small items; some likely DONE (verify) |

## Cluster: Versioning frontend (backend done)

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #46 | L | med | med | med | HIGH (FE=0) | very stale | Frontend version UI; assigned RolandGlueck; backend ready |
| #127 | M | med | med | med | HIGH (FE=0) | very stale | Version-aware navigation |
| #150 | M | med | low | med | HIGH (FE=0) | very stale | Diff view — nice-to-have |
| #155 | S | low | low | med | HIGH (FE=0) | very stale | Cosmetic version label |
| #271 | M | med | low | low | HIGH (FE=0) | very stale | Likely SUPERSEDED — needs triage |

All blocked on frontend capacity in handover. `frontend/components/context/` has no `version` directory.

## Cluster: Spatial data (feature-flagged)

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #441-#447 | M-L each | med-high | low | low | HIGH | very stale | Behind `SHEPARD_SPATIAL_DATA_ENABLED`; not adopted |
| #530 | M | med | low | low | HIGH | very stale | Same |
| #557 | S | low | low | low | HIGH | very stale | Heap-memory issue; mirror gap on GitHub side |

**Cross-cut**: security finding C1 (SQL injection in `NativeQueryStringBuilder.java`) supersedes feature work in this cluster.

## Cluster: Semantic annotations

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #43 | L | high | med | med | HIGH | very stale | PARTIAL impl in `data/semantic/`; partly blocked on Neo4j refactor |
| #553 | M | high | med | low | HIGH | stale | BLOCKED on Neo4j refactor |
| #656 | M | high | med | low | HIGH | stale | BLOCKED on Neo4j refactor |
| #660 | L | high | med | low | HIGH | stale | Overlaps Neo4j refactor itself |

## Cluster: Neo4j refactor

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #274 | XL | high | high | low | HIGH | very stale | Foundational; unblocks #43/#553/#656/#660 |
| #577 | L | high | med | low | HIGH | stale | Sub-area of #274 |
| #660 | L | high | med | low | HIGH | stale | Listed in semantic cluster too |

## Cluster: Permissions / admin (overlaps security C3, H1, H2)

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #41 | M | high | high | med | MED | very stale | Permission model — overlaps security C3 |
| #62 | M | high | high | med | MED | very stale | Admin role gaps; C3 |
| #424 | M | med | high | med | MED | stale | xit-* assigned; permission UX |
| #483 | M | med | high | med | MED | stale | xit-* assigned; permission UX |
| #667 | S | med | high | high | MED | fresh | Recent permission bug; tied to C3 |

Treat as a single security workstream alongside C3/H1/H2/H7 (Permissions Hardening epic — see `cluster-map.md`).

## Cluster: UX cluster (2025-06-06 batch)

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #642-#645 | S each | low | low | med | HIGH (FE=0) | stale | Created same day; no follow-through; close-as-wontfix candidates |

## Cluster: Search & fresh bugs (recent)

| iid | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|
| #683 | S | med | med | med | HIGH | fresh | Search bug (mvistein); also corresponds to GH gh#683 reconciliation note |
| #688 | S | med | med | med | HIGH | fresh | lettfe |
| #722 | S | low | med | med | MED | fresh | kauf_pt |
| #758 | M | med | med | med | MED | fresh | kauf_pt |
| #763 | M | med | med | med | HIGH | fresh | TS payload search (Willmeroth); cf MR !763 |
| #779 | XS | low | low | high | n/a | fresh | Dep update |
| #780 | XS | low | low | high | n/a | fresh | Dep update |

## Cluster: Long-tail research / refactoring backlog (~90 items)

Group gauge: **Effort L+, Complexity high, Value low, Confidence low, Staleness very stale.** Includes Aras / Nexus / Object Storage / Workflow Mgmt evaluations; mostly `issue::research` / `issue::meta` labels.

High-confidence outliers worth retaining (have an active owner):

- #23 (Willmeroth, noheton)
- #32 (thaase)
- #284, #507, #513, #655, #657, #659 (RolandGlueck cluster)
- #296, #602, #650, #673 (lettfe)
- #306 (mvistein), #326 (ZeroOne42)
- #390, #394, #524, #633 (xit-* contractor handover items)

Everything else in this cluster: bulk close-with-comment unless owner objects.

## Cluster: Renovate / dependency MRs (group)

| Group gauge | Effort XS each | Complexity low | Value medium (security hygiene) | Confidence high | Staleness fresh |

Open: `!636`, `!647`, `!695`, `!722`, `!723`, `!734`, `!740`, `!758`, `!766`, `!779`, `!780`, `!803`, `!804`, `!805`, `!806`, `!810` (≈19). Action: batch-merge after CI green; sequence to avoid conflicts with `!808/!809` (Quarkus) and #720 (nuxt).

## Cluster: Active draft MRs

| MR | Title | Effort | Cmplx | Value | Conf | Test | Stale | Notes |
|---|---|---|---|---|---|---|---|---|
| !498 | RetrieveCollectionWithVersionOfIncomingReferences | M (rebase) | high | low | med | HIGH | very stale (BIT-ROT 402d) | Close or rebase |
| !80 | Draft: Timeseries payload search concept | L | high | med | low | HIGH | very stale | May be superseded by #763 |
| !763 | Draft: search annotatedTimeseries in container | M | med | med | med | HIGH | fresh | Active |
| !779 | Draft: update @vuetify | XS | low | low | high | n/a | fresh | |
| !780 | Draft: update @vue-tsc | XS | low | low | high | n/a | fresh | |
| !788 | Draft: takeSuccessorIntoAccount | M | med | med | med | MED | fresh | |
| !800 | Draft: update documentation and Readme | S | low | med | high | n/a | fresh | |
| !808 | Draft: Refactor timeseries graph DB | L | high | high | high | HIGH | fresh | #712 |
| !809 | #712 refactor referenced timeseries | L | high | high | high | HIGH | fresh | |

## Top 20 highest value-per-effort

Ranked by (value / effort) with security tiebreakers. Note: untracked critical security findings (C1, C2, C4, C5) outrank everything below — see `security-issues.md`.

1. **#710** — XS, already DONE; verify-and-close
2. **#711** — XS, already DONE; verify-and-close
3. **#717** — S, security-adjacent (timeseries metadata auth gap)
4. **#721** — S, race condition / data corruption in `FileContainerService`
5. **#667** — S, fresh permission bug (C3 overlap)
6. **!800** — S, doc update, ready
7. **#722** — S, fresh, owner assigned
8. **#688** — S, fresh search bug
9. **#683** — S, fresh search bug
10. **!779 / !780** — XS dep updates
11. **#779 / #780** — XS dep updates
12. **#718** — XS standing dep bumps
13. **!808** — L but unblocks Sprint 23 closure
14. **!809** — L, same
15. **#712** — L, unblocks #713-#716
16. **#763** — M, fresh and owner-driven
17. **!763** — M, ties to #763
18. **!788** — M, fresh draft
19. **#41 / #62 / #424 / #483** — M each, but high permissions value (C3) — bundle as one workstream
20. **#697-#707** — S each, fresh bugs; many can be batched

## Low-confidence items (need triage / clarification)

- **#271** versioning frontend — possibly superseded
- **#553, #656, #660, #43** semantic annotations — blocked on #274; status unclear
- **#274, #577** Neo4j refactor — scope vs handover capacity
- **Spatial cluster #441-#447, #530, #557** — feature flag still warranted? Or close?
- **#642-#645** UX batch — intent unclear given no follow-through
- **!498** bit-rotted MR — rebase or close?
- **!80** — superseded by #763 / !763?
- **xit-* items #390, #394, #524, #633** — contractor handover status unknown
- **Long-tail research issues** (~80 of cluster minus called-out owners) — bulk-triage candidates

## Recommended for closure (staleness / superseded / done)

Detailed list and closing comment drafts in `ready-to-close.md`.
