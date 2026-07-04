---
stage: concept
last-stage-change: 2026-06-10
---

# vis-afp-thermo-overlay — install guide

## Prerequisites

- Shepard `>=6.0.0-SNAPSHOT`
- The `vis-afp-thermo-overlay` JAR on the classpath (included in the `with-plugins` profile)
- AFP course DataObjects annotated by `MffdAfpCourseKind`
  (`urn:shepard:mffd:afp-{section,module,ply,course}` predicates)
- OTvis NDT DataObjects annotated by `MffdNdtOtvisMeasurementKind`
  (`urn:shepard:mffd:ndt-{section,module,layer,frame}` predicates)
- `vis-trace3d` plugin also active (slice 2 executor builds on its Trace3D frame resolver)

## Deployment

The plugin is included in the standard backend `with-plugins` Maven profile.
No additional Docker Compose profile is required in slice 1 (no sidecar).

### Verify the plugin is active

```
GET /v2/admin/plugins
```

Look for an entry with `"id": "vis-afp-thermo-overlay"` and `"enabled": true`.

## Config keys

No runtime-configurable keys in slice 1 (shape + manifest only).

Slice 2 will add a `:VisAfpThermoOverlayConfig` singleton and
`GET/PATCH /v2/admin/vis-afp-thermo-overlay/config` (following the A3b/N1c2/UH1a pattern).

## Reverse-proxy

No sidecar in slice 1. No Caddyfile changes required.

## Healthcheck

```
GET /v2/admin/plugins?id=vis-afp-thermo-overlay
```

## Known pitfalls

- **Missing TCP channels**: if an AFP DataObject's TimeseriesReference lacks
  `tcp-temperature` channel data, the executor (slice 2) falls back to colouring
  by `head-speed`. Ensure the AFP data importer maps the KRL `TCP_TEMP` variable
  to the `tcp-temperature` field name.
- **Tile mismatch**: the executor validates that the AFP DataObject and NDT DataObject
  share the same `section` + `module` annotation values. If they differ, materialize
  returns `status: UNRESOLVED` with reason `TILE_MISMATCH`.
- **vis-trace3d inactive**: if `vis-trace3d` is not on the classpath, the executor
  (slice 2) degrades to a static 2D scatter plot instead of the 3D Trace3D view.
  Both plugins should be enabled together for the full experience.
