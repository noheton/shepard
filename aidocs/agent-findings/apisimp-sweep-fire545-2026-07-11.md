---
stage: deployed
last-stage-change: 2026-07-11
---

# APISIMP Sweep — fire-545 (2026-07-11)

Triggered by: all named APISIMP/V2CONV rows in `aidocs/16` through the end of the file
are now shipped (last shipped: `APISIMP-SNAP-MANIFEST-PAGEPARAM`, PR #2478, fire-545).

## Scope

Scanned: `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` (31 files with
pagination params), plus plugins (no new findings).

## Summary

The v2 REST surface is in good shape. The systematic one-by-one sweep of the last
several fires (fire-519 through fire-545) has cleared the major classes of findings:

- `new ProblemJson(` inline: **0 remaining** (fully resolved — all calls go through `ProblemResponse.problem()`)
- `@Parameter` missing on pagination params: **0 remaining** — all 31 paginated v2 REST
  files have consistent `@Parameter` coverage. The scan found several apparent gaps
  that were false positives from multiline `@Parameter(...)` blocks or class-level
  `@Parameter` declarations (confirmed by reading each file).
- `@Max` non-standard page caps: **0 remaining** in v2/ or plugins. Remaining `@Max`
  usages with non-200 values are domain constraints (`maxPoints`, `maxItems`,
  image `size`, etc.) — not page-size caps.
- Non-standard path param names (`{collectionAppId}`, `{dataObjectAppId}`, etc.):
  **0 remaining** in primary-entity positions. All 2-param paths use qualified names
  correctly for their secondary entity.

## Finding filed

**`APISIMP-PUBLICATION-GONE-PARAMS`** (XS) — only confirmed finding this sweep.

`PublicationsListRest.java:56-63` is the `GET /v2/{kind}/{appId}/publications`
tombstone endpoint (filed as `APISIMP-PUBLICATIONS-KIND-410` and shipped in an
earlier fire). The `list()` method still declares two pagination params that are
never read — the entire body is `return gone()`:

```java
public Response list(
  @PathParam("kind") String kind,
  @PathParam("appId") String appId,
  @QueryParam("page") @DefaultValue("0") int page,       // ← dead
  @QueryParam("pageSize") @DefaultValue("50") int pageSize  // ← dead
) {
  return gone();
}
```

Problems:
1. Dead params appear in the OpenAPI spec as documented query params on a 410-only
   endpoint — confusing to API consumers.
2. Imports `@DefaultValue` and `@QueryParam` for no purpose.
3. Missing standard validators (`@PositiveOrZero`, `@Min(1)`, `@Max(200)`) —
   these don't matter functionally (params are ignored) but would fail a validator
   audit sweep.

Fix: delete the two param declarations + remove unused imports.

## What was NOT found

- No `ProblemJson` inline construction in v2 or plugin REST resources.
- No list endpoints returning raw `List<T>` without `PagedResponseIO` (the one false
  positive — `AdminConfigRest`, `SemanticAdminRest`, `PluginsAdminRest`,
  `AdminUserGitCredentialRest` — use small known-bounded lists where paging isn't
  warranted; these are admin-only endpoints listing registry entries, not
  user-data collections).
- No v1 `@Path(Constants.SHEPARD_API + ...)` in v2 package (zero results).
- `@PathParam("shepardId")` in `ContainersV2Rest.java` (timeseries channel identity)
  is intentional pre-rename of the timeseries-channel UUID identifier; tracked
  under task #123 (coordinated `appId` → `shepardId` rename). Not an ad-hoc finding.

## Next sweep trigger

When `APISIMP-PUBLICATION-GONE-PARAMS` ships: run another sweep targeting
the `X-Total-Count` deprecation window (`APISIMP-PAGINATION-ENVELOPE` tracked row)
— the header is still emitted on ~12 list endpoints during the deprecation window.
Once clients have migrated to reading from `PagedResponseIO.total`, the headers can
be silently dropped (no wire-shape change).
