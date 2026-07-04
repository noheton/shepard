---
stage: fragment
date: 2026-06-25
author: claude-sonnet-4-6
---

# API Simplification Sweep — 2026-06-25 (fire-211)

Read-only sweep of `backend/src/main/java/de/dlr/shepard/v2/` REST surface plus
adjacent files. Checked for the seven APISIMP smell categories:
1. Numeric Neo4j IDs leaking through the v2 wire
2. Per-kind bespoke paths not yet unified under `?kind=`
3. Inconsistent pagination params (`size` vs `pageSize`, missing pagination on list endpoints)
4. Bare `@QueryParam` without `@Parameter` annotation
5. Non-RFC-7807 error bodies on v2 endpoints
6. `@Path(Constants.SHEPARD_API + ...)` violations in the v2 package
7. Missing `containerAppId` / `appId` parallel fields

## Status

Previous sweeps (fire-61 through fire-210) exhausted the `@Parameter` annotation
backlog. This sweep focused on structural issues: numeric IDs still on the v2 wire,
missing pagination on list endpoints.

## Findings

### MAJOR-1 — SQL timeseries endpoint exposes `container_id_in: List<Long>`

- **ID**: APISIMP-SQL-CONTAINER-ID-NUMERIC
- **Location**: `backend/src/main/java/de/dlr/shepard/data/timeseries/sql/SqlQuerySpec.java:110`
  and `backend/src/main/java/de/dlr/shepard/v2/sql/resources/SqlTimeseriesRest.java:188`
- **Severity**: MAJOR
- **Category**: Numeric Neo4j IDs leaking

`POST /v2/sql/timeseries` accepts a JSON body whose `where.container_id_in` field is
`List<Long>` — numeric Neo4j OGM container IDs. A caller must know the internal
numeric IDs of TimescaleDB containers to scope a cross-container timeseries query.
The v2 contract requires UUID v7 `appId` as the only cross-substrate handle.

**Fix**: Replace `container_id_in: List<Long>` with `containerAppIds: List<String>`;
resolve to numeric container IDs internally via `EntityIdResolver` before calling
`filterAllowedForUser`. Update the `@JsonProperty` annotation and the internal
`SqlQuerySpec.where()` record. Update `SqlTimeseriesRest:188` to pass resolved IDs.
AC: `POST /v2/sql/timeseries` body accepts `containerAppIds: ["uuid-v7", ...]`; old
`container_id_in: [long]` returns 400; `mvn verify -pl backend` green.

- **Size**: M
- **Filed as**: APISIMP-SQL-CONTAINER-ID-NUMERIC in aidocs/16

---

### MAJOR-2 — `PermissionsIO` leaks `entityId`, `readerGroupIds`, `writerGroupIds` on v2 wire

- **ID**: APISIMP-CONTAINERS-PERMS-IO-NUMERIC
- **Location**: `backend/src/main/java/de/dlr/shepard/auth/permission/io/PermissionsIO.java:19,34,36`
  and `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1295,1298`
- **Severity**: MAJOR
- **Category**: Numeric Neo4j IDs leaking

`GET /v2/containers/{appId}/permissions` returns `PermissionsIO` which exposes:
- `entityId: long` — Neo4j OGM node ID of the container entity
- `readerGroupIds: long[]` — Neo4j OGM IDs of reader UserGroups
- `writerGroupIds: long[]` — Neo4j OGM IDs of writer UserGroups

Parallel appId fields (`readerGroupAppIds: String[]`, `writerGroupAppIds: String[]`)
already exist and are populated. However the `PATCH /v2/containers/{appId}/permissions`
handler at lines 1295–1298 still reads from `readerGroupIds`/`writerGroupIds` (numeric)
in the request body — the only v2 PATCH that accepts numeric IDs as write input.

**Fix**: (1) Mark `entityId`, `readerGroupIds`, `writerGroupIds` as
`@Schema(deprecated=true)` + `@JsonProperty(access = READ_ONLY)` in `PermissionsIO`;
(2) update `PATCH` handler to resolve permissions only from `readerGroupAppIds` /
`writerGroupAppIds`; (3) return 400 if a body carries only numeric group ids without
the appId counterparts. AC: PATCH body with `readerGroupAppIds`/`writerGroupAppIds`
sets permissions; old numeric fields in PATCH body produce 400;
`mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-CONTAINERS-PERMS-IO-NUMERIC in aidocs/16

---

### MAJOR-3 — `LabJournalEntryIO.dataObjectId` is a numeric Neo4j OGM ID on the v2 wire

- **ID**: APISIMP-LJE-DATAOBJECTID-NUMERIC
- **Location**: `backend/src/main/java/de/dlr/shepard/context/labJournal/io/LabJournalEntryIO.java:18,86`
- **Severity**: MAJOR
- **Category**: Numeric Neo4j IDs leaking

`GET /v2/collections/{collectionAppId}/lab-journal-entries` returns entries whose
`dataObjectId: long` is a numeric Neo4j node ID (populated from
`labJournalEntry.getDataObject().getShepardId()` — the OGM numeric ID, not the UUID v7
`appId`). A caller correlating lab journal entries to DataObjects cannot use this field
against the v2 DataObject surface (which is keyed on UUID v7 `appId`).

**Fix**: Add `dataObjectAppId: String` field to `LabJournalEntryIO`, populated from
`labJournalEntry.getDataObject().getAppId()`. Annotate `dataObjectId` with
`@Schema(deprecated=true)`. Both fields present until callers migrate;
`dataObjectId` suppressed in a future sweep.
AC: `GET .../lab-journal-entries` response includes `dataObjectAppId` (UUID v7);
`mvn verify -pl backend` green. Additive — no wire break.

- **Size**: S
- **Filed as**: APISIMP-LJE-DATAOBJECTID-NUMERIC in aidocs/16

---

### MINOR-1 — `GET /v2/containers?kind=...` returns unbounded list (no pagination)

- **ID**: APISIMP-CONTAINERS-LIST-NO-PAGINATION
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:394-412`
- **Severity**: MINOR
- **Category**: Inconsistent pagination

`ContainersV2Rest.list()` returns a plain `List<ContainerV2IO>` with no `page` or
`pageSize` params — inconsistent with `/v2/references`, `/v2/collections`, and
`/v2/data-objects` which all paginate. A Collection with many containers returns an
uncontrolled result set that grows silently with the data.

**Fix**: Add optional `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` (server cap 200);
propagate to `ContainersV2Service.list()`; add `X-Total-Count` response header.
Omitting params preserves backward-compat (return-all mode with cap).
AC: `?page=0&pageSize=20` returns first 20; `X-Total-Count` present;
`mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-CONTAINERS-LIST-NO-PAGINATION in aidocs/16

---

### MINOR-2 — `GET /v2/notifications` returns unbounded list (hardcoded 200-row service cap, no pagination)

- **ID**: APISIMP-NOTIFICATIONS-LIST-NO-PAGINATION
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/notifications/resources/NotificationRest.java:42-65`
- **Severity**: MINOR
- **Category**: Inconsistent pagination

`NotificationRest.list()` accepts only `@Context SecurityContext sc` and returns all
notifications with a hardcoded 200-row cap in the service. An active instance-admin
receiving broadcast notifications can silently lose older notifications beyond the cap
with no way to page further. No `page` or `pageSize` params exist.

**Fix**: Add `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` (server cap 200);
propagate to `NotificationService.listForUser()`; add `X-Total-Count` header so the
bell-icon badge can show `>200` accurately.
AC: `?page=0&pageSize=50` returns first 50; `X-Total-Count` is total count;
`mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-NOTIFICATIONS-LIST-NO-PAGINATION in aidocs/16

---

## Already-tracked / not a new finding

- `/v2/files` and `/v2/bundles` — both have 410 tombstones (APISIMP-FILE-PATH-RETIRE-2
  and APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE, both ✅ done).
- Numeric `id` and `containerId` in `TimeseriesChannelV2IO` — tracked as
  APISIMP-TSCHANNEL-CONTAINER-ID (row 3715, slice 1 done #1984, slice 2 gated on
  TS-IDb/c migration).
- `PermissionAuditEntryIO.neo4jNodeId` — tracked as APISIMP-PERMISSION-AUDIT-NEO4J-ID
  (queued, blocked on L2 migration gate).
- `JupyterConfigPublicRest` at `/v2/jupyter/config` — intentional second endpoint for
  non-admins; not bespoke admin config, not a violation.
- `MffdProcessChainMappingRest` at `/v2/admin/mffd/process-chain-mapping` — MFFD plugin
  admin endpoint; outside the generic `AdminConfigRest` registry by design (domain-specific
  shape, not a singleton config knob).
