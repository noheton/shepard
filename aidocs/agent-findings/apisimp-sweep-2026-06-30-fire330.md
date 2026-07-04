---
stage: concept
last-stage-change: 2026-06-30
---

# APISIMP Sweep — fire-330 (2026-06-30)

**Scope:** Full scan of the `/v2/` REST surface for API simplification findings.
**Criteria applied:** numeric Neo4j id leaks, per-kind path prefixes, missing `PagedResponseIO` envelopes, inconsistent `page`/`pageSize` params, non-standard error envelopes, bespoke admin config endpoints outside the unified registry.

## What I found

The v2 REST surface is clean against all sweep criteria. The recent fire-316 through fire-328 work (IO stale path refs, chain pagination, container list envelopes, DO-CHAIN-MISSING-PAGE-PARAMS) has left no new open items.

### No-issue confirmation by criterion

1. **Numeric Neo4j IDs** — All `@PathParam` and `@QueryParam` identifiers use `appId` (UUID v7). No `Long` or numeric IDs exposed on the wire.
2. **Per-kind path prefixes** — No active endpoints under deprecated per-kind paths (`/v2/file-containers/`, `/v2/timeseries-references/`). `APISIMP-FILEREF-V2-TOMBSTONE-DELETE` (merged fire-318) removed the last 410-only residual.
3. **`PagedResponseIO<T>` envelopes** — All list endpoints wrap responses in `{items, total, page, pageSize}`. The single intentional exception (`GET /v2/data-objects` using `Content-Range` + `X-Total-Count`) is documented in row `APISIMP-DO-LIST-CONTENT-RANGE` (merged fire-328, PR #2205).
4. **Pagination param names** — Uniformly `page` + `pageSize` across all v2 list endpoints.
5. **Error envelopes** — All error paths return RFC 7807 `ProblemJson` with `/problems/{category}.{type}` type URIs; no bare-string errors.
6. **Generic AdminConfigRest** — Unified `GET|PATCH /v2/admin/config/{feature}` in place. Bespoke admin classes either retired or narrowly scoped (e.g., `JupyterConfigPublicRest` is an intentional public read-only complement).
7. **IO Javadoc stale path refs** — Resolved by APISIMP-IO-STALE-PATH-REFS (merged fire-325, PR #2203).

### Positive signals

- `SemanticAnnotationV2Rest` — well-structured polymorphic annotation surface; proper auth gating, `ProblemJson` throughout, text-search pagination consistent with v2 contract.
- `SnapshotListRest` (recently added) — cross-collection snapshot picker uses `PagedResponseIO` envelope and permission-aware filtering; follows established conventions.
- Plugin REST classes — no bespoke per-kind CRUD residuals; unified kind-handling via `ReferencesV2Rest`.

## Opportunities

None new. All filed APISIMP rows have been dispatched (merged or in-flight for PR #2206 `APISIMP-ME-NAMESPACE-CONSOLIDATION` + `APISIMP-ADMIN-STORAGE-TAG-DIVERGENCE`).

## Ideas

No new sweep-level ideas this fire. The two in-flight rows in PR #2206 address the remaining namespace and tag divergence items.

## Real-world impact

Surface is in good shape for external client adoption. Consistent envelopes and no numeric-id leaks mean clients built against the v2 surface today will survive the planned `appId → shepardId` rename with a single generated-client swap.

## Gaps & blockers

- `APISIMP-PERMISSION-AUDIT-NEO4J-ID` remains blocked pending L2 migration confirmation.
- PR #2206 (`APISIMP-ME-NAMESPACE-CONSOLIDATION` + `APISIMP-ADMIN-STORAGE-TAG-DIVERGENCE`) awaiting CI pass before merge.

## What surprised me

The surface is cleaner than expected for a v2 API at this stage of development. The consistent enforcement of `PagedResponseIO`, RFC 7807 errors, and appId-only identifiers across ~40 resource classes is good hygiene.

**Verdict:** No new APISIMP rows to file. Dispatch next fire against the first unblocked queued row (PR #2206 if CI green, else revisit `APISIMP-PERMISSION-AUDIT-NEO4J-ID` prerequisites).
