---
name: Feature toggle runtime migration audit
description: Audit of all deploy-time feature toggles in application.properties; classifies each as DONE / MIGRATE / DEPLOY-ONLY and generates per-toggle migration rows for the FEATURE-TOGGLES-AS-PLUGINS backlog.
type: design
stage: concept
last-stage-change: 2026-06-20
---

# 195 — Feature toggle runtime migration audit

**Parent row:** `FEATURE-TOGGLES-AS-PLUGINS` in `aidocs/16-dispatcher-backlog.md`  
**Scope:** Every `shepard.*` toggle in `backend/src/main/resources/application.properties` that controls
whether a runtime capability is active. `quarkus.*` settings and infrastructure-credential keys are
out of scope (framework internals / cluster topology — legitimately deploy-time-only per CLAUDE.md).

---

## Audit methodology

Three decision categories:

- **DONE** — already runtime-mutable today via `FeatureToggleRegistry` (`GET|PATCH /v2/admin/features`),
  `ConfigRegistry` (`GET|PATCH /v2/admin/config/{feature}`), or `PluginsAdminRest`
  (`PATCH /v2/admin/plugins/{id}`). No further work needed.
- **MIGRATE** — operator has a legitimate need to flip this without a restart;
  migration target is either a new `ConfigDescriptor` or a new `FeatureToggleEntry`.
  Each MIGRATE entry gets a `FTOGGLE-*` backlog row.
- **DEPLOY-ONLY** — meets one of the CLAUDE.md legitimate exceptions:
  cluster identity / topology, pre-startup ordering invariant, or buffer size with no runtime operator need.

---

## Toggle inventory

### Plugin-level toggles (`shepard.plugins.*.enabled`)

All 18 plugin entries are managed by `PluginRegistry` + `PluginsAdminRest`
(`PATCH /v2/admin/plugins/{id}`) and are already runtime-mutable. **Status: DONE.**

| Plugin key | Default |
|---|---|
| `shepard.plugins.unhide.enabled` | false |
| `shepard.plugins.kip.enabled` | false |
| `shepard.plugins.minter-local.enabled` | false |
| `shepard.plugins.minter-datacite.enabled` | false |
| `shepard.plugins.minter-epic.enabled` | false |
| `shepard.plugins.spatiotemporal.enabled` | true |
| `shepard.plugins.hdf5.enabled` | false |
| `shepard.plugins.git.enabled` | false |
| `shepard.plugins.file-s3.enabled` | false |
| `shepard.plugins.aas.enabled` | false |
| `shepard.plugins.video.enabled` | false |
| `shepard.plugins.ai.enabled` | false |
| `shepard.plugins.wiki-writer.enabled` | false |
| `shepard.plugins.importer.enabled` | false |
| `shepard.plugins.analytics-ts.enabled` | false |
| `shepard.plugins.v1-compat.enabled` | true |
| `shepard.plugins.krl-interpreter.enabled` | false |
| `shepard.plugins.fileformat-svdx.enabled` | true |
| `shepard.plugins.fileformat-thermography.enabled` | true |
| `shepard.plugins.fileformat-cad.enabled` | true |
| `shepard.plugins.fileformat-robotics.enabled` | true |

---

### Cross-feature capability toggles

| Toggle key | Default | Status | Migration target | Row |
|---|---|---|---|---|
| `shepard.infrastructure.spatial.enabled` | false | **DONE** | `FeatureToggleRegistry` (`spatial`) via A3b | — |
| `shepard.features.versioning.enabled` | false | **DONE** | `FeatureToggleRegistry` (`versioning`) via A3b | — |
| MFG2 quality-gates toggle | false | **DONE** | `FeatureToggleRegistry` (`quality-gates`) via A3b | — |
| `shepard.jupyter.enabled` | false | **DONE** | `JupyterConfigDescriptor` → `/v2/admin/config/jupyter` | — |
| `shepard.semantic.internal.preseed-ontologies.enabled` | true | **DONE** | `SemanticConfigDescriptor.preseedEnabled` → `/v2/admin/config/semantic` | — |
| `shepard.legacy.v1.enabled` | true | **DONE** | `LegacyV1ConfigDescriptor` → `/v2/admin/config/legacy-v1` | — |
| `shepard.unhide.enabled` | false | **DONE** | `UnhideConfigDescriptor.enabled` → `/v2/admin/config/unhide` | — |
| `shepard.timeseries.sql.enabled` | true | **MIGRATE** | Add `enabled` to `SqlTimeseriesConfig` entity + `SqlTimeseriesConfigDescriptor` patch path | `FTOGGLE-SQL-ENABLE-1` |
| `shepard.timeseries.quality-scoring.enabled` | false | **MIGRATE** | New `QualityScoringConfig` entity + `QualityScoringConfigDescriptor` (`feature="quality-scoring"`) covering `enabled`, `interval`, `batchSize` | `FTOGGLE-QS-1` |
| `shepard.provenance.enabled` | true | **MIGRATE** | New `ProvenanceConfig` entity + `ProvenanceConfigDescriptor` (`feature="provenance"`) covering `enabled`, `captureReads`, `retentionDays` | `FTOGGLE-PROV-1` |
| `shepard.migration.auto-sweep.enabled` | false | **MIGRATE** | Add `enabled` + `source` + `target` to `FileMigrationAutoSweepConfig` (or extend existing `FileMigrationRest`) via `AutoSweepConfigDescriptor` | `FTOGGLE-AUTOSWEEP-1` |
| `shepard.hdf.enabled` | false | **DEPLOY-ONLY** | HsdsClient bean wired at startup; would require conditional CDI + restart to re-seed HSDS domain. The plugin `shepard.plugins.hdf5.enabled` runtime toggle is sufficient for operators. | — |
| `shepard.migration.split-singletons.enabled` | false | **DEPLOY-ONLY** | One-time startup migration; pre-startup ordering invariant. | — |
| `shepard.permissions.cache.warm.enabled` | false | **DEPLOY-ONLY** | Cache pre-warm at startup; restart to change. Low operational value in runtime flip. | — |
| `shepard.autoconvert-int` | false | **DEPLOY-ONLY** | Edge-case workaround; no operator need to flip at runtime. | — |

---

### Tunable parameters (not toggles but operator-settable)

These are not on/off switches but operational parameters. They are either already in a
`ConfigDescriptor` (covering the runtime-mutable subset) or legitimately restart-required.

| Key(s) | Status | Notes |
|---|---|---|
| `shepard.timeseries.sql.max-rows`, `.max-duration` | **DONE** | `SqlTimeseriesConfigDescriptor` covers both |
| `shepard.timeseries.quality-scoring.interval`, `.batch-size` | **MIGRATE** | Included in `FTOGGLE-QS-1` scope |
| `shepard.provenance.capture-reads`, `.retention-days` | **MIGRATE** | Included in `FTOGGLE-PROV-1` scope |
| `shepard.provenance.enabled` | **MIGRATE** | Included in `FTOGGLE-PROV-1` scope |
| `shepard.migration.auto-sweep.interval` | **MIGRATE** | Included in `FTOGGLE-AUTOSWEEP-1` scope |
| `shepard.hdf.hsds.endpoint`, `.username`, `.password`, `.timeout` | **DEPLOY-ONLY** | Infrastructure credentials, require restart + HSDS domain |
| `shepard.permissions.cache.ttl`, `.max-size` | **DEPLOY-ONLY** | Caffeine cache config wired at startup |
| `shepard.permissions.default-owner` | **DEPLOY-ONLY** | One-time bootstrap value |
| `shepard.timeseries.ingest.ndjson.*` | **DEPLOY-ONLY** | Buffer sizes; no runtime operator need |
| `shepard.timeseries.compression-backfill.interval` | **DEPLOY-ONLY** | Scheduling; restart to change cadence |
| `shepard.instance.id` | **DEPLOY-ONLY** | Cluster identity; changing at runtime splits PID namespace |

---

## Migration row specifications

### FTOGGLE-SQL-ENABLE-1 — runtime `enabled` on sql-timeseries

**Goal:** Operators should be able to enable or disable the SQL timeseries endpoint
(`POST /v2/sql/timeseries`) at runtime without a restart.

**Scope:**
1. Add `Boolean enabled` (nullable, default `true`) to `SqlTimeseriesConfig` Neo4j entity.
   Seed from `${shepard.timeseries.sql.enabled}` on startup.
2. In `SqlTimeseriesConfigDescriptor.applyMergePatch()`: support `"enabled"` field.
3. In `SqlTimeseriesConfigDescriptor.currentShape()`: surface `enabled` in `SqlTimeseriesConfigIO`.
4. In the SQL endpoint (`SqlTimeseriesRest`): change from `@ConfigProperty` `enabled` check to
   `sqlTimeseriesConfigService.effectiveEnabled()`.
5. Test: `SqlTimeseriesConfigDescriptorTest` patch enabled/disabled + end-to-end gate test.

**Size:** S. No new files beyond the entity field addition.

---

### FTOGGLE-QS-1 — runtime quality-scoring config

**Goal:** Operators should be able to enable or disable the timeseries quality-scoring background job
at runtime, and tune its interval and batch-size.

**Scope:**
1. New `QualityScoringConfig` Neo4j entity (HasAppId, single-instance):
   - `enabled` (Boolean, nullable, default `false`)
   - `intervalIso` (String, nullable, default `"PT6H"`)
   - `batchSize` (Integer, nullable, default `100`)
2. `QualityScoringConfigDAO` (GenericDAO wrapper).
3. `QualityScoringConfigService` (seed from props on StartupEvent; `effectiveEnabled()` / `effectiveInterval()` / `effectiveBatchSize()`).
4. `QualityScoringConfigDescriptor` (`@ApplicationScoped`, `feature="quality-scoring"`);
   validates `intervalIso` with `Duration.parse()`.
5. `QualityScoringConfigIO` record.
6. Update `TimeseriesQualityScoringJob` to read from `QualityScoringConfigService` instead of `@ConfigProperty`.
7. Tests: `QualityScoringConfigDescriptorTest` (5 cases).

**Size:** S.

---

### FTOGGLE-PROV-1 — runtime provenance config

**Goal:** Operators should be able to disable provenance capture and tune read-capture and
retention window at runtime (e.g. temporarily disable during bulk ingest, tune retention).

**Scope:**
1. New `ProvenanceConfig` Neo4j entity (HasAppId, single-instance):
   - `enabled` (Boolean, nullable, default `true`)
   - `captureReads` (Boolean, nullable, default `false`)
   - `retentionDays` (Integer, nullable, default `730`)
2. `ProvenanceConfigDAO` + `ProvenanceConfigService` (seed from props on StartupEvent).
3. `ProvenanceConfigDescriptor` (`@ApplicationScoped`, `feature="provenance"`);
   validates `retentionDays > 0 || == -1`.
4. `ProvenanceConfigIO` record.
5. `ProvenanceCaptureFilter`: replace `@ConfigProperty` checks with `provenanceConfigService.effectiveEnabled()` / `effectiveCaptureReads()`.
6. `ProvenanceRetentionJob`: replace `@ConfigProperty retentionDays` with `provenanceConfigService.effectiveRetentionDays()`.
7. Tests: `ProvenanceConfigDescriptorTest` (6 cases).

**Size:** S. No behavior change — semantics are identical, just the source of truth moves from deploy-time prop to runtime-mutable entity.

**Note:** Disabling provenance at runtime is always safe — the primary operation (data mutation) succeeds regardless. The filter is already fire-and-forget.

---

### FTOGGLE-AUTOSWEEP-1 — runtime auto-sweep config

**Goal:** Operators should be able to enable the storage migration auto-sweep and set its source/target
at runtime without a restart (e.g. to schedule a drain from one storage adapter to another during
a maintenance window).

**Scope:**
1. New `FileMigrationAutoSweepConfig` Neo4j entity (HasAppId, single-instance):
   - `enabled` (Boolean, nullable, default `false`)
   - `sourceAdapter` (String, nullable)
   - `targetAdapter` (String, nullable)
   - `intervalIso` (String, nullable, default `"PT5M"`)
2. `FileMigrationAutoSweepConfigService` (seed from props on StartupEvent).
3. `AutoSweepConfigDescriptor` (`@ApplicationScoped`, `feature="auto-sweep"`).
4. `FileMigrationAutoSweepJob`: replace `@ConfigProperty` checks with the config service.
5. Tests: `AutoSweepConfigDescriptorTest` (5 cases).

**Size:** S.

**Note:** `FileMigrationRest` already provides manual trigger endpoints; this makes the
continuous-sweep mode admin-configurable at runtime rather than restart-requiring.

---

## Summary

| Category | Count | Action |
|---|---|---|
| Plugin-level (DONE via PluginsAdminRest) | 21 | None |
| Cross-feature (DONE via ConfigRegistry/FeatureToggleRegistry) | 7 | None |
| MIGRATE — ConfigDescriptor additions | 4 → 4 new rows | See FTOGGLE-* rows above |
| DEPLOY-ONLY (legitimate exceptions) | 11 | None |

**Follow-on:** FEATURE-TOGGLES-AS-PLUGINS-2 will implement FTOGGLE-SQL-ENABLE-1 (smallest,
least risky — adds one field to an existing descriptor); subsequent fires pick the remaining three.
