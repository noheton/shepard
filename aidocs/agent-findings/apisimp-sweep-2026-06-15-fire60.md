---
stage: deployed
last-stage-change: 2026-06-15
---

# APISIMP sweep — fire-60 (2026-06-15)

Routine V2 surface scan following the CONT-NS-COLLAPSE + CROSS-TS-BULK + VIDEO-ANNOT
series (fires 56–59). Checks: stale tracker entries, per-kind bespoke endpoints,
numeric-id leaks, absolute DLR URIs in problem types, inconsistent pagination,
empty-body 4xx, forbidden v1 path additions.

---

## Summary

**No new code-level violations found.** The surface is clean after accounting for
pending PRs (#1951–#1955, all 11/11 CI green + mergeable=clean as of this fire).
One stale tracker entry corrected; one L-size structural gap formally filed.

---

## Finding 1 — Stale tracker: APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS already shipped

**Severity:** tracker-only (no runtime impact)

The `aidocs/16` row for `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS` listed status `queued`
but the fix was shipped as PR #1919 (commit `f344abe`, 2026-06-15 06:50 UTC,
authored by noheton). All three files now carry relative `/problems/…` URIs:

| File | Line | Before | After |
|---|---|---|---|
| `plugins/git/.../GitReferenceRest.java` | 105 | `"https://shepard.dlr.de/problems/git.adapter.unsupported-host"` | `"/problems/git.adapter.unsupported-host"` |
| `plugins/kip/.../KipResolverRest.java` | 117, 126 | `"https://shepard.dlr.de/problems/kip.pid.not-found"` (×2) | `"/problems/kip.pid.not-found"` |
| `plugins/v1-compat/.../LegacyV1GateFilter.java` | 45 | `"https://shepard.dlr.de/problems/v1-disabled"` | `"/problems/v1-disabled"` |

Confirmed by: `grep -rn "shepard.dlr.de/problems" /home/user/shepard/ --include="*.java"` → 0 results.

**Action:** `aidocs/16` row 3693 updated to `✓ shipped (PR #1919, ~fire-41)` in this commit.

---

## Finding 2 — Residual per-kind reference endpoints outside unified surface (L-size)

**Severity:** MAJOR (structural — long-term)

After the CONT-NS-COLLAPSE series unified all per-kind *container* endpoints and the
CROSS-TS-BULK rename dissolved the last lone `/v2/timeseries/` sub-namespace, two
bespoke reference endpoint groups remain outside the unified `/v2/references?kind=`
surface (`ReferencesV2Rest`):

### `/v2/files` (FileReferenceV2Rest)

- `POST /v2/files` — multipart binary upload (`multipart/form-data`; creates singleton FileReference)
- `GET /v2/files/{appId}` — get by appId
- `GET /v2/files/{appId}/content` — stream file bytes
- `GET /v2/files/by-data-object/{dataObjectAppId}` — list files for a DataObject
- `PATCH /v2/files/{appId}` — update metadata
- `DELETE /v2/files/{appId}` — delete

The existing `POST /v2/references?kind=file` in `ReferencesV2Rest` handles JSON-only
non-binary reference creation (the `@Consumes(APPLICATION_JSON)` on the endpoint).
Binary file uploads require `multipart/form-data`, which is a different content-type
and can't simply be routed through the existing `POST /v2/references` method.

Design options:
1. **Multipart overload**: `POST /v2/references?kind=file` gains a second `@Consumes(MULTIPART_FORM_DATA)` method variant (JAX-RS allows multiple `@Consumes` methods at the same path with different media types).
2. **Content companion**: `POST /v2/references/{appId}/content` accepts the binary after the reference metadata is created (split into two calls; cleaner for large files).
3. **Presign-first**: `POST /v2/references?kind=file` creates only metadata + returns a presigned upload URL; caller uploads directly to storage; `POST /v2/references/{appId}/content/commit` seals it. Aligns with the existing `ContainersV2Rest` presign pattern.

Option 3 is the most consistent with the unified containers surface pattern (which already has `upload-url` + `commit`). Operator feedback needed before code.

### `/v2/bundles` (FileBundleReferenceRest)

Complex sub-resource tree:
- `GET /v2/bundles/{bundleAppId}` — get bundle with FileGroups
- `GET /v2/bundles/{bundleAppId}/groups` — list FileGroups
- `POST /v2/bundles/{bundleAppId}/groups` — create FileGroup
- `GET /v2/bundles/{bundleAppId}/groups/{groupAppId}` — get group
- `PATCH /v2/bundles/{bundleAppId}/groups/{groupAppId}` — update group
- + more (delete, file-level operations)

The sub-resource shape (`/bundles/{id}/groups/{groupId}`) is harder to unify under
`/v2/references?kind=bundle` without redesigning the group sub-resource paths.
Target: `/v2/references/{bundleAppId}/groups/{groupAppId}` or
`/v2/references/{bundleAppId}/groups` (keeping sub-resource pattern, replacing prefix).

**Action:** Filed as `APISIMP-KIND-DISCRIMINATOR` (L, 4-slice delivery) in `aidocs/16`.
Design-first; first code slice gated on operator verdict on the binary-upload pattern.

---

## Surface health summary (post pending-PRs)

| Area | Status |
|---|---|
| New `@Path(Constants.SHEPARD_API + ...)` additions | ✅ zero |
| Per-kind container endpoints | ✅ unified (PRs #1950/1951 pending); `/v2/file-containers` / `/v2/timeseries-containers` / `/v2/structured-data-containers` deleted by #1951 |
| Per-kind reference endpoints | ⚠️ `/v2/files` + `/v2/bundles` remain (APISIMP-KIND-DISCRIMINATOR, L) |
| Absolute `https://shepard.dlr.de/problems/` URIs | ✅ zero (core + plugins) |
| Pagination `?size` params on list endpoints | ✅ zero after PR #1954 merges |
| Empty-body 4xx in containers cluster | ✅ zero after PR #1955 merges |
| Numeric id leaks in `@PathParam`/`@QueryParam` | ✅ none (provenance `since`/`until` Longs are Unix ms timestamps, not Neo4j IDs) |
| Plugin per-kind namespace violations | ✅ none (AAS + KIP + Spatial allowlisted/verdicted) |
| Bespoke admin config REST (not on registry) | ✅ none (V2CONV-A4 + V2CONV-A7-PLUGIN-ADMIN-CONFIG fully shipped) |

---

## New rows filed in aidocs/16

| Row ID | Size | Description |
|---|---|---|
| `APISIMP-KIND-DISCRIMINATOR` | L | Unify `/v2/files` + `/v2/bundles` under `/v2/references?kind=file|bundle`; design-first. |
