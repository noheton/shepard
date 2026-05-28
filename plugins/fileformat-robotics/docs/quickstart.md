---
title: fileformat-robotics — quickstart
stage: feature-defined
last-stage-change: 2026-05-28
audience: end users
---

# I uploaded a `.rdk` file — what happens?

When you upload a RoboDK station file to a Shepard `FileContainer`,
the `fileformat-robotics` plugin is automatically invoked
(post-upload, asynchronously). You should see a handful of new
semantic annotations on the file's reference once the parse
completes (typically under a second for a 12 MB station file).

## Look at the annotations

Open the file's detail page and switch to the **Semantic
Annotations** panel (or query `/v2/file-references/{appId}/annotations`).
You will see entries like:

```
urn:shepard:rdk:appVersion        → "5.5.3"
urn:shepard:rdk:platform          → "WIN64"
urn:shepard:rdk:programSource     → "D:/MFFD/RoboDK/Ply 1-15"
urn:shepard:rdk:cadRef            → "…/MFZ_Grundkonstruktion.dae"
urn:shepard:rdk:cadRef            → "…/MFZ_Mittelschiene.dae"
urn:shepard:rdk:cadRef            → "…/MFZ_Mittelschiene_Schlitten1.dae"
urn:shepard:rdk:stepRef           → "D:/MFFD/CAD/MTLH_MultiTape Schneideinheit.CATProduct.stp"
urn:shepard:rdk:stepRef           → "D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp"
urn:shepard:rdk:apiEndpoint       → "127.0.0.1"
urn:shepard:rdk:robotController   → "R20_MFZDriver"
```

## Pair the station with its Spatial Analyzer companion

If you also upload the matching `<base>.xit` or `<base>.xit64`
Spatial Analyzer file to the same `FileContainer` (e.g.
`MFZ.rdk` next to `MFZ.xit`), the parser emits one extra
annotation:

```
urn:shepard:rdk:companionSpatialAnalyzer → (appId of the .xit file)
```

That's the metrology → digital-twin cross-link: the `.xit` carries
the *measured* coordinate frame, the `.rdk` carries the *design*
frame.

## What if I see nothing?

- The file may have been uploaded before the plugin was installed.
  Trigger a reparse: `POST /v2/files/{appId}/reparse` (when the
  parse-status endpoint is wired in your installation).
- The file may be corrupt. The plugin is best-effort: on a malformed
  zlib stream it emits zero annotations rather than failing the
  upload. Open an issue with the file's checksum if you suspect this.
- Some predicates are optional. A station file with no `.dae` or
  `.stp` references will skip those rows — that's expected, not a
  bug.

## What's not done yet (tier-2)

The full kinematic tree — robot joints, tool frames, target poses,
the cell base frame hierarchy — requires the RoboDK Python API and is
delivered as a separate sidecar service (RDK-PARSE-2). Until that
ships, the tier-1 text-scrape annotations are what you get; they're
enough to power the URDF viewer's joint-mapping when paired with a
URDF export.
