---
stage: deployed
last-stage-change: 2026-05-23
---

# 87 — Timeseries appId Migration (TS-ID)

**Status:** Design · in-flight (substrate correction landed 2026-05-22)
**Audience:** Contributors, frontend developers, script authors
**Depends on:** L2d (appId-first v2 surface, shipped), P-series (SQL timeseries, shipped)
**Blast radius:** ⚠ HIGH — touches every timeseries endpoint, all frontend channel selectors, backend client, existing TimeseriesReferences

---

## CHANGELOG

### 2026-05-22 — Substrate correction: shepardId lives in Timescale, not Neo4j

The original §3 TS-IDa step assumed timeseries channels were modeled as
`:Timeseries` nodes in Neo4j. **That node never existed in this codebase.**
The Timeseries surface (containers, channels, datapoints) is implemented
entirely in PostgreSQL/Timescale (`backend/src/main/resources/db/migration/`
+ `de.dlr.shepard.data.timeseries.*`). The Neo4j layer holds
`:TimeseriesContainer` and `:TimeseriesReference` only — not the channel
metadata itself.

**Corrected substrate:** the existing `timeseries` Postgres table (created
by `V1.0.0__setup_timeseries_tables.sql`) IS the per-channel metadata
table. Its 6-tuple uniqueness invariant
(`container_id, measurement, field, device, location, symbolic_name`)
already encodes channel identity. The migration adds a single
`shepard_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()` column,
backfilled on existing rows. Resolution from `shepardId` to the 5-tuple
is a JDBC lookup against this column — no Neo4j round-trip.

§3 TS-IDa as originally written is **superseded** by Flyway migration
`V1.11.0__add_shepard_id_to_timeseries.sql` + `TsChannelResolver` JDBC
service. The Neo4j-side migration (`V56__timeseries_appid_backfill.cypher`
described in §3) is **not needed** and was never written.

The user-facing naming also flips at this point: while the
Java-internal getter stays `getAppId()` (deep rename deferred to task
#123), the **wire/IO surface** on `/v2/` adopts `shepardId` everywhere —
query params, path params, JSON field names (additive — `appId` stays on
`/v2/` too for one release cycle, with `shepardId` as the canonical alias
that emits the same value).

`/shepard/api/...` (the frozen upstream v5 surface) continues to emit
`appId` byte-for-byte; `shepardId` does NOT appear on the v1 wire. v1
wire fidelity is proven by `V1WireFidelityTest` (pinned-JSON serializer
assertions per IO class).

This brings PR-9 (SHACL placeholder bind) into scope as a single resolver
binding — the SHACL substrate's `mffd:hasTraceChannelPlaceholder` gets
finalised to a `shepard:shepardId` lookup against the `timeseries` row.

---

## 1. The Problem

Every timeseries channel in shepard is currently addressed by a 5-tuple:

```
{ measurement, device, location, symbolicName, field }
```

This is inherited from InfluxDB's line-protocol lineage (measurement = table name, tags = dimensions, field = column). It made sense at the storage layer. At the API and UI layer it is painful:

**For researchers writing scripts:**
```python
# Every channel operation needs all five fields
client.get_metrics(
    collection_id=42, data_object_id=7, timeseries_reference_id=3,
    measurement="nozzle", device="AFP-1", location="layup-head",
    symbolic_name=None, field="temperature"
)
```
If any field is spelled differently in different calls, the reference silently fails.

**For the frontend:**
`ShowTimeseriesReferenceDialog.vue`, `ChannelPreviewChart.vue`, `useFetchTimeseries.ts`, `useFetchTimeseriesAnnotations.ts`, `useFetchChannelPreview.ts` — every one of these passes all 5 parameters on every call. The `getTimeseriesKey()` helper concatenates all five into a string key for deduplication. Five parameters is too many to keep consistent across 6+ call sites.

**For import scripts:**
The MFFD import manifest references channels. A 5-tuple is verbose and fragile — if the AFP team renames the measurement field in a new dataset version, all existing references break.

**Root cause:** The `Timeseries` Neo4j entity has no stable single-field identifier. Its identity IS the 5-tuple.

---

## 2. Target State

Each `Timeseries` entity gets an `appId` (UUID v7), minted on creation, immutable thereafter. Callers can reference a channel by `appId` alone:

```python
# After TS-ID
client.get_metrics(timeseries_app_id="01924b5c-7e26-7000-a000-000000000abc")
```

The 5-tuple remains a **valid lookup key** (for upstream compatibility and for human readability) but is no longer the only key. A `GET /v2/timeseries-containers/{appId}/channels?appId=...` lookup works. The 5-tuple lookup also works.

---

## 3. Migration Shape

### Phase TS-IDa — Mint appIds on all existing Timeseries nodes

Neo4j migration (`V56__timeseries_appid_backfill.cypher`):

```cypher
// TS-IDa: mint a UUID v7 appId on every :Timeseries node that lacks one.
// Idempotent: nodes that already have appId are skipped.
MATCH (t:Timeseries)
WHERE t.appId IS NULL
SET t.appId = randomUUID()
RETURN count(t) AS backfilled;
```

This is the only write. The Timeseries Java entity (`AnnotatableTimeseries` or `Timeseries`) gets the `@Property("appId")` field. `GenericDAO.createOrUpdate` mints the UUID on new nodes automatically (standard HasAppId pattern).

**Risk:** Low. Additive only. Existing queries that don't use appId are unaffected.

### Phase TS-IDb — Expose appId on v2 list and get endpoints

The existing v2 timeseries endpoints return channel data. Add `appId` to the response shape:

```json
{
  "appId": "01924b5c-...",
  "measurement": "nozzle",
  "device": "AFP-1",
  "location": "layup-head",
  "symbolicName": null,
  "field": "temperature"
}
```

Backwards-compatible: new field added, nothing removed.

Update the `backend-client` generated types to include `appId` on `Timeseries` / `TimeseriesEntity`.

### Phase TS-IDc — Accept appId as lookup key on data endpoints

Add `timeseriesAppId` as an alternative query parameter on endpoints that currently require the full 5-tuple. Both forms accepted:

```
GET /v2/timeseries-containers/{appId}/channels/data
  ?timeseriesAppId=01924b5c-...       ← new, preferred
  &measurement=nozzle&field=temp...   ← old, still works
```

Backend resolves appId → 5-tuple before hitting the query layer. The SQL/InfluxDB query stays unchanged.

### Phase TS-IDd — Frontend migration

Replace 5-tuple prop-drilling with single `timeseriesAppId` in:
- `ShowTimeseriesReferenceDialog.vue` — `getTimeseriesKey()` becomes `ts.appId ?? getTimeseriesKey(ts)` during the transition, pure `ts.appId` after
- `useFetchTimeseries.ts`, `useFetchTimeseriesAnnotations.ts`, `useFetchChannelPreview.ts` — add optional `appId` param; prefer it when present
- `TimeseriesMeasurementsTable.vue` — pass `appId` through to row actions

This phase can be done incrementally. Old callers still work.

### Phase TS-IDe — Deprecation window (deferred, after L2e)

Once TS-IDa through TS-IDd are shipped and stable, the 5-tuple query params on v2 endpoints can be marked `@Deprecated` in the OpenAPI spec. Removal is L2e territory — not before.

---

## 4. Blast Radius Map

Files that must change for TS-IDa/b (the safe phases):

| Layer | File | Change |
|-------|------|--------|
| Backend entity | `Timeseries.java` / `AnnotatableTimeseries.java` | Add `@Property("appId")` + `HasAppId` |
| Backend entity | `TimeseriesAnnotation.java` | Add `@Property("appId")` if missing |
| Backend DAO | `TimeseriesDAO` | `findByAppId()` method |
| Backend IO | `TimeseriesIO` / `TimeseriesEntityIO` | Add `appId` field to response |
| Backend migration | `V56__timeseries_appid_backfill.cypher` | New file |
| NeoConnector | `NeoConnector.java` | `HasAppId` already handled by `createOrUpdate` |
| Frontend client | `backend-client/src/models/Timeseries.ts` | Add `appId?: string` |
| Frontend client | `backend-client/src/models/TimeseriesEntity.ts` | Add `appId?: string` |

Files that change for TS-IDc/d (the riskier phases — do separately):

| Layer | File | Change |
|-------|------|--------|
| Backend REST | Timeseries container REST resources | Add `timeseriesAppId` query param |
| Frontend | `ShowTimeseriesReferenceDialog.vue` | Replace 5-tuple with appId |
| Frontend | `useFetchTimeseries.ts` | Add appId param |
| Frontend | `useFetchTimeseriesAnnotations.ts` | Add appId param |
| Frontend | `useFetchChannelPreview.ts` | Add appId param |
| Frontend | `ChannelPreviewChart.vue` | Pass appId through |

---

## 5. What Breaks and When

| Phase | What could break | Mitigation |
|-------|-----------------|------------|
| TS-IDa | Nothing — additive Neo4j field | Idempotent migration; existing queries unchanged |
| TS-IDb | Nothing — additive JSON field | Callers that don't read appId are unaffected |
| TS-IDc | Scripts that use 5-tuple still work; nothing removed | Dual-key lookup at REST boundary |
| TS-IDd | Frontend intermediate state: some components use appId, others use 5-tuple | Use `ts.appId ?? getTimeseriesKey(ts)` as the bridge key during migration |
| TS-IDe | 5-tuple-only clients break | Only after deprecation window; L2e timing |

**Upstream compatibility:** The upstream `/shepard/api/timeseries/...` endpoints are frozen (see API-version policy). TS-ID only touches `/v2/` surface — upstream clients are unaffected throughout.

---

## 6. Import Manifest Connection

The `ImportManifestIO` (IMP1) can now reference timeseries containers by a single `containerAppId` and channels by `timeseriesAppId` — no 5-tuple in the manifest. This lands in TS-IDc: once channels have stable appIds, the importer can generate a manifest that's human-readable and copy-paste safe.

---

## 6b. Alternative Approaches — Prior Art Survey

Before committing to the plan in §3, three alternatives worth understanding:

### Alt-1: Deterministic Hash ID (OpenTSDB TSUID model)

OpenTSDB computes a `TSUID` — a hex string derived from the metric name + sorted tag key-value pairs. The key property: **the ID is computable from the 5-tuple without hitting the database**.

```
tsuid = hex( sha256(sorted(measurement, device, location, symbolicName, field)) )[:16]
```

Formatted as a UUID: `01924b5c-7e26-7000-a000-000000000abc` (deterministic, not random).

**Pros for shepard:**
- Import scripts can reference channels by ID without a prior lookup
- The 5-tuple and the ID are losslessly equivalent — no separate storage
- No Neo4j migration needed for existing nodes; compute on read

**Cons:**
- If any field in the 5-tuple changes (rename), the ID changes — not truly stable
- Two channels with identical 5-tuples in different containers get the same hash; scoping by container appId is required: `hash(containerAppId + 5-tuple)`
- Opaque to humans (hex string with no meaning)

**Verdict:** Best choice if the primary goal is script convenience and cross-instance determinism. Worst choice if channels ever get renamed.

---

### Alt-2: Clean Break — appId replaces 5-tuple (InfluxDB 3.x model)

InfluxDB 3.x moved away from the tag-set-as-identity model entirely. Each "series" gets a stable UUID at creation. The tag set is stored as metadata but is not the address.

For shepard this would mean:
- Phase 1: Mint UUIDs on all existing `Timeseries` nodes (same as TS-IDa)
- Phase 2: All v2 endpoints **require** `timeseriesAppId`; 5-tuple params removed from v2
- Phase 3: v1 endpoints (frozen for upstream compat) keep 5-tuple; v2 is appId-only

**Pros:**
- Clean API — one way to reference a channel
- Channels can be renamed (5-tuple updated as metadata) without breaking references
- Import manifest is tidy: just one field

**Cons:**
- Breaking for all existing `/v2/` callers (scripts, generated clients) even in the short term
- Requires coordinated client regen + frontend update in a single PR
- More migration risk than Alt-A

**Verdict:** The right end-state. The question is whether the migration cost is worth paying now vs. deferring. Given the current number of consumers, the cost is manageable — but needs a deprecation notice and 1-sprint migration window.

---

### Alt-3: Two-Level Addressing — measurement namespace + shortId (Prometheus model)

Prometheus uses `{metric_name, label_set}` as identity. Within a metric (measurement), each unique label combination has an internal integer series ID. Exposed as `{__name__="nozzle_temperature", device="AFP-1", ...}` in PromQL — the label set IS the address, but tooling makes it ergonomic via label matchers rather than positional args.

For shepard this would mean:
- Within a container, channels are addressed as `{containerAppId}/{measurement}/{shortId}`
- `shortId` is a sequential integer per measurement (1, 2, 3…)
- Example: `TS-CONTAINER-ABC/nozzle/3`

**Pros:**
- Human-readable in URLs and logs
- Short and memorable for datasets with few channels per measurement

**Cons:**
- Adds a new concept (shortId) that must be managed and stored
- Cross-measurement references still need the full address
- Doesn't compose well with the import manifest (shortIds assigned by DB, not predictable upfront)

**Verdict:** Interesting for display purposes (showing "nozzle#3" in the UI) but wrong as the canonical API identifier.

---

### Recommendation after surveying alternatives

| | Alt-A (current plan) | Alt-1 (hash) | Alt-2 (clean break) | Alt-3 (shortId) |
|---|---|---|---|---|
| Script convenience | ✓ (appId lookup) | ✓✓ (computable) | ✓✓ (single field) | ~ |
| Rename-safe | ✓✓ | ✗ | ✓✓ | ~ |
| Breaking change | none | none | yes (v2 only) | yes |
| Migration complexity | low | very low | medium | high |
| Best for MFFD import | ✓ | ✓✓ | ✓ | ✗ |

**Adopt Alt-2 as the target, Alt-A as the migration path.** Ship TS-IDa/IDb immediately (zero risk). Use the dual-key period (TS-IDc) as the migration window for consumers. Then in TS-IDe, drop the 5-tuple from v2 and arrive at the clean Alt-2 end-state. This is the same "additive then deprecate then drop" shape as L2d/L2e.

The deterministic hash (Alt-1) is worth adding as an optional helper in the import manifest: the manifest can include pre-computed `timeseriesAppId` values for channels it's creating, derived from `hash(containerAppId + 5-tuple)`. This lets the importer reference a channel before the backend creates it — a useful property for cross-referencing within a manifest.

---

## 7. Recommended Sequence

```
TS-IDa  (migration, low risk)   ← start here; can ship alone
    │
TS-IDb  (IO exposure)            ← ship with or immediately after IDa
    │
TS-IDc  (REST dual-key lookup)   ← ship after IDa/IDb are stable (1 sprint)
    │
TS-IDd  (frontend migration)     ← ship after IDc; incremental component-by-component
    │
TS-IDe  (deprecation)            ← deferred to L2e; not before all consumers migrated
```

TS-IDa and TS-IDb can be done in a single PR with zero breaking changes. That's the entry point.

---

## 8. Test Plan

- **TS-IDa:** After migration, `MATCH (t:Timeseries) WHERE t.appId IS NULL RETURN count(t)` returns 0.
- **TS-IDb:** `GET /v2/timeseries-containers/{appId}/channels` response includes `appId` on every channel.
- **TS-IDc:** `GET /v2/.../channels/data?timeseriesAppId=X` returns the same data as `?measurement=...&field=...` for the same channel.
- **TS-IDd:** `getTimeseriesKey()` is no longer called with undefined appId in any component (grep check).
