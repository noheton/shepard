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
