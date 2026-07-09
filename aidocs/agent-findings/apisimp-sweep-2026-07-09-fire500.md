---
stage: fragment
last-stage-change: 2026-07-09
---

# APISIMP sweep — fire-500 (2026-07-09)

Automated sweep of the `/v2/` REST surface by the hourly dispatcher. All findings are
confined to the fork's development surface; the frozen `/shepard/api/` surface was not
examined. Previous sweep: fire-496 (apisimp-sweep-2026-07-09-fire496.md).

---

## §F1 — Dead tombstone classes ready for deletion

### F1-1: `FileBundleReferenceRest` — 8 methods, all 410 Gone

`backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java`

APISIMP-BUNDLE-REF-KIND-UNIFY (✅ fire-457) migrated all CRUD to the unified
`/v2/references?kind=file-bundle` surface and left eight 410 stubs behind. Every
method body is a single `Response.status(GONE).build()` (plus a `LOG.debugf`). The
class now adds noise to OpenAPI and inflates the WAR deploy time. No callers in
`frontend/` (verified by `grep -r "v2/bundles" frontend/` returning only comment lines).

**Filed as**: APISIMP-BUNDLE-TOMBSTONE-DELETE (XS)

### F1-2: `WikiWriterTombstoneRest` — 1 method, 410 Gone, correct Location header

`plugins/wiki-writer/src/main/java/de/dlr/shepard/plugins/wikiwriter/resources/WikiWriterTombstoneRest.java`

Single `POST` stub at `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/wiki-write`
returning 410 Gone with correct `Location` header pointing to the canonical
`/v2/data-objects/{dataObjectAppId}/wiki-write`. The tombstone has existed long enough
that client migration is complete. Verify no `collectionAppId` path in `frontend/` then
delete.

**Filed as**: APISIMP-WIKI-TOMBSTONE-DELETE (XS)

---

## §F2 — Spring Data field naming in `PagedFilesIO`

`backend/src/main/java/de/dlr/shepard/v2/bundle/io/PagedFilesIO.java`

The paged-files envelope for `GET /v2/references/{bundleAppId}/groups/{groupAppId}/files`
uses Spring Data conventions (`size`, `totalElements`, `totalPages`) instead of the
standard v2 envelope fields used by `PagedResponseIO` everywhere else (`pageSize`,
`total`). A JS client needs to branch its deserialiser: `body.total` for every
other list endpoint, `body.totalElements` for bundle files. The `totalPages` field is
also redundant — clients compute it from `total / pageSize`.

**Filed as**: APISIMP-PAGEDFILES-SPRING-NAMING (S)

---

## §F3 — Missing `@Min`/`@Max` on bundle-files `pageSize`

`backend/src/main/java/de/dlr/shepard/v2/bundle/resources/BundleGroupsV2Rest.java` lines 354–359

The `listFiles` endpoint declares `@QueryParam("pageSize") @DefaultValue("200") Integer pageSize`
but clamps via `Math.min(MAX, Math.max(1, pageSize))` in Java, emitting no OpenAPI constraint.
The sibling `listGroups` endpoint correctly carries `@Min(1) @Max(200)`. OpenAPI consumers
(generated SDK, Swagger UI) see an unconstrained integer on the files endpoint.

**Filed as**: APISIMP-BUNDLES-FILES-PAGESIZE-UNCLAMPED (XS)

---

## §F4 — Text-filter param naming inconsistency (`?name=` vs `?q=`)

### F4-1: `ContainersV2Rest` — `?name=`

`backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java` line 455

`GET /v2/containers?kind=…&name=…` uses `?name=` as the substring filter.

### F4-2: `DataObjectV2Rest` — `?name=`

`backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java` line 203

`GET /v2/data-objects?name=…` uses `Constants.QP_NAME` ("name") as the substring filter.

The established v2 convention for a generic text filter is `?q=` (set by
`GET /v2/user-groups?q=`). Having both `?name=` and `?q=` across sibling endpoints
forces SDK consumers to check the correct param per endpoint.

**Filed as**: APISIMP-CONTAINERS-NAME-TO-Q (S), APISIMP-DO-NAME-TO-Q (S)

---

## §F5 — Deprecated `PublicationsListRest` missing tombstone

`backend/src/main/java/de/dlr/shepard/v2/publish/resources/PublicationsListRest.java`

`GET /v2/{kind}/{appId}/publications` is already marked `deprecated = true` in the
OpenAPI operation. The canonical surface is `GET /v2/publications` via
`FlatPublicationsRest`. Despite APISIMP-PUBS-ALIAS-HARDCODED-LIMIT (fire-461) adding
pagination, the endpoint still returns live data — old callers never discover the
migration path. Next step is a 410 tombstone with `Location: /v2/publications`.

**Filed as**: APISIMP-PUBLICATIONS-KIND-410 (XS)

---

## §F6 — `AnomalyDetectRequestIO` is 5-tuple-only

`backend/src/main/java/de/dlr/shepard/v2/timeseries/io/AnomalyDetectRequestIO.java`

`POST /v2/anomaly-detection/detect` body accepts a channel selector exclusively as the
5-tuple (`measurement`, `device`, `location`, `symbolicName`, `field`). The live-window
endpoint already supports both the 5-tuple AND `channelAppId` as alternatives (per
APISIMP-TIMESERIES-APPID-MIGRATION). Clients that already hold a channel `appId` must
expand it into 5 fields before calling detect.

Note: APISIMP-ANOMALY-ACTION-PATH (fire-496) is the companion path-routing row. This row
is strictly about the body schema — adding `channelAppId` as an alternative channel
selector independent of where the endpoint eventually lives.

**Filed as**: APISIMP-ANOMALY-5TUPLE-ADD-UUID (M)

---

## §F7 — `UserGroupV2Rest` accepts numeric group IDs without a 400 block

`backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java` lines 319, 326

When `readerGroupIds` / `writerGroupIds` are in a PATCH body, the handler calls
`Long.parseLong(v.toString())` and persists numeric Neo4j IDs without complaint.
`ContainersV2Rest` (lines 1379–1385) already returns 400 with an informative message
directing callers to use `readerGroupAppIds` / `writerGroupAppIds` (UUID v7). The
`UserGroupV2Rest` gap means callers hit silent data corruption (numeric ID is stored,
then later rejected by the permissions graph) instead of a clear error.

**Filed as**: APISIMP-USERGROUP-NUMERIC-PERMS-BLOCK (S)

---

## Summary table

| ID | File | Size |
|---|---|---|
| APISIMP-BUNDLE-TOMBSTONE-DELETE | `FileBundleReferenceRest.java` | XS |
| APISIMP-WIKI-TOMBSTONE-DELETE | `WikiWriterTombstoneRest.java` | XS |
| APISIMP-PAGEDFILES-SPRING-NAMING | `PagedFilesIO.java` | S |
| APISIMP-BUNDLES-FILES-PAGESIZE-UNCLAMPED | `BundleGroupsV2Rest.java:354` | XS |
| APISIMP-CONTAINERS-NAME-TO-Q | `ContainersV2Rest.java:455` | S |
| APISIMP-DO-NAME-TO-Q | `DataObjectV2Rest.java:203` | S |
| APISIMP-PUBLICATIONS-KIND-410 | `PublicationsListRest.java` | XS |
| APISIMP-ANOMALY-5TUPLE-ADD-UUID | `AnomalyDetectRequestIO.java` | M |
| APISIMP-USERGROUP-NUMERIC-PERMS-BLOCK | `UserGroupV2Rest.java:319,326` | S |
