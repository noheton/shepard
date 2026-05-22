---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 74 — Auto-Refresh on Stale Session

**Status.** Design — initial draft 2026-05-19.
**Audience.** Frontend contributors.
**Relates to.** `aidocs/ops/85-ui-overhaul-design.md` (UX overhaul),
`aidocs/16-dispatcher-backlog.md` (backlog item #49).

---

## 1. Problem

Users get stuck in two distinct ways during a live session:

**A. Token expiry mid-session.** The OIDC access token expires (typically
after 5–60 minutes depending on the Keycloak realm configuration). All
subsequent API calls receive `401 Unauthorized`. The current frontend
has no interceptor — the composable throws, the component shows an
error toast, and the user must manually navigate to `/auth/signIn`.
For a researcher mid-upload or mid-annotation, this is data-loss risk.

**B. Stale JS chunk after a deploy.** When a new frontend image is
deployed while a browser tab is open, the Nuxt router tries to lazy-load
a route chunk by its content-hash filename (`/_nuxt/<old-hash>.js`).
That file no longer exists on the server — the browser gets a 404,
Nuxt raises a `ChunkLoadError`, and the page goes blank or throws an
unhandled promise rejection.

`StaleBundleBanner.vue` (at `frontend/components/layout/StaleBundleBanner.vue`)
already handles the slow-path case: it polls `/shepard/api/versionz` every
5 minutes and shows a "Refresh available" banner when the reported
`shepardVersion` changes. `nuxt.config.ts` handles the fast-path
case with `experimental.emitRouteChunkError: "automatic"`, which
triggers an automatic one-shot page reload on the next navigation after
a chunk 404. Both mechanisms exist but leave gaps described below.

---

## 2. Current Mechanisms and Gaps

| Mechanism | What it covers | Gap |
|---|---|---|
| `StaleBundleBanner.vue` | Slow-path: deploy happened; user is still on old bundle | Does not handle token expiry. Up to 5 minutes of stale-chunk risk between polls. |
| `emitRouteChunkError: "automatic"` | Fast-path: chunk 404 on navigation triggers one reload | Does not guard against reload loops (if the chunk is genuinely missing, repeated reloads thrash). No user-visible feedback. |
| nuxt-auth `sessionRefresh.enablePeriodically: 30s` | Keeps the nuxt-auth server-side session alive | The 30-second server-session ping does NOT refresh the OIDC access token itself; it refreshes the NextAuth session cookie. If Keycloak invalidates the token, the 401 still surfaces. |
| None | 401 responses from `/v2/` or `/shepard/api/` endpoints | No recovery attempt today. |
| None | Warning before expiry | Users have no advance notice that sign-in is required. |

---

## 3. Proposed Additions

### 3.1 401 interceptor in API composables

`useV2ShepardApi.ts` and `useShepardApi.ts` both construct an API
client instance with the `accessToken` from `useAuth().data`. The
generated client throws an `ApiException` (or equivalent) on non-2xx
responses, which callers handle via `try/catch`.

Add a thin wrapper — either a Nuxt plugin or a composable — that
intercepts `401` responses:

1. Call `useAuth().refresh()` (nuxt-auth's built-in refresh flow,
   which triggers the JWT callback in `NuxtAuthHandler` and exchanges
   the refresh token with Keycloak).
2. Retry the original request once with the new token.
3. If the retry also returns `401` (refresh token also expired, or
   Keycloak session revoked), redirect to `/auth/signIn` with a
   `callbackUrl` query parameter pointing at the current route so the
   user lands back where they were after re-authenticating.

Implementation options:

**Option A — Nuxt plugin (`plugins/auth-interceptor.ts`).** Hook into
`$fetch` via `ofetch`'s `onResponseError` interceptor. Applies
globally to all `$fetch` calls including those made by generated
API clients. Simple but intercepts _all_ 401s including public-endpoint
scenarios.

**Option B — Composable wrapper.** Add a `withAuthRetry(fn)` helper
that wraps any async API call, catches `401`, and applies the
refresh-and-retry logic. Composables adopt it explicitly. More surgical
but requires updating every API composable.

Recommendation: **Option A** for v1 — the `ofetch` interceptor is the
established Nuxt pattern and the public-endpoint 401 risk is low in
practice (all non-auth endpoints require a valid session anyway).

### 3.2 ChunkLoadError reload guard

`emitRouteChunkError: "automatic"` in `nuxt.config.ts` already triggers
one reload. The gap is reload loops: if the reload lands on the same
stale bundle (e.g. CDN cache hit), the chunk 404s again → reload again
→ infinite loop.

Add a `sessionStorage` guard:

```typescript
// plugins/chunk-error-guard.client.ts
export default defineNuxtPlugin(() => {
  const KEY = "shepard:chunkErrorReloaded";
  window.addEventListener("unhandledrejection", (event) => {
    const err = event.reason;
    if (typeof err?.message !== "string") return;
    if (!err.message.includes("Loading chunk") && !err.message.includes("ChunkLoadError")) return;
    if (sessionStorage.getItem(KEY)) {
      // Already reloaded once this session — don't loop.
      // Show the stale-bundle banner instead (already handles this case).
      return;
    }
    sessionStorage.setItem(KEY, "1");
    window.location.reload();
  });
});
```

Clear the flag on a successful full navigation (Nuxt's `page:finish`
hook) so a genuine error on a later visit isn't blocked.

Note: `emitRouteChunkError: "automatic"` already handles the route-navigation
path. This plugin targets the `unhandledrejection` path (dynamic
imports outside navigation, e.g. inside `onMounted` or composables).

### 3.3 Session expiry warning toast

Derive the token's expiry time from the JWT `exp` claim available in
`useAuth().data.value.accessToken`. Show a dismissible `v-snackbar`
warning 5 minutes before expiry with a "Stay signed in" button that
calls `useAuth().refresh()`.

```
┌─────────────────────────────────────────────────────────────────┐
│  ⏱  Your session expires in 5 minutes.                          │
│  [ Stay signed in ]   [ Dismiss ]                               │
└─────────────────────────────────────────────────────────────────┘
```

Implementation sketch:

1. Parse the JWT `exp` field from `accessToken` (base64url decode the
   payload — no library needed; the token is already decoded by
   nuxt-auth's `data.value` but the raw `exp` may not be surfaced).
2. `setTimeout(() => showWarning(), (exp * 1000) - Date.now() - 5 * 60 * 1000)`
   set on session load / refresh.
3. "Stay signed in" calls `refresh()`, which resets the timer.
4. If the user dismisses and the token expires, the 401 interceptor
   (§3.1) handles recovery.

Place this in a `SessionExpiryWarning.vue` component mounted in the
default layout alongside `StaleBundleBanner.vue`.

---

## 4. Interaction Between Mechanisms

The three additions are complementary and non-overlapping:

| Scenario | Recovery path |
|---|---|
| Token expires, user triggers an API call | §3.1 interceptor: refresh → retry → redirect if still 401 |
| Token near expiry | §3.3 warning toast: user clicks "Stay signed in" before the 401 occurs |
| Deploy rotates chunks during navigation | `emitRouteChunkError: "automatic"` (existing) + §3.2 guard prevents loops |
| Slow-deploy: bundle stale but chunks still load | `StaleBundleBanner.vue` (existing): user sees banner, reloads on their own schedule |

---

## 5. Implementation Notes

### 5.1 No backend changes needed

All three additions are client-side only. The backend already issues
`401` on invalid tokens. Keycloak refresh token exchange happens via
nuxt-auth's `refresh()` which calls the server-side NextAuth handler —
no new API endpoints.

### 5.2 nuxt-auth version compatibility

`@sidebase/nuxt-auth` is already in use (configured in `nuxt.config.ts`
under `auth.provider.type: "authjs"`). The `useAuth()` composable
exposes `refresh()` as a first-class method. The `data.value.accessToken`
field is populated by the `jwt` callback in `NuxtAuthHandler`. No
upgrade needed.

### 5.3 `sessionRefresh.enablePeriodically: 30s`

The existing 30-second periodic session refresh keeps the NextAuth
session cookie alive but does not proactively refresh the OIDC access
token if the Keycloak token lifetime is shorter than 30 seconds. In
practice Keycloak token lifetimes are 5–60 minutes — the 30-second
session ping is not the same as a token refresh. The §3.3 warning toast
is the correct UX for the token-expiry case; the 30-second ping is
correct for the session-cookie case. Both are needed.

### 5.4 File placement

| Artefact | Path |
|---|---|
| 401 interceptor plugin | `frontend/plugins/auth-interceptor.client.ts` |
| ChunkLoadError guard | `frontend/plugins/chunk-error-guard.client.ts` |
| Session expiry warning | `frontend/components/layout/SessionExpiryWarning.vue` |
| Layout mount | `frontend/layouts/default.vue` (alongside `StaleBundleBanner`) |

---

## 6. Open Questions

| # | Question | Current thinking |
|---|----------|-----------------|
| OQ1 | Should the 401 interceptor apply to `/shepard/api/` (upstream API client) as well as `/v2/`? | Yes — both clients share the same token; both should benefit from refresh-and-retry. |
| OQ2 | How to surface a "redirect after sign-in" UX? nuxt-auth supports `callbackUrl` on the sign-in redirect. | Pass `encodeURIComponent(useRoute().fullPath)` as `callbackUrl`. nuxt-auth handles restoring it post-auth. |
| OQ3 | What if the Keycloak refresh token is also expired (user been away for days)? | Redirect to `/auth/signIn` with the callback URL. The user signs in fresh; they land back on the same page. |
| OQ4 | JWT `exp` parsing — can we rely on `useAuth().data.value.accessToken` being a parseable JWT? | Yes in the current Keycloak/OIDC setup. Add a safe fallback (if parse fails, set a 30-minute timer). |
| OQ5 | What warning duration is right — 5 minutes? | 5 minutes matches the typical Keycloak access token lifetime floor. If the admin configures a longer lifetime, the warning is earlier (which is fine). |
