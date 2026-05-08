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
