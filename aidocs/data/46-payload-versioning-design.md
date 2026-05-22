---
stage: feature-defined
last-stage-change: 2026-05-23
---

# Payload Versioning — Design

**Scope.** Extend shepard's versioning surface from **graph entities**
(`VersionableEntity`, today's `Version` marker, the V2 snapshots
designed in `aidocs/41`) down to **payload bytes** — the actual file
content, structured-data documents, timeseries data, and spatial
geometry that References point at.

**Status.** Design.
**Snapshot date.** 2026-05-08.
**Originating items.** User request: "extend the versioning feature
to payload versioning as well. consider implications." Couples to
`aidocs/41` (entity-revision snapshots) and `aidocs/45` (S3 file
storage — S3's native versioning is the cheapest path here).

## 1. The gap today

shepard's existing versioning model:

| Layer | What's versioned today | How |
|---|---|---|
| **Graph entity** (Collection, DataObject) | Tagged via `Version` marker on `VersionableEntity` (today: marker only, no freeze) | Today: live data even after tagging. After **V2** (`aidocs/41`): logical snapshot via `(entityAppId, revision)` pinning |
| **Payload** (file bytes, JSON doc, timeseries data, geometry) | **Not versioned.** | A re-upload either replaces the same OID (in-place mutation) or creates a new OID (the reference now points at a different payload entirely; old payload is orphan or gone) |

The **gap** is the payload layer. Three concrete failure modes
this allows today:

1. A user uploads `report-v1.pdf`, the lab journal cites
   "see attached test report"; the user later re-uploads to fix a
   typo; the journal entry now silently refers to a different
   file. **No audit trail; no way to retrieve the originally-cited
   bytes.**
2. An RO-Crate export (`aidocs/31`) cites a FileReference's
   current OID. If the file is re-uploaded between two exports, the
   two crates contain different bytes for "the same" reference —
   reproducibility is lost without anyone noticing.
3. The V2 snapshots design (`aidocs/41`) freezes entity references
   but **not the bytes the references point at**. A snapshot taken
   today and read tomorrow can return different file content if the
   user re-uploaded in between. The snapshot's "frozen" promise is
   only true at the graph layer, not at the payload layer.

This design closes the gap.

## 2. What payload versioning means per payload kind

Each payload kind has different storage and different semantics for
"version."

### 2.1 `FileReference` — the easy case

A file is a blob; "version" is "the blob you wrote at time T."
Every re-upload becomes a new version; old versions are retained
(subject to GC policy in §6).

**With FS1b S3 backend** (`aidocs/45`): trivial — S3's bucket-level
versioning does this natively. shepard records the `versionId` S3
returns.
**With GridFS backend** (today's default): the old GridFS OID is
**not** deleted on re-upload; shepard records `(payloadVersion,
gridFsOid)` tuples.

### 2.2 `StructuredDataReference` — also easy

Mongo doc; "version" is "the document body you wrote at time T."
On edit, copy-on-write: the previous doc stays, a new doc is
inserted, the reference's `payloadVersion` increments.

### 2.3 `SpatialDataReference` — case-by-case

PostGIS geometry rows. "Version" can mean either:

- (a) The whole geometry collection at a point in time (a snapshot
  of the rows for this reference).
- (b) Individual row revisions.

Recommend **(a)** for v1 — matches the file/doc shape, simpler.
Implementation: each version is a separate row group with a
`version_id` column; the reference picks the latest by default,
pinned-version reads filter by `version_id`.

### 2.4 `TimeseriesReference` — the awkward case

Timeseries are **already temporal** by construction. "Version" of a
timeseries is genuinely ambiguous:

| Interpretation | Verdict |
|---|---|
| Version = the data ingested up to time T | Already addressed by `time` filtering on the hypertable. Not a new feature. |
| Version = a complete re-ingest replacing the prior data | Useful — when a calibration is re-applied to historical data. |
| Version = a "branch" of timeseries with different processing | Out of scope (data lakehouse territory). |

**Recommendation: ship a "re-ingest as new version" flow only.**
Each `TimeseriesReference` carries a `payloadVersion` that bumps
when an admin / pipeline calls `POST /v2/timeseries/{appId}/reingest`.
The new version is a separate hypertable segment (or a `version_id`
column added to the existing hypertable + composite primary key).
Reads default to latest; pinned reads filter by `version_id`.

This is **separate from append-only writes**, which keep flowing
into the latest version.

## 3. The model

### 3.1 Schema additions

Each payload-bearing reference (`FileReference`,
`StructuredDataReference`, `SpatialDataReference`,
`TimeseriesReference`) gains:

```java
@Property("payloadVersion")
private long payloadVersion = 1;   // current/latest, monotonic
```

Plus a sibling `PayloadVersion` Neo4j node per version:

```java
@NodeEntity
public class PayloadVersion implements HasAppId {
  @Property private long version;          // 1, 2, 3, ...
  @Property private Instant createdAt;
  @Property private String createdBy;
  @Property private String sha256;         // payload digest, content-addressing
  @Property private long sizeBytes;
  @Property private String storageRef;     // backend-specific:
                                           //   GridFS: ObjectId
                                           //   S3:     versionId
                                           //   Mongo:  document _id
                                           //   Timescale: version_id
  @Property private Instant deletedAt;     // soft-delete; nullable
  @Relationship(type="VERSION_OF") private BasicReference reference;
}
```

`storageRef` is the backend-specific pointer; `sha256` enables
content-addressable dedup (two references uploading the identical
file share `storageRef` and consume one copy of bytes).

### 3.2 Read path

```
GET /v2/file-references/{appId}                      → latest version
GET /v2/file-references/{appId}?payloadVersion=3     → pinned version 3
GET /v2/file-references/{appId}/versions             → list of PayloadVersion records
GET /v2/file-references/{appId}/versions/3           → metadata for a specific version
```

Default-latest preserves today's read semantics. Pinning is opt-in.

### 3.3 Write path

`POST /v2/file-references/{appId}/payload` (multipart) — uploads the
new bytes, computes SHA-256 inline, creates a new `PayloadVersion`,
increments the reference's `payloadVersion` counter. Returns the
new version metadata.

**Idempotent re-upload.** If incoming SHA-256 matches the latest
`PayloadVersion`'s SHA-256, no-op (no new version created). Avoids
unintentional version churn from auto-retried uploads.

### 3.4 Deletion semantics

Three deletion verbs to clarify:

| Verb | Effect | Storage |
|---|---|---|
| `DELETE /v2/file-references/{appId}` | Soft-delete the reference | All versions stay; reference is marked deleted |
| `DELETE /v2/file-references/{appId}/versions/3` | Soft-delete a specific version | That version's bytes stay; `PayloadVersion.deletedAt` set; future reads of `?payloadVersion=3` return 410 Gone |
| `DELETE /v2/file-references/{appId}/versions/3?hard=true` | Hard-delete (admin only) | Bytes removed from storage; `PayloadVersion` row stays as a tombstone for audit |

Hard-delete refuses if any V2 snapshot pins this version
(`409 Conflict` listing the snapshot ids); same rule as
`aidocs/41 §6 risks`.

## 4. Cross-store atomicity

The hard part: a payload write touches **two** stores — Neo4j (new
`PayloadVersion` node) and the payload backend (S3 / GridFS /
Mongo / Timescale). Atomicity isn't free.

### 4.1 The simple rule

**Payload first, then Neo4j.** Order of operations:

1. Compute SHA-256 in transit.
2. Write bytes to the payload backend; capture `storageRef`.
3. If write succeeded: open Neo4j tx, create `PayloadVersion` node,
   increment counter, commit.
4. If Neo4j commit fails: the bytes are orphaned in the backend.
   Background GC sweep (per `aidocs/22 §4.x` admin CLI) reclaims
   orphans by sha256 difference: any `storageRef` not referenced
   by a `PayloadVersion` for `> PT24H` gets removed.

Inverse order (Neo4j first, payload second) leaves the user with a
"version 3 exists but you can't read it" failure mode, which is
worse than the orphan-bytes case.

### 4.2 Read consistency

Pinned reads (`?payloadVersion=N`) traverse Neo4j → `PayloadVersion`
node → `storageRef` → payload backend. If `PayloadVersion.deletedAt`
is set, return 410 Gone with the deletion timestamp. If `storageRef`
exists but the backend says "not found", return 502 — that's a
storage-tier corruption case, not a payload-version semantics case.

## 5. Snapshot interaction (V2 + payload)

The `aidocs/41` `Snapshot` model pins `(entityAppId, revision)`.
Payload versioning extends this to `(entityAppId, revision,
payloadVersion)` for payload-bearing entities.

`SnapshotEntry` schema gains:

```java
@Property private Long payloadVersion;  // null for non-payload entities
```

Reads via snapshot resolve both axes — `?snapshot={snapshotAppId}`
on a `GET /v2/file-references/{appId}` returns:

- the entity revision pinned by the snapshot, **and**
- the payload version pinned by the snapshot.

This gives the **byte-identical reproducibility** the V2 design
promised but couldn't deliver alone. RO-Crate exports against a
snapshot (V2d) become genuinely reproducible — a re-export weeks
later returns the same files.

## 6. Lifecycle / GC policy

Storing every version forever is the default for safety; but
unbounded growth needs admin policy. Three modes:

| Mode | Behaviour | Default |
|---|---|---|
| `keep-all` | Retain every version forever | Default |
| `keep-latest-N` | Keep latest N + any version pinned by a snapshot | Operator-configurable per Collection |
| `ttl` | Hard-delete versions older than T (admin), unless snapshot-pinned | Admin only |

Per-Collection setting: `Collection.payloadRetentionPolicy` (a JSON
blob with `{mode, n, ttl}` shape; nullable defaults to system-wide).

System-wide default: `shepard.payloads.retention.default = keep-all`
(safest); operator can flip to `keep-latest-N` with `N=10` for
"reasonable history without explosion." Snapshots **always**
pin — a snapshot-pinned version cannot be GC'd.

A `shepard-admin payloads gc` CLI (per `aidocs/22 §4.x`) walks
references, applies policy, hard-deletes eligible bytes, reports.

## 7. Cost concerns

Payload versioning multiplies storage. Three mitigations:

1. **Content-addressing via SHA-256.** Two uploads of identical
   bytes share `storageRef`. Common case: a user re-uploads "the
   same file" with no real change → no new bytes stored. Big win
   for backup-style workflows.
2. **S3 backend (FS1)** has cheap storage tiers; per-version
   archival to Glacier-class storage via S3 lifecycle rules is
   one bucket-policy line.
3. **Per-Collection policy** (§6) lets operators bound growth where
   needed without forcing the all-or-nothing choice.

Without S3 (GridFS default), payload versioning is more expensive
because Mongo storage is hot-tier. **Recommend the FS1 + payload-
versioning combo for any deployment that wants serious payload
versioning at scale.**

## 8. Coupling to other designs

| Design | Interaction |
|---|---|
| `aidocs/41` V2 snapshots | Extend `SnapshotEntry` with `payloadVersion: Long`; snapshots become byte-identical reproducible. |
| `aidocs/45` FS1 S3 backend | S3 native versioning is the cheapest implementation path. The `GridFsFileStorage` impl needs the explicit "old OID stays alive" trick; `S3FileStorage` gets it free. |
| `aidocs/31` RO-Crate exports | When emitting a FileReference into a crate, cite the pinned `(payloadVersion, sha256)`. With V2 + payload versioning together, the crate is bit-reproducible. |
| `aidocs/37` Lab journal | Journal entries cite the payload version as of authoring time (`{ref: <appId>, payloadVersion: 3}` in the entry's metadata). Re-renders show the user "this entry was about version 3." |
| `aidocs/38` Git references | Git already has versioning (commit SHA is the version); a `GitReference` mode-c "pinned snapshot" is the analogue — no extension needed in this design. |
| `aidocs/35` HDF5/HSDS | HSDS tracks dataset modifications natively; couple to HSDS's own versioning rather than reimplement. |
| `aidocs/30` Provenance | Payload-version transitions are first-class lineage events (`payload-modified-at`). |
| `aidocs/22` Admin CLI | New `shepard-admin payloads gc` + `shepard-admin payloads list <ref>` commands. |
| `aidocs/25` L2 chain | `PayloadVersion` nodes inherit `HasAppId` from L2a. No new constraint needed beyond `V19__Add_appId_constraint_PayloadVersion.cypher` at PV1a. |

## 9. Phasing — PV series

| ID | Slice | Size | Gate |
|---|---|---|---|
| **PV1a** | `PayloadVersion` Neo4j node + `payloadVersion` counter on `FileReference`. `V19__Add_appId_constraint_PayloadVersion.cypher`. Read path: `/v2/file-references/{appId}?payloadVersion=N` + version listing. Write path: SHA-256 dedup + new-version-on-upload. **`FileReference` only** in this slice; behaviour preserved for the legacy `/shepard/api/files/...` endpoints (always serve latest). | M | None — but FS1 (S3 backend) makes this dramatically easier; recommend landing FS1a first |
| **PV1b** | Same shape applied to `StructuredDataReference`. | M | PV1a |
| **PV1c** | Same shape applied to `SpatialDataReference` (using PostGIS `version_id` column on row groups). | M | PV1a |
| **PV1d** | `TimeseriesReference` re-ingest flow + version-aware reads. | M-L | PV1a + careful design review |
| **PV1e** | V2 snapshot extension — `SnapshotEntry.payloadVersion` field, snapshot-pinned reads resolve both axes. | S | PV1a + V2b (`aidocs/41`) |
| **PV1f** | RO-Crate export pins `payloadVersion` automatically when `?snapshot=` is set; cites SHA-256 in the manifest. | S | PV1a + V2d + `aidocs/31` |
| **PV1g** | Per-Collection retention policy (`Collection.payloadRetentionPolicy`) + `shepard-admin payloads gc` CLI. | M | PV1a + `aidocs/22` |
| **PV1h** | (deferred) Per-version permissions (today: inherit from reference). | L | parked |

Recommended order: **FS1a → FS1b → PV1a → PV1e → PV1g → PV1f → PV1b/c → PV1d**.

PV1a delivers the killer use case (file re-upload preserves history)
in the smallest viable slice. PV1e + PV1f together unlock
byte-reproducible RO-Crate exports — the headline reason this
design exists. PV1d (timeseries) is intentionally last because the
semantics are murkiest.

## 10. Migrations

| Migration | Scope | Idempotent | Notes |
|---|---|---|---|
| `V19__Add_appId_constraint_PayloadVersion.cypher` | Constraint on new label | Yes (`IF NOT EXISTS`) | PV1a |
| `V20__Backfill_initial_payload_version.cypher` | Set `payloadVersion = 1` on every existing `FileReference` (and per kind in PV1b/c) | Yes (`COALESCE(payloadVersion, 1)`) | One per PV1a/b/c |

No bytes-level migration — existing payloads stay where they are
and become "version 1" by definition.

**Tracker rows in `aidocs/34`:** PV1a/b/c each get a ZERO row
(additive nullable column on existing references; reads default to
latest, equivalent to today's behaviour). PV1g gets an AWARE row
(operator should know retention policy is now configurable).

## 11. Risks

- **Storage explosion.** Mitigated by (a) SHA-256 dedup for
  no-op re-uploads, (b) per-Collection retention policy, (c) S3
  cold-tier lifecycle. Document storage-cost-per-version in
  `docs/admin.md`.
- **Snapshot pinning prevents GC.** A long-lived snapshot keeps a
  payload version alive forever. Operator's responsibility; the
  GC tool reports what's pinned-by-snapshot in its dry-run output.
- **Cross-store partial-write.** Mitigated by §4.1 ordering
  (payload first); orphan-byte GC sweep cleans stragglers.
- **Read-path latency.** Pinned reads add one Neo4j hop (resolve
  `PayloadVersion`); negligible vs. the byte transfer time. The
  existing permission cache extends to `(user, appId, payloadVersion)`
  triples without changes.
- **Idempotent re-upload misfires.** If a user genuinely wants a
  new version with identical bytes (e.g. to record a re-validation
  event), the SHA-256 dedup blocks it. Add an explicit
  `?force=true` flag on the upload endpoint to override.
- **Mongo / Timescale GC complexity.** Hard-deleting old payload
  versions in Mongo / Timescale is more involved than S3's
  bucket-versioning delete. The PV1g GC CLI handles per-backend
  semantics.

## 12. Out of scope (deferred)

- **Per-version permissions** (PV1h, parked). Today's permissions
  are reference-scoped; making them per-version inflates the
  permission graph for a use case that hasn't surfaced.
- **Forking / branching of payload versions.** `(version, branch)`
  tuples — too heavyweight; out of scope.
- **Conflict resolution on concurrent writes** to the same
  reference. Last-writer-wins (the new version's `createdAt`
  records who won). A future "merge two payload versions" flow
  is out of scope.
- **Per-byte diffs** between versions for text-like payloads.
  Could be a UI affordance later; out of scope for the storage
  layer.

## 13. Cross-references

- **aidocs:** `aidocs/16` (PV1 series queueing entry will follow
  this design), `aidocs/22 §4.x` (admin CLI commands for `payloads
  gc` and `payloads list`), `aidocs/30` (provenance — payload-version
  transitions are lineage events), `aidocs/31` (RO-Crate emits
  pinned versions at PV1f), `aidocs/34` (per-slice ZERO/AWARE
  rows), `aidocs/35` (HSDS — couple to its native versioning),
  `aidocs/37` (lab journal cites payload version), `aidocs/38`
  (git references — already versioned by SHA), `aidocs/41` (V2
  snapshots — extended to dual-axis pinning at PV1e), `aidocs/44`
  (feature matrix — new row), `aidocs/45` (FS1 S3 backend — best
  shipping platform for payload versioning at scale).
- **Backlog:** new **PV1** umbrella + PV1a-PV1h sub-IDs in
  `aidocs/16`.
