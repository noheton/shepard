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

## Spot the hot-spots in a layup region (MFFD-NDT-QUALITY-1)

Once your DataObject carries a thermography FileBundleReference (a bag
of TIFF frames from the layup pass), open its detail page. The
**Thermography NDT** panel mounts automatically — three things appear:

1. **Quality chip** — green ≥ 0.8, amber 0.5–0.8, red < 0.5.
   The score is `1 − max(peak-delta-c) / threshold-c`. A perfectly
   uniform layup scores 1.0; a layup whose worst frame's peak hit
   the 80 °C threshold scores 0.0.
2. **Plate heatmap** — a single composite image showing the maximum
   temperature observed at each pixel across the entire layup pass.
   Bright yellow = hot-spot zones; deep purple = cold zones. Hover
   to see exact temperatures.
3. **Re-analyze** button — click to re-run the analysis (Write
   permission required). Useful after uploading new TIFFs or after
   adjusting the threshold via the admin config.

You never need to click into a 6,000-frame strip to find the bad
plies — the heatmap surfaces them at a glance. If you need the
individual frame, the bundle's existing image-strip view remains
available below the heatmap.

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

## How do I view a thermography scan? (OTVIS-VIEWER)

Once an `.OTvis` file is uploaded as a **single-file FileReference**
(the default for one-file uploads), Shepard decodes its amplitude and
phase frames and shows them on the DataObject detail page:

1. Open the DataObject that carries the `.OTvis` scan.
2. Scroll to the **"Thermography Frames (OTvis)"** panel (one panel per
   `.OTvis` reference) and expand it.
3. The heatmap renders straight away. For a lock-in result frame, use
   the **amplitude / phase** toggle:
   - **Phase** (default) — the channel that reveals subsurface defects
     (delamination, porosity) with the least sensitivity to surface
     emissivity and uneven heating. This is the channel you read for
     CFRP NDT.
   - **Amplitude** — the magnitude of the thermal response.
4. Drag the **frame scrubber** to step through frames (single-frame
   archives show no scrubber).

You never type a path or URL — the viewer pulls the bytes from the
reference by its appId. If the panel shows a warning banner, some
frames were tolerated with issues (e.g. an unsupported frame format);
the rest still render.

## Where to learn more

- `docs/reference.md` — every annotation key and how it is derived;
  §6.5 documents the decoded-frame viewer REST endpoints.
- `aidocs/integrations/114-process-monitoring-parser-plugin.md` —
  the full design doc, including the planned tier-2 frame extraction
  and the cross-modal correlation with AFP layup timeseries.
