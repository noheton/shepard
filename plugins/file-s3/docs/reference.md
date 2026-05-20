# Plugin: file-s3 — S3 File Storage Adapter

Stores shepard file payloads in any S3-compatible object store (AWS S3,
Cloudflare R2, Backblaze B2, Garage, MinIO, Ceph RGW, etc.).

## What it does

Implements the `FileStorage` SPI with id `s3` using AWS SDK v2. When
active, all file upload, download, and delete operations are routed to
the configured S3 bucket. The plugin also provides:

- **Presigned upload URLs (FS1c)** — clients PUT directly to S3,
  bypassing the backend for large files.
- **Presigned download URLs (FS1c)** — clients GET directly from S3.
- **Presigned export URLs (FS1g)** — transient export artefacts (e.g.
  RO-Crate ZIPs) are stored under an `exports/` prefix and returned as
  time-limited download URLs. Operators should configure a 24 h S3
  lifecycle rule on the `exports/` prefix to clean up stale artefacts.

Object locators have the form `<containerMongoId>/<uuid>`. The bucket
name is a deploy-time knob and is not embedded in the locator.

The adapter auto-creates the configured bucket on startup if it does not
exist. If `shepard.files.s3.bucket` is unset, the adapter is disabled
and logs an info message; the plugin manifest remains visible in
`GET /v2/admin/plugins`.

## Config keys

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.storage.provider` | — | Must be `s3` to route file operations to this adapter. |
| `shepard.files.s3.bucket` | _(empty)_ | Target bucket name. Adapter is disabled when blank. |
| `shepard.files.s3.endpoint` | _(empty)_ | Full endpoint URL (e.g. `http://garage:3900`). Omit for real AWS S3. |
| `shepard.files.s3.region` | `us-east-1` | AWS region or Garage placement zone. |
| `shepard.files.s3.access-key-id` | _(empty)_ | S3 access key ID. Falls back to the AWS default credentials chain if blank. |
| `shepard.files.s3.secret-access-key` | _(empty)_ | S3 secret access key. |
| `shepard.files.s3.path-style-access` | `true` | Force path-style URLs (`http://<host>/<bucket>/<key>`). Required for Garage, MinIO, LocalStack. Set `false` for real AWS S3. |
| `shepard.plugins.file-s3.enabled` | `true` | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`. |

## How to enable

1. Provision an S3-compatible bucket (the `files-s3` compose profile in
   `infrastructure/` starts a Garage instance).
2. Set the config keys above in `application.properties` or as
   environment variables.
3. Include `shepard-plugin-file-s3` on the backend classpath (bundled in
   the `with-plugins` Maven profile).

Minimal example (Garage quick-start):

```properties
shepard.storage.provider=s3
shepard.files.s3.endpoint=http://garage:3900
shepard.files.s3.bucket=shepard-files
shepard.files.s3.access-key-id=GK...
shepard.files.s3.secret-access-key=...
```

Verify via:
```
GET /v2/admin/plugins   # should include { "id": "file-s3", "version": "1.0.0-SNAPSHOT" }
```
