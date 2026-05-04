# Reconciliation — GitHub mirror vs GitLab authoritative

Snapshot date: 2026-05-04. GitLab is authoritative.

## Summary

| Metric | GitHub (noheton/shepard) | GitLab (dlr-shepard/shepard) |
|---|---|---|
| Issues — total | 973 | 693 |
| Issues — open | 166 | 166 |
| Issues — closed | 807 | 527 |
| Pull/Merge requests — total | 22 | 801 |
| PRs/MRs — open | 1 | 23 |
| PRs/MRs — merged | — | 699 |
| PRs/MRs — closed | 21 | 79 |

**Match rate (GitHub items → GitLab counterpart)**: 972 / 973 = **99.9%**.

State mismatches: **0**. Every GitHub item that is open has an open GitLab counterpart; every closed GitHub item has a closed/merged GitLab counterpart. The mirror is in sync at the issue level.

## Mirror model

- The mirror imports both GitLab issues and GitLab MRs into the GitHub *issues* number space (PRs are not used for the mirror itself).
- Two label values: `gitlab issue` and `gitlab merge request`.
- Where a GitLab MR has the same iid as an existing GitHub issue, the mirror creates a `[PLACEHOLDER] - for issue #N` row to keep numbering aligned. 27 placeholders found; all are by design.
- Issue titles are mirrored verbatim; MR titles get a `- [merged]/[closed]` suffix on GH.
- Body of every mirrored row contains a `Migrated from GitLab: …/-/work_items/N` (issue) or `…/-/merge_requests/N` (MR) URL — used as the iid mapping.

| Match type | Count |
|---|---|
| `url` (GitLab issue URL embedded in body) | 692 |
| `url→MR` (GitLab MR URL embedded in body) | 252 |
| `placeholder` (numbering pad) | 27 |
| `title→MR` (title-only MR match) | 1 |
| `no-match` | 1 |

## GitHub items with no GitLab match (1)

- **gh#683 "Bug explore"** (CLOSED, label `gitlab issue`). Body says "migrated from `…/-/work_items/684`", but GitLab iid 684 does not exist — deleted on GitLab. Recommendation: leave closed; document as historical mirror artifact.

## State mismatches needing close-on-GitHub

**None.** No action required.

## Mirror gaps — open on GitLab, missing from GitHub

### Issues (1)

| GL iid | Title | Action |
|---|---|---|
| 557 | spatial data: Out of (heap) memory while getting larger datasets | Re-import via the mirror tool, or open a corresponding GitHub issue manually |

### MRs (23)

The latest GitHub-mirrored MR-as-issue body in the dataset is dated **2024-09-16**, suggesting MR mirroring stopped while issue mirroring continued. All open GitLab MRs are missing from the GitHub issue stream:

| GL MR iid | Title |
|---|---|
| 810 | Update dependency `eu.michael-simons.neo4j:neo4j-migrations` to v4 |
| 809 | #712 refactor referenced timeseries |
| 808 | Draft: Refactor timeseries structure in graph database |
| 806 | Update dependency `webpack-cli` to v7 |
| 805 | Update load tests |
| 804 | Update scripts dependencies |
| 803 | Update backend dependencies |
| 800 | Draft: update documentation and Readme Datein |
| 788 | Draft: takeSuccessorIntoAccount |
| 780 | Draft: update `@vue-tsc` dependencies |
| 779 | Draft: update `@vuetify` dependencies |
| 766 | Update infrastructure dependencies |
| 763 | Draft: search annotatedTimeseries in container |
| 758 | Update `nuxtend` dependencies |
| 740 | Update `timescale/timescaledb` Docker tag to v2.26.3 |
| 734 | Update `python` Docker tag to v3.14.3 |
| 723 | Update backend Quarkus update |
| 722 | Update `nuxtend` dependencies (major) |
| 695 | Update scripts dependencies (major) |
| 647 | Update `openapitools/openapi-generator-cli` Docker tag to v7.21.0 |
| 636 | Update develop dependencies |
| 498 | RetrieveCollectionWithVersionOfIncomingReferences |
| 80  | Draft: Timeseries payload search concept |

Recommendation: re-enable the MR mirror, or accept GitLab as canonical and document the gap.

## GitHub PRs corresponding to GitLab MRs

All 22 PRs in `noheton/shepard` correspond to GitLab MRs by exact title match.

### PRs needing review (4 closed on GitHub while GL MR is open)

| GH PR | GH state | GL MR | GL state | Action |
|---|---|---|---|---|
| 995 | closed | !758 | opened | Likely safe to leave closed (GitLab is authoritative); confirm with maintainer |
| 994 | closed | !803 | opened | Same |
| 963 | closed | !636 | opened | Same |
| 946 | closed | !803 | opened | Duplicate of 994 — same |

### PR open on GitHub (1)

| GH PR | GH state | GL MR | GL state | Action |
|---|---|---|---|---|
| 799 | open | !80 | opened | Leave open — correctly mirrors active draft |

### Other PRs (17)

All closed; all correspond to closed/merged/abandoned GitLab MRs. No action.

## Recommended sync actions (top-level)

1. **Fix MR mirroring** — last MR-as-issue body dated 2024-09-16; reconfigure or replace the mirror tool to resume MR ingestion. ~23 open and 80–100 closed/merged MRs since 2024-09 are not represented on GitHub.
2. **Re-import GitLab issue iid 557** — only open GitLab issue not on GitHub.
3. **Document gh#683** — orphan since GL iid 684 was deleted; leave closed.
4. **Review PRs #995/#994/#963/#946** — closed on GH while GitLab MR open. Treat GitLab as canonical; close-and-leave is acceptable.
5. **No close-on-GitHub state-mismatch actions** are needed today.
