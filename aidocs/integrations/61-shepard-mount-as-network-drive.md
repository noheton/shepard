# shepard Mount as a Network Drive — Concept Design

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. Read-only WebDAV mount as the
lowest-friction shepard access mode.

**Originating items.** User brief: "another idea can we map Shepard
somehow to a network drive (for now for read only access). goal is
to mount Shepard." Couples to: `aidocs/42` (vision — casual-user
north star; "I want a path, not an API client"), `aidocs/45`
(FileStorage SPI — presigned-URL delivery for `GET` of payloads),
`aidocs/47` (plugin SPI — the mount talks through PayloadStorage
adapters), `aidocs/49` (in-app docs — mount-instructions page per
OS), `aidocs/51` (instance-admin + API-key roles — the auth shape),
`aidocs/53` (FileBundle + FileGroup rename — the file-tree the
mount exposes), `aidocs/55` (PROV-O provenance — mount reads emit
Activity rows), `aidocs/56` (`/v2/` flat appId paths — the URL
hierarchy), `aidocs/58` (`:CollectionProperties` — `webdavVisible`
opt-out flag), `aidocs/60` (Edge — Edge instances also expose
`/v2/webdav`).

---

## 1. The "shepard mount" concept in one paragraph

**A researcher opens File Explorer / Finder / `ls` on their
workstation and sees shepard's Collection tree as a regular
filesystem.** No API client, no `pip install`, no token-juggling —
just a path you can hand to Python, Excel, MATLAB, R, or a
colleague's laptop. The mount is a thin WebDAV server in front of
shepard's existing read APIs; the OS treats it as a network share;
the user treats it as a folder. v1 is **read-only** — drag-out-to-
copy works; drag-in-to-write doesn't yet. The shape:

```
/mnt/shepard/
  ├── LUMEN-Inspired Hot-Fire Test Campaign — Q3 2024/
  │   ├── _README.md             ← auto-generated from Collection description
  │   ├── TR-001 — cold-flow/
  │   │   ├── _metadata.json     ← DataObject attributes + appId + createdAt
  │   │   ├── timeseries/        ← TimeseriesReference payloads as CSV/Parquet
  │   │   ├── files/             ← FileBundle contents (per FileGroup grouping)
  │   │   ├── structured/        ← StructuredDataReference payloads as JSON
  │   │   ├── spatial/           ← SpatialDataReference payloads as GeoJSON
  │   │   ├── videos/            ← VideoReference payloads as HLS / MP4
  │   │   ├── semantic-annotations.ttl
  │   │   ├── lab-journal.md     ← lab-journal entries rendered as markdown
  │   │   └── provenance.json    ← recent PROV-O Activity for this DataObject
  │   ├── TR-002 — anomaly/
  │   └── ...
  └── ...
```

Every directory carries a hidden `.shepard-appId` file so a tool
that walks the tree can recover the shepard identity after a slug
rename. Every payload is streamed from the underlying storage —
GridFS, S3 (via FS1), HSDS (via A5), TimescaleDB — through the
existing PayloadStorage SPI shape (`aidocs/47 §2`). Nothing in the
mount layer needs to know which backend a payload lives in.

---

## 2. Why this matters

Two researcher reflexes the mount addresses directly:

| Reflex | Today | With the mount |
|---|---|---|
| **"I want this CSV / image / Excel sheet in my analysis script — give me a path."** | `pip install shepard-client`; pin a version; `client = Client(...)`; `client.collections.get(appId).dataobjects.get(...).timeseries["pressures"].to_pandas()`. Hours of yak-shaving for a casual user. | `pd.read_csv("/mnt/shepard/LUMEN.../TR-001.../timeseries/pressures.csv")`. One line. No client. |
| **"Let me hand a folder to a colleague / partner who doesn't use shepard's UI yet."** | "Here's the API docs; here's how you get a key; here's how you list a Collection's children…" | "Mount `https://shepard.dlr.de/v2/webdav/`. Username's the same as your shepard login. Drag what you need." |

The vision link: `aidocs/42 §1.0` says **casual users come first**.
The Python client (option 2 in `aidocs/42 §"How you actually use
it"`) is already a smaller cognitive load than the raw REST API,
but it still has the *installable software* tax. The mount is the
**lowest-friction access mode** below even the REST API — zero
client install on every Windows / macOS / GNOME machine. It is the
right answer to the casual user who says "I just want the file."

It is **not** the right answer for bulk programmatic access — for
that the convenience client (`aidocs/27`) and the SQL side door
(`aidocs/29 §P10`) stay canonical. The mount is the *fall-out*
access mode: easy because it's a folder, capped by what makes
sense over WebDAV.

---

## 3. Protocol options — pick one

Five candidate protocols evaluated against three criteria: **native
support on Windows / macOS / Linux without extra software**;
**reuses shepard's existing HTTPS / auth chain**; **doesn't introduce
a CVE-prone server surface**.

| # | Protocol | Native Windows | Native macOS | Native Linux | Reuses HTTPS / auth | CVE risk | Verdict |
|---|---|---|---|---|---|---|---|
| **A** | **WebDAV (RFC 4918)** | Yes (`net use`, Explorer "Add network location") | Yes (Finder → Connect to Server) | Yes (`davfs2`, GNOME / KDE / Nautilus `gvfs-mount`, `cadaver`) | Yes — HTTP-based, same TLS / auth chain as REST | Low — runs in-process inside shepard's existing JAX-RS stack | **Recommended for v1.** |
| **B** | SMB / CIFS | Yes (Explorer) | Yes (Finder) | Yes (`mount.cifs`) | No — separate protocol, separate auth | High — historically a major CVE source (EternalBlue, etc.); a complex server surface | Skip. Wrong shape for an HTTP-shaped backend; ops nightmare. |
| **C** | NFSv4 | Limited (NFS Client for Windows is an enterprise-only feature) | Yes | Yes | No — separate auth (Kerberos or `sys`); separate port | Medium — Linux-only audience for casual users | Skip for v1. |
| **D** | FUSE userspace filesystem (`shepard-fuse`) | No (FUSE on Windows requires WinFsp or Dokan install) | Yes (macFUSE install) | Yes (kernel FUSE) | Yes (talks to REST) | Low — but client install required on every researcher's machine | **Defer to v2.** Most flexible but kills the "no client install" promise. |
| **E** | SFTP | Yes (Windows 10+ has built-in client; mounting requires WinFsp+sshfs) | Yes (Finder mounts via `osxfuse` / GUI clients like Cyberduck) | Yes (`sshfs`) | Partial — SSH key infrastructure separate from shepard's existing JWT / API key | Medium — full SSH server surface | **Defer to v2.** Feels like a remote shell, not a drive; mounting is GUI-client-mediated on most platforms. |

**Pick: (A) WebDAV.** Reasons in priority order:

1. **Zero extra software on every casual-user platform.** Windows
   ships `net use https://... /persistent:yes`; macOS Finder has
   "Connect to Server" with `https://` prefix; GNOME / KDE /
   Nautilus mount WebDAV through `gvfs-mount`. `davfs2` is in
   every major Linux distro's package manager.
2. **HTTPS reuse.** WebDAV is HTTP plus extra verbs. shepard's
   existing TLS termination, JWT validation, JAX-RS filter chain,
   and OpenAPI scaffolding apply unchanged. Auth flows through the
   same `JWTPrincipal` / API-key path that already covers the REST
   API (`aidocs/51`).
3. **Mature Java libraries.** **Apache Milton** (MIT / Apache-2.0,
   maintained, used in production by Sakai / OpenCMS / others)
   gives us a `ResourceFactory` SPI we plug shepard's resolver
   into; we don't hand-roll RFC 4918's `PROPFIND` XML parsing.
   Alternative: Jakarta WebDAV is leaner but less battle-tested.
4. **Range requests come free.** RFC 7233 range support is part
   of HTTP, which means a 4 GB FileBundle download streams without
   special handling, and the FileStorage SPI's existing range
   path (`aidocs/45 §3.1`) flows through.

Cite the spec: **RFC 4918** (HTTP Extensions for Web Distributed
Authoring and Versioning). Library: **Apache Milton 2.7+**
(`io.milton:milton-server-ce`, MIT/Apache-2.0).

---

## 4. Endpoint surface — `/v2/webdav/...`

Per the `CLAUDE.md` API-version policy and `aidocs/56` flat-appId
shape, the mount lives on the `/v2/` shelf with appId-indexed
paths.

### 4.1 Methods served

| Method | Path | Behaviour |
|---|---|---|
| `OPTIONS` | `/v2/webdav/{...}` | Returns `Allow: OPTIONS, GET, HEAD, PROPFIND` + `DAV: 1` header (level-1 compliance, no LOCK). |
| `PROPFIND` | `/v2/webdav/` | Lists root → enumerates Collections the caller has READ permission on. Honors `Depth: 0` / `1` / `infinity` (capped at depth-2 for `infinity` to bound the response). |
| `PROPFIND` | `/v2/webdav/{collectionAppId}/` | Lists DataObjects in the Collection. |
| `PROPFIND` | `/v2/webdav/{collectionAppId}/{dataObjectAppId}/` | Lists synthetic files + reference subdirectories (timeseries/, files/, structured/, spatial/, videos/) + child DataObjects. |
| `PROPFIND` | `/v2/webdav/{collectionAppId}/{dataObjectAppId}/files/` | Lists FileGroups as sub-directories. |
| `PROPFIND` | `/v2/webdav/{collectionAppId}/{dataObjectAppId}/files/{groupName}/` | Lists individual files in the FileGroup. |
| `GET` / `HEAD` | `/v2/webdav/{...}/file.csv` | Streams the payload. Range requests honored. Delegates to FileStorage SPI; if the plugin returns `Optional.of(presignedUrl)` we `307`-redirect rather than proxy the bytes (per `aidocs/45 §3.2`). |
| `PUT` / `POST` / `MKCOL` / `MOVE` / `COPY` / `DELETE` / `LOCK` / `UNLOCK` / `PROPPATCH` | `/v2/webdav/...` | All return **`403 Forbidden`** with a problem+json body per `aidocs/H4`: `{"type": "...", "title": "Read-only", "detail": "v1 of /v2/webdav/ is read-only. Write support is tracked at MNT1i."}`. |

### 4.2 PROPFIND response shape

Milton handles the XML emission. Each resource carries:

- `displayname` — slugified, human-readable name.
- `getcontentlength` — byte size for files; absent for collections.
- `getcontenttype` — MIME (e.g. `text/csv`, `application/json`,
  `text/markdown`, `image/png`).
- `getlastmodified` — `updatedAt` from the underlying entity.
- `getetag` — SHA-256 prefix of (entity revision, payload version)
  per `aidocs/46` versioning. Stable across mount sessions; clients
  short-circuit re-reads.
- `creationdate` — `createdAt`.
- `resourcetype` — `<collection/>` for directories, empty for files.

shepard-specific properties (under namespace
`xmlns:s="https://noheton.github.io/shepard/webdav/ns"`):

- `s:appId` — the entity's appId (so a smart WebDAV client can
  re-correlate after a rename).
- `s:kind` — `collection` / `dataobject` / `timeseries-reference`
  / `file-bundle` / `file-group` / `file` / `lab-journal` / …
- `s:hasReadPermission` — `true` (filtered server-side; absent
  entities never appear).

### 4.3 Auth

Three shapes, all already supported by the JAX-RS auth filter chain:

| Header shape | Audience | Notes |
|---|---|---|
| `Authorization: Bearer <jwt>` | Browser-mounted scenarios where the JWT cookie also rides | Default for in-app discovery. |
| `Authorization: Bearer <api-key>` | Python / CLI users who already have an API key | Long-lived; works directly. Documented in §10. |
| `Authorization: Basic <base64(user:webdav-password)>` | Casual users mounting via Finder / Explorer | Windows / macOS WebDAV dialogs only know HTTP Basic. The "webdav password" is a thin wrapper over an API key — see §6. |

Permissions enforcement: every `PROPFIND` filters the listing
through `PermissionsService.isAllowed(principal, entity, READ)`
exactly like every other REST endpoint. An entity the caller can't
see never appears in the directory listing — there is no
information-leak through filename existence.

---

## 5. Mapping shepard's graph onto a tree

The mount has to project shepard's **graph** (Collection →
DataObjects-with-children → References → Payloads, plus
cross-cutting Lab-Journal entries and PROV-O Activity) onto a
**tree**. Five conventions.

### 5.1 Filenames

- **Human-readable name, slugified.** `unicode_to_ascii_slug(name)`
  applied at the directory level: spaces stay (filesystems handle
  them), reserved characters (`/ \ : * ? " < > |`) replaced with
  `-`, normalized to NFC, length-capped at 200 chars per segment.
- **Collision resolution.** Two siblings with the same slug get
  `-{appId-prefix-8}` appended deterministically. The lexically-
  first wins the un-suffixed name; the second gets the suffix.
  Stable across sessions.
- **Reserved prefix.** Anything starting with `_` (single
  underscore) is a synthetic file generated by the mount, not a
  real shepard payload. Reserved prefix advertised in
  `/help/mount-shepard.md`.

### 5.2 Hidden `.shepard-appId` marker

Every directory contains a hidden `.shepard-appId` file holding
the entity's appId as a single UTF-8 line. This is the tree's
escape hatch for tooling that wants to round-trip a path back to a
shepard identity after a rename — `find /mnt/shepard -name
.shepard-appId -exec grep -l "$APPID" {} +` is the unix-pipe shape
that survives slug churn.

### 5.3 Synthetic files at the directory level

Each Collection / DataObject directory carries up to four synthetic
files generated server-side:

| File | Source | Format | Cache |
|---|---|---|---|
| `_README.md` | Collection / DataObject description field | Markdown | `Cache-Control: max-age=60` |
| `_metadata.json` | Full attribute set + appId + createdAt + createdBy + tags | Pretty-printed JSON | `Cache-Control: max-age=60` |
| `lab-journal.md` | Concatenated lab-journal entries (post-J1a markdown rendering) | Markdown | `Cache-Control: max-age=60` |
| `provenance.json` | Last 30 PROV-O Activity entries scoped to the entity (per `aidocs/55`) | JSON | `Cache-Control: max-age=30` |

These never hit the heavy payload SPI; they're stitched from
metadata the backend already has in memory.

### 5.4 Reference payloads as files / subdirectories

Per-payload-kind layout. Default formats picked for the
lowest-common-denominator analysis-tool consumer.

| Reference kind | Layout | Default format | Alternates via query param |
|---|---|---|---|
| **TimeseriesReference** | `timeseries/{name}.csv` | CSV | `timeseries/{name}.csv?format=parquet`; `?format=ndjson` |
| **FileBundle** (post-`aidocs/53` rename) | `files/{groupName}/{filename}` — each FileGroup surfaces as a sub-directory | Pass-through (file's original MIME) | n/a |
| **StructuredDataReference** | `structured/{name}.json` | JSON | n/a |
| **SpatialDataReference** | `spatial/{name}.geojson` | GeoJSON | `?format=wkt`; `?format=geopkg` (later) |
| **SemanticAnnotationReference** | `semantic-annotations.ttl` — **one combined file** per DataObject, all annotations | Turtle | `?format=jsonld`; `?format=rdfxml` |
| **VideoReference** (post-`aidocs/53`) | `videos/{name}.m3u8` (always) + `videos/{name}.mp4` (lazy server-side concat from HLS segments, gated `shepard.video.mount.compose=true`) | HLS manifest | MP4 (lazy concat) |
| **GitReference** (post-`aidocs/38`) | `git/{name}.url` — single-line text file with the pinned commit URL; not a checkout | URL stub | n/a |
| **HdfReference** (post-`aidocs/35`) | `hdf/{name}.h5.url` — HSDS link stub | URL stub | n/a (heavy bytes flow via HSDS / `h5pyd`) |

### 5.5 Children DataObjects

The existing Neo4j parent / child shape projects naturally:
child DataObjects appear as sub-directories of their parent
DataObject's directory. Depth is whatever the graph carries; the
`PROPFIND Depth: infinity` cap (§4.1) keeps a pathological deep
tree from blowing up.

---

## 6. Auth + the "webdav password" UX

The auth shape splits by audience. API users with a long-lived API
key paste it directly into `Authorization: Bearer`. **Casual
researchers mounting via Finder / Explorer can't.** Two reasons:

1. The Finder / Explorer auth dialog speaks HTTP Basic only, not
   Bearer.
2. A 64-character API key pasted into a Finder password field is a
   user-experience disaster — it's invisible in the dialog and
   ends up in the macOS Keychain / Windows Credential Manager
   under a synthetic entry the user can't find later.

### 6.1 New endpoint — `POST /v2/me/mount-credentials`

Issues a **time-limited "webdav password"** that's a thin wrapper
over an API key:

```http
POST /v2/me/mount-credentials
Authorization: Bearer <user-jwt>
Content-Type: application/json

{
  "label": "MacBook Pro at home",
  "ttlHours": 24
}
```

Response:

```json
{
  "username": "j.smith",
  "password": "wmnt_2026-05-12_kFp7-9Hj2-Lq8m-Rt5w-Xz3v",
  "expiresAt": "2026-05-13T14:00:00Z",
  "scope": "webdav-read",
  "revokeUrl": "/v2/me/mount-credentials/{credId}"
}
```

Properties:

- **One-shot reveal.** Password is shown exactly once in the
  response; never returned again. Mirrors the API-key mint UX from
  `aidocs/51 §3.3`.
- **Time-limited.** Default 24h; max 30 days (configurable via
  `shepard.webdav.mount-credential.max-ttl-hours`); admin-tunable.
  Expired credentials surface as 401 + `WWW-Authenticate: Basic`
  → the mount asks the user to re-auth.
- **Scoped.** The credential carries `scope: "webdav-read"` —
  it can ONLY be used for `/v2/webdav/...` and **only with GET /
  HEAD / PROPFIND / OPTIONS**. A leaked webdav password cannot
  mint other API keys, can't write, can't escape the WebDAV path
  prefix.
- **Revocable.** Listed under `/v2/me/credentials`; `DELETE`
  revokes immediately. Frontend `/me/mount` page (MNT1h) renders
  the list with "issued at / last used at / revoke" actions.
- **Keychain-friendly.** The 30-character format
  (`wmnt_YYYY-MM-DD_xxx-xxx-xxx-xxx-xxx`) is small enough to read
  back from a keychain entry; the date prefix makes "which one
  expired" trivial to eyeball.

Storage: a `WebdavMountCredential` row in Postgres (NOT Neo4j —
this is a session-class artefact, not a permission edge), keyed by
SHA-256 of the password. The plaintext leaves the backend exactly
once.

### 6.2 Permissions enforcement

The credential maps to the user's *current* permissions at request
time, not at mint time. Revoke a user's READ on a Collection →
their mount stops listing it immediately (within
`shepard.permissions.cache.ttl`, default 60s). The webdav-password
is not a permission grant; it's an auth shim.

---

## 7. Performance + caching

WebDAV clients are *chatty*. A Finder window opening a directory
fires one `PROPFIND` plus one `GET` per icon-preview-worthy file.
Three mitigations.

### 7.1 PROPFIND memoisation

Per-request `PROPFIND` results memoised in a request-scoped cache
keyed by `(principal, path, Depth)`. Identical re-requests within
the same HTTP keep-alive window served from cache. The Collection
list rarely changes within a 5-minute Finder session; this collapses
the chatter without a server-side TTL store.

### 7.2 HTTP cache headers on synthetic + binary files

- Synthetic files (`_README.md`, `_metadata.json`, `lab-journal.md`)
  → `Cache-Control: max-age=60, private` + `ETag`. Re-reads inside
  a minute hit 304 Not Modified.
- Real payloads → `ETag` keyed by `(entity revision, payload
  version)` per `aidocs/46`. `If-None-Match` re-reads short-circuit
  to 304 cheaply.
- `Cache-Control: no-cache, must-revalidate` on `provenance.json`
  (it's a feed, not an archive).

### 7.3 Streaming + size caps

- **Timeseries CSV** generated by streaming from TimescaleDB
  through a chunked HTTP response — never materialised into a
  byte[] before the first byte. Cap: `shepard.webdav.timeseries.
  max-bytes-per-response = 100MB`. Past the cap, the response
  truncates and a final `# TRUNCATED at 100MB — use ?from=...&
  to=...` comment ends the file. (CSV's "first line is a header"
  shape tolerates trailing content; a script that pandas-reads it
  will see one extra row that fails type parsing — *intentional*,
  so silent truncation is impossible.) Alternative: `?paginate=
  by-hour` flag emits a multi-file directory.
- **Range requests** delegated to the PayloadStorage SPI. GridFS
  supports range natively (`aidocs/45 §3.1`); S3 supports range
  natively; the FS1 abstraction passes the byte range through
  unchanged.
- **Presigned-URL redirect.** When the FileStorage plugin returns
  `Optional.of(url)` for `GET`, the mount responds `307 Temporary
  Redirect` with `Location: <url>` rather than proxying. The WebDAV
  client follows the redirect and pulls bytes direct from S3 / MinIO
  / Azure — shepard's bandwidth bill drops to zero for that download.

### 7.4 Per-Collection enable/disable

`:CollectionProperties.webdavVisible` (boolean, default `true`,
sourced from `aidocs/58 §5`) lets a Collection-owner opt their
Collection out of WebDAV exposure even when the server has WebDAV
enabled globally. Filtered at PROPFIND time alongside the
permission check.

---

## 8. Observability + provenance

Every successful `GET` and `PROPFIND` emits a PROV-O **Activity**
row per `aidocs/55`:

```json
{
  "appId": "...",
  "actionKind": "READ",
  "targetKind": "Collection|DataObject|FileBundle|Timeseries|...",
  "targetAppId": "...",
  "actor": { "username": "j.smith", "via": "webdav" },
  "summary": "WebDAV mount read",
  "mountSession": "f7a3b2-4c8d-...",   // ULID grouping the session
  "timestamp": "2026-05-12T10:34:21Z"
}
```

Volume note: a moderate Finder browse generates **50-200** reads.
The `mountSession` field is the **collapse-key** for the activity
dashboard — the per-Collection activity feed shows ONE row per
session (expandable to the per-read detail), not 200. A session
ID is minted on the first WebDAV request from a `(principal,
remoteIp, userAgent)` triple, refreshed after a 10-minute idle gap.

Per-method observability:

- `PROPFIND` rows tagged `actionKind: READ`, `targetKind:
  ContainerListing`.
- `GET` of a payload tagged `actionKind: READ`, `targetKind` set
  to the payload-kind.
- `OPTIONS` / `HEAD` not logged (handshake noise).
- `403` write attempts logged at `WARN` with the attempted method
  and path; not stored as Activity rows (we don't want a denial-
  of-service vector for the activity table).

Grafana panel: **"WebDAV reads / minute"** + **"WebDAV bandwidth /
minute"** + **"WebDAV unique users (5m)"** added to the `shepard
— Overview` dashboard per `aidocs/59` PERF1.

---

## 9. Phasing — MNT1 series

Nine slices, each independently mergeable. **MNT1a — MNT1g** are
the backend; **MNT1h** is the frontend; **MNT1i** is deferred.

| Slice | Scope | Dependencies | Est. eng |
|---|---|---|---|
| **MNT1a** | Apache Milton dep + Quarkus `WebdavResource` skeleton + `PROPFIND` on `/v2/webdav/` for Collections. No payloads. ArchUnit fence: nothing under `/v2/webdav/` accepts non-read methods. | None | 1.5w |
| **MNT1b** | `GET` for `_README.md` / `_metadata.json` synthetic files at Collection + DataObject scope. ETag + cache headers wired. | MNT1a | 0.5w |
| **MNT1c** | `POST /v2/me/mount-credentials` + Postgres `WebdavMountCredential` table + the Basic-auth shim that maps webdav-password → user identity. Revoke list at `/v2/me/credentials`. | MNT1a, A0 (`aidocs/51` shipped) | 1w |
| **MNT1d** | FileBundle / FileGroup → directory + file. Real payloads via the FileStorage SPI. Presigned-URL `307` redirect when the plugin returns one. Range requests. | MNT1a, `aidocs/53` FileBundle rename, `aidocs/45` FS1a FileStorage SPI | 1w |
| **MNT1e** | TimeseriesReference CSV / parquet generation. 100MB cap; `?format=` switch. Streaming through `StreamingOutput`. | MNT1a | 1w |
| **MNT1f** | StructuredDataReference (JSON), SpatialDataReference (GeoJSON), SemanticAnnotationReference (Turtle / JSON-LD) rendering. `lab-journal.md` synthesis. | MNT1a, `aidocs/48` semantic-repo (for SemanticAnnotationReference render) | 1w |
| **MNT1g** | PROV-O Activity row emission per `aidocs/55`; `mountSession` grouping; Grafana dashboard panel. | MNT1a, `aidocs/55` PROV1a-PROV1c | 0.5w |
| **MNT1h** | Frontend `/me/mount` page — shows the WebDAV URL, per-OS mount instructions (Finder / Explorer / `davfs2` / `gvfs-mount`), "Generate webdav password" button consuming MNT1c. Wires into `aidocs/49` `/help` so the same instructions appear in `docs/help/mount-shepard.md`. | MNT1c, `aidocs/49` D1c | 1w |
| **MNT1i** | **(Deferred to v2.)** Write support: `PUT` on an existing FileBundle (replaces the payload, new PayloadVersion per `aidocs/46`); `MKCOL` mints a new DataObject; `DELETE` soft-deletes. Out of scope here; tracked separately. | MNT1a-h, `aidocs/46` payload versioning | (deferred) |

Recommended landing order: **MNT1a → MNT1b → MNT1c → MNT1d →
MNT1e → MNT1f → MNT1g → MNT1h**. After MNT1c lands, the Mount is
usable end-to-end with a webdav-password against MNT1a + MNT1b
synthetic files; **MNT1d** flips the "can I actually pull a file
out" answer to yes; MNT1h closes the casual-user loop with the
in-app mount-instructions page.

---

## 10. Auth-via-existing-API-key path

API users with a long-lived API key (Python scripts, CLI tools,
notebooks running unattended) **don't need the mount-credential
round-trip**:

| Client | Header | Notes |
|---|---|---|
| `curl`, Python `requests`, modern CLI tools | `Authorization: Bearer <api-key>` | The API key per `aidocs/51 §3.3` carries the user's roles. Works directly against `/v2/webdav/...`. |
| WebDAV clients that don't speak Bearer (some Windows versions, older `davfs2`) | `Authorization: Basic <base64(username:api-key)>` | The API-key serves as the Basic password. The auth filter recognizes both `wmnt_...` (webdav-credential) and the regular API-key prefix. |
| Browsers / Finder / Explorer | webdav-password (§6) | The user-friendly path. |

Documented in `docs/help/mount-shepard.md` (per `aidocs/49 D1c`)
with three copy-pasteable snippets — `davfs2`, Finder
"Connect to Server", Windows `net use` — plus the "advanced:
mount with your API key" callout for the API-savvy reader.

---

## 11. Cross-references

| Doc | Coupling |
|---|---|
| `aidocs/16` | The MNT1 series rows added by the dispatcher when this design lands. |
| `aidocs/27` | The Python convenience client is the *sibling* access mode — chatty programmatic access stays on the client; quick path-grabs go through the mount. |
| `aidocs/42` | §"How you actually use it" gains a sixth entry-point: **"6. The mount — every Finder / Explorer / `ls`."** |
| `aidocs/45` | FileStorage SPI delivers payload bytes; presigned-URL redirect rides the `Optional<URL>` path. |
| `aidocs/46` | Payload-version pinning surfaces in the ETag so re-reads short-circuit cheaply. |
| `aidocs/47` | PayloadStorage SPI is the plugin shape every per-kind renderer (timeseries CSV, structured JSON, spatial GeoJSON) calls; the mount layer is plugin-agnostic. |
| `aidocs/49` | `docs/help/mount-shepard.md` + per-OS reference page in `docs/reference/webdav-mount.md`. |
| `aidocs/51` | API-key role + instance-admin role drive who can mint mount-credentials at what scope. |
| `aidocs/53` | FileBundle + FileGroup naming surfaces in the file-tree shape directly. Video lazy-concat lives behind `shepard.video.mount.compose=true`. |
| `aidocs/55` | Every mount read emits a PROV-O Activity row; `mountSession` collapses the dashboard. |
| `aidocs/56` | `/v2/webdav/...` follows the flat-appId convention; `x-mcp-side-effects: read-only` extension on every OpenAPI operation. |
| `aidocs/58` | `:CollectionProperties.webdavVisible` (boolean, default `true`) is the per-Collection opt-out. |
| `aidocs/60` | Edge instances can expose `/v2/webdav/` locally — the same casual-user reflex works on an Edge laptop. |

---

## 12. What this isn't

- **Not a write surface** (v1). Drag-in-to-write is `MNT1i`,
  deferred. Operators who need writes use the REST API, the
  Python client (`aidocs/27`), or the frontend.
- **Not a sync-back protocol** like Dropbox / OneDrive / Syncthing.
  The mount is *read-only access*, not bidirectional state
  reconciliation. Operators wanting offline edit-and-sync use
  `aidocs/60` Edge.
- **Not a replacement for the REST API.** The mount is a
  complementary access mode — strictly less expressive than the
  REST surface (no search, no permission edits, no admin actions).
  Anything the mount can do, the REST API can do; not the other
  way round.
- **Not an unsecured share.** Auth applies on every request;
  permissions enforced per-entity; every read logged to the
  activity table. A leaked webdav-password is bounded to read-only
  + revocable + scope-limited + time-limited.
- **Not a network filesystem in the kernel sense.** No file
  locking, no `mmap` semantics, no `inotify` events. Clients that
  expect those break — but no casual user's tool expects them.

---

## 13. Open questions for the maintainer

Four questions; recommended answers below each but the call is
yours.

### 13.1 Default-on or default-off?

**Recommendation: default-off** for the first release.
`shepard.webdav.enabled=false` (a feature toggle per
`aidocs/47 §3`). Operators flip it on once they've thought about
their network egress shape — a 1000-image FileBundle pulled
through a Finder window over a corporate VPN is the kind of
"why is shepard slow" call that wants to be opt-in. Flip the
default to `true` after one quarter of field use without an
incident.

Migration tracker entry per `aidocs/34` shape: `CONFIG` —
operators who want the mount flip the env var.

### 13.2 CSV vs Parquet as the default timeseries serialisation

**Recommendation: CSV.** Two reasons:

1. **Lowest common denominator.** Every analysis tool — pandas,
   Excel, R, MATLAB, Origin, even `awk` — reads CSV. Parquet
   needs `pyarrow` / `fastparquet` installed.
2. **Excel reflex.** A researcher's first move when handed a CSV
   in Finder is double-click → Excel. Same researcher with a
   `.parquet` file double-clicks and gets "no app to open this
   file."

Parquet stays available via `?format=parquet` for the power user
who knows they want it. The default optimises for the casual user.

### 13.3 WebDAV server library — Apache Milton vs hand-roll on JAX-RS

**Recommendation: Apache Milton.** RFC 4918 is a real spec with
XML schemas, multi-status responses, lock tokens, depth headers,
property-set merging. Hand-rolling it on top of JAX-RS resources
buys nothing and costs months. Milton is MIT/Apache-2.0,
maintained, and its `ResourceFactory` SPI maps cleanly onto
shepard's `Collection → DataObject → Reference` resolution path.
Adds ~600 KB to the backend JAR; acceptable.

Risk: if Milton goes unmaintained we own a fork. Hedge: keep the
`ResourceFactory` boundary thin (~200 LoC of glue) so we can swap
to Jakarta WebDAV without touching the rest of the codebase.

### 13.4 Per-Collection visibility opt-out — where does the flag live?

**Recommendation: `:CollectionProperties.webdavVisible: bool`**
(boolean, default `true`). Sourced from `aidocs/58 §5` — the same
properties-node design that replaces the `default_filecontainer`
hack. Collection-owner toggles it from the Collection settings
panel in the frontend (post-MNT1h). Filtered at PROPFIND time
alongside the READ-permission check. An admin-side global override
(`shepard.webdav.collection.force-include=true`) is documented but
default-off — Collection-owner intent wins by default.

---

## 14. Closing note

The mount is **not the canonical access mode**. The REST API and
the Python convenience client are. The mount is the **fall-out
mode** — the answer to "give me a path, not an API client" and
"let me hand a folder to a colleague who doesn't use shepard's UI
yet." It costs ~5 engineering weeks across MNT1a-h, ships under
the existing TLS / auth / permission machinery, and moves the
casual-user friction line one notch lower than the Python client
already does. Every other piece of shepard — search, write,
permissions, snapshots, RO-Crate, the AI features — stays on the
REST surface where it belongs.

For an admin upgrading from upstream `dlr-shepard/shepard 5.2.0`:
the mount is **additive surface on the `/v2/` shelf**, default-off,
zero impact on existing endpoints. The `aidocs/34` row records it
as `CONFIG` (one env var to enable) — no migration, no breakage,
no upstream-API-shape change.
