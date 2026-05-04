# Cleanup Plan — shepard

Executive summary. Snapshot date: 2026-05-04. Detail in the other `/aidocs/` documents.

## Repository at a glance

shepard is a multi-database research-data platform from DLR (Augsburg). Backend: Quarkus 3.27 / Java 21. Frontend: Nuxt 3 / Vuetify. Persistence: Neo4j, MongoDB, Postgres+TimescaleDB, Postgres+PostGIS. Auth: external OIDC + long-lived API keys.

GitHub `noheton/shepard` is a mirror of GitLab `dlr-shepard/shepard`. **GitLab is authoritative.** The mirror is in sync at the issue level (zero state mismatches across 973 GH ↔ 693 GL items, 99.9% match), but **MR mirroring stopped on 2024-09-16** so ~23 open + 80-100 closed/merged GitLab MRs are not represented on GitHub.

The project is in a **handover / wind-down phase**: active milestones include `Handover period` and `Interim`; multiple `xit-*` external-contractor assignees; many `staus::longterm` (sic) and `stale` labels; 79% of open issues are stale or very stale.

## Findings at a glance

| Area | Headline |
|---|---|
| **Security** | 5 CRITICAL findings, 8 HIGH — none tracked in GitLab. Most pressing: SQL injection in spatial query builder, CORS wildcard, permissions-fallback that grants full access to entities without a Permissions node, SSRF/ReDoS in subscription webhooks, Cypher injection in search query builder. |
| **CI/CD** | SpotBugs + findsecbugs configured but never run by CI (live in `<reporting>` only). No SAST, no container image scanning, no E2E tests, no frontend tests. Main branch pipeline only runs `check`. |
| **Test coverage** | Backend overall 0.74 ratio. HIGH risk: timeseries 0.37, spatialdata 0.26, neo4j 0.29, search 0.38, labJournal 0.20. **Frontend has zero tests.** |
| **Dependencies** | 19 open Renovate MRs. `dotenv` (PyPI orphan) declared in `scripts/` — supply-chain risk. jjwt 0.11 (EOL) still in use. JWT API-key tokens minted without expiration. Nuxt 4, ESLint 9 not adopted. Renovate config has a silent typo (`natchUpdateTypes`). |
| **Issues / MRs** | 166 open issues (35 fresh, 86 stale, 45 very stale). 23 open MRs (only !498 bit-rotted at 402 days). Sprint 23 / shepard 5.4.2 milestone is in flight (timeseries refactor). |
| **Mirror** | 1 issue gap (#557), 23 open + many closed MR gaps; 4 PRs closed on GitHub while their GitLab MR is still open; 1 orphan GH issue (gh#683). |

## Critical security findings (no remediation tracked anywhere)

| ID | Severity | Where | One-line |
|---|---|---|---|
| C1 | Critical | `data/spatialdata/repositories/NativeQueryStringBuilder.java` | SQL injection via `'%s'` interpolation of JSON metadata |
| C2 | Critical | `application.properties:18-21` | CORS `origins=*` with state-changing methods + Authorization header |
| C3 | Critical | `auth/permission/services/PermissionsService.java:258-262` | Permissions fallback grants full read/write/manage when an entity has no Permissions node |
| C4 | Critical | `common/filters/SubscriptionFilter.java:63-78` | SSRF (no URL allowlist) + ReDoS (no regex timeout) on every successful response |
| C5 | Critical | `common/search/query/Neo4jQueryBuilder.java` | Cypher injection via user-controlled property names and IRI types |

See `security-issues.md` for full detail and remediations.

## Phased plan (high level)

| Phase | Focus | Outcome |
|---|---|---|
| **0** | Housekeeping | Mirror re-sync (#557 + MR mirror); file untracked findings; trivial config/doc fixes (Renovate typo, ADR index) |
| **1** | Security fixes | Ship C1-C5 (CRITICAL), then H1-H8 (HIGH); activate SpotBugs+findsecbugs in CI; add Trivy + lint-security |
| **2** | First issues | Verify-close #710 / #711; fix #721 race condition, #717 metadata permission, #667 permission bug; replace `dotenv`; scaffold Vitest; ship #720 frontend column export |
| **3** | Core improvements | Permissions Hardening epic; Subscription Hardening; close out Sprint 23 / 5.4.2; medium security findings |
| **4** | Larger work | Cluster E (Neo4j refactor); Cluster D (semantics, gated on E); product decision on spatial; Cluster B versioning frontend |
| **5** | Dependency / code quality | Drain 19 Renovate MRs in batches sequenced around feature work; close code-quality TODOs |
| **6** | Backlog closure | Bulk-close UX cluster #642-#645, ~70 long-tail research/refactoring items, !498 bit-rotted MR, low-value versioning items |

See `implementation-plan.md` for full detail.

## Recommended immediate actions

1. **File the 5 critical security findings as GitHub issues** (C1-C5). They are not tracked anywhere today.
2. **Activate SpotBugs + findsecbugs in CI** by moving the plugins from `<reporting>` to `<build>`. This is a small change with the biggest leverage in Phase 1.
3. **Fix the `renovate.json` typo** (`natchUpdateTypes` → `matchUpdateTypes`); replace `dotenv` with `python-dotenv` in `scripts/`.
4. **Verify-and-close #710 and #711** to confirm Sprint 23 progress.
5. **Re-enable MR mirroring** so the GitHub mirror reflects GitLab MRs again.
6. **Assign explicit ownership** for the Permissions Hardening epic (C3 + #41/#62/#424/#483/#667 + H1/H2/H7) and the Cluster E Neo4j refactor — both are at risk of indefinite slip in the handover phase.

## Recommended bulk-closure pass (maintainer sign-off required)

Consolidating ~80-90 issues to recover backlog signal:

- ~70 long-tail research / refactoring items (very stale, no owner, project in wind-down)
- 4 UX-triage items (#642-#645)
- 9 spatial backlog items if product decision is "deprecate"
- 2-3 low-value versioning items (#150, #155, #271)
- 1 bit-rotted MR (!498)
- 1-2 likely-superseded drafts (!80)

Closing comments and confidence levels in `ready-to-close.md`.

## Risks worth flagging to stakeholders

1. **Five critical untracked security findings** — first priority.
2. **Frontend has zero tests** — every frontend change is a regression risk; this needs to change before any meaningful frontend feature work is committed.
3. **Cluster E (Neo4j refactor) is foundational and ownerless** — every month it slips, semantic-annotation work and parts of timeseries slip with it.
4. **Permissions Hardening epic is ownerless** — the cluster has been long-standing; C3 elevates it from tech-debt to security priority.
5. **Renovate vs feature-MR collisions** — Quarkus !723 vs !808/!809; nuxt !722/!758 vs #720. Sequence carefully.
6. **Handover phase reduces capacity** — favour scope reduction and closure over ambitious new starts.

## Documents in `/aidocs/`

| File | Contents |
|---|---|
| `repo-overview.md` | Codebase structure, tech stack, CI/CD baseline, architecture + wiki synthesis, gaps |
| `issues-status.md` | Full open-issue gauging by cluster (effort / complexity / value / confidence / test risk / staleness / impl status) |
| `reconciliation.md` | GitHub vs GitLab table, mirror gaps, sync actions |
| `security-issues.md` | All findings by severity (5 critical, 8 high, 12 medium, ~8 low/info) |
| `dependency-report.md` | Outdated, deprecated, CVE-affected packages with recommended actions; Renovate analysis |
| `code-quality.md` | Dead code, TODOs, test coverage map, frontend zero-tests, cross-references |
| `cluster-map.md` | Issue/MR clusters with dependency graph, epics, sequencing recommendations, risk callouts |
| `ready-to-close.md` | Items to close on GitHub with evidence and closing-comment drafts |
| `first-issues.md` | Ranked entry-point shortlist for new contributors |
| `implementation-plan.md` | Full phased plan with effort totals, risks, cluster references, suggested order |
| `cleanup-plan.md` | This document — executive summary |
