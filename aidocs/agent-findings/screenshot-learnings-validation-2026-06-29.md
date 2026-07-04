---
stage: feature-defined
title: Screenshot-learnings v2-API-conformance audit (L1–L7)
date: 2026-06-29
author: API-conformance audit agent
slug: screenshot-learnings-validation-2026-06-29
---

# Screenshot-learnings v2-API-conformance validation (L1–L7)

Read-only audit. No code was edited. Working dir `/opt/shepard`.
Hunts the seven recurring `/v2/` bug classes surfaced while debugging
five presentation screenshots. Each finding carries `file:line`, the
mismatching expression, the bug class, and a severity argued briefly.

## What I found

Headline: the highest-value defects are **L2** (numeric-id suppression →
eternal spinner) on the per-reference detail pages, and **L1** backend
*inconsistency* (the primary DataObject list endpoint returns a bare array
+ pagination-in-headers while ~all sibling v2 lists return a `{items,…}`
envelope). **L3** and **L4** are each a single concrete defect with a clean
fix. **L6** safeguard is already present. **L7** is the surprise: the
premise is stale — V49 seeds **zero** vocabularies (it only mints the
singleton `:SemanticRepository`); the real preseed is
`ontologies-manifest.json`, which lists **19** ontologies, so the
"19 Ontologien" caption is *correct* against today's code.

Severity legend: CRITICAL = user-facing crash / eternal spinner;
MAJOR = silent empty / wrong data; MINOR = cosmetic / diagnostic / latent.

---

## Per-class instance tables

### L1 — bare-array vs `{items}` envelope mismatch

**(a) Backend — which v2 list endpoints emit an envelope vs a bare array.**
The inconsistency itself is the root cause. There are at least three
*different* envelope conventions live in `/v2/` simultaneously:

| Endpoint (file:line) | Shape returned | Envelope? | Severity |
|---|---|---|---|
| `template/resources/ShepardTemplateRest.java:101` | `new PagedResponseIO<>(rows,total,page,pageSize)` | `{items}` PagedResponseIO | — (reference-good) |
| `collection/resources/CollectionContainersRest.java:100` | `new PagedResponseIO<>(containers,…)` | `{items}` PagedResponseIO | — |
| `plugins/aas/.../v2/resources/AasShellsRest.java:118,194` | `new PagedResponseIO<>(shells,…)` | `{items}` PagedResponseIO | — |
| `search/resources/SearchV2Rest.java:151` | `new SearchV2ResultIO(items,total,…)` | `{items}` **bespoke** envelope | MINOR (drifts from PagedResponseIO) |
| `shapes/resources/ShapesApplicableRest.java:133` | `new ShapesApplicableResponseIO(focusAppId,items)` | `{items}` **bespoke** | MINOR |
| `snapshot/resources/SnapshotListRest.java:184` | `new SnapshotListPageIO(filtered,total,…)` | `{items}` **bespoke** | MINOR |
| **`dataobject/resources/DataObjectV2Rest.java:320,325`** | **bare `List` JSON array** via `writer.writeValueAsString(result)`; pagination in `X-Total-Count` + `Content-Range` headers | **NO — bare array** | **MAJOR** |
| `DataObjectV2Rest.java:250` (empty-parent branch) | literal `Response.ok("[]")` | bare array | MAJOR (shape-consistent with :325 but diverges from every envelope sibling) |

The `DataObjectV2Rest` list — the single most-hit list in the product —
is the canonical L1 root cause: it is a **bare array with header-based
pagination**, while the rest of `/v2/` moved to a body envelope. Any
consumer that assumes `.items` here silently gets `undefined`; any
consumer that assumes a bare array elsewhere gets `.map of undefined`.
Three coexisting envelope styles (PagedResponseIO, three bespoke
`*PageIO`/`*ResultIO`, and bare-array+headers) is the structural smell.

**(b) Frontend — `.items` readers and whether they tolerate a bare array.**
Reference-good hardened pattern: `composables/annotated.ts:87`
(`(page.items ?? [])`). Tolerance taxonomy:

| File:line | Expression | Tolerates bare array? | Severity |
|---|---|---|---|
| `composables/useApplicableShapes.ts:83` | `Array.isArray(body.items) ? body.items : []` | partial (guards `.items` non-array, not a top-level bare array) | MINOR |
| `composables/context/admin/useFetchPlugins.ts:83-85` | `Array.isArray(body) ? body : (body?.items ?? [])` | **yes (both shapes)** | — (reference-good) |
| `composables/context/useProjectMembership.ts:46-48` | `Array.isArray(body) ? … : (body?.items ?? [])` | **yes** | — |
| `composables/useSnapshotList.ts:69` | `page.items ?? []` | null-tolerant, **not** bare-array | MINOR |
| `composables/context/useFetchNotebooks.ts:17` | `result.items ?? []` | null-tolerant only | MINOR |
| `composables/context/useSnapshots.ts:36` | `result.items ?? []` | null-tolerant only | MINOR |
| `composables/context/useFetchCollectionContainers.ts:19` | `containers.value = page.items` | **no — assumes `.items`** | MAJOR (blank container list if backend ever bare-arrays) |
| `composables/context/admin/useFetchTemplates.ts:18` | `templates.value = page.items` | **no** | MAJOR |
| `composables/context/admin/useFetchFeatureToggles.ts:15` | `features.value = page.items` | **no** | MAJOR |
| `composables/context/admin/useInstanceAdmins.ts:71` | `(… as {items:…}).items` | **no** | MAJOR |
| `composables/aas/useAasShells.ts:93` | `shells.value = data.items` | **no** | MAJOR (mitigated: AAS backend is PagedResponseIO) |
| `composables/context/useGlobalSearch.ts:128,135` | `result.items.filter(…)` | **no — crashes if `.items` absent** | MAJOR |

The intolerant readers are currently *safe-but-fragile*: their matching
backend endpoint does emit an envelope today (verified for templates,
containers, AAS shells, search). The fragility is the bug — a one-line
backend change (or a plugin endpoint that bare-arrays) silently empties
the list with no type error, because TS sees the generated client's
envelope type and the runtime mismatch is invisible. The cheap, universal
fix is the `useFetchPlugins`/`annotated.ts` shape everywhere.

### L2 — v2 numeric-`id` suppression breaks v1-id resolution (highest-value class)

v2 IOs carrying `@JsonIgnoreProperties` id suppression (grep under
`backend/.../v2`):

| IO (file) | Suppresses |
|---|---|
| `v2/references/io/BasicReferenceV2IO.java:29` | `id`, `dataObjectId` |
| `v2/references/io/ReferenceV2IO.java` (extends BasicReferenceV2IO) | inherits both |
| `v2/dataobject/io/DataObjectListItemV2IO.java` | `id` (+ dataObjectId family) |
| `v2/dataobject/io/DataObjectDetailV2IO.java` | `id` |
| `v2/dataobject/io/CreateDataObjectV2IO.java` | `id` |
| `v2/collection/io/CollectionV2IO.java` | `id` |
| `v2/containers/io/BasicContainerV2IO.java:25` | `id` |
| `common/neo4j/io/BasicEntityV2IO.java` | `id` (base) |
| `v2/template/resources/TemplatePortabilityRest.java` | (inline `@JsonIgnoreProperties` on a DTO) |

Pages depending on a numeric id the v2 entity no longer carries:

| Page (file:line) | Mechanism | Result | Severity |
|---|---|---|---|
| **`…/timeseriesereferences/[timeseriesReferenceId]/index.vue:68`** | `timeseriesReferenceNumericId = referenceV2.value?.id` — but `ReferenceV2IO` suppresses `id`, so it is **always `undefined`** | downstream `useFetchTimeseriesReference` (`:70`) + `useFetchTimeseriesPayload` (`:120`) gate on that id → v1 calls never fire → **Channel Overview spins forever** | **CRITICAL** (the canonical broken instance; the "fix" comment at :59-64 is itself broken — `useFetchReferenceV2` comment claims it returns `.id`, but the IO strips it) |
| **`…/filereferences/[fileReferenceId]/index.vue:41-42`** | `fileReferenceNumericId = resolveNumericId(undefined, <uuid>)` → `Number("uuid")=NaN` → **always `undefined`** | `useFetchFileReference(…)` never fires; main content gated on `v-if="… && !!fileReference"` (`:148`) → never renders → spinner / falls to `EntityNotFound` | **CRITICAL** |
| **`…/structureddatareferences/[structuredDataReferenceId]/index.vue:38-39`** | `structuredDataReferenceNumericId = resolveNumericId(undefined, <uuid>)` → **always `undefined`** | gated on `v-if="… && !!structuredDataReference"` (`:158`) → never renders | **CRITICAL** |
| `…/dataobjects/[dataObjectId]/index.vue:101-105` | `collectionNumericId`/`dataObjectNumericId = resolveNumericId(entity.value?.id, routeParam)` | tolerated — render is NOT gated on the numeric id (fixed in #317/#318 DataObject-detail-hang); numeric id feeds only the spatial sub-fetch (`:253`), which degrades | MINOR (already-fixed shape; reference for the pattern) |
| `…/collections/[collectionId]/index.vue:48-49` | `collectionNumericId = resolveNumericId(collection.value?.id, …)` | `CollectionV2IO` suppresses `id`, so resolves only via the route-param fallback (numeric route) or `undefined` (uuid route); children `v-if`-guarded on `collectionNumericId` → those panels (lineage, cross-track, MFFD NDT) **silently hidden** on uuid routes | MAJOR (panels vanish, no crash/spinner) |
| `pages/containers/files/[containerId]/index.vue:19` | `containerNumericId = /^\d+$/.test(id) ? Number(id) : (fileContainer.value?.id ?? 0)`; `BasicContainerV2IO` suppresses `id` → `?? 0` | accessor itself fetches by `appId` (`FileContainerAccessor.ts:49,72,170`) so data loads; only child components needing a v1 numeric id receive `0` | MINOR/latent (no spinner; wrong-id risk only for v1 child calls e.g. roles) |
| `pages/containers/timeseries/[containerId]/index.vue:22-23` | same `?? 0` shape | same | MINOR/latent |
| `pages/containers/structureddata/[containerId]/index.vue:21-22` | same; `containerNumericId.value` fed to `structuredDataContainerId` (`:78,97`) | wrong container-id `0` passed to a write/query if reached on a uuid route | MAJOR (latent) |
| `pages/containers/spatialdata/[containerId]/index.vue:77` | `spatialData.value?.id` (spatiotemporal plugin keeps numeric v1 paths per compat exception) | tolerated (plugin is the named frozen-compat carrier) | MINOR |

The three CRITICALs are the same class as the already-fixed
DataObject-detail hang (#317/#318): a render/fetch gate transitively
depends on a numeric id the v2 IO suppresses. The timeseries page is
especially deceptive — it *looks* fixed (loads `referenceV2` first) but
`referenceV2.value?.id` is `undefined` because `BasicReferenceV2IO`
strips `id`. The composable's own docstring
(`useFetchReferenceV2.ts`: "ReferenceV2 … plus the numeric `.id`") is
factually wrong against the IO.

### L3 — `materialize` on a `VIEW_RECIPE` template → HTTP 422

Backend contract: `v2/mappings/resources/MappingsMaterializeRest.java:130-134`
returns `422 "materialize requires a MAPPING_RECIPE template; templateKind=…"`
when `templateKind != MAPPING_RECIPE`. A `VIEW_RECIPE` must go to
`/v2/shapes/render`.

| Caller (file:line) | Branches on `templateKind`? | Severity |
|---|---|---|
| `pages/scene-graphs/play/[templateAppId].vue:58` | **No** — calls `materializeMapping(appId,{})` unconditionally; only inspects `result.outputKind` *after* the call. A VIEW_RECIPE 422s into the `catch` (`:73`) → generic "Failed to materialize" toast | **MAJOR** (known instance; never reaches `/shapes/render`) |
| `pages/afp-thermo-overlay/[templateAppId].vue:45` | **No** — same unconditional `materializeMapping(…,{})` | MAJOR (same shape; same fix) |
| `composables/useKrlTrajectory.ts:188`, `useUrScriptTrajectory.ts:181` | callers create the MAPPING_RECIPE themselves first, so kind is known-correct | — (not a defect) |

Fix: branch on the loaded template's `templateKind` (fetch
`/v2/templates/{appId}` first, or read it from the entity already in
hand) and route VIEW_RECIPE → `/v2/shapes/render`, MAPPING_RECIPE →
materialize. The play page already has the data to do this — it just
calls materialize before checking.

### L4 — loud error toast where a plugin-optional fetch should fail-soft

| File:line | Expression | Severity |
|---|---|---|
| `composables/context/admin/useJupyterConfig.ts:82` | `handleError(e, "fetching Jupyter config")` inside `refresh()` catch — fires unconditionally, **including on the public-read path** `/v2/jupyter/config` | **MAJOR** |

Consumed on **core (non-admin) pages**: `DataObjectDataReferencesTable.vue`,
`DataObjectNotebooksPane.vue`, `ProfilePane.vue`. When the Jupyter plugin
is absent/unconfigured (the default), the public read fails and a red
toast lands on a core DataObject page — exactly the loud-failure shape.
The fix: in the non-admin read path, swallow the error
(`config.value = null`, no `handleError`); reserve the toast for the
admin save path. Other plugin-optional fetches to scan for the same shape:
any `useV2ShepardApi` call on a core page whose endpoint only exists when
a plugin is loaded (AAS config read on non-AAS instances, git-credential
reads) — none surfaced a *core-page* loud toast in this pass, but the
`handleError`-in-catch idiom is widespread and should grow a
`failSoft: true` option.

### L5 — diagnostic discipline (not a code grep)

During the screenshot triage, a `401` from an expired access token was
mis-read as a systemic `406 Not Acceptable` regression, sending the team
down a content-negotiation rabbit-hole. The auth layer's bearer token had
simply expired; once refreshed, the "406" vanished. **Recommendation:**
the backend-regression triage runbook must include a *token-refresh /
re-auth* step **before** classifying any 4xx as a content/serialization
regression — re-issue the token (or hit a known-200 endpoint with a fresh
token) and reproduce. A stale token presents as a constellation of
unrelated 4xx (401/403/406 depending on which filter rejects first); the
cheapest disambiguation is a fresh login. No code change; add the step to
`docs/admin/runbooks/` regression triage.

### L6 — stale generated client safeguard

`make build-frontend` (Makefile:56-62):

```make
build-frontend:
	rm -rf frontend/node_modules/@dlr-shepard && cd frontend && npm install
	cd frontend && npm run build
```

**Safeguard present.** The `rm -rf frontend/node_modules/@dlr-shepard`
before `npm install` busts the npm `file:`-dep version cache (the dep
stays `1.0.0` across regens, so `npm install` alone keeps the stale
client). The comment cites the V2-SWEEP-001-CLIENT-REGEN incident
(2026-06-12) and BUG-SIDEBAR-CHILD-FETCH-STALE-CLIENT. No action needed,
except: this only protects the **Makefile** path. A developer who runs
`cd frontend && npm install && npm run build` by hand bypasses the
`rm -rf` and re-bundles the stale client. Recommend moving the cache-bust
into a `frontend/scripts/` prebuild hook (or a `preinstall`/`prebuild`
npm script) so the safeguard travels with the package, not the Makefile.

### L7 — content gap: "19 Ontologien" vs V49 seed count

**The premise is stale; the caption is correct.**

- `V49__Bootstrap_internal_semantic_repository.cypher` seeds **0**
  vocabularies. It only `MERGE`s the singleton `:SemanticRepository`
  node of type `INTERNAL`. There is no per-ontology seeding in V49 at all.
- The actual ontology preseed is
  `backend/src/main/resources/ontologies/ontologies-manifest.json`,
  consumed by `de.dlr.shepard.context.semantic.OntologySeedService` at
  startup. It declares **19** ontologies (verified: 19 `"id":` keys, 19
  `"file":` keys, and 19 matching `*.ttl` files on the classpath).
- The 19: prov-o, dublin-core, schema-org, foaf, qudt, om-2, time,
  geosparql, obo-relations, metadata4ing, simat, lumen-inspired,
  nasa-thesaurus, shepard-experiment, chameo, ssn-sosa, iot-lite,
  iec-61360, metadata4ing-hpmc.

So a presentation caption of "19 Ontologien" is **accurate** against the
current manifest. The "only 11" figure in the audit brief is itself
outdated (the manifest grew via ONT1a/ONT1b/AI1r/N1e/N1k/M4I-f/IOT1
additions — its own header comment narrates the growth from 8 → 19).
**No content gap.** If anything, the risk is the *reverse*: docs or
slides that still say "11" or "8" are stale and should be bumped to 19.

---

## Opportunities

**Single consolidating fixes (high leverage):**

1. **L1 frontend — one tolerant unwrap helper.** Add a
   `unwrapList<T>(body): T[]` (`Array.isArray(b) ? b : (b?.items ?? [])`,
   the `useFetchPlugins`/`annotated.ts` shape) and route all ~12 `.items`
   readers through it. One helper, ~12 one-line call-site edits, kills the
   entire fragility class regardless of which envelope style a given
   endpoint uses.
2. **L2 — fix `ReferenceV2IO` to carry a wire-safe numeric handle, OR
   stop gating on it.** Two clean options: (a) add an explicit
   `referenceId` (or keep `id`) field to `ReferenceV2IO` that is *not*
   suppressed — the numeric id is needed by three pages and the
   suppression buys nothing the appId doesn't; or (b) migrate the three
   broken pages' v1 sub-calls to appId-keyed v2 endpoints
   (`/v2/references/{appId}/content`, timeseries payload by appId) and
   delete the numeric-id resolution entirely. Option (b) is the
   CLAUDE.md-aligned direction but larger; option (a) unblocks all three
   CRITICALs in one IO edit. **Recommend (a) now, (b) as the TS-IDc
   follow-through.**
3. **L1 backend — converge the list envelope.** `DataObjectV2Rest` is the
   odd one out (bare array + header pagination). Either move every list to
   `PagedResponseIO` (one canonical envelope) or document the bare-array+
   headers convention as the standard and migrate the bespoke `*PageIO`s
   to match. Three coexisting conventions is the actual root cause; pick
   one. (Note: changing `DataObjectV2Rest`'s wire shape is a breaking
   change for its current consumers — do it behind `?envelope=true` or in
   a coordinated FE+BE PR.)

**Per-instance fixes (small, independent):**

4. **L3** — branch `scene-graphs/play` + `afp-thermo-overlay` on
   `templateKind` (2 files).
5. **L4** — `useJupyterConfig` fail-soft on the public read (1 file).
6. **L6** — move the client cache-bust into an npm prebuild hook (1 file).

## Real-world impact

- The **three L2 CRITICALs** are precisely the screenshot failures: a
  reviewer opening a timeseries / file / structured-data reference detail
  page on a uuid route (the v2-only frontend's *normal* route) gets an
  eternal spinner or a false "not found". This is the most visible,
  most-reported breakage and it is on the primary drill-down path
  (Collection → DataObject → Reference).
- **L1 backend inconsistency** is a latent landmine: every new list
  consumer is a coin-flip on which envelope the author assumed. The
  cost compounds — each new endpoint and each new composable adds a
  potential silent-empty.
- **L4** erodes trust on core pages: a red error toast on a DataObject
  page tells a researcher "something is broken" when Jupyter simply
  isn't installed — the canonical plugin-optional-should-be-quiet miss.
- **L3** is narrow (scene-graph/AFP demo surfaces) but it's a
  presentation-critical demo path (the AFP thermal overlay is a flagship
  MFFD demo).

External grounding: Zalando's API guidelines mandate a single, consistent
pagination envelope with an `items` array as the *one* collection shape —
exactly the convergence L1 needs. RFC 9457 problem+json's
"clients ignore unknown members / dispatch on media type" model is the
fail-soft contract L4 violates by toasting on an expected-absent plugin.
OpenAPI-generator guidance ("delete the generated dir before regen;
regenerate on every build, automatically") is precisely the L6 safeguard
the Makefile implements — and the reason a hand-run `npm install` is the
remaining hole.

## Gaps & blockers

- **L2 fix choice is a fork in the road.** Option (a) (un-suppress a
  numeric handle) contradicts the "suppress numeric id on the v2 wire"
  principle; option (b) (appId-key the v1 sub-calls) needs the v2
  timeseries-payload-by-appId and file-content-by-appId endpoints to
  exist. `/v2/references/{appId}/content` exists (`ReferencesV2Rest:385`);
  the **timeseries payload by appId** path is the blocker — I did not
  confirm a v2 payload-by-appId endpoint. If it's missing, the timeseries
  page can't take option (b) cleanly and needs option (a) as a bridge.
- I could not fully trace whether every intolerant L1 frontend reader's
  backend *always* returns the envelope under all query-param
  combinations (e.g. error/empty branches). `DataObjectV2Rest:250`
  proves an endpoint can return a *different* shape on an edge branch
  (`"[]"` string vs the header-bearing array) — so even "verified
  envelope" endpoints may have an untested empty/error branch that bare-
  arrays. The tolerant-helper fix (Opportunity 1) sidesteps this entirely.
- L5 has no code artifact to verify; it's a runbook addition.

## What surprised me

1. **L7 inverted.** The audit brief asserts V49 seeds 11 vocabularies and
   the slide overclaims 19. The opposite is true: V49 seeds none, the
   manifest seeds 19, and the slide is correct. The "11" figure is the
   stale one. The seeding moved out of the Cypher migration into a
   manifest-driven `OntologySeedService` and the brief's mental model
   didn't follow.
2. **The L2 timeseries page looks fixed but isn't.** It carries an
   elaborate fix comment (BUG-COLL-APPID-ROUTE-007-REFPAGE / UX612-C1)
   and loads `referenceV2` first — yet `referenceV2.value?.id` is
   *guaranteed undefined* because the very IO it loads suppresses `id`.
   The composable's docstring asserts the field exists. A fix that
   documents itself as correct while reading a suppressed field is the
   most dangerous shape in this whole audit.
3. **Three live envelope conventions** in one `/v2/` surface
   (PagedResponseIO, three bespoke `*PageIO`/`*ResultIO`, and bare-array+
   headers). The bare-array one is the *primary* DataObject list. The
   inconsistency isn't drift at the edges — it's at the center.
4. **L6 is the one class that's genuinely solved** — and even there the
   safeguard lives in the Makefile, not the package, so a hand-run build
   re-opens the hole.

---

## Prioritized fix rows to file (user-value × effort)

| # | Row id (proposed) | Class | Files | Why first |
|---|---|---|---|---|
| 1 | `FIX-L2-REF-NUMERICID` | L2 | `v2/references/io/ReferenceV2IO.java` (add un-suppressed `referenceId`); `…/timeseriesereferences/[…]/index.vue:68`; `…/filereferences/[…]/index.vue:42`; `…/structureddatareferences/[…]/index.vue:39` | unblocks 3 CRITICAL eternal-spinners on the primary drill-down path; smallest backend edit that fixes all three |
| 2 | `FIX-L4-JUPYTER-FAILSOFT` | L4 | `composables/context/admin/useJupyterConfig.ts:82` | one-line; removes a trust-eroding red toast from core pages; pure win |
| 3 | `FIX-L3-MATERIALIZE-KIND-BRANCH` | L3 | `pages/scene-graphs/play/[templateAppId].vue:58`; `pages/afp-thermo-overlay/[templateAppId].vue:45` | unblocks the flagship AFP/scene-graph demo; route VIEW_RECIPE → `/v2/shapes/render` |
| 4 | `FIX-L1-FE-TOLERANT-UNWRAP` | L1(b) | new `utils/unwrapList.ts` + ~12 call sites (`useFetchCollectionContainers`, `useFetchTemplates`, `useFetchFeatureToggles`, `useInstanceAdmins`, `useAasShells`, `useGlobalSearch`, the `?? []`-only readers) | kills the entire silent-empty fragility class; low effort per site |
| 5 | `FIX-L2-COLLECTION-PANELS` | L2 | `pages/collections/[collectionId]/index.vue:48` + the `v-if=collectionNumericId` panels | restores lineage / cross-track / NDT panels on uuid routes (currently silently hidden) |
| 6 | `FIX-L1-BE-ENVELOPE-CONVERGE` | L1(a) | `DataObjectV2Rest.java:250,325` + bespoke `*PageIO` siblings | converge to one envelope; larger/breaking → behind `?envelope=true` or coordinated PR |
| 7 | `FIX-L2-CONTAINER-NUMERICID` | L2 | `pages/containers/{structureddata,timeseries,files}/[containerId]/index.vue` | latent wrong-id-0 on uuid routes; lower urgency (data still loads via appId) |
| 8 | `FIX-L6-PREBUILD-HOOK` | L6 | `frontend/package.json` (prebuild) / `frontend/scripts/` | move the cache-bust off the Makefile so hand-runs stay safe |
| 9 | `DOC-L5-TRIAGE-TOKEN-REFRESH` | L5 | `docs/admin/runbooks/` regression-triage page | add token-refresh-before-classifying-4xx step |
| 10 | `DOC-L7-ONTOLOGY-COUNT` | L7 | any slide/doc citing "11"/"8" ontologies | bump to 19 (cite `ontologies-manifest.json`) |

Sources:
- [Zalando RESTful API Guidelines — pagination chapter](https://github.com/zalando/restful-api-guidelines/blob/main/chapters/pagination.adoc) (consistent `items[]` collection envelope)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) (clients ignore unknown members → fail-soft / graceful degradation, basis for L4)
- [OpenAPI Generator — typescript-fetch generator docs](https://openapi-generator.tech/docs/generators/typescript-fetch/) + [type-safe API codegen 2026](https://www.saschb2b.com/blog/typesafe-api-codegen-2026) (delete generated dir before regen; regenerate automatically — basis for L6)
