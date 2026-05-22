# MFFD AFP Manufacturing Campaign — Shepard Showcase

The shepard showcase for the **MFFD upper fuselage demonstrator** manufactured
at ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP — without autoclave.
The MFFD won the **JEC World Innovation Award 2025 (Aerospace — Parts)**.

This showcase imports the **real DLR cube3 data** via LIVE API pull. The local on-disk
export at `raw-data/mffd-data/` is **shape reference only — incomplete**.

## Two artefacts, two purposes

| Artefact | Path | Purpose |
|---|---|---|
| **Live cube3 source** | `backend.bt-au-cube3.intra.dlr.de` (DLR intranet) | The actual dataset for the import. Real TS payloads accessible via `/export?csv_format=COLUMN`. |
| **On-disk drop (shape reference)** | `examples/mffd-showcase/raw-data/mffd-data/` (~15 GB, gitignored) | Snapshot of the export structure for reading offline. **TS payloads are 0-byte placeholders** — the exporter v1.2 didn't include them. NOT the import source. Useful for: understanding the layout, checking metadata shapes, validating the script offline. |

## Live cube3 source — what's there

| Collection | DataObjects | File refs | Structured refs | TS refs |
|---|---|---|---|---|
| `48297` (mffd-tapelaying) | 5012 (28 PlyGroup + 77 Ply + 3985 Track + scaffold) | 23,328 | 0 | 4627 (with real payloads via `/export`) |
| `163811` (mffd-bridgewelding) | 3371 (24 Frame, ~22 AF/Frame, 1–7 ProcessData/AF) | counts in references/ | StepMetaProcessStep + StepMetaProcessExecution | 0 |

## Why the on-disk drop is not the import source

The cube3 exporter v1.2 skipped TS payloads — `raw-data/mffd-data/mffd-tapelaying/references/`
has 4627 × 0-byte `ts-*.csv` files. Without TS payloads no Trace3D thermal-trail rendering,
no ODIX channel analysis, no real showcase demonstration. The live cube3 API DOES have the
TS payloads, accessed per-ref via `GET /timeseriesReferences/{id}/export?csv_format=COLUMN`
(v15 Bug H fix — v14 used `WIDE` which is invalid on the v5.4.0 source enum). v15 pulls live
from cube3 during the import.

## Export shape (for reference)

```
<process>/
  data-objects/   do-<old-id>.json metadata
  references/
    file-<id>.json + file-<id>/<oid>   FileReferences (with payloads on cube3 + disk)
    sd-<id>.json   + sd-<id>/<oid>.json StructuredDataReferences
    ts-<id>.csv    + ts-<id>.json       TimeseriesReferences  (csv 0-byte on disk; live on cube3)
  annotations.json
  lab-journal/    (empty — exporter ran with skip_lab_journals)
  manifest.json
```

## Sister docs

- [`SHOWCASE.md`](./SHOWCASE.md) — narrative for what the substrate-split chain
  demonstrates running against this data (ODIX, F(AI)²R, regulatory framing)
- [`SHOWCASE_ANALYSIS.md`](./SHOWCASE_ANALYSIS.md) — analytical findings
- [`ontology/mffd-process.ttl`](./ontology/mffd-process.ttl) — MFFD domain ontology
- [`scripts/`](./scripts/) — import + extract + analysis scripts
- [`aidocs/integrations/93-mffd-import-v15-requirements.md`](../../aidocs/integrations/93-mffd-import-v15-requirements.md) —
  v15 import script requirements (4 workers, exp backoff, redeploy-resilient,
  presigned-URL uploads, semanticAnnotation provenance writeback)

## Usage

The current import path is **live cube3 → nuclide.systems** via v15 (in progress;
spec in `aidocs/integrations/93`). v15 runs from cube@bt-au-cube-mig (the only host
with both DLR intranet + nuclide reachability). v14 lives at `scripts/mffd-dropbox-import.py`
and is being superseded.

For the v15 sequence: see `aidocs/integrations/93 §14` (Sequencing).

## What's NOT here anymore

- **`seed.py`** (synthetic AFP-Q1-anomaly seeder) — removed 2026-05-22. The Q1 ply-5
  consolidation-force-drop + TCP temp-spike + NDT FAIL → Rework → NDT recheck PASS
  narrative was a synthetic showcase fiction, NOT a feature of the real cube3 data.
  Real data has its own (empirical) anomaly profile; ODIX analysis finds whatever
  is actually there, post-import.
- **`data/`** directory contents (`afp-q1-*.csv`, `ndt-q1-report.json`, etc.) — synthetic
  data that fed `seed.py`. The directory is now orphaned of its consumer; the gitignored
  contents can be cleaned at the operator's discretion. `data/generate.py` is kept for
  reference (it shows the shape of channels the AFP robot produces, useful when reading
  the real export's `ts-*.json` metadata).
