---
stage: concept
last-stage-change: 2026-05-23
---

# shepard Edge — Concept Design

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. New form-factor — researcher-facing
"git-like" workflow that takes shepard offline-first and syncs
back to central when connectivity returns.

**Originating items.** User brief: "new idea: how would a Shepard
edge look like. cam be deployed in the field without Internet
connection for later import." Couples to: `aidocs/42` (vision —
casual-user north star; "I just want to record data here and sync
later"), `aidocs/50` (experiment orchestration — test stands are
the canonical Edge candidate), `aidocs/51` (instance-admin role +
bootstrap-token mechanism — Edge needs the same single-admin path),
`aidocs/55` (PROV-O provenance — Edge activities sync with origin
stamp), `aidocs/31` (RO-Crate export — the manual-fallback sync
shape), `aidocs/40` (ecosystem — Edge is a sibling deployment to
sTC / SPW / sVC), `aidocs/49` (in-app docs — `docs/help/deploy-edge.md`
lands as part of this).

---

## 1. The "shepard Edge" concept in one paragraph

**shepard Edge is a small, self-contained shepard instance an
operator deploys at a measurement site, lab, vehicle, vessel,
container, drone, or test bench — runs offline indefinitely —
captures data locally — then syncs back to a central shepard when
connectivity returns** (USB hand-off, cellular link, WiFi at base
camp). The mental model is **git for research data**: distributed,
work-offline, sync-when-ready, central is canonical but every Edge
is fully functional standalone. A researcher walks into a field
site with a Raspberry Pi running `shepard-edge up`, records a
campaign on it for three weeks with no network, drives back to the
lab, runs `shepard-edge sync`, and the campaign appears in the
central shepard the team uses — with full provenance, all payload
kinds, and an audit trail saying "captured at Edge XYZ between
2026-04-01 and 2026-04-21."

---

## 2. Why this matters

Concrete use-cases that have no clean answer today:

| Use-case | Why central shepard alone doesn't cut it |
|---|---|
| **Field tests in remote locations** — rocket-engine test stand without LAN, wind-turbine campaign, fieldwork in geology / archaeology / biology | No network at the site; the team is back two weeks after the last measurement; data ends up on a laptop in someone's locker. |
| **Manufacturing cells with strict isolation** — Industrie 4.0 cells, classified manufacturing | Network policy forbids the cell talking to the corporate LAN; the operator wants captured data in the central repository anyway. |
| **Vehicle / vessel / aircraft instrumentation** — instrumented test car, research vessel, UAV mission | Host moves between domains (transit + base + open sea); cellular bandwidth is the wrong shape for live REST traffic. |
| **"Demo to a partner without exposing central"** | Edge is the showcase mode — partner gets a real shepard with seeded data, no VPN, no firewall negotiation. Couples to `aidocs/58 §BIZ1` ("zeigen"). |
| **Test stands per `aidocs/50`** | Many test stands ARE Edge candidates — the experiment-coordinator's natural runtime is on an Edge near the PLC, not on a central server two firewalls away. |
| **Casual-user north star (`aidocs/42 §1.0`)** | "I just want to record data here and sync later" must be a one-command experience. Today the only answer is "set up a full shepard," which is hours of work for a one-week campaign. |

The vision link: `aidocs/42 §Who it's for` already names the
research engineer "who has timeseries from OPC/UA or MQTT and
wants to attach them to a logical test run." Edge is where that
research engineer lives when they're not at HQ.

---

## 3. Form-factors

Three sizes; an operator picks one by hardware constraint and
campaign length. Each is **the same shepard backend** — just
different compose profiles and resource budgets.

| Form-factor | Hardware | Compose profile | Daily capture budget | What it does | What it can't do |
|---|---|---|---|---|---|
| **Edge-Micro** | Raspberry Pi 5 / Jetson Nano / N100 mini-PC, ARM-compatible, ~1 GB RAM available to shepard, 64-256 GB SSD | `edge-micro` | ~1 GB/day | Backend + Neo4j (512 MB heap) + Mongo + TimescaleDB. Optional sTC sidecar. Frontend served from backend static assets. | No PostGIS (spatial). No HSDS sidecar. No full search rebuild on resume. No Grafana / Prometheus. |
| **Edge-Workstation** | Laptop / NUC / small workstation, 8-16 GB RAM, 500 GB-2 TB SSD | `edge-workstation` (**default**) | ~50 GB/day | Full feature set: backend + Neo4j (2 GB heap) + Mongo + TimescaleDB + PostGIS. Full frontend. Optional HSDS + monitoring profile (off by default). | Search-index rebuild on cold start can be slow on a large local store. No multi-host clustering. |
| **Edge-Truck** | Instrumented vehicle / lab-on-wheels / shipping-container test rig, 32+ GB RAM, 4+ TB SSD, may run for months between syncs | `edge-truck` | TB-scale per campaign | Everything in Edge-Workstation plus the `monitoring` profile (Prometheus + Grafana — operators want local dashboards during a long campaign), plus multi-instrument sTC fan-in. | Not a cluster — still single-host. If the host dies, the truck is down. |

The three profiles share the same `docker-compose.yml`; the
profile switch decides which services come up and tunes the heap
/ cache sizes via env. An operator can move from Edge-Micro to
Edge-Workstation just by stopping the stack, copying
`/var/lib/shepard-edge/` to the bigger host, and starting with the
new profile — the on-disk format is identical.

---

## 4. Operating mode — what does "offline" really mean?

### 4.1 Offline-by-default

- **Backend, OIDC, license-check, image-pull all work without
  network.** No external service is on the hot path. The compose
  bundle ships container images pre-pulled (air-gap install path,
  §9 EDGE1i).
- **No phone-home.** No telemetry, no usage stats, no version-check
  ping leaves the box unless the operator explicitly enables it.
  Same gate as the security stance in `CLAUDE.md` — opt-in only,
  with a documented payload shape.
- **DNS is best-effort.** The Edge's hostname is `shepard.local`
  via mDNS (Avahi); operator can override to a static IP. No
  external DNS lookup is required to boot or serve.

### 4.2 Auth in offline mode

Without an IdP at the field site, the only auth path is the
**bootstrap-token + local instance-admin** mechanism already
designed in `aidocs/51 §5`. Edge ships **single-user mode by
default**:

- First boot mints a bootstrap token to
  `/var/lib/shepard-edge/.bootstrap-token` (mode `0600`).
- `shepard-edge init` reads the token, creates a single
  instance-admin user named `edge-operator` (or operator-supplied
  name), grants the `instance-admin` role via the Neo4j-internal
  `:HAS_ROLE` edge (per `aidocs/51 §3.3` dual-source check), and
  deletes the token.
- That user logs in to the local frontend at
  `https://shepard.local/` with username + password (Edge ships
  a local-user-credential backend keyed off the same Neo4j-internal
  path that `aidocs/51` uses for the bootstrap admin — no IdP
  needed).
- **No UserGroup ceremony.** Permissions ACLs still exist
  internally (the data model doesn't get simpler just because
  there's one user), but the UI hides the permission picker in
  single-user mode — every entity is implicitly owned by
  `edge-operator`.
- Optional second user via `shepard-edge user create <name>` —
  same shape as `aidocs/51`'s `shepard-admin instance-admin grant`,
  scoped to the Edge instance only.

### 4.3 Persistent storage volumes

Standardised layout under `/var/lib/shepard-edge/` on the host
filesystem (or a USB SSD mounted there):

```
/var/lib/shepard-edge/
├── neo4j/         # graph store
├── mongodb/       # file payloads + structured-data payloads
├── timescaledb/   # timeseries payloads
├── postgis/       # spatial payloads (edge-workstation+ only)
├── backend/
│   ├── logs/
│   └── config/
├── .bootstrap-token        # only present during initial bootstrap
├── .edge-instance-id       # UUID v4 minted at init (the originInstance stamp)
└── .shepard-edge/
    └── last-sync.json      # sync-cursor state per central target
```

The operator can stop the stack, snapshot
`/var/lib/shepard-edge/` to a USB SSD, hand it off, and a sibling
operator can resume — same UUID, same data, same sync state.

### 4.4 Self-signed TLS

Edge's hostname is `shepard.local`; first boot generates a
self-signed cert valid for `shepard.local` + the host's
discovered LAN IPs. The operator's browser warns on first visit;
that's documented in `docs/help/deploy-edge.md`. Operators with
their own CA override via `SHEPARD_EDGE_TLS_CERT` /
`SHEPARD_EDGE_TLS_KEY` env vars.

---

## 5. Sync model — getting data back to central shepard

This is the load-bearing piece. Three candidate shapes were
evaluated.

### 5.1 The three candidates

| # | Shape | Effort | Operator UX | Recommendation |
|---|---|---|---|---|
| **(A)** | **RO-Crate bundle export.** Edge produces an RO-Crate ZIP (already a shipped export shape per `aidocs/31`) of all post-last-sync changes; operator carries it on USB or sends it as a file; central shepard imports via existing import endpoint. | XS — reuses R2 / R3 import path. | Manual: operator runs `shepard-edge export-bundle`, transfers file, runs `shepard import-bundle` on central. | **Manual fallback** — always available; phased as EDGE1f. |
| **(B)** | **Replication protocol over HTTP/2 (push-from-Edge).** When the Edge gets connectivity, it talks to central shepard's new `POST /v2/edge/sync` endpoint and streams a delta — graph mutations + payload bytes — keyed off a sync-cursor against the Edge's local activity log per `aidocs/55`. | M — new endpoint, new client, new conflict-detect path. | One command: `shepard-edge sync` on the Edge; central UI shows a "syncing from Edge XYZ" banner. | **Recommended for v1.** Phased as EDGE1b + EDGE1c + EDGE1d. |
| **(C)** | **Git-like push/pull DVCS.** Full distributed model — multiple Edges sync among each other, central is just another peer, conflict resolution is structural (three-way merge on the graph). | L — significant. Precondition is appId-native identifiers (`aidocs/25` L2 chain post-L2d). | Powerful but cognitively heavy; the casual researcher doesn't want to think about "branches." | **Future / out-of-scope for v1.** Parked as EDGE1k. |

### 5.2 The pick: (B) — replication protocol, push-from-Edge

**Rationale.**

- **Connectivity event is on the Edge side.** Central usually
  can't reach into an Edge behind NAT / cellular / no fixed IP.
  Push-from-Edge sidesteps the firewall problem entirely.
- **Reuses existing surfaces.** `aidocs/55`'s `:Activity` log is
  the natural sync-cursor — every mutation already has a
  timestamp + `targetAppId`. The Edge sends activities since
  `last-sync.json`'s timestamp; central replays them.
- **Pairs with `aidocs/25` L2.** Every entity already has a UUID
  v7 `appId` (post-L2a + L2b). The sync protocol speaks `appId`
  natively; no risk of id collision.
- **(A) is the manual fallback.** Operators in true air-gapped
  environments (no allowed sync endpoint reachable at all) carry
  RO-Crate bundles on USB. Always available; sometimes the only
  option.

### 5.3 Sync invariants

- **Idempotent.** Re-running `shepard-edge sync` after an
  interrupted upload is safe. Each chunk carries a stable
  `(originInstance, activityAppId)` key; central dedupes on that
  pair. A half-finished sync resumes from the last
  acknowledged-by-central activity, not from scratch.
- **Atomic per activity.** A single `Activity` and the entities it
  `:GENERATED` are sent + applied as one unit; central holds a
  transaction open across the entity create(s) + the activity
  insert. Either both land or neither does.
- **Fail-fast on schema mismatch.** If the Edge ran a schema
  version newer than central's, the sync endpoint rejects with a
  clear error and a pointer to the central-shepard upgrade.
  Edge → central downgrade is not supported.

### 5.4 Conflict handling

If the same DataObject was edited on both Edge and Central while
disconnected (rare but real — someone in the lab edits the same
campaign DataObject's metadata while the Edge is on a vessel
two weeks out), the protocol **surfaces a conflict; it does not
auto-merge graph entities**.

The rule:

1. Central holds the **canonical** version (its UPDATE wins for
   display + read).
2. Edge's change becomes a **sibling DataObject** under a
   `(:DataObject)-[:CONFLICTED_WITH {syncedAt, originInstance}]->(:DataObject)`
   edge. The sibling carries the Edge's `originInstance` stamp.
3. Central's UI surfaces an "X conflicts to triage" badge on the
   parent Collection; the operator opens a "Resolve conflict"
   pane (Edge-version vs. central-version side-by-side; pick
   one or both).
4. Resolution removes the `:CONFLICTED_WITH` edge and optionally
   deletes the loser sibling — or keeps both if the operator
   chooses (the casual user shouldn't lose data because a sync
   tool tried to be clever).

**Why not auto-merge?** Three-way merge on a typed property graph
is well-defined for primitive scalars, but ambiguous for relations
("alice removed predecessorOf X, bob added predecessorOf Y, who
wins?"). Conservative behaviour: never silently lose either edit;
flag it.

Conflict scope: applies to **scalar property updates** on the
same entity, **and** to graph-edge presence/absence (predecessor,
annotation links). Payload-byte conflicts (same FileReference's
content bytes diverged) are a sub-case: sibling DataObjects each
keep their own payload, and the operator picks at triage time.

---

## 6. What does NOT sync back

Some things never cross from Edge to central:

| Item | Why excluded | What happens instead |
|---|---|---|
| **User accounts** | Edge is single-user; central has many. The Edge's `edge-operator` user has no meaning on central. | At `shepard-edge sync`, the CLI prompts: "Map Edge user `edge-operator` to which central user?" Operator picks (or supplies a username); all Edge-side `createdBy` / `updatedBy` get rewritten to that central user during import. |
| **Edge-local instance-admin role** | Edge admins do not become central admins; the import is data-only. | The `:HAS_ROLE` edge on the Edge stays on the Edge. Central's role membership is unaffected. |
| **Secrets** | `.env`, API keys, bootstrap tokens. | Stay on the Edge. The sync protocol's auth uses a separate central-minted API key with role `edge-sync` (see §10). |
| **Edge-internal config** | Local-only env vars (TLS cert paths, mDNS hostname, hardware-specific tuning). | Not part of the sync payload; central doesn't need them. |
| **Search index, cache state** | Derived from the canonical data. | Central rebuilds its own indices over the imported entities. |

What **does** sync, contrary to one's first guess:

| Item | Notes |
|---|---|
| **Activity log (`aidocs/55`)** | Every Edge-side `:Activity` syncs as-is, with the `originInstance` field stamped to the Edge's UUID so central distinguishes "captured at Edge XYZ" from "edited centrally." The provenance dashboard on central groups by `originInstance` — a casual user sees their fieldwork sessions as distinct strands in the sparkline. |
| **Annotations** | Including any new ones the operator added in the field. PROV-O annotation IRIs are stable across instances. |
| **Lab journal entries** | The free-text notes are the *point* of a field campaign. Sync verbatim. |
| **Permissions ACLs** | Edge's single-user world has trivial permissions; on import, the chosen central user becomes owner of every imported entity. Central's existing ACLs on already-existing entities are untouched. |

---

## 7. Identifier strategy

- **Every entity gets a UUID v7 `appId` at creation.** Already
  shipped per `aidocs/25` L2a + L2b. The Edge mints `appId`s
  locally with no central coordination.
- **Edge stamps `originInstance` on every entity created on the
  Edge.** New property on `:BasicEntity` (post-this design), a
  stable Edge-instance UUID set during `shepard-edge init` and
  persisted to `/var/lib/shepard-edge/.edge-instance-id`.
- **On import to central, `appId` collisions are statistically
  impossible** (UUID v7 = timestamp + 80 bits of randomness; two
  Edges colliding within the same millisecond on the same
  random suffix has probability ~2^-80).
- **`originInstance` is the audit trail.** Central's UI surfaces
  "Captured at Edge XYZ" badges on entities where
  `originInstance != central-instance-id`. A query against the
  provenance graph filtered by `originInstance` shows everything
  one field campaign captured.

A `central-instance-id` is also stamped at central-shepard's first
boot — entities created on central carry that value in
`originInstance`; the field is never null post-L2c.

Migration: `V##__Add_originInstance_to_BasicEntity.cypher` —
backfills the central instance's UUID into every existing
entity's `originInstance` field; default value for fresh entities
is the per-deployment `central-instance-id`. Idempotent + fail-fast
per `CLAUDE.md` migration policy.

---

## 8. Compose shape

### 8.1 New file: `infrastructure/edge/docker-compose.yml`

Sibling to the main `infrastructure/docker-compose.yml`. Shares
image references where possible, swaps in lightweight tags where
useful.

```
infrastructure/
├── docker-compose.yml          # central-shepard (today)
├── edge/
│   ├── docker-compose.yml      # NEW
│   ├── env.edge-micro.example
│   ├── env.edge-workstation.example
│   ├── env.edge-truck.example
│   └── caddy/
│       └── Caddyfile           # self-signed-TLS variant
└── ...
```

### 8.2 Profiles

The Edge compose file declares three profiles:

| Profile | Services up | Heap / cache budget |
|---|---|---|
| `edge-micro` | backend, neo4j, mongodb, timescaledb, caddy | Neo4j heap 512 MB, page-cache 512 MB; Mongo wiredTiger 512 MB; Timescale shared_buffers 256 MB; backend `JAVA_OPTS=-Xms512m -Xmx768m` |
| `edge-workstation` (default) | backend, neo4j, mongodb, timescaledb, postgis, caddy | Same as central single-host defaults — Neo4j heap 2 GB, page-cache 3 GB; Mongo 2 GB; backend `-Xms2g -Xmx2g` |
| `edge-truck` | All edge-workstation services + prometheus + grafana | Same as edge-workstation; monitoring on by default for long-running campaigns |

**Image variants.** Where alpine-flavoured images exist (Neo4j
community, Mongo, Postgres), the Edge compose prefers them — image
pull is the slowest part of an air-gap install. Backend image is
the same (no Edge-specific build); a runtime feature flag
`shepard.edge.mode=true` switches the Edge-specific behaviours
on (single-user UI mode, sync-cursor persistence, conflict
display, no phone-home).

**No Grafana / Prometheus by default.** The `monitoring` profile
from PERF1 (`aidocs/59`) is opt-in; `edge-truck` flips it on
because long campaigns want local dashboards. `edge-micro` and
`edge-workstation` ship without.

### 8.3 Single-binary `shepard-edge` CLI

Sibling to `shepard-admin` (`aidocs/22` L1 phasing). A small Go
or Java binary that wraps `docker compose ...`. Subcommands:

| Command | What |
|---|---|
| `shepard-edge init` | First-run interactive setup: pick profile, pick mount path, mint bootstrap token, create operator user, open browser at `https://shepard.local/`. |
| `shepard-edge up` | `docker compose --profile <profile> up -d`. |
| `shepard-edge down` | `docker compose down`. |
| `shepard-edge status` | Containers state + disk usage + last sync time. |
| `shepard-edge sync [--target <central-url>]` | Run a delta sync against central. Resumable; idempotent. |
| `shepard-edge export-bundle [--since <ts>]` | RO-Crate fallback (§5.1 path A). |
| `shepard-edge user create <name>` | Optional second user. |
| `shepard-edge logs [<service>]` | Tail logs for the operator. |

The CLI ties to `aidocs/22` admin-CLI L1 phasing — same Picocli
framework, same env-driven auth, same single-binary distribution
pattern. Distributed via the repo's GitHub Releases (multi-arch:
linux/amd64, linux/arm64, darwin/arm64, windows/amd64).

---

## 9. Phasing — EDGE1a–EDGE1k

| ID | Slice | Size | Gate |
|---|---|---|---|
| **EDGE1a** | `infrastructure/edge/docker-compose.yml` + lightweight image tags + the `shepard-edge` CLI's `init` / `up` / `down` / `status` subcommands. One command from `git clone` to a running Edge. | M | None — ships standalone. |
| **EDGE1b** | Sync-cursor server-side: central tracks `last sync from edge X at timestamp T` per `(centralInstance, originInstance)` pair. Edge persists sync state in `/var/lib/shepard-edge/.shepard-edge/last-sync.json`. | S | EDGE1a, `aidocs/55` PROV1a (Activity log present on both sides). |
| **EDGE1c** | `POST /v2/edge/sync` endpoint on central. Streams the graph-delta + payload bytes. NDJSON-framed activities + reference-bytes; idempotent on `(originInstance, activityAppId)`. | L | EDGE1b, `aidocs/51` (`edge-sync` role on API keys). |
| **EDGE1d** | Conflict-detection on import; `:CONFLICTED_WITH` edge on collision; central UI surfaces "N conflicts to triage" badge. | M | EDGE1c. |
| **EDGE1e** | Operator UX polish: green "syncing now" banner in Edge UI; "sync history" page on central listing every successful + failed sync per Edge. | S | EDGE1c, EDGE1d. |
| **EDGE1f** | RO-Crate export fallback. `shepard-edge export-bundle` produces a ZIP per `aidocs/31`; central import endpoint accepts it. Always available, no network required. | S | EDGE1a, `aidocs/31` R2. |
| **EDGE1g** | Tie-in to `aidocs/50` experiment-coordinator: the coordinator can run on an Edge. Edge compose grows an opt-in `coordinator` service. | M | EDGE1a, `aidocs/50` EXP1c. |
| **EDGE1h** | Tie-in to `aidocs/55` provenance: Edge-side activities sync with `originInstance` stamp; central dashboard groups sparkline strands by Edge. | S | EDGE1c, `aidocs/55` PROV1a-c. |
| **EDGE1i** | Air-gap install path: `shepard-edge package-images` exports all required container images to a tar on a connected machine; `shepard-edge import-images` loads them on the Edge. No network during Edge boot. | S | EDGE1a. |
| **EDGE1j** | Docs page `docs/help/deploy-edge.md` per `aidocs/49` D1c; reference page `docs/reference/edge.md`. | S | EDGE1a (so screenshots are capturable against a real Edge). |
| **EDGE1k** | Edge → Edge sync. **Parked.** Only Central is the canonical sink in v1. | — | Deferred to a future EDGE2 series. |

Recommended order: **EDGE1a → EDGE1f → EDGE1b → EDGE1c → EDGE1d
→ EDGE1e → EDGE1h → EDGE1i → EDGE1j → EDGE1g** — RO-Crate
fallback ships before the streaming protocol so operators have a
working answer the moment EDGE1a lands, and the protocol's
correctness pieces (conflict detect, provenance) layer on after.

---

## 10. Open questions for the maintainer

**Q1. Sync direction: pull-from-Edge vs push-from-Edge.**
- **Recommendation: push-from-Edge.** The connectivity event is on
  the Edge side (operator drives into the parking lot, walks into
  a coffee shop with WiFi, runs `shepard-edge sync`); central
  usually can't reach into an Edge behind NAT / cellular. Pull
  would require either (a) inbound connectivity to the Edge — not
  realistic — or (b) a long-poll / WebSocket the Edge keeps open
  to central — wasteful on cellular.

**Q2. Auth on the sync endpoint.**
- Edge identifies via a **long-lived API key minted on central**
  carrying the role `edge-sync` per `aidocs/51 §4` API-key roles
  (a new role string added to `shepard.apikey.role-allowlist`).
- **Should the key be revocable per-Edge? Yes.** Each Edge gets
  its own API key at `shepard-edge init` (operator pastes it
  during setup, or runs an enrolment flow against central if
  online at init-time). Central's `/v2/admin/edges` lists every
  enrolled Edge with revoke action — if a laptop is stolen, the
  admin revokes its key immediately; future sync attempts from
  that Edge 401.

**Q3. Storage budget alarms.**
- **Yes.** Edge UI warns at 80% / 90% disk used. The casual user
  filling up a campaign deserves a polite ceiling-coming warning,
  not a "Mongo refuses writes" surprise.
- Implementation rides on `aidocs/55`'s provenance dashboard:
  the `:Activity` `payloadDeltaBytes` rollup already knows the
  ingest rate; combined with `df` on the volume mount, the Edge
  UI shows a "X days until full at current rate" stat.

**Q4. Multiple Edges syncing into the same central — should they
appear as separate entries in central's "data sources" pane?**
- **Yes.** Tie to `aidocs/58` BIZ1 (the UI cluster has a similar
  "scope" feel — different data sources surfaced separately in
  the UI). Each Edge gets a row in `/admin/edges` on central with:
  display name, `originInstance` UUID, last sync time, sync
  protocol version, total bytes synced, conflict count.

**Q5. Demo / read-only Edge mode for showing partners.**
- **Defer to `aidocs/58` BIZ1 ("zeigen")** — same idea. The "show
  a partner without exposing central" use-case is the BIZ1
  problem in a different deployment shape; the design decisions
  there (read-only flag, sanitised data subset, time-boxed
  access) apply unchanged to a read-only Edge.

---

## 11. Cross-references

- **`aidocs/22`** — admin CLI (the `shepard-edge` binary mirrors
  the `shepard-admin` distribution + framework pattern).
- **`aidocs/25`** — L2 application-generated IDs (UUID v7
  `appId`s on every entity are a precondition for collision-free
  sync).
- **`aidocs/31`** — RO-Crate exports (the (A) fallback sync
  shape).
- **`aidocs/40`** — ecosystem (Edge is a sibling deployment shape
  alongside sTC / SPW / sVC).
- **`aidocs/42`** — vision (Edge moves from "not yet imagined" to
  a near-horizon line item; vision §"Where it's going" gets a new
  bullet on the same PR as EDGE1a).
- **`aidocs/49`** — in-app user docs (`docs/help/deploy-edge.md`
  is mandatory per D1c).
- **`aidocs/50`** — experiment-coordinator (Edge is its natural
  runtime; EDGE1g wires the two together).
- **`aidocs/51`** — instance-admin role + bootstrap-token
  mechanism + API-key `roles` field. Edge reuses the
  bootstrap-token path verbatim; `edge-sync` is a new role string
  in the allowlist.
- **`aidocs/55`** — PROV-O provenance survives sync; activities
  carry `originInstance`; central dashboard groups by Edge.
- **`aidocs/56`** — v2 simplification (`/v2/edge/sync` is a new
  endpoint living on the development shelf, not the frozen
  upstream surface).
- **`aidocs/58`** — UI cluster (Edge UI shares with central UI;
  BIZ1 "zeigen" is the same problem in a different deployment).
- **`aidocs/16`** — backlog row EDGE1 to be added by the
  dispatcher when this design is approved.
- **`aidocs/34`** — upgrade-tracker entry to be added by the
  dispatcher when EDGE1a lands (new opt-in compose file; zero
  impact on existing central deployments).
- **`aidocs/44`** — fork-vs-upstream matrix gains a new
  "Deployment shapes" row marking Edge as a fork-only feature.

---

## 12. What this isn't

To set the boundary clearly:

- **Not a cluster.** Edge is single-host. If the host dies, the
  Edge is down. High availability is `aidocs/19`'s territory on
  central, not Edge's.
- **Not a thin client.** Edge is fully autonomous offline — it
  has its own backend, its own databases, its own frontend. It
  is not "the central UI pointed at a local database."
- **Not bi-directional sync.** Central is canonical in v1.
  Changes on central don't push down to Edge. If an operator
  needs central data on the Edge, they hand-carry an RO-Crate
  bundle the other direction (out-of-scope for v1 — most field
  workflows are write-from-Edge, read-on-central).
- **Not multi-user with full UserGroups.** Edge is
  single-user-first; the UI hides UserGroup ceremony. Optional
  second user via CLI for hand-offs between operators, but no
  Keycloak, no LDAP, no group-permission picker.
- **Not on-the-fly merging.** Conflicts get `:CONFLICTED_WITH`
  edges and a triage pane. The Edge does not try to be clever
  about three-way merges.
- **Not a permanent satellite.** Edge is for the duration of a
  campaign. The expected lifecycle: deploy → record → sync →
  optionally wipe + redeploy for the next campaign. A long-lived
  Edge is fine (Edge-Truck runs for months), but it's still
  Edge — not "a second central."
- **Not a federation primitive.** Federation across shepard
  instances (`aidocs/16` X3) is a different problem with
  different trade-offs. Edge's "central is canonical" rule keeps
  v1 simple; federation can build on top of EDGE1k (Edge → Edge
  sync) if the demand materialises.

---

## 13. The casual-user story (worked example)

A geology PhD student is heading to Iceland for three weeks of
fieldwork. She needs to record GPS points, photos, drone imagery,
soil-sample structured data, and a daily lab journal entry.

**Before Edge.** She'd record on her laptop in a folder structure,
back up to an external SSD, come home, and spend two days
uploading and organising the data into the central shepard at
DLR. Filenames mismatch the upload form. The lab journal lives in
an Obsidian vault no-one else can see. Permissions get set wrong
on three Collections.

**With Edge.**

```
# At her desk, before flying out
$ shepard-edge init --profile edge-workstation \
    --target https://shepard.dlr.de \
    --campaign "Iceland-Volcano-2026"
# Opens the browser at https://shepard.local/, she logs in as
# `iris`, sets up the Collection from a campaign template
# (per aidocs/39), pre-creates the daily-journal DataObjects.

# In Iceland (no network)
# Records data, takes photos via shepard mobile (or just SCPs
# them into the FileBundle), writes daily lab-journal entries.
# Three weeks. The Edge laptop charges off the truck's inverter.

# Back at base camp with WiFi
$ shepard-edge sync
# Sync from Edge "iris-iceland-laptop" (originInstance: 4b91...)
#  → central https://shepard.dlr.de
# Mapping Edge user `iris` → central user [iris@dlr.de]? Y
# Streaming 847 activities, 12.4 GB payloads...
# ...
# Done in 18 min 42s. View on central:
# https://shepard.dlr.de/collections/iceland-volcano-2026
```

On central, the team sees the Iceland Collection appear with full
provenance — every `:Activity` stamped `originInstance: 4b91…`,
every FileReference carrying the captured-at-Edge audit badge.
Her advisor opens the lab journal entries the same evening.

This is the casual-user north star (`aidocs/42 §1.0`) made
concrete: **the researcher records data, syncs, done.** No
manual upload, no folder-structure ceremony, no "but does it know
which DataObject this file belongs to?" The Edge knew the whole
time — central just learned about it.
