---
stage: feature-defined
last-stage-change: 2026-05-31
audience: [operator, dispatcher, contributor]
---

# MFFD ingest readiness audit — 346 GB scale (2026-05-31)

**Scope.** Read-only preparation audit for the 346 GB MFFD timeseries export
that just finished at cube3 (`mffd-export/` dir; `ts-export/manifest.json`).
The operator has explicitly said **ingest will be later** — nothing in this
audit mutates state on either side.

**Prior ledger.** This audit revises and supersedes the `TS-INGEST-222GB-*`
projections in `aidocs/16-dispatcher-backlog.md §1913–1964`. The headline
deltas vs. the 222 GB ledger:

- Raw volume up **56 %** (222 → 346 GB).
- Six of seven chokepoints from the 2026-05-29 audit are now `done`
  (CHOKE-02/03/04/06 shipped; CHOKE-01 verified as a non-problem; CHOKE-05/07
  inherit those fixes); only the operational pre-flight + disk capacity story
  remain open.
- **NEW: capacity becomes the headline risk.** The 222 GB ledger called disk
  "OK / 421 G free → 30 G compressed → fits." At 346 GB on the same volume
  with the same compression ratio assumption the math is now tight, and the
  bind-mount substrate audit below shows the tightness is real.

Cross-refs: `aidocs/data/108-mffd-dump-ingestion-plan.md`,
`aidocs/agent-findings/ts-ingest-222gb-importer-audit-2026-05-29.md`,
`aidocs/agent-findings/db-baseline-post-mffd.md`,
`examples/mffd-showcase/scripts/RUNBOOK-2026-05-26-mffd-v16-content-transfer.md`,
`project_mffd_api_keys`, `project_mffd_timeseries_ingest`.

---

## §1 Capacity check

### 1.1 Host disk — the single shared substrate

`shepard.nuclide.systems` is a Proxmox LXC container (CT 101). All four data
substrates **share one ZFS dataset** mounted at `/`:

```
rpool/data/subvol-101-disk-0   500G   100G   401G   20%   /
```

Probed live 2026-05-31 via `df -h` inside containers + `docker inspect`.

The 19 T NFS at `/mnt/pve/unas` is the only other significant storage on the
host. Both `mffd-data.7z` (106 GB, 7z archive) and the eventual 346 GB
`ts-export/` would naturally land there.

### 1.2 Per-substrate volumes — mapped to host paths

| Substrate | Container mount | Host path | Mount type | Current size |
|---|---|---|---|---|
| TimescaleDB | `/var/lib/postgres/data` | `/opt/shepard/timescaledb/` | bind | 252 MB |
| Neo4j | `/data` | `/opt/shepard/neo4j/data/` | bind | 7.3 MB |
| MongoDB | `/data/db` | `/opt/shepard/mongodb/db/` | bind | 367 MB |
| Garage S3 (data) | `/var/lib/garage/data` | docker volume `infrastructure_garage_data` | named volume | 21 MB |
| Garage S3 (meta) | `/var/lib/garage/meta` | docker volume `infrastructure_garage_meta` | named volume | 193 KB |
| Backend logs | `/deployments/logs` | `/opt/shepard/backend/logs/` | bind | 18 MB |

Source: `docker inspect <container> --format '{{json .Mounts}}'` +
`infrastructure/docker-compose.yml` confirms — TimescaleDB's compose entry has
**two** volumes declared (a bind to `/opt/shepard/timescaledb` and an implicit
docker volume `b42c5fd9…`). The container reads from
`/var/lib/postgres/data` (bind path) — confirmed by
`docker exec infrastructure-timescaledb-1 ls -la /var/lib/postgres/data` showing
`PG_VERSION` + `base/` dir. The docker volume on `/var/lib/postgresql/data` is
**vestigial** (image-default mount; empty). Not deleting it pre-ingest is safe
but worth a one-line cleanup note in the runbook.

The `dxflrs/garage:v1.0.1` container also has **a third mount** —
`./garage-data` in the compose file resolves to
`/opt/shepard/infrastructure/garage-data` — but that path **does not exist on
the host**. Docker silently creates an empty bind dir at first start, which
ZFS likely never used because the named volumes outranked it. Verified:
`docker exec shepard-garage` data lives in
`/var/lib/docker/volumes/infrastructure_garage_data/_data` (21 MB), not in
`./garage-data`. **Action: confirm with the operator which path is canonical**
before kicking off MFFD — the compose declaration suggests the bind-mount was
intended; the runtime says the named volume won. Either is fine; the runbook
needs to reflect the actual one.

### 1.3 At-ingest projection (346 GB)

Compression: TimescaleDB ratio observed on the synthetic baseline is
~7–10× (113 channels, 1.17 M rows, hypertable size 18 MB; per
`aidocs/agent-findings/db-baseline-post-mffd.md`). The real-world MFFD data
is mostly numeric uint16/float32 at kHz rates — compression should hold or
improve (Timescale's tunable + `pg_stat_compression` on AFP sensor data
elsewhere in the literature reports 10–30× on dense numeric channels). The
346 GB raw therefore projects to **~35–50 GB compressed in TimescaleDB**.

Headroom math, **assuming compression-applies-during-ingest** behaviour (it
does, on chunk-close, per `MFFD-TS-01` migration shipped 2026-05-26):

```
Host free now:        401 GB
Peak transient state: 346 GB raw + active chunk uncompressed ~50 GB = 396 GB peak
                      → leaves 5 GB headroom at peak. UNSAFE.
                      
After compression policy sweeps + raw export cleanup:
  TimescaleDB:          35-50 GB
  Garage S3 (NDT + OTvis + file payloads): est 20-40 GB (binary attachments)
  Neo4j metadata:        2-5 GB  (≈1000 :Timeseries + ~10K :SemanticAnnotation
                                  + Activities scaled to per-batch granularity)
  MongoDB structured:     5-10 GB (SD payloads from v15 import)
  Total steady-state:   ~70-105 GB on top of current 100 GB → 170-205 GB used.
                       → 295-330 GB free. OK.
```

**The risk window is the 24–48h immediately around ingest.** Once a chunk
closes Timescale fires the recompression job (`timescaledb_information.jobs`
verified active on the synthetic baseline), but the active chunk and the
landing-zone copy of the export coexist. If the operator **stages the
`ts-export/` on the same host disk**, peak transient state breaks 500 GB.

### 1.4 Capacity verdict

🔴 **MUST land the staged export on NFS (`/mnt/pve/unas/dump/`), not on host
ZFS.** UNAS has 17 TB free and the importer's `LOCAL_MODE` reads files via
`DATA_DIR=<path>` (`mffd-import-v15.py:242`). Pointing `DATA_DIR` at
`/mnt/pve/unas/dump/ts-export/` is the trivial fix.

🟠 **Mid-ingest disk-watch is mandatory.** Add to the pre-flight: a 30-min
cron-loop logging `df -h /` + `du -sh /opt/shepard/timescaledb/` to
`/tmp/mffd-disk-watch.log` for the duration of the import. The runbook §0.5
heartbeat can subsume this.

🟢 **Compressed steady-state fits comfortably** (~205 GB on a 500 GB volume).

---

## §2 Source transfer-path options — 346 GB cube3 → nuclide

The export sits on `bt-au-cube3.intra.dlr.de` (DLR Augsburg intranet). The
nuclide dev box has no direct network path into the DLR intranet — operators
move data via VPN-on-demand or sneakernet. Source-of-truth: `RESUME.md §32`
and `project_mffd_api_keys` confirm cube3 is on `intra.dlr.de` (no public
route). cube3 ↔ nuclide is therefore inherently **mediated by the operator's
workstation** (jump host).

### 2.1 Options + first-order time estimates

Assumed conservative residential-bandwidth jump-host link (operator on home
fibre, ~100 Mbit/s symmetric upload):

| Option | Throughput assumption | 346 GB wall-clock | Pros | Cons |
|---|---|---|---|---|
| (a) `rsync -avP --partial --append-verify` over WireGuard tunnel via operator workstation | 80 Mbit/s sustained (15 % protocol overhead, jump-host CPU bottleneck on encryption) | **~9.6 h** | Restart-safe, incremental, integrity-verified via per-block checksums on resume. PEP-723 standard. | Two hops; bandwidth limited to operator uplink; tunnel-up duration matters. |
| (b) `scp -C` (zlib stream) via jump-host | 60 Mbit/s sustained (zlib on already-binary numeric data ≈ 0 win; encryption overhead dominates) | **~12.8 h** | Trivial — single command. | No resume; 1× network hiccup = restart from zero. **Avoid for 346 GB.** |
| (c) Garage-to-Garage S3 replication (if cube3 had Garage) | N/A — cube3 has no Garage backend per the v5 deployment shape. | — | Would have been ideal architecturally. | Not available. |
| (d) Chunked `rsync --partial --append-verify` with `--bwlimit` + nightly windows | 80 Mbit/s × 8 h/night = 230 GB/night | **~36 h elapsed wall-clock (≈ 2 nightly windows)** | Operator presence not required during transfer; restart-safe; same `--append-verify` integrity. | Drags ingest to second day; tunnel must stay up. |
| (e) SneakerNet — external SSD, hand-carry / courier | I/O bound: SATA III ~500 MB/s = 12 min copy each end, plus courier time | **~24 h door-to-door (overnight courier) + 2 × 12 min copy** | Decouples from network entirely; gives the operator a tangible backup artefact; courier insurance covers IP loss. | Capital cost (~€60 for a 1 TB SSD); customs friction if cube3 is overseas (it isn't — Augsburg → Nuclide is German-domestic, so no friction). |
| (f) UNAS as inter-instance courier | UNAS at 192.168.1.31 is on operator's LAN, not DLR intranet. Same transfer story as (a)/(b) for cube3 → UNAS; then UNAS → nuclide is gigabit LAN (~80 MB/s) ≈ 80 min. | **~9.6 h (cube3→UNAS) + 1.3 h (UNAS→nuclide LAN)** | Once on UNAS, the artefact is durable; staging to nuclide is fast. | Same first-hop bandwidth as (a). |

### 2.2 Recommendation

**Option (a) — `rsync --partial --append-verify` directly into
`/mnt/pve/unas/dump/ts-export/` — is the default.**

Reason: it is what the operator's setup is already optimised for (UNAS is
mounted at `/mnt/pve/unas/` on the nuclide host; cube3 has SSH; rsync handles
both resume and integrity). Walltime ~10 h overnight; restart-safe; integrity
guaranteed. The 7z archive precedent (`mffd-data.7z`, 106 GB transferred to
`/mnt/pve/unas/dump/` per ledger §1) shows this path is already validated for
~100 GB volumes.

Falls back to **(d)** (nightly windows) if the operator can't keep the tunnel
up for 10 contiguous hours. Falls back to **(e)** (sneakernet) if cube3's
egress is throttled or the tunnel is unreliable.

### 2.3 Pre-flight for the transfer itself

Before starting the transfer:

1. `df -h /mnt/pve/unas` shows ≥ 400 GB free (currently 17 TB — fine).
2. `ssh kreb_fl@bt-au-cube3.intra.dlr.de "du -sb /path/to/ts-export"`
   confirms the size projection matches the operator's claim ("346 GB").
3. `rsync --dry-run -avP --partial` against a small probe (~1 GB subdir)
   shows the resume mechanism works end-to-end.
4. Start the real transfer inside `tmux` so a dropped SSH does not kill it.
5. Log the transfer to a file the operator can `tail -f` later from anywhere.

---

## §3 v15.x importer state — readiness

### 3.1 Latest importer version + commits

`examples/mffd-showcase/scripts/mffd-import-v15.py` — the file is named v15
but its in-tree iteration is now **v16.5**:

```
6d5338270 fix(mffd-import): v16.4 — session pool flush + lower workers + upload retry
f8d2e3738 fix(mffd-import): Q7 / #145 — fileRef payload truncation + forgotten oid
69fff1cb7 feat(mffd): V16-IMPL-3 attribute copy + V16-IMPL-6 parallel-sibling verification
65d79bc02 feat(mffd-import): source instance URL/ID capture + original attrs provenance stamp
23d17f094 feat(mffd-import): MFFD-IMPORT-PERF3+PERF4 — preempt-friendly self-update + cube3 gauge metrics
```

HEAD: `b075c0b49cd2e216440b410854914e62ed830416` (2026-05-30 main).

The version comments at the top of the file (line 9 onwards) document:
- v15.2 smart warmup module (`_smart_warmup.py`) — IMPORT-W1/W2/W3
- v15.9 source-side user identity capture (MFFD-IMPORT-USER-CAPTURE)
- v15.16 PEP-723 `uv run --script` runner
- v15.17 ENV-ALIAS + AUTH-PROBE
- v15.18 semantic-repo soft-fail on 4xx
- v15.19 `MFFD_CREATE_SEMANTIC_REPOS` env opt-out
- v16.0 PRESERVE-HIERARCHY (3-pass tree replication; shipped 2026-05-23)
- v16.1 worker fan-out (8 workers default; lowered to 4 in v16.4)
- v16.3 attribute copy + parallel-sibling verification
- v16.4 session pool flush, workers→4, upload retry
- v16.5 BATCH-API adoption (POST `/v2/data-objects/batch`)

### 3.2 Compatibility with the 346 GB `ts-export/manifest.json` shape

The exporter `mffd-ts-export.py` defines the layout (lines 41–55):

```
ts-export/
├── manifest.json
├── tapelaying/
│   ├── collection.json
│   ├── hierarchy.json
│   └── <do_name>/
│       ├── metadata.json
│       ├── ts/<ref>.csv     ← ROW-format
│       ├── files/<name>
│       └── structured/<ref>.json
└── bridgewelding/
    └── ...
```

The importer reads via `DATA_DIR` (`mffd-import-v15.py:242`). LOCAL_MODE is
the documented fallback when no SOURCE vars are set
(`LOCAL MODE     Read files from DATA_DIR subdirs. Fallback when no SOURCE
vars set.` — line 76). The `step_key` resolution at line 4633 (`local_dir =
DATA_DIR / step_key`) suggests the importer iterates per-DO subdir, which
matches the export layout. ✅ Same wire shape.

**Caveat:** the importer's primary execution mode is SOURCE_SHEPARD_URL +
SOURCE_SHEPARD_API_KEY (cross-instance live pull from cube3). The 346 GB
export shifts this to LOCAL_MODE (read from disk). The LOCAL_MODE path has
historical implementation debt — it was the original mode before SOURCE_*
became the default, and may not have been re-tested since v15.13's
`CONTAINED-COMPLETENESS` pass. **Action: pre-flight a small (~1 GB) probe
import via LOCAL_MODE against a clean nuclide instance and verify a sample
DO + container + ref lands correctly before kicking off the 346 GB.**

### 3.3 Worker count + backoff defaults

```python
PRESERVE_HIERARCHY_WORKERS = max(
    1, int(os.environ.get("MFFD_PRESERVE_HIERARCHY_WORKERS") or "4")
)  # was "8" — v16.4 lowered to stay under connection-reset threshold
```

Default 4 workers. v16.4 explicitly lowered from 8 after observing
ConnectionReset errors at higher fan-out. The backoff (line 308) is
exponential with full jitter, capped at 60 s.

**Question for operator (§8.1).** At 346 GB the throughput cost of 4 workers
is real. The CHOKE-01 audit projected 7–10 h end-to-end with 4 workers and
multi-row INSERT at 20 K rows/statement (verified shipped). Doubling to 8
workers cuts wall-clock in half if the backend can absorb it.
`quarkus.thread-pool.max-threads=64` + `queue-size=1000` was shipped
(CHOKE-06) which gives backend headroom. PgBouncer pool size is 20 default
(see §4.3), which at 4 workers × ~5 active per worker = 20 → comfortably
in budget; at 8 workers × 5 = 40 → would need PgBouncer bump.

Recommend **defaulting to 4 workers for the first 10 % of ingest** (the
adaptive-backoff signal during the first hour tells you whether to climb to
6 or 8). Cheap to step up; expensive to recover from a thundering-herd
restart.

### 3.4 PROV-O writeback (n10s + dual-write)

The importer's PROV-O writeback was extended 2026-05-26:
- PROV-USER-MIRROR-ENDPOINT (#229) — `POST /v2/admin/users/mirror` shipped.
- PROV-USER-ENRICH (#234) — `X-Source-User-*` headers → `:MirroredUser` node
  in `ProvenanceCaptureFilter` shipped.
- CHOKE-04 (TS-SEMANTIC-01 dual-write fire-and-forget) verified
  `try { ... } catch (Throwable t) { Log.warnf(...) }` at outermost wrapper —
  Neo4j failure does NOT roll back Postgres.

The 8 `urn:shepard:*` predicates V61 registered for batch writeback are
documented at `mffd-import-v15.py:537–546`. The 10 PROV-O predicates at
552–563 are pre-seeded by `OntologySeedService` at backend startup (live
verified — no missing-predicate WARN in logs).

⚠️ **Edge case to watch.** v15.18 introduced soft-fail for semantic-repo
creation (line 1934). If the dest instance is **freshly reset per §0 of the
runbook** and the OntologySeedService didn't complete by the time the
importer's first writeback call lands, the importer prints a warn and skips
provenance annotations. Concretely:

```
[warn-sr] could not create semantic repo <name>; continuing without it
          (provenance annotations will skip)
```

This is **silent data loss for prov annotations**, not data loss for the
primary entities. Pre-flight: after `docker compose up -d`, wait for
`shepard-api/healthz/ready` to show **all** checks UP (not just liveness)
before launching the importer. Backend startup includes Flyway + Neo4j
migrations + OntologySeedService — the `healthz/ready` endpoint waits for
all three. Verified live: `curl -sf
https://shepard-api.nuclide.systems/shepard/api/healthz/ready` returns
`status: UP` and the checks include postgis-readiness.

### 3.5 MFFD API keys — status

Per `project_mffd_api_keys`:

| Side | Subject | jti | Minted | Days old (2026-05-31) | Status |
|---|---|---|---|---|---|
| Dest (nuclide) | `ee4c010f-d648-4630-…` | `76c20c60` | iat 2026-05-22 | 9 d | Likely valid — nuclide JWTs have no `exp` claim either; verify with `GET /v2/me`. |
| Source (cube3) | `kreb_fl` | `eca5887a` | iat 2026-05-23 13:51 UTC | 8 d | Should still work; the v15.9 token without `exp` claim is described as "long-lived" via `POST /shepard/api/users/kreb_fl/apikeys`. Pre-flight verify. |

**Re-mint pattern (from memory `project_mffd_api_keys`):**

```bash
curl -fsS -X POST \
  -H "X-API-KEY: $CURRENT_JWT" \
  -H "Content-Type: application/json" \
  -d '{"name":"mffd-importer-346gb-2026-05-31"}' \
  https://backend.bt-au-cube3.intra.dlr.de/shepard/api/users/kreb_fl/apikeys
# → response.jwt is the new credential
```

Re-mint defensively before kickoff. The transfer is 10 h — the operator does
not want a 401 at hour 8 stalling the ingest.

### 3.6 Importer-state verdict

🟢 v15.x / v16.5 is **ingest-ready** for the 346 GB shape. All six chokepoints
shipped or verified N/A. PROV-O dual-write fire-and-forget verified. The
warmup module (`_smart_warmup.py`) was explicitly designed for this scenario
— fail-fast diagnostic if dest is incompatible.

🟠 LOCAL_MODE path needs a small probe (~1 GB subdir) before full 346 GB.
This is the only gap between v15's continuous-development surface and the
346 GB-from-disk shape.

🟠 API keys are days-old, not expired (no `exp` claim) — re-mint defensively.

---

## §4 Readiness ledger reconciliation — 222 GB → 346 GB

Walk every `TS-INGEST-222GB-*` row in `aidocs/16-dispatcher-backlog.md`
(lines 1913–2027) and resolve each one.

### 4.1 The risks table (`aidocs/16 §1933-1940`)

| ID | 222 GB status | 346 GB status | Evidence | Blocking? |
|---|---|---|---|---|
| TS-INGEST-222GB-NEO4J | queued (DB-OPT5 mitigations exist) | **unchanged — non-blocking** | DB-OPT5 trims `referenceIds` from default DataObject list payload (shipped). At 346 GB the channel count projects to ~1500 (vs 1000 at 222 GB), per-DO refcount scaling matches. `211bb3fec feat(api): DB-OPT5 — DataObject list payload diet`. | No — verify post-ingest. |
| TS-INGEST-222GB-PGBOUNCER | queued | **NEW GAP: at-4-workers OK; at-8-workers needs bump** | `pool_mode=transaction`, `max_client_conn=200`, `default_pool_size=20` per `aidocs/16 §1995`. 4 workers × ~5 active = 20 → in budget. If operator decides to go 8 workers (§3.3), bump `default_pool_size` to 40 first. | Conditionally — only if operator chooses >4 workers. |
| TS-INGEST-222GB-COMPRESSION | queued | **satisfied** | DB-OPT3 `chunk_time_interval` shipped; live verified 82/82 baseline chunks compressed (100 %) per `db-baseline-post-mffd.md`. Recompression job verified active in `timescaledb_information.jobs`. | No. |
| TS-INGEST-222GB-DOSPRAWL | queued | **non-blocking — runbook mitigated** | v16 PRESERVE-HIERARCHY shipped 2026-05-23 — preserves cube3 tree shape instead of flattening. DOsprawl is now ~equal to source-DO count. Per `RESUME.md`, MFFD-Dropbox cube source has 8514 DOs at tapelaying coll 48297 + smaller bridgewelding coll 163811. UI-020 bulk lab-journal endpoint mitigates N+1. | No — but verify post-ingest with the BATCH-API audit (`aidocs/agent-findings/batch-api-audit-2026-05-27.md`). |
| TS-INGEST-222GB-PROVENANCE | queued | **satisfied** | PROV-USER-MIRROR-ENDPOINT (#229) + PROV-USER-ENRICH (#234) shipped 2026-05-26. ProvenanceCaptureFilter captures at per-HTTP-request granularity = 1 Activity per import-batch HTTP call (not per-data-point). At ~10 K HTTP calls for 346 GB import → ~10 K Activity rows added. Baseline is 2925; final ~13 K — Neo4j page-cache pressure negligible. | No. |
| TS-INGEST-222GB-GARAGE | queued | **needs runbook step** | `shepard-files` bucket is single-node, replication factor 1. No quota set. Binary attachments at 346 GB scale project to ~20–40 GB (NDT + OTvis tar bundles + calibration + mesh). Garage bucket lifecycle: none configured. **Pre-flight: confirm there's no quota OR set one explicitly to 100 GB to avoid surprise saturation.** | No (soft) — operator should explicitly accept/set. |

### 4.2 The CHOKEPOINTS table (`aidocs/16 §2003–2027`)

| ID | 222 GB status | 346 GB status | Notes |
|---|---|---|---|
| TS-INGEST-222GB-CHOKE-01 | **PARTIAL — verified non-problem** | non-blocking | Per the 2026-05-29 audit, importer uses v1 multipart CSV → server-side multi-row VALUES INSERT batched at `INSERT_BATCH_SIZE=20000` rows/statement (`TimeseriesDataPointRepository.java:59,108–125`). **Projection: 7–10 h end-to-end for 222 GB. At 346 GB: ~11–16 h.** The faster TS-OPT3-COPY endpoint exists but switching is optional. |
| TS-INGEST-222GB-CHOKE-02 | ✓ done (N/A for importer) 2026-05-29 | satisfied | v1 CSV-import write path does not call 5-tuple resolver per data point; `getOrCreateTimeseries` is once-per-channel. |
| TS-INGEST-222GB-CHOKE-03 | ✓ done 2026-05-29 | satisfied | 3 `:*Config` singleton DAOs patched (SqlTimeseriesConfigDAO + InstanceRorConfigDAO + SemanticConfigDAO). 12 regression tests green. |
| TS-INGEST-222GB-CHOKE-04 | ✓ done | satisfied | TS-SEMANTIC-01 dual-write fire-and-forget verified at `TimeseriesSemanticDualWriteService.java:106–251`. |
| TS-INGEST-222GB-CHOKE-06 | ✓ done | satisfied | `quarkus.thread-pool.queue-size=1000` + `max-threads=64` in `application.properties`. |
| TS-INGEST-222GB-DASHBOARD | queued (NICE) | **deferred — not pre-flight critical** | A `/tmp/mffd-disk-watch.log` cron is the minimum viable replacement. Real dashboard ships post-ingest. |

### 4.3 Net assessment

Eight rows reconcile to **non-blocking** or **satisfied**. Two operational
gaps remain:

1. **Capacity: stage `ts-export/` on UNAS, not host ZFS** (§1.4).
2. **PgBouncer pool**: bump if worker count > 4 (§3.3 + §4.1).

### 4.4 Revised wall-clock projection

The 222 GB ledger said "7–10 h end-to-end" at 4 workers with verified
multi-row INSERT batching. At 346 GB the ratio is linear (1.56×):

- **4 workers, multi-row INSERT (current default): 11–16 h.**
- **8 workers, multi-row INSERT (operator-elective): 6–10 h.** Requires
  PgBouncer pool bump to 40.
- **TS-OPT3 COPY endpoint (M-effort code change pre-ingest): cuts another
  3–5×. → 2–5 h.** Out-of-scope for this audit but worth flagging.

For the §7 cost estimate the canonical figure is **11–16 h at 4 workers**.

---

## §5 Pre-flight checklist — operator-runnable

Annotated checklist. Items prefixed 🔴 are blocking, 🟠 are recommended, 🟢
are completed/verified.

```
🟢 1. Backend deployed at HEAD = b075c0b49cd2e216440b410854914e62ed830416 (J1e + TS-IDc landed)
🟢 2. CHOKE-01..06 satisfied (audit §4.2)
🟢 3. PROV writeback (n10s + MirroredUser) shipped (audit §3.4)

🔴 4. Disk capacity: stage ts-export/ at /mnt/pve/unas/dump/ts-export/, not host ZFS
       Verify: df -h /mnt/pve/unas shows ≥ 500 GB free (currently 17 TB)
       Set:    DATA_DIR=/mnt/pve/unas/dump/ts-export/ in importer env

🔴 5. API key freshness:
       Source (cube3): curl -sf -H "X-API-KEY:$SOURCE_SHEPARD_API_KEY" \
                       https://backend.bt-au-cube3.intra.dlr.de/shepard/api/me
       Dest (nuclide): curl -sf -H "X-API-KEY:$SHEPARD_API_KEY" \
                       https://shepard-api.nuclide.systems/v2/me
       If either returns 401: re-mint per §3.5 pattern, update memory file.

🟠 6. LOCAL_MODE probe (~1 GB sample):
       Extract 1 step subdir from ts-export/tapelaying/ to /tmp/mffd-probe/
       DATA_DIR=/tmp/mffd-probe/ uv run --script mffd-import-v15.py --dry-run
       Verify: DO + container + ref counts match the sample.

🟠 7. Pre-import reset per RUNBOOK §0.1–0.7:
       docker compose stop backend frontend neo4j timescaledb shepard-garage
       rm -rf /opt/shepard/neo4j/data/* /opt/shepard/timescaledb/* infrastructure/garage-data/*
       docker compose up -d
       until curl -sf https://shepard-api.nuclide.systems/shepard/api/healthz/ready | grep -q UP; do
         echo "waiting..."; sleep 5
       done

🟠 8. Verify Flyway migrations clean post-reset:
       docker compose exec timescaledb psql -U postgres -d shepard \
         -c "SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
       Expected: V1.14.0 (channel_metadata) or higher, success=t.

🟠 9. PgBouncer pool config: if planning >4 workers, edit
       infrastructure/docker-compose.yml pgbouncer env:
         PGBOUNCER_DEFAULT_POOL_SIZE=40
         PGBOUNCER_MAX_CLIENT_CONN=200 (already)
       Restart: docker compose restart pgbouncer

🟠 10. Garage bucket sanity:
       docker exec shepard-garage /garage bucket info shepard-files
       Expected: alive, no quota. If quota set, raise to ≥ 100 GB.

🟠 11. Quarkus thread-pool already tuned (verified):
       grep "quarkus.thread-pool" backend/src/main/resources/application.properties
       Expected: queue-size=1000 + max-threads=64

🟠 12. Disk-watch loop in tmux:
       while sleep 1800; do
         df -h / >> /tmp/mffd-disk-watch.log
         du -sh /opt/shepard/timescaledb/ /var/lib/docker/volumes/infrastructure_garage_data/_data/ \
           >> /tmp/mffd-disk-watch.log
       done

🟠 13. Importer health: GET /v2/admin/importer/health (does not exist today; SKIP)
       Alternative: tail infrastructure-backend-1 logs:
       docker compose logs -f backend | tee /tmp/mffd-backend.log

🟠 14. Alerting:
       Verify Matrix notification transport is configured (AA shipped per ledger).
       Test: trigger one warmup probe; confirm Matrix message arrives.
       (If not configured, accept that operator must tail logs manually.)
```

Items 4–5 are **blocking**. Items 6, 7, 8, 12 are strongly recommended. Items
9, 10, 11, 13, 14 are operational hygiene.

---

## §6 Risk + abort plan

If the ingest gets ~30 % through and an issue surfaces (wire mismatch,
unexpected channel cardinality, PgBouncer exhaustion, disk pressure):

### 6.1 Clean stop

```bash
# 1. Stop the importer wrapper (mffd-runner.sh tracks PID; SIGTERM gracefully exits)
echo "stop" > /tmp/mffd-runner.stop      # see mffd-runner.sh STOP_FILE pattern

# 2. Wait for in-flight workers to drain (≤ 60 s with default backoff cap)
tail -f /tmp/mffd-import.log | grep -E "drain|exited cleanly"

# 3. Confirm no in-flight HTTP transactions
curl -sf https://shepard-api.nuclide.systems/v2/admin/instance/stats 2>/dev/null  # if available
```

The importer's state file (`./mffd-import-state.json` by default) preserves
batch_sequence and per-DO done status — restart is idempotent. `is_do_done`
short-circuit (PERF2, v15.8) ensures done-DOs are not re-processed.

### 6.2 Snapshot partial state

```bash
# Neo4j: hot online backup via cypher-shell (no downtime needed for tar snapshot)
docker compose exec neo4j neo4j-admin database dump neo4j \
  --to-path=/data/dumps/partial-$(date +%Y%m%d-%H%M%S).dump

# TimescaleDB: pg_dump custom format
docker compose exec timescaledb pg_dump -U postgres -Fc shepard \
  > /mnt/pve/unas/backup/mffd-partial-$(date +%Y%m%d-%H%M%S).dump

# Garage: copy the docker volume to UNAS
docker run --rm -v infrastructure_garage_data:/g -v /mnt/pve/unas/backup:/out \
  alpine tar czf /out/garage-partial-$(date +%Y%m%d-%H%M%S).tgz -C /g .

# MongoDB
docker compose exec mongodb mongodump --archive \
  > /mnt/pve/unas/backup/mongo-partial-$(date +%Y%m%d-%H%M%S).archive
```

This creates a four-substrate snapshot. Restore script: standard
substrate-level restores. The §0 RUNBOOK reset path destroys these by
design — keep the snapshots outside the wipe paths.

### 6.3 Diagnose + restart

Typical failure modes + adjustments:

| Symptom | Likely cause | Adjustment | Restart shape |
|---|---|---|---|
| ConnectionReset surge in worker logs | PgBouncer saturation | Bump `default_pool_size` to 40; restart pgbouncer | Re-run importer; PERF2 short-circuit skips done DOs |
| Disk free dropping below 50 GB | active uncompressed chunk size > expected | Wait for `timescaledb_information.jobs` to fire recompression (~5 min after chunk-close); if no fire, manually trigger: `SELECT compress_chunk(c) FROM show_chunks('timeseries_data_points') c;` | continue ingest |
| Neo4j page cache eviction storms | unexpected Activity row growth | confirm per-batch granularity (CHOKE-05); if per-data-point, file an emergency fix on `ProvenanceCaptureFilter` | stop, fix, restart |
| LOCAL_MODE path 500s | importer expected SOURCE_* mode | confirm `DATA_DIR` is set and points at the right subdir; re-check the LOCAL_MODE probe (§5.6) succeeded | restart |
| 401 from dest | nuclide JWT expired despite no `exp` claim | re-mint per §3.5 | restart |
| 401 from source (cube3) | kreb_fl JWT expired | re-mint per §3.5 | only matters if importer is in SOURCE_* mode (it's not, in LOCAL_MODE) |

### 6.4 Full rollback (worst case)

If the partial state is unsalvageable (corrupt migration, partial schema
write, wrong data shape committed):

```bash
# RUNBOOK §0 — same wipe path as pre-import reset
docker compose stop backend frontend neo4j timescaledb shepard-garage
rm -rf /opt/shepard/neo4j/data/* /opt/shepard/timescaledb/* \
       /var/lib/docker/volumes/infrastructure_garage_data/_data/*
docker compose up -d
make wait-for-health
# Then either: re-run from §5.7 (clean wipe + restart) or
#              restore from §6.2 snapshot if a known-good intermediate state exists
```

This is the same shape as PRE-MUT-SNAP (currently suspended per
`feedback_pre_mut_snap_suspended_pre_reset.md`); the snapshot in §6.2 is the
substitute until PRE-MUT-SNAP resumes post-reset.

---

## §7 Time + cost estimate

### 7.1 Wall-clock

Ingest itself (per §4.4 reconciliation):

| Config | Wall-clock for 346 GB | When to pick |
|---|---|---|
| 4 workers, multi-row INSERT (current default) | **11–16 h** | First ingest, no time pressure. The safe-default. |
| 8 workers + PgBouncer pool=40 | 6–10 h | Operator wants overnight finish + has budget to bump PgBouncer. |
| TS-OPT3 COPY endpoint (M-effort code) | 2–5 h | Re-ingest scenarios, not first attempt. |

Plus the transfer cost (§2.1):

| Transfer | Wall-clock | Operator presence |
|---|---|---|
| `rsync -avP --partial --append-verify` over WireGuard | ~10 h | Tunnel-up required; can be `tmux` + cron-recheck |
| Nightly windows | ~36 h elapsed (2 nights × 8 h) | None mid-window |
| SneakerNet | ~24 h door-to-door | Two ~12 min copy windows |

### 7.2 End-to-end (cube3 → ingested + queryable on nuclide)

- **Best case (rsync overnight + 4-worker ingest):** 22–26 h total, finishes
  in roughly one 24h cycle.
- **Worst case (nightly transfer + 4-worker ingest):** 48–52 h, two 24h
  cycles.
- **Optimised (sneakernet + 8-worker ingest):** 30–34 h, single overnight +
  morning ingest. Doesn't actually beat option 1 because the transfer is the
  ratelimiter once the courier is dispatched.

### 7.3 Operator-time

The operator is in the loop for:
- ~30 min pre-flight (§5 items 4–8)
- ~5 min kickoff (`mffd-runner.sh` launch in tmux)
- Periodic check-ins during ingest (§5.12 disk-watch makes this tail-able)
- ~30 min post-ingest verification (run `mffd-completeness-check.py`)

Total operator-hours: ~1.5 h spread over a 24–48 h calendar window. The
operator is **not** required to babysit the actual ingest hours.

### 7.4 Resource cost

- Backend CPU: 4 Quarkus workers + JVM + GC. Peak ~4 vCPU sustained.
- TimescaleDB CPU: write-heavy phase ~2 vCPU; recompression bursts ~1 vCPU
  per chunk-close.
- Memory: Neo4j heap is at 2 G initial per compose; bump to 4 G transient if
  Activity growth exceeds projection. Otherwise unchanged.
- Network: ~10 GB egress to UNAS (small — the importer is local-disk-bound,
  not network-bound, in LOCAL_MODE).
- Energy: ~24 h × ~150 W = 3.6 kWh. Per `feedback_energy_log_per_commit.md`,
  this lands in `aidocs/sustainability/00-energy-estimation-log.md` post-run.

---

## §8 Open questions for the operator

The audit cannot answer these unilaterally. Operator decision required
before kickoff.

### 8.1 Worker count

**Default (recommended):** 4 workers.

**Alternative:** 8 workers, with PgBouncer `default_pool_size=40` bumped
first.

**Question:** is the wall-clock saving (16 h → 10 h, ~6 h faster) worth the
PgBouncer config change and the elevated risk of ConnectionReset
thundering-herd if backend GC hiccups during ingest?

**Recommendation:** start at 4 workers. Watch the first hour. If the
adaptive-backoff log lines are quiet (no retries), bump to 6 or 8 mid-ingest
via env override + worker-pool resize (`mffd-runner.sh` supports SIGCONT
worker-pool resize as of v16.1).

### 8.2 Source transfer strategy

**Default (recommended):** rsync `--partial --append-verify` overnight via
operator-workstation WireGuard tunnel.

**Alternatives:** nightly windows (option d); sneakernet (option e).

**Question:** how confident is the operator that their WireGuard tunnel
stays up for 10 contiguous hours?

**Recommendation:** start with rsync. If the first 30 min of transfer drops
the tunnel even once, switch to sneakernet (€60 SSD + overnight courier).

### 8.3 Ingest window — overnight or 24h continuous?

**Default:** 24 h continuous in tmux, operator does periodic check-ins.

**Alternative:** scheduled overnight ingest, operator out-of-loop.

**Question:** does the operator want to be available for failure modes
(§6.3) or accept the cost of a restart-from-state in the morning?

**Recommendation:** start in tmux during a workday so the first 1–2 h are
co-observed. If the adaptive-backoff signal is quiet at hour 2, leave it
running overnight; the importer's `_smart_warmup.py` already includes
fail-fast diagnostic for the issues that surface in the first hour.

### 8.4 Tolerance for partial-import failures

The importer has two modes for handling per-DO failures:

- **Skip + log** (default in older versions): a failed DO is logged but the
  rest of the import continues.
- **Abort** (configurable): a failed DO halts everything.

Per CLAUDE.md `feedback_completeness_nonnegotiable.md`: **completeness is
non-negotiable for importers** — never SKIP; retry-forever with adaptive
backoff. The importer already implements this (v15's retry-with-backoff is
explicit). But there are corner cases the importer cannot recover from
(corrupt source data, persistent backend 500, missing OID payloads).

**Question:** what's the operator's tolerance for the importer halting at
hour 8 of a 16 h run because of one bad DO?

**Recommendation:** keep the importer in fail-fast mode. Better to halt and
restart than to skip and discover a hole post-ingest.

### 8.5 Garage bucket quota

**Default:** no quota set on `shepard-files`. The bucket can grow until host
disk runs out.

**Alternative:** set explicit quota at 100 GB. The importer hits a 507
Insufficient Storage at ~100 GB instead of crashing the host.

**Question:** does the operator prefer an explicit failure mode (quota → 507)
or an implicit one (disk full → all services degraded)?

**Recommendation:** set the quota. `garage bucket set-quota --max-size 100G
shepard-files`.

### 8.6 Should we ship TS-OPT3-COPY endpoint pre-ingest?

The faster `/v2/timeseries-containers/{id}/channels/{shepardId}/data/ingest`
endpoint exists and uses Postgres `COPY FROM STDIN`. It would cut ingest
wall-clock by another 3–5× (→ ~3 h at 4 workers).

**Cost:** the importer change is M-effort. The endpoint exists already
(verified at
`backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/resources/TimeseriesContainerChannelsRest.java`
+ `CopyIngestRequestIO.java`). The importer's CSV-import path would need a
switch.

**Question:** is the wall-clock saving worth a pre-ingest code change?

**Recommendation:** no for this ingest. Save it for the re-ingest cycle
where the data shape is known and the wire contract is proven.

---

## §9 Post-ingest verification (not in scope but referenced)

For completeness, the post-ingest verification is owned by
`examples/mffd-showcase/scripts/mffd-completeness-check.py` (533 lines) and
the `db-baseline-post-mffd.md` snapshot pattern. The minimum verification
after the 346 GB ingest finishes:

1. `mffd-completeness-check.py` — confirms src ≅ dest DO + ref counts.
2. Re-run DB-OPT probes against now-realistic-volume data (`EXPLAIN
   ANALYZE` on hot-path queries).
3. Snapshot the four substrates for the new baseline (replaces the synthetic
   showcase baseline).
4. Update `aidocs/agent-findings/db-baseline-post-mffd.md` with the
   real-data numbers.
5. Run the DB-OPT family review per `feedback_post_ingest_db_audit.md`.

---

## §10 Audit verdict + recommended next action

🟢 **Backend is ingest-ready.** All six CHOKE-* items shipped or verified
N/A. PROV-O dual-write is fire-and-forget. Importer is v16.5.

🟠 **One blocker + one risk** before kickoff:

- **Blocker:** stage `ts-export/` at `/mnt/pve/unas/dump/ts-export/`, not on
  host ZFS. Setting `DATA_DIR=/mnt/pve/unas/dump/ts-export/` resolves it.
- **Risk:** API keys are 8-9 days old. Re-mint defensively per §3.5.

🟠 **Open operator decisions** in §8 (worker count, transfer strategy,
ingest window, partial-import tolerance, Garage quota, TS-OPT3 question).

**Recommended next action sequence:**

1. **Operator confirms §8.1–§8.5 decisions** (~10 min).
2. **§3.5 API key freshness check** (~5 min).
3. **§5 pre-flight items 4–8** executed in sequence (~30 min).
4. **rsync transfer kickoff in tmux** (~10 h unattended).
5. **LOCAL_MODE probe with ~1 GB subdir** while transfer runs (~15 min).
6. **Full importer kickoff** via `mffd-runner.sh` once transfer completes
   and probe is clean (~11–16 h unattended, monitored via §5.12 disk-watch
   + log tail).
7. **Post-ingest verification** per §9 (~30 min).

**Total operator-time:** ~1.5 h spread over a 24–28 h calendar window.

---

## §11 Audit metadata

- Branch at audit time: `main` @ `b075c0b49cd2e216440b410854914e62ed830416`
- Backend container: `shepard-backend-patched:local` (Up 3 h, healthy)
- Importer version: v16.5 (file labelled `mffd-import-v15.py`)
- Runbook: `RUNBOOK-2026-05-26-mffd-v16-content-transfer.md` (§0–§7)
- Probe targets: `https://shepard-api.nuclide.systems/shepard/api/healthz/ready`
  (UP), `https://shepard-api.nuclide.systems/shepard/api/healthz/live` (UP)
- Read-only invariant: no mutations performed on cube3 or nuclide; this
  audit is a planning document only.
