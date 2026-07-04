---
title: Upload and annotate video
description: How to attach a video file to a DataObject and mark intervals or events on the video timeline
permalink: /help/upload-and-annotate-video/
layout: default
audience: user
---
# Upload and annotate video

Shepard's **video plugin** lets you attach MP4, MOV, or WebM recordings directly
to a DataObject and mark time intervals or point events on the video timeline —
for example, ignition phases in a test campaign or anomaly windows in a
manufacturing process recording.

> **Is the video plugin enabled on your instance?**  If the **Add reference**
> menu on a DataObject does not show a "Video stream" option, contact your
> administrator (see `plugins/video/docs/install.md`).

---

## Attach a video to a DataObject

1. Open the **DataObject** detail page for the experiment run or process step
   you want to document.
2. In the **References** panel, click **Add reference → Video stream**.
3. Click **Choose file** (or drag-and-drop your video file onto the upload area).
4. Enter a **name** (required) and an optional description, then click **Upload**.

Shepard stores the file and automatically extracts metadata — duration, resolution,
frame rate, codec, and a wall-clock timestamp if your camera embedded one.
The reference appears in the References panel as soon as the upload completes.

> **Supported formats:** MP4 (H.264 / H.265), QuickTime MOV, WebM, and most
> containers `ffprobe` can read.  Very large files (hours of footage) upload
> without a size limit but may take a few minutes on a slow connection.

---

## Play the video

Click the video reference's name in the References panel to open the inline
player.  The player shows:

- the video with standard play/pause/seek controls,
- the recording duration and wall-clock start time (when embedded in the file),
- a timeline bar where existing annotations appear as coloured segments or
  vertical markers.

---

## Add a time-segment annotation

A **segment** marks a continuous interval — for example, an ignition transient
from t = 5.2 s to t = 6.8 s.

1. In the video player, drag the scrubber to the **start** of the interval you
   want to mark, then click **+ Annotation**.
2. The annotation dialog opens with the current playback position pre-filled as
   the start time.  Adjust the start and end times if needed (decimals are OK,
   e.g. `5.2` and `6.8`).
3. Enter a **label** (short name shown on the timeline, e.g. `ignition`) and an
   optional longer description.
4. Click **Save**.

The segment appears as a coloured block on the timeline.

---

## Add a point annotation

A **point** marks a single instant — for example, the moment of engine cutoff.

Follow the same steps as for a segment, but leave the **end time** field empty.
The annotation renders as a vertical marker on the timeline rather than a block.

---

## Edit or delete an annotation

- Click an existing annotation block or marker on the timeline to open its
  detail card.
- Click the **pencil** icon to edit the label, times, or description.
- Click the **trash** icon to delete it.  Deletion is permanent.

---

## View all annotations as a list

On the video reference detail page, click the **Annotations** tab below the
player to see every annotation in a table with start time, end time, and label.
You can sort the table by start time.

---

## Cross-reference with sensor data

If your DataObject also has a **TimeseriesReference** whose channels were
recorded at the same time as the video, Shepard can align them using the
`wallClockTimestamp` embedded in the video file and the timeseries channel
timestamps.  See [Timeseries plotting](/help/timeseries-plotting/) for how to
open the synchronised view.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| "Video stream" not in the Add reference menu | Video plugin is disabled | Ask your admin to enable `shepard-plugin-video` (see `plugins/video/docs/install.md`) |
| Upload stalls at 0% | File storage backend not reachable | Check your network; ask your admin to verify that GridFS or S3 is healthy |
| Player shows "cannot play" in Firefox | Browser codec limitation for H.265 | Re-encode to H.264 with `ffmpeg -c:v libx264 output.mp4`; or use Chrome/Edge |
| `durationSeconds` is null after upload | `ffprobe` could not parse the container | Verify the file plays locally; re-export from your recording software as standard MP4 |
| Annotation times appear offset | Clock drift between recorder and timeseries | Correct the `wallClockTimestamp` via the API (`PATCH` the reference) once you find the offset |

---

## Related help

- [Upload data](/help/upload-data/) — attach files or bundles that are not videos
- [Annotating data](/help/annotating-data/) — add semantic metadata tags to the DataObject itself
- [Timeseries plotting](/help/timeseries-plotting/) — plot sensor channels alongside the video timeline
