---
name: ShepardTemplate icons — templates carry their visual identity
description: Every ShepardTemplate gains an optional iconKey field; the UI renders the template's icon everywhere a DataObject is shown (tree rows, breadcrumbs, sidebar, cards, pickers).
type: design
stage: feature-defined
last-stage-change: 2026-06-02
---

# 122 — ShepardTemplate icons

**Status:** decision-quality 2026-06-02 (operator: *"don't forget templates with icons"*).
**Pairs with:** `aidocs/integrations/119 §2.2.1` (MFFD process-type templates), `aidocs/integrations/121 §3` (generic Project surface), `aidocs/strategy/83` (admin-configurable type icons feature request from `project_feature_ideas`).

## 0. TL;DR

A `ShepardTemplate` carries an optional `iconKey` (string, MDI name). The UI
renders that icon **anywhere a DataObject is shown** — tree rows, breadcrumbs,
sidebar, DO list cards, Predecessor/Successor chips, the Project sub-collections
panel, the Layer card on `mffd-afp-tapelaying`, MCP tool responses (as a hint
string). The DataObject's primary template is the icon source; falls back to a
per-kind default when null.

This subsumes task #22 / `project_feature_ideas` *admin-configurable type icons*:
the icon lives on the template (the natural carrier of "what kind of thing is
this DO?"), and the existing PATCH endpoint on the template lets admins change
the icon without code changes.

## 1. Why on the template (and not the DataObject)

Two alternatives — icon on the DataObject, icon on a separate `:TypeIcon`
registry — were considered.

**Icon on the DataObject** spreads the same icon name across every DO of the
same kind. 14 000 MFFD AFP-tapelaying DOs would each carry `mdi-layers`; an
admin renaming the Layer icon would have to touch 14 000 rows. The template is
the canonical "what kind of thing is this?" carrier; the icon belongs with it.

**Separate `:TypeIcon` registry keyed by template name** adds a join for every
render. The template is already loaded for any DO that has one. Adding one
optional string field has no measurable cost; introducing a new entity has the
usual SPI sprawl cost.

**On the template** is the right shape: one row per template, admin-mutable via
the existing PATCH endpoint, no new entity, no join for the common path. The
substrate (template instance hydration) already happens at render time on every
DO list endpoint.

## 2. The field

Single string field on `:ShepardTemplate`:

```
iconKey  : Optional<String>   // MDI name, e.g. "mdi-layers", "mdi-precision-manufacturing"
                              // null means "use the per-kind default"
```

**MDI** (Material Design Icons) is the right family: the existing Vuetify 3
frontend already ships with it. No new icon-font dependency, no per-platform
asset pipeline. `iconKey` is the raw MDI name string (with the `mdi-` prefix)
so a UI render is one `<v-icon :icon="iconKey">` away.

Other families (Material Symbols, font-awesome, custom SVG) are out of scope for
v1. They become straightforward later: `iconFamily` defaults to `mdi`; a future
optional `iconFamily` field opens the door without breaking the v1 contract.

## 3. Per-kind default icons (used when `iconKey` is null)

| DataObject "kind" hint                  | Default icon            |
|-----------------------------------------|-------------------------|
| Collection                              | `mdi-folder-multiple`   |
| Project (Collection with `project=true`)| `mdi-flag`              |
| DataObject (no template / unknown kind) | `mdi-circle-medium`     |
| FileReference                           | `mdi-file-outline`      |
| FileBundleReference                     | `mdi-folder-zip-outline`|
| TimeseriesReference                     | `mdi-chart-line`        |
| SpatialDataReference                    | `mdi-cube-outline`      |
| SceneGraph                              | `mdi-graph-outline`     |
| LabJournalEntry                         | `mdi-notebook-outline`  |

Kept narrow on purpose. Per-kind defaults are an in-tree fallback, not the
canonical naming layer; templates override.

## 4. MFFD process-type template icons (shipped with V100, set retroactively via V104)

V100 already shipped 2026-05-30 without `iconKey`. Per the CLAUDE.md
"never edit a migration file that has been applied to any production instance"
rule, a new migration (V104, additive) sets the icons on the existing templates.

| Template                  | iconKey                                | Reads as            |
|---------------------------|----------------------------------------|---------------------|
| `MFFDStepRoot`            | `mdi-factory`                          | the factory step    |
| `MFFDLayer`               | `mdi-layers`                           | layers stacked      |
| `MFFDPlyGroup`            | `mdi-format-list-group`                | a group of plies    |
| `MFFDTrack`               | `mdi-vector-polyline`                  | a tape track path   |
| `MFFDExecution`           | `mdi-play-circle-outline`              | a runnable execution|
| `MFFDBridgeWeldExecution` | `mdi-flash-outline`                    | the welding arc     |
| `MFFDSpotWeld`            | `mdi-dots-circle`                      | the weld spots      |
| `MFFDNDTScan`             | `mdi-radar`                            | thermography scan   |
| `MFFDCell`                | `mdi-floor-plan`                       | the work cell       |
| `MFFDLayerOverview`       | `mdi-view-dashboard-variant`           | the recipe view     |

The L2 / LUMEN / PLUTO templates set their own icons in their own seed.

## 5. Backend changes

### 5.1 Entity + IO

- `:ShepardTemplate` Neo4j entity gains `iconKey: String?` (nullable property).
- `ShepardTemplateIO`, `CreateShepardTemplateIO`, `PatchShepardTemplateIO`
  each gain a nullable `iconKey` field with the OpenAPI `@Schema(required=false,
  nullable=true, description="MDI icon name, e.g. 'mdi-layers'. Null means use
  the per-kind default.")`.
- `ShepardTemplateIO.from()` maps the field through.
- The service-layer COW (copy-on-write versioning) writes `iconKey` straight
  through. No special handling.

### 5.2 Migrations

- **V104** (new, idempotent): `MATCH (t:ShepardTemplate)` → `SET t.iconKey =
  coalesce(t.iconKey, $defaultForName)` for each of the 10 templates in §4
  using a small key→icon map. `IF NOT EXISTS` guard is implicit (re-running
  re-applies the same map). Rollback (`V104_R`) unsets `iconKey` on the same
  set.
- The pattern (one migration row per shipped template seed) repeats for
  future template seeds.

### 5.3 Admin path

The existing `PATCH /v2/templates/{appId}` body (RFC 7396 merge-patch) carries
the new field naturally. No new endpoint.

CLI: a future `shepard-admin templates set-icon <appId> <iconKey>` is
trivial — same admin-config pattern as the other knobs (per the
CLAUDE.md "Surface operator knobs in the admin config" rule); not blocking
for v1.

### 5.4 MCP

The existing `getTemplate({appId})` MCP tool's response shape gains
`iconKey` transparently from `ShepardTemplateIO`. The `getDataObject`
response could optionally hydrate `primaryTemplate: {appId, name,
iconKey}` to save MCP clients a round-trip; ship as a later
quickwin tracked separately.

## 6. Frontend changes

### 6.1 Composable

```ts
// frontend/composables/useTemplateIcon.ts
export function useTemplateIcon(template: ShepardTemplate | null, kindHint?: string): string {
  if (template?.iconKey) return template.iconKey
  return defaultIconForKind(kindHint ?? 'DataObject')
}
```

Backed by a single `defaultIconForKind` map (the §3 table). Pure function, easy
to unit-test with Vitest.

### 6.2 Render points

The composable resolves to a single MDI string; the call sites change:

- **DO list rows** (`CollectionDataObjectsPanel.vue`, `DataObjectsTable.vue`) —
  current leading column adds `<v-icon :icon="useTemplateIcon(row.primaryTemplate, row.kind)">`.
- **DO detail breadcrumbs** (`pages/collections/[id]/dataobjects/[id]/index.vue`)
  — each crumb's leading icon.
- **DO detail header** — large icon at top alongside name.
- **Tree views** — `CollectionTreeView.vue` (sub-DO tree), `DataObjectTree.vue`.
- **Predecessor/Successor chips** (`DataObjectProvGraph.vue`, lineage panels).
- **Sub-collections panel tiles** (`CollectionSubCollectionsPanel.vue`) — each
  tile's leading icon when the child Collection has a primary template.
- **Reference picker dialogs** — when picking a DO by appId.
- **MCP-driven UI** (chat surfaces that render DO previews) — uses `iconKey`
  from the MCP response.

### 6.3 Tests

Vitest unit tests for `useTemplateIcon` (template wins; falls back to kind
default; null kind hint falls back to generic). Playwright snapshot at the
4K viewport confirming the row icons render and don't shift table layout.

## 7. SHACL

No SHACL constraint required for v1 — `iconKey` is free-form by design
(operators may want custom MDI strings their org's font shipped). A future
constraint (`shepard:TemplateShape sh:property [sh:path :iconKey ; sh:pattern
"^mdi-[a-z0-9-]+$"]`) is a follow-up that ships when there's evidence of
operator confusion.

## 8. CLAUDE.md compliance audit

| Principle                                            | Status |
|------------------------------------------------------|--------|
| Schema additive + nullable                           | ✓ — `iconKey` nullable, no backfill needed at write time; V104 backfills the 10 known templates |
| Migrations idempotent + fail-fast + rollback         | ✓ — `IF NOT EXISTS` semantics via `coalesce`; V104_R unsets |
| Admin-configurable at runtime                        | ✓ — existing PATCH endpoint carries the field |
| Three-audience docs                                  | ✓ — admin runbook section + user-facing reference table + plugin-author note that plugin templates get the same field |
| Aidocs/34 ledger                                     | ✓ — V104 lands as a row |
| Aidocs/44 progress matrix                            | ✓ — task #22 marks `shipped` when V104 lands |
| Aidocs/42 vision currency                            | n/a — visual polish, not a new payload kind |
| Aidocs/16 backlog hygiene                            | ✓ — three rows added (§9) |

## 9. Backlog rows

| ID | What | Size |
|----|------|------|
| `TEMPLATE-ICONS-1` | Backend: `iconKey` on `:ShepardTemplate` + the three IOs + V104 migration with rollback. | S |
| `TEMPLATE-ICONS-2-FE` | Frontend: `useTemplateIcon` composable + wire into the §6.2 render points. | M |
| `MFFD-TEMPLATE-ICONS-1` | V104 sets the 10 MFFD icons per §4. (Sub-task of TEMPLATE-ICONS-1; called out separately so it's visible in MFFD prep.) | XS |

## 10. References

- `aidocs/integrations/119 §2.2.1` — MFFD 5-level hierarchy + the templates that now get icons
- `aidocs/integrations/121 §3` — generic Project surface; sub-collections panel needs the icon
- `aidocs/strategy/83 §22` / `project_feature_ideas` — original "admin-configurable type icons" request
- `aidocs/54` — ShepardTemplate spec (the v1 spec being extended here)
