---
title: Video stream references
weight: 65
---

# Video stream references

shepard's **`VideoStreamReference`** payload kind attaches a video file
to a `DataObject`. On upload shepard runs `ffprobe` to extract
technical metadata (duration, resolution, codec, frame rate, file
size). The metadata is stored on the entity and surfaced in both the
REST API and the frontend's **Video References** expansion panel.

## Endpoints

All paths are under the `/v2/` surface (this fork's development
shelf; not present in upstream 5.2.0).

| Verb / Path | What it does |
|---|---|
| `POST /v2/data-objects/{appId}/video-stream-references` | Upload a video file (multipart/form-data). Returns `VideoStreamReferenceIO` with ffprobe metadata populated. |
| `GET /v2/data-objects/{appId}/video-stream-references` | List all video references attached to a DataObject. |
| `GET /v2/data-objects/{appId}/video-stream-references/{refAppId}` | Fetch metadata for one reference. |
| `GET /v2/data-objects/{appId}/video-stream-references/{refAppId}/download` | Stream the raw video bytes (progressive download; `Content-Type` reflects detected MIME type). |
| `DELETE /v2/data-objects/{appId}/video-stream-references/{refAppId}` | Delete the reference and its stored video bytes. |

## Upload example

```http
POST /v2/data-objects/do-abc123/video-stream-references
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="file"; filename="test-run.mp4"
Content-Type: video/mp4

<binary video data>
--boundary--
```

Response (201 Created):

```json
{
  "appId": "vsr-xyz789",
  "name": "test-run.mp4",
  "mimeType": "video/mp4",
  "fileSizeBytes": 104857600,
  "durationSeconds": 73.4,
  "width": 1920,
  "height": 1080,
  "frameRate": 29.97,
  "videoCodec": "h264",
  "audioCodec": "aac",
  "wallClockTimestamp": 1716000000000000000,
  "createdAt": "2026-05-17T10:00:00Z",
  "createdBy": "researcher@example.dlr.de"
}
```

## Metadata fields

| Field | Type | Description |
|---|---|---|
| `appId` | `string` | Stable identifier for this reference. |
| `name` | `string` | Original filename from the upload. |
| `mimeType` | `string` | MIME type detected from the file content. |
| `fileSizeBytes` | `long` | File size in bytes. |
| `durationSeconds` | `double` | Total duration extracted by ffprobe. `null` if ffprobe is unavailable or the file has no duration. |
| `width` / `height` | `int` | Video resolution in pixels. `null` if not detected. |
| `frameRate` | `double` | Frames per second. `null` if not detected. |
| `videoCodec` | `string` | Video stream codec name (e.g. `h264`, `vp9`, `hevc`). |
| `audioCodec` | `string` | Audio stream codec name (e.g. `aac`, `opus`). `null` if the file has no audio. |
| `wallClockTimestamp` | `long` | UTC nanoseconds epoch extracted from the ffprobe `creation_time` tag. Used as the TM1 temporal anchor for cross-source alignment (timeseries, sensor data). `null` if not present in the container metadata. |

## ffprobe availability

ffprobe metadata extraction is opt-in at the OS level. If `ffprobe`
is not on the backend's `PATH`, shepard logs a warning at startup
and stores `null` for all technical metadata fields. The upload still
succeeds and the file is stored; metadata can be re-populated by
deleting and re-uploading once ffprobe is installed.

To enable ffprobe, install `ffmpeg` (which bundles `ffprobe`) in the
backend container or host:

```yaml
# In docker-compose.yml, add to the backend service:
environment:
  SHEPHERD_FFPROBE_ENABLED: "true"
```

The `docker-compose.yml` in this repo includes ffprobe in the backend
image by default when the `video` compose profile is active.

## Viewing videos in the frontend (UI3a)

The DataObject detail page shows a **Video References** expansion
panel for any DataObject that has an `appId`. The panel:

- Renders a native `<video>` player for each reference (progressive
  MP4/WebM playback; no HLS in VID1a). The player fetches the video
  as a blob using the session Bearer token on mount, then hands the
  browser a `blob:` URL — so the auth-gated `/download` endpoint works
  without any extra cookie or signed-URL setup.
- Displays metadata chips: duration, resolution, video codec, audio
  codec, frame rate, file size, estimated bitrate.
- Provides a per-reference **Download** button.

HLS segmented delivery is deferred to VID1b. The native player works
for all MP4 and WebM files in modern browsers. Large video files are
loaded into browser memory before playback begins — for very large
files prefer the **Download** button and play locally until VID1b
ships the streaming endpoint.

## Supported container formats

Any format ffprobe can decode is accepted at the API level. The
frontend `<video>` element renders natively in-browser:

| Format | Extension | Browser support |
|---|---|---|
| MPEG-4 / H.264+AAC | `.mp4` | Excellent (all modern browsers) |
| WebM / VP9+Opus | `.webm` | Good (Chrome, Firefox, Edge) |
| QuickTime | `.mov` | Varies (Safari; limited elsewhere) |
| Matroska / H.264 | `.mkv` | Limited (requires browser codec support) |

## Storage

Video bytes are stored in the `_shepard_videos` shared storage
container (MongoDB GridFS, same infrastructure as file references).
The download endpoint streams bytes directly from GridFS; no
intermediate copy is made.

## See also

- `aidocs/16-dispatcher-backlog.md` VID1 — slice-by-slice status.
- `aidocs/34-upstream-upgrade-path.md` VID1a — operator upgrade notes.
- `aidocs/ops/86-ui-changelog.md` UI3a — frontend changelog entry.
