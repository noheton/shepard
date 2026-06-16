---
stage: deployed
last-stage-change: 2026-06-16
audience: advanced
---

# Materialize mapping reference

**Feature ID:** V2CONV-B3  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)

---

## Overview

**MAPPING_RECIPE** is a `ShepardTemplate` kind that encodes a *transform* rather
than a data-kind schema or a view recipe. Where a `VIEW_RECIPE` template projects
a DataObject's channels into a read-only rendered view, a `MAPPING_RECIPE` template
*derives a new output* from existing reference `appId`s you bind at run time:

- A **derived reference** — a brand-new persisted entity (e.g. a
  `TimeseriesReference` of a joint trajectory computed by the KRL interpreter).
- A **view-model** — an ephemeral played/rendered projection that is not
  persisted (e.g. a scene-graph play envelope).

The dispatch mechanism is the same as `VIEW_RECIPE` (shape IRI → registered
executor via a fail-soft registry), but the verb and path are different:
`POST /v2/mappings/{templateAppId}/materialize` (not `POST /v2/shapes/render`).

---

## Endpoint

| Method | Path | Auth |
|--------|------|------|
| `POST` | `/v2/mappings/{templateAppId}/materialize` | Authenticated |

### Path parameter

| Parameter | Type | Description |
|---|---|---|
| `templateAppId` | UUID v7 string | `appId` of the `MAPPING_RECIPE` `ShepardTemplate` to materialize |

### Request body

```json
{
  "inputReferenceAppIds": {
    "srcFileAppId":  "<UUID v7 of the .src FileReference>",
    "urdfFileAppId": "<UUID v7 of the URDF FileReference>"
  }
}
```

`inputReferenceAppIds` is a **binding-role → reference `appId`** map. Keys are
the roles the MAPPING_RECIPE shape declares (e.g. `srcFileAppId`,
`urdfFileAppId`, `urscriptFileAppId`). Values are UUID v7 `appId` strings —
never paths or URLs; the backend resolves them server-side.

The field is optional: a recipe with no required inputs (e.g. the
identity/no-op executor) may receive an empty map or omit the body.

### Response — 200 OK

```json
{
  "templateAppId":         "<UUID v7 of the template>",
  "outputKind":            "REFERENCE",
  "derivedReferenceAppId": "<UUID v7 of the derived reference>",
  "viewModel":             null,
  "executor":              "KrlTrajectoryTransformExecutor"
}
```

| Field | Type | When set |
|---|---|---|
| `templateAppId` | string | Always — echo of the path param |
| `outputKind` | `"REFERENCE"` \| `"VIEW"` | Always — discriminator |
| `derivedReferenceAppId` | UUID v7 string \| null | When `outputKind == "REFERENCE"` |
| `viewModel` | JSON object \| null | When `outputKind == "VIEW"` |
| `executor` | string | Always — name of the `TransformExecutor` that ran |

### Error responses

All errors use `application/problem+json` (RFC 7807):

| Status | Type URN | When |
|--------|----------|------|
| 400 | `/problems/transform.error` | Missing/blank `templateAppId` path param |
| 401 | — | Unauthenticated |
| 404 | `/problems/transform.executor.not-registered` | Template found, but no executor is registered for its `mappingRecipeShape` IRI (plugin not installed) |
| 404 | `/problems/transform.error` | Template `appId` not found |
| 422 | `/problems/transform.error` | Template found but `templateKind != MAPPING_RECIPE` |
| 422 | `/problems/transform.error` | Template body declares no `mappingRecipeShape` IRI |
| 422 | `/problems/transform.input.missing` | A required binding role was not supplied |
| 422 | `/problems/transform.body.invalid` | Template body doesn't parse under the executor's contract |
| 500 | `/problems/transform.internal-error` | Executor threw an untyped exception |

---

## Dispatch internals

1. Load the template by `templateAppId` (404 if absent).
2. Verify `templateKind == "MAPPING_RECIPE"` (422 if not).
3. Parse `mappingRecipeShape` IRI from the template body JSON (422 if absent).
4. Call `TransformExecutorRegistry.resolve(shapeIri)` — fail-soft; returns
   `Optional.empty()` when no executor claims the IRI → 404 (plugin not installed).
5. Build a `TransformRequest` and call `executor.materialize(req)`.
6. Map typed `TransformException` codes to 4xx; unexpected `RuntimeException` → 500.
7. Record a best-effort `EXECUTE` `:Activity` (provenance — fire-and-forget; never
   blocks the primary op). Tell `ProvenanceCaptureFilter` to step back via
   `PROP_SKIP_CAPTURE` so exactly one Activity is produced per call.

---

## `TransformExecutor` SPI

Register a custom executor via
`META-INF/services/de.dlr.shepard.spi.transform.TransformExecutor`
(ServiceLoader, same pattern as `ViewRecipeRenderer`).

```java
public class MyTransformExecutor implements TransformExecutor {

    public static final String MY_SHAPE_IRI =
        "http://semantics.dlr.de/shepard/transform#MyShape";

    @Override
    public Set<String> supportedShapeIris() {
        return Set.of(MY_SHAPE_IRI);
    }

    @Override
    public TransformResult materialize(TransformRequest req) {
        String fileAppId = req.inputReferenceAppIds().get("myFileAppId");
        if (fileAppId == null || fileAppId.isBlank()) {
            throw new TransformException(
                "transform.input.missing", "myFileAppId binding required");
        }
        // ... do work ...
        return TransformResult.reference(derivedRefAppId, name());
    }
}
```

### `TransformRequest` fields available to the executor

| Field | Description |
|---|---|
| `templateAppId` | `appId` of the template being materialized |
| `shapeIri` | The dispatch shape IRI (the key that found this executor) |
| `inputReferenceAppIds` | `Map<role, appId>` of caller-supplied bindings |
| `inputReferenceAppIdValues()` | Flat list of the appId values (for single-input executors) |
| `invokerUsername` | Username of the caller (for provenance attribution) |
| `templateBodyJson` | Raw JSON body of the template (executor parses its own knobs) |

### `TransformException` codes

Throw `TransformException(code, message)` for typed executor failures. The
dispatcher maps these to HTTP status codes:

| Code | HTTP |
|---|---|
| `transform.input.missing` | 422 |
| `transform.input.not-found` | 404 |
| `transform.body.invalid` | 422 |
| `transform.focus.not-found` | 404 |

Any other `RuntimeException` → 500 with `transform.internal-error`.

### Duplicate IRI is fail-fast

Two executors claiming the same shape IRI cause a startup error naming both
registrants. This mirrors the `ViewRecipeRenderer` registry. There is no
silent last-wins behaviour.

---

## Built-in executors

### `NoOpTransformExecutor` (core — always present)

**Shape IRI:** `http://semantics.dlr.de/shepard/transform#IdentityTransformShape`

**What it does:** Identity transform. Returns a `REFERENCE` result whose
`derivedReferenceAppId` is the *first* input reference appId supplied — no new
entity is minted. Useful for testing the materialize path end-to-end without
any plugin installed.

**Example template body:**
```json
{ "mappingRecipeShape": "http://semantics.dlr.de/shepard/transform#IdentityTransformShape" }
```

---

### `KrlTrajectoryTransformExecutor` (`shepard-plugin-krl-interpreter`)

**Shape IRI:** `http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape`

**What it does:** Resolves a KUKA Robot Language (`.src`/`.krl`) FileReference
+ a URDF FileReference, invokes the KRL interpreter sidecar, persists the
resulting joint trajectory as a new `TimeseriesReference` on the target
DataObject, and returns a `REFERENCE` result pointing at that new reference.

**Required bindings:**

| Role | Description |
|---|---|
| `srcFileAppId` | `appId` of the KRL `.src`/`.krl` singleton `FileReference` |
| `urdfFileAppId` | `appId` of the URDF singleton `FileReference` |

Both roles fall back to the template body's `srcFileReferenceAppId` /
`urdfFileReferenceAppId` fields when not supplied by the caller.

---

### `UrScriptTrajectoryTransformExecutor` (core — URSCRIPT-TRAJECTORY-1)

**Shape IRI:** `http://semantics.dlr.de/shepard/transform#UrScriptTrajectoryShape`

**What it does:** Same pattern as the KRL executor but for Universal Robots
URScript programs (`.urscript`/`.script`). Invokes the URScript interpreter
sidecar and persists the derived trajectory as a `TimeseriesReference`.

**Required bindings:**

| Role | Description |
|---|---|
| `urscriptFileAppId` | `appId` of the URScript singleton `FileReference` |
| `urdfFileAppId` | `appId` of the URDF singleton `FileReference` |

---

## UI entry point

**Route:** `/tools/materialize-mapping`

The page is *not* listed in the Tools tile grid — it is always entered
in-context:

1. Open a **DataObject detail page** whose attached template has kind
   `MAPPING_RECIPE`.
2. Click the **"Materialize"** item in the `ActionMenuButton` toolbar.
3. The page opens pre-populated with `?templateAppId=<...>&focusDataObjectAppId=<...>`.

The `TemplateAutocomplete` picker (scoped to `MAPPING_RECIPE` kind) is shown for
manual selection when no pre-population is present. Input bindings are entered as
role / reference-appId pairs — no path or URL fields are exposed.

---

## Worked API example

### 1 — Identity transform (no plugin needed)

```bash
# 1. Find a MAPPING_RECIPE template that targets the identity shape
TMPL=$(curl -s -H "apikey: $KEY" \
  "https://shepard.example.org/v2/templates?kind=MAPPING_RECIPE" \
  | jq -r '.items[0].appId')

# 2. Pick any reference appId (e.g. a FileReference on a DataObject)
REF=$(curl -s -H "apikey: $KEY" \
  "https://shepard.example.org/v2/data-objects/$DO_APP_ID/references" \
  | jq -r '.items[0].appId')

# 3. Materialize — echoes the reference back
curl -s -X POST \
  -H "apikey: $KEY" \
  -H "Content-Type: application/json" \
  "https://shepard.example.org/v2/mappings/$TMPL/materialize" \
  --data "{\"inputReferenceAppIds\":{\"srcFileAppId\":\"$REF\"}}"
```

**Response:**
```json
{
  "templateAppId": "01933d4e-...",
  "outputKind": "REFERENCE",
  "derivedReferenceAppId": "<same as $REF>",
  "viewModel": null,
  "executor": "NoOpTransformExecutor"
}
```

### 2 — KRL trajectory interpret (plugin installed)

```bash
curl -s -X POST \
  -H "apikey: $KEY" \
  -H "Content-Type: application/json" \
  "https://shepard.example.org/v2/mappings/$KRL_TMPL_APP_ID/materialize" \
  --data '{
    "inputReferenceAppIds": {
      "srcFileAppId":  "01933d4e-aaa1-...",
      "urdfFileAppId": "01933d4e-bbb2-..."
    }
  }'
```

**Response:**
```json
{
  "templateAppId":         "01933d4e-ccc3-...",
  "outputKind":            "REFERENCE",
  "derivedReferenceAppId": "01933d4e-ddd4-...",
  "viewModel":             null,
  "executor":              "KrlTrajectoryTransformExecutor"
}
```

The `derivedReferenceAppId` is a newly minted `TimeseriesReference`. You can now
fetch it via the timeseries container API to read the joint trajectory.

---

## Data model

Materialization does not introduce new Neo4j entity *kinds*. The artifacts it touches:

| Entity | How used |
|---|---|
| `:ShepardTemplate` (`MAPPING_RECIPE`) | Template driving the dispatch |
| `Reference` (FileReference, TimeseriesReference, …) | Input bindings + derived output |
| `:Activity` (`EXECUTE`) | Provenance record (best-effort, fire-and-forget) |

---

> **See also:**
> - `docs/reference/view-recipes.md` — `VIEW_RECIPE` / `POST /v2/shapes/render` (the read-only sibling)
> - `docs/reference/template-editor.md` — authoring templates
> - `docs/reference/tools.md` — Tools cluster overview
