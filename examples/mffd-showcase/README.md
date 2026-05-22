# MFFD AFP Manufacturing Campaign — Shepard Showcase

The shepard showcase for the **MFFD upper fuselage demonstrator** manufactured
at ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP — without autoclave.
The MFFD won the **JEC World Innovation Award 2025 (Aerospace — Parts)**.

This showcase ingests the **real DLR cube3 export** from on-disk drop:

```
examples/mffd-showcase/raw-data/mffd-data/    ← real cube3 export (gitignored, ~15 GB)
  ├── mffd-tapelaying/         — 5012 DataObjects (PlyGroup → Ply → Track)
  ├── mffd-framewelding/       — 3371 DataObjects (Frame → AF → Execution → ProcessData)
  ├── mffd-confluence-space-export/  — 116 wiki pages + 980 attachments
  └── cell/                    — workcell models
```

Each subtree carries:

```
<process>/
  data-objects/   do-<old-id>.json metadata
  references/
    file-<id>.json + file-<id>/<oid>   FileReferences (with payloads)
    sd-<id>.json   + sd-<id>/<oid>.json StructuredDataReferences
    ts-<id>.csv    + ts-<id>.json       TimeseriesReferences  (⚠ csv currently empty per export)
  annotations.json
  lab-journal/    (empty — exporter ran with skip_lab_journals)
  manifest.json
```

## Data shape on disk

| Subtree | DataObjects | File refs | Structured refs | TS refs | TS payloads |
|---|---|---|---|---|---|
| mffd-tapelaying | 5012 (28 PlyGroup + 77 Ply + 3985 Track + scaffold) | 23,328 | 0 | 4627 (json meta) | **0 bytes each — exporter wrote placeholders only** |
| mffd-framewelding | 3371 (24 Frame → ~22 AF/Frame → 1–7 ProcessData/AF) | counts in references/ | yes (StepMetaProcessStep, StepMetaProcessExecution) | 0 | n/a |

**Known gap — empty TS payloads:** the cube3 exporter version 1.2 wrote TS placeholder
files (4627 × 0-byte CSVs in tapelaying) but no actual point data. A re-export with TS
payloads enabled is needed for full-fidelity TS work (Trace3D thermal-trail rendering,
ODIX channel analysis). Metadata + lineage + files + structured data are intact and
can be imported now.

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

The current import path is **on-disk export → nuclide.systems** via v15 (in progress;
spec in `aidocs/integrations/93`). v14 lives at `scripts/mffd-dropbox-import.py` and is
being superseded.

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
