---
title: spatial-importer — Quickstart
stage: feature-defined
last-stage-change: 2026-06-02
audience: power-user
---

# Quickstart — see Track 244's laser profile in 3D

This is the casual-user task page. You've already imported MFFD
tapelaying data into Shepard with W2, and now you want to see the
laser-scan surface profile and TCP trajectory as a 3D plot in the
browser.

## Two clicks from a DataObject

1. Open the Track DataObject in the UI. For Track 244, navigate to
   Collections → MFFD Upper-Fuselage (Real) → tapelaying → Track 244.
2. Expand the **Spatial data** panel. You'll see one row per
   promoted container, grouped by kind:
   - **Surface profile (laser scan)** — `TPS 3D pointclouds.0` and `.1`
   - **TCP trajectory** — `FSD course 3D pointclouds`
3. Click **Open in 3D viewer** on any row.

The viewer shows the points coloured by Z (height) for the profile
and by time for the trajectory. Drag to rotate; scroll to zoom.

## What if the panel says "No spatial data containers"?

The Track has been imported but the spatial promotion pass hasn't
run yet. Two ways to fix:

### Option A — admin runs the pass

The admin runs the importer CLI once. See [install.md](install.md)
for the runbook.

### Option B — re-open the Track tomorrow

Most operators schedule the spatial pass nightly. The next nightly
run will populate every Track that has the source files.

## How big can the pointcloud be?

The Three.js viewer decimates to 500,000 points by default using
voxel-grid downsampling (deterministic, preserves surface coverage).
A typical Keyence laser stripe is ~4,000 points, which renders without
decimation. The legend tells you whether decimation kicked in.

## "Open in 3D viewer" → broken page?

If the viewer says "Could not load spatial points: …", the
SpatialDataContainer was created but the payload didn't upload
(usually a network or backend timeout during the original pass).
The admin can re-run the pass; SHA256-based idempotency means
re-running is safe.
