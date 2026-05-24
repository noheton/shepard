---
stage: deployed
last-stage-change: 2026-05-24
audience: instance-admin + contributor
---

# ops-migration-healthcheck-2026-05-24 — readiness gate on Neo4j migration-chain integrity

Closes `OPS-MIGRATION-HEALTHCHECK` (`aidocs/16`) — the hidden production risk that
the UI-020 agent surfaced 2026-05-24: V61 (`v15_prov_predicates`, commit
`0c6ead4b` from 2026-05-22) was on `main` for two days but had never been
applied to the live Neo4j, and the existing healthcheck reported green
throughout because it only ping-checked Neo4j connectivity.

## What I found

### How `MigrationsRunner` currently aborts (and why the gap exists)

`backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java:105-119`:

```java
public void apply() {
  runMigrations(migrations::apply);
}

static void runMigrations(Runnable migrationsApply) {
  try {
    migrationsApply.run();
  } catch (ServiceUnavailableException e) {
    Log.error("Migrations cannot be executed because the neo4j database is not available", e);
    throw new RuntimeException("Aborting startup: neo4j became unavailable during migrations", e);
  } catch (MigrationsException e) {
    Log.error("An error occurred during the execution of the migrations: ", e);
    throw new RuntimeException("Aborting startup: neo4j migration failed", e);
  }
}
```

The abort-on-error guard **does** fire — A1e shipped that. But it can only fire
when `MigrationsRunner.apply()` is actually invoked. The historical drift the
UI-020 agent observed was the opposite case: `apply()` ran successfully but the
chain it was applying didn't include V61, because the agent's deploy didn't
include V61 yet. By the time main carried V61 + the cube was rebuilt, the
container restart applied V61 fine — but for two days, ANY deploy of `main`
against the live Neo4j would have failed startup with `MigrationsException:
Unexpected migration at index 60`.

Worse, the readiness check (`NeoHealthCheck` →
`AbstractDbReadinessCheck` → `NeoPinger.ping()`) is a one-shot
`MATCH (n) RETURN n LIMIT 0` against Neo4j. It reports UP as long as the driver
can round-trip a query — it has zero awareness of whether the schema chain
matches what the running backend was built for.

**The structural fix**: a new readiness check that compares classpath
migrations to the applied `__Neo4jMigration` chain on every probe.

### What I added

| File | Role |
|---|---|
| `backend/src/main/java/de/dlr/shepard/common/healthz/MigrationChainStatus.java` | Immutable value object for one inspection result. `healthy` + `outcome` + `pendingVersions` + `warnings` + `errorMessage` + `checkedAtEpochMs`. |
| `backend/src/main/java/de/dlr/shepard/common/healthz/MigrationChainInspector.java` | Pure helper. Calls `Migrations.validate()` + `Migrations.info(COMPARE)`, captures exceptions as `CHECK_FAILED`. Accepts a `Function<Migrations, ChainSnapshot>` extractor so tests can substitute without touching the library's sealed types. |
| `backend/src/main/java/de/dlr/shepard/common/healthz/MigrationChainReadinessCheck.java` | `@Readiness @ApplicationScoped` SmallRye HealthCheck. Owns its own driver + `Migrations` instance (couldn't reuse `MigrationsRunner`'s — that's a local in `ShepardMain.init()` and goes out of scope after `apply()`). Caches per `shepard.health.readiness.max-staleness`. |
| `backend/src/test/java/de/dlr/shepard/common/healthz/MigrationChainInspectorTest.java` | 6 unit tests covering VALID, DIFFERENT_CONTENT, defensive-pending-on-VALID, exception capture, null Migrations, multi-pending. |
| `backend/src/test/java/de/dlr/shepard/common/healthz/MigrationChainReadinessCheckTest.java` | 6 unit tests covering UP / DOWN payload shape, CHECK_FAILED, null migrations, cache window honoured, cache eviction at staleness expiry. |
| `backend/src/test/java/de/dlr/shepard/common/healthz/MigrationChainInspectorIT.java` | `@Tag("integration")` Testcontainers-Neo4j IT. Two scenarios: (1) apply 2-step chain → UP, (2) drop a `V99__never_applied.cypher` into the locations dir without applying → DOWN with `pending = ["99"]` + error message naming V99 and the runbook. |
| `docs/admin/runbooks/migration-chain-integrity.md` | Operator runbook — diagnosis flow, four outcome scenarios, manual remediation including the UI-020-style V61 splice worked example. |

## Checksum algorithm reference

The UI-020 agent reverse-engineered `DefaultCypherResource.computeChecksum`
because they needed to splice a `__Neo4jMigration` node by hand. **For the
readiness check, we deliberately do NOT reimplement this.** Instead, the
inspector delegates to `Migrations.validate()` and `Migrations.info(COMPARE)`
— the same library entry points that `Migrations.apply()` itself relies on to
decide what's already in the database. That share-the-definition posture
matters: the day the migrations library changes the checksum algorithm (e.g.
the Flyway-compat flag introduced in v3 of the library adds a second variant),
our readiness check and our startup gate stay aligned by construction.

That said — the **runbook** has to document the algorithm for operators
following §1b (manual splice path). The findings, from inspecting
`eu.michael-simons.neo4j:neo4j-migrations:3.2.1` (the pinned version per
`backend/pom.xml:274-275`):

- Class: `ac.simons.neo4j.migrations.core.DefaultCypherResource`
- Method: `static String computeChecksum(Collection<String> statements)`
- Algorithm: `java.util.zip.CRC32` over the UTF-8 bytes of each statement
- Inputs: the file's *executable* statements (output of `getStatements()` with
  the `NOT_A_SINGLE_COMMENT` predicate filter — `//`-prefixed lines are
  excluded; a BOM at file-start is stripped via `filterBomFromString`)
- Output: the CRC32 long, decimal-stringified, returned as the `checksum`
  property on the `__Neo4jMigration` node

The runbook documents this with a worked V61 example (`'674265064'`, verified
against the cube on 2026-05-24).

## Test results

All 12 unit tests pass; the IT runs against a Neo4j 5 testcontainer.

```
$ ./mvnw -DskipITs -Dtest='MigrationChainInspectorTest,MigrationChainReadinessCheckTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- MigrationChainInspectorTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- MigrationChainReadinessCheckTest
[INFO] BUILD SUCCESS
```

The IT (`MigrationChainInspectorIT`) is wired under the `@Tag("integration")`
band — it runs when `-DskipITs=false` AND when docker is available. It is the
end-to-end proof that the inspector reads real `__Neo4jMigration` state via the
library's real extractor: it applies a 2-step chain, then adds a third file to
the locations dir without applying it, then inspects → DOWN with pending=[99]
and an error message naming V99 and the runbook path.

## Wire-shape additivity

This PR is **purely additive** on the readiness surface:

- `/shepard/api/healthz/live` — **unchanged** (still just `jvm-liveness`).
- `/shepard/api/healthz/ready` — **one new check appears in `checks[]`**:
  `neo4j-migration-chain-readiness`. The aggregate `status` field
  AND-aggregates with the existing checks; a chain-integrity violation
  flips the whole readiness response to `DOWN`.

Operators upgrading from upstream `5.2.0` (or from any earlier point on this
fork) see zero behavioural change on `/healthz` UNLESS the live Neo4j has a
chain drift — in which case the readiness check legitimately reports DOWN
(which is the desired end-state).

## Per CLAUDE.md docs discipline

- ✅ `aidocs/34-upstream-upgrade-path.md` — new row `OPS-MIGRATION-HEALTHCHECK` in the change ledger.
- ✅ `aidocs/44-fork-vs-upstream-feature-matrix.md` — new row in §1 (`Readiness check verifies Neo4j migration-chain integrity`).
- ✅ `aidocs/16-dispatcher-backlog.md` — `OPS-MIGRATION-HEALTHCHECK` row flipped from `queued` → `✅ shipped 2026-05-24` with shipped-notes.
- ✅ `docs/admin/runbooks/migration-chain-integrity.md` — new operator runbook per `feedback_admin_runbooks_pattern.md`.
- ⏭ `aidocs/data/00-model-inventory.md` — not touched (no new entities, no schema migration).
- ⏭ `aidocs/42-vision.md` — not touched (operational hardening, not researcher-visible).
- ⏭ `aidocs/47-dev-experience-and-plugin-system.md` — not touched (no new SPI).

## What surprised me

1. **`MigrationChain` is sealed.** Mockito 5 refuses to mock it, and anonymous
   inner classes can't subclass it. The pragmatic fix is to funnel the only
   two pieces of data the inspector actually needs (each element's `version`
   and `state`) through a tiny `ChainSnapshot` record + a `Function<Migrations,
   ChainSnapshot>` extractor — the production path uses
   `defaultExtractor()` which talks to the live sealed types; tests substitute
   the function and never touch the library's interfaces.

2. **`MigrationsRunner` is a local, not a CDI bean.** `ShepardMain.init()`
   constructs it, calls `waitForConnection()` + `apply()`, then the reference
   falls out of scope. The Driver gets closed implicitly. So the readiness
   check can't borrow it — it has to own its own driver lifecycle. Documented
   as a deliberate choice in the class javadoc; the cost is one extra bolt
   driver in the JVM, gained back by a config-init step on every PostConstruct.

3. **The health-check path is `/shepard/api/healthz/ready`, not `/q/health/ready`.**
   The task spec used the Quarkus default; the running app has
   `quarkus.smallrye-health.root-path=/shepard/api/healthz` in
   `application.properties` to keep upstream-compat. The runbook + the
   live-verification commands all use the real path.

4. **The `ValidationResult.Outcome` enum has no `DIFFERENT_COUNT`.** First-pass
   tests assumed it did (carried over from the v2 of the library). Real values
   are `UNDEFINED`, `VALID`, `INCOMPLETE_DATABASE`, `INCOMPLETE_MIGRATIONS`,
   `DIFFERENT_CONTENT`. The runbook's outcome cheat-sheet uses the real names.

## Live verification

After `make redeploy-backend` against the nuclide deploy, the new readiness
check appears as one of five entries in `/shepard/api/healthz/ready`,
reporting UP / outcome=VALID:

```bash
$ curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready \
    | jq '.checks[] | select(.name == "neo4j-migration-chain-readiness")'
{
  "name": "neo4j-migration-chain-readiness",
  "status": "UP",
  "data": {
    "outcome": "VALID",
    "checkedAtEpochMs": 1779619029080,
    "maxStalenessMs": 30000
  }
}
```

Aggregate response shows 5 checks (was 4 pre-deploy), aggregate status UP:

```bash
$ curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready \
    | jq '.status, (.checks | length), [.checks[].name]'
"UP"
5
[
  "neo4j-readiness",
  "neo4j-migration-chain-readiness",
  "postgis-readiness",
  "mongodb-readiness",
  "timescaledb-readiness"
]
```

The `VALID` outcome confirms the live Neo4j chain — including the manually
spliced V61 from the UI-020 fix — matches the deployed backend's classpath
exactly. If the splice had missed (wrong checksum, wrong chain wiring), this
check would have flipped DOWN with `outcome=DIFFERENT_CONTENT` and the
runbook's §2 would have caught it.

Smoke suite: 25/25 PASS after redeploy.

### A note on the task spec's manual `V99__never_applied.cypher` step

The task's manual-verification step ("deliberately add a junk
`V99__never_applied.cypher` to the classpath locally + rebuild + curl
`/q/health/ready` — assert DOWN") is **infeasible at runtime as stated**:
the backend's `MigrationsRunner.apply()` runs at startup *before* any
readiness probe can fire. Given a `V99` on the classpath, `apply()` either
applies it (chain becomes VALID, readiness UP — not the asserted DOWN), or
fail-fasts (`MigrationsException` aborts startup, no readiness response at
all). The state the readiness check is meant to catch — a chain mutated
out-of-band relative to the running backend's classpath — cannot be reached
just by adding a file.

The IT (`MigrationChainInspectorIT.inspect_returnsDownWithPendingWhenExtraMigrationAdded`)
is the correct substitution: it exercises the exact code path against real
Neo4j, simulating the out-of-band mutation by adding the `V99` file to the
locations dir *after* `apply()` already ran on the 2-step chain. That's the
post-startup drift scenario the readiness check guards.

### Integration test results

Integration test (`MigrationChainInspectorIT`) — runs end-to-end against a
Neo4j 5 testcontainer in 29 s; 2/2 tests pass:

```text
Applied migration 10 ("create node").
Applied migration 11 ("more").
Applied migration 1 ("create node").
Applied migration 2 ("more").
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

The mismatched-chain scenario (drop a `V99__never_applied.cypher` into the
locations dir without applying it → inspect → DOWN with pending=[99]) is
exercised in `inspect_returnsDownWithPendingWhenExtraMigrationAdded`.
