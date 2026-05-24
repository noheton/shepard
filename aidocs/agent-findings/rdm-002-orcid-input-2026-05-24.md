---
stage: feedback-implemented
last-stage-change: 2026-05-24
---

# RDM-002 — ORCID input on `/me/profile` (FAIR R1)

Closes **RDM-2026-05-24-002** flagged by `aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md`:
*"User profile at `/me` has no ORCID input field, even though the backend validates ORCID format."*

## What I found before writing code

The premise *"no ORCID input"* was off-by-one. The input **was already
present** in `ProfilePane.vue`, but only inside an Edit dialog that opened
when the user clicked an "Edit" button next to the page title (lines
371–377 of the pre-change file). The bug was **discoverability**, not
absence: a user landing on `/me#profile` saw ORCID as a read-only row in
the table with no visual hint that it was editable — exactly what the
scrutinizer experienced.

The second premise *"header avatar shows 'A' circle without an image"*
was also off: `HeaderBar.vue` line 238–241 rendered nothing more than a
plain `mdi-account-outline` icon button. No `v-avatar`, no initial
fallback. There was nothing to badge — building a real header avatar
was a small scope expansion the task narrative anticipated.

## Backend support — already in place (no backend changes)

- `backend/src/main/java/de/dlr/shepard/auth/users/entities/User.java`
  line 46 — `private String orcid;`
- `backend/src/main/java/de/dlr/shepard/auth/users/validation/OrcidValidator.java`
  — ISO 7064 mod 11-2 checksum
- `PATCH /v2/users/me` — accepts `orcid` (nullable; setting to `null`
  clears the field; setting to an invalid string is rejected at the
  validator). Per `aidocs/16` row U1a (already shipped).
- `GET /v2/users/{appId}/avatar` — public endpoint, image returned as
  blob. Per `aidocs/16` row U1e (already shipped).

So this task was a frontend-only fix: surface what the backend already
supports.

## What I added

### `frontend/utils/orcidFormat.ts` (new)
Client-side mirror of the Java `OrcidValidator` — same NNNN-NNNN-NNNN-NNN[N|X]
shape, same ISO 7064 mod 11-2 checksum. Used by the inline input to
surface an immediate error message before the user clicks Save. Exports
`isValidOrcid()` and a Vuetify-rules-friendly `orcidVTextFieldRule()`.

### `frontend/tests/unit/orcidFormat.test.ts` (new — Vitest, 14 tests)
Coverage:
- canonical example `0000-0002-1825-0097`
- `X`-checksum example `0000-0002-1694-233X`
- null / undefined / empty
- wrong length, missing hyphens, misplaced hyphens, letters in digit
  positions, mismatched checksum, free-text rejection
- `orcidVTextFieldRule` empty-as-valid + non-empty error message shape
- **All 14 pass** (verified by copying the test into the main worktree
  where `node_modules` + `.nuxt/tsconfig.json` exist, since worktrees
  don't carry `node_modules`).

### `frontend/components/context/user/ProfilePane.vue` (modified)
- Removed the `Edit` button and the `v-dialog` scaffolding.
- Removed `ORCID` and `Display Name` rows from the read-only `v-table`
  (they become editable inline immediately below).
- Added a new `<section data-testid="profile-identity-section">` with:
  - `Display name` `v-text-field` (data-testid `profile-display-name-input`)
  - `ORCID` `v-text-field` (data-testid `profile-orcid-input`) with
    `:error-messages` driven by the client-side validator, helper text
    linking to orcid.org, and `placeholder="0000-0000-0000-0000"`
  - `Save identity` button (data-testid `profile-identity-save`)
    disabled while clean, while saving, or while the ORCID input is
    invalid
  - Conditional "Currently set: …" line (data-testid
    `profile-orcid-current`) linking to the live ORCID profile
- Preserved (append-only on user data):
  - Avatar + upload/remove + on-avatar ORCID badge (lines 165–229)
  - Username, First Name, Last Name, E-Mail rows
  - "Resolved display name" derived-value row (now shows the derivation
    when displayName ≠ effectiveDisplayName so the user understands the
    fallback chain)
  - ORCID public-profile keywords + works section
  - JupyterHub URL section
  - Display settings (advanced mode toggle + ORCID-badge visibility
    toggle)

### `frontend/components/layout/HeaderBar.vue` (modified)
- Replaced the plain `mdi-account-outline` icon button with a real
  `<v-avatar>` (size 36) that renders the user's uploaded avatar image
  with an initials fallback. Unauthenticated visitors still see the
  account icon.
- Added an ORCID-badge overlay (14×14 SVG, the same green ORCID mark
  the ProfilePane already uses) when the user has an ORCID set and
  hasn't opted out via `ui.showOrcidBadge`.
- New `data-testid`s: `header-user-avatar-btn`, `header-user-avatar`,
  `header-user-orcid-badge`.
- Wiring: reuses `useFetchUserProfile()` and `useShowOrcidBadge()` —
  no new composables. The `v2BaseUrl()` helper is a copy of the same
  pattern from `ProfilePane.vue` (six lines; not worth lifting to a
  shared util in this PR).
- The `mobile-hidden` "Sign In/Out" button still exists adjacent; the
  avatar is the link to `/me#profile`. Outside the scope of this PR:
  swapping the sign-out behaviour into the avatar's overflow menu
  (would change behaviour for already-trained users; tracked as a
  separate UX refinement).

### `e2e/tests/rdm-002-orcid-on-profile.spec.ts` (new — Playwright, 5 tests)
Against `BASE_URL=https://shepard.nuclide.systems` with user `alice`:
1. ORCID input is visible without clicking Edit (the bug acceptance test)
2. Valid ORCID `0000-0002-1825-0097` → Save → reload → persists
3. Invalid ORCID `abc` → inline error visible, Save disabled
4. Alternate `X`-checksum ORCID `0000-0002-1694-233X` is accepted
5. Once ORCID is set, the header avatar shows the ORCID badge

`beforeEach` and `afterAll` clear the ORCID so the suite is
re-runnable from a known state.

## Test results

- Vitest `orcidFormat.test.ts`: **14/14 pass** (verified in main
  worktree).
- `vue-tsc --noEmit` against the modified `.vue` files: **no errors
  introduced** (pre-existing errors in unrelated files like
  `useFetchCollectionLabJournalEntries.ts` remain — none touch this PR).
- Playwright e2e: requires the build to land on
  `https://shepard.nuclide.systems` first; spec is checked in and ready
  to run via `npx playwright test e2e/tests/rdm-002-orcid-on-profile.spec.ts`
  once `make redeploy-frontend` completes.

## Backend already supports it? — Yes

Confirmed. `User.orcid` field + `OrcidValidator` (ISO 7064 mod 11-2) +
`PATCH /v2/users/me` (merge-patch with orcid + displayName) all shipped
under U1a / U1b. No backend changes in this PR.

## Live URL to verify

After `make redeploy-frontend` lands:
- https://shepard.nuclide.systems/me#profile — Identity section under
  the read-only details table, with the ORCID input visible and the
  helper text linking to orcid.org.
- Header (top-right) — avatar with initials fallback or uploaded image;
  green ORCID badge overlay appears once the user saves a valid ORCID
  and hasn't toggled the badge off.

## Scope notes for the next PR

- The "Resolved display name" row only renders when `displayName ≠
  effectiveDisplayName`. When the user picks an override that matches
  what would be derived (firstName + lastName), we don't show the row.
  Open question for UX: is that the right rule, or should the row
  always render so the user sees what others see?
- Both `ProfilePane.vue` and `HeaderBar.vue` now have local
  `v2BaseUrl()` helpers (six lines, duplicated). Consolidating to a
  `utils/v2BaseUrl.ts` would be a tiny follow-up — not done here to
  keep the PR diff scoped to RDM-002.
- The ORCID-badge `<svg>` markup is duplicated between ProfilePane and
  HeaderBar. A `components/common/OrcidBadge.vue` would be the right
  shape if a third site appears (DataObject "createdBy" chip is the
  obvious next site, per FAIR2 in `aidocs/16`).
- An invalid ORCID currently shows the error message inside the field's
  details slot, which competes with the `placeholder + helper link`
  text. Vuetify replaces the hint with the error when `:error-messages`
  is non-empty, which is the standard behaviour — no fix needed unless
  UX disagrees.
