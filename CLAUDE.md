# CLAUDE.md — durable instructions for Claude in this repo

## Always: maintain the upstream upgrade path

This repo is a fork / mirror of `gitlab.com/dlr-shepard/shepard`. Every
change we merge to `main` should leave **upgrading from upstream
shepard** to this repo's `main` a low-friction operator experience:

1. **Track each merged change** that materially affects an upstream
   admin in `aidocs/34-upstream-upgrade-path.md`. The table there is
   the authoritative ledger; keep it consistent with what's actually
   on `main`. An entry becomes stale the moment the code moves
   without the table moving.
2. **If a change has no clean migration path, flag it.** Don't paper
   over a breaking change as "additive" — say so explicitly in the
   tracker, mark it `BREAKING`, and call out what an operator must do.
3. **If a migration script is needed, ship it.** Cypher migrations go
   under `backend/src/main/resources/neo4j/migrations/`; SQL under
   `backend/src/main/resources/db/migration/`. Cite the file in the
   tracker entry. Migrations must be **idempotent** (safe to re-run)
   and **fail-fast** (abort startup on error — the
   `MigrationsRunner` post-A1e propagates `MigrationsException`).
4. **Migration tests are deferred but tracked.** Each entry that
   ships a migration should reference the planned regression test
   (testcontainer fixture or pre/post-migration assertion). Don't
   block landing a needed migration on the test, but don't let the
   test obligation rot either — note it in the tracker's "tests"
   column.
5. **Comfort over cleverness.** If two migration shapes work,
   prefer the one an admin can run from `cypher-shell` /
   `psql` / `mongosh` without setting up the project. Print
   progress logs. Provide a rollback file (`V##_R__*.cypher` style)
   when the change is data-mutating. Document an operator-runbook
   pointer in the migration's top comment.

When you merge a PR that touches anything an admin would notice
(config keys, endpoints, schemas, defaults, dependencies, breaking
behaviour), the tracker update is part of the same PR — not a
follow-up.

## API-version policy

**The upstream API surface stays frozen.** `/shepard/api/...` paths
must remain byte-compatible with `gitlab.com/dlr-shepard/shepard 5.2.0`
so a client built against upstream keeps working against this fork.

**All new endpoints we add land under `/v2/`.** This is the
development version where this fork's additions live (P-series,
R-series additive endpoints, U-series profile, A5 HDF, J1 lab
journal render, G1 git integration, T1 templates, anything else).
Existing `/shepard/api/` paths get only bug fixes that preserve
their wire shape.

The L2 chain (`aidocs/25`) is the formalisation of this split: L2d
introduces the `/v2/` shelf with `appId` as the native identifier;
L2e eventually drops the upstream `/v1/` long-id paths after a
deprecation window. Until L2d ships, treat any new endpoint as a
**`/v2/` candidate** and put it there; the routing scaffolding is
trivial to add early.

For an admin upgrading from upstream:
- `/shepard/api/...` works exactly like upstream — zero breakage.
- `/v2/...` is opt-in additional surface — they choose when to
  consume it.

Document each new endpoint's path in the same PR's `aidocs/34`
tracker row, calling out whether it's `/shepard/api/` (compat
surface, additive only) or `/v2/` (this fork's development surface).

## Always: keep `aidocs/42-vision.md` current

`aidocs/42-vision.md` is the **live researcher-facing vision** of
shepard. It is the one document a researcher would read to decide
whether shepard is the right tool for them. A stale vision doc is
worse than no vision doc.

When a PR ships a feature that's user-visible — a new payload kind,
a new top-level concept, a new cross-cutting capability, a removed
or renamed surface — **update `aidocs/42-vision.md` in the same PR**.
Most user-visible changes touch the §"Where it's going" section
(moving a bullet from "near horizon" to "what's in the box (today)")
or the §"What's in the box" payload-kind table.

Reviewers should reject feature-shipping PRs that don't touch the
vision when the feature is user-visible.

(Internal refactors, performance work, security fixes, dependency
bumps — none of these need a vision update.)

## Always: keep `aidocs/44-fork-vs-upstream-feature-matrix.md` current

`aidocs/44` is the **live contributor-facing progress tracker** —
the per-feature matrix comparing this fork to upstream. Audience
is contributors / PIs who want to know "what does this fork have
that upstream doesn't, and what's still in design?"

When a PR ships a feature, lands a design doc, or moves a row from
`📐 designed` to `🚧 in-flight` to `✓ shipped`, **update `aidocs/44`
in the same PR**. Most updates are a single status-symbol flip and
optionally a commit-hash citation in the row's notes.

`aidocs/34` (admin-facing upgrade ledger) and `aidocs/44`
(contributor-facing progress matrix) are siblings, not duplicates.
Both update on the same PR; they project different views of the
same change.

## Always: keep test coverage at the recommended floor

Backend coverage gate: **≥ 60% line / ≥ 60% branch** measured via
JaCoCo over the entire `de.dlr.shepard` package (current snapshot is
~68% line / 66% branch). Enforced in CI via
`-Djacoco.haltOnFailure=true` (`backend/pom.xml` `jacoco-maven-plugin`
`check` execution). Local `mvn verify` defaults to off so partial
runs don't error.

When a PR adds non-trivial code:

- **Add tests in the same PR.** A PR that drops coverage by more
  than 0.5% line or 0.5% branch (new code's coverage averaged
  against the bundle's prior level) needs either tests or an
  explicit `aidocs/44` row noting why the floor moved.
- **New code targets ≥ 70% line coverage** per the CI
  `min-coverage-changed-files: 70` rule (`backend-ci.yml`). Higher
  than the bundle floor because new code has no excuse — old code
  carries the legacy debt that pulls the average down.
- **Excluded classes** stay narrowly scoped (currently `Backend.*`
  and `Constants.*`). Adding to `<excludes>` to dodge the gate is
  the bug.

Raise the bundle floor incrementally — when the measured number is
2 percentage points above the floor, bump the floor in `backend/pom.xml`
and the CI `min-coverage-overall` to the new floor. Don't ratchet
in giant steps.

## Always: keep the security gates green

Six gates wired into CI:

1. **SpotBugs + findsecbugs** (Java SAST) — `spotbugs:check` with
   `Effort=Max`, `Threshold=High` in `backend-ci.yml`. Fail on
   any High-confidence finding. Fix or suppress with a
   `@SuppressFBWarnings` carrying a justification (no bare
   suppressions).
2. **CodeQL** (multi-language SAST, GitHub native) —
   `codeql.yml`. Java + JS/TS, `security-extended` query set.
   Findings flow to the Security tab → Code Scanning + inline
   PR annotations.
3. **OWASP Dependency-Check** (Java SCA) — `security.yml`
   weekly + on `pom.xml` / `poetry.lock` touch. Fails at
   `CVSS >= 7.0`. Suppress in
   `backend/dependency-check-suppressions.xml` with a CVE id +
   reasoning (no opaque suppressions).
4. **Trivy on the GHCR images** (container CVE scan) — runs in
   `build-images.yml` after each push. Fails on `CRITICAL,HIGH`
   with `--ignore-unfixed` (so `OS-pkg-with-no-fix` stragglers
   don't perma-block; the weekly schedule re-checks).
5. **gitleaks** (secret scan) — `security.yml` weekly + on push.
6. **Dependency-review** (PR-time license + new-CVE check) —
   `security.yml` on every PR that touches dependency manifests.
   Bans GPL / AGPL / SSPL families per the licence-compatibility
   policy; suppress in `.github/dependency-review-config.yml`
   with a justification.

Plus **SBOM** (CycloneDX) generated for every published image
via `anchore/sbom-action` in `build-images.yml` — uploaded as
workflow artifact + attached to GitHub releases.

A PR that introduces a finding from any of these gates must
either fix the issue or land a suppression with justification
in the same PR.

## Always: keep user-facing docs in step with shipped features

`docs/` (the user-facing docs, served both as the public Pages
site and as the in-app `/help` route per `aidocs/49`) is
two-track:

- **`docs/help/*.md`** — casual-task pages (front-door for
  newcomers; a stuck user gets answers in two clicks).
- **`docs/reference/*.md`** — per-primitive reference pages
  covering every shipped feature.

When a PR ships a user-visible feature, the matching
**reference page** must land in the same PR (and a **task page**
when the feature has a casual expression — uploading a new
payload kind, exporting a new shape, etc.). The catalogue lives
in `aidocs/49 §2.2`; reviewers reject feature PRs that don't
touch the relevant `docs/reference/*.md`.

This is the structural fix for screenshot/feature drift in the
user-facing docs — same shape as the vision currency
(`aidocs/42`), upgrade-tracker currency (`aidocs/34`), and
feature-matrix currency (`aidocs/44`) rules above.

(Internal refactors, performance work, security fixes, dependency
bumps — none of these need a docs update unless they change a
user-visible behaviour.)
