---
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributor, frontend, ui-ux
companion: aidocs/agent-findings/v5-metadata-enrichment-survey-2026-05-23.md
---

# 100 — Cross-instance provenance: client-side rendering design

**Status.** Design — `feature-defined`. **No frontend code yet.** Upstream
survey is `stage: idea`; the importer attrs this UI consumes are not yet
on the wire on a real dest. Frontend ships are gated on
`MFFD-IMPORT-SOURCE-BY-ATTRS` landing + at least pass S1 of the dest
backfill (per `project_mffd_dest_backfill_pass.md`).

**Audience.** Frontend contributors implementing the prov surface;
reviewers gate-checking the same-PR rule for FAIR-relevant UI changes.

**Companion docs:**
- [`aidocs/agent-findings/v5-metadata-enrichment-survey-2026-05-23.md`](../agent-findings/v5-metadata-enrichment-survey-2026-05-23.md) — the survey that surfaced the attribute set
- `aidocs/semantics/94-metadata4ing-integration-design.md` — M4I-b canonical PROV-O predicates (the §6 migration target)
- `aidocs/integrations/97-fair2r-integration.md` (planned) — f(ai)²r `prov:wasGeneratedBy` framing
- `aidocs/data/00-model-inventory.md` — entity inventory (DataObject + Collection touchpoints)

---

## §1 Background

The MFFD-Dropbox importer (currently v15.x) is about to start populating
**source-instance provenance attributes** on every dest artefact it
creates. The first wave (recommended `v15.10` bundle in the survey):

- `source_created_by` / `source_updated_by` — source-side username
- `source_created` / `source_updated` — ISO-8601 source-side timestamps
- `source_created_by_displayName` / `_email` — distinct-user resolution
- `source_shepard_version` — collection-level source-instance version
- `source_user_groups` — source-side group membership

Subsequent waves add `source_permissions_at_import` (JSON snapshot for
EN 9100 audit), `source_semantic_annotations`, and source-side lab
journal entries.

**Today** the frontend would render these as plain `key: value` rows in
`AttributesDisplay.vue`, losing every bit of FAIR signal. From the
researcher's POV `source_created_by: kreb_fl` looks indistinguishable
from `temperature_setpoint_c: 180`. This is the gap this design closes.

**Why this matters strategically.** Per
[`project_competitive_position.md`](../../) cross-instance provenance is
the daily reality of DLR's two-instance topology (cube3-BT, RY,
nuclide). Per [`project_dlr_institutional_strategy.md`](../../) the
DataHub vision has multiple `shepard-*` instances publishing into
Databus. Showing cross-instance attribution well is a positional
differentiator vs. Kadi4Mat / openBIS / SciCat (which assume a single
instance).

**The design question.** How should the client distinguish
source-instance prov from dest-instance prov, and render each in a way
that a researcher (Reluctant Senior in 1 second) and an EN 9100 auditor
(in ≤ 3 clicks) can both act on?

---

## §2 Attribute taxonomy

Source-instance prov vs. dest-instance prov vs. derived (computed from
the graph). All three render differently.

| Attribute | Source | Dest binding | UI render-as | FAIR dimension |
|---|---|---|---|---|
| `createdBy` / `createdAt` | dest (already in `TitleAndMetadataDisplay`) | wire field on every entity | dest creator chip + "Created" label | R |
| `updatedBy` / `updatedAt` | dest (already in `TitleAndMetadataDisplay`) | wire field on every entity | dest editor chip + "Last edited" label | R |
| `source_created_by` | source-instance attr | `attributes["source_created_by"]` | **source author chip** with instance badge | R |
| `source_updated_by` | source-instance attr | `attributes["source_updated_by"]` | **source editor chip** (only if differs from `source_created_by`) | R |
| `source_created` | source-instance attr | `attributes["source_created"]` | "Authored on `<source-instance>`" timestamp | R |
| `source_updated` | source-instance attr | `attributes["source_updated"]` | "Last edited on `<source-instance>`" timestamp | R |
| `source_created_by_displayName` | source-instance attr | `attributes["source_created_by_displayName"]` | hover tooltip on author chip | R, I |
| `source_created_by_email` | source-instance attr | `attributes["source_created_by_email"]` | mailto link in tooltip | A, R |
| `source_shepard_version` | source-instance attr (collection-level) | `Collection.attributes["source_shepard_version"]` | Collection sidebar / footer | R (reproducibility) |
| `source_instance_url` (NEW — recommend) | source-instance attr (collection-level) | `Collection.attributes["source_instance_url"]` | makes the instance badge clickable (link-out) | F, A |
| `source_instance_id` (NEW — recommend) | source-instance attr (collection-level) | `Collection.attributes["source_instance_id"]` | stable instance identifier (`shepard-bt`, `shepard-ry`, `cube3`) for de-duplication + cross-instance discovery | F |
| `source_user_groups` | source-instance attr | `attributes["source_user_groups"]` (JSON array) | "Institutional affiliation" expandable line | I |
| `source_permissions_at_import` | source-instance attr | `attributes["source_permissions_at_import"]` (JSON object) | Audit pane — ACL diff vs current dest ACL | A |
| `source_import_session_id` | dest-side import attr | `attributes["source_import_session_id"]` | Audit pane — backfill grouping | (operational) |
| `source_attrs_provenance` (NEW — recommend) | dest-side stamp | `attributes["source_attrs_provenance"]` = `original` \| `backfilled:<activity-appId>` | "Reconstructed from source body at TTT" footnote (when backfilled) | R |

The **NEW** rows are recommendations this design surfaces but the
survey did not enumerate. Without them:

- `source_instance_url` — the instance badge is a dead string ("cube3"),
  link-out impossible. Required for FAIR-F (Findable across instances).
- `source_instance_id` — distinguishes `shepard-bt` from `shepard-ry`
  from `cube3` cleanly; the URL alone is too brittle (DNS changes).
- `source_attrs_provenance` — needed to honour
  `project_mffd_dest_backfill_pass.md` "reconstructed-vs-original"
  signal. Without it the UI cannot tell whether `source_created_by`
  came in at import time or was retro-fitted by a backfill pass.

§9 backlog adds these as `MFFD-IMPORT-INSTANCE-URL` +
`MFFD-IMPORT-ATTRS-PROVENANCE`.

---

## §3 Wire shape decision

**Decision: keep the flat `source_*` attributes as the substrate; add a
new aggregator endpoint `GET /v2/data-objects/{appId}/provenance` (and
`GET /v2/collections/{appId}/provenance`) that returns a *typed
projection* over the attrs + dest createdBy/At + `:Activity` log.**

The UI (and any pandas-friendly caller) reads the aggregator. The
importer keeps writing flat attrs. The aggregator is the projection.

### Wire shape (aggregator response)

```jsonc
{
  "appId": "019e4e56-ca63-...",
  "kind": "DataObject",
  "dest": {
    "createdBy": "shepard-importer",
    "createdAt": "2026-05-22T14:30:00Z",
    "updatedBy": "kreb_fl",
    "updatedAt": "2026-05-23T08:15:00Z",
    "instance": { "id": "shepard-nuclide", "url": "https://shepard.nuclide.systems" }
  },
  "source": {
    "createdBy": { "username": "kreb_fl", "displayName": "Florian Krebs", "email": "florian.krebs@dlr.de" },
    "createdAt": "2023-01-19T10:42:00Z",
    "updatedBy": { "username": "kreb_fl", "displayName": "Florian Krebs", "email": "florian.krebs@dlr.de" },
    "updatedAt": "2023-04-02T16:00:00Z",
    "userGroups": ["dlr-bt-zlp"],
    "instance": { "id": "shepard-bt-cube3", "url": "https://backend.bt-au-cube3.intra.dlr.de", "shepardVersion": "5.4.0" }
  },
  "backfill": {
    "kind": "original",     // or "backfilled"
    "activityAppId": null,  // or the :Activity appId that wrote these attrs
    "at": null
  },
  "links": {
    "sourceArtefact": "https://backend.bt-au-cube3.intra.dlr.de/.../dataObjects/12345"  // null if no source_instance_url
  }
}
```

For a DO with **no** source prov captured (pre-backfill state), the
`source` block is `null`; the aggregator returns `{ dest: {...},
source: null }`. The UI renders an explicit "Source provenance not
captured for this artefact" placeholder rather than hiding the section.

### Why aggregator endpoint, not typed `prov:` block on `DataObjectDetailV2IO`

Argued (API Scrutinizer vs. UI/UX vs. Digital Native):

- **API Scrutinizer.** Adding a typed `provenance` field to
  `DataObjectDetailV2IO` is technically additive and non-breaking, but
  it **leaks the source-of-truth bifurcation** into the existing IO
  shape: the importer would either need to double-write (flat attrs
  AND typed block) or the backend would need to project-on-read inside
  the existing IO, mixing data substrate concerns into a shape that
  every other caller (search results, list views, the v1-compat
  surface) also consumes. The aggregator endpoint isolates this
  concern.
- **UI/UX (Reluctant Senior).** The page already calls
  `useFetchDataObject`. Adding one more composable call
  (`useFetchProvenanceProjection(appId)`) is fine; the prov section is
  expansion-panel content, not always-visible — so the extra
  round-trip happens lazily.
- **Digital Native.** `GET /v2/data-objects/{appId}/provenance` is
  drop-into-pandas friendly. Single call, typed JSON, no need to know
  the `source_*` prefix convention. Five-line Python: `pd.json_normalize(
  requests.get(f"{base}/v2/data-objects/{app_id}/provenance",
  headers={"X-API-Key": KEY}).json())`.
- **m4i / PROV-O migration seam.** §6 — when `M4I-b` (`aidocs/semantics/94`)
  switches the substrate from opaque attrs to typed PROV-O edges, the
  aggregator endpoint changes its source-of-truth internally; the
  client contract is unchanged.

### Counter-argument and when I'd reverse this

The opposing call is "embed the projection in `DataObjectDetailV2IO`
because it's one fewer round-trip on the always-visible header." I'd
reverse only if:

- Profiling shows the extra GET cost is material on list-density pages
  (collection summary card listing 1000 DOs each fetching prov — not
  the case; prov rendering is detail-page only).
- Or if the typed block becomes the substrate AND attrs go away (i.e.,
  post-M4I-b world). At that point folding into the IO is a wash.

For v1, the aggregator wins on substrate isolation.

### UI/API parity check

Per [`feedback_ui_api_parity.md`](../../). Every UI operation on the
prov surface MUST be a single REST call:

- **Read** — `GET /v2/data-objects/{appId}/provenance` (designed
  above).
- **Backfill (write)** — out of scope here; the backfill is operator
  CLI per `project_mffd_dest_backfill_pass.md`. The UI does NOT mutate
  source-side prov; it only renders it. If a future "request backfill"
  button lands, the endpoint behind it must be a single
  `POST /v2/data-objects/{appId}/provenance/backfill` — but that's a
  separate design pass.
- **Source link-out** — pure browser `<a href>` to
  `links.sourceArtefact`; no Shepard API involved.

Parity holds: the only Shepard server-side op the UI invokes is one
GET.

---

## §4 UI rendering spec

Three affected pages + the lineage graph. ASCII sketches throughout.

### §4.1 DataObject detail page — `Provenance` section header strip

The provenance section is **already** an expansion panel (see
`frontend/pages/.../[dataObjectId]/index.vue` lines 305–333 — the
"Provenance" panel with Log/Graph tabs).

**Change:** add a **prov header strip** *above* the Log/Graph tabs,
inside the same panel. The strip is **always visible when the panel is
expanded**; expanded-by-default (already in `:default-open="[2, 3]"`
list — we'll add `4` if we add a new panel index, but simpler is to
mount the strip inside the existing Provenance panel).

```
┌─ Provenance ────────────────────────────────────────────────────────┐
│                                                                      │
│   AUTHOR  [🧑 Florian Krebs]  ←  [⬢ shepard-bt-cube3 · 5.4.0]        │
│           authored 2023-01-19 (3y ago)                               │
│                                                                      │
│   EDITS   [🧑 Florian Krebs] last edited 2023-04-02 on source        │
│           [🧑 Florian Krebs] last edited 2026-05-23 on dest          │
│                                                                      │
│   IMPORTED  by shepard-importer · 2026-05-22  ⓘ                     │
│             attrs: original (captured at import)                     │
│                                                                      │
│   ─── [Log] [Graph] [Audit] ──────────────────────────────────────  │
│                                                                      │
│   ... existing DataObjectProvLog / DataObjectProvGraph here ...      │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

The header strip is `<SourceProvDisplay />` (new). Three rows:

1. **AUTHOR** — primary signal. Source author chip + instance badge.
   This is the "1 second" answer to "who made this." Hover the chip
   for tooltip with email + mailto link. Click the instance badge to
   link out to the source artefact (if `source_instance_url` present).
2. **EDITS** — split-line view: source-side last edit AND dest-side
   last edit. If source `createdBy == updatedBy`, hide the source line
   (collapse to single "AUTHOR" row).
3. **IMPORTED** — small-text dest createdBy/At + `attrs:
   original|backfilled` provenance signal. Click the `ⓘ` for "this
   provenance was reconstructed on YYYY-MM-DD by backfill pass S2"
   detail.

Below the header strip: the existing **[Log] [Graph]** tab strip gains
a third tab **[Audit]** (rendered only when `source_permissions_at_import`
is present — §4.4).

### §4.2 DataObject detail page — empty / pre-backfill state

When source prov is null (no `source_*` attrs yet):

```
┌─ Provenance ────────────────────────────────────────────────────────┐
│                                                                      │
│   AUTHOR  [🧑 shepard-importer]                                      │
│           created 2026-05-22 (this instance)                         │
│                                                                      │
│   ┌─ Source provenance not captured for this artefact ──────────┐   │
│   │  This DataObject was imported before cross-instance prov     │   │
│   │  capture was enabled. A backfill pass can reconstruct it     │   │
│   │  from the source instance if still reachable.                │   │
│   │  [Learn more about provenance backfill →]                    │   │
│   └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│   ─── [Log] [Graph] ────────────────────────────────────────────── │
└──────────────────────────────────────────────────────────────────────┘
```

The placeholder is informational, NOT scary. It does not block the
researcher; it documents the gap and links to docs explaining the
backfill protocol (per `project_mffd_dest_backfill_pass.md`).

### §4.3 Collection detail page — source-instance footer

A single-line footer renders on `pages/collections/[collectionId]/index.vue`
near the existing metadata block. New component
`<SourceInstanceFooter />`:

```
┌─ collection metadata ─────────────────────────────────────────────┐
│  ... existing metadata ...                                         │
│                                                                    │
│  Imported from  [⬢ shepard-bt-cube3 · v5.4.0] on 2026-05-22       │
│  by shepard-importer · 6,847 DataObjects                          │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

The instance badge is the same component as on the DataObject page.
Click the badge → opens source URL if reachable. Hover → version +
import-date tooltip. The DataObject count comes from existing
collection summary endpoints; the instance + version data from
`GET /v2/collections/{appId}/provenance`.

For a non-imported (locally-authored) collection, the footer hides
entirely.

### §4.4 Audit pane — permissions snapshot diff

Rendered **only** when `source_permissions_at_import` is present
(MFFD-IMPORT-PERMS-SNAPSHOT, ships v15.11+). New component
`<SourcePermissionsAudit />`:

```
┌─ Audit ──────────────────────────────────────────────────────────┐
│  Permissions snapshot at time of import (2026-05-22 14:30 UTC)  │
│                                                                  │
│  SOURCE (shepard-bt-cube3)         vs   DEST (this instance)    │
│  ───────────────────────────────────────────────────────────── │
│  Owner: kreb_fl                          Owner: shepard-importer│
│  Read:  dlr-bt-zlp [group]               Read:  team-mffd [grp] │
│  Write: kreb_fl, dannemann_a             Write: shepard-importer│
│  Manage: kreb_fl                         Manage: instance-admin │
│                                                                  │
│  Click 🔍 to expand each role for the username list.            │
└──────────────────────────────────────────────────────────────────┘
```

Two-column diff. The auditor's 3-click test (Manufacturing-Quality lens):

1. Click DataObject in collection table
2. Expand "Provenance" panel (default open)
3. Click "Audit" tab — sees source ACL alongside dest ACL

Done. ≤ 3 clicks (one of which is a default-open expand → effectively
2 clicks).

### §4.5 Lineage graph — cross-instance edge styling

Today `CollectionLineageGraph.vue` (Vue-ECharts force-directed) and
`DataObjectProvGraph.vue` render all edges in one style. We add
**visual differentiation** for source-instance edges:

| Edge type | Style today | Style after this design |
|---|---|---|
| Dest-instance `:PREDECESSOR_OF` | solid 1.5px, single colour | unchanged |
| Source-side predecessor (from `source_predecessor_chain` attr) | n/a | **dashed 1.5px, slight grey overlay** |
| Activity log edges (`:wasAttributedTo`) | solid coloured by action kind | unchanged |
| Cross-instance derivation (`prov:wasDerivedFrom` source→dest, when M4I-b lands typed) | n/a | **dotted purple, source-instance-badge endpoint** |

Legend chip strip already exists (lines 213–219 of
`DataObjectProvGraph.vue`); we extend it with a new chip "from
source instance" using a dashed-line motif.

### §4.6 Lab journal entries — source attribution badge

When source-side lab journal entries are migrated (MFFD-IMPORT-LABJOURNAL,
v15.11+), each entry gets a small instance badge in its existing
header row, plus author attribution from source:

```
┌─ Lab Journal Entry · 2023-03-15 ────────────────────────────────┐
│  by 🧑 Florian Krebs  ←  [⬢ shepard-bt-cube3]                  │
│  (originally written on source instance)                        │
│                                                                  │
│  ... existing markdown content ...                              │
└──────────────────────────────────────────────────────────────────┘
```

Reuses `<SourceAuthorChip />` + `<SourceInstanceBadge />` — no
lab-journal-specific component.

### §4.7 Visual language — badges, icons, colours

Two atomic components carry the visual language; everything else composes
them.

**`<SourceInstanceBadge>`** — small chip with hexagon icon:

```
[⬢ shepard-bt-cube3 · 5.4.0]
```

- Icon: hexagon (`mdi-hexagon-outline`) — distinct from anything else
  in the UI (chips with shield are roles; chips with star are
  watched). Hexagon = "instance / federated node."
- Background colour: muted neutral (Vuetify `surface-variant`).
- Border: 1px solid `outline-variant` to make the chip readable on
  both light and dark themes.
- Text: instance ID (12 chars cap, ellipsised); version follows after
  `·`.
- Hover tooltip: full URL + DLR institute name (if mapped in instance
  registry — see §5 component map for `useInstanceRegistry`).
- Click: opens `source_instance_url` in new tab when present;
  otherwise no-op (chip rendered with `cursor: default`).

**`<SourceAuthorChip>`** — chip with person icon prefix:

```
[🧑 Florian Krebs]
```

- Icon: `mdi-account` (same as existing actor chips elsewhere in
  Shepard).
- Hover tooltip: username + email + mailto link.
- Colour: same as existing `MetadataCreatedField` username — consistent
  with the rest of the UI; the **instance badge** carries the
  cross-instance signal, the author chip is plain.

The principle: **author chips look the same on source and dest**
(people are people across instances). **Instance badges only appear
adjacent to source-side attribution** (dest is the assumed default; we
don't badge it). This keeps the visual load low — instance badges fire
only when there's actually a cross-instance distinction to surface.

Colour-blind safety: the visual hierarchy is icon + chip-shape, not
colour. Hexagon vs. person-circle is distinguishable regardless of
palette.

---

## §5 Component map

### New components

| Component | Path | Responsibility |
|---|---|---|
| `<SourceProvDisplay>` | `frontend/components/context/data-object/SourceProvDisplay.vue` | The §4.1 prov header strip on DataObject pages |
| `<SourceInstanceFooter>` | `frontend/components/context/collection/SourceInstanceFooter.vue` | The §4.3 collection-level source-instance footer |
| `<SourcePermissionsAudit>` | `frontend/components/context/data-object/SourcePermissionsAudit.vue` | The §4.4 audit pane (only when `source_permissions_at_import` present) |
| `<SourceInstanceBadge>` | `frontend/components/context/display-components/provenance/SourceInstanceBadge.vue` | Atomic instance badge chip (hexagon icon, version, click link-out) |
| `<SourceAuthorChip>` | `frontend/components/context/display-components/provenance/SourceAuthorChip.vue` | Atomic author chip with hover tooltip + mailto |
| `<ProvenanceEmptyState>` | `frontend/components/context/data-object/ProvenanceEmptyState.vue` | The §4.2 "source provenance not captured" placeholder |

### New composables

| Composable | Path | Responsibility |
|---|---|---|
| `useProvenanceProjection(appId, kind)` | `frontend/composables/context/useProvenanceProjection.ts` | Single source of truth fetch for the §3 aggregator response; reactive; key on `[appId, kind]` |
| `useInstanceRegistry()` | `frontend/composables/common/useInstanceRegistry.ts` | Static map `instanceId → { name, url, dlrInstitute }`. Seeded from admin config (`shepard.instance-registry.*`). Used by `<SourceInstanceBadge>` for tooltip enrichment. |

The registry composable enables friendly hover text ("DLR BT,
Augsburg") without each instance needing to publish its institute
metadata. Operator-configurable per
[`feedback_admin_configurable_runtime.md`](../../) (so adding a new
peer instance is a runtime PATCH, not a deploy).

### Existing components touched

| Component | Path | Change |
|---|---|---|
| `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | (existing) | Inject `<SourceProvDisplay>` inside the existing "Provenance" expansion panel; add `[Audit]` tab when source perms present |
| `pages/collections/[collectionId]/index.vue` | (existing) | Inject `<SourceInstanceFooter>` near existing metadata block |
| `CollectionLineageGraph.vue` | `frontend/components/context/collection/` | Extend edge-styling switch for source-instance edges (dashed) |
| `DataObjectProvGraph.vue` | `frontend/components/context/data-object/` | Same — dashed-line motif on source-side edges |
| `DataObjectLabJournalEntryList` | (existing) | Add `<SourceInstanceBadge>` to each entry header when entry came from source instance |

### Components explicitly NOT touched

| Component | Why |
|---|---|
| `TitleAndMetadataDisplay.vue` | Shared across DataObject + every container + every ref. Adding source prov here forces every container/ref to deal with it. Source prov gets its own section. |
| `AttributesDisplay.vue` | Stays opaque key/value renderer for non-prov attrs. The `source_*` prefix is filtered out by `<SourceProvDisplay>` consumption and rendered there instead — so it doesn't double-render in both places. |
| `MetadataCreatedField` / `MetadataUpdatedField` | These continue to show dest createdBy/At in the top metadata bar — unchanged. |

The principle (per advisor): adjacent, not embedded. `<SourceProvDisplay>`
sits *next to* the existing dest-side metadata, not inside it. This
keeps `TitleAndMetadataDisplay.vue` reusable across all entity types
without source-prov contamination.

### Hide the `source_*` keys from the generic attributes panel

`AttributesDisplay.vue` currently iterates `Object.keys(entity.attributes)`.
We add a filter: keys starting with `source_` are *handled by
`<SourceProvDisplay>`* and not rendered in the generic attributes
list. This prevents the same data appearing in two places. Implementable
as one-line filter; gated by a `hideSourcePrefixedKeys` prop so
advanced-mode users can opt to see the raw attrs if they want.

### Plugin boundary

This is **core frontend, in-tree.** Justification per the plugin-first
heuristic in `CLAUDE.md`:

- Cross-instance prov is not a new payload kind; it's a rendering
  treatment of provenance metadata that every payload kind will carry.
- The atomic primitives (`<SourceInstanceBadge>`, `<SourceAuthorChip>`)
  belong in core because every future plugin (including
  `shepard-plugin-aas`, `shepard-plugin-publisher`) needs to render the
  same badges over its own payload pages.
- The aggregator endpoint (`GET /v2/data-objects/{appId}/provenance`)
  is a cross-cutting facility, not a plugin-specific surface. Lives in
  `de.dlr.shepard.v2.provenance.resources` next to existing
  `ProvenanceRest`.
- Plugins **consume** these components via the shared frontend
  package; they don't bring their own.

(No plugin is being created by this design.)

---

## §6 m4i / PROV-O migration path

Today's substrate: flat `source_*` attrs on the DataObject's
`Map<String,String> attributes`.

Target substrate (per `aidocs/semantics/94` M4I-b): typed PROV-O edges
in Neo4j, e.g.:

```cypher
(:DataObject {appId: 'dest-abc'})-[:prov_wasDerivedFrom]->(:DataObject {appId: 'source-xyz'})
(:DataObject)-[:prov_wasAttributedTo]->(:User {username: 'kreb_fl'})
(:DataObject)-[:prov_wasGeneratedBy]->(:Activity {appId: 'import-act-001'})
```

Plus f(ai)²r predicates for AI-driven mutations (per
`project_fair2r_integration.md`):

```cypher
(:Activity {appId: 'backfill-pass-2'})-[:fair2r_modeOfProduction]->("ai")
```

### Migration sequence

1. **Today (pre-M4I-b).** `GET /v2/data-objects/{appId}/provenance`
   reads attrs directly: parses `source_created_by`, `source_created`,
   etc. into the typed JSON response. The UI sees the typed shape; the
   substrate is opaque attrs.
2. **M4I-b ships canonical predicates.** The aggregator's
   data-fetching internals switch from "read attrs" to "read typed
   edges + materialised user nodes." Wire shape (JSON response) is
   **unchanged.** The UI sees no difference.
3. **Optional: deprecate `source_*` attrs.** Once M4I-b stable + a
   migration pass has rewritten all old attrs into typed edges, the
   importer stops writing attrs (or a backwards-compatibility writer
   continues writing them as a hint until v6 — operator choice via
   feature flag).

The aggregator endpoint is the M4I-b migration seam. The frontend
ships **once**, against the typed response shape, and survives
substrate-substitution underneath.

### Frontend transparently consumes either substrate

The `useProvenanceProjection` composable is the *only* code that
touches the aggregator. Components consume the composable's typed
output. Substrate switches are invisible above the composable
boundary.

A small concession: if for some reason a deployment skips M4I-b (e.g.,
a fork that doesn't adopt m4i), the frontend still works — it just
keeps reading attrs via the aggregator. No coupling.

---

## §7 Backfill UX

### Pre-backfill state

For a dest DataObject imported before `MFFD-IMPORT-SOURCE-BY-ATTRS`
shipped (most of today's MFFD-Dropbox collection on `nuclide`), the
aggregator returns `source: null`. The UI renders the §4.2 empty
placeholder.

### During backfill (between S0 and S(n))

`project_mffd_dest_backfill_pass.md` runs backfill as multiple iterative
passes (S0 → S1 → S2 → …). During the pass:

- The backfill operator (CLI) creates a `:Activity` per write batch
  with `actionKind: BACKFILL`, `agentUsername: <operator>`,
  `summary: "Pass S2: distinct-user resolution"`.
- Each attr write is associated with that Activity (recorded in the
  provenance log).
- The aggregator response carries
  `backfill: { kind: "backfilled", activityAppId: "<the-S2-activity>",
  at: "<timestamp>" }`.

The UI signals the reconstructed state with a small subtle annotation
in the §4.1 IMPORTED row:

```
IMPORTED  by shepard-importer · 2026-05-22  ⓘ
          attrs: backfilled by Pass S2 on 2026-05-23
```

Click `ⓘ` → drawer opens showing the full backfill chain:
"S0 (post-ingest) → S1 (source-attrs preserved) → S2 (users
resolved) → ... current state." Each step links to the corresponding
snapshot in the snapshot timeline (per `project_snapshot_boundaries.md`).

### Post-backfill state

Once the backfill chain reaches its target shape (let's say S4 covers
all of the survey's recommendations), the UI looks **identical** to a
freshly-imported "captured at import" DataObject — except the
`backfill.kind` is still `"backfilled"`, so the `ⓘ` annotation
remains. The researcher can always trace which step in the chain
added which fact.

The transparency principle (per
[`feedback_no_synthetic_provenance.md`](../../)): reconstructed-vs-
original is visible, not hidden.

### Failed-backfill state

If the source instance is decommissioned / unreachable when backfill
runs, the operator records `:Activity { actionKind: BACKFILL,
status: FAILED, errorReason: "source unreachable" }`. The UI shows
the empty placeholder with an additional note: "Backfill attempted
2026-06-12 — source instance no longer reachable. Provenance cannot be
reconstructed."

Honest gap-visibility, not silent failure.

---

## §8 Lens panel

### Primary lens: Frontend UI/UX (Role 1)

The prov section can't visually overpower the data. Researchers come
to a DataObject page for *the data*, not for a provenance audit. The
design keeps the prov section behind an expansion panel (already the
case today), gives the header strip three quiet rows above the
existing Log/Graph tabs, and confines visual flair (hexagon badges,
chips) to the prov section. The §4.7 visual language uses icon shape
(hexagon vs. person) not colour — accessibility-friendly and quiet on
the page. **Recommendation stands.**

What would change my mind: if usability testing shows researchers miss
the prov section entirely. Then we'd promote one signal (the author
chip) to the always-visible metadata bar — at the cost of crowding
`TitleAndMetadataDisplay.vue` again.

### Research Data Manager (Role 5) — FAIR alignment

Each design decision tagged by FAIR dimension:

- **F (Findable)** — `source_instance_url` + `source_instance_id` plus
  the link-out from the badge makes the source artefact discoverable
  by URL. Without these, FAIR-F at the cross-instance level is broken.
- **A (Accessible)** — `source_permissions_at_import` rendered in
  §4.4 audit pane lets an auditor reconstruct "who had access when"
  without re-auth against the source instance. Email-link from the
  author chip closes the "how do I contact the responsible person"
  half.
- **I (Interoperable)** — m4i alignment (§6) means the prov data is
  expressible in canonical PROV-O / metadata4ing; the aggregator can
  serve `application/ld+json; profile=metadata4ing` (sibling content
  negotiation pattern to `ProvenanceRest`). Future plugin: same view,
  RDF triple export.
- **R (Reusable)** — preserves the source author / source timestamp /
  source group context that a downstream researcher needs to interpret
  the data. Pre-this-design, R was broken because `createdBy =
  importer-api-key`. Post-this-design, R = 2/3 (still missing
  PIDs/DOIs — separate work).

Lens conclusion: **this design closes the largest FAIR-R hole the
fork has today.** Recommendation stands.

### Industrial Manufacturing & Quality Engineer (Role 4) — EN 9100

Click-path test: "show me who created TR-Q1-step5 on source + who has
touched it on dest" in ≤ 3 clicks.

1. Open collection → click DataObject "TR-Q1-step5" *(click 1)*
2. Expand "Provenance" panel — but it's default-expanded *(click 0)*
3. Read the AUTHOR row (source author + instance) and EDITS row
   (source last-edit + dest last-edit) *(no click)*
4. For full ACL diff: click "Audit" tab *(click 2)*

**Total: 2 clicks to full audit answer.** Beats the ≤ 3 budget.
Recommendation stands.

What would tighten this: pin the Audit tab in default-collapsed mode
and make the auditor expand it (a 3rd click), to keep the prov section
quiet for casual researchers. Acceptable trade.

### API Scrutinizer (Role 3) — endpoint shape

Adding a new endpoint vs. extending an existing one:

- The existing `GET /v2/provenance/entity/{appId}` returns
  `ActivityIO[]` only — Activity-log rows, no source-side aggregation.
- Extending it to add a `?include=source-prov` query param would mix
  the response shape (sometimes an array, sometimes an object). Bad
  shape.
- A new `/v2/data-objects/{appId}/provenance` (and
  `/v2/collections/{appId}/provenance`) is **scoped to a single
  entity**, returns a typed projection object, and lives next to the
  existing collection/dataobject REST surfaces (not under
  `/v2/provenance/...` which is "the activity log").

Recommendation stands. The new endpoint is one shape, one purpose, no
overload. Pairs cleanly with the existing
`/v2/provenance/entity/{appId}` (which remains the Activity log).

### Reluctant Senior Researcher (Role 9) — 1-second test

"Who authored this?" — answered by the AUTHOR row's source-author
chip + instance badge in the prov header strip. **No click needed**
beyond expanding the prov panel (default-expanded).

"When was it made?" — directly under the author chip.

"On which instance?" — instance badge right of the author.

These are the three questions the Reluctant Senior asks in 1 second
on his folder structure. Pre-this-design they're not answerable from
dest alone; post-this-design they're answerable without leaving the
detail page. **Recommendation stands.**

What would fail the lens: if the prov panel defaulted to *collapsed*
and the Reluctant Senior had to expand it to get any prov info. Spec
says default-expanded; honour that.

### Digital Native Researcher (Role 10) — 5-line Python

```python
import requests, pandas as pd
KEY, BASE = "...", "https://shepard.nuclide.systems"
r = requests.get(f"{BASE}/v2/data-objects/{appid}/provenance",
                 headers={"X-API-Key": KEY})
df = pd.json_normalize(r.json())
```

Single call. Typed JSON. Drop into pandas. No `source_*` prefix lore
to learn. **Recommendation stands.**

### Opposing lens — what gets cut

Tension across lenses (mandatory per
[`feedback_agents_argue_and_consult.md`](../../)):

- **UI/UX wants the prov section quiet** (low visual load).
- **RDM wants it loud** (FAIR signal must be obvious).
- **Auditor wants it scannable** (≤ 3 clicks).

UI/UX is primary. The compromise: prov section is behind an expansion
panel (UI/UX win) but defaults to expanded (RDM compromise: the data
is visible the first time the page loads, but a one-click collapse
gets it out of the way for repeat visits). Audit tab is hidden behind
a tab strip inside the panel (auditor's 3-click budget honoured, UI/UX
not crowded).

What RDM would cut from this compromise: nothing — the source-prov
data is all present and visible.
What UI/UX would cut: the legend chip strip in the lineage graph
(§4.5) is borderline visual-noise; ship without it first, add only if
researchers report confusion.
What the auditor would cut: nothing — the audit tab is a clean
isolated surface.

The biggest unresolved trade-off: should the §4.1 AUTHOR row appear
in `TitleAndMetadataDisplay.vue` for always-visible source attribution?
UI/UX says no (crowds the header). RDM says yes (1-second test wins
without panel expansion). I land with UI/UX because the panel default-
expanded delivers the same 1-second answer. If usability testing
reverses this, revisit.

---

## §9 Backlog rows

Paste-ready (matching the survey's row format). Append to
[`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md).

```markdown
| FE-PROV-AGGREGATOR | **Backend — provenance aggregator endpoint.** Implement `GET /v2/data-objects/{appId}/provenance` + `GET /v2/collections/{appId}/provenance` returning the typed `{ dest, source, backfill, links }` shape from `aidocs/frontend/100 §3`. Substrate today = `source_*` attrs on the entity + dest createdBy/At + `:Activity` log; substrate post-M4I-b = typed PROV-O edges (same response shape). Lives in `de.dlr.shepard.v2.provenance.resources.ProvenanceAggregatorRest` (new sibling to existing `ProvenanceRest`). 200 with `source: null` for entities without source prov captured. Content-negotiation for `application/ld+json; profile=metadata4ing` (mirrors `ProvenanceRest`). | M | queued | Source: `aidocs/frontend/100 §3 + §9`. Pairs with MFFD-IMPORT-SOURCE-BY-ATTRS (writer) + FE-PROV-COMPONENTS (consumer). Gated on MFFD-IMPORT-SOURCE-BY-ATTRS landing — endpoint can ship before, but tests against a real DO require the writer first. |

| FE-PROV-COMPONENTS | **Frontend — cross-instance provenance rendering.** Ship `<SourceProvDisplay>` + `<SourceInstanceFooter>` + `<SourcePermissionsAudit>` + `<SourceInstanceBadge>` + `<SourceAuthorChip>` + `<ProvenanceEmptyState>` per `aidocs/frontend/100 §4 + §5`. Wires through `useProvenanceProjection(appId)` composable hitting FE-PROV-AGGREGATOR. Touches: DataObject detail page (existing Provenance expansion panel — add header strip + Audit tab), Collection detail page (footer below existing metadata), lineage graphs (dashed edges for source-side). Hide `source_*` keys from `AttributesDisplay.vue`. Vitest coverage ≥ 70% on new components per CLAUDE.md test-floor rule. | M | queued | Source: `aidocs/frontend/100`. Gated on FE-PROV-AGGREGATOR (consumer of the new endpoint). |

| FE-PROV-INSTANCE-REGISTRY | **Admin-configurable instance registry.** `:InstanceRegistry` Neo4j singleton + `GET/PATCH /v2/admin/instances` for operators to register peer Shepard instances (id → name → url → dlrInstitute). Seeds the `useInstanceRegistry()` composable's friendly tooltip text. Follows the A3b/N1c2/UH1a runtime-configurable pattern (per `feedback_admin_configurable_runtime.md`). Default seed: empty (operator opt-in). | S | queued | Source: `aidocs/frontend/100 §5 + §4.7`. Enables hover text on `<SourceInstanceBadge>` ("DLR BT, Augsburg"). Used by FE-PROV-COMPONENTS but optional (badge falls back to bare instance ID if registry empty). |

| MFFD-IMPORT-INSTANCE-URL | **v15.10/v15.11 — capture source instance URL + ID on import.** The importer fetches `GET /versionz` (per MFFD-IMPORT-VERSIONZ-STAMP) but doesn't capture the source instance's own URL or canonical ID. Add `source_instance_url` (full base URL the importer is calling) + `source_instance_id` (operator-configured stable identifier, default = derived from URL hostname) to the dest collection. Required for the frontend `<SourceInstanceBadge>` link-out to actually link. | S | queued | Source: `aidocs/frontend/100 §2`. Pairs with MFFD-IMPORT-VERSIONZ-STAMP — write same time, both collection-level attrs. Recommends an `--source-instance-id` CLI flag with auto-derived default. |

| MFFD-IMPORT-ATTRS-PROVENANCE | **v15.10/v15.11 — stamp every source_* attribute write with `original` or `backfilled` origin.** Add `source_attrs_provenance` attribute on each artefact carrying either `"original"` (captured at first import) or `"backfilled:<activity-appId>"` (added by a later backfill pass). Read by the FE-PROV-AGGREGATOR endpoint's `backfill` block. Without this, the frontend cannot distinguish "captured cleanly" from "reconstructed retroactively" — the §7 backfill UX falls apart. | S | queued | Source: `aidocs/frontend/100 §2 + §7`. Pairs with MFFD-IMPORT-SOURCE-BY-ATTRS (original) and the dest-backfill pass operator script (backfilled stamp written there). |

| FE-PROV-LABJOURNAL-BADGE | **Frontend — source-instance badge on imported lab journal entries.** When `MFFD-IMPORT-LABJOURNAL` lands (lab journal entry migration), each entry's header gains a `<SourceInstanceBadge>` indicating "originally written on source instance." Uses the existing `<SourceAuthorChip>` for author and the new badge for instance. | S | queued | Source: `aidocs/frontend/100 §4.6`. Gated on MFFD-IMPORT-LABJOURNAL + FE-PROV-COMPONENTS (depends on the atomic badge component). |

| FE-PROV-LINEAGE-DASHED | **Frontend — dashed-edge styling for source-instance lineage edges.** Extend `CollectionLineageGraph.vue` + `DataObjectProvGraph.vue` to render source-side predecessor links with dashed-line motif distinct from dest-side solid edges. New "from source instance" legend chip. Gated on source predecessor chain attrs being available (today the importer drops them per `MFFD-IMPORT-SOURCE-CHAIN`). | M | queued | Source: `aidocs/frontend/100 §4.5`. Pairs with `MFFD-IMPORT-SOURCE-CHAIN`. |

| FE-PROV-PLAYWRIGHT | **Frontend — Playwright tests for cross-instance prov UI.** Per `feedback_validate_via_ui.md`. Scenarios: (a) DO with no source prov shows empty placeholder; (b) DO with full source prov shows AUTHOR + EDITS + IMPORTED rows; (c) backfilled DO shows `ⓘ` annotation; (d) Audit tab visible only when `source_permissions_at_import` present; (e) instance badge link-out fires when `source_instance_url` set, no-op otherwise; (f) Reluctant Senior 1-second test — author resolvable in single page load. | S | queued | Source: `aidocs/frontend/100 §8`. Gated on FE-PROV-COMPONENTS landing. |
```

---

## §10 Sequencing

Five-step chain. Each step gates on the previous.

```
MFFD-IMPORT-SOURCE-BY-ATTRS       (writer: dest attrs populated)
    ↓
MFFD-IMPORT-VERSIONZ-STAMP +      (collection-level + per-DO context)
MFFD-IMPORT-DISTINCT-USERS +
MFFD-IMPORT-INSTANCE-URL +
MFFD-IMPORT-ATTRS-PROVENANCE
    ↓
FE-PROV-AGGREGATOR                (backend endpoint reads the substrate)
    ↓
FE-PROV-COMPONENTS                (frontend consumes the endpoint)
    ↓
FE-PROV-PLAYWRIGHT                (validation)
```

In parallel (not gated on the chain):

- **FE-PROV-INSTANCE-REGISTRY** — admin can configure peers any time;
  badge tooltip enrichment is nice-to-have for shipping but not
  blocking.
- **FE-PROV-LABJOURNAL-BADGE** — gated on `MFFD-IMPORT-LABJOURNAL`
  (separate, lower priority).
- **FE-PROV-LINEAGE-DASHED** — gated on `MFFD-IMPORT-SOURCE-CHAIN`
  (separate, mid priority).

**Pre-aggregator state.** The `<ProvenanceEmptyState>` component
renders for *every* DO until source attrs are populated on at least one
DO. We can land FE-PROV-COMPONENTS earlier than the importer fix by
also rendering empty state for `source: null` responses; this gives
the frontend code a place to live without becoming user-visible
prematurely.

**Persona-audit gate.** Per `aidocs/00-doc-stages.md` taxonomy, this
doc is `feature-defined`. To advance to `audited-by-personas`, dispatch
agents Role 1 (UX), Role 5 (RDM), Role 4 (IMQE) against the design;
incorporate or close findings.

---

## Executive summary

1. **Biggest UX win:** Source author + source instance answerable in
   < 1 second on every DataObject page (Reluctant Senior 1-second test
   passes). FAIR-R hole that today shows `createdBy = importer-api-key`
   gets properly closed. Cross-instance attribution becomes a visual
   first-class signal, not opaque attr noise.
2. **Biggest risk:** Substrate bifurcation if M4I-b doesn't ship cleanly
   — the aggregator endpoint absorbs that risk (it's the migration
   seam), but if M4I-b never lands, the prov data stays as opaque
   `source_*` attrs forever. Mitigation: aggregator endpoint internals
   are self-contained; M4I-b is desirable but not blocking.
3. **Next concrete step:** Dispatch a persona-audit on this design
   (Role 1 UX + Role 5 RDM + Role 4 IMQE) — promote to `audited-by-
   personas`. In parallel: file the §9 backlog rows. Implementation
   begins once `MFFD-IMPORT-SOURCE-BY-ATTRS` lands on real dest data
   (likely v15.10 release).
