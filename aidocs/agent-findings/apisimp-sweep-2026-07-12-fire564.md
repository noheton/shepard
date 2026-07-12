---
stage: fragment
last-stage-change: 2026-07-12
---

# APISIMP sweep — fire-564 (2026-07-12)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings are
confined to the fork's development surface; the frozen `/shepard/api/` surface was not
examined. Previous sweep: fire-563 (apisimp-sweep-fire-563, part of fire-563 run).

No forbidden `@Path(Constants.SHEPARD_API + ...)` additions found in `/v2/`.

---

## §F1 — Dead tombstone classes ready for deletion

### F1-1: `AnomalyDetectionTombstoneRest` — single 410 stub, ~68 fires old

`backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/AnomalyDetectionTombstoneRest.java`

APISIMP-ANOMALY-ACTION-PATH (shipped fire-496) moved anomaly detection from
`POST /v2/references/{appId}/detect-anomalies` to the unified actions surface
`POST /v2/references/{appId}/actions?action=detect-anomalies` and left this
single-method 410 tombstone behind. The tombstone has been live for ~68 fires —
well past the 10-fire stabilization window. No frontend or MCP caller references
the old path (verified: `grep -r "detect-anomalies" frontend/` returns only the
new actions surface).

**Filed as**: APISIMP-ANOMALY-TOMBSTONE-DELETE (XS)

### F1-2: `CrossDoBulkTombstoneRest` — single 410 stub, ~68 fires old

`backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkTombstoneRest.java`

APISIMP-CROSS-BULK-KIND-PATH (shipped fire-496) moved the cross-DO bulk endpoint from
`POST /v2/data-objects/cross-timeseries-bulk` to
`POST /v2/data-objects/cross-bulk?kind=timeseries` and left this single-method 410
tombstone behind. Same vintage as F1-1 (~68 fires), same verdict: delete.

**Filed as**: APISIMP-CROSSBULK-TOMBSTONE-DELETE (XS)

---

## §F2 — Ambiguous `?entityId=` in `ProvenanceRest.stats()`

`backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:517`

`GET /v2/provenance/stats?scope=…&entityId=…` uses `entityId` as a dual-purpose parameter:
- for `scope=collection` it is the collection's UUID v7 appId
- for `scope=user` it is a username string (not an appId at all)

The name `entityId` conflicts with Shepard's established identity vocabulary (`appId` = UUID v7,
`entityId` formerly meant the numeric Neo4j internal id), and it's doubly ambiguous here
because the same parameter holds either a UUID or a plain username depending on `scope`.
A clearer name is `subject` — neutral, scope-agnostic, does not imply numeric or UUID type.
The OpenAPI description already says "Collection appId for scope=collection, username for scope=user"
so the rename is purely the wire name.

**Filed as**: APISIMP-PROV-STATS-ENTITYID-RENAME (XS)

---

## Summary table

| ID | File | Size |
|---|---|---|
| APISIMP-ANOMALY-TOMBSTONE-DELETE | `AnomalyDetectionTombstoneRest.java` | XS |
| APISIMP-CROSSBULK-TOMBSTONE-DELETE | `CrossDoBulkTombstoneRest.java` | XS |
| APISIMP-PROV-STATS-ENTITYID-RENAME | `ProvenanceRest.java:517` | XS |
