# FileReference Rename + Video Payload Kind — Design

**Scope.** Two coupled concerns that the user briefed together
because they touch the same payload-kind territory:

1. The `FileReference` entity is **misnamed and under-structured**.
   It is a *collection* of files (one Neo4j node fronting a
   `FileContainer` of N `ShepardFile` rows in GridFS), not a
   reference to one file. Casual workflows — a lab camera doing
   cyclic capture, a measurement rig dumping 1 000 frames into one
   "run" — produce hundreds of files inside one Reference with no
   navigable structure below the Reference level. The fix is a
   rename **and** a first-class grouping concept under the
   Reference.

2. A **new payload kind for video** (stored files **and** live
   streams). Video is the next payload kind after files /
   timeseries / structured / spatial, and it unlocks lab capture
   for the experiment-orchestration work in `aidocs/50` and the
   "record a 30-second clip of the test rig" casual workflow from
   `aidocs/42 §1.0`.

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. The rename touches a long-shipped
primitive — naming decision needs maintainer sign-off.

**Originating items.** User brief, 2026-05-12. Couples to:
`aidocs/42` (vision — primitives + payload-kind table),
`aidocs/47` (PayloadKind / PayloadStorage SPI — video lands as a
plugin candidate from day 1),
`aidocs/45` (FileStorage interface — segments-on-object-store
sits on FS1's seam),
`aidocs/40 §3` (sTC is the sibling capture tool; "shepard video
capture tool" is sketched as the same shape),
`aidocs/16` (backlog rows added later — VID1 for video, FR-rename
under L2-adjacent or new FR-series).

---

## Part 1 — FileReference rename + grouping

### 1.1 The naming problem, concretely

`FileReference` is the most-used payload kind, and the name does
not match the model. The wiring:

```
DataObject  ─(:has_reference)─►  FileReference  ─HAS_PAYLOAD─►  List<ShepardFile>
                                       │                              │
                                       └─IS_IN_CONTAINER─►  FileContainer (mongoId)
                                                                      │
                                              one Mongo collection per FileContainer,
                                              GridFS chunks under <oid>.files/.chunks
```

A `FileReference` is a *bag of files* fronted by one
`FileContainer`. Blob count per Reference ranges from 1 (PDF) to
1 000 (camera frame dump). Two distinct paper-cuts:

- **Naming.** New users expecting "points at one file" hit a
  list-shaped endpoint and have to mentally re-map. Casual users
  describing their data use the word in the singular even though
  the model is plural.
- **Structure.** A 1 000-frame cyclic-camera capture is one flat
  scrollable list. No "sub-run 1 / 2 / 3" navigation. Users either
  create N FileReferences and lose the "they belong together"
  semantic, or one FileReference and lose discoverability inside it.

### 1.2 Naming candidates

| # | Candidate | Pros | Cons |
|---|---|---|---|
| (i) | **`FileBundle`** | Short. Describes the model accurately (a bundle of files). Friendly for casual users. Distinguishes from `Reference` (the pointer concept) which `aidocs/42` already names. | Renames the *primitive*, not just the entity — the URL path and the API shape both move. Bigger blast radius. |
| (ii) | **`FileCollectionReference`** | Minimal change — still "Reference," still hyphen-compatible with `TimeseriesReference` / `StructuredDataReference`. Reads as "this Reference points at a collection of files." | Conflicts visually with `Collection` (the top-level primitive). Two "collections" in one sentence is painful. |
| (iii) | **`FileSetReference`** | Same upside as (ii) without the `Collection` clash. "Set" matches the bag-of-files reality. | "Set" implies uniqueness, which the model doesn't enforce; minor semantic miss. |

**Recommended: `FileBundle`** (option i), `Reference` suffix
dropped at the primitive level.

The four current "Reference" primitives all suffer the same
low-grade naming bug — "Reference" is a structural detail (the
edge from DataObject to payload, modelled as a node), not a
user-facing concept. A researcher thinks "I am uploading **a
bundle of files**," not "a FileReference." `Bundle` also aligns
with adjacent ecosystem vocabulary (RO-Crate "bundle," BagIt
"bag," BioCompute "bundle"). And for the inner-grouping concept
(§1.3) `FileGroup` reads cleanly: a `FileBundle` contains
`FileGroup`s, each `FileGroup` contains `ShepardFile`s.

The wire format on `/shepard/api/...` keeps the name
`FileReference` forever per `CLAUDE.md` §API-version-policy. The
rename is **internal + `/v2/`-surface only**.

### 1.3 Grouping concept — three shapes considered

A capture run of 1 000 frames needs sub-Reference structure.
Three shapes:

| # | Shape | Storage | Tradeoffs |
|---|---|---|---|
| (a) | **Path-prefix convention** — encode group in the filename (`run-01/frame-0001.png`); UI splits on `/`. | None — uses existing `ShepardFile.filename`. | Zero schema change. **But:** "group" is implicit; filtering by group is a substring match; clients can't query "give me group `run-01`" without scanning. UI behaviour becomes a sniff of filenames, which drifts. |
| (b) | **Metadata-keyed grouping** — a `group: string` field on `ShepardFile`; group is whatever the writer named it. | One new property on the Mongo doc. | Better — explicit, queryable. **But:** still flat. No group-level metadata (no "this group is sub-run 2, started at 14:23, ended at 14:35"). The group is just a label. |
| (c) | **First-class `FileGroup` sub-node** — Neo4j `FileGroup` node between `FileBundle` and `ShepardFile`. Each group carries `name`, `description`, `attributes`, `index`, optional `startedAt` / `endedAt`. | One new Neo4j label + one relationship type. Migration is additive (existing bundles get a single default group). | Best — symmetric with the Collection/DataObject pattern (Collections contain DataObjects, DataObjects contain References, **Bundles contain Groups, Groups contain Files**). Per-group permissions inherit from the Bundle (no new auth surface). |

**Recommended: shape (c), `FileGroup` as a first-class sub-node.**

(a) and (b) don't solve the "1 000 frames, no structure"
complaint — a group is a logical sub-run with its own start/end
and attributes, not a string label. (c) keeps permissions at the
Bundle level (Group is navigation + metadata, not a security
boundary — `PermissionsService` untouched), gives snapshots
(`aidocs/41`) and payload versioning (`aidocs/46`) a clean
`appId`-shaped anchor, and lets annotations (`aidocs/42`) pin to
a Group directly — "this Group is `phase = ramp_up`" is a more
natural target than annotating file 432.

### 1.4 The data-model shape

Neo4j:

```cypher
(:DataObject {appId})
  -[:has_reference]->
  (:FileBundle:Reference {appId, name, ...})
    -[:HAS_GROUP {index: <int>}]->
    (:FileGroup {appId, name, description, attributes, startedAt, endedAt})
      -[:HAS_PAYLOAD]->
      (:ShepardFile {mongoOid, filename, md5, byteSize})
```

The `FileBundle` continues to point at exactly one `FileContainer`
(today's `mongoId` / Mongo-collection-per-bundle layout, unchanged).
The `FileGroup` is a logical grouping in Neo4j; **bytes still live
in the bundle's one Mongo collection** so storage shape doesn't
fragment.

Backwards compatibility:

- The legacy `FileReference.files` direct list to `ShepardFile`
  stays in Neo4j for the upstream-API read path. The migration
  attaches a **default group** (`name: "default"`, `index: 0`) to
  every existing bundle and re-parents the files under that group.
- Upstream API readers see the flat list as before; `/v2/`
  readers see groups.

### 1.5 Migration

One Cypher migration, idempotent + fail-fast per `CLAUDE.md`:
`V16__Rename_FileReference_to_FileBundle_and_introduce_FileGroup.cypher`.

Steps:

1. Add `:FileBundle` label to every `:FileReference` node. Keep
   the legacy `:FileReference` label — upstream-API DAOs continue
   to query by that label.
2. For each `:FileBundle` lacking a `:HAS_GROUP` edge, create a
   default `:FileGroup` (`name: "default"`, `index: 0`, fresh
   `appId`) and attach `(bundle)-[:HAS_GROUP]->(group)`.
3. Re-parent existing `(bundle)-[:HAS_PAYLOAD]->(file)` edges
   under the default group. **Leave the bundle's direct
   `HAS_PAYLOAD` edges in place** as a compatibility shadow —
   the upstream-API read path uses them.
4. Add a unique-`appId` constraint on `:FileGroup` (mirrors V11).
5. Log progress every 1 000 bundles.

Rollback (`V16_R__*.cypher`): remove `:HAS_GROUP` edges and
`:FileGroup` nodes (only `name = "default"` with fresh appIds;
refuse rollback if any user-created groups exist); remove the
`:FileBundle` label.

**Tests (deferred but tracked):** pre/post testcontainer fixture
asserting one default group per bundle + no files leak;
forward+reverse rollback test asserting graph equals starting
state.

### 1.6 API surface

| Surface | Path | Behaviour |
|---|---|---|
| **Upstream-frozen** | `/shepard/api/collections/{c}/dataObjects/{d}/fileReferences/...` | Unchanged. Reads return the flat-files list as today. Writes accept the flat shape; the backend places new files under the bundle's default group. **Zero wire change** per `CLAUDE.md`. |
| **`/v2/` shelf** | `/v2/bundles/{appId}` | New `FileBundle` GET — returns the bundle with `groups: [...]` populated. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups` | Create / list groups. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups/{groupAppId}` | Group GET / PUT / DELETE. Listing files in the group via `files[]`. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups/{groupAppId}/files` | Upload file into a specific group. |

Naming on `/v2/`: `bundle` (not `fileBundle`) — the path segment
matches the user-facing noun. Same way `/v2/timeseries/...` would
drop the redundant `Reference` suffix if/when L2d formalises the
shelf.

### 1.7 Internal renames + docs

Java rename in the same slice: `FileReference.java → FileBundle.java`
(keep `@NodeEntity(label = "FileReference")` so OGM writes the
legacy label too), plus `FileReferenceService/DAO → FileBundleService/DAO`.
`FileReferenceIO` stays as the **legacy wire shape** for the upstream
surface; new `FileBundleIO` is the `/v2/` wire shape with
`groups: List<FileGroupIO>`. New `FileGroup{,Service,DAO,IO,Rest}` at
`/v2/bundles/{appId}/groups`.

Docs (per `CLAUDE.md`): `aidocs/42 §"What's in the box"` table
row updates; `docs/reference/file-reference.md` → `file-bundle.md`
with a Jekyll redirect; `docs/help/upload-data.md` reworded.

---

## Part 2 — Video payload kind

### 2.1 The model

Video is **not** a single file. A captured video is a sequence of
**segments** (HLS-shape: ~2-10 second `.ts` or `.mp4` slices) plus
a **manifest** (`.m3u8`). A live stream is the same shape
arriving incrementally. Two ingestion modes:

| Mode | Shape | Example |
|---|---|---|
| **Fully-uploaded** | One `.mp4` POSTed to shepard; manifest derived server-side. | Researcher records on a phone, drops the file in. |
| **Live stream** | Client POSTs segments as they arrive; manifest grows; `finalize` closes the stream. | Lab camera cyclic capture; WebRTC ingest from a workstation. |

Both modes produce the same on-disk shape — ordered segments +
manifest. The Reference (`VideoStream`, see §2.2) is the Neo4j-side
handle.

### 2.2 Naming

Following §1.2: drop the `Reference` suffix on the `/v2/` shelf.
**Recommended: `VideoStream`** (path segment `videos` —
`/v2/videos/{appId}`).

Why "Stream": it covers both stored-segments and live-segments;
HLS / DASH / WebRTC all use it. `VideoBundle` was considered for
parallelism with `FileBundle` but rejected — a video is one
continuous thing in the user's mental model, not a bundle.

### 2.3 Storage — three options

| # | Option | Pros | Cons |
|---|---|---|---|
| (a) | **Existing FileBundle path with object-store backing** — treat each segment as a `ShepardFile`, the bundle as the segment list. | Zero new storage code. Rides FS1 (`aidocs/45`) for S3 backing automatically. | The bundle / file ontology doesn't fit live ingestion (random segment writes during capture); HLS manifest assembly is hand-rolled per-read. |
| (b) | **Dedicated video payload-storage plugin** — segments-on-object-store, manifest generation, range-request server, all in one plugin behind the PayloadStorage SPI (`aidocs/47 §2.2`). | Right shape for HLS / live. Plugin shape isolates ffmpeg + WebRTC deps from the core. Frame-extract + range reads live in one place. | New storage path; more code. |
| (c) | **External streaming server** (MediaMTX / Ant Media / OpenVidu) with shepard storing only the metadata + stream-pointer. | Best for live WebRTC ingest (these tools exist for exactly this). | Two systems to operate; shepard loses bytes-under-management for the video data; permission enforcement crosses a process boundary. |

**Recommended: (b), with an optional (c) ingest adapter.**

(b) makes shepard the bytes-of-record for video — same posture
as every other payload kind, same backup story (`aidocs/45 §2.1 W3`),
same permission enforcement, same RO-Crate export. It drops into
the PayloadKind / PayloadStorage SPI cleanly — `aidocs/47 §2.2`'s
interfaces are already segment- / handle-shaped. (c) becomes an
**ingest-side detail**: an operator who wants WebRTC runs MediaMTX
as a sidecar; MediaMTX writes segments to shepard via the same
POST-a-segment API a shepard-side capture client uses. (a) was
the first instinct but doesn't survive contact with live ingest;
the fully-uploaded-MP4 case rides (a) as a degenerate-of-(b) for
VID1a, then the proper plugin lands at VID1b.

### 2.4 The "shepard video capture tool"

Sibling to `shepard-timeseries-collector` (sTC) per `aidocs/40 §3`.
**Not** built in this design — a separate repository, `shepard-video-collector` (sVC),
implementing:

- **Input.** WebRTC / RTSP / V4L2 (lab cameras over USB / GigE).
- **Encoder.** ffmpeg (transcode to H.264 if input isn't already);
  segment at configurable duration (default `PT4S`).
- **Output.** POSTs each segment to shepard via the §2.6
  endpoints; updates the manifest; `finalize` on EOF.
- **Failure handling.** Buffer segments locally if shepard is
  unreachable (same shape as sTC's per-source backpressure per
  `aidocs/40 §3.2`).

Minimum API surface in shepard backend that sVC needs (a strict
subset of §2.6):

```
POST   /v2/videos                     — claim a stream (return appId)
POST   /v2/videos/{id}/segments       — append a segment
POST   /v2/videos/{id}/finalize       — close the stream
```

That's three endpoints. Anything else (playback, frame extract,
navigation) is a reader concern, not a capture concern.

### 2.5 Navigation API

Two axes for "go to point in the video":

| Axis | Param | Format | Example |
|---|---|---|---|
| **Video-time** (offset into the recording) | `at` | ISO 8601 duration | `?at=PT5M30S` → 5 min 30 s in |
| **Wall-clock** (real-time the recording was running) | `atRealtime` | ISO 8601 timestamp | `?atRealtime=2026-05-12T14:23:00Z` |

Both resolve to the same internal coordinate: a **segment index +
intra-segment offset**. The wall-clock mapping uses per-segment
`recordedAt` metadata (§2.7). If a video has no wall-clock metadata
(e.g. user-uploaded MP4 with stripped timestamps), `atRealtime`
returns 400 with `video.no_wall_clock` (RFC 7807 per `aidocs/47
§4.6` / H4).

The resolved segment index becomes the **start segment** for
streaming; client-side HLS players seek within the segment.

### 2.6 Endpoints

All under `/v2/` per `CLAUDE.md`:

| Method + path | Purpose |
|---|---|
| `POST /v2/videos` | Mint a VideoStream entity; returns `appId` and the segment-upload URL prefix. Body carries optional initial metadata (`codec` hint, `recordedStartTime`, `targetSegmentDuration`). Permission-checked against the parent DataObject. |
| `POST /v2/videos/{id}/segments` | Ingest one segment. Body is the binary blob (`Content-Type: video/mp4` or `video/MP2T` for `.ts`); headers carry `X-Shepard-Segment-Index`, `X-Shepard-Segment-RecordedAt`. Append-only; rejects out-of-order indices unless `?allowReorder=true`. |
| `POST /v2/videos/{id}/finalize` | Mark stream complete. Server seals the manifest; further segment POSTs return `409`. |
| `GET  /v2/videos/{id}` | Metadata only — codec / width / height / fps / duration / segmentCount / recordedStartTime. |
| `GET  /v2/videos/{id}/playlist.m3u8` | HLS manifest. Browser-playable directly via `<video>` + hls.js. Manifest URIs point at `segments/{n}` below. |
| `GET  /v2/videos/{id}/segments/{n}` | Raw segment, range-request-capable (`Range: bytes=...`). Permission-checked once per session; subsequent range reads hit a short-lived signed-URL cache (TTL ≤ `PT5M`). |
| `GET  /v2/videos/{id}/frame?at=PT5M30S` | Single-frame extract (JPEG / PNG). Server-side ffmpeg per §2.9. Slow path; rate-limited. |
| `GET  /v2/videos/{id}/frame?atRealtime=...` | Same, wall-clock seek. |
| `DELETE /v2/videos/{id}` | Delete stream + all segments. Permission-gated; respects snapshot pinning (a snapshot-pinned video cannot be hard-deleted until the snapshot is dropped). |

**Compat surface.** `/shepard/api/...` gets **no video endpoints**.
Video is fork-only by definition (not in upstream 5.2.0).
`aidocs/34` row marks it `AWARE` (new endpoints; admin must
expose them through their ingress).

### 2.7 Metadata model

`VideoStream` Neo4j entity (extends `BasicReference`):

| Field | Type | Notes |
|---|---|---|
| `appId` | UUID v7 | Per L2a. |
| `codec` | string | "h264" / "h265" / "vp9" — populated post-finalize from ffprobe. |
| `width`, `height` | int | Pixels. |
| `fps` | float | Frames per second. |
| `bitrate` | long | Bits/sec; average. |
| `recordedStartTime` | Instant | Wall-clock at first-frame; user-provided or capture-tool-stamped. Nullable. |
| `duration` | Duration | PT-string; set on finalize. |
| `segmentCount` | int | Set on finalize. |
| `segmentDuration` | Duration | HLS slice target; e.g. `PT4S`. |
| `finalized` | boolean | False during ingest. |
| `manifestOid` | string | Pointer to the stored `.m3u8` blob. |

Per-segment metadata in MongoDB (sibling to `ShepardFile`):

| Field | Type | Notes |
|---|---|---|
| `oid` | ObjectId | Mongo doc ID. |
| `streamAppId` | UUID v7 | Parent VideoStream. |
| `index` | int | Segment number; 0-based. |
| `byteSize` | long | |
| `recordedAt` | Instant | Wall-clock of first frame of *this segment*. Nullable. |
| `videoOffsetMs` | long | Milliseconds into the video at this segment's first frame. Always set. |
| `durationMs` | int | This segment's duration. |
| `payloadHandle` | PayloadHandle | Opaque; resolved by the storage plugin (GridFS / S3). |

**Annotations + lab-journal pinning.** `aidocs/42`'s annotation
primitive attaches to a `VideoStream`. A "marker at t=42s" is
recorded as **video-time offset** (`videoOffsetMs: 42000`), not
wall-clock — wall-clock can be wrong (camera clock un-synced,
post-upload timestamps stripped), video-time is the durable
coordinate. Wall-clock is a convenience axis derived at read-time
from `recordedAt`. Segment-level annotations are an open question
(§2.13).

### 2.8 Permissions

`VideoStream` is a `BasicReference` like every other reference
kind. ACL inherits from the parent DataObject's
`Permissions` node. No new permission surface.

Segment-level permissions are **not** in scope. A user who can
read the stream can read every segment; a user who cannot read
the stream gets `403` on every segment URL. Segment reads consult
the same `PermissionsService.isAllowed` path as file reads.

### 2.9 Frame extract — ffmpeg in the backend image?

**Defer to VID1d.** Frame extract requires `ffmpeg` (~70 MB
image bloat + annual Trivy-flagged CVEs per `CLAUDE.md`
§"security gates"). The casual-user path doesn't need it —
hls.js plays HLS in the browser; "save current frame" is a
client-side canvas-snapshot one-liner. The cases that *do* need
server-side extract (programmatic thumbnails, AI keyframe analysis
per `aidocs/43`, batch poster rendering) are not v1.

VID1a-c ship without ffmpeg. VID1d adds `GET /frame?at=...` behind
`shepard.video.frame-extract.enabled` (default `false`) — the
plugin pattern from `aidocs/47 §1.2`. Operators who decline keep
a smaller image.

### 2.10 Infrastructure additions

| Component | Where | Status |
|---|---|---|
| **MediaMTX sidecar** (or similar — Ant Media, OpenVidu) | Compose profile `video-ingest`, off by default. | **Suggested**, not shipped in core. Operators who want WebRTC / RTSP relay enable the profile. MediaMTX writes segments to shepard via the §2.6 endpoints — same API a sVC desktop client uses. |
| **ffmpeg** in backend image | Plugin-only, behind `shepard.video.frame-extract.enabled`. | Defer to VID1d. |
| **MinIO** for segment storage | Already on the table per `aidocs/45` FS1. | Video segments ride the same object-store backing once FS1 is selectable. GridFS for v1 is fine — a 1-hour 1080p H.264 video at 4 Mbit is ~1.8 GB; GridFS handles this and the upgrade to S3 is mechanical (per §2.3 option b). |
| **hls.js** in the frontend | Frontend bundle dep, ~120 KB minified. | Required for browser playback in VID1c. |

### 2.11 Why this matters in shepard

Tied to `aidocs/42`'s primitives + the casual-user north star:

- **Lab capture.** `aidocs/50`'s experiment-orchestration vision
  (KUKA cells, PLC-driven runs) routinely captures rig video.
  Today that lives "on someone's NAS" — outside shepard's
  permission + provenance + RO-Crate surface.
- **Lab-journal evidence.** A journal entry today references a
  FileBundle of photos. With VideoStream it references a 30-second
  clip pinned to the right moment of the experiment.
- **Snap-dashboard companion.** `aidocs/43 §5.8`'s chat-driven
  analysis sits next to "look at the recording of the run."
- **RO-Crate.** `aidocs/31`'s selective-export extends cleanly: a
  per-stream toggle (full / poster-frame / metadata-only) drops
  into the existing `PerPayloadSelection` map (`aidocs/16 R2b`
  precedent).

### 2.12 Phasing — VID1 series

| ID | Slice | Size | Gate |
|---|---|---|---|
| **VID1a** | Fully-uploaded video (any common format: **MP4, MOV, AVI, MKV, WebM**) as a `VideoStream` wrapper entity pointing at one ShepardFile. `POST /v2/videos` (single-shot upload). `GET /v2/videos/{id}/playlist.m3u8` derives a one-segment manifest. No live ingest, no frame extract. Permission gating via existing path. **Wall-clock extraction on upload**: backend runs `ffprobe -v quiet -print_format json -show_format -show_streams` against the uploaded file; if `format.tags.creation_time` or `streams[0].tags.creation_time` is present, `recordedStartTime` is pre-populated automatically and `wallClockResolved: true` is returned. If absent (`wallClockResolved: false`), the frontend shows a post-upload prompt: "When did this recording start?" — the user picks a wall-clock timestamp (calendar + time with ms), which is PATCHed via `PATCH /v2/videos/{id}` with `{ "recordedStartTime": "..." }`. Once set, `atRealtime` seek and the SB2a/SB2d timeline overlay work correctly. | M | None (rides FileBundle infra; if §1 ships first the path is straight). |
| **VID1b** | Segments-as-storage plugin behind PayloadKind / PayloadStorage SPI per `aidocs/47 §2.2`. Multi-segment manifest. `POST /v2/videos/{id}/segments` + `finalize`. Random-segment range reads. ffmpeg / WebRTC still out of scope. | L | VID1a + PL1a + PL1c (the SPI must be solid). |
| **VID1c** | Frontend `/v2/videos/{id}` viewer with hls.js. Wall-clock navigation in the UI (`atRealtime`). | M | VID1b. |
| **VID1d** | Server-side frame extract via ffmpeg. Plugin sub-module; off by default. `GET /v2/videos/{id}/frame?at=...`. | M | VID1b + ffmpeg image-bake decision. |
| **VID1e** | `shepard-video-collector` (sVC) MVP — companion repo, push-based desktop / sidecar capture from WebRTC / RTSP / V4L2. Ships against the VID1b ingest API. Same shape as `aidocs/40 §3.3` sTC sequencing. | L | VID1b. Lives in a separate repo. |
| **VID1f** | MediaMTX compose profile + recipe in `docs/deploy.md` — operators get WebRTC / RTSP relay as opt-in. | S | VID1b + VID1e (sVC's recipe is the analogue). |

Recommended order: **§1 (FileBundle rename) → VID1a → VID1b →
VID1c → VID1d → VID1e → VID1f**. §1 first because the rename
clarifies the payload-kind taxonomy before a new kind lands; VID1a
proves the wire shape on the simplest possible path; VID1b moves
to the proper storage plugin; everything else stacks on top.

### 2.13 Open questions for the maintainer

The design intentionally does **not** decide:

1. **Compose default for MediaMTX.** Ship it as a default-on
   sidecar (operators get WebRTC ingest out of the box) or
   document-only / opt-in profile? Recommendation in this doc is
   opt-in (VID1f), but full operator-friendliness argues for
   default-on.
2. **WebRTC ingest in v1?** This doc punts WebRTC to sVC / MediaMTX
   in VID1e+. Is HTTP-PUT chunked upload via the §2.6 segment
   endpoints sufficient for v1, or is direct WebRTC into the
   backend required?
3. **Dedicated `VideoCollection` grouping concept?** §1's
   FileBundle/FileGroup story argues *no* — videos are one
   continuous thing per stream, and the segment list is the
   sub-structure. But "a Collection of related videos from one
   campaign" might want first-class treatment beyond just being
   N VideoStreams under one DataObject.
4. **Segment-level annotations.** §2.7 punts on whether each
   segment becomes a Neo4j node (annotation-anchorable) or whether
   annotations stick at the stream level with `videoOffsetMs`
   ranges. Pick the second to avoid Neo4j-node bloat (a 1-hour
   video at 4-second segments = 900 segment nodes); revisit if
   users actually want per-segment annotation.
5. **Transcode on ingest?** Today's plan: accept whatever the
   client sends, transcode on read if needed. Alternative:
   normalise to H.264 at ingest time. Trade-off is server CPU
   vs storage volume + browser-compat.
6. **VideoStream rename collision.** "Stream" already means
   something in `subscriptions` / SSE territory. Worth
   double-checking before locking the name.

### 2.14 Cross-references

- `aidocs/42 §"What's in the box"` — new payload-kind row for
  Video (post-VID1a).
- `aidocs/47 §2.2 / §2.5 / §3` — VideoStream lands as a plugin via
  the PayloadKind SPI; `shepard-plugin-video` is the second
  net-new plugin after HDF5 (`aidocs/35`) and Git (`aidocs/38`).
- `aidocs/45 §3.2 / §4` — segment storage rides FS1's S3 seam;
  presigned-URL pattern from §4 applies to segment reads.
- `aidocs/40 §3` — sVC is the sibling tool to sTC; same shape,
  different transport.
- `aidocs/50 §"experiment-orchestration"` — KUKA / lab-rig video
  is one of the headline reasons video exists.
- `aidocs/43 §"AI features"` — frame-extract + AI keyframe
  analysis live in AI1k or later; video is the prerequisite.
- `aidocs/31 §"selective export"` — per-stream RO-Crate export
  toggles fold into the existing per-payload selection map.
- `aidocs/16` — new **VID1** umbrella + sub-IDs; new **FR1**
  umbrella for the FileReference rename (or hung off L2-adjacent).

---

## 3. What this isn't

Counter-claims to keep the scope honest:

- **Not** a YouTube-clone. No transcoding pipeline beyond the bare
  minimum to make a video playable; no recommendations engine; no
  comments thread.
- **Not** a video editor. Trimming, splicing, overlaying captions
  are explicit non-goals. shepard stores; the editor is whatever
  the user already has.
- **Not** ffmpeg in every install. Frame extract is plugin-gated
  and off by default. An operator who never wants video gets the
  same image as today.
- **Not** a live-streaming platform. shepard is the *destination*
  for ingested segments + the *playback source* for stored video.
  Live broadcast to many viewers (transcoding ladders, CDN
  fan-out) is out of scope — that's MediaMTX's job, not shepard's.
- **Not** a security-sensitive surveillance store. shepard's
  per-Collection ACL is the right granularity for research-data
  video. Compliance-grade surveillance (CCTV evidence chain,
  signed-timestamping) is a different product.
- **Not** a breaking rename of `FileReference`. The upstream
  endpoint name and wire shape stay frozen per `CLAUDE.md`. Only
  the `/v2/` shelf + internal Java identifiers move.

---

## 4. Migrations + tracker rows

- **`V16__Rename_FileReference_to_FileBundle_and_introduce_FileGroup.cypher`**
  (§1.5) — add `:FileBundle` label, attach default `:FileGroup`,
  re-parent files. Idempotent, fail-fast.
- **`V16_R__*.cypher`** — rollback path.
- **`V17__Add_appId_constraint_FileGroup.cypher`** — unique
  constraint on `:FileGroup`.
- **`V18__Add_appId_constraint_VideoStream.cypher`** (VID1a) — unique
  constraint on the new `:VideoStream` label.
- No SQL migrations needed. Per-segment metadata is MongoDB
  (sibling Mongo collection to today's `FileContainer`); MongoDB
  doesn't need migrations for additive document shape.

**`aidocs/34` upgrade-path rows:**

- FileBundle rename → **AWARE.** Upstream API surface unchanged;
  internal Java identifiers move; `/v2/` adds new paths.
  Operator-visible: new `/v2/bundles/...` and `/v2/bundles/{id}/groups/...`
  endpoints. Operators who never call `/v2/` see no change.
- VID1a → **AWARE.** New `/v2/videos/...` endpoints. No config
  change required. Image size unchanged for VID1a (no ffmpeg).
- VID1d → **CONFIG.** `shepard.video.frame-extract.enabled` toggle.
  Image size grows by ~70 MB when enabled.
- VID1f → **AWARE.** New compose profile `video-ingest`.

**`aidocs/44` matrix rows:** new "video" payload-kind row under
the payload-kind section; new "FileBundle (renamed from
FileReference)" status note on the existing files row.

---

## 5. Cross-references

- **CLAUDE.md** — upstream-API freeze (§API-version policy);
  vision currency (§`aidocs/42`); matrix currency (§`aidocs/44`);
  upgrade-tracker currency (§`aidocs/34`); user-docs currency
  (§docs); migration shape (§upstream upgrade-path).
- **`aidocs/16`** — FR1 + VID1 series queueing entries follow this
  design (dispatcher reconciles).
- **`aidocs/34`** — three new AWARE / CONFIG rows above.
- **`aidocs/42 §"What's in the box"`** — payload-kind table updates
  (FileBundle row; new Video row post-VID1a).
- **`aidocs/44`** — matrix updates per §4.
- **`aidocs/45`** — FS1 FileStorage seam; segments ride the same
  backing.
- **`aidocs/47 §2`** — PayloadKind SPI; VideoStream is a plugin
  from VID1b.
- **`aidocs/49 §2.2`** — `docs/reference/file-bundle.md` (renamed
  from `file-reference.md`); new `docs/reference/video-stream.md`.
- **`aidocs/50`** — experiment-orchestration uses video.
- **`aidocs/40 §3`** — sTC is the model for sVC.
