---
stage: deployed
last-stage-change: 2026-06-27
audience: [contributor]
---

# APISIMP Sweep — 2026-06-27 (fire-262)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.

## What I found

### Numeric ID hygiene — CLEAN

Zero v2 `@PathParam` or `@QueryParam` carry a `Long` or `int` type.
All path params are UUID v7 strings. No new leaks since fire-231.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path` annotation.
Only known residual: `SpatialDataReferenceRest.java:40` (spatiotemporal plugin —
frozen upstream-compat surface, deferred per APISIMP-V1-PATH-RESIDUAL-1).
ArchUnit `V2NamespaceTest` enforces this at build time.

### Bespoke admin `*ConfigRest` outside generic registry — CLEAN

No change from fire-231. `JupyterConfigPublicRest` (`GET /v2/jupyter/config`)
remains intentional: the generic registry requires `instance-admin`; this
public read lets any authenticated user discover JupyterHub availability.
No new bespoke admin configs found.

### Per-kind endpoints not unified — CLEAN

Same as fire-231. No new per-kind endpoints bypassing the `?kind=` discriminator.
`FileBundleReferenceRest`, `SqlTimeseriesRest`, and `FileReferenceV2Rest` (410 Gone)
remain correctly categorised as intentional non-sprawl.

### New plugin REST — CLEAN

20 plugin REST resources scanned (`SpatialDataReferenceRest`, `SpatialDataPointRest`,
`AasShellsRest`, `AasAdminRest`, `AasRegistrationAdminRest`, `AasWellKnownRest`,
`GitReferenceRest`, `GitReferenceActionsRest`, `MeCredentialsRest`,
`KipResolverRest`, `HdfAdminRest`, `EpicAdminRest`, `DataciteAdminRest`,
`SpatialPromoteRest`, `LegacyV1StatsAdminRest`, `UnhideAdminRest`,
`UnhideFeedRest`, `VideoStreamReferenceV2Rest`, `WikiWriterRest`,
`WikiWriterTombstoneRest`). No new forbidden `SHEPARD_API` uses beyond the known
deferred residual.

### Pagination wave completeness check

All shape-B and shape-C endpoints from fire-231 are now fully migrated to
`PagedResponseIO` (APISIMP-PAGINATION-ENVELOPE fully shipped PR #2102-2105).
APISIMP-PAGINATION-PARAM-STYLE shipped PR #2101. The pagination wave is complete.

Three list endpoints added after the main pagination wave (fires ~220–236) were
not swept in fire-231 because they did not exist at that time or were overlooked:

**Finding 1 — `FlatPublicationsRest.list()` (XS)**
`GET /v2/publications?entityAppId=…` at `FlatPublicationsRest.java:84` returns a
plain `List<PublicationIO>` with no `page`/`pageSize` params, no `X-Total-Count`
header, and no `PagedResponseIO` envelope. Publications per entity are expected to
be small (typically <10) but grow unboundedly with each publish/retire cycle.
Inconsistent with the paginated envelope pattern now standard across all other
v2 list endpoints.

**Finding 2 — `PersonalVocabularyRest.list()` (XS)**
`GET /v2/vocabularies/personal` at `PersonalVocabularyRest.java:176` returns a
plain `List<VocabularyIO>` with no pagination. Personal vocabularies per user are
bounded by user creation behaviour but follow no server cap. The endpoint was
added as part of SEMA-V6-014 (personal vocabulary minting) and post-dates the
pagination envelope wave.

**Finding 3 — `LabJournalHistoryRest.history()` (XS)**
`GET /v2/lab-journal/{entryAppId}/history` at `LabJournalHistoryRest.java:100`
returns a plain `List<LabJournalRevisionIO>` with no pagination. Revision count
per entry is bounded by edit frequency but carries no server cap. A high-traffic
journal entry (used collaboratively, edited daily) will accumulate revisions
indefinitely.

## Opportunities

1. **APISIMP-FLAT-PUBS-NO-PAGINATION** (XS): Add `page`/`pageSize` + `PagedResponseIO`
   envelope to `FlatPublicationsRest.list()`. Same pattern as all other post-wave
   list endpoints. Acceptance: `?page=0&pageSize=10` returns first 10 publications;
   `X-Total-Count` header present; `mvn verify -pl backend` green.

2. **APISIMP-PERSONAL-VOCAB-NO-PAGINATION** (XS): Add `page`/`pageSize` +
   `PagedResponseIO` to `PersonalVocabularyRest.list()`. Same pattern.
   Acceptance: `?page=0&pageSize=20` returns first 20 vocabularies; `X-Total-Count`
   header present; `mvn verify -pl backend` green.

3. **APISIMP-LAB-JOURNAL-HISTORY-NO-PAGINATION** (XS): Add `page`/`pageSize` +
   `PagedResponseIO` to `LabJournalHistoryRest.history()`. Same pattern.
   Acceptance: `?page=0&pageSize=50` returns first 50 revisions; `X-Total-Count`
   header present; `mvn verify -pl backend` green.

## Real-world impact

All three are low-severity DX inconsistencies — no security, integrity, or
upgrade-path risk. A client building a generic paginator over v2 list endpoints
would have to special-case these three. Fixing them completes the pagination
envelope convergence started in fires 216–236.

## Gaps & blockers

None. All three findings are small-cardinality lists in practice. The largest
conceivable set (a frequently-published entity with 50+ publication events) would
still be fetchable in a single call; pagination is a consistency fix, not a
performance fix, for these three.

## What surprised me

The surface is genuinely stable. After 262 fires of APISIMP work:
- Zero numeric-id leaks in path/query params
- Zero forbidden `SHEPARD_API` constant uses in v2 resources
- Zero bespoke admin config resources outside the generic registry
- Zero per-kind endpoints that should be unified but aren't
- Zero redundant endpoint pairs

The only remaining gaps are three small list endpoints that post-date the
pagination wave. The API surface convergence program described in
`aidocs/platform/191-v2-surface-convergence.md` is nearly complete at the
sprawl-prevention level; the remaining open items from 191 (fileKind
discriminator, plugin-namespace OASFilter) are architectural completions,
not sprawl regressions.
