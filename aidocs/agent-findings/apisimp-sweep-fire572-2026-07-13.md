---
stage: fragment
last-stage-change: 2026-07-13
---

# APISIMP Sweep — fire-572 (2026-07-13)

Scan of the live `/v2` REST surface (`backend/src/main/java/de/dlr/shepard/v2/**`) for residual API sprawl. Cross-referenced against all existing `APISIMP-*` rows in `aidocs/16-dispatcher-backlog.md` to surface only genuine new findings.

**Rows filed this fire:** 5 (2 S + 1 XS + 1 XS gated + 1 decision)

---

## Finding 1 — `APISIMP-ANNOTATION-EPOCH-MS-TO-ISO`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/annotations/io/AnnotationIO.java:72,75`
- `backend/src/main/java/de/dlr/shepard/v2/annotations/io/CreateAnnotationIO.java:65,68`
- `backend/src/main/java/de/dlr/shepard/v2/annotations/io/UpdateAnnotationIO.java:39,42`

`AnnotationIO`, `CreateAnnotationIO`, and `UpdateAnnotationIO` all carry `validFromMillis` and `validUntilMillis` as `Long` epoch-milliseconds on both the request (POST/PATCH) and response (GET) surfaces of the semantic annotation endpoints. The `@Schema` descriptions already say "millis since epoch," making these fields explicitly epoch-ms — a convention outlier now that `APISIMP-GIT-SOURCE-EPOCH-MS-BODY` and `APISIMP-PROV-ISO8601-TIMESTAMPS` have shipped ISO 8601 strings for other timestamp fields.

Three mapping sites must be updated: `AnnotationIO.from()` at line ~108 passes `a.getValidFromMillis()` and `a.getValidUntilMillis()` directly and must call `Instant.ofEpochMilli(v).toString()` with a null-guard on each. The `SemanticAnnotationV2Rest` `@Operation` text at lines 626–627 and 731–732 references these field names by description and must be updated. The wire fields should be renamed `validFrom`/`validUntil` (dropping the `Millis` suffix) for ISO 8601 convention consistency.

**Fix:** Change `Long validFromMillis`/`Long validUntilMillis` → `String validFrom`/`String validUntil` across all three IO classes; update `AnnotationIO.from()` mapping; update `SemanticAnnotationV2Rest` `@Operation` descriptions.

**Size:** S  
**AC:** `GET /v2/annotations/{appId}` response body carries `validFrom`/`validUntil` as ISO 8601 UTC strings; POST/PATCH request bodies accept ISO 8601 strings in the same fields; `mvn verify -pl backend` green.

---

## Finding 2 — `APISIMP-ACTIVITY-EPOCH-MS-TO-ISO`

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/io/ActivityIO.java:40,43`

`ActivityIO.startedAtMillis` and `endedAtMillis` are `Long` epoch-ms values on the `GET /v2/provenance/activities` response. The `@Schema` descriptions already document them as "Millis since epoch when the activity began/ended" — making these the last two explicitly epoch-ms-described Long fields on the v2 activity surface.

Three mapping sites must be updated: `ActivityIO.from()` at line ~87 passes `a.getStartedAtMillis()`/`a.getEndedAtMillis()` directly; `metadataOnly()` at line ~112 and `relationsOnly()` at line ~122 pass `startedAtMillis` positionally while setting `endedAtMillis` to `null`. All three methods must use `Instant.ofEpochMilli(...).toString()` with null-guards.

The `?cursor`-pagination endpoints that use epoch-ms `?from`/`?to` query params are a separate concern (APISIMP-PROVENANCE-CURSOR-UNDOCUMENTED, ✅ done) and are not affected by this change.

**Fix:** Change `Long startedAtMillis`/`Long endedAtMillis` → `String startedAt`/`String endedAt` in `ActivityIO`; update `from()`, `metadataOnly()`, `relationsOnly()`; update `@Schema` descriptions.

**Size:** S  
**AC:** `GET /v2/provenance/activities` response body carries `startedAt`/`endedAt` as ISO 8601 UTC strings; `mvn verify -pl backend` green.

---

## Finding 3 — `APISIMP-MULTI-IO-EPOCH-MS-TO-ISO`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/template/io/ShepardTemplateIO.java:57,60`
- `backend/src/main/java/de/dlr/shepard/v2/notifications/io/NotificationIO.java:41,44`
- `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/io/NotificationTransportReadIO.java:34`
- `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/io/TimeseriesContainerChartViewIO.java:28`

Four IO classes carry timestamp fields as `Long` epoch-ms in v2 GET responses:

1. `ShepardTemplateIO.createdAt`/`updatedAt` (lines 57/60) — returned on `GET /v2/templates/{appId}`.
2. `NotificationIO.createdAtMillis`/`expiresAtMillis` (lines 41/44) — the `@Schema` annotations added in PR #2516 now document them as epoch-ms, making the type mismatch with ISO 8601 convention more visible, not less.
3. `NotificationTransportReadIO.lastTestedAt` (line 34 record component) — `GET /v2/admin/notifications/transports`.
4. `TimeseriesContainerChartViewIO.updatedAt` (line 28 record component) — `GET /v2/timeseriescontainers/{appId}/views`.

All four are response-body-only changes; no migration needed.

**Fix:** Convert all six fields to `String` ISO 8601; rename `createdAtMillis`/`expiresAtMillis` → `createdAt`/`expiresAt`; update `@Schema` descriptions; update factory/mapping methods.

**Size:** XS  
**AC:** All four GET responses return the above fields as ISO 8601 UTC strings; `@Schema` descriptions updated; `mvn verify -pl backend` green.

---

## Finding 4 — `APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP`

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:518,527-530`

`ProvenanceRest.stats()` carries a hidden `@Parameter(hidden=true) @QueryParam("entityId")` param and a rejection block (lines 527–530) that returns 400 with "use 'subject' instead". This tombstone was added in fire-568 (PR #2505, sha `ecef299`) when `?entityId=` was renamed to `?subject=`. The `@APIResponse` at line ~511 includes the phrase "or use of removed 'entityId' parameter" which can be simplified once the tombstone is gone.

Per `APISIMP-PROV-STATS-ENTITYID-RENAME`, the tombstone must stay for a 10-fire stabilization window. **Gate: not before fire-578.**

**Fix:** Remove the `legacyEntityId` param declaration and the `if (legacyEntityId != null)` rejection block; simplify the `@APIResponse` description at line ~511.

**Size:** XS  
**AC:** `ProvenanceRest.stats()` signature contains no `legacyEntityId` param; `mvn verify -pl backend` green.

---

## Finding 5 — `APISIMP-PROVENANCE-LIMIT-TO-PAGESIZE`

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:134,202,248,301,347,382`

Six `ProvenanceRest` endpoints use `?limit=N` as the page-size parameter while all other v2 list endpoints use `?pageSize=N`. The `APISIMP-PROVENANCE-CURSOR-UNDOCUMENTED` row (✅ done, PR #2076) argued for keeping `?limit` on cursor-based semantics, but none of these six endpoints actually use a cursor token — they use `?from`/`?to` epoch-ms bounds. The "cursor" justification no longer holds.

This is a **decision row** — resolve before dispatching. Recommendation: rename with a 10-fire tombstone for `?limit`.

**Size:** S  
**AC (if rename proceeds):** All six endpoints accept `?pageSize=N`; `?limit=N` rejected with 400 + migration hint for 10 fires; `mvn verify -pl backend` green.

---

## Rows not filed (already tracked or not actionable)

- `TimeseriesChannelV2IO.int id` → `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` (✅ done fire-218).
- `TimeseriesChannelV2IO.long containerId` → `APISIMP-TSCHANNEL-CONTAINER-ID` (gated on Postgres migration).
- `PermissionAuditEntryIO.Long neo4jNodeId` → `APISIMP-PERMISSION-AUDIT-NEO4J-ID` (gated on L2 migration).
- `JupyterConfigPublicRest` 301 tombstone → `APISIMP-JUPYTER-PUBLIC-REST-DELETE` (gated fire-573).
- Hidden `?entityId` tombstone in ProvenanceRest → filed as `APISIMP-PROVENANCE-ENTITYID-TOMBSTONE-DROP` above (gated fire-578).
- `?limit` naming on ProvenanceRest → filed as `APISIMP-PROVENANCE-LIMIT-TO-PAGESIZE` decision row above.
