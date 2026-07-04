---
stage: deployed
last-stage-change: 2026-06-26
audience: [contributor]
---

# APISIMP Sweep — 2026-06-26 (fire-238)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.
Verification fire: confirms fire-231 findings resolved and scans for new gaps.

## What I found

### Numeric ID hygiene — CLEAN

Zero new numeric-id leaks. Previously-tracked items confirmed in expected state:
- `TimeseriesChannelV2IO.id` and `containerId` — both `@Schema(deprecated=true)`.
  Already tracked as APISIMP-TSCHANNEL-INT-ID-DEPRECATE (done) and
  APISIMP-TSCHANNEL-CONTAINER-ID (slice 1 done, slice 2 gated on TS-IDb/c).
- `PermissionAuditEntryIO.neo4jNodeId` — intentional triage handle for
  pre-L2-migration orphan rows with no `appId`. Tracked as
  APISIMP-PERMISSION-AUDIT-NEO4J-ID (blocked on L2 clean).
- `ContainersV2Rest` Long params — `@QueryParam("start")/@QueryParam("end") Long`
  are epoch-millisecond timestamps. Confirmed NOT Neo4j IDs. Legitimate.
- `ProvenanceRest` Long params — `@QueryParam("since")/@QueryParam("until") Long`
  are epoch-millisecond timestamps. Legitimate.
No new leaks found.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path` annotation.
All occurrences in v2 files are in comments only.

### Bespoke admin `*ConfigRest` outside generic registry — CLEAN

`AdminConfigRest` at `/v2/admin/config` is the only config registry resource.
All plugin-specific admin resources follow the A3b/N1c2/UH1a shape:
- `AdminFeaturesRest` — feature toggle registry (`/v2/admin/features`)
- `SemanticAdminRest` — semantic ontology admin (`/v2/admin/semantic`)
- `UnhideAdminRest` — Unhide harvest key management (`/v2/admin/unhide`)
- `EpicAdminRest`, `DataciteAdminRest` — per-minter credentials
- `HdfAdminRest`, `AasAdminRest` — per-plugin lifecycle
None of these are config registries — they are domain-specific admin surfaces,
which is correct. No new bespoke config resources found.

### Pagination style — CLEAN (fire-231 gaps confirmed resolved)

**Gap 1 (APISIMP-PAGINATION-PARAM-STYLE) — VERIFIED SHIPPED**
`ContainersV2Rest` confirmed using `@DefaultValue("0") @PositiveOrZero int page`
and `@DefaultValue("50") @Min(1) @Max(200) int pageSize` — PR #2101 landed.
`CollectionLabJournalEntriesRest` confirmed intentional backward-compat bulk mode
(no page param, full-collection fetch) — doc-only finding, no code change needed.

**Intentional deviations not filed as gaps:**

- `FileBundleReferenceRest.listGroupFiles()` uses nullable `Integer page/pageSize`
  with explicit server-side clamping. Comment states: "Out-of-range hints are
  clamped, never rejected — surfacing a 400 here would be hostile to a UI that
  may just have a stale slider value while the bundle's file count is fluctuating."
  Applying `@PositiveOrZero` here would break this intent (it rejects negative values
  as 400 instead of clamping). The deviation is intentional and documented.

- `ProvenanceRest` list endpoints use time-cursor pagination (`since`/`until` epoch ms),
  NOT offset pagination. Comment: "Offset pagination produces inconsistent results as
  new Activities land concurrently (append-only event stream — a new row shifts all
  subsequent offsets)." `pageSize` is nullable Integer with server-side cap at 1000.
  Multi-format endpoint (JSON / PROV-JSON / JSON-LD) that cannot use PagedResponseIO.
  Intentional design decision, not a gap.

### Pagination envelope — CLEAN (fire-231 gaps confirmed resolved)

**Gap 2 (APISIMP-PAGINATION-ENVELOPE) — VERIFIED SHIPPED**
All APISIMP-PAGINATION-ENVELOPE series (fires 232–236) confirmed merged:
- `ContainersV2Rest`, `NotificationRest`, `ProjectsRest`, `CollectionWatchesRest`,
  `CollectionWatchersRest` — all use `PagedResponseIO`. ✓
- `CollectionV2Rest`, annotation list endpoints — all use `PagedResponseIO`. ✓
- `SnapshotListRest` — already used `PagedResponseIO` (was the reference). ✓

**Intentional plain-array responses not filed as gaps:**

- `ProvenanceRest` — time-cursor paginated, multi-format, plain array intentional.
- `DataObjectV2Rest.getPredecessors()` — returns a bounded relationship list
  (graph edges, typically < 20 items). Plain array appropriate; paginating
  relationship edges would be over-engineering.

### Per-kind endpoints — CLEAN

No new per-kind endpoints that should be unified found. Previously confirmed
intentional bifurcations (FileBundleReferenceRest, SqlTimeseriesRest,
FileReferenceV2Rest 410) remain as designed.

### Redundant endpoints — NONE

No pairs of endpoints doing the same thing at different paths.

### Bloated response bodies — NONE

No new internal OGM implementation details in JSON shapes. All tracked items
are already `@Schema(deprecated=true)`.

## Opportunities

None. All tracked items are either shipped, intentional deviations, or blocked
on upstream migration work (L2, TS-IDb/c). Surface is extremely clean.

## Real-world impact

After fires 231–237, the v2 REST surface has achieved the target state:
- Uniform pagination parameters (`@DefaultValue`/`@PositiveOrZero`) on all
  offset-paginated list endpoints. Deviations are intentional and documented.
- Uniform `PagedResponseIO` response envelope on all offset-paginated lists.
  Deviations (provenance, predecessors) are intentional and documented.
- Zero numeric-id leaks in live path/query params.
- Zero forbidden `SHEPARD_API` constant usage in v2 `@Path` annotations.
- Zero bespoke admin config resources outside the generic registry.

## Gaps & blockers

None. No actionable APISIMP findings this fire.

## What surprised me

The surface is cleaner than fire-231's baseline in every dimension.
The pagination work shipped in fires 232–236 was comprehensive — even
endpoints added since the prior sweep landed with the correct envelope.
The intentional deviations (FileBundleReferenceRest clamping,
ProvenanceRest time-cursor, DataObjectV2Rest predecessors plain array)
all have inline documentation explaining the design choice.
