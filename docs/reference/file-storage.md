# File storage

shepard stores file payloads (the bytes behind a `FileReference` or
a `FileBundleReference`) through a **pluggable storage backend**.
The SPI seam is in core; concrete adapters either ship in core
(the GridFS default) or as drop-in plugin JARs (S3-compatible
adapters, future SeaweedFS / Garage-direct adapters).

This page covers the operator surface: which adapter is active,
how to pick a different one, and what guarantees apply across the
upgrade path.

## Quick reference

| Knob | Default | Scope | Notes |
|---|---|---|---|
| `shepard.storage.provider` | `gridfs` | deploy-time only | Switches the active storage adapter. Set to `none` to disable file payloads (resolver-only). |
| `:ShepardFile.providerId` | `"gridfs"` | per-row Neo4j property | Internal bookkeeping. Stamped on every new upload + backfilled on legacy rows by V34. Not on the wire. |
| `shepard-admin storage status` | — | CLI verb | Read-only operator visibility. Reports MongoDB health as the proxy for "GridFS up?". |

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

### `s3` (FS1b, planned)

S3-compatible adapter for any endpoint that speaks the S3 wire
protocol — AWS S3, Cloudflare R2, Backblaze B2, Wasabi, Garage
(per `aidocs/63` ADR-0024), SeaweedFS, Ceph RGW, MinIO. Plugin
shape: `plugins/storage-s3/` produces a drop-in
`shepard-plugin-file-s3-<version>.jar`.

**When to flip to it.** Multi-TB deployments, presigned-URL needs
(direct frontend uploads / RO-Crate ZIP delivery from the bucket),
cross-region replication, or anyone who already runs an
S3-compatible service.

Not shipped yet. Status tracked in `aidocs/16` FS1b row and
`aidocs/44 §13a`.

## Switching the active adapter

`shepard.storage.provider` is **deploy-time only** per the
`CLAUDE.md` "cluster identity / topology" exception. Switching
the storage backend re-points the bytes pipeline; runtime flips
would orphan in-flight writes and break existing-row reads until
the FS1e migration sweep completes.

### Greenfield install (no existing files)

1. Pick the adapter you want — `gridfs` (default) or `s3` (once
   FS1b lands + you've installed the plugin).
2. Set `shepard.storage.provider=<id>` in `application.properties`
   (or env `SHEPARD_STORAGE_PROVIDER=<id>`).
3. Start shepard. New uploads land on the chosen backend.

### Existing GridFS install moving to S3

When FS1b ships, the recommended flow is:

1. Install the S3 plugin under `/deployments/plugins/`.
2. Configure the S3 endpoint + bucket + credentials per the
   plugin's own admin runbook (per-feature `:S3Config` singleton,
   admin REST + CLI parity per CLAUDE.md "admin-configurable").
3. **Keep `shepard.storage.provider=gridfs`** — for now.
4. When FS1e ships, run `shepard-admin files migrate gridfs s3`
   to copy existing payloads. Migration runs in background mode
   so the API stays available.
5. After the sweep finishes, set `shepard.storage.provider=s3`
   and restart. Existing rows already point at the right adapter
   via their `:ShepardFile.providerId`.

Until FS1e, the migration runway is a manual `mongodump` + `aws
s3 cp` sequence + a Cypher `MATCH (f:ShepardFile) SET f.providerId
= 's3'` stamp — workable but not the operator UX FS1e will land.

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

## Health check

`shepard-admin storage status` shows the MongoDB-side health
signal as the proxy for "is the default GridFS adapter up?":

```text
$ shepard-admin storage status
STORAGE — file-payload adapter status

FIELD              | VALUE
-------------------+------------------------------------------------
active provider    | (FS1d will expose this; check shepard.storage.provider)
gridfs connection  | UP

note: full provider listing + per-adapter detail lands in FS1d
      (GET /v2/admin/storage); FS1b adds the S3 adapter under plugins/storage-s3/.
```

Exit code: 0 when MongoDB is `UP`; 1 when `DOWN`. Usable as a
kubelet-style probe in a shell pipeline.

`--output=json` emits a machine-readable shape:

```json
{
  "activeProviderHint": "see shepard.storage.provider in application.properties (FS1d will expose it)",
  "gridfsConnection": "UP",
  "gridfsConnectionUp": true
}
```

FS1d (queued) will land `GET /v2/admin/storage` for the proper
per-adapter listing; until then the CLI's `--output=json` is the
stable surface to script against.

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
