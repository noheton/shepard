---
stage: concept
last-stage-change: 2026-05-26
---

# VIS-S2 — Garage S3 + OME-Zarr storage-policy operator doc

**Audience:** Instance administrators deploying the `shepard-plugin-vis-volume`
(VIS-X1) plugin or any visualization plugin that writes pyramidised data to Garage.

**Status:** Concept. This doc describes the storage policy that the `volume-pyramidiser`
sidecar (shipped in VIS-X1) will implement. Until VIS-X1 ships, no code writes to the
`shepard-volumes` bucket. Read this as the pre-flight contract for VIS-X1 deployment.

**Prerequisite:** Garage must already be activated per the general runbook at
`docs/ops/garage-activation-runbook.md` (also summarised in
`aidocs/agent-findings/garage-activation-runbook.md`).

---

## §1 Why a separate bucket?

The existing `shepard-files` bucket stores opaque binary payloads at the locator
shape `<containerMongoId>/<uuid>`. An OME-Zarr store is fundamentally different:
it is a *directory tree of thousands of chunk files* under a common prefix. Mixing
these in `shepard-files` has three problems:

1. **Lifecycle rules cannot target a Zarr store prefix** in the general bucket without
   also catching unrelated file payloads.
2. **ACL boundaries are coarser** — a Garage key granted `read` on `shepard-files`
   would also be able to read raw file uploads it has no business accessing.
3. **Browser-direct chunk reads require permissive CORS.** Granting `*` CORS on
   `shepard-files` would expose every uploaded file to arbitrary cross-origin reads.
   A dedicated `shepard-volumes` bucket scopes that exposure to pyramidised data only.

**The decision: `shepard-volumes` is a second Garage bucket, parallel to
`shepard-files`.** It is only created and populated when the VIS-X1 plugin (or a
sibling vis plugin that writes chunks) is enabled.

---

## §2 Bucket layout

```
shepard-volumes/
  volumes/
    {collectionAppId}/
      {dataObjectAppId}/
        {filePayloadAppId}.zarr/
          .zattrs
          .zgroup
          0/
            0/0/0
            0/0/1
            ...
          1/
            ...
```

**Key design choices:**

| Level | Purpose |
|---|---|
| `volumes/` | Top-level prefix isolates OME-Zarr stores from any future non-Zarr volume formats sharing the same bucket. |
| `{collectionAppId}` | Enables a bucket lifecycle rule targeting a specific collection (e.g., `volumes/<coll-id>/` → delete after N days when collection is archived). |
| `{dataObjectAppId}` | Scopes the store to a single DataObject — the natural unit of ACL in Shepard. |
| `{filePayloadAppId}.zarr/` | One `.zarr/` store per file payload (the original TIFF/DICOM/RAW that the pyramidiser converts). Using the filePayloadAppId (not a random key) makes the store addressable from the Shepard data model without a database lookup. |

**All three IDs are UUID v7 (appId format).** They sort chronologically and are safe
as S3 key segments without encoding.

**Implementation note:** The backend config key for this bucket is
`shepard.volumes.s3.bucket` (default `shepard-volumes`), paralleling
`shepard.files.s3.bucket`. This key does not exist in the current codebase — it is
a code-side prerequisite that the VIS-X1 plugin must add to its plugin manifest and
application config. This doc is the design contract; the code lands with VIS-X1.

---

## §3 CORS configuration

OME-Zarr chunk reads happen **browser-direct**: the frontend fetches chunk keys
directly from Garage, bypassing the Shepard backend. Neuroglancer, VTK.js, and
zarrita.js all issue `GET` and `HEAD` requests with a `Range` header. Neuroglancer
additionally issues CORS preflights (`OPTIONS`).

Required CORS rules for `shepard-volumes`:

| Field | Value |
|---|---|
| `AllowedOrigins` | Your instance's frontend origin(s) — e.g. `https://shepard.example.org`. Do NOT use `*` unless the bucket is fully public. |
| `AllowedMethods` | `GET`, `HEAD`, `OPTIONS` |
| `AllowedHeaders` | `Authorization`, `Range`, `Content-Type`, `Accept`, `Origin`, `X-Requested-With` |
| `ExposeHeaders` | `Content-Range`, `Accept-Ranges`, `ETag`, `Content-Length` |
| `MaxAgeSeconds` | `3600` (1 hour — reduces preflight frequency for long viewer sessions) |

**How to apply in Garage v1.0.x.**

Garage does not yet expose CORS via its CLI (`garage bucket` has no `--cors` flag in
v1.0.1). Apply via the S3 API using the `aws` CLI or any S3-compatible tool:

```bash
# Replace ORIGIN with your frontend URL (no trailing slash).
ORIGIN="https://shepard.example.org"
BUCKET="shepard-volumes"
ENDPOINT="http://localhost:3900"  # or your Garage S3 port

# Write the CORS config (stdin):
aws s3api put-bucket-cors \
  --bucket "${BUCKET}" \
  --endpoint-url "${ENDPOINT}" \
  --cors-configuration '{
    "CORSRules": [{
      "AllowedOrigins": ["'"${ORIGIN}"'"],
      "AllowedMethods": ["GET", "HEAD", "OPTIONS"],
      "AllowedHeaders": ["Authorization", "Range", "Content-Type",
                         "Accept", "Origin", "X-Requested-With"],
      "ExposeHeaders": ["Content-Range", "Accept-Ranges", "ETag", "Content-Length"],
      "MaxAgeSeconds": 3600
    }]
  }'

# Verify:
aws s3api get-bucket-cors \
  --bucket "${BUCKET}" \
  --endpoint-url "${ENDPOINT}"
```

Set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to the Garage key that was
granted `--owner` on `shepard-volumes` (the `shepard-volumes-backend` key created in
the deployment checklist below).

**If the instance is single-origin,** add only that origin. If the `v2` API and the
frontend are served from different subdomains, list both. Avoid `*` — it makes the
CORS grant permanent and non-revocable regardless of Shepard's own ACL.

---

## §4 Presigned-URL TTL for chunk reads

The VIS-X1 `VolumeResolver` returns a presigned GET URL pointing at the
`{filePayloadAppId}.zarr/` prefix. The browser's zarrita.js / Neuroglancer fetcher
appends individual chunk paths to this base URL.

**There is no single presigned URL for the whole store.** The resolver mints a
short-lived signed URL for the `.zarr/.zattrs` metadata file; individual chunk
requests use un-signed paths gated by Garage's bucket ACL (public read) or by a
per-viewer session token (restricted collections).

### TTL defaults and the P23 constraint

The existing `PresignTtlValidator` (P23, `backend/src/main/java/de/dlr/shepard/storage/PresignTtlValidator.java`)
caps every effective presigned TTL at `shepard.permissions.cache.ttl` (default
`PT5M`). Any configured TTL above the cache TTL is silently clamped with a startup
WARN.

For a volume viewer session the default `PT5M` (5 minutes) is too short — a
cold-cache pyramid build can take several minutes, and the user's viewer will still
be requesting chunks when the URL expires.

**Recommended configuration pair for VIS deployments:**

```properties
# application.properties (or env vars with SHEPARD_ prefix)

# Raise the permissions-cache TTL first — this is the binding constraint.
shepard.permissions.cache.ttl=PT1H

# Then set the download TTL to match (or less).
# The VolumeResolver uses the download-url TTL for its presigned chunk base URL.
shepard.storage.presign.download-ttl=PT1H
```

**Trade-off:** raising the cache TTL to 1 hour means a revoked user can still access
presigned URLs (already issued) for up to 1 hour after revocation. For restricted
research data this matters — operators should weigh this against the user experience
cost of 5-minute viewer sessions expiring mid-render.

**For public collections** (world-readable data with no access control): configure
the `shepard-volumes` bucket for public read (no signing required for GET), and skip
the presigned URL mechanism entirely for those collections. The VolumeResolver will
need a `publicRead` flag per VolumeViewShape recipe — document this as a VIS-X1
design requirement.

**For restricted collections:** the 1-hour pair above is the recommended default.
Set `shepard.storage.presign.download-ttl=PT15M` and
`shepard.permissions.cache.ttl=PT15M` if your access-control model is stricter.

---

## §5 Retention policy hooks (SM1 interface contract)

A Zarr store for a single payload is a key prefix with thousands of chunk files. The
SM1a orphan-retention sweep (`aidocs/16-dispatcher-backlog.md` SM1a) MUST NOT delete
chunk files one-by-one — this would leave half-deleted Zarr stores that crash any
viewer trying to open them (missing `.zattrs` or missing resolution-level chunks
causes an uncatchable reader error).

**Interface contract for SM1 + VIS-X1:**

1. **Delete-by-prefix, not delete-by-key.** When a DataObject is deleted or its
   retention window expires, the retention sweep calls
   `S3FileStorage.deletePrefix("volumes/{collectionAppId}/{dataObjectAppId}/")`.
   This is a new method not yet in the `FileStorage` SPI — VIS-X1 must add it.

2. **Snapshot before delete.** Per `PRE-MUT-SNAP1`
   (`aidocs/16-dispatcher-backlog.md`), any retention-driven deletion must take a
   pre-mutation snapshot of the affected scope. For Zarr stores the snapshot is the
   `.zattrs` + `.zgroup` metadata files only (not the full pyramid — that would
   defeat the point of deleting it). The snapshot artefact references the
   `filePayloadAppId` so the deletion is auditable.

3. **Lifecycle rule on the bucket (Garage native).** As a belt-and-suspenders
   measure, Garage supports S3 object lifecycle rules via
   `PUT /shepard-volumes/?lifecycle`. Set an expiry on the
   `volumes/{collectionAppId}/` prefix when a collection is archived:

   ```bash
   # Example: expire all chunks under a collection 30 days after archival.
   # The VIS-X1 archival hook writes an `:Activity` and calls this endpoint.
   aws s3api put-bucket-lifecycle-configuration \
     --bucket shepard-volumes \
     --endpoint-url http://localhost:3900 \
     --lifecycle-configuration '{
       "Rules": [{
         "ID": "expire-collection-{collectionAppId}",
         "Filter": {"Prefix": "volumes/{collectionAppId}/"},
         "Status": "Enabled",
         "Expiration": {"Days": 30}
       }]
     }'
   ```

   This is an example shape only. The actual lifecycle rule is written by the
   `VolumeRetentionService` (VIS-X1 code-side) using the collectionAppId at
   archival time.

4. **Orphan Zarr stores** (pyramidiser wrote the store but the filePayloadAppId was
   never committed to Shepard, e.g. after a failed upload): these have no Neo4j
   counterpart. The SM1 orphan sweep identifies them by listing the bucket and
   diffing against `:FileReference` nodes in Neo4j. The grace window is the SM1a
   per-container `orphan_retention_days` setting (default: 365 days).

---

## §6 Deployment checklist

Complete these steps before enabling the VIS-X1 plugin. Steps 1–4 are one-time
setup; step 5 is per-instance verification.

**Prerequisites:** Garage is up and healthy (`docker compose --profile files-s3 up -d`
returns healthy on `shepard-garage`). The `shepard-files` bucket already exists per
the main activation runbook.

---

**Step 1 — Create the `shepard-volumes` bucket.**

```bash
docker exec shepard-garage /garage bucket create shepard-volumes
```

Verify:

```bash
docker exec shepard-garage /garage bucket list
# shepard-files should already appear. shepard-volumes should now appear too.
```

---

**Step 2 — Create and authorize a dedicated Garage key.**

Using a separate key (not the `shepard-backend` key that owns `shepard-files`)
makes ACL rotation and audit easier:

```bash
docker exec shepard-garage /garage key create shepard-volumes-backend
# Capture "Key ID:" and "Secret key:" from the output immediately — shown once.

docker exec shepard-garage /garage bucket allow \
  --read --write --owner \
  shepard-volumes \
  --key shepard-volumes-backend
```

Set the key in your compose env (add to `.env` or `docker-compose.override.yml`):

```
SHEPARD_VOLUMES_S3_ACCESS_KEY_ID=<Key ID from above>
SHEPARD_VOLUMES_S3_SECRET_ACCESS_KEY=<Secret key from above>
SHEPARD_VOLUMES_S3_BUCKET=shepard-volumes
SHEPARD_VOLUMES_S3_ENDPOINT=http://garage:3900
SHEPARD_VOLUMES_S3_REGION=garage-region
```

*(These env var names are the expected mapping for the `shepard.volumes.s3.*`
config keys that VIS-X1 will introduce. They do not exist in the codebase yet.)*

---

**Step 3 — Apply CORS rules.**

Using the `aws` CLI against Garage (see §3 for the full command). Replace
`ORIGIN` with your frontend's public URL:

```bash
ORIGIN="https://shepard.example.org"
AWS_ACCESS_KEY_ID=<Key ID> \
AWS_SECRET_ACCESS_KEY=<Secret key> \
aws s3api put-bucket-cors \
  --bucket shepard-volumes \
  --endpoint-url http://localhost:3900 \
  --cors-configuration '{
    "CORSRules": [{
      "AllowedOrigins": ["'"${ORIGIN}"'"],
      "AllowedMethods": ["GET", "HEAD", "OPTIONS"],
      "AllowedHeaders": ["Authorization","Range","Content-Type","Accept","Origin","X-Requested-With"],
      "ExposeHeaders": ["Content-Range","Accept-Ranges","ETag","Content-Length"],
      "MaxAgeSeconds": 3600
    }]
  }'
```

---

**Step 4 — Raise presigned-URL TTL (optional but recommended).**

Add to `application.properties` or as env vars:

```properties
shepard.permissions.cache.ttl=PT1H
shepard.storage.presign.download-ttl=PT1H
```

See §4 for the trade-off discussion. Skip this step if your instance uses
`PT5M` for strict access revocation and your volumes are small enough to
render within 5 minutes.

---

**Step 5 — Enable the VIS-X1 plugin and verify health.**

```bash
# Add the vis-volume profile (VIS-X1 introduces this compose profile):
docker compose --profile files-s3 --profile vis-volume up -d

# Check the volume-pyramidiser sidecar is healthy:
docker compose ps | grep pyramidiser
# Expected: "shepard-volume-pyramidiser ... Up (healthy)"

# Test end-to-end: upload a small TIFF via the UI,
# wait for the pyramidiser to write the Zarr store,
# then open the DataObject's volume view.
# Expect chunks to appear at:
#   http://localhost:3900/shepard-volumes/volumes/{coll}/{do}/{fp}.zarr/.zattrs

# Verify the chunk is readable from the browser origin (CORS check):
curl -H "Origin: https://shepard.example.org" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS \
     "http://localhost:3900/shepard-volumes/volumes/.../test.zarr/.zattrs"
# Expect: HTTP 200 with Access-Control-Allow-Origin header.
```

---

## §7 Gaps and future work

| Gap | Resolution path |
|---|---|
| `shepard.volumes.s3.*` config keys do not exist yet | VIS-X1 plugin manifest adds them. This doc is the design spec. |
| `FileStorage.deletePrefix()` method not in SPI | VIS-X1 must extend the SPI (or introduce `VolumeStorage` as a separate SPI). Cite `feedback_reuse_before_reimplement.md` before designing a new SPI. |
| Public-collection bypass (no signing needed for world-readable data) | VolumeViewShape needs a `publicRead: boolean` slot; `VolumeResolver` skips signing when set. Design in VIS-X1. |
| Garage v1.0.1 has no CLI for CORS | aws CLI workaround documented above. Track Garage upstream for native CLI support. |
| Lifecycle rule automation | `VolumeRetentionService` (VIS-X1) writes per-collection lifecycle rules on archival. Not yet implemented. |
| Zarr version | OME-Zarr v0.5 (Zarr v3 array store). If the pyramidiser writes v0.4 (Zarr v2) the browser client library (`zarrita.js v0.4`) must match. Pin both in VIS-X1. |
