---
title: Feature-showcase reseed — MFFD plugin set + per-feature example seeds
stage: feature-defined
last-stage-change: 2026-06-09
---

# Feature-showcase reseed initiative (2026-06-09)

Operator directive 2026-06-09: deploy latest `main`, **full instance wipe**, reseed
**only LUMEN (updated) + a set of focused per-feature showcase Collections**, and
**define a minimum MFFD plugin set that the examples must reflect**. Replaces the
prior ad-hoc seed set (mffd, btkvs-docket, home, ts-id-demo, microsections,
mffd-rdk-urdf are all retired from the live instance).

## 1. MFFD minimum plugin set (the showcase floor)

These are the plugins an MFFD demonstrator MUST have bundled; every example below
exercises at least one. Anything outside this list is optional for the showcase.

| Plugin | MFFD role in the demonstrator | Example(s) that exercise it |
|---|---|---|
| `fileformat-thermography` | OTvis NDT scans → thermography heatmap + S×M×L×F grid | `feat-ndt-thermography` |
| `fileformat-svdx` | TwinCAT Scope welding recordings → spot/stringer weld timeseries | `feat-welding-svdx` |
| `fileformat-cad` | STEP / 3DXML / `.CATPart` → plybook + CAD metadata annotations | `feat-cad-plybook` |
| `fileformat-robotics` | RDK / URDF → AFP cell geometry, scene-graph source | `feat-scenegraph`, `feat-cell` |
| `vis-trace3d` | Trace3D path + scene-graph play + AFP-thermo overlay renderers | `feat-trace3d`, `feat-scenegraph` |
| `krl-interpreter` | KRL `.src` + URDF → derived joint-trajectory (MAPPING_RECIPE transform) | `feat-krl-transform` |
| `video` | GoPro process videos (mp4/lrv) as VideoReferences | `feat-welding-svdx` (companion video) |
| `file-s3` | Garage S3 backend for MFFD-scale binary payloads | (substrate — all file seeds) |

**Optional (full demonstrator, not minimum):** `spatial` + `spatial-importer`
(NDT pointcloud / spatial brush), `hdf5` (HDF container), `analytics-ts`
(timeseries analytics), `ai` (quality scoring), `unhide` (FAIR publish),
`minter-*` (DOI/EPIC), `aas` (asset-admin-shell), `jupyter` (notebooks).

All 8 minimum plugins are already in the `backend/pom.xml` with-plugins profile.

## 2. Deploy

Build from clean latest `origin/main` (has B8 + SHACLPREFILL + PERKIND-CLEANUP +
MATERIAL-BATCH + STRINGER-COLL-1 merged). `make redeploy` (= build-plugins →
image-backend → image-frontend → restart). Backend image was 5 days stale (2026-06-04).

## 3. Full instance wipe

All substrates to empty, then reseed. Snapshot-before-destructive is waived per the
prior operator directive (`feedback_pre_mut_snap_suspended_pre_reset.md`). Wipe:
Neo4j (DETACH DELETE all) + n10s repo, Postgres + Timescale (truncate channel/SD
tables), MongoDB (drop file collections), Garage S3 (empty buckets). Re-run the
bootstrap migrations (V49 semantic repo, upper-ontology, predicate vocab) so the
instance comes back with templates/vocab but no data.

## 4. LUMEN (kept, updated)

`examples/lumen-showcase/seed.py` — refresh to use the **current** surfaces:
v2 unified `/v2/references?kind=` + `/v2/containers?kind=`, semantic annotations
(not the legacy `attributes` bag), templates-as-shapes for the test-run DataObjects,
NCR status on TR-004 anomaly, cite-this-dataset + license/accessRights. Keep the
15-test-run hotfire narrative (TR-004 anomaly → TR-005 hold → TR-006 re-test).

## 5. Per-feature showcase seeds

Each is a small focused Collection named `feat-<slug>` that demonstrates ONE shipped
feature end-to-end. Synthetic data only (no real MFFD/DLR IP).

### Core V2CONV set
| Seed | Feature shown | Plugins / surfaces |
|---|---|---|
| `feat-templates-as-shapes` | B6/B7 — DATAOBJECT_RECIPE template + SHACL-validated instances (422 on violation) | core shapes/templates |
| `feat-mapping-recipe` | B3 — MAPPING_RECIPE template + `POST /v2/mappings/{appId}/materialize` (refs → derived ref/view) | core transform SPI |
| `feat-unified-refs-containers` | A2/A3 — `/v2/references?kind=` + `/v2/containers?kind=` across file/timeseries/uri/video/git/hdf | core + video/git/hdf |
| `feat-render-media` | A1 — VIEW_RECIPE rendered via `/v2/shapes/render` with `Accept: image/png` | vis-trace3d |
| `feat-admin-config` | A4 — `/v2/admin/config/{feature}` generic registry (ror/jupyter/semantic/sql-ts) | core admin |

### Robotics / transform set
| Seed | Feature | Plugins |
|---|---|---|
| `feat-scenegraph` | B4 — scene-graph as MAPPING_RECIPE (URDF + joint TS → played Trace3D) | robotics + vis-trace3d |
| `feat-krl-transform` | B5 — KRL `.src` + URDF → derived joint-trajectory TS | krl-interpreter + robotics |
| `feat-trace3d` | Trace3D — X/Y/Z + value TS → colour-mapped 3D path | vis-trace3d |

### Quality / semantic set
| Seed | Feature | Plugins |
|---|---|---|
| `feat-ncr-disposition` | AAA2 — NCR status + disposition + rework Predecessor chain | core |
| `feat-semantic-sparql` | semantic annotations + SPARQL playground + vocabulary terms | core semantic |
| `feat-fair-publish` | license/accessRights + metadata-completeness + cite-this-dataset | core (+ unhide optional) |

### MFFD demonstrator templates (synthetic mini-MFFD)
| Seed | Feature | Plugins |
|---|---|---|
| `feat-mffd-templates` | mffd:material-batch (keystone) + afp-course + weld-step(ultrasonic/continuous) + ndt-otvis templates, instantiated as a small AFP→weld→NDT chain | thermography + svdx + cad |
| `feat-ndt-thermography` | OTvis NDT scan → thermography heatmap + (when shipped) S×M×L×F grid | thermography + vis |
| `feat-welding-svdx` | synthetic TwinCAT svdx → welding timeseries + companion process video | svdx + video |
| `feat-cad-plybook` | synthetic STEP/CATPart → CAD metadata annotations | cad |

## 6. Phasing

1. **Deploy** clean `main` with the minimum plugin set (in flight).
2. **Wipe** all substrates + re-run bootstrap migrations.
3. **LUMEN** update + reseed.
4. **Feature seeds** — write `examples/feature-showcase/feat-*/seed.py` (parallelizable; one agent per set), each idempotent + documented with the feature + plugins it proves.
5. **Validate** — deploy smoke + per-seed UI/Playwright spot-check at 4K.

Tracked as `RESEED-*` rows in `aidocs/16`.
