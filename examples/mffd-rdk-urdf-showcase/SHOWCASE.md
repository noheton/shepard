# MFFD RDK → URDF Showcase

End-to-end demonstrator of Shepard's browser URDF viewer + animator
driven by real DLR MFFD AFP-cell metadata.

## What this shows

- **Real `.rdk` station file uploaded → tier-1 parser auto-scrapes
  8 `urn:shepard:rdk:*` annotations on the FileReference** (appVersion,
  platform, programSource, robotController, apiEndpoint, three cadRef
  paths, two stepRef paths) — no UI work; the RDK-PARSE-1 plugin runs
  on upload.
- **URDF model rendered in-browser** (Three.js via `urdf-loader`)
  for the **real** KR210 R2700/2 — the MFFD AFP cell robot, sourced
  from `kroshu/kuka_robot_descriptions` (Apache-2.0); see
  `urdf/SOURCES.md`.
- **6-channel joint trajectory whose channels match the URDF joint
  names (`joint_1 … joint_6`)** → `UrdfChannelPicker` auto-binds
  channels to URDF joints with zero manual configuration. The
  picker's preselection contract is `urn:shepard:urdf:joint` (per
  URDF-WEBVIEW-1 §3) with a field-name heuristic fallback (per
  `frontend/utils/urdfChannelPicker.ts` line 47); the showcase
  exercises both — the seeder best-effort writes the annotation, and
  the channel field names match the joint names, so the binding lands
  even if the annotation predicate is rejected by the current
  channel-annotation endpoint (see "Honest caveats" below).
- **Animator plays the synthetic AFP layup trajectory** through the
  URDF at ≥30 FPS — scrub, play/pause, speed presets — via
  `UrdfAnimator.vue`.

## 90-second click-walkthrough

After running `seed.py` (see `README.md`) the seeder prints the
Collection URL. Then:

1. **Collection page** — three DataObjects visible:
   - `MFFD AFP Cell — MFZ.rdk source`
   - `R10 (KR210 R2700/2) — kinematic model`
   - `AFP Ply 5 layup — joint trajectory`

2. **Click "MFZ.rdk source" → its single FileReference** — the
   FileReference detail panel shows the eight scraped annotations
   under the `urn:shepard:rdk:*` namespace.
   The scrape happened automatically on upload — no user action.

3. **Back to Collection → click "AFP Ply 5 layup" → its
   TimeseriesReference** → "Visualize in 3D" → toggle the renderer
   to **URDF**. Enter the URDF source URL
   `/urdf-samples/kr210_r2700_2/kuka_quantec_support/urdf/kr210_r2700_2.urdf` +
   packagePath `/urdf-samples/kr210_r2700_2` (the seeder logs these
   as the final summary). Click "Open" → renderer dispatches
   `UrdfView` → the KR210 R2700/2 robot paints.

4. **Open `UrdfChannelPicker`** — the six joints `joint_1 …
   joint_6` are already bound to the seeded channels with zero
   clicks (the annotation does the work).

5. **Press play on the animator** — the robot moves through the
   30-second AFP raster sweep. Scrub the timeline; toggle 0.1×,
   1×, 10× speed.

## Honest caveats

This showcase **routes around** the missing phase-2 piece of the
URDF-WEBVIEW-1 chain. Things you should know:

- **Tier-1 scrape is metadata-only** — `RdkTextScrapeParser`
  emits cell-level annotations from the `.rdk` byte stream but
  **does not** derive kinematics. The URDF is sourced separately.
- **The URDF is open-source ROS, not generated from the `.rdk`** —
  the real KR210 R2700/2 from `kroshu/kuka_robot_descriptions`
  (Apache-2.0). The kinematics match the MFFD AFP cell robot;
  `urdf/SOURCES.md` documents the upstream + commit + flatten
  procedure.
- **Phase 2 — `RdkToUrdfExporter` sidecar — is queued**, gated on
  RoboDK SDK + KUKA OLP licence availability. When shipped, this
  showcase's URDF can be regenerated directly from MFZ.rdk (closing
  the loop on cell-specific frame offsets and tool definitions that
  the static URDF doesn't carry).
- **The joint trajectory is synthetic but joint-limit-respecting** —
  `trajectory/generate.py` produces 30 s × 100 Hz of believable
  AFP raster motion. Joint angles are hand-tuned sinusoids + ramps,
  not back-solved from a TCP path. Looks like an AFP layup; isn't
  one.
- **Channel-annotation endpoint** — `POST /v2/timeseries-containers/
  {containerId}/channels/{shepardId}/annotations` currently anchors
  the predicate at `urn:shepard:spatial:axis` (the TS-AXIS-AUTO
  shape). A companion endpoint for arbitrary predicates (or one
  parameterised at `urn:shepard:urdf:joint`) is the URDF-WEBVIEW-1 §3
  TODO. Until that lands, the seeder's annotation step may be a
  no-op; auto-binding still works via the field-name heuristic
  fallback because the channel `field` values exactly match the URDF
  joint names. When the proper endpoint ships, the annotations the
  seeder tries to write will succeed and the binding will switch
  from heuristic to declared.
- **Mesh weight** — the visual meshes total ~12 MB across 7
  binary STL files. `link_1.stl` is the largest at 4.5 MB. The
  collision meshes are NOT shipped; the viewer uses visual meshes
  only.
- **Path-mounted at `/urdf-samples/kr210_r2700_2`** — the URDF +
  meshes are served from `frontend/public/` so the browser fetches
  them via plain HTTP. The seeder also uploads them as
  FileReferences for provenance; the runtime fetch path is the
  public dir, not the FileReference signed URL (saves CORS +
  presign latency).

## Cross-references

- [`urdf/SOURCES.md`](./urdf/SOURCES.md) — URDF licence + commit hash
- [`urdf/kr210_r2700_2.urdf`](./urdf/kr210_r2700_2.urdf) — URDF source-of-truth copy
- [`trajectory/generate.py`](./trajectory/generate.py) — synthetic trajectory generator
- [`plugins/fileformat-robotics/`](../../plugins/fileformat-robotics/) — RDK-PARSE-1 plugin
- [`aidocs/integrations/113-urdf-viewer.md`](../../aidocs/integrations/113-urdf-viewer.md) — URDF-WEBVIEW-1 design
- [`aidocs/integrations/110-file-format-parser-plugin.md`](../../aidocs/integrations/110-file-format-parser-plugin.md) — Parser plugin SPI
- [`examples/mffd-showcase/`](../mffd-showcase/) — sibling MFFD AFP showcase (process chain + sensors)
- URDF-WEBVIEW-1, RDK-PARSE-1 in [`aidocs/16-dispatcher-backlog.md`](../../aidocs/16-dispatcher-backlog.md)

## Phase 2 — what's missing

- `RdkToUrdfExporter` sidecar (`plugins/fileformat-robotics/sidecars/rdk-to-urdf/`)
  wrapping the RoboDK Python API. When licence-cleared, this would
  derive a URDF directly from MFZ.rdk's frame tree (capturing the
  cell-specific tool definitions and frame offsets that the static
  KR210 R2700/2 URDF doesn't carry).
- Composition with Trace3D — overlay the TCP path (Trace3D) on top of
  the moving URDF robot in a single canvas, so the IR temperature
  trail + joint motion render synchronized. Tracked under
  URDF-WEBVIEW-1 phase 2 acceptance.
- Foxglove Studio iframe fallback — same URDF, ROS-bag replay, for
  operators living in RViz already.
- `UrdfRecordButton` — record the in-browser animation as a new
  `TimeseriesReference` (round-trip for human-tuned demo motion).

## Real MFFD `.src` — first integration (2026-05-30)

First end-to-end pass against **actual KUKA KRL programs** from the DLR MFFD
Confluence-export tree. Source dir (NOT in this repo — DLR-internal): seven
`.src` callback hooks attached to the AFP cell's TPS / thermography subsystem.
Files uploaded as singletons under DataObject appId
`019e78ef-95fa-7cd6-baab-568992a2ff81` in Collection 4289 via `/v2/files`.

Per CLAUDE.md "uploads NEVER in public repo" — **the `.src` content is not
in this repo**. Only aggregate stats are recorded below.

### Per-file stats (sizes, lines, run outcome)

| File | Size (B) | Lines | URDF singleton | Interpret HTTP | Trajectory appId | Tier-1 outcome |
| --- | --- | --- | --- | --- | --- | --- |
| `AFTER_PART.src` | 654 | 24 | `019e788e-9d46…` | 422 | — | encoding-reject (ISO-8859 `ß`) |
| `AT_LEADIN_START.src` | 353 | 18 | `019e788e-9d46…` | 422 | — | encoding-reject (`ü`) |
| `AT_POST_POS.src` | 783 | 27 | `019e788e-9d46…` | 422 | — | encoding-reject (`ü`) |
| `AT_PRE_POS.src` | 5992 | 143 | `019e788e-9d46…` | 422 | — | encoding-reject (`ü`) |
| `AT_TRACK_START.src` | 39 | 3 | `019e788e-9d46…` | 201 | `019e78f2-6cd7-78b1-b093-419f8c2e494f` | empty traj (DEF/END only) |
| `BEFORE_LAYER.src` | 441 | 18 | `019e788e-9d46…` | 201 | `019e78f2-6be6-7e2f-8bdb-55facf181ff3` | 2 syntax errors + 1 IF non-literal warning; empty traj |
| `BEFORE_PART.src` | 800 | 33 | `019e788e-9d46…` | 422 | — | encoding-reject (`ü`) |

### Aggregate

- 7 real `.src` uploaded as singleton `FileReference`s; provenance recorded via
  the standard upload Activity chain.
- **2 / 7** reached the parser; **5 / 7** rejected at the encoding gate.
- **0 / 7** produced a non-empty trajectory — these are *callback hooks*
  (`BEFORE_PART`, `AFTER_PART`, `AT_LEADIN_START`, `AT_PRE_POS`, `AT_POST_POS`,
  `AT_TRACK_START`, `BEFORE_LAYER`), not motion programs. They contain `WAIT`,
  `IF`, `;EKRL` (external KRL channel) calls, and `RET` — no `PTP` / `LIN` /
  `CIRC`, so the sidecar correctly emits an empty trajectory.
- `ikSolverStats.totalPoses == 0` across all successful runs (consistent with
  no motion commands).

### Tier-2 parser gaps surfaced (real-world)

1. **Encoding: ISO-8859-1 (Latin-1) `.src` files reject at the API boundary.**
   The KRL-INTERPRETER sidecar / backend assumes UTF-8 srcContent; real KUKA
   `.src` are typically Latin-1 / CP1252. The shipped MFFD files contain
   German comments (`für`, `Schließen`, `übermitteln`, `Karenzzeit`) encoded
   as ISO-8859-1. **5 of 7 real files affected.** This is the single biggest
   blocker for real-world adoption. Backlog row:
   `KRL-INTEGRATION-MFFD-REAL-01-ENCODING-LATIN1`.
2. **Compound `IF (cond1) OR (cond2) THEN`** — `BEFORE_LAYER.src` (and others
   in the family) triggers two syntax errors on `IF … THEN` lines containing
   parenthesised compound boolean expressions. Backlog row:
   `KRL-INTEGRATION-MFFD-REAL-02-IF-COMPOUND-BOOLEAN`.
3. **Non-literal IF conditions** correctly handled as tier-1 (both branches
   skipped) but warning could carry richer context (e.g. condition variable
   name → suggested mock value). Existing `KRL-INTERPRETER-02-PARSER` row.
4. **`;EKRL` channel comments / external KRL calls** — common in MFFD AFP
   programs (`;EKRL: Variablen übermitteln`, `;EKRL: Schließen`). Currently
   no semantic meaning attached; tier-2 could surface external-channel I/O as
   structured `unsupportedConstruct` entries with the channel name for downstream
   tooling. Backlog row: `KRL-INTEGRATION-MFFD-REAL-03-EKRL-CHANNEL`.
5. **`;FOLD … ;ENDFOLD` blocks** — KUKA SmartPad folding directives. Currently
   not surfaced; useful for UI grouping. Sub-row under
   `KRL-INTERPRETER-02-PARSER`.

### Honest verdict

These 7 files are **not the right showcase** for the KRL preview pipeline —
they're event hooks with no motion. The pipeline is healthy (the 2 that parse
return well-formed empty-trajectory responses + Activity records); the gap is
upstream: we need actual motion-bearing `.src` (the TCP path program) from
the MFFD cell. The synthetic `ply5_layup.src` stays canonical for the
demo until a real layup program lands.

The Latin-1 encoding finding is the single highest-value real-world surface
gap and should land first under `KRL-INTERPRETER-02-PARSER` before any tier-2
grammar work.
