---
stage: deployed
last-stage-change: 2026-05-24
audience: operator + contributor
---

# ops-cleanup-2026-05-24 — un-ignore neo4j migrations + worktree env bootstrap

Two operational-hygiene rows closed in one PR. Both were surfaced by the UI-020
agent earlier in the day.

## OPS-MIGRATIONS-GITIGNORED (S, schema-integrity bug — FIXED)

### What was wrong

Root `.gitignore` carried two unanchored data-volume patterns:

```text
mongodb/
neo4j/
infrastructure/neo4j/
timescaledb/
```

A bare directory name like `neo4j/` in gitignore matches **any** directory
named `neo4j` at any depth (gitignore documentation:
https://git-scm.com/docs/gitignore — "If the pattern does not contain a
slash /, Git treats it as a shell glob pattern and checks for a match
against the pathname"). The intent was to ignore the docker-compose
bind-mount data volumes at the repo root (`/opt/shepard/neo4j/data/`,
etc.), but the pattern also matched:

- `backend/src/main/resources/neo4j/migrations/` — production Cypher
  migration scripts (the canonical post-A1e migrations chain)
- `backend/src/main/java/de/dlr/shepard/common/neo4j/` — Java packages
  for the Neo4j connector and `MigrationsRunner` class
- `backend/src/main/java/de/dlr/shepard/data/timeseries/migrations/` —
  Java migration runner classes

Existing tracked files were unaffected (git keeps tracking what's already
indexed even after an ignore rule is added), but **any new file in those
paths was silently ghosted**. This silently dropped two migration scripts:

| File | Live? | In repo? |
|------|-------|----------|
| `V54__NOOP_heroImageUrl_additive.cypher` | yes (applied on cube box) | NO |
| `V55__NOOP_ImportPlan_additive.cypher`   | yes (applied on cube box) | NO |
| `V56__NOOP_SemanticAnnotation_quantified.cypher` | yes | yes (probably `git add -f`) |
| `V57__NOOP_AbstractDataObject_fair_fields.cypher` | yes | yes (probably `git add -f`) |

Both V54 and V55 are NOOP migrations — they don't mutate schema. But they
ARE tracked by Flyway / the Shepard migration runner, so an operator
rebuilding from `main` would have a Neo4j migration chain that diverges
from any cube-side checkout. That's the failure mode this rule exists to
prevent.

### Investigation

```bash
# Confirm offending rule:
grep -n 'neo4j' .gitignore
# 119:neo4j/
# 120:infrastructure/neo4j/

# Confirm V54 is ignored under the v1 (broken) rule:
git check-ignore -v backend/src/main/resources/neo4j/migrations/V54__NOOP_heroImageUrl_additive.cypher
# .gitignore:119:neo4j/  backend/src/.../V54__NOOP_heroImageUrl_additive.cypher

# Enumerate files ignored under backend/src/ today:
find backend/src -path '*neo4j*' -type f | \
  while read f; do git check-ignore -q "$f" && echo "IGNORED: $f"; done
# IGNORED: backend/src/main/resources/neo4j/migrations/V54__NOOP_heroImageUrl_additive.cypher
# IGNORED: backend/src/main/resources/neo4j/migrations/V55__NOOP_ImportPlan_additive.cypher
```

Only V54 and V55 were missing — no other ghost files.

### Fix shape

Two changes to `.gitignore`:

1. **Anchor the data-volume rules at the repo root** by adding the leading
   `/`. `git check-ignore` confirms `backend/src/main/resources/neo4j/...`
   is no longer matched after the change.
2. **Add a comment** explaining the history so the next person who's
   tempted to "tidy up" by removing the leading `/` knows what breaks.

```text
# Runtime data volumes (Docker bind-mounts — never commit database files)
# Root-anchored with leading `/` so source-tree paths like
# `backend/src/main/resources/neo4j/migrations/` are NOT silently excluded.
# History: a bare `neo4j/` pattern previously caused V54/V55 migrations to
# be ghosted from the repo (see OPS-MIGRATIONS-GITIGNORED, 2026-05-24).
/mongodb/
/neo4j/
/infrastructure/neo4j/
/timescaledb/
```

Verification matrix after change:

| Path | Should be | check-ignore result |
|------|-----------|---------------------|
| `backend/src/main/resources/neo4j/migrations/V54...` | NOT ignored | exit 1 (clean) |
| `neo4j/data/transactions/...` (root data dir) | ignored | exit 0 (`/neo4j/`) |
| `mongodb/foo` (root data dir) | ignored | exit 0 (`/mongodb/`) |
| `infrastructure/neo4j/plugins/foo.jar` | ignored | exit 0 (`/infrastructure/neo4j/`) |
| `infrastructure/docker-entrypoint-initdb.d/mongodb/test.js` | NOT ignored | exit 1 (clean) |
| `backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java` | NOT ignored | exit 1 (clean) |

### Recovered files

```
backend/src/main/resources/neo4j/migrations/V54__NOOP_heroImageUrl_additive.cypher  (NOOP, comment-only)
backend/src/main/resources/neo4j/migrations/V55__NOOP_ImportPlan_additive.cypher    (NOOP, `RETURN 1`)
```

Both are NOOP-shaped — they exist so the migration chain has a numbered slot
for the feature that landed without needing a schema change. Re-running them
is safe (idempotent).

### What was NOT changed

- **`infrastructure/neo4j/`** stays fully ignored. Today that path only
  contains `plugins/neosemantics-5.20.0.jar` (a runtime download into a
  bind-mount). If we later need to track config under
  `infrastructure/neo4j/conf/`, the rule will need narrowing or an
  explicit `!infrastructure/neo4j/conf/` allow. Not needed today.
- **No `git add -f`** anywhere. Files were unblocked by tightening the
  pattern, then added normally.

## OPS-WORKTREE-ENV (XS, DX cleanup — FIXED)

### What was wrong

Agent worktrees (via `isolation: "worktree"`) live at
`/opt/shepard/.claude/worktrees/agent-*/`. They share git history with the
canonical checkout at `/opt/shepard/`, but **untracked files don't follow**
— and `infrastructure/.env` is untracked (it carries credentials, secret
keys, DB URLs).

`docker compose` reads `$(COMPOSE_DIR)/.env` for variable substitution.
Without it, `make redeploy` (and every variant) fails with cryptic
substitution errors instead of a clear "you're missing the env file"
message.

Every worktree-isolated agent so far has had to manually `cp` the file from
the canonical checkout. That's friction every dispatch pays.

### Fix shape

Two pieces, lightly coupled:

**1. `scripts/setup-worktree.sh`** — symlinks the canonical env into the
worktree. Idempotent (no-ops if already symlinked correctly; aborts if a
non-symlink env file already exists, rather than clobbering it).

```bash
# Safe to invoke from canonical OR worktree:
scripts/setup-worktree.sh
```

The script:

- Detects whether it's running in the canonical checkout (no-op) or a
  worktree (create symlink)
- Refuses to overwrite an existing non-symlink env file (so a worktree
  that wants its own override survives)
- Refuses to overwrite an existing symlink pointing somewhere else

**2. `make preflight-env`** — Makefile target gated on the env file
existing. All `deploy` / `redeploy*` targets now depend on it. If the env
file is missing, the operator sees an actionable error message that names
the script to run:

```
ERROR: ./infrastructure/.env not found.
       Run: scripts/setup-worktree.sh
       (symlinks the canonical /opt/shepard/infrastructure/.env into this worktree.)
```

### Verification

```bash
# Negative path (no env file):
rm infrastructure/.env
make preflight-env
# ERROR: ./infrastructure/.env not found.
#        Run: scripts/setup-worktree.sh
# exit code 2

# Recovery:
scripts/setup-worktree.sh
# setup-worktree.sh: symlinked .../infrastructure/.env -> /opt/shepard/infrastructure/.env

# Positive path:
make preflight-env
# (no output, exit 0)

# Idempotent:
scripts/setup-worktree.sh
# setup-worktree.sh: .../infrastructure/.env already symlinked to canonical env — OK.
```

### Acceptance

A fresh worktree-isolated agent can:

```bash
cd /opt/shepard/.claude/worktrees/agent-XXXX
scripts/setup-worktree.sh
make redeploy-frontend  # passes preflight-env, runs through normally
```

Or, if they forget the script, `make redeploy-frontend` aborts with the
self-documenting error pointing them at the fix.

### Follow-up not done in this PR

- **Auto-invocation on worktree creation**: a `.git/hooks/post-checkout`
  could run `setup-worktree.sh` automatically. Git hooks aren't versioned
  by default, so this needs either `core.hooksPath` config or a separate
  install step. Left for the next pass — the Makefile gate is sufficient
  to surface the issue clearly without it.
- **`OPS-MIGRATION-HEALTHCHECK`** (S, deeper fix) explicitly out of scope
  per the orchestrator. That one needs its own dispatch — it's about
  Neo4j migration-chain integrity verification, not the .gitignore
  ghosting fixed here.

## Changed files

```
.gitignore                                                            (M)
Makefile                                                              (M)
scripts/setup-worktree.sh                                             (A)
backend/src/main/resources/neo4j/migrations/V54__NOOP_heroImageUrl_additive.cypher  (A)
backend/src/main/resources/neo4j/migrations/V55__NOOP_ImportPlan_additive.cypher    (A)
aidocs/agent-findings/ops-cleanup-2026-05-24.md                       (A — this file)
aidocs/16-dispatcher-backlog.md                                       (M — rows flipped to shipped)
```

No application code touched (per orchestrator hard rule).
