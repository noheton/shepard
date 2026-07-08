---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP sweep — fire-475 (2026-07-08)

Scope: full v2 REST surface + AAS plugin; focus on X-Total-Count header gaps,
OpenAPI schema drift, and non-standard response envelopes. Batched XS findings
into PR `APISIMP-XCOUNT-BATCH-2` (4 items, implemented same fire).

## Findings

### F1 — APISIMP-TERMSEARCH-NO-XCOUNT (XS) ✅ batch

`SemanticTermSearchRest.java:244` — `GET /v2/semantic/terms/search` wraps results
in `PagedResponseIO` but emits no `X-Total-Count` header. Purely additive fix:
chain `.header("X-Total-Count", (long) results.size())`.

Architectural note: `total = results.size()` because the n10s fulltext index has
no count-only mode; this is pre-existing and documented in the existing PagedResponseIO
field.

### F2 — APISIMP-SNAP-DO-XCOUNT (XS) ✅ batch

`SnapshotPinnedReadRest.java:160` — `GET /v2/collections/{cid}/snapshots/{sid}/data-objects`
already computes `total` via `snapshotService.countDataObjectAppIds(snapshot)` but
does not chain it onto the response as `X-Total-Count`. Missing header only.

### F3 — APISIMP-AAS-SHELLS-NO-XCOUNT (XS) ✅ batch

`AasShellsRest.java` — two list endpoints:
- `GET /v2/aas/shells` (line 121): `total` computed, no header.
- `GET /v2/aas/shells/{aasId}/submodels` (line 210): `total` computed, no header.

Both fixed by chaining `.header("X-Total-Count", total)`.

### F4 — APISIMP-AAS-REG-SCHEMA-DRIFT (XS) ✅ batch

`AasRegistrationAdminRest.java:70,84` — two issues in one endpoint
(`GET /v2/admin/aas/registrations`):
1. `@APIResponse` declared `@Schema(implementation = AasRegistrationIO.class)` but
   actual return type is `PagedResponseIO<AasRegistrationIO>` — schema annotation drift.
   Fixed: changed to `@Schema(implementation = PagedResponseIO.class)`.
2. Missing `X-Total-Count` header. Fixed: chained `.header("X-Total-Count", total)`.

### F5 — APISIMP-BUNDLE-FILES-PAGEFMT (M) 🔴 deferred

`BundleGroupsV2Rest.java:376` — file-group list endpoints return `PagedFilesIO`
with fields `{size, totalElements, content}` instead of standard
`PagedResponseIO` `{items, total, page, pageSize}`. Wire-breaking rename.

**Decision:** do not implement without a design doc covering the deprecation
window and migration path. Filed in backlog; deferred to a future fire that
includes a `/v2/references/{appId}/groups?envelope=v2` migration plan.

### F6 — APISIMP-PROV-CURSOR-PAGED-WRAP (S) queued

`ProvenanceRest.java:160,312` — two cursor-paged activity endpoints use
`PagedResponseIO` with `total=rows.size(), page=0, pageSize=rows.size()`.
These fields are misleading for cursor pagination. No `X-Has-More` or
`X-Next-Cursor` headers. Size S because fix requires a dedicated
`CursorPagedResponseIO` type or header convention, not just a one-liner.

### F7 — APISIMP-SHAPES-BUILD-400-NOTRFC7807 (S) queued

`ShapesBuildRest.java:105-120` — 400 responses carry `Content-Type:
application/problem+json` but body is `ShapeBuildResponseIO` (a bespoke record
with `{shapeIri, shapeGraph, error}`). RFC7807 mandates `{type, title, status,
detail}`. Fix: replace 400 body with `ProblemJson`; keep `ShapeBuildResponseIO`
only for 200.

### F8 — APISIMP-IMPORT-RUNS-BARE (XS) queued

`ImportDiagnosticsV2Rest.java:192` — `GET /v2/import/diagnostics/runs` returns
bare `List<RunSummaryIO>` without `PagedResponseIO` envelope or `X-Total-Count`.
Companion events endpoint (line 154) similarly returns a bare list. Straightforward
wrapping fix for the next XS batch fire.

## Not-findings / known-good

- `AasShellsRest.java:159` — `APISIMP-AAS-SHELL-DO-LOAD-CAP` comment is correct
  design; `probe.size()` capped at `SHELL_MAX_SUBMODELS+1` is intentional memory
  guard, not a finding.
- `ImportDiagnosticsV2Rest.java:154` events endpoint truncation at `limit` is
  documented and intentional; bare list shape is the finding (F8), not the cap.
- Dead `SchemaType` imports seen in sweep — cleaned in-line as incidental noise.

## Batch PR: APISIMP-XCOUNT-BATCH-2

Implements F1–F4. Files changed:
- `backend/.../semantic/resources/SemanticTermSearchRest.java`
- `backend/.../snapshot/resources/SnapshotPinnedReadRest.java`
- `plugins/aas/.../v2/resources/AasShellsRest.java`
- `plugins/aas/.../admin/resources/AasRegistrationAdminRest.java`
