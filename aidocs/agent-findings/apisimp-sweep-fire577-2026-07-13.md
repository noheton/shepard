---
stage: deployed
last-stage-change: 2026-07-13
---

# APISIMP sweep — fire-577 (2026-07-13)

Dispatcher fire-577. No named APISIMP row was dispatchable (all are decision rows
or gated until fire-578). Ran a full scan of `backend/src/main/java/de/dlr/shepard/v2/**`
IO classes and REST endpoints for residual sprawl.

## Scope

- All `*IO.java` classes in `/v2/**` for `Long`/`long` fields that are not
  byte-counts, durations, pagination counts, or nanosecond sensor timestamps.
- All `/v2/` REST endpoints for `@PathParam`/`@QueryParam` of numeric type
  where a UUID or ISO string would be idiomatic.
- Config registry coverage — all `ConfigDescriptor` beans.
- `@Path(Constants.SHEPARD_API + ...)` leaks into v2 namespace.

## What I found

### Epoch-ms series status: near-complete

The `*Millis → ISO 8601` campaign is substantially done. Previous fires converted:
- `AnnotationIO.createdAtMillis` / `updatedAtMillis` → ISO (fire-576, #2535)
- `ActivityIO.startedAtMillis` / `endedAtMillis` → ISO (fire-577, #2536)

Remaining epoch-ms `Long` fields on the REST wire surface:

| Field | IO class | Endpoint | Size |
|-------|----------|----------|------|
| `createdAt` (`Long`) | `OntologyAlignmentIO` | `GET /v2/semantic/ontology/alignment` | XS |
| `timestamp` (`long`) | `CollectionEventIO` | `GET /v2/collections/{id}/events` (SSE) | XS |
| `sinceMillis`, `untilMillis` (`long`) | `ProvenanceStatsIO` | `GET /v2/provenance/stats` | S |

### Config registry: comprehensive (16 descriptors)

All major backend and plugin features are registered under `GET/PATCH /v2/admin/config/{feature}`:
Backend: `featureToggles`, `provenance`, `jupyter`, `timeseriesQualityScoring`, `ror`,
`sqlTimeseries`, `semantic`, `autosweep`, `thermography`.
Plugins: `aas`, `unhide`, `datacite`, `video`, `ai`, `legacyV1`, `hdf`, `epic`.

`UnhideAdminRest` retains bespoke credential endpoints (harvest key mint/rotate/revoke) —
correctly NOT on the generic config registry per CLAUDE.md §"Surface operator knobs".
V2CONV-A4 + A7 are complete.

### No v1 path leaks

Zero `@Path(Constants.SHEPARD_API + ...)` in any v2 class. Confirmed clean.

### No numeric REST params

Zero `Long`/`long` `@PathParam` or `@QueryParam` in v2 REST endpoints
(excluding intentional nanosecond timeseries window params on
`GET .../channels/{id}/data`, `POST .../channels/data/bulk`, and `CrossDoBulkDataRest`).

### Intentional exceptions (not findings)

- `PermissionAuditEntryIO.neo4jNodeId` — deprecated triage handle, `deprecated=true`,
  tracked for removal post-L2 migration.
- `AdminMetricsSummaryIO.uptimeMillis` / `httpMeanRequestMillis` — *durations* (ms),
  not *timestamps*; ISO 8601 duration format would be less useful here.
- `ContainerStatsIO` (`pointCount`, `channelCount`, etc.) — raw counts and byte sizes.
- `ProvenanceStatsIO.bucketMillis` — bucket *duration* (86_400_000 = 1 day).
- `ProvenanceStatsIO.buckets` / `cumulative` — `List<long[]>` charting series;
  numeric timestamps are required for chart rendering.
- `TimeseriesAnnotationIO.startNs/endNs`, `CrossDoBulkDataRequestIO.start/end`,
  `LiveWindowResponseIO.windowStart/End`, `LiveWindowPointIO.timestamp` — all
  nanosecond or millisecond sensor timestamps for charting; intentionally numeric.

## New APISIMP rows filed

Three new rows added to `aidocs/16-dispatcher-backlog.md`:

1. **`APISIMP-ONTOLOGY-ALIGNMENT-EPOCH-MS-TO-ISO`** (XS) — convert
   `OntologyAlignmentIO.createdAt` from `Long` epoch-ms to `String` ISO 8601.
   Same pattern as ANNOTATION/ACTIVITY conversions.

2. **`APISIMP-COLLECTION-EVENT-EPOCH-MS-TO-ISO`** (XS) — convert
   `CollectionEventIO.timestamp` from `long` epoch-ms to `String` ISO 8601 UTC.
   SSE field; frontend charting code must be updated alongside.

3. **`APISIMP-PROVENANCE-STATS-EPOCH-MS-TO-ISO`** (S) — convert
   `ProvenanceStatsIO.sinceMillis` / `untilMillis` from `long` epoch-ms to
   `String` ISO 8601. `bucketMillis`, `buckets`, and `cumulative` stay numeric
   (duration + charting series).

## Dispatch recommendation for fire-578

Fire-578 unlocks two gated rows:
- `APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP` (XS) — drop tombstone field
- `APISIMP-LJE-ENTRY-V2-CRUD` (M) — lab journal entry CRUD surface

Dispatch `APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP` first (XS, lower risk).
The three new rows from this sweep are XS/S and can interleave.
