---
stage: fragment
last-stage-change: 2026-05-23
---

# S-06 — Snapshots × Garage S3 (no versioning): Shepard absorbs the gap

## Synergy

Garage S3 (the planned backend per ADR-0024) **deliberately does not
implement S3 object versioning** — its issue tracker has refused
it. For most platforms this is a blocker for "immutable scientific
data" claims. For Shepard, the COW snapshot architecture
(`aidocs/data/41`) + the payload SHA-256 (PV1b, shipped) already
provides bit-exact immutability at the *application layer* — the
"Garage gap" becomes a non-issue and turns into a marketing point:
*"Shepard's immutability is at the data-context layer, not the
storage layer, and survives substrate swap."*

## Elements (named anchors)

- **Substrate decision (memory):**
  `project_storage_s3_garage` — S3 backend = Garage; MinIO rejected;
  ADR-0024.
- **Garage limitation:** Garage issue
  [#166 "Support S3 versioning"](https://git.deuxfleurs.fr/Deuxfleurs/garage/issues/166)
  open since 2022; explicitly out of scope per the maintainers.
- **Shepard feature (deployed):** Snapshots design —
  `aidocs/data/41-snapshots-design.md` (COW + frozen +
  reproducible).
- **Shepard feature (deployed):** Payload version history (PV1a/PV1b)
  — per-upload SHA-256 + version counter — `aidocs/data/46`,
  `aidocs/44 §Dev-track`.
- **Plugin (designed):** `shepard-plugin-file-s3` (FS1b) —
  `aidocs/40 §4`, S3-compatible file backend via FileStorage SPI.
- **Operator-facing:** S3 versioning gap is what blocks adoption
  of self-hosted MinIO / Garage in many regulated industries
  (per Object First whitepapers).

## Why this is non-obvious

- The Garage decision (ADR-0024) appears in the storage / ops layer
  and never references the snapshot design.
- The snapshot design (aidocs/41) treats Neo4j subtree reads as the
  scope and doesn't mention the S3-side payload story.
- PV1b's SHA-256 is described as a "version history" feature, not as
  the integrity-guarantee that compensates for missing S3 versioning.
- Combined, the three pieces produce something stronger than either
  vendor offers: **content-addressable payload at the file level + a
  COW snapshot at the graph level = the snapshot chain is the
  immutability boundary**. The S3 store can swap, the SHA stays.
- This is the structural reason Shepard's claim is durable: the
  immutability primitive lives in shepard's appId + SHA + snapshot
  ledger. Substrate selection (Garage now, Ceph RGW or AWS S3 later)
  becomes an operator decision, not a data-trust decision.

## Concrete output

### 1. Three-layer immutability stack

```
   ┌─────────────────────────────────────────────────────────────┐
   │  Snapshot S_n  (Neo4j subtree freeze, immutable, citable)   │
   │      ├─ each DataObject referenced by appId                 │
   │      └─ each payload referenced by SHA-256                  │
   └──────────────────────────┬──────────────────────────────────┘
                              │ resolves to
                              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Payload version (PV1b)                                     │
   │      file/{name}/versions  →  SHA-256 + uploadedAt + by     │
   └──────────────────────────┬──────────────────────────────────┘
                              │ resolves to
                              ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Object backend                                             │
   │  Garage / MinIO / AWS S3 / Ceph RGW — substrate-of-choice   │
   │  S3 versioning NOT REQUIRED (snapshot guarantees this above)│
   └─────────────────────────────────────────────────────────────┘
```

### 2. "Frozen URL" pattern

Every snapshot S_n exposes its DataObjects via a frozen URL shape:

```
GET /v2/snapshots/{snapshotAppId}/dataobjects/{dataObjectAppId}/files/{name}
```

The handler resolves `{name}` to the SHA-256 active at snapshot
time, then resolves SHA → object backend key. The same bytes return
forever, regardless of subsequent uploads to the same `name`. No S3
versioning required.

### 3. Tamper-detection on read

The download path computes the SHA-256 streaming on read and rejects
the response if mismatch. Garage corruption / silent bit-rot is
caught at the Shepard layer; the user receives RFC 7807 with
`integrity-mismatch` error code.

### 4. Migration scenarios become trivial

Operator wants to move from Garage to AWS S3? `POST /v2/admin/files/migrate`
(FS1e1, designed) walks every SHA, copies into the new backend, flips
the storage pointer. Snapshots remain valid because they reference
SHA, not S3 key. The FileStorage SPI seam (FS1b) is what makes this
work.

### 5. Marketing message

For funders / FAIR auditors: "Shepard's immutability is *content-
addressable*, not *storage-addressable*. Your data integrity does
not depend on a specific S3 vendor or feature flag. Snapshots are
citable forever even if you migrate substrates."

## Real-world use case

**Persona:** the DLR ZLP-Augsburg ops engineer choosing the
object backend for the MFFD campaign data. They want self-hosted
(data sovereignty), geo-distributed (Augsburg + Cologne), and
EN 9100-compliant (immutable audit trail). Today: they hesitate
between MinIO (community edition archived 2024), Garage (no S3
versioning), and AWS S3 (sovereignty issue). After this synergy:
they pick Garage knowing the snapshot + SHA-256 layer guarantees
the audit trail; the choice becomes ops-driven, not compliance-
driven.

For a regulated PLUTO mission archive: the EU regulator (ESA-equivalent
oversight) asks "prove this telemetry is bit-identical to what was
captured 18 months ago." The snapshot ID + SHA-256 resolves it
without touching the object backend's versioning configuration.

## External evidence

- **Garage issue #166 *Support S3 versioning*** —
  [git.deuxfleurs.fr/Deuxfleurs/garage/issues/166](https://git.deuxfleurs.fr/Deuxfleurs/garage/issues/166)
  Takeaway: maintainers explicit that versioning is "not in
  Garage's scope"; the upstream gap is durable.
- **Garage *S3 Compatibility status*** —
  [garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/)
  Takeaway: `GET / PUT / DELETE Object` supported; `PUT/GET Object
  Tagging`, `PUT/GET Object Retention`, `PUT/GET Object Lock`,
  versioning — **not supported**. Confirms the gap.
- **AWS *S3 Object Lock + Versioning* (Object First whitepaper)** —
  [objectfirst.com/blog/how-object-first-uses-the-s3-versioning-and-object-lock](https://objectfirst.com/blog/how-object-first-uses-the-s3-versioning-and-object-lock/)
  Takeaway: the industry standard for S3-tier immutability is
  versioning + Object Lock; without those, "immutable storage"
  claims need a different mechanism. Shepard's snapshot+SHA layer
  IS that mechanism.
- ***Immutable Snapshot Pipelines for IaC* (UMA Technology, 2025)** —
  [umatechnology.org/immutable-snapshot-pipelines-for-infra-as-code-playbooks-fit-for-ai-workloads](https://umatechnology.org/immutable-snapshot-pipelines-for-infra-as-code-playbooks-fit-for-ai-workloads/)
  Takeaway: the AI-data world is converging on snapshot-based
  reproducibility as the alternative to bucket-level versioning;
  Shepard's pattern is the platform-level shape of this trend.

## Effort estimate

**S (small).** Components:

- Snapshot design (aidocs/41) — already in the "deployed" stage per
  front-matter.
- PV1b — already shipped.
- FileStorage SPI (FS1b) — designed, will ship with the Garage
  switch.
- The *frozen URL* endpoint + tamper-detection on read — ~3-5 days
  once the snapshot endpoint is in place.
- Documentation positioning (the marketing message above) — half a
  day.

The synergy is more of a *recognition* than a build. The pieces are
already converging; what's missing is the explicit framing in the
ops + vision docs so operators don't gate Garage adoption on the
S3-versioning gap.

## Risk / counter-evidence

- The SHA-256 layer assumes payloads can be re-streamed on demand
  for verification. For very large HDF5 / video payloads, this is
  expensive. Mitigation: verify on first read after snapshot
  creation, then cache verification result; full re-stream only
  on operator demand.
- Garage's RAID / replication semantics differ from AWS S3. If a
  Garage node fails and the chunk is unrecoverable, the SHA layer
  detects the loss but cannot fix it. Mitigation: Garage's own
  replication factor (3 or more) is the data-durability primitive;
  the SHA layer is integrity-only.
- arXiv 2603.26983 (*Transparency as Architecture*) and similar
  pieces note that "users don't read provenance" — the marketing
  message above needs UI surfacing to land with non-expert users.
  Mitigation: a snapshot-citation badge in the UI ("This view is
  pinned to snapshot S_n, SHA verified") — small frontend work.
- The S3 Object Lock direction in the broader S3 ecosystem (per
  Allthings-distributed *S3 Files and the changing face of S3*,
  2026) is moving toward stronger storage-tier guarantees. Over
  time the synergy may become a "belt and braces" rather than a
  "compensation for the gap." That is still net good.
