---
stage: feature-defined
last-stage-change: 2026-05-26
task: "#96"
id: TPL17
---

# TPL17 — Distributed Ledger Anchoring for Tamper Evidence

## 1. Why

EN 9100 (aerospace quality management) and EASA Part 21 (G) demand immutable audit
trails: once a `prov:Activity` is recorded, a third party must be able to independently
verify that it has not been modified after the fact.  Shepard already maintains an
HMAC chain on `:Activity` nodes (`auditHmac` / `auditPrevHmac`, shipped in the
activity overhaul, see `aidocs/workflows/55-provenance-and-activity-overhaul.md`).  The HMAC chain
proves internal consistency but requires trust in the Shepard operator — anyone with
the `instance_secret` can recompute the chain after tampering.

Distributed ledger anchoring closes this gap: a SHA-256 digest of the activity's
JSON-LD serialisation is written as immutable data into a public blockchain,
providing a cryptographically verifiable *external* timestamp and content proof.
The ledger transaction is controlled by no single party; even the Shepard operator
cannot alter a committed anchor.

IME / AQE persona assessment (aidocs/agent-findings/manufacturing-quality.md):
**CRITICAL** for EN 9100 production use.

---

## 2. Backend Options

### Phase 1 — Bloxberg (research Ethereum sidechain)

| Attribute | Value |
|-----------|-------|
| Network | Ethereum sidechain operated by Max Planck Digital Library for research institutions |
| Finality | ~15 s |
| Cost | Free for registered research institutions |
| Anchoring endpoint | `POST https://certify.bloxberg.org/certifyData` |
| Payload | `{ "publicKey": "<wallet>", "crid": "<sha256-hex>", "cridType": "sha2-256", "metaData": { "creatorName": "<instanceId>" } }` |
| Response | `{ "txHash": "0x...", "blockHash": "0x...", "blockNumber": N, "timestamp": epoch }` |
| Receipt format | Full JSON response stored verbatim in `Activity.ledgerAnchor` |
| Prerequisites | Bloxberg wallet address + API key; DLR ZLP is an eligible institution |
| Reference | https://bloxberg.org, https://certify.bloxberg.org/docs |

Bloxberg is the Phase 1 target: low barrier, free, DLR-aligned, short finality.

### Phase 2 — OpenTimestamps (Bitcoin OP_RETURN)

| Attribute | Value |
|-----------|-------|
| Network | Bitcoin mainnet (OP_RETURN via Merkle tree aggregation) |
| Finality | ~1 h (next Bitcoin block after aggregation) |
| Cost | Free (aggregation servers are community-operated) |
| Anchoring | Java subprocess calling `ots stamp <sha256>` *or* HTTP POST to a public stamper |
| Receipt format | `.ots` binary blob (base64-encoded) stored in `Activity.ledgerAnchor` |
| Prerequisites | OpenTimestamps Java/CLI client on classpath, or network access to `https://alice.btc.calendar.opentimestamps.org/` |
| Reference | https://opentimestamps.org, https://github.com/opentimestamps/java-opentimestamps |

OpenTimestamps is the Phase 2 alternative / fallback for operators who prefer Bitcoin
over the Bloxberg sidechain or require a non-institutional anchor.

---

## 3. Design Decision (Phase 1 = Bloxberg)

Phase 1 ships Bloxberg only.  The provider field in the `LedgerAnchorRequestIO` body
allows callers to explicitly request `"bloxberg"` or `"opentimestamps"`.  If omitted,
the runtime default is taken from `shepard.ledger.default-provider` (application.properties).

OpenTimestamps becomes LDGR1b once the Bloxberg implementation is validated on live
EN 9100 data.

---

## 4. What Gets Anchored

Each anchor operation targets one or more `prov:Activity` nodes identified by
`appId`.  The canonical input to the SHA-256 digest is the JSON-LD serialisation
of the Activity with the following stable field set (order fixed alphabetically):

```json
{
  "actionKind": "...",
  "agentUsername": "...",
  "appId": "...",
  "endedAtMillis": 0,
  "method": "...",
  "originInstance": "...",
  "path": "...",
  "startedAtMillis": 0,
  "status": 0,
  "summary": "...",
  "targetAppId": "...",
  "targetKind": "..."
}
```

Fields that are themselves integrity primitives (`auditHmac`, `auditPrevHmac`,
`secretVersion`, `ledgerAnchor`) are excluded from the digest input to avoid
circular dependency.

The resulting digest and ledger receipt are stored back on the `:Activity` node
as the `ledgerAnchor` JSON blob:

```json
{
  "provider": "bloxberg",
  "digest": "<sha256-hex>",
  "txHash": "0x...",
  "anchoredAt": "<ISO-8601>",
  "receipt": { /* verbatim provider response */ }
}
```

---

## 5. Admin REST Surface

All endpoints require `instance-admin` role.

### 5.1 Trigger anchor batch

```
POST /v2/admin/ledger/anchor
```

Request body (`LedgerAnchorRequestIO`):

```json
{
  "activityAppIds": ["019...uuid7", "019...uuid7"],
  "provider": "bloxberg"
}
```

- `activityAppIds`: 1–100 Activity `appId` values. Required.
- `provider`: `"bloxberg"` | `"opentimestamps"`. Optional; falls back to
  `shepard.ledger.default-provider`.

Response `202 Accepted`:

```json
{
  "jobId": "019...uuid7",
  "status": "queued",
  "message": "Anchor job queued for 3 activities via bloxberg."
}
```

The job runs asynchronously.  Polling interval is not prescribed; 30 s is reasonable
for Bloxberg (15 s finality), minutes for OpenTimestamps.

### 5.2 Poll anchor job

```
GET /v2/admin/ledger/anchor/{jobId}
```

Response `200 OK` (`LedgerAnchorJobIO`):

```json
{
  "jobId": "019...uuid7",
  "status": "complete",
  "message": "3/3 activities anchored via bloxberg."
}
```

Status values: `queued` | `running` | `complete` | `failed`.

### 5.3 Get anchors for a DataObject

```
GET /v2/data-objects/{appId}/ledger-anchors
```

Returns all `:Activity` nodes linked to the DataObject that have a non-null
`ledgerAnchor` field, with the anchor metadata inlined.  Allows an auditor to
list and verify all tamper-evident records for a given DataObject without
knowing the Activity `appId` values in advance.

---

## 6. Feature Toggle

The ledger subsystem is **off by default**.  Set in `application.properties`:

```properties
# Distributed ledger anchoring (TPL17).  Off by default; enable for EN 9100 production.
shepard.ledger.enabled=false
shepard.ledger.default-provider=bloxberg
# Bloxberg credentials — obtain from certify.bloxberg.org
shepard.ledger.bloxberg.api-key=
shepard.ledger.bloxberg.wallet-address=
```

The `LedgerAnchorRest` bean is gated by
`@LookupIfProperty(name = "shepard.ledger.enabled", stringValue = "true", lookupIfMissing = false)`.
When the toggle is off, CDI does not instantiate the bean and JAX-RS returns 404
for all `/v2/admin/ledger/*` paths.

Runtime flipping via `FeatureToggleRegistry` (A3b) is a Phase 2 enhancement;
Phase 1 requires a service restart to enable/disable.

---

## 7. Phasing

| Phase | Tag | Scope | Status |
|-------|-----|-------|--------|
| 1 — Design + skeleton | LDGR1 | This doc + thin REST returning 501 | **design done** |
| 2 — Bloxberg client | TPL17a | `LedgerAnchorService`, Bloxberg HTTP client, async job store in Neo4j | queued |
| 3 — OpenTimestamps | TPL17b | OTS client, receipt storage, upgrade path from Bloxberg | queued |
| 4 — Verification CLI | TPL17c | `shepard-admin ledger verify <activityAppId>` standalone verifier | queued |
| 5 — Audit UI | TPL17d | `/v2/admin/ledger` page showing anchor status per DataObject | queued |

---

## 8. Upstream Impact

No upstream-breaking changes.  All new surface is additive under `/v2/admin/ledger/`
and requires `instance-admin`.  Upstream operators who never enable
`shepard.ledger.enabled=true` are unaffected.

See `aidocs/34-upstream-upgrade-path.md` for the admin upgrade ledger row.

---

## 9. References

- Bloxberg: https://bloxberg.org / https://certify.bloxberg.org/docs
- OpenTimestamps: https://opentimestamps.org
- W3C PROV-O: https://www.w3.org/TR/prov-o/
- EN 9100:2018 (aerospace QMS) — Section 8.5.2 (identification and traceability)
- EASA Part 21 Subpart G — production organisation approval, record requirements
- `aidocs/workflows/55-provenance-and-activity-overhaul.md` — the HMAC chain this extends
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI (ledger client may become a plugin)
