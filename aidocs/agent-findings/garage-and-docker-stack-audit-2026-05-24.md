---
stage: feature-defined
last-stage-change: 2026-05-24
---

# Garage S3 + Docker stack audit — 2026-05-24

Sibling to:

- `bug-148-do-perms-seeded-2026-05-24.md`
- `backend-logs-sift-2026-05-24.md`
- (file-routing — sibling agent #209)
- (TS — sibling agent #208)
- Neo4j+n10s / Mongo / Postgres+PgBouncer audits

Scope: observation only. No mutations. Backlog rows opened, no migrations
written. Synthesis report will pick up cross-substrate observations.

Audit window: 2026-05-24 14:13 UTC. Live nuclide deploy.

---

## A) Garage S3

### TL;DR

Garage is **healthy, correctly under-provisioned, and over-permissive on
two surfaces**. The cluster is single-node `dxflrs/garage:v1.0.1` with
RF=1 (correct for one node), 13.6 MiB across 24 objects in the
`shepard-files` bucket. The S3 wire shape is correct (anonymous list → 403
`AccessDenied`), and the admin port is locked to `127.0.0.1` — both good.

The four real findings:

1. **Capacity is set to 1 GB** in the layout (`/garage layout show` →
   `1000.0 MB`). Effective namespace cap = 1 GB. The host has 464.8 GB
   free; once Garage data crosses 1 GB it will refuse writes silently.
   Symptom would be `InsufficientStorage` / `ServiceUnavailable` from
   the backend's S3 client. Pre-MFFD-ingest blocker.
2. **No `metrics_token` / `admin_token` configured** in `garage.toml`
   (verified: `curl :3903/v1/health` returns "Admin token isn't
   configured, admin API access is disabled for security"). Prometheus
   scrape of the `/metrics` endpoint works without auth (legacy path);
   the `v1/*` admin family is unreachable, including the structured
   health check. This was a deliberate Garage v1 break — operators
   must set `admin_token` to use the new admin API.
3. **No backup procedure documented or scripted.** Garage v1 has
   `garage block list`, `garage repair`, and snapshot capability via
   filesystem-level copy of `/var/lib/garage/{meta,data}` while the
   process is quiesced; nothing in `infrastructure/` or `scripts/`
   touches Garage backup. Neo4j and Mongo have at least implicit
   runbooks (the host volume bind paths under `/opt/shepard/*`); Garage
   only has the docker-named volumes (`infrastructure_garage_{data,meta}`).
4. **`docker-compose.yml` Garage config is dead** (lines 224–250 under
   `profiles: [files-s3]`). The Garage actually running is defined in
   `docker-compose.override.yml` lines 309–339 with a different volume
   shape (`garage_data:`, `garage_meta:` named volumes vs. the base
   file's `./garage-data` bind), no `profiles:` gate, and a different
   healthcheck (`/garage status` vs. `curl :3903/health`). The
   duplication is a footgun — an operator following the base compose's
   FS1d runbook (`docker compose --profile files-s3 up -d`) would spin
   up a second, mis-configured Garage on the same ports.

### Cluster state

```
ID                Hostname      Address         Tags  Zone  Capacity   DataAvail
2aadf702f795f5cb  cbc8ada59882  127.0.0.1:3901  []    dc1   1000.0 MB  464.8 GB (86.6%)
Zone redundancy:  maximum
Layout version:   1
Replication factor: 1
```

- **`rpc_public_addr = "127.0.0.1:3901"`** (`garage.toml:10`) is correct
  for single-node — Garage RPC is loopback-only.
- **Data engine: lmdb** (default, fine for ≤100 GB scale).
- **`rpc_secret`** is literal in `garage.toml:8` (committed to the
  repo). Not catastrophic since RPC is loopback only, but it should
  rotate through `${GARAGE_RPC_SECRET}` env-var like the base compose's
  Garage definition already shows (line 235). The override-file's
  Garage doesn't pass it because it relies on `garage.toml`.

### Buckets + keys

```
shepard-files   13.6 MiB / 24 objects / 0 multipart
  Global aliases: shepard-files
  Website access: false                            ← good
  Authorized keys: RWO  GK6f1eb80a3f7237cda3cf5830 shepard-backend
```

- **Single bucket, single key, RWO grants** — minimal blast radius.
  Acceptable for this scale.
- **Object count vs. file-routing finding**: only 24 objects in
  Garage; sibling agent #209 (file-routing) is investigating why
  uploads still land in Mongo GridFS. From the Garage side this is
  consistent — Garage **is** receiving the s3-provider writes (24
  objects, 13.6 MiB), so the new path works; the question is just
  whether older / kind-routed payloads still take the Mongo branch.

### Antipatterns

| ID | Severity | Finding |
|---|---|---|
| GARAGE-AUDIT-2026-05-24-001 | CRITICAL | 1 GB capacity cap in cluster layout will silently block writes before MFFD ingest scale (~10 GB+ expected for the AFP demo). |
| GARAGE-AUDIT-2026-05-24-002 | MAJOR | Two duplicate Garage service definitions in compose files (`docker-compose.yml :: shepard-garage [profiles: files-s3]` vs. `docker-compose.override.yml :: garage [no profile]`). Operator following base-file runbook spins up second instance on the same ports — confusion + brokenness. |
| GARAGE-AUDIT-2026-05-24-003 | MAJOR | No documented or scripted backup procedure. Garage data is a docker named volume (`infrastructure_garage_data`) — no host-readable bind path, no `scripts/backup-garage.sh`, no mention in `aidocs/ops/`. |
| GARAGE-AUDIT-2026-05-24-004 | MAJOR | No `admin_token` set → Garage v1 admin API (`/v1/health`, `/v1/cluster/*`) returns 403 `AccessDenied`. Cannot do structured health checks; can't programmatically inspect cluster state. Legacy `/health` + `/metrics` still work (used by current healthcheck + Prometheus). |
| GARAGE-AUDIT-2026-05-24-005 | MINOR | `rpc_secret` literal in `infrastructure/garage.toml` (committed). Loopback-only RPC limits exposure, but env-var pattern via `${GARAGE_RPC_SECRET}` (used in base compose) is the better shape. |
| GARAGE-AUDIT-2026-05-24-006 | MINOR | No bucket versioning + no lifecycle policy. Acceptable for current usage; flag for the day a user deletes a payload the audit chain points at. |
| GARAGE-AUDIT-2026-05-24-007 | MINOR | Single zone (`dc1`), single replica. Correct for single-node deploy; explicitly note in operator docs that multi-node deploys require layout re-assign + `replication_factor` bump in `garage.toml` (requires Garage restart). |
| GARAGE-AUDIT-2026-05-24-008 | INFO | `dxflrs/garage:v1.0.1` is pinned (good). Upstream `v1.0.x` series is the current stable line; no urgent CVE driver to bump. Re-check at next stack refresh. |

### Top 3 fixes (Garage)

1. **Raise layout capacity** before MFFD ingest. One-liner via:
   `/garage layout assign -z dc1 -c 100G 2aadf702f795f5cb` then
   `/garage layout apply --version 2`. Idempotent — re-runnable. Bake
   into a runbook at `docs/admin/runbooks/garage-capacity.md`.
2. **De-duplicate the compose definitions.** Decide: is Garage
   profile-gated (`files-s3`) or always-on? Current deploy treats it
   as always-on (override.yml). Move the canonical definition to
   override.yml or rip out the base-file copy, and document that
   `SHEPARD_STORAGE_PROVIDER=s3` requires Garage running.
3. **Ship a Garage backup runbook**: filesystem snapshot of
   `infrastructure_garage_{data,meta}` while the container is briefly
   stopped (or use `restic` against the live volume + accept eventual
   consistency). Track as `ADMIN-RUNBOOKS-LIBRARY` follow-up.

---

## B) Docker stack

### TL;DR

13 containers running across the box (10 shepard + 3 ops-side: alloy,
dozzle, arcane). **Resource limits are unset on every shepard service**
— ten OOM-risk surfaces. **Healthchecks exist on only 2 of 10 shepard
services** (backend + pgbouncer; +garage from override) — meaning
`depends_on: condition: service_healthy` only works for backend↔pgbouncer
and seeder↔backend. Neo4j, Mongo, TimescaleDB, Keycloak, Frontend all
lack healthchecks, so `depends_on` for them is just process-up not
service-ready. **Logging driver is `journald` for every container** with
no rotation config visible (relies on host systemd journal settings) —
acceptable but unaudited. **Network segmentation is partial**: mongoexpress
correctly sits on its own `mongo` network, but Keycloak admin (port 8082)
shares the `shepard` network with the backend.

### Inventory

| Container | Image | Uptime | Healthcheck | Mem limit |
|---|---|---|---|---|
| infrastructure-backend-1 | shepard-backend-patched:local | 4h | YES (healthy) | none |
| infrastructure-frontend-1 | shepard-frontend:local | 2h | NONE | none |
| infrastructure-pgbouncer-1 | edoburu/pgbouncer:latest | 6h | YES (healthy) | none |
| infrastructure-neo4j-1 | neo4j:5.26 | 6h | NONE | none |
| infrastructure-mongodb-1 | mongo:8.0.4 | 2h | NONE | none |
| infrastructure-timescaledb-1 | timescale/timescaledb:2.24.0-pg16 | 2h | NONE | none |
| infrastructure-keycloak-1 | keycloak/keycloak:latest | 45h | NONE | none |
| infrastructure-mongoexpress-1 | mongo-express:latest | 45h | NONE | none |
| infrastructure-caddy-1 | caddy:2 | 45h | NONE | none |
| shepard-garage | dxflrs/garage:v1.0.1 | 40h | YES (healthy) | none |
| arcane-agent | ghcr.io/getarcaneapp/arcane-headless:latest | 27h | NONE | **128 MiB** |
| dozzle-agent | amir20/dozzle:latest | 32h | NONE | **64 MiB** |
| alloy | grafana/alloy:latest | 36h | NONE | none |

**Observation**: only the third-party ops agents (arcane, dozzle) carry
memory limits — exactly the services least likely to OOM. The shepard
stack itself is unbounded.

### Resource-limit gap matrix

| Service | mem_limit | cpus | Risk |
|---|---|---|---|
| backend | 0 (unbounded) | 0 | Has `JAVA_OPTS: -Xms2G -Xmx2G` so JVM-heap-bounded, but non-heap (metaspace, direct buffers, native libs) can grow. Quarkus + ProvJsonLdRenderer + RDF4J → moderate native footprint. **Set `mem_limit: 4g`.** |
| frontend | 0 | 0 | Node Nuxt SSR — leak-prone. **Set `mem_limit: 1g`.** |
| neo4j | 0 | 0 | `NEO4J_server_memory_heap_max__size: 2G` + `pagecache_size: 3G` = ~5G expected, native overhead ~1G. **Set `mem_limit: 7g`.** |
| mongodb | 0 | 0 | `--wiredTigerCacheSizeGB 2.0` is the cache; total RSS commonly 3-4x cache size. **Set `mem_limit: 8g`.** |
| timescaledb | 0 | 0 | Postgres `shared_buffers` defaults + `work_mem` × N connections (via pgbouncer pool 20). **Set `mem_limit: 4g`.** |
| pgbouncer | 0 | 0 | Tiny process, but unbounded. **Set `mem_limit: 256m`.** |
| keycloak | 0 | 0 | JVM. **Set `mem_limit: 2g`.** |
| mongoexpress | 0 | 0 | Trivial. **Set `mem_limit: 256m`.** |
| caddy | 0 | 0 | Tiny. **Set `mem_limit: 256m`.** |
| garage | 0 | 0 | Tiny Rust binary. **Set `mem_limit: 1g`.** |

Default-no on cpu limits — they hurt more than they help on a dev box.

### Healthcheck gap matrix

| Service | Has HC | Other services declare `depends_on: <this>: service_healthy`? |
|---|---|---|
| backend | YES | seeder, home-showcase-seeder (override.yml :250, :302) — works |
| pgbouncer | YES | backend (override.yml :122) — works |
| garage | YES | nothing (backend depends on pgbouncer not garage; backend's healthz proves Garage reach indirectly) |
| neo4j | **NO** | backend `depends_on: neo4j` (no `service_healthy`) — process-up only |
| mongodb | **NO** | backend `depends_on: mongodb` — process-up only; mongo-init-js may not have completed |
| timescaledb | **NO** | pgbouncer `depends_on: timescaledb` — process-up only; pgbouncer can race the DB |
| keycloak | **NO** | nothing depends on it formally, but backend OIDC discovery requires it |
| frontend | **NO** | nothing |
| caddy | **NO** | nothing |
| mongoexpress | **NO** | nothing |

**Real bug**: `pgbouncer` `depends_on: timescaledb` is process-only.
PgBouncer can come up before `00-init-postgres-db.sh` finishes (which
creates the `pgbouncer.get_auth` function via SECURITY DEFINER). If
pgbouncer's `AUTH_QUERY` runs before the function exists, the first
backend auth attempt fails. Currently masked because pgbouncer's own
healthcheck loops until `pg_isready` returns OK + restart-policy
`unless-stopped` papers over the race.

### Network segmentation

```
                 ┌──────────────────────────────────────────────────┐
                 │ infrastructure_shepard (bridge)                  │
                 │   backend, pgbouncer, neo4j, mongodb,            │
                 │   timescaledb, garage, keycloak, caddy           │
                 └─────────────────────────┬────────────────────────┘
                                           │
                                           ├──── shared with ──── caddy
                                           │
                 ┌─────────────────────────┴────────────────────────┐
                 │ infrastructure_mongo (bridge)                    │
                 │   mongoexpress, mongodb, caddy                   │
                 └──────────────────────────────────────────────────┘

                 ┌──────────────────────────────────────────────────┐
                 │ infrastructure_frontend (bridge)                 │
                 │   frontend, caddy                                │
                 └──────────────────────────────────────────────────┘

                 ┌──────────────────────────────────────────────────┐
                 │ infrastructure_influxdata (bridge) — DEAD        │
                 │   (no members; remnant from pre-Timescale era)   │
                 └──────────────────────────────────────────────────┘
```

Findings:

- **Keycloak admin (`:8082`)** sits on `shepard` network alongside the
  backend, AND has port 8082 exposed on `0.0.0.0` (override.yml :126).
  Zoraxy fronts it externally as `shepard-auth.nuclide.systems`. The
  master-realm admin console is reachable on the public port — gated
  by Keycloak's own auth, but a separate `internal: true` network for
  admin endpoints is the textbook fix. For nuclide-scale this is
  acceptable; for a DLR institutional deploy, recommend a separate
  network + reverse-proxy ACL.
- **mongoexpress** correctly isolated on `mongo` network (caddy
  bridges both). Caddy in this deploy doesn't actually route to
  mongoexpress (Zoraxy fronts everything), so the bridge isn't
  reachable from outside. Acceptable.
- **`infrastructure_influxdata`** is a dead network with zero
  members — legacy from before the InfluxDB→Timescale migration.
  `docker network rm infrastructure_influxdata` is safe; remove the
  declaration from compose (`docker-compose.yml :331`) at the same
  time.
- **Garage on `shepard` net only** — correct. Backend reaches it as
  `garage:3900` (override.yml :92). The `127.0.0.1:3900` host port
  binding is a guardrail (good — explicit loopback, not `0.0.0.0`).

### Image freshness + provenance

| Image | Freshness | Concerns |
|---|---|---|
| keycloak/keycloak:latest | `:latest` tag, 2 weeks old | Should pin to `26.0.x`. `latest` breaks reproducible deploys. |
| edoburu/pgbouncer:latest | `:latest` tag | Should pin to a specific tag. |
| mongo-express:latest | `:latest` tag | Lowest-priority (admin tool) but still. |
| caddy:2 | major-only pin, 11 days | Acceptable — Caddy 2.x is API-stable. |
| amir20/dozzle:latest | `:latest`, 4 days | Ops tool; acceptable. |
| grafana/alloy:latest | `:latest`, 2 weeks | Ops tool; acceptable. |
| ghcr.io/getarcaneapp/arcane-headless:latest | `:latest`, 6 days | Ops tool; acceptable. |
| shepard-backend-patched:local | local, 4h | No git-sha tag, no build-time label. Can't tell which commit is running from `docker images` alone. |
| shepard-frontend:local | local, 2h | Same as above. |
| neo4j:5.26 | pinned to minor, 12 days | Good. |
| mongo:8.0.4 | pinned to patch, 8 days | Good. |
| timescale/timescaledb:2.24.0-pg16 | pinned to patch | Good. |
| dxflrs/garage:v1.0.1 | pinned to patch | Good. |

### Env-var hygiene + secrets

Secrets in plain env (`.env` interpolation):

- `${NEO4J_PW}`, `${MONGO_*_PASSWORD}`, `${POSTGRES_*_PASSWORD}` —
  standard pattern, acceptable for single-host dev. Docker secrets
  would be the production shape.
- `SHEPARD_FILES_S3_ACCESS_KEY_ID` + `SHEPARD_FILES_S3_SECRET_ACCESS_KEY`
  are **literal in `docker-compose.override.yml` lines 65–66** (not
  via env interpolation). Committed in clear. For nuclide demo this
  is acknowledged; for a DLR deploy this leaks Garage backend write
  access on `git log -p`. Move to `${GARAGE_S3_ACCESS_KEY_ID}` +
  `${GARAGE_S3_SECRET_ACCESS_KEY}` env vars.
- `SHEPARD_AUDIT_INSTANCE_SECRET: "demo-instance-secret-placeholder-do-not-use-in-prod"`
  (override.yml :74) — comment correctly flags this. For prod move
  to `${SHEPARD_AUDIT_INSTANCE_SECRET}` env var.

### Logging

- All 13 containers use `journald` driver.
- No `log_opts` (no max-size, no max-file). Relies on host
  `/etc/systemd/journald.conf` settings to bound disk.
- Backend ALSO writes to `/opt/shepard/backend/logs` via host bind
  (override.yml :89-91 → `/deployments/logs`). Verified mount.
  Double-logging is OK; just track total disk pressure.

### Compose structure

- **Monolithic** — `docker-compose.yml` + `docker-compose.override.yml`.
  Plugin sidecars (`shepard-hsds`, `shepard-garage`,
  `home-showcase-{seeder,collector}`, `seeder`) all live in the
  monolith.
- Per memory `feedback_plugins_declare_sidecars.md`, the design
  direction is each plugin owns its sidecar declaration and deploy
  assembles compose from active-plugin manifests. None of this
  scaffolding exists yet. Track as future work (referenced in
  aidocs/16 task #143 follow-up per override.yml :305 comment).

### Antipatterns

| ID | Severity | Finding |
|---|---|---|
| STACK-AUDIT-2026-05-24-001 | CRITICAL | Zero mem_limit on 10/13 shepard services. One leaky service can OOM the entire host. Backend has heap-cap but no container-cap; Nuxt SSR is leak-prone with no cap; Neo4j+Mongo+Timescale combined could blow past their configured caches under load. |
| STACK-AUDIT-2026-05-24-002 | MAJOR | Healthchecks missing on 8/10 services (neo4j, mongodb, timescaledb, keycloak, frontend, caddy, mongoexpress, plus the seeder/collector one-shots). `depends_on` without `condition: service_healthy` is process-up only, not service-ready. Cold-start race conditions are masked only by `restart: unless-stopped`. |
| STACK-AUDIT-2026-05-24-003 | MAJOR | `:latest` tags on 5 production images (keycloak, pgbouncer, mongo-express, dozzle, alloy, arcane). Breaks reproducible deploys + lets surprise breaking changes land on `docker compose pull`. |
| STACK-AUDIT-2026-05-24-004 | MAJOR | S3 secrets (`SHEPARD_FILES_S3_ACCESS_KEY_ID/SECRET_ACCESS_KEY`) committed in literal form to `docker-compose.override.yml`. Acknowledged-acceptable for nuclide demo, but a DLR institutional deploy inheriting this file leaks Garage write access. |
| STACK-AUDIT-2026-05-24-005 | MAJOR | Dead network `infrastructure_influxdata` declared in `docker-compose.yml :331` with zero members (post-Timescale-migration legacy). Caddy still attaches to it. Remove. |
| STACK-AUDIT-2026-05-24-006 | MAJOR | Locally-built images (`shepard-backend-patched:local`, `shepard-frontend:local`) carry no git-sha tag or build-time label. `docker images` can't answer "what commit is running?". Add `--build-arg GIT_SHA=$(git rev-parse HEAD)` + `LABEL org.opencontainers.image.revision`. |
| STACK-AUDIT-2026-05-24-007 | MINOR | No `log_opts` (max-size / max-file) on any container. Disk-fill risk depends entirely on host journald config — not auditable from compose alone. |
| STACK-AUDIT-2026-05-24-008 | MINOR | Keycloak admin port 8082 exposed on `0.0.0.0`; admin endpoints share the `shepard` docker network with the backend. Textbook fix: separate `internal: true` admin network + Zoraxy ACL on `/admin/*`. |
| STACK-AUDIT-2026-05-24-009 | MINOR | Mixed volume strategy: backend uses host binds (`/opt/shepard/backend/logs`), Neo4j+Mongo+Timescale use host binds (`/opt/shepard/<name>`), Garage+Keycloak use named volumes. Inconsistent backup story — host binds are `rsync`-friendly, named volumes need `docker run --rm -v ... -v $HOST:/backup busybox tar`. Pick one. |
| STACK-AUDIT-2026-05-24-010 | MINOR | `pgbouncer` `depends_on: timescaledb` is process-only (no Timescale healthcheck). The pgbouncer `AUTH_QUERY` references `pgbouncer.get_auth` which is created by an init script that may not have completed. Currently masked by pgbouncer's own healthcheck + restart-policy, but a CI cold-start hits this. |
| STACK-AUDIT-2026-05-24-011 | INFO | seeder + home-showcase-seeder one-shot containers exit 0 after run but stay around (`Exited (0) 3 days ago`). `restart: "no"` is correct; consider `docker compose run --rm` invocation instead so the container is reaped — keeps `docker ps -a` clean. |

### Top 3 fixes (stack)

1. **Memory limits on every shepard service.** One-line change per
   service in `docker-compose.override.yml`. Numbers in the matrix
   above. Biggest single risk reducer.
2. **Healthchecks on neo4j + mongodb + timescaledb + keycloak**, plus
   flip `depends_on` to `condition: service_healthy`. Cypher
   `RETURN 1`, `mongosh --eval 'db.adminCommand("ping")'`,
   `pg_isready`, `curl /realms/master`. Eliminates the cold-start
   race class entirely.
3. **Pin every `:latest`** to a specific tag + add git-sha label to
   locally-built images. Reproducibility + auditability in one pass.

---

## Cross-cutting (feeds synthesis — light)

Observations the synthesis report will want to cross-reference; not
expanded here per role-scope:

1. **Garage adoption is real but partial.** 24 objects / 13.6 MiB in
   `shepard-files` confirms the new s3-provider path works in
   production. Sibling agent #209's file-routing question is
   genuinely "what fraction of writes still take the Mongo path?" —
   not "is Garage working?". Garage is working.
2. **Healthcheck gap is cross-substrate.** Every DB substrate
   (Neo4j, Mongo, Timescale, PostGIS-when-active) lacks a
   container-level healthcheck. The Quarkus side has `DbHealthState`
   + per-DB pingers (A1f scheduler) which is the real readiness
   surface — but it can't gate `depends_on` for sibling services like
   pgbouncer or mongoexpress. The fix is symmetric across substrates.
3. **Resource-limit gap is universal**, not Garage-specific. Pair
   with the per-substrate audits' "OOM risk" findings — the
   underlying compose hygiene fix is the same one-line per service.
4. **Backup story is fragmented per substrate.** Garage = named
   volume, Neo4j/Mongo/Timescale = host bind. Synthesis report
   should consolidate the "backup contract" question across all
   substrates into one runbook obligation, not five.
5. **Compose duplication is a smell.** The base-file Garage def +
   override-file Garage def is one instance; another is the
   `frontend-old` (profile-gated) + `frontend` (active) shape. The
   monolithic compose has organically grown; the plugin-sidecar
   declaration model (`feedback_plugins_declare_sidecars`) is the
   structural fix.
6. **`infrastructure_influxdata` dead network** is the visible
   archaeological layer of the InfluxDB → TimescaleDB migration. A
   sweep for "what else is stale post-Timescale-migration?" might
   surface other dust — env vars, comment references, etc.

---

## Methodology

Read-only commands used (logged for repro):

```bash
docker exec shepard-garage /garage status
docker exec shepard-garage /garage layout show
docker exec shepard-garage /garage bucket list
docker exec shepard-garage /garage bucket info shepard-files
docker exec shepard-garage /garage key list
docker exec shepard-garage /garage key info GK6f1eb80a3f7237cda3cf5830

cat /opt/shepard/infrastructure/garage.toml
curl -s http://127.0.0.1:3903/health
curl -s http://127.0.0.1:3903/v1/health
curl -s http://127.0.0.1:3903/metrics | head -20
curl -sI http://127.0.0.1:3900/shepard-files/does-not-exist
curl -s http://127.0.0.1:3900/

docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.RunningFor}}'
docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.CreatedSince}}\t{{.Size}}'
docker network ls
docker network inspect infrastructure_{shepard,mongo,frontend,influxdata}
docker volume ls
docker inspect <each-container> --format '{{json .HostConfig}} {{json .Config.Healthcheck}} {{json .Mounts}}'

du -sh /var/lib/docker/volumes/infrastructure_garage_{data,meta}
```

Zero mutations. No `garage layout assign`, no `garage bucket create`,
no `docker compose up/down`, no compose edits.
