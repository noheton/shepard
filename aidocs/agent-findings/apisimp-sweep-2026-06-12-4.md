---
stage: fragment
last-stage-change: 2026-06-12
---

# APISIMP seventh-pass sweep — 2026-06-12

Scope: v2 REST surface post-APISIMP-ERROR-ENVELOPE-RESIDUALS (#1871, merged this
fire). Focused on error-envelope residuals missed by the prior six passes.
No numeric-id leaks or pagination inconsistencies found (all actioned by previous
sweeps). One class of finding remains: `Map.of("error", ...)` / `Map.of("message", ...)`
non-RFC-7807 4xx/5xx bodies in three resources.

## What I found

**`ShapesRenderRest` — 6 remaining `Map.of("error", ...)` bodies on `POST /v2/shapes/render`:**

`backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesRenderRest.java`

| Line | Status code | Current body | Notes |
|---|---|---|---|
| 201 | 404 | `Map.of("error", "template not found: …")` | template not found |
| 207 | 422 | `Map.of("error", "render not yet supported for templateKind=…")` | wrong template kind |
| 273 | 422 | `Map.of("error", "no renderer registry available for shape: …")` | registry unavailable |
| 279 | 422 | `Map.of("error", "no renderer registered for shape: …")` | no renderer match |
| 453 | 422 | `Map.of("error", "media render failed …", "renderer", renderer.name())` | render failure (2 fields) |
| 641 | 400 | `badRequest(message)` helper → `Map.of("error", message)` | generic 400 helper |

The `badRequest` helper at line 641 is called from many dispatch paths — updating it to
return `ProblemJson` fixes all its call sites in one shot.

**`TemplatePortabilityRest` — 4 remaining `Map.of("error", ...)` bodies on `/v2/templates/export-import`:**

`backend/src/main/java/de/dlr/shepard/v2/template/resources/TemplatePortabilityRest.java`

| Line | Status | Current body | Notes |
|---|---|---|---|
| 124 | 500 | `Map.of("error", "YAML serialisation failed: …")` | export path |
| 181 | 400 | `Map.of("error", "YAML parse error: …", "line", line)` | import parse (2 fields) |
| 199 | 400 | `Map.of("error", "entry[i]: required fields missing", "line", i+1)` | import validation (2 fields) |
| 208 | 400 | `Map.of("error", "entry[i] body invalid: …", "line", i+1)` | import body invalid (2 fields) |

Multi-key maps → `ProblemJson(…, Map ext)` overload carries `"line"` as an extension member
(RFC-7807 §3.2 extension member).

**`AasAdminRest` — 2 remaining `Map.of("error", ...)` on `/v2/admin/aas/...`:**

`plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasAdminRest.java`

| Line | Status | Current body |
|---|---|---|
| 69 | 400 | `Map.of("error", "Bundled template body invalid: …")` |
| 73 | 400 | `Map.of("error", e.getMessage())` |

These are admin endpoints, not IDTA-spec-mandated surfaces. Should be `ProblemJson`.

## Skipped / not new

- AAS `@QueryParam("size")` — IDTA REST standard pagination (Tier-3, spec-frozen). Already noted in prior passes.
- `ThumbnailRest @QueryParam("size")` — pixel dimension, not pagination.
- `SpatialDataPointRest @QueryParam("limit")` — frozen `/shepard/api/` v1 surface; untouchable.
- `PermissionAuditEntryIO.id` — special-case Neo4j orphan diagnostics; decommission after L2.
- APISIMP-PAGINATION-UNIFY slice 1 — still blocked (PR #1870, CodeQL ⚠️).

## Stale backlog entries corrected

These rows had "in-flight" status but were actually merged before this fire:

| Row | Was | Correct | Commit |
|---|---|---|---|
| V2-SWEEP-003-2 | in-flight 2026-06-12 | ✓ shipped | `53638d69` |
| CONTAINER-V2-ROUTE | FE in-flight 2026-06-12 | ✓ shipped (V2-SWEEP-003-2) | `53638d69` |
| V2-SWEEP-004-3 | in-flight 2026-06-12 | ✓ shipped | `43f903bf` |
| V2-SWEEP-004-2 | in-flight 2026-06-11 | ✓ shipped | `b467a246` |
| APISIMP-SNAPSHOT-RESP-SIZE | in-progress | ✓ shipped | `f31c31d2` |
| V2-SWEEP-004-REF-API-MIGRATION | in-flight slice 3 | slices 2+3 shipped | `43f903bf`, `b467a246` |

## Findings filed in aidocs/16

- APISIMP-SHAPES-RENDER-ERROR-ENVELOPE (XS)
- APISIMP-TPL-PORTABILITY-ERROR-ENVELOPE (XS)
- APISIMP-AAS-ADMIN-ERROR-ENVELOPE (XS)

Dispatch order: APISIMP-SHAPES-RENDER-ERROR-ENVELOPE first (highest call-volume path).
