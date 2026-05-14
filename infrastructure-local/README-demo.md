# Demo posture — `make demo-up`

> **TL;DR**
> ```
> git clone https://github.com/noheton/shepard.git
> cd shepard
> make demo-up
> ```
> => a complete, seeded shepard at <http://localhost:3000/> in
> well under 90 seconds (after the first image build).

This is the **DX5a** seeded-demo flow. It deploys a working
shepard stack — backend (Quarkus), frontend (Nuxt), Neo4j,
MongoDB, Postgres + TimescaleDB, Keycloak, the four shipped
plugins (`unhide`, `kip`, `minter-local`,
`reference-dbpedia-databus`) — with example data already loaded,
so a casual user can poke at every shipped feature without
reading docs.

The base layer is `infrastructure-local/docker-compose.yml`. The
`docker-compose.demo.yml` overlay adds the seeded posture; both
are invoked together by `make demo-up`.

## 60-second quickstart

```bash
# 1. From a fresh clone:
make demo-up

# 2. Wait for the green tick to print (90s typical, 3 min first
#    time if images need building).

# 3. Open the frontend:
open http://localhost:3000/        # macOS
xdg-open http://localhost:3000/    # Linux
# or paste into your browser

# 4. Log in as one of the test users (passwords on the
#    confirmation banner from step 1):
#       alice / alice-demo         (Manager — full RWMA)
#       bob   / bob-demo           (Reader — read-only)
#       admin / admin-demo         (instance-admin)
#       harvester / harvester-demo (service account)
```

## What's seeded

| Layer | Count | Notes |
|---|---|---|
| OIDC users | 4 | `alice`, `bob`, `admin`, `harvester` (Keycloak realm `shepard-demo`) |
| Plugins | 4 | `unhide` (disabled), `kip`, `minter-local`, `reference-dbpedia-databus` |
| Collections | 5 | engineering, climate, lab-journal, private, public-showcase |
| DataObjects | 12 | spread across the five Collections |
| FileContainers | 3 | with pointers to small example payloads |
| References | 2 | one DBpedia Databus URI + one cross-Collection BasicReference |
| Publications | 1 | LocalMinter PID resolvable at `/v2/.well-known/kip/01HFDEMO...` |

The five seeded Collections are:

| Collection | Use case | Permissions |
|---|---|---|
| `Cyclic-fatigue test campaign 2026-Q1` | Engineering — mechanical-test data | Alice owns, Bob read+write |
| `MERRA-2 reanalysis subset` | Climate — published dataset reference | Alice owns, Bob read |
| `Lab notebook 2026` | Lab-journal — daily entries | Alice owns, Bob read |
| `DLR-internal materials catalogue` | Private — only Alice has access | Alice only |
| `Public showcase: shepard demo` | Everyone-readable — KIP-resolvable | PUBLIC |

The seed Cypher lives under
[`demo-seed/cypher/*.cypher`](demo-seed/cypher) and is **idempotent**
(every statement uses `MERGE` / `ON CREATE` / `WHERE NOT EXISTS`,
so re-running `make demo-seed` on a populated graph is a no-op).

## Make targets

| Target | What it does |
|---|---|
| `make demo-up` | Build images + start the stack + seed example data |
| `make demo-down` | Stop the stack (keeps data volumes) |
| `make demo-reset` | Stop + nuke data volumes (next `demo-up` re-seeds) |
| `make demo-seed` | Re-run just the seeder (idempotent) |
| `make demo-status` | Show plugins installed + Collections seeded |
| `make demo-logs` | Tail the backend logs |
| `make demo-smoke` | Run the post-up smoke test |
| `make help` | List every target |

## Optional features (operator opt-in)

The demo defaults to safe values. Several shipped features are
**off by default** to keep the demo working without operator
credentials; flip them on once you have the credential bits in
place:

### Helmholtz Unhide feed (UH1a)

```bash
shepard-admin --url http://localhost:8080 \
              --api-key demo-admin-api-key-value-not-for-prod \
              unhide enable
shepard-admin --url http://localhost:8080 \
              --api-key demo-admin-api-key-value-not-for-prod \
              unhide mint-harvest-key --name demo-harvest-key
```

After enabling, the feed lives at
<http://localhost:8080/v2/unhide/feed.jsonld>.

### DataCite minting (KIP1d)

The KIP1d plugin ships once you point it at a DataCite Fabrica
test account:

```bash
shepard-admin minters datacite set-prefix 10.5072
shepard-admin minters datacite set-repository-id DLR.SHEPARD-DEMO
shepard-admin minters datacite set-publisher "Demo Publisher"
shepard-admin minters datacite set-landing-page-base http://localhost:8080/v2
shepard-admin minters datacite set-password   # prompts
shepard-admin minters datacite test-connection
shepard-admin minters datacite enable
```

Then edit the backend env to flip `SHEPARD_PUBLISH_MINTER=datacite`
and restart.

### HSDS / HDF5 sidecar (A5a)

```bash
# Bring up the hdf profile (HSDS POSIX backend):
docker compose \
  -f infrastructure/docker-compose.yml \
  --profile hdf up -d shepard-hsds
```

(The HSDS sidecar lives in `infrastructure/docker-compose.yml`,
not the demo overlay — the demo image-size budget didn't include
the HSDS image by default.)

## Demo-only credential reference

| Service | User / key | Value | Role |
|---|---|---|---|
| Keycloak admin console | `admin` | `admin` | bootstrap admin (default) |
| Keycloak shepard-demo realm | `alice` | `alice-demo` | Manager |
| Keycloak shepard-demo realm | `bob` | `bob-demo` | Reader |
| Keycloak shepard-demo realm | `admin` | `admin-demo` | instance-admin |
| Keycloak shepard-demo realm | `harvester` | `harvester-demo` | service |
| Backend admin API key | `demo-admin-api-key` | `demo-admin-api-key-value-not-for-prod` | instance-admin |
| Neo4j | `neo4j` | `demo-neo4j-password` | DB admin |
| MongoDB root | `mongo` | `demo-mongo-password` | DB admin |
| MongoDB shepard user | `username` | `demo-mongo-password` | shepard db |
| Postgres root | `postgres` | `demo-postgres-password` | DB admin |
| Postgres shepard user | `shepard` | `demo-postgres-shepard-password` | shepard db |

> Every credential above is **obviously fake** and committed to
> git on purpose. `gitleaks` treats the `demo-*` / `*-demo`
> convention as the exemption. Do NOT reuse these for any
> deployment reachable from outside your machine.

## Troubleshooting

**Backend won't come up.** Run `make demo-logs` and look for a
`MigrationsRunner` failure. The most common cause is leftover
state from an earlier `make demo-up` on a different branch — run
`make demo-reset` for a clean slate.

**OIDC login redirects fail.** Verify the Keycloak realm
imported. Check
`docker compose -f infrastructure-local/docker-compose.yml -f infrastructure-local/docker-compose.demo.yml logs keycloak | grep "shepard-demo"`.
The expected lines mention `imported realm` and four `imported
user`.

**Neo4j OOM.** The base compose pins Neo4j to upstream defaults.
If your Docker host is memory-constrained, lower the heap in
`infrastructure-local/docker-compose.yml`'s `neo4j.environment`.

**`make demo-up` hangs at "Waiting for backend health".** The
backend's first start does the Neo4j migration sweep + builds the
plugin manifest cache; cold start can take ~60s. If
`wait-for-backend.sh` times out, run `make demo-logs` and look
for `Listening on:` — that's the success signal.

**Re-running the seeder duplicates rows.** It shouldn't — every
Cypher MERGE is keyed on `appId`. If you see duplicates, file a
bug; meantime, `make demo-reset` cleans state.

## Caveats

- **This is a demo. Don't deploy this config to production.** All
  credentials are public; the `shepard.instance.id=local.demo.shepard`
  is a fingerprint identifying the demo posture; the OIDC realm
  has no MFA; the backend doesn't run behind TLS.
- **File payloads aren't loaded into GridFS.** The seed mints
  `:FileContainer` + `:ShepardFile` graph metadata so the
  "Collection -> file" shape is visible, but the actual blobs
  aren't streamed into MongoDB GridFS. Clicking "Download" on a
  seeded file returns 404. The path to fix this (FS1a or a
  dedicated mongo-side blob seeder) is tracked in `aidocs/16`.
- **Image-size budget.** Seed-files volume is < 5 MB by design;
  pulling 200 MB of seed data would defeat the
  "git clone + make demo-up = fast" goal. If you need more
  realistic data, point your own seed Cypher files at
  `infrastructure-local/demo-seed/cypher/`.

## Reference

- Tracker: [`aidocs/16-dispatcher-backlog.md`](../aidocs/16-dispatcher-backlog.md)
  row **DX5a**.
- Upgrade-from-upstream rule:
  [`aidocs/34-upstream-upgrade-path.md`](../aidocs/34-upstream-upgrade-path.md).
- Public docs front door:
  [`docs/reference/deployment-quickstart.md`](../docs/reference/deployment-quickstart.md).
- Feature matrix:
  [`aidocs/44-fork-vs-upstream-feature-matrix.md`](../aidocs/44-fork-vs-upstream-feature-matrix.md).
