# S3-compatible File Storage (FS1b)

Plugin id: `storage-s3` | Module: `shepard-plugin-storage-s3`

The S3 adapter stores file payloads in any S3-compatible object store.
Verified compatible endpoints: AWS S3, Garage, MinIO, Ceph RGW,
Cloudflare R2, Backblaze B2, Wasabi, SeaweedFS.

## Quick start

### Configure Garage (recommended for self-hosted)

```bash
# 1. Set the endpoint override to your Garage instance
shepard-admin storage s3 set-endpoint https://garage.example.org

# 2. Set the region (Garage accepts any value; us-east-1 is conventional)
shepard-admin storage s3 set-region us-east-1

# 3. Set the bucket name
shepard-admin storage s3 set-bucket my-shepard-bucket

# 4. Set credentials (accessKeyId passed as arg, secretKey read from stdin/tty)
shepard-admin storage s3 set-credentials GK...key-id

# 5. Test the connection before enabling
shepard-admin storage s3 test-connection

# 6. Enable the adapter
shepard-admin storage s3 enable
```

Also set `shepard.storage.provider=s3` in `application.properties` (or
`SHEPARD_STORAGE_PROVIDER=s3` env var) and restart.

### Configure AWS S3

```bash
# Leave endpointUrl blank (empty string) to use AWS's regional endpoint
shepard-admin storage s3 set-endpoint ""
shepard-admin storage s3 set-region eu-central-1
shepard-admin storage s3 set-bucket my-shepard-prod-bucket
shepard-admin storage s3 set-credentials AKIAIOSFODNN7EXAMPLE
# → enter secret access key at the prompt

# For AWS S3, path-style addressing is optional; disable it with:
curl -X PATCH https://shepard.example.org/v2/admin/storage/s3/config \
     -H 'Authorization: Bearer $TOKEN' \
     -H 'Content-Type: application/json' \
     -d '{"forcePathStyle":false}'

shepard-admin storage s3 test-connection
shepard-admin storage s3 enable
```

## Admin REST endpoints

All endpoints require the `instance-admin` role.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v2/admin/storage/s3/config` | Read current config (credential masked) |
| `PATCH` | `/v2/admin/storage/s3/config` | RFC 7396 merge-patch patchable fields |
| `POST` | `/v2/admin/storage/s3/credential` | Set or rotate credentials |
| `DELETE` | `/v2/admin/storage/s3/credential` | Clear stored credentials |
| `POST` | `/v2/admin/storage/s3/test-connection` | `HeadBucketRequest` probe |

### GET /v2/admin/storage/s3/config

Returns the masked config — `secretKeySet` (bool) + `secretKeyFingerprint`
(first 8 hex of the SHA-256) instead of the raw key.

### PATCH /v2/admin/storage/s3/config

Patchable fields: `enabled`, `endpointUrl`, `region`, `bucket`,
`bucketPrefix`, `forcePathStyle`, `sseAlgorithm`, `multipartThresholdBytes`,
`connectionTimeoutSeconds`, `requestTimeoutSeconds`.

Credential fields (`accessKeyId`, `secretAccessKeyCipher`,
`secretAccessKeyHash`, `secretKey`) are **read-only** via PATCH — use
`POST .../credential` instead.

```bash
# Example: disable SSE, increase multipart threshold to 64 MiB
curl -X PATCH https://shepard.example.org/v2/admin/storage/s3/config \
     -H 'Authorization: Bearer $TOKEN' \
     -H 'Content-Type: application/json' \
     -d '{"sseAlgorithm":"","multipartThresholdBytes":67108864}'
```

### POST /v2/admin/storage/s3/credential

```json
{ "accessKeyId": "AKIAIOSFODNN7EXAMPLE", "secretKey": "wJalrX..." }
```

Response: `{ "secretKeySet": true, "secretKeyFingerprint": "8a4b6e1f" }`.
The plaintext is never echoed.

### POST /v2/admin/storage/s3/test-connection

No request body. Response:

```json
{
  "reachable": true,
  "statusCode": 200,
  "latencyMs": 42,
  "endpoint": "https://garage.example.org",
  "bucket": "my-bucket",
  "detail": null
}
```

A `403` status means the endpoint is reachable but the credentials lack
`s3:HeadBucket` permission — still counts as "endpoint up".

## CLI reference

```
shepard-admin storage s3 status
shepard-admin storage s3 enable
shepard-admin storage s3 disable
shepard-admin storage s3 set-endpoint <url>
shepard-admin storage s3 set-region <region>
shepard-admin storage s3 set-bucket <name>
shepard-admin storage s3 set-credentials [<accessKeyId>]
shepard-admin storage s3 clear-credentials
shepard-admin storage s3 test-connection
```

All commands support `--output=json` for machine-readable output and
`--url <base-url>` + `--api-key <key>` for non-default endpoints.

`set-credentials` reads the secret key from an interactive terminal
(echo off) when available, falling back to a single line of stdin for
CI pipelines (`echo $KEY | shepard-admin storage s3 set-credentials AKIA...`).

## Configuration reference

Deploy-time defaults (`application.properties` or environment variables):

| Key | Env var | Default | Description |
|-----|---------|---------|-------------|
| `shepard.storage.provider` | `SHEPARD_STORAGE_PROVIDER` | `gridfs` | Set to `s3` to activate this adapter |
| `shepard.storage.s3.enabled` | — | `false` | Seed value for the runtime toggle |
| `shepard.storage.s3.endpoint-url` | — | `""` (AWS default) | Endpoint override |
| `shepard.storage.s3.region` | — | `us-east-1` | AWS region or dummy value |
| `shepard.storage.s3.bucket` | — | `""` | Default bucket name |
| `shepard.storage.s3.bucket-prefix` | — | `""` | Optional key prefix |
| `shepard.storage.s3.force-path-style` | — | `true` | Path-style addressing |
| `shepard.storage.s3.sse-algorithm` | — | `""` | Server-side encryption |
| `shepard.storage.s3.multipart-threshold-bytes` | — | `16777216` | 16 MiB |
| `shepard.storage.s3.connection-timeout-seconds` | — | `10` | Connect timeout |
| `shepard.storage.s3.request-timeout-seconds` | — | `30` | Per-request timeout |

**Runtime values win.** Deploy-time keys seed the `:S3StorageConfig` node
on first start; subsequent `PATCH /v2/admin/storage/s3/config` calls mutate
the node in place and take effect immediately without a restart.

The secret access key is **never** a deploy-time key — it must always be
set via `POST .../credential` to keep it out of `application.properties`
(gitleaks would flag it as a credential leak).

## Locator format

The S3 adapter's opaque locator is `<bucket>:<key>` where key is
`<container>/<fileName>`. This is stored in the `:ShepardFile.locator`
Neo4j property and treated as opaque by the rest of shepard.

## Security posture

The secret access key is stored encrypted in Neo4j (AES-GCM-256, keyed
off `shepard.instance.id`). This protects against "attacker reads Neo4j
but not the JVM config" — the threat model documented in
`aidocs/45 §3.2`. An attacker with both Neo4j and `application.properties`
access can recover the key; deploy-time protection should focus on
securing those two surfaces. A KMS-backed credential store is tracked as
a future improvement.

## Related docs

- `aidocs/16` FS1b — dispatcher backlog entry
- `aidocs/45` — GridFS-to-S3 evaluation
- `aidocs/63` ADR-0024 — Garage as the preferred self-hosted S3 endpoint
- `docs/reference/file-storage.md` — FileStorage SPI overview (FS1a)
