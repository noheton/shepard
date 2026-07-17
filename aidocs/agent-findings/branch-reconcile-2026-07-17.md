---
stage: deployed
last-stage-change: 2026-07-17
---

# APISIMP branch reconciliation — 2026-07-17

Reconciled the ~520 stale remote branches matching `APISIMP-*` / `apisimp-*`
(case-insensitive) left by the cloud dispatcher on `github.com/noheton/shepard`.
Because the dispatcher's PRs were squash-merged, `git rev-list main..branch`
misleadingly reports them as unmerged; classification was therefore done against
the PR ledger (full paginated `gh api repos/noheton/shepard/pulls?state=all`,
1273 PRs, covering all APISIMP PRs up to #2626), with `git cherry` patch-equivalence
as the fallback for branches that never had a PR. Where a branch had multiple PRs,
the latest (highest-numbered) PR decided the classification.

No `dependabot/*` or non-APISIMP branch was touched. No branch with an open PR
was deleted.

## Counts

| Category | Count | Action |
|---|---:|---|
| Total remote APISIMP branches found | 527 | |
| MERGED-PR (squash-merged) | 482 | deleted |
| CLOSED-PR (not merged; rejected/superseded/duplicate) | 41 | deleted (listed below) |
| NO-PR, patch-equivalent on main (`git cherry` all `-`) | 1 | deleted (`APISIMP-PAGINATION-UNIFY-2-limit-to-pagesize`) |
| OPEN-PR | 1 | **kept** |
| NO-PR, genuinely unmerged content (`git cherry` shows `+`) | 2 | **kept — needs triage** |
| **Deleted total** | **524** | |
| **Kept total** | **3** | |

Post-deletion `git fetch --prune` confirms exactly the 3 kept branches remain
under `origin/APISIMP*`.

## Kept branches

### Open PR (1)

| Branch | PR | Title |
|---|---|---|
| `APISIMP-DQR-EVAL-INMEM` | #2626 (OPEN) | perf(APISIMP-DQR-EVAL-INMEM): bound DQR evaluation heap by maxItems early-exit |

### Unmerged content, needs triage (2)

No PR exists for these, and `git cherry origin/main origin/<branch>` shows a `+`
(patch not on main):

| Branch | Unmerged commit subject(s) | Note |
|---|---|---|
| `APISIMP-CONT-NS-COLLAPSE-6-safe-delete-unify` | feat(APISIMP-CONT-NS-COLLAPSE-6): safe-delete unify — DI1 on /v2/containers + delete 3 per-kind REST files | Likely superseded by merged PR #1951 (`APISIMP-CONT-NS-COLLAPSE-6b-cleanup`: "delete dead per-kind safe-delete REST + migrate FE composable"), but the patch itself is not on main — verify before deleting. |
| `APISIMP-CONT-NS-COLLAPSE-7-stats-chartview-unify` | feat(APISIMP-CONT-NS-COLLAPSE-7): migrate stats+chart-view to /v2/containers/{appId} | No sibling PR found; check whether stats/chart-view already live under `/v2/containers/{appId}` on main, else this is real unlanded work. |

## Deleted: CLOSED-PR branches (41)

Work rejected, superseded, or duplicated; branches deleted but listed here for
the record:

| Branch | PR | Title |
|---|---|---|
| `APISIMP-ANNOTATION-PLAIN-ARRAY` | #2141 | feat(apisimp): APISIMP-ANNOTATION-PLAIN-ARRAY — wrap annotation list+find in PagedResponseIO |
| `APISIMP-ANNOTATION-SUBRESOURCE-COLLISION` | #1971 | fix(APISIMP-ANNOTATION-SUBRESOURCE-COLLISION): unify annotation sub-resource via ReferenceKindHandler SPI |
| `APISIMP-BUNDLE-REF-PARAMS-UNDOCUMENTED` | #2013 | fix(APISIMP-BUNDLE-REF-PARAMS-UNDOCUMENTED): document 3 @QueryParam params in FileBundleReferenceRest |
| `APISIMP-COLLECTION-LIST-PARAMS` | #2016 | docs(apisimp): document CollectionV2Rest.list() query params (APISIMP-COLLECTION-LIST-PARAMS) |
| `APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE-prose` | #2028 | fix(apisimp): stale pageSize prose + @Parameter for CollectionV2Rest.list() and FileBundleReferenceRest pagination (APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE + APISIMP-BUNDLE-GROUP-FILES-SIZE-PAGESIZE) |
| `APISIMP-COLLECTION-SNAPSHOT-LABJ-PARAMS-UNDOCUMENTED` | #2008 | fix(APISIMP-COLLECTION-SNAPSHOT-LABJ-PARAMS-UNDOCUMENTED): document 7 @QueryParams across 3 collection-scoped list endpoints |
| `APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY-and-search-400` | #2139 | fix: wrap snapshot list in PagedResponseIO; return problem+json on search 400 [CLOSED - duplicate of #2140] |
| `APISIMP-COLLECTION-V2-EMPTY-BODIES` | #1885 | fix(APISIMP-COLLECTION-V2-EMPTY-BODIES): RFC 7807 bodies for empty 4xx in CollectionV2Rest |
| `APISIMP-DATAOBJECT-V2-PARAMS-UNDOCUMENTED` | #2005 | feat(apisimp): APISIMP-DATAOBJECT-V2-PARAMS-UNDOCUMENTED — document 8 @QueryParams in DataObjectV2Rest |
| `apisimp-do-chain-depth-param` | #2024 | feat(APISIMP): document depth param on predecessor/successor chain endpoints |
| `APISIMP-DO-COLL-IO-NUMERIC-ID-LEAK` | #2071 | feat(APISIMP-DO-COLL-IO-NUMERIC-ID-LEAK): suppress numeric id arrays from /v2/ DataObject+Collection wire shapes |
| `APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING` | #1973 | fix(APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING): return RFC-7807 body on thumbnail 503 |
| `APISIMP-FILE-PATH-RETIRE-2` | #1978 | feat(APISIMP-FILE-PATH-RETIRE-2): tombstone /v2/files/{appId}* CRUD → 410 Gone |
| `APISIMP-GIT-CRED-BARE-LIST-1` | #2432 | feat(APISIMP-GIT-CRED-BARE-LIST,APISIMP-MINTER-CRED-TOMBSTONE-VERIFY): wrap git-credential list in PagedResponseIO + add Location header to minter tombstones |
| `APISIMP-GIT-REF-PATH-actions-under-v2-refs` | #1967 | feat(APISIMP-GIT-REF-PATH): move git preview+check-update to /v2/references/{appId}/… |
| `APISIMP-KIND-DISCRIMINATOR-slice3-bundle` | #1979 | feat(APISIMP-KIND-DISCRIMINATOR): slice 3 — kind=bundle ReferenceKindHandler for FileBundleReference |
| `apisimp/labjournal-params-doc` | #2018 | docs(labjournal): add @Parameter descriptions to page/pageSize (APISIMP-LABJOURNAL-PARAMS-DOC) |
| `APISIMP-MAX-POINTS-PARAM-CASE` | #1977 | feat(APISIMP-MAX-POINTS-PARAM-CASE): rename @QueryParam max_points → maxPoints in getChannelData |
| `APISIMP-PAGINATION-UNIFY-1-size-to-pagesize` | #1847 | feat(APISIMP-PAGINATION-UNIFY-1): rename size→pageSize on all 7 v2 list endpoints |
| `APISIMP-PATHPARAM-BUNDLE-MAPPINGS` | #2576 | refactor(APISIMP): rename {bundleAppId}/{templateAppId} → {appId} in v2 REST resources |
| `APISIMP-PERM-AUDIT-LOG-APPID-drop-bigserial-add-uuid` | #1863 | feat(APISIMP-PERM-AUDIT-LOG-APPID): drop BIGSERIAL id, add app_id UUID on permission audit log wire [SUPERSEDED] |
| `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS-impl` | #1918 | fix(APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS): relative problem-type URIs in plugin REST resources |
| `APISIMP-PLUGIN-V2-PARAMS-UNDOCUMENTED` | #2021 | fix(APISIMP-PLUGIN-V2-PARAMS-UNDOCUMENTED): document @QueryParam params in plugin REST resources |
| `APISIMP-PROJECT-BY-ANNOTATION-IRI-PATH-query-param` | #2241 | feat(APISIMP-PROJECT-BY-ANNOTATION-IRI-PATH): move predicate/value to @QueryParam |
| `APISIMP-PROVENANCE-CONTENT-NEG-PARAMS` | #2014 | fix(APISIMP-PROVENANCE-CONTENT-NEG-PARAMS): document 28 @QueryParam params across 6 content-negotiated provenance endpoints |
| `apisimp-provenance-v2-params-undocumented` | #2019 | feat(APISIMP-PROVENANCE-V2-PARAMS-UNDOCUMENTED): document 28 undocumented params in provenance content-negotiated endpoints |
| `APISIMP-REFERENCES-CREATE-LIST-PARAMS` | #2022 | fix(APISIMP-REFERENCES-CREATE-LIST-PARAMS): document @QueryParam params on ReferencesV2Rest create/list/uploadContent |
| `APISIMP-REFERENCES-V2-PARAMS-UNDOCUMENTED` | #2006 | fix(APISIMP-REFERENCES-V2-PARAMS-UNDOCUMENTED): add @Parameter docs to 6 QueryParams in ReferencesV2Rest |
| `apisimp-snapshot-resp-size` | #1858 | feat(APISIMP-SNAPSHOT-RESP-SIZE): rename response field size→pageSize in snapshot list envelope [superseded by #1860] |
| `APISIMP-SNAPSHOT-RESP-SIZE` | #1870 | feat(APISIMP-PAGINATION-UNIFY): rename ?size → ?pageSize on 7 v2 list endpoints |
| `apisimp-sparql-query-param-doc` | #2026 | feat(apisimp): document @QueryParam("query") on SPARQL GET endpoint |
| `APISIMP-STALE-COMMENTS` | #1972 | docs(APISIMP-STALE-COMMENTS): strip stale migration-history clauses from ContainersV2Rest @Operation descriptions |
| `APISIMP-STRUCTURED-DATA-KIND-2-uploadcontent` | #1975 | feat(APISIMP-STRUCTURED-DATA-KIND): slice 2 — uploadContent for kind=structured-data |
| `APISIMP-STRUCTURED-DATA-KIND-3-fe-composable` | #1976 | feat(APISIMP-STRUCTURED-DATA-KIND): slice 3 — FE composable useCreateStructuredDataReference |
| `APISIMP-TIMESERIES-CHANNEL-PARAMS-UNDOCUMENTED` | #2015 | feat(APISIMP-TIMESERIES-CHANNEL-PARAMS-UNDOCUMENTED): document 16 bare @QueryParam annotations |
| `APISIMP-USER-GROUP-LIST-DUAL-SHAPE-1` | #2371 | feat(APISIMP-USERGROUP-Q-FILTER): add ?q name-filter to GET /v2/user-groups |
| `apisimp-usergroup-orderby-params-doc` | #2027 | docs(apisimp): document orderBy/orderDesc @Parameter descriptions on UserGroupV2Rest (APISIMP-USERGROUP-ORDERBY-PARAMS-UNDOCUMENTED) |
| `APISIMP-VIDEO-STREAMREF-PATH-actions-under-v2-refs` | #1969 | feat(APISIMP-VIDEO-STREAMREF-PATH): relocate VideoStreamReference upload+download to unified /v2/references surface |
| `APISIMP-VIDEO-STREAMREF-PATH-video-content-migration` | #1970 | feat(APISIMP-VIDEO-STREAMREF-PATH): migrate video upload/download to unified /v2/references surface |
| `APISIMP-VIDEO-TOMBSTONE-DELETE` | #2372 | feat(apisimp): APISIMP-VIDEO-TOMBSTONE-DELETE — delete VideoStreamReferenceV2Rest 410 stubs |
| `APISIMP-XTOTALCOUNT-LIST-DO` | #2482 | [SUPERSEDED by #2483] feat(apisimp): drop X-Total-Count from DataObjectV2Rest.listDataObjects (APISIMP-XTOTALCOUNT-LIST-DO) |

## Deleted: MERGED-PR branches (482)

All 482 squash-merged branches were deleted; the full list is reproducible from
the PR ledger (`gh api 'repos/noheton/shepard/pulls?state=all&per_page=100'
--paginate`, filter `head.ref` matching `^apisimp` case-insensitive with
`merged_at != null`). Not inlined here to keep the report readable.

## Method

1. `git fetch origin --prune`
2. One paginated PR-ledger pull (no per-branch API calls): 1273 PRs total,
   535 with APISIMP head refs (some branches carried >1 PR; latest PR wins).
3. Classification per branch: MERGED → delete; CLOSED → delete + list;
   OPEN → keep; NO-PR → `git cherry origin/main origin/<branch>`
   (all `-` → delete; any `+` → keep).
4. Deletions batched 50 branches per `git push origin --delete` (11 pushes,
   524 refs, all succeeded).
