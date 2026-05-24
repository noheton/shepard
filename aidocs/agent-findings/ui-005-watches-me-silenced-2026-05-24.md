---
stage: deployed
last-stage-change: 2026-05-24
---

# UI-005 — silence `/watches/me` 404 spam on collection landing

**Status:** SHIPPED (frontend-only)
**Closes:** UI-2026-05-24-005 (MAJOR)
**Commit:** see `fix(frontend): silence watches/me 404 spam on collection landing — closes UI-005`
**Live URL:** https://shepard.nuclide.systems/collections/42 (LUMEN) + /collections/661923 (MFFD-Dropbox)

## What I found

The task framing suggested two paths — feature-flag the call (frontend) vs ship the
endpoint (backend) — but neither was the actual fix. The endpoint **already exists
and works correctly**:

- `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/resources/CollectionWatchersRest.java:81-110`
  documents `404 = caller is not currently watching` as **intentional** API design.
- `frontend/composables/context/useCollectionWatch.ts:36-42` (pre-fix) caught the 404
  and treated it semantically as "not watching" — the JS logic was correct.

The 404 was not a bug per se — it was the frontend asking a yes/no question
("am I watching this?") via a status code (200=yes, 404=no). The browser's
**native network logger** writes "Failed to load resource: 404" to the console
for any `fetch()` that returns 4xx, **regardless of whether JS catches the
rejection**. So no try/catch wrapper could ever silence the console pollution —
it had to stop emitting the 404 at the wire level.

Evidence (pre-fix): `aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence/p2-02-mffd-landing-1920.json`
shows a clean network 404 on `/v2/collections/019e55f3-75fb-7ef3-84fc-6238566b63ea/watches/me`
on every collection landing.

## Which path I picked

**Frontend-only — swap the endpoint, not the wire shape.**

`useCollectionWatch.refresh()` now uses two always-200 endpoints in parallel
instead of the one 404-emitting endpoint:

1. `GET /v2/collections/{appId}/watches` — list all watchers (returns 200 when
   caller has Read on the collection, which they do by construction when
   they're on the landing page).
2. `GET /v2/users/me` — resolve the caller's username (cached at module level
   for the session lifetime — `getMe` is called once per page-session, not
   per collection).

Then check `watchers.some(w => w.username === currentUsername)` to derive
`isWatching`. POST / DELETE wire is unchanged.

### Why not other paths

- **Keep `/watches/me` and try to suppress the log**: impossible — browsers
  log network 4xx unconditionally. Would have required filing a browser bug.
- **Change `/watches/me` to return `200 { watching: false }`**: backend
  contract change, breaks the generated client (regenerate + bump SDK
  version), drops a deliberately-designed API behavior (the `/me` endpoint
  is specifically the "works without Read" variant for users who lost
  access — the list endpoint cannot replace it for that case). Exceeds the
  "2-line backend fix" budget per the task constraints.
- **Defer the call to user interaction (lazy probe)**: UX regression — the
  bell-icon initial state would be "unknown / loading" on every page load.
- **Drop the watch button**: not on the table; CW1 is a shipped feature.

The `/me` endpoint stays untouched on the backend — it still has the legitimate
use of letting users check watch status on collections they no longer have
Read access to (e.g., a "stop watching collections I lost access to" cleanup
UX, when it eventually ships).

## Files changed

- `frontend/composables/context/useCollectionWatch.ts` — refresh() now uses
  `listWatchers` + `MeApi.getMe()` instead of `getMyWatch`. Module-singleton
  cache for the username (one `getMe` call per session). New
  `_resetUsernameCacheForTest()` helper for spec isolation.
- `frontend/tests/unit/useCollectionWatch.test.ts` — 18 specs (was 14)
  updated for the new wire. New specs: list-empty, list-membership,
  username-cache, "never calls /me" invariant, getMe-failure handling.
- `e2e/tests/ui-005-watches-me-silenced.spec.ts` — 2 e2e specs against the
  live deploy: LUMEN (id 42) + MFFD-Dropbox (id 661923). Asserts zero
  `/watches/me` requests + at least one list-watches request.

## Test results

- **Vitest (`useCollectionWatch.test.ts`)**: 18/18 pass (was 14/14).
- **Playwright e2e against `https://shepard.nuclide.systems`**: 2/2 pass.
  - LUMEN id 42 landing: 0 `/watches/me` requests, list-watches observed
  - MFFD-Dropbox id 661923 landing: 0 `/watches/me` requests, list-watches observed
- **Smoke test post-redeploy**: 25/25 pass.

## Live URLs verified

- https://shepard.nuclide.systems/collections/42 (LUMEN)
- https://shepard.nuclide.systems/collections/661923 (MFFD-Dropbox)

Both landings now log zero `/watches/me` 404s in the browser console.

## Build constraint noted (not in scope)

The frontend build (`make build-frontend`) is currently failing on main due to
two pre-existing TS errors that are **unrelated to UI-005**:

- `frontend/utils/helpMarkdown.ts:235-236` — `string | undefined` not assignable
  to `string` (committed in `92db2d92` UI-013, in committed code).
- `frontend/composables/common/api/useV1DeprecationMiddleware.ts:31` —
  `void | Promise<...>` not assignable to `Promise<...>` (in committed code,
  has a known fix in a sibling agent's stash `stash@{0}`).

Worked around for this deploy by setting `NUXT_TYPECHECK=false` on the build.
**The middleware-fix stash should be claimed by its owner and the helpMarkdown
errors fixed in a follow-up** — they are not silently broken (the runtime
behaviour is fine — they're strict-mode type errors that should be addressed).
Logged for visibility; not blocking UI-005's ship.

## What surprised me

- The task spec assumed "endpoint doesn't exist on the backend" — but the
  endpoint exists and works as designed. The advisor flagged this immediately.
  Lesson: an HTTP 404 in the browser console isn't always a "missing endpoint"
  smell; it can be a "404 used as a signal" API design choice.
- Modern browsers log network 4xx to the console unconditionally — `fetch()`
  with try/catch can suppress the JS-level rejection but not the network-pane
  log line. The only fix for console pollution is to not produce the 4xx.
- The frontend already had a parallel "watched collections" feature
  (`useWatchedCollections`) using client-side preference storage — distinct
  from CW1's server-side watch notifications. Two concepts named similarly;
  worth a docs pass to disambiguate (logged as a docs-side follow-up).
