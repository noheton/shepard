# Worktree Consolidation Triage — 2026-05-23

Triage of the six dirty agent worktrees flagged in `aidocs/16` tasks
#149–#154. Inspection-only — no merges performed, no worktrees modified.

Base of comparison: `main` at `b647a0e3` (`feat(mffd-import): v15.13 —
PRE-FLIGHT-TOTALS banner + CONTAINED-COMPLETENESS pass`).

All six worktree branches share an ancient base (`fe61df00`, ~213
commits behind `main`). The `worktree-agent-*` branches have HEAD at
that base — i.e. **no commits ahead of main**, only working-tree mutations.
The one branch that actually carries committed work
(`fs1e3-rollback-fields`) is also based on that ancient base, so a
straight `git merge` would either no-op or rewrite half the repo with
deletions. **None of these can be merged without rebase or cherry-pick**.

## Summary table

| Worktree | Branch | Ahead | Scope | Verdict | Action |
|---|---|---|---|---|---|
| `a23a1610` | `worktree-agent-a23a1610720759395` | 0 (uncommitted only) | 6 v2 REST files, follow-up #148 (DataObject permissions) — 46 +/13 − | **SALVAGE-PARTS** — uncommitted, follow-up to #148 (63515a32) | Cherry-pick the diff onto main as one commit; close gaps for PublishRest + 4 other call sites |
| `a3a8672d` | `worktree-agent-a3a8672df0d4c466c` | 0 (uncommitted only) | Net vs main: `CollectionIO.java` (-13 lines, **regression**) + `frontend/.../[collectionId]/index.vue` (mixed bookmark/heroImage) | **OBSOLETED + REGRESSION-RISK** — heroImage already on main; uncommitted changes regress v1 byte-compat | Drop. Do not merge. |
| `a02dc8eb` | `fs1e3-rollback-fields` | 3 commits ahead, clean tree | FS1e3a/b/c — `:ShepardFile` rollback fields, `rollbackOne()`, `POST /v2/admin/files/migrate/rollback/{appId}` (1115 +/6 −) | **MERGE-AFTER-REBASE** — committed, scope coherent, not on main, no main collision | Rebase branch onto `main`; PR-ready after rebase |
| `a798dc76` | `worktree-agent-a798dc76c44bb41b0` | 3 commits ahead, clean tree | LIC1 — license + accessRights backend, frontend SPDX components, docs | **OBSOLETED** — backend ALREADY on main (`e0a571d4 REF-1 + ANC-1 + QA-1 + FAIR-1`); frontend + docs may still be valuable | Inspect frontend commit 88948764 + docs commit d3b713eb for parts not on main; SALVAGE-PARTS if frontend missing |
| `a57fdb01` | `worktree-agent-a57fdb0115620aa2f` | 4 commits ahead, clean tree | V1COMPAT.0 Phase 1 hardening — `@ApplicationScoped` on filters, drop startup observer, async middleware, smoke-test v1 headers | **SALVAGE-PARTS** — 2 of 4 commits already on main, 2 are still needed | Cherry-pick `80c176d4` (`@ApplicationScoped`) + `bdb87c01` (drop seedIfNeeded) + `a7af5f38` (smoke-test); skip `325f9190` (already on main) |
| `aa3378ed` | `worktree-agent-aa3378eddd141970b` | 1 commit ahead, clean tree | File-upload progress (xhrUpload, useFileUploadProgress, FileUploadProgressPanel) + 22 Vitest tests + Playwright | **MERGE-AFTER-REBASE** — none of these files on main; not superseded by anything shipped | Rebase commit `7d6c15ab` onto main; PR-ready after rebase |

## Per-worktree details

### a23a1610 — task #149 (6 v2 REST files, follow-up #148)

**Identity.** Branch `worktree-agent-a23a1610720759395`, head `fe61df00`
(matches its base, 0 commits ahead of base, 213 behind `main`). Working
tree has 6 modified `.java` files, **uncommitted**.

**Scope.** Six v2 REST resources tightened against the #148 permissions
fix:
- `v2/bundle/.../FileBundleReferenceRest.java` — comment clarification (no behaviour change vs main)
- `v2/file/.../FileReferenceV2Rest.java` — comment + new `isAccessAllowedForDataObjectAppId` call at line 144 (behaviour change)
- `v2/labjournal/.../LabJournalHistoryRest.java` — appId-aware fallback ternary (behaviour change)
- `v2/labjournal/.../LabJournalRenderRest.java` — appId-aware fallback ternary (behaviour change)
- `v2/publish/.../PublishRest.java` — drops trailing `0L` arg on both `isAccessTypeAllowedForUser` call sites (behaviour change — main still has the `0L` bug at lines 133 + 219)
- `v2/timeseries/.../TimeseriesReferenceV2Rest.java` — appId-aware fallback ternary (behaviour change)

Total 46 +/13 −. Single-concern: the follow-up half of `#148`.

**Commit status.** **At-risk-of-loss** — all six edits are in working
tree only, no commits. Per `feedback_agent_worktree_must_commit.md`,
this is the highest-risk category in this batch.

**Mergeability.** Cannot `git merge` (branch has no commits ahead).
The diff applies cleanly file-by-file against main (verified: each file
on main differs from the worktree by exactly the lines shown).

**Overlap.** Main already has `63515a32 fix(#148): use 3-arg
PermissionsService overload at DataObject v2 call sites`, which covered
some sites but NOT these six. The worktree work is the **next-pass
completion of #148**, not a duplicate.

**Verdict.** **SALVAGE-PARTS** (the whole diff is salvage-worthy; the
naming reflects that it's uncommitted rather than a branch).

**Recommended action.**
1. Capture the diff from a23a1610 working tree as a single patch.
2. Apply on a fresh branch off `main`, build + test.
3. Commit as `fix(#148): complete 3-arg PermissionsService rollout to 6
   remaining v2 sites` citing the predecessor `63515a32`.
4. Run smoke-tests on a DataObject-write path (e.g. PublishRest +
   FileReference + LabJournal endpoints) to confirm permission gates
   actually pass for authorised callers (the 4-arg bug was a
   fail-closed regression).
5. Update `aidocs/34` row #148 noting the second-pass commit.

---

### a3a8672d — task #150 (13 LIC1/heroImage files)

**Identity.** Branch `worktree-agent-a3a8672df0d4c466c`, head `0a3597cb`
(matches its base, 0 commits ahead, 235 behind `main`). Working tree has
12 modified files + 1 added migration, **uncommitted**.

**Scope.** Claims 13 files for LIC1 + heroImage. Inspection vs `main`:
- `Collection.java` — **identical to main** (heroImage already shipped).
- `CollectionIO.java` — **net 13 lines REMOVED** including the load-bearing
  `@JsonInclude(JsonInclude.Include.NON_NULL)` annotation that keeps
  `heroImageUrl` off the v1 wire. **This is a regression** — main's
  comment block explicitly explains why the annotation is needed for
  v5.2.0 byte-fidelity.
- `CollectionService.java`, `CollectionV2RestTest.java`,
  `EditCollectionDialog.vue`, `collectionEditTypes.ts`,
  `useEditCollection.ts`, `Collection.ts` (client) — **identical to
  main** (shipped via main's heroImage commits).
- `frontend/pages/collections/[collectionId]/index.vue` — 47 +/30 −,
  but mixes a hero-image inline editor with a **rename of
  `useWatchedCollections` → `useBookmarkedCollections`** and a
  binoculars-icon-→star rename. Main has `useWatchedCollections` as
  the canonical (Watched-collections SHIPPED feature); the
  Bookmark variant appears to be an abandoned earlier draft that
  predates the Watched/CW1 work. Re-applying this would back out a
  shipped feature.
- `aidocs/34`, `aidocs/44`, `package-lock.json`, `V54__NOOP_heroImageUrl_additive.cypher`
  — V54 migration already on main; tracker rows duplicated.

**Commit status.** **At-risk-of-loss** (uncommitted). But the only
*new* content vs main is the regression on `CollectionIO.java` + the
broken `[collectionId]/index.vue` mixed-concerns diff.

**Mergeability.** N/A — there is no useful net delta to merge.

**Overlap.** heroImageUrl shipped via main's
`9b90c283 feat: collection watchers, import validator, live-window, me v2,
frontend UX polish`. LIC1 backend shipped via `e0a571d4 feat(backend):
REF-1 + ANC-1 + QA-1 + FAIR-1`. Both completely superseded.

**Verdict.** **OBSOLETED + REGRESSION-RISK**.

**Recommended action.**
1. Do **not** merge or cherry-pick.
2. Leave the worktree in place for one cleanup cycle in case the
   author wants to extract their hero-image inline-editor idea from the
   `index.vue` change (the editor UI itself is reasonable — only the
   bookmark rename is the broken part).
3. After one cycle, drop the worktree.

---

### a02dc8eb — task #151 (FS1e3 rollback fields + REST)

**Identity.** Branch `fs1e3-rollback-fields` (not the
`worktree-agent-*` style — this one carries committed work). Head
`963f2213`, 3 commits ahead of a `fe61df00` base, 213 behind main.
**Clean working tree.**

**Scope.** Three coherent commits:
1. `5baf35d1 feat(FS1e3a)` — add 4 nullable `:ShepardFile` rollback
   bookkeeping fields + `V59__NOOP_ShepardFile_migration_rollback_fields.cypher`
   + 96 lines of `ShepardFileTest` wire-shape regression. 215 +/0 −.
2. `365d6d7e feat(FS1e3b)` — `FileMigrationService.rollbackOne(appId)` +
   stamp-before-swap inside `migrateOne`'s Cypher. 497 +/0 −.
3. `963f2213 feat(FS1e3c)` — `POST /v2/admin/files/migrate/rollback/{appId}`
   admin REST + 6 unit tests + operator runbook section in
   `docs/reference/file-storage.md`. 1115 +/6 −.

Single-concern: GridFS → S3 per-file rollback path (FS1e3a/b/c).
All three commits ship migration + test + docs per CLAUDE.md.

**Commit status.** Committed cleanly, three commits, clean tree. No
loss risk.

**Mergeability.** Cannot `git merge` (branch base is too old; main
has 213 commits the branch doesn't). But cherry-pick of the three
commits onto main is the natural path. Verified no collision on `main`:
- `ShepardFile.java`: no `previousProviderId` / `previousLocator` /
  `migrationHmac` field on main.
- `FileMigrationService.java`: no `rollbackOne` method on main.
- `FileMigrationRest.java`: no `rollback` endpoint on main.
- `V59`: not on main.

No FS1e3 traces in `aidocs/34` or `aidocs/44` on main, so the rebase
also has room to land the row updates the commits already include.

**Overlap.** None — completely unshipped.

**Verdict.** **MERGE-AFTER-REBASE**.

**Recommended action.**
1. `git checkout main && git checkout -b fs1e3-rollback-fields-rebased`
2. `git cherry-pick 5baf35d1 365d6d7e 963f2213` (or rebase the branch
   onto main).
3. Resolve `aidocs/34`/`aidocs/44` row insertion conflicts (main has
   moved; insert the FS1e3 row in current position).
4. `mvn verify` for the new `FileMigrationServiceTest` +
   `FileMigrationRestTest` + `ShepardFileTest`.
5. PR title: `feat(FS1e3): per-file GridFS↔S3 migration rollback
   (entity + service + admin REST + runbook)`.
6. Model-inventory update (per `feedback_model_inventory_maintained.md`)
   for the four new `:ShepardFile` fields.

---

### a798dc76 — task #152 (LIC1 license + accessRights)

**Identity.** Branch `worktree-agent-a798dc76c44bb41b0`, head `d3b713eb`,
3 commits ahead of base `fe61df00`, 213 behind main. **Clean working tree.**

**Scope.** Three commits:
1. `a5bc6405 feat(LIC1) backend` — JavaDoc tightening on
   `AbstractDataObject` + `AbstractDataObjectIO`, OpenAPI `enumeration`
   hint on `accessRights`, **14 new Jackson serialization tests**
   (CollectionIOTest + DataObjectIOTest). 180 +/11 −.
2. `88948764 feat(LIC1) frontend` — LicenseChip / AccessRightsChip /
   LicenseInput / AccessRightsInput components, `utils/spdxLicenses.ts`
   curated 28-license SPDX list, wired into Create/Edit dialogs for
   Collection + DataObject, table column, 13 Vitest tests. 614 +/4 −.
3. `d3b713eb docs(LIC1)` — `aidocs/34` + `aidocs/44` + `aidocs/42`
   updates, NEW `docs/reference/collections.md` + `docs/reference/data-objects.md`.
   242 +/0 −.

**Commit status.** Committed cleanly, three commits, clean tree.

**Mergeability.** Backend commit `a5bc6405` is **partially superseded** —
`e0a571d4 feat(backend): REF-1 + ANC-1 + QA-1 + FAIR-1` already added
the `license` + `accessRights` fields on main. But `a5bc6405` also
brings JavaDoc tightening + the 14 new IO tests, which are NOT on main
(confirmed: `git log -S "license" -- CollectionIOTest.java DataObjectIOTest.java`
returns nothing pointing at LIC1).

Frontend commit `88948764` — `spdxLicenses.ts`, `LicenseChip.vue`,
`AccessRightsChip.vue`, `LicenseInput.vue`, `AccessRightsInput.vue` —
none of these files exist on main. The wiring touches `EditCollectionDialog.vue`,
`CreateCollectionDialog.vue`, `CollectionList.vue` etc.; those will
conflict because main has evolved.

Docs commit `d3b713eb` — `docs/reference/collections.md` +
`docs/reference/data-objects.md` are NEW files; likely no conflict
unless main grew similar pages.

**Overlap.** Backend wire fields already shipped; frontend UI +
tracker + docs **not shipped**.

**Verdict.** **SALVAGE-PARTS** — drop the now-redundant entity field
changes from `a5bc6405`, keep the IO tests + JavaDoc fixes. Cherry-pick
`88948764` (frontend) and `d3b713eb` (docs) with conflict resolution.

**Recommended action.**
1. Branch off main.
2. Apply only the test additions + JavaDoc fixes from `a5bc6405` (skip
   the entity field additions that are already merged).
3. Cherry-pick `88948764` (frontend); resolve conflicts in the dialog
   wiring against main's current dialog shapes.
4. Cherry-pick `d3b713eb` (docs); confirm `aidocs/42` LIC1 paragraph
   isn't already there.
5. Run `npm test -- spdxLicenses` + Vitest LicenseChip tests.
6. Verify `frontend/utils/spdxLicenses.ts` is current vs SPDX 3.x list.

---

### a57fdb01 — task #153 (V1COMPAT.0 Phase 1 fixes)

**Identity.** Branch `worktree-agent-a57fdb0115620aa2f`, head `a7af5f38`,
4 commits ahead of base `85dfe2ba` (which IS on main), 163 behind main.
**Clean working tree.**

**Scope.** Four commits closing live-validation defects:
1. `80c176d4 fix(V1COMPAT.0)` — `@ApplicationScoped` on both
   `LegacyV1DeprecationFilter` + `LegacyV1GateFilter` + reflection-based
   regression test pinning both annotations. 73 +/0 −.
2. `bdb87c01 fix(V1COMPAT.0)` — drop startup observer; rely on V63 +
   lazy seed in `current()`. 63 +/33 −.
3. `325f9190 fix(V1COMPAT.0)` — async middleware fix +
   `SHEPARD_AUDIT_INSTANCE_SECRET` placeholder. 8 +/1 −.
4. `a7af5f38 test(smoke)` — 3 v1 deprecation header assertions in
   `infrastructure/smoke-test.sh`. 41 +/0 −.

**Commit status.** Committed cleanly, four commits, clean tree.

**Mergeability.** Per-commit check against main:
- `80c176d4` (`@ApplicationScoped`) — **NOT on main** — main's
  `LegacyV1DeprecationFilter.java` + `LegacyV1GateFilter.java` are
  `@Provider`-only; cherry-pick needed.
- `bdb87c01` (drop seedIfNeeded) — **NOT on main** — main's
  `LegacyV1ConfigService.java` still has `@Observes StartupEvent` +
  `onStart` + `seedIfNeeded()`; cherry-pick needed.
- `325f9190` (async + audit secret) — **ALREADY on main**:
  - `useV1DeprecationMiddleware.ts` already has `async function post`
  - `infrastructure/docker-compose.override.yml` already has the
    `SHEPARD_AUDIT_INSTANCE_SECRET: "demo-instance-secret-placeholder-do-not-use-in-prod"` line.
  - **Skip this commit on cherry-pick.**
- `a7af5f38` (smoke-test) — **NOT on main** — no v1-deprecation header
  probes in `infrastructure/smoke-test.sh` on main; cherry-pick needed.

**Overlap.** Mixed — 1 of 4 commits already landed via another path.

**Verdict.** **SALVAGE-PARTS** — cherry-pick three of four commits.

**Recommended action.**
1. Branch off main.
2. `git cherry-pick 80c176d4 bdb87c01 a7af5f38` (in that order).
3. `mvn verify` for the v1-compat plugin tests, particularly
   `LegacyV1FilterRegistrationTest` (annotation pin) +
   `LegacyV1ConfigServiceTest.service_hasNoStartupEventObserver`.
4. Run the new smoke-test in `infrastructure/smoke-test.sh` against a
   live deploy to confirm the deprecation headers actually emit (the
   load-bearing live-validation that closes the loop).
5. Update `aidocs/34` V1COMPAT.0 row noting the three-commit hardening.

---

### aa3378ed — task #154 (file-upload progress)

**Identity.** Branch `worktree-agent-aa3378eddd141970b`, head `7d6c15ab`,
1 commit ahead of base `fe61df00`, 213 behind main. **Clean working tree.**

**Scope.** One large commit `7d6c15ab feat(UX): wire file-upload
progress (bytes + ETA + cancel)` — 1515 +/55 − over 13 files:
- NEW: `xhrUpload.ts`, `useFileUploadProgress.ts`,
  `FileUploadProgressPanel.vue`, `xhrUpload.test.ts` (10 tests),
  `useFileUploadProgress.test.ts` (12 tests), `upload-progress.spec.ts`
  (Playwright at 4K).
- MODIFIED: `FileContainerAccessor.ts`,
  `DataObjectFileUploadDialog.vue`, `FileUploadDialog.vue`,
  `UploadFilesButton.vue`, `VideoStreamReferencesPane.vue`,
  `containers/files/[containerId]/index.vue`, `aidocs/44`.

Single-concern: XHR-based upload progress (with cancel + ETA + per-file
rows) — replaces the spinner-only legacy.

**Commit status.** Committed cleanly, clean tree.

**Mergeability.** Per-file check against main:
- `xhrUpload.ts`, `useFileUploadProgress.ts`,
  `FileUploadProgressPanel.vue`, `upload-progress.spec.ts` — **none
  exist on main**. No git log entry for any of them.
- `git log main -S "xhrUpload"` → empty.
- `aidocs/16` references "#135 file upload progress" but does not show
  a separate task line for it (likely referred-to in
  `BUG-UI-FILEREF-EMPTY-OIDS` row's "Pairs with… #135"); no shipped
  flag for #135.
- Wiring touches existing files that have evolved on main; rebase will
  almost certainly conflict on `FileContainerAccessor.ts` +
  `DataObjectFileUploadDialog.vue` (high-traffic files since 2026-05-22).

**Overlap with shipped #135.** No evidence #135 has shipped. The
`aidocs/16` row uses #135 as a future reference, not a completed feature.
The prompt's "vs already-shipped #135" framing appears mistaken — this
work is NOT superseded.

**Verdict.** **MERGE-AFTER-REBASE**.

**Recommended action.**
1. Branch off main.
2. `git cherry-pick 7d6c15ab` and resolve the expected conflicts in
   `FileContainerAccessor.ts`, the two upload dialogs, and
   `containers/files/[containerId]/index.vue` (where main has likely
   evolved upload-button wiring).
3. `npm test -- xhrUpload useFileUploadProgress` to confirm the 22
   Vitest tests pass post-rebase.
4. Run `e2e/tests/upload-progress.spec.ts` at 4K viewport per
   `feedback_validate_user_viewport.md`.
5. Update `aidocs/44` with a #135-equivalent shipped row.
6. Flo's original trigger was a 507 MB Confluence-zip upload showing
   only a spinner — re-validate by uploading a sizable file to confirm
   the progress bar + ETA + cancel actually fire.

---

## Cross-cutting findings

### Pattern 1 — Agents writing into worktrees ~3 weeks behind main, then never rebasing

All six branches share `fe61df00` (the commit that landed the dagre
graph rename) as ancestor. That commit landed at the start of an
agent-batch dispatch in early May. From there, `main` advanced 213
commits before any of these were brought back. Two of the six landed
work that was duplicated by other agents working on main (LIC1
backend, heroImage, V1COMPAT.0 secret placeholder + async middleware).
Net: ~12 % of the committed work was redundant by the time triage
happened.

**Fix idea.** Add a rebase gate to the agent-dispatch protocol — if a
worktree's base is >50 commits behind main, the agent's first action
is "rebase onto main" before doing anything else. Otherwise duplicate
work is the default.

### Pattern 2 — Two of six worktrees ended uncommitted

`a23a1610` (6 files of #148 follow-up) and `a3a8672d` (heroImage +
LIC1 + frontend mixed concerns) both ended with uncommitted edits
only. Per `feedback_agent_worktree_must_commit.md`, this is the at-risk
category. The a3a8672d state turned out to be obsoleted-plus-broken
(no loss), but a23a1610 had genuinely valuable follow-up that would
have been lost on the next cleanup.

**Fix idea.** A pre-completion hook checking `git diff --quiet ||
git diff --cached --quiet` in any active worktree would have caught
both before the agents reported done.

### Pattern 3 — Tracker rows added in worktree, never reaching main

a3a8672d, a798dc76, aa3378ed all add `aidocs/34` and/or `aidocs/44`
rows that never landed on main. The tracker entries point at PRs /
commits that don't exist on main, leaving stale references that
trace-feature.sh would flag.

**Fix idea.** Per `feedback_github_pm_policies.md` §5 pre-flight
checklist — when a worktree's tracker row points at an unmerged
commit, the dispatcher should warn the next agent picking up the work.

### Pattern 4 — One-of-many: scope mixing in working tree

a3a8672d had heroImage editor mixed with a `useWatchedCollections` →
`useBookmarkedCollections` revert. That second concern is unrelated to
task #150 and would have backed out shipped work. Single-concern
worktrees (FS1e3, file-upload-progress) salvaged cleanly. Worktrees
where the agent's intermediate work tangled with an earlier branch
direction did not.

**Fix idea.** Worktree-level `git diff --stat HEAD` at agent dispatch
start (against the branch's claimed scope) would have caught the
bookmark revert — files outside the task's named scope should require
an explicit "I'm also touching X because Y" note in the agent's
report.

## Recommended dispatch order

Ordered by `(value × ease of landing)`:

1. **a02dc8eb (FS1e3)** — three coherent committed commits, no main
   conflict, fully unshipped. Lowest risk, highest cohesion. Rebase
   and PR first.
2. **a23a1610 (#148 follow-up)** — uncommitted but at-risk. Capture
   the patch immediately so it survives the next cleanup pass. Then
   apply as a single commit on main.
3. **a57fdb01 (V1COMPAT.0)** — cherry-pick three of four commits;
   skip the one that's already on main. Closes V1COMPAT.0 Phase 1
   live-validation defects.
4. **aa3378ed (file upload progress)** — one big commit, will have
   merge conflicts but content is genuinely missing on main and the
   user explicitly hit the missing feature on a 507 MB upload. Worth
   the rebase pain.
5. **a798dc76 (LIC1 frontend + docs)** — backend already shipped; the
   frontend chip components + tests + docs are the salvage value.
   Some conflict resolution; lower urgency than the others because
   the wire fields are usable today without the UI components.
6. **a3a8672d (heroImage + LIC1)** — drop. Optionally extract the
   `[collectionId]/index.vue` inline hero-URL editor idea before
   dropping, but the commit as a whole is obsoleted and the
   `CollectionIO.java` change is an active regression. **Bring to
   Flo only if the inline-editor UI is wanted as a follow-up task.**
