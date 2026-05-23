---
title: file-s3 — Quickstart
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

# file-s3 — quickstart

**Goal:** switch a fresh shepard install from MongoDB GridFS to
S3-via-Garage, in 5 minutes.

Garage is the canonical S3 backend per
[ADR-0024](../../../aidocs/63-architecture-decision-log.md#adr-0024)
— self-hosted, no AWS account required, single-binary deploy.

---

## Step 1 — bring up Garage

The `infrastructure/` directory ships a `files-s3` compose profile
that pins Garage with sensible defaults.

```bash
cd infrastructure
docker compose --profile files-s3 up -d garage
```

Wait for Garage to be healthy:

```bash
docker compose exec garage /garage status
```

You should see one node, `HEALTHY`.

---

## Step 2 — provision a bucket + access key

Inside the Garage container:

```bash
# Assign storage capacity to the node.
NODE_ID=$(docker compose exec garage /garage node id | awk -F'@' '{print $1}')
docker compose exec garage /garage layout assign $NODE_ID -z dc1 -c 1G
docker compose exec garage /garage layout apply --version 1

# Create the bucket.
docker compose exec garage /garage bucket create shepard-files

# Mint an access key for the backend.
docker compose exec garage /garage key new --name shepard-backend
```

The `key new` command prints:

```text
Key name: shepard-backend
Key ID: GK1234567890abcdef
Secret key: 1234567890abcdef1234567890abcdef
```

**Save both values now — the secret is shown once.** Grant it
read+write on the bucket:

```bash
docker compose exec garage /garage bucket allow \
  --read --write shepard-files --key shepard-backend
```

---

## Step 3 — point shepard at the bucket

Add to your `application.properties` (or the equivalent env vars
on the backend container):

```properties
shepard.storage.provider=s3
shepard.files.s3.endpoint=http://garage:3900
shepard.files.s3.bucket=shepard-files
shepard.files.s3.region=garage-region
shepard.files.s3.path-style-access=true
shepard.files.s3.access-key-id=GK1234567890abcdef
shepard.files.s3.secret-access-key=1234567890abcdef1234567890abcdef
```

Restart the backend. The startup log should include:

```text
FS1b: file-s3 plugin v1.0.0-SNAPSHOT active via PluginManifest SPI
       (id=file-s3, compat=>=6.0.0-SNAPSHOT,<7).
       Set shepard.storage.provider=s3 to activate the S3 storage path.
S3FileStorage: bucket 'shepard-files' verified at http://garage:3900
```

---

## Step 4 — verify with a real upload

Create a `:FileContainer` and upload a file:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...
COLLECTION_ID=...

# Create a FileContainer inside a Collection.
FC_ID=$(curl -s -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name":"smoke-test"}' \
  "$SHEPARD_URL/shepard/api/collections/$COLLECTION_ID/fileContainers" | \
  jq -r '.id')

# Upload a small file.
echo "hello s3" > /tmp/hello.txt
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  --data-binary @/tmp/hello.txt \
  "$SHEPARD_URL/shepard/api/fileContainers/$FC_ID/payload?filename=hello.txt"
```

Check Garage:

```bash
docker compose exec garage /garage bucket list-objects shepard-files
```

The file appears with key `<containerMongoId>/<uuid>` — that's
S3FileStorage's locator format.

---

## Step 5 — verify the plugin is healthy

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "file-s3")'
```

Should return:

```json
{
  "id": "file-s3",
  "version": "1.0.0-SNAPSHOT",
  "state": "ENABLED"
}
```

---

## Done

Every file uploaded to shepard from this point on lands in
Garage's `shepard-files` bucket. Files already in GridFS stay
there until migrated — see the GridFS → S3 migration section in
[`install.md`](install.md#migrating-from-gridfs).

---

## Going further

- **Presigned URLs**: clients can `PUT`/`GET` directly to Garage,
  bypassing the backend for large files. See FS1c in
  [`reference.md`](reference.md).
- **Lifecycle rules**: configure `exports/*` to auto-expire after
  24h so the bucket doesn't accrete orphaned RO-Crate ZIPs.
- **Production hardening**: use Garage's `garage cluster` mode
  with three-way replication for durability.
- **Different backend**: switch the `endpoint` + `path-style-access`
  to point at AWS S3, R2, B2, or any other S3-compatible store.
  See [`install.md`](install.md#per-backend-snippets).

---

## See also

- [`reference.md`](reference.md) — full config + locator format.
- [`install.md`](install.md) — per-backend setup table.
- [Garage docs](https://garagehq.deuxfleurs.fr/documentation/quick_start/).
- [`aidocs/63-architecture-decision-log.md` ADR-0024](../../../aidocs/63-architecture-decision-log.md).
