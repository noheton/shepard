---
stage: concept
last-stage-change: 2026-06-09
---

# vis-ndt-grid — quickstart

> **Slice 1 note:** The executor (slice 2) and renderer (slice 3) are pending.
> You can create and validate NdtGridShape templates today; materialize
> will return a VIEW result once slice 2 lands.

## Create an NDT grid template

1. Open the MFFD NDT OTvis Collection (e.g. *MFFD Q1 NDT Thermography*).
2. Click **Templates → New template** → select **NDT Grid** from the MFFD group.
3. Fill in:
   - **Row dimension**: `section` (for the "show me layer L18" view)
   - **Column dimension**: `module`
   - **Layer filter**: `L18`
   - **Colour mode**: `mean-delta-t`
4. Click **Save**.

## View the grid (slice 2+)

Once slice 2 is merged:

1. Open the Collection detail page.
2. Click **Views → NDT Grid**.
3. The tile mosaic renders with the configured colour map.

## Export as PNG

Content negotiation (A1): add `Accept: image/png` to the materialize request:

```
POST /v2/mappings/{templateAppId}/materialize
Accept: image/png
```

Returns a PNG raster of the grid mosaic.
