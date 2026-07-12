---
stage: deployed
last-stage-change: 2026-07-12
---

# APISIMP Sweep — fire-568 (2026-07-12)

Triggered by: all queued APISIMP rows were either merged (fire-548–567 campaign) or
blocked (operator decision / stabilization window / L2e). Background sweep agent
`afaa4a081abc0789a` running concurrently to scan full v2 surface; this file captures
the manual incremental scan that ran in parallel.

Last shipped before this sweep:
- `APISIMP-ANOMALY-TOMBSTONE-DELETE` (fire-565, sha `d03b923`)
- `APISIMP-CROSSBULK-TOMBSTONE-DELETE` (fire-567, PR #2504, sha `0105edb`)
- `APISIMP-PROV-STATS-ENTITYID-RENAME` (fire-568, PR #2505, sha `ecef299`)

All fire-547 queued rows confirmed shipped:
- `APISIMP-TYPED-PRED-NEO4J-ID-DROP` — merged PR #2485 (fire-548)
- `APISIMP-XTOTALCOUNT-DOC-CLEANUP` — merged PR #2486 (fire-548)
- `APISIMP-ANNOT-LEGACY-FIELDS-DROP` — shipped PR #2487 (fire-549)
- `APISIMP-OID-PATHPARAM-REPLACE` — all 3 slices merged (PR #2488/2489/2490)

## Scope

Scan of `LabJournalEntryIO` (shared IO between v1 and v2 lab journal surfaces);
`ContainersV2Rest` Long query params (timestamp values — acceptable).
Fire-568 background agent also scanning full `de.dlr.shepard.v2/**/*IO.java` set.

---

## Finding 1 — APISIMP-LJE-ENTRY-ID-SUPPRESS (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/context/labJournal/io/LabJournalEntryIO.java:39`

**What's wrong:** `LabJournalEntryIO` carries `@Schema(readOnly = true, required = true) private Long id;`
at line 39 — this is the Neo4j OGM node ID of the LabJournalEntry itself, exposed
wire-visible on every `GET /v2/collections/{appId}/lab-journal-entries` response.
The entity now has `private String appId` (UUID v7, line 76, populated since J1d).
The numeric `id` has no `@Deprecated` annotation and no `@JsonIgnore`; it is the
current identifier used by v1 CRUD endpoints and the frontend's `LabJournalExistingEntry.vue`.

**Frontend callers (v1, numeric id):**
- `LabJournalExistingEntry.vue:44` — `getLabJournalById({ labJournalEntryId: model.value.id })`
- `LabJournalExistingEntry.vue:58` — `updateLabJournal({ labJournalEntryId: model.value.id, … })`
- `LabJournalExistingEntry.vue:75` — `deleteLabJournal({ labJournalEntryId: model.value.id })`
- `CollectionLabJournalEntryList.vue:87` — `:key="'lab-journal-' + entry.id"`

All four calls go through `useShepardApi(LabJournalEntryApi)` (v1 path), not `useV2ShepardApi`.

**Two-step fix:**

Step A (XS, fire-568): Add `@Deprecated @Schema(readOnly = true, required = true, deprecated = true, description = "DEPRECATED — numeric Neo4j OGM node ID. Use appId (UUID v7) instead.")` to `private Long id`. Additive — no wire break. Starts the deprecation clock. Shared IO so affects both v1 and v2 OpenAPI spec; deprecation annotation only.

Step B (M, APISIMP-LJE-ENTRY-V2-CRUD): Add v2 CRUD endpoints for individual lab journal entries by appId (`GET/PUT/DELETE /v2/lab-journal/{entryAppId}`); migrate `LabJournalExistingEntry.vue` + `CollectionLabJournalEntryList.vue` to `useV2ShepardApi` + `appId`; then add `@JsonIgnore` to `LabJournalEntryIO.id`.

**Size:** Step A = XS (one annotation change, one file). Step B = M.

---

## What was NOT found

- `ContainersV2Rest` `@QueryParam("start") Long start` / `@QueryParam("end") Long end`
  (line 717–719): Unix timestamp values, not entity IDs. Substrate-neutral. NOT filed.
- No new bare-array list endpoints in v2 (all use `PagedResponseIO`).
- No new `@PathParam Long` leaks in v2 (ContainersV2Rest timestamps excluded above).
- No `@Path(Constants.SHEPARD_API + ...)` in v2 package.

## New rows filed

| Row | Size | Status |
|-----|------|--------|
| APISIMP-LJE-ENTRY-ID-SUPPRESS | XS | ⏳ dispatched fire-568 (Step A) |
| APISIMP-LJE-ENTRY-V2-CRUD | M | ⏳ queued (Step B — after Step A stabilizes) |

## Next sweep trigger

After `APISIMP-JUPYTER-PUBLIC-REST-DELETE` ships (fire-573+): check for any
remaining tombstone stubs past stabilization window. Also: after Step B ships,
verify `LabJournalEntryIO.id` has `@JsonIgnore` and `entry.id` is absent from
frontend composables.
