---
stage: fragment
last-stage-change: 2026-06-14
---
# APISIMP Sweep — fire-34 (2026-06-14)

Scope: all Java files under `backend/src/main/java/de/dlr/shepard/v2/` and
`plugins/*/src/main/java/`.

Skipped (frozen upstream-compat): `SpatialDataPointRest`,
`SpatialDataReferenceRest` (APISIMP-NUMERIC-ID-BATCH-2 / APISIMP-V1-PATH-RESIDUAL-1,
already tracked and deferred).

Context: follows fire-33 (APISIMP-ERRORTYPE-BATCH-1 PR #1912 created +
APISIMP-WIKIWRITE-LJERID PR #1911 merged; fire-33 merged PR #1912).
All seven fire-32 sweep rows now shipped.

---

## Findings

### Finding 1: APISIMP-FILEREF-416-BARE-RESPONSE

**File:** `backend/src/main/java/de/dlr/shepard/v2/file/resources/FileReferenceV2Rest.java:378–380`

**Pattern:** Pattern 3 (error response missing body and `application/problem+json` content-type)

**What's wrong:** The range-request handler returns HTTP 416
REQUESTED_RANGE_NOT_SATISFIABLE with only a `Content-Range` header and no body
or content-type. All other 4xx responses in this file use the `problem()` helper
defined in the same class. A bare 416 gives callers no machine-readable error
description.

**Fix:** Replace the bare `Response.status(REQUESTED_RANGE_NOT_SATISFIABLE).build()`
with a `problem("/problems/files.range-not-satisfiable", "Range Not Satisfiable",
REQUESTED_RANGE_NOT_SATISFIABLE, "Byte range start exceeds file size")` call using
the existing helper. Update the OAS `@APIResponse(responseCode = "416")` annotation
to declare `mediaType = "application/problem+json"`.

**Proposed row ID:** `APISIMP-FILEREF-416-BARE-RESPONSE`

**Size:** XS

---

### Finding 2: APISIMP-PUBLISH-REP-EXPORT-URN-FORMAT

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/publish/resources/PublishRest.java` (~13 sites)
- `backend/src/main/java/de/dlr/shepard/v2/export/rep/RepExportV2Rest.java` (~8 sites)

**Pattern:** Pattern 8 (inconsistent error type URNs — non-standard absolute URL format)

**What's wrong:** Both files use full absolute URLs
(`https://shepard.dlr.de/problems/...`) as the RFC 7807 `type` URI instead of the
standard relative path pattern (`/problems/...`) used by every other v2 resource.
Example:
- `PublishRest.java`: `problem("https://shepard.dlr.de/problems/publish.unauthorized", ...)`
- Correct pattern (SnapshotRest, etc.): `problem("/problems/snapshots.bad-request", ...)`

The absolute URL embeds an operator-specific hostname in the error contract, making
the API non-portable and inconsistent with ~95 other v2 error responses.

**Fix:** Search-and-replace `"https://shepard.dlr.de/problems/` →
`"/problems/` in both files (18 occurrences total). No logic change — only the
type URI string literal.

**Proposed row ID:** `APISIMP-PUBLISH-REP-EXPORT-URN-FORMAT`

**Size:** XS (18 string literal changes across 2 files)

---

## Clean areas

| Pattern | Result |
|---|---|
| Pattern 1: Numeric entity ids in v2 response IO | CLEAN — two tracked rows (APISIMP-TYPED-PREDECESSOR-NUMERIC-ID, APISIMP-TSCHANNEL-CONTAINER-ID) are gated; no new numeric-id leaks found |
| Pattern 4: Bespoke `*ConfigRest` not on generic registry | CLEAN — verified again; all admin config routes through `AdminConfigRest` |
| Pattern 5: New `@Path(Constants.SHEPARD_API + ...)` in v2/plugins | CLEAN — zero occurrences |
| Pattern 6: Redundant/verbose response fields | CLEAN — no payload-computable fields in v2 response surface |
| Pattern 7: Per-kind endpoints not under `?kind=` | BY DESIGN — `VideoStreamReferenceV2Rest` and `GitReferenceRest` retain domain-specific binary/preview operations; generic CRUD is on `/v2/references?kind=` |

---

## Summary

- **2 findings total** (fire-34)
- Smallest dispatchable: `APISIMP-FILEREF-416-BARE-RESPONSE` (XS) — one error path, one `problem()` call
- Slightly larger: `APISIMP-PUBLISH-REP-EXPORT-URN-FORMAT` (XS) — 18 string substitutions across 2 files
- Both are zero-logic-change, pure API consistency fixes
