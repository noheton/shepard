---
stage: concept
last-stage-change: 2026-05-27
purpose: spatial plugin reference
---

# shepard-plugin-spatial — reference

The `spatial` plugin adds PostGIS-backed spatial data containers to Shepard.
It provides geographic point observation storage and querying via the `SpatialDataPoint`
payload kind and the `SpatialDataReference` context entity.

See also: `plugins/spatiotemporal/docs/reference.md` for the combined
PostGIS + TimescaleDB spatiotemporal plugin (SPATIAL-V6-001), which extends
this foundation with time-varying engineering geometry profiles (AFP sweeps,
robot paths, NDT line scans) and beam-steering NDT orientation extensions
(PAUT/TOFD).

## NDT orientation extensions

### Beam-steering NDT orientation extensions

The `orientation` JSONB field on `BrushTraceShape`/`MFFDUTAScanShape` accepts two
optional sub-schemas for phased-array and time-of-flight diffraction instruments:

**PAUT (Phased-Array Ultrasound Testing):**
```json
{
  "pose": { "qx": 0, "qy": 0, "qz": 0, "qw": 1 },
  "beamSteer": { "angleDeg": 45.0, "skewDeg": 0.0 }
}
```

**TOFD (Time-of-Flight Diffraction):**
```json
{
  "pose": { "qx": 0, "qy": 0, "qz": 0, "qw": 1 },
  "pairOffsetMm": {
    "transmitter": [0, -15, 0],
    "receiver": [0, 15, 0]
  }
}
```

No schema migration is required — `orientation` is JSONB (open schema). These conventions
are enforced by the `MFFDUTAScanShape` SHACL companion shape. A future validator can
require `beamSteer` when `scanMode = "PAUT"` and `pairOffsetMm` when `scanMode = "TOFD"`.
