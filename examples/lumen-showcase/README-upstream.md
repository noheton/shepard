# LUMEN-Inspired Showcase — upstream-current importer

Operator instructions for `import_upstream.py`. This script is the
**parallel counterpart** to `seed.py` and targets the upstream
`shepard-client` published on GitLab today, *without* the post-PR-#1000 /
post-PR-#1001 endpoints.

## Disclaimer

The data is **synthetic** and only loosely inspired by DLR's LUMEN test
campaign. No proprietary measurements, geometry, or operating points are
reproduced. The Collection name carries the "(synthetic)" marker; the
description carries it again. Treat any number you see as illustrative.

## What this script imports

| Entity | Count | Notes |
|---|---|---|
| Collection | 1 | "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)" |
| DataObject (test run) | 7 | TR-001..TR-007, top-level under the Collection |
| DataObject (analysis) | 1 | "TR-004 Anomaly Investigation", child + predecessor of TR-004 |
| TimeseriesContainer | 7 | one per run, named `lumen-TR-NNN-timeseriesContainer` |
| FileContainer | 7 | one per run; carries the run report `.md` |
| StructuredDataContainer | 7 | one per run; carries a one-doc run header |
| TimeseriesReference | 7 | per-run `vib_fuel_pump` window |
| FileReference | 7 | per-run report |
| StructuredDataReference | 7 | per-run header |
| LabJournalEntry | 2 | TR-004 debrief, TR-006 fix-confirmation |
| SemanticAnnotation | 29 | 4 phase-of-burn × 7 runs + TR-004 anomaly placeholder |
| Collection Version | 3 | v0 (pre-import marker), v1 (after 7 runs), v2 (after analysis) |
| Permissions roles | 3 | campaign_lead (manager), analyst (writer), reviewer (reader) |
| API keys | 3 | one per role; **no `validUntil`** — see below |

## What this script does NOT exercise

These features are landing on the dispatcher branch (PR #1000 / PR #1001)
and are not in the published `shepard-client` today:

- **L5 — API-key `validUntil`.** Reviewer key is created without an expiry.
  On `seed.py` (dispatcher branch) the reviewer key gets `validUntil=now+90d`.
- **R2 — selective export with body.** This script does not call the new
  `POST /collections/{id}/export` body shape. The operator can still
  trigger the legacy `GET /collections/{id}/export` against the imported
  Collection for a baseline RO-Crate.
- **P3 — migration progress.** No reads of `MigrationProgress` /
  `/temp/migrations/state`.
- **P14 — NDJSON ingest.** Timeseries are uploaded as legacy
  `application/json` arrays batched at 1000 points per request.
- **P21 — `@PATCH` semantics.** Updates use full PUT (e.g. for permissions).
- **A1c — spatial 503 on PostGIS down.** The script does not depend on the
  fail-fast behaviour `@RequiresDatabase` adds.
- **A1b — split health probes.** Only the legacy single `/healthz` exists
  upstream; this script does not call `/healthz/started` etc.
- **`shepard.permissions.cache.*`** config knobs — none referenced.

## Where the synthetic data comes from

The same generator the sibling `seed.py` uses, with
`numpy.random.default_rng(2024)`. The seven runs are drawn in TR-001..
TR-007 order from a single rng instance — re-ordering breaks bit
equivalence. TR-004's `vib_fuel_pump` channel carries the vibration
anomaly: a +6.0 g gaussian spike centred at t = 32.5 s, σ = 0.4 s. TR-006
is the re-fly with the fix in place; the spike does not appear.

If `examples/lumen-showcase/data/generate.py` is present (sibling agent has
landed), the importer reads its CSVs from `data/timeseries/TR-NNN.csv`. If
not, the bundled `_data_fallback.py` regenerates them in-place using the
same spec. The fallback is by-design bit-identical to the sibling
generator's output — both produce six-decimal CSVs with the column order
`t,thrust_kN,p_chamber_bar,t_throat_K,vib_fuel_pump`.

## Operator invocation

### 1. Against an upstream public release (local docker-compose)

```sh
# Bring shepard up via infrastructure-local/.
# Mint a manager API key for your user via the frontend (Settings → API keys).
pip install shepard-client \
  --index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple
pip install numpy  # only needed if you use the data fallback

python examples/lumen-showcase/import_upstream.py \
  --host http://localhost:8081/shepard/api \
  --apikey "$SHEPARD_APIKEY"
```

### 2. Against a remote upstream instance

```sh
python examples/lumen-showcase/import_upstream.py \
  --host https://shepard.example.org/shepard/api \
  --apikey "$SHEPARD_APIKEY"
```

### 3. Wipe-and-re-seed

```sh
python examples/lumen-showcase/import_upstream.py \
  --host http://localhost:8081/shepard/api \
  --apikey "$SHEPARD_APIKEY" \
  --reset
```

`--reset` deletes the Collection and the seven per-run containers (matched
by name) before re-importing. Runs that the script's own log line marks
`SKIP` are already present and will not be modified.

## Known limitations

The upstream API surface lacks a few things the dispatcher-branch surface
adds. Concretely: there is no native expiry on API keys (operators must
rotate manually); selective RO-Crate export is the legacy whole-Collection
GET only, so the export will include the lab-journal bodies verbatim; and
the `versions` endpoint is feature-toggle gated on the backend — if the
target instance has versioning disabled, the three Collection-version
calls log `SKIP (versioning toggle off)` and the rest of the import
proceeds normally.

## When to use the dispatcher-branch `seed.py` instead

If your target shepard is built from the **dispatcher branch** (PR #1000
and PR #1001 merged) and you want to demonstrate the post-merge features —
L5 API-key `validUntil`, R2 selective-export, the post-merge endpoint
shapes — run `seed.py` instead. Both scripts produce a Collection with
the same name and the same seven runs, and both are deterministic via
`rng(2024)`, so the timeseries are bit-identical. The dispatcher-branch
script just exercises more of the API.

## Version pin note for maintainers

`import_upstream.py` is written against `shepard-client==5.2.0` (the pin
in `clients/tests/python/requirements.txt` at the time of writing). It
uses only the REST verbs/paths declared in the upstream OpenAPI on
`main` and issues raw `urllib` calls for transport — so the script
itself does not depend on which exact operationId names a given
`shepard-client` version generates. **However**, if the deployed shepard
predates the introduction of the per-reference `semanticAnnotations`
sub-resource (`/collections/{id}/dataObjects/{do}/references/{r}/semanticAnnotations`),
the annotations step will 404. In that case pin the deployed backend to
≥ shepard 4.0 or comment out the annotations block; the rest of the
importer is independent.
