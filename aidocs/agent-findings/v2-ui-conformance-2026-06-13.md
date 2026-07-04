---
stage: audited-by-personas
last-stage-change: 2026-06-13
---

# Strict v2 UI conformance sweep — 2026-06-13

Enforcement pass after the v2 surface convergence + type-safe client regen + `@Tag`
cleanup landed ("after simplification"). The v2-only rule (`CLAUDE.md §"this fork's
frontend builds on /v2/ exclusively"`) is now enforceable. This sweep (a) fixed the
operator-reported live annotation crash, (b) eliminated every reachable numeric-id
leak in the annotation path, and (c) produced the verdict table for the remaining
`useShepardApi` (v1 helper) call sites.

## The live bug — fixed first (canonical instance)

**Symptom (operator, 2026-06-13):** *"Required parameter collectionId was null or
undefined when calling getAllCollectionAnnotations()"* on
`https://shepard.nuclide.systems/collections/019eb02b-…`.

**Root cause:** `composables/annotated.ts` `AnnotatedCollection` /
`AnnotatedDataObject` / `AnnotatedReference` called the **v1-singular**
`SemanticAnnotationApi` (via `useShepardApi`) keyed on numeric `collectionId` /
`dataObjectId`. They were constructed with `collection.id` / `dataObject.id` /
`value.id` — but the numeric `.id` was dropped from v2 entities, so the key was
`undefined` and the v1 client threw before the request even left the browser. ~16
construction sites all passed numeric ids; every one was a latent crash.

**Fix:** converged the whole `Annotated` family onto the v2 polymorphic annotation
surface — the typed `SemanticAnnotationsApi` (plural; `listAnnotations` /
`createAnnotation` / `deleteAnnotation`, keyed by `subjectAppId` + `subjectKind`)
via `useV2ShepardApi`. `AnnotationDialog.vue` already used this same `/v2/annotations`
surface for its `subjectAppId` path; `annotated.ts` now converges onto it instead of
keeping two annotation paths.

### What `annotated.ts` looks like now

A single abstract base `SubjectAnnotated` keyed by `(subjectAppId: string,
subjectKind)`, routing through `useV2ShepardApi(SemanticAnnotationsApi)`:

- `fetchAnnotations()` → `listAnnotations({ subjectAppId, subjectKind, pageSize: 200 })`,
  mapping each `AnnotationV2` onto the legacy `SemanticAnnotation` shape the UI chips
  still consume (a sequential `fakeId` is the v-for key + delete handle; the real
  annotation `appId` is kept in a private `_appIdMap`).
- `deleteAnnotation(fakeId)` → resolves the real appId from `_appIdMap` →
  `deleteAnnotation({ appId })`.
- `addAnnotation(payload)` → `createAnnotation({ createAnnotationV2: { subjectAppId,
  subjectKind, predicateIri, objectIri|objectLiteral } })`.
- Empty `subjectAppId` fails soft (returns `[]`, no network call).

The thin subclasses differ only by `subjectKind`: `AnnotatedCollection`
(`"Collection"`), `AnnotatedDataObject` (`"DataObject"`), `AnnotatedReference`
(`referenceKind` arg, default `"Reference"`), `AnnotatedTimeseriesContainer` /
`AnnotatedFileContainer` / `AnnotatedStructuredDataContainer`. The constructors now
take the **appId string**, not numeric ids. `AnnotatedChannel` (TS-SEMANTIC-REST,
nested channel route) and `AnnotatedTimeseries` (upstream per-channel route) are the
two named fallbacks, kept as-is with `// why` comments + backlog rows.

### Construction sites repointed to appId (all reachable crashes — fixed this PR)

| File | Was | Now |
|---|---|---|
| `pages/collections/[collectionId]/index.vue` | `AnnotatedCollection(collection.id)` ×2 | `AnnotatedCollection(collection.appId)` + `v-if` guard |
| `pages/.../dataobjects/[dataObjectId]/index.vue` | `AnnotatedDataObject(collection.id, dataObject.id)` ×2 | `AnnotatedDataObject(dataObject.appId)` |
| `pages/.../filereferences/[fileReferenceId]/index.vue` | `AnnotatedReference(numeric ×3)` ×2 | `AnnotatedReference(fileReference.appId, 'FileReference')` |
| `pages/.../structureddatareferences/[…]/index.vue` | `AnnotatedReference(numeric ×3)` ×2 | `AnnotatedReference(structuredDataReference.appId, 'StructuredDataReference')` |
| `pages/.../timeseriesereferences/[…]/index.vue` | `AnnotatedReference(numeric ×3)` ×3 | `AnnotatedReference(timeseriesReferenceAppId, 'TimeseriesReference')` |
| `MetadataCompletenessCard.vue` | `AnnotatedCollection(props.collection.id)` | `AnnotatedCollection(props.collection.appId)` |
| `DataObjectDataReferencesTable.vue` | `AnnotatedReference(collectionId, dataObjectId, value.id!)` + numeric dialog | `AnnotatedReference(value.appId, legacyKindFor(type))` (meta now carries `appId`) |
| `DataObjectRelationshipsTable.vue` | `AnnotatedReference(collectionId, dataObjectId, value.referenceId)` ×2 | `AnnotatedReference(value.referenceAppId, value.referenceKind)` (table element carries `referenceAppId`/`referenceKind`) |
| `LinkedDataObjectRow.vue` | `AnnotatedDataObject(dataObject.collectionId, dataObject.id)` | `AnnotatedDataObject(doAppId)` |
| `ReferencedByRow.vue` | `AnnotatedDataObject(numeric)` + `AnnotatedReference(numeric ×3)` | `AnnotatedDataObject(doAppId)` + `AnnotatedReference(ref.appId, 'TimeseriesReference')` |
| `OpenIn3dViewButton.vue` | `AnnotatedReference(collectionId, dataObjectId, fileReferenceId)` | `AnnotatedReference(props.fileReferenceAppId, 'FileReference')` (numeric props dropped) |
| `ShowTimeseriesReferenceDialog.vue` | `AnnotatedReference(collectionId, dataObjectId, timeseriesReferenceId)` | `AnnotatedReference(props.timeseriesReference?.appId, 'TimeseriesReference')` |
| `useReferenceTemplatePrefill.ts` | `AnnotatedDataObject(collectionId, dataObjectId)` | `AnnotatedDataObject(dataObjectAppId)` (signature → `(dataObjectAppId: string)`) |
| container pages (`files`/`timeseries`/`structureddata`) | `Annotated*Container(containerAppId)` | unchanged (already appId-keyed; base class is now v2-typed under the hood) |

Supporting carriers added (additive, nullable; no numeric plumbing rewritten):
- `dataTableElement.ts` meta + `dataTableElementMappingUtil.ts`: legacy reference rows
  now carry `meta.appId` (read defensively from the `DataReference` wire shape).
- `relationshipTableElement.ts` + mapper: `information.referenceAppId`/`referenceKind`
  + `actions.referenceAppId`/`referenceKind` (annotatable relationship rows).
- `CreateDataReferenceDialog.vue` + `AddRelationshipDialog.vue`: new `dataObjectAppId?`
  prop drives the v2 template-prefill annotation pull; the numeric `collectionId`/
  `dataObjectId` reference-create plumbing is **dispatcher-owned** (V2-SWEEP-004) and
  left untouched.

## Verdict table — every `useShepardApi` call site

~110 v1-helper call sites across `frontend/`. None pairs a v2 client with the v1
helper (the `/shepard/api/v2/...` 404 class is **clean** — zero hits for
`useShepardApi(*V2Api | *AnnotationsApi | ReferencesApi)`). Grouped by family:

| Family / client | Sites | Verdict | Reason |
|---|---|---|---|
| `SemanticAnnotationApi` (v1 singular) — annotation accessors | `annotated.ts` ×3 classes | **MIGRATED** | The live bug. → `SemanticAnnotationsApi` via `useV2ShepardApi`. |
| `SemanticAnnotationApi` — MFFD NDT grid/probe | `MffdNdtGridCard.vue`, `useMffdNdtGridProbe.ts` | **deferred** (V2UI-MFFD-NDT-ANNO-V2) | Numeric id resolved from loaded v2 collection (documented fallback); works, not a crash. MFFD NDT cluster. |
| `TimeseriesContainerApi` — per-channel annotations | `annotated.ts` `AnnotatedTimeseries` | **keep-fallback** (V2UI-TS-ANNO-V2) | Upstream per-channel numeric route; TS channel content has no v2 appId twin yet (`aidocs/platform/87`). |
| `*ReferenceApi` (File/Timeseries/StructuredData/Uri/Collection/DataObject) | `useCreate/Delete/FetchReferences`, create dialogs, `ReferencedByRow`, ref detail pages | **deferred-to-dispatcher** (V2UI-REF-CREATE-V2, V2UI-REFERENCEDBY-LIST-V2) | Owned by **V2-SWEEP-004-REF-API-MIGRATION** (in-flight). Do NOT touch. |
| `*ContainerApi` (File/Timeseries/StructuredData/Spatial) — content + accessors | `*ContainerAccessor.ts`, container pages, `useFetchChannelPreview`, charts | **deferred-to-dispatcher** | Owned by V2-SWEEP-003-CONTAINER-API-MIGRATION / CONTAINER-V2-ROUTE. |
| `CollectionApi` / `DataObjectApi` | `CollectionAccessor`, `useFetchCollection`, sidebar, export, lineage | **keep-fallback / deferred** | Tracked by BUG-COLL-APPID-ROUTE-005, EXPORT-V2-STREAM, LINEAGE-V2, SIDEBAR-V2-CREATE. Numeric id resolved from v2 entity. |
| `UserApi` / `UserGroupApi` | permissions, members, profile, subscriptions | **keep-fallback** | Genuine v1-only (no v2 twin). UserGroup tracked by V2-SWEEP-002-USERGROUP-V2. |
| `ApikeyApi` / `SubscriptionApi` | api-key + subscription panes | **keep-fallback** | Genuine v1-only; no v2 twin. |
| `SearchApi` | collection/dataobject/container/member search | **keep-fallback** | Genuine v1-only; tracked by SEARCH-V2. |
| `SemanticRepositoryApi` | repository panes | **keep-fallback** | Genuine v1-only. |
| `LabJournalEntryApi` / `CollectionApi.getCollectionRoles` | lab-journal lists | **keep-fallback** | Named-fallback set (`getCollectionRoles`) per CLAUDE.md. |
| `HealthzApi` / `VersionzApi` / `SubscriptionApi` | health/version/about panes | **keep-fallback** | Genuine v1-only ops panes. |

**Counts:** `useShepardApi` sites — **kept (legit fallback / genuine v1-only): ~104**;
**migrated this PR: 3** (the `annotated.ts` annotation classes, which were the crash);
**deferred (filed V2UI-* / owned by dispatcher V2-SWEEP-003/004): ~5 families**.

## Numeric-id leak sweep

- **Annotation path:** zero remaining numeric-id `Annotated*` constructions. Every
  `new AnnotatedReference/Collection/DataObject(...)` now takes an appId string.
- **No v2-client-with-v1-helper 404 pattern** anywhere in `frontend/`.
- **Numeric ids still in routes/props:** the reference-create dialogs + container
  content surfaces still carry numeric `collectionId`/`dataObjectId` — these are the
  dispatcher's V2-SWEEP-003/004 territory, deliberately untouched here, and are not
  reachable crashes (they resolve the numeric id from the loaded v2 entity).

## Gates

- `npm run typecheck` — **0 errors** (after coalescing one `dataObject.appId ??
  '' ` in the AnnotationDialog fallback).
- `npm run lint` — **0 errors** (15 pre-existing warnings, none in touched files).
- `npm run test` — **2007 passed** (added `annotatedSubject.test.ts` ×10;
  `useAnnotations.test.ts` ×7 + `AnnotatedChannel.test.ts` ×8 green).

## What surprised me

The brief expected `annotated.ts` to still be wholly v1, but the **container**
variants (`AnnotatedTimeseriesContainer` etc.) and `AnnotationDialog.vue` had already
moved to the `/v2/annotations` surface via raw `fetch`. The convergence opportunity
was bigger than "fix the three v1 classes": replacing the raw-`fetch` container
classes with the now-typed `SemanticAnnotationsApi` collapsed two parallel annotation
paths into one typed surface, and removed the `v2BaseUrl()` + `authHeaders()` raw-fetch
shims from everything except the genuinely-nested channel-annotation route.
