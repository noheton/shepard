---
stage: fragment
last-stage-change: 2026-07-12
---

# APISIMP Sweep — fire-570 (2026-07-12)

Scan of the live `/v2` REST surface (`backend/src/main/java/de/dlr/shepard/v2/**`) for residual API sprawl. Cross-referenced against all existing `APISIMP-*` rows in `aidocs/16-dispatcher-backlog.md` to surface only genuine new findings.

**Rows filed this fire:** 3 (all XS)

---

## Finding 1 — `APISIMP-NOTIF-IO-FIELD-SCHEMA`

**File:** `backend/src/main/java/de/dlr/shepard/v2/notifications/io/NotificationIO.java`

All 10 fields in `NotificationIO` lack per-field `@Schema` annotations. The class has a class-level `@Schema(description=...)` but field-level documentation is entirely absent. The two `Long` fields — `createdAtMillis` and `expiresAtMillis` — are indistinguishable from numeric Neo4j IDs in the generated OpenAPI output; a client has no way to know they are epoch-milliseconds.

Contrast with `ActivityIO`, where every field carries `@Schema(description="Millis since epoch when the activity began.")`.

**Fix:** Add `@Schema(description=..., readOnly=true)` to all 10 fields; for `createdAtMillis`/`expiresAtMillis` write "Milliseconds since Unix epoch when the notification was created/expires. Null means no expiry." for the nullable field.

**Size:** XS  
**AC:** All 10 `NotificationIO` fields appear in the OpenAPI output with non-empty descriptions. `createdAtMillis` and `expiresAtMillis` descriptions include "milliseconds since Unix epoch". `mvn verify -pl backend` green.

---

## Finding 2 — `APISIMP-WATCH-IO-FIELD-SCHEMA`

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/watches/io/WatchIO.java` (7 record components)
- `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/io/CollectionWatcherIO.java` (4 record components)

Both record types have a class-level `@Schema(description=...)` but zero per-field annotations on any component. The `since: Long` field present in both types has no documented unit — it is unclear whether it is epoch-ms, epoch-seconds, or something else. `watchAppId`, `containerKind`, `containerAppId`, `containerAvailability`, and `addedBy` in `WatchIO` are equally undocumented, making the OpenAPI spec for `GET /v2/watches` and `GET /v2/collections/{appId}/watchers` functionally opaque.

**Fix:** Annotate all components with `@Schema`; document `since` as "Epoch-milliseconds when this subscription was registered."

**Size:** XS  
**AC:** All 7 `WatchIO` components and all 4 `CollectionWatcherIO` components appear in the OpenAPI output with descriptions. `since` description includes "Epoch-milliseconds". `mvn verify -pl backend` green.

---

## Finding 3 — `APISIMP-GIT-SOURCE-EPOCH-MS-BODY`

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/io/OntologyGitSourceIO.java` (lines 59–69)

`lastIngestedAt` (line 60) and `createdAt` (line 69) are `Long` epoch-ms values returned in the GET response body for `GET /v2/admin/semantic/ontologies/{appId}`. The rest of the admin surface has adopted ISO 8601 strings for timestamp response fields (e.g., `PermissionAuditLog.from`/`to`, `InstanceAdminRest` uptime fields, and the APISIMP-PROV-ISO8601-TIMESTAMPS ProvenanceRest fix), making these two fields a convention outlier.

The `@Schema` descriptions currently read `"Epoch-ms of the last completed ingest run."` and `"Epoch-ms when this record was created."` — explicit but inconsistent.

**Fix:** Change both fields to `String`; update `OntologyGitSourceIO.from()` factory to serialize as `Instant.ofEpochMilli(v).toString()` (ISO 8601 UTC). Keep the `@Schema` `readOnly=true`. No migration needed — this is a response-body-only change.

**Size:** XS  
**AC:** GET response for `/v2/admin/semantic/ontologies/{appId}` returns `lastIngestedAt` and `createdAt` as ISO 8601 strings (e.g., `"2025-09-01T14:32:00Z"`). `@Schema` descriptions updated to drop "Epoch-ms" wording. `mvn verify -pl backend` green.

---

## Rows not filed (already tracked or not actionable)

- `SchemaType` usage in `DataObjectBatchV2Rest.java:139` — live `@Content` schema annotation, not a sprawl finding.
- `OntologyGitSourceRest` dual-auth — already parked as `APISIMP-GIT-SOURCE-DUAL-AUTH` (by design; see SemanticAdminRest defence-in-depth comment).
- Various in-memory paging patterns already tracked (APISIMP-GIT-SOURCE-IN-MEMORY-PAGING ✅ shipped).
