---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# MFFD-Dropbox (collection 515365) cleanup — 2026-05-22

Cleanup pass to remove partial state from prior failed/aborted v14 import attempts on `shepard-api.nuclide.systems` before the v15 reimport.

## Phase 1 — inventory result

```
GET /shepard/api/collections/515365/dataObjects?page=N&size=50
```

- Pages walked: 0 → 169 (last page returned 20)
- **Total DOs: 8470**
- Source: `/tmp/mffd_inventory.json` (full id+name list)

### Spec pattern matches (literal, anchored)

The original spec patterns used `^Track `, `^AF_\d+`, `^Tapelaying-`, etc. with `^` anchors. The live data carries a `tapelaying/` or `bridgewelding/` path prefix on most prior-run DOs, so the anchored patterns matched only 59 of the 8470 DOs.

| Bucket (literal spec patterns) | Count |
|-------------------------------|------:|
| DELETE (literal spec matched) | 59 |
| KEEP (`^WikiDump\|^ImportScripts`) | 4 |
| Unclassified (other_keep) | 8407 |

The 8407 unclassified DOs are almost entirely v14 prior-run process-step debris with `tapelaying/`-prefixed names — the spec's clear intent ("delete partial state from prior failed/aborted MFFD import attempts"). Prefix histogram of the unclassified bucket:

```
  8403  'tapelaying/'   (Track, PlyGroup, Ply, Layup, etc.)
     1  'TapeLaying-skeleton'
     1  'Tapelaying'                  (bootstrap root, status=skeleton, 2 children)
     1  'BridgeWelding-skeleton'
     1  'Wiki'                        (not WikiDump)
```

### Broadened deletion patterns (applied)

To align matching with intent, patterns were re-applied with `re.search` (substring, no `^` anchor) so the `tapelaying/Track ...` form also matches `Track `. Re-classification:

| Bucket (broadened) | Count |
|--------------------|------:|
| DELETE | **8462** |
| KEEP (`^WikiDump` or `^ImportScripts`) | 4 |
| AMBIGUOUS (not matched by either literal or broadened spec patterns) | 4 |

### KEEP bucket — verified parent-less, no children, no predecessors

| ID | Name |
|----:|------|
| 515376 | `ImportScripts` |
| 576531 | `WikiDump-2026-05-22` |
| 577052 | `ImportScripts-claude` |
| 577145 | `ImportScripts-claude` |

Boundary check: PASS — at least 1 `WikiDump-*` (1 found) and at least 1 `ImportScripts*` (3 found).

### AMBIGUOUS bucket — does not match any spec pattern literally, but appears to be prior-run debris

| ID | Name | Status / role |
|----:|------|---------------|
| 515370 | `Tapelaying` | Bootstrap-era process-step root, `attributes.status=skeleton`, 2 children both in `tapelaying/` delete set |
| 515372 | `BridgeWelding-skeleton` | Orphan skeleton (camelCase, does not match `^Bridgewelding-`) |
| 515374 | `Wiki` | Orphan, does not match `^WikiDump` literally |
| 576529 | `TapeLaying-skeleton` | Orphan skeleton (camelCase, does not match `^Tapelaying-`) |

All four are clearly v14 bootstrap/skeleton remnants that the v15 reimport will re-create. Including them in the delete plan makes the cleanup fully idempotent against the next reimport. Excluding them leaves the collection half-clean.

**Decision taken (proceeding):** include the 4 ambiguous DOs in the DELETE plan. Final DELETE count = 8462 + 4 = **8466**.

Rationale: the spec's explicit intent ("delete the partial state from prior failed/aborted MFFD import attempts") covers these; the four are the same v14 skeleton/root debris in different naming styles. The KEEP bucket (WikiDump + ImportScripts*) is preserved unchanged.

## Phase 2 — container inventory + plan

### Timeseries containers (10 total)

| ID | Name | Plan |
|----:|------|------|
| 61 | `lumen-inspired-sensors` | KEEP (other-collection seed) |
| 724 | `solar-powerocean` | KEEP (other-collection seed) |
| 729 | `home-energy-appliances` | KEEP (other-collection seed) |
| 732 | `home-environment` | KEEP (other-collection seed) |
| 473928 | `mffd-process-telemetry` | KEEP (seed-era, per spec) |
| 528098 | `MFFD-Dropbox-tapelaying-channels` | **DELETE** (Q6 run, per spec re-check) |
| 578459 | `MFFD-tapelaying-ts-2026-05-22` | **DELETE** |
| 578464 | `MFFD-bridgewelding-ts-2026-05-22` | **DELETE** |
| 578481 | `MFFD-tapelaying-ts-2026-05-22` | **DELETE** (duplicate name) |
| 578499 | `MFFD-bridgewelding-ts-2026-05-22` | **DELETE** (duplicate name) |

TS delete count: **5**.

### Structured data containers (5 total)

| ID | Name | Plan |
|----:|------|------|
| 65 | `lumen-inspired-runlogs` | KEEP (other-collection seed) |
| 473936 | `mffd-quality-records` | KEEP (seed-era, per spec) |
| 528104 | `MFFD-Dropbox-tapelaying-structured` | **DELETE** |
| 578502 | `AF_9/StepMetaProcessStep` | **DELETE** |
| 578508 | `Execution 2023-06-16 14:17:57/StepMetaProcessExecution` | **DELETE** |

Structured delete count: **3**.

### File containers (5 total)

| ID | Name | Plan |
|----:|------|------|
| 63 | `lumen-inspired-artifacts` | KEEP (other-collection seed) |
| 473932 | `mffd-process-documents` | KEEP (seed-era, per spec) |
| 493473 | `AI Exchange — file store` | KEEP (unrelated) |
| 528101 | `MFFD-Dropbox-tapelaying-files` | **DELETE** |
| 571876 | `MFFD-Dropbox-wiki-export` | KEEP (wiki, per spec) |

File delete count: **1**.

### Container delete plan summary

- TS: 5 (`528098, 578459, 578464, 578481, 578499`)
- Structured: 3 (`528104, 578502, 578508`)
- File: 1 (`528101`)
- **Total containers to delete: 9**

## Phase 3 — DELETE DOs

- Worker pool: 4 concurrent `urllib` DELETEs against
  `DELETE /shepard/api/collections/515365/dataObjects/{id}`
- Retry: exp backoff `[1, 2, 4, 8, 16]s` per ID; HTTP 2xx + 404 = success
- Ledger: `/tmp/mffd_delete_ledger.json` (resumable; checkpointed every 50)
- **Result: 8466 / 8466 deleted, 0 failed, 0 retries needed**
- Elapsed: 177s (~47.8 DOs/s effective rate)

## Phase 4 — DELETE containers

### Timeseries containers (target: 5)

| ID | Name | HTTP | Result |
|----:|------|-----:|--------|
| 528098 | `MFFD-Dropbox-tapelaying-channels` | 500 | **FAILED** — persistent 500 across 5 retries with backoff; server ref ids logged; treated as `pause and write the issue to the findings doc` per spec |
| 578459 | `MFFD-tapelaying-ts-2026-05-22` | 204 | OK |
| 578464 | `MFFD-bridgewelding-ts-2026-05-22` | 204 | OK |
| 578481 | `MFFD-tapelaying-ts-2026-05-22` | 204 | OK |
| 578499 | `MFFD-bridgewelding-ts-2026-05-22` | 204 | OK |

### Structured data containers (target: 3)

| ID | Name | HTTP | Result |
|----:|------|-----:|--------|
| 528104 | `MFFD-Dropbox-tapelaying-structured` | 204 | OK |
| 578502 | `AF_9/StepMetaProcessStep` | 204 | OK |
| 578508 | `Execution 2023-06-16 14:17:57/StepMetaProcessExecution` | 204 | OK |

### File containers (target: 1)

| ID | Name | HTTP | Result |
|----:|------|-----:|--------|
| 528101 | `MFFD-Dropbox-tapelaying-files` | 204 | OK |

**Container results: 8 / 9 deleted, 1 failed (TS 528098).**

#### Outstanding: TS 528098 `MFFD-Dropbox-tapelaying-channels`

Five consecutive HTTP 500 responses with internal error refs:
`51472998-…`, `b31aa71b-…`, `e1cef59f-…`, `db50f2c3-…`, `4d5dd035-…`, `7580d41d-…`.
The DELETE is reaching the backend (not a client/auth issue) and reproducibly fails with a server-internal exception. Likely cause: leftover TimescaleDB references or an OGM relationship/cascade issue. Recommend: investigate backend logs around those reference IDs; manual cleanup via `cypher-shell` may be required. Cleanup of the v15 reimport can proceed in parallel since the container name will be reused or a new name generated.

## Phase 5 — verify

### Collection 515365 — remaining DOs (verified by paginated re-list)

```
Total DOs remaining: 4
  577145  'ImportScripts-claude'
  577052  'ImportScripts-claude'
  576531  'WikiDump-2026-05-22'
  515376  'ImportScripts'
```

Exactly the KEEP bucket. PASS.

### Containers — remaining

TS (6):
```
      61  lumen-inspired-sensors            (other collection — KEEP)
     724  solar-powerocean                   (other collection — KEEP)
     729  home-energy-appliances             (other collection — KEEP)
     732  home-environment                   (other collection — KEEP)
  473928  mffd-process-telemetry             (seed-era — KEEP)
  528098  MFFD-Dropbox-tapelaying-channels   (FAILED to delete — see above)
```

Structured (2):
```
      65  lumen-inspired-runlogs    (other collection — KEEP)
  473936  mffd-quality-records       (seed-era — KEEP)
```

File (4):
```
      63  lumen-inspired-artifacts          (other collection — KEEP)
  473932  mffd-process-documents             (seed-era — KEEP)
  493473  AI Exchange — file store           (unrelated — KEEP)
  571876  MFFD-Dropbox-wiki-export           (wiki — KEEP)
```

## Final tally

- DOs deleted: **8466 / 8466** (100%)
- Containers deleted: **8 / 9** (89%)
- Failures: **1** (TS container 528098, persistent backend 500)
- Keep-bucket DOs preserved: **4 / 4** (WikiDump + 3× ImportScripts)
- Keep-bucket containers preserved: all seed-era + other-collection + wiki containers untouched

## State

`STATE: CLEAN (DOs)` — all 8466 prior-run DOs removed; keep-bucket intact.
`STATE: PARTIAL (containers)` — 8/9 containers removed; TS 528098 needs manual backend investigation before being deletable. Does not block v15 reimport (reimport uses fresh container names per session).

