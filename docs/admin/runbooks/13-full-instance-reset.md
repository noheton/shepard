---
layout: default
title: "Runbook — Full instance reset (preserving users)"
description: "Wipe operational data (Collections, DataObjects, containers, payloads, semantic annotations, activities) from a Shepard instance while preserving Keycloak users, API keys, and admin config. Then re-seed the curated synthetic showcases (LUMEN + MFFD) and admin-runtime defaults. Use when accumulated debris (orphan containers, half-imported runs, content-shape mistakes) makes the instance noisier than the work it's hosting."
stage: feature-defined
last-stage-change: 2026-05-28
audience: instance-admin
host: nuclide
tested: "— (procedure derived from session pattern; reviewed 2026-05-28)"
---

# Full instance reset (preserving users)

> **When to use this runbook**: The instance has accumulated enough debris that
> targeted cleanup is more work than a wipe-and-reseed. Typical triggers:
> half-imported MFFD-Dropbox runs, broken content-shape choices that left
> hundreds of ill-fitting DOs (e.g. wiki-pages-as-DOs), orphaned TS / SD
> containers, schema drift from many partial migrations, or just a strong
> desire for a clean substrate before a major demo.
>
> **What survives**: Keycloak users, API keys, OIDC realm config,
> admin-config singletons (`:FeatureToggleRegistry`, `:SemanticConfig`,
> `:UnhideConfig`, `:AiConfig`, instance metadata),
> bind-mounted `infrastructure/.env`, mounted volumes' filesystem layout.
> **What gets wiped**: every Collection, every DataObject, every container
> (TS / File / SD / Spatial / HDF / Video), every reference, every
> SemanticAnnotation, every Activity, all Garage S3 objects, all
> TimescaleDB chunks, all MongoDB GridFS contents, all PostGIS spatial
> points.
>
> **The PRE-MUT-SNAP rule is suspended for the duration of this reset** —
> per `feedback_pre_mut_snap_suspended_pre_reset.md`. Per-mutation
> snapshots are not taken; everything goes away in one pass and the
> integrity rebuilds from the seeds.

---

## Prerequisites

- SSH / shell access to the nuclide host (`shepard.nuclide.systems`).
- `docker` available; current user in `docker` group.
- The compose stack at `/opt/shepard/infrastructure/` on nuclide.
- `${NEO4J_PW}` available (source from `/opt/shepard/infrastructure/.env`).
- Backend image cut from a known-good `main` commit (post-Jandex-hang fix; do
  not start a reset on a wedged backend).
- LUMEN and MFFD seed scripts working (`examples/lumen-showcase/seed.py`,
  `examples/mffd-showcase/seed.py`) — verify with `--dry-run` first.

---

## Phase 0 — Preflight

### 0.1 Verify the live backend is on a clean image

```bash
docker compose -f /opt/shepard/infrastructure/docker-compose.yml ps backend
# Confirm: image tag matches a commit on `main`, status is healthy.

# Also verify the backend version endpoint reports the expected commit:
curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/version | jq .
```

If the backend is wedged or running an old image, **stop and resolve that first**.
A reset against a broken backend just produces a different broken state.

### 0.2 Snapshot the Keycloak realm export (users + roles)

Even though Keycloak's volume survives the reset, take a fresh realm export so
you can re-import if anything corrupts:

```bash
docker exec -t infrastructure-keycloak-1 /opt/keycloak/bin/kc.sh export \
  --dir /tmp/realm-export --realm shepard-demo --users realm_file

docker cp infrastructure-keycloak-1:/tmp/realm-export /tmp/keycloak-realm-export-$(date +%Y%m%d-%H%M%S)
ls -la /tmp/keycloak-realm-export-*/
```

### 0.3 Note the live state for the post-reset diff

```bash
# DataObject + Collection counts (Neo4j)
docker exec -e NEO4J_PW infrastructure-neo4j-1 cypher-shell -u neo4j -p "$NEO4J_PW" \
  "MATCH (n) RETURN labels(n)[0] AS label, count(*) AS n ORDER BY n DESC" \
  | tee /tmp/preset-counts-neo4j.txt

# Postgres table sizes
docker exec -e PGPASSWORD=$POSTGRES_PASSWORD infrastructure-postgres-1 psql -U postgres -d postgres -c \
  "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 20" \
  | tee /tmp/preset-counts-postgres.txt

# Mongo collection sizes
docker exec infrastructure-mongo-1 mongosh --quiet --eval \
  'db.getSiblingDB("database").getCollectionNames().forEach(c => print(c + " " + db.getSiblingDB("database").getCollection(c).countDocuments({})))' \
  | tee /tmp/preset-counts-mongo.txt

# Garage bucket sizes
docker exec infrastructure-garage-1 /garage bucket info shepard-payloads \
  | tee /tmp/preset-counts-garage.txt
```

These are NOT acceptance gates — they're just so you can see post-reset
that "12 348 DataObjects → 17 DataObjects" makes sense.

---

## Phase 1 — Stop the data-plane services

Keycloak stays up; users and config survive. Everything else stops.

```bash
cd /opt/shepard/infrastructure
docker compose stop backend frontend neo4j postgres mongo garage timescaledb
docker compose ps
# Confirm: only keycloak (+ any reverse-proxy / monitoring) is Up.
```

---

## Phase 2 — Wipe operational data volumes

### 2.1 Neo4j (graph data — DataObjects, Collections, SemanticAnnotations, Activities)

The `:User` nodes were generated by `UserFilter` on first sign-in; they will be
regenerated automatically when users sign in again post-reset, so wiping the
whole graph is safe.

```bash
# Wipe the data volume
docker volume rm infrastructure_neo4j-data
# Migrations rerun on startup via MigrationsRunner; new instance is created
# at the next `docker compose up neo4j`.
```

If you want to preserve runtime-mutable admin config (`:FeatureToggleRegistry`,
`:SemanticConfig`, `:UnhideConfig`, `:AiConfig`), export them BEFORE the volume
wipe:

```bash
docker exec -e NEO4J_PW infrastructure-neo4j-1 cypher-shell -u neo4j -p "$NEO4J_PW" \
  "MATCH (c) WHERE c:FeatureToggleRegistry OR c:SemanticConfig OR c:UnhideConfig OR c:AiConfig OR c:InstanceRegistry \
   RETURN labels(c)[0] AS label, properties(c) AS props" \
  > /tmp/preset-admin-config.tsv
```

Post-reset, re-apply via the admin REST endpoints (`PATCH /v2/admin/<feature>/config`)
or simply re-run the seed scripts which know how to set sensible defaults.

### 2.2 Postgres (TimescaleDB hypertables, channel_metadata, spatial)

```bash
docker volume rm infrastructure_postgres-data
docker volume rm infrastructure_timescaledb-data
# Flyway migrations rerun on startup (V1.* + V2.*); fresh schema.
```

### 2.3 MongoDB (GridFS file payloads, journal entries, SD payloads)

```bash
docker volume rm infrastructure_mongo-data
```

### 2.4 Garage S3 (file payloads, OME-Zarr volumes)

```bash
# Stop garage first to release file locks, then wipe the data volume.
docker compose stop garage
docker volume rm infrastructure_garage-data infrastructure_garage-meta
# Rebuild the bucket post-restart (see Phase 3).
```

### 2.5 Verify

```bash
docker volume ls | grep -E "infrastructure_(neo4j|postgres|mongo|garage|timescaledb)"
# Expected output: empty (all wiped) — volumes are recreated on next `up`.
```

---

## Phase 3 — Restart with fresh state

```bash
cd /opt/shepard/infrastructure
docker compose up -d neo4j postgres mongo garage timescaledb
# Wait for healthchecks
make -C /opt/shepard wait-for-health 2>/dev/null || sleep 60

# Now bring the backend up — it will run Flyway + Neo4j migrations on first
# connect (fail-fast per the MigrationsRunner contract in CLAUDE.md).
docker compose up -d backend
make -C /opt/shepard wait-for-health
make -C /opt/shepard smoke
```

### 3.1 Re-create the Garage bucket + access keys

```bash
docker exec infrastructure-garage-1 /garage layout assign $(docker exec infrastructure-garage-1 /garage status -1 | awk 'NR==2{print $1}') -z dc1 -c 1G
docker exec infrastructure-garage-1 /garage layout apply --version 1
docker exec infrastructure-garage-1 /garage bucket create shepard-payloads
# Re-import the access key from infrastructure/.env (SHEPARD_GARAGE_KEY / _SECRET)
docker exec infrastructure-garage-1 /garage bucket allow --read --write --key shepard shepard-payloads
```

(If your Garage runbook differs — e.g. you use the `garage-activation-runbook.md`
procedure — follow that. The end state is one bucket `shepard-payloads`, RW
permission for the `shepard` access key from `.env`.)

### 3.2 Bring the frontend up

```bash
docker compose up -d frontend
curl -fsS https://shepard.nuclide.systems/ | head -2
```

---

## Phase 4 — Re-apply admin-runtime config

If you exported `/tmp/preset-admin-config.tsv` in §2.1, walk it and re-apply via
admin REST. Otherwise, re-seed sensible defaults:

```bash
export ADMIN_TOKEN=$(curl -fsS -X POST "https://shepard-auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=frontend-dev" \
  -d "username=admin" -d "password=admin-demo" \
  | jq -r .access_token)

# Re-apply feature toggles (example)
curl -fsS -X PATCH "https://shepard-api.nuclide.systems/v2/admin/features" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"versioning":true,"unhide":true,"spatialData":true,"semanticRepositories":true}'

# Re-apply semantic config (ontology bundles)
curl -fsS -X PATCH "https://shepard-api.nuclide.systems/v2/admin/semantic/ontologies" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabledBundles":["m4i","obo-relations","prov-o","dcterms","datacite"]}'

# Re-apply Unhide config if relevant for the deployment
# Re-apply AI config (default to local TEI; flip to SAIA/OpenAI per deployment)
```

Per CLAUDE.md's "Always: surface operator knobs in the admin config" rule, these
all have admin PATCH endpoints — the singleton entities are seeded with defaults
on startup, then overlaid with your operator choices.

---

## Phase 5 — Re-seed the curated showcases

### 5.1 LUMEN hotfire showcase (Collection 42, 15 TR runs + anomaly investigation)

```bash
cd /opt/shepard/examples/lumen-showcase
HOST="https://shepard-api.nuclide.systems" APIKEY="<long-lived-flo-token>" \
  python3 seed.py
# Expected: Collection appId 019e55f3-... (or a fresh one), 15 TestRun DOs,
# TR-004 anomaly + investigation sub-tree, predecessor chain intact.
```

### 5.2 MFFD synthetic showcase (Collection 987758, 16 process DOs)

```bash
cd /opt/shepard/examples/mffd-showcase
HOST="https://shepard-api.nuclide.systems" APIKEY="<long-lived-flo-token>" \
  python3 seed.py
# Expected: 16 DOs forming the AFP/welding chain, Q1 anomaly + rework loop,
# Trace3D-ready channel annotations on container 987749 (or its new id).
```

### 5.3 MFFD-Dropbox real-data ingest (re-run v16 import from cube3)

Only after Phases 1–5.2 are green. Drives the `mffd-import-v15.py` script from
cube3-side; the dest collection re-builds organically from the cube3 source
hierarchy. See `examples/mffd-showcase/scripts/README.md` for the runner.

```bash
# cube3-side (operator runs there)
cd /opt/shepard/examples/mffd-showcase/scripts
SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \
SOURCE_SHEPARD_API_KEY=<cube-jwt> \
DEST_SHEPARD_URL=https://shepard-api.nuclide.systems \
DEST_SHEPARD_API_KEY=<nuclide-long-lived-token> \
uv run python mffd-import-v15.py
```

This is the heavyweight step (≈8 500 DataObjects). Bracket with a tmux session.

### 5.4 OTvis tier-1 fixture upload (post-deploy regression sample)

Pick one `.OTvis` file and upload to a fresh MFFD-NDT collection. Confirm the
tier-1 parser fires and the FileReference carries the full annotation set
(per `aidocs/integrations/114 §1.1`). This is the acceptance test for the
plugin's first deployment after the reset.

---

## Phase 6 — Acceptance gates

After all the above, the reset is "good" when:

| Check | Expected | Command |
|---|---|---|
| Backend healthy | `200` from `/healthz/ready` | `curl -fsS https://shepard-api.nuclide.systems/shepard/api/healthz/ready` |
| Frontend reachable | `200` on `/` | `curl -fsS -o /dev/null -w "%{http_code}" https://shepard.nuclide.systems/` |
| Users can sign in | OIDC roundtrip succeeds | sign in as `flo` and as a generic user |
| LUMEN seeded | 15 TR DOs + anomaly tree | check `/collections/<lumen-id>` in the UI |
| MFFD synthetic seeded | 16 process DOs + Q1 anomaly | check `/collections/<mffd-id>` |
| Smoke suite | `make smoke` green | `make smoke` |
| Trace3D | dropdowns auto-populate | open "Visualize in 3D" on MFFD container |
| OTvis tier-1 | annotation set lands on upload | upload `sample_S4_M13_L18_F4.OTvis` |
| Activity log | first activities appear | `MATCH (a:Activity) RETURN count(a)` ≥ low double digits |
| Garage objects | post-LUMEN/MFFD seed | bucket has ≥ 100 OIDs |

If any gate fails, **stop and resolve before moving forward**. A half-reset is
worse than a working dirty state.

---

## Phase 7 — Post-reset

### 7.1 Restore the PRE-MUT-SNAP rule

Per `feedback_pre_mut_snap_suspended_pre_reset.md`, the snapshot-before-
destructive rule was suspended during the reset window. From the moment §6 is
green, the rule comes back into force in full. Update
`feedback_pre_mut_snap_suspended_pre_reset.md` to mark the reset as fired (or
re-read it; the rule reactivates automatically when the operator confirms the
reset is done).

### 7.2 Update `RESUME.md`

Move the reset entry into the "Decisions baked" section with the date and the
list of what survived vs. what re-seeded. Future sessions read this file and
need to know whether they're operating on a pre- or post-reset instance.

### 7.3 Update `aidocs/34-upstream-upgrade-path.md`

If the reset was triggered by a data-shape mistake or a bad migration, the
post-reset state may differ from what an upstream admin would see. Note any
visible deltas in the upgrade tracker.

### 7.4 Notify

Anyone with a bookmark to a DataObject from the pre-reset state will see 404.
This is by design (reset is destructive) but they should be told. If there
are external integrations (Helmholtz Unhide, eLib DLR exports, MCP client
sessions), they'll need their indexes to refresh.

---

## What this runbook deliberately does NOT do

- **Does not** restore from backup. This is a forward-only reset, not a
  rollback. For point-in-time restore see `04-restore-neo4j.md`,
  `05-restore-timescaledb.md`, `06-restore-garage.md`, `11-postgres-restore.md`.
- **Does not** touch the upstream DLR cube. The cube is the source of truth
  for MFFD real data; the reset is dest-side only.
- **Does not** re-create Keycloak users. They survive automatically. If you
  need to add a user, see `07-add-instance-admin.md`.
- **Does not** snapshot each destructive step individually. The whole instance
  is going away in one pass; per-step snapshots are wasted work, and the
  PRE-MUT-SNAP rule is explicitly suspended for the reset window.

---

## Reverse course / abort

If you abort mid-phase, the cleanest path is to NOT cherry-pick a partial
reset. Either:

1. **Roll forward**: complete the reset, accept the wipe, re-seed.
2. **Roll back from backup**: stop everything, restore the volumes from the
   most recent off-host backups via the `04`–`06` and `11` restore runbooks,
   then bring everything back up.

A half-reset (some substrates wiped, others not) is the worst state — Neo4j
nodes that reference S3 OIDs the bucket no longer has, Postgres rows with
appIds that don't exist in Neo4j, etc. Don't ship the half-reset; finish or
restore.

---

## Cross-references

- `feedback_pre_mut_snap_suspended_pre_reset.md` — the suspension rule, terminates when this runbook completes.
- `RESUME.md` — current arc state; gets updated post-reset.
- `aidocs/34-upstream-upgrade-path.md` — operator-visible deltas tracker.
- `aidocs/integrations/114-process-monitoring-parser-plugin.md` — OTvis tier-1 acceptance hook.
- `examples/lumen-showcase/seed.py` + `examples/mffd-showcase/seed.py` — re-seeders.
- `docs/admin/runbooks/04-restore-neo4j.md` and siblings — the reverse-course path.
