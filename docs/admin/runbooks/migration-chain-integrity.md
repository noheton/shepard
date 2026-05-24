---
layout: default
title: "Runbook — Diagnose and repair a divergent Neo4j migration chain"
description: "What to do when /shepard/api/healthz/ready flips DOWN with neo4j-migration-chain-readiness reporting INCOMPLETE_MIGRATIONS or DIFFERENT_CONTENT. Covers diagnosis, manual remediation, and chain repair."
stage: feature-defined
last-stage-change: 2026-05-24
audience: instance-admin
---

# Diagnose and repair a divergent Neo4j migration chain

> **When to use this runbook**: `/shepard/api/healthz/ready` returns HTTP 503 and the
> `neo4j-migration-chain-readiness` check shows `"status": "DOWN"` with an `outcome`
> of `INCOMPLETE_MIGRATIONS`, `DIFFERENT_CONTENT`, `INCOMPLETE_DATABASE`, or
> `CHECK_FAILED`. This means the live Neo4j's `__Neo4jMigration` chain does not
> match the classpath the running backend was built from.

## Background — why this matters

The Shepard backend applies Cypher migrations from `backend/src/main/resources/neo4j/migrations/`
on startup via `MigrationsRunner.apply()`. A clean deploy of a new image runs every
migration that hasn't been applied yet, then the backend starts serving traffic.

The hidden risk: if the migration apply step is somehow **skipped** but the
backend still starts (e.g. an operator restored an older Neo4j volume from a
snapshot taken before the latest deploy, or the backend was started with a
custom `-Dquarkus.profile` that disabled `@Startup`), the API will appear to
work but queries against tables / fields introduced by the missing migration
will silently return the wrong shape.

Before 2026-05-24 (`OPS-MIGRATION-HEALTHCHECK`), the readiness probe only
checked Neo4j connectivity (a one-shot Cypher ping). A missing migration would
not flip readiness, and the operator would have no signal until a downstream
query failed. The `neo4j-migration-chain-readiness` check closes this gap by
asserting on every probe that the live chain matches the classpath.

---

## 0. Pre-flight — read the readiness payload

```bash
# Replace the host with your instance.
curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready | jq '.checks[]
  | select(.name == "neo4j-migration-chain-readiness")'
```

Expected DOWN payload shape:

```json
{
  "name": "neo4j-migration-chain-readiness",
  "status": "DOWN",
  "data": {
    "outcome": "INCOMPLETE_MIGRATIONS",
    "checkedAtEpochMs": 1779617403256,
    "maxStalenessMs": 30000,
    "pendingVersions": "61,62",
    "warnings": "...",
    "errorMessage": "Neo4j migration chain integrity violation (outcome=INCOMPLETE_MIGRATIONS); pending versions: 61, 62. See docs/admin/runbooks/migration-chain-integrity.md for remediation."
  }
}
```

Capture the `pendingVersions` and `outcome` — you'll need them below.

Outcome cheat-sheet (from `ac.simons.neo4j.migrations.core.ValidationResult.Outcome`):

| Outcome | Meaning | Section to follow |
|---|---|---|
| `VALID` | (Shouldn't be DOWN) | n/a — file a bug |
| `INCOMPLETE_MIGRATIONS` | Classpath has migrations the DB hasn't applied | §1 — apply missing migrations |
| `INCOMPLETE_DATABASE` | DB has migration nodes for versions absent from classpath | §3 — chain has drifted ahead |
| `DIFFERENT_CONTENT` | Same version on both sides, but checksum mismatch | §2 — checksum repair |
| `CHECK_FAILED` | Inspector itself errored (driver down, classpath unreadable) | §4 — inspector failure |

---

## 1. Pending migrations — apply missing migrations

This is the most common case: the classpath has e.g. V61 but the database
stops at V60. Two paths.

### 1a. Preferred path — rolling restart

If you can tolerate a brief restart, just bounce the backend. `MigrationsRunner`
will detect the gap and apply the missing migrations as part of `@Startup`. Per
the rule in `CLAUDE.md` ("Migrations must be **idempotent** (safe to re-run) and
**fail-fast** (abort startup on error)"), this is the supported recovery path:

```bash
# Replace with your compose/k8s rollout command.
docker compose restart backend
```

Then re-check readiness:

```bash
curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready | jq '.checks[]
  | select(.name == "neo4j-migration-chain-readiness") | .status'
# Expected: "UP"
```

If startup fails with `MigrationsException: Aborting startup: neo4j migration failed`
in the backend logs, that's the fail-fast guard — fix the underlying migration
script, rebuild the image, redeploy. **Do not** comment out the migration; that
just defers the failure.

### 1b. Cannot restart — manually apply one specific migration

If you must keep the backend running and apply a known-good migration manually
(e.g. you're in a maintenance window but the backend is also handling live
traffic on a sibling instance), follow the worked example below. This is the
path the UI-020 agent used on 2026-05-24 to splice V61 into the live chain.

**Worked example: applying V61 (`v15_prov_predicates`) manually**

```bash
# HOST: any host that can reach the live neo4j container (typically the host
# running infrastructure-neo4j-1).

# 1. Execute the migration body itself. V61 is idempotent (uses MERGE), so
#    safe to re-run even if some of its statements have already been applied.
docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  < backend/src/main/resources/neo4j/migrations/V61__v15_prov_predicates.cypher

# 2. Compute the CRC32 checksum the library will expect for this file.
#    The library (eu.michael-simons.neo4j:neo4j-migrations 3.2.1) uses
#    java.util.zip.CRC32 over the UTF-8 bytes of each *executable* statement
#    (single-line `//` comments are stripped first), via
#    DefaultCypherResource.computeChecksum(Collection<String>).
#
#    The simplest way to get the right number is to let the library tell you:
#    spin up a throwaway Migrations instance pointed at JUST this one file and
#    ask info() for the element. Or — for the manual path — paste the value
#    that another instance (with V61 applied via the normal startup path) reports.
docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration {version: '61'}) RETURN m.checksum;"
# Expected example: "674265064" — confirmed for V61 on cube box, 2026-05-24.

# 3. Splice the migration node into the chain. The chain is a linked list
#    via :MIGRATED_TO; V61 must sit between V60 and V63 (the existing tail).
#    This is sensitive — make a backup first.
docker exec infrastructure-neo4j-1 neo4j-admin database dump neo4j \
  --to-path=/data/dumps/pre-V61-splice-$(date +%Y%m%d-%H%M%S).dump

docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" <<'CYPHER'
// Pre-splice assert: V60 exists, V63 is current tail, V61 does NOT exist.
MATCH (v60:__Neo4jMigration {version: '60'}) WITH v60
MATCH (v63:__Neo4jMigration {version: '63'}) WITH v60, v63
OPTIONAL MATCH (v61:__Neo4jMigration {version: '61'})
WITH v60, v63, v61 WHERE v61 IS NULL

// Remove the V60 -> V63 edge.
MATCH (v60)-[r:MIGRATED_TO]->(v63) DELETE r

// Create V61 node with the reverse-engineered checksum.
CREATE (v61:__Neo4jMigration {
  version: '61',
  description: 'v15 prov predicates',
  checksum: '674265064',
  source: 'V61__v15_prov_predicates.cypher',
  type: 'CYPHER',
  installedOn: datetime(),
  installedBy: 'manual-runbook/' + coalesce($actor, 'instance-admin'),
  executionTime: duration('PT0S')
})

// Re-link the chain.
CREATE (v60)-[:MIGRATED_TO]->(v61)
CREATE (v61)-[:MIGRATED_TO]->(v63)

RETURN v60.version, v61.version, v63.version;
CYPHER
```

After splicing, re-poll the readiness check. The `neo4j-migration-chain-readiness`
check caches its result for `shepard.health.readiness.max-staleness` (default
30 s) — wait that long, or restart the backend to clear the cache immediately.

---

## 2. Checksum mismatch — `DIFFERENT_CONTENT`

The database has a `__Neo4jMigration {version: 'N'}` whose `checksum` does not
match the CRC32 of the current classpath file for V`N`. This happens when:

- Someone edited a migration file after it was applied (forbidden — migrations
  are immutable once shipped).
- Someone manually inserted a `__Neo4jMigration` node with the wrong checksum
  (e.g. botched §1b above).
- The classpath file was corrupted in flight (rare).

### Diagnose which file

```bash
# Get the DB-side checksum for every applied migration.
docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration) WHERE m.version IS NOT NULL
   RETURN m.version AS v, m.checksum AS db_checksum
   ORDER BY toInteger(m.version);"
```

Cross-reference against the warnings line in the readiness DOWN payload — the
library prints something like `Checksum of V57 doesn't match local file`.

### Fix path A — DB's checksum is correct, classpath is corrupted

Restore the classpath file from git:

```bash
git -C /opt/shepard checkout HEAD -- \
  backend/src/main/resources/neo4j/migrations/V57__NOOP_AbstractDataObject_fair_fields.cypher
# Then rebuild + redeploy the backend image.
```

### Fix path B — classpath is correct, DB checksum is wrong

The migration on disk is the source of truth. **Do not** edit the classpath
file to match the DB — that perpetuates the drift. Instead, repair the DB
checksum (requires the same UI-020-style worked example as §1b for computing
the correct value):

```bash
docker exec -i infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration {version: '57'}) SET m.checksum = '<correct-crc32>' RETURN m;"
```

Then restart the backend (the `migrations.repair()` library call would also do
this programmatically — that's a future BACKEND-DX2 enhancement).

---

## 3. Chain has drifted ahead — `INCOMPLETE_DATABASE`

The database has applied migrations the running backend doesn't know about
(e.g. someone deployed a newer image, applied V64 + V65, then downgraded to the
older image that stops at V63).

**Do not** try to delete the surplus `__Neo4jMigration` nodes — the schema
changes those migrations made are still in the database, and deleting their
chain entries will mask that. Instead:

1. Confirm the offending versions: `MATCH (m:__Neo4jMigration) WHERE NOT m.version IN [<known good list>] RETURN m.version;`
2. **Roll forward** — deploy an image that includes those classpath files. This
   is almost always the right answer (the data's already there; just bring the
   code back in step).
3. If you genuinely need to roll back, find each migration's matching
   `V##_R__rollback.cypher` rollback file (see CLAUDE.md "Provide a rollback
   file ... when the change is data-mutating"), apply it manually via
   `cypher-shell`, then delete the `__Neo4jMigration` node.

---

## 4. Inspector failure — `CHECK_FAILED`

The readiness check itself couldn't run. Read `errorMessage` in the payload:

| `errorMessage` substring | Cause | Fix |
|---|---|---|
| `Migrations instance not initialised` | `@PostConstruct` init() in `MigrationChainReadinessCheck` failed (driver creation, classpath scan) | Check backend logs at startup for `MigrationChainReadinessCheck: failed to initialise` |
| `ServiceUnavailableException` / `ClientException` | Neo4j is down / unreachable | Address the underlying outage; the existing `neo4j-readiness` check will also be DOWN |
| `IllegalStateException: driver gone` | Driver was closed unexpectedly | Restart the backend |

If none of the above match, capture the `errorMessage` and file an issue; the
inspector's exception-handling path is meant to be exhaustive but the library's
behaviour evolves.

---

## 5. End-state verification

```bash
curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready \
  | jq '.status, (.checks[] | select(.name == "neo4j-migration-chain-readiness"))'
# Expected:
# "UP"
# {
#   "name": "neo4j-migration-chain-readiness",
#   "status": "UP",
#   "data": {
#     "outcome": "VALID",
#     ...
#   }
# }
```

And confirm the live chain matches what the running backend's classpath has:

```bash
# Backend's view (cached up to maxStalenessMs):
curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready \
  | jq '.checks[] | select(.name == "neo4j-migration-chain-readiness") | .data.outcome'
# "VALID"

# Direct DB query — sanity check that the chain length matches your classpath:
docker exec infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration) WHERE m.version IS NOT NULL RETURN count(m);"
```

And compare against the classpath count:

```bash
find /opt/shepard/backend/src/main/resources/neo4j/migrations -name 'V*.cypher' \
  -not -name '*_R__*' | wc -l
```

The two numbers should agree (`+/- 1` is fine if you have a BASELINE node — the
library's bootstrap migration).

---

## A note on out-of-order `installedOn` timestamps

When you run `MATCH (m:__Neo4jMigration) RETURN m ORDER BY m.version DESC`
you may see rows where a numerically-higher version was applied **before** a
numerically-lower one — e.g. V63 stamped `2026-05-22T15:38`, V61 stamped
`2026-05-22T15:39+1 day`. This is **working as intended**, not a sign of
chain corruption.

The migrations library applies versions forward-only on every restart. If a
new migration is spliced in late (V61 here was added to `main` two days after
V62 + V63 had already been applied to the live database), the next restart
applies the missing version with the current wall-clock timestamp. The chain
itself stays valid — every version is applied exactly once with the right
checksum — but the `installedOn` axis no longer monotonically tracks the
`version` axis.

The readiness check (`MigrationChainReadinessCheck`) validates **versions
applied + checksums** against the classpath; it does **not** validate
timestamp ordering. If you need a "what was applied when" timeline, query
`installedOn` ascending and the picture is correct.

**Why this is intentional.** The forward-only-from-current-state behaviour
is the library's correct response to late-arriving chain splices — refusing
to apply V61 because V63 was applied first would lock the deploy out of any
past-tense fix that lands on `main` after a partial deploy. Operator
action: none; this is documentation of expected behaviour. Filed by
NEO-AUDIT-2026-05-24-014.

---

## Provenance

- Closes: `aidocs/16` row OPS-MIGRATION-HEALTHCHECK (2026-05-24).
- Surfaced by: UI-020 follow-up — V61 (`v15_prov_predicates`, commit `0c6ead4b`,
  2026-05-22) was on `main` for two days before the live Neo4j caught up, and
  the existing readiness signal reported green throughout. Detailed
  reverse-engineering of the checksum splice is in
  `aidocs/agent-findings/ui-020-labjournal-bulk-fix-2026-05-24.md` §"What surprised me"
  + this runbook's §1b worked example.
- Code: `de.dlr.shepard.common.healthz.MigrationChainReadinessCheck` (readiness
  bean) + `MigrationChainInspector` (pure logic) +
  `MigrationChainStatus` (value object). Tests under the same package.
- Checksum algorithm: `ac.simons.neo4j.migrations.core.DefaultCypherResource.computeChecksum(Collection<String>)`,
  `java.util.zip.CRC32` over UTF-8 bytes of each executable statement (BOM
  stripped, single-line comments excluded). This runbook does NOT compute
  checksums in-band; it defers to the library via the readiness check and
  documents the algorithm only so an operator following §1b knows what shape
  is expected.
