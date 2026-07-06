---
stage: deployed
last-stage-change: 2026-07-06
---
# APISIMP Sweep — fire-444 (2026-07-06)

Sweep scope: all REST resource files under
`backend/src/main/java/de/dlr/shepard/v2/` and
`plugins/*/src/main/java/` (plugin REST surfaces).

Skipped (already tracked / shipped per task brief):
APISIMP-AAS-SHELL-DO-LOAD-CAP, APISIMP-REFANNOT-IN-MEMORY-PAGING,
APISIMP-TS-CONT-ANNOT-IN-MEMORY-PAGING, APISIMP-COLL-CONTAINERS-BARE-LIST,
APISIMP-DQR-LIST-IN-MEMORY-PAGING, APISIMP-USER-SEARCH-NO-PAGINATION,
APISIMP-TSCHANNEL-CONTAINER-ID-WIRE, APISIMP-PERMISSION-AUDIT-NEO4J-ID,
APISIMP-USERGROUP-SEARCH-FAKE-PAGED.

---

## Sweep findings (clean passes)

| Pattern checked | Result |
|---|---|
| `subList` in-memory paging | All remaining occurrences are either shipped (backlog rows ✅) or legitimately depth-bounded. No new violation. |
| Unbounded `findAll()` / `findBy*` without SKIP/LIMIT | Zero new occurrences. All previously-fixed DAO call sites now use bounded variants. |
| Fake-paged wrappers `(list, list.size(), 0, count)` | One new violation — see F1 below. Fixed in this fire. |
| Numeric Neo4j/Postgres id leaks in IO | One new violation in plugin — see F2 below. Fixed in this fire. |
| Missing `@DefaultValue` on paginated list params | One new violation — see F3 below. Fixed in this fire. |
| Undocumented non-standard `pageSize` caps | Two endpoints — see F4 below. Fixed in this fire. |
| Missing OpenAPI `@Content(schema=...)` on 200 `@APIResponse` | Two endpoints — see F5 below. Fixed in this fire. |
| Dual pagination vocabularies on single endpoint | One new violation — see F6 below. Queued (Size S). |

---

## F1 — APISIMP-USERGROUP-SEARCH-FAKE-PAGED (MINOR, Size XS)

**Status: ✅ Merged fire-444 (PR #2361, sha: 4318a9d)** — carried over from fire-443 dispatch.

---

## F2 — APISIMP-WIKI-NUMERIC-ID-WIRE (MINOR, Size XS)

**File:** `plugins/wiki-writer/src/main/java/de/dlr/shepard/plugins/wikiwriter/io/WikiWriteResponseIO.java:25`

`private long labJournalEntryId` — a Neo4j OGM numeric ID — is serialized on the v2 wire even though a stable `labJournalEntryAppId` (UUID v7) replacement is present on line 32. The field carries `@Deprecated` Javadoc but is NOT `@JsonIgnore`d, so the Neo4j internal node ID appears in every successful `POST /v2/data-objects/{dataObjectAppId}/wiki-write` response. This violates the "no numeric internal IDs on the v2 surface" contract.

**Fix:** Annotate `labJournalEntryId` with `@JsonIgnore`. The `labJournalEntryAppId` field already provides the stable addressable reference; no wire-shape migration is needed.

**AC:** `labJournalEntryId` absent from response body; `labJournalEntryAppId` present; `mvn verify -pl backend` green.

**Status: ✅ Fixed in this fire (PR #2362).**

---

## F3 — APISIMP-BUNDLE-FILES-NO-DEFAULTVALUE (MINOR, Size XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java:450–453`

`listGroupFiles` declares `@QueryParam("page") Integer page` and `@QueryParam("pageSize") Integer pageSize` without `@DefaultValue`, so the OpenAPI spec shows both params as having no default even though the implementation applies server-side defaults of 0 and 200. A caller reading the spec has no way to know the defaults without reading source.

**Fix:** Add `@DefaultValue("0")` and `@DefaultValue("200")` and `schema = @Schema(...)` to document the defaults and bounds in the OpenAPI output. Keep `Integer` type to preserve existing clamping behavior (intentional design: out-of-range hints clamped, not rejected).

**AC:** OpenAPI spec shows `defaultValue: 0` / `defaultValue: 200`; behavior unchanged; `mvn verify -pl backend` green.

**Status: ✅ Fixed in this fire (PR #2362).**

---

## F4 — APISIMP-PAGESIZE-CAP-UNDOCUMENTED (MINOR, Size XS)

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java:450–453` (`listGroupFiles` — cap 1000)
- `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotPinnedReadRest.java:124–125` (`getDataObjects` — cap 2000)
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1011–1013, 1123–1125` (`listChannelAnnotations`, `listTemporalAnnotations` — cap 500)

Several list endpoints enforce non-standard `pageSize` maxima (500, 1000, 2000) while the rest of `/v2/` caps at 200. None of these divergences were documented in `schema = @Schema(maximum = "…")` on the `@Parameter`, so OpenAPI consumers could discover the cap only via a runtime error.

**Fix:** Add `schema = @Schema(minimum = "…", maximum = "…", defaultValue = "…")` to each outlier endpoint's `pageSize` (and `page`) `@Parameter` annotation. Add `@Parameter` import to `SnapshotPinnedReadRest.java`.

**AC:** OpenAPI spec documents the cap for all three endpoints; `mvn verify -pl backend` green.

**Status: ✅ Fixed in this fire (PR #2362).**

---

## F5 — APISIMP-CHANNEL-ANNOTATION-MISSING-SCHEMA (MINOR, Size XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1001–1002, 1115–1116`

The `@APIResponse(responseCode = "200")` annotations on `listChannelAnnotations` and `listTemporalAnnotations` are missing `content = @Content(schema = @Schema(implementation = PagedResponseIO.class))`, so the OpenAPI spec documents the 200 response with no body schema even though the actual response is a `PagedResponseIO<SemanticAnnotationIO>`. Every other list endpoint on the same class documents its 200 schema correctly.

**Fix:** Add `content = @Content(schema = @Schema(implementation = PagedResponseIO.class))` to both 200 `@APIResponse` annotations, matching the pattern used by every other list endpoint on the same class.

**AC:** OpenAPI spec shows `PagedResponseIO` schema for both 200 responses; `mvn verify -pl backend` green.

**Status: ✅ Fixed in this fire (PR #2362).**

---

## F6 — APISIMP-SEARCH-SPLIT-PAGINATION (MINOR, Size S)

**File:** `backend/src/main/java/de/dlr/shepard/v2/search/resources/SearchV2Rest.java:113, 118`

`GET /v2/search` exposes two parallel pagination vocabularies — `page`/`pageSize` (Collections) and `doPage`/`doPageSize` (DataObjects) — in the same response, making it the only v2 endpoint a caller cannot drive with a single standard page-cursor variable. A REST client that page-walks must maintain two cursors and issue separate requests to get the next page of each kind.

**Fix:** Remove the DataObject-specific pagination params and return a single unified `PagedResponseIO` whose `items[]` interleaves Collections and DataObjects; if per-kind totals are needed, add them as top-level fields on a typed `SearchV2ResultIO` rather than a second pagination cursor.

**AC:** `GET /v2/search?q=mffd&page=0&pageSize=20` returns a single paged envelope; `doPage`/`doPageSize` params removed; `mvn verify -pl backend` green.

**Status: queued — next fire.**

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-WIKI-NUMERIC-ID-WIRE | MINOR | XS | ✅ Fixed fire-444 (PR #2362) |
| APISIMP-BUNDLE-FILES-NO-DEFAULTVALUE | MINOR | XS | ✅ Fixed fire-444 (PR #2362) |
| APISIMP-PAGESIZE-CAP-UNDOCUMENTED | MINOR | XS | ✅ Fixed fire-444 (PR #2362) |
| APISIMP-CHANNEL-ANNOTATION-MISSING-SCHEMA | MINOR | XS | ✅ Fixed fire-444 (PR #2362) |
| APISIMP-SEARCH-SPLIT-PAGINATION | MINOR | S | queued — next fire |

**Surface verdict:** The v2 REST surface is near-clean at fire-444. Four minor annotation/wire violations fixed in this fire. One new minor violation found (dual pagination on search endpoint, Size S). All previous APISIMP-* patterns with performance impact have been resolved.
