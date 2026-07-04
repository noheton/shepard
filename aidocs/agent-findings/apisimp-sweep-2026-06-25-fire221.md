---
stage: fragment
date: 2026-06-25
author: claude-sonnet-4-6
---

# API Simplification Sweep — 2026-06-25 (fire-221)

Read-only sweep of `backend/src/main/java/de/dlr/shepard/v2/` REST surface.
Checked for the seven APISIMP smell categories:
1. Numeric Neo4j IDs leaking through the v2 wire
2. Per-kind bespoke paths not yet unified
3. Inconsistent pagination params (missing pagination on list endpoints)
4. Bare `@QueryParam` / `@Schema` without adequate description
5. Non-RFC-7807 error bodies on v2 endpoints
6. `@Path(Constants.SHEPARD_API + ...)` violations in the v2 package
7. Missing `containerAppId` / `appId` parallel fields

## Status

Prior sweeps (fire-211 through fire-220) filed: APISIMP-SQL-CONTAINER-ID-NUMERIC,
APISIMP-CONTAINERS-PERMS-IO-NUMERIC, APISIMP-LJE-DATAOBJECTID-NUMERIC,
APISIMP-CONTAINERS-LIST-NO-PAGINATION, APISIMP-NOTIFICATIONS-LIST-NO-PAGINATION,
APISIMP-PROJECTS-LIST-NO-PAGINATION, APISIMP-WATCHES-LIST-NO-PAGINATION.
All merged or blocked. This sweep scanned packages not reached by prior fires:
`collectionwatchers`, `timeseries` (CrossDo bulk endpoint), `publish`, `quality`,
`admin/semantic`. Two candidate findings were dropped after in-code verification:
`lastRotatedAt` in `AdminUserGitCredentialRest` is explicitly documented as
an operator-facing rotation-decision field (not verbose); `@Parameter` annotations
ARE present on `PublicationsListRest` path params.

## Findings

### MINOR-1 — `GET /v2/collections/{collectionAppId}/watches` unbounded list (CW1)

- **ID**: APISIMP-PAGINATION-LIST-WATCHERS
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/resources/CollectionWatchersRest.java:73-80`
- **Severity**: MINOR
- **Category**: Inconsistent pagination

`CollectionWatchersRest.list()` returns an unbounded `List<CollectionWatcherIO>` with no
`page`/`pageSize` params. The `@Operation` description notes "the number of watchers is
expected to be small relative to the DataObject count," but there is no hard cap —
on an instance with many users, a widely-watched Collection would return an unbounded
list. Inconsistent with the `APISIMP-WATCHES-LIST-NO-PAGINATION` fix just applied to
the sibling `CollectionWatchesRest` endpoint.

**Fix**: Add optional `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` (server cap 200);
propagate to `CollectionWatcherService.list(collectionAppId, caller, page, pageSize)`;
add `X-Total-Count` response header. The "small count" assumption becomes a default
rather than a constraint.
AC: `GET /v2/collections/{collectionAppId}/watches?page=0&pageSize=20` returns first 20;
`X-Total-Count` header present; `mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-PAGINATION-LIST-WATCHERS in aidocs/16

---

### MINOR-2 — `CrossDoBulkDataRequestIO.downsampleTo` minimum undocumented in schema

- **ID**: APISIMP-UNDOCUMENTED-DOWNSAMPLE-MIN
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/timeseries/io/CrossDoBulkDataRequestIO.java:73-77`
  and `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkDataRest.java:190-195`
- **Severity**: MINOR
- **Category**: Bare `@Schema` without adequate description

`downsampleTo` has `@Schema(description = "LTTB target rows per series. Default 500. Hard cap 5000.")`.
The clamp logic in `clampDownsample()` enforces minimum 1, but the `@Schema` does not document the
minimum — a caller who passes `downsampleTo: 0` gets silently clamped to 1 without knowing this from
the OpenAPI spec. The description also doesn't mention that `null` falls back to the default 500.

**Fix**: Extend the `@Schema` description to include the minimum and null-fallback:
`"LTTB target rows per series. Minimum 1, default 500 (when null or omitted), hard cap 5000."`
Also add `minimum = 1, maximum = 5000` to the `@Schema` annotation for machine-readable
constraint expression.
AC: OpenAPI spec for `CrossDoBulkDataRequestIO.downsampleTo` shows `minimum=1`, `maximum=5000`,
and the description includes the null-fallback behaviour; `mvn verify -pl backend` green.
No runtime change.

- **Size**: XS
- **Filed as**: APISIMP-UNDOCUMENTED-DOWNSAMPLE-MIN in aidocs/16

---

### MINOR-3 — `GET /v2/{kind}/{appId}/publications` path embeds kind as segment

- **ID**: APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/publish/resources/PublicationsListRest.java:65`
- **Severity**: MINOR
- **Category**: Per-kind bespoke path (structural)

The Publications list endpoint is mounted at `/v2/{kind}/{appId}/publications` where `{kind}` is
a path segment (e.g. `data-objects`, `collections`). This breaks the canonical v2 resource-first
pattern (`/v2/resources/{appId}/sub-resource`) and makes the endpoint difficult to discover from
an entity `appId` alone — callers must know the entity's kind to construct the URL.
`/v2/publications?entityAppId=…` would be discoverable from any entity; the current shape forces
callers to embed kind as a path token.

**Fix (medium-term)**: Add a flat alias `GET /v2/publications?entityAppId={appId}` that does not
require the kind prefix; the existing `{kind}/{appId}` path stays for backward compat but is
marked deprecated in OpenAPI. The alias resolves the entity kind from the registry internally.
AC: `GET /v2/publications?entityAppId=<uuid>` returns the same list as
`GET /v2/data-objects/<uuid>/publications`; `{kind}/{appId}` form returns identical results;
`mvn verify -pl backend` green.

- **Size**: M
- **Filed as**: APISIMP-PUBLICATIONS-KIND-PATH-SEGMENT in aidocs/16

---

### MINOR-4 — `GET /v2/collections/{collectionAppId}/dqr` unbounded list (TPL10)

- **ID**: APISIMP-PAGINATION-LIST-DQR
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:78-86`
- **Severity**: MINOR
- **Category**: Inconsistent pagination

`CollectionDQRRest.list()` returns an unbounded `List<DQRIO>` with no `page`/`pageSize` params.
DQR count per collection is practically small today, but there is no enforced cap.
Inconsistent with paginated collection-scoped list endpoints (DataObjects, References).

**Fix**: Add optional `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` (server cap 200);
propagate to `DQRService.list(collectionAppId, caller, page, pageSize)`;
add `X-Total-Count` response header.
AC: `GET /v2/collections/{collectionAppId}/dqr?page=0&pageSize=10` returns first 10 DQRs;
`X-Total-Count` header present; `mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-PAGINATION-LIST-DQR in aidocs/16

---

### MINOR-5 — `GET /v2/admin/semantic/git-sources` unbounded list (TPL5)

- **ID**: APISIMP-PAGINATION-LIST-GIT-SOURCES
- **Location**: `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/OntologyGitSourceRest.java:94-101`
- **Severity**: MINOR
- **Category**: Inconsistent pagination

`OntologyGitSourceRest.list()` calls `gitSourceDAO.listAll()` and returns the full list
with no `page`/`pageSize` params. Admin-only endpoint, but still unbounded — an instance
with many registered ontology git sources returns an uncontrolled result set. Inconsistent
with the pattern used by other admin list endpoints.

**Fix**: Add optional `@QueryParam("page") @DefaultValue("0") int page` and
`@QueryParam("pageSize") @DefaultValue("50") int pageSize` (server cap 200);
replace `gitSourceDAO.listAll()` with a paginated DAO query; add `X-Total-Count` header.
AC: `GET /v2/admin/semantic/git-sources?page=0&pageSize=20` returns first 20 sources;
`X-Total-Count` header present; `mvn verify -pl backend` green.

- **Size**: S
- **Filed as**: APISIMP-PAGINATION-LIST-GIT-SOURCES in aidocs/16

---

## Already-tracked / not a new finding

- `AdminUserGitCredentialRest.lastRotatedAt` — intentional, documented operational field
  ("operators see only the discovery metadata they need to decide whether to rotate");
  not verbose. Non-finding.
- `PublicationsListRest` path param annotations — `@Parameter(description=..., required=true)`
  IS present on both `{kind}` and `{appId}` at lines 107–110. Non-finding.
- `CrossDoBulkDataRest.list()` does not have a pagination issue — it's a POST with a
  request body scoped to specific `dataObjectAppIds`, not an unscoped list endpoint.
