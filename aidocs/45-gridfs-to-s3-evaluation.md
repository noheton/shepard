# GridFS → S3 — Evaluation

**Scope.** Evaluate migrating shepard's primary file storage from
**MongoDB GridFS** (today) to **S3-compatible object storage**
(MinIO / AWS S3 / Azure Blob via S3 gateway / Ceph Rados Gateway).
Recommend an architectural shape, document the trade-offs, sketch a
migration runway.

**Status.** Concept design.
**Snapshot date.** 2026-05-08.
**Originating items.** User question: "have you evaluated the
benefits of migrating from GridFS to S3?" Issue **#27** ("Evaluate
Object Storages") is open and old; this is the focused write-up
that's been missing. Prior scattered mentions in `aidocs/31`
(O3 — drop export at S3 presigned URL), `aidocs/32` (long-running
result delivery via S3 presigned), `aidocs/33` (P12 frontend
uploads via presigned PUT) — all about *output* paths, none about
the primary file storage.

## 1. Today: GridFS, briefly

`backend/src/main/java/de/dlr/shepard/data/file/services/FileService.java`:

- One **MongoDB collection per `FileContainer`**, named
  `"FileContainer" + UUID` (`FileService.createFileContainer:48`).
  GridFS lays this out as `<oid>.files` + `<oid>.chunks` underneath.
- Files chunked at **1 MiB** (`CHUNK_SIZE_BYTES = 1024 * 1024`,
  line 30).
- Inserts compute MD5 inline via `DigestInputStream` (line 24).
- Reads via `gridBucket.openDownloadStream(fileId)` (line 120) —
  shepard backend always proxies the bytes; no presigned URLs today.
- Deletes via `gridBucket.delete(ObjectId)` (line 167).
- All state lives in the same MongoDB the structured-data API uses
  (`StructuredDataService`).

The FileContainer's Mongo collection name is stored on the Neo4j
`FileContainer` node (`mongoId` property). The Neo4j-side identity
lookup → Mongo `mongoId` lookup → GridFS bucket → file is the
read path.

## 2. What S3-compatible storage would change

### 2.1 Wins

| # | Win | Why |
|---|---|---|
| W1 | **Presigned URLs** — clients fetch directly from object storage | Frees the shepard backend from being the file-bytes proxy. Today every file download burns shepard CPU + bandwidth + JVM heap chunks. Concrete asks: P12 frontend uploads (`aidocs/33`), R2 RO-Crate ZIP delivery (`aidocs/31 §O3`), `aidocs/32` long-running result URLs. **Same primitive solves all three**. |
| W2 | **Cheap at scale** — $0.023/GB/month (S3 Standard) vs $0.10–$0.30/GB/month for managed Mongo storage | Object storage is purpose-built for cold/warm bytes; Mongo storage is purpose-built for hot indexed documents. shepard's files are mostly write-once-read-rare. |
| W3 | **Backup independence** — file blobs back up via S3 lifecycle / versioning, not via Mongo dumps | Today's `mongodump` backup of a 100 GB FileContainer takes hours and the dump is bigger than the source (BSON overhead). S3 versioning + cross-region replication is one config change. |
| W4 | **Range requests + multipart upload** built-in | Resumable uploads (#699 area), partial reads, large-file streaming — all native to S3, hand-rolled in GridFS. |
| W5 | **Per-object lifecycle** — auto-archive cold files to Glacier/cold tier | Today shepard has no concept of file temperature; S3 lifecycle policies handle the retention question without shepard code. |
| W6 | **Operational separation** — file-storage outage doesn't degrade Mongo metadata reads | Today a slow GridFS read can pin a Mongo connection in the same pool that serves StructuredData reads. Splitting the storage tier removes the contention. |
| W7 | **Scaling beyond Mongo's practical limits** — Mongo file collections in the TB range get hairy | Object storage scales horizontally; Mongo replica-set storage is bounded by the slowest member's disk. |

### 2.2 Costs

| # | Cost | Notes |
|---|---|---|
| C1 | **New infrastructure dependency** — operators run an S3-compatible service | MinIO is the lightweight self-hosted answer (~50 MB binary, S3-compatible, BSL/AGPL-licensed). For all-in-one shepard installs, MinIO joins the compose stack as a profile-bound service alongside HSDS (`aidocs/35`). |
| C2 | **Auth bridge** — shepard's per-Collection / per-DataObject permissions vs S3's IAM/bucket-policy model | Two paths: (a) the **backend stays in the data path** for permission enforcement (proxies streams from S3, defeats W1); (b) **presigned URLs** carry per-request scope (does W1 properly but requires careful URL TTL + revocation thinking). Recommend (b) with short TTLs (≤ 5 min). Detailed in §4. |
| C3 | **Migration cost** — copying existing FileContainers from GridFS to S3 takes wall-clock and bytes-out from Mongo | A 1 TB shepard install needs ~1 TB read from Mongo + 1 TB write to S3, single-pass. Doable in hours; needs a background job not a `docker compose up` migration. Detailed in §6. |
| C4 | **Multi-region complexity** | Mongo replica sets handle this for GridFS; S3 cross-region replication needs operator config. For single-region installs, no extra work. |
| C5 | **Backup story is now multi-system** | Mongo backups + S3 backups, two procedures. Mitigated because S3's own lifecycle/versioning is *the* backup primitive. |
| C6 | **Eventual deprecation of GridFS code paths** | If we go all-in, the GridFS code can eventually be deleted. If we keep both, shepard maintains two storage paths — modest ongoing tax. |

### 2.3 Performance comparison (rule-of-thumb)

| Operation | GridFS (Mongo replica set, NVMe) | S3 / MinIO (single host, NVMe) |
|---|---|---|
| Small-file (< 1 MiB) write | ~3–8 ms | ~5–15 ms (TCP overhead dominates) |
| Small-file read | ~2–5 ms | ~3–8 ms |
| Large-file (1 GB) write | ~30–60 s (chunking + WiredTiger journaling) | ~10–25 s (multipart, sequential PUT) |
| Large-file read | ~30–60 s (chunked stream through Mongo) | ~10–25 s (presigned URL, direct CDN-like) |
| Range read of 10 MiB at offset 500 MiB in a 1 GB file | hits 11 chunks (10 + boundary), full Mongo round-trips | one HTTP `Range:` request, byte-precise |
| Concurrent 100-client large-file download | shepard backend bottleneck | object-storage scales horizontally |

**Net:** S3 wins every read/write that's > 100 MiB and is overwhelmingly
better for concurrent large-file traffic. GridFS stays competitive
for small files because the latency floor is set by MongoDB's
network round-trip rather than chunk count.

## 3. The recommended architecture: pluggable storage backend

Three architectures considered:

### 3.1 (A) Replace GridFS entirely with S3

Rip out GridFS; require an S3-compatible endpoint everywhere.

Pro: clean code, one path. **Con: forces every existing operator
into a migration before they can upgrade.** Hard no — we have an
upstream-compat constraint (`CLAUDE.md`) and a "comfortable for
admins" rule.

### 3.2 (B) Pluggable storage backend — the recommendation

Introduce a **`FileStorage` interface** with two implementations:

```java
public interface FileStorage {
  String storeFile(String containerOid, String name, InputStream in) throws IOException;
  InputStream readFile(String containerOid, String fileId) throws IOException;
  Optional<URL> presignedDownloadUrl(String containerOid, String fileId, Duration ttl);
  Optional<URL> presignedUploadUrl(String containerOid, String name, Duration ttl);
  void deleteFile(String containerOid, String fileId);
  void createContainer(String oid);
  void deleteContainer(String oid);
}
```

Two implementations:

- **`GridFsFileStorage`** — today's behaviour, refactored from
  `FileService`. **Stays the default** for backward compat and
  for the all-in-one install.
- **`S3FileStorage`** — uses AWS SDK v2 (`software.amazon.awssdk:s3`),
  configured via standard AWS env / config (`AWS_REGION`,
  `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
  `SHEPARD_FILES_S3_ENDPOINT_OVERRIDE` for MinIO). Optional
  `presignedDownloadUrl` / `presignedUploadUrl` return real
  presigned URLs; the GridFs impl returns `Optional.empty()` and
  callers fall back to backend-proxied streams.

Selection at startup: `shepard.files.storage = gridfs | s3` (default
`gridfs`). Resolved by a CDI `@Produces` bean. Single-file change at
the seam in `FileService` (the existing class becomes a thin facade
that delegates to `FileStorage`).

### 3.3 (C) Per-Collection storage choice

Different Collections use different backends — e.g. raw telemetry
to S3, small attached PDFs to GridFS.

Verdict: **out of scope for v1.** Adds significant complexity
(per-FileContainer storage tag in Neo4j, two paths in every read,
migration between backends within a Collection). Revisit if real
demand surfaces.

## 4. Auth bridge — the careful piece

**Today.** Backend reads bytes via Mongo connection; permission check
runs on the way in. Bytes never leave shepard's process boundary
without an authz pass.

**With S3 presigned URLs.** Two-step:

1. Client requests `GET /files/{appId}` → shepard checks permissions
   → returns `302` to a presigned S3 URL with TTL `PT5M`.
2. Client follows the redirect; S3 streams the bytes directly to
   the client. shepard's bandwidth is the redirect, not the file.

**Concerns + answers:**

- **TTL bound on presigned URL.** Default `PT5M` for downloads,
  `PT15M` for uploads (multipart can take longer). Configurable
  per-deployment. Short enough that screen-share leaks are limited;
  long enough that interactive use works.
- **Revocation.** Presigned URLs are immutable until they expire —
  no revocation. For sensitive data, the operator can set TTL
  shorter or use the GridFS-style backend-proxied path
  (`?proxied=true` query param, default off). Documented.
- **Audit.** S3-side access logs capture which presigned URL was
  used and from where; shepard-side issuance log captures who
  requested the URL. Joinable on request-id.
- **CORS.** S3 / MinIO need CORS rules to allow the shepard frontend
  to follow the redirect. One-line bucket policy on setup.

## 5. The cost model

For an institute running shepard:

| Setup | Mongo file storage | S3 file storage |
|---|---|---|
| **100 GB** of files | ~$10–$30 / month (managed Mongo) or NVMe $$ amortised | ~$2.30 / month (S3 Standard) or $0 (self-hosted MinIO + box you already own) |
| **1 TB** of files | ~$100–$300 / month or NVMe sizing pain | ~$23 / month (S3) or self-hosted equivalent |
| **10 TB** | impractical on managed Mongo's per-shard limits | ~$230 / month (S3) or self-hosted |

For DLR-style on-prem deployments the comparison is between
"buy/expand the Mongo machine's NVMe" and "stand up MinIO on a
storage node with cheap spinning disks." MinIO on rust-disk is
~10-100× cheaper than Mongo on NVMe per GB and the perf differential
for large files is small.

## 6. Migration runway (when an operator flips the toggle)

Three modes for the cutover:

| Mode | Shape | Use when |
|---|---|---|
| **Greenfield** | New shepard install with `shepard.files.storage = s3` from day 1. | New deployments. |
| **Big-bang** | Stop writes, run `shepard-admin files migrate gridfs s3`, flip config, restart. Linear in file count + size; needs a maintenance window. | Small installs (≤ 10 GB). |
| **Dual-store with background sweep** | Toggle to `s3-with-gridfs-fallback`. New writes go to S3. Background `shepard-admin files migrate-background` sweeps old FileContainers from GridFS to S3 in the background. Reads check S3 first, fall through to GridFS. After sweep completes, flip to `s3` and drop the GridFS dependency for that install. | Large installs where downtime is unacceptable. |

The migration job is implementable as an admin-CLI command
(`aidocs/22 §4.x`); ideally with progress reporting via the P3
`migration_progress` pattern.

**Rollback.** During the dual-store window, flipping back to `gridfs`
is safe — old files stay in GridFS; new files written during the
S3 window need a reverse sweep (sometimes acceptable, sometimes not
depending on how long the S3 mode ran). After the GridFS dependency
is dropped, no rollback — the old chunks are gone.

## 7. Compatibility with upstream

Per `CLAUDE.md`'s API-version policy: **`/shepard/api/files/...`
endpoint shape stays unchanged.** The wire contract (POST /
GET / DELETE) is identical regardless of backend. Two new
**`/v2/`** endpoints added:

- `POST /v2/files/{containerAppId}/upload-url` — returns a
  presigned upload URL (only when storage = s3 or capable; 404 with
  hint when GridFS).
- `GET /v2/files/{appId}/download-url` — returns a presigned
  download URL (same fallback).

The legacy `/shepard/api/files/{...}/payload` paths keep proxying
bytes regardless of backend — that's the upstream compatibility
contract. Native presigned-URL clients use `/v2/`.

## 7a. Downstream beneficiaries — what FS1 unblocks

FS1 isn't just a "files now live in S3" change — it's the substrate
for several other features that have been waiting on cheap,
direct-from-storage delivery. Three concrete:

### 7a.1 RO-Crate ZIP delivery (closes `aidocs/31` O3)

Today the RO-Crate exporter buffers the whole ZIP server-side and
streams it through shepard's HTTP layer on download. For a
multi-GB Collection that's heap pressure + bandwidth on every
fetch. With FS1:

- Exporter writes the ZIP as a multi-part upload to a dedicated
  `shepard-exports/` prefix in the FS1 bucket.
- `POST /v2/collections/{appId}/export` returns the **presigned URL**
  (FS1c) instead of streaming bytes. Client follows the redirect;
  S3 / MinIO serves the ZIP directly.
- Lifecycle policy on the prefix expires the export ZIP after
  `PT24H` (configurable) so the bucket doesn't grow unbounded.

Closes the `aidocs/31 §O3` "drop the export at an S3 URL" question
that's been queued.

### 7a.2 dataship as a publication pipeline (`aidocs/40 §3a`)

dataship's whole reason-for-being is "publish a shepard Collection
to a public archive." With FS1 it becomes:

1. dataship reads the snapshotted Collection (`aidocs/41` V2).
2. dataship invokes the RO-Crate export with `?snapshot={appId}`
   (PV1f from `aidocs/46` — byte-reproducible).
3. The export lands at a **public-bucket** prefix in the FS1
   bucket — `s3://<bucket>/published/<doi>/<crate>.zip`.
4. dataship hands the (now-permanent) URL to Zenodo / B2SHARE /
   the user's web archive.

**No new infrastructure.** The publication store *is* the same
S3 bucket FS1 already provisions, with a per-prefix bucket policy
flipping it from "presigned-only" to "public-read" on the
`published/` prefix.

### 7a.3 Other artifact hosting — generic `/v2/artifacts/`

Beyond RO-Crate and dataship, other shepard subsystems produce
"artifacts that should be downloadable but aren't payload-kind
references":

- Migration progress reports (P3).
- `shepard-admin payloads gc` dry-run reports (`aidocs/22 §4.x`).
- AI-generated dashboard exports (`aidocs/43 §5.8` saved
  dashboards as PNG / SVG / Vega-Lite spec ZIPs).
- Long-running job results (`aidocs/32`).

Today each subsystem invents its own delivery shape. After FS1c,
they all share **`/v2/artifacts/{type}/{id}/url`** which returns a
presigned URL into the FS1 bucket's `artifacts/` prefix. One
endpoint, one pattern, one TTL story.

This is a small follow-up after FS1c; doesn't need a separate
backlog row, but worth landing as part of the same FS1 wave so
the pattern doesn't fragment.

## 8. Verdict

**Yes — migrate, via path (B) pluggable storage backend.** The
combination of W1 (presigned URLs) + W2 (cost) + W6 (operational
separation) is decisive for any deployment ≥ 100 GB of files. The
cost is one new dependency (S3-compatible service, MinIO is a
trivial sidecar) and ~3-4 weeks of implementation work for the
storage abstraction + migration tooling. **Worth it.**

GridFS stays as the default for new all-in-one installs — keeps
the "5-minute setup" story intact for evaluation deployments. S3
becomes the recommendation for production deployments and lights
up automatically when the operator configures it.

## 9. Phasing — FS series ("File Storage")

| ID | Slice | Size | Gate |
|---|---|---|---|
| **FS1a** | `FileStorage` interface + `GridFsFileStorage` extracted from `FileService` (pure refactor; behaviour-equivalent). Tests pin existing behaviour. | M | None |
| **FS1b** | `S3FileStorage` implementation using AWS SDK v2 + endpoint-override config for MinIO. New `shepard.files.storage` config key + CDI selector. Backend-proxied path works against both. | M | FS1a |
| **FS1c** | Presigned-URL `/v2/` endpoints (`/files/{containerAppId}/upload-url`, `/files/{appId}/download-url`). Returns 404 when backend doesn't support presigned URLs. | S | FS1b |
| **FS1d** | MinIO sidecar in `infrastructure/docker-compose.yml` under `files-s3` profile (off by default; mirrors `spatial`/`hdf` patterns). One-line operator switch. | S | FS1b + `aidocs/22 §4.6a` profile-bound toggles |
| **FS1e** | `shepard-admin files migrate` CLI command (big-bang and background-sweep modes), progress via P3 pattern. | M | FS1a + FS1b + `aidocs/22` |
| **FS1f** | Frontend update — large-file uploads use the `/v2/upload-url` presigned path when available, fall back to backend-proxied. | M | FS1c + frontend changes |
| **FS1g** | RO-Crate export delivery (`aidocs/31 §O3`) returns presigned URLs when backend = s3. | S | FS1c + `aidocs/31` |
| **FS1h** | (deferred) Per-Collection storage choice (architecture C from §3.3) — only on real demand. | L | parked |

Recommended order: **FS1a → FS1b → FS1d → FS1c → FS1e → FS1f → FS1g**.
FS1a is a behaviour-preserving refactor that ships independently
(low risk, prepares the ground). FS1d (MinIO sidecar) ships before
FS1c (presigned endpoints) so operators have something to point at
when the new endpoints land.

## 10. Risks

- **Operator rollback to GridFS after dropping the GridFS code.**
  Out of scope per §6: once the operator ships a release with
  GridFS removed, rollback means restoring from backup. Document
  the no-going-back semantics in the release notes when GridFS
  goes from default-off to fully-removed.
- **MD5 computation in transit.** Today's `DigestInputStream`
  computes MD5 during write to GridFS. S3 supports server-side
  ETag (which is MD5 for non-multipart objects, opaque for
  multipart). The MD5 attribute on `ShepardFile` keeps working;
  the backend computes it on the way in either way.
- **Multi-tenant S3 bucket layout.** One bucket per shepard
  deployment vs. one bucket per FileContainer? Recommend
  **one bucket** with prefix-per-container
  (`<bucket>/<containerOid>/<fileId>`). Matches the GridFS
  one-collection-per-container shape; simpler IAM.
- **Cost surprise on egress.** AWS S3 charges for egress. If a
  shepard install has lots of internet downloads, the S3 bill can
  surprise. Document the per-deployment cost model in
  `docs/admin.md` under the storage chapter; recommend MinIO for
  high-egress installs.
- **CORS misconfiguration.** A wrong CORS rule on the S3 bucket
  manifests as silent failures in the browser. Provide a known-good
  CORS template in `docs/deploy.md`.

## 11. Cross-references

- **aidocs:** `aidocs/16` (FS-series queueing entry will follow this
  design), `aidocs/22 §4.6a` (profile-bound toggles — `files-s3`
  joins `spatial` / `hdf` / `monitoring`), `aidocs/31 §O3`
  (RO-Crate ZIP via presigned URL — closes this open question once
  FS1g lands), `aidocs/32` (long-running result delivery — same
  presigned-URL shape applies), `aidocs/33` (P12 frontend upload —
  benefits directly from FS1f), `aidocs/34` (CONFIG-status row when
  FS1b ships, BREAKING-status when GridFS removal lands; staged),
  `aidocs/44` (feature matrix gains an FS row).
- **Issues:** **#27** (Evaluate Object Storages — this design is
  the answer; close on FS1a landing).
- **Backlog:** new **FS1** umbrella + sub-IDs in `aidocs/16`.
