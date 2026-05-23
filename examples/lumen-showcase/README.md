# LUMEN-Inspired Hot-Fire Showcase Seed

> **Disclaimer.** This dataset is **synthetic** and **fake**. It is *loosely
> inspired* by the public description of DLR's
> [LUMEN](https://www.dlr.de/en/ra/research-transfer/projects/project-archive/liquid-upper-stage-demonstrator-engine-lumen)
> demonstrator at Lampoldshausen, but contains **no real DLR or LUMEN
> measurements**. Every numerical value in this directory is a deterministic
> output of `data/generate.py` (`numpy.random.default_rng(2024)`). Do not
> attribute any conclusion drawn from this data to the real LUMEN project.
>
> **DaMaST connection.** Per operator clarification 2026-05-23: this
> showcase is the **operational synthetic demonstrator of the DaMaST
> Vorhaben** (Data Management for Space Transport) — see
> [`aidocs/strategy/95-damast-space-transport-data-management.md`](../../aidocs/strategy/95-damast-space-transport-data-management.md).
> LUMEN is one of the two DaMaST use-cases (alongside STORT hyperschall);
> the synthetic dataset here exercises Shepard end-to-end against the
> shape DaMaST's real data will have when the Vorhaben 2026 demonstrator
> instantiates the `shepard + databus + MOSS` DataHub stack.

This is a self-contained showcase that exercises shepard end-to-end:
seven hot-fire test runs, ten sensor channels each at 100 Hz × 30 s,
phase-of-burn semantic annotations, two debrief lab-journal entries,
three Collection versions (best-effort), and an investigation-sub-tree
with predecessor/successor links that follow a fuel-turbopump vibration
anomaly through to its post-bearing-replacement re-test.

The companion notebook (`notebooks/anomaly-analysis.ipynb`) walks the
campaign, finds the spike with a rolling-median ± k·MAD detector,
confirms the fix on TR-006, and builds a selective RO-Crate export
request.

## Two scripts

This directory ships **two** import scripts that produce the same
entity tree against different targets. Both are deterministic via
`numpy.random.default_rng(2024)` so the timeseries are bit-identical;
both create a Collection with the same name. Pick exactly one — the
two are **not** interchangeable against the same database (running
both writes duplicate annotations and lab-journal entries).

- **`seed.py`** — for shepard built from the **dispatcher branch**
  (PR #1000 / PR #1001 merged). Exercises L5 API-key `validUntil`,
  R2 selective-export, P14 NDJSON ingest, P21 PATCH semantics, and
  the body-form `POST /collections/{id}/export`. Documented in this
  README.
- **`import_upstream.py`** — for **upstream-current** shepard at
  `gitlab.com/dlr-shepard/shepard` whose published `shepard-client`
  is the currently-deployed reality. Imports the same entity tree
  minus the post-PR features. **See [`README-upstream.md`](README-upstream.md)**
  for the full feature delta and operator invocation.

## What it ships

```
examples/lumen-showcase/
├── README.md                 # this file (operator instructions for seed.py)
├── README-upstream.md        # operator instructions for import_upstream.py
├── seed.py                   # idempotent dispatcher-branch importer
├── import_upstream.py        # idempotent upstream-current importer
├── _data_fallback.py         # rng(2024) fallback used by import_upstream.py
│                             # if data/generate.py is absent
├── data/
│   ├── generate.py           # deterministic synthetic data generator
│   ├── manifest.json         # generator output summary (post-generate)
│   ├── timeseries/           # 60 CSVs (10 channels × 6 fired runs)
│   ├── files/                # CAD stubs, test reports, photo stub
│   └── structured/           # one JSON run-log per run + schema sketch
└── notebooks/
    └── anomaly-analysis.ipynb
```

## Entity tree the seed creates

- Collection `LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)`.
  - DataObject `TR-001` … `TR-007` — siblings; `TR-{N+1}` predecessor
    is `TR-{N}`.
  - Child of `TR-004`: `Anomaly Investigation — TR-004 Fuel Turbopump`,
    `severity=HIGH`. Listed as an extra predecessor of TR-006.
  - One TimeseriesReference per fired run wrapping ten Timeseries
    (`pc_chamber`, `pc_nozzle`, `rpm_fuel_pump`, `rpm_lox_pump`,
    `mdot_fuel`, `mdot_lox`, `tc_chamber`, `vib_fuel_pump`,
    `vib_lox_pump`, `t_coolant_out`).
  - One FileReference per run (CAD stub + markdown test report).
  - One StructuredDataReference per run (operator's run-log JSON).
  - LabJournalEntries on TR-004 (debrief), the investigation
    (finding), and TR-006 (re-test).
  - Phase-of-burn semantic annotations on each fired run's
    TimeseriesReference (`precool` / `ignition` / `ramp_up` /
    `steady_state` / `throttle` / `shutdown` / `purge`) plus a
    `dlr:vibration-anomaly` annotation on TR-004.
  - Best-effort: three Collection versions `v0` / `v1` / `v2`. The
    versioning REST is gated behind a Quarkus feature toggle and may
    not be enabled on every dev stack — the seed logs `SKIP` if so.
  - Best-effort: two API keys (`campaign_lead_key`,
    `reviewer_key (intended validUntil=...)`). The shepard
    `ApiKeyIO` schema does not yet carry a `validUntil` field; the
    seed prints the intended expiry alongside the key name so an
    operator can wire it up manually once the field ships
    (see backlog item L5).

The three logical principal roles described in the showcase doc —
`campaign_lead` (owner), `analyst` (writer on the analysis sub-tree),
and `reviewer` (read-only) — are documented for the operator. Group-
based RBAC requires admin user-group setup beyond the API-key surface;
the seed makes the Collection PUBLIC so the showcase is explorable
without that wiring.

## Prerequisites

- Python 3.12 or newer.
- `numpy` (used by the generator and indirectly by shepard-client).
- The shepard Python client. From a clean venv:
  ```
  pip install --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple shepard-client==5.2.0
  pip install numpy
  ```
- A reachable shepard backend with a working API key. The seed has
  been written for the dev stack at `infrastructure/docker-compose.yml`.

### Optional (for the notebook only)

```
pip install pandas matplotlib jupyterlab
```

These are guarded — the seed itself does not import them. The notebook
does, and prints a friendly hint if they are missing.

## Generating the data

The CSVs / JSONs / file stubs are checked in; you only need to
regenerate if you edit `data/generate.py`:

```
python data/generate.py
```

The generator is **deterministic** (`numpy.random.default_rng(2024)`)
and re-running it produces bit-for-bit identical output. The TR-004
fuel-turbopump vibration anomaly always lands at peak ~12.7 g rms,
t ≈ 7.85 s. `data/manifest.json` records the seed and the anomaly
summary so a re-generation can be diffed.

## Running against a local stack

```
docker compose -f infrastructure/docker-compose.yml up -d
# Provision an API key via the UI, then:
python examples/lumen-showcase/seed.py \
    --host http://localhost:8080/shepard/api \
    --apikey <your-key>
```

To wipe and re-seed:

```
python examples/lumen-showcase/seed.py --host ... --apikey ... --reset
```

## Two-pass workflow (anomaly close)

The investigation DataObject's `closed_at` attribute is empty on the
first pass (mirroring "v1 — campaign complete, anomaly open"). After
publishing the investigation finding, re-run with `--close-anomaly`
to set a deterministic ISO timestamp; this matches the v2 export the
notebook walks through.

```
python examples/lumen-showcase/seed.py --host ... --apikey ... --close-anomaly
```

## Output

Each step logs one line in the form:

```
OK     <name> (<kind>, <id>)
SKIP   <name> (<kind>, <id>)             # already present, identical
UPDATE <name> (<kind>, <id>)             # already present, drifted -> patched
```

Re-running with no arguments after the first successful run will
produce only `SKIP` lines (idempotent).

## Determinism check (operator quick-verify)

```
$ python data/generate.py
OK generated 7 runs into .../examples/lumen-showcase/data
OK anomaly: TR-004 vib_fuel_pump peak 12.669 g rms at t=7.85 s (rng_seed=2024)
```

## Where the disclaimer lives

For audit, the "synthetic / inspired-by-LUMEN" note appears in three
places:

1. The Collection `description` attribute the seed creates (verbatim
   in `seed.py`'s `COLLECTION_DESCRIPTION`).
2. The opening paragraph of this README.
3. The opening paragraph of `docs/showcase.md`.

## Limits and known gaps

- Versioning REST is feature-toggled (`shepard.versioning`). On dev
  stacks where it's off the seed logs `SKIP` and continues; the
  notebook treats versions as optional surface.
- Group-based role assignment for `analyst` / `reviewer` is left to
  the operator (no admin user-group plumbing in the API key surface
  today).
- The selective-export body (`ExportSelection`) the notebook builds is
  the **proposed R2c shape**. The current `/collections/{id}/export`
  endpoint is GET-only, so the notebook will fall back to the GET form
  and document the redaction intent in the printed manifest. Once R2c
  ships, the notebook is ready.
