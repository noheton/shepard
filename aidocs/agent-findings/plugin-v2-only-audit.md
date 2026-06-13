---
stage: feature-defined
last-stage-change: 2026-06-13
---

# Plugin backends build on /v2/ — audit

## STRICT v2 conformance re-sweep — 2026-06-13

Post-convergence enforcement pass (svdx/thermography/hdf/scenegraph/krl dissolutions
landed; generic `/v2/references?kind=`, `/v2/containers?kind=`, `/v2/admin/config/{feature}`,
`/v2/mappings/{appId}/materialize` seams live). Re-grepped every `plugins/*/src/main/java`
for: v1 mints (`@Path(Constants.SHEPARD_API…)` / `/shepard/api`), per-plugin `/v2/<word>`
namespaces, numeric `Long` `@PathParam`/`@QueryParam`, IO classes exposing numeric `id`,
`@Tag` internal codes, and missing explicit `operationId`.

### Per-plugin verdict (26 plugin modules)

| Plugin | v2-conformance verdict | Detail |
|---|---|---|
| `aas` | **fixed-here** | `/v2/aas/*` Tier-3 allowlisted (191, IDTA spec-shaped); `/v2/admin/aas*` are non-config admin ops (registrations/sync/idta-import) — credential/admin sisters, expected bespoke. Added 4 missing `operationId`s (`listAasShells`, `getAasShell`, `listAasShellSubmodels`, `importAasIdtaTemplates`, `describeAasServer`, `listAasRegistrations`, `triggerAasRegistrySync`). `AasShellIO.id` is a String IRI — not a numeric leak. |
| `ai` | **fixed-here** | `/v2/admin/ai/capabilities` is a slot-config admin surface (kept bespoke — capability-keyed, not a single `{feature}` config). Added 3 `operationId`s. |
| `hdf5` | **fixed-here** | `/v2/admin/hdf/rebuild-acls` is a non-config admin op (ACL rebuild) — bespoke OK. Config + `/file` already folded (A7-HDF-UNIFY). Added `rebuildHdfAcls` operationId. |
| `kip` | **fixed-here** | `/v2/.well-known/kip` discovery resolver — standard well-known shape, appId-keyed PID resolution. Added `resolveKipRecord` operationId. |
| `unhide` | **fixed-here** | `/v2/unhide/feed.jsonld` verdict already settled (A7-UNHIDE-NS-REVIEW: NOT Tier-3, kept — 503-when-disabled harvester UX). `/v2/admin/unhide/harvest-key/*` are credential sisters (config folded in A7 slice 1). Added 4 `operationId`s. |
| `video` | **fixed-here** | `/v2/data-objects/{appId}/video-stream-references` already partially-dissolved — only the two domain-specific sub-ops (upload, raw download) remain; base CRUD is on generic `/v2/references?kind=`. appId-keyed. Added 2 `operationId`s. |
| `git` | **fixed-here** | `/v2/data-objects/{appId}/git-references` likewise already partially-dissolved — only `preview` + `check-update` domain sub-ops remain. appId-keyed. Added 2 `operationId`s. |
| `wiki-writer` | **fixed-here** | `/v2/collections/{appId}/data-objects/{appId}/wiki-write` — appId-keyed action sub-resource. Added `writeWikiJournalEntry` operationId. |
| `spatiotemporal` (frozen v1) | **allowlisted-exception** | `SpatialDataPointRest` (`@Path(SHEPARD_API + spatialDataContainers)`, numeric `Long`) + `SpatialDataReferenceRest` (`SHEPARD_API/…/spatialDataReferences`, numeric `Long`) are byte-frozen upstream `openapi-5.4.0.json` surfaces — **deliberately NOT touched** (no operationId added, no Long→appId). v2 parity tracked by PLUGIN-V2-001. |
| `spatiotemporal` (v2 promote) | **fixed-here** | `/v2/spatial/promote` (SpatialPromoteRest, SPATIAL-UNIFY-004) — appId-keyed in-context action, returns unified `ReferenceV2IO`. Distinct capability, no generic seam covers it. Added `promoteFileReferenceToSpatial` operationId. |
| `v1-compat` | **allowlisted-exception** | Whole module is the named v1 carrier; its own admin is `/v2/admin/legacy/v1/stats`. No change. |
| `analytics-ts`, `fileformat-cad`, `fileformat-robotics`, `fileformat-svdx`, `fileformat-thermography`, `file-s3`, `importer`, `jupyter`, `krl-interpreter`, `minter-datacite`, `minter-epic`, `minter-local`, `spatial-importer`, `vis-afp-thermo-overlay`, `vis-ndt-grid`, `vis-trace3d` | **clean** | No v1 mints, no numeric `@PathParam`/`@QueryParam`, no per-plugin namespace violation. `fileformat-svdx`/`-thermography` REST already dissolved onto generic seams (A7-SVDX/THERMO). minters already have `operationId`s + config on generic registry (A7 slice 5). `jupyter` is Tier-3 allowlisted (sidecar). |

### Fixed in-PR vs deferred

**Fixed in-PR (20 `operationId` additions across 13 resources — rule #4):** clean, annotation-only,
zero runtime behaviour change. `@Tag` names were already clean (human-readable) across all plugins —
no code-stripping needed. The frozen spatial Constants tags (`spatialDataContainer`/`spatialDataReference`)
resolve to clean camelCase and live on frozen resources anyway.

**Deferred (no NEW dissolution warranted):** every per-plugin `/v2/<word>` namespace found was already
triaged by the V2CONV-A7 survey (2026-06-10) and is either RESOLVED (svdx, thermography, hdf, the 11
admin configs), allowlisted (aas, jupyter), kept-by-verdict (unhide feed), or a genuine domain-specific
sub-resource on an appId-keyed path (git preview/check-update, video upload/download, wiki-write,
spatial promote). The only outstanding dissolution is **PLUGIN-V2-001** (the frozen spatiotemporal v1
resources' `/v2/` appId parity shelf) — pre-existing, not re-filed. **No net-new v1 violations.**

### Numeric-id wire audit

Zero plugin IO classes leak a numeric Neo4j id on the wire. The only `Long` `@PathParam`/`@QueryParam`
usages are the two frozen spatiotemporal v1 resources (allowlisted). `AasShellIO.id` is a String IRI.

### Gate numbers (2026-06-13)

- `bash scripts/install-plugins.sh` → all plugins installed.
- 9 touched plugin modules (`aas`, `ai`, `hdf5`, `kip`, `unhide`, `video`, `git`, `wiki-writer`,
  `spatiotemporal`) `test-compile` → all EXIT 0.
- CI-parity `backend $ ./mvnw -P unit-test -DskipITs -Djacoco.haltOnFailure=true verify` →
  **BUILD SUCCESS**: JaCoCo all checks met, SpotBugs `BugInstance size 0 / Error size 0`,
  OpenAPI yaml generated cleanly (validates the new operationIds).
- Pre-existing offline-mode test failures in `aas` (AasShellsRestTest DI-null, 14 errs) + `ai`
  (LocalEchoTransport) are **baseline** (confirmed via `git stash` re-run) — unrelated to annotation-only edits.

---

# Plugin backends build on /v2/ — audit (original 2026-06-03 pass)

Audited every `plugins/*/src/main/java` module (24 plugins) for dependence on the
frozen upstream-compat v1 REST surface (`/shepard/api/...`), numeric Neo4j ids as
external identifiers, and v1 IO/service shapes where a v2 equivalent exists. Mirror
of the new frontend-v2-only rule.

## What I found

Per-plugin table (only plugins with findings; the other 22 are clean — their REST is
`/v2/...` with `appId` path params, and their `de.dlr.shepard.*` imports target the
**shared SPI/core layer** (`spi.ai`, `spi.payload`, `storage`, `auth.permission`,
`context.collection`), not the frozen v1 REST surface):

| Plugin | Finding | Severity | Fix |
|---|---|---|---|
| `spatiotemporal` | `SpatialDataPointRest` mounts under `Constants.SHEPARD_API` (`/shepard/api/spatialDataContainers`) with numeric `@PositiveOrZero Long containerId`. | **MAJOR (frozen-by-design — see note)** | Keep as frozen byte-compat; ship `/v2/spatial-containers/{appId}` parity via existing SPATIAL-V6-003. Backlog `PLUGIN-V2-001`. |
| `spatiotemporal` | `SpatialDataReferenceRest` mounts under `SHEPARD_API/collections/{collectionId}/dataObjects/{dataObjectId}/spatialDataReferences` with numeric Long ids. | **MAJOR (frozen-by-design)** | Same: frozen for upstream; `/v2/data-objects/{dataObjectAppId}/spatial-data-references` parity. Backlog `PLUGIN-V2-001`. |
| `v1-compat` | Whole module is a deliberate v1 carrier (`LegacyV1*`, `/v2/admin/legacy/v1/...` admin shelf gates the v1 surface). | **N/A — intentional** | No change. This is the *exception that proves the rule*: the only plugin allowed to know about v1, and even its own admin REST is `/v2/`. |

Net: **zero net-new v1 violations.** The two spatiotemporal REST resources predate the
plugin extraction — `spatialDataContainers`/`spatialDataReferences` are present in upstream
`openapi-5.4.0.json`, so they are **frozen byte-compat surface**, not a fork addition. They
must NOT be rewritten in place (doing so breaks third-party upstream clients).

## Opportunities

- The SPI seam is already correct: `FileStorage` keys on UUID, `PayloadKind` on string
  names — no interface forces a numeric-id v1 dependency. New plugins inherit appId-shaped
  contracts for free.
- Grounding from practice: Grafana plugins declare a host-version floor
  (`grafanaDependency: ">=9.0.0"`) and build only on GA host surfaces, never experimental
  alpha. The spatiotemporal manifest already declares `shepardCompatibility ">=6.0.0-SNAPSHOT,<7"`
  — same shape. The rule formalises "build on the stable fork (`/v2/`) surface, never the
  frozen-for-others v1 paths."

## What I fixed

- Codified **"Always: plugin backends build on the /v2/ surface + appId"** in `CLAUDE.md`
  (sibling to the frontend-v2-only rule).
- No plugin *code* rewritten: the only v1 usages are frozen-by-design and rewriting would
  break upstream byte-compat — flagged + backlogged instead.

## Gaps & blockers

- `PLUGIN-V2-001` (filed): spatiotemporal needs `/v2/` appId parity for its two REST
  resources. SPATIAL-V6-003 already introduces `/v2/spatial-containers/{appId}/trace`;
  full CRUD-parity `/v2/` shelf is the remaining gap. The v1 resources stay (frozen).
- No core SPI change needed — the interfaces already expose appId/UUID-keyed contracts.

## What surprised me

The audit's one "violation" is the upstream-keeper's nightmare *and* its vindication:
the spatiotemporal numeric-id v1 REST is exactly what the upstream-upgrade-path lens
worries about — but here it's correct, because the path is byte-identical to upstream
5.4.0 and third-party clients depend on it. The rule's boundary is therefore not "no v1
ever" but "no *new* v1; existing frozen surfaces stay and get a `/v2/` sibling." `v1-compat`
being a plugin whose own admin API is `/v2/` is the cleanest possible statement of the rule.
