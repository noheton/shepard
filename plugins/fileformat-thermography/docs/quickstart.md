# Quickstart — Uploading an Edevis OTvis measurement

You have a `.OTvis` file from the MFFD thermography campaign. This
page tells you what happens when you upload it.

## Two-click upload

1. Open the DataObject for your AFP layup (e.g. ply 18, section 4).
2. Drag your `S4_M13_L18_F4.OTvis` file onto the FileContainer.

That's it. The tier-1 parser runs automatically once the plugin
is wired into the backend (see the install page for the wire-up
status). Within seconds, your DataObject and the uploaded
FileReference are enriched with up to 17 semantic annotations:

- **Acquisition parameters** (on the FileReference): frame rate,
  integration time, excitation device + frequency + amplitude,
  signal type, recording type, resolution, conditioning &
  acquisition periods, campaign, creating software version,
  creation date (normalised to ISO-8601 UTC).
- **MFFD grid position** (on the parent DataObject): section,
  module, layer, frame — extracted from the filename pattern.

Find them in the DataObject's annotations panel under the
`urn:shepard:thermography:*` and `urn:shepard:mffd:*` namespaces.

## Filename convention matters

The parser uses the filename to determine the grid position. The
canonical pattern is:

```
S<section>_M<module>_L<layer>_F<frame>.OTvis
```

Example: `S4_M13_L18_F4.OTvis` → Section 4, Module 13, Layer 18,
Frame 4.

If your file doesn't match this pattern (e.g. `report-final.OTvis`),
the four `urn:shepard:mffd:*` annotations are skipped. All other
acquisition annotations still emit.

## What I cannot do yet (tier-1)

- See IR frames in the browser — the frame-extraction step is
  filed as `OTVIS-PARSE-2`. For now the file remains a binary
  payload on the FileReference; download it and open in the Edevis
  Display Img tool.
- Plot lock-in amplitude / phase images in a Shepard view — same
  reason. The placeholder `ThermographyView` shows the metadata
  table and a Three.js canvas wired up to receive the texture
  once tier-2 lands.

## What I should NOT upload

- `.diproj` project-manifest files — deliberately ignored. If you
  have a project that aggregates many `.OTvis` measurements, upload
  the individual `.OTvis` files; the project file is not parsed
  (DO-sprawl containment rule, see
  `aidocs/integrations/114-process-monitoring-parser-plugin.md §0`).

## Where to learn more

- `docs/reference.md` — every annotation key and how it is derived.
- `aidocs/integrations/114-process-monitoring-parser-plugin.md` —
  the full design doc, including the planned tier-2 frame extraction
  and the cross-modal correlation with AFP layup timeseries.
