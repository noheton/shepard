---
stage: decommissioned
last-stage-change: 2026-05-27
purpose: spatial plugin reference — REDIRECTED
---

# shepard-plugin-spatial — reference (redirected)

This page previously documented the v5 `shepard-plugin-spatial` plugin.
The combined spatiotemporal plugin (`shepard-plugin-spatiotemporal`,
SPATIAL-V6-001) supersedes it.

**Current documentation:** [`plugins/spatiotemporal/docs/reference.md`](../../spatiotemporal/docs/reference.md)

The spatiotemporal reference covers:
- v6 `shepard_spatial` schema (profile hypertable)
- All REST endpoints under `/shepard/api/spatialDataContainers/`
- BrushTraceShape VIEW_RECIPE for AFP/NDT sweep rendering
- Coordinate frame handshake (`coord_frame_app_id` FK convention)
- PAUT/TOFD beam-steering NDT orientation extensions
- `GeoTimeVocabularyProvider` SPI contribution
- PostGIS co-located on TimescaleDB (no separate container)
