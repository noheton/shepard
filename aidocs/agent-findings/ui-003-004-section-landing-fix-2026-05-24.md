---
stage: tests-implemented
last-stage-change: 2026-05-24
---

# UI-2026-05-24-003 + UI-2026-05-24-004 fix — section index landings + Unauthorized view

Closes the two MAJOR UX failures bundled as Top-5 fix #3 from the
UI Scrutinizer 2026-05-24 report:

- **UI-2026-05-24-003 (MAJOR)** — `/me`, `/admin`, `/about`, `/configuration` rendered an empty content panel + sub-nav only when no URL fragment was set; user thought the section was broken.
- **UI-2026-05-24-004 (MAJOR)** — alice (non-admin) navigating to `/admin` was silently bounced to `/me` with no toast, no 403, no "not authorized" message.

## What I built

### 1. `SectionIndexLanding.vue` (new)

`frontend/components/layout/SectionIndexLanding.vue` — a reusable Vuetify
card-grid landing for fragment-based section pages. Props:

| Prop | Type | Notes |
| --- | --- | --- |
| `title` | `string` | Section heading (h4). |
| `subtitle?` | `string` | Optional one-liner under the heading. |
| `sections` | `SectionLandingCard[]` | List of `{ fragment, icon, title, description, badge? }`. |
| `emptyMessage?` | `string` | Shown when `sections` is empty (info alert). |
| default slot | — | Free area below the grid for section-specific content. |

Renders a `<v-row>` of `<v-card>` tiles (12 / sm:6 / lg:4 columns). Cards
navigate via `{ hash: '#<fragment>' }` — same shape as `MenuList.vue`, so
the existing `useRouteFragment` + active-button class logic keeps working.
No new dependencies.

Pure-helper module `frontend/components/layout/sectionLanding.ts` (testable
without DOM) exports `buildSectionLandingCards()` which attaches the `to`
shape.

### 2. `UnauthorizedView.vue` (new)

`frontend/components/layout/UnauthorizedView.vue` — polite "you don't have
access" card with two CTAs:

- **Go home** (`<v-btn to="/">`)
- **Sign in as another user** — calls `useAuth().signOut({ callbackUrl: "/" })`

Optional `requiredRole` prop renders a `Required role: <code>...</code>` line.

### 3. Section pages wired up

| Page | Landing cards | Notes |
| --- | --- | --- |
| `pages/me/index.vue` | Profile, API Keys, MCP, Subscriptions, Git Credentials | Mirrors `UserMenuEntries`. |
| `pages/admin/index.vue` | Feature Toggles, Plugins, Instance Health, Storage Overview, Templates, Semantic Repositories, User Groups, Research Organization, Permission Audit Log, Unhide, Legacy v1 | Mirrors `AdminMenuEntries`. |
| `pages/about/index.vue` | Version, Organization, System Health, Documentation | Mirrors `AboutMenuEntries`. |
| `pages/configuration/index.vue` | **not touched** | Already `await navigateTo("/admin", { replace: true })` — i.e. a real redirect, not a blank section. The scrutinizer's `p6-04-configuration-as-admin-1920.png` was capturing /admin's empty state after the redirect; fixing /admin's index fixes this surface too. |

### 4. Unauthorized handling — Option A (URL-stable)

Picked Option A: removed the silent `watchEffect(() => navigateTo("/me"))`
in `pages/admin/index.vue` and replaced it with a conditional render —
`<UnauthorizedView v-if="showUnauthorized" />` else the normal pane layout.

Rationale: keeps the `/admin` URL stable (shareable links still resolve),
gives the user explicit feedback ("instance-admin role required"), and
offers a clean recovery path (sign out + sign in as another user). The
alternative (toast + redirect) would still strand a non-admin on `/me`
with no obvious way back to try again.

The check waits until `status.value !== "loading"` so the view doesn't
flash "Unauthorized" before auth has resolved.

## Test results

### Vitest unit tests — passing

```
$ npx vitest run tests/unit/sectionLanding.test.ts

 ✓ tests/unit/sectionLanding.test.ts (6 tests) 10ms

 Test Files  1 passed (1)
      Tests  6 passed (6)
```

The codebase has no `@vue/test-utils` (verified — not in `frontend/package.json`).
Instead of adding the dep just for one test, I extracted the routable-shape
logic to a pure helper (`sectionLanding.ts`) so the Vitest test exercises the
prop-transform contract directly. Component rendering itself is covered by
the Playwright e2e tests below. If the team wants full mount tests later,
adding `@vue/test-utils` is a small follow-up; tracked in the backlog row.

### Playwright e2e tests — `e2e/tests/section-index-landing.spec.ts`

Three landing tests (admin) + one unauthorized test (alice):

- `/me shows the landing card grid (no blank panel)`
- `/admin shows the landing card grid (no blank panel)`
- `/about shows the landing card grid (no blank panel)`
- `alice hitting /admin sees Unauthorized view (URL stable, no silent bounce)`

Each landing test verifies (a) the section title renders, (b) the first
card is visible + clickable, (c) clicking sets the matching URL hash. The
unauthorized test verifies the URL stays at `/admin` and that body text
contains `access|permission|restricted`.

To run against the live instance after redeploy:

```bash
cd e2e && BASE_URL=https://shepard.nuclide.systems \
  npx playwright test section-index-landing.spec.ts
```

## Live URLs (after deploy)

Test landings:

- https://shepard.nuclide.systems/me (any signed-in user)
- https://shepard.nuclide.systems/admin (admin user)
- https://shepard.nuclide.systems/about (any signed-in user)

Test unauthorized handling:

- Sign in as alice (`alice / alice-demo`) and visit https://shepard.nuclide.systems/admin — URL stays at `/admin`, Unauthorized card renders.

## Coordination notes

- **Did not touch**: `components/layout/HeaderBar.vue` or anything under `composables/context/` — both reserved for the parallel agent working on UI-2026-05-24-002 (header search dropdown).
- **Other agent's WIP**: `frontend/composables/context/useGlobalSearch.ts` + `frontend/tests/unit/useGlobalSearch.test.ts` are theirs and trigger `vue-tsc` advisory output during build. They are not blocking (the nuxt build itself succeeded).

## Backlog / deferred

- **Component mount tests for `SectionIndexLanding.vue` + `UnauthorizedView.vue`** — would need `@vue/test-utils` (not currently in the frontend). Playwright covers the behaviour today; the unit dep can be added in a separate PR if the team wants finer-grained component tests.
- **`/configuration` landing** — currently a redirect to `/admin`. If we ever want a non-admin configuration landing (e.g. user-scoped preferences that aren't admin-gated), this is the place. Out of scope for this fix.
- **Apply `SectionIndexLanding` to the `containers/` index** if a similar blank-on-no-fragment pattern exists there — not surveyed for this fix.
