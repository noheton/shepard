---
stage: deployed
last-stage-change: 2026-07-14
---

# APISIMP Sweep — 2026-07-14 (fire-604)

**Scope:** Full v2 REST surface + admin endpoints + timeseries plugin.
**Prior sweep:** fire-599 (`apisimp-sweep-2026-07-14-fire599.md`) — F1-F4 merged (PR #2563), F5 merged (PR #2564), F6–F10 blocked (spatial, frozen v1).

---

## What I found

Five new findings across two categories:

| # | ID | Category | Size | File | Blocked? |
|---|-----|----------|------|------|----------|
| 1 | APISIMP-ADMIN-CONFIG-FEATURES-UNSLICED | B (accepted-but-ignored pagination) | XS | `AdminConfigRest.java:96–103` | No |
| 2 | APISIMP-REST-CHANNEL-DATA-NANOS-TO-ISO | D (timestamp Long query params) | S | `ContainersV2Rest.java:733,735` | No (callers need migration window) |
| 3 | APISIMP-CROSS-DO-BULK-START-END-NANOS | D (timestamp Long in request body) | S | `CrossDoBulkDataRequestIO.java:65,70` | No |
| 4 | APISIMP-TS-ANNOTATION-IO-NS-TO-ISO | D (timestamp Long in IO) | XS | `TimeseriesAnnotationIO.java:16,19` | No |
| 5 | APISIMP-ANOMALY-INTERVAL-NS-TO-ISO | D (timestamp Long in IO) | XS | `AnomalyIntervalIO.java:16,19` | No |

Immediately dispatchable: findings 1–5 (none blocked).
Callers need a migration window for findings 2–3 before enforcement.

Surface confirmed clean: `SearchV2Rest`, `ProvenanceRest`, all importer resources,
all snapshot resources, `CollectionV2Rest`, `CollectionTimelineRest`,
`NotificationRest`/`NotificationAdminRest`, `OntologyGitSourceRest`, all admin
stub/single-resource endpoints, `v2/git/resources/`, `v2/profile/resources/` (do not exist).

---

## Finding 1 — APISIMP-ADMIN-CONFIG-FEATURES-UNSLICED

**Category B — accepted-but-ignored pagination params**

`AdminConfigRest.java:92–103`:
```java
public Response listFeatures(
  @QueryParam("page") @DefaultValue("0") @Min(0) int page,
  @QueryParam("pageSize") @DefaultValue("50") @Min(1) @Max(200) int pageSize
) {
  List<ConfigFeatureIO> rows = registry.all().stream().map(...).toList();
  long total = rows.size();
  return Response.ok(new PagedResponseIO<>(rows, total, page, pageSize))   // rows is never sliced
    .header("X-Total-Count", total).build();
}
```

`GET /v2/admin/config` accepts `?page=` and `?pageSize=` and reflects them in the
`PagedResponseIO` envelope, but `rows` is never sliced. A caller requesting
`page=1&pageSize=5` receives the full list (all N features) while the envelope says
`{page:1, pageSize:5, total:N}`. This is a subtler variant than the `(items,
items.size(), 0, items.size())` pattern — the params are wired but semantically
ignored. At current registry size (< 20 features) the materialisation is harmless
but the contract is broken.

Historical note: `APISIMP-ADMIN-CONFIG-LIST-BARE` (fire-369) fixed
`AdminFeaturesRest.list()` (which removed the fake-paged wrapper on *feature
toggles*). A later pass added `page`/`pageSize` to `AdminConfigRest.listFeatures()`
for envelope consistency but forgot to add the corresponding slice.

**Fix:** Add safe in-memory slice before wrapping:
```java
long from = Math.min((long) page * pageSize, total);
List<ConfigFeatureIO> slice = rows.subList((int) from,
    (int) Math.min(from + pageSize, total));
return Response.ok(new PagedResponseIO<>(slice, total, page, pageSize))
    .header("X-Total-Count", total).build();
```

**Size: XS.** One file, no schema change, no migration.

---

## Finding 2 — APISIMP-REST-CHANNEL-DATA-NANOS-TO-ISO

**Category D — timestamp Long query params**

`ContainersV2Rest.java:733,735`:
```java
@Parameter(description = "Window start, nanoseconds since Unix epoch …")
@QueryParam("start") @NotNull @PositiveOrZero Long start,
@Parameter(description = "Window end, nanoseconds since Unix epoch …")
@QueryParam("end")   @NotNull @PositiveOrZero Long end,
```

`GET /v2/containers/{appId}/channels/{channelId}/data` accepts `start`/`end` as
raw nanosecond `Long` query parameters. The MCP tool equivalents were converted
to ISO 8601 in `APISIMP-MCP-TS-NANOS-ISO` (fire-597, PR #2557); the REST endpoint
was not. A frontend caller and an MCP caller now observe different type conventions
for the same underlying operation.

ISO 8601 with 9-digit fractional seconds (`2026-07-14T12:00:00.123456789Z`) preserves
nanosecond precision and is self-describing. Conversion: `Instant.parse(s)` gives
`Instant`; `instant.getEpochSecond() * 1_000_000_000L + instant.getNano()` gives ns.

**Blocker:** `@PositiveOrZero` on `Long` becomes meaningless on `String`; replace with
`@NotBlank`. Frontend callers (currently passing nanosecond Longs) need a migration
window before enforcement. Recommend a 5-fire tombstone accepting both forms.

**Size: S.** One REST method + handler interface + tests.

---

## Finding 3 — APISIMP-CROSS-DO-BULK-START-END-NANOS

**Category D — timestamp Long in request body**

`CrossDoBulkDataRequestIO.java:65,70`:
```java
@Schema(description = "Window start, nanoseconds since epoch.", required = true,
        example = "1700000000000000000")
Long start,

@Schema(description = "Window end, nanoseconds since epoch.", required = true,
        example = "1700003600000000000")
Long end,
```

`POST /v2/data-objects/cross-timeseries-bulk` accepts `start`/`end` in the request
body as nanosecond `Long`s. Same inconsistency as Finding 2: MCP tools use ISO 8601
after APISIMP-MCP-TS-NANOS-ISO but the REST body has not been updated.

**Fix:** Change `Long start` / `Long end` to `String start` / `String end`; parse with
`Instant.parse()` at the resource boundary; convert to nanoseconds for the service call.
Update `@Schema` examples to `"2026-07-14T12:00:00.123456789Z"`.

**Callers need migration window** — same advice as Finding 2.

**Size: S.** One IO record + one resource method + tests.

---

## Finding 4 — APISIMP-TS-ANNOTATION-IO-NS-TO-ISO

**Category D — timestamp Long in IO class**

`TimeseriesAnnotationIO.java:16,19`:
```java
@Schema(description = "Start of the annotated interval in nanoseconds since Unix epoch.",
        required = true)
private Long startNs;

@Schema(description = "End of the annotated interval in nanoseconds since Unix epoch.
        Null for point annotations.")
private Long endNs;
```

`TimeseriesAnnotationIO` is used as both request body (POST) and response (GET list,
POST 201). The nanosecond fields appear in:
- `POST /v2/containers/{appId}/temporal-annotations` (request body)
- `GET /v2/containers/{appId}/temporal-annotations` (response items)
- The `@APIResponse(responseCode="400")` description literally says `` `startNs` is null ``

**Fix:** Change `Long startNs` / `Long endNs` to `String startNs` / `String endNs`;
convert at the service boundary. Update `@APIResponse(responseCode="400")` wording to
match new field convention. The field *names* can stay as `startNs`/`endNs` or become
`start`/`end` depending on whether the ISO representation makes the `Ns` suffix obsolete.

**Size: XS.** One IO class + constructor + DAO binding + tests.

---

## Finding 5 — APISIMP-ANOMALY-INTERVAL-NS-TO-ISO

**Category D — timestamp Long in IO record**

`AnomalyIntervalIO.java:16,19`:
```java
@Schema(description = "Timestamp of the first anomalous point in the run,
        in nanoseconds since Unix epoch.")
long startNs,

@Schema(description = "Timestamp of the last anomalous point in the run,
        in nanoseconds since Unix epoch. Equal to startNs for single-point anomalies.")
long endNs,
```

`AnomalyIntervalIO` is the response record for `POST /v2/anomaly-detection/detect`.
It maps anomaly detection output intervals whose timestamps come from
`TimeseriesDataPoint` (also nanoseconds). Callers can't compare `AnomalyIntervalIO.startNs`
to `TimeseriesAnnotationIO.startNs` (Finding 4) without knowing both are nanoseconds.

**Fix:** Change both `long` fields to `String`; convert at the service boundary (same
pattern as Finding 4). Natural pair: implement Findings 4 + 5 in the same PR.

**Size: XS.** One IO record + constructor + tests.

---

## Opportunities

1. **Batch Findings 4 + 5** in a single XS PR — both are IO class changes with no REST
   endpoint signature change. Anomaly detection already uses `TimeseriesAnnotationIO`
   for the optional `createAnnotations=true` path, so a single conversion PR touches
   the whole cluster naturally. Estimated 30 minutes.
2. **Implement Finding 1 (B-6)** immediately — XS, single-file, no callers affected.
   Estimated 5 minutes.
3. **Findings 2 + 3** (query param and request body nanos) need a frontend impact
   assessment before dispatching. The frontend `useV2ShepardApi` callers passing
   nanosecond values must be updated in the same PR or a coordinated pair.

---

## Gaps & blockers

- **Spatial findings F6–F10 from fire-599** remain blocked on SPATIAL-V6-003 /
  PLUGIN-V2-001 (v2 sibling shelf for spatiotemporal plugin). No change in status.
- **Findings 2–3 migration window:** changing REST query params and request body types
  breaks existing callers who pass nanosecond Longs. A 5-fire tombstone accepting both
  forms (Long coerced to ns, String parsed as ISO) is the recommended approach.

---

## What surprised me

All five nanosecond-Long findings cluster around the timeseries subsystem. The MCP
surface was already converted (APISIMP-MCP-TS-NANOS-ISO, fire-597), but the REST
surface was left behind. A caller who drives timeseries via MCP and via REST now sees
two different type conventions for the same `start`/`end` concept. The fix path is
clear, but the order matters: IO classes (Findings 4 + 5) first (no callers to
migrate), then REST endpoints (Findings 2 + 3) after a frontend migration.
