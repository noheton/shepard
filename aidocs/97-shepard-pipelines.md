# 97 — Shepard-pipelines: modern REBAR, Shepard-native

**Status.** Design — companion to `aidocs/40-ecosystem.md` § "Planned
plugins" and `aidocs/semantics/95-shacl-templates-and-individuals.md`
Part 11 (plugin contribution model).
**Snapshot date.** 2026-05-21.
**Audience.** Contributors deciding whether to ship a pipeline /
workflow capability inside Shepard, or keep the REBAR-style separate
stack.
**Originating prompt.** User 2026-05-21: "explore how a modern REBAR
could look like optimized for working with Shepard… maybe we can
simplify a lot? git repo dag plus data set, Shepard becomes
artifact. DAG continues until finished notification to user. Stats
calculated automatically. Probably will need a plugin with a real
sidecar this time."

---

## 1. What this is and is not

**Is:** a sketch for `shepard-plugin-pipelines` — a backend SPI plus
a sidecar runner that turns Shepard into the orchestration substrate
for reproducible ML / data-processing pipelines, replacing the role
REBAR (Apache Airflow + MLflow + MinIO + Marquez/OpenLineage)
plays today.

**Is not:** a Shepard fork of Airflow. The point is the opposite:
*remove* the Airflow / MLflow / Marquez / MinIO stack as separate
beasts because everything they do is already provided by Shepard's
core. The pipeline manifest declares what to run; Shepard hosts the
inputs, outputs, lineage, artefacts, and notifications.

REBAR (`gitlab.dlr.de/rebar/rebar-infrastructure`) is the reference
prior art for "DLR cross-institute ML pipeline infrastructure." Last
commit 2024; per user 2026-05-21 may be superseded by newer tools.
This design takes the *idea* and rebuilds it as Shepard-native.

---

## 2. The simplification thesis

Compare what REBAR ships vs. what Shepard already has:

| Capability | REBAR provides | Shepard already provides |
|---|---|---|
| Workflow scheduler | Apache Airflow + Celery + Redis | (gap — needs the sidecar) |
| Experiment metadata tracking | MLflow Tracking | Annotations + SHACL shapes |
| Model registry | MLflow Model Registry | StructuredDataContainer + DataObject + FAIR4ML shape |
| Artefact storage | MinIO + MLflow artefact store | FileContainer (already backed by MinIO via StorageProvider SPI) |
| Data lineage | Marquez (OpenLineage) | PROV-O via Neo4j (already richer) |
| Auth | Per-tool (Airflow / MLflow / MinIO each separate) | OIDC + JWT once, used everywhere |
| Provenance of human / AI activity | None typed | F(AI)²R + PROV1a |
| FAIR publish | None | Dataship / Unhide adapters |
| Multi-instance federation | None | Multi-cube reality at ZLP (per ecosystem doc) |
| Regulatory audit pack | None | TPL14 Regulatory Evidence Pack |

**Net:** the *only* thing REBAR genuinely provides that Shepard does
not is **DAG orchestration with task isolation**. Build that one
capability as a Shepard plugin + sidecar and everything else falls
into place — without operating five separate stacks.

This drops 4 of REBAR's 8 background services (Airflow scheduler,
Airflow worker, MLflow, Marquez) and converges 3 of the remaining
4 (Redis, Postgres, MinIO) onto Shepard's existing infrastructure.

---

## 3. The user's mental model

What you wrote, lifted directly:

> "git repo dag plus data set, Shepard becomes artifact. DAG
> continues until finished notification to user. Stats calculated
> automatically. Probably will need a plugin with a real sidecar
> this time."

Translated to architecture:

1. **One git repo per pipeline.** Contains pipeline manifest +
   per-task scripts + (small) data + references to (large) data
   in Shepard.
2. **Push the repo at Shepard.** Either via the importer plugin
   pulling from a URL, or by uploading a tag, or via webhook.
3. **Shepard creates a `:PipelineRun` DataObject** representing
   the in-flight execution; tasks become child DataObjects with
   `status` progressing through DRAFT → READY → IN_PROGRESS →
   COMPLETED / FAILED.
4. **The sidecar executes** — pulls tasks, runs containers,
   pushes outputs back, computes stats automatically.
5. **Auto-stats** — every output gets the standard battery of
   stats as annotations: SHA-256, byte size, schema fingerprint,
   timeseries min/max/mean/stdev, ODIX-style constraint check if
   the shape declares parameters, FAIR4ML model-card fields if
   the output is a model.
6. **On completion, NTF1 notifies the user.** In-app, email,
   Matrix (per planned `shepard-plugin-matrix`).

---

## 4. The pipeline manifest format

Minimal, declarative, YAML. Designed to fit on one page.

```yaml
# pipeline.yaml — example for the MFFD showcase loop
name: mffd-bridge-welding-analysis
version: 1.0
description: |
  Walks the MFFD framewelding data through the ODIX semantic
  process analysis loop and emits a Regulatory Evidence Pack.

# References to existing Shepard resources (resolved by appId)
inputs:
  framewelding_collection:
    kind: shepard:Collection
    appId: 019e4a4f-2a13-7fcc-bd56-4604f281077b
  process_ontology:
    kind: shepard:Ontology
    iri: http://semantics.dlr.de/mffd-process

# Tasks form a DAG via depends_on
tasks:

  - id: import-bson
    image: shepard/runner-python:3.12
    script: scripts/import-bson.py
    inputs:
      collection: framewelding_collection
    outputs:
      sd_docs:
        kind: shepard:StructuredDataContainer
        shape: mffd:ProcessParameterShape   # SHACL — validates on write

  - id: odix-analysis
    image: shepard/runner-python:3.12
    script: scripts/odix-analysis.py
    inputs:
      docs: import-bson.sd_docs
      ontology: process_ontology
    outputs:
      anomalies:
        kind: shepard:StructuredDataContainer
        shape: mffd:AnomalyShape
      report:
        kind: shepard:FileBundle
        formats: [markdown]
    depends_on: [import-bson]

  - id: rep-export
    image: shepard/runner-python:3.12
    script: scripts/rep-export.py
    inputs:
      anomalies: odix-analysis.anomalies
      report: odix-analysis.report
    outputs:
      evidence_pack:
        kind: shepard:FileBundle
        formats: [bag-it+ro-crate+prov-o]   # TPL14 shape
    depends_on: [odix-analysis]
    on_complete:
      anchor_ledger: true                    # TPL17 — Bloxberg anchor

notify:
  on_complete:
    - user                  # email / in-app via NTF1
  on_failure:
    - user
    - admin

# Optional — declare the pipeline outputs as one final REP target
regulatory_evidence_pack:
  target: rep-export.evidence_pack
  authority: EASA-CP2 + EU-Machinery-2023-1230
```

The manifest is **also a SHACL-shape consumer** — the `inputs` /
`outputs` declarations resolve to Shepard shape IRIs, so every
artefact lands typed and validated. No untyped blob storage.

---

## 5. Architecture — backend plugin + sidecar

```
┌──────────────────────────────────────────────────────────────────┐
│  Shepard backend (Quarkus / JVM)                                 │
│  ──────────────────────────────                                   │
│                                                                  │
│  shepard-plugin-pipelines (in-tree Java plugin)                  │
│  ├── REST: POST /v2/pipelines (submit manifest)                  │
│  │        GET  /v2/pipelines/{appId}                             │
│  │        POST /v2/pipelines/{appId}/cancel                      │
│  │        GET  /v2/pipelines/{appId}/tasks/next   ← sidecar polls│
│  │        POST /v2/pipelines/{appId}/tasks/{id}/complete         │
│  │        POST /v2/pipelines/{appId}/tasks/{id}/fail             │
│  │                                                               │
│  ├── Manifest parser + DAG validator                             │
│  ├── PipelineRun DataObject lifecycle                            │
│  ├── PROV-O activity emission per task (fair2r:AuthoringPass)    │
│  ├── Auto-stats computation on output upload                     │
│  ├── NTF1 notification dispatch                                  │
│  └── TPL14 REP export hook + TPL17 ledger anchor hook            │
│                                                                  │
└────────────────────┬─────────────────────────────────────────────┘
                     │  HTTP (REST + JWT or shared token)
                     │
┌────────────────────▼─────────────────────────────────────────────┐
│  Sidecar — shepard-pipeline-runner (Python, separate container)  │
│  ────────────────────────────────                                 │
│                                                                  │
│  Loop:                                                           │
│   1. GET /v2/pipelines/.../tasks/next     (long-poll, 30s)       │
│   2. Receive task descriptor + input pointer list                │
│   3. docker run --rm <task.image> ...                             │
│        with inputs mounted from Shepard via FUSE-style adapter   │
│        and outputs landing in a sandbox dir                      │
│   4. Stream stdout/stderr to backend (live progress)             │
│   5. On exit:                                                    │
│        compute SHA-256 + byte-size + schema fingerprint          │
│        + ODIX-style stats (if shape declares parameters)         │
│        POST /v2/pipelines/.../tasks/{id}/complete with outputs   │
│   6. Loop                                                        │
│                                                                  │
│  N sidecars can run concurrently — horizontal scaling.           │
│  Each sidecar isolates a task via docker-in-docker or a runc-    │
│  level sandbox (depending on host operator preference).          │
└──────────────────────────────────────────────────────────────────┘
```

### Why a sidecar (and not in-process)

- **Isolation:** task-defined container images must not run inside
  the Quarkus JVM. Docker-in-docker / runc / Kubernetes Jobs is
  the only safe place for arbitrary user code
- **Scaling:** spawn N sidecar replicas based on load; one Shepard
  backend per institute, many sidecars
- **Language flexibility:** task scripts are Python / R / shell /
  Julia; the sidecar abstracts the runtime
- **Failure isolation:** a misbehaving task can't OOM the backend

### Why a backend plugin (and not just the sidecar)

- **Authority:** task descriptors, status updates, output writes
  all flow through the same auth + permission gates as the rest of
  Shepard
- **Atomicity:** "task completed" + "outputs registered" + "stats
  computed" + "next-task-queued" must be one transaction
- **Provenance:** the `fair2r:AuthoringPass` activity emission is
  Shepard's job; the sidecar reports facts, the plugin records
  the typed activity

---

## 6. Task-runner image — what `shepard/runner-python:3.12` provides

Default runner image (one per language) provides:

- `pip install shepard-client` pre-baked
- An `OPENAPI_BASE_URL` + token mount → `inputs` / `outputs` resolve
  against the live Shepard instance
- A `task_descriptor.json` mounted at `/shepard/task.json` with the
  manifest entry, input pointers, output spec
- Standard Python stack: pandas, numpy, scipy, matplotlib (~250 MB)
- Optional: pre-loaded ontology library (`rdflib`, `pyshacl`) for
  ODIX-style work
- Optional GPU variant (`shepard/runner-python:3.12-cuda`) for
  ML inference / training tasks

Custom images extend any of the above. The manifest's `image:` key
accepts any container the host's docker / containerd can pull.

---

## 7. Auto-stats — what happens to every output

Per the user prompt: **"stats calculated automatically"**.

When the sidecar reports a task complete with an output, the plugin
computes (and stores as typed annotations on the output DataObject):

- **Always:** SHA-256, byte size, MIME type, schema fingerprint, IRI
- **If FileBundle:** per-file SHA-256, image dimensions (for
  images), video duration (for videos)
- **If TimeseriesContainer:** per-channel min / max / mean / stdev /
  point count, anomaly count from ODIX-style constraint check (if
  the shape declares parameters), strongest cross-channel
  correlations
- **If StructuredData with FAIR4ML shape:** model-card field
  completeness, training-dataset reference resolution, license
  presence
- **If REP target:** BagIt + RO-Crate manifest validation, ledger
  anchor txid (if TPL17 enabled)

All stats land as `SemanticAnnotation` records on the output
DataObject — queryable, facetable in the UI (per TPL2c — list
columns and facets driven by shape), and surfaced on the detail
page.

---

## 8. Notifications — pipeline finished

Per the user prompt: **"DAG continues until finished notification
to user."**

When the final task completes (or any task fails terminally):

- **NTF1** dispatches notification: in-app dropdown, email, optional
  Matrix message (per planned `shepard-plugin-matrix`)
- The notification payload includes: pipeline name, duration, REP
  URL (if regulatory-evidence-pack was declared), top-3 anomaly
  summary (if `odix-analysis`-style task emitted anomalies),
  one-click "view full report" link
- For pipelines that are part of a regulated workflow (Annex IV
  machinery under EU 2023/1230), the notification can additionally
  cc the institute's notified-body contact (per-instance config)

---

## 9. What disappears from REBAR

| REBAR component | Replaced by | Net effect |
|---|---|---|
| Apache Airflow scheduler | Shepard-plugin-pipelines backend job queue (Postgres LISTEN/NOTIFY or simple polling) | One service deleted |
| Airflow workers (Celery) | Sidecar replicas | Replaced 1:1 but unified auth |
| Redis | (gone) | One service deleted |
| MLflow tracking server | Annotation + SHACL on output DataObjects | One service deleted |
| MLflow Model Registry | StructuredDataContainer + FAIR4ML shape on the model DataObject | One service deleted |
| MinIO (separate from Shepard's) | Shepard FileContainer (already MinIO-backed) | One service deleted (the duplicate MinIO) |
| Marquez (OpenLineage UI) | PROV-O graph in Neo4j (richer); Shepard UI shows lineage already | One service deleted; native UI better |
| git-sync container | The importer plugin's git path | One service deleted |
| Flower (Celery monitoring) | Shepard pipeline-status UI | One service deleted |

**Before:** 8 background services + 2 databases + 1 git client.
**After:** Shepard + 1 sidecar (replicable) + the host's container
runtime. The sidecar is the only new background process.

Per-institute deployment goes from "complex docker-compose with 11
services and cross-service network config" to "Shepard + N sidecar
replicas."

---

## 10. What stays REBAR-like

- **Containerised tasks for reproducibility** — every task pins an
  image hash; reproducible by definition.
- **DAG-based dependency declaration** — manifest still declares
  task dependencies explicitly.
- **Cross-institute reuse** — a manifest authored at one institute
  runs anywhere with a Shepard + sidecar; the inputs/outputs resolve
  via the same shape vocabulary.
- **Best-practice library** — REBAR shipped fastai/PyTorch wrappers
  for common tasks (classification, segmentation). The same can
  ship as a `shepard/runner-python:3.12-fastai` image variant.

---

## 11. Provenance angle (TPL9 alignment)

Every task execution generates a `fair2r:AuthoringPass` Activity:

```turtle
act:pipeline-run-abc123-task-odix-analysis
  a fair2r:AuthoringPass ;
  rdfs:label "ODIX analysis task in MFFD bridge welding pipeline" ;
  prov:wasAssociatedWith agent:shepard-runner-python-3.12 ;  # the image
  prov:wasAssociatedWith agent:human-fkrebs ;                # who submitted
  prov:used ent:framewelding-sd-docs ;
  prov:used ent:mffd-process-ontology-v1.2 ;
  prov:generated ent:anomalies-2026-05-21 ;
  prov:generated ent:report-2026-05-21 ;
  prov:startedAtTime "2026-05-21T16:00:00Z" ;
  prov:endedAtTime "2026-05-21T16:00:03Z" .
```

Combined with TPL17 (ledger anchor), every pipeline run yields a
**tamper-evident, ledger-anchored audit trail** showing what code
ran, on what data, with what outputs, who triggered it, and when.
This is the exact form EU Machinery Regulation 2023/1230 Article
17(2) wants for safety-software change logs.

---

## 12. Comparison vs alternative modern stacks

The user noted REBAR's tech may be superseded. Alternatives
considered:

| Stack | Could Shepard adopt instead of build? | Verdict |
|---|---|---|
| **Prefect 3** | Modern Python-first orchestration. Could be the sidecar implementation. | Reasonable; embed Prefect *inside* the sidecar instead of writing a new poll loop. Saves ~1 week of work. |
| **Dagger.io** | Container-native CI-as-code. Manifest could BE a Dagger module. | Tempting but Dagger's model is more CI than scientific-pipeline; doesn't map cleanly. |
| **Snakemake / Nextflow** | Declarative workflow languages, popular in bioinformatics. | Could be a *manifest dialect* alongside the YAML default. Add as a second parser later. |
| **Apache Airflow 3** | The reference REBAR uses. Still heavyweight. | Skip — the whole point is to replace it. |
| **Kubeflow Pipelines** | Kubernetes-native. | Too heavy for the small-institute deployment target; viable for large-cluster sites only. |

**Recommendation:** start with a hand-rolled sidecar poll loop (~500
lines of Python). If load-volume justifies it later, swap the loop
internals for Prefect 3 without changing the manifest format. The
plugin SPI is what matters; the sidecar is replaceable.

---

## 13. Implementation slices

| Slice | Scope | Days |
|---|---|---|
| **TPL18a** | `shepard-plugin-pipelines` backend plugin: REST endpoints, manifest parser, DAG validator | 5 |
| **TPL18b** | `:PipelineRun` + `:Task` SHACL shapes; status state-machine wired through DataObject status | 2 |
| **TPL18c** | Sidecar v1 — Python poll loop, container runner, output upload | 5 |
| **TPL18d** | `shepard/runner-python:3.12` task-runner image (preloaded shepard-client, ontology stack) | 2 |
| **TPL18e** | Auto-stats computation hooks (SHA-256, schema fingerprint, ODIX-style) | 3 |
| **TPL18f** | NTF1 notification wiring on completion / failure | 2 |
| **TPL18g** | F(AI)²R Activity emission per task (depends on TPL9) | 1 |
| **TPL18h** | TPL14 REP export hook on pipeline completion (depends on TPL14) | 2 |
| **TPL18i** | TPL17 ledger anchor hook (depends on TPL17) | 1 |
| **TPL18j** | Frontend pipeline status view — DAG visualisation + task list + live logs | 5 |
| **TPL18k** | Documentation + worked example (the MFFD showcase wrapped as a pipeline) | 2 |

**Total:** ~30 days, but stages: **TPL18a/b/c (12 days) ship the
core**, the rest layer on as the related features mature.

Sensible release order:

1. **M-pipeline-1**: TPL18a + 18b + 18c + 18d (the minimum viable
   pipeline)
2. **M-pipeline-2**: TPL18e (auto-stats) + TPL18j (UI) — usable
   for daily work
3. **M-pipeline-3**: TPL18f + TPL18g (notifications + AI
   provenance)
4. **M-pipeline-4**: TPL18h + TPL18i (regulatory pack + ledger
   anchor) — the regulated-AI story

---

## 14. The MFFD showcase as the first pipeline

The artefacts under `examples/mffd-showcase/` (ontology + ODIX
analysis script) are *already* the body of an M-pipeline-1 demo —
the script is one task, the ontology is an input, the markdown
report is the output. Wrapping them in a `pipeline.yaml` (per §4 of
this doc) is the worked example for TPL18k.

That gives the regulatory pitch a single end-to-end demonstrable
flow:

1. Submit `pipeline.yaml` to Shepard
2. Sidecar runs the ODIX analysis container
3. Report + anomalies + correlations land as typed Shepard
   DataObjects
4. REP wrapper bundles them as BagIt + RO-Crate + PROV-O
5. Ledger anchor produces tamper-evident proof
6. User gets notification with one-click access to the audit pack

The whole loop, demonstrable, ~3 seconds end-to-end on the existing
synthetic data.

---

## 15. Open questions

- **Container runtime choice on the sidecar host:** docker-in-docker
  vs rootless podman vs Kubernetes Jobs. Probably configurable via
  plugin-config per instance.
- **Resource limits / GPU scheduling:** how does the sidecar know
  which host has GPUs free? V1 punts (single-sidecar-per-host); V2
  may need a smarter dispatcher.
- **Manifest dialects:** YAML-default but should we accept Snakefile
  / Nextflow / Argo as second parsers later? Decide based on
  institute uptake.
- **Inter-pipeline composition:** can a pipeline's output trigger
  another pipeline? Probably yes via the same submit endpoint
  invoked from a task; needs an explicit "pipeline chaining" pattern
  before too many cycles emerge.
- **Backfill / rerun semantics:** when a pipeline reruns, does it
  *replace* or *version* the outputs? Likely versions (per PV1a),
  with a "supersedes" `prov:Activity` linking the rerun to the
  original.

---

## 16. References

- REBAR (the reference prior art): `gitlab.dlr.de/rebar/rebar-infrastructure`
- Companion design: `aidocs/semantics/95-shacl-templates-and-individuals.md`
  Parts 9 (scaling), 11 (plugin model), 14e (F(AI)²R), 16 (ledger)
- Ecosystem context: `aidocs/40-ecosystem.md` § "Planned plugins"
- MFFD worked example: `examples/mffd-showcase/`
- OpenLineage spec: <https://openlineage.io/spec/>
- FAIR4ML: <https://www.rd-alliance.org/groups/fair-machine-learning-fair4ml-ig>

---

**Authorship.** Drafted 2026-05-21 in response to the user prompt
"explore how a modern REBAR could look like optimized for working
with Shepard."
