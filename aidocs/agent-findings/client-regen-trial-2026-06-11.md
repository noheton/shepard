---
stage: fragment
last-stage-change: 2026-06-11
---

# V2-SWEEP-001-CLIENT-REGEN — trial regen findings (ABORTED)

**Date:** 2026-06-11
**Task:** Regenerate `@dlr-shepard/backend-client` (npm `@dlr-shepard/backend-client`,
consumed by `frontend/` via `file:../backend-client`) from the live v2 OpenAPI
spec so the unified v2 surfaces become type-safe generated classes.
**Verdict:** **ABORTED — not a clean superset.** `backend-client/` was NOT touched.

## TL;DR

The live v2 spec (`https://shepard.nuclide.systems/shepard/doc/openapi/v2.json`,
`info.version = 6.0.0-SNAPSHOT`, 216 paths, 294 operations, 362 schemas) **cannot
be regenerated into a superset of the current client.** Two independent, fatal
blockers:

1. **The spec is invalid for `typescript-fetch` codegen — 113 validation errors,
   the dominant class being 21 distinct `operationId`s reused across 294
   operations** (`list` ×27, `create` ×15, `patch` ×14, `delete` ×13, `get` ×13,
   `getConfig` ×7, `patchConfig` ×6, `read` ×6, …). openapi-generator 7.8.0 aborts
   with `SpecValidationException`. Forcing `--skip-validate-spec` makes it generate,
   but the duplicate IDs collapse into generic-verb method names (`list()`,
   `get()`, `create()`, `patch()`, `delete()`) that **lose every descriptive
   method name the current client has** (`getCollections`, `getDataObject`,
   `createCollection`, …).

2. **The class/model taxonomy is wholesale-renamed**, because the live spec now
   carries JAX-RS `@Tag` names that openapi-generator turns into class names. The
   regen is a *different client*, not an extension of the current one.

Both blockers originate in the **backend** (missing `@Operation(operationId=…)`
annotations + `@Tag` churn) — out of scope for this client-regen task. The client
regen is **blocked on a backend spec-hygiene fix** (file a `V2-SWEEP-001-SPEC-FIX`
prerequisite).

## What the trial produced (evidence)

Trial command (per protocol, to `/tmp/bc-trial`, never in place):

```bash
curl -fsS https://shepard.nuclide.systems/shepard/doc/openapi/v2.json -o /tmp/v2spec.json
npx --yes @openapitools/openapi-generator-cli version-manager set 7.8.0
npx --yes @openapitools/openapi-generator-cli generate \
  -i /tmp/v2spec.json -g typescript-fetch -o /tmp/bc-trial \
  --skip-validate-spec \
  --additional-properties=npmName=@dlr-shepard/backend-client,supportsES6=true,typescriptThreePlus=true
```

Without `--skip-validate-spec` the generator fails outright:

```
SpecValidationException: There were issues with the specification.
| Error count: 113, Warning count: 104
-attribute paths.'/v2/containers'(get).operationId is repeated
-attribute paths.'/v2/references'(get).operationId is repeated
-attribute paths.'/v2/collections'(get).operationId is repeated
... (×113)
```

### Structural divergence (current `backend-client/src` → trial `/tmp/bc-trial/src`)

| Axis | Current | Trial regen | Superset? |
|------|---------|-------------|-----------|
| api classes | 46 | 75 | **NO — renamed** |
| model files | 92 | 356 | NO — renamed + added |
| `runtime.ts` | (7.8.0) | differs byte-for-byte | NO |
| api class names | `CollectionApi`, `DataObjectApi`, `FileContainerApi`, `ShepardTemplateApi`, `UserApi`, `UserGroupApi`, `ReferenceApi`, `ApikeyApi`, `TimeseriesContainerApi`, `StructuredDataContainerApi`, … | `CollectionsV2Api`, `DataObjectsV2Api`, `ContainersV2UnifiedApi`, `TemplatesV2Api`, `ReferencesV2UnifiedApi`, … (`@Tag`-derived) | **NO** |
| method names (collided ops) | `getCollections`, `getDataObject`, `createCollection`, … | `list()`, `get()`, `create()`, `patch()`, `delete()` (23 api files have a bare `async list(`) | **NO** |

**Every existing API class name is GONE** except `MeApi` (verified by file check):

```
GONE: CollectionApi, DataObjectApi, DataObjectV2Api, FileContainerApi,
      ShepardTemplateApi, UserApi, UserGroupApi, TimeseriesContainerApi,
      StructuredDataContainerApi, ApikeyApi, ReferenceApi
PRESENT: MeApi
```

### Hand-patched-field survival check

The CLAUDE.md / task-named hand-patches were each traced:

| Patch | In live spec? | Survives regen? | Notes |
|-------|---------------|-----------------|-------|
| `parentTemplateAppId` (TPL-INHERIT) | YES | renamed schema | now on `ShepardTemplate` / `CreateShepardTemplate` / `PatchShepardTemplate`, **not** `ShepardTemplateIO` |
| template `iconKey` (TEMPLATE-ICONS) | YES | renamed schema | same — `IO` suffix dropped |
| `?fields=` on DO list (DB-OPT5) | YES | YES (as `ListRequest.fields`) | but on renamed `DataObjectsV2Api.list()` |
| `?include=` / `?status=` on DO list | YES | YES | renamed method |
| `Collection.importedFrom` (NEO-AUDIT-007) | YES | YES | — |
| `Collection.license` / `accessRights` (LIC1) | YES | YES | — |
| `DataObject.license` / `accessRights` | YES | YES | — |
| `{Timeseries,StructuredData,File}Container.appId` (V2-SWEEP-003-2 forward-fill) | YES | YES | the forward-fill is now in the spec — good |
| **`listDataObjectsWithCount` helper** (UX-DOPANEL-TOTAL-COUNT) | N/A — **hand-written**, not generatable | **NO — would be wiped** | custom `X-Total-Count`/`Content-Range` header-parse in `DataObjectV2Api.ts:78-106`; consumed by `usePagedDataObjects.ts` + `usePagedDataObjects.dbopt5.test.ts`. openapi-generator never emits this; a regen erases it. |

So even the *fields* that exist would arrive on **renamed classes/models**, and the
one genuinely custom helper would be destroyed.

## Frontend blast radius (why adopt would be catastrophic)

`@dlr-shepard/backend-client` is imported in **257 frontend files**. The renamed
api classes alone touch:

```
ReferenceApi: 23   CollectionApi: 17   UserApi: 12   TimeseriesContainerApi: 12
DataObjectApi: 11  FileContainerApi: 10  StructuredDataContainerApi: 9
DataObjectV2Api: 6  ShepardTemplateApi: 5  UserGroupApi: 4
ShepardTemplateIO (model rename): 15
```

Adopting the regen renames every one of these symbols, so `npm run typecheck`
would surface hundreds of new errors across ~100+ files — the opposite of "zero
new errors." This is not a regen the protocol permits adopting.

## Root cause & recommendation

**Root cause is in the backend, not the client.** The v2 JAX-RS resources don't
set `@Operation(operationId = "…")`, so MicroProfile OpenAPI synthesises generic
IDs from the Java method names (`list`, `get`, `create`, `patch`, `delete`,
`getConfig`, …) and they collide across resources. A clean client regen is only
possible once the spec gives every operation a **unique, descriptive operationId**
(and, ideally, stabilises `@Tag` names so the api-class names match the current
client's, avoiding the 257-file rename churn).

**Recommended path (file these, then re-attempt this task):**

1. **`V2-SWEEP-001-SPEC-FIX` (backend, prerequisite — BLOCKER):** add explicit
   `@Operation(operationId = "<resource><Verb>")` to every v2 resource method that
   currently collides (21 IDs / 294 ops). Verify `v2.json` has 294 distinct
   operationIds and passes `openapi-generator validate` with 0 errors. Without this,
   no clean regen is possible by any generator.
2. **(optional but strongly advised) `@Tag` stabilisation:** name v2 resource
   `@Tag`s so generated class names equal the current client's class names
   (`CollectionApi`, `DataObjectApi`, `FileContainerApi`, `ShepardTemplateApi`, …)
   to keep the regen a true drop-in and avoid the 257-file rename. If renaming is
   accepted, it becomes a coordinated frontend sweep — a separate, large task, not
   a "regen."
3. **Preserve the `listDataObjectsWithCount` helper:** whatever the regen shape,
   the custom `DataObjectV2Api.ts` header-parse helper must be re-applied
   post-regen (it is consumed by `usePagedDataObjects.ts`). Keep it in a small
   hand-maintained extension file (or via `.openapi-generator-ignore` on
   `DataObjectV2Api.ts`) so future regens don't clobber it.
4. **Then re-run this trial-diff-then-conditional-adopt protocol** against the
   fixed spec; with unique operationIds + stable tags it should be a clean
   superset that the frontend typecheck accepts.

## What was NOT touched

- `backend-client/` — untouched (verdict was ABORT before step 4).
- `frontend/` — untouched.
- `clients-v2/*-kiota` — untouched (out of scope).
- backend — untouched (the spec fix is the recommended follow-up, not done here).

Only this findings doc + the `aidocs/16` row flip to `blocked` were committed.
