---
title: vis-trace3d — Quickstart
stage: feature-defined
last-stage-change: 2026-05-28
audience: user
---

# vis-trace3d — quickstart

You have time-varying X/Y/Z position data for an end effector (a
robot's tool-centre-point, an AFP head, a satellite). You want to
see the path as a colour-mapped 3D ribbon, with a scalar channel
(temperature, force, anomaly score) driving the colour.

This page shows how to author the VIEW_RECIPE that does it.

---

## What you need

- A DataObject whose TimeseriesContainer carries (at minimum)
  three TS channels: `tcp_x`, `tcp_y`, `tcp_z` (the names are
  arbitrary; what matters is that you can pick them in the channel
  picker).
- Optionally, a fourth channel for the colour map — e.g.
  `tcp_temperature`, `rms_acceleration`, `vibration_g`.
- A user account with **write** access to your DataObject's
  parent Collection (so you can create the VIEW_RECIPE template).

---

## Step 1 — open the channel picker

1. Navigate to your DataObject's detail page.
2. Open the **TimeseriesContainer** of the reference whose channels
   you want to render.
3. Click **Add view → Trace3D**.

The Trace3D channel-picker dialog opens with three required
dropdowns (X / Y / Z) and one optional dropdown (Colour).

---

## Step 2 — pick channels

| Dropdown | Pick | Notes |
|---|---|---|
| **X channel** | the channel carrying the X spatial coordinate | usually `tcp_x` or similar. The picker's annotation-driven preselection auto-fills if the channel carries the `urn:shepard:spatial:axis` = `x` annotation. |
| **Y channel** | the Y coordinate channel | same rule. |
| **Z channel** | the Z coordinate channel | same rule. |
| **Colour channel** | (optional) the scalar | the colour map maps the channel's min/max into the chosen palette. |

If the dropdowns are empty: the channels don't carry the
`urn:shepard:spatial:axis` annotation. You can either annotate
them once (so the preselection auto-fills next time) or pick
manually from the channel-name list.

---

## Step 3 — pick a colour map

The dialog's **Colour map** dropdown lists the supported palettes.

| Palette | Best for |
|---|---|
| `viridis` (default) | general-purpose; perceptually uniform; colour-blind safe. |
| `inferno` / `magma` | hot-stuff visualisation — e.g. AFP head temperature, where the eye reads "high = bright orange-yellow". |
| `cool` | low-temperature / cryogenic data. |
| `rdbu` (diverging) | signed values around zero — e.g. residuals, anomaly scores. |
| `heat` / `cool` / `viridis` | the three palettes pre-wired in the in-tree Vitest suite, guaranteed to work without extra adapter code. |

---

## Step 4 — set the line width + time window

- **Line width**: defaults to `2.0` (world units). Bump to `5.0` if
  the path is short and disappears at zoom-out.
- **Time window**: leave empty to render the whole shared time
  domain. Set a sub-window (`2026-05-28T10:00:00Z/2026-05-28T10:30:00Z`)
  to focus on a specific test-run phase.

---

## Step 5 — save the recipe + render

Click **Save view recipe**. The dialog mints a `VIEW_RECIPE`
ShepardTemplate with the picked channel bindings and the
trace3d-specific properties. The render pane updates immediately
with the colour-mapped path.

The recipe is saved on the DataObject — anyone with read access can
re-open it later without picking channels again. The recipe is
also a regular `:ShepardTemplate` and shows up in
`GET /v2/templates?kind=view`.

---

## The MFFD AFP TCP thermal-trail example

The flagship demonstrator: render the carbon-tape laydown path
during an AFP layup, colour-mapped by tool-centre-point
temperature. The dataset (ETA `2026-05-26`) carries the AFP TCP
X/Y/Z channels plus a `tcp_temperature` channel.

```python
# examples/mffd-showcase/shapes.py — abbreviated
import json, requests

template = {
  "templateKind": "VIEW_RECIPE",
  "name": "afp-tcp-thermal-trail",
  "title": "AFP TCP thermal trail — Tapelaying TR-2026-05-28",
  "body": json.dumps({
    "renderer": "tresjs",
    "channelBindings": [
      { "role": "x",     "channelSelector": SEL_TCP_X, "unit": "http://qudt.org/vocab/unit/MilliM", "required": True },
      { "role": "y",     "channelSelector": SEL_TCP_Y, "unit": "http://qudt.org/vocab/unit/MilliM", "required": True },
      { "role": "z",     "channelSelector": SEL_TCP_Z, "unit": "http://qudt.org/vocab/unit/MilliM", "required": True },
      { "role": "color", "channelSelector": SEL_TCP_T, "unit": "http://qudt.org/vocab/unit/DEG_C", "required": False }
    ],
    "trace3d:colorMap": "inferno",
    "trace3d:interpolation": "linear",
    "trace3d:alignment": "linear-resample",
    "trace3d:sampleRate": 30.0,
    "trace3d:lineWidth": 2.0
  })
}
requests.post(f"{URL}/v2/templates", headers=HDR, json=template).raise_for_status()
```

The render route is `frontend/pages/shapes/render.vue?templateAppId=…&focusShepardId=…`.

---

## Tracking the work

This plugin is **Phase 1** — capability declaration + SHACL shape +
docs. The full renderer (`Trace3DView.vue`) is already in-tree and
works today; what's not yet in the plugin is the resolver that
turns channel selectors into aligned per-frame data (gated on
**VIS-S1**, queued in `aidocs/16-dispatcher-backlog.md`).

If you hit a missing feature — multi-trace overlay, animated
playback scrubber, PNG export — see the matching backlog rows
in `aidocs/16` (most are already designed and queued).
