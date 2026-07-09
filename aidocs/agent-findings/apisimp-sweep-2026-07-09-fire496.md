---
stage: deployed
last-stage-change: 2026-07-09
---

# APISIMP Sweep — fire-496 (2026-07-09)

Scan of `backend/src/main/java/de/dlr/shepard/v2/` and plugin REST classes
across 7 pattern categories. Categories 2, 5, and 7 clean; 3 already-tracked
blocked rows confirmed; 6 genuinely new findings filed.

## Category 1 — Per-kind endpoints not yet unified under `?kind=`

### F1.1 — AnomalyDetectionRest hangs a timeseries-specific action under generic /v2/references (MAJOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/AnomalyDetectionRest.java:50`
- **Path:** `POST /v2/references/{appId}/detect-anomalies`
- **Problem:** Sub-path encodes kind; resolves only `TimeseriesReference`, 404s for all other kinds. Per the Tier-1/Tier-2 convergence (191 §2), kind-specific operations must go through an SPI or kind-discriminated action param.
- **Fix:** `POST /v2/references/{appId}/actions?action=detect-anomalies` dispatched via `AnomalyDetectionPlugin` SPI. Row: **APISIMP-ANOMALY-ACTION-PATH** (M).

### F1.2 — CrossDoBulkDataRest encodes `timeseries` in path segment (MAJOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkDataRest.java:62`
- **Path:** `POST /v2/data-objects/cross-timeseries-bulk`
- **Problem:** Path segment `cross-timeseries-bulk` is timeseries-specific; resolver is timeseries-only.
- **Fix:** `POST /v2/data-objects/cross-bulk?kind=timeseries`; dispatch via `CrossDoBulkKindPlugin` SPI. Row: **APISIMP-CROSS-BULK-KIND-PATH** (M).

### F1.3 — SqlTimeseriesRest dedicated namespace for timeseries SQL (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/sql/resources/SqlTimeseriesRest.java:74`
- **Path:** `POST /v2/sql/timeseries`
- **Problem:** Entire `de.dlr.shepard.v2.sql` sub-package exists for this one timeseries-specific endpoint.
- **Fix:** Deferred — re-root to `/v2/containers/query?kind=timeseries` once the container kind-discriminator surface is complete. Row: **APISIMP-SQL-TIMESERIES-PATH** (M, deferred).

## Category 2 — Bespoke admin *ConfigRest classes

**Clean.** Only `AdminConfigRest.java` exists under v2; it IS the generic registry at `GET|PATCH /v2/admin/config/{feature}`. Note: `JupyterConfigPublicRest.java` at `/v2/jupyter/config` is an intentional auth-tier variant (authenticated vs instance-admin), not a registry bypass.

## Category 3 — Numeric internal IDs leaking into @PathParam / @QueryParam / response bodies

**Two already-tracked blocked rows confirmed:**
- `TimeseriesChannelV2IO.java:38,48` — `int id` + `long containerId` (deprecated, tracked as `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` + `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE`, blocked on TS-IDb/c Postgres migration).
- `PermissionAuditEntryIO.java:28` — `Long neo4jNodeId` (deprecated, tracked as `APISIMP-PERMISSION-AUDIT-NEO4J-ID`, blocked on L2a/L2b migration).

No new numeric-id leaks found.

## Category 4 — FQN annotations

### F4.1 — PluginsAdminRest.java:145 uses @jakarta.ws.rs.Path FQN (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/admin/plugins/PluginsAdminRest.java:145`
- **Fix:** Replace `@jakarta.ws.rs.Path("/{id}")` with `@Path("/{id}")` using existing import.

### F4.2 — ContainersV2Rest.java:223 uses @jakarta.ws.rs.HeaderParam FQN (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:223`
- **Fix:** Replace `@jakarta.ws.rs.HeaderParam("Range")` with `@HeaderParam("Range")` using existing import.

### F4.3 — ReferencesV2Rest.java:481,483 uses @jakarta.ws.rs.DefaultValue FQN ×2 (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java:481,483`
- **Fix:** Add `import jakarta.ws.rs.DefaultValue;`; replace both FQN forms with short name.

Batch these three into one XS PR. Row: **APISIMP-FQN-IMPORT-BATCH-3** (XS).

## Category 5 — Missing @Schema on IO classes

**Clean.** `find backend/src/main/java/de/dlr/shepard/v2 -name "*IO.java" | xargs grep -L "@Schema" | grep -Ev "Filter|Customizer|Query"` returns zero.

## Category 6 — Inconsistent pagination param names

### F6.1 — ProvenanceRest cursor endpoints use `?limit=` instead of `?pageSize=` (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:134,202,248,301,347,382`
- **Note:** Cursor pagination is intentional; the naming `?limit=` vs `?pageSize=` is the issue.
- Row: **APISIMP-PROV-CURSOR-PAGENAME** (S).

### F6.2 — ImportDiagnosticsV2Rest uses `?limit=` for result-cap (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/importer/resources/ImportDiagnosticsV2Rest.java:135`
- Row: **APISIMP-RESULT-CAP-NAME-UNIFY** (S, batched with F6.3 + F6.4).

### F6.3 — CollectionDQRRest mixes `?pageSize=` and `?limit=` in same class (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:206`
- Row: **APISIMP-RESULT-CAP-NAME-UNIFY** (S, same batch).

### F6.4 — SnapshotDiffRest uses `?maxItems=` (MINOR)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotDiffRest.java:133`
- Note: `maxItems` is arguably the clearest name for result-caps. Recommendation: adopt `maxItems` as the canonical result-cap param and rename `?limit=` usages in F6.2/F6.3 to match. Row: **APISIMP-RESULT-CAP-NAME-UNIFY** (S, same batch).

## Category 7 — @Path(Constants.SHEPARD_API + ...) in v2 package

**Clean.** `grep -r "SHEPARD_API" backend/src/main/java/de/dlr/shepard/v2/` returns zero results. The frozen v1 prefix is correctly confined to non-v2 packages.

## Filed rows

| Row | Size | Status |
|-----|------|--------|
| APISIMP-FQN-IMPORT-BATCH-3 | XS | queued — next dispatchable after #2428 merges |
| APISIMP-PROV-CURSOR-PAGENAME | S | queued |
| APISIMP-RESULT-CAP-NAME-UNIFY | S | queued |
| APISIMP-ANOMALY-ACTION-PATH | M | queued (architectural; needs design) |
| APISIMP-CROSS-BULK-KIND-PATH | M | queued (architectural; needs design) |
| APISIMP-SQL-TIMESERIES-PATH | M | ⛔ deferred — blocked on container kind surface |
