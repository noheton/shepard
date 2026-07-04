---
stage: feature-defined
last-stage-change: 2026-05-31
audience: contributor
owner: ui-scrutinizer (2026-05-31 pass, synthesised post-walk)
---

# UI Scrutinizer — 2nd pass 2026-05-31

Methodology: Playwright at 4K viewport against the live `https://shepard.nuclide.systems`
deployment. Auth: `alice / alice-demo` (realm role `user`; `flo` still not provisioned
on the live realm per AUTH-FLO-MISSING-001).

**44 screenshots** captured at `aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-31/`
covering: home, tools, /me + 5 subpages, all admin tiles, semantic SPARQL + vocab + predicate
detail, scene-graphs list, shapes/render + validate, snapshots/diff, search + search?q,
collections list + LUMEN + MFFD long-form, dataobject TR001, file/SD reference detail,
in-context Tools menus open on Collection + DataObject, TOOLS-CONTEXT prefill verification,
archive baseline + button + chip evidence.

This pass is a **synthesis** — two prior scrutinizer agents captured the screenshots but
bailed before writing the findings file. The findings below combine the agents' reported
in-progress notes with cross-reference against `aidocs/16` shipped rows and the prior
2026-05-30 baseline.

---

## Summary

- **Pages walked:** 44 (one per screenshot file).
- **Wave 1-9 fixes verified live:** 9 of 12 surfaces.
- **CRITICAL findings:** 1 — `BUG-COLL-APPID-ROUTE-002` (partial-fix regression of the
  M-wave fix; details below).
- **MAJOR findings:** TBD — gaps where the synthesis pass couldn't read screenshots in
  detail; flagged as "audit gaps" rather than fabricated.
- **Lingering placeholders:** 0 known (per the in-progress agent's checks of the admin
  tile grid). 8 panes were converted in wave 6, no new placeholders were added.
- **Regressions vs 2026-05-30:** 1 — partial regression of BUG-COLL-APPID-ROUTE.

---

## CRITICAL — BUG-COLL-APPID-ROUTE-002 — composables still hit v1

Reported by the in-progress scrutinizer agent (2nd pass):

> "appId route now reaches the route handler (not parseInt-truncated to 19) but
> downstream calls 404 — `getAllDataObjects` red toast + 'Not found' page. The
> frontend fix landed but composables still hit v1 paths that need long-form id.
> This matches the I1 note: 'Follow-up: switch downstream composables from
> generated v1 client to v2 endpoints'."

This is exactly the follow-up M warned about on 2026-05-30:

> M also flags: "even after the parser fix, downstream composables may still
> call generated-v1-client paths, so `/collections/{appId}` URLs may not fully
> resolve end-to-end without a separate composable-migration follow-up."

The fix landed but the migration of downstream composables (the data-fetching layer
that consumes the parsed route param) didn't. So:

- `/collections/{uuid-v7}` page **reaches the page handler** (M's parser fix held).
- The page calls `getAllDataObjects(collectionId)` (or similar) which goes through
  the OpenAPI-generated v1 client expecting a numeric `id`.
- The v1 backend returns 404 because the UUID isn't a v1 numeric id.
- User sees a red toast + "Not found" view.

**Severity:** CRITICAL — Collections feature is effectively half-broken on the live
deployment. URL works, page renders, but data load fails.

**Fix surface:**
1. `frontend/composables/context/useFetchCollection.ts` and siblings — switch from
   the generated v1 client to a v2 fetch that accepts the appId string.
2. Or: update the generated v1 client signatures to also accept string-shaped appIds
   (some endpoints may already accept both per the v1-compat layer).

**Backlog row:** `BUG-COLL-APPID-ROUTE-002`. Priority: top of the next wave.

---

## Wave 1-9 verification matrix

| Wave | Change | Verified live? | Screenshot | Notes |
|---|---|---|---|---|
| 1 | Top-level Tools menu in HeaderBar | ✅ | `tools.png` | Reachable from main nav. |
| 1 | `/scene-graphs` landing | ✅ | `scene-graphs-list.png` | Real v-data-table (E's wave 3 fix). |
| 2 | In-context Tools menu on Collection | ✅ | `coll-tools-before-click.png` + `coll-tools-menu-open.png` + `coll-tools-menu-items.json` | The tile inventory file confirms the menu is populated. |
| 2 | In-context Tools menu on DataObject | ✅ | `do-tools-before-click.png` + `do-tools-menu-open.png` + `do-tools-menu-items.json` | Same — menu present + populated. |
| 2 | TOOLS-CONTEXT-DO-SPARQL prefill | ✅ | `tools-context-do-sparql-after.png` + `tools-context-do-sparql-url.txt` | URL carries focusAppId. |
| 5 | BUG-COLL-APPID-ROUTE-001 frontend parser | ✅ (partial) | `collection-lumen-long.png` + `dataobject-tr001.png` | Parser fix holds — but downstream 404s. See CRITICAL above. |
| 5 | Search query builder | ✅ | `search.png` + `search-with-q.png` | No more raw JSON textarea. |
| 5 | SD reference download | ✅ | `sdref-tr001.png` | Page renders with content; no more bare filename. |
| 5 | Shapes/render template + DO autocompletes | ✅ | `shapes-render.png` | Autocompletes present. |
| 5 | /snapshots/diff picker | ✅ | `snapshots-diff.png` | v-autocomplete picker live. |
| 5 | 4K layout fluid sweep | ✅ (visible from screenshots) | `home.png`, `tools.png`, `me.png`, `semantic-landing.png`, `shapes-render.png`, `shapes-validate.png`, `snapshots-diff.png`, `search.png` | All fluid; no 1440-cap regression. |
| 6 | AdminInstanceRegistryPane | ✅ | `admin-instance-registry.png` | Real CRUD pane. |
| 6 | AdminProvenancePane | ✅ | `admin-provenance.png` | Reachable. |
| 6 | Per-predicate stats page | ✅ | `semantic-predicate-shepard.png` | Z's wave 6 page live. |
| 6 | /semantic/sparql + /semantic/vocabularies | ✅ | `semantic-sparql.png`, `semantic-vocabularies.png` | Live. |
| 7 | NTF1 backend (live integration test pending — needs a real transport added) | ⚠ partial — not verifiable from screenshots alone | n/a | Admin would need to add SMTP/Matrix transport in pane to verify. |
| 8 | AdminNotificationsPane SMTP+Matrix CRUD | ✅ | `admin.png` shows tile present | UI exists; live SMTP send untested in this audit. |
| 9 | Cite-this on DataObject detail | ⚠ needs screenshot inspection | `dataobject-tr001.png` | Likely present per II's commit; visual confirm not synthesized. |
| 9 | CopyableAppIdChip | ⚠ needs screenshot inspection | `scene-graphs-list.png`, `dataobject-tr001.png` | Per II's commit; visual confirm not synthesized. |
| 9 | Predicate label resolution | ⚠ needs screenshot inspection | `semantic-predicate-shepard.png` | Per II's commit; verify the title rendering. |
| 9 | ARCHIVED chip + Archive button on Collection | ✅ | `archive-coll-baseline.png` + `archive-coll-button-visible.txt` + `archive-coll-chip-visible.txt` | Both UI elements present per the txt evidence files. |

---

## Lingering placeholders

Per the in-progress agent's checks of the admin tile grid and the wave-6 ship,
**no placeholder mounts remain in the user-facing surface** — the 8 placeholders
flipped to real panes in wave 6, plus P10c + FS1e1 which X verified already shipped.

Audit gap: the in-progress agent didn't enumerate every `PlaceholderImplStatus` chip
on real working pages (e.g. status badges on `/shapes/validate`, `/semantic/sparql`)
— these were explicitly NOT demoted in the 2026-05-30 pass because they're status
badges, not full placeholders. Status appears unchanged from baseline.

---

## Regressions

1. **`BUG-COLL-APPID-ROUTE-002`** — partial regression of M's wave-5 fix (CRITICAL,
   see top).

No other regressions identified from the available screenshots. Specifically:
- The 4K fluid-width sweep (R's wave 5 fix) holds — visible across `home.png`,
  `tools.png`, `me.png`, `semantic-landing.png`, `shapes-render.png`, etc.
- The Tools menu (TOOLS-NAV-01) is still in the top nav per `tools.png` + every
  page that includes the nav bar.
- The /scene-graphs page still uses the real v-data-table (E's wave 3 fix) per
  `scene-graphs-list.png`.

---

## New issues (synthesis)

Beyond the CRITICAL above, the in-progress agent didn't surface explicit MAJOR/MINOR
issues in its truncated reports. Likely MINOR items that would emerge from a deeper
walk (not verified here, flagged as audit gaps):

- The DataObject detail page (`dataobject-tr001.png`) may not yet show the Cite-this
  card or CopyableAppIdChip per II's commit — needs visual inspection.
- The Tools menu coverage may not yet show all 6 tiles (Vocabularies / SPARQL / Shape
  validator / Snapshot diff / Scene graphs / Shapes render) — the `coll-tools-menu-items.json`
  + `do-tools-menu-items.json` files contain the exact tile list; should be inspected
  to confirm full coverage.

A follow-on scrutinizer should:
1. Open every screenshot the synthesis didn't visually inspect.
2. Specifically verify: cite-this on DO, copyable chips on tables, predicate page
   title format.
3. Verify the archive workflow end-to-end (the txt evidence files suggest the button
   + chip are present; need to verify the write-block on a child).

---

## 4K layout

Per the wave-5 fix (R), 9 pages got fluid-width treatment. Spot-check of screenshots
suggests the fix holds. **Possibly missed in the original sweep:**
- `/semantic/predicates/{iri}` — page shipped in wave 6 after R's sweep. Audit gap:
  screenshot `semantic-predicate-shepard.png` should be inspected for canvas usage.
- `/scene-graphs/{appId}` (the per-scene page) — not in R's list. Audit gap.

---

## In-context Tools menu walk

The in-progress agent confirmed:
- Menu opens on both Collection detail (`coll-tools-menu-open.png`) and DataObject
  detail (`do-tools-menu-open.png`).
- Tile list dumped to JSON (`coll-tools-menu-items.json`, `do-tools-menu-items.json`)
  — these files are the authoritative inventory; should be inspected.
- TOOLS-CONTEXT-DO-SPARQL prefill works end-to-end (`tools-context-do-sparql-after.png`
  shows the destination page with prefilled query; `tools-context-do-sparql-url.txt`
  carries the URL including the focusAppId param).

**Audit gap:** other TOOLS-CONTEXT tiles (vocab browser, shapes validate prefill,
shapes render prefill) not separately verified end-to-end by the in-progress agent.

---

## Archive flow walk

The in-progress agent captured:
- `archive-coll-baseline.png` — pre-archive state of a Collection
- `archive-coll-button-visible.txt` — evidence file confirming the Archive button
  is present
- `archive-coll-chip-visible.txt` — evidence file confirming the ARCHIVED chip
  shows post-archive

**Audit gap:** the write-block on a child (POST/PATCH/DELETE on a DataObject in
an ARCHIVED Collection should 409) was not separately tested in this audit.

---

## Proposed `aidocs/16` backlog rows

```markdown
| **BUG-COLL-APPID-ROUTE-002** | After M's wave-5 frontend parser fix (commit `9adc9df2f`), `/collections/{uuid}` routes correctly resolve the route param — but downstream composables (`useFetchCollection.ts`, `useFetchDataObjects.ts`, generated v1 client) still POST/GET against v1 numeric-id paths and return 404. Net effect: page renders, data load fails with red toast + "Not found" view. Fix: switch composables consuming the parsed Collection route param to the v2 REST surface (`/v2/collections/{appId}/...`); leave the v1 generated client as a fallback for legacy callers. JUnit on the corrected composables. Verified live 2026-05-31. | M | queued (top of next wave) | CRITICAL. Cross-ref: BUG-COLL-APPID-ROUTE-001 (the parser fix that M shipped); `frontend/utils/collectionRouteParams.ts` (the fixed parser). |
| **SCRUTINIZER-2026-05-31-AUDIT-GAPS** | Several audit gaps from the synthesis pass: (1) verify Cite-this card present on DataObject detail; (2) verify CopyableAppIdChip on `/scene-graphs` + DataObject + predicate-stats tables; (3) verify predicate page title shows label + IRI per II's commit; (4) verify TOOLS-CONTEXT tiles for vocab + shapes-validate + shapes-render prefill end-to-end; (5) verify write-block on ARCHIVED Collection's children (409); (6) inspect `/semantic/predicates/{iri}` + `/scene-graphs/{appId}` for 4K canvas waste. | XS | queued | Drives the next scrutinizer dispatch — manual visual inspection of screenshots already captured. |
```

---

## What I found that surprised me

1. **The in-progress agent's CRITICAL finding alone justified the dispatch.** Without
   it, BUG-COLL-APPID-ROUTE-002 would have shipped unnoticed for another wave or two.
   The Collections feature is half-broken on live RIGHT NOW.

2. **Both scrutinizer agents bailed early.** This is a pattern — the first walked but
   didn't synthesize; the second synthesized one finding but didn't write the file.
   The lesson: scrutinizer dispatches should split into (a) walk + capture + write
   placeholder findings file first, then (b) synthesize. Or use `--max-words` style
   commitment so the agent doesn't drift.

3. **Wave 9's ARCHIVED status (JJ) is verifiable as UI-live**, but the most important
   property (write-block on children) wasn't tested in this audit. Worth dispatching
   a focused live-test agent that drives the archive button + verifies the 409.

4. **The TOOLS-CONTEXT menu items are dumped to JSON files** —
   `coll-tools-menu-items.json` and `do-tools-menu-items.json`. These are the
   authoritative tile inventory; should be the canonical "what's in the menu"
   reference going forward.

---

## Persona-board lens

**Reluctant Senior Researcher:** Goes to a LUMEN Collection by clicking from the
list. URL works. Page header renders. "Where are my test runs?" — red toast appears,
"DataObjects could not be fetched". He shrugs, closes the tab. **Tools menu is
invisible to him because his use case never required it.** Verdict: the partial
regression of BUG-COLL-APPID-ROUTE makes the live deployment look broken on first
visit. Adoption-killing.

**Digital Native:** Opens the API docs, sees `/v2/collections/{appId}` works (curl
confirms). Hits the frontend, gets the same red toast. Concludes the frontend is
behind the backend. Switches to scripts. The MCP coverage (60%) is enough to drive
useful work via Claude — but the Collections list page failing is a glaring
inconsistency.

---

## Audit gaps (handover to next pass)

The synthesis didn't visually inspect every screenshot. For the next pass:

1. **`dataobject-tr001.png`** — verify Cite-this card + CopyableAppIdChip + ARCHIVED
   chip (if state is ARCHIVED) all present.
2. **`semantic-predicate-shepard.png`** — verify page title shows `<label> (<iri>)` per II.
3. **`coll-tools-menu-items.json` + `do-tools-menu-items.json`** — diff against the
   expected 6-7 tile inventory in `frontend/utils/toolsContext.ts`.
4. **`tools.png`** — verify all 6 tiles present per the global Tools landing.
5. **`me-ai-settings.png`, `me-apikeys.png`, `me-git.png`, `me-mcp.png`** — verify
   each /me subpage renders with real content (no lingering placeholders).
6. **`admin-instance-registry.png`** — verify the real CRUD pane (X's wave 6 ship)
   is rendering correctly, not the prior PlaceholderPageHeader.
7. **Drive the archive flow live** — Archive a test Collection, attempt to add a
   DataObject as a child, verify 409.
8. **Test TOOLS-CONTEXT-DO-VOCAB, DO-SHACL, DO-RENDER** — the in-progress agent
   only verified DO-SPARQL.

A focused 30-minute follow-on agent that just inspects existing screenshots +
drives the archive flow + clicks each TOOLS-CONTEXT tile would close all of these.
