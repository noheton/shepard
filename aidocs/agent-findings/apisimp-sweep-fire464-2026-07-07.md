---
stage: deployed
last-stage-change: 2026-07-07
---

# APISIMP Sweep — fire-464 (2026-07-07)

Scanned the full `/v2/` REST surface + plugin REST files for residual
API-simplification opportunities. fire-464 sweep follows fire-463
(1 finding: APISIMP-BUNDLE-GROUP-FILES-INTEGER-PARAMS, ✅ merged same fire).

## What I found

9 new genuine findings surfaced; 2 stale findings discarded
(C: CollectionV2IO already suppresses numeric IDs via `@JsonIgnoreProperties`;
K/L: intentional bare arrays per APISIMP-NOTEBOOK-LIST-FAKE-PAGED /
APISIMP-GIT-CRED-ADMIN-FAKE-PAGED, fire-368).

## Findings

### §B — `CrossDoBulkDataRest`: bare List<CrossDoSeriesIO> response (`APISIMP-CROSS-DO-BULK-NO-ENVELOPE`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkDataRest.java:177`  
**Path:** `POST /v2/data-objects/cross-timeseries-bulk`  
**Size:** S

`return Response.ok(out).build()` where `out` is `List<CrossDoSeriesIO>` — no
`PagedResponseIO` wrapper, no `total` count. Input is bounded to 100 DOs
(`MAX_DATA_OBJECTS`) but callers cannot tell if they received all results.

**Fix:** wrap in `PagedResponseIO<CrossDoSeriesIO>` with `total = out.size(),
page = 0, pageSize = out.size()`; update `@APIResponse` schema annotation.

---

### §D — `SqlTimeseriesRest`: `container_id_in` numeric Long fallback on v2 wire (`APISIMP-SQL-TIMESERIES-NUMERIC-CONTAINER-ID-FALLBACK`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/sql/resources/SqlTimeseriesRest.java:195–208`  
**Path:** `POST /v2/sql/timeseries`  
**Size:** S

Request body accepts `container_id_in: List<Long>` as a deprecated fallback
alongside the canonical `container_app_id_in: List<String>` (UUID v7). Exposes
substrate-internal numeric Neo4j node IDs on the v2 wire — the invariant violation
the `CollectionV2IO` sidecar pattern exists to prevent.

**Fix:** remove `container_id_in` from the request IO; return 400 with a clear
migration message pointing to `container_app_id_in` for any caller still using it.

---

### §E — `ContainersV2Rest.listChannels`: bare array despite page/pageSize params (`APISIMP-CONTAINERS-CHANNELS-LIST-BARE-ARRAY`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:638–659`  
**SPI:** `backend/src/main/java/de/dlr/shepard/v2/containers/spi/ContainerKindHandler.java:286`  
**Path:** `GET /v2/containers/{appId}/channels`  
**Size:** S

The REST layer correctly uses `@PositiveOrZero int page` + `@Min(1) @Max(500) int
pageSize` (Bean Validation already fixed). But `ContainerKindHandler.listChannels`
returns `Optional<List<TimeseriesChannelV2IO>>` — bare list. The REST layer passes
through as `Response.ok(result.get()).build()` with `@APIResponse(schema =
SchemaType.ARRAY)`. Callers cannot distinguish "got exactly pageSize results, more
follow" from "all results fit in one response."

**Fix:** change SPI return to `Optional<PagedResponseIO<TimeseriesChannelV2IO>>`;
update `TimeseriesContainerKindHandler` to push SKIP/LIMIT to timeseries DAO;
wrap in `PagedResponseIO`; update `@APIResponse` annotation.

---

### §F — `ContainersV2Rest.getBulkChannelData`: bare array response (`APISIMP-CONTAINERS-BULK-CHANNEL-BARE-ARRAY`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:757–795`  
**Path:** `POST /v2/containers/{appId}/channels/data/bulk`  
**Size:** XS

Returns bare `List<TimeseriesWithDataPoints>` (SchemaType.ARRAY in `@APIResponse`).
Operation is bounded (max 200 channel IDs per the description), but response is
inconsistent with the `PagedResponseIO` envelope used everywhere else.

**Fix:** wrap in `PagedResponseIO<TimeseriesWithDataPoints>` with `total = items.size()`.

---

### §G — `ProvenanceRest`: boxed `Integer limit` × 6 overloads (`APISIMP-PROVENANCE-LIMIT-BOXED`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:129,192,239,291,332,368`  
**Size:** XS

Six endpoint overloads (entity / collection / activity / admin variants) each declare
`@QueryParam("limit") Integer limit` — boxed — and manually default:
`int eff = limit == null ? 100 : limit;`. The `@Parameter` description already
documents the range (1–1000, default 100) but Bean Validation is not enforced;
callers can pass `?limit=-1` without receiving a 400.

**Fix:** change to `@DefaultValue("100") @Min(1) @Max(1000) int limit`; remove the
null-check line in each overload.

---

### §H — `ContainersV2Rest.getThumbnail`: boxed `Integer size` without validation (`APISIMP-CONTAINERS-THUMBNAIL-SIZE-BOXED`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1463`  
**Path:** `GET /v2/containers/{appId}/thumbnail`  
**Size:** XS

`@QueryParam("size") Integer size` — boxed, no `@DefaultValue`, no `@Min`/`@Max`.
The `@Parameter` doc was fixed in APISIMP-CONTAINERS-THUMBNAIL-SIZE-PARAM (fire-141)
but Bean Validation was not wired: callers can pass `?size=-1` without a 400.

**Fix:** `@DefaultValue("200") @Min(64) @Max(2048) int size`; remove any manual null
check in the method body.

---

### §I — `UnhideFeedRest`: boxed `Integer page`/`pageSize` (`APISIMP-UNHIDE-FEED-BOXED-PARAMS`)

**File:** `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/resources/UnhideFeedRest.java:132–134`  
**Path:** `GET /v2/admin/unhide/feed`  
**Size:** XS

`@QueryParam("page") Integer page` and `@QueryParam("pageSize") Integer pageSize` —
both boxed, no `@DefaultValue`, no Bean Validation. Pattern inconsistency with
every other paginated v2 admin endpoint.

**Fix:** `@DefaultValue("0") @PositiveOrZero int page` + `@DefaultValue("50")
@Min(1) @Max(500) int pageSize`; remove manual null-defaults.

---

### §J — `SemanticAdminRest.listOntologies`: bare array (`APISIMP-SEMANTIC-ONTOLOGIES-BARE-ARRAY`)

**File:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java:254–260`  
**Path:** `GET /v2/admin/semantic/ontologies`  
**Size:** XS

Returns bare `List<OntologyBundleIO>` (SchemaType.ARRAY). List is bounded by
the number of seeded + uploaded bundles (typically < 20), but response shape
is inconsistent with every other v2 admin list that uses `PagedResponseIO`.

**Fix:** wrap in `PagedResponseIO<OntologyBundleIO>` with `total = items.size()`;
update `@APIResponse` schema.

---

### §M — `LegacyV1StatsAdminRest`: boxed `Integer topN` with manual clamping (`APISIMP-V1STATS-TOPN-BOXED`)

**File:** `plugins/v1-compat/src/main/java/de/dlr/shepard/plugins/v1compat/resources/LegacyV1StatsAdminRest.java:70–71`  
**Path:** `GET /v2/admin/v1compat/stats`  
**Size:** XS

`@QueryParam("topN") Integer topN` — boxed — followed by:
`int effective = topN == null ? DEFAULT_TOP_N : Math.min(Math.max(1, topN), MAX_TOP_N);`
Classic boxed-Integer + manual clamp pattern the APISIMP programme has eliminated
across 200+ call sites. Bean Validation can replace both lines.

**Fix:** `@DefaultValue("50") @Min(1) @Max(1000) int topN`; remove the clamp line;
add `@Min`/`@Max` imports.

---

## Stale Findings (not filed)

- **C (APISIMP-COLLECTION-V2-IO-NUMERIC-IDS):** `CollectionV2IO` at
  `v2/collection/io/CollectionV2IO.java:22` already carries
  `@JsonIgnoreProperties({"id", "dataObjectIds", "incomingIds", "defaultFileContainerId"})`.
  Numeric IDs are suppressed on the v2 wire. Finding was stale.

- **K (APISIMP-NOTEBOOK-LIST-BARE-ARRAY):** Intentional — bare array introduced by
  APISIMP-NOTEBOOK-LIST-FAKE-PAGED (fire-368). Not re-wrapping.

- **L (APISIMP-ADMIN-USER-GIT-CREDS-BARE-ARRAY):** Intentional — bare array introduced
  by APISIMP-GIT-CRED-ADMIN-FAKE-PAGED (fire-368). Not re-wrapping.

## What surprised me

`ContainersV2Rest.listChannels` has had correct Bean Validation on its `int` params
for some time, but the SPI interface itself (`ContainerKindHandler.listChannels`)
returns a raw `List` — so the pagination params are accepted, silently passed down,
and ignored by any handler that doesn't implement internal SKIP/LIMIT. The bug is
at the interface boundary, not the REST layer. This is the most significant structural
finding in the sweep.

The `container_id_in` fallback in `SqlTimeseriesRest` is the last known exposure of
numeric Neo4j IDs on the v2 wire outside the frozen v1 `/shepard/api/` surface.
Removing it closes the last known substrate-ID leak in the fork's own endpoints.

## Summary

| ID | Size | Status |
|----|------|--------|
| APISIMP-CROSS-DO-BULK-NO-ENVELOPE | S | ⏳ queued |
| APISIMP-SQL-TIMESERIES-NUMERIC-CONTAINER-ID-FALLBACK | S | ⏳ queued |
| APISIMP-CONTAINERS-CHANNELS-LIST-BARE-ARRAY | S | ⏳ queued |
| APISIMP-CONTAINERS-BULK-CHANNEL-BARE-ARRAY | XS | ⏳ queued |
| APISIMP-PROVENANCE-LIMIT-BOXED | XS | ⏳ queued |
| APISIMP-CONTAINERS-THUMBNAIL-SIZE-BOXED | XS | ⏳ queued |
| APISIMP-UNHIDE-FEED-BOXED-PARAMS | XS | ⏳ queued |
| APISIMP-SEMANTIC-ONTOLOGIES-BARE-ARRAY | XS | ⏳ queued |
| APISIMP-V1STATS-TOPN-BOXED | XS | ⏳ queued |
