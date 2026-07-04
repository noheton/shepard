---
stage: deployed
last-stage-change: 2026-05-31
---

# BUG-SIGNOUT-LOOP-1 — sign-out infinite loop fix

Operator report 2026-05-31: clicking "Sign out" on `https://shepard.nuclide.systems` triggered an infinite redirect loop.

## Evidence captured

- `signout-chain-PRE-FIX.json` — Playwright trace of the loop. 339 navigations in 12 seconds, ping-ponging between `/`, `/api/auth/signin/oidc`, and `/auth/signIn?callbackUrl=/`.
- `01-signed-in-before.png` — header showing SIGN OUT button.
- `02-after-signout-attempt.png` — captured at the end of the 12 s window.

## Root cause

After the user clicks sign out, `signOut({ callbackUrl: "/" })` clears the next-auth session. Components mounted in the layout (e.g. `HeaderBar`'s `useFetchUserProfile`, the notifications poller) keep firing API calls **unconditionally**. Those calls 401 because there is no token. `useAuthRefreshMiddleware` reacted to every 401 by calling `signIn("oidc", { redirect: true })` — which navigates to `/api/auth/signin/oidc`, which renders `/auth/signIn`, whose `onMounted` navigates back to `/`, where the same components fire again. Infinite loop.

## Fix

`useAuthRefreshMiddleware` now suppresses the re-auth call when **any** of the following hold:

- `data.accessToken` is absent (the user signed out, no token to refresh),
- `status === "unauthenticated"` (covers the race where data still holds a stale shape),
- the browser is currently on a known-public route (`/`, `/auth/signIn`, `/auth/signOut`).

The 401 propagates to the caller's `.catch(handleError)` instead. Components that genuinely need authenticated data should gate their fetch on auth state (filed as `AUTH-API-CALLS-UNGATED` in `aidocs/16`).

## Related, not in this PR

- `BUG-SIGNOUT-KC-SSO-LINGERS` — `events.signOut` calls Keycloak's `end_session_endpoint` server-side via `fetch`, which does not clear the user's browser Keycloak SSO cookie. The fix is a client-side redirect to `end_session_endpoint?id_token_hint=…&post_logout_redirect_uri=https://shepard.nuclide.systems/`. Filed as a follow-up; not required to break the loop.
- `AUTH-API-CALLS-UNGATED` — gate `useFetchUserProfile`, notifications poller, and similar layout-mounted fetches on `status === "authenticated"` instead of relying on the middleware safety net.
