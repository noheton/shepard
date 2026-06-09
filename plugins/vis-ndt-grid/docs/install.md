---
stage: concept
last-stage-change: 2026-06-09
---

# vis-ndt-grid — install guide

## Prerequisites

- Shepard `>=6.0.0-SNAPSHOT`
- The `vis-ndt-grid` JAR on the classpath (included in the standard `with-plugins` profile)
- A Collection of `mffd:ndt-otvis-measurement` DataObjects with
  `urn:shepard:mffd:ndt-{section,module,layer,frame}` annotations

## Deployment

The plugin is included in the standard backend `with-plugins` Maven profile.
No additional Docker Compose profile is required in slice 1 (no sidecar).

### Verify the plugin is active

```
GET /v2/admin/plugins
```

Look for an entry with `"id": "vis-ndt-grid"` and `"enabled": true`.

## Config keys

No runtime-configurable keys in slice 1 (shape + manifest only).

Slice 2 will add a `:VisNdtGridConfig` singleton and
`GET/PATCH /v2/admin/vis-ndt-grid/config` (following the A3b/N1c2/UH1a pattern).

## Reverse-proxy

No sidecar in slice 1. No Caddyfile changes required.

Slice 2 may add an optional PNG export cache (operator-configurable, off by default).

## Healthcheck

```
GET /v2/admin/plugins?id=vis-ndt-grid
```

## Known pitfalls

- **Missing annotations**: if NDT OTvis DataObjects lack `urn:shepard:mffd:ndt-layer`
  annotations (not seeded by `MffdNdtOtvisMeasurementKind`), the executor (slice 2)
  will produce an empty grid. Ensure the seeder ran on all DataObjects.
- **colourMode=pass-fail with no quality annotation**: the executor treats missing
  quality annotations as `pending` → grey cells.
