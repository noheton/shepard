---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Cluster Map — shepard

Snapshot date: 2026-05-04. Synthesized from Wave 1-3 outputs.

## Cluster summary

| Cluster | Items | Primary modules | Depends on | Blocks | Status |
|---|---|---|---|---|---|
| **A. Timeseries Active (5.4.2)** | #710 (DONE), #711 (DONE), #712-#716 + !808/!809, #717, #720, #721 | `data/timeseries`; frontend timeseries pages | E (long-term); J (Quarkus !723) | #720 blocked on #711 backend shape | **Active** (lettfe, mvistein) |
| **B. Versioning Frontend** | #46, #127, #150, #155, #271 | `frontend/components/version/*` (missing); backend `context/version` (done) | Frontend test infra (none today) | Nothing critical | **Stale** |
| **C. Spatial Data** | #441-#447, #530, #557; security C1 | `data/spatialdata`; feature flag `SHEPARD_SPATIAL_DATA_ENABLED` | C1 SQL injection fix; spatial test investment | Removing feature flag / GA | **Stale, gated** |
| **D. Semantic Annotations** | #43, #553, #656, #660 | `data/semantic`; `common/neo4j` | E (Neo4j refactor) | — | **Stale, blocked** |
| **E. Neo4j Refactor** | #274, #577, #660 | `common/neo4j` | — | D fully; A long-term; H partially | **Stale, foundational** |
| **F. Permissions / Admin** | #41, #62, #424, #483, #667; tech-debt #1, #2 | `auth/permission`, `auth/users`, `auth/security` | C3 fix; H1; H2 | #717 (perm leak in A); compliance posture | **Long-standing, NOT_IMPL** |
| **G. UX Triage (2025-06-06)** | #642-#645 | Frontend (cross-cutting) | — | — | **Stale, triage-eligible** |
| **H. Recent Bugs / Search** | #696-#707; security C5 | `common/search`; `data/timeseries` (plot); audit trail | E (partially) | — | **Mixed** (some likely DONE) |
| **I. Long-tail Research** | Aras, Nexus, Object Storage, Workflow Mgmt evals (~90 items) | Cross-cutting / external | — | — | **Very stale, SUPERSEDED candidates** |
| **J. Renovate Deps** | !636, !647, !695, !722, !723, !734, !740, !758, !766, !779, !780, !803-!806, !810; #718 meta | All; build & runtime | — | Conflicts with A (Quarkus !723) and #720 (nuxt !722/!758) | **Active, mechanical** |
| **K. Bit-rotted MR** | !498 (402d idle) | Frontend versioning (cluster B) | B's frontend infra | — | **Close or rebase** |

### Cross-cluster items

- **#660** sits in both D and E (semantics depend on Neo4j model change).
- **#717** is in A but is fundamentally an F (permissions) bug.
- **C3** (permissions fallback) cross-cuts F and tech-debt #1.
- **C5** (Cypher injection) sits in H but touches every search-using module (timeseries, semantic, references, collections).
- **C2** (CORS wildcard) and **H4** (ExceptionMapper leaks) cross-cut everything (Common.Configuration / Common.Exceptions).
- **H6** (`dotenv` supply chain) belongs to J but materially affects scripts and developer onboarding.
- **H7** (jjwt 0.11 EOL) lives in J but lands in F (auth code paths).

## Cross-cluster dependency graph

```
                 ┌────────────────────────────────────┐
                 │ E. Neo4j Refactor                  │
                 │   #274 #577 #660                   │
                 └────────┬──────────────┬────────────┘
                          │              │
                          ▼              ▼
                  ┌──────────────┐  ┌─────────────────┐
                  │ D. Semantic  │  │ H. Search/Bugs  │
                  │ #43 #553     │  │ #696-#707, C5   │
                  │ #656 #660    │  │                 │
                  └──────────────┘  └─────────────────┘
                          ┊
                          ┊  long-term
                          ▼
                  ┌────────────────────────────────────┐
                  │ A. Timeseries Active                │
                  │  #710-#721, !808/!809               │
                  └─────┬─────────────────┬─────────────┘
                        │                 │
                        ▼                 ▼
              ┌──────────────────┐ ┌─────────────────┐
              │ #717 perm leak   │ │ #720 frontend   │
              │   ── into ──>F   │ │   needs FE test │
              └──────────────────┘ │   infra (Epic 6)│
                                   └─────────────────┘

      ┌────────────────────────────────────────────┐
      │ F. Permissions / Admin                     │
      │   #41 #62 #424 #483 #667                   │
      │   + C3 (CRITICAL), H1, H2, H7              │
      │   + tech-debt #1, #2                       │
      └────────────────────────────────────────────┘

      ┌─────────────────────────┐  ┌──────────────────────┐
      │ C. Spatial (gated)      │  │ Subscriptions: C4    │
      │  #441-447, #530, #557   │  │  (independent module)│
      │  + C1 (CRITICAL SQLi)   │  └──────────────────────┘
      └─────────────────────────┘

      ┌────────────────────────────────────────────┐
      │ J. Renovate (collides with A and #720)     │
      │  !723 Quarkus  ↔  !808/!809                │
      │  !722/!758 nuxt ↔ #720                     │
      └────────────────────────────────────────────┘

      ┌─────────────────────────┐  ┌──────────────────────┐
      │ B. Versioning Frontend  │  │ K. !498 (bit-rotted) │
      │  #46 #127 #150 #155 #271│  │   close or rebase    │
      └────────────┬────────────┘  └──────────────────────┘
                   │
                   ▼
       ┌──────────────────────────────┐
       │ Frontend Test Infra (none)   │
       │   prerequisite for B, #720, K│
       │   ↳ Epic 6                   │
       └──────────────────────────────┘
```

### Reading the graph

- **E** is the deepest blocker (D fully, parts of H, long-term A).
- **F** is the deepest *security* blocker — C3, H1, H2, H7, tech-debt #1, #2, and one A-cluster issue (#717) all converge here.
- **Frontend test infrastructure** is a missing prerequisite node for any meaningful frontend work (B, #720, K).
- **CI/SAST enabling** does not block specific issues but materially de-risks every security cluster.
- **J's Renovate MRs** are mechanically independent but practically collide with A (Quarkus !723 ↔ !808/!809) and #720 (nuxt !722/!758).

### Module hot-spots (broad blast radius)

- `common/neo4j` — touched by A (long-term), D, E, H. Change here cascades.
- `auth/permission` — touched by A (#717), F, tech-debt #1. Single biggest hot-spot beyond Neo4j.
- `common/search` — touched by H + every cluster that queries data. C5 fix has the broadest blast radius.
- `common/configuration` — touched by C2, H8. CORS / credential changes land here.

## Logical feature-sets (epics)

### Epic 1 — Timeseries Column Export, full-stack
- **Rationale**: #711 backend done; #720 frontend stranded. Timeseries module test coverage 0.37 (HIGH risk). Shipping these together prevents partial-feature drift and forces test investment in the riskiest backend module.
- **Items**: #711 (verify-close), #720 (frontend), regression tests for column-export endpoint, frontend Vitest scaffolding for the export page (kicks off frontend test infra).
- **Sequencing**: finish !808/!809 → cut 5.4.2 → start #720 → land Vitest scaffolding alongside.
- **Tests**: backend integration tests for column-export endpoint shape; first frontend component tests in the codebase.

### Epic 2 — Permissions Hardening
- **Rationale**: F + C3 + H1 + H2 + #717 + tech-debt #1, #2 are one logical entity. C3 (permissions fallback) is critical and a single fix cascades.
- **Items**: C3 fallback fix → H1 + H7 (JWT expiry + jjwt upgrade together) → H2 (cache TTL one-liner) → #717 → triage of #41/#62/#424/#483/#667 → tech-debt #1.
- **Sequencing**: C3 first (severity); H1 + H7 paired; H2 simultaneously; then #717; then permissions-cluster review.
- **Tests**: Auth.Permissions module needs negative-path coverage *before* C3 fix to lock in current behaviour, then refactor.

### Epic 3 — Spatial Data Decision (graduate or deprecate)
- **Rationale**: Cluster C is gated behind a feature flag with critical SQL injection (C1). Test coverage 0.26. Decision needed before further investment.
- **Items**: C1 SQL injection fix; product decision on graduation; if graduate, batch #441-#447 / #530 / #557 + spatial integration tests + remove feature flag; if deprecate, document and remove.
- **Sequencing**: C1 first (security regardless); then product decision; then either graduation or deprecation work.
- **Tests**: Geometry encoding edge cases, SRID handling, parameterised SQL coverage.

### Epic 4 — Subscription Hardening
- **Rationale**: C4 (SSRF + ReDoS) is self-contained in `common/subscription`; module-isolated, no current test investment.
- **Items**: C4 SSRF allowlist; C4 ReDoS regex bound; subscription unit + integration tests.
- **Sequencing**: Independent — can run in parallel with anything.
- **Tests**: Adversarial URL fixtures, regex catastrophic-backtracking benchmarks.

### Epic 5 — Search & Neo4j Hardening
- **Rationale**: C5 (Cypher injection) cross-cuts cluster H; cluster E is the structural fix that makes C5 robust rather than patched. Search test coverage 0.38.
- **Items**: C5 Cypher parameterisation; cluster E (#274, #577, #660); affected H bugs (#696-#707 search-related); search regression test suite.
- **Sequencing**: C5 patch first as defence-in-depth → start E refactor (long, foundational) → land #660 (semantic, depends on E) as the proof point → close out D in its wake.
- **Tests**: Cypher query parameterisation tests; Neo4j contract tests for the refactored model.

### Epic 6 — Frontend Foundation (enabler, not feature)
- **Rationale**: Zero frontend tests. Cluster B + #720 + K cannot be safely refactored without a baseline. This epic produces test infra, not features.
- **Items**: Vitest + Vue Test Utils setup; Playwright e2e for one critical flow (login + collection list); component tests for any component touched by Epic 1's #720.
- **Sequencing**: Begins concurrent with Epic 1's frontend work — first tests land *with* #720.
- **Tests**: Self-meta — this is the test investment.

### Epic 7 — CI Security Plumbing (enabler)
- **Rationale**: SAST + dependency scanning + secret scanning in CI converts every future PR into a security-aware one. De-risks all of F, C, H, Subscription work.
- **Items**: Activate SpotBugs+findsecbugs in `<build>` (move from `<reporting>`, fail on Medium); enable GitLab SAST template; add Trivy for container scanning; add `bandit` or `semgrep` for `scripts/`; extend secret scanning to fail on default values like `shepard_secret`.
- **Sequencing**: Land before Epic 2 starts shipping fixes (so fixes are validated by automated checks).

### Epic 8 — Dependency Update Hygiene
- **Rationale**: Cluster J + #718 meta-issue. 19 open Renovate MRs is debt and a collision risk. H6 (`dotenv`) and H7 (jjwt) are security-flavoured.
- **Items**: Drain J in batches; replace `dotenv` with `python-dotenv`; fix `renovate.json` typo (`natchUpdateTypes`); prune stale Vue 2-era pins; relax `<0.12` jjwt and `<5.11` junit pins.
- **Sequencing**: Non-colliding MRs (build tooling, frontend libs not touching #720) first, in parallel with everything; Quarkus !723 *after* !808/!809 land; nuxt !722/!758 *after* #720 lands; H6 and H7 prioritised within their batches.

## Sequencing recommendations

### MUST go first (blocking or critical)
1. **C3 permissions fallback fix** — critical severity, simplest blocker to ship.
2. **C5 Cypher injection patch** — critical, defence-in-depth before any E refactor.
3. **C1 SQL injection (spatial)** — critical even if cluster C is deprecated.
4. **Finish A's #712-#716 refactor (!808/!809)** — already in flight; blocks 5.4.2 cut and creates merge-conflict surface for J.
5. **Epic 7 CI plumbing** — enables every later security fix.

### SHOULD be parallel (independent)
- **Epic 4 (Subscription)** — module-isolated.
- **Epic 6 (Frontend foundation)** — enables but doesn't block; can start anytime.
- **Renovate non-colliding batch** — mechanical, parallelisable.
- **Epic 2 sub-items H1, H2, H7** — small, low-conflict; can land while A is still in flight.
- **Cluster G (UX triage)** — pure triage, no engineering dependency; close or schedule.

### Can-defer (low value or blocked)
- **Cluster I (long-tail research)** — most are SUPERSEDED candidates; close en masse.
- **Cluster K (!498)** — close unless reviver volunteers; rebase cost > value.
- **Cluster B feature work** — defer until Epic 6 frontend test infra exists; otherwise risks the same bit-rot as !498.
- **Cluster D feature work** — gated on E's refactor; queue behind Epic 5.
- **Cluster C feature work** beyond C1 — gate on product graduation decision.

### Depends on infrastructure improvements
- **Frontend feature work (B, #720, G)** ← Epic 6 (test infra).
- **All security epics** ← Epic 7 (CI SAST/dep scanning).
- **Epic 5 long-term** ← Cluster E completion (Neo4j model).
- **Test coverage gates on critical modules** ← coverage CI thresholds (timeseries, semantic, search, labJournal HIGH risk).

## Risk callouts

1. **Quarkus update !723 vs A's !808/!809** — both touch backend dependency surface. Mitigation: freeze !723 until !808/!809 merge and 5.4.2 ships; rebase !723 after.
2. **Nuxt updates !722/!758 vs #720 frontend export** — same Nuxt 3 / Vuetify surface. Mitigation: decide order — either land Renovate first and rebuild #720 against new versions, or land #720 first; do not interleave.
3. **Cluster E Neo4j refactor is foundational and stale** — every month it slips, D, parts of A, and Epic 5's deep fix slip with it. Mitigation: explicit owner assignment.
4. **C5 Cypher injection patch must NOT be conflated with E** — patch first as parameterisation, refactor later. Conflating delays the security fix behind a multi-month effort.
5. **Permissions Epic 2 has no current owner** — F has been long-standing. C3 is critical. Mitigation: assign Epic 2 explicitly.
6. **Frontend zero tests + active feature work (#720)** — every frontend MR is a regression risk. Mitigation: Epic 6 must start *with* #720, not after.
7. **`dotenv` supply chain in scripts/** — low-effort to remove; high impact if compromised. Do not let it sit in J's queue.
8. **jjwt 0.11 EOL + JWT no-expiry entanglement** — fixing H1 on EOL'd jjwt is wasted work; bundle H1+H7 in one MR.
9. **`UserLastSeenCache` 30 min vs documented 5 min (H2)** — small fix but the doc/code drift signals Auth.Users lacks tests; treat as a coverage signal.
10. **Renovate volume (19 MRs) creates review fatigue** — batch by risk class (frontend libs / build tooling / runtime / security-relevant) and review per batch.
11. **Cluster I (research) eats backlog signal** — closing SUPERSEDED issues materially improves the ability to read the remaining 100ish issues. Do this before next planning round.
12. **Test coverage HIGH-risk modules align with active feature work** (timeseries 0.37 ↔ A; search 0.38 ↔ Epic 5; labJournal 0.20 ↔ no current cluster but a latent bomb). Tie test investment to feature work; do not let A ship more code without coverage gain.
