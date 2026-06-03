---
stage: feature-defined
last-stage-change: 2026-06-03
---

# Plugin backends build on /v2/ — audit

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
