# Ready to Close on GitHub — shepard

Snapshot date: 2026-05-04. GitLab is authoritative. The GitHub issue mirror is in sync at the issue level (zero state mismatches), so most "closure" candidates here are about closing the underlying GitLab item, which the mirror will reflect; or about historical / mirror-artifact GH issues that can be safely left closed.

Confidence levels: **certain** (objective evidence) / **likely** (strong inference) / **uncertain** (needs maintainer review).

## A. GitLab issues already implemented — verify-and-close

These appear DONE based on code evidence; closing on GitLab will sync to GitHub.

| GL iid | Title (abbr) | Evidence | Confidence | Closing comment draft |
|---|---|---|---|---|
| #710 | Support additional parameters for timeseries reference | `TimeseriesReferenceRest.java:267-275` exposes `function`, `groupBy`, `fillOption`, device/location/symbolic/measurement/field tag, `csvFormat` | certain | "Closing — endpoint already accepts the requested parameter set per `TimeseriesReferenceRest.java:267-275`. Reopen if a specific parameter is still missing." |
| #711 | Provide column-based export of timeseries | `CsvFormat.java` (ROW + COLUMN), `CsvColumnLineProvider.java`; commit `c4980a6` | certain | "Closing — column-mode CSV export is implemented (`CsvFormat.COLUMN`, `CsvColumnLineProvider`). Frontend exposure tracked under #720." |

## B. GitHub-only artifacts — leave closed, document

| GH id | Why | Action |
|---|---|---|
| gh#683 "Bug explore" | CLOSED on GitHub; body references `…/-/work_items/684` but GitLab iid 684 was deleted on GitLab. Orphan mirror artifact. | Leave closed. Optional: add comment "Mirror artifact — GitLab counterpart was deleted upstream." |
| 27 `[PLACEHOLDER]` rows on GitHub (label `gitlab merge request`) | By-design numbering pads from the mirror; no underlying GitLab counterpart. | Leave as-is. They are part of the mirror's numbering strategy. |

## C. Stale open MRs to close on GitLab (will reflect on GitHub)

| GL MR | State | Reason | Confidence | Closing comment draft |
|---|---|---|---|---|
| !498 RetrieveCollectionWithVersionOfIncomingReferences | open, 402 days idle | Bit-rotted; `develop` has diverged substantially. Versioning frontend has no owner in handover phase. | likely | "Closing as stale — branch is 402 days idle and develop has moved past the surface. Reopen against current develop if the work is still desired." |
| !80 Draft: Timeseries payload search concept | open, very stale | Likely superseded by #763 / !763 (search annotatedTimeseries) | uncertain | Owner-confirm first; if confirmed: "Superseded by #763 / !763 — closing the original concept draft." |

## D. Stale open issues — closure candidates

### D1. Spatial-data backlog (#441-#447, #530, #557)

All very stale; feature is gated behind `SHEPARD_SPATIAL_DATA_ENABLED` and not adopted. Security finding C1 (SQL injection in `NativeQueryStringBuilder.java`) supersedes feature work in this cluster.

| iid | Confidence | Closing comment draft |
|---|---|---|
| #441-#447, #530, #557 | uncertain | "Closing pending product decision on spatial-data graduation. The feature flag has been disabled and there is no active investment. Filing a separate ticket to track the SQL-injection finding (C1) in `NativeQueryStringBuilder.java` regardless of graduation outcome. Reopen if graduation is decided." |

### D2. UX triage cluster (#642-#645)

Created same day 2025-06-06; no follow-through commits.

| iid | Confidence | Closing comment draft |
|---|---|---|
| #642-#645 | likely | "Closing — created in a single batch on 2025-06-06 with no subsequent activity, no owner assigned, and no commits referencing the items. Reopen with concrete repro/scope if any of these are still observable regressions." |

### D3. Versioning frontend (low-value items)

| iid | Confidence | Closing comment draft |
|---|---|---|
| #150 Diff view | likely | "Closing as low-value nice-to-have; no FE owner in handover phase, very stale. Reopen if user demand surfaces." |
| #155 Cosmetic version label | likely | "Closing as low-value cosmetic; very stale. Reopen alongside any broader versioning frontend epic." |
| #271 (versioning behaviour) | uncertain | "Closing as likely superseded — backend versioning is in place; specific behaviour gap is no longer clear. Reopen with a concrete repro." |

### D4. Long-tail research / refactoring backlog (~70 items, very stale)

`issue::research`, `issue::meta`, and very-stale `issue::refactoring` items in clusters such as Aras / Nexus / Object Storage / Workflow Mgmt evaluations. Suggest **bulk close-with-comment** unless an owner objects.

Generic closing comment:

> "Closing as part of backlog hygiene. This research / refactoring item has been idle for 12+ months and the project is in a handover/wind-down phase (see `Handover period` and `Interim` milestones). The architectural decisions captured in `architecture/src/09_architecture_decisions/*.adoc` have rendered several evaluation items obsolete. Reopen if active work resumes."

Exclusions (do NOT bulk-close — assigned to active or named owner):
- #23 (Willmeroth, noheton)
- #32 (thaase)
- #284, #507, #513, #655, #657, #659 (RolandGlueck)
- #296, #602, #650, #673 (lettfe)
- #306 (mvistein)
- #326 (ZeroOne42)
- #390, #394, #524, #633 (xit-* contractor handover)

These need individual owner-confirmation rather than bulk closure.

## E. PRs on GitHub corresponding to still-open GitLab MRs

The following GH PRs were closed on GitHub while their GitLab MR counterpart is still `opened`. Treat GitLab as canonical — leave closed-on-GitHub is acceptable but worth a confirmation comment.

| GH PR | GL MR | Action | Comment draft |
|---|---|---|---|
| 995 | !758 | leave closed | "GitLab is authoritative; tracking continues at gitlab.com/dlr-shepard/shepard/-/merge_requests/758." |
| 994 | !803 | leave closed | Same template (link to !803). |
| 963 | !636 | leave closed | Same template (link to !636). |
| 946 | !803 | leave closed (duplicate of 994) | Same template. |
| 799 | !80 | **leave open** | This PR correctly mirrors an open GitLab draft. |

## F. Top-level mirror operations (not closure, but related)

1. ~~Re-import GitLab issue #557~~ — **ignore**, confirmed by maintainer 2026-05-04; superseded by spatial-cluster closure candidacy.
2. Re-enable MR mirroring (last MR-as-issue body dated 2024-09-16; ~23 open and 80–100 closed/merged MRs since are not represented).

## Closure plan summary

| Action | Item count | Confidence |
|---|---|---|
| Verify-and-close on GitLab (will sync to GH) | 2 (#710, #711) | certain |
| Leave closed on GitHub (mirror artifacts) | 1 + 27 placeholders | certain |
| Close stale open MRs on GitLab | 2 (!498, !80*) | likely / uncertain |
| Close spatial-data cluster (gated, very stale) | 9 (#441-#447, #530, #557) | uncertain — needs product decision |
| Close UX triage cluster | 4 (#642-#645) | likely |
| Close versioning low-value items | 3 (#150, #155, #271*) | likely / uncertain |
| Bulk-close research/refactoring backlog | ~70 | likely (assuming maintainer agrees with bulk pass) |
| Add confirmation comments to GH PRs | 4 | certain |
| Re-import / mirror fixes | 2 mirror operations | n/a |
