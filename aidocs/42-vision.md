# shepard — Vision (for researchers)

**Status.** **Live.** Kept current as features land. The doc you'd
hand a researcher who asks "what is shepard, and should I use it?"

**Snapshot date.** 2026-05-08.
**Audience.** Researchers, group leads, principal investigators.
Not operators (they have `docs/admin.md`), not developers (they have
the rest of `aidocs/`).
**Standing rule.** Per `CLAUDE.md`, this doc updates in the same PR
as any user-visible feature change. A stale vision is worse than no
vision.

---

## In one paragraph

shepard is a **research-data platform** for the kinds of mixed,
heterogeneous datasets a real DLR experiment produces — sensor
timeseries, CAD files, lab-journal notes, photos, geometry,
structured run-logs, semantic annotations — all under one
permission-aware, exportable umbrella. You put your campaign in,
your collaborators see what they're allowed to see, and when it's
time to publish you get a citable RO-Crate ZIP back. It is not a
files-and-folders share, and it is not a database; it is the
**data context** layer in between.

## Who it's for

- A **PI** running a multi-month experiment campaign who wants the
  raw data, the analysis notes, and the publication artifact to all
  trace back to the same provenance graph.
- A **research engineer** who has timeseries from OPC/UA or MQTT and
  wants to attach them to a logical "test run" alongside the test
  report and the operator's debrief.
- A **postdoc** who wants to find the dataset their colleague
  produced last quarter without filename-scavenging.
- A **data steward** who has to ship an RO-Crate to a journal and
  doesn't want to assemble it by hand.

It is **less obviously useful** for a single-author short-term
project where a git repo plus three CSV files would do. The
overhead of shepard is amortised across collaboration and
re-discovery.

## What's in the box (today)

shepard organises everything into five primitives:

| Primitive | What it represents | Example |
|---|---|---|
| **Collection** | A campaign, project, or topic — the top-level grouping | "LUMEN-Inspired Hot-Fire Test Campaign — Q3 2024" |
| **DataObject** | A logical thing inside a Collection, freely nestable, with attributes | "TR-004 — fired run with anomaly" |
| **Reference** | A pointer from a DataObject to a payload of one of five kinds | "`tr-004-sensors`" — TimeseriesReference |
| **Annotation** | A semantic tag from an ontology attached to anything | `phase = ramp_up`, `severity = HIGH` |
| **Lab journal entry** | A free-text (post-J1a: markdown) note attached to anything | "Vibration spike on fuel-turbopump observed at t=8s..." |

Plus payload kinds (the things References point at):

- **TimeseriesReference** → time-stamped numeric data, stored in
  TimescaleDB.
- **FileReference** → arbitrary file blobs, stored in MongoDB
  (CAD, PDFs, photos, source code).
- **StructuredDataReference** → JSON documents, stored in MongoDB
  (run-logs, configs, metadata bundles).
- **SpatialDataReference** → geo / spatial geometry, stored in
  PostGIS (optional feature toggle).
- **HDF5 / HSDS reference** *(coming with A5; design done in
  `aidocs/35`)* → HDF5 datasets accessible via `h5pyd`-compatible
  REST.
- **Git reference** *(coming with G1; design done in `aidocs/38`)* →
  pinned git commit + path, for analysis code provenance.

## The cross-cutting features

These work the same way across every primitive:

- **Permissions.** Every entity has owner / readers / writers /
  managers. PUBLIC is one toggle away. Group-based permissions
  designed (`aidocs/24` F2) but not yet shipped.
- **Lineage / predecessors.** Every DataObject can point at one or
  more predecessors. The "TR-006 was the bearing-replaced re-test
  of TR-004" relationship is a graph edge, traversable in both
  directions.
- **Versioning + snapshots.** Today: `Version` is a marker.
  *(Coming with V2; design done in `aidocs/41`)*: snapshots make
  point-in-time, immutable, reproducible reads first-class — your
  RO-Crate export against a snapshot is byte-reproducible.
- **Search.** Across all entities and attributes; semantic-annotation
  search lights up additionally for ontology terms. Improvements
  in flight (`aidocs/13`).
- **Subscriptions.** URL-pattern webhooks fire on entity changes
  for downstream pipeline glue.
- **RO-Crate export.** Selective (`aidocs/31`) — choose which
  payload kinds, redact specific fields, optionally include
  permissions / annotations / versions. Reproducible (post-V2d).
- **API keys** with optional `validUntil` expiry (L5, shipped) —
  you can hand a campaign-lead key to a sub-team without forever
  granting access.

## How you actually use it

Five typical entry points:

1. **The web UI.** Browse, search, edit. Add lab-journal entries.
   Configure your profile (post-U1c). Spin up a process run
   (post-PR1b).
2. **The Python client.** `pip install shepard-client`. Open a
   notebook, walk a Collection, run analysis, write results back.
   The `examples/seed-showcase/notebooks/` are the canonical
   pattern.
3. **`h5pyd` against shepard's HSDS sidecar** *(post-A5)*. Existing
   `h5py.File(...)` analysis code keeps working — just point at
   shepard's HSDS endpoint.
4. **shepard-timeseries-collector.** Configure once; it pushes
   OPC/UA / MQTT / KUKA RSI telemetry into shepard timeseries
   containers continuously. See `aidocs/40 §3` for the improvement
   roadmap.
5. **shepard-process-wizard, transitioning to in-shepard processes**
   *(post-PR1b)*. Model a campaign as a sequence of templated steps;
   step through it; every step's output is captured automatically.

## What shepard is not

- **Not a PLM.** No CAD revision graph, no BOM management, no
  approval workflows beyond the templates feature's required-fields
  mechanism. Aras / Teamcenter do PLM; shepard talks to PLM via
  references.
- **Not a HPC scheduler.** No job queue, no compute. Run your
  analysis where you already do; attach the result to shepard.
- **Not a code repository.** Code lives in git; shepard tracks the
  link to the right commit (post-G1). Don't paste source files into
  StructuredData blobs.
- **Not a permissions-light publishing platform.** Every entity has
  ACLs; if you want anonymous global reads, you set a Collection
  PUBLIC explicitly.
- **Not a real-time / OLTP system.** shepard reads happily; bulk
  writes have caps (`aidocs/29` P10 design quotes them). It's a
  research-data store, not a trading system.

## Where it's going

The three things on the near horizon, in priority order:

1. **HDF5 / HSDS as a payload kind** (`aidocs/35`, A5 series).
   `h5pyd` parity for existing analysis code; gated on L2c.
2. **Templates + processes** (`aidocs/39` + `aidocs/40 §2`, T1 +
   PR1 series). Standardise how DataObjects get created; bring
   process design + runtime into shepard core.
3. **User profile + ORCID** (`aidocs/36`, U1 series). Closes #29
   and makes RO-Crate exports cite authors properly.

Mid-horizon:

- **Snapshots** (`aidocs/41`, V2 series). Reproducible reads, deep
  exports, lineage diff.
- **Git artifact tracking** (`aidocs/38`, G1 series). Commit-SHA
  provenance in RO-Crate exports.
- **Lab journal + Jupyter** (`aidocs/37`, J1 series). Inline
  notebook render, "Open in Jupyter" deep link.
- **Unified search + pagination** (`aidocs/13`, P-series).
- **Provenance / lineage** (`aidocs/30`). OpenLineage-shape events
  across the pipeline.

Long-horizon (deliberately deferred):

- Federation across shepard instances (`aidocs/16` X3, parked).
- Live-kernel notebooks (`aidocs/37` J1f, parked).
- shepard as a git host (`aidocs/38 §10`, explicit non-goal).

## Why this fork exists

`gitlab.com/dlr-shepard/shepard` is the upstream. `noheton/shepard`
is a development fork that picks up the backlog faster: ID-migration
phasing (L2 series), bigger search/permissions investments,
HDF5/HSDS integration, the upgrade-tracker discipline. Per
`CLAUDE.md` the upstream API surface stays byte-frozen; everything
this fork adds lives at `/v2/` so an admin upgrading from upstream
has a low-friction path (see `aidocs/34`).

## Where to go next

- **Operator** ("how do I run it?") → `docs/admin.md`,
  `docs/deploy-oracle-free.md`, `docs/deploy-self-hosted-zoraxy.md`.
- **API user** ("how do I write a client?") → the OpenAPI at
  `<host>/shepard/doc/openapi.json`; generated clients on
  `gitlab.com/groups/dlr-shepard/-/packages`.
- **Showcase walk** ("show me a real example") →
  `examples/seed-showcase/` + `examples/seed-showcase/notebooks/anomaly-analysis.ipynb`.
- **Per-feature design** → the `aidocs/00-index.md` table of
  contents; each row's link is a self-contained design note.
- **What's coming** → `aidocs/16-dispatcher-backlog.md` (the live
  backlog) and the §"Where it's going" section above (kept current).

## How this doc stays honest

The standing rule in `CLAUDE.md` makes this doc's update part of
any PR that changes user-visible behaviour. The §"Where it's going"
section is the most-likely-to-rot section — every time a feature
moves from "queued" to "shipped," the corresponding bullet moves
from "near horizon" to "what's in the box (today)." A reviewer who
sees a feature-shipping PR without a corresponding `aidocs/42`
update should reject the PR. That's the only way the doc stays
useful.

For richer per-feature detail, follow the links above into the
design corpus.
