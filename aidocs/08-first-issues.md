# First Issues — shepard

Snapshot date: 2026-05-04. Ranked entry-points for new contributors. Excludes security findings (handled separately in `07-security-issues.md` and Phase 1 of `11-implementation-plan.md`).

## Selection criteria

Each pick is one or more of:
- XS or S effort and low complexity
- High value relative to effort
- Partially implemented (clear next step)
- Unblocks a cluster
- Suitable for an outside contributor (well-scoped, no architectural debate required)

## Ranked shortlist

### 1. Verify-and-close #710 ("timeseries reference parameters")
- **Status**: DONE — `TimeseriesReferenceRest.java:267-275` already exposes the requested parameters.
- **Effort**: XS (read code, add a test if missing, close on GitLab with a one-line comment).
- **Value**: medium — clears stale-but-fresh issue; confirms feature delivery.
- **Why first**: zero-risk, builds reviewer trust, exercises the close-as-done flow.

### 2. Verify-and-close #711 ("column-based CSV export")
- **Status**: DONE — `CsvFormat.java`, `CsvColumnLineProvider.java`, commit `c4980a6`.
- **Effort**: XS.
- **Value**: medium — Sprint 23 / 5.4.2 milestone hygiene.
- **Why**: same as #710; confirms backend completion to motivate #720 frontend work.

### 3. #721 — Race condition in default file container
- **Status**: NOT_IMPLEMENTED.
- **Files**: `backend/src/main/java/de/dlr/shepard/data/file/services/FileContainerService.java` (no synchronization currently).
- **Acceptance**: concurrent creation of a default file container does not produce duplicate `default` relations; integration test demonstrates fix.
- **Effort**: S.
- **Value**: high — data-integrity bug; fresh issue.
- **Tests needed**: new integration test using `@QuarkusIntegrationTest` with concurrent calls.

### 4. #717 — Timeseries metadata accessible without permissions
- **Status**: NOT_IMPLEMENTED.
- **Files**: `backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java:80` (`getTimeseriesById`), `getTimeseries`.
- **Acceptance**: metadata access path enforces appropriate permission check (likely a metadata-only role distinct from container-read).
- **Effort**: S.
- **Value**: high — security-adjacent, fresh, in active milestone.
- **Tests needed**: negative-path tests for unauthenticated and unprivileged access to metadata endpoints.
- **Dependencies**: aligns with Permissions Hardening epic (`02-cluster-map.md` Epic 2).

### 5. #667 — Recent permission bug (fresh)
- **Status**: NOT_IMPLEMENTED, fresh.
- **Effort**: S.
- **Value**: high — fresh permission bug; aligns with C3 fallback work.
- **Why**: Pairs naturally with C3 fix; small enough to land first as a unit-test-driven fix.

### 6. !800 — Draft documentation update
- **Status**: in flight, fresh draft MR.
- **Effort**: S.
- **Value**: medium — documentation hygiene; assigned RolandGlueck/kauf_pt/lettfe.
- **Why**: ready to ship after review; low-risk merge.

### 7. #722 — Search/UX bug (kauf_pt)
- **Status**: NOT_IMPLEMENTED, fresh.
- **Effort**: S.
- **Value**: medium — owner already assigned.

### 8. #688 / #683 — Fresh search bugs
- **Status**: NOT_IMPLEMENTED, fresh.
- **Effort**: S each.
- **Value**: medium — search module test coverage is HIGH risk (0.38), so fixing these with tests adds doubled value.
- **Tests needed**: regression tests in `common/search` module.

### 9. Replace `dotenv` with `python-dotenv` in scripts
- **Status**: H6 dependency security finding.
- **Files**: `scripts/pyproject.toml:18`, `scripts/poetry.lock:158-169`.
- **Acceptance**: `dotenv` removed from manifest and lock; CLI continues to work; smoke-tested locally.
- **Effort**: XS.
- **Value**: medium — supply-chain risk.
- **Why**: trivial diff; obvious documented win.

### 10. Fix `renovate.json` typo (`natchUpdateTypes`)
- **Files**: `renovate.json:21`.
- **Acceptance**: ruff Renovate rule's `matchUpdateTypes` filter applies as intended.
- **Effort**: XS.
- **Value**: low/medium — silently broken automerge filter is restored.

### 11. Update ADR index
- **Files**: `architecture/src/09_architecture_decisions/index.adoc` — add ADR 019 and ADR 020 entries.
- **Acceptance**: rendered architecture docs include all ADRs that exist on disk.
- **Effort**: XS.
- **Value**: low — documentation hygiene.

### 12. Reduce `UserLastSeenCache` TTL from 30 min to 5 min (or update docs)
- **Files**: `backend/src/main/java/de/dlr/shepard/auth/security/UserLastSeenCache.java:8`.
- **Acceptance**: TTL matches documented "5-minute grace period" — or, if 30 min is intentional, architecture docs and `auth.adoc` are updated to match.
- **Effort**: XS.
- **Value**: medium — security finding H2; clears doc/code drift.

### 13. Move `#642`/`#643`/`#644`/`#645` UX-cluster items into a triage decision
- **Effort**: S total.
- **Value**: medium — backlog hygiene; enables clearer reading of remaining issues.
- **Why**: not engineering work but pure triage; ideal for a maintainer pass.

### 14. Add a Vitest scaffold to the frontend (paired with #720 work)
- **Acceptance**: `frontend` has Vitest configured; one example component test passes; CI runs `npm test` in MR pipeline.
- **Effort**: M (scaffolding) + per-MR cost thereafter.
- **Value**: high — unlocks Cluster B and #720 safely.
- **Why**: enabler; do this *with* the first frontend feature MR (#720), not after.

### 15. Activate SpotBugs/findsecbugs in CI
- **Files**: `backend/pom.xml:476-490` (move plugins from `<reporting>` to `<build>`); `.gitlab/ci/test_backend.yml`.
- **Acceptance**: `mvn -P ci verify` runs SpotBugs+findsecbugs and fails on Medium-severity security categories.
- **Effort**: S.
- **Value**: high — converts every future PR into a security-aware one.

## Quick-pick by archetype

- **First-time contributor, weekend afternoon**: #710 verify-close, #711 verify-close, ADR-index fix, `renovate.json` typo.
- **Backend Java developer**: #721 race condition, #717 metadata permissions, #667 permission bug, SpotBugs activation.
- **Python developer**: replace `dotenv` with `python-dotenv`, audit `scripts/poetry.lock`.
- **Frontend developer**: Vitest scaffolding paired with #720 column export UI.
- **Maintainer triage**: bulk-close UX cluster #642-#645, bulk-close research/refactoring backlog, decide spatial-graduation question.

## Items deliberately excluded from "first issues"

- All security CRITICAL findings (C1, C2, C3, C4, C5) — too high blast radius for first-issue work; tracked in `07-security-issues.md` and Phase 1 of the implementation plan.
- Cluster E (Neo4j refactor) — XL effort, foundational.
- Cluster B versioning frontend — gated on Epic 6 frontend test infra.
- Anything in cluster D (semantic) — blocked on E.
- Cluster I long-tail research — closure candidates, not implementation candidates.
