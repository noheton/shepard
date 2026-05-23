---
layout: default
title: Cross-instance import (reference)
permalink: /reference/import/
audience: user
---
# Cross-instance import — `mffd-import-v15`

This page documents `examples/mffd-showcase/scripts/mffd-import-v15.py`,
the reference script for pulling DataObjects + files + timeseries
+ structured-data from one shepard instance (or a local data
directory) into another, with full provenance writeback.

It is the production-shape companion to the lighter, single-purpose
import samples under `examples/`. v15 is the live ingest path for
the MFFD showcase (`live cube3 → nuclide`), proven over the
2026-05-22 import campaign.

## When to use it

- You have a source shepard instance behind an authenticated
  network boundary (DLR intranet, partner site, cube3 fixture)
  and want to mirror selected Collections into a destination
  instance you control.
- You want every byte transferred to carry **shepard provenance**
  on the destination — predecessor / successor chain preserved,
  source IDs captured as attributes, PROV-O `:Activity` rows
  written for each upload.
- You want the import itself to be **resumable** across redeploy,
  JWT expiry, and operator interrupt.
- You want **presigned-URL upload** straight into the destination's
  S3 backend (Garage) without bytes flowing through the destination
  JVM.

If none of those apply, the simpler `examples/` scripts will be a
better starting point.

## Prerequisites

- Python `>=3.11`.
- `uv` (or `pip`) to satisfy the inline PEP 723 deps
  (`requests`, `tqdm`).
- A reachable destination shepard with the S3 file-storage
  adapter active. See
  [Garage S3 sidecar activation runbook]({{ '/ops/garage-activation-runbook' | relative_url }})
  and
  [GridFS → S3 migration]({{ '/ops/migrate-gridfs-to-s3' | relative_url }}).
- An API key (X-API-KEY) or Keycloak bearer token on the
  destination, with write permission on the target Collection.
- For cross-instance source-pull: a JWT or API key on the source
  instance.

The script will refuse to start (exit code 4) if the destination
is still on GridFS — the presigned-URL flow needs S3.

## The three modes

```text
--bootstrap    One-shot: creates the destination Collection, the
               skeleton ImportScripts DataObject, uploads the
               script itself as a provenance artefact, and takes
               a t=0 snapshot capturing the empty-state baseline.

SOURCE MODE    Default. Traverses the source shepard's
               Collections (set SOURCE_SHEPARD_URL +
               SOURCE_SHEPARD_API_KEY + SOURCE_*_COLL_ID env
               vars), pulls every DataObject + file + TS
               channel + structured-data blob, re-uploads to
               the destination.

LOCAL MODE     Fallback when no SOURCE_* env vars are set.
               Reads files from DATA_DIR subdirectories.
```

## Invocation

### Step 1 — bootstrap (run once on the destination)

```bash
SHEPARD_URL=https://shepard.example.dlr.de \
SHEPARD_API_KEY=<dest-key> \
uv run python examples/mffd-showcase/scripts/mffd-import-v15.py --bootstrap
```

This creates the destination Collection, uploads the script as a
self-describing provenance artefact, and writes the initial
snapshot. The Collection's appId is printed to stdout — record it.

### Step 2 — full import (run on the network side that can see the source)

```bash
SHEPARD_URL=https://shepard.example.dlr.de \
SHEPARD_API_KEY=<dest-key> \
SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \
SOURCE_SHEPARD_API_KEY=<source-jwt> \
SOURCE_TAPELAYING_COLL_ID=48297 \
SOURCE_BRIDGEWELDING_COLL_ID=163811 \
SESSION_ID=2026-05-22-Q1 \
uv run python examples/mffd-showcase/scripts/mffd-import-v15.py
```

Or, after bootstrap, fetch the latest script from the destination
and run it via the bundled shell helper:

```bash
bash examples/mffd-showcase/scripts/run-mffd-import.sh
```

The helper downloads the script from the destination's
`ImportScripts` DataObject (so source-side operators don't need
the repo) and then `uv run`s it.

### Dry-run

```bash
uv run python examples/mffd-showcase/scripts/mffd-import-v15.py --dry-run
```

Prints the plan; makes no API calls.

## Environment variables

| Variable | Meaning |
|---|---|
| `SHEPARD_URL` | Destination instance base URL. Default `https://shepard.nuclide.systems`. |
| `SHEPARD_API_KEY` | Destination auth — `X-API-KEY` header. Use for shepard-minted JWTs. |
| `SHEPARD_BEARER_TOKEN` | Destination auth — `Authorization: Bearer`. Use for Keycloak tokens. |
| `SOURCE_SHEPARD_URL` | Source instance base URL for cross-instance pull. Defaults to `SHEPARD_URL`. |
| `SOURCE_SHEPARD_API_KEY` | Source auth (JWT or API key). |
| `SOURCE_TAPELAYING_COLL_ID` | Source numeric Collection id (legacy v1) to mirror. |
| `SOURCE_BRIDGEWELDING_COLL_ID` | Same, second source Collection. |
| `SESSION_ID` | Operator-supplied stamp included on every upload's attributes — used to filter "what landed in this session". |
| `DATA_DIR` | Local-mode root directory. Ignored when `SOURCE_*` are set. |

## The agentic 4-phase workflow

The script implements a **human + AI-in-the-loop** import shape so an
operator can cancel or pause before the bulk transfer commits.

1. **Bootstrap** — destination Collection + skeleton DataObjects +
   script-as-provenance + t=0 snapshot. Idempotent; safe to re-run.
2. **Shell fetch** — `run-mffd-import.sh` downloads the script from
   the destination, then runs it. Lets the source-side operator
   run a script that's pinned to the destination's snapshot of the
   import logic.
3. **Warmup probe** — one payload of each reference type is
   uploaded. Claude (or the operator) reviews the result in the
   destination UI; the operator then `PATCH`es the Collection's
   `import_ready` attribute to `"true"`. The script blocks until
   that flip — no bulk transfer begins until a human or AI agent
   has visually verified the destination is correctly receiving
   data.
4. **Full import** — traverses both source Collections, downloads
   each DataObject + file + TS channel + structured-data blob,
   re-uploads to the destination. Every transfer is checkpointed.

A **post-import snapshot** captures the final destination state with
a label that embeds the current Collection name, so future renames
remain traceable.

## Provenance writeback

Every transferred entity carries:

- `source_shepard_url`, `source_id`, `source_app_id` attributes —
  the original location.
- `source_created`, `source_modified` attributes — the original
  timestamps (file upload timestamps + DataObject `creationDate`
  on the destination always reflect the **import time**, not the
  original measurement time; the source values are preserved as
  attributes per the script's TIMESTAMP NOTE).
- A `semanticAnnotation` batch writeback tagging the entity with
  the V61 predicates registered for the MFFD import (registered
  by `V61` Neo4j migration before the import runs).
- `X-AI-Agent` header on every destination call —
  `claude-opus-4-7 actedOnBehalfOf fkrebs@nucli.de` (or the
  current operator) — so the destination's
  `:Activity` rows from `ProvenanceCaptureFilter` (PROV1a)
  attribute the work to the agent.

## ETA + log-as-proof

The script publishes **liveness signals** so an operator (or a
chat agent watching the destination) can see progress without
ssh'ing to the host:

- Every 30 s, the destination Collection's attributes are PATCHed
  with an `import_eta_seconds` value.
- Every 5 min, the script's full stdout/stderr log is re-uploaded
  to the `ImportScripts` DataObject as a new file version (PV1a's
  payload-versioning trail captures each one). After the run, the
  Collection's snapshot manifest carries the full ordered log set
  as a reproducible artefact.

## JWT pause behaviour

Source-instance JWTs typically expire after 1 hour. v15 handles
this without losing state:

1. On HTTP 401 from the source, the script SIGSTOPs every worker
   thread and prints `[jwt-pause] paused; export SOURCE_SHEPARD_API_KEY
   then kill -SIGCONT <pid>`.
2. The operator obtains a fresh JWT from the source UI, exports
   it into the parent shell, then `kill -SIGCONT <pid>`.
3. The script resumes from the last checkpoint. Already-uploaded
   work isn't re-uploaded (the state file tracks per-entity
   completion).

## Presigned-URL upload flow

For each file payload the script performs a **3-step direct upload**
into the destination's Garage backend:

1. `POST /v2/file-containers/{id}/upload-url` →
   `{uploadUrl, oid, expiresAt}` (15-minute TTL).
2. `PUT <uploadUrl>` of the file bytes straight to Garage — no
   Authorization header on the presigned PUT.
3. `POST /v2/file-containers/{id}/upload-url/commit` with
   `{oid, fileName, fileSize}` to register the file in shepard.

The pre-flight probe (`POST /v2/file-containers/{x}/upload-url`
against a dummy container) detects a GridFS-only destination and
exits with code 4 before any work starts. See
[file storage]({{ '/reference/file-storage' | relative_url }})
for the wire shapes.

## Concurrency primitives (C7)

- **4-worker producer/consumer pool** with a bounded queue of 256
  in-flight payloads.
- **Exponential backoff with jitter** on every retryable HTTP
  error (500/502/503/504 + network). Backoff caps at 30 s.
- **Single state-writer thread** owns `<dest>.state.json`.
  Writes are atomic (tmp file + `fsync()` + `rename()`). No
  worker thread touches the state file directly.
- **Redeploy-resilient long-wait**: a 503 / connection-refused
  burst (the destination's compose stack restarting under the
  operator's hand) triggers a 5-minute back-off rather than
  cascading worker failure.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Import completed successfully. |
| 1 | Generic failure — network, unrecoverable HTTP error. |
| 2 | Authentication failure — no JWT or all retries exhausted on 401. |
| 3 | Source unreachable — no DLR intranet, or the URL doesn't resolve. |
| 4 | Garage S3 backend not active on the destination — pre-flight probe returned a GridFS error. Activate Garage first per the runbook. |
| 5 | Operator interrupt (SIGINT received) — state persisted; restart will resume. |

## Operational gotchas

- The pre-flight probe is **not optional**. Running v15 against a
  GridFS-only destination fails fast (exit 4); do not bypass.
- `--bootstrap` must complete on the destination before any
  source-side run. The shell fetch helper assumes the script is
  reachable as a file reference under the destination's
  `ImportScripts` DataObject.
- The X-AI-Agent header is **not optional** either — the
  destination's audit trail relies on it. Run the script under
  your own operator identity (`SHEPARD_API_KEY`) and let the
  header attribute the work to the agent on your behalf.
- The `import_ready` warmup gate is intentional. Don't bypass it
  — the warmup probe is the only opportunity to catch a wire-shape
  mismatch before bulk transfer.

## See also

- [Garage S3 sidecar activation runbook]({{ '/ops/garage-activation-runbook' | relative_url }})
- [GridFS → S3 migration runbook]({{ '/ops/migrate-gridfs-to-s3' | relative_url }})
- [File storage reference]({{ '/reference/file-storage' | relative_url }})
- [Plugins reference]({{ '/reference/plugins' | relative_url }})
- [Sidecars SPI reference]({{ '/reference/sidecars' | relative_url }})
- `examples/mffd-showcase/scripts/mffd-import-v15.py` — the script
  itself. Header comment is the authoritative spec.
- `aidocs/integrations/93-mffd-import-v15-requirements.md` — the
  full requirements + design doc.
