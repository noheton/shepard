---
name: PROJ-PLAYWRIGHT-1 — unauth baseline captures (2026-06-03)
description: Unauthenticated 4K screenshots of the live shepard.nuclide.systems Projects surface, captured while PROJ-PLAYWRIGHT-1 was partially run on 2026-06-03. The authenticated 3-page pass remains blocked on PROJ-PLAYWRIGHT-1-AUTH-FIXTURE.
type: finding
stage: fragment
last-stage-change: 2026-06-03
---

# PROJ-PLAYWRIGHT-1 — unauth baselines (2026-06-03)

Captured against `https://shepard.nuclide.systems` at 3840×2160 viewport.

| File | URL | Notes |
|---|---|---|
| `home.png` | `/` | Pre-login redirect to the sign-in surface — confirms the host is reachable and the 4K viewport renders correctly. |
| `projects.png` | `/projects` | The new top-nav route. Authenticated content is gated; the screenshot captures whatever the unauth response renders. |

## Why these are unauth-only

`e2e/tests/helpers/auth.ts:loginAs()` ran the spec's default credentials
(`flodemo` / `flo-demo`) against Keycloak on the live host. Keycloak
returned **"Invalid username or password"**. Because the helper waits
30 s for `text=SIGN OUT` to appear post-login, the test's own 30 s
timeout fires before the helper gives up.

The Playwright infrastructure itself (4K viewport, navigation,
screenshot capture, full-page render) works as designed — verified by
the captures in this directory, taken with a direct
`chromium.launch()` outside the auth flow.

The full 3-page authenticated pass is tracked as
**`PROJ-PLAYWRIGHT-1-AUTH-FIXTURE`** in `aidocs/16`. Three resolution
options listed there; (b) — a committed `storageState.json` fixture
— is cheapest for CI parity.

## Verifying the new endpoint is up

```
curl -fsS https://shepard.nuclide.systems/v2/projects \
  -o /dev/null -w '%{http_code}\n'
# → 401 (authentication required — namespace is live and gated)
```
