---
stage: deployed
last-stage-change: 2026-07-08
last-touched: 2026-07-08
---

# APISIMP sweep — fire-473 — 2026-07-08

## Scope

Continuation of the APISIMP (API Simplification) workstream.

**fire-473 opened** from `main` at SHA `9794f1ae` (PR #2400 squash-merged).

fire-473 actions:
1. Verified PR #2400 (APISIMP-VOCAB-BROWSE-SCHEMA-MISMATCH) passed doc-stage-check (fix pushed as SHA `b95b6c6`) and was squash-merged.
2. Ran a fresh sweep of `/v2` REST resources for annotation-quality findings.
3. Found and filed **APISIMP-PAGE-PARAM-MIN0** (7 sites, 5 files).
4. Implemented fix, opened PR #2401.

---

## §A — Findings

### §A1 · APISIMP-PAGE-PARAM-MIN0 — `@Min(0)` vs `@PositiveOrZero` on page params

**Status:** 🔄 in-flight (PR #2401)

**Finding:** Seven v2 REST endpoints declare `@DefaultValue("0") @QueryParam("page") @Min(0) int page`
instead of the standard `@PositiveOrZero` used by the majority of v2 list endpoints.

Both annotations enforce `>= 0` on primitive `int` — zero behavioural difference at runtime.
`@PositiveOrZero` is the fork convention (used by 15+ v2 endpoints) and is semantically richer:
it communicates "zero-indexed, non-negative" intent more precisely than a raw `@Min` constraint.

**Affected sites (7 occurrences, 5 files):**

| File | Method | Line (approx) |
|---|---|---|
| `ReferenceAnnotationRest.java` | `list()` | 164 |
| `ShapesPredicatesRest.java` | `predicates()` | 101 |
| `ContainersV2Rest.java` | `listChannelAnnotations()` | ~1014 |
| `ContainersV2Rest.java` | `listTemporalAnnotations()` | ~1130 |
| `BundleGroupsV2Rest.java` | `listGroups()` | 133 |
| `ShepardTemplateRest.java` | `list()` | 92 |
| `ShepardTemplateRest.java` | `tags()` | 302 |

**Fix applied:**
- Swapped `@Min(0)` → `@PositiveOrZero` at all 7 sites.
- Added `import jakarta.validation.constraints.PositiveOrZero` to the three files that lacked it
  (`ReferenceAnnotationRest.java`, `ShapesPredicatesRest.java`, `ShepardTemplateRest.java`).
- `BundleGroupsV2Rest.java` and `ContainersV2Rest.java` already had the import; only the annotation changed.

**Runtime impact:** None. Annotation-only change.

**AC:** Zero `@Min(0) int page` occurrences remain in v2 REST resources; `mvn verify -pl backend` green.

---

## §B — Non-findings (checked, no action)

### §B1 · DataObjectV2Rest bare-array paging

`DataObjectV2Rest` returns a bare `List<>` with `GitHub-REST-style` pagination via
`Content-Range` + `X-Total-Count` response headers and a `?fields=` projection param.
This is intentional — confirmed by inspecting the resource and the `Content-Range` header
wiring. Not a finding.

### §B2 · BundleGroupsV2Rest PagedFilesIO non-standard envelope

`listBundleGroupFiles()` returns a `PagedFilesIO` envelope with fields
`{items, page, size, totalElements, totalPages}` instead of the standard
`{items, total, page, pageSize}`. Wire-breaking to normalize; deferred.
Not tackled in fire-473.

---

## §C — Sweep coverage

Files scanned for `@Min(0)` on `page` params across `backend/src/main/java/de/dlr/shepard/v2/`:

```
grep -r "@Min(0)" --include="*.java" de/dlr/shepard/v2/
```

All 7 occurrences were in the 5 files listed in §A1. No other page-param annotation
inconsistencies found.
