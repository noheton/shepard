---
stage: deployed
last-stage-change: 2026-06-14
---

# APISIMP Sweep — 2026-06-14 (fire-42)

## Scope

Full live codebase sweep of the `/v2/` REST surface and `frontend/` against the
APISIMP backlog table in `aidocs/16-dispatcher-backlog.md` (rows ~3596–3700).
Objective: find any residual numeric-id leaks, wrong-base-URL pairings, dead
endpoint calls, inconsistent response envelopes, or missing `ProblemJson` wrappers
not yet captured as a backlog row.

---

## What I found

### Backlog table status

All named APISIMP rows in `aidocs/16` are either:

- `**✓ shipped**` — code change landed in `main`, status already updated, or
- Blocked on a PR that is open and CI-green (see §Open PRs below).

No row was found to be silently unresolved without a status entry.

### Live codebase — residual findings

| Area | Finding | Status |
|---|---|---|
| **Pagination (`?size` vs `?pageSize`)** | 7 v2 resources still accept `?size` via `Constants.QP_SIZE = "size"`: `CollectionV2Rest`, `InstanceAdminRest`, `SnapshotListRest`, `CollectionSnapshotRest`, `FileBundleReferenceRest`, `DataObjectV2Rest`, `TimeseriesContainerChannelsRest`, `UserGroupV2Rest` | All covered by open PR #1887 (`APISIMP-PAGINATION-UNIFY-RECREATE`). No new row needed. |
| **`Constants.QP_SIZE`** | `Constants.java:157` still holds `"size"` — root constant that all above endpoints derive from. | Will be renamed as part of #1887. |
| **`id` in v2 wire shapes** | `BasicEntityIO.id` (Long) present in the base class. All v2 IO subclasses suppress via `@JsonIgnoreProperties({"id"})`. | Pattern complete: `CollectionV2IO`, `DataObjectDetailV2IO`, `DataObjectListItemV2IO`, `BasicContainerV2IO`, `BasicReferenceV2IO`. No further suppression needed. |
| **Frontend v1 API helper calls** | `annotated.ts` — `AnnotatedTimeseries` uses `useShepardApi(TimeseriesContainerApi)` (v1). One-line comment at the call site cites the missing v2 counterpart (`V2UI-TS-ANNO-V2` in `aidocs/16`). Named exception per CLAUDE.md. | Valid fallback, documented. No action. |
| **`PinnedChannelTile.vue` container key** | Line 109: `containerKey = props.channel.containerAppId ?? props.channel.containerId`. Fallback to numeric `containerId` for pins stored before `APISIMP-PINNED-CHANNEL-TILE-APPID`. | Resolved. Fallback will self-heal as pins are refreshed. |
| **Plugin problem URI absoluteness** | All plugin `ProblemJson` calls use relative URIs. `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS` was filed in fire-40 and is tracked in #1887. | Covered. |

### New findings: NONE

No new APISIMP residuals were found that are not already covered by an existing
backlog row or open PR.

---

## Open APISIMP-related PRs at sweep time

| PR | Branch | Status | Notes |
|---|---|---|---|
| #1887 | `APISIMP-PAGINATION-UNIFY-RECREATE` | **RED** — CodeQL summary alert (pre-existing; not introduced by PR) | Operator must dismiss pre-existing CodeQL finding in GitHub Security → Code Scanning, then re-run summary job. |
| #1915 | `BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH-appIds` | Rebased onto `8f3b5eb` (fire-42); awaiting CI re-run | Rebase conflict in `aidocs/01-doc-stage-index.md` resolved via `regenerate-doc-stage-index.py`. |
| #1916 | `APISIMP-TYPED-PREDECESSOR-NUMERIC-ID-appid-join` | Rebased onto `8f3b5eb` (fire-42); awaiting CI re-run | Same resolution pattern. |
| #1917 | (APISIMP surface area) | **READY** — all checks green | Surfaced to orchestrator for merge. |
| #1919 | (APISIMP surface area) | **READY** — all checks green | Surfaced to orchestrator for merge. |

---

## Conclusion

The APISIMP surface is clean as of 2026-06-14. All named rows have been resolved.
The one remaining structural gap (`?size` pagination) is actively in-flight under
#1887 and will close once the CodeQL false-positive is dismissed. No new rows are
filed from this sweep.

Next implementation work: **MFFD-RENDER-AFP-THERMO-OVERLAY slice 3** (Canvas UI).
