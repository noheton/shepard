---
stage: concept
last-stage-change: 2026-06-10
---

# GitHub issue-hygiene sweep — 2026-06-10

Conservative close pass over `noheton/shepard` open issues. Authoritative
source of truth for shipped status: the local repo at `/opt/shepard`
(`aidocs/16-dispatcher-backlog.md` status cells, `aidocs/44` ✓ rows, live
code). When unsure, kept open.

- **Open before:** 302
- **Open after:** 258
- **Closed:** 44 (32 backfill-shipped + 11 gitlab-implemented + 1 stale)

## CLOSED

| # | Population | Reason |
|---|-----------|--------|
| 1219 | backfill | implemented — aidocs/16 `A5c` = **shipped** (HdfReference) |
| 1347 | backfill | implemented — aidocs/16 `P13` = done (SSE change-feed) |
| 1442 | backfill | implemented — aidocs/16 `EXP1o` = done (ExportService StrategyPattern) |
| 1443 | backfill | implemented — aidocs/16 `J1g` = done (LabJournalService refactor) |
| 1444 | backfill | implemented — aidocs/16 `UI14` = done ("Shared with me") |
| 1446 | backfill | implemented — aidocs/16 `AAS1l` = ✓ shipped (AAS admin config) |
| 1448 | backfill | implemented — aidocs/16 `VID1c` = ✓ done (video admin config) |
| 1452 | backfill | implemented — aidocs/16 `PROV1j` = done (X-AI-Agent header) |
| 1453 | backfill | implemented — aidocs/16 `PROV1k` = done (typed-predecessor) |
| 1461 | backfill | implemented — aidocs/16 `N1k` = done (CHAMEO/SSN/SOSA bundles) |
| 1462 | backfill | implemented — aidocs/16 `T1i` = done (EquipmentItem template) |
| 1464 | backfill | implemented — aidocs/16 `UI18` = done (channel unit picker) |
| 1465 | backfill | implemented — aidocs/16 `UI19` = done (skos:related sidebar) |
| 1470 | backfill | implemented — aidocs/16 `L10` = ✓ shipped (IdP-claim path) |
| 1471 | backfill | implemented — aidocs/16 `CC1d` = done (link-to-existing-container) |
| 1472 | backfill | implemented — aidocs/16 `CC1e` = done (linked-from column) |
| 1484 | backfill | implemented — aidocs/16 `GH-PM-ENH-RDM-1` = ✓ done |
| 1487 | backfill | implemented — aidocs/16 `GH-PM-ENH-RDM-4` = done |
| 1488 | backfill | implemented — aidocs/16 `GH-PM-ENH-RDM-5` = done (CITATION.cff commit) |
| 1490 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-1` = ✓ done |
| 1491 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-2` = ✓ done |
| 1492 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-3` = ✓ done |
| 1494 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-5` = ✓ done |
| 1495 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-6` = ✓ done |
| 1498 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-9` = ✓ done |
| 1499 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-10` = ✓ done (security-finding template; verified shipped) |
| 1500 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-11` = ✓ done |
| 1501 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-12` = ✓ done |
| 1502 | backfill | implemented — aidocs/16 `GH-PM-ENH-API-13` = done (trace-feature surfaces 10-13) |
| 1504 | backfill | implemented — aidocs/16 `GH-PM-ENH-STRAT-1` = ✓ done |
| 1506 | backfill | implemented — aidocs/16 `GH-PM-ENH-Q5-1` = done (bootstrap-gh-project) |
| 1507 | backfill | implemented — aidocs/16 `GH-PM-ENH-Q3-1` = done (trace-all-shipped) |
| 20 | gitlab | implemented — thermography plugin TIFF render/viewer (`OtvisFrameRenderer`, `DataObjectOtvisViewer.vue`) |
| 45 | gitlab | implemented — `FileRest.java` payload list takes `?page=`+`?size=` (distinct from queued APISIMP-PAGINATION-UNIFY/#485) |
| 46 | gitlab | implemented — V2 Snapshots (V2a-V2e, `aidocs/41`); HEAD + named milestone snapshots |
| 59 | gitlab | implemented — `FileReferenceV2Rest` createdBy/updatedBy + `PayloadVersionRest` uploadedBy + PROV1a `:Activity` |
| 127 | gitlab | implemented — versioning FE = `SnapshotsPane.vue` (UI1a) + snapshots/diff + payload version dialog |
| 271 | gitlab | implemented — same versioning UI as #127 (UI1a) |
| 441 | gitlab | implemented — spatial upload via `CreateDataReferenceDialog` + SPATIAL-UNIFY-002/003/004 shipped |
| 442 | gitlab | implemented — spatial refs in `DataObjectDataReferencesTable.vue` "Spatial (N)" tab (SPATIAL-UNIFY-003) |
| 443 | gitlab | implemented — create spatial ref via `/v2/references?kind=spatial` (SPATIAL-UNIFY-002) |
| 468 | gitlab | implemented — API-key tokens with expiry (L5) + scoped roles (A0); `X-API-KEY` auth |
| 632 | gitlab | implemented — `LandingPage.vue` (logo banner + concept cards + overview links) |
| 211 | gitlab | stale — fork is Nuxt3/Vue3 only; no old Vue2 frontend exists; CI on GitHub Actions not GitLab |

## KEPT-OPEN-NOTABLE

Considered for closure but deliberately left open:

- **#42** Restrict API keys to specific endpoints/objects — fork shipped
  *role*-scoped keys (A0), but **object-level** scoping (key limited to one
  collection) is not implemented. Genuinely unmet; narrower than #468.
- **#56** Delete data objects recursively — no recursive-delete query param
  found in v2 DataObject REST. Unmet feature.
- **#97 / #98** Reactive (Mutiny) / ApplicationScoped endpoint refactors —
  `P20` (reactive timeseries read path) is **queued** in aidocs/16; many
  endpoints are still `@RequestScoped`. Real refactor debt.
- **#112** "Integer as id" — appId (UUID v7) migration shipped (L2a/b/c),
  but the issue's AC explicitly wanted a *human-memorable short* id; a UUID
  does not satisfy that. **Flagged for operator judgement** (superseded-by-
  architecture vs. unmet-as-specified).
- **#447** Spatial-data CSV upload endpoint — JSON payload POST + File→spatial
  promote exist, but the dedicated CSV/bulk ingest path and its importer
  (`SPATIAL-UNIFY-004-SIDECAR`) are **queued**. Conservative keep.
- **#485** Consistent pagination across endpoints — explicitly queued as
  `APISIMP-PAGINATION-UNIFY` (newly filed). Hard-excluded.
- **#523** Direct drag-drop file upload on the DataObject page — labelled
  `stale` upstream but a `FileUploadDialog` exists; could not positively
  confirm the requested drop-zone UX shipped. Kept open (real UX feature).
- **#17** Grafana `abs()` percentage-units deprecation — `issue::bug`;
  fork ships Grafana dashboards but fix not positively confirmed. Bug-keep.
- **#34** CPACS WebViewer — no CPACS viewer shipped. Unmet.
- All remaining `meta:backfill` issues whose aidocs/16 row is
  `queued`/`blocked`/`designed` (87 rows), incl. all `APISIMP-*`.

## Three lowest-confidence closes (spot-check these)

1. **#468** (access tokens) — closed because API keys gained expiry + role
   scope; reasonable reading of "access tokens for authentication", but the
   issue body was truncated and may have intended OIDC-style bearer tokens.
2. **#59** (payload user/created-date) — the ACs include "How to handle
   timeseries?" which the closing evidence (file/structured payloads) does
   not explicitly cover; timeseries upload attribution may be partial.
3. **#632** (landing page) — `LandingPage.vue` clearly has logo + concept
   cards + links, but I did not pixel-verify every AC (e.g. "link to GitLab
   docs further down"); content-completeness judged from source, not a live
   render.
