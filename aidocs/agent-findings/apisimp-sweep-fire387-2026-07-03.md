---
stage: concept
last-stage-change: 2026-07-03
---

# APISIMP Sweep — 2026-07-03 (fire-387)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (~100 REST resource files, all IO shapes) and plugin `@Path` endpoints against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout. Plugin directories (`backend/plugins/`, `backend/plugins-stage/`) are empty — no in-tree plugin REST to audit.

**Baseline:** Last sweep fire-351 (2026-07-01) filed APISIMP-MISSING-OPERATIONID-P3, APISIMP-DQR-ORPHAN, APISIMP-LEDGER-ANCHOR-ORPHAN. P3 merged fire-352; DQR-ORPHAN and LEDGER-ANCHOR-ORPHAN remain as operator-decision rows. New REST files added since fire-351: `LabJournalHistoryRest`, `NotebookRest`, `CollectionLabJournalEntriesRest`, `SqlTimeseriesRest`, `IndependenceProofRest`, `ReferencesV2Rest`, `ReferenceAnnotationRest` (consolidation of per-kind annotation endpoints from TA1a/APISIMP-VIDEO-ANNOT-PATH).

**What merged into main this fire (fire-387):** PR #2262 (PLACEHOLDER-video-container) — replaced VideoContainer placeholder with `VideoStreamReferencesPane`.

---

## Axes confirmed clean this sweep

| Axis | Verdict |
|---|---|
| Forbidden `@Path(Constants.SHEPARD_API + ...)` additions | ✅ zero found in v2/ (grep returned empty) |
| Per-kind endpoints NOT yet unified under `?kind=` | ✅ none found — `ContainersV2Rest` + `ReferencesV2Rest` (unified kind dispatch) cover all kinds |
| Bespoke admin `*ConfigRest` not on generic registry | ✅ all admin config routes through `AdminConfigRest` + `ConfigDescriptor` SPI; `JupyterConfigPublicRest` at `/v2/jupyter/config` is documented public read-only companion (not a bespoke admin config) |
| Numeric Neo4j id leaks in `@PathParam`/`@QueryParam` | ✅ zero new leaks; only existing `PermissionAuditEntryIO.neo4jNodeId` (tracked APISIMP-PERMISSION-AUDIT-NEO4J-ID, blocked) |
| Error envelope consistency | ✅ all 4xx/5xx return `application/problem+json` + `ProblemJson` entity (either via `problem()` helper or direct construction); `ShapesValidateRest` 400 returns `ShapeValidationReportIO` which is intentional (the error IS the report) |
| `operationId` coverage | ✅ zero gaps across all ~100 HTTP method sites (confirmed by full file scan + individual spot-checks including fire-351 P3 targets: `MappingsMaterializeRest`, `UserAvatarByAppIdRest`, `UserAvatarRest` — all fixed) |
| Endpoints superseded by `POST /v2/shapes/render` | ✅ no new superseded endpoints found |
| Response fields with zero callers / numeric id leaks in IO | ✅ all new IO classes are plain POJOs with no OGM entity extension; `DataObjectDetailV2IO` / `DataObjectListItemV2IO` still strip via `@JsonIgnoreProperties` |
| New lab journal endpoints (LabJournalHistoryRest, NotebookRest, CollectionLabJournalEntriesRest) | ✅ clean — `?page=`/`?pageSize=` throughout, operationIds present |
| New SqlTimeseriesRest, IndependenceProofRest | ✅ POST-only, single operationId each, no pagination surface |
| Non-standard `?limit=` / `?from=` params that aren't pagination | ✅ confirmed legitimate — `ProvenanceRest ?limit=` is cursor-window (not offset pagination; co-exists with `?since=`/`?until=`); `InstanceAdminRest ?from=`/`?to=` are ISO-8601 timestamp filters (co-exists with `?page=`/`?pageSize=`); `ContainersV2Rest ?start=`/`?end=` are epoch-ns timestamps; `?size=` is thumbnail pixel size |

---

## Findings (1 new row filed)

### F1 — APISIMP-REF-ANNOTATION-PAGINATION-PARAM (MINOR, XS)

**What:** `ReferenceAnnotationRest.java:157` declares `@QueryParam("limit")` as its page-size parameter, making `/v2/references/{appId}/annotations` the only paginated v2 endpoint using `?page=` + `?limit=`. Every other paginated endpoint across the v2 surface (37+ endpoints) uses `?page=` + `?pageSize=`. The `@APIResponse` description also documents the non-standard pair: `"Paged envelope: items + total + page + pageSize. Header X-Total-Count = total count..."` — the description says `pageSize` but the actual param is `limit`.

| File | Endpoint | Gap |
|---|---|---|
| `ReferenceAnnotationRest.java:157` | `GET /v2/references/{appId}/annotations` | `@QueryParam("limit")` should be `@QueryParam("pageSize")` |

**Frontend callers:** Both `useTimeseriesReferenceAnnotations.ts` and `useFetchVideoAnnotations.ts` call this endpoint without passing any pagination param — they rely on the default (200). No caller would break on rename.

**Fix:** In `ReferenceAnnotationRest.java`:
- Line 157: `@QueryParam("limit")` → `@QueryParam("pageSize")`
- Line 163: `int limit` → `int pageSize` (local variable rename)
- Update `@Parameter` description at line ~150: remove `?page=0&limit=200`, say `?page=0&pageSize=200`
- Update `@APIResponse` description at line ~147: already says `pageSize` in the envelope description; no change needed there
- Update body: `(long) page * limit` → `(long) page * pageSize`; `from + limit` → `from + pageSize`; `PagedResponseIO` arg: `limit` → `pageSize`

**AC:** `GET /v2/references/{appId}/annotations?page=0&pageSize=200` works; `mvn verify -pl backend` green.

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-REF-ANNOTATION-PAGINATION-PARAM | MINOR | XS | queued — dispatch next fire |
| APISIMP-DQR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-LEDGER-ANCHOR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |

**Recommended next dispatch:** APISIMP-REF-ANNOTATION-PAGINATION-PARAM (XS, no operator input needed, parameter rename in one file, no wire change if no callers pass `?limit=`).
