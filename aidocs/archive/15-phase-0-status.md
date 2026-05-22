---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Phase 0 — Housekeeping Status

Snapshot date: 2026-05-04. Records progress on Phase 0 of
`11-implementation-plan.md` (housekeeping) made in the current
working session, plus what remains for maintainer action.

## Done in this session

### 0.4 Trivial config / doc fixes

- ✅ **`renovate.json:21`** — fixed typo `natchUpdateTypes` →
  `matchUpdateTypes`. The ruff package rule was previously
  silently broken; minor/patch updates now match as intended.
- ✅ **ADR index** — added `019-prefer-member-injection.adoc` and
  `020-use-single-relation-for-default-container.adoc` includes
  to `architecture/src/09_architecture_decisions/index.adoc`. The
  ADR files existed on disk but were not wired into the rendered
  docs.
- ⏭️ **GitLab label typo** `staus::longterm` →  `status::longterm`
  — left for the maintainer; requires GitLab admin access and a
  decision on whether to re-label existing issues or accept the
  legacy label as historical.

### Forward-looking design (extending Phase 0 outputs)

Three new docs added under `aidocs/`:

- `12-timescaledb-performance-analysis.md` — extended with §11
  covering the read path through the API: streaming reads
  end-to-end without heap blow-up, and aligning the numeric
  `timeseries_id` (used by semantic annotations) with the 5-tuple
  (used by data endpoints), with a backwards-compatibility plan.
- `13-search-improvements.md` — proposal for one unified search
  API replacing today's five endpoints, richer query options
  (predicate JSON + fulltext + raw SPARQL/SQL escape hatches),
  cursor pagination, streaming.
- `14-semantic-improvements.md` — generalising semantic
  annotations to file / structured / spatial payloads, label vs
  IRI discipline, search-as-you-type for terms, triplestore
  integration (n10s on Neo4j → GraphDB / RDF4J) with
  knowledge-graph use cases.

The aidocs directory is now numbered (01-…14, plus this file as
15) with `00-index.md` as the navigation entry point.

## Not done (requires maintainer / explicit ownership)

### 0.1 Mirror reconciliation

| Item | Why deferred |
|---|---|
| ~~Re-import GitLab issue #557~~ | **Resolved 2026-05-04**: maintainer confirmed GL #557 can be safely ignored — superseded by spatial-cluster closure candidacy (gated behind `SHEPARD_SPATIAL_DATA_ENABLED`, no active investment). Will resolve when the cluster decision lands on GitLab. |
| Re-enable MR mirroring (broken since 2024-09-16) | Operational, not code; needs upstream mirror tool re-config |
| Confirmation comments on GH PRs #995, #994, #963, #946 pointing to authoritative GitLab MRs | "Be frugal about posting replies on GitHub" — wanted explicit owner go-ahead before adding 4 PR comments |
| Document gh#683 as mirror artifact | Same |

### 0.2 File untracked critical findings as GitHub issues

13 issues to file (C1-C5, H1-H8) — these touch security and a
unilateral filing pass risks duplicates and label noise. The
content is fully drafted in `07-security-issues.md`. Action for
the maintainer: confirm filing strategy (one-by-one with full
remediation drafts vs. tracker meta-issue with links).

### 0.3 File untracked code-quality TODOs

10 small items at the file/line refs in
`06-code-quality.md` and `11-implementation-plan.md:39`. Same
reasoning as 0.2 — content is drafted; filing pass needs a
maintainer green-light to avoid mirror noise.

## Research-topic closure check

The implementation plan calls out checking whether long-tail
research / refactoring items can be closed based on current
implementation state. Findings:

| Cluster | Closeable now? | Notes |
|---|---|---|
| Spatial-data backlog (#441-#447, #530, #557) | **Pending product decision.** Feature is gated behind `SHEPARD_SPATIAL_DATA_ENABLED`; no active investment. C1 (SQL injection) supersedes feature work regardless. Not closeable by code state alone. |
| Versioning low-value items (#150, #155, #271) | **Likely yes** for #150, #155; #271 unclear. Backend versioning is in place; FE owner missing in handover. Maintainer pass. |
| UX triage cluster (#642-#645) | **Likely yes** — no follow-through commits since 2025-06-06 batch. Maintainer pass. |
| Stale MRs (!498, !80) | **!498 likely yes** (402 days idle). **!80 superseded** by #763 / !763 (search annotatedTimeseries). Owner-confirm. |
| Verify-and-close (#710, #711 GitLab iids) | **DONE in code** (`TimeseriesReferenceRest.java:267-275`, `CsvFormat.COLUMN`). On GitHub, these correspond to issues with body referencing GitLab work_items 711, 710. The GH#710 issue (= GL #711, column export) already carries `status::testing` label — close after sign-off. |
| Long-tail research (~70 items) | **Mostly stale**, but should not be bulk-closed by an agent. Maintainer pass with the closing-comment template in `09-ready-to-close.md`. |
| Semantic cluster (#43, #553, #656, #660) | **Not closeable** — these are *blocked* on the Neo4j refactor (#274), not implemented. The proposal in `14-semantic-improvements.md` is the path forward; do not close. |
| Neo4j refactor (#274, #577) | **Not closeable** — foundational, unblocks D / semantic / parts of A. Phase 4 work. |

No issues are closed automatically here; the table is the input
for the maintainer's closure pass.

## Suggested next steps

1. Maintainer pass: act on the closure table above using the
   templates in `09-ready-to-close.md`.
2. Decide on filing strategy for 0.2 / 0.3 (one-tracker meta-issue
   per cluster, or per-finding issues).
3. Operations: re-enable MR mirroring on the GitHub side. (GL #557
   re-import is ~~no longer required~~ per maintainer 2026-05-04.)
4. Begin Phase 1 (security fixes) per the implementation plan;
   the C1-C5 findings are documented in
   `07-security-issues.md`.
5. Review the three forward-looking docs (12 §11, 13, 14) and
   greenlight any phase that should enter the next sprint plan.
