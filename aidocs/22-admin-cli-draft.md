# Admin CLI — Candidate-Function Draft

**Scope.** Forward-looking design note for a future shepard administrator
command-line tool. Catalogues candidate verbs/nouns, frames the
authentication model (which is currently blocking), proposes a CLI
framework, and sketches a phased rollout. **This document is research
and writing only — no code is being written here.**

**Snapshot date:** 2026-05-05.
**Originating items.** `aidocs/16-dispatcher-backlog.md` row L1
("Admin CLI: cleanup of data marked for deletion, import/export of
collections as RO-Crate"); `aidocs/input/input_raw.md:705-708`; new
mention at `aidocs/input/input_raw.md:8398` ("Admin CLI und static
admin user (DISABLE user through env)").

---

## 1. Goals and non-goals

### 1.1 Goals

- Provide a single binary that an operator can run on the same host (or
  pod) as a shepard backend to perform tasks the REST API does not
  expose to ordinary users.
- Hard-delete entities that have been soft-deleted (`deleted = true` —
  set via `CollectionDAO.java:122`, `DataObjectDAO.java:258/281`,
  `FileContainerService.java:129`, `StructuredDataContainerService.java:112`,
  `SpatialDataContainerService.java:97`,
  `TimeseriesContainerService.java:117`,
  `LabJournalEntryDAO.java:23`, and the eight reference services that
  call `setDeleted(true)`) once they have aged past a configurable TTL.
- Trigger and observe Neo4j migrations in non-default ways: status
  read, resume, abort. The REST surface for this is being added under
  P3 (`16` row P3, commit `7cc74b8`); the CLI is a thin client over
  it.
- Inspect feature toggles and (later) flip them at runtime, on top of
  A3 (`16` row A3 — runtime toggle mechanism in place) and A3b (the
  `/admin/features` endpoint, queued).
- Export a collection plus its referenced payloads as an RO-Crate ZIP,
  reusing the existing `ExportService.exportCollectionByShepardId`
  path (`backend/src/main/java/de/dlr/shepard/context/export/ExportService.java:77`,
  exposed at `CollectionRest.java:274`).
- Import an RO-Crate ZIP into a target namespace. **Read path only
  exists today**; the inverse is not implemented and is a Phase-3
  scope item.
- Provide health probes against each storage backend, callable from a
  shell script. Pingers exist as
  `MongoHealthCheck.java`, `NeoHealthCheck.java`, and
  `TimescaleDBHealthCheck.java` under
  `backend/src/main/java/de/dlr/shepard/common/healthz/`.
- Manage long-lived API keys outside the user-impersonation model
  (`apikey create --user x --validUntil ...`). Depends on L5 (already
  done — commit `30c687a` added `validUntil`) and on A0 (admin role
  mechanism — `not implemented`, see §3).

### 1.2 Non-goals

- **Not a UI replacement.** Daily-driver workflows (browse, create,
  edit) stay on the Vue/Vuetify frontend. The CLI is for tasks that
  are inappropriate for an end-user UI: bulk delete, cache flush,
  migration orchestration.
- **Not a backup tool.** Snapshotting Neo4j / MongoDB / Postgres
  belongs to the infrastructure layer (`infrastructure/`,
  `infrastructure/docker-compose.yml`). The CLI may *trigger* a logical
  export of a collection, but consistent multi-DB snapshots are an ops
  concern.
- **Not a service-mesh / per-user impersonation tool.** Operations
  always run as the configured admin principal, never as another user.
  "Run this as user X" is explicitly out of scope.
- **Not a frontend.** No interactive TUI, no menus. Verb-noun-flag
  invocation only.
- **Not a config installer.** Quarkus configuration, OIDC keys, and
  the `oidc.public` / `oidc.role` properties (see `JWTFilter.java:67`)
  remain in `application.properties` / env vars / mounted secrets.

---

## 2. Naming

Two conventions in use across comparable tools:

- **Verb-noun**: `kubectl get pods`, `gh pr list`, `flyctl apps list`.
- **Noun-verb**: `git commit`, `cargo build`. Noun-verb groups by
  resource and reads naturally for a few well-known nouns; it scales
  poorly to many resources.

The candidate-function set below has roughly 10 nouns
(`migrations`, `cache`, `features`, `collection`, `apikey`, `db`,
`stats`, `health`, `subscriptions`, `sessions`) and a handful of verbs
each. Verb-noun (kubectl-style) scales better and matches the
existing in-repo Python CLI (`scripts/shepard_scripts/main.py:21`,
which uses `cli release`, `cli packages`, `cli sample`).

**Proposed binary name:** `shepard-admin`. Alternatives considered:

- `shepardctl` — concise, follows `kubectl` / `etcdctl` precedent.
  Slightly opaque to first-time users.
- `shepard` — already overloaded with the backend service name and the
  Python `shepard-client` package (`scripts/pyproject.toml:19`).

The `-admin` suffix makes intent explicit and avoids name-clash with
client SDKs.

**Proposed shape:**

```bash
shepard-admin <verb> <noun> [flags]
shepard-admin <verb> <noun> <subnoun> [args] [flags]
```

Where the second form covers cases like `shepard-admin cleanup deleted-entities`.

---

## 3. Authentication & authorisation model

### 3.1 Today

Roles are not consumed. `JWTFilter.java:205` instantiates
`new JWTPrincipal(audience, issuedFor, username, keyId, new String[0])`
— roles are always an empty array. The OIDC realm-access claim is
*read* and used as a gate (line 191), but never propagated. The
API-key path at `JWTFilter.java:258` constructs a 2-arg principal
which also defaults `roles = new String[0]` (`JWTPrincipal.java:24`).

`JWTSecurityContext.isUserInRole` (`JWTSecurityContext.java:23`)
correctly compares against the array, but since the array is always
empty, `@RolesAllowed(...)` would never let anything through.

This is tracked as backlog row **A0** ("Admin role mechanism") in
`16-dispatcher-backlog.md`. **A0 is a hard prerequisite for option (a)
below and therefore for the realistic Phase 1 of the CLI.** The blocker
report from agent A3b confirms the same finding.

The associated input from the maintainer (`input_raw.md:8398`,
"Admin CLI und static admin user (DISABLE user through env)") suggests
a **static admin** identity controlled by an environment variable as a
deliberate fallback when OIDC is unavailable — see option (c).

### 3.2 Three options for how the CLI authenticates

#### (a) Recommended: admin-role API key (after A0 lands)

The CLI presents `X-API-KEY: <jws>` for an API key whose owner has the
configured admin role. `JWTFilter.parseApiKey` (lines 224-247) already
validates JWS API keys and looks them up in Neo4j; A0 extends this
path so realm-access roles or a Neo4j-side `Role` relationship attaches
to the principal.

Pros: reuses existing infra; keys are revocable
(`ApiKeyService.deleteApiKey`, line 120); rotatable; auditable per call;
expirable (L5, `validUntil`, commit `30c687a`).

Cons: needs A0 first.

#### (b) Direct DB credentials, bypassing the HTTP API

The CLI opens a Neo4j Bolt session, MongoDB connection, and TimescaleDB
JDBC connection using credentials read from the same secret store the
backend uses. No HTTP, no JWT, no role check.

Pros: works today, no A0 dependency. Simpler error surface for
hard-delete (just run Cypher).

Cons: bypasses every audit log the backend writes
(`@SubscriptionFilter`, `LoggingFilter.java`, `UserFilter.java` — all
in `backend/src/main/java/de/dlr/shepard/common/filters/`). Bypasses
permission caches → cache flush is *required* after every mutation,
otherwise stale results will be served. Couples the CLI to private
schema details (Neo4j label/property names; SQL hypertable structure).
Encourages "lift over the wall" patterns that erode the API contract.

**Discouraged.** Useful as a last resort when the backend itself is
broken and won't accept HTTP traffic; ship as a `--direct-db` mode
explicitly gated by `--i-know-what-im-doing`.

#### (c) Local-only mode: same container, Unix-socket carve-out

The backend opens a Unix-domain socket listener (e.g.
`/run/shepard/admin.sock`) only when
`shepard.admin.local-socket.enabled=true`. The CLI, running inside the
same container (`kubectl exec ...`, `docker exec ...`), connects to the
socket. The socket carve-out skips `JWTFilter` for requests that arrive
via that listener — local privilege is treated as authentication.

Pros: useful for `kubectl exec` / `docker compose exec` workflows;
matches the maintainer's "static admin user (DISABLE user through env)"
note (`input_raw.md:8398`); zero secret-distribution problem.

Cons: a new transport surface; needs careful Quarkus wiring
(probably a second JAX-RS application with its own
`PublicEndpointRegistry`-equivalent); blast radius if the socket leaks
out of the container; not usable from a workstation.

### 3.3 Decision

Phase 1 should ship **(a)** behind A0 and **(c)** behind a feature flag
in parallel. The two complement each other: (a) for remote ops,
(c) for break-glass and CI-driven smoke. **(b) should never be the
default** and only exists for the case where the backend's HTTP layer
itself is down.

### 3.4 Env-driven auto-configuration (added 2026-05-08)

Operators do not type `--host` and `--apikey` on every invocation.
The CLI reads these from the environment in this precedence order:

1. CLI flags (`--host`, `--apikey`, `--config`) — explicit override.
2. `SHEPARD_HOST`, `SHEPARD_API_KEY` env vars — typical case.
3. `.env` file in `$PWD` or `$XDG_CONFIG_HOME/shepard-admin/config`
   — auto-discovered. The `init` wizard (§4.11) writes to this path
   by default, so a freshly-bootstrapped operator gets zero-config
   `shepard-admin` after running `init` once.
4. Compose discovery — if running on the same host as a docker-compose
   shepard stack, parse `.env` next to `infrastructure/docker-compose.yml`.

The API key must carry the `admin` role per option (a) above. If the
key has only ordinary user roles, every mutating command refuses with
a single-line error pointing at the role-grant doc — there is no
"escalate" path; rotate the key with the right role instead.

Failure mode: missing both env and config file → CLI prompts to run
`shepard-admin init` and exits with status 2. No silent fallbacks to
defaults that could touch real infrastructure.

---

## 4. Candidate functions

For each candidate the table records:

- **Cmd** — proposed verb-noun shape and one-sentence purpose.
- **Role** — required scope.
- **Side effects** — does it mutate state.
- **Hint** — REST call, direct DB, or new admin endpoint.
- **Idempotent** — running twice in a row is safe / not safe.
- **Size** — S (≤2 d), M (1 wk), L (≥2 wk).

### 4.1 Soft-deletion management

```bash
shepard-admin cleanup deleted-entities \
  --older-than 30d \
  --type collection|dataobject|reference|container \
  [--dry-run]
```

Purge entities that have `deleted = true` and `updatedAt` older than
the TTL. Today these stay forever — `CypherQueryHelper.java:32-94`
filters them out of reads via `WHERE n.deleted = FALSE OR n.deleted IS
NULL` but no purger ever runs.

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | Hard-deletes Neo4j nodes; cascades into MongoDB blobs (file payloads in `data/file/`); leaves orphan TimescaleDB rows unless paired with `db check-orphans` |
| Hint | New admin endpoint that runs Cypher: `MATCH (n {deleted: true}) WHERE n.updatedAt < $cutoff DETACH DELETE n` plus matching MongoDB `deleteMany` for file payloads pinned to the deleted containers |
| Idempotent | Yes (a second run finds nothing to delete) |
| Size | M |

`--dry-run` prints either Cypher or a summary (open question §8).

### 4.1a Timeseries inventory + cleanup of unreferenced intervals

```bash
# Inventory: what's actually stored vs what's referenced.
shepard-admin timeseries inventory \
  [--container <containerAppId>] \
  [--format table|json|csv]

# Cleanup: drop hypertable rows that no live reference points at,
# bounded to specific containers + time windows.
shepard-admin timeseries cleanup \
  --container <containerAppId> \
  [--older-than <ISO-8601-duration>] \
  [--time-range <start>..<end>] \
  [--dry-run] \
  [--no-confirm]
```

**Why this slot.** TimescaleDB hypertables grow append-only. When
a `TimeseriesReference` is deleted (soft or hard), the *reference
node* in Neo4j goes; the **rows in the hypertable do not**. Over
time, instances accumulate hypertable bytes that no shepard entity
addresses any more — invisible to the API, expensive on disk.

`inventory` walks each `TimeseriesContainer` and reports:

- Total row count + on-disk size per container (queried via
  `pg_total_relation_size`).
- For each timeseries id present in the hypertable: whether a
  `Timeseries` / `AnnotatableTimeseries` Neo4j node still
  references it.
- Per-container "unreferenced bytes" estimate.

`cleanup` deletes hypertable rows for **unreferenced timeseries
ids** within the named container, bounded to the user-specified
time range (so a partial-window cleanup is possible — useful when
a sensor was re-calibrated and the old span should go, but the
new span stays).

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | Hard-deletes hypertable rows. **Irrecoverable** without backup restore. |
| Hint | TimescaleDB hypertable row-delete with a `(timeseries_id, time)` predicate. Per-chunk; relies on Timescale's chunk-level pruning to be efficient. |
| Idempotent | Yes (re-run finds no candidates) |
| Confirmation | Prints the row count + estimated bytes, prompts y/N (TUI mode shows a picker). `--no-confirm` for scripts; `--dry-run` for a CI-style report. |
| Size | M (needs the inventory query first to be safe) |

**Cross-references.** Couples to:

- `aidocs/22 §4.1` (entity soft-delete cleanup; this is the
  payload-side counterpart for timeseries specifically — the
  `aidocs/22 §4.1` Hint already mentions TimescaleDB orphans
  needing `db check-orphans`; this section makes the check-orphans
  part executable).
- `aidocs/46` PV1g (per-Collection retention policy + CLI sweep —
  same shape, different payload kind).
- `aidocs/12` (TimescaleDB perf — informs the chunk-aware delete
  strategy).

### 4.2 Stale lock / session cleanup

```bash
shepard-admin sessions list
shepard-admin sessions kill <id>
```

Real lock surfaces in the code:

- **Neo4j OGM `Session`** — `GenericDAO.java:20`
  (`protected Session session = null`); `NeoConnector.java:134`
  (`public Session getNeo4jSession()`). Sessions are `@RequestScoped`
  in practice; long-lived sessions are not currently a known leak, but
  Neo4j-side transactions can hang during migrations
  (V2 migration uses `try (Session session = ...)` at
  `V2__Extract_json.java:54` which is correct, but operator-level
  visibility is missing).
- **MongoDB transactions** — see the `mongoDB` module (3 files,
  `aidocs/06-code-quality.md` line 71). No explicit lock taken in
  application code; relies on driver pooling.
- **TimescaleDB advisory locks** — Flyway acquires its `flyway_schema_history`
  lock on startup; if a migration crashes mid-flight (the V1.7.0 long
  migration is called out in `aidocs/12-timescaledb-performance-analysis.md`
  row 16) the lock can persist. `MigrationsRunner.java` does not currently
  release on shutdown.
- **`FileContainerService`** race (issue #721, `aidocs/03-issues-status.md`
  line 45) — not strictly a lock leak but a missing one. Listed here
  because once a synchronisation primitive lands, it will need an
  inspection surface.

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | `kill` aborts a transaction → may roll back uncommitted writes |
| Hint | Cypher `CALL dbms.listTransactions()` / `CALL dbms.killTransaction(id)`; for Postgres, `pg_stat_activity` + `pg_terminate_backend`; for Mongo, `db.currentOp()` + `db.killOp()` |
| Idempotent | `list` yes; `kill` no |
| Size | M |

### 4.3 Cache invalidation

```bash
shepard-admin cache list
shepard-admin cache invalidate --cache permissions-service-cache [--key <user>:<entity>]
```

Two Quarkus caches exist today:

- `permissions-service-cache` — `PermissionsService.java:39,114`. Keyed
  by `CompositeCacheKey(entityId, ...)`. Already exposes
  `invalidateIf` at line 144.
- `container-cache` — `TimeseriesContainerService.java:37`.

The `LastSeenCache` family
(`ApiKeyLastSeenCache.java`, `UserLastSeenCache.java`,
`PermissionLastSeenCache.java` under `auth/security/`) are 30-min TTL
caches independent of Quarkus; flushing them needs a different code
path. Security finding H2 (`aidocs/07-security-issues.md`) calls out
the 30-min TTL as a permission-revocation gap, so this command is also
the operational mitigation for H2.

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | Next read repopulates from Neo4j → brief load spike |
| Hint | Admin REST endpoint that calls `cache.invalidateAll()` / `LastSeenCache.invalidate(key)` |
| Idempotent | Yes |
| Size | S |

### 4.4 Migration orchestration

```bash
shepard-admin migrations status
shepard-admin migrations resume <containerId>
shepard-admin migrations abort
```

P3 (`16` row P3, commit `7cc74b8`) added a migration-progress endpoint
under `/temp/migrations/state` (referenced in
`MigrationModeFilter.java:32`). The CLI is a thin client over it.
`migrations resume` is post-P3 and depends on P3b (`16` row P3b —
wiring the external `timescale-migration-preparation` image). Today
`MigrationsRunner` (`backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java`)
runs once at startup; resuming a partially-completed container migration
needs the P3b orchestration layer.

| Field | Value |
|---|---|
| Role | `admin` (status) / `admin` (resume) |
| Side effects | `resume` triggers data movement; `abort` flips `MigrationModeToggle` off mid-flight (risk of inconsistent state) |
| Hint | `GET /temp/migrations/state`; `POST /temp/migrations/{id}/resume` (post-P3b) |
| Idempotent | `status` yes; `resume` should be (already-completed → no-op); `abort` no |
| Size | S (status) + M (resume / abort, post-P3b) |

P3c (`16` row P3c) flags that `/temp/migrations/*` is currently in the
always-allow carve-out at `PermissionsService.java:202-205`; the CLI
should be ready for that to tighten.

### 4.5 Health probe

```bash
shepard-admin health <neo4j|mongo|timescale|all>
```

Calls the existing healthz pingers. A1b
(`16` row A1b, done in commit `8f40156`) split startup vs runtime
health; the CLI exposes the runtime path so `kubectl exec` can verify
each backend independently.

| Field | Value |
|---|---|
| Role | none (or `admin` if the readiness contract is private) |
| Side effects | None (read-only ping) |
| Hint | `GET /q/health/ready` exists today (Quarkus default); admin path may want a per-DB breakdown via the three `*HealthCheck.java` classes |
| Idempotent | Yes |
| Size | S |

### 4.6 Feature toggle inspection and flipping

```bash
shepard-admin features list                         # show all + current state
shepard-admin features get <name>                   # show one toggle's state
shepard-admin features set <name> <true|false>     # post-A3b
shepard-admin features set <name> <true|false> --restart-stack    # opt-in
```

A3 (`16` row A3, commit `ddeeb31`) made the toggle mechanism runtime;
A3b (queued) adds the `/admin/features` endpoint. Read-only operations
are post-A3b. Mutation is post-A3b *and* post-A0 (since the admin role
is what gates writes).

The toggles available today
(`backend/src/main/java/de/dlr/shepard/common/configuration/feature/toggles/`):
`MigrationModeToggle`, `SpatialDataFeatureToggle`, `VersioningFeatureToggle`,
plus `TogglePropertyUtil`.

**Profile-bound toggles** (added 2026-05-08): some features are not
just runtime flags but bind to a docker-compose **profile** because
they require a separate service on the wire (the canonical example
is `spatial` → `postgis`; the upcoming `hdf` profile → `hsds` per
`aidocs/35-hdf5-hsds-implementation-design.md`). For these, the CLI
needs to do two things atomically:

1. Update the `.env` file to add/remove the profile from
   `COMPOSE_PROFILES`.
2. Flip the corresponding `*FeatureToggle` once the service is up.

```bash
# What the CLI does end-to-end:
shepard-admin features enable hdf
# 1. Reads .env, computes new COMPOSE_PROFILES
# 2. Confirms with the operator (or --yes)
# 3. Writes .env back
# 4. If --restart-stack: docker compose up -d  (else: prints the command)
# 5. Polls /healthz until the new service reports up
# 6. PATCH /admin/features/hdf {enabled: true}  (post-A3b)
```

Without `--restart-stack` the CLI is **doc-mode** — it tells the
operator the exact `docker compose` command to run and exits with a
non-zero status. This keeps the "the CLI never restarts a stack
without consent" invariant from §1.2 intact.

| Field | Value |
|---|---|
| Role | `admin` (read), `admin` (write) |
| Side effects | `set` activates / deactivates feature wiring; `MigrationModeToggle` is special — flipping it on locks the API per `MigrationModeFilter.java:25-36`. Profile-bound toggles also touch `.env` and may need a stack restart. |
| Hint | `GET /admin/features`, `PATCH /admin/features/{name}` (post-A3b) |
| Idempotent | Yes (set to the same value is a no-op; `enable` on an already-enabled profile is a no-op too) |
| Size | S for plain toggles; M for the profile-bound shape (needs `.env` round-trip + healthcheck poll) |

### 4.6a Profile-bound toggle catalogue

| Toggle | Profile | Service | Docs |
|---|---|---|---|
| `spatial` | `spatial` | `postgis` | `infrastructure/docker-compose.yml` |
| `hdf` | `hdf` | `hsds` | `aidocs/35-hdf5-hsds-implementation-design.md` |
| `monitoring` | `monitoring` | `prometheus` + `grafana` | `infrastructure/docker-compose.yml` |
| `frontend-old` | `frontend-old` | legacy `frontend:4.0.0` | `infrastructure/docker-compose.yml` |

When `aidocs/34-upstream-upgrade-path.md` gains a row for the next
profile-bound feature (e.g. A5a's `hdf`), this table grows in the
same PR.

### 4.7 Collection import / export

```bash
shepard-admin collection export <id> --format ro-crate -o file.zip
shepard-admin collection import file.zip --target-namespace foo
```

Export reuses `ExportService.exportCollectionByShepardId`
(`backend/src/main/java/de/dlr/shepard/context/export/ExportService.java:77`)
which emits an RO-Crate ZIP via `RoCrateBuilder`
(`ExportBuilder.java:13,38,52`) with `ro-crate-metadata.json`
(`ExportConstants.java:7`). The endpoint exists at
`CollectionRest.java:274` (`GET /collections/{id}/export`).

Import is **not implemented**. Backlog row R2 is the per-payload
selective-export refinement; there is no inverse path. To land import,
new code is needed on the backend side (an `ImportService` mirroring
`ExportService` plus an `ImportBuilder`). The CLI is a thin client.

| Field | Value |
|---|---|
| Role | `admin` (export — could relax to user with read perms); `admin` (import) |
| Side effects | Export: none. Import: creates Neo4j nodes + MongoDB blobs + TimescaleDB rows; permission-cache impact |
| Hint | Export: `GET /collections/{id}/export`. Import: new `POST /admin/collection/import` |
| Idempotent | Export: yes. Import: **no** — re-running creates duplicates unless name-collision handling lands first |
| Size | S (export wrap) + L (import, since the inverse RO-Crate parser is greenfield) |

### 4.8 API-key management

```bash
shepard-admin apikey create --user x --name "ci-bot" --validUntil 2026-12-31
shepard-admin apikey list --user x
shepard-admin apikey revoke <uid>
```

Sits on `ApiKeyService` (`auth/apikey/services/ApiKeyService.java`)
plus the L5 work (`16` row L5, commit `30c687a`) which added
`validUntil`. The current REST surface
(`ApiKeyRest.java:39 → /users/{username}/apikeys`) requires the caller
to be the user themselves
(`ApiKeyService.assertCurrentUserEquals`, `ApiKeyService.java:42,58`),
which blocks admin-on-behalf-of-user creation. Either:

- A0 introduces an admin-role bypass for `assertCurrentUserEquals`, or
- A new `/admin/users/{username}/apikeys` endpoint that bypasses the
  same-user check.

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | `create` writes to Neo4j; `revoke` deletes from Neo4j and triggers `ApiKeyLastSeenCache` invalidation |
| Hint | New admin variants of the existing endpoints |
| Idempotent | `list` yes; `create` no (each call mints a new key); `revoke` yes |
| Size | M (after A0) |

### 4.9 Usage / cache statistics

```bash
shepard-admin stats top-entities [--limit 50]
shepard-admin stats cache-hit-rate
```

Read-only. `stats cache-hit-rate` is post-A4d (`16` row A4d — Micrometer
metrics on `permissions-service-cache`); without metrics enabled there is
no source data. `stats top-entities` is a Cypher aggregation
(`MATCH (n) RETURN labels(n), count(n) ORDER BY count DESC`) over the
graph.

| Field | Value |
|---|---|
| Role | `admin` (or `viewer`) |
| Side effects | None (read-only) |
| Hint | New admin endpoint; metrics already exposed at `/shepard/doc/metrics/prometheus` per `aidocs/01-repo-overview.md:78` — `stats cache-hit-rate` could just scrape Prometheus |
| Idempotent | Yes |
| Size | S |

### 4.10 Database integrity checks

```bash
shepard-admin db check-orphans
shepard-admin db check-integrity
```

Two integrity gaps map to this:

- **Orphans across stores.** Soft-deleted Neo4j containers leave
  TimescaleDB rows behind because `TimeseriesRepository.deleteByContainerId`
  (`backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesRepository.java:73`)
  is only called on hard-delete, but the hard-delete path does not exist
  yet. After 4.1 (Soft-deletion management) lands, this check confirms
  that hard-delete cleaned both stores.
- **Reference integrity.** `BasicReference` nodes pointing to deleted
  containers — these are filtered out at read time
  (`Neo4jQueryBuilder.java:482`, `(variable.deleted = FALSE)`) but
  remain on disk. Listing them gives operators a sense of cleanup
  pressure.

| Field | Value |
|---|---|
| Role | `admin` |
| Side effects | None (read-only) |
| Hint | Cypher integrity queries; cross-store queries fan out to TimescaleDB and MongoDB |
| Idempotent | Yes |
| Size | M |

### 4.11 First-time setup wizard (`init`)

```bash
shepard-admin init                       # interactive TUI wizard
shepard-admin init --output .env         # explicit target file
shepard-admin init --non-interactive --profile=prod    # answers from defaults
```

Targets the friction at first-run setup: today an operator copies
`infrastructure/.env.example`, hand-edits ~25 keys, generates random
secrets manually, and discovers `aidocs/07` H8 the hard way when their
shipped placeholders end up in production. The wizard collapses this
into a single guided session that ends with a known-good `.env`.

**Design.** Modern menu-mode TUI in the spirit of `whiptail` /
`dialog` but with a 2026-era look — boxed sections, keyboard
navigation, inline validation, contextual help on the right pane.
**[Lanterna](https://github.com/mabe02/lanterna)** is the canonical
fit for the Java + Picocli stack (BSD-3, actively maintained, runs on
any POSIX terminal + Windows console). Falls back to a plain
question-stream when stdout is not a TTY (CI, `docker exec` without
`-t`, piped invocations).

**Wizard flow** (six screens, each can be skipped to keep current
value if a `.env` already exists):

1. **Welcome** — detects existing `.env` (offers `--force` overwrite,
   merge, or abort). Detects host capabilities (POSIX permissions,
   docker compose plugin version) and prints a one-line readiness
   summary.
2. **Identity provider** — see §4.11a for the full screen flow.
   Three top-level paths: (a) bring an existing OIDC IdP (paste
   issuer URL, the wizard auto-discovers `/.well-known/openid-configuration`
   + JWKS); (b) sidecar **Keycloak** added to the compose stack;
   (c) sidecar **Pocket ID** added to the compose stack
   (passkey-first; lighter footprint). Sets `OIDC_AUTHORITY`,
   `OIDC_PUBLIC`, `OIDC_ROLE`, and (if needed) the
   `SHEPARD_OIDC_ROLES_CLAIM_PATH` adapter from §4.11a.4.
3. **Profiles** — multi-select from the catalogue in §4.6a:
   `spatial`, `hdf`, `monitoring`, `frontend-old`. Tooltips quote the
   per-profile cost (RAM / disk / start time). Sets `COMPOSE_PROFILES`
   in `.env`.
4. **Storage backends** — the conditional section. If `spatial`
   selected: PostGIS service config. If `hdf` selected: storage
   choice (POSIX / S3 / MinIO / Azure) per
   `aidocs/35-hdf5-hsds-implementation-design.md` §3.
5. **Secrets** — generates strong random values for every `*_PW` /
   `*_SECRET` placeholder via `SecureRandom` (96 bits base64).
   Operator can opt to paste their own. The shipped defaults from
   `aidocs/07` H8 are explicitly **never** used; declining to set a
   secret aborts with an explanation.
6. **Review + write** — shows the diff against the existing `.env`
   (or against `.env.example` for first-run), asks for confirmation,
   writes the file with `0600` perms, prints the next step
   (`docker compose up -d`).

**Idempotency.** `shepard-admin init` against an already-configured
`.env` is a **no-op review session** unless the operator changes
something. The wizard always reads → renders → writes; it never
mutates state mid-session.

**`--non-interactive`.** All prompts use defaults; profile choice
comes from `--profile` (`prod` / `dev` / `demo` presets). Useful for
provisioning scripts and CI test rigs. Conservative defaults: no
profiles, all secrets regenerated, OIDC stays unset (admin must
configure it before serving traffic).

**Cross-references.** Aligns with `docs/deploy-oracle-free.md` §5b,
`docs/deploy-self-hosted-zoraxy.md` §3, `infrastructure/.env.example`,
and `aidocs/07` H8.

| Field | Value |
|---|---|
| Role | None (pre-auth — runs before shepard is up) |
| Side effects | Writes `.env` (always with `0600`); may write `docker-compose.override.yml` if Keycloak option chosen |
| Hint | TUI library: Lanterna. Falls back to plain prompts when not a TTY. |
| Idempotent | Yes (re-run is a review unless operator changes inputs) |
| Size | M (≈ 1 week for the wizard frame + 6 screens + non-interactive path) |

### 4.11a OIDC screen — detail (added 2026-05-08)

Step 2 of the wizard is the riskiest one for first-time operators
(get the IdP wrong and shepard refuses every login), so it's a
multi-screen sub-flow with auto-discovery, sanity probes, and clear
fallbacks.

#### 4.11a.1 Top-level choice

```
┌─ Identity provider ────────────────────────────────────────────────┐
│                                                                    │
│  ( ) (a) Use an existing OIDC provider (recommended for prod)      │
│  ( ) (b) Add Keycloak as a sidecar service to this stack           │
│  ( ) (c) Add Pocket ID as a sidecar service (passkey-first)        │
│                                                                    │
│  ( ) (d) Skip — I'll set OIDC env vars by hand later  [advanced]  │
│                                                                    │
│   [ < Back ]                                          [ Next > ]   │
└────────────────────────────────────────────────────────────────────┘
```

(a) is the default choice. (d) writes `OIDC_*` as commented
placeholders in the `.env` and leaves a banner in §6 review reminding
the operator to fix it before serving traffic.

#### 4.11a.2 Path (a) — bring an existing OIDC IdP

The wizard prompts for the **issuer URL** (e.g.
`https://auth.dlr.de/realms/shepard`) and probes
`<issuer>/.well-known/openid-configuration`:

```
┌─ Existing OIDC provider ───────────────────────────────────────────┐
│                                                                    │
│  Issuer URL:  https://auth.example.dlr.de/realms/shepard           │
│                                                                    │
│  ⓘ Probing /.well-known/openid-configuration ...                  │
│  ✓ Discovery document found                                       │
│  ✓ JWKS endpoint reachable (https://.../protocol/openid-connect/   │
│    certs)                                                          │
│  ⚠ Roles claim path 'realm_access.roles' not found in a sample     │
│    token (this is fine if your users haven't logged in yet)        │
│                                                                    │
│  Roles claim path:  realm_access.roles            [ default ]      │
│  Required role:     shepard-user                                   │
│  Client ID:         shepard-backend                                │
│                                                                    │
│   [ < Back ]   [ Test login ]                       [ Next > ]    │
└────────────────────────────────────────────────────────────────────┘
```

The probe distinguishes:

| Probe outcome | UI feedback | Next step |
|---|---|---|
| Discovery doc 200, JWKS 200 | ✓ green | Auto-fills `OIDC_AUTHORITY` and downloads the public key into `~/.shepard/keys/oidc_public.key` (the path stays user-overridable). |
| Discovery doc 4xx / 5xx | ✗ red, with the actual response body in a collapsed pane | Refuse to advance until issuer URL is fixed. |
| Discovery doc 200, JWKS 401 | ⚠ yellow | Advances; warns that a private JWKS endpoint may need a token. |
| Connection refused / TLS error | ✗ red | Refuse; offers to retry with `--insecure-tls` (writes `OIDC_TRUST_INSECURE=true` flag — banner in §6). |

The **Test login** button mints a device-code flow against the IdP
and walks the operator through completing it in a browser; on
success, the wizard captures the resulting access token, decodes it
locally (does not upload it anywhere), and shows the actual claim
shape — letting the operator confirm the `realm_access.roles` path
matches what their IdP emits. **This is the most-failed-step rescue
mechanism** — it catches "wrong claim path" before it becomes a
post-deploy 401 mystery.

Sets:
```
OIDC_AUTHORITY=<issuer>
OIDC_PUBLIC=~/.shepard/keys/oidc_public.key
OIDC_ROLE=<required-role>
SHEPARD_OIDC_ROLES_CLAIM_PATH=<dot-path>     # only if non-default
```

`SHEPARD_OIDC_ROLES_CLAIM_PATH` is a **new** backend config key
(currently the path is hard-coded to `realm_access.roles` in
`JWTFilter.parsePrincipalFromAccessToken`). Adding it is a small
follow-up — see §4.11a.4. Without it, only Keycloak-shaped IdPs
work today.

#### 4.11a.3 Paths (b) and (c) — sidecar IdP

Both paths share the same UX: choose a hostname for the IdP service
(default `auth.<your-shepard-host>`), the wizard appends a service
to `docker-compose.override.yml`, generates an admin password, and
prints the URL + initial credentials in the §6 review.

**Keycloak (b).** The wizard pre-configures a Keycloak realm
`shepard` with a client `shepard-backend`, a default user `admin`,
and a role `shepard-user`. The admin's first chore is to create
real users in the Keycloak UI. Heavy (~600 MB image, ~30s start),
but the canonical match for shepard's claim shape — no claim-path
adapter needed.

**Pocket ID (c).** The wizard provisions a Pocket ID instance with
a single OIDC client for shepard. Lighter (~30 MB image, ~3s
start). Two configuration tasks the wizard does on the operator's
behalf because they're easy to miss:

- **Custom claim mapping** for `realm_access`. Pocket ID's
  per-group custom claims feature is used to inject
  `{"realm_access": {"roles": ["shepard-user"]}}` for users in the
  `shepard-users` group. The wizard creates the group and the
  custom claim. Caveat: per Pocket ID issue
  [#1273](https://github.com/pocket-id/pocket-id/issues/1273),
  same-key custom claims from multiple groups overwrite rather
  than merge — fine for single-role users; for multi-role setups,
  prefer (b) Keycloak.
- **Passkey enrollment.** The wizard prints a clear instruction
  that the admin must enroll a passkey at first login (Pocket ID
  is passkey-only, no password fallback). Falls back to (b) with
  a warning if the operator confirms they don't have a
  passkey-capable device.

Both sidecar paths set:
```
OIDC_AUTHORITY=http://<sidecar-host>:8080/realms/shepard           # Keycloak
OIDC_AUTHORITY=http://<sidecar-host>:1411                           # Pocket ID
OIDC_PUBLIC=~/.shepard/keys/oidc_public.key                         # downloaded from JWKS
OIDC_ROLE=shepard-user
SHEPARD_OIDC_ROLES_CLAIM_PATH=realm_access.roles                    # both, post-Pocket-ID-claim-mapping
```

#### 4.11a.4 Required backend follow-up: configurable claim path

Today `JWTFilter.parsePrincipalFromAccessToken` hard-codes the
`realm_access.roles` path via Jackson's
`Map.of("realm_access", RolesList.class)` deserialiser. To support
non-Keycloak IdPs without forcing admins into the custom-claim
workaround above, ship a small backend follow-up:

- New config key `shepard.oidc.roles-claim-path` (default
  `realm_access.roles` for backward compatibility).
- `JWTFilter` parses the dot-separated path and walks the JSON tree
  to find the roles array. Validates `String[]` shape.
- Tracker row in `aidocs/34` as **CONFIG** — additive, default-safe.

This is a S-sized backlog item (call it **F8**, slotting in alongside
the F-series in `aidocs/24`'s permission-system review). Until F8
ships, path (a) only works against IdPs that emit `realm_access.roles`
natively or can be configured to do so.

#### 4.11a.5 Tested-but-unprovisioned IdPs (notes)

Beyond Keycloak and Pocket ID, the issuer-URL discovery path (a)
should work against any standards-compliant OIDC provider; the
wizard's claim-path field handles the variance. Notes for the most
common ones:

| IdP | Roles claim path | Works on path (a)? |
|---|---|---|
| **Keycloak** | `realm_access.roles` (default) | Yes, no config |
| **Pocket ID** | `realm_access.roles` (after the wizard's custom claim mapping) | Yes; multi-role caveat |
| **Authentik** | `groups` (flat array) | Yes, set `SHEPARD_OIDC_ROLES_CLAIM_PATH=groups` (post-F8) |
| **Authelia** | `groups` (flat array) | Yes, same as Authentik |
| **Azure AD** | `roles` (flat array, requires app-role config in AD) | Yes, set path to `roles` (post-F8) |
| **Auth0** | `https://shepard/roles` (custom; namespaced) | Yes, set path to the full URL-style key (post-F8) |
| **Google Workspace** | No native roles claim | No — needs an upstream group-mapper bridge; out of scope. |

The wizard's "Test login" step (§4.11a.2) auto-suggests the right
path when it detects a known shape.

### 4.12 Backup hooks

Marked **out of scope** for the CLI. Backup orchestration belongs to
the infrastructure layer (the `infrastructure/` and
`infrastructure-local/` directories already contain compose definitions
that an external backup runner can hook into). The CLI may
*trigger* a logical export of a single collection via 4.7, but
"snapshot the cluster" should not be in this binary.

The one trivial hook worth surfacing: `shepard-admin db dump-schema`
that prints the Neo4j label catalog and the Postgres `\d+`-equivalent.
This is a debugging aid, not a backup. Size: S.

---

## 4.x TUI everywhere — universal interaction principle (added 2026-05-08)

Every command in §4 has **two invocation modes**:

1. **Direct mode** — fully argumented, scriptable, the canonical
   `shepard-admin <verb> <noun> [flags]` shape documented per row
   above. Used in CI, cron, ansible playbooks, ops runbooks.
2. **TUI mode** — invoke the same command without the required
   positional args (or with a global `--menu` flag) and the CLI
   drops into an interactive Lanterna screen for that command.
   Used by humans at a terminal who want autocomplete + confirmation.

### Auto-fill from server state

The TUI is not just a "press Y to confirm" loop; it pre-populates
selectable values from the live API:

| Command | Auto-fill source | Result |
|---|---|---|
| `cleanup deleted` | `GET /admin/deleted-entities?olderThan={ttl}` (post-A0) | Multi-select list of soft-deleted entities, ordered by age |
| `features set` | `GET /admin/features` (post-A3b) | Tab-cycle through known toggles; tooltip shows current value, side effects, gate dependencies |
| `cache invalidate` | Quarkus cache catalogue (already enumerable via `/q/cache/cleared`) | Multi-select of cache names with hit-rate hint per cache |
| `migrations` | `GET /migrations/progress` (P3, shipped) | Pre-shows applied migration list, highlights the last one; resume / abort options gated on its state |
| `apikey list/revoke` | `GET /admin/apikeys` (post-A0) | Filterable table; revoke prompts a per-row confirmation |
| `export collection` | `GET /collections` (existing, with paging) | Cursor-paged picker; once selected, the export-options form auto-fills with sane defaults from `aidocs/31-rocrate-export-optimisation.md` |
| `import collection` | local filesystem browser starting at `$PWD` | RO-Crate `.zip` picker; preview the manifest before commit |
| `db check` | label catalogue from Neo4j | Per-label integrity-check selector |

**Auto-fill failure modes.** If the auto-fill API is unreachable
(network, auth, version skew with a backend that lacks the endpoint),
the TUI degrades to a plain text-input box and an inline warning —
never to a "default to first item silently" misclick trap.

### TUI reference patterns

- **Wizard pattern** (`init`, `import collection`) — multi-screen
  forward-only flow with a final review screen.
- **Picker pattern** (`cleanup deleted`, `cache invalidate`,
  `apikey revoke`) — filterable table with checkboxes and a
  confirm/cancel footer.
- **Dashboard pattern** (`health`, `migrations status`, `stats top`)
  — read-only, auto-refresh every `PT5S`, `q` to quit. Useful as a
  poor-operator's "single pane of glass."

### Library + accessibility

[Lanterna](https://github.com/mabe02/lanterna) for all three patterns;
the same dependency the `init` wizard already pulls in. Provides:

- True-color + 256-color + plain-VT100 rendering automatically.
- Mouse support optional (off by default — keyboard navigation is
  faster for skilled operators and works in headless `screen`/`tmux`).
- `--no-color` honoured (inherits from `NO_COLOR` env var per the
  community standard).
- Falls back to a plain question-stream when stdout is not a TTY,
  so piping (`shepard-admin features list | jq`) keeps working.

### When TUI is not appropriate

- **Read-only one-shot queries** (`health`, `features list`,
  `migrations status` without args) print and exit — no TUI prompt.
  Adding a TUI to these would just slow scripted invocations.
- **`stats` subcommands** that produce a single number (cache-hit
  rate, total entity count) print the number; the dashboard
  pattern is opt-in via `stats top --watch`.

### Auth applied uniformly

All TUI sessions use the same env-driven auth-discovery from §3.4 —
the wizard never asks for the API key inside the loop. If discovery
fails, the TUI's first screen is the same `init`-style prompt that
sets up the env, then proceeds.

---

## 5. CLI framework choice

### 5.1 Java + Picocli

Pros: ships as a single shaded JAR; can import backend classes directly
(reusing `ExportService`, `JWTPrincipal` deserialisation, `ApiError`
DTOs, the existing `Constants` path strings — all of these are already
under `de.dlr.shepard.common.util` and the `*Rest.java` payload
classes); the team is already on Java 21 + Maven (per
`backend/pom.xml`). GraalVM native-image is supported by Picocli for
fast startup. Test stack matches backend (JUnit 5, Mockito).

Cons: shaded JAR is ~30 MB; `java -jar shepard-admin.jar` requires a
JVM on the host (mitigated by GraalVM). Dependency drift if the CLI
imports backend modules directly — needs a thin shared
`backend-client`-style module.

### 5.2 Python + Click

Pros: matches the existing `scripts/shepard_scripts/main.py:21`
pattern (Click + Poetry); the generated `shepard-client` is already a
dependency of that project (`scripts/pyproject.toml:19`); Python is
faster to write for ad-hoc scripts.

Cons: cannot reuse backend Java DTOs. Two release pipelines instead of
one. Risk that the CLI's view of API responses drifts from the
backend's model classes — same problem the existing TS / Java / Python
clients already solve via OpenAPI generation, but harder for admin
endpoints that won't be in the public OpenAPI.

### 5.3 Recommendation

**Java + Picocli.** Three reasons:

1. The new admin endpoints will not be auto-generated (they live under
   `/admin` or `/temp` paths that are explicitly outside the public
   OpenAPI contract — see `MigrationModeFilter.java:23`'s `/temp`
   carve-out). A Java CLI shares the DTO definitions with the backend
   and avoids hand-rolling them.
2. The team's CI is already Java/Maven-fluent
   (`aidocs/01-repo-overview.md:55-57`); adding a fourth Maven module
   is incremental.
3. Picocli ships argument parsing, completion (bash/zsh/fish), and
   subcommand routing out of the box. Click is a fine choice but the
   library reuse argument pushes Java.

Open question §8: maintainer may prefer Python on the basis that the
existing `scripts/` project is already Python and that adding a Java
CLI binary increases the surface of artefacts shipped per release.

---

## 6. Distribution

`infrastructure/docker-compose.yml:3,37,51,174` shows shepard already
ships Docker images via `registry.gitlab.com/dlr-shepard/shepard/...`
and tags by version (e.g. `:5.2.0`).

Mirror that:

- **Container image.** `registry.gitlab.com/dlr-shepard/shepard/admin-cli:<version>`,
  built in `.gitlab-ci.yml` next to the backend image. Default
  `ENTRYPOINT` is the binary, so `docker run --rm
  registry.gitlab.com/.../admin-cli:5.4 cleanup deleted-entities --dry-run`
  works in CI without any local install. This is the **primary**
  distribution channel and matches the on-prem operator workflow.
- **Shaded JAR** in GitLab Maven Package Registry alongside the existing
  Java client (`clients/java/pom.xml`). Direct download for hosts that
  don't have docker.
- **GraalVM native-image** as an optional artefact later. Adds CI
  complexity (linux/x86_64 + linux/aarch64 + macOS) and is unnecessary
  for Phase 1.
- **Homebrew tap** — explicitly **not** in scope. shepard targets DLR
  on-prem operators; macOS distribution is a nice-to-have at best
  (per the platform constraint at `aidocs/01-repo-overview.md:16`,
  "On-premises only").

The Python CLI in `scripts/shepard_scripts/` is published to
GitLab PyPI registry (`scripts/pyproject.toml:24`); if §5.3 is
overruled in favour of Python, the same channel would be reused.

---

## 7. Phased rollout

The phases below assume the framework decision (§5.3) and the auth
decision (§3.3) are made first. Phase 1 cannot ship until A0 is at
least decided (option (b) is the only A0-free path, and §3 explicitly
discourages it).

### 7.1 Phase 1 — foundation (skeleton + read-only + init wizard)

- Repository scaffolding under `admin-cli/` (Java + Picocli). Maven
  module wired into the parent `pom.xml`.
- Auth: env-driven discovery from §3.4. Option (a) admin-role API key
  when A0 lands; option (c) behind `shepard.admin.local-socket.enabled`
  for break-glass.
- TUI scaffolding: Lanterna dependency, the dashboard / picker /
  wizard pattern primitives from §4.x.
- **`init` wizard** (§4.11) ships in this phase — operators need it
  before they can use any other command.
- Three read-only commands, each available in direct + dashboard modes:
  - `shepard-admin features list` (post-A3b for full read; today reads
    the build-time toggle JSON only).
  - `shepard-admin health <db>` (no new backend code needed).
  - `shepard-admin migrations status` (calls the P3 endpoint).
- CI: build + smoke test against the existing
  `infrastructure-local/` compose stack. TUI tested via Lanterna's
  test harness (`TestTerminalFactory`).

### 7.2 Phase 2 — cleanup (most asked-for)

- `shepard-admin cleanup deleted-entities --older-than Nd [--dry-run]`.
- `shepard-admin cache invalidate --cache permissions-service-cache`.
- Backend work: a new admin endpoint that runs the purge Cypher inside
  a single transaction (`MATCH (n {deleted:true}) WHERE n.updatedAt <
  $cutoff DETACH DELETE n`), plus the cross-store payload purges for
  files (MongoDB) and timeseries
  (`TimeseriesRepository.deleteByContainerId`).
- Tests: integration test with a fixture graph that has stale `deleted=true`
  nodes to confirm the purge is correct and the matching MongoDB /
  TimescaleDB rows are removed.

### 7.3 Phase 3 — RO-Crate import / export

- `shepard-admin collection export <id> -o file.zip` — thin wrapper
  over the existing `/collections/{id}/export`.
- `shepard-admin collection import file.zip --target-namespace foo` —
  needs a new `ImportService` on the backend. This is the largest
  piece of new code in the rollout (size L per §4.7). Aligns with
  backlog row R2 (per-payload selective export, queued).

### 7.4 Phase 4 — advanced

- `shepard-admin sessions list/kill` (4.2).
- `shepard-admin features set` (post-A3b mutation).
- `shepard-admin apikey create/list/revoke --user x` (post-A0 admin
  bypass for `assertCurrentUserEquals`).
- `shepard-admin stats *` (post-A4d for cache-hit-rate; `top-entities`
  ships earlier).
- `shepard-admin db check-orphans / check-integrity`.

---

## 8. Open questions

### 8.1 Java vs Python

§5.3 recommends Java + Picocli. The counter-argument is that the
existing `scripts/` Python project is the natural home (`cli release`,
`cli packages`, `cli sample` are already wired) and adding a fourth
language artefact increases per-release surface. **Maintainer call.**

### 8.2 Unix-socket carve-out (option (c)) — yes or no?

The maintainer's `input_raw.md:8398` note ("static admin user (DISABLE
user through env)") suggests yes. The cost is a second JAX-RS
application surface (or a Quarkus REST endpoint that listens on a
Unix-domain `Vertx` socket) and the operational discipline to keep the
socket inside the container. **Maintainer call.**

### 8.3 Should `--dry-run` print Cypher?

Two camps:

- **Print Cypher.** Fully transparent; an experienced operator can
  paste it into Neo4j Browser to double-check. Couples the CLI output
  to schema; an operator's saved diff becomes wrong when the schema
  evolves.
- **Print high-level summary.** "Will hard-delete 47 collections, 234
  data objects, 891 references, and 3 file containers (12.4 GB
  blobs)." Friendlier; opaque about what exactly will run.

A reasonable middle ground is `--dry-run` for the summary,
`--dry-run --explain` for the Cypher. **Maintainer call.**

### 8.4 How is the CLI tested in CI?

Three options:

- **Unit tests only**, with a mocked HTTP layer. Fastest, weakest
  guarantee.
- **Integration test against `infrastructure-local/` compose stack.**
  Slowest but catches contract drift between CLI and backend. Same
  pattern the backend integration tests already use
  (`@QuarkusIntegrationTest` per `aidocs/01-repo-overview.md:50`).
- **Contract test** that runs both: a generated OpenAPI for admin
  endpoints, plus the CLI consumes the generated client. Highest
  bar; closest to how the existing language clients work
  (`clients/java`, `clients/python`).

Contract testing is the long-term answer but Phase 1 should start with
unit tests + a single happy-path integration test.

### 8.5 Where does the admin REST surface live?

Two existing precedents:

- `/admin/...` — fresh path, no existing carve-outs. `MigrationModeFilter`
  would let it through if listed in the public registry, otherwise it
  blocks during migrations (which is probably wrong: admin work needs
  to happen *during* migrations).
- `/temp/...` — already exempt from `MigrationModeFilter`
  (`MigrationModeFilter.java:23`), but the name signals
  "experimental" and P3c flags this as a security gap
  (`PermissionsService.java:202-205` always-allow carve-out).

`/admin/...` with an explicit `MigrationModeFilter` exemption is
cleaner. **Maintainer call.**

### 8.6 Should the CLI have a config file?

A `~/.config/shepard/admin.yml` for backend URL + key. Following
precedent: `gh` keeps creds in `gh auth login` files; `kubectl` reads
`~/.kube/config`. For a tool that mostly runs in CI / `docker exec`
environments, env vars (`SHEPARD_API_URL`, `SHEPARD_ADMIN_KEY`) are
probably enough for Phase 1, with a config-file path reserved for
later if multiple environments need to be juggled.

---

## 9. Cross-references

- Backlog source: `aidocs/16-dispatcher-backlog.md` row L1
  (queued, size M).
- Auth blocker: `aidocs/16-dispatcher-backlog.md` row A0
  ("Admin role mechanism: configurable `shepard.admin.role`,
  populate `JWTPrincipal.roles` from realm-access claims").
- Migration plumbing: `aidocs/16-dispatcher-backlog.md` rows P3
  (done, commit `7cc74b8`), P3b (queued), P3c (queued).
- Feature toggles: `aidocs/16-dispatcher-backlog.md` rows A3
  (done, commit `ddeeb31`), A3b (queued), A3c (queued).
- API-key expiry (semi-permanent keys):
  `aidocs/16-dispatcher-backlog.md` row L5 (done, commit `30c687a`).
- Cache metrics: `aidocs/16-dispatcher-backlog.md` row A4d (queued).
- RO-Crate selective export: `aidocs/16-dispatcher-backlog.md` row R2
  (queued).
- Static admin user prompt: `aidocs/input/input_raw.md:8398`.
- Original CLI prompt: `aidocs/input/input_raw.md:705-708`.
- Permission TTL gap (motivates 4.3): `aidocs/07-security-issues.md`
  finding H2; `UserLastSeenCache.java:8` (30 min).
- File-container race (motivates 4.2): issue #721 per
  `aidocs/03-issues-status.md` line 45.
- Performance backlog that interacts with 4.4 / 4.10:
  `aidocs/12-timescaledb-performance-analysis.md` rows 15 and 16 (no
  retention policy; long V1.7.0 migration without timeouts).
