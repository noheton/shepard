---
title: Install video plugin
---

# Installing `shepard-plugin-video`

The video plugin (`shepard-plugin-video`) provides the `VideoStreamReference`
payload kind and the `VideoAnnotation` graph entity. It is bundled in the
standard `shepard-backend-patched` image but is **disabled by default**
(`shepard.plugins.video.enabled=false`).

---

## Enabling the plugin

### Option 1 — environment variable (recommended)

```
SHEPARD_PLUGINS_VIDEO_ENABLED=true
```

Or in `application.properties`:

```properties
shepard.plugins.video.enabled=true
```

Restart the backend after the change. On startup:

1. `PluginRegistry` reads the toggle and marks the "video" capability ENABLED.
2. `VideoPayloadKind` registers `videostreamreference.model` and `v2.video.model`
   with the Neo4j OGM `SessionFactory` via the `PayloadKind` SPI.
3. CDI bean discovery activates `VideoStreamReferenceService`,
   `VideoStreamReferenceV2Rest`, `VideoAnnotationRest`, and the supporting DAOs.

Verify:

```
GET /v2/admin/plugins
```

Look for `"id": "video"` with `"state": "ENABLED"`.

---

### Option 2 — CLI

```
shepard-admin plugins enable video
```

---

## Prerequisites

### ffprobe (optional but recommended)

`VideoProbeService` calls `ffprobe` to extract video metadata (duration, codec,
resolution, frame rate, wall-clock timestamp). If `ffprobe` is absent the upload
succeeds but metadata fields are left null.

**Standard image**: `ffprobe` is included in `ffmpeg` which is installed in the
`shepard-backend-patched` image at `/usr/bin/ffprobe`.

**Custom image**: install `ffmpeg` and verify `ffprobe` is on `PATH`.

```dockerfile
RUN apt-get install -y ffmpeg
```

### File storage backend

Video bytes are stored via the active `FileStorage` adapter (MongoDB GridFS or S3).
At least one adapter must be enabled. If no adapter is configured, upload and
download endpoints return `503 Service Unavailable`.

The plugin uses the shared storage container **`_shepard_videos`** (one container
per deployment, not per reference). No manual container creation is needed — the
storage adapter creates it on first write.

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.video.enabled` | `false` | Runtime toggle. `false` disables all video endpoints without removing the JAR. |

No other deploy-time keys are introduced. Video storage uses the same
`shepard.storage.*` keys as the file plugin.

The video plugin does not yet have a runtime-mutable `:VideoConfig` endpoint.
A follow-up task (`VID-config`) will add `/v2/admin/video/config` parity.

---

## Bean indexing (standard image)

The standard image's `application.properties` already includes:

```properties
quarkus.index-dependency.shepard-plugin-video.group-id=de.dlr.shepard.plugins
quarkus.index-dependency.shepard-plugin-video.artifact-id=shepard-plugin-video
```

These entries tell Quarkus to scan the plugin JAR for CDI beans at build time.
Operators building a custom image with `-DnoPlugins` and adding the JAR at
runtime must ensure these two lines are present, or Quarkus will not discover
the plugin's `@RequestScoped` beans.

---

## Neo4j entities

The plugin introduces two Neo4j node labels:

| Label | Description |
|---|---|
| `:VideoStreamReference` | One node per uploaded video file, linked to a `:DataObject`. Carries name, MIME type, file size, storage locator, and ffprobe metadata fields. |
| `:VideoAnnotation` | One node per time-segment annotation on a reference. Carries `startSeconds`, `endSeconds` (nullable), `label`, `description`, `aiGenerated`, and `confidence`. |

No Neo4j schema migrations are shipped with VID1b. The OGM session factory
creates `appId` uniqueness constraints automatically on first startup when the
plugin is enabled.

---

## REST endpoints

All endpoints require authentication. Permission checks walk the DataObject →
parent Collection graph and call `PermissionsService.isAccessAllowedForDataObjectAppId`.

### VideoStreamReference

| Method | Path | Permission | Description |
|---|---|---|---|
| `GET` | `/v2/data-objects/{doAppId}/video-stream-references` | Read | List all references on a DataObject. |
| `POST` | `/v2/data-objects/{doAppId}/video-stream-references` | Write | Upload a video file (multipart). |
| `GET` | `/v2/data-objects/{doAppId}/video-stream-references/{appId}` | Read | Get one reference by appId. |
| `DELETE` | `/v2/data-objects/{doAppId}/video-stream-references/{appId}` | Write | Delete a reference and its stored bytes. |
| `GET` | `/v2/data-objects/{doAppId}/video-stream-references/{appId}/download` | Read | Download raw video bytes. |

### VideoAnnotation

| Method | Path | Permission | Description |
|---|---|---|---|
| `GET` | `/v2/data-objects/{doAppId}/video-stream-references/{refAppId}/annotations` | Read | List all annotations on a reference. |
| `POST` | `/v2/data-objects/{doAppId}/video-stream-references/{refAppId}/annotations` | Write | Create a new annotation. |
| `GET` | `/v2/data-objects/{doAppId}/video-stream-references/{refAppId}/annotations/{annAppId}` | Read | Get one annotation by appId. |
| `PATCH` | `/v2/data-objects/{doAppId}/video-stream-references/{refAppId}/annotations/{annAppId}` | Write | Partial-update an annotation. |
| `DELETE` | `/v2/data-objects/{doAppId}/video-stream-references/{refAppId}/annotations/{annAppId}` | Write | Delete an annotation. |

---

## Disabling the plugin

```properties
shepard.plugins.video.enabled=false
```

Or at runtime (when the `:VideoConfig` admin endpoint ships):

```http
PATCH /v2/admin/plugins/video
{"enabled": false}
```

When disabled:
- All `/v2/data-objects/{doAppId}/video-stream-references` and `/annotations`
  endpoints return `404`.
- Existing `:VideoStreamReference` and `:VideoAnnotation` nodes in Neo4j are
  preserved; no data is removed.
- The `_shepard_videos` storage container is not touched.

---

## Known pitfalls

- **Plugin disabled but beans referenced**: If `shepard.plugins.video.enabled`
  is `false` and another plugin tries to inject `VideoStreamReferenceDAO`, CDI
  will fail to resolve the dependency at startup. Keep the plugin enabled or
  remove any cross-plugin injections before disabling.
- **ffprobe not on PATH**: Upload succeeds, but duration / codec / resolution
  fields are null in the response. Check `which ffprobe` inside the container.
- **503 on upload**: No active file storage adapter. Enable MongoDB or S3 storage
  first via the standard `shepard.storage.*` configuration.
- **`_shepard_videos` container missing in S3**: The container (bucket key prefix)
  is created on first write. If IAM permissions only allow `PutObject` on specific
  keys, ensure the policy covers `_shepard_videos/*`.

---

## Verify the install

```
GET /v2/data-objects/{anyDoAppId}/video-stream-references
```

Returns `200 []` (empty list) for a valid DataObject when the plugin is active.
Returns `404` when the plugin is disabled.
