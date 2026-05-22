# File storage

shepard stores file payloads (the bytes behind a `FileReference` or
a `FileBundleReference`) through a **pluggable storage backend**.
The SPI seam is in core; concrete adapters either ship in core
(the GridFS default) or as drop-in plugin JARs (S3-compatible
adapters, future SeaweedFS / Garage-direct adapters).

This page covers the operator surface: which adapter is active,
how to pick a different one, and what guarantees apply across the
upgrade path.

> **Operator runbooks** (live, in production at `shepard.nuclide.systems`):
> - [Garage S3 sidecar activation]({{ '/ops/garage-activation-runbook' | relative_url }})
>   — the manual realisation of `FileS3PluginManifest.sidecars()` while
>   the operator-side bootstrap is still being designed.
> - [GridFS → S3 migration]({{ '/ops/migrate-gridfs-to-s3' | relative_url }})
>   — in-place adapter swap with no API downtime.

## Quick reference

| Knob | Default | Scope | Notes |
|---|---|---|---|
| `shepard.storage.provider` | `gridfs` | deploy-time only | Switches the active storage adapter. Set to `none` to disable file payloads (resolver-only). |
| `:ShepardFile.providerId` | `"gridfs"` | per-row Neo4j property | Internal bookkeeping. Stamped on every new upload + backfilled on legacy rows by V34. Not on the wire. |
| `shepard-admin storage status` | — | CLI verb | Read-only adapter listing. Reads `GET /v2/admin/storage`. Requires admin API key. |
| `GET /v2/admin/storage` | — | REST (instance-admin) | Lists all discovered adapters with `id`, `enabled`, `active` flags. |
| `shepard-admin files migrate <src> <tgt>` | — | CLI verb | Triggers big-bang migration in background (FS1e1). |
| `GET /v2/admin/files/migrate/status` | — | REST (instance-admin) | Polls migration state (IDLE / RUNNING / DONE / FAILED). |

## What's in the box

### `gridfs` (default; ships with stock shepard)

The legacy upstream-compatible path: file bytes live in MongoDB
GridFS (1 MiB chunks, one Mongo collection per `FileContainer`).
Zero extra infrastructure — the same Mongo instance shepard uses
for structured data + bookkeeping carries the file payloads too.

**When to keep it.** Small-to-medium deployments (sub-TB total
files), all-in-one Compose stacks, air-gapped installs, anyone
who values "5-minute setup". The GridFS adapter is **not** a
deprecation path — it stays first-class supported indefinitely.

### `s3` (FS1b — shipped)

S3-compatible adapter for any endpoint that speaks the S3 wire
protocol — AWS S3, Cloudflare R2, Backblaze B2, Wasabi, Garage
(per `aidocs/63` ADR-0024), SeaweedFS, Ceph RGW, MinIO. Plugin
shape: `plugins/file-s3/` produces a drop-in
`shepard-plugin-file-s3-<version>.jar`.

**When to flip to it.** Multi-TB deployments, presigned-URL needs
(direct frontend uploads / RO-Crate ZIP delivery from the bucket),
cross-region replication, or anyone who already runs an
S3-compatible service.

**Config keys** (all deploy-time only; set once in
`application.properties`):

| Key | Default | Notes |
|---|---|---|
| `shepard.files.s3.endpoint` | `""` (AWS S3) | Full URL — e.g. `http://garage:3900`. Omit for real AWS. |
| `shepard.files.s3.region` | `us-east-1` | AWS region or Garage placement zone. |
| `shepard.files.s3.access-key-id` | `""` | Access key ID. Falls back to AWS credential chain if blank. |
| `shepard.files.s3.secret-access-key` | `""` | Secret access key. |
| `shepard.files.s3.bucket` | `""` | Bucket name. Adapter is disabled when blank. |
| `shepard.files.s3.path-style-access` | `true` | Required for Garage, MinIO, LocalStack. Set `false` for real AWS S3. |
| `shepard.storage.provider` | `gridfs` | Set to `s3` to activate this adapter. |

**Locator format.** Each S3 object is keyed as
`<containerMongoId>/<uuid>`. The bucket name is read from
`shepard.files.s3.bucket` at runtime and is not stored in the
locator row.

**Quick-start with Garage** (the recommended self-hosted endpoint):

```bash
# 1. Start the Garage sidecar (FS1d compose profile):
docker compose --profile files-s3 up -d

# 2. Initialize the cluster (one-time; from the compose comment):
docker exec garage garage layout assign -z dc1 -c 1G <node-id>
docker exec garage garage layout apply --version 1
docker exec garage garage key create shepard-key
# Note the access key + secret printed above.

# 3. Set the credentials in application.properties:
#    shepard.files.s3.endpoint=http://localhost:3900
#    shepard.files.s3.access-key-id=<access-key>
#    shepard.files.s3.secret-access-key=<secret>
#    shepard.files.s3.bucket=shepard
#    shepard.storage.provider=s3
#    shepard.plugins.file-s3.enabled=true

# 4. Restart shepard.
```

### Presigned URLs (FS1c/FS1f — S3 only)

When `shepard.storage.provider=s3`, clients can upload and
download directly to/from S3 without routing bytes through the
backend. Three `/v2/` endpoints handle the presigned-URL flow.

**The shepard web frontend uses presigned upload automatically (FS1f).**
When S3 is active, `FileContainerAccessor.uploadFile()` tries the
presigned path first. On a 503 response (GridFS active), it silently
falls back to the legacy upload path — GridFS users see no change.
For this to work in a browser, **CORS must be configured on the S3
bucket** to allow `PUT` requests from the frontend origin:

```bash
# Garage — add to garage.toml:
# [s3_api]
# cors_allow_origins = ["https://shepard.example.dlr.de"]

# MinIO:
mc anonymous cors set <alias>/shepard --method PUT --origin https://shepard.example.dlr.de
```

Without CORS, the browser will block the presigned PUT and the user
will see an upload error (no data is lost — the S3 PUT never completed
before the browser blocked it).

Three `/v2/` endpoints handle the presigned-URL flow:

#### Upload flow

```
POST /v2/file-containers/{containerAppId}/upload-url
Content-Type: application/json

{"fileName": "sensor-data.csv", "contentType": "text/csv"}
```

Response:
```json
{
  "uploadUrl": "https://storage.example.com/shepard/container123/uuid?X-Amz-...",
  "oid": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": "2024-08-15T11:33:44Z"
}
```

Then upload bytes directly to S3 (no auth headers needed — the
signature is embedded in the URL):
```bash
curl -X PUT "<uploadUrl>" --data-binary @sensor-data.csv
```

Then commit (register the file in shepard):
```
POST /v2/file-containers/{containerAppId}/upload-url/commit
Content-Type: application/json

{"oid": "550e8400-...", "fileName": "sensor-data.csv", "fileSize": 204800}
```

Returns `201 Created` with the `ShepardFile` JSON.

#### Download flow

```
GET /v2/file-containers/{containerAppId}/files/{oid}/download-url
```

Response:
```json
{
  "downloadUrl": "https://storage.example.com/shepard/container123/uuid?X-Amz-...",
  "expiresAt": "2024-08-15T11:23:44Z"
}
```

Then download bytes directly:
```bash
curl -o sensor-data.csv "<downloadUrl>"
```

#### TTLs and auth

Upload URLs expire in **15 minutes**; download URLs in **5
minutes**. Both are single-use in the sense that after the TTL
they cannot be used again — but multiple PUT requests against the
same presigned PUT URL before expiry will succeed (S3 semantics;
the last write wins). Auth requirements on the presigned-URL
endpoints themselves match all other `/v2/` endpoints
(authenticated + container Read for download-url, Write for
upload-url and commit).

#### GridFS fallback

When `shepard.storage.provider=gridfs`, all three endpoints return
`503 Service Unavailable` with a message directing the caller to
the direct upload path (`POST /shepard/api/fileContainers/{id}/payload`).
No code change needed — `GridFsFileStorage` inherits the SPI's
`Optional.empty()` default and the service layer converts it to 503.

## Export URL (FS1g — S3 only)

`POST /v2/collections/{appId}/export-url` builds a RO-Crate ZIP for
the collection and returns a **presigned S3 GET URL** instead of
streaming the ZIP through the backend. The client downloads directly
from S3; the JVM is no longer in the gigabyte-streaming path.

**Request body** (optional — same `ExportSelection` as the legacy
export endpoint):

```json
{
  "includePermissions": false,
  "payloads": {}
}
```

Omitting the body uses default export selection (all payload kinds,
no redaction).

**Response** (`200 OK`):

```json
{
  "downloadUrl": "https://storage.example.com/shepard/exports/uuid.zip?X-Amz-...",
  "fileName": "my-collection-2024-08-15-export.zip",
  "expiresAt": "2024-08-15T12:05:44Z"
}
```

The presigned URL expires in **30 minutes**. Auth requirements match
all other `/v2/` collection endpoints — authenticated + Read
permission on the collection.

**GridFS / non-S3 fallback.** When the active storage adapter does
not support presigned export (any adapter returning `Optional.empty()`
from `FileStorage.presignedExportUrl()`), the endpoint returns
`503 Service Unavailable`. Use the legacy streaming export
(`GET /shepard/api/collections/{id}/export`) instead.

### Lifecycle cleanup for the `exports/` prefix

Export ZIPs accumulate in the same bucket as file payloads, under the
`exports/` prefix. Configure a **lifecycle rule** on that prefix so
objects expire automatically — 24 hours is a reasonable default.

**Garage** (`garage.toml`):

Garage does not yet support S3-style lifecycle rules natively
(v1.0.x). Use a cron job or manual sweep:

```bash
# List and delete exports older than 1 day:
garage s3 ls --bucket shepard --prefix exports/ \
  | awk -v cutoff="$(date -d '1 day ago' +%Y-%m-%dT%H:%M:%S)" '$1 < cutoff {print $4}' \
  | xargs -I{} garage s3 rm --bucket shepard --key {}
```

**AWS S3 / MinIO** (lifecycle rule via AWS CLI):

```json
{
  "Rules": [
    {
      "ID": "shepard-export-ttl",
      "Status": "Enabled",
      "Filter": {"Prefix": "exports/"},
      "Expiration": {"Days": 1}
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket shepard \
  --lifecycle-configuration file://lifecycle.json
```

Without a lifecycle rule, export objects persist indefinitely. The
30-minute presigned URL TTL controls access, not object retention.

## Migrating between adapters (FS1e1)

`shepard-admin files migrate <source> <target>` streams every
file from the source adapter to the target adapter in the
background. OIDs are preserved — existing API clients keep
working because only `providerId` changes in Neo4j.

### Full GridFS → S3 migration walkthrough

```bash
# 1. Ensure both adapters are visible:
shepard-admin storage status
# Should show gridfs: enabled, s3: enabled

# 2. Trigger migration (returns immediately):
shepard-admin files migrate gridfs s3

# 3. Poll until done:
watch -n5 shepard-admin files migrate-status

# 4. If filesFailed > 0, re-run to sweep residual:
shepard-admin files migrate gridfs s3

# 5. Once status: DONE, flip the active provider and restart:
# In application.properties:
#   shepard.storage.provider=s3
# Then: docker compose restart shepard-backend
```

Migration can be scripted with exit codes:

```bash
# Wait until migration finishes (exit 0 = DONE/IDLE):
until shepard-admin files migrate-status; do
  sleep 10
done
```

Exit codes: `0` = IDLE or DONE; `1` = RUNNING; `2` = FAILED.

### Re-running after partial failure

Re-running migration is safe — files already on the target have
`providerId = <target>` and are excluded from the Cypher query.
Only files still at the source (unexpected failures, transient
network errors) are touched on a re-run.

## Switching the active adapter

`shepard.storage.provider` is **deploy-time only** per the
`CLAUDE.md` "cluster identity / topology" exception. Switching
the storage backend re-points the bytes pipeline; runtime flips
would orphan in-flight writes and break existing-row reads until
the migration sweep completes.

### Greenfield install (no existing files)

1. Pick the adapter you want — `gridfs` (default) or `s3` (install
   `shepard-plugin-file-s3-<version>.jar` and configure
   `shepard.files.s3.*`).
2. Set `shepard.storage.provider=<id>` in `application.properties`
   (or env `SHEPARD_STORAGE_PROVIDER=<id>`).
3. Start shepard. New uploads land on the chosen backend.

### Existing GridFS install moving to S3

1. Copy `shepard-plugin-file-s3-<version>.jar` into
   `/deployments/plugins/` and set `shepard.plugins.file-s3.enabled=true`.
2. Configure the S3 endpoint + bucket + credentials
   (`shepard.files.s3.*`).
3. **Keep `shepard.storage.provider=gridfs`** — for now.
4. Run `shepard-admin files migrate gridfs s3` to copy existing
   payloads. Migration runs in the background so the API stays
   available.
5. After the sweep finishes, set `shepard.storage.provider=s3`
   and restart. Existing rows already point at the right adapter
   via their `:ShepardFile.providerId`.

### Disabling file payloads

`shepard.storage.provider=none` (or leave blank) puts shepard into
a **resolver-only** posture for file payloads:

- `POST /shepard/api/fileContainers/{id}/payload` returns 503
  `storage.provider.not-installed` (RFC 7807 envelope).
- `GET /…/payload/{oid}` returns the same 503.
- `DELETE /…/payload/{oid}` returns the same 503.
- Metadata endpoints (`GET /fileContainers/{id}` listing files)
  still work — they don't touch the storage tier.

Useful for archival deployments that want to retain the Neo4j
graph + the MongoDB bookkeeping documents but not let users add
new files until an operator picks the next storage backend.

## Adapter status

`shepard-admin storage status` reads `GET /v2/admin/storage` and
shows all discovered adapters with their enabled/active state:

```text
$ shepard-admin storage status
STORAGE — file-payload adapter status

FIELD              | VALUE
-------------------+------------------------------------------------
active provider    | gridfs
adapter: gridfs    | enabled, active
adapter: s3        | disabled
```

Exit code: 0 when an active provider is configured; 1 when
no provider is active. Requires an admin API key
(`--api-key <key>` or `SHEPARD_API_KEY=<key>`).

`--output=json` emits:

```json
{
  "activeProviderId": "gridfs",
  "adapters": [
    {"id": "gridfs", "enabled": true, "active": true},
    {"id": "s3", "enabled": false, "active": false}
  ]
}
```

The same payload is available directly via the REST API:

```bash
curl -H "X-API-KEY: <admin-key>" https://shepard.example.dlr.de/v2/admin/storage
```

## Upgrade path

Upgrading from a pre-FS1a backend (upstream shepard 5.2.0 or
anywhere before this fork's FS1a slice):

1. Restart shepard. Migration **V34** runs on first start:
   ```cypher
   MATCH (f:ShepardFile)
   WHERE f.providerId IS NULL
   SET f.providerId = 'gridfs';
   ```
   Single-digit seconds on a 1M-row file deployment. Fail-fast,
   idempotent — re-running is a no-op.
2. `FileStorageRegistry` logs `discovered 1 storage adapter(s):
   [gridfs]` + `active storage 'gridfs'` on startup.
3. Verify file payloads keep working:
   ```bash
   curl https://shepard.example.dlr.de/shepard/api/fileContainers/<id>/payload/<oid>
   ```
   Returns the same bytes as before — the upstream wire shape is
   preserved.

**Rollback** (rare; needed only to downgrade to a pre-FS1a backend
image): run the operator-runnable rollback:

```bash
cypher-shell -u neo4j -p <pw> -f V34_R__Rollback_Backfill_FilePayload_providerId.cypher
```

Pre-FS1a backends ignore the `providerId` property on
`:ShepardFile`; the downgrade is otherwise transparent.

## Behaviour change (one)

FS1a's SPI contract makes `DELETE /…/payload/{oid}` **idempotent**:
a second DELETE on the same `oid` now returns the same success
shape as the first (pre-FS1a, it would have returned 404). The
wire shape of the success response is unchanged — clients that
already treated DELETE as fire-and-forget see no difference.

Every other endpoint (upload, download, metadata reads, container
CRUD) is byte-identical to upstream.

## See also

- `aidocs/45-gridfs-to-s3-evaluation.md` — the underlying design
  doc covering the SPI shape, the wins / costs of S3, and the
  full backend matrix.
- `aidocs/16-dispatcher-backlog.md` FS1 row — current phase
  status across FS1a–FS1g.
- `aidocs/63-architecture-decision-log.md` ADR-0024 — the
  `infrastructure-local/` reference-image choice (Garage replaces
  MinIO when FS1d lands).
- `docs/reference/admin-cli.md` — the broader `shepard-admin` CLI
  reference page.
