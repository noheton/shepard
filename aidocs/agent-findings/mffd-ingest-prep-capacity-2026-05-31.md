---
stage: feature-defined
last-stage-change: 2026-05-31
audience: [operator, dispatcher]
---

# MFFD ingest prep — capacity snapshot (2026-05-31)

**Scope.** Read-only capacity verification for the 346 GB MFFD timeseries
ingest. Probed live 2026-05-31 ~17:43 UTC from the nuclide LXC container
(CT 101). Pairs with the readiness audit at
`aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md` §1 and
reconciles the same numbers against current state.

---

## §1 Per-substrate snapshot

| # | Substrate | Host path / mount | Current size | Free at mount | Status |
|---|---|---|---|---|---|
| 1 | NFS staging (`/mnt/pve/unas/`) | `192.168.1.31:/var/nfs/shared/storage` (19 T total, 2.1 T used) | n/a (network mount) | **17 TB** | ✅ Plenty for 346 GB staged export. |
| 2 | Host ZFS root (`/`) | `rpool/data/subvol-101-disk-0` (500 G LXC quota) | 101 GB used (20%) | **400 GB** | ✅ ≥ 401 GB audit target met. Watch peak: see §2. |
| 3 | TimescaleDB | bind `/opt/shepard/timescaledb/` → container `/var/lib/postgres/data` | 252 MB on disk; `postgres` DB = 32 MB; hypertable `timeseries_data_points` = 18 MB (86 chunks, all compressed) | (shares host ZFS — same 400 GB free) | ✅ Baseline pristine. Projected steady-state 35–50 GB compressed. |
| 4 | Garage S3 (data) | docker volume `infrastructure_garage_data` → host `/var/lib/docker/volumes/infrastructure_garage_data/_data` | 22 MB on disk; bucket `shepard-files` = 38.5 MiB / 116 objects | (shares host ZFS — Garage reports 429.1 GB DataAvail / 536.9 GB capacity) | ✅ No quota set on bucket (operator decision §8.5 of readiness audit). |
| 5 | Garage S3 (meta) | docker volume `infrastructure_garage_meta` → host `/var/lib/docker/volumes/infrastructure_garage_meta/_data` | 194 KB | (shares host ZFS) | ✅ Negligible. |
| 6 | Neo4j data | bind `/opt/shepard/neo4j/data/` → container `/data` | 2.6 MB (databases) + 4.8 MB (transactions) = ~7.4 MB | (shares host ZFS) | ✅ Baseline pristine. Projected steady-state 2–5 GB. |
| 7 | MongoDB | bind `/opt/shepard/mongodb/db/` → container `/data/db` | 368 MB on disk; `database` DB = 330 MB | (shares host ZFS) | ✅ Baseline. Projected steady-state +5–10 GB after structured ingest. |
| 8 | Backend logs | bind `/opt/shepard/backend/logs/` | 19 MB | (shares host ZFS) | ✅ Negligible; will grow during ingest, rotate as needed. |

**Probes used:**
- `df -h /mnt/pve/unas` → 19T / 2.1T used / 17T free
- `df -h /` → 500G / 101G / 400G
- `du -sh /opt/shepard/timescaledb/` → 252M
- `du -sh /opt/shepard/neo4j/data/databases /opt/shepard/neo4j/data/transactions` → 2.6M + 4.8M
- `du -sh /opt/shepard/mongodb/db/` → 368M
- `du -sh /var/lib/docker/volumes/infrastructure_garage_data/_data` → 22M
- TimescaleDB: `psql -d postgres` → `pg_database_size`, `timescaledb_information.hypertables`
- MongoDB: `mongosh -u mongo -p secret --authenticationDatabase admin` → `listDatabases`
- Garage: `/garage stats`, `/garage bucket info shepard-files`

---

## §2 At-ingest projection (346 GB)

Compression hold-up assumption: ~7–10× on hypertable, matching the synthetic
baseline (113 channels, 1.17 M rows → 18 MB at 100 % compression — see
`db-baseline-post-mffd.md`).

```
PRE-INGEST (now):
  host ZFS used:        101 GB  (free 400 GB)
  NFS staging used:     2.1 TB  (free 17 TB)

AT PEAK (mid-ingest, before chunk-close):
  + raw ts-export/ on NFS staging:  +346 GB  → NFS free 16.7 TB (still vast)
  + active uncompressed chunk + heap on host ZFS: ~50 GB
  + write-ahead log + transient hypertable rows: ~30 GB
  → host ZFS free shrinks to ~320 GB.  COMFORTABLE.
  (only if DATA_DIR=/mnt/pve/unas/dump/ts-export/ — NOT host ZFS.)

POST-INGEST STEADY-STATE:
  TimescaleDB hypertable (compressed):   35–50 GB
  Garage S3 (file payloads):             20–40 GB
  Neo4j (metadata + Activity rows):       2–5 GB
  MongoDB (structured SD payloads):       5–10 GB
  Backend logs (rotated):                ~5 GB
  → +70–110 GB on host ZFS → 170–210 GB used → 290–330 GB free.
  ✅ Comfortable.
```

## §3 Capacity verdict

🔴 **Blocker if `DATA_DIR` points at host ZFS.** Set the importer env
`DATA_DIR=/mnt/pve/unas/dump/ts-export/` so the 346 GB raw stays on NFS
(17 TB free, no host-ZFS pressure). The readiness audit §1.4 calls this
out as the single hard requirement.

🟠 **Mid-ingest disk watch.** Add the kickoff-script disk-watch loop
(see `scripts/mffd-ingest-kickoff.sh` §5). Logs `df -h /` +
`du -sh /opt/shepard/timescaledb/` to `/tmp/mffd-disk-watch.log` every 30 min
for the duration of the import.

🟢 **Garage data + meta volumes** report 429.1 GB available cluster-wide
(single-node config) — well above the 20–40 GB projected payload growth.

🟢 **No surprises vs. the readiness audit §1.** All probed numbers match
the audit projections within rounding.

---

## §4 Garage bucket — no quota set

`shepard-files` bucket info confirms no quota set
(`max-size` / `max-objects` not present in output).

Per readiness audit §8.5, operator decision:
- (default) accept "fail when host disk full"
- (explicit) set `garage bucket set-quota --max-size 100G shepard-files`
  pre-kickoff → bucket returns 507 Insufficient Storage instead of
  cascading disk-full failures.

Recommendation: set the quota. One command, clean failure mode.

---

## §5 What changed since the 2026-05-31 readiness audit

The capacity audit § referenced these baseline numbers; this snapshot
reconciles each one against live state.

| Item | Audit value | Live (this snapshot) | Delta |
|---|---|---|---|
| NFS staging free | 17 TB | 17 TB (16.9) | ±0 |
| Host ZFS free | 401 GB | 400 GB | -1 GB (within rounding) |
| TimescaleDB size | 252 MB | 252 MB | ±0 |
| Neo4j data | 7.3 MB | 7.4 MB | +0.1 MB |
| MongoDB DB | 367 MB | 368 MB | +1 MB |
| Garage data | 21 MB | 22 MB | +1 MB |
| Garage meta | 193 KB | 194 KB | +1 KB |

Drift is within noise — substrates have been near-idle since the audit.

---

## §6 Pre-flight reminders relevant to capacity

1. Stage the 346 GB raw export on `/mnt/pve/unas/dump/ts-export/`, not
   on host ZFS.
2. Set `DATA_DIR=/mnt/pve/unas/dump/ts-export/` in importer env.
3. Optional: `garage bucket set-quota --max-size 100G shepard-files`
   (§4 above).
4. Add disk-watch cron to `tmux` (subsumed by the kickoff script).
5. Before kickoff: re-confirm `df -h /` shows ≥ 200 GB free (defensive
   threshold so transient ingest never burns more than half).

---

## §7 Snapshot metadata

- Probe time: 2026-05-31 17:43–17:45 UTC
- LXC container: CT 101 (`shepard.nuclide.systems`)
- Backend up + healthy: `infrastructure-backend-1 Up 40m (healthy)`
- Substrate containers up + reachable
- Readiness audit reference: `aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md`
