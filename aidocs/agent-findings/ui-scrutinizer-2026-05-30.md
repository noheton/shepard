---
stage: feature-defined
last-stage-change: 2026-05-30
audience: contributor
owner: ui-scrutinizer agent
---

# UI Scrutinizer — full-pass audit 2026-05-30

Methodology: Playwright at 4K viewport (3840×2160) against the live
`https://shepard.nuclide.systems` deployment. Auth: `alice / alice-demo`
(realm role `user`, NOT instance-admin). The brief asked for `flo /
flo-demo` but **that user is not provisioned on the live demo realm**
(`shepard-demo-realm.json` has `flodemo` but the imported realm only
exposes `alice` / `bob` / `admin`). Same `user` realm role, so the
audit scope is unchanged — finding `AUTH-FLO-MISSING-001` below.

Output: 40 screenshots at
`/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30/`.
Three Playwright phases:

1. `ui-scrutinizer-2026-05-30.spec.ts` — main 28-page walk.
2. `ui-scrutinizer-2026-05-30-p3.spec.ts` — long-form-id deep dive into
   LUMEN + MFFD (because `/collections/{appId}` is broken — finding
   `BUG-COLL-APPID-ROUTE-001` below).
3. `ui-scrutinizer-2026-05-30-p4.spec.ts` — reference and container
   detail pages, signin.

---

## Summary

* **Pages walked**: 40 distinct screenshots, every page from the brief
  except `/admin/sparql-playground` (admin-only, behind the
  `instance-admin` gate — empty-state per `ADMIN-EMPTY-STATE-242-UX`).
* **Placeholders found**: **18 placeholder mounts** across the
  authenticated surface, sourced from
  `frontend/components/common/placeholder/placeholderRegistry.ts`.
  Two of them (`admin-instance-registry`, `me-ai-settings`) are
  unmistakably the full placeholder shape (PlaceholderPageHeader +
  PlaceholderImplStatus + PlaceholderRestDump); the others are the
  thinner PlaceholderImplStatus status chip mounted on real working
  pages.
* **Issues found**: **23 distinct issues** across CRITICAL / MAJOR /
  MINOR. The biggest is `BUG-COLL-APPID-ROUTE-001` — the entire
  `/collections/{appId}` URL family is broken because
  `parseCollectionId()` calls `parseInt()` on the UUID-v7 appId,
  truncating it to the leading numeric prefix (e.g. `019e6ffc-...` →
  `19`). **Fixed 2026-05-31** in worker-agent-a2b9b4f1 — parser
  returns raw string, call-sites cast at boundary, 13 Vitest cases.
  The audit's secondary `BUG-V2-COLL-LIST-404` was a **false positive**:
  the curl prefix `/shepard/api/v2/...` is the wrong base (v2 mounts
  directly at `/v2/...`); `/v2/collections` returns 401 (endpoint
  present and auth-gated). See §Issue catalogue I2 for the strike-through.
* **Alpha demotions proposed**: **8 features** in `aidocs/44` should
  drop from `✓ shipped` (or `⚙ BE ✓ / UI pending`) to `alpha` because
  their UI surface is still a placeholder stub.
* **4K layout issues**: massive empty whitespace on **every centered
  card-shaped page** (home, tools, /me, /semantic, /shapes/render,
  /shapes/validate, /snapshots/diff, /search). The layout caps at
  roughly 1440-1920px and leaves ~50% of the 4K canvas grey. Not a
  bug per se, but at 4K the perception is "page is broken / empty"
  on first impression.

**Verdict.** The placeholder-kit roll-out from 2026-05-24 did its
job: every backend-shipped feature has at least a stub mount. But
the kit was meant as a floor, not the ceiling — and several features
labelled `✓ shipped` in `aidocs/44` still wear the placeholder stub
as their entire UI surface. The bigger problem this walk surfaced
is `BUG-COLL-APPID-ROUTE-001`: every URL the platform shares on
chat / docs / bookmark — `/collections/{appId}` — is broken end-to-end
for any caller who has a UUID-shaped appId rather than the legacy
numeric long-form id. The audit deep-dives only succeeded by
switching back to the long-form id path. This is the regression
that needs to ship a fix first; everything else is grading on the
curve.

---

## Placeholder catalogue

Source of truth: `frontend/components/common/placeholder/placeholderRegistry.ts`
(EXPECTED_PLACEHOLDER_COUNT = 18 entries). Cross-referenced against
`aidocs/44-fork-vs-upstream-feature-matrix.md` for the parent-feature
shipped status.

| # | Page route | Component used | Slug | Parent feature in `aidocs/44` | Current status | Proposed status | Backlog row to file |
|---|---|---|---|---|---|---|---|
| 1 | `/me#ai-settings` | PlaceholderFragmentPane (full shape: PageHeader + ImplStatus + RestDump) | `ai-settings` | AI1a — per-user LLM provider config | `designed` (BE not shipped) | already `designed` | already filed (AI1a) |
| 2 | `/admin#ai-config` | PlaceholderFragmentPane (full shape) | `ai-config` | AI1a — instance LLM fallback | `designed` | already `designed` | already filed (AI1a) |
| 3 | `/admin#notifications-admin` | PlaceholderFragmentPane (full shape) | `notifications-admin` | NTF1 — Notification transports admin | `partial` (BE shipped, transports config UI pending) | **`alpha` (UI is placeholder)** | `UI-NTF1-ADMIN-PANE` |
| 4 | `/admin#instance-admins` | PlaceholderFragmentPane (full shape) | `instance-admins` | ADM-MANAGE — instance-admin role grant UI | BE `shipped`, UI placeholder | **`alpha`** | `ADM-MANAGE-UI` |
| 5 | `/admin#users-orcid` | PlaceholderFragmentPane (full shape) | `users-orcid` | ADM-USR-ORCID — admin override of user ORCID | BE `shipped`, UI placeholder | **`alpha`** | `ADM-USR-ORCID-UI` |
| 6 | `/admin#users-git` | PlaceholderFragmentPane (full shape) | `users-git` | ADM-USR-GIT — admin issue/rotate user git creds | BE `shipped`, UI placeholder | **`alpha`** | `ADM-USR-GIT-UI` |
| 7 | `/admin#sql-timeseries` | PlaceholderFragmentPane (full shape) | `sql-timeseries` | P10c — runtime caps for SQL-over-HTTP bulk reads | BE `shipped`, admin pane pending | **`alpha`** | `P10c-UI` (already in `aidocs/44` as "admin config UI pending") |
| 8 | `/admin#file-migration` | PlaceholderFragmentPane (full shape) | `file-migration` | FS1e1 — file-storage migration trigger | BE `shipped`, UI pending | **`alpha` (or "⚙ BE / UI pending" status flag — already used)** | `FS1e1-UI` (already in `aidocs/44`) |
| 9 | `/admin#backup` | PlaceholderFragmentPane (full shape) | `backup` | PG-COLLAPSE-002 — backup configuration | `designed` (BE not shipped) | already `designed` | already filed (PG-COLLAPSE-002) |
| 10 | `/admin#ontology-alignment` | PlaceholderFragmentPane (full shape) | `ontology-alignment` | TPL3a-lite | BE `shipped`, admin pane is a placeholder + RestDump | **`alpha`** | `TPL3a-UI-FULL` |
| 11 | `/admin/instance-registry` (top-level route) | PlaceholderPageHeader + ImplStatus + RestDump | `instance-registry` | FE-PROV-INSTANCE-REGISTRY | BE `shipped`, UI placeholder | **`alpha`** | `FE-PROV-INSTANCE-REGISTRY-UI` |
| 12 | `/shapes/validate` | PlaceholderImplStatus only | `shapes-validate` | SHAPES-V — SHACL validation playground | BE `shipped` + real UI (paste-Turtle textarea + Validate button) | leave `shipped` — the placeholder is the **status chip only**, not the surface | n/a |
| 13 | `/shapes/render` | PlaceholderImplStatus only (also: unknown-renderer fallback) | `shapes-render` | TPL2b — VIEW_RECIPE projection endpoint | BE `shipped` + real UI (template appid + DataObject appid + Fetch bindings) | leave `shipped` for the chip; but see ISSUE `UI-SHAPES-RENDER-PICKERS-001` — the inputs are bare text fields | n/a (chip is fine; pickers are an issue, not a placeholder) |
| 14 | `/snapshots/diff` | PlaceholderImplStatus only | `snapshots-diff` | SNAP-DIFF — Collection snapshot diff viewer | BE `shipped` + real UI (raw JSON output only, structured viz queued) | leave `shipped` for the chip; ISSUE `UI-SNAP-DIFF-STRUCTURED-001` | n/a (chip ok) |
| 15 | `/semantic/sparql` | PlaceholderImplStatus only | `semantic-sparql` | N1f — SPARQL proxy | BE `shipped` + real UI (CodeMirror-style textarea + Run + results table) | leave `shipped` for the chip; ISSUE `UI-SPARQL-EDITOR-001` — "full editor / autocomplete queued" | n/a |
| 16 | `/semantic/predicates/{predicateIri}` | PlaceholderImplStatus only | (route) | SEMA-V6-UI-FOLLOWUP — per-predicate usage stats | BE `partial`, UI is a thin shell + SPARQL-fallback hint | **`alpha`** — page renders the IRI + an "Open in SPARQL playground" button + chip; that's it | `SEMA-V6-PRED-UI` |
| 17 | `/containers/timeseries/{containerId}` → "Channel Annotations" expandable | PlaceholderFragmentPane | `ts-channel-annotations` | TS-SEMANTIC-REST | BE `shipped` (REST + MCP tools, 5 unit tests) — UI is collapsed placeholder | **`alpha` for the UI** (BE remains `shipped`); `aidocs/44` row should call out "UI ⚠ placeholder" | `TS-CHANNEL-ANNOTATIONS-UI` |
| 18 | `/admin#sparql-playground` (admin-only) | nested in `SparqlPlaygroundPane.vue`, PlaceholderImplStatus only | `sparql-playground` (admin) | N1f admin pane | shipped + real UI (admin-only); the chip is "Backend live (N1f). Admin pane shipped in #64. Full editor / autocomplete queued." | leave `shipped` for chip | n/a (admin-only, not in alice's scope) |

**Net placeholder demotions** (rows 3, 4, 5, 6, 7, 8, 10, 11, 16, 17): **10
features should drop from `shipped` / `BE ✓ UI pending` to `alpha`** in
`aidocs/44`. Rows 1, 2, 9 are already explicitly `designed`.

---

## Issue catalogue

| # | ID | Page route | Severity | What's broken | Repro steps | Suggested fix |
|---|---|---|---|---|---|---|
| I1 | `BUG-COLL-APPID-ROUTE-001` | `/collections/{appId}` | **CRITICAL** | The frontend `parseCollectionId()` (`frontend/utils/collectionRouteParams.ts:39-47`) calls `parseInt(routeParams.collectionId)` on the UUID-v7 appId. `parseInt("019e6ffc-89a4-76b5-8dbb-15888646a904")` returns `19` (JS parseInt stops at the first non-digit). The page then fetches `/shepard/api/collections/19`, gets 404, displays the toast `"ID ERROR - Collection with id 19 is null or deleted"`. | Sign in as alice → visit `https://shepard.nuclide.systems/collections/019e6ffc-89a4-76b5-8dbb-15888646a904` (LUMEN's live appId). See screenshot `collection-lumen-live.png`. | `parseCollectionId` must accept appId-shaped strings *and* numeric long-form. The `/v2/` chain (L2d) provides `/v2/collections/{appId}` natively; the frontend should branch on shape (UUID-regex → v2 endpoint; numeric → legacy v1). Also: clear the cached numeric mapping cache between sessions. |
| I2 | ~~`BUG-V2-COLL-LIST-404`~~ **FALSE POSITIVE — closed 2026-05-31** | (API) `/v2/collections` | ~~CRITICAL~~ | The audit curl'd `https://shepard.nuclide.systems/shepard/api/v2/collections` — the `/shepard/api/` prefix is the upstream-frozen v1 base path and **does NOT apply to** `/v2/...` resources (per `application.properties` `quarkus.http.root-path=/`, v2 mounts directly at `/v2/...`). Direct `curl /v2/collections` (no prefix) returns **401** (auth required, endpoint present); `CollectionV2Rest` shipped 2026-05-18 in 9c8794cb2 with full CRUD + 22 unit tests. Frontend composables (`useCollectionProperties`, `useCollectionWatch`, etc.) already hit `/v2/collections/{appId}/...` paths successfully via the `v2BaseUrl()` helper that strips the `/shepard/api` prefix. Route pinned in `CollectionV2RestTest.classLevelPathIsExactlyV2Collections` (2026-05-31) to surface any future rename as a test failure. | Re-run with the correct base path: `curl -H "Authorization: Bearer $TOK" "https://shepard-api.nuclide.systems/v2/collections"` → 200. | n/a (false positive) |
| I3 | `BUG-COLL-FETCH-REFCONT-404` | `/collections/{id}` (long-form) | MAJOR | A toast appears at bottom-right: `"Error while fetchReferencedContainers: HTTP 404 Not Found"` on every collection detail page (LUMEN + MFFD). Page still renders but throws this on load. | Visit `/collections/2107`. See screenshot `collection-lumen-byid.png` — bottom-right red toast. | Frontend `useFetchReferencedContainers` (or similar) is calling a v2 endpoint that 404s (likely same v2 collections-by-appId rot). Suppress the toast OR fix the call. |
| I4 | `UI-COLL-APPID-EVERYWHERE` | (multiple) `/collections/{id}/dataobjects/{id}` etc. | MAJOR | The page-template uses long-form numeric `id` as the path key, not `appId`. So the URLs we share on chat / Slack / docs (`/collections/2107/dataobjects/2112/...`) are stable only for as long as the underlying long-form id stays. The "shareable appId URL" promise of `aidocs/platform/25` is broken: appId-form URLs 404 today. | Same as I1. | Once I1+I2 ship, the URL family switches to appId; backfill old long-form URLs to redirect via a `(num) → appId` mapper. |
| I5 | `UI-SEARCH-JSON-QUERY-001` | `/search?q=…` | **CRITICAL UX** | The search page (`SearchView.vue`) requires the user to write the JSON query body `{"property":"name","operator":"contains","value":"…"}` by hand in a text field. The "Use Code view" toggle exists but the default IS code view. There's no simple name-search field. A non-technical researcher cannot discover this. | Visit `/search?q=test`. See screenshot `search.png`. The pre-filled JSON `{"property":"name","operator":"contains","value":...}` is the visible default. | Add a simple "search by name" mode as the default; demote the JSON shape to "advanced". The header-bar global search already covers the simple case; the dedicated `/search` page should embrace the JSON shape with autocomplete + property dropdown + operator dropdown + value field, not a raw textarea. |
| I6 | `UI-SHAPES-RENDER-PICKERS-001` | `/shapes/render` | MAJOR | The page asks for "Template appid (VIEW_RECIPE)" and "Focus DataObject appid" as **bare text fields**. Violates CLAUDE.md "UI never asks for paths/URLs/IDs". The user must know two UUIDs to use the page. | Visit `/shapes/render`. See screenshot `shapes-render.png`. | Add a Template picker (list of VIEW_RECIPE templates the user has access to) and a DataObject picker scoped by collection. The endpoint is `POST /v2/shapes/render`; the picker only needs `GET /v2/templates?kind=VIEW_RECIPE` and `GET /v2/collections/{c}/data-objects`. |
| I7 | `UI-SNAP-DIFF-PICKERS-001` | `/snapshots/diff` | MAJOR | Same shape as I6 — two text fields for "Snapshot A (older) appid" and "Snapshot B (newer) appid". No picker, no list of available snapshots. Backend is shipped (V2b/V2c). | Visit `/snapshots/diff`. See screenshot `snapshots-diff.png`. | Add a Collection picker → snapshot picker for both A and B. The endpoints `GET /v2/collections/{appId}/snapshots` (list) and `GET /v2/snapshots/{a}/diff/{b}` exist. |
| I8 | `UI-SNAP-DIFF-STRUCTURED-001` | `/snapshots/diff` | MINOR | Output mode is **raw JSON only**. Page itself says "Raw diff JSON shown. Structured visualisation queued under SNAP-DIFF-UI-FOLLOWUP." Acknowledged debt; chip status is correct (`shipped` for BE, this is queued UI). | Same as I7. | Build the structured viewer per the `SNAP-DIFF-UI-FOLLOWUP` row. |
| I9 | `UI-SDREF-NO-CONTENT-001` | `/.../structureddatareferences/{id}` | **CRITICAL** | The Structured Data Reference detail page shows a 1-row table (Name / Oid / Created at) with the file's filename — **no link, no view affordance, no JSON preview, no download button**. The user sees the filename and is stuck. The icons in the action toolbar are trash + tag — NO pencil / edit. | Visit `/collections/2107/dataobjects/2112/structureddatareferences/2261`. See screenshot `sdref-tr001.png`. | (a) Make the filename a link that opens the underlying JSON in a modal / new tab. (b) Add an Edit pencil icon to match `FileReference` page's toolbar (a missing Edit affordance is the REF-EDIT-* rule violation). (c) Add inline JSON preview for objects ≤ 1 MB. |
| I10 | `UI-SDCONT-NO-CONTENT-001` | `/containers/structureddata/{id}` | MAJOR | Same shape as I9 — Structured Data container detail lists 16 items but no filename is a link, no preview, no download. | Visit `/containers/structureddata/2156`. See screenshot `container-structureddata.png`. | Same as I9 — make names clickable, add per-row "view content" icon. |
| I11 | `UI-FILEREF-FIRSTFILE-NOLINK-001` | `/.../filereferences/{id}` | MINOR | The first row of the file list in a FileReference is rendered as plain text (no link), while subsequent rows are linked. The `tr-001-cad-stub.bin` row has no anchor; `tr-001-test-report.md` and `tr-001-thermal.png` do. | Visit `/collections/2107/dataobjects/2112/filereferences/2256`. See screenshot `fileref-tr001.png`. | Investigate the rendering condition — likely a missing `key` or a v-for guard that excludes the first row. Possibly the `.bin` extension is treated as "no preview available" and the link is conditionally suppressed; it should still allow download. |
| I12 | `UI-FILE-CONTAINER-NO-DOWNLOAD-001` | `/containers/files/{id}` | MAJOR | The 18-file File Container table has no per-row Download / View icon; filenames are plain text. The container has 36.8 MB of files and the user cannot get bytes out via the UI. | Visit `/containers/files/4277`. See screenshot `container-files.png`. | Add a Download icon column. The endpoint is `GET /shepard/api/v2/files/{appId}/content` (FR1b singleton) or `GET /shepard/api/file-containers/{cid}/payload/{oid}` (legacy). |
| I13 | `UI-SEMANTIC-SPARQL-DEFAULT-REPO-001` | `/semantic/sparql` | MAJOR | The Repository ID input defaults to literal `internal`, but the v2 SPARQL endpoint 404s on the default `internal` repo per `aidocs/16` task #244 (`MATRIX-N1F-RECONCILE-01`). A non-admin user clicks Run with the default and gets an inscrutable error. | Visit `/semantic/sparql`. See screenshot `semantic-sparql.png`. | Resolve task #244; until then, the UI should either pre-populate with an appId that actually exists (look up the first available semantic repository on page load) or surface a clearer message than "404 on default 'internal' repo". |
| I14 | `UI-SPARQL-EDITOR-001` | `/semantic/sparql` | MINOR | Plain textarea — no syntax highlighting, no autocomplete, no query history, no example-query gallery. Acknowledged via the placeholder chip ("Full editor / autocomplete queued"). | Same as I13. | Use [yasgui](https://triply.cc/docs/yasgui/) or [codemirror-sparql](https://github.com/zazuko/codemirror-sparql). Both are MIT-licensed and lightweight. |
| I15 | `UI-SCENE-GRAPHS-LIST-EMPTY-001` | `/scene-graphs` | MAJOR | List shows "No scenes yet" for alice even though scene `019e79be-b880-7438-82df-4163625862b7` exists and renders fine when visited directly. The list endpoint apparently filters by ownership (bob owns the MFFD showcase scene; alice can only "Open by appid"). | Visit `/scene-graphs`. See screenshot `scene-graphs-list.png`. | Either (a) widen the list to include scenes shared into Collections the user has read access on (analogous to "Shared with me" on `/`) or (b) clarify the empty-state copy to say "no scenes you own — paste an appId below if someone shared one with you". |
| I16 | `UI-HOME-RECENT-EMPTY-001` | `/` | MINOR | The "Recent collections" section has just a "Browse all collections →" link and no list of items. It looks empty. The "Shared with me" section below DOES render 3 entries. The user can't tell whether they have any recent collections. | Visit `/`. See screenshot `home.png`. | When the user has 0 owned collections, hide the "Recent collections" heading entirely OR show "You haven't created a collection yet. Browse all or create your first." |
| I17 | `LAYOUT-4K-CENTERED-EMPTY-001` | `/`, `/tools`, `/me`, `/me#*`, `/semantic`, `/shapes/render`, `/shapes/validate`, `/snapshots/diff`, `/search`, `/scene-graphs`, `/admin`, `/admin/instance-registry`, `/admin/provenance` (gates) | MAJOR (cosmetic) | The content area is centered with a max-width of ~1400px on every page. At 3840×2160 this leaves ~50% of the screen as empty grey, including ~75% of vertical space below the fold on `/tools` and the `/admin` and `/admin/instance-registry` gates. | Visit any page above at 3840×2160. See `home.png`, `tools.png`, `semantic-landing.png`, `shapes-render.png` for the canonical examples. | (a) Either widen the max-width on tile-grid pages (`/tools`, `/me`, `/admin`, `/semantic`) to 1920+ so the tile grid uses more of the viewport, OR (b) add a layout-toggle (compact / wide / full) per user preference. The Collections list page (`/collections`) and DataObject detail page demonstrate that the existing components scale up; it's the landing/hub pages that don't. |
| I18 | `UI-COLL-ERR-TOAST-PERSIST-001` | (multiple collection pages) | MINOR | The "Error while fetching collection roles" / "fetchReferencedContainers" red toasts auto-show on every page load and persist until manually dismissed. They are noise — the page itself loads fine. | Visit `/collections/019e6ffc-...` or any long-form id collection. | Suppress role-fetch errors with a softer treatment (gray inline note in the affected pane). The toast UX assumes a real, user-actionable failure; an internal API miss should not pop a red toast. |
| I19 | `AUTH-FLO-MISSING-001` | KC / `/auth/signIn` | MINOR | The brief asked to sign in as `flo / flo-demo`. `flodemo` exists in `infrastructure/keycloak/shepard-demo-realm.json` (with role `user`) but the live realm (`https://shepard-auth.nuclide.systems/realms/shepard-demo`) does NOT have the user provisioned. Only `alice` / `bob` / `admin` are imported. | `curl -X POST https://shepard-auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token -d 'client_id=frontend-dev&username=flodemo&password=flo-demo&grant_type=password'` returns `"invalid_grant"`. | Re-import the realm config OR add the user via KC admin. |
| I20 | `UI-SEMANTIC-LANDING-SPARSE-001` | `/semantic` | MINOR | The semantic-substrate landing page has 2 tiles (Vocabularies + SPARQL) and nothing else. The brief mentioned `/semantic/repositories` but that route doesn't exist (no page file under `frontend/pages/semantic/repositories/`). Per `placeholderRegistry`, `instance-registry` and others exist as admin tiles but no public-facing repositories tile is anywhere. | Visit `/semantic`. See screenshot `semantic-landing.png`. | Add a "Repositories" tile (list of `:SemanticRepository` entities available to the user). The backend supports this — `findByAppId` is wired and used by SPARQL playground. |
| I21 | `UI-SEMANTIC-VOCABS-COUNT-001` | `/semantic/vocabularies` | MINOR | 10 vocabularies listed with name + URI only. No predicate count, no last-updated, no enable/disable hint, no annotation usage count. User has to click each row to see how many predicates a vocab has. | Visit `/semantic/vocabularies`. See screenshot `semantic-vocabularies.png`. | Add columns: `# predicates` (count from `:Predicate` linked to the vocab), `# annotations using it`, `last seen` (most recent `:SemanticAnnotation.timestamp` over its predicates). |
| I22 | `UI-MFFD-EMOJI-ORDINAL-001` | DataObject detail page (MFFD) | MINOR | The collection-mffd page shows DataObject names with trailing emoji circled-digit characters ("AFP Layup ②") that aren't friendly to screen readers or copy-paste. Likely a seed-script artefact. | Visit `/collections/1787`. See screenshot `collection-mffd-byid.png`. | Adjust the seed script (`examples/mffd-showcase/seed.py`) to use plain integers; not a UI bug per se but the UI surfaces it prominently. |
| I23 | `UI-CONTAINER-LIST-KIND-FILTER-001` | `/containers` | MINOR | The "Select container type" dropdown filter is empty by default, lists all 8 rows, and offers no quick chips for the 3 kinds present. At 4K the dropdown is tiny relative to the empty space. | Visit `/containers`. See screenshot `containers-list.png`. | Replace with chip-group filter (`File`, `Timeseries`, `Structured`, `Spatial`, `HDF`, `Video`) so a user can tap one kind without opening a dropdown. |

---

## Unfinished pages

| # | Page route | What looks incomplete | Severity | Backlog row |
|---|---|---|---|---|
| U1 | `/semantic/predicates/{predicateIri}` | Renders only the IRI as a heading + a "Suggested query" code block + a chip. No usage count, no example values, no annotated-entity list. Acknowledged via PlaceholderImplStatus chip. | MINOR | `SEMA-V6-PRED-UI` |
| U2 | `/containers/timeseries/{id}` → "Channel Annotations" pane | Pane is collapsed; expanding shows the PlaceholderFragmentPane for `ts-channel-annotations`. The REST + MCP tools exist. | MAJOR | `TS-CHANNEL-ANNOTATIONS-UI` |
| U3 | `/admin#sql-timeseries` | Whole pane is the placeholder; the runtime cap mutate endpoint (`PATCH /v2/admin/sql-timeseries/config`) is shipped + CLI works. | MAJOR | `P10c-UI` |
| U4 | `/admin#notifications-admin` | Whole pane is the placeholder; backend transports are partial. | MAJOR | `UI-NTF1-ADMIN-PANE` |
| U5 | `/admin/instance-registry` | Whole page is PlaceholderPageHeader + ImplStatus + RestDump — no actual form to add/edit/delete an instance, just a JSON dump. | MAJOR | `FE-PROV-INSTANCE-REGISTRY-UI` |
| U6 | `/admin#instance-admins` | Same shape as U5 — placeholder pane, no real UI. | MAJOR | `ADM-MANAGE-UI` |
| U7 | `/admin#users-orcid` | Same shape — placeholder pane only. | MINOR | `ADM-USR-ORCID-UI` |
| U8 | `/admin#users-git` | Same shape — placeholder pane only. | MINOR | `ADM-USR-GIT-UI` |
| U9 | `/admin#ontology-alignment` | Placeholder shows the RestDump of `/v2/semantic/ontology/alignment` (12 rows). No human-readable rendering, no diff, no validation status. | MINOR | `TPL3a-UI-FULL` |

---

## 4K layout breaks

Captured at 3840×2160 (the user's actual viewport, per
`feedback_validate_user_viewport.md`).

| # | Page | What's wrong | Fix |
|---|---|---|---|
| L1 | `/` | Hero card centered with ~50% empty horizontal space; "Recent collections" header followed by zero items renders as visual dead air. | Tighten the empty-state collapse; widen card grid to 1920px. |
| L2 | `/tools` | 6 tiles in a centered 3×2 grid; the bottom 75% of the viewport is empty. | Widen tile grid OR add usage stats below ("recently used tools", "examples"). |
| L3 | `/me` and `/me#*` | Same as L2 — 7 profile tiles in a centered 3-column grid; vast empty space below. The right-pane content area also caps at ~1300px. | Either widen the profile pane or add a "recent activity" feed on the right. |
| L4 | `/semantic` | 2 tiles centered, nothing else. Looks broken at 4K. | Add the missing Repositories tile (I20). |
| L5 | `/shapes/render`, `/shapes/validate`, `/snapshots/diff` | Bare form-shaped pages with empty grey covering the whole viewport below. | Add a side panel listing the most recent N runs / templates / snapshots so the page is not visually empty for first-time visitors. |
| L6 | `/search` | "Advanced Search" card uses ~30% of the screen. Results table is narrow. | Widen the results table; show a faceted-search sidebar on the left. |
| L7 | `/admin`, `/admin/instance-registry`, `/admin/provenance` (gates) | The "access denied" cards are tiny dots in the middle of a vast grey 4K canvas. | Use a softer empty-state pattern — full-bleed background with a "Request access" CTA. |
| L8 | `/scene-graphs` | "No scenes yet" + "Open by appid" expand at the top; rest of viewport empty. | Same as I15. |
| L9 | `/me#ai-settings` (the only profile placeholder pane) | Card with a single chip + one paragraph; ~85% of viewport empty. | Once the real AI-settings UI ships (AI1a), the chip disappears and the page fills naturally. Until then, accept. |

---

## Proposed `aidocs/16` backlog rows

Copy-pastable markdown table rows; group by family.

### PLACEHOLDER-* (UI debt replacing the placeholder stub with a real surface)

| ID | what it gives the user | acceptance | size | priority | risks | links |
|---|---|---|---|---|---|---|
| UI-NTF1-ADMIN-PANE | Real Notification transports admin pane (SMTP / Matrix / in-app) | Form to add/edit/test each transport; smoke-test button hits `POST /v2/admin/notifications/test`. | M | MAJOR (queued) | NTF1 plugin family still evolving | aidocs/40 |
| ADM-MANAGE-UI | Real instance-admin role grant/revoke pane | List users with role chips; grant/revoke buttons; calls `/v2/admin/instance-admins`. | S | MAJOR (queued) | none | aidocs/16 ADM-MANAGE |
| ADM-USR-ORCID-UI | Real admin pane to set/clear other users' ORCIDs | Search users + per-user ORCID field + Save | S | MINOR (queued) | none | aidocs/16 ADM-USR-ORCID |
| ADM-USR-GIT-UI | Real admin pane to issue/rotate other users' git creds | Search users + per-user "Issue token" + "Rotate" actions | S | MINOR (queued) | none | aidocs/16 ADM-USR-GIT |
| P10c-UI | Real admin pane for SQL-over-HTTP runtime caps (max-rows, max-duration) | Two number inputs + Save; PATCH `/v2/admin/sql-timeseries/config` | XS | MAJOR (queued) | none | aidocs/29 P10c |
| FS1e1-UI | Real admin pane for file-storage migration (trigger, watch progress) | Source/target adapter pickers + Start + live progress polling on `/v2/admin/files/migrate/status` | M | MAJOR (queued) | none | aidocs/45 §6 |
| TPL3a-UI-FULL | Real Ontology Alignment pane (12 alignments, readable, with diff view) | Table rendering with linked Shepard concept ↔ upper-ontology IRI columns; SPARQL-deep-link per row | S | MINOR (queued) | none | aidocs/semantics/97 |
| FE-PROV-INSTANCE-REGISTRY-UI | Real Instance Registry pane (add / edit / delete instances) | Form to add (instanceId, displayName, baseUrl, dlrInstitute); table to list/delete | M | MAJOR (queued) | none | aidocs/16 FE-PROV-INSTANCE-REGISTRY |
| SEMA-V6-PRED-UI | Real per-predicate usage page (count, top values, annotated entities) | Backend endpoint `GET /v2/semantic/predicates/{iri}/stats` + table render | M | MINOR (queued) | needs new BE endpoint | aidocs/semantics/100 §5 |
| TS-CHANNEL-ANNOTATIONS-UI | Real channel-annotations pane (list / add / delete per channel) | Pane lists annotations per channel (5-tuple key); add-annotation dialog scoped to channel | M | MAJOR (queued) | none | aidocs/16 TS-SEMANTIC-REST |

### UI-FIX-* (issue fixes that don't need a new feature)

| ID | what it gives the user | acceptance | size | priority | risks | links |
|---|---|---|---|---|---|---|
| BUG-COLL-APPID-ROUTE-001 | URLs `/collections/{appId}` work end-to-end | `parseCollectionId` returns the raw string (UUID v7 or numeric); call-sites cast at the boundary. Vitest covers both paths. **Shipped 2026-05-31 (frontend fix).** Follow-up: switch downstream composables from generated v1 client to v2 endpoints so UUID v7 ids resolve (currently 404 cleanly on v1 paths). | S (frontend done) + M (composable switch) | **CRITICAL** | none — the frontend fix landed standalone; the composable switch is its own row | this file §Issue catalogue I1 |
| ~~BUG-V2-COLL-LIST-404~~ | n/a — false positive (audit used wrong base path) | route pinned in `CollectionV2RestTest` | — | — | — | this file §Issue catalogue I2 |
| BUG-COLL-FETCH-REFCONT-404 | No persistent red toast on every collection-page load | Frontend either suppresses 404 from `useFetchReferencedContainers` or backend route is fixed | S | MAJOR | none | this file §Issue catalogue I3 |
| UI-SEARCH-SIMPLE-FIELD-001 | A simple "search by name" mode as default on `/search` | Default mode is a text input + Search; "Advanced JSON" is a toggle | S | **CRITICAL** | none | I5 |
| UI-SHAPES-RENDER-PICKERS-001 | Template + DataObject pickers on `/shapes/render` | No bare appId fields visible | M | MAJOR | none | I6 |
| UI-SNAP-DIFF-PICKERS-001 | Collection + snapshot pickers on `/snapshots/diff` | No bare appId fields visible | M | MAJOR | none | I7 |
| UI-SDREF-CONTENT-001 | Filename clickable on Structured Data Reference page; JSON preview modal | Filename is a link; clicking opens a modal with raw + tree view; Edit pencil added to toolbar | M | **CRITICAL** | none | I9 |
| UI-SDCONT-CONTENT-001 | Same on Structured Data Container detail | per-row View icon + filename link | S | MAJOR | none | I10 |
| UI-FILEREF-FIRSTFILE-NOLINK-001 | First row in file-reference list is a link like the others | Investigate v-for / key bug | XS | MINOR | none | I11 |
| UI-FILE-CONTAINER-DOWNLOAD-ICON-001 | Download icon column on `/containers/files/{id}` | Per-row Download button; hits `/v2/files/{appId}/content` | S | MAJOR | none | I12 |
| UI-SEMANTIC-SPARQL-DEFAULT-REPO-001 | SPARQL playground works with the default repo selection | Either pre-populate with first available repo OR clearer error toast for missing-repo case | S | MAJOR | gated on task #244 backend fix | I13 |
| UI-SCENE-GRAPHS-LIST-WIDEN-001 | List includes scenes shared via Collection access | Backend list endpoint scoped via Collection read role | M | MAJOR | none | I15 |
| UI-HOME-RECENT-EMPTY-001 | Better empty-state for "Recent collections" | Hide section OR show "create your first" copy | XS | MINOR | none | I16 |
| UI-COLL-ERR-TOAST-PERSIST-001 | No persistent red toast on collection pages from non-critical role-fetch failures | Soft inline note in pane instead of toast | S | MINOR | none | I18 |
| UI-SEMANTIC-LANDING-REPO-TILE-001 | Add Repositories tile to `/semantic` | Tile linking to a new `/semantic/repositories` page that lists `:SemanticRepository` entities | S | MINOR | needs new page file | I20 |
| UI-SEMANTIC-VOCABS-COUNT-001 | Vocab list shows predicate count + annotation count + last-used | Extra columns; backend `GET /v2/semantic/vocabularies?withStats=true` | S | MINOR | needs BE shape | I21 |
| UI-MFFD-EMOJI-ORDINAL-001 | DataObject names in MFFD seed don't have trailing emoji circles | Edit seed script | XS | MINOR | none | I22 |
| UI-CONTAINER-LIST-KIND-FILTER-001 | Chip-group filter on `/containers` for kind | Chips replace the kind dropdown | XS | MINOR | none | I23 |
| AUTH-FLO-MISSING-001 | `flodemo` user works on the live realm | Realm re-import or admin-add user | XS | MINOR | none | I19 |

### LAYOUT-4K-*

| ID | what it gives the user | acceptance | size | priority | risks | links |
|---|---|---|---|---|---|---|
| LAYOUT-4K-CENTERED-EMPTY-001 | Hub-style pages (`/`, `/tools`, `/me`, `/semantic`, `/admin`) fill the 4K canvas | max-width raised to 1920+ on landing pages OR layout-toggle preference added; Playwright spec captures 4K screenshots that aren't 50% grey | M | MAJOR (cosmetic) | regression risk on smaller viewports | I17 |
| LAYOUT-4K-GATE-EMPTY-001 | Admin-gate "Access denied" cards use the full viewport thoughtfully | Card grows to a full-bleed pattern; CTA visible above the fold | S | MINOR | none | L7 |

---

## Proposed `aidocs/44` demotions

Rows to flip from `✓ shipped` / `⚙ BE ✓ / UI ✓` to `alpha` (or to
add the explicit "UI ⚠ placeholder" flag where the row already
calls out "UI pending").

| `aidocs/44` row | current status | proposed status | reason |
|---|---|---|---|
| NTF1 — Notification transports admin | shipped (?) | **alpha (UI is placeholder)** | `/admin#notifications-admin` is the full PlaceholderFragmentPane |
| ADM-MANAGE — instance-admin grant | BE ✓ / UI pending | **alpha** (already implied; make explicit) | `/admin#instance-admins` is placeholder |
| ADM-USR-ORCID | BE ✓ | **alpha** (UI is placeholder) | `/admin#users-orcid` is placeholder |
| ADM-USR-GIT | BE ✓ | **alpha** (UI is placeholder) | `/admin#users-git` is placeholder |
| P10c — SQL-over-HTTP runtime caps | ⚙ BE ✓ / admin config UI pending | already correct — keep `BE ✓ / UI pending`, but enumerate a target sprint | placeholder pane today |
| FS1e1 — File migration trigger | ⚙ BE ✓ / UI pending ↑ | keep — already explicit | placeholder pane today |
| FS1e2 — Auto-sweep | ⚙ BE ✓ / UI pending ↑ | keep | placeholder pane today |
| FS1e3 — Per-file rollback | ⚙ BE ✓ / UI pending ↑ | keep | placeholder pane today |
| TPL3a-lite — Ontology Alignment | ✓ shipped | **alpha (UI is RestDump only)** | `/admin#ontology-alignment` placeholder |
| FE-PROV-INSTANCE-REGISTRY | ✓ shipped | **alpha (UI is full placeholder shape)** | `/admin/instance-registry` is the full placeholder page |
| TS-SEMANTIC-REST | ✓ shipped (2026-05-27) | UI is placeholder — change to `BE ✓ / UI ⚠ placeholder` | `ts-channel-annotations` placeholder pane in TS container detail |

---

## What I found that surprised me

1. **The `parseInt`-on-UUID bug** (`BUG-COLL-APPID-ROUTE-001`). The
   regression is silent — the URL "works" (200) because Nuxt renders
   the page shell, then the inner data-fetch resolves to id 19 and
   shows a red toast that an experienced user dismisses without
   reading. This is the most user-affecting bug I found; it touches
   every URL shared via chat, docs, MCP tools, or bookmarks. It
   should be the **first** thing fixed after this report.
2. **`/v2/collections` returns 404** for everything — list, by-appId,
   anything. Confirmed via direct curl with a valid JWT. The
   frontend's appId-resolution failure stacks on top of this backend
   bug. The L2d row in `aidocs/16` is `gated on H4 (P4 cleared)` but
   H4 is `done`; the endpoint should already be live.
3. **The placeholder catalogue is internally consistent and useful**.
   `placeholderRegistry.ts` is the single source of truth for the 18
   stubs, and each carries `backend`, `backlogRow`, and `designDoc`
   metadata. The kit did exactly what it was designed to do — make
   no-UI features visible AND auditable. The work now is to mine
   the registry and ship the real UIs.
4. **The Reference detail pages diverge in completeness**. The
   TimeseriesReference page is the standout — chart + 5-tuple channel
   table + time-reference panel + annotations + actions. The
   FileReference page is decent (3 files, mostly linked, edit
   pencil). The StructuredDataReference page is a hole — single
   filename, no link, no preview, no edit pencil. The REF-EDIT-*
   audit (CLAUDE.md "every reference type ships a complete CRUD UI")
   confirms StructuredData is the laggard.
5. **The home page "Recent collections" empty state**. The Header
   reads "Recent collections" with no count, no items, and no
   "you have no recent collections" message. Just a "Browse all
   collections →" link. This is the worst first-impression on
   the entire site for a brand-new user.
6. **The search page is JSON-by-default**. `/search?q=test` shows a
   textarea pre-filled with `{"property":"name", "operator":"contains",
   "value":"test"}`. A non-technical researcher would close the tab.
   The header-bar search works fine — the dedicated `/search` page is
   the regression.
7. **`/shapes/render` and `/snapshots/diff` ask for two appIds each**.
   No pickers. Per CLAUDE.md "UI never asks for paths/URLs/IDs" this
   is a doctrinal violation. The fixes are small (template picker +
   collection picker) and unlock the killer features (Trace3D from
   Templates; "what changed between two snapshots?").
8. **The `instance-registry` page is the **most polished placeholder**
   on the site** — full PlaceholderPageHeader + ImplStatus chip +
   RestDump rendering — and yet it's still functionally a JSON dump.
   The contrast between the polish of the *placeholder pattern* and
   the absence of the *real UI* is the recurring theme of this audit.
9. **Persona walk — Reluctant Senior Researcher** (per
   `feedback_agents_argue_and_consult.md` lens citation):
   * "I have folders. I'd visit `/`, see the centered card on 50%
     grey, think 'this is a blank page', and never come back."
   * "I'd type a URL my colleague sent me — `/collections/019e...` —
     get a red toast saying 'id 19 is null or deleted', and assume my
     colleague was wrong."
   * "I'd click `/search`, see the JSON template, and close the tab.
     I have grep and folders; I don't have time for this."
   * The senior would not get past the first 30 seconds — and the
     three things that bounced them are all in this report.
10. **Persona walk — Digital Native** (lens):
    * "The TimeseriesReference page is great — clear chart, clear
      table, I can see the channel structure."
    * "The StructuredDataReference page has no link to the JSON — I
      have to drop to curl. Frustrating but acceptable."
    * "The SPARQL playground works, the editor is a plain textarea
      — I'd want yasgui. Acknowledged via the chip."
    * "The `/search` page is a JSON editor — actually fine for me,
      but I'd never demo it to my PI."
    * "The MCP tile in `/me#mcp` is great — I can copy the SSE URL
      and wire Claude to my data."
    * Digital Native verdict: "production tool, with three rough
      edges (search, snapshot diff pickers, render pickers); I'd
      contribute a PR for the snap-diff picker."

---

## Sources walked

40 screenshots at
`/opt/shepard/aidocs/agent-findings/screenshots/ui-scrutinizer-2026-05-30/`,
listed alphabetically:

- `about.png`, `admin.png`, `admin-instance-registry.png`,
  `admin-provenance.png`
- `collection-lumen.png` (broken: appId path),
  `collection-lumen-live.png` (broken: appId path),
  `collection-lumen-byid.png` (works: long-form id)
- `collection-mffd-live.png` (broken: appId path),
  `collection-mffd-byid.png` (works: long-form id)
- `collections-list.png`
- `container-files.png`, `container-structureddata.png`,
  `container-timeseries.png`
- `containers-list.png`
- `dataobject-lumen.png` (TR-001 detail page, rich)
- `fileref-tr001.png`, `tsref-tr001.png`, `sdref-tr001.png`
- `healthz.png`, `help.png`, `home.png`
- `me.png`, `me-profile.png`, `me-apikeys.png`, `me-mcp.png`,
  `me-subscriptions.png`, `me-git.png`, `me-ai.png`, `me-semantic.png`
- `scene-graph-detail.png`, `scene-graphs-list.png`
- `search.png`
- `semantic-landing.png`, `semantic-sparql.png`,
  `semantic-vocabularies.png`
- `shapes-render.png`, `shapes-validate.png`
- `snapshots-diff.png`
- `tools.png`
- `__login-fail.png` (the flodemo failure that motivated I19)

Specs at `/opt/shepard/e2e/tests/`:

- `ui-scrutinizer-2026-05-30.spec.ts` (main 28-page walk)
- `ui-scrutinizer-2026-05-30-p3.spec.ts` (long-form-id deep dive)
- `ui-scrutinizer-2026-05-30-p4.spec.ts` (refs + containers + signin)

Manifests at the same screenshot path:
`_manifest.json`, `_manifest_phase3.json`, `_manifest_phase4.json`.
