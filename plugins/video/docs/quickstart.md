---
title: video — Quickstart
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# video — quickstart

**Goal:** upload a video file to a DataObject, then mark the
ignition transient as a time-segment annotation.

Time: 3 minutes. Assumes the plugin is enabled (per
[`install.md`](install.md)) and a file storage backend is
configured (GridFS or S3).

The walkthrough below mirrors how the LUMEN showcase attaches
hot-fire camera footage to test-run DataObjects (see
`examples/lumen-showcase/seed.py` `best_effort_video_reference`).

---

## Step 1 — pick a DataObject

Any DataObject you have write permission on. The `appId` is in
the URL bar on the DataObject's detail page:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...
DATA_OBJECT_APPID=019e4e56-ca63-76f3-9bf0-6681f7fe6d56
```

---

## Step 2 — upload the video

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -F "file=@tr-004-hotfire.mp4;type=video/mp4" \
  -F "name=TR-004 — exterior cam" \
  -F "description=Engine bay camera, 30 fps, 1080p" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references"
```

The backend stores the bytes (via the active FileStorage adapter),
shells out to `ffprobe` to extract metadata, and returns the new
reference:

```json
{
  "appId": "019e9a01-...",
  "name": "TR-004 — exterior cam",
  "mimeType": "video/mp4",
  "fileSizeBytes": 12345678,
  "durationSeconds": 42.5,
  "width": 1920,
  "height": 1080,
  "frameRate": 30.0,
  "videoCodec": "h264",
  "audioCodec": "aac",
  "wallClockTimestamp": 1747112400000000000
}
```

Save the `appId`:

```bash
REF_APPID=019e9a01-...
```

---

## Step 3 — annotate the ignition transient

Time intervals are in **seconds from video start**. The hot-fire
ignition typically sits at t=5-7s; mark it:

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "startSeconds": 5.2,
    "endSeconds": 6.8,
    "label": "ignition",
    "description": "First visible flame; pre-chamber pressure rising."
  }' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations"
```

---

## Step 4 — add a point annotation

For instantaneous events (cutoff, ignitor click, shutdown
solenoid fire), omit `endSeconds`:

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "startSeconds": 10.0,
    "label": "cutoff",
    "description": "Operator-commanded shutdown."
  }' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations"
```

A point annotation renders as a vertical marker on the video
timeline.

---

## Step 5 — list and inspect

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations" | \
  jq .
```

Response:

```json
[
  {
    "appId": "019e9a02-...",
    "startSeconds": 5.2,
    "endSeconds": 6.8,
    "label": "ignition",
    "description": "First visible flame; pre-chamber pressure rising.",
    "aiGenerated": false,
    "confidence": null
  },
  {
    "appId": "019e9a03-...",
    "startSeconds": 10.0,
    "endSeconds": null,
    "label": "cutoff",
    "description": "Operator-commanded shutdown.",
    "aiGenerated": false,
    "confidence": null
  }
]
```

---

## Step 6 — view it in the UI

Navigate to the DataObject detail page in the frontend.
The video reference shows up under the "References" section. Click
it to open the **video player** with annotation timeline overlay —
clicking an annotation seeks the video to that interval.

Annotations are also surfaced in the DataObject's RO-Crate
export as `schema:Comment` contextual entities — useful when
sharing the dataset externally.

---

## Going further

### AI-generated annotations

When an external detector marks anomalies on a video, set
`aiGenerated: true` and provide a `confidence` score so the UI
can render the marker distinctly (the LUMEN showcase uses this
shape for the TR-004 vibration-spike anomaly):

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "startSeconds": 8.0,
    "endSeconds": 8.4,
    "label": "anomaly:vibration-spike",
    "description": "AccelerometerAnomalyV2 (>10g threshold)",
    "aiGenerated": true,
    "confidence": 0.93
  }' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations"
```

### Editing annotations

Use PATCH (RFC 7396 merge-patch) for partial updates:

```bash
ANN_APPID=019e9a02-...

curl -X PATCH \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"description": "Updated: first visible flame at 5.2s, sustained burn at 6.0s."}' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations/$ANN_APPID"
```

### Correlation with timeseries

The `wallClockTimestamp` on the `VideoStreamReference` connects
the video to TimeseriesReference's TM1 time-reference model.
Once both are in place, a future frontend update will overlay the
matching timeseries chart on the video timeline.

---

## See also

- [`reference.md`](reference.md) — full payload shape + endpoints.
- [`install.md`](install.md) — operator setup + ffprobe install.
- [`examples/lumen-showcase/seed.py`](../../../examples/lumen-showcase/seed.py)
  `best_effort_video_reference` — the LUMEN demo upload path.
- [`docs/reference/timeseries.md`](../../../docs/reference/timeseries.md)
  — TM1 wall-clock anchoring for video-timeseries overlay.
