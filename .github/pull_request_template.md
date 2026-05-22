<!--
PR template — mirrors CLAUDE.md's "Always:" sections so that a PR
author (human or AI agent) cannot accidentally skip the durability,
documentation, and security rules that keep this fork's upgrade path,
vision, feature matrix, and security gates current.

Delete sections that genuinely don't apply, but think twice — most of
these apply more often than they look like they do.
-->

## Summary

<!-- 1–3 sentences. What does this PR change and why? Cite the
     aidocs/16 row ID if this implements a backlog slice. -->

## Backlog reference

<!-- aidocs/16 backlog row ID(s) implemented or progressed by this PR.
     Example: implements `VIS-T1`, progresses `IMPORT-W` to `done`. -->

- aidocs/16 ID:

## Type

<!-- Pick at least one. -->

- [ ] `feat` — new user-visible capability
- [ ] `fix` — bug fix
- [ ] `docs` — documentation only
- [ ] `refactor` — internal refactor, no behaviour change
- [ ] `test` — tests only
- [ ] `chore` — tooling, infra, deps
- [ ] `breaking` — admin-visible breakage (also tag with `BREAKING` in aidocs/34)

## Conventional Commits

<!-- The PR title should follow `<type>(<scope>): <subject>` where
     `<scope>` is the aidocs/16 row ID (e.g. `feat(VIS-T1): ...`,
     `fix(IMPORT-W2): ...`). Auto-categorisation in
     `.github/release.yml` relies on this. -->

- [ ] PR title follows Conventional Commits with an aidocs/16 scope.

## "Always:" checklist (from CLAUDE.md)

Check each row, **strike through with `~~rule~~` and a one-line reason if
it doesn't apply**. Don't leave unchecked rows silent — silence reads as
"the author forgot," and reviewers will bounce the PR.

### Upgrade path + ledger currency

- [ ] **aidocs/34 ledger** — added a row if this changes anything an
      upstream admin would notice (config keys, endpoints, schemas,
      defaults, dependencies, breaking behaviour). Marked `BREAKING` if
      the migration is non-trivial.
- [ ] **Migration script (if needed)** — Cypher under
      `backend/src/main/resources/neo4j/migrations/`, SQL under
      `backend/src/main/resources/db/migration/`. Idempotent +
      fail-fast. Rollback file shipped for data-mutating migrations.

### API-version policy

- [ ] **New endpoints land under `/v2/`** — not `/shepard/api/...`. The
      upstream API surface stays frozen byte-for-byte.

### Live docs currency

- [ ] **aidocs/42-vision.md** — updated if this is user-visible (new
      payload kind, top-level concept, cross-cutting capability, removed
      or renamed surface).
- [ ] **aidocs/44-fork-vs-upstream-feature-matrix.md** — status symbol
      flipped (`📐 designed` → `🚧 in-flight` → `✓ shipped`).
- [ ] **docs/reference/<feature>.md** — reference page landed in this
      PR if user-visible. Task page in `docs/help/` added when the
      feature has a casual expression.
- [ ] **Plugin docs trio** — if this is plugin code, `plugins/<id>/docs/`
      has `reference.md` + `quickstart.md` + `install.md`. Stale plugin
      docs are treated like stale aidocs/42.

### Tests + coverage

- [ ] **Tests added in the same PR.** No "follow-up tests" promises.
- [ ] **Backend coverage** ≥ 60% line + 60% branch (current snapshot
      ~68%/66%). New code targets ≥ 70% line per the CI
      `min-coverage-changed-files: 70` rule.
- [ ] **Frontend coverage** holds; Vitest test added per
      `feedback_always_write_tests.md`.

### Security

- [ ] **SpotBugs + findsecbugs** green (no new High-confidence findings,
      or suppressed with `@SuppressFBWarnings` + justification).
- [ ] **CodeQL** green (Security tab clean for this branch).
- [ ] **OWASP Dependency-Check** green at CVSS ≥ 7.0 (suppressions in
      `backend/dependency-check-suppressions.xml` carry CVE + reasoning).
- [ ] **Trivy** green on built image (CRITICAL/HIGH, `--ignore-unfixed`).
- [ ] **gitleaks** green; no secrets in diff.
- [ ] **dependency-review** green; no banned licences
      (GPL/AGPL/SSPL families).

### Architecture posture

- [ ] **Plugin-first heuristic considered** — per CLAUDE.md
      `§"Always: think plugin-first for new features"`. New payload
      kinds + external integrations default to plugin shape. If this
      lands in-tree, the linked design doc says why.
- [ ] **Operator runtime knob** — if this ships a feature toggle /
      retention window / cap / integration endpoint, the default
      shape is a `:*Config` Neo4j entity + `GET/PATCH /v2/admin/<feature>/config`
      + CLI parity (per A3b / N1c2 / UH1a pattern). Deploy-time-only
      is the exception, not the rule.

### Doc-stage front-matter

- [ ] **aidocs/* doc-stage front-matter present** on any aidocs files
      added or substantively edited in this PR, per
      `aidocs/00-doc-stages.md` (gated on agent #171 landing — until
      then, this row is a leading indicator, not a blocker).

## Risk + rollback

<!-- One paragraph. What's the worst that happens if this is wrong?
     Is rollback a `git revert` (good) or does it require a
     data-mutating reverse migration (call it out)? -->

## Screenshots / API examples (if user-visible)

<!-- Front-end change → screenshot. New endpoint → curl example with
     real values (per `feedback_no_redactions.md`, not placeholders). -->

## Reviewer notes

<!-- Anything you want the reviewer to look at hard, or a gotcha you
     hit that future-you should know about. -->
