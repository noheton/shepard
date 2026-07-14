---
stage: deployed
last-stage-change: 2026-07-14
---

# APISIMP Sweep — 2026-07-14 (fire-605)

**Scope:** Full v2 REST surface + plugin REST (git, aas, spatiotemporal, v1-compat, unhide) + lab-journal cluster + search + notifications + events + projects + snapshots + bundle groups + semantic + annotations + vocabulary.
**Prior sweep:** fire-604 (`apisimp-sweep-2026-07-14-fire604.md`) — F1 shipped same fire (direct commit); F2–F5 queued (nanos-to-ISO cluster); spatial F6–F10 remain blocked.

---

## What I found

One new dispatchable finding across category G (bare list without page wrapper):

| # | ID | Category | Size | File | Blocked? |
|---|-----|----------|------|------|----------|
| 1 | APISIMP-ME-GIT-CRED-LIST-ENVELOPE | G (bare List, no PagedResponseIO wrapper) | XS | `MeCredentialsRest.java:76-77` | No |

Immediately dispatchable: finding 1.

Surface confirmed clean this fire: `CollectionDQRRest`, `IndependenceProofRest`,
`LabJournalEntryRest`, `CollectionLabJournalEntriesRest`, `LabJournalHistoryRest`,
`LabJournalRenderRest`, `NotebookRest`, `SearchV2Rest`, `AasAdminRest`, `AasWellKnownRest`,
`AasRegistrationAdminRest`, `SpatialPromoteRest`, `LegacyV1StatsAdminRest`, `UnhideFeedRest`,
`NotificationRest`, `NotificationAdminRest`, `CollectionEventsRest`, `ProjectsRest`,
`MeRoleInRest`, `PluginsAdminRest`, `CollectionWatchersRest`, `CollectionWatchesRest`,
`FlatPublicationsRest`, `SnapshotListRest`, `BundleGroupsV2Rest`, `SemanticAdminRest`,
`SemanticAnnotationV2Rest`, `SemanticTermSearchRest`, `PersonalVocabularyRest`,
`InstanceRegistryRest`.

---

## Finding 1 — APISIMP-ME-GIT-CRED-LIST-ENVELOPE

**Category G — bare `List<T>` returned without `PagedResponseIO` wrapper**

`MeCredentialsRest.java:73-77`:
```java
@APIResponse(
  responseCode = "200",
  content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = GitCredentialIO.class))
)
public Response list(@Context SecurityContext securityContext) {
  ...
  List<GitCredentialIO> result = gitCredentialDAO.findAllByUser(caller).stream()
      .map(GitCredentialIO::new).toList();
  return Response.ok(result).build();
}
```

`GET /v2/me/git-credentials` returns a bare JSON array (`SchemaType.ARRAY`) with no
`PagedResponseIO` envelope and no `page`/`pageSize` parameters. This is inconsistent
with its admin counterpart `GET /v2/admin/users/{username}/git-credentials`
(`AdminUserGitCredentialRest.list()`), which was upgraded to `PagedResponseIO<AdminGitCredentialListItemIO>`
with real `page`/`pageSize` params in fire-599/600 (PR #2563, SHA b762a265).

The asymmetry forces OpenAPI clients to import two different list shapes for the same
resource type. Frontend composables that call both endpoints see one as a plain array
and the other as a `{items, total, page, pageSize}` envelope — a shape inconsistency
that surfaces as a type error on the generated client side.

Git credentials per user are naturally bounded (O(1–5) per user), so pagination has
no performance motive. The fix is purely about envelope consistency with the admin
counterpart and the general v2 surface contract.

**Fix:**
1. Add `page`/`pageSize` params to `list()` with `@DefaultValue("0")`/`@DefaultValue("50")`.
2. Change return to `new PagedResponseIO<>(slice, total, page, pageSize)` with in-memory slice
   (same safe `long from = Math.min((long) page * pageSize, total)` arithmetic as the admin endpoint).
3. Update `@APIResponse` schema from `SchemaType.ARRAY` to `implementation = PagedResponseIO.class`.
4. Update `backend-client` generated model.
5. Update frontend composable reading `.items` instead of plain array.

**Size: XS.** One REST method + one `@APIResponse` + test + frontend composable update.

**Wire-safe:** the admin counterpart's same fix (fire-599/600) changed shape from bare array
to `PagedResponseIO`. This user-facing endpoint can follow the same change; frontend callers
expecting a bare array must be updated in the same PR.

---

## Opportunities

1. **Finding 1** is an XS with a direct precedent in the admin endpoint fix (PR #2563). The
   admin endpoint PR can serve as a template — copy the `from`/`slice` arithmetic, copy the
   `@APIResponse` update, and adapt the composable change. Estimated 20 minutes.
2. **Batch with any nanosecond-ISO PR in the git plugin** — if a fire takes up
   `APISIMP-GIT-CRED-CREATED-AT-ISO` (currently queued, line 5329 of aidocs/16), the two
   changes are in the same file and can be combined.

---

## Gaps & blockers

- **Spatial findings from fire-599** (F6–F10) remain blocked on SPATIAL-V6-003 / PLUGIN-V2-001.
  No change in status.
- **Nanos-to-ISO cluster from fire-604** (F2–F5: APISIMP-REST-CHANNEL-DATA-NANOS-TO-ISO,
  APISIMP-CROSS-DO-BULK-START-END-NANOS, APISIMP-TS-ANNOTATION-IO-NS-TO-ISO,
  APISIMP-ANOMALY-INTERVAL-NS-TO-ISO) remain queued. F2–F3 need a frontend migration window
  before enforcement.

---

## What surprised me

The surface is substantially converged at this point. After 605 fires the main remaining
dispatchable work is the nanosecond-to-ISO cluster (4 rows, fire-604) and this one envelope
inconsistency. The user-facing git credential list (`/v2/me/git-credentials`) was the only
endpoint found that returns a plain array where its admin sibling uses a `PagedResponseIO`
envelope — a one-off shape inconsistency introduced by the admin upgrade in fire-599 not
being applied symmetrically to the user-facing endpoint in the same PR.
