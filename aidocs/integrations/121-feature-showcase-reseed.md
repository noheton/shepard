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

Reseed status (Robotics / transform set, regenerated 2026-06-10 against the freshly
bootstrapped instance — all three run idempotently and were GET-verified):

| Seed | Collection appId | Result |
|---|---|---|
| `feat-trace3d` | minted per run (`--reset`) | ✓ **GREEN** — 4 TS refs (x/y/z/temp) + VIEW_RECIPE template; `POST /v2/shapes/render` JSON returns 4 DECLARED bindings AND `Accept: image/png` returns a real 800×600 / 39 KB PNG raster via `Trace3DPngRenderer` (after the JANDEX + FONTCONFIG fixes below) |
| `feat-scenegraph` | minted per run | ✓ **GREEN** — synthetic 3-DOF URDF (FR1b) + joint TS ref → MAPPING_RECIPE materialized to a play-envelope: 4 frames / 3 joints / 3 channel→joint bindings via `SceneGraphPlayTransformExecutor` |
| `feat-krl-transform` | minted per run | ✓ **GREEN** — KRL `.src` + URDF (FR1b) + MAPPING_RECIPE → `POST /v2/mappings/{appId}/materialize` calls the KRL interpreter sidecar and persists a derived joint-trajectory `TimeseriesReference` (GET-verified to resolve), after the Arc-unremovable fix below |

**RESEED-FIND rows (Robotics / transform set):**

- **RESEED-FIND-TRACE3D-JANDEX** (FIXED this PR) — `shepard-plugin-vis-trace3d` shipped with **no**
  `quarkus.index-dependency` entry in `application.properties`. Consequence: any
  `io.quarkus.logging.Log` call in the plugin throws
  `UnsupportedOperationException` ("only possible with Quarkus bytecode
  transformation; … include a beans.xml file"). When the plugin is enabled at
  deploy time its `VisTrace3DPluginManifest.onRegister()` FAILS (plugin state
  `FAILED`); the `Trace3DPngRenderer.renderMedia()` catch-block `Log.warnf` makes
  `POST /v2/shapes/render` with `Accept: image/png` 422. The SPI ServiceLoader
  registration (renderer + scene-graph executor) still works regardless, so the
  JSON render + scene-graph materialize are unaffected. **Fix shipped this PR:**
  added the `quarkus.index-dependency.shepard-plugin-vis-trace3d` block (same shape
  as krl/video/ai) + `SHEPARD_PLUGINS_VIS_TRACE3D_ENABLED=true`; verified post-rebuild
  the plugin loads cleanly (`state=ENABLED`) and the renderer reaches the actual
  rasteriser instead of throwing on `Log`.
- **RESEED-FIND-TRACE3D-FONTCONFIG** (FIXED this PR) — once the JANDEX fix let the
  renderer reach the AWT rasteriser, the PNG path threw `RuntimeException: Fontconfig
  head is null, check your fonts or fonts configuration` — the UBI9 backend runtime
  image ships no fontconfig + no fonts, so headless AWT text rendering
  (`Graphics2D.drawString`) can't initialise. The renderer fail-softs correctly
  (catches, logs, returns the JSON view-model — HTTP 200, not 500). **Fix shipped this
  PR:** `backend/Dockerfile` now `microdnf install -y fontconfig dejavu-sans-fonts`.
  Verified post-rebuild: `POST /v2/shapes/render` with `Accept: image/png` returns a
  real 800×600 / 39 KB PNG (magic `\x89PNG`).
- **RESEED-FIND-KRL-BEANS** (FIXED this PR) — `feat-krl-transform`'s materialize
  initially 422'd `KrlTrajectoryService … No bean found`. **Confirmed root cause**
  (from the Arc startup log even after enabling at deploy time + adding the
  index-dependency): *"At least one bean matched the required type … but was marked as
  unused and removed during build."* The `KrlTrajectoryTransformExecutor` is a
  ServiceLoader POJO that reaches its `@ApplicationScoped KrlTrajectoryService` via a
  dynamic `CDI.current().select(KrlTrajectoryService.class)` lookup — Quarkus Arc's
  build-time dead-code elimination can't see that dynamic reference, so it removes the
  bean as unused. **Fix shipped this PR:**
  `quarkus.arc.unremovable-types=de.dlr.shepard.v2.transform.krl.services.KrlTrajectoryService`
  in `application.properties`. Verified post-rebuild: materialize calls the KRL sidecar
  and persists a derived joint-trajectory `TimeseriesReference` (GET-verified). The
  sibling `SceneGraphPlayTransformExecutor` did NOT hit this because its dynamic lookup
  target (`SingletonFileReferenceService`) is a core bean other in-tree code already
  `@Inject`s, so Arc keeps it.
  Separately, `PluginRuntimeOverride` is "not a valid entity class" in the Neo4j OGM
  scan, so the runtime `PATCH /v2/admin/plugins` toggle does not persist across
  restart — deploy-time `SHEPARD_PLUGINS_*_ENABLED` env is the reliable path. Both the
  Arc-unremovable and OGM-entity-registration bugs are core/plugin findings filed for
  follow-up, not robotics-specific.
- **RESEED-FIND-ROBOTICS-NO-MANIFEST** — `shepard-plugin-fileformat-robotics`
  ships a JAR (`/deployments/lib/main/…-fileformat-robotics-…jar`) but does **not**
  appear in `GET /v2/admin/plugins` at all — it carries no `PluginManifest`
  (it is a `FileParserPlugin`-SPI-only module, mid-refactor per the working-tree
  `META-INF/services/de.dlr.shepard.spi.fileparser.FileParserPlugin` move). The
  robotics seeds don't depend on a robotics REST surface — they craft synthetic
  URDF/KRL inline and exercise vis-trace3d + krl-interpreter — so this is a
  catalogue note, not a blocker for the set.

### Quality / semantic set
| Seed | Feature | Plugins |
|---|---|---|
| `feat-ncr-disposition` | AAA2 — NCR status + disposition + rework Predecessor chain | core |
| `feat-semantic-sparql` | semantic annotations + SPARQL playground + vocabulary terms | core semantic |
| `feat-fair-publish` | license/accessRights + metadata-completeness + cite-this-dataset | core (+ unhide optional) |

Reseed status (Quality / semantic set, regenerated 2026-06-10 against the freshly
bootstrapped instance — all three run green end-to-end and were GET-verified):

| Seed | Collection appId | Result |
|---|---|---|
| `feat-ncr-disposition` | minted per run (`--reset`) | ✓ 2 DataObjects, 7 annotations, typed `fair2r:repairs` edge; quality `status` 403 (role-gated) recorded via annotations |
| `feat-semantic-sparql` | minted per run | ✓ 3 coupons, 12 annotations read back; SPARQL endpoint SKIP (see RESEED-FIND-SPARQL) |
| `feat-fair-publish` | minted per run | ✓ FAIR fields + citation + keyword; completeness self-score 95/100; 2 SKIP findings |

**RESEED-FIND rows (open):**

- **RESEED-FIND-SPARQL** — `GET /v2/semantic/internal/sparql` returns 400
  `urn:shepard:error:sparql.upstream-error` ("n10s INTERNAL returned HTTP 404")
  on the freshly bootstrapped instance: n10s `_GraphConfig` was initialised
  *after* the bootstrap data landed, so the RDF/SPARQL projection is empty/
  unqueryable. Annotations themselves are written + queryable as
  `:SemanticAnnotation` nodes (`GET /v2/annotations`). Remediation:
  `n10s.graphconfig.init` on an empty graph before data lands, or
  `n10s.graphconfig.init(...,{force:true})` on a maintenance window.
- **RESEED-FIND-ANN-LIST-403** — `GET /v2/annotations?subjectAppId=<Collection>`
  returns 403 `annotations.forbidden` ("lacks Read permission on the subject
  entity") even to the Collection's creator under API-key auth, while the
  per-DataObject subject form works. The annotation POSTs succeed; only the
  Collection-subject LIST is gated. Expectation: a Collection's creator/admin
  should be able to list annotations on their own Collection.
- **RESEED-FIND-UNHIDE-500** — `GET /v2/admin/unhide/config` returns 500
  `internal.unexpected` on the freshly bootstrapped instance (singleton likely
  not seeded). Non-blocking for `feat-fair-publish` (publishing is optional).

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
