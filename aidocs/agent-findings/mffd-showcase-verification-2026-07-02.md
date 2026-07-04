---
stage: deployed
last-stage-change: 2026-07-02
---

# MFFD showcase verification sweep — 2026-07-02

Full verification of the MFFD showcase against the live deployment
`https://shepard.nuclide.systems` (main, post-`82a575fc4`), ahead of the
guided tour (`docs/help/mffd-tour.md`). Method: Playwright at 1920×1080
(`loginAs(bob)`), one visit per surface capturing HTTP success, console
errors, error banners/toasts, and the load-bearing element; Cypher +
API-key probes for the data-level claims. Screenshots in the session
scratchpad (`mffd-verify/`), not committed.

## What I verified

### Collections (8/8 green)

| Collection | appId | expected DOs | DB actual | UI | notes |
|---|---|---|---|---|---|
| MFFD Upper Shell — Project | `019ed455-62cd…` | 1 | 1 | ✅ | project umbrella renders |
| mffd-afp-tapelaying | `019ed455-66f4…` | 8483 | 8483 | ✅ | FAIR landing + cite card + DMP + RO-Crate + evidence pack; sidebar tree loads lazily, no hang (12 s settle) |
| mffd-bridge-welding | `019ed455-6781…` | 3108 | 3108 (1031 direct + nested) | ✅ | |
| mffd-spot-welding | `019ed455-67f7…` | 21 | 21 | ✅ | |
| mffd-ndt-thermography | `019ed455-6866…` | 1845 | 1845 (744 direct + nested) | ✅ | |
| mffd-cell | `019ed455-68d9…` | 2 | 2 | ✅ | |
| mffd-stringer-welding | `019edb10-c107…` | 144 | 144 (139 direct + nested) | ✅ | |
| MFFD RDK → URDF Viewer Showcase | `019f1472-d0af…` | 4 | 4 | ✅ | rich description, cite card |

All 8 also answer `GET /v2/collections/{appId}` → 200 via the
`mffd-import-2026-06-17` API key. DO counts verified in Neo4j
(`has_dataobject` + `has_child*` closure) — every expected number matches
exactly.

### Deck surfaces (14) + additional aspects

| # | Surface | Status | Notes |
|---|---|---|---|
| 1/A | DataObject detail (Track 9, provenance + annotations + cite) | ✅ | cite card (4 formats), 2 annotations, 5 attributes, provenance panel with activities |
| 2/B1 | `/semantic/vocabularies` | ✅ | 11 vocabularies listed (CHAMEO, DataCite, DC, GeoSPARQL, HDF5, Material OWL, metadata4ing, PROV-O, schema.org, Shepard Internal, SKOS) |
| 3/B2 | Collection FAIR landing (AFP) | ✅ | hero-hint, cite, completeness 40/100 (honest 4/9 checks), DMP, RO-Crate, evidence pack |
| 4/C | Trace3D thermal | ✅ (after fix) | was ❌ "Channel 'x' returned 0 points" — see Fixed-in-passing; recheck renders the trace canvas |
| 5/D | TS reference detail | ✅ | chart canvas 1360×320; channel auto-populate also fixed by the pageSize fix |
| 6/E | SVDX welding DO | ✅ | DO page renders (cite + 2 annotation chips + File(1)); the 213 `urn:shepard:svdx:*` annotations live on the FileReference detail — verified separately (below) |
| 7/F | TS container (192 channels) | ✅ (after fix) | was ❌ "Error while fetching timeseries channels: HTTP 400" + empty table |
| 8/G | Trace3D pressure/force | ✅ (after fix) | same failure + fix as C |
| 9/H | OTvis thermography heatmap | ✅ | phase heatmap paints, 2 frames, amplitude/phase toggle, no error banner |
| 10/I | Video player (h.264 proxy) | ✅ | `readyState=4`, `duration=199.4 s`, seek to 2:12 paints a real welding frame (screenshot pixel analysis: mean luminance 130, full dynamic range — not black). `getImageData` checksum blocked by canvas taint (proxy served cross-origin-opaque), so verification is screenshot-based |
| 11/J | Scene-graph play (KR210 URDF) | ✅ | KUKA-orange mesh renders, "10 frames · 9 joints · 6 joint bindings", no error banner |
| 12/K | Home | ✅ | greeting, watched empty-state, recent + shared-with-me lists |
| 13/L | `/me` | ✅ | profile hub tiles (API keys, MCP, Git credentials, AI settings, Semantic, Templates) |
| 14/M | Collections gallery | ✅ | table view with per-collection # DOs, descriptions, timestamps |
| + | `/scene-graphs` index | ✅ | lists the MFFD AFP KR210 play recipe |
| + | `/search?q=tapelaying` | ✅ | finds mffd-afp-tapelaying |
| + | SPARQL from collection detail | ✅ | collection Tools dropdown carries "Query annotations (SPARQL) — pre-filled query of this Collection's annotation triples" (+ "Terms used here" + "Create template"); in-context-first rule satisfied |
| + | Provenance on recently-mutated entity | ✅ | video ref `019f129f-aa8b…` has a PROV-O `GENERATED`-linked `:Activity`; DO detail (A) renders the activity feed |
| + | Sidebar tree on 8483-DO collection | ✅ | lazy loads, no hang |
| + | Video backfill fleet | ✅ (with caveat) | see below |
| + | SVDX annotations on FileReference | ✅ | `Scope Project_AutoSave_18_09_41.svdx` (`019ed586-8f3d…`) carries 213 `urn:shepard:svdx:*` SemanticAnnotations in Neo4j; the ref detail page renders the full chip set (formatVersion, projectGuid, per-channel channelName/symbolName/dataType, …). Note: "Open as… — No view recipes for file kind svdx yet" |

### Video backfill fleet state

```text
MATCH (v:VideoStreamReference) RETURN v.proxyStatus, v.deleted, count(*)
→ READY / deleted=false  : 63
→ NULL  / deleted=false  :  3
→ NULL  / deleted=true   : 853  (tombstones of superseded refs — expected)
```

**63 READY, 0 PENDING, 0 FAILED** — the 60-video fleet drain finished.
The 3 live NULL rows are *not* transcode stragglers: they have **no
`storageLocator` and no probe metadata at all** (aborted/incomplete uploads:
`P02Strich_s_1.Bahn.MP4`, `P02Strich_s_2.Bahn.MP4`, `P02_1.Bahn.MP4` on DOs
`019efce6-9a65…`, `019eff0b-8601…`, `019eff1e-44bd…`). The backfill endpoint
correctly excludes them (`storageLocator IS NOT NULL` guard — dry-run
returns 0 candidates). Their player pages will show a source with no bytes.
Backlog row filed: `VIDEO-EMPTY-UPLOAD-ROWS-2026-07-02` in `aidocs/16`.

## Broken/degraded

1. **[FIXED] Trace3D C + G, TS container F, TS-ref D channel auto-populate —
   channels fetch 400.** Root cause: `APISIMP-CHANNEL-PAGESZ-MAX` (PR #2196,
   `e5fae68e4`) added `@Max(500)` to `listChannels pageSize` on the backend,
   but four frontend callers still requested 1000/2000 →
   `400 Constraint Violation` → empty channel lists → "Channel 'x' returned
   0 points" (C/G), empty channel-inventory table + error toast (F), silent
   auto-populate failure (D). Fixed in this sweep (see below). The MFFD AFP
   container has 192 channels, well under one 500-page, so a single capped
   page fully restores behaviour.
2. **3 dead VideoStreamReference rows** (no bytes, no proxy) in
   mffd-stringer-welding — degraded, backlog row filed (above), not deleted
   per the referenced-data retention rule.
3. **NEW (revealed by the fix):** the TS container page's Semantic
   Annotations panel toasts `Error while fetching semantic annotations:
   Caller 'bob' lacks Read permission on the subject entity` for a Reader
   persona, even though the container page itself gates fine. Previously
   masked by the channels-400 toast. Filed
   `SEMANN-CONTAINER-PERM-2026-07-02` in `aidocs/16`.
3. **Minor, noted (not fixed):**
   - TS reference detail (D) header shows `Container: unknown name
     (ID: 221633)` — container name unresolved on the ref page, and a v1
     numeric id is displayed. Filed as `TSREF-CONTAINER-NAME-2026-07-02`.
   - `/search` results table shows the numeric collection id (209) as its ID
     column — appId-rule annoyance, logged in
     `aidocs/agent-findings/ui-annoyances.md` ledger spirit; existing
     SEARCH-* rows cover the search surface rework.
   - AFP + RDK collection landings show "No hero image set — add one via the
     Edit Collection dialog" — cosmetic nudge visible to readers; consider
     seeding hero images for the showcase collections.
   - `/semantic/vocabularies` lists 11 vocabularies but no svdx-specific
     entry — the `urn:shepard:svdx:*` predicates presumably live under the
     Shepard Internal Vocabulary; not a breakage, just discoverability.

## Fixed-in-passing

- **Channels pageSize cap regression (C/D/F/G above).** Clamped all four
  frontend callers to the server cap of 500:
  `frontend/utils/shapesRenderChannels.ts`,
  `frontend/composables/container/TimeseriesContainerAccessor.ts`
  (`CHANNEL_PAGE_SIZE`),
  `frontend/composables/container/useFetchV2Channels.ts`,
  `frontend/pages/.../timeseriesereferences/[...]/index.vue`; updated the
  unit-test expectation (`tests/unit/shapesRenderChannels.test.ts`). Gates:
  lint ✅ (0 errors), vitest ✅ (2526), typecheck ✅, `make redeploy-frontend`
  smoke ✅ (26/26). Rechecked live: C + G render the 3D trace, F lists the
  channel inventory without a toast.
- **In-app help catalogue entry** for the new MFFD tour page
  (`frontend/utils/helpMarkdown.ts` `DOC_SECTIONS`), so `/help` reaches the
  tour without a deep URL; second `make redeploy-frontend` ships the tour
  markdown (`docs/help/mffd-tour.md` → `public/docs/`) + catalogue entry.

## Deliverable 2 — the guided tour

`docs/help/mffd-tour.md` (13 stops, every stop verified green above):
gallery → AFP collection → Track 9 DO → TS ref → TS container → Trace3D
thermal → Trace3D force → OTvis heatmap → SVDX annotations → welding video
→ URDF scene-graph play → vocabularies → search, plus a "Where to go
deeper" section. Linked from `docs/user-guide.md §Task guides` and the
in-app `/help` sidebar ("Getting started" section).

## What surprised me

- **A backend-only "API simplification" PR silently broke three deck
  surfaces.** `@Max(500)` was the right call, but no sweep of frontend
  callers accompanied it — and no e2e test covers the Trace3D happy path.
  The channels listing is consumed in four places with three different
  hand-rolled page sizes; a shared `MAX_CHANNEL_PAGE_SIZE` constant (or
  respecting the generated client's documented cap) would have prevented
  this class. Filed `TRACE3D-E2E-SMOKE-2026-07-02`.
- **The 853 deleted VideoStreamReference tombstones** dwarf the 63 live
  rows — reference-churn from repeated import passes leaves an order of
  magnitude more dead rows than live ones. Harmless today, but worth
  watching for query-shape costs.
- **The nested-DO-count convention is invisible in the UI**: the gallery
  shows direct children (1031/744/139) while the showcase inventory speaks
  in closure counts (3108/1845/144). Both are correct; nothing in the UI
  says which one you're looking at.
- The svdx plugin's 213-annotation materialisation is genuinely impressive
  in the UI — the annotation panel on the FileReference is the strongest
  "files become facts" demo moment in the whole showcase.
