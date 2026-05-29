---
stage: feature-defined
last-stage-change: 2026-05-29
audience: contributor + RDM
---

# Microsections showcase — composite cross-sections + Jupyter analysis

This showcase mounts a real (not synthetic) **fiber-reinforced composite
metallography dataset** as a shepard Collection: 7 polished cross-section
specimens (PH2940-01 … PH2940-07) with paired Jupyter analysis notebooks
that compute Fiber Volume Fraction (FVF) + porosity via 3-peak histogram
segmentation.

The flagship purpose: this is **the JupyterHub-integration acceptance
test** (task `J2` in `aidocs/16-dispatcher-backlog.md`). When the J2
plugin lands, the notebook attached to each sample is parameterised +
fired through JupyterHub; FVF results flow back as SemanticAnnotations
on the sample DataObject.

---

## What the data is

Seven samples, all CF/LMPAEK-class thermoplastic-composite specimens
polished into metallographic micrographs and shot under optical
microscopy at 3072 × 2304 px, 8-bit RGB, uncompressed TIFF.

Sample PH2940-01 is **dual-bound**: two parallel notebooks compute FVF
for the same physical sample under different fiber-type interpretations
(`_carbon` and `_flachs`, German for flax/linen) — comparative
biocomposite analysis.

| Sample | Variant | Notebook | TIF |
|---|---|---|---|
| PH2940-01 | carbon | `PH2940-01_carbon.ipynb` | `PH2940-01.tif` |
| PH2940-01 | flax | `PH2940-01_flachs.ipynb` | `PH2940-01.tif` |
| PH2940-02 | carbon | `PH2940-02.ipynb` | `PH2940-02.tif` |
| PH2940-03 | carbon | `PH2940-03.ipynb` | `PH2940-03.tif` |
| PH2940-04 | carbon | `PH2940-04.ipynb` | `PH2940-04.tif` |
| PH2940-05 | carbon | `PH2940-05.ipynb` | `PH2940-05.tif` |
| PH2940-06 | carbon | `PH2940-06.ipynb` | `PH2940-06.tif` |
| PH2940-07 | carbon | `PH2940-07.ipynb` | `PH2940-07.tif` |

---

## Analysis pipeline (per the live notebooks)

All eight notebooks share the same skimage + scipy + numpy stack and
follow the same structural pattern, sample-specifically tuned via the
`crop_row`, `crop_column`, `peak`, and `width` parameters.

1. Load TIF (`imageio` or `skimage.io.imread(as_gray=True)`)
2. Crop the region of interest to the polished area
3. Histogram + KDE (`scipy.stats.gaussian_kde`) on the cropped pixel
   intensities to identify peak modes
4. Specify three peaks at `(3, 97, 152)` for voids / matrix / fibers
   plus per-peak width windows
5. Sobel elevation map (`skimage.filters.sobel`)
6. Watershed + random-walker segmentation
   (`skimage.segmentation.watershed` + `random_walker`)
7. Per-phase pixel counts + percentage → **FVF + porosity**
8. Cutouts via `skimage.color.label2rgb` for visual sanity-check

The 3-peak peak/width parameterisation is the obvious provenance gap:
the values are operator-tuned by hand. Shepard's PROV-O Activity
capture closes this hole — every J2 notebook run records the
parameters used + the timestamp + the principal as a typed
`:Activity` node.

---

## How this lands in shepard

| Concept | Shepard primitive | Population |
|---|---|---|
| Collection | `:Collection` "Microsections — PH2940 Composite Cross-Sections" | seed.py |
| Sample (PH2940-XX) | `:DataObject` per `SAMPLE_VARIANTS` entry | seed.py |
| Micrograph | `:FileReference` (image/tiff) on a shared `micrographs` `:FileContainer` | seed.py |
| Analysis notebook | `:FileReference` (application/x-ipynb+json) on a shared `notebooks` `:FileContainer` | seed.py |
| Notebook execution | `:NotebookReference` (J1 / J2) — **not seeded today**, lands when J2 ships | J2 plugin |
| Crop / peak / width parameters | `:SemanticAnnotation` with `urn:shepard:rdm:notebook-param:*` predicates — **not seeded today** | J2 plugin |
| FVF result `{fiber%, matrix%, void%}` | `:StructuredDataReference` OR three numeric `:SemanticAnnotation` with `urn:shepard:phase:fraction` predicate + QUDT `PERCENT` unit IRI | J2 plugin acceptance |

Sample-series + EN 9100 audit attributes go on every sample
DataObject via the `SAMPLE_ATTRIBUTES_COMMON` dict in `seed.py`.

---

## The J2 acceptance test

When the JupyterHub plugin ships, the canonical loop is:

1. Click a sample DO in the UI → "Open analysis notebook"
2. Plugin spawns a JupyterHub kernel with the TIF preloaded into the
   notebook's working directory
3. Notebook executes (manually or via papermill in headless mode)
4. Output cells + structured-result cells are detected; numeric phase
   fractions are extracted from the final cell
5. Plugin POSTs the results back as SemanticAnnotations on the
   sample DO
6. UI shows the result table on the sample landing

Acceptance: after running J2 against all 7 samples, the FVF table on
the Collection landing page shows per-sample fiber%, matrix%, void%
with the correct relative ordering (PH2940-01-carbon vs
PH2940-01-flax must differ).

---

## Running the seed

```bash
cd examples/microsections-showcase

# The raw data is gitignored; mirror it locally first if you don't have it.
# Inside DLR/nuclide the source-of-truth is /mnt/pve/unas/dump/microsections/.

python3 seed.py \
  --host https://shepard-api.nuclide.systems \
  --apikey <token>
```

The script is idempotent — re-runs do not duplicate. `--reset` deletes
the Collection and recreates it from scratch.

---

## See also

- `aidocs/16-dispatcher-backlog.md` row `J2` — JupyterHub integration plugin (J2a–J2d)
- `aidocs/16-dispatcher-backlog.md` row `STAGE1` — per-user data dump / staging area
- `aidocs/16-dispatcher-backlog.md` row `AI1v` — channel-unit auto-inference (sibling deterministic-floor pattern)
- `aidocs/16-dispatcher-backlog.md` row `26` — drop-data-and-go workflow
- `aidocs/16-dispatcher-backlog.md` row `RDM-005` — metadata completeness widget (FVF lands as a completeness signal)
- `examples/lumen-showcase/seed.py` + `examples/mffd-showcase/seed.py` — sibling showcase seed shape
