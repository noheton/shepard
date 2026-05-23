---
title: file-s3 — Install
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

# file-s3 — install

`shepard-plugin-file-s3` is the S3-compatible object-storage
adapter for shepard's `FileStorage` SPI. The plugin is **bundled
with the standard image** but only activates when
`shepard.storage.provider=s3` is set. The canonical S3 backend per
[ADR-0024](../../../aidocs/63-architecture-decision-log.md#adr-0024)
is **Garage**.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-file-s3-${revision}.jar`.
- An S3-compatible object store reachable from the backend
  container. Tested backends:
  - **Garage** (recommended; see §"Garage sidecar" below)
  - AWS S3
  - Cloudflare R2
  - Backblaze B2
  - Wasabi
  - MinIO (their community edition is archived per ADR-0024 — use
    only for legacy installs)
  - SeaweedFS
  - Ceph RGW

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.storage.provider` | `gridfs` | Must be `s3` to route file operations through this adapter. |
| `shepard.files.s3.bucket` | (empty) | Target bucket name. Adapter is disabled when blank — even with `provider=s3`. |
| `shepard.files.s3.endpoint` | (empty) | Full endpoint URL (e.g. `http://garage:3900`). Omit for real AWS S3. |
| `shepard.files.s3.region` | `us-east-1` | AWS region or Garage placement zone. |
| `shepard.files.s3.access-key-id` | (empty) | S3 access key ID. Falls back to the AWS default credentials chain if blank. |
| `shepard.files.s3.secret-access-key` | (empty) | S3 secret access key. |
| `shepard.files.s3.path-style-access` | `true` | Force path-style URLs (`http://<host>/<bucket>/<key>`). Required for Garage, MinIO, LocalStack. Set `false` for real AWS S3. |
| `shepard.plugins.file-s3.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |

The adapter auto-creates the configured bucket on startup if it
doesn't already exist. The bucket name is **not** embedded in the
storage locator — object keys are `<containerMongoId>/<uuid>`,
which means re-binding the bucket only requires updating
`shepard.files.s3.bucket`.

---

## Per-backend snippets

### Garage (recommended)

The plugin's
[`SidecarSpec`](../src/main/java/de/dlr/shepard/plugins/files3/FileS3PluginManifest.java)
declares Garage as the default backend. The sidecars assembler
renders a compose snippet automatically; operators using the
bundled `infrastructure/` profiles can simply:

```bash
docker compose --profile files-s3 up -d garage
```

Then in `application.properties`:

```properties
shepard.storage.provider=s3
shepard.files.s3.endpoint=http://garage:3900
shepard.files.s3.bucket=shepard-files
shepard.files.s3.region=garage-region
shepard.files.s3.path-style-access=true
shepard.files.s3.access-key-id=GK...
shepard.files.s3.secret-access-key=...
```

The `access-key-id` + `secret-access-key` come from `garage key
new --name shepard-backend`. See
[`quickstart.md`](quickstart.md) for the end-to-end walkthrough.

### AWS S3

```properties
shepard.storage.provider=s3
shepard.files.s3.bucket=my-shepard-files
shepard.files.s3.region=eu-central-1
shepard.files.s3.path-style-access=false
# Leave access-key-id + secret-access-key empty to use the
# default AWS credentials chain (IAM role / env vars).
```

### Cloudflare R2

```properties
shepard.storage.provider=s3
shepard.files.s3.endpoint=https://<account-id>.r2.cloudflarestorage.com
shepard.files.s3.bucket=shepard-files
shepard.files.s3.region=auto
shepard.files.s3.path-style-access=false
shepard.files.s3.access-key-id=...
shepard.files.s3.secret-access-key=...
```

### Backblaze B2

```properties
shepard.storage.provider=s3
shepard.files.s3.endpoint=https://s3.us-west-002.backblazeb2.com
shepard.files.s3.bucket=shepard-files
shepard.files.s3.region=us-west-002
shepard.files.s3.path-style-access=false
```

---

## Migrating from GridFS

The default `shepard.storage.provider=gridfs` stores files in
MongoDB. Switching to S3 is **not** automatic — existing files
remain in GridFS until migrated explicitly. The migration runbook
lives at
[`docs/admin/storage-migration.md`](../../../docs/admin/storage-migration.md)
(planned) and uses the `shepard-admin storage migrate` CLI to
re-upload each `:FileContainer`'s bytes from GridFS to the new
S3 bucket, then rewrites the storage locator field.

A clean install + S3 from the start (no historical files) skips
all of this.

---

## Healthcheck

```bash
# After bringing up the backend:
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "https://shepard.example.dlr.de/v2/admin/plugins" | \
  jq '.[] | select(.id == "file-s3")'
```

Should return `state: "ENABLED"`. A subsequent file upload to any
`:FileContainer` confirms the S3 path is live:

```bash
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  --data-binary @small.txt \
  "https://shepard.example.dlr.de/shepard/api/fileContainers/<id>/payload?filename=small.txt"
```

Check the bucket for the resulting object.

---

## Disabling the plugin

```properties
shepard.plugins.file-s3.enabled=false
shepard.storage.provider=gridfs
```

Both keys must change — the toggle alone doesn't reroute file
operations. Restart the backend to apply.

---

## Known pitfalls

- **Bucket auto-create requires `s3:CreateBucket` permission.**
  For AWS S3 with a tightly scoped IAM role, pre-create the
  bucket out-of-band before starting the backend.
- **`path-style-access=false` against Garage**. Garage requires
  path-style URLs. The default `true` works for all the
  recommended backends except real AWS S3.
- **Presigned URL host mismatch**. If the backend reaches the
  endpoint at `http://garage:3900` (internal DNS) but clients
  reach Garage at `https://files.example.dlr.de`, presigned
  upload/download URLs carry the **internal** host and fail in
  the browser. Set `shepard.files.s3.endpoint` to the public URL
  in that case, and accept the small extra hop.
- **Bucket name conflicts on shared object stores**. R2 + B2 have
  account-global namespaces. Pick a unique bucket name.
- **`exports/` lifecycle rule**. RO-Crate exports are written
  under `exports/<jobId>/` with a 24h presigned-download URL.
  Configure a bucket lifecycle rule to delete `exports/*` after
  24h, otherwise the bucket accretes orphaned ZIPs.

---

## See also

- [`reference.md`](reference.md) — full plugin reference + config keys.
- [`quickstart.md`](quickstart.md) — switch a bundled instance to
  Garage in 5 minutes.
- [`aidocs/63-architecture-decision-log.md` ADR-0024](../../../aidocs/63-architecture-decision-log.md)
  — Garage selection rationale.
- [Garage docs](https://garagehq.deuxfleurs.fr/documentation/) —
  upstream.
