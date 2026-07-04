---
stage: concept
last-stage-change: 2026-06-12
audience: frontend, UX, product, dispatcher
---

# UX audit 2026-06-12 — shapes-for-displays + canonical user journeys

**Auditor:** Core Tech & UX Auditor (Role 1) — worktree agent, scope `UX-SHAPES-JOURNEYS`
**Trigger:** operator complaint — *"The process of using shapes for displays seems convoluted."*
**Target:** `https://shepard.nuclide.systems` (live), viewport **3840×2160** (operator hardware)
**Auth:** authenticated Playwright walk as `flo / flo-demo` (OIDC; tolerant-login helper pattern from `e2e/tests/helpers/auth.ts`)
**Evidence:** `aidocs/agent-findings/screenshots/ux-2026-06-12/` (19 PNG) + re-runnable walk script `e2e/scripts/ux-walk-2026-06-12.mjs` + `e2e/scripts/ux-walk2-2026-06-12.mjs`
**Builds on:** `aidocs/frontend/01-user-research-findings-2024.md` (5-phase journey frame — all four journeys below are phase 5 *Working with the data*), `aidocs/agent-findings/ux-walk-2026-05-29.md`

Live-data caveat (honesty): the instance was re-seeded ~2026-06-10. Only the
**LUMEN** collection (17 DOs) + four `feat-*` showcases exist live. **MFFD,
BTKVS-docket, and Microsections collections are NOT currently seeded** on the
live instance — those three journeys are grounded in their seeds
(`examples/*/seed.py` + `SHOWCASE.md`) and in the page/component code, and are
flagged per-step where live verification was impossible.

---

## What I found

The operator's complaint is **two-thirds plumbing failure and one-third
genuine UX-architecture problem**. There are four user-facing paths into a
shapes-rendered view today, and on the live instance **three of the four are
hard-broken** — each by a different appId-vs-numeric-id mismatch — and the
fourth (the hardwired MFFD cross-track pane) only fires for one specific
annotation predicate. A user who tries "shapes for displays" today does not
experience a convoluted-but-working pipeline; they experience a sequence of
spinners, empty errors and raw 422 JSON, and reasonably conclude the whole
concept is impenetrable.

Underneath the breakage there *is* a real concept-count problem: the working
playground path requires **~14 interactions and ~9 substrate concepts**
(template, VIEW_RECIPE kind, focus appId, binding, channel 5-tuple,
DECLARED/DIRECT status, numeric TS container id, renderer name, colormap) to
get from "I have a DataObject with plottable data" to pixels. Grafana does the
equivalent in 2 interactions and 0 named concepts (open editor → suggested
visualization, live preview). The substrate (VIEW_RECIPE templates +
`POST /v2/shapes/render` + spatial-role channel annotations) is sound and
should not be discarded — the missing piece is a **discovery + auto-binding
layer** that turns "template × focus × container × window" assembly into a
single "View" button.

### The four current entry paths and their live status

| # | Path | Live status | Evidence |
|---|------|-------------|----------|
| 1 | DataObject detail → **Tools ▾ → "Render view"** (`toolsContext.ts:191-207`) | **Never usable.** Hidden on every LUMEN DO (no `attachedTemplateAppId`); on the one DO that has a template (`coupon-valid`) it appears but the template is a `DATAOBJECT_RECIPE` → backend 422, raw JSON in the alert | `06-tr004-tools-menu-4k.png` (no Render view), `07-coupon-tools-menu-4k.png` (present), `18-shapes-render-coupon-dataobject-recipe-4k.png` (422) |
| 2 | TimeseriesReference detail → **"Visualize in 3D"** → `ViewRecipeBuilderDialog` → `/shapes/render?roles=…` | **Unreachable.** The TS reference detail page never finishes loading under either id shape (see C1 below) | `08-tr004-tsref-detail-4k.png`, `08b-numericid-tsref-diag.png`, `08c-tsref-via-click-4k.png` |
| 3 | Tools → **Shapes render playground** (manual assembly) | **Breaks at the last step.** Template autocomplete + collection/DO pickers work; "Fetch bindings" works; the channel data fetch 404s because `render.vue` calls `/v2/timeseries-containers/{numericId}/channels` while the endpoint is now appId-keyed (see C2) | `17-shapes-render-from-dialog-4k.png` ("Channel 'x' returned 0 points"), `19-template-autocomplete-open-4k.png` |
| 4 | Collection detail → `CollectionCrossTrackViewPane` (auto-renders the V102 `Cross-ply TCP temperature` VIEW_RECIPE via `urn:shepard:afp:tcp-temperature-c` annotations) | Renders an empty hint on LUMEN (no AFP annotations). **This is architecturally the best pattern in the codebase** — annotation-driven, zero user configuration — but it is hardwired to one recipe + one pane | `frontend/components/context/CollectionCrossTrackViewPane.vue:10,51` |

(There is a fifth, kind-specific path: URDF FileReference → "Open 3D view",
V2CONV-B4 — not re-verified this walk; no URDF refs in the current seed.)

---

## The shapes-for-displays flow, diagnosed

### Click/concept count, measured live (path 3 — the only one that nearly works)

Starting point: researcher on TR-004's detail page, wants the gimbal-acc trace
in 3D.

| Step | Interaction | New concept the user must hold |
|---|---|---|
| 1 | Top-nav **Tools** | "views live under Tools, not on my data" |
| 2 | Tile **"Shapes render playground"** (tile subtitle says *"Render URDF / mesh / spatial-shape previews"* — wrong description, `toolsLanding.ts:54-58`) | "shape", "render playground" |
| 3–4 | Template autocomplete → pick | "template", "VIEW_RECIPE" (the options are full-paragraph description dumps, `19-template-autocomplete-open-4k.png`) |
| 5–8 | Collection picker → pick → DataObject picker → pick | "focus appId" (caption shows `Focus appId: 019eb02b-10b…`) |
| 9 | **Fetch bindings** | "binding", "role", 5-tuple columns (measurement/device/symbolicName/field), "status: DECLARED" chips |
| 10–12 | Find the **numeric TS container id** — requires leaving the page (Containers → timeseries → read the id off the URL or detail header) | "container", numeric Neo4j id (an implementation detail the frontend-v2-only rule bans from user surfaces) |
| 13 | Type it into "TS container ID (numeric)" (`render.vue:894-900`) | — |
| 14 | **Render 3D** | "renderer", "colormap" |
| — | **Result: error.** `Channel 'x' returned 0 points. Check the container ID and time window.` | (the actual cause is C2, not anything the user did) |

**≥14 interactions, ~9 concepts, 1 cross-page id-transcription task, ending in
a false-blame error.** For comparison the target experience (below) is 2
clicks and 0 named concepts.

### Root causes, ranked

1. **Plumbing: three independent id-shape breaks.** Findings C1, C2, M6
   below. Each is a seam where one layer moved to appId while a neighbour
   still speaks numeric Neo4j ids. The flow *feels* convoluted partly because
   every attempt dies somewhere different.
2. **Inverted defaults — the user assembles what the system already knows.**
   The TimeseriesReference knows its container appId *and* its time window
   (`payload.timeseriesContainerAppId`, `start`, `end`). The channels carry
   spatial-role annotations written by TS-AXIS-AUTO precisely so views could
   auto-configure (the annotation-driven-preselection principle,
   `project_annotation_preselection_principle`). The template registry knows
   which VIEW_RECIPEs exist and what roles they need. All three prefill
   sources exist in production data; the playground uses none of them
   unless arriving via narrow, currently-broken query-param handoffs.
3. **Entry-point gating contradicts the endpoint contract.** The Tools-menu
   "Render view" item gates on *any* `attachedTemplateAppId`
   (`toolsContext.ts:205-206`) while `POST /v2/shapes/render` accepts *only*
   `templateKind=VIEW_RECIPE` (`ShapesRenderRest.java:53-54,88-91`). Net
   effect on live data: the button is hidden exactly where it could work and
   shown exactly where it 422s.
4. **Machinery leaks into intent-level surfaces.** "VIEW_RECIPE", "Fetch
   bindings", "focusShepardId", "status=DECLARED", the channel 5-tuple table,
   the `PlaceholderImplStatus` footer citing TPL2b + design-doc paths
   (`render.vue:1146-1152`) — all visible to a regular researcher on a
   first-class Tools page. These are correct *power-user/debug* affordances
   in the wrong default layer.
5. **Genuinely necessary complexity is a small residue.** Choosing *which*
   view when several apply, and overriding channel bindings when annotations
   are absent/ambiguous, are real decisions. Everything else is accidental.

### The named bugs (file the rows)

**C1 — TimeseriesReference detail page is dead under both id shapes**
(CRITICAL). With appId route params, `timeseriesReferenceNumericId =
resolveNumericId(undefined, <uuid>)` → undefined → the v1 fetch never fires →
perpetual spinner (`…/timeseriesereferences/[timeseriesReferenceId]/index.vue:54-62`).
With numeric route params (which is what the DO refs table actually pushes —
`DataObjectDataReferencesTable.vue:47-56` builds the route from numeric
`item.id`), the v1 ref fetch succeeds but every v2 call 404s
(`GET /v2/collections/364325` → 404) so the collection layout + DO context
never hydrate, the sidebar shows "Couldn't load the DataObject tree", and a
red "Error while listDataObjects:" toast fires. The page also `$fetch`es
`/v2/timeseries-containers/{appId}/channels` **relative to the frontend
origin** → 401 (`index.vue:352-358`); it must go through the API base URL.
Consequence: charts, channel metrics, CSV export, **and the only "Visualize
in 3D" entry point** are unreachable for every timeseries reference on the
instance. This is the successor of BUG-COLL-APPID-ROUTE-007-REFPAGE — the
half that resolves the *reference's own* numeric id from an appId param (or
better: a v2 reference endpoint) was never built.

**C2 — `/shapes/render` channel fetches still numeric-keyed** (CRITICAL).
`render.vue:227-241` (`fetchChannelList`) and `:344-422` (`fetchBulkTrace`)
guard on `isFinite(Number(containerId))` and call
`/v2/timeseries-containers/{numericId}/channels` →
**404 on the live backend**, which now keys this endpoint on appId
(APISIMP-TSCONT-APPID-KEY — the TS ref page got the migration comment, the
render page didn't). Because the input field *requires* a numeric value and
the endpoint *requires* an appId, **no Trace3D render can succeed at all**,
from any entry point. The `ViewRecipeBuilderDialog.openTrace3D()` handoff
(`ViewRecipeBuilderDialog.vue:84-105`) likewise passes only the numeric
`containerId` query param.

**M1 — Render-view gate ≠ render contract** (MAJOR). Gate on
`templateKind === "VIEW_RECIPE"` of the attached template, not on mere
presence. And surface the 422 as a human sentence, not
`422 : {"error":"render not yet supported for templateKind=DATAOBJECT_RECIPE…"}`
(`18-…png`).

**M2 — "Create template for this DataObject/Collection" routes to a hard 404**
(MAJOR). `toolsContext.ts:130-136,216-222` push `/admin/templates`; no such
page exists — templates are a *fragment* of `/admin`
(`pages/admin/index.vue:97-101`). Live: full "Page not found" + nuxt error
`Page not found: /admin/templates` (`13-admin-templates-route-4k.png`).
Two of the six in-context tool actions dead-end.

**M3 — DO references table navigates by numeric id with `href="#"`**
(MAJOR). `DataObjectDataReferencesTable.vue:47-56,381,397` — violates the
frontend-v2-only rule (numeric id in a route push), breaks middle-click /
copy-link, and is the direct feeder into C1's broken numeric branch.

---

## Ideal flow proposal

**Target experience (researcher, basic mode):**

> Open TR-004 → a **"View"** split-button sits next to "Annotate" in the
> action bar → click it → menu lists *applicable* views ("3D trace — gimbal
> path", "Channel overview", "Table") each with a readiness dot → click one →
> rendered view, full-bleed, with the time window taken from the reference
> and channels taken from spatial-role annotations. **2 clicks, 0 new
> concepts, 0 typed ids.**

The shapes substrate stays exactly as is. What's added is a thin discovery +
auto-binding layer:

1. **`GET /v2/shapes/render/applicable?focusAppId=…`** (new, sibling of
   render): walks the focus DataObject's references + their containers'
   channel annotations + the VIEW_RECIPE catalogue and returns
   `[{templateAppId, title, renderer, readiness: READY|NEEDS_BINDING|NO_DATA,
   prefill:{containerAppId, startNs, endNs, roles…}}]`. This is the Grafana
   "suggested visualizations" move — Grafana's panel editor analyses the
   query result shape and proposes suitable visualizations with live preview
   ([Grafana panel editor docs](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/panel-editor-overview/));
   and it is the JupyterLab/IPython MIME-renderer move — the *data* declares
   what it is (`_repr_mimebundle_` / MIME type) and renderers self-select with
   zero user configuration
   ([JupyterLab file/output formats](https://jupyterlab.readthedocs.io/en/stable/user/file_formats.html)).
   Shepard's equivalent of the MIME type is **the semantic annotation**
   (`urn:shepard:spatial:axis`, `urn:shepard:afp:tcp-temperature-c`) — the
   annotate-once-views-auto-configure principle, finally cashed in.
2. **One `ViewMenuButton.vue`** mounted on DataObject detail, reference
   detail (when C1 is fixed), and TS container detail. Renders the applicable
   list; one click navigates to `/shapes/render` with the *full* prefill
   (template + focus + containerAppId + window + roles) so the page renders
   immediately — the existing SHAPES-V-PREFILL-1 auto-fetch path, completed.
   `READY` items render in one click; `NEEDS_BINDING` items open the existing
   `Trace3DEditChannelsDialog` first.
3. **`/shapes/render` becomes intent-first.** Header: "3D trace — TR-004
   (tr-004-sensors)". The bindings table, 5-tuple columns, status chips,
   container field and `PlaceholderImplStatus` footer collapse into an
   **"Advanced"** expansion panel (advanced mode = strict superset rule —
   nothing is removed, it is re-layered). The "TS container ID (numeric)"
   field dies entirely: container + window always arrive resolved from the
   reference (the "UI never asks for paths/URLs — pulls from references" rule
   applies to ids exactly the same way).
4. **`CollectionCrossTrackViewPane` generalises into the same mechanism**:
   instead of one hardwired recipe, the collection page asks
   `applicable?focusAppId=<collection>` and renders panes for collection-scoped
   VIEW_RECIPEs. The MFFD pane becomes content (a template), not code —
   which is the stated design intent of `aidocs/semantics/98` anyway.

**Opposing lenses (required paragraph).** The *Reluctant Senior Researcher*
(Role 9) would say: "Template, recipe, shape, binding — four words for 'show
me my plot'. Every one of them is a reason to go back to my Excel sheet. If
the View button isn't on the page where my data is, I will never find it —
and if it ever shows me the word DECLARED I'm done." The proposal answers
this by making the in-context button the *only* thing a basic-mode user sees.
The *API Minimalist* (Role 3) pushes from the other side: "Do not build a
parallel 'simple views' subsystem next to the shapes substrate — that's how
you get two view systems that drift. And be suspicious of a new
`/applicable` endpoint: could the client compute it from
`/v2/templates?kind=view` + the reference payload it already has?" The
answer: partially yes, but role-matching requires the channel-annotation
inventory of every referenced container (N+1 from the client; one graph walk
on the server), and Grafana's lesson is that suggestion quality lives
server-side where the data shape is known. The endpoint is a *projection* of
existing state, not new state, and the render path stays the single
substrate — so the minimalist constraint "one substrate, thin discovery"
is honoured. The minimalist also wins one concretely: the hardwired
cross-track pane should be subsumed by the generic mechanism, not kept as a
sibling.

---

## General usability findings

Severity: CRITICAL = blocks a primary task end-to-end; MAJOR = breaks a
common task or violates a standing repo rule on a shipped surface; MINOR =
friction/polish. Persona codes: RS = Reluctant Senior (R9), DN = Digital
Native (R10), IME = shop-floor/quality (R4), ALL = everyone.

| ID | Sev | Finding (evidence) | Suffers | Fix |
|----|-----|--------------------|---------|-----|
| UX612-C1 | CRITICAL | TS reference detail page dead under both id shapes; perpetual spinner, no error surface on the appId branch (`08*.png`); chart/export/Visualize-3D all unreachable | ALL | Resolve the reference via a v2 appId endpoint (`/v2/timeseries-references/{appId}` exists — annotations already use it, `index.vue:76-80`); fix the relative `$fetch`; make refs-table links push appId routes |
| UX612-C2 | CRITICAL | No Trace3D render can succeed: `render.vue` channel fetches numeric-keyed against the appId-keyed endpoint; error blames the user's container id (`17-…png`) | ALL | Carry `containerAppId` through `ViewRecipeBuilderDialog` query + `render.vue`; delete the numeric field (prefill from reference) |
| UX612-M1 | MAJOR | "Render view" gate ≠ 422 contract; raw JSON error shown (`07/18-…png`) | RS, ALL | Gate on `templateKind===VIEW_RECIPE`; friendly empty/error states |
| UX612-M2 | MAJOR | "Create template for this DataObject/Collection" → hard 404 `/admin/templates` (`13-…png`) | ALL | Route to `/admin#templates` fragment (and honour `newTemplate=1` prefill there) |
| UX612-M3 | MAJOR | Refs table: numeric-id route pushes + `href="#"` anchors (no middle-click/copy-link) `DataObjectDataReferencesTable.vue:47-56,381,397` | DN | Real `:to` links carrying appIds |
| UX612-M4 | MAJOR | Recurring console `TypeError: t?.toLocaleDateString is not a function` thrown twice per collection/DO page load from a date-rendering component (minified `BbvvbfCw.js`; likely a string fed to a `Date`-expecting prop) | ALL (silent) | Source-map triage; type the prop; add a unit test |
| UX612-M5 | MAJOR | Guaranteed 404 spam on every authed page: `GET /v2/users/{id}/avatar` + `GET /v2/data-objects/{appId}/publications` — masks real failures in console + network panel | DN, devs | Return 204/`null`-body 200 for absent avatar/publications, or feature-gate the calls |
| UX612-M6 | MAJOR | TS container page fires `GET /v2/timeseries-containers//channels` → 405 (empty appId on numeric route) (`phase5` log) | ALL | Resolve container appId before the channels call; accept appId route |
| UX612-m1 | MINOR | Tools tile for the render playground describes it as "Render URDF / mesh / spatial-shape previews" — wrong function (`toolsLanding.ts:54-58`, `12-…png`) | ALL | "Render views of your data (3D traces, heatmaps, URDF)" |
| UX612-m2 | MINOR | Template autocomplete options are full-paragraph description dumps — at 4K each option is a wall of text (`19-…png`) | RS | Title + one-line subtitle, truncated |
| UX612-m3 | MINOR | 5-tuple columns (measurement/device/location/symbolicName/field) as the *primary* labelling of channels on TS container + ref surfaces | RS | Lead with symbolicName + role chips; tuple in tooltip/advanced |
| UX612-m4 | MINOR | No container-level "Visualize" CTA on `/containers/timeseries/{id}` despite 28 channel annotations incl. roles (`15-…png`; repeat of 2026-05-29 m2) | ALL | Mount `ViewMenuButton` there |
| UX612-m5 | MINOR | `PlaceholderImplStatus` footers (TPL2b, design-doc paths, backlog rows) on user-facing Tools pages (`17-…png`) | RS | Show only in advanced/admin mode |
| UX612-m6 | MINOR | Hydration-mismatch console error on nearly every page | devs | Investigate the layout-level conditional rendering |
| UX612-m7 | MINOR | Raw fetch errors rendered verbatim in alerts (`422 : {...}`, `Channel 'x' returned 0 points…` blaming the user) | RS | Error-shape mapper: cause → human sentence + retry affordance |

Confirmed working (credit where due): LUMEN collection page is strong —
hero image, Cite-this-dataset, metadata-completeness meter, DO table with
per-kind ref count icons; DO detail with annotations chips, lab journal,
unified refs table; TS container chart + LTTB/Raw toggle + channel
annotations + referenced-by; `/tools` landing exists and is clean; template
autocomplete reads `/v2/templates` correctly; admin hub tile grid is
comprehensive.

---

## User journeys

All four are phase 5 ("Working with the data") of the canonical 5-phase
frame in `aidocs/frontend/01 §1`; the target journeys below are this doc's
extension of that frame, not a replacement.

### J1 — LUMEN: test engineer, post-campaign anomaly review (live-verified)

**Persona:** Sina, propulsion test engineer. **Trigger:** "TR-004 showed a
turbopump vibration spike; did TR-006 clear it?"

Current journey (walked live, screenshots 01–08):
1. Landing → Collections → LUMEN (3 clicks; list + detail render well).
2. TR-004 from sidebar tree or DO table (1 click). Annotations chips
   ("Vibration Anomaly", "Anomaly Detected") + lab-journal entry tell the
   story well. ✔
3. Relationships panel → "Anomaly Investigation — TR-004 Fuel Turbopump"
   successor link → investigation DO → TR-005/TR-006 chain navigable. ✔
4. **Dead end:** open `tr-004-sensors` to *see* the vibration spike →
   UX612-C1 spinner. The one step that needs pixels fails.
5. Fallback: TS container page via Containers nav shows all-channel chart —
   but mixed channels for the whole campaign, not TR-004's window, and
   finding the right container requires knowing its name.

**Friction:** the decisive evidence (the trace) is the only unreachable
artefact. **Target:** step 4 = TR-004 → "View" → "3D trace / channel chart
(window = reference)" → spike visible in 2 clicks; anomaly annotation chip
deep-links the chart at t=8s.

### J2 — MFFD: process + quality engineer, Q1 ply-5 anomaly → NDT → rework (seed-grounded; not seeded live)

**Persona:** Jonas, AFP process engineer; Greta, quality engineer.
**Trigger:** "Consolidation-force drop + TCP temp spike at ply 5 — was the
rework cleared?"

From `examples/mffd-showcase/` (12-step process DAG, Q1 anomaly → NDT FAIL →
Rework → recheck PASS) + shipped MFFD panes: the structural surfaces exist —
`MaterialBatchTracePane` on DO detail (`dataobjects/[dataObjectId]/index.vue:995`),
`mffd-material-batch-view-shape` + `MFFDLayerOverview` + `Cross-ply TCP
temperature` VIEW_RECIPEs are in the live template registry,
`/admin/mffd-process-chain` exists. Current journey on a seeded instance:
process-chain navigation works via Predecessor/Successor relationship panels
(same shape as J1); the layer-overview view depends on path-3 rendering →
currently dead (C2). Greta's audit question ("show me the NDT FAIL → rework →
PASS chain with the force trace") needs three separate page visits + the
broken render path. **Target:** AFP-layup DO → "View" → "MFFD layer overview"
(the seeded VIEW_RECIPE, auto-bound via `urn:shepard:mffd:layer` +
`urn:shepard:afp:tcp-temperature-c` annotations); NDT DO carries a PASS/FAIL
status chip that links its rework loop.

### J3 — BTKVS docket: composites lab engineer, docket entry + retrieval (seed-grounded; not seeded live)

**Persona:** Nils, C/SiC fabrication engineer. **Trigger:** "Plate I123
finished siliconization — record the step + its post-analysis, then pull the
full docket for the group lead."

From `examples/btkvs-docket-showcase/SHOWCASE.md`: one docket = 12 DOs + 11
StructuredDataReferences (5 process steps × post-analysis sub-shapes); the
group's own `templates_api.py` already does **form-based docket entry against
templates** — this showcase is the strongest validator of the
template-as-form line. Current UI journey: creating the step DO from a
DATAOBJECT_RECIPE template works (template-driven create), but the
post-analysis lives in StructuredDataReference JSON — entry and review are
raw-JSON surfaces, and the "render the docket as a one-page record" need maps
exactly onto a VIEW_RECIPE ("Disposition record" STRUCTURED_RECIPE is already
in the registry) with no working render path (C2) and no entry point (M1
gate). **Target:** docket DO → "View" → "Docket record" rendered as the
structured one-pager; "Add step" pre-filled from the process-step template.

### J4 — Microsections: lab analyst, FVF inspection (seed-grounded; not currently seeded live)

**Persona:** Maren, materialography analyst. **Trigger:** "Is PH2940-04's
fiber-volume-fraction in range? Compare carbon vs flax interpretation on
PH2940-01."

From `examples/microsections-showcase/`: 8 samples × (TIFF micrograph FR1b
singleton + notebook FR1b singleton). Journey: collection → sample DO →
singleton file ref → image preview + "Open in Jupyter" launch
(`jupyterLaunchUrl`, refs table `:529`). The singleton path (no pointless
file picker) is the right shape per the FR1b rule. Friction: micrograph
preview at 4K and FVF results returning as annotations are the J2-plugin
acceptance criteria — the comparison question ("PH2940-01 carbon vs flax")
is again a *view* over two notebook-result annotation sets. **Target:**
sample DO → image preview inline + FVF annotation chips; collection-level
"View → FVF overview" small-multiples (one VIEW_RECIPE, the Grafana-style
suggested view for this collection).

**Cross-journey observation:** all four journeys converge on the same missing
affordance — an in-context, auto-bound "View" of the entity the user is
already standing on. That is precisely the shapes-for-displays layer that is
broken/buried today. Fixing it is not a Tools-page nicety; it is the last
mile of every showcase's story.

---

## Top 5 changes by effort × user value

1. **Fix the three id-shape breaks (C1, C2, M6) + M3 link shapes.** Days, not
   weeks; unblocks every render path and the headline complaint. Without
   this, every other UX investment is invisible.
2. **`GET /v2/shapes/render/applicable` + `ViewMenuButton` on DO / reference
   / container detail.** The single highest-leverage UX addition: 14
   interactions → 2 clicks; the annotation-preselection principle becomes
   user-visible for the first time. (Grafana suggested-visualizations +
   JupyterLab MIME-dispatch patterns, cited above.)
3. **Fix the gates + dead routes (M1, M2).** Hours: VIEW_RECIPE-kind gate,
   `/admin#templates` fragment route, human 422 message, "no views yet —
   create one" empty state.
4. **Intent-first `/shapes/render`:** title says what is being viewed of
   what; bindings/5-tuple/status/impl-footer behind "Advanced"; container +
   window always reference-resolved (delete the numeric field).
5. **Error-surface hygiene (m7 + M5):** map raw fetch errors to human
   sentences; silence the guaranteed avatar/publications 404s so real
   failures are findable again.

---

## What surprised me

- **The flagship dataset has zero working path to a rendered view.** Not
  "convoluted" — *zero*. Three independent id-migration seams each broke a
  different third of the flow, and no e2e test walks DO → reference → render
  (the existing specs stop at page-loads).
- **The best UX in the whole flow is the one nobody routed to:** the
  annotation-driven `CollectionCrossTrackViewPane` renders with literally
  zero user input. The architecture for the ideal flow already exists
  in-tree; it's hardwired to one MFFD recipe instead of generalised.
- **The Tools-menu items most likely to be clicked by a curious user
  ("Create template…") 404.** The in-context-first rule was honoured in
  shape but never live-verified end-to-end.
- **`href="#"` on data links in 2026.** The refs table — the single most
  important navigation surface in the app — defeats middle-click,
  copy-link-address, and the browser's back-forward cache.
- **How good the non-shapes surfaces have become.** Cite-this-dataset,
  metadata completeness, lab journal, unified refs table with per-kind
  chips: the collection/DO read path is genuinely strong. The contrast with
  the view path is what makes the complaint land so hard.

---

## External patterns cited

- Grafana panel editor — suggested visualizations from data shape + always-on
  preview + progressive disclosure of options:
  [Panel editor overview](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/panel-editor-overview/),
  [Panels and visualizations](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/).
- JupyterLab MIME renderers / IPython `_repr_mimebundle_` — data declares its
  type, renderers self-select, zero configuration:
  [File and output formats](https://jupyterlab.readthedocs.io/en/stable/user/file_formats.html),
  [jupyter-renderers](https://github.com/jupyterlab/jupyter-renderers).

## Backlog rows to file (aidocs/16)

`UX612-C1` (TS ref page id-shape), `UX612-C2` (render channels appId),
`UX612-M1`…`M6`, `UX612-m1`…`m7` as above; `SHAPES-APPLICABLE-1`
(discovery endpoint + ViewMenuButton); `SHAPES-RENDER-INTENT-1`
(progressive disclosure); `CROSSTRACK-GENERALIZE-1` (pane → generic
applicable-views mechanism).
