---
title: video — Reference
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

# video — reference

`shepard-plugin-video` adds the **`VideoStreamReference`** payload
kind and the **`VideoAnnotation`** time-segment annotation entity.
A DataObject can carry zero or more `VideoStreamReference` entries
(one per uploaded video file), and each reference can carry zero
or more `VideoAnnotation` entries (one per time interval marked
on the video).

The plugin uses the active `FileStorage` adapter (GridFS or S3)
for byte storage and calls `ffprobe` at upload time to extract
duration, codec, resolution, frame rate, and the wall-clock
recording timestamp.

---

## When to use this plugin

- Hot-fire test campaigns where you want to correlate **video
  phases** (ignition transient, steady-state burn, throttle-down,
  shutdown) with timeseries sensor data.
- Manufacturing process videos (AFP layup, welding cell footage,
  NDT C-scans rendered as MP4) where you want to mark anomaly
  intervals.
- Lab-bench recordings where the human operator's verbal
  commentary is the primary metadata.
- Drone-flight or robot-arm recordings where you need a wall-clock
  anchor for joining with telemetry.

---

## Payload kinds

### `:VideoStreamReference`

Neo4j node entity, one per uploaded video file, attached to a
DataObject.

| Field | Type | Notes |
|---|---|---|
| `appId` | string (UUID v7) | Server-minted on create. |
| `name` | string | Required. The video's human-readable name. |
| `description` | string? | Optional free-form description. |
| `storageLocator` | string | Server-managed. `<providerId>:<locator>` (e.g. `s3:abc/def`, `gridfs:65f...`). Opaque to clients. |
| `mimeType` | string | e.g. `video/mp4`, `video/quicktime`. From `ffprobe` or `Content-Type` fallback. |
| `fileSizeBytes` | long | From `Content-Length` or `ffprobe format.size`. |
| `durationSeconds` | double? | From `ffprobe format.duration`. Null if unknown. |
| `width` | int? | Pixel width. From `ffprobe streams[v:0].width`. |
| `height` | int? | Pixel height. From `ffprobe streams[v:0].height`. |
| `frameRate` | double? | Parsed from `ffprobe r_frame_rate` (e.g. `30/1` → `30.0`). |
| `videoCodec` | string? | e.g. `h264`, `vp9`, `hevc`. |
| `audioCodec` | string? | e.g. `aac`, `opus`. Null when video is silent. |
| `wallClockTimestamp` | long? | Nanoseconds since Unix epoch (UTC). Parsed from `ffprobe format.tags.creation_time`. The **temporal anchor** for cross-reference with TimeseriesReference's TM1 model. |

Standard shepard ACL applies (Reader / Writer / Manager via the
parent DataObject's Collection).

### `:VideoAnnotation`

Neo4j node entity, one per time-segment annotation on a
`VideoStreamReference`.

| Field | Type | Notes |
|---|---|---|
| `appId` | string (UUID v7) | Server-minted on create. |
| `startSeconds` | double | Start of the interval, seconds from video start. Required. |
| `endSeconds` | double? | End of the interval. Null for **point annotations**. |
| `label` | string | Human-readable label, e.g. `ignition`, `burn`, `cooldown`. Required. |
| `description` | string? | Optional longer description. |
| `aiGenerated` | boolean | `true` when created by an automated detector. Default `false`. |
| `confidence` | double? | Confidence score `[0.0, 1.0]` from AI detection. Null for human-created. |

---

## REST surface

All endpoints live on the **`/v2/` shelf** (this fork's
development surface). Upstream shepard 5.2.0 has no video
payload kind; nothing lands on `/shepard/api/...`.

### VideoStreamReference

Path prefix: `/v2/data-objects/{dataObjectAppId}/video-stream-references`.

| Verb | Path | Description | Status |
|---|---|---|---|
| `GET` | (root) | List all references on a DataObject. | 200 |
| `POST` | (root) | Upload a video. Multipart form-data with a `file` part. Returns the new reference incl. ffprobe metadata. | 201 |
| `GET` | `/{appId}` | Fetch one reference's metadata. | 200 |
| `DELETE` | `/{appId}` | Delete a reference + its stored bytes. | 204 |
| `GET` | `/{appId}/download` | Download the raw video bytes. Streams from the active FileStorage adapter. | 200 |

### VideoAnnotation

Path prefix: `/v2/data-objects/{dataObjectAppId}/video-stream-references/{refAppId}/annotations`.

| Verb | Path | Description | Status |
|---|---|---|---|
| `GET` | (root) | List all annotations on a reference. | 200 |
| `POST` | (root) | Create a new annotation. | 201 |
| `GET` | `/{annotationAppId}` | Fetch one annotation. | 200 |
| `PATCH` | `/{annotationAppId}` | RFC 7396 merge-patch. Accepts `startSeconds`, `endSeconds`, `label`, `description`, `aiGenerated`, `confidence`. | 200 |
| `DELETE` | `/{annotationAppId}` | Delete an annotation. | 204 |

All endpoints require authentication and walk the DataObject →
parent Collection graph for the permission check
(`PermissionsService.isAccessAllowedForDataObjectAppId`).

---

## Worked examples

### Upload a video

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...
DATA_OBJECT_APPID=019e4e56-...

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -F "file=@tr-004-hotfire.mp4;type=video/mp4" \
  -F "name=TR-004 hotfire — exterior cam" \
  -F "description=Engine bay camera, 30 fps" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references"
```

Response (201):

```json
{
  "appId": "019e9a01-...",
  "name": "TR-004 hotfire — exterior cam",
  "description": "Engine bay camera, 30 fps",
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

### List references

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references" | jq .
```

### Annotate the ignition transient

```bash
REF_APPID=019e9a01-...

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

### Add an AI-detected anomaly with confidence

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "startSeconds": 8.0,
    "endSeconds": 8.4,
    "label": "anomaly:vibration-spike",
    "description": "Detected by AccelerometerAnomalyV2 (10g threshold)",
    "aiGenerated": true,
    "confidence": 0.93
  }' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/annotations"
```

### Stream the bytes back

```bash
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/video-stream-references/$REF_APPID/download" \
  -o tr-004.mp4
```

---

## `ffprobe` metadata extraction

The `VideoProbeService` shells out to `ffprobe` immediately
after the multipart upload completes. The extracted fields land
on the new `:VideoStreamReference` node:

| ffprobe source | Stored as |
|---|---|
| `format.format_name` (best-guess) | `mimeType` (fallback to client-supplied `Content-Type`) |
| `format.size` | `fileSizeBytes` |
| `format.duration` | `durationSeconds` |
| `format.tags.creation_time` (ISO-8601) | `wallClockTimestamp` (parsed → nanoseconds-since-epoch) |
| `streams[v:0].width` | `width` |
| `streams[v:0].height` | `height` |
| `streams[v:0].r_frame_rate` (e.g. `30000/1001`) | `frameRate` (decimal evaluation) |
| `streams[v:0].codec_name` | `videoCodec` |
| `streams[a:0].codec_name` | `audioCodec` |

If `ffprobe` is missing from `PATH`, the upload still succeeds —
the affected fields stay null. The plugin treats `ffprobe` as
best-effort enrichment.

---

## Temporal anchoring (TM1)

The `wallClockTimestamp` field connects the video to **TimeseriesReference**'s
TM1 time-reference model. When a `TimeseriesReference` carries a
`wallClockOffset` matching the video's recording time, frontend
viewers can overlay the timeseries chart on the video timeline
without per-channel manual alignment.

The format is **nanoseconds since the Unix epoch (UTC)** — the
same unit used throughout shepard for high-precision timestamps.
The parser accepts ISO-8601 with millisecond, microsecond, or
nanosecond precision; sub-nanosecond precision is truncated.

---

## Storage

Video bytes live in a shared storage container named
**`_shepard_videos`** — one container per deployment, not one
per reference. The active `FileStorage` adapter handles bucket
creation (`s3:`) or collection-creation (`gridfs:`) on first
write.

The `storageLocator` field is opaque — it carries the SPI
identifier prefix (`s3:` / `gridfs:`) and a backend-specific
locator. Clients should never construct or parse it.

---

## Plugin manifest

| Field | Value |
|---|---|
| `id` | `video` |
| `version` | `1.0.0-SNAPSHOT` |
| `shepardCompatibility` | `>=6.0.0-SNAPSHOT,<7` |
| `title` | `Video (VideoStreamReference)` |
| `licence` | `Apache-2.0` |

The plugin currently runs in **Phase 1 (VID1b phase 1)** — the
manifest declares the capability but the entity classes + REST
resources still live in the backend (extraction to the plugin
module is tracked under `VID1b-full`).

---

## Config keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.video.enabled` | `false` | Runtime toggle. `false` makes every video endpoint return 404. |

The plugin reuses `shepard.storage.*` keys via the shared
FileStorage adapter — no video-specific storage config keys exist.

A runtime-mutable `:VideoConfig` admin endpoint (`/v2/admin/video/config`)
is tracked under follow-up `VID-config`.

---

## MCP tools

The video plugin currently exposes **no MCP tools** of its own.
The generic `list_data_object_references` MCP tool surfaces
video references like every other reference type, and
`get_data_object` returns the embedded `videoStreamReferenceIds`
in the DataObject body.

---

## RO-Crate export

Video references appear in RO-Crate `ro-crate-metadata.json` as
`schema:VideoObject` contextual entities, with:

- `@id` — the shepard appId.
- `contentUrl` — the `/download` URL (consumers must authenticate
  separately).
- `duration` — ISO-8601 duration derived from `durationSeconds`.
- `encodingFormat` — `mimeType`.
- `height` / `width` — pixel dimensions.

Annotations are emitted as `schema:Comment` contextual entities
keyed off the video reference, with `startTime` / `endTime` in
ISO-8601 offset notation.

---

## Known pitfalls

- **`ffprobe` not on PATH**: upload succeeds but `durationSeconds` /
  `videoCodec` / `width` / `height` / `frameRate` are null. Check
  `which ffprobe` inside the backend container.
- **HEIC / unusual codecs**: `ffprobe` extracts what's there, but
  the in-browser playback (HTML5 `<video>` tag) supports a
  narrower codec set. Stick to H.264 + AAC for maximum compat.
- **Large uploads timing out**: presigned-upload URLs (FS1c) are
  the planned fix — track under `VID-presigned`. Until then,
  uploads stream through the backend.
- **`wallClockTimestamp` missing**: most cameras don't tag
  `creation_time` by default. Use `ffmpeg -metadata
  creation_time=...` to stamp before upload.

---

## Migrations

The plugin introduces no Cypher migrations. The Neo4j-OGM
session factory creates `appId` uniqueness constraints
automatically on first startup when the plugin is enabled.

---

## See also

- [`quickstart.md`](quickstart.md) — upload + annotate a video.
- [`install.md`](install.md) — operator setup.
- `aidocs/integrations/89-video-payload-kind.md` — VID1 design.
- [`docs/reference/timeseries.md`](../../../docs/reference/timeseries.md)
  — TM1 wall-clock anchoring.
- [HTML5 video codec support](https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Video_codecs).
- [ffprobe docs](https://ffmpeg.org/ffprobe.html).
