# microsections-showcase

Real metallographic micrograph dataset (7 fiber-reinforced composite samples
with paired Jupyter analysis notebooks) for the shepard JupyterHub-integration
acceptance test.

See [`SHOWCASE.md`](SHOWCASE.md) for the full narrative.

## Quick start

```bash
# Pull the raw archive (Schliffbilder.7z) into ./raw-data/ if you don't have
# it locally; the live source-of-truth inside DLR is the unas dump:
#   /mnt/pve/unas/dump/microsections/Schliffbilder.7z
# Extract into ./raw-data/ — the seed script reads the .tif + .ipynb files
# from there.

python3 seed.py \
  --host https://shepard-api.nuclide.systems \
  --apikey "$SHEPARD_API_KEY"
```

The `raw-data/` directory is gitignored (see top-level `.gitignore`).

## What gets seeded

- 1 Collection: "Microsections — PH2940 Composite Cross-Sections"
- 2 FileContainers: `micrographs`, `notebooks`
- 8 DataObjects: PH2940-01-carbon, PH2940-01-flachs, PH2940-02 … PH2940-07
- Per sample: 1 micrograph FileReference + 1 notebook FileReference
- Sample-series + EN 9100 audit attributes on every DO

The Fiber Volume Fraction / Porosity result tables are **not** seeded
here — they're the J2 plugin's acceptance test output.
